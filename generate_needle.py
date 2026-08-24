#!/usr/bin/env python3
"""Draw the compass needle.

The old one was a fat wedge sitting in the top ten rows: both ends looked much the
same at a glance, and because it was not centred in its own sprite it swung about a
point outside itself when the heading changed.

This one is one arrow in one colour: a barbed head at the front, a split tail at the
back. Two shapes at opposite ends say which way it points without needing a second
colour to help, and one colour reads better at sixteen pixels than two do. Centred, so
rotation turns it on the spot.
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/map-plus-plus/textures/gui/sprites/needle.png")

# R = red head, W = pale tail, . = nothing. Drawn on a 16x16 field, centred on the
# midpoint so the sprite turns about its own middle.
ART = """
................
................
.......##.......
......####......
.....######.....
....########....
...##########...
..############..
.....######.....
.....######.....
.....######.....
....###..###....
...###....###...
..###......###..
................
................
"""

RED = (0xD8, 0x3A, 0x3A, 0xFF)
NONE = (0, 0, 0, 0)
EDGE = (0x1A, 0x1A, 0x1A, 0xFF)


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    art = [line for line in ART.strip("\n").split("\n")]
    assert len(art) == 16 and all(len(r) == 16 for r in art), "the field must be 16x16"

    fill = {"#": RED, ".": NONE}
    grid = [[fill[c] for c in row] for row in art]

    # A dark edge on every side that faces nothing, so the needle reads against pale
    # map squares as well as dark ones.
    edged = [row[:] for row in grid]
    for y in range(16):
        for x in range(16):
            if grid[y][x] != NONE:
                continue
            touches = any(
                0 <= y + dy < 16 and 0 <= x + dx < 16 and grid[y + dy][x + dx] != NONE
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))
            )
            if touches:
                edged[y][x] = EDGE

    write_png(OUT, edged)
    print(f"wrote {os.path.relpath(OUT, HERE)}")


if __name__ == "__main__":
    main()
