package justfatlard.map_plus_plus.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Whether the fishing minigame currently owns the screen.
 *
 * <p>The minimap and the minigame both live in a corner of the same screen, and the minigame is the
 * one with something to say: it is a timed thing that wants watching, and it is over in seconds.
 * A map is a reference you consult, and it will still be there afterwards - so the map is the one
 * that stands aside.
 *
 * <p>Guarded the way the rest of the suite's optional integrations are. The flag is a field on this
 * class, which always loads; the fishing type is named inside a method body behind that flag, so a
 * server without the mod never reaches an instruction that would have to resolve it.
 */
public final class FishingOverlay {
	private FishingOverlay() {}

	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("minedew-fishing");

	public static boolean isUp(ServerPlayer player) {
		if (!PRESENT) return false;
		return com.minedew.fishing.encounter.FishingEncounterManager.isMinigameHudUp(player.getUUID());
	}
}
