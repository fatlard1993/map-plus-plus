package justfatlard.map_plus_plus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.map_plus_plus.MapSlices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A map drawn under a roof shows the ground at its holder's height, as a slice: open floor
 * below head height is drawn with its shading, rock at head height as the walls around it.
 * Vanilla paints such a map as noise, since looking down from the top meets only the roof.
 * Above the roof the slice starts higher than the ground, so the roof is drawn as it is.
 *
 * <p>Each pixel's slice height is kept, see {@link MapSlices}, so the relief can stand it up.
 */
@Mixin(MapItem.class)
public class MapItemMixin {
	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/dimension/DimensionType;hasCeiling()Z", ordinal = 1))
	private boolean mapPlusPlus$drawUnderRoof(boolean hasCeiling) {
		return false;
	}

	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/chunk/LevelChunk;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
	private int mapPlusPlus$sliceAtHolder(int top, Level level, Entity player, MapItemSavedData data) {
		if (!level.dimensionType().hasCeiling()) return top;
		return Math.min(top, mapPlusPlus$cut(player));
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z"))
	private boolean mapPlusPlus$keepSlice(MapItemSavedData data, int x, int y, byte colour, Operation<Boolean> original,
			Level level, Entity player, MapItemSavedData sameData) {
		boolean changed = original.call(data, x, y, colour);
		MapId id = MapSlices.drawing();
		if (id != null && level instanceof ServerLevel server && level.dimensionType().hasCeiling()) {
			MapSlices slices = MapSlices.get(server.getServer());
			// Only where the pixel changed, so a hop on the spot does not rewrite what it already shows
			if (changed || slices.unrecorded(id, x, y)) slices.record(id, x, y, mapPlusPlus$cut(player));
		}
		return changed;
	}

	@WrapOperation(method = "inventoryTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/MapItem;update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"))
	private void mapPlusPlus$drawingHeld(MapItem self, Level level, Entity owner, MapItemSavedData data, Operation<Void> original,
			ItemStack stack, ServerLevel sameLevel, Entity sameOwner, EquipmentSlot slot) {
		MapId id = stack.get(DataComponents.MAP_ID);
		if (id == null) original.call(self, level, owner, data);
		else MapSlices.drawing(id, () -> original.call(self, level, owner, data));
	}

	/** Head height: the block a player's eyes are in when they stand. */
	@Unique
	private static int mapPlusPlus$cut(Entity player) {
		return player.getBlockY() + 1;
	}
}
