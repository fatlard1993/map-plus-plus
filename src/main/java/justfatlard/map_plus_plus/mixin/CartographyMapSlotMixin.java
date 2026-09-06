package justfatlard.map_plus_plus.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The map slot also takes a lodestone compass, the thing that can be copied there.
 *
 * <p>An anonymous class, so it is named by number: the third one the menu's constructor makes,
 * after the input container and the result container. Only a tracking compass gets in - a plain
 * one has nothing to copy, and would sit in the slot promising a result that never comes.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
public abstract class CartographyMapSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void mapPlusPlus$acceptTrackedCompass(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(Items.COMPASS) && stack.has(DataComponents.LODESTONE_TRACKER)) {
			cir.setReturnValue(true);
		}
	}
}
