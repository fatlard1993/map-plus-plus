package justfatlard.map_plus_plus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.pandorical.api.MapTerrain;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The height each pixel of a map drawn under a roof was sliced at: the holder's head when that
 * pixel last changed. The map's colours alone cannot say where the ground they show stands, and
 * the relief needs to know.
 */
public final class MapSlices extends SavedData {
	private static final int SIDE = MapTerrain.SIDE;
	public static final short NONE = Short.MIN_VALUE;

	/** Two pixels to an int, so a map's heights save as half as many numbers. */
	private record Stored(int map, List<Integer> packed) {
		static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("map").forGetter(Stored::map),
			Codec.INT.listOf().fieldOf("cuts").forGetter(Stored::packed)
		).apply(instance, Stored::new));
	}

	private static final Codec<MapSlices> CODEC = Stored.CODEC.listOf().xmap(MapSlices::fromStored, MapSlices::toStored);

	private static final SavedDataType<MapSlices> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "slices"), MapSlices::new, CODEC, DataFixTypes.LEVEL);

	/** The map being drawn on the server thread right now, which {@code MapItem.update} is not told. */
	private static MapId drawing;

	private final Map<Integer, short[]> cuts = new HashMap<>();

	public static MapSlices get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Draw a map, letting the slice heights its pixels get be kept under its id. */
	public static void drawing(MapId id, Runnable draw) {
		MapId outer = drawing;
		drawing = id;
		try {
			draw.run();
		} finally {
			drawing = outer;
		}
	}

	public static MapId drawing() {
		return drawing;
	}

	/** This map's slice heights, or null if none of it has been drawn under a roof. Not to be changed. */
	short[] of(MapId id) {
		return cuts.get(id.id());
	}

	public boolean unrecorded(MapId id, int x, int y) {
		short[] heights = cuts.get(id.id());
		return heights == null || heights[x + y * SIDE] == NONE;
	}

	public void record(MapId id, int x, int y, int height) {
		short[] heights = cuts.computeIfAbsent(id.id(), unused -> {
			short[] fresh = new short[SIDE * SIDE];
			Arrays.fill(fresh, NONE);
			return fresh;
		});
		short now = (short) height;
		if (heights[x + y * SIDE] == now) return;
		heights[x + y * SIDE] = now;
		setDirty();
	}

	private static MapSlices fromStored(List<Stored> stored) {
		MapSlices slices = new MapSlices();
		for (Stored map : stored) {
			if (map.packed().size() != SIDE * SIDE / 2) continue;
			short[] heights = new short[SIDE * SIDE];
			for (int i = 0; i < heights.length / 2; i++) {
				int pair = map.packed().get(i);
				heights[2 * i] = (short) pair;
				heights[2 * i + 1] = (short) (pair >> 16);
			}
			slices.cuts.put(map.map(), heights);
		}
		return slices;
	}

	private List<Stored> toStored() {
		return cuts.entrySet().stream().map(e -> {
			short[] h = e.getValue();
			List<Integer> packed = IntStream.range(0, h.length / 2)
				.map(i -> (h[2 * i] & 0xFFFF) | (h[2 * i + 1] << 16)).boxed().toList();
			return new Stored(e.getKey(), packed);
		}).toList();
	}
}
