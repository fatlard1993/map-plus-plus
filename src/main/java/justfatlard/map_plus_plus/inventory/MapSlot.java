package justfatlard.map_plus_plus.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MapSlot extends Slot {
	public MapSlot(Container container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return stack.is(Items.FILLED_MAP);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public Identifier getNoItemIcon() {
		return Identifier.fromNamespaceAndPath("map-plus-plus", "empty_map_slot");
	}

}
