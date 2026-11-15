package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.ReliefProbe;
import justfatlard.pandorical.api.MapTerrain;
import net.minecraft.core.registries.BuiltInRegistries;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A Nether map is drawn as a slice at its holder's height: the floor of the room they stand in,
 * and the rock around it as walls, rather than vanilla's noise. Its relief stands that slice up:
 * the floor at the floor's height, the walls up to the slice.
 */
public final class NetherSlice implements FabricClientGameTest {
	private static final int X = 0, Y = 70, Z = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			// A sealed room: gold floor, three blocks of air, crying obsidian walls and a roof of the same.
			String in = "execute in minecraft:the_nether run ";
			// There first, so the room's chunks are loaded for the fills.
			server.runCommand("gamemode creative @a");
			server.runCommand(in + "tp @a %.1f %d %.1f".formatted(X + 0.5, Y, Z + 0.5));
			context.waitTicks(40);
			server.runCommand(in + "fill %d %d %d %d %d %d minecraft:crying_obsidian".formatted(X - 12, Y - 2, Z - 12, X + 12, Y + 4, Z + 12));
			server.runCommand(in + "fill %d %d %d %d %d %d minecraft:gold_block".formatted(X - 8, Y - 1, Z - 8, X + 8, Y - 1, Z + 8));
			server.runCommand(in + "fill %d %d %d %d %d %d minecraft:air".formatted(X - 8, Y, Z - 8, X + 8, Y + 2, Z + 8));
			server.runCommand(in + "tp @a %.1f %d %.1f".formatted(X + 0.5, Y, Z + 0.5));
			context.waitTicks(20);

			int mapId = server.computeOnServer(s -> {
				ItemStack map = MapItem.create(s.getLevel(net.minecraft.world.level.Level.NETHER), X, Z, (byte) 0, true, false);
				PandoricalApi.playerInventory().setSlot(connection.getServerPlayer(), Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, map);
				return map.get(DataComponents.MAP_ID).id();
			});
			// Vanilla scans a sixteenth of the columns a tick: give it a few rounds.
			context.waitTicks(80);

			String found = server.computeOnServer(s -> {
				MapItemSavedData data = MapItem.getSavedData(new MapId(mapId), s.getLevel(net.minecraft.world.level.Level.NETHER));
				if (data == null) return "no map data";
				return "floor " + colourAt(data, X + 3, Z + 3) + ", wall " + colourAt(data, X + 10, Z + 3);
			});
			check(found.equals("floor " + Blocks.GOLD_BLOCK.defaultMapColor().id + ", wall " + Blocks.CRYING_OBSIDIAN.defaultMapColor().id),
				"expected gold floor and crying obsidian wall, got " + found);

			server.waitFor(s -> {
				MapItemSavedData data = MapItem.getSavedData(new MapId(mapId), s.getLevel(net.minecraft.world.level.Level.NETHER));
				return ReliefProbe.reliefFor(s, new MapId(mapId), data) != null;
			});
			String relief = server.computeOnServer(s -> {
				MapItemSavedData data = MapItem.getSavedData(new MapId(mapId), s.getLevel(net.minecraft.world.level.Level.NETHER));
				MapTerrain terrain = ReliefProbe.reliefFor(s, new MapId(mapId), data);
				return "floor " + column(terrain, pixel(data, X + 3, Z + 3)) + ", wall " + column(terrain, pixel(data, X + 10, Z + 3));
			});
			// Walls stand to the slice at head height, two blocks over the floor the player is on
			check(relief.matches("floor (\\d+) gold_block, wall (\\d+) crying_obsidian")
					&& wallOverFloor(relief) == 2,
				"expected a gold floor two below crying obsidian walls, got " + relief);
			context.takeScreenshot("map-plus-plus-nether-slice");
		}
	}

	private static int pixel(MapItemSavedData data, int x, int z) {
		return (x - data.centerX + 64) + (z - data.centerZ + 64) * 128;
	}

	private static int colourAt(MapItemSavedData data, int x, int z) {
		return (data.colors[pixel(data, x, z)] & 0xFF) >> 2;
	}

	/** A column as its height and the block on top. */
	private static String column(MapTerrain terrain, int column) {
		int height = 0;
		String top = "nothing";
		for (int run = terrain.firstRun(column); run < terrain.firstRun(column + 1); run++) {
			height += terrain.runLength(run);
			var block = terrain.blocks().get(terrain.runBlock(run));
			if (block != null) top = BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath();
		}
		return height + " " + top;
	}

	private static int wallOverFloor(String relief) {
		String[] words = relief.split("[ ,]+");
		return Integer.parseInt(words[4]) - Integer.parseInt(words[1]);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
