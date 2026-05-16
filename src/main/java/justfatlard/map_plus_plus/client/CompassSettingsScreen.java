package justfatlard.map_plus_plus.client;

import justfatlard.pandorical.client.component.MapDisplaySettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side settings screen for mob sight (compass enchantment) display.
 * Opened by right-clicking the compass slot in the inventory, only when
 * the equipped compass has the map-plus-plus:mob_sight enchantment.
 */
public class CompassSettingsScreen extends Screen {

    private static final int PANEL_W = 170;
    private static final int PANEL_H = 190;
    private static final int DARK_BG = 0xAA000000;
    private static final int PANEL_BG = 0xFF1E1E1E;
    private static final int PANEL_BORDER = 0xFF555555;

    // Mob list for individual filtering (passive + other combined)
    private static final String[] MOB_IDS = {
        "bat", "bee", "cat", "chicken", "cod", "cow", "donkey", "fox",
        "frog", "goat", "horse", "mooshroom", "mule", "ocelot", "parrot",
        "pig", "polar_bear", "pufferfish", "rabbit", "salmon", "sheep",
        "sniffer", "squid", "strider", "tadpole", "tropical_fish", "turtle", "wolf",
        "axolotl", "glow_squid", "iron_golem", "snow_golem", "allay", "armadillo", "breeze"
    };

    private static final int MOB_COLS = 2;
    private static final int MOB_BTN_W = 75;
    private static final int MOB_BTN_H = 12;
    private static final int MOB_ROWS_VISIBLE = 3;

    private final Screen parent;

    // UI state
    private boolean expandMobs = false;
    private int scrollOffset = 0;

    private Checkbox hostileCheckbox;
    private Checkbox passiveOtherCheckbox;
    private Button expandButton;
    private Button doneButton;
    private final List<Button> mobButtons = new ArrayList<>();

    private int panelX, panelY;

    public CompassSettingsScreen(Screen parent) {
        super(Component.literal("Mob Sight Settings"));
        this.parent = parent;
        MapDisplaySettings.ensureLoaded();
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        mobButtons.clear();

        int cx = panelX + 8;
        int cy = panelY + 18; // below title

        // --- Mob Sight section header ---
        cy += 4;
        // "Mob Sight:" label drawn in extractRenderState
        cy += 10;

        // Hostile checkbox
        hostileCheckbox = Checkbox.builder(Component.literal("Hostile"), this.font)
            .pos(cx, cy)
            .selected(MapDisplaySettings.isShowHostile())
            .onValueChange((cb, val) -> MapDisplaySettings.setShowHostile(val))
            .build();
        addRenderableWidget(hostileCheckbox);
        cy += 16;

        // Passive & Other checkbox + Expand button
        passiveOtherCheckbox = Checkbox.builder(Component.literal("Passive & Other"), this.font)
            .pos(cx, cy)
            .selected(MapDisplaySettings.isShowPassiveOther())
            .onValueChange((cb, val) -> MapDisplaySettings.setShowPassiveOther(val))
            .build();
        addRenderableWidget(passiveOtherCheckbox);

        expandButton = Button.builder(
            Component.literal(expandMobs ? "v" : ">"),
            b -> {
                expandMobs = !expandMobs;
                scrollOffset = 0;
                b.setMessage(Component.literal(expandMobs ? "v" : ">"));
                rebuildMobButtons();
            }
        ).bounds(cx + 148, cy, 12, 12).build();
        addRenderableWidget(expandButton);
        cy += 16;

        // Mob buttons area (dynamic, inside scrollable region)
        rebuildMobButtons();

        // Done button — always at bottom of panel
        doneButton = Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(panelX + PANEL_W / 2 - 40, panelY + PANEL_H - 22, 80, 14)
            .build();
        addRenderableWidget(doneButton);
    }

    private void rebuildMobButtons() {
        for (Button b : mobButtons) {
            removeWidget(b);
        }
        mobButtons.clear();

        if (!expandMobs) return;

        // Position below passive/expand row:
        // panelY + 18(title) + 4(gap) + 10(mob lbl) + 16(hostile) + 16(passive) = panelY + 64
        int mobAreaY = panelY + 64;
        int cx = panelX + 8;

        int totalRows = (MOB_IDS.length + MOB_COLS - 1) / MOB_COLS;
        int visibleRows = Math.min(MOB_ROWS_VISIBLE, totalRows - scrollOffset);

        for (int row = 0; row < visibleRows; row++) {
            int mobRow = row + scrollOffset;
            for (int col = 0; col < MOB_COLS; col++) {
                int idx = mobRow * MOB_COLS + col;
                if (idx >= MOB_IDS.length) break;
                final String mobId = "minecraft:" + MOB_IDS[idx];
                final String label = capitalize(MOB_IDS[idx]);
                boolean disabled = MapDisplaySettings.getDisabledMobTypes().contains(mobId);
                Button btn = Button.builder(
                    Component.literal((disabled ? "[x] " : "[v] ") + label),
                    b -> {
                        boolean nowDisabled = !MapDisplaySettings.getDisabledMobTypes().contains(mobId);
                        MapDisplaySettings.setMobTypeDisabled(mobId, nowDisabled);
                        b.setMessage(Component.literal((nowDisabled ? "[x] " : "[v] ") + label));
                    }
                ).bounds(cx + col * (MOB_BTN_W + 4), mobAreaY + row * (MOB_BTN_H + 2), MOB_BTN_W, MOB_BTN_H).build();
                addRenderableWidget(btn);
                mobButtons.add(btn);
            }
        }

        // Up/down scroll buttons if needed
        if (totalRows > MOB_ROWS_VISIBLE) {
            int scrollBtnX = panelX + PANEL_W - 16;
            int scrollBtnY = mobAreaY;
            int scrollAreaH = MOB_ROWS_VISIBLE * (MOB_BTN_H + 2);

            if (scrollOffset > 0) {
                Button upBtn = Button.builder(Component.literal("^"), b -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    rebuildMobButtons();
                }).bounds(scrollBtnX, scrollBtnY, 14, 14).build();
                addRenderableWidget(upBtn);
                mobButtons.add(upBtn);
            }
            if (scrollOffset + MOB_ROWS_VISIBLE < totalRows) {
                Button downBtn = Button.builder(Component.literal("v"), b -> {
                    scrollOffset = Math.min(totalRows - MOB_ROWS_VISIBLE, scrollOffset + 1);
                    rebuildMobButtons();
                }).bounds(scrollBtnX, scrollBtnY + scrollAreaH - 14, 14, 14).build();
                addRenderableWidget(downBtn);
                mobButtons.add(downBtn);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, DARK_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Panel background and border
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);

        // Title
        graphics.centeredText(this.font, this.title,
            panelX + PANEL_W / 2, panelY + 6, 0xFFFFFFFF);

        // Section label
        int cx = panelX + 8;
        int mobSightLabelY = panelY + 18 + 4;
        graphics.text(this.font, "Mob Sight:", cx, mobSightLabelY, 0xFFCCCCCC, false);

        // Render widgets
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (expandMobs && mobButtons.size() > 0) {
            int totalRows = (MOB_IDS.length + MOB_COLS - 1) / MOB_COLS;
            if (totalRows > MOB_ROWS_VISIBLE) {
                if (vAmount < 0) {
                    scrollOffset = Math.min(totalRows - MOB_ROWS_VISIBLE, scrollOffset + 1);
                } else {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                }
                rebuildMobButtons();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        MapDisplaySettings.save();
        this.minecraft.setScreen(parent);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String replaced = s.replace('_', ' ');
        String[] words = replaced.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
