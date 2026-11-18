package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.hud.HudOverlay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A Block Magnet compass draws the radar, even before it has taken anything up, and keeps drawing
 * it when a map joins it in the corner, moved out of the map's way rather than hidden under it.
 * And the enchanting table offers both compass enchantments.
 */
public final class RadarBesideMap implements FabricClientGameTest {
	private static final String MINIMAP = "map-plus-plus:minimap";
	private static final String RADAR = "map-plus-plus:radar";
	private static final String NEEDLE = "map-plus-plus:needle";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			// Mob Sight costs little and comes up on the lower rows; Block Magnet only on the top one
			String low = offeredAt(server, Items.COMPASS, 15), top = offeredAt(server, Items.COMPASS, 30);
			check(low.contains("map-plus-plus:mob_sight"), "the table at level 15 does not offer Mob Sight, only: " + low);
			check(top.contains("map-plus-plus:block_magnet"), "the table at level 30 does not offer Block Magnet, only: " + top);
			String onMap = offeredAt(server, Items.FILLED_MAP, 20);
			check(onMap.contains("map-plus-plus:scroll"), "the table at level 20 does not offer Scroll for a map, only: " + onMap);

			int[] geometry = server.computeOnServer(s -> {
				var player = connection.getServerPlayer();
				ItemStack compass = new ItemStack(Items.COMPASS);
				compass.enchant(s.overworld().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Main.BLOCK_MAGNET), 1);
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, compass);
				return new int[] {
					justfatlard.map_plus_plus.MinimapPrefs.padding(player),
					justfatlard.map_plus_plus.MinimapPrefs.size(player)};
			});
			int padding = geometry[0], size = geometry[1];

			context.waitFor(client -> overlay(RADAR) != null);
			check(overlay(NEEDLE) == null, "an unused magnet drew the plain needle as well as the radar");
			check(overlay(RADAR).offsetY == padding, "alone, the radar is not in the corner: " + overlay(RADAR).offsetY);

			server.runOnServer(s -> {
				var player = connection.getServerPlayer();
				ItemStack map = MapItem.create(s.overworld(), player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, map);
			});
			context.waitFor(client -> overlay(MINIMAP) != null && overlay(RADAR) != null && overlay(RADAR).offsetY > padding);
			int stacked = context.computeOnClient(client -> overlay(RADAR).offsetY);
			check(stacked >= padding + size, "the radar is still under the map: offset " + stacked + ", map " + size);
			check(overlay(RADAR).offsetX == padding, "stacked, the radar moved sideways: " + overlay(RADAR).offsetX);

			server.runOnServer(s -> PandoricalApi.playerInventory().setSlot(connection.getServerPlayer(),
				Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, ItemStack.EMPTY));
			context.waitFor(client -> overlay(MINIMAP) == null && overlay(RADAR) != null && overlay(RADAR).offsetY == padding);
		}
	}

	private static String offeredAt(TestServerContext server, net.minecraft.world.item.Item item, int power) {
		return server.computeOnServer(s -> {
			ItemStack compass = new ItemStack(item);
			check(compass.isEnchantable(), item + " is not enchantable, so the table will not take it");
			var table = s.overworld().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE);
			List<EnchantmentInstance> results = EnchantmentHelper.getAvailableEnchantmentResults(power, compass,
				StreamSupport.stream(table.spliterator(), false));
			return results.stream().map(r -> r.enchantment().unwrapKey().map(k -> k.identifier().toString()).orElse("?"))
				.collect(Collectors.joining(","));
		});
	}

	private static HudOverlay overlay(String id) {
		return HudManager.getActiveOverlays().get(id);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
