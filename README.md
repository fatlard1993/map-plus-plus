# Map++

A Minecraft Fabric mod. Maps and compasses get slots of their own, and an equipped map becomes a minimap you can actually read.

## What This Mod Does

A map is a thing you hold, which means it is a thing you are not holding a sword with. Carrying one costs a hotbar slot and every glance at it costs the use of your hand, so in practice nobody navigates by map: they make one, look at it once, and put it in a chest.

Here a map and a compass each get **a slot of their own** on the inventory screen, and a map sitting in that slot draws itself in the corner of your screen, live, while your hands stay free.

## The Slots

Two extra squares on the vanilla inventory screen: one that takes a filled map, one that takes a compass — regular, lodestone, or recovery. They persist across sessions and across deaths, and they are ordinary inventory as far as everything else is concerned.

## The Minimap

A map in the slot is drawn in a screen corner, updating as you move.

**Your own marker is vanilla's.** The same white arrow the game draws on a held map, rotated to your heading, so there is nothing new to learn about reading it. Other players on the same map show as their own markers alongside.

**A compass adds a bearing.** With one in the compass slot, the minimap gains a marker for whatever the compass is pointed at: a lodestone, your last death for a recovery compass, world spawn for a plain one. If that target is off the edge of the map it is clamped to the correct edge and turned outward, which is how vanilla draws an off-map decoration, so the direction is still readable when the distance is not.

The compass's own needle is drawn small in the bottom-left corner of the map, so you can see which way it points without doing the arithmetic yourself.

**A compass with no map** gets the corner to itself: a needle and a distance, rotated to where you are looking. A death compass or a lodestone you are walking back to is one bearing and one number, and the map is the part you do not need.

## Enchantments

**Scroll**, one level, for maps. An ordinary map is a picture of where it was made: walk far enough and you fall off the edge of it, and the only cure is to make a second one and carry both. A scroll map re-centres itself on whoever is holding it, so the thing in your hand is always about where you are.

It re-centres in place, under the same map id, so the item in your inventory is never swapped and the world does not accumulate an abandoned map every few hundred blocks. Re-centring costs the picture — the new one starts blank and fills in as vanilla's scan catches up — so it happens as rarely as it can while still keeping you on the map: only once you are three quarters of the way to an edge, and then it puts you back in the middle with the whole width to cross before it is needed again.

**Mob Sight**, for compasses. While it is equipped, nearby mobs are scanned each tick and shown as coloured dots on the minimap: blue for villagers, red for hostiles, green for passive animals, orange for everything else.

## Details Worth Knowing

- **Compasses cost nuggets.** The recipe is four iron nuggets around a redstone rather than four ingots, because a compass you are expected to keep equipped should not cost most of an iron block.
- **A slotted map counts as carried.** Vanilla checks your inventory every tick to decide whether you still have the map it is tracking, and a slot it does not know about reads as empty - which dropped you from tracking each tick, so no position marker was ever sent. The check is taught to look in the slots.
- **The slots survive death.** They are inventory, and they come back the way the rest of your inventory does — with [Dead Heads](https://github.com/fatlard1993/dead-heads) installed, into the head with everything else, and back into the same slots when you collect it.
- **A death compass goes straight to the compass slot** where Dead Heads is installed, moving any ordinary compass down into the pack rather than throwing it away.

## Pandorical

Map++ is one of the most Pandorical-dependent mods in this suite. The slots are registered through Pandorical's player-inventory API, which patches the vanilla inventory screen to add and persist them; the minimap is a Pandorical HUD overlay, pushed from the server as you move and as the map data changes.

**Pandorical must be installed client-side for any of this to appear.** Without it there are no extra slots and no minimap. No Map++ jar is needed on a client.

## Configuration

`config/map-plus-plus.properties`, generated on first run:

| Key | |
|---|---|
| `minimap_position` | `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, or `BOTTOM_LEFT` |
| `minimap_size` | Size in pixels |
| `minimap_padding` | Padding from the screen edge in pixels |

## Source Map

| File | What is in it |
|---|---|
| `MapEquipHandler.java` | What the minimap shows, and pushing it to the client each tick |
| `ScrollMap.java` | Re-centring a scroll map on its owner, in place |
| `inventory/MapPlusPlusInventory.java` | The two slots' contents, per player |
| `inventory/MapSlot.java` | What counts as a map |
| `inventory/CompassSlot.java` | What counts as a compass |
| `mixin/PlayerIsHoldingMixin.java` | Teaching `Inventory.contains` to look in the slots, so map tracking holds |
| `integration/VillageQuestsLessons.java` | What a villager can teach about maps |

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
