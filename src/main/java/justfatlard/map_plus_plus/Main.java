package justfatlard.map_plus_plus;

import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PlayerInventoryApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main implements ModInitializer {
	public static final String MOD_ID = "map-plus-plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Enchantment> MOB_SIGHT = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath(MOD_ID, "mob_sight")
	);

	/** Namespace used when registering our slot group with Pandorical. */
	public static final Identifier SLOTS_NAMESPACE = Identifier.fromNamespaceAndPath(MOD_ID, "slots");

	@Override
	public void onInitialize() {
		MapPlusPlusConfig.load();

		// Register the map and compass slots with Pandorical.
		// Pandorical patches InventoryMenu on both sides and persists slot data automatically.
		PandoricalApi.playerInventory().registerSlots(
			SLOTS_NAMESPACE,
			List.of(
				new PlayerInventoryApi.SlotEntry(
					MapPlusPlusInventory.MAP_SLOT,
					127, 62,
					stack -> stack.is(Items.FILLED_MAP),
					"map-plus-plus:empty_map_slot"
				),
				new PlayerInventoryApi.SlotEntry(
					MapPlusPlusInventory.COMPASS_SLOT,
					145, 62,
					stack -> stack.getItem() instanceof CompassItem || stack.is(Items.RECOVERY_COMPASS),
					"map-plus-plus:empty_compass_slot"
				)
			)
		);

		// Keep the in-memory MapPlusPlusInventory (read by MapEquipHandler) in sync
		// whenever Pandorical processes a slot change (including on player login/respawn).
		PandoricalApi.playerInventory().onSlotChange(SLOTS_NAMESPACE, (player, event) -> {
			LOGGER.info("[map++] onSlotChange: player={} slot={} item={}", player.getName().getString(), event.slotIndex(), event.newStack());
			MapPlusPlusInventory inv = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();
			inv.setItem(event.slotIndex(), event.newStack());
		});

		ServerTickEvents.END_SERVER_TICK.register(MapEquipHandler::tick);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MapEquipHandler.onPlayerDisconnect(handler.getPlayer().getUUID());
		});

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
