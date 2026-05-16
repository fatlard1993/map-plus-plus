package justfatlard.map_plus_plus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapPlusPlusConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("MapPlusPlus");
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("map-plus-plus.properties");

	private static MinimapPosition position = MinimapPosition.TOP_RIGHT;
	private static int minimapSize = 100;
	private static int minimapPadding = 5;

	public enum MinimapPosition {
		TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT;

		public static MinimapPosition fromString(String s) {
			try {
				return valueOf(s.toUpperCase().trim());
			} catch (IllegalArgumentException e) {
				return TOP_RIGHT;
			}
		}
	}

	private static final String DEFAULT_CONFIG = """
			# Map++ Configuration
			# Delete this file to regenerate with defaults.

			# Minimap position on screen: TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT
			minimap_position=TOP_RIGHT

			# Minimap size in pixels (default 100)
			minimap_size=100

			# Padding from screen edge in pixels (default 5)
			minimap_padding=5
			""";

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			createDefaultConfig();
			LOGGER.info("[{}] Created default config at {}", Main.MOD_ID, CONFIG_PATH);
			return;
		}

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
			props.load(in);
		} catch (IOException e) {
			LOGGER.error("[{}] Failed to read config, using defaults: {}", Main.MOD_ID, e.getMessage());
			return;
		}

		position = MinimapPosition.fromString(getStr(props, "minimap_position", "TOP_RIGHT"));
		minimapSize = getInt(props, "minimap_size", minimapSize);
		minimapPadding = getInt(props, "minimap_padding", minimapPadding);
		LOGGER.info("[{}] Config loaded from {}", Main.MOD_ID, CONFIG_PATH);
	}

	public static MinimapPosition getPosition() {
		return position;
	}

	public static int getMinimapSize() {
		return minimapSize;
	}

	public static int getMinimapPadding() {
		return minimapPadding;
	}

	private static String getStr(Properties props, String key, String defaultValue) {
		String value = props.getProperty(key);
		return (value == null || value.isBlank()) ? defaultValue : value.trim();
	}

	private static int getInt(Properties props, String key, int defaultValue) {
		String value = props.getProperty(key);
		if (value == null || value.isBlank()) return defaultValue;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			LOGGER.warn("[{}] Invalid value for '{}': '{}', using default {}", Main.MOD_ID, key, value, defaultValue);
			return defaultValue;
		}
	}

	private static void createDefaultConfig() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
		} catch (IOException e) {
			LOGGER.error("[{}] Failed to create default config: {}", Main.MOD_ID, e.getMessage());
		}
	}
}
