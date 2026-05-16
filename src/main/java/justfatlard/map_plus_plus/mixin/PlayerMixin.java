package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerMixin implements MapPlusPlusPlayerAccess {
	@Unique
	private final MapPlusPlusInventory mapPlusPlusInventory = new MapPlusPlusInventory();

	@Override
	public MapPlusPlusInventory mapPlusPlus$getInventory() {
		return mapPlusPlusInventory;
	}
}
