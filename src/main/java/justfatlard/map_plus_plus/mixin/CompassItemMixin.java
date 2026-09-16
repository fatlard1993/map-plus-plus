package justfatlard.map_plus_plus.mixin;

import justfatlard.map_plus_plus.BlockMagnet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Block Magnet used on a block takes up that block, lodestones included, where vanilla would
 * bind a compass to a lodestone and do nothing with anything else. Here rather than on the use
 * event so the block has its turn first: a chest still opens, and sneaking reaches past it.
 */
@Mixin(CompassItem.class)
public class CompassItemMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void mapPlusPlus$blockMagnet(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) return;
		if (!BlockMagnet.isMagnet(player, context.getItemInHand())) return;
		BlockMagnet.takeUp(player, context.getItemInHand(), context.getClickedPos());
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
