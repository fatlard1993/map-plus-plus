package justfatlard.map_plus_plus;

import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PlayerInventoryApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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

	public static final ResourceKey<Enchantment> SCROLL = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath(MOD_ID, "scroll")
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
					justfatlard.map_plus_plus.inventory.MapSlot::isMap,
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
			MapPlusPlusInventory inv = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();
			inv.setItem(event.slotIndex(), event.newStack());
		});

		// Two stores hold these slots and they can come apart.
		//
		// Pandorical's is what the inventory screen draws from; this mod keeps its own copy
		// because the minimap is read every tick and the attachment is not shaped for that. The
		// listener above keeps the copy following the original, which is fine while both
		// survive - but Pandorical's attachment used not to be carried across a death while this
		// copy was, and a player who died in that window came back with a map the minimap could
		// see and the slot could not. Both are saved to disk, so the disagreement kept.
		//
		// Reconciled on the way in, once, in the only direction that can recover anything: where
		// the slot reads empty and this copy does not, the copy is holding the survivor and it
		// goes back through Pandorical - the same path a player dropping an item in would take.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			MapPlusPlusInventory mirror = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();
			var slots = PandoricalApi.playerInventory();

			for (int slot = 0; slot < mirror.getContainerSize(); slot++) {
				ItemStack kept = mirror.getItem(slot);
				if (kept.isEmpty()) continue;
				if (!slots.getSlot(player, SLOTS_NAMESPACE, slot).isEmpty()) continue;

				LOGGER.info("[{}] Restoring {} to slot {} for {} - it was in this mod's copy and"
					+ " not in the shared one", MOD_ID, kept.getItem(), slot,
					player.getName().getString());
				slots.setSlot(player, SLOTS_NAMESPACE, slot, kept.copy());
			}
		});

		// A respawn builds a new player object rather than reading one from disk,
		// so saved data does not carry these slots across a death. This does.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
			((MapPlusPlusPlayerAccess) newPlayer).mapPlusPlus$getInventory()
				.copyFrom(((MapPlusPlusPlayerAccess) oldPlayer).mapPlusPlus$getInventory()));

		ServerTickEvents.END_SERVER_TICK.register(MapEquipHandler::tick);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MapEquipHandler.onPlayerDisconnect(handler.getPlayer().getUUID());
		});

		// Isolated in its own class and only reached from here: it refers to
		// Village Quests types directly, so it must not load without that mod.
		if (FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
			justfatlard.map_plus_plus.integration.VillageQuestsLessons.register();
		}

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
