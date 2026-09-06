package justfatlard.map_plus_plus;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * What the cartography table makes of a compass.
 *
 * <p>Two things, shaped on what the table already does for maps. A map and a blank map come out
 * as two of the map; a lodestone compass and a blank compass come out as two pointing at the
 * same lodestone. A map and a piece of paper come out as a wider map; a map and a compass come
 * out as the same map with the compass's place marked on it, provided that place is somewhere on
 * the map to begin with. The marks accumulate, so a map can carry every lodestone you own.
 *
 * <p>The mark is a {@link MapDecorations} entry on the item, the same way an explorer map
 * carries its X: the map's saved data reads the component off whoever is holding it and draws
 * the decoration, and the item keeps it through copying and framing. Nothing here writes to the
 * map's data directly.
 */
public final class CompassCartography {
	private CompassCartography() {}

	/** Vanilla's own marker for "the place you are looking for". */
	private static final net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> MARK = MapDecorationTypes.TARGET_X;

	/** Whether either input is a compass, in which case the result is this class's to decide. */
	public static boolean applies(ItemStack first, ItemStack second) {
		return CompassTarget.isCompass(first) || CompassTarget.isCompass(second);
	}

	/**
	 * The result for these two inputs, or empty when they make nothing.
	 *
	 * @param first  the map slot: a map to mark, or a lodestone compass to copy
	 * @param second the other slot: the compass to mark with, or the blank compass to copy onto
	 */
	public static ItemStack result(ItemStack first, ItemStack second, Player player, Level level) {
		if (isTracked(first) && isBlank(second)) {
			return first.copyWithCount(2);
		}
		if (first.has(DataComponents.MAP_ID) && CompassTarget.isCompass(second)) {
			return marked(first, second, player, level);
		}
		return ItemStack.EMPTY;
	}

	/** A compass that has found its lodestone. One that lost it has nothing worth copying. */
	private static boolean isTracked(ItemStack stack) {
		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		return stack.is(Items.COMPASS) && tracker != null && tracker.target().isPresent();
	}

	/** A plain compass with no lodestone of its own: the blank the copy goes onto. */
	private static boolean isBlank(ItemStack stack) {
		return stack.is(Items.COMPASS) && !stack.has(DataComponents.LODESTONE_TRACKER);
	}

	/**
	 * The map with the compass's place on it, or empty: the compass points nowhere, it points
	 * into another dimension, the place is off this map's edge, or it is already marked.
	 */
	private static ItemStack marked(ItemStack map, ItemStack compass, Player player, Level level) {
		MapItemSavedData data = MapItem.getSavedData(map, level);
		Optional<GlobalPos> target = CompassTarget.resolve(player, compass);
		if (data == null || target.isEmpty()) return ItemStack.EMPTY;

		GlobalPos place = target.get();
		int x = place.pos().getX();
		int z = place.pos().getZ();
		if (!place.dimension().equals(data.dimension) || !covers(data, x, z)) return ItemStack.EMPTY;

		String key = Main.MOD_ID + ":" + x + "," + z;
		MapDecorations marks = map.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
		if (marks.decorations().containsKey(key)) return ItemStack.EMPTY;

		ItemStack out = map.copyWithCount(1);
		// 180 is the way up an explorer map's X is drawn; the icon is symmetric, the number is
		// vanilla's, and matching it keeps two kinds of X from reading as two kinds of thing
		out.set(DataComponents.MAP_DECORATIONS,
			marks.withDecoration(key, new MapDecorations.Entry(MARK, x, z, 180.0F)));
		return out;
	}

	/** A map at scale s is 128 << s blocks across, centred on its centre. */
	private static boolean covers(MapItemSavedData data, int x, int z) {
		int half = 64 << data.scale;
		return Math.abs(x - data.centerX) <= half && Math.abs(z - data.centerZ) <= half;
	}
}
