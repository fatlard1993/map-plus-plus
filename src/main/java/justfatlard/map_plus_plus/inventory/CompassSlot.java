package justfatlard.map_plus_plus.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CompassSlot extends Slot {
	public CompassSlot(Container container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		// Accept any compass type: regular, lodestone (COMPASS + component), recovery, and custom CompassItem subclasses
		return stack.getItem() instanceof CompassItem || stack.is(Items.RECOVERY_COMPASS);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public Identifier getNoItemIcon() {
		return Identifier.fromNamespaceAndPath("map-plus-plus", "empty_compass_slot");
	}

}
