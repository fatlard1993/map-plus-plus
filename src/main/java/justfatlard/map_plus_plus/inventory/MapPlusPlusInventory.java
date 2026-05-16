package justfatlard.map_plus_plus.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class MapPlusPlusInventory extends SimpleContainer {
	public static final int MAP_SLOT = 0;
	public static final int COMPASS_SLOT = 1;

	public MapPlusPlusInventory() {
		super(2);
	}

	public ItemStack getMapStack() {
		return getItem(MAP_SLOT);
	}

	public ItemStack getCompassStack() {
		return getItem(COMPASS_SLOT);
	}

	public boolean hasMap() {
		return !getMapStack().isEmpty();
	}

	public boolean hasCompass() {
		return !getCompassStack().isEmpty();
	}

	public void copyFrom(MapPlusPlusInventory other) {
		for (int i = 0; i < getContainerSize(); i++) {
			setItem(i, other.getItem(i).copy());
		}
	}
}
