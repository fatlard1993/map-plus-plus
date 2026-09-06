# Map++ - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `MapEquipHandler.java` | What the minimap shows, and pushing it to the client each tick |
| `ScrollMap.java` | Re-centring a scroll map on its owner, in place |
| `inventory/MapPlusPlusInventory.java` | The two slots' contents, per player |
| `inventory/MapSlot.java` | What counts as a map |
| `inventory/CompassSlot.java` | What counts as a compass |
| `CompassTarget.java` | Where a compass points, as a place: the one answer the minimap and the table share |
| `CompassCartography.java` | What the cartography table makes of a compass: a copy, or a mark on a map |
| `mixin/Cartography*Mixin.java` | Letting compasses into the table's slots, answering them, and not spending the marking one |
| `mixin/PlayerIsHoldingMixin.java` | Teaching `Inventory.contains` to look in the slots, so map tracking holds |
| `integration/VillageQuestsLessons.java` | What a villager can teach about maps |

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
