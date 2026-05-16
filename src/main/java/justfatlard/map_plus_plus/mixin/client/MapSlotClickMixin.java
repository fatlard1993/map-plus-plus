package justfatlard.map_plus_plus.mixin.client;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.client.CompassSettingsScreen;
import justfatlard.map_plus_plus.client.MapSettingsScreen;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts right-click on the map and compass slots in the inventory screen.
 * - Map slot: opens MapSettingsScreen (corner, zoom, show-coords).
 * - Compass slot: opens CompassSettingsScreen only when the equipped compass
 *   has the map-plus-plus:mob_sight enchantment.
 * Targets AbstractContainerScreen where mouseClicked is actually defined.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MapSlotClickMixin {

    private static final int MAP_X = 127;
    private static final int MAP_Y = 62;
    private static final int COMPASS_X = 145;
    private static final int COMPASS_Y = 62;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mapPlusPlus$onMapSlotRightClick(MouseButtonEvent event, boolean doubleClick,
                                                  CallbackInfoReturnable<Boolean> cir) {
        // Only intercept on InventoryScreen (not chests, furnaces, etc.)
        if (!(((Object) this) instanceof InventoryScreen)) return;
        if (event.button() != 1) return;

        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) this;
        int lp = acc.getLeftPos();
        int tp = acc.getTopPos();

        double mx = event.x();
        double my = event.y();

        // --- Map slot ---
        int slotX = lp + MAP_X;
        int slotY = tp + MAP_Y;
        if (mx >= slotX && mx < slotX + 17 && my >= slotY && my < slotY + 17) {
            Minecraft.getInstance().setScreen(new MapSettingsScreen((Screen)(Object) this));
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        // --- Compass slot: only open if Mob Sight enchanted ---
        int compassSlotX = lp + COMPASS_X;
        int compassSlotY = tp + COMPASS_Y;
        if (mx >= compassSlotX && mx < compassSlotX + 17 && my >= compassSlotY && my < compassSlotY + 17) {
            MapPlusPlusInventory inv =
                ((MapPlusPlusPlayerAccess) Minecraft.getInstance().player)
                    .mapPlusPlus$getInventory();
            ItemStack compassStack = inv.getCompassStack();
            if (!compassStack.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    try {
                        var enchHolder = mc.level.registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Main.MOB_SIGHT);
                        if (compassStack.getEnchantments().getLevel(enchHolder) > 0) {
                            mc.setScreen(new CompassSettingsScreen((Screen)(Object) this));
                            cir.setReturnValue(true);
                            cir.cancel();
                        }
                    } catch (Exception ignored) {}
                }
            }
            // Consumed regardless — no action if not enchanted
            return;
        }
    }
}
