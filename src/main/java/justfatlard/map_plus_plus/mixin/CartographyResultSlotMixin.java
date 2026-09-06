package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.CompassTarget;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Marking a map does not spend the compass.
 *
 * <p>Taking a result removes one from each input, which is right for every other thing the
 * table makes: the paper, the pane, the blank map and the blank compass are all used up. A
 * compass that marks a map is not: the mark is where it points, and it points there still.
 * Consuming it would also price the mark at a netherite ingot when the compass is a lodestone
 * one, for a line of information the map then carries for free. So the second removal is
 * skipped when the thing taken is a map and the thing in the slot is a compass. The fifth
 * anonymous class is the result slot.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class CartographyResultSlotMixin {
	@Redirect(method = "onTake",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;remove(I)Lnet/minecraft/world/item/ItemStack;", ordinal = 1))
	private ItemStack mapPlusPlus$keepMarkingCompass(Slot slot, int amount, Player player, ItemStack taken) {
		if (taken.has(DataComponents.MAP_ID) && CompassTarget.isCompass(slot.getItem())) {
			return ItemStack.EMPTY;
		}
		return slot.remove(amount);
	}
}
