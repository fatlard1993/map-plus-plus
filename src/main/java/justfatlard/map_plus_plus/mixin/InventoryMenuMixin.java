package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.CompassSlot;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.map_plus_plus.inventory.MapSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
	private InventoryMenuMixin() { super(null, 0); }

	@Inject(method = "<init>", at = @At("TAIL"))
	private void addMapPlusPlusSlots(Inventory inventory, boolean isLocalPlayer, Player player, CallbackInfo ci) {
		MapPlusPlusInventory mppInv = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();

		// Horizontal pair to the right of the recipe book button (which is at 104, 52)
		addSlot(new MapSlot(mppInv, MapPlusPlusInventory.MAP_SLOT, 127, 62));
		addSlot(new CompassSlot(mppInv, MapPlusPlusInventory.COMPASS_SLOT, 145, 62));
	}
}
