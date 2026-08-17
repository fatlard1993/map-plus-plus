#!/usr/bin/env python3
"""Generate the compass needle used when a compass is equipped without a map.

Pure stdlib PNG writer (zlib + struct), no Pillow, deterministic: re-running
produces identical bytes. Same house pattern as the suite's icon generators.

The needle points UP at rest; the HUD rotates the sprite by the bearing, so the
art only has to be correct at zero degrees.

Usage: python3 generate_needle.py
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/map-plus-plus/textures/gui/sprites/needle.png")

SIZE = 16
CLEAR = (0, 0, 0, 0)

# Vanilla's map-marker red for the pointing half, and a muted tail, so it reads
# as a needle rather than an arrow cursor.
POINT = (200, 48, 48, 255)
TAIL = (216, 216, 216, 255)
EDGE = (32, 32, 32, 255)


def triangle(px, apex_y, base_y, half_at_base, color):
    """Isoceles triangle on the vertical centre line, apex at the top."""
    span = base_y - apex_y
    if span == 0:
        return
    cx = SIZE // 2
    for y in range(apex_y, base_y + 1):
        t = (y - apex_y) / span
        half = max(0, int(round(half_at_base * t)))
        for x in range(cx - half, cx + half + 1):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                px[y][x] = color


def outline(px, color):
    """One-pixel border wherever an opaque pixel touches a transparent one."""
    snapshot = [row[:] for row in px]
    for y in range(SIZE):
        for x in range(SIZE):
            if snapshot[y][x][3] != 0:
                continue
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < SIZE and 0 <= nx < SIZE and snapshot[ny][nx][3] != 0:
                    px[y][x] = color
                    break


def build():
    px = [[CLEAR] * SIZE for _ in range(SIZE)]
    # Pointing half from the top down to the waist, tail below it, narrower so
    # the direction stays unambiguous at a glance from across a room.
    triangle(px, 1, 8, 5, POINT)
    triangle(px, 14, 8, 3, TAIL)
    outline(px, EDGE)
    return px


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
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
    write_png(OUT, build())
