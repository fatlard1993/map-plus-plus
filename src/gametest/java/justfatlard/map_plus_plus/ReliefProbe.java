package justfatlard.map_plus_plus;

import justfatlard.pandorical.api.MapTerrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** The relief a framed map would stand up as, for a test to look at; null while it is being read. */
public final class ReliefProbe {
	private ReliefProbe() {}

	public static MapTerrain reliefFor(MinecraftServer server, MapId id, MapItemSavedData data) {
		return MapGround.reliefFor(server, id, data);
	}
}
