#!/usr/bin/env python3
"""Generate the empty-slot outlines from the vanilla items they stand for.

The map slot's placeholder was a diamond, which is what a map looked like until
this snapshot redrew it as a scroll: two square sheets, offset. A hand-drawn
outline of a vanilla item is a copy that has no way of knowing the original
moved, so this traces the silhouette out of the client jar instead. When vanilla
redraws something again, the fix is running this.

Eroded by one pixel before tracing, so the outline sits inside the slot rather
than along its edge, and drawn in the same grey the compass placeholder already
used, so the two still look like a pair.

Pure stdlib PNG writer, no Pillow, deterministic.

Usage: python3 generate_slot_placeholders.py
"""
import glob, os, struct, subprocess, sys, tempfile, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
SPRITES = os.path.join(HERE, "src/main/resources/assets/map-plus-plus/textures/gui/sprites")

INK = (0x55, 0x55, 0x55, 200)
CLEAR = (0, 0, 0, 0)
OPAQUE = 32          # alpha at or under this is background, not the item


def vanilla_alpha(item, scratch):
    """The item's silhouette, as a grid of booleans, out of the newest client jar."""
    jars = sorted(glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/*/minecraft-client.jar")), key=os.path.getmtime)
    if not jars:
        sys.exit("no Loom client jar cached - run a build first")

    subprocess.run(["unzip", "-o", "-j", "-q", jars[-1],
                    f"assets/minecraft/textures/item/{item}.png", "-d", scratch], check=True)
    raw = os.path.join(scratch, f"{item}.raw")
    subprocess.run(["magick", os.path.join(scratch, f"{item}.png"),
                    "-depth", "8", f"RGBA:{raw}"], check=True)

    data = open(raw, "rb").read()
    size = int((len(data) // 4) ** 0.5)
    return [[data[(y * size + x) * 4 + 3] > OPAQUE for x in range(size)] for y in range(size)]


def eroded(mask):
    """Drop every pixel touching the outside, pulling the shape one in from the slot edge."""
    size = len(mask)

    def solid(x, y):
        return 0 <= x < size and 0 <= y < size and mask[y][x]

    return [[mask[y][x] and all(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
             for x in range(size)] for y in range(size)]


def outline(mask):
    """Just the rim: a filled pixel with at least one empty side."""
    size = len(mask)

    def solid(x, y):
        return 0 <= x < size and 0 <= y < size and mask[y][x]

    return [[INK if mask[y][x] and not all(solid(x + dx, y + dy)
                                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
             else CLEAR for x in range(size)] for y in range(size)]


def write_png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


if __name__ == "__main__":
    scratch = tempfile.mkdtemp(prefix="map-plus-plus-")
    write_png(os.path.join(SPRITES, "empty_map_slot.png"),
              outline(eroded(vanilla_alpha("map", scratch))))
