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
import net.minecraft.network.protocol.Packet;
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

	/**
	 * The compass with no map: a needle and a distance, nothing else.
	 *
	 * <p>A compass used to be decoration on a minimap, so equipping one without a
	 * map showed nothing at all. That is wrong for the case the compass is best
	 * at: a death compass, or a lodestone you are walking back to, is a single
	 * bearing and a number, and drawing a whole map around it is the part you do
	 * not need.
	 */
	private static final String NEEDLE_OVERLAY_ID = "map-plus-plus:needle";
	private static final String NEEDLE_COMPONENT_ID = "needle";
	private static final String NEEDLE_LABEL_ID = "needle_distance";
	private static final String NEEDLE_TEXTURE = "map-plus-plus:textures/gui/sprites/needle.png";
	private static final int NEEDLE_SIZE = 16;

	/** Players currently shown the needle, so it is only pushed when it changes. */
	/**
	 * What the needle is currently showing each player, so nothing is pushed twice.
	 *
	 * <p>Was the bearing alone, which meant walking straight at the target never changed it and
	 * the distance under the needle sat frozen the whole way in. Holding what is actually drawn
	 * means anything that changes on screen gets sent and nothing else does.
	 */
	private record NeedleState(int bearing, String label) {}

	private static final Map<UUID, NeedleState> needleBearing = new HashMap<>();

	/**
	 * The eight-point heading the player is facing.
	 *
	 * <p>Minecraft's yaw has zero facing south and grows clockwise, so the labels start there.
	 * The half-step in the rounding puts the boundary between two names in the middle of the gap
	 * between them, rather than on top of one, which is what stops a name flickering when you
	 * stand on a diagonal.
	 */
	private static final String[] HEADINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private static String headingOf(float yaw) {
		return HEADINGS[Math.floorMod((int) Math.floor(yaw / 45.0F + 0.5F), HEADINGS.length)];
	}

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

			// Before anything is read off the map, in case this is the tick it moves: everything
			// below wants the centre it will have, not the one it is about to leave behind.
			ScrollMap.tick(player, mapStack);

			UUID playerId = player.getUUID();
			PlayerState lastState = playerStates.get(playerId);

			if (mapStack.isEmpty()) {
				// No map equipped: the compass can still stand on its own.
				if (lastState != null) {
					PandoricalApi.hud().hide(player, OVERLAY_ID);
					playerStates.remove(playerId);
					lastMobData.remove(playerId);
				}
				tickNeedleOnly(player, inv, hasCompass);
				continue;
			}

			MapId mapId = mapStack.get(DataComponents.MAP_ID);
			if (mapId == null) {
				if (lastState != null) {
					PandoricalApi.hud().hide(player, OVERLAY_ID);
					playerStates.remove(playerId);
					lastMobData.remove(playerId);
				}
				tickNeedleOnly(player, inv, hasCompass);
				continue;
			}

			// A map is up, so the needle would be saying the same thing twice.
			hideNeedle(player);

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
			boolean compassOffMap = false;
			MapItemSavedData mapData = MapItem.getSavedData(mapId, player.level());
			if (mapData != null) {
				int scaleFactor = 1 << mapData.scale;
				int rawX = (int)Math.round((player.getX() - mapData.centerX) / scaleFactor * 2.0);
				int rawY = (int)Math.round((player.getZ() - mapData.centerZ) / scaleFactor * 2.0);
				selfDecX = (byte)Math.max(-127, Math.min(127, rawX));
				selfDecY = (byte)Math.max(-127, Math.min(127, rawY));
				// Compass target as stable map dec bytes (independent of player position).
				//
				// Walked back along its own bearing when it falls outside, rather than clamped
				// per axis: two independent clamps put anything past a corner *in* that corner,
				// so a target away to the north-east and one away to the east arrived at the
				// same pixel. Scaling both by the same factor keeps the direction, which is the
				// only thing an off-map marker has left to say.
				if (!Double.isNaN(compassTx) && !Double.isNaN(compassTz)) {
					double dx = (compassTx - mapData.centerX) / scaleFactor * 2.0;
					double dz = (compassTz - mapData.centerZ) / scaleFactor * 2.0;
					double furthest = Math.max(Math.abs(dx), Math.abs(dz));

					compassOffMap = furthest > 127.0;
					if (compassOffMap) {
						dx = dx / furthest * 127.0;
						dz = dz / furthest * 127.0;
					}
					compassDecX = (byte) Math.round(dx);
					compassDecY = (byte) Math.round(dz);
				}
			}

			PlayerState currentState = new PlayerState(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY);

			// Tick the map, and then post it, because nothing else will.
			if (mapData != null) {
				// Registers the player as a carrier and keeps their position decoration current.
				mapData.tickCarriedBy(player, mapStack, null);

				// The terrain scan: this is what turns fog into ground as somebody walks.
				Item item = mapStack.getItem();
				if (item instanceof MapItem mapItem) {
					mapItem.update(player.level(), player, mapData);
				}

				// And the delivery, which vanilla will not do for a map kept here.
				//
				// ServerPlayer walks its own Inventory - all 41 slots of it - and posts a map
				// packet for every map it finds. This slot is an attachment and not part of that
				// inventory, so a map in it was scanned into the server's copy every tick and
				// never sent anywhere: the ground was explored, recorded, and invisible. Taking
				// the map into a hand put it back in the walk and the whole backlog arrived at
				// once, which reads as the map filling in the instant you unequip it.
				//
				// Null when there is nothing new, so this costs a comparison on a still player.
				Packet<?> update = mapData.getUpdatePacket(mapId, player);
				if (update != null) {
					player.connection.send(update);
				}
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

				int scaleFactor = 1 << mapData.scale;

				// Other players, always. Mob Sight decides whether you can see the wildlife;
				// knowing where the people you are playing with are standing is what a shared
				// map is for, and it should not depend on an enchantment.
				String currentPlayers = otherPlayerDots(player, mapData, scaleFactor);

				String currentMobs = "";
				if (mobSightLevel > 0) {
					int range = 64 * scaleFactor;
					double cx = mapData.centerX;
					double cz = mapData.centerZ;
					AABB scanBox = new AABB(
						cx - range, -64, cz - range,
						cx + range, 384, cz + range
					);

					// Players are drawn from their own list above; without this they arrive twice,
					// once in team colour and once in whatever mobColor makes of them.
					List<LivingEntity> mobs = player.level().getEntitiesOfClass(
						LivingEntity.class, scanBox,
						e -> e != player && !(e instanceof net.minecraft.world.entity.player.Player)
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

				if (!currentPlayers.isEmpty()) {
					currentMobs = currentMobs.isEmpty() ? currentPlayers : currentPlayers + ";" + currentMobs;
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
				showHud(player, mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY, compassOffMap);
				playerStates.put(playerId, currentState);
			} else if (lastState.mapId() != mapIdValue) {
				// Map changed
				PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
					new ComponentUpdate(MAP_COMPONENT_ID, buildProps(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY, compassOffMap))
				));
				playerStates.put(playerId, currentState);
			} else if (lastState.hasCompass() != hasCompass
					|| !coordEqual(lastState.compassTargetX(), compassTx)
					|| !coordEqual(lastState.compassTargetZ(), compassTz)
					|| lastState.selfDecX() != selfDecX
					|| lastState.selfDecY() != selfDecY) {
				// Compass/target/position changed
				PandoricalApi.hud().update(player, OVERLAY_ID, List.of(
					new ComponentUpdate(MAP_COMPONENT_ID, buildProps(mapIdValue, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY, compassOffMap))
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

	/**
	 * Everyone else on this map, as dots in their team's colour.
	 *
	 * <p>Rides the same channel the mob dots use, because the client already knows how to draw
	 * that: position in map bytes, a colour, and the entity type. Team colour rather than one
	 * flat shade so a glance at the minimap says who is who, and white for anybody unteamed,
	 * which is what {@code getTeamColor} already answers for them.
	 *
	 * <p>The player holding the map is not in the list: they get vanilla's own arrow, drawn from
	 * the self position the server sends separately, so they can tell which dot is theirs by it
	 * not being a dot.
	 */
	private static String otherPlayerDots(ServerPlayer self, MapItemSavedData mapData, int scaleFactor) {
		double cx = mapData.centerX;
		double cz = mapData.centerZ;
		int range = 64 * scaleFactor;

		StringBuilder sb = new StringBuilder();
		for (ServerPlayer other : self.level().players()) {
			if (other == self || other.isSpectator()) continue;
			if (Math.abs(other.getX() - cx) > range || Math.abs(other.getZ() - cz) > range) continue;

			int rawX = (int) Math.round((other.getX() - cx) / scaleFactor * 2);
			int rawZ = (int) Math.round((other.getZ() - cz) / scaleFactor * 2);
			byte decX = (byte) Math.max(-127, Math.min(127, rawX));
			byte decZ = (byte) Math.max(-127, Math.min(127, rawZ));

			if (sb.length() > 0) sb.append(';');
			sb.append(decX).append(',').append(decZ).append(',')
				.append(0xFF000000 | other.getTeamColor()).append(',')
				.append("minecraft:player");
		}
		return sb.toString();
	}

	private static Map<String, String> buildProps(int mapId, boolean hasCompass,
			double compassTx, double compassTz, byte selfDecX, byte selfDecY,
			byte compassDecX, byte compassDecY, boolean offMap) {
		Map<String, String> m = new java.util.HashMap<>();
		m.put("map_id",       String.valueOf(mapId));
		m.put("rotate",       String.valueOf(hasCompass));
		m.put("compass_tx",   formatCoord(compassTx));
		m.put("compass_tz",   formatCoord(compassTz));
		m.put("self_dec_x",   String.valueOf(selfDecX));
		m.put("self_dec_y",   String.valueOf(selfDecY));
		m.put("compass_off_map", String.valueOf(offMap));
		// The heading arrow the client lays in the corner. Ours to supply, because the texture
		// ships in this mod and pandorical has no business knowing its name.
		m.put("needle", NEEDLE_TEXTURE);
		// Stable map-coord bytes for the X marker: avoids drift when player is clamped at edge
		m.put("compass_dec_x", String.valueOf(compassDecX));
		m.put("compass_dec_y", String.valueOf(compassDecY));
		return m;
	}

	private static void showHud(ServerPlayer player, int mapId, boolean hasCompass,
			double compassTx, double compassTz, byte selfDecX, byte selfDecY,
			byte compassDecX, byte compassDecY, boolean offMap) {
		int size = MapPlusPlusConfig.getMinimapSize();
		String anchor = MapPlusPlusConfig.getPosition().name().toLowerCase();
		int padding = MapPlusPlusConfig.getMinimapPadding();

		Map<String, String> props = buildProps(mapId, hasCompass, compassTx, compassTz, selfDecX, selfDecY, compassDecX, compassDecY, offMap);
		ComponentBuilder comp = new ComponentBuilder(MAP_COMPONENT_ID, ComponentType.MAP)
			.bounds(0, 0, size, size);
		props.forEach(comp::prop);

		HudBuilder hud = new HudBuilder(OVERLAY_ID)
			.anchor(anchor)
			.offset(padding, padding)
			.component(comp.build());

		PandoricalApi.hud().show(player, hud.build());
	}

	/**
	 * Show, update or hide the standalone needle for a player with no map.
	 *
	 * <p>Only pushed when something on screen would differ, so a player standing still costs
	 * nothing and one walking sends about as much as the minimap did.
	 */
	private static void tickNeedleOnly(ServerPlayer player, MapPlusPlusInventory inv, boolean hasCompass) {
		if (!hasCompass) {
			hideNeedle(player);
			return;
		}

		double[] target = computeCompassTarget(player, inv.getCompassStack());
		if (target == null) {
			hideNeedle(player);
			return;
		}

		double dx = target[0] - player.getX();
		double dz = target[1] - player.getZ();

		// Yaw that faces the target, then made relative to where the player is
		// already looking, so the needle reads as "that way from here" rather than
		// "north is over there".
		double facing = Math.toDegrees(Math.atan2(-dx, dz));
		int bearing = Math.floorMod((int) Math.round(facing - player.getYRot()), 360);
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

		// Which way the player is facing, which is a different question from which way the
		// needle points: the needle is relative to their heading, so on its own it says
		// "that way from here" and never says where here is pointed.
		String label = headingOf(player.getYRot()) + "  " + distance + "m";

		UUID playerId = player.getUUID();
		NeedleState last = needleBearing.get(playerId);
		if (last != null && last.bearing() == bearing && last.label().equals(label)) return;

		needleBearing.put(playerId, new NeedleState(bearing, label));

		if (last == null) {
			showNeedle(player, bearing, label);
		} else {
			PandoricalApi.hud().update(player, NEEDLE_OVERLAY_ID, List.of(
				new ComponentUpdate(NEEDLE_COMPONENT_ID,
					Map.of(ComponentType.PROP_ROTATION, String.valueOf(bearing))),
				new ComponentUpdate(NEEDLE_LABEL_ID,
					Map.of(ComponentType.PROP_TEXT, label))
			));
		}
	}

	private static void showNeedle(ServerPlayer player, int bearing, String label) {
		String anchor = MapPlusPlusConfig.getPosition().name().toLowerCase();
		int padding = MapPlusPlusConfig.getMinimapPadding();

		HudBuilder hud = new HudBuilder(NEEDLE_OVERLAY_ID)
			.anchor(anchor)
			.offset(padding, padding)
			.component(new ComponentBuilder(NEEDLE_COMPONENT_ID, ComponentType.SPRITE)
				.bounds(0, 0, NEEDLE_SIZE, NEEDLE_SIZE)
				.prop(ComponentType.PROP_TEXTURE, NEEDLE_TEXTURE)
				.prop(ComponentType.PROP_ROTATION, String.valueOf(bearing))
				// Turning is continuous, so the default short blend is right here:
				// it hides the gap between pushes without lagging the needle.
				.build())
			.component(new ComponentBuilder(NEEDLE_LABEL_ID, ComponentType.TEXT)
				.bounds(0, NEEDLE_SIZE + 2, NEEDLE_SIZE * 3, 9)
				.prop(ComponentType.PROP_TEXT, label)
				.prop(ComponentType.PROP_SHADOW, "true")
				.build());

		PandoricalApi.hud().show(player, hud.build());
	}

	private static void hideNeedle(ServerPlayer player) {
		if (needleBearing.remove(player.getUUID()) != null) {
			PandoricalApi.hud().hide(player, NEEDLE_OVERLAY_ID);
		}
	}

	/** Returns ARGB color for a mob dot based on its type. */
	private static int mobColor(LivingEntity entity) {
		if (entity instanceof Villager) return 0xFF3399FF; // blue: villager
		if (entity instanceof Enemy)   return 0xFFFF3333; // red: hostile
		if (entity instanceof Animal)  return 0xFF33FF33; // green: passive
		return 0xFFFFAA00;                                 // orange: other
	}

	public static void onPlayerDisconnect(UUID playerId) {
		playerStates.remove(playerId);
		lastMobData.remove(playerId);
		needleBearing.remove(playerId);
	}
}
