package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.CompassTarget;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The second slot, paper and panes and blank maps, also takes any compass: the one to mark a
 * map with, or the blank one a lodestone compass is copied onto. Numbered like its sibling;
 * this is the fourth anonymous class in the constructor.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyExtraSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void mapPlusPlus$acceptCompass(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (CompassTarget.isCompass(stack)) {
			cir.setReturnValue(true);
		}
	}
}
