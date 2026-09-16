package justfatlard.map_plus_plus;

import justfatlard.pandorical.api.MapTerrain;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map framed on the floor, sneak-clicked with an empty hand, stands up as terrain; clicked
 * again, it lies flat. Redstone does the same for a whole table of them: see {@link MapTable}.
 *
 * <p>The choice belongs to the frame, not the map, and is kept as a tag on it, so it survives a
 * restart and a frame swapped to another map shows that one in relief too. Only players with
 * Pandorical see the relief; everyone else sees the map as it always was.
 */
public final class MapRelief {
	private MapRelief() {}

	private static final String TAG = Main.MOD_ID + ":relief";
	private static final int CHECK_EVERY_TICKS = 20;

	/** Every loaded frame set to relief, and what it was last sent, or null for nothing. */
	private static final Map<ItemFrame, Sent> frames = new IdentityHashMap<>();

	/** The map's own ground, and how much deeper it was stood to meet the rest of its table. */
	private record Sent(MapTerrain ground, int lowered) {}

	/** Frames just turned on, whose ground rises when it is first sent rather than simply appearing. */
	private static final Set<ItemFrame> rising = Collections.newSetFromMap(new IdentityHashMap<>());

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
			if (!(entity instanceof ItemFrame frame) || frame.getDirection() != Direction.UP) return InteractionResult.PASS;
			if (!player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
			if (frame.getFramedMapId(frame.getItem()) == null) return InteractionResult.PASS;

			set(frame, !isOn(frame));
			announce(frame, 1.0F);
			// Anything but PASS also stops vanilla turning the map a notch
			return InteractionResult.SUCCESS;
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemFrame frame && frame.getDirection() == Direction.UP && isOn(frame)) {
				frames.put(frame, null);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemFrame frame) {
				frames.remove(frame);
				rising.remove(frame);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MapGround.tick();
			MapTable.tick(server);
			if (server.getTickCount() % CHECK_EVERY_TICKS != 0) return;
			frames.keySet().forEach(MapRelief::refresh);
			MapGround.forgetUnasked(server.getTickCount());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			frames.clear();
			rising.clear();
			MapGround.forgetAll();
		});
		MapTable.register();
	}

	static boolean isOn(ItemFrame frame) {
		return frame.entityTags().contains(TAG);
	}

	/** Stand the frame's map up or lay it flat, as something to watch. Nothing happens if it is that way already. */
	static void set(ItemFrame frame, boolean on) {
		if (on == isOn(frame)) return;
		if (on) {
			frame.addTag(TAG);
			frames.put(frame, null);
			rising.add(frame);
			refresh(frame);
		} else {
			frame.removeTag(TAG);
			rising.remove(frame);
			if (frames.remove(frame) != null) PandoricalApi.mapReliefs().lower(frame);
		}
	}

	/**
	 * Whether the ground under the frame's map is ready to send, starting it being read if not.
	 * A frame with no map is ready; there is nothing to wait for.
	 */
	static boolean ready(ItemFrame frame) {
		return frame.getFramedMapId(frame.getItem()) == null || groundFor(frame) != null;
	}

	/** The sound of a map being turned over. */
	static void announce(ItemFrame frame, float pitch) {
		frame.level().playSound(null, frame.getX(), frame.getY(), frame.getZ(),
			SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.5F, pitch);
	}

	/**
	 * Send the frame the relief for whatever map it holds now, if that has changed: a map taken
	 * out, swapped for another, explored further since, or its table's floor gone deeper.
	 */
	private static void refresh(ItemFrame frame) {
		MapTerrain ground = groundFor(frame);
		int lowered = ground == null ? 0 : MapTable.loweringFor(frame);
		Sent last = frames.get(frame);
		if (last == null ? ground == null : last.ground() == ground && last.lowered() == lowered) return;
		frames.put(frame, ground == null ? null : new Sent(ground, lowered));
		if (ground == null) PandoricalApi.mapReliefs().clear(frame);
		else if (rising.remove(frame)) PandoricalApi.mapReliefs().raise(frame, ground.lowered(lowered));
		else PandoricalApi.mapReliefs().show(frame, ground.lowered(lowered));
	}

	private static MapTerrain groundFor(ItemFrame frame) {
		MinecraftServer server = frame.level().getServer();
		MapId id = frame.getFramedMapId(frame.getItem());
		if (server == null || id == null) return null;
		MapItemSavedData data = frame.level().getMapData(id);
		return data == null ? null : MapGround.reliefFor(server, id, data);
	}
}
