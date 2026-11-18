package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;

/**
 * Shift-clicking a compass or a map in the pack puts it in its slot, and shift-clicking it there
 * takes it back out.
 */
public final class ShiftClickSlots implements FabricClientGameTest {
	/** The inventory menu's first hotbar slot: result, grid, armour and pack come before it. */
	private static final int HOTBAR_0 = 36, HOTBAR_1 = 37;
	/** The extra slots follow vanilla's 46, map first. */
	private static final int MAP_SLOT = 46, COMPASS_SLOT = 47;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			server.runOnServer(s -> {
				var player = connection.getServerPlayer();
				player.getInventory().setItem(0, new ItemStack(Items.COMPASS));
				player.getInventory().setItem(1, MapItem.create(s.overworld(), player.getBlockX(), player.getBlockZ(), (byte) 0, true, false));
			});
			context.waitFor(client -> client.player.getInventory().getItem(0).is(Items.COMPASS)
				&& client.player.getInventory().getItem(1).is(Items.FILLED_MAP));

			shiftClick(context, HOTBAR_0);
			shiftClick(context, HOTBAR_1);
			server.waitFor(s -> slot(connection, MapPlusPlusInventory.COMPASS_SLOT).is(Items.COMPASS)
				&& slot(connection, MapPlusPlusInventory.MAP_SLOT).is(Items.FILLED_MAP));
			check(server.computeOnServer(s -> connection.getServerPlayer().getInventory().getItem(0).isEmpty()
				&& connection.getServerPlayer().getInventory().getItem(1).isEmpty()),
				"the compass and map were copied into their slots, not moved");

			// A full slot takes no more: the next compass goes where vanilla would put it
			server.runOnServer(s -> connection.getServerPlayer().getInventory().setItem(0, new ItemStack(Items.COMPASS)));
			context.waitFor(client -> client.player.getInventory().getItem(0).is(Items.COMPASS));
			shiftClick(context, HOTBAR_0);
			server.waitFor(s -> connection.getServerPlayer().getInventory().getItem(0).isEmpty());
			check(server.computeOnServer(s -> slot(connection, MapPlusPlusInventory.COMPASS_SLOT).getCount() == 1),
				"a second compass went into a full compass slot");

			// Out of a stack, an empty slot takes one and the rest stay put
			server.runOnServer(s -> {
				var player = connection.getServerPlayer();
				player.getInventory().clearContent();
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, ItemStack.EMPTY);
				player.getInventory().setItem(0, new ItemStack(Items.COMPASS, 5));
			});
			context.waitFor(client -> client.player.getInventory().getItem(0).getCount() == 5);
			shiftClick(context, HOTBAR_0);
			server.waitFor(s -> slot(connection, MapPlusPlusInventory.COMPASS_SLOT).is(Items.COMPASS));
			check(server.computeOnServer(s -> slot(connection, MapPlusPlusInventory.COMPASS_SLOT).getCount() == 1
				&& connection.getServerPlayer().getInventory().countItem(Items.COMPASS) == 4),
				"shift-clicking a stack did not equip exactly one compass");

			shiftClick(context, COMPASS_SLOT);
			server.waitFor(s -> slot(connection, MapPlusPlusInventory.COMPASS_SLOT).isEmpty());
			check(server.computeOnServer(s -> connection.getServerPlayer().getInventory().countItem(Items.COMPASS) == 5),
				"shift-clicking out of the compass slot lost it");
		}
	}

	private static void shiftClick(ClientGameTestContext context, int menuSlot) {
		context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,
			menuSlot, 0, ContainerInput.QUICK_MOVE, client.player));
		context.waitTicks(2);
	}

	private static ItemStack slot(TestServerConnection connection, int index) {
		return PandoricalApi.playerInventory().getSlot(connection.getServerPlayer(), Main.SLOTS_NAMESPACE, index);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
