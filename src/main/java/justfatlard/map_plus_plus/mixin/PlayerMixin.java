package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every player the two extra slots, and writes them down.
 *
 * <p>The slots used to be a field and nothing else, which meant they lasted
 * exactly as long as the player object did. That object is thrown away on
 * logout and built fresh on login, so a map put in the slot survived until the
 * player left and no longer.
 *
 * <p>They ride in the player's own saved data now, alongside the rest of what a
 * player is, which is also where they belong: it is an inventory.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements MapPlusPlusPlayerAccess {
	@Unique
	private final MapPlusPlusInventory mapPlusPlusInventory = new MapPlusPlusInventory();

	@Override
	public MapPlusPlusInventory mapPlusPlus$getInventory() {
		return mapPlusPlusInventory;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
	private void mapPlusPlus$save(ValueOutput output, CallbackInfo ci) {
		this.mapPlusPlusInventory.save(output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
	private void mapPlusPlus$load(ValueInput input, CallbackInfo ci) {
		this.mapPlusPlusInventory.load(input);
	}
}
