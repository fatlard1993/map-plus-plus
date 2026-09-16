package justfatlard.map_plus_plus;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import justfatlard.map_plus_plus.mixin.ChunkMapAccessor;
import justfatlard.pandorical.api.MapTerrain;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntFunction;

/**
 * The ground under each pixel of a map, block by block, for drawing it as a relief.
 *
 * <p>A map keeps colours and nothing else, so the ground comes from the world, a column per
 * pixel: every block the map would see, from a few under the lowest ground on the map up to the
 * top of that column. Blocks the map looks through, glass and air, are left out as it leaves
 * them out. A pixel standing for more than one block takes the column at its middle, and a
 * block of its stack stands for as many blocks of height as the pixel is wide.
 *
 * <p>It is read in two passes, surfaces then blocks, because where the floor goes depends on the
 * lowest ground on the whole map. Chunks in memory are read a few at a time each tick; the rest
 * come straight from their region files, parsed off the server thread, so a map of somewhere far
 * away stands up without loading any of it.
 *
 * <p>After the first time only pixels whose colour has changed are read again, which is how the
 * relief keeps up as the map is explored, unless the floor itself has to move.
 */
final class MapGround {
	/**
	 * Blocks of ground under the lowest point, so the thinnest part of the relief is still a
	 * slab and not a sheet of paper.
	 */
	static final int FLOOR = 4;

	private static final int SIDE = MapTerrain.SIDE;
	/** Region-file reads at once, so a big map cannot crowd out the chunks players are waiting for. */
	private static final int READS_IN_FLIGHT = 32;
	/** Columns read from chunks in memory each tick, so standing a map up never costs a tick much. */
	private static final int COLUMNS_PER_TICK = 1024;
	private static final int FORGET_AFTER_TICKS = 20 * 60 * 5;
	private static final int UNKNOWN = Integer.MIN_VALUE;

	private static final Map<Integer, MapGround> known = new HashMap<>();

	private final MinecraftServer server;
	private final ResourceKey<Level> dimension;
	private final int centerX, centerZ, scale;
	/** The highest block in each pixel's column, or {@link #UNKNOWN}. */
	private final int[] surface = new int[SIDE * SIDE];
	private final Column[] columns = new Column[SIDE * SIDE];
	/** The map's colours when its ground was last read: which pixels there are, and which have changed. */
	private byte[] seen;
	/** Where the stacks stand from, in blocks; {@link #UNKNOWN} before they have been read. */
	private int floor = UNKNOWN;
	private MapTerrain terrain;
	/** Terrains put together so far; one finishing after a later one started is stale and dropped. */
	private int assembled;
	private Pass pass;
	private int lastAsked;

	/** One pixel's stack, bottom up, as runs, and the biome at its top. */
	private record Column(BlockState[] blocks, int[] counts, Identifier biome) {}

	private MapGround(MinecraftServer server, MapItemSavedData data) {
		this.server = server;
		this.dimension = data.dimension;
		this.centerX = data.centerX;
		this.centerZ = data.centerZ;
		this.scale = data.scale;
	}

	/**
	 * The ground to draw this map with, or null while it is first being read. The same terrain
	 * comes back until the ground changes, so a caller can tell a new one by identity.
	 */
	static MapTerrain reliefFor(MinecraftServer server, MapId id, MapItemSavedData data) {
		MapGround ground = known.computeIfAbsent(id.id(), unused -> new MapGround(server, data));
		ground.lastAsked = server.getTickCount();
		if (ground.pass == null) ground.reread(data.colors);
		return ground.terrain;
	}

	/** Where a map's stacks stand from, in blocks, and how many blocks each block of a stack stands for. */
	record Footing(int floor, int width) {}

	/** Where the map's ground stands from, or null until it has been read. */
	static Footing footingOf(MapId id) {
		MapGround ground = known.get(id.id());
		return ground == null || ground.floor == UNKNOWN ? null : new Footing(ground.floor, ground.width());
	}

	/** Carry every read under way a tick further. */
	static void tick() {
		for (MapGround ground : List.copyOf(known.values())) {
			if (ground.pass != null) ground.pass.pump();
		}
	}

	/** Drop the ground of maps nobody has asked about in a while. */
	static void forgetUnasked(int now) {
		known.values().removeIf(ground -> ground.pass == null && now - ground.lastAsked > FORGET_AFTER_TICKS);
	}

