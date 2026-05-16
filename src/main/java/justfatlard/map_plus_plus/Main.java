package justfatlard.map_plus_plus;

import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "map-plus-plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Enchantment> MOB_SIGHT = ResourceKey.create(
		Registries.ENCHANTMENT,
		Identifier.fromNamespaceAndPath(MOD_ID, "mob_sight")
	);

	@Override
	public void onInitialize() {
		MapPlusPlusConfig.load();

		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			MapPlusPlusInventory oldInv = ((MapPlusPlusPlayerAccess) oldPlayer).mapPlusPlus$getInventory();
			MapPlusPlusInventory newInv = ((MapPlusPlusPlayerAccess) newPlayer).mapPlusPlus$getInventory();
			newInv.copyFrom(oldInv);
		});

		ServerTickEvents.END_SERVER_TICK.register(MapEquipHandler::tick);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MapEquipHandler.onPlayerDisconnect(handler.getPlayer().getUUID());
		});

		LOGGER.info("Map++ loaded");
	}
}
