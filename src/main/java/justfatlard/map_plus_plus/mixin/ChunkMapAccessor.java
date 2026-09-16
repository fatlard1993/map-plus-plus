package justfatlard.map_plus_plus.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Chunks waiting to unload, which wait for a tick with time to spare: on a busy server that is a
 * while, and they are neither among the chunks in play nor on disk until it comes.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("pendingUnloads")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> mapPlusPlus$pendingUnloads();
}
