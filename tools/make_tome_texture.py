"""Draws the GLASS Tome's item texture (16x16): a dark book with a cyan spine and edges and a little glass screen with
the nano grid on its cover. Pure Python: python tools/make_tome_texture.py"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "generallaymansaestheticspyingscreen",
                   "textures", "item", "glass_tome.png")

_ = None                       # transparent
K = (10, 18, 28, 255)          # cover, dark
D = (18, 32, 46, 255)          # cover, lighter
S = (24, 120, 140, 255)        # spine
C = (60, 230, 255, 255)        # cyan edge
G = (20, 70, 90, 255)          # screen glass
L = (110, 240, 255, 255)       # screen grid line
P = (225, 235, 240, 255)       # pages
Q = (170, 185, 195, 255)       # page shade

ART = [
    [_, _, C, C, C, C, C, C, C, C, C, C, C, _, _, _],
    [_, S, K, K, K, K, K, K, K, K, K, K, K, C, _, _],
    [_, S, K, D, D, D, D, D, D, D, D, D, K, C, P, _],
    [_, S, K, D, C, C, C, C, C, C, C, D, K, C, P, _],
    [_, S, K, D, C, G, L, G, L, G, C, D, K, C, Q, _],
    [_, S, K, D, C, L, L, L, L, L, C, D, K, C, P, _],
    [_, S, K, D, C, G, L, G, L, G, C, D, K, C, P, _],
    [_, S, K, D, C, L, L, L, L, L, C, D, K, C, Q, _],
    [_, S, K, D, C, G, L, G, L, G, C, D, K, C, P, _],
    [_, S, K, D, C, C, C, C, C, C, C, D, K, C, P, _],
    [_, S, K, D, D, D, D, D, D, D, D, D, K, C, Q, _],
    [_, S, K, D, D, C, C, C, C, C, D, D, K, C, P, _],
    [_, S, K, D, D, D, D, D, D, D, D, D, K, C, P, _],
    [_, S, K, K, K, K, K, K, K, K, K, K, K, C, Q, _],
    [_, _, C, C, C, C, C, C, C, C, C, C, C, P, P, _],
    [_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _],
]


def write_png(path, rows):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in (p or (0, 0, 0, 0))) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    write_png(os.path.normpath(OUT), ART)
    print("wrote", os.path.normpath(OUT))
