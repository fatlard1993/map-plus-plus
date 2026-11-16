package justfatlard.map_plus_plus.gametest;

import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.MapEquipHandler;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.client.component.MapComponent;
import justfatlard.pandorical.client.hud.HudManager;
import justfatlard.pandorical.client.hud.HudOverlay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * A mob walking across a Mob Sight map glides there on the minimap, rather than hopping from one
 * spot to the next, and comes to rest where it stopped.
 */
public final class MobSightGlide implements FabricClientGameTest {
	private static final String OVERLAY = "map-plus-plus:minimap";
	private static final String MAP = "minimap";
	private static final String TAG = "mob_sight_glide";
	private static final int WALK_TICKS = 40;
	private static final double STEP = 0.25;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			check(server.computeOnServer(s -> PandoricalApi.hasCapability(connection.getServerPlayer(), Capabilities.DECLARED_MOTION)),
				"the client does not declare declared_motion");

			int mapId = server.computeOnServer(s -> {
				var player = connection.getServerPlayer();
				ServerLevel level = s.overworld();
				ItemStack map = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.MAP_SLOT, map);
				ItemStack compass = new ItemStack(Items.COMPASS);
				compass.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Main.MOB_SIGHT), 1);
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, compass);
				return map.get(DataComponents.MAP_ID).id();
			});
			// Near the player, and on the map the whole walk: it is centred on a grid, not on them.
			String at = server.computeOnServer(s -> {
				var player = connection.getServerPlayer();
				double x = player.getX() + (player.getX() < data(s.overworld(), mapId).centerX ? 8 : -18);
				return x + " " + player.getY() + " " + player.getZ();
			});
			server.runCommand("summon pig " + at + " {NoAI:1b,NoGravity:1b,Silent:1b,Tags:[\"" + TAG + "\"]}");
			int pig = server.computeOnServer(s -> {
				for (Entity e : s.overworld().getAllEntities()) {
					if (e.entityTags().contains(TAG)) return e.getId();
				}
				throw new AssertionError("the pig was not summoned");
			});
			String key = server.computeOnServer(s -> MapEquipHandler.dotKey(mapId, data(s.overworld(), mapId),
				s.overworld().getEntity(pig)));

			context.waitFor(client -> dot(key) != null);
			context.waitTicks(15);

			List<Float> drawn = new ArrayList<>();
			for (int i = 0; i < WALK_TICKS; i++) {
				server.runOnServer(s -> {
					Entity e = s.overworld().getEntity(pig);
					e.setPos(e.getX() + STEP, e.getY(), e.getZ());
				});
				context.waitTick();
				drawn.add(context.computeOnClient(client -> dot(key)[0]));
			}

			int between = 0;
			for (int i = 0; i < drawn.size(); i++) {
				float x = drawn.get(i);
				if (x != Math.round(x)) between++;
				if (i == 0) continue;
				float step = x - drawn.get(i - 1);
				check(step > -0.01F && step < 2F, "the dot jumped or went back, from " + drawn.get(i - 1) + " to " + x + ": " + drawn);
			}
			check(between >= 5, "the dot hopped from spot to spot instead of gliding: " + drawn);

			int restDec = server.computeOnServer(s -> (int) Math.max(-127, Math.min(127,
				Math.round((s.overworld().getEntity(pig).getX() - data(s.overworld(), mapId).centerX) * 2))));
			context.waitTicks(25);
			float rested = context.computeOnClient(client -> dot(key)[0]);
			check(rested == restDec, "the dot did not come to rest where the pig stopped (" + restDec + "): " + rested);
		}
	}

	private static MapItemSavedData data(ServerLevel level, int mapId) {
		return MapItem.getSavedData(new MapId(mapId), level);
	}

	private static float[] dot(String key) {
		HudOverlay overlay = HudManager.getActiveOverlays().get(OVERLAY);
		if (overlay == null || !(overlay.componentIndex.get(MAP) instanceof MapComponent map)) return null;
		return map.drawnDot(key, 0F);
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
