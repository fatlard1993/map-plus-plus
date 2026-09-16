package justfatlard.map_plus_plus;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps framed side by side on the floor are one table, and redstone turns the whole table:
 * power reaching any part of it stands every map up, and the power going lays them all flat.
 *
 * <p>The table turns as a wave from where the power comes in. The map the switch reaches goes
 * first and each map a step further along the table follows a beat after, so where the switch is
 * decides how the table comes alive. A wave to stand the table up waits for the ground under
 * every map on it to be read first, so a map read late does not break the wave.
 *
 * <p>A frame counts as powered when something redstone feeds the block it rests on, a lever or
 * button on the table's side or dust leading into it, or reaches the space it fills, a button on
 * the tabletop beside it. Like a door, the table answers to the power changing rather than to it
 * being on, so between one throw of the switch and the next each map can still be turned by hand.
 *
 * <p>The maps on a table stand from one floor, the lowest any of them needs, so the land on one
 * meets the land on the next at the same height.
 *
 * <p>Frames are not blocks and hear nothing of redstone, so they are looked at every couple of
 * ticks. What each saw last is kept as a tag on it, so a restart is not mistaken for the switch
 * being thrown.
 */
final class MapTable {
	private MapTable() {}

	private static final String POWERED = Main.MOD_ID + ":powered";
	private static final int EVERY_TICKS = 2;
	/** Ticks between one map turning and the next one along. */
	private static final int STEP_TICKS = 3;
	/** Longest a wave waits on ground still being read before it goes anyway. */
	private static final int MOST_WAIT_TICKS = 100;
	private static final Direction[] ACROSS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** Every loaded frame lying on a floor, whatever it holds: an empty frame is still part of a table. */
	private static final Set<ItemFrame> onFloors = Collections.newSetFromMap(new IdentityHashMap<>());
	/** The floor each frame's table stands from, in blocks, once any map on it has been read. */
	private static final Map<ItemFrame, Integer> tableFloors = new IdentityHashMap<>();
	/** Where the power last came into each frame's table, for the wave to start from when it goes. */
	private static final Map<ItemFrame, Set<ItemFrame>> cameInAt = new IdentityHashMap<>();
	private static final List<Wave> waves = new ArrayList<>();
	/** The wave each frame is waiting on; a later throw of the switch takes it over from an earlier one. */
	private static final Map<ItemFrame, Wave> waitingOn = new IdentityHashMap<>();

	/** Frames to turn, each so many steps from where the power came in. */
	private static final class Wave {
		final List<ItemFrame> frames;
		final int[] steps;
		final boolean on;
		final int thrown;
		int started = -1;

		Wave(List<ItemFrame> frames, int[] steps, boolean on, int thrown) {
			this.frames = frames;
			this.steps = steps;
			this.on = on;
			this.thrown = thrown;
		}

