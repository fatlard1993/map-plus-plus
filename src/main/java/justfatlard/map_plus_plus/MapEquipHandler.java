package justfatlard.map_plus_plus;

import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side handler that watches equipped map/compass changes
 * and drives the Pandorical HUD overlay for each player.
 */
public class MapEquipHandler {
	private static final String OVERLAY_ID = "map-plus-plus:minimap";
	private static final String MAP_COMPONENT_ID = "minimap";

	// Per-player last-known state
	private static final Map<UUID, PlayerState> playerStates = new HashMap<>();

	// Per-player last-sent mob list string (to avoid redundant updates)
	private static final Map<UUID, String> lastMobData = new HashMap<>();

	/**
	 * Tracks last-sent state per player. compassTargetX/Z are NaN when no target is known.
	 * selfDecX/Y are the player's current decoration bytes on the map (for client self-identification).
	 */
	private record PlayerState(int mapId, boolean hasCompass, double compassTargetX, double compassTargetZ,
			byte selfDecX, byte selfDecY) {}

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("MapEquipHandler");

	public static void tick(MinecraftServer server) {
		if (!PandoricalApi.isAvailable()) return;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!PandoricalApi.isAvailable(player)) continue;

			MapPlusPlusInventory inv = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();
			ItemStack mapStack = inv.getMapStack();
			boolean hasCompass = inv.hasCompass();

			UUID playerId = player.getUUID();
			PlayerState lastState = playerStates.get(playerId);

