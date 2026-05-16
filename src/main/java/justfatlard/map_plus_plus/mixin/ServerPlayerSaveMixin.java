package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void saveMapPlusPlus(ValueOutput output, CallbackInfo ci) {
		MapPlusPlusInventory inv = ((MapPlusPlusPlayerAccess) this).mapPlusPlus$getInventory();
		ItemStack mapStack = inv.getMapStack();
		ItemStack compassStack = inv.getCompassStack();

		if (!mapStack.isEmpty()) {
			output.store("mapPlusPlus_map", ItemStack.CODEC, mapStack);
		}
		if (!compassStack.isEmpty()) {
			output.store("mapPlusPlus_compass", ItemStack.CODEC, compassStack);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void loadMapPlusPlus(ValueInput input, CallbackInfo ci) {
		MapPlusPlusInventory inv = ((MapPlusPlusPlayerAccess) this).mapPlusPlus$getInventory();

		input.read("mapPlusPlus_map", ItemStack.CODEC).ifPresent(stack ->
			inv.setItem(MapPlusPlusInventory.MAP_SLOT, stack)
		);
		input.read("mapPlusPlus_compass", ItemStack.CODEC).ifPresent(stack ->
			inv.setItem(MapPlusPlusInventory.COMPASS_SLOT, stack)
		);
	}
}
