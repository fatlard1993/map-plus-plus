package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * An explorer map in the map slot brings its X to the client, where the minimap reads it. Whether
 * the minimap then draws it is the screenshot's to show: the marker is drawn by vanilla's map
 * renderer, which leaves nothing to ask afterwards.
 */
public final class SlottedMapMarkers implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			int mapId = server.computeOnServer(s -> {
				var player = connection.getServerPlayer();
				BlockPos here = player.blockPosition();
				// As a cartographer makes one: centred on the place, which is far from the buyer.
				BlockPos place = here.offset(900, 0, -700);
				ItemStack map = MapItem.create(s.overworld(), place.getX(), place.getZ(), (byte) 2, true, true);
				MapItemSavedData.addTargetDecoration(map, place, "+", MapDecorationTypes.RED_X);
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, map);
				// And a compass pointed at a corner of the same map, whose marker is drawn the same way.
				ItemStack compass = new ItemStack(net.minecraft.world.item.Items.COMPASS);
				compass.set(DataComponents.LODESTONE_TRACKER, new net.minecraft.world.item.component.LodestoneTracker(
					java.util.Optional.of(net.minecraft.core.GlobalPos.of(s.overworld().dimension(), place.offset(-150, 0, -150))), false));
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, compass);
				return map.get(DataComponents.MAP_ID).id();
			});
			context.waitTicks(40);

			String onServer = server.computeOnServer(s -> describe(MapItem.getSavedData(new MapId(mapId), s.overworld())));
			String onClient = context.computeOnClient(client -> describe(MapItem.getSavedData(new MapId(mapId), client.level)));
			check(onServer.contains("red_x"), "the server's map has no X: " + onServer);
			check(onClient.contains("red_x"), "the X never reached the client (server has " + onServer + "): " + onClient);
			// For a look at what was drawn: the X and the compass mark, both on the map.
			context.takeScreenshot("map-plus-plus-slotted-x");
		}
	}

	private static String describe(MapItemSavedData data) {
		if (data == null) return "no map data";
		StringBuilder out = new StringBuilder();
		for (MapDecoration decoration : data.getDecorations()) {
			out.append(decoration.type().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"))
				.append('@').append(decoration.x()).append(',').append(decoration.y()).append(' ');
		}
		return out.isEmpty() ? "nothing" : out.toString().trim();
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
