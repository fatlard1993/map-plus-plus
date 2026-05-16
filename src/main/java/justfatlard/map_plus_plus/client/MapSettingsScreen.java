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
 * Client-side settings screen for the map minimap display.
 * Opened by right-clicking the map slot in the inventory.
 * Shows corner, zoom, and show-coords options only.
 */
public class MapSettingsScreen extends Screen {

    private static final int PANEL_W = 170;
    private static final int PANEL_H = 145;
    private static final int DARK_BG = 0xAA000000;
    private static final int PANEL_BG = 0xFF1E1E1E;
    private static final int PANEL_BORDER = 0xFF555555;

    // Corner button states
    private static final String[] CORNERS = { "top_left", "top_right", "bottom_left", "bottom_right" };
    private static final String[] CORNER_LABELS = { "Top Left", "Top Right", "Bot Left", "Bot Right" };

    // Zoom levels
    private static final float[] ZOOM_LEVELS = { 1.0f, 1.5f, 2.0f, 3.0f, 4.0f };
    private static final String[] ZOOM_LABELS = { "1\u00d7", "1.5\u00d7", "2\u00d7", "3\u00d7", "4\u00d7" };

    private final Screen parent;

    // Buttons tracked for corner/zoom selection highlight
    private final List<Button> cornerButtons = new ArrayList<>();
    private final List<Button> zoomButtons = new ArrayList<>();
    private Button doneButton;

    private int panelX, panelY;

    public MapSettingsScreen(Screen parent) {
        super(Component.literal("Map Display Settings"));
        this.parent = parent;
        MapDisplaySettings.ensureLoaded();
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        cornerButtons.clear();
        zoomButtons.clear();

        int cx = panelX + 8;
        int cy = panelY + 18; // below title

        // --- Corner selector ---
        cy += 4;
        // Label "Corner:" drawn in extractRenderState
        cy += 10;
        int cornerBtnW = 36;
        for (int i = 0; i < CORNERS.length; i++) {
            final String cornerVal = CORNERS[i];
            Button btn = Button.builder(Component.literal(CORNER_LABELS[i]), b -> {
                MapDisplaySettings.setCorner(cornerVal);
                rebuildCornerHighlight();
            }).bounds(cx + i * (cornerBtnW + 2), cy, cornerBtnW, 12).build();
            addRenderableWidget(btn);
            cornerButtons.add(btn);
        }
        cy += 15;

        // --- Zoom selector ---
        cy += 4;
        // "Zoom:" label drawn in extractRenderState
        cy += 10;
        int zoomBtnW = 28;
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            final float zoomVal = ZOOM_LEVELS[i];
            Button btn = Button.builder(Component.literal(ZOOM_LABELS[i]), b -> {
                MapDisplaySettings.setZoomLevel(zoomVal);
                rebuildZoomHighlight();
            }).bounds(cx + i * (zoomBtnW + 2), cy, zoomBtnW, 12).build();
            addRenderableWidget(btn);
            zoomButtons.add(btn);
        }
        cy += 15;
        rebuildZoomHighlight();

        // --- Show Coords ---
        cy += 4;
        Checkbox coordsCheckbox = Checkbox.builder(Component.literal("Show coords"), this.font)
            .pos(cx, cy)
            .selected(MapDisplaySettings.isShowCoords())
            .onValueChange((cb, val) -> MapDisplaySettings.setShowCoords(val))
            .build();
        addRenderableWidget(coordsCheckbox);

        // Done button — always at bottom of panel
        doneButton = Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(panelX + PANEL_W / 2 - 40, panelY + PANEL_H - 22, 80, 14)
            .build();
        addRenderableWidget(doneButton);

        rebuildCornerHighlight();
    }

    private void rebuildCornerHighlight() {
        String active = MapDisplaySettings.getCorner();
        for (int i = 0; i < CORNERS.length; i++) {
            boolean isActive = CORNERS[i].equals(active);
            cornerButtons.get(i).active = !isActive;
        }
    }

    private void rebuildZoomHighlight() {
        float active = MapDisplaySettings.getZoomLevel();
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            boolean isActive = Math.abs(ZOOM_LEVELS[i] - active) < 0.01f;
            zoomButtons.get(i).active = !isActive;
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

        // Section labels
        int cx = panelX + 8;
        int labelY = panelY + 18 + 4;
        graphics.text(this.font, "Corner:", cx, labelY, 0xFFCCCCCC, false);

        int zoomLabelY = labelY + 10 + 15 + 4;
        graphics.text(this.font, "Zoom:", cx, zoomLabelY, 0xFFCCCCCC, false);

        // Render widgets
        super.extractRenderState(graphics, mouseX, mouseY, delta);
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
}
