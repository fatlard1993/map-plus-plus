package justfatlard.map_plus_plus.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class MapSlot extends Slot {
	public MapSlot(Container container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return isMap(stack);
	}

	/**
	 * Anything carrying a map id, rather than a list of map item types.
	 *
	 * <p>This generation split explorer maps out of {@code filled_map} into
	 * sixteen items of their own — {@code abandoned_camp_map},
	 * {@code buried_treasure_map}, the five village maps and the rest — so a
	 * check against {@code filled_map} rejected every structure map a player
	 * would most want pinned here.
	 *
	 * <p>The map id is also exactly what the minimap reads to render, so the
	 * slot now accepts precisely what it can draw, and stays correct when the
	 * seventeenth map item arrives. The blank {@code map} has no id and is still
	 * refused, which is right: there is nothing on it to show.
	 */
	public static boolean isMap(ItemStack stack) {
		return stack.has(DataComponents.MAP_ID);
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