	static void forgetAll() {
		known.clear();
	}

	private static boolean drawn(byte colour) {
		return (colour & 0xFF) >> 2 != 0;
	}

	private int width() {
		return 1 << scale;
	}

	/** The column that stands for a pixel: the one at the middle of the blocks it covers. */
	private int columnX(int pixel) {
		return (centerX / width() - SIDE / 2) * width() + (pixel % SIDE) * width() + width() / 2;
	}

	private int columnZ(int pixel) {
		return (centerZ / width() - SIDE / 2) * width() + (pixel / SIDE) * width() + width() / 2;
	}

	private void reread(byte[] colours) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) return;
		byte[] now = colours.clone();
		boolean[] redo = new boolean[SIDE * SIDE];
		boolean any = false;
		for (int i = 0; i < redo.length; i++) {
			redo[i] = drawn(now[i]) && (seen == null || seen[i] != now[i]);
			any |= redo[i];
		}
		seen = now;
		if (!any) {
			if (terrain == null) assemble();
			return;
		}
		// Vanilla paints a map drawn under a roof as noise, not ground, so there is no ground to stand up
		if (level.dimensionType().hasCeiling()) {
			floor = 0;
			for (int i = 0; i < redo.length; i++) {
				if (!redo[i]) continue;
				surface[i] = FLOOR - 1;
				columns[i] = new Column(new BlockState[] {Blocks.NETHERRACK.defaultBlockState()}, new int[] {FLOOR}, null);
			}
			assemble();
			return;
		}
		pass = new Surfaces(level, redo);
	}

	/**
	 * Put the columns together as a terrain, off the server thread: for a rugged map that is a
	 * hundred thousand runs, more than a tick should carry.
	 */
	private void assemble() {
		Column[] now = columns.clone();
		byte[] drawnNow = seen;
		int serial = ++assembled;
		CompletableFuture.supplyAsync(() -> build(now, drawnNow), Util.backgroundExecutor())
			.thenAccept(built -> server.execute(() -> {
				if (serial == assembled) terrain = built;
			}));
	}

	private static MapTerrain build(Column[] columns, byte[] seen) {
		MapTerrain.Builder builder = MapTerrain.builder();
		for (int i = 0; i < SIDE * SIDE; i++) {
			Column column = columns[i];
			if (column == null || !drawn(seen[i])) continue;
			int x = i % SIDE, y = i / SIDE;
			for (int run = 0; run < column.blocks().length; run++) builder.stack(x, y, column.blocks()[run], column.counts()[run]);
			if (column.biome() != null) builder.biome(x, y, column.biome());
		}
		return builder.build();
	}

	/**
	 * One sweep over the chunks under some pixels: those in memory read a tick's worth at a time
	 * on the server thread, the rest read from disk and prepared off it, then handed back.
	 */
	private abstract class Pass {
		final ServerLevel level;
		final boolean[] redo;
		private final LongArrayFIFOQueue inMemory = new LongArrayFIFOQueue();
		private final LongArrayFIFOQueue onDisk = new LongArrayFIFOQueue();
		private final Long2ObjectOpenHashMap<IntArrayList> pixelsIn = new Long2ObjectOpenHashMap<>();
		private final ConcurrentLinkedQueue<Runnable> arrived = new ConcurrentLinkedQueue<>();
		private int reading;

		Pass(ServerLevel level, boolean[] redo) {
			this.level = level;
			this.redo = redo;
			LongLinkedOpenHashSet chunks = new LongLinkedOpenHashSet();
			for (int i = 0; i < redo.length; i++) {
				if (!redo[i]) continue;
				long chunk = ChunkPos.pack(columnX(i) >> 4, columnZ(i) >> 4);
				chunks.add(chunk);
				pixelsIn.computeIfAbsent(chunk, c -> new IntArrayList()).add(i);
			}
			for (long chunk : chunks) {
				if (held(chunk) != null) inMemory.enqueue(chunk);
				else onDisk.enqueue(chunk);
			}
		}

		/**
		 * A finished chunk in memory, at any ticket level or waiting to unload. One out of every
		 * player's reach is gone from {@code getChunkNow} long before it is saved, and until then
		 * the disk has nothing for it, or an older copy.
		 */
		private ChunkAccess held(long chunk) {
			ChunkMap chunks = level.getChunkSource().chunkMap;
			ChunkHolder holder = chunks.getUpdatingChunkIfPresent(chunk);
			if (holder == null) holder = ((ChunkMapAccessor) chunks).mapPlusPlus$pendingUnloads().get(chunk);
			ChunkAccess latest = holder == null ? null : holder.getLatestChunk();
			return latest != null && latest.getPersistedStatus() == ChunkStatus.FULL ? latest : null;
		}

		void pump() {
			for (Runnable done; (done = arrived.poll()) != null; ) done.run();
			int columns = 0;
			while (columns < COLUMNS_PER_TICK && !inMemory.isEmpty()) {
				long chunk = inMemory.dequeueLong();
				ChunkAccess held = held(chunk);
				IntArrayList pixels = pixelsIn.get(chunk);
				// Unloaded since it was counted: the disk has it now
				if (held == null) onDisk.enqueue(chunk);
				else for (int k = 0; k < pixels.size(); k++) fromMemory(pixels.getInt(k), held);
				columns += pixels.size();
			}
			while (reading < READS_IN_FLIGHT && !onDisk.isEmpty()) {
				long chunk = onDisk.dequeueLong();
				reading++;
				IntArrayList pixels = pixelsIn.get(chunk);
				level.getChunkSource().chunkMap.read(ChunkPos.unpack(chunk))
					.thenApplyAsync(tag -> tag.map(this::upgrade).map(this::prepare), Util.backgroundExecutor())
					.whenComplete((prepared, failure) -> arrived.add(() -> {
						reading--;
						if (failure == null && prepared.isPresent()) for (int k = 0; k < pixels.size(); k++) fromDisk(pixels.getInt(k), prepared.get());
					}));
			}
			if (reading == 0 && inMemory.isEmpty() && onDisk.isEmpty() && arrived.isEmpty()) {
				pass = null;
				finish();
			}
		}

		/** A chunk saved by an older version, brought up to this one's shape before it is read. */
		private CompoundTag upgrade(CompoundTag tag) {
			ChunkMap chunks = level.getChunkSource().chunkMap;
			return chunks.upgradeChunkTag(tag, -1,
				ChunkMap.getChunkDataFixContextTag(level.dimension(), level.getChunkSource().getGenerator().getTypeNameForDataFixer()),
				SharedConstants.getCurrentVersion().dataVersion().version());
		}

		/** Off the server thread: turn a chunk's saved form into what {@link #fromDisk} reads. */
		abstract Object prepare(CompoundTag tag);

		abstract void fromMemory(int pixel, ChunkAccess chunk);

		abstract void fromDisk(int pixel, Object prepared);

		abstract void finish();
	}

	/** Where the top of each column is, which is what the floor waits on. */
	private final class Surfaces extends Pass {
		Surfaces(ServerLevel level, boolean[] redo) {
			super(level, redo);
			for (int i = 0; i < redo.length; i++) if (redo[i]) surface[i] = UNKNOWN;
		}

		@Override
		Object prepare(CompoundTag tag) {
			Optional<long[]> packed = tag.getCompound("Heightmaps")
				.flatMap(h -> h.getLongArray(Heightmap.Types.WORLD_SURFACE.getSerializationKey()));
			if (packed.isEmpty()) return null;
			try {
				return new SimpleBitStorage(Mth.ceillog2(level.getHeight() + 1), 256, packed.get());
			} catch (SimpleBitStorage.InitializationException e) {
				return null;
			}
		}

		@Override
		void fromMemory(int pixel, ChunkAccess chunk) {
			surface[pixel] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, columnX(pixel), columnZ(pixel));
		}

		@Override
		void fromDisk(int pixel, Object prepared) {
			if (!(prepared instanceof SimpleBitStorage heights)) return;
			surface[pixel] = heights.get((columnX(pixel) & 15) + (columnZ(pixel) & 15) * 16) + level.getMinY() - 1;
		}

		@Override
		void finish() {
			int lowest = Integer.MAX_VALUE;
			for (int i = 0; i < surface.length; i++) {
				if (drawn(seen[i]) && surface[i] != UNKNOWN) lowest = Math.min(lowest, surface[i]);
			}
			if (lowest == Integer.MAX_VALUE) {
				assemble();
				return;
			}
			int wanted = lowest - (FLOOR - 1) * width();
			boolean[] stacks = redo;
			// New lower ground moves the floor down, and every stack has to reach it
			if (wanted != floor) {
				floor = wanted;
				stacks = new boolean[SIDE * SIDE];
				for (int i = 0; i < stacks.length; i++) stacks[i] = drawn(seen[i]);
			}
			pass = new Stacks(level, stacks);
		}
	}

	/** Each column's blocks, from the floor to its top. */
	private final class Stacks extends Pass {
		private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

		Stacks(ServerLevel level, boolean[] redo) {
			super(level, redo);
		}

		@Override
		Object prepare(CompoundTag tag) {
			SerializableChunkData data = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
			Map<Integer, LevelChunkSection> sections = new HashMap<>();
			for (SerializableChunkData.SectionData section : data.sectionData()) {
				if (section.chunkSection() != null) sections.put(section.y(), section.chunkSection());
			}
			return sections;
		}

		@Override
		void fromMemory(int pixel, ChunkAccess chunk) {
			stack(pixel, y -> chunk.getBlockState(at.set(columnX(pixel), y, columnZ(pixel))),
				y -> chunk.getNoiseBiome(QuartPos.fromBlock(columnX(pixel)), QuartPos.fromBlock(y), QuartPos.fromBlock(columnZ(pixel))));
		}

		@Override
		@SuppressWarnings("unchecked")
		void fromDisk(int pixel, Object prepared) {
			Map<Integer, LevelChunkSection> sections = (Map<Integer, LevelChunkSection>) prepared;
			int x = columnX(pixel) & 15, z = columnZ(pixel) & 15;
			stack(pixel, y -> {
				LevelChunkSection section = sections.get(y >> 4);
				return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(x, y & 15, z);
			}, y -> {
				LevelChunkSection section = sections.get(y >> 4);
				return section == null ? null : section.getNoiseBiome(x >> 2, (y & 15) >> 2, z >> 2);
			});
		}

		/**
		 * A pixel's stack from the floor to its top: each block of it the highest block the map
		 * would see in the stretch of column it stands for, or empty where there is none.
		 */
		private void stack(int pixel, IntFunction<BlockState> blockAt, IntFunction<Holder<Biome>> biomeAt) {
			int top = surface[pixel];
			if (top == UNKNOWN || top < floor) {
				// Ground that could not be read sits on the floor rather than leaving a hole
				columns[pixel] = new Column(new BlockState[] {Blocks.STONE.defaultBlockState()}, new int[] {FLOOR}, null);
				return;
			}
			int width = width();
			List<BlockState> blocks = new ArrayList<>();
			List<Integer> counts = new ArrayList<>();
			for (int from = floor; from <= top; from += width) {
				BlockState seenHere = null;
				for (int y = Math.min(from + width - 1, top); y >= from; y--) {
					BlockState block = blockAt.apply(y);
					if (stands(block, at.set(columnX(pixel), y, columnZ(pixel)))) {
						seenHere = block;
						break;
					}
				}
				int last = blocks.size() - 1;
				if (last >= 0 && blocks.get(last) == seenHere) counts.set(last, counts.get(last) + 1);
				else {
					blocks.add(seenHere);
					counts.add(1);
				}
			}
			Holder<Biome> biome = biomeAt.apply(top);
			columns[pixel] = new Column(blocks.toArray(BlockState[]::new), counts.stream().mapToInt(Integer::intValue).toArray(),
				biome == null ? null : biome.unwrapKey().map(ResourceKey::identifier).orElse(null));
		}

		/**
		 * Whether a block stands in the relief: one the map would see, less the grass and ferns
		 * that carpet the ground, which at this size are a speckle over every field rather than
		 * anything the eye can pick out. Flowers and crops are kept; they read as colour.
		 */
		private boolean stands(BlockState block, BlockPos pos) {
			if (block.isAir() || block.getMapColor(level, pos) == MapColor.NONE) return false;
			return !block.canBeReplaced() || !block.getFluidState().isEmpty() || block.is(Blocks.SNOW);
		}

		@Override
		void finish() {
			assemble();
		}
	}
}
