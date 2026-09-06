package justfatlard.map_plus_plus;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import justfatlard.map_plus_plus.MapPlusPlusConfig.MinimapPosition;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.SettingsApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * How each player wants their minimap: where it sits, how big, how close, and what it shows.
 *
 * <p>Every one of these is the player's own, kept per player by Pandorical and changed from
 * the mod menu; the config file only says what a player gets before they have chosen. Nothing
 * about a minimap in someone else's corner is the server's business.
 */
public final class MinimapPrefs {
	private MinimapPrefs() {}

	private static SettingsApi.Setting<String> corner;
	private static SettingsApi.Setting<Integer> size;
	private static SettingsApi.Setting<Integer> padding;
	private static SettingsApi.Setting<Integer> zoomTenths;
	private static SettingsApi.Setting<Boolean> coords;
	private static SettingsApi.Setting<Boolean> hostile;
	private static SettingsApi.Setting<Boolean> passive;

	/** Declare the settings, once, after the config file has been read for the defaults. */
	public static void register() {
		SettingsApi.Group group = PandoricalApi.settings().group(Main.MOD_ID, "Map++");
		Map<String, String> corners = new LinkedHashMap<>();
		for (MinimapPosition at : MinimapPosition.values()) {
			String id = at.name().toLowerCase(Locale.ROOT);
			corners.put(id, Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' '));
		}
		corner = group.choice("minimapPosition", "Minimap corner", corners,
				MapPlusPlusConfig.getPosition().name().toLowerCase(Locale.ROOT))
			.onChange(MinimapPrefs::changed);
		size = group.number("minimapSize", "Minimap size", 50, 200, 10, MapPlusPlusConfig.getMinimapSize())
			.onChange(MinimapPrefs::changed);
		padding = group.number("minimapPadding", "Minimap padding", 0, 20, 1, MapPlusPlusConfig.getMinimapPadding())
			.describe("Pixels in from the edge of the screen")
			.onChange(MinimapPrefs::changed);
		zoomTenths = group.number("minimapZoom", "Minimap zoom, tenths", 5, 40, 5, 10)
			.describe("Ten shows the whole map; more shows less of it, closer")
			.onChange(MinimapPrefs::changed);
		coords = group.toggle("minimapCoords", "Minimap coordinates", true)
			.describe("Your facing and position, under the map")
			.onChange(MinimapPrefs::changed);
		hostile = group.toggle("minimapHostile", "Minimap shows hostile mobs", true)
			.onChange(MinimapPrefs::changed);
		passive = group.toggle("minimapPassive", "Minimap shows other mobs", true)
			.onChange(MinimapPrefs::changed);
	}

	private static void changed(ServerPlayer player, Object value) {
		MapEquipHandler.refresh(player);
	}

	/** The anchor name Pandorical's HUD takes. */
	public static String anchor(ServerPlayer player) {
		return corner == null ? MapPlusPlusConfig.getPosition().name().toLowerCase(Locale.ROOT) : corner.get(player);
	}

	public static int size(ServerPlayer player) {
		return size == null ? MapPlusPlusConfig.getMinimapSize() : size.get(player);
	}

	public static int padding(ServerPlayer player) {
		return padding == null ? MapPlusPlusConfig.getMinimapPadding() : padding.get(player);
	}

	public static float zoom(ServerPlayer player) {
		return zoomTenths == null ? 1.0f : zoomTenths.get(player) / 10.0f;
	}

	public static boolean coords(ServerPlayer player) {
		return coords == null || coords.get(player);
	}

	public static boolean hostile(ServerPlayer player) {
		return hostile == null || hostile.get(player);
	}

	public static boolean passive(ServerPlayer player) {
		return passive == null || passive.get(player);
	}
}
