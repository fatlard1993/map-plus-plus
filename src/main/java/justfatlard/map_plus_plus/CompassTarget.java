package justfatlard.map_plus_plus;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * Where a compass points, as a place rather than a bearing.
 *
 * <p>One answer for the three kinds, so the minimap and the cartography table agree on it: a
 * lodestone compass points at its lodestone, a recovery compass at the holder's last death, a
 * plain one at the overworld's spawn. Each comes back with its dimension, because a compass in
 * the wrong dimension spins, and whoever asks decides whether a place in another world is any
 * use to them.
 */
public final class CompassTarget {
	private CompassTarget() {}

	/** Regular, lodestone or recovery: anything the compass slot would take. */
	public static boolean isCompass(ItemStack stack) {
		return stack.getItem() instanceof CompassItem || stack.is(Items.RECOVERY_COMPASS);
	}

	/** Empty when the compass has nothing to point at: a lost lodestone, no death yet. */
	public static Optional<GlobalPos> resolve(Player player, ItemStack compass) {
		if (compass.isEmpty()) return Optional.empty();

		LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
		if (tracker != null) {
			return tracker.target();
		}
		if (compass.is(Items.RECOVERY_COMPASS)) {
			return player.getLastDeathLocation();
		}
		if (compass.getItem() instanceof CompassItem) {
			var server = player.level().getServer();
			if (server == null) return Optional.empty();
			return Optional.of(new GlobalPos(Level.OVERWORLD, server.overworld().getRespawnData().pos()));
		}
		return Optional.empty();
	}
}