		boolean waited(ItemFrame frame) {
			return waitingOn.get(frame) == this;
		}
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemFrame frame && frame.getDirection() == Direction.UP) onFloors.add(frame);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemFrame frame) {
				onFloors.remove(frame);
				tableFloors.remove(frame);
				cameInAt.remove(frame);
				waitingOn.remove(frame);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			onFloors.clear();
			tableFloors.clear();
			cameInAt.clear();
			waves.clear();
			waitingOn.clear();
		});
	}

	static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		roll(now);
		if (now % EVERY_TICKS != 0 || onFloors.isEmpty()) return;
		Map<ServerLevel, Map<BlockPos, ItemFrame>> byLevel = new IdentityHashMap<>();
		for (ItemFrame frame : onFloors) {
			if (frame.isRemoved() || !(frame.level() instanceof ServerLevel level)) continue;
			// Its neighbours are sure to be loaded, so asking after power never loads a chunk
			if (!level.isPositionEntityTicking(frame.getPos())) continue;
			byLevel.computeIfAbsent(level, l -> new HashMap<>()).put(frame.getPos(), frame);
		}
		byLevel.forEach((level, frames) -> {
			Set<BlockPos> done = new HashSet<>();
			for (BlockPos start : frames.keySet()) {
				if (done.add(start)) answer(level, table(frames, start, done), now);
			}
		});
	}

	/** Carry each wave on: start it once its ground is in, then turn each map as its step comes. */
	private static void roll(int now) {
		for (Wave wave : List.copyOf(waves)) {
			if (wave.started < 0) {
				boolean ready = true;
				if (wave.on) {
					for (ItemFrame frame : wave.frames) {
						if (wave.waited(frame) && !frame.isRemoved() && !MapRelief.ready(frame)) ready = false;
					}
				}
				if (!ready && now - wave.thrown < MOST_WAIT_TICKS) continue;
				wave.started = now;
				// The ground has only just come in, so the floor it all stands from is worked out afresh
				if (wave.on) standTogether(wave.frames);
			}
			boolean left = false;
			for (int i = 0; i < wave.frames.size(); i++) {
				ItemFrame frame = wave.frames.get(i);
				if (!wave.waited(frame)) continue;
				if (now < wave.started + wave.steps[i] * STEP_TICKS) {
					left = true;
					continue;
				}
				waitingOn.remove(frame);
				if (frame.isRemoved() || MapRelief.isOn(frame) == wave.on) continue;
				MapRelief.set(frame, wave.on);
				if (frame.getFramedMapId(frame.getItem()) != null) {
					// The note climbs as the wave goes out and falls as it comes back
					float pitch = wave.on ? 0.9F + 0.08F * wave.steps[i] : 1.1F - 0.05F * wave.steps[i];
					MapRelief.announce(frame, Math.clamp(pitch, 0.5F, 1.6F));
				}
			}
			if (!left) waves.remove(wave);
		}
	}

	/** The frames joined to one by edges, at the same height. */
	private static List<ItemFrame> table(Map<BlockPos, ItemFrame> frames, BlockPos start, Set<BlockPos> done) {
		List<ItemFrame> table = new ArrayList<>();
		ArrayDeque<BlockPos> next = new ArrayDeque<>();
		next.add(start);
		while (!next.isEmpty()) {
			BlockPos at = next.poll();
			table.add(frames.get(at));
			for (Direction way : ACROSS) {
				BlockPos beside = at.relative(way);
				if (frames.containsKey(beside) && done.add(beside)) next.add(beside);
			}
		}
		return table;
	}

	/** How many blocks of its own stack deeper a frame's map stands, to meet its table's floor. */
	static int loweringFor(ItemFrame frame) {
		Integer floor = tableFloors.get(frame);
		MapId id = frame.getFramedMapId(frame.getItem());
		MapGround.Footing footing = id == null ? null : MapGround.footingOf(id);
		if (floor == null || footing == null) return 0;
		return Math.max(0, (footing.floor() - floor) / footing.width());
	}

	/** Stand a table's maps from the lowest floor any of them needs. */
	private static void standTogether(List<ItemFrame> table) {
		int floor = Integer.MAX_VALUE;
		for (ItemFrame frame : table) {
			MapId id = frame.getFramedMapId(frame.getItem());
			MapGround.Footing footing = id == null ? null : MapGround.footingOf(id);
			if (footing != null) floor = Math.min(floor, footing.floor());
		}
		for (ItemFrame frame : table) {
			if (floor == Integer.MAX_VALUE) tableFloors.remove(frame);
			else tableFloors.put(frame, floor);
		}
	}

	private static void answer(ServerLevel level, List<ItemFrame> table, int now) {
		standTogether(table);

		Set<ItemFrame> powered = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ItemFrame frame : table) {
			if (powered(level, frame.getPos())) powered.add(frame);
		}
		boolean on = !powered.isEmpty();
		List<ItemFrame> thrown = new ArrayList<>();
		for (ItemFrame frame : table) {
			if (frame.entityTags().contains(POWERED) == on) continue;
			if (on) frame.addTag(POWERED);
			else frame.removeTag(POWERED);
			thrown.add(frame);
		}
		if (thrown.isEmpty()) return;

		Set<ItemFrame> from = Collections.newSetFromMap(new IdentityHashMap<>());
		if (on) {
			from.addAll(powered);
			for (ItemFrame frame : table) cameInAt.put(frame, powered);
		} else {
			for (ItemFrame frame : table) {
				Set<ItemFrame> last = cameInAt.remove(frame);
				if (last != null) for (ItemFrame source : last) if (table.contains(source)) from.add(source);
			}
			if (from.isEmpty()) from.addAll(thrown);
		}
		Map<ItemFrame, Integer> steps = stepsFrom(table, from);
		int[] each = new int[thrown.size()];
		for (int i = 0; i < each.length; i++) each[i] = steps.getOrDefault(thrown.get(i), 0);
		Wave wave = new Wave(thrown, each, on, now);
		for (ItemFrame frame : thrown) waitingOn.put(frame, wave);
		waves.add(wave);
	}

	/** Steps across the table from the nearest of some frames on it. */
	private static Map<ItemFrame, Integer> stepsFrom(List<ItemFrame> table, Set<ItemFrame> from) {
		Map<BlockPos, ItemFrame> at = new HashMap<>();
		for (ItemFrame frame : table) at.put(frame.getPos(), frame);
		Map<ItemFrame, Integer> steps = new IdentityHashMap<>();
		ArrayDeque<ItemFrame> next = new ArrayDeque<>();
		for (ItemFrame frame : from) {
			steps.put(frame, 0);
			next.add(frame);
		}
		while (!next.isEmpty()) {
			ItemFrame frame = next.poll();
			for (Direction way : ACROSS) {
				ItemFrame beside = at.get(frame.getPos().relative(way));
				if (beside != null && !steps.containsKey(beside)) {
					steps.put(beside, steps.get(frame) + 1);
					next.add(beside);
				}
			}
		}
		return steps;
	}

	private static boolean powered(ServerLevel level, BlockPos frame) {
		return fed(level, frame.below()) || level.hasNeighborSignal(frame);
	}

	/**
	 * Whether redstone feeds a block: a lever, button, dust or the like beside it giving it power.
	 * Not whether it counts as powered, which a block next to a powered block also does, the way a
	 * lamp beside it would light; by that the whole corner of a table would be where the power
	 * came in, and the wave would start three maps wide.
	 */
	private static boolean fed(ServerLevel level, BlockPos block) {
		for (Direction way : Direction.values()) {
			BlockPos beside = block.relative(way);
			BlockState state = level.getBlockState(beside);
			if (!state.isRedstoneConductor(level, beside) && state.getSignal(level, beside, way) > 0) return true;
		}
		return false;
	}
}
