package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Makes Inventory.contains(Predicate) also check the map++ custom inventory slots.
 *
 * MapItemSavedData.tickCarriedBy() calls player.getInventory().contains(mapMatcher)
 * to verify the player still has the map. Without this mixin, the map in the custom
 * slot is invisible to this check, so the player is removed from map tracking every
 * tick and no position decoration is ever sent to the client.
 */
@Mixin(Inventory.class)
public abstract class PlayerIsHoldingMixin {

    @Shadow
    public net.minecraft.world.entity.player.Player player;

    @Inject(method = "contains(Ljava/util/function/Predicate;)Z", at = @At("RETURN"), cancellable = true)
    private void mapPlusPlus$checkMapSlot(Predicate<ItemStack> predicate, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // Already found in vanilla inventory

        if (!(player instanceof MapPlusPlusPlayerAccess access)) return;
        MapPlusPlusInventory inv = access.mapPlusPlus$getInventory();
        ItemStack mapStack = inv.getMapStack();
        if (!mapStack.isEmpty() && predicate.test(mapStack)) {
            cir.setReturnValue(true);
        }
    }
}
