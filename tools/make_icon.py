"""Draws the mod icon (assets/.../icon.png, 128x128): a GLASS screen in a dark frame, its glass with the cyan nano grid,
half of it "cloaked" (showing sky and ground behind it). Pure Python (no image library): python tools/make_icon.py"""
import math
import os
import struct
import zlib

SIZE = 128
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "generallaymansaestheticspyingscreen", "icon.png")


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def pixel(x, y):
    cx, cy = x + 0.5, y + 0.5
    # Rounded dark frame.
    r = 18
    dx = max(r - cx, 0, cx - (SIZE - r))
    dy = max(r - cy, 0, cy - (SIZE - r))
    if dx * dx + dy * dy > r * r:
        return (0, 0, 0, 0)
    frame = (14, 20, 30)
    edge = min(cx, cy, SIZE - cx, SIZE - cy)
    if edge < 14:
        light = 1 - edge / 14 * 0.4
        return tuple(int(c * light) for c in frame) + (255,)
    # The glass: x in 14..114.
    gx, gy = (cx - 14) / 100, (cy - 14) / 100
    cloaked = gx + gy * 0.35 > 0.62  # a diagonal: right of it the screen shows what is behind
    if cloaked:
        horizon = 0.62
        if gy < horizon:
            col = mix((120, 175, 235), (190, 225, 250), gy / horizon)
        else:
            col = mix((95, 150, 60), (70, 115, 45), (gy - horizon) / (1 - horizon))
    else:
        col = mix((10, 45, 60), (20, 80, 95), gy)
        # Nano grid.
        cell = 12.5
        fx, fy = ((cx - 14) % cell) / cell, ((cy - 14) % cell) / cell
        line = min(fx, 1 - fx, fy, 1 - fy)
        if line < 0.09:
            col = mix(col, (90, 235, 255), 0.75)
        # A sweeping highlight.
        band = math.exp(-((gx - gy * 0.5 - 0.15) ** 2) / 0.004)
        col = mix(col, (150, 245, 255), band * 0.5)
    # The wave front between the two: glowing.
    d = abs(gx + gy * 0.35 - 0.62)
    glow = math.exp(-(d * d) / 0.0006)
    col = mix(col, (170, 250, 255), glow * 0.9)
    # Bright inner rim.
    if edge < 16:
        col = mix(col, (90, 235, 255), 0.85)
    return tuple(int(max(0, min(255, c))) for c in col) + (255,)


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    # 2x2 supersampling for smooth edges.
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            samples = [pixel(x + ox - 0.25, y + oy - 0.25) for ox in (0, 0.5) for oy in (0, 0.5)]
            a = sum(s[3] for s in samples) / 4
            if a == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append(tuple(int(sum(s[i] * s[3] for s in samples) / (a * 4)) for i in range(3)) + (int(a),))
        rows.append(row)
    write_png(os.path.normpath(OUT), SIZE, SIZE, rows)
    print("wrote", os.path.normpath(OUT))
