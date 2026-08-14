# Map++

A Fabric mod that gives maps and compasses their own dedicated inventory slots and turns an equipped map into a live minimap HUD overlay.

## Features

- **Dedicated Map and Compass Slots**: Two extra slots appear in the vanilla inventory screen: one for a filled map, one for a compass (regular, lodestone, or recovery compass), freeing up hotbar/inventory space and persisting across sessions
- **Live Minimap HUD**: While a map is equipped in its dedicated slot, a minimap overlay is shown on screen, continuously updated as you move
  - Tracks and renders your position on the map in real time
  - When a compass is also equipped, shows a directional marker toward the compass's target (lodestone location, last death location for a recovery compass, or world spawn for a regular compass)
  - Configurable position (any screen corner), size, and padding via a config file
- **Mob Sight Enchantment**: An enchantment for compasses: while worn/held with sufficient level, nearby mobs are scanned each tick and shown as color-coded dots on the minimap (blue for villagers, red for hostiles, green for passive animals, orange for everything else)

## Requirements

- Targets the Minecraft, Fabric Loader, and Fabric API versions declared in this mod's `gradle.properties`. Check there for the exact currently-supported version
- Java version as declared in `fabric.mod.json`'s `depends` block
- Pandorical (see below)

## Pandorical

Map++ is one of the most Pandorical-dependent mods in this suite:

- The dedicated map and compass inventory slots are registered through Pandorical's player-inventory API, which patches the vanilla inventory screen to add and persist them.
- The live minimap overlay (including the Mob Sight mob dots) is a Pandorical HUD overlay, continuously updated from the server as the player moves, their map data changes, or nearby mobs come and go.

**The Pandorical mod must be installed client-side to use this mod's slots and minimap at all.** Without it, the extra inventory slots and minimap overlay do not appear.

## Configuration

A config file is generated at `config/map-plus-plus.properties` on first run, with settings for:
- `minimap_position`: `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, or `BOTTOM_LEFT`
- `minimap_size`: size in pixels
- `minimap_padding`: padding from the screen edge in pixels

## Installation

Install alongside its declared dependencies (see `fabric.mod.json`), including Pandorical on both server and every connecting client.

## License

MIT
