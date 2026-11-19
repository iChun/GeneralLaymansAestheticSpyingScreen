"""Textures for the gadgets: animated hard light, a red alert glow, the tablet's screen, and the camo effect icon."""
import json, math, os, random
from PIL import Image

ASSETS = r'E:\Downloads\GeneralLaymansAestheticSpyingScreen\src\main\resources\assets\generallaymansaestheticspyingscreen\textures'
BLOCK = os.path.join(ASSETS, 'block')
EFFECT = os.path.join(ASSETS, 'mob_effect')
os.makedirs(EFFECT, exist_ok=True)
rnd = random.Random(11)


def clamp(v):
    return max(0, min(255, int(v)))


# Hard light: a see-through cyan pane with a bright frame, a fine grid, and a band of light running up (8 frames).
FRAMES = 8
img = Image.new('RGBA', (16, 16 * FRAMES))
for f in range(FRAMES):
    band = (f / FRAMES) * 16
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            grid = x % 4 == 0 or y % 4 == 0
            d = (y - (16 - band)) % 16
            sweep = max(0.0, 1.0 - abs(d - 1.5) / 2.0)
            twinkle = rnd.random() < 0.025
            r, g, b, a = 40, 190, 255, 70
            if grid:
                r, g, b, a = 90, 220, 255, 105
            if edge:
                r, g, b, a = 160, 245, 255, 190
            r += 90 * sweep
            g += 40 * sweep
            a += 80 * sweep
            if twinkle:
                r, g, b, a = 220, 255, 255, 200
            img.putpixel((x, y + 16 * f), (clamp(r), clamp(g), clamp(b), clamp(a)))
img.save(os.path.join(BLOCK, 'hard_light.png'))
with open(os.path.join(BLOCK, 'hard_light.png.mcmeta'), 'w') as fh:
    json.dump({'animation': {'frametime': 3, 'interpolate': True}}, fh, indent=2)

# Red alert glow (the motion sensor's eye when it sees someone).
img = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 7.5) / 10.6
        img.putpixel((x, y), (255, clamp(80 - 60 * d), clamp(90 - 70 * d), 255))
img.save(os.path.join(BLOCK, 'holo_alert.png'))

# The tablet's screen: dark glass with a little cyan interface.
img = Image.new('RGBA', (16, 16), (8, 16, 22, 255))
for x in range(1, 15):
    img.putpixel((x, 1), (60, 230, 255, 255))
for y in range(3, 13):
    for x in range(2, 14):
        if (x + y) % 5 == 0 and rnd.random() < 0.6:
            img.putpixel((x, y), (24, 90, 110, 255))
for x, y in [(3, 4), (4, 4), (5, 4), (3, 6), (4, 6), (9, 9), (10, 9), (11, 9), (12, 9), (9, 11), (10, 11)]:
    img.putpixel((x, y), (60, 230, 255, 255))
img.putpixel((13, 3), (255, 60, 70, 255))
img.save(os.path.join(BLOCK, 'tablet_screen.png'))

# The optical camo effect icon (18x18): an eye half fading into dots.
img = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
for y in range(18):
    for x in range(18):
        dx, dy = (x - 8.5) / 7.5, (y - 8.5) / 4.2
        inside = dx * dx + dy * dy <= 1.0
        rim = inside and dx * dx + dy * dy > 0.62
        pupil = math.hypot(x - 8.5, y - 8.5) < 2.3
        if inside:
            fade = x / 17.0
            if rnd.random() < fade * 0.8:
                continue
            if pupil:
                img.putpixel((x, y), (20, 60, 80, 255))
            elif rim:
                img.putpixel((x, y), (120, 240, 255, 255))
            else:
                img.putpixel((x, y), (60, 200, 240, 200))
img.save(os.path.join(EFFECT, 'optical_camo.png'))
print('ok')
