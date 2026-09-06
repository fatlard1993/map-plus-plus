package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.CompassCartography;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cartography table answers a compass in either slot.
 *
 * <p>Vanilla's result logic only knows maps: with anything else in the map slot it finds no map
 * data and quietly does nothing, leaving whatever was in the result slot there. So a compass in
 * either slot is taken over whole here, result or cleared result, and the map cases are left to
 * vanilla untouched.
 *
 * <p>The player is remembered from the inventory the menu was opened with, because a recovery
 * compass points at whoever is holding it, and the menu never otherwise knows who that is.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {
	@Shadow @Final private ContainerLevelAccess access;
	@Shadow @Final private ResultContainer resultContainer;

	@Unique
	private Player mapPlusPlus$player;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
		at = @At("RETURN"))
	private void mapPlusPlus$rememberPlayer(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		this.mapPlusPlus$player = inventory.player;
	}

	@Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
	private void mapPlusPlus$compassResult(ItemStack first, ItemStack second, ItemStack result, CallbackInfo ci) {
		if (!CompassCartography.applies(first, second)) return;
		ci.cancel();

		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		access.execute((level, pos) -> {
			ItemStack out = CompassCartography.result(first, second, mapPlusPlus$player, level);
			if (out.isEmpty()) {
				resultContainer.removeItemNoUpdate(CartographyTableMenu.RESULT_SLOT);
				menu.broadcastChanges();
			} else if (!ItemStack.matches(out, result)) {
				resultContainer.setItem(CartographyTableMenu.RESULT_SLOT, out);
				menu.broadcastChanges();
			}
		});
	}
}
