package justfatlard.map_plus_plus;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A map that follows its owner instead of remembering one place.
 *
 * <p>An ordinary map is a picture of where it was made. Walk far enough and you fall off the edge
 * of it, and the only cure is to make another one and carry both. A scroll map re-centres itself
 * on whoever is holding it, so the thing in your hand is always about where you are.
 *
 * <p><b>Re-centred in place, under the same map id.</b> The centre of a map is final, so moving
 * one means building a fresh one - but writing it back under the id the item already carries
 * means the item is untouched, nothing has to be swapped in anybody's inventory, and the world
 * does not accumulate an abandoned map every time somebody walks a few hundred blocks.
 *
 * <p>Re-centring costs the picture: the new one starts blank and fills in as vanilla's own scan
 * catches up around the player. So it happens as rarely as it can while still keeping you on the
 * map - only once you are three quarters of the way to an edge, and then it puts you back in the
 * middle with the whole width to cross before it is needed again.
 */
public final class ScrollMap {
	private ScrollMap() {}

	/**
	 * How far from the centre a player gets before the map moves, as a fraction of the half-width.
	 *
	 * <p>High on purpose. Every re-centre throws the explored picture away, so a map that chased
	 * the player closely would never be anything but a small lit disc travelling over blankness.
	 */
	private static final double DRIFT_ALLOWED = 0.75;

	/** Map pixels from the centre to an edge, before the scale multiplier. */
	private static final int HALF_WIDTH = 64;

	public static void tick(ServerPlayer player, ItemStack mapStack) {
		if (mapStack.isEmpty()) return;
		if (!(player.level() instanceof ServerLevel level)) return;
		if (!isScroll(level, mapStack)) return;

		MapId mapId = mapStack.get(DataComponents.MAP_ID);
		if (mapId == null) return;

		MapItemSavedData data = level.getMapData(mapId);
		if (data == null || data.locked) return;

		// A map made in another dimension is not this one's business: vanilla shows it blank
		// there, and re-centring it would quietly rewrite somebody's nether map as overworld.
		if (!data.dimension.equals(level.dimension())) return;

		int scaleFactor = 1 << data.scale;
		double allowed = HALF_WIDTH * scaleFactor * DRIFT_ALLOWED;
		if (Math.abs(player.getX() - data.centerX) < allowed
			&& Math.abs(player.getZ() - data.centerZ) < allowed) {
			return;
		}

		level.setMapData(mapId, MapItemSavedData.createFresh(
			player.getX(), player.getZ(), data.scale, true, true, level.dimension()));
	}

	/** Whether this map has been taught to follow. */
	private static boolean isScroll(ServerLevel level, ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null || enchantments.isEmpty()) return false;

		Holder<Enchantment> scroll = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(Main.SCROLL)
			.orElse(null);

		return scroll != null && enchantments.getLevel(scroll) > 0;
	}
}
