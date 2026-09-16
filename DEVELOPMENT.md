# Map++ - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `Main.java` | Registering the slots with Pandorical, keeping this mod's copy of them in step, and starting everything else |
| `MapPlusPlusConfig.java` | `config/map-plus-plus.properties`: the corner, size and padding a player gets before choosing |
| `MinimapPrefs.java` | Each player's minimap settings, declared on Pandorical's mod menu |
| `MapEquipHandler.java` | What the minimap, the needle and the radar show, and pushing them to the client each tick |
| `ScrollMap.java` | Re-centring a scroll map in the map slot on its owner, in place |
| `MapPlusPlusPlayerAccess.java` | Reaching a player's two slots |
| `mixin/PlayerMixin.java` | Giving every player the two slots, and saving them with the player |
| `inventory/MapPlusPlusInventory.java` | The two slots' contents, per player |
| `inventory/MapSlot.java` | What counts as a map |
| `inventory/CompassSlot.java` | What counts as a compass |
| `BlockMagnet.java` | Block Magnet: taking up a block, and pointing the compass at the nearest other one |
| `mixin/CompassItemMixin.java` | Giving a Block Magnet its turn when a compass is used on a block |
| `CompassTarget.java` | Where a compass points, as a place: the one answer the minimap and the table share |
| `CompassCartography.java` | What the cartography table makes of a compass: a copy, or a mark on a map |
| `MapRelief.java` | Turning a floor-framed map to relief and back, and keeping each such frame's ground current |
| `MapTable.java` | Floor frames side by side as one table: redstone turning it as a wave from the switch, and one floor for all its maps |
| `MapGround.java` | The blocks under each pixel of a map, read a few chunks a tick from memory or parsed straight from region files |
| `mixin/ChunkMapAccessor.java` | Reaching chunks waiting to unload, which are neither in play nor on disk yet |
| `mixin/Cartography*Mixin.java` | Letting compasses into the table's slots, answering them, and not spending the marking one |
| `mixin/PlayerIsHoldingMixin.java` | Teaching `Inventory.contains` to look in the slots, so map tracking holds |
| `integration/VillageQuestsLessons.java` | What a villager can teach about maps |
| `integration/FishingOverlay.java` | Whether Minedew Fishing's minigame has the screen, so the minimap, needle and radar stand aside |

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`), which include Pandorical 1.3.9 or later; connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
