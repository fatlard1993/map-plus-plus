package justfatlard.map_plus_plus.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    // Slot positions in menu coordinates (must match InventoryMenuMixin)
    private static final int MAP_X     = 127;
    private static final int MAP_Y     = 62;
    private static final int COMPASS_X = 145;
    private static final int COMPASS_Y = 62;

    // Vanilla slot colors — sampled from inventory.png (ARGB)
    private static final int SLOT_DARK  = 0xFF373737; // dark border (top + left, 1px)
    private static final int SLOT_FILL  = 0xFF8B8B8B; // slot interior (medium gray)
    private static final int SLOT_WHITE = 0xFFFFFFFF; // highlight (bottom + right, 1px)

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void drawMapSlotBackgrounds(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) this;
        int lp = screen.getLeftPos();
        int tp = screen.getTopPos();

        drawSlotBox(context, lp + MAP_X,     tp + MAP_Y);
        drawSlotBox(context, lp + COMPASS_X, tp + COMPASS_Y);
    }

    private static void drawSlotBox(GuiGraphicsExtractor ctx, int x, int y) {
        // 1px dark border on top + left
        ctx.fill(x - 1, y - 1, x + 16, y,      SLOT_DARK);  // top
        ctx.fill(x - 1, y - 1, x,      y + 16,  SLOT_DARK);  // left
        // 1px white highlight on bottom + right (creates the inset/sunken look)
        ctx.fill(x,     y + 16, x + 17, y + 17, SLOT_WHITE); // bottom
        ctx.fill(x + 16, y,    x + 17, y + 16,  SLOT_WHITE); // right
        // Medium gray fill
        ctx.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }
}
