package justfatlard.map_plus_plus.inventory;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class MapPlusPlusInventory extends SimpleContainer {
	public static final int MAP_SLOT = 0;
	public static final int COMPASS_SLOT = 1;

	/** Where these two items live inside the player's own saved data. */
	private static final String KEY = "MapPlusPlusItems";

	/** Optional, so an empty map slot still holds the compass slot's place. */
	private static final Codec<List<ItemStack>> ITEMS = ItemStack.OPTIONAL_CODEC.listOf();

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

	/**
	 * Carry these across a respawn. A death builds a new player object, and
	 * nothing about that object came from disk, so without this the map is gone
	 * the first time you die.
	 */
	public void copyFrom(MapPlusPlusInventory other) {
		for (int i = 0; i < getContainerSize(); i++) {
			setItem(i, other.getItem(i).copy());
		}
	}

	public void save(ValueOutput output) {
		List<ItemStack> items = new ArrayList<>(getContainerSize());
		for (int slot = 0; slot < getContainerSize(); slot++) items.add(getItem(slot));
		output.store(KEY, ITEMS, items);
	}

	public void load(ValueInput input) {
		input.read(KEY, ITEMS).ifPresent(items -> {
			for (int slot = 0; slot < getContainerSize(); slot++) {
				setItem(slot, slot < items.size() ? items.get(slot) : ItemStack.EMPTY);
			}
		});
	}
}
