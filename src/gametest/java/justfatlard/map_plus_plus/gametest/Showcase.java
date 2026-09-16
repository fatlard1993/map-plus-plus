package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * The pictures for the readme and the mod page: the minimap in the corner with a map and a compass
 * in their slots, and a framed map standing up as the land it shows.
 *
 * <p>In a generated world rather than the flat one the other tests use: a relief of flat ground is
 * a flat slab, which says nothing about what this does. Run it under xvfb-run; the frames land in
 * build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> {
					settings.setWorldType(settings.getNormalPresetList().get(0));
					settings.setSeed("map plus plus");
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				})
				.create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set noon");

			minimap(context, server, connection);
			relief(context, server, connection);
		}
	}

	/** The minimap as a player sees it: a map in the slot, a compass beside it, the HUD left alone. */
	private void minimap(ClientGameTestContext context, TestServerContext server,
			TestServerConnection connection) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			BlockPos here = player.blockPosition();
			ItemStack map = MapItem.create(s.overworld(), here.getX(), here.getZ(), (byte) 1, true, false);
			PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, map);

			// A compass pointing at somewhere on the map, so the minimap has a target to mark.
			ItemStack compass = new ItemStack(Items.COMPASS);
			compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
				Optional.of(GlobalPos.of(s.overworld().dimension(), here.offset(90, 0, -60))), false));
			PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, compass);
		});
		// The map draws as vanilla scans it, a strip at a time: give it long enough to fill in.
		context.waitTicks(200);
		shoot(context, "minimap");
	}

	/** A map framed on the ground, stood up as the land it shows. */
	private void relief(ClientGameTestContext context, TestServerContext server,
			TestServerConnection connection) {
		// No hand, no hotbar: the relief is the whole picture.
		context.getInput().pressKey(options -> options.keyToggleGui);

		// The ground comes off the chunks as they are stored, so a world this young has to be told
		// to write them before there is anything to read.
		server.runCommand("save-all flush");

		BlockPos plinth = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = connection.getServerPlayer();
			BlockPos here = player.blockPosition();
			// A flat stone floor for the frame, and the grass around it cut: waist-high grass in
			// front of the camera hides the very thing the picture is of.
			BlockPos floor = here.offset(3, 0, 0);
			for (int x = -6; x <= 6; x++) {
				for (int z = -6; z <= 6; z++) {
					for (int y = 0; y <= 3; y++) {
						level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
					if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
						level.setBlockAndUpdate(floor.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
					}
				}
			}

			// Held first, and framed after: vanilla colours a map in while a player carries it, and
			// an all-white map has no ground for the relief to stand up.
			ItemStack map = MapItem.create(level, floor.getX(), floor.getZ(), (byte) 0, true, false);
			player.setItemInHand(InteractionHand.MAIN_HAND, map);

			return floor;
		});

		// Long enough for the scan to walk across it.
		context.waitTicks(200);

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = connection.getServerPlayer();
			ItemFrame frame = new ItemFrame(level, plinth, Direction.UP);
			frame.setItem(player.getMainHandItem().copy(), false);
			level.addFreshEntity(frame);

			// Sneak-click it with an empty hand, which is what a player does: through the event the
			// mod listens on, rather than a method of its own, so the shot goes the way play does.
			player.setShiftKeyDown(true);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, frame, null);
			player.setShiftKeyDown(false);
		});

		// The ground under the map is read off the chunks in the background, and the frame is only
		// sent its relief on the next sweep: a couple of seconds, not a couple of ticks. The wait
		// also lets vanilla's own scan colour the map in, a strip at a time.
		context.waitTicks(260);
		// Three distances in one run: the relief's size on screen is not something to guess at.
		for (int[] shot : new int[][] {{3, 1, 16}, {4, 1, 20}}) {
			stand(server, plinth.getX() + 0.5, plinth.getY() + shot[1], plinth.getZ() + shot[0], 180, shot[2]);
			context.waitTicks(20);
			shoot(context, "map-relief-" + shot[0]);
		}
	}

	/**
	 * Put the camera here, looking this way. The y is the feet: what the camera sees is 1.62 above.
	 *
	 * <p>Through /tp rather than a server-side move, which the client's own position packets undo
	 * before the shutter opens.
	 */
	private void stand(TestServerContext server, double x, double y, double z, int yaw, int pitch) {
		server.runCommand("tp @a %.2f %.2f %.2f %d %d".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