			if (mapStack.isEmpty()) {
				// No map equipped
				if (lastState != null) {
					PandoricalApi.hud().hide(player, OVERLAY_ID);
					playerStates.remove(playerId);
					lastMobData.remove(playerId);
				}
				continue;
			}
			LOGGER.info("[map++] tick: player={} mapStack={} hasMapId={}", player.getName().getString(), mapStack, mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID) != null);

			MapId mapId = mapStack.get(DataComponents.MAP_ID);
			if (mapId == null) {
				if (lastState != null) {
					PandoricalApi.hud().hide(player, OVERLAY_ID);
					playerStates.remove(playerId);
					lastMobData.remove(playerId);
				}
				continue;
			}

			int mapIdValue = mapId.id();

			// Compute compass target (NaN if none)
			ItemStack compassStack = inv.getCompassStack();
			double[] target = hasCompass ? computeCompassTarget(player, compassStack) : null;
			double compassTx = (target != null) ? target[0] : Double.NaN;
			double compassTz = (target != null) ? target[1] : Double.NaN;

			// Compute self decoration bytes (server-side, using actual map center).
			// Also compute compass target dec bytes so client can position the X marker
			// without depending on mc.player.getX() (which drifts when clamped at map edge).
			byte selfDecX = 0;
			byte selfDecY = 0;
			byte compassDecX = 0;
			byte compassDecY = 0;
			MapItemSavedData mapData = MapItem.getSavedData(mapId, player.level());
			if (mapData != null) {
				int scaleFactor = 1 << mapData.scale;
				int rawX = (int)Math.round((player.getX() - mapData.centerX) / scaleFactor * 2.0);
				int rawY = (int)Math.round((player.getZ() - mapData.centerZ) / scaleFactor * 2.0);
				selfDecX = (byte)Math.max(-127, Math.min(127, rawX));
				selfDecY = (byte)Math.max(-127, Math.min(127, rawY));
				// Compass target as stable map dec bytes (independent of player position)
				if (!Double.isNaN(compassTx) && !Double.isNaN(compassTz)) {
					compassDecX = (byte)Math.max(-127, Math.min(127,
						(int)Math.round((compassTx - mapData.centerX) / scaleFactor * 2.0)));
					compassDecY = (byte)Math.max(-127, Math.min(127,
						(int)Math.round((compassTz - mapData.centerZ) / scaleFactor * 2.0)));
				}
			}

			PlayerState currentState = new PlayerState(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY);

			// Tick the map so vanilla sends data updates to the client
			if (mapData != null) {
				// tickCarriedBy: updates player position decoration and sends map packet to client
				mapData.tickCarriedBy(player, mapStack, null);
				// update: processes embedded decorations (treasure X marks, explorer map icons, etc.)
				Item item = mapStack.getItem();
				if (item instanceof MapItem mapItem) {
					if (server.getTickCount() % 40 == 0) {
						int before = 0; for (byte b : mapData.colors) if (b != 0) before++;
						mapItem.update(player.level(), player, mapData);
						int after = 0; for (byte b : mapData.colors) if (b != 0) after++;
						LOGGER.info("[map++] update: before={} after={} dim={} center=({},{})",
							before, after, mapData.dimension.toString(), mapData.centerX, mapData.centerZ);
					} else {
						mapItem.update(player.level(), player, mapData);
					}
				}

			} else {
				LOGGER.warn("[map++] mapData is null for mapId={}", mapIdValue);
			}

			// --- Mob Sight enchantment: scan nearby mobs and send as HUD prop ---
			if (mapData != null) {
				ItemEnchantments enchantments = compassStack.get(DataComponents.ENCHANTMENTS);
				int mobSightLevel = 0;
				if (enchantments != null) {
					Holder<Enchantment> mobSightHolder = player.level()
						.registryAccess()
						.lookupOrThrow(Registries.ENCHANTMENT)
						.get(Main.MOB_SIGHT)
						.orElse(null);
					if (mobSightHolder != null) {
						mobSightLevel = enchantments.getLevel(mobSightHolder);
					}
				}

				String currentMobs = "";
				if (mobSightLevel > 0) {
					int scaleFactor = 1 << mapData.scale;
					int range = 64 * scaleFactor;
					double cx = mapData.centerX;
					double cz = mapData.centerZ;
					AABB scanBox = new AABB(
						cx - range, -64, cz - range,
						cx + range, 384, cz + range
					);

					List<LivingEntity> mobs = player.level().getEntitiesOfClass(
						LivingEntity.class, scanBox,
						e -> e != player
					);

					// Sort by distance to player, take top 50
					double px = player.getX(), pz = player.getZ();
					mobs.sort(Comparator.comparingDouble(e -> {
						double dx = e.getX() - px, dz = e.getZ() - pz;
						return dx * dx + dz * dz;
					}));
					if (mobs.size() > 50) {
						mobs = mobs.subList(0, 50);
					}

					StringBuilder sb = new StringBuilder();
					for (LivingEntity mob : mobs) {
						int rawDecX = (int) Math.round((mob.getX() - cx) / scaleFactor * 2);
						int rawDecZ = (int) Math.round((mob.getZ() - cz) / scaleFactor * 2);
						byte decX = (byte) Math.max(-127, Math.min(127, rawDecX));
						byte decZ = (byte) Math.max(-127, Math.min(127, rawDecZ));
						int color = mobColor(mob);
						String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
						if (sb.length() > 0) sb.append(';');
						sb.append(decX).append(',').append(decZ).append(',').append(color).append(',').append(entityTypeId);
					}
					currentMobs = sb.toString();
				}

				String lastMobs = lastMobData.getOrDefault(playerId, "");
				if (!currentMobs.equals(lastMobs)) {
					lastMobData.put(playerId, currentMobs);
					Map<String, String> mobProps = new java.util.HashMap<>();
					mobProps.put("mobs", currentMobs);
					PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
						new ComponentUpdate(MAP_COMPONENT_ID, mobProps)
					));
				}
			}

			if (lastState == null) {
				// New: show HUD
				LOGGER.info("[map++] Showing HUD for {} — mapId={} compass={} selfDec=({},{}) pandorical={}",
					player.getName().getString(), mapIdValue, hasCompass, selfDecX, selfDecY, PandoricalApi.isAvailable(player));
				showHud(player, mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY);
				playerStates.put(playerId, currentState);
			} else if (lastState.mapId() != mapIdValue) {
				// Map changed
				PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
					new ComponentUpdate(MAP_COMPONENT_ID, buildProps(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY))
				));
				playerStates.put(playerId, currentState);
			} else if (lastState.hasCompass() != hasCompass
					|| !coordEqual(lastState.compassTargetX(), compassTx)
					|| !coordEqual(lastState.compassTargetZ(), compassTz)
					|| lastState.selfDecX() != selfDecX
					|| lastState.selfDecY() != selfDecY) {
				// Compass/target/position changed
				PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
					new ComponentUpdate(MAP_COMPONENT_ID, buildProps(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY))
				));
				playerStates.put(playerId, currentState);
			}
		}
	}

	/**
	 * Resolves the compass target world coordinates for the given player and compass stack.
	 * Returns a double[]{x, z} if a target is found in the player's current dimension,
	 * or null if no target is available.
	 */
	private static double[] computeCompassTarget(ServerPlayer player, ItemStack compassStack) {
		if (compassStack.isEmpty()) return null;

		// 1. Lodestone compass: has LODESTONE_TRACKER component with a target GlobalPos
		LodestoneTracker lodestoneTracker = compassStack.get(DataComponents.LODESTONE_TRACKER);
		if (lodestoneTracker != null) {
			Optional<GlobalPos> targetOpt = lodestoneTracker.target();
			if (targetOpt.isPresent()) {
				GlobalPos gp = targetOpt.get();
				// Only valid if the lodestone is in the player's current dimension
				if (gp.dimension().equals(player.level().dimension())) {
					return new double[]{gp.pos().getX(), gp.pos().getZ()};
				}
			}
			return null; // lodestone is in another dimension or lost
		}

		// 2. Recovery compass: points to player's last death location
		if (compassStack.is(Items.RECOVERY_COMPASS)) {
			Optional<GlobalPos> deathOpt = player.getLastDeathLocation();
			if (deathOpt.isPresent()) {
				GlobalPos gp = deathOpt.get();
				if (gp.dimension().equals(player.level().dimension())) {
					return new double[]{gp.pos().getX(), gp.pos().getZ()};
				}
			}
			return null; // no death recorded or in another dimension
		}

		// 3. Regular compass (CompassItem): points to world spawn of the overworld
		if (compassStack.getItem() instanceof CompassItem) {
			// Only meaningful in the overworld
			if (player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
				net.minecraft.core.BlockPos spawnPos = player.level().getServer().overworld().getRespawnData().pos();
				return new double[]{spawnPos.getX(), spawnPos.getZ()};
			}
			return null;
		}

		return null;
	}

	/** Format a coordinate as a string, or "" if NaN (no target). */
	private static String formatCoord(double coord) {
		return Double.isNaN(coord) ? "" : String.valueOf(coord);
	}

	/** NaN-aware coordinate equality with 0.5-block tolerance (block centres). */
	private static boolean coordEqual(double a, double b) {
		if (Double.isNaN(a) && Double.isNaN(b)) return true;
		if (Double.isNaN(a) || Double.isNaN(b)) return false;
		return Math.abs(a - b) < 0.5;
	}

	private static Map<String, String> buildProps(int mapId, boolean hasCompass,
			double compassTx, double compassTz, byte selfDecX, byte selfDecY,
			byte compassDecX, byte compassDecY) {
		Map<String, String> m = new java.util.HashMap<>();
		m.put("map_id",       String.valueOf(mapId));
		m.put("rotate",       String.valueOf(hasCompass));
		m.put("compass_tx",   formatCoord(compassTx));
		m.put("compass_tz",   formatCoord(compassTz));
		m.put("self_dec_x",   String.valueOf(selfDecX));
		m.put("self_dec_y",   String.valueOf(selfDecY));
		// Stable map-coord bytes for the X marker — avoids drift when player is clamped at edge
		m.put("compass_dec_x", String.valueOf(compassDecX));
		m.put("compass_dec_y", String.valueOf(compassDecY));
		return m;
	}

	private static void showHud(ServerPlayer player, int mapId, boolean hasCompass,
			double compassTx, double compassTz, byte selfDecX, byte selfDecY,
			byte compassDecX, byte compassDecY) {
		int size = MapPlusPlusConfig.getMinimapSize();
		String anchor = MapPlusPlusConfig.getPosition().name().toLowerCase();
		int padding = MapPlusPlusConfig.getMinimapPadding();

		Map<String, String> props = buildProps(mapId, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY);
		ComponentBuilder comp = new ComponentBuilder(MAP_COMPONENT_ID, ComponentType.MAP)
			.bounds(0, 0, size, size);
		props.forEach(comp::prop);

		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor(anchor)
			.offset(padding, padding)
			.component(comp.build());

		PandoricalApi.hud().show(player, hud.build());
	}

	/** Returns ARGB color for a mob dot based on its type. */
	private static int mobColor(LivingEntity entity) {
		if (entity instanceof Villager) return 0xFF3399FF; // blue — villager
		if (entity instanceof Enemy)   return 0xFFFF3333; // red  — hostile
		if (entity instanceof Animal)  return 0xFF33FF33; // green — passive
		return 0xFFFFAA00;                                 // orange — other
	}

	public static void onPlayerDisconnect(UUID playerId) {
		playerStates.remove(playerId);
		lastMobData.remove(playerId);
	}
}
