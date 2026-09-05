"""Generate the mod's block textures.

Every PNG under assets/flattsthings/textures/ is produced here rather than drawn by hand, so a
texture is a diff you can read instead of a binary you have to trust. Run from the repo root:

    python tools/make_textures.py

Deterministic: the noise is seeded, so re-running produces a byte-identical file and a no-op diff.
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/flattsthings/textures/block"

# Sampled to sit beside vanilla stone rather than match it exactly - the plate has to read as a
# different block at a glance, or the feature is invisible in a build.
BASE = (124, 124, 128)
DARK = (98, 98, 104)
LIGHT = (146, 146, 150)
GLYPH = (191, 194, 203)

# A head-and-shoulders mark, the clearest "this one is about people" read available in 16 pixels.
# One row per y, one character per x: '#' is the glyph, '.' is untouched plate.
FIGURE = [
    "................",
    "................",
    "................",
    "................",
    "......####......",
    "......####......",
    "......####......",
    "................",
    "....########....",
    "...##########...",
    "...##########...",
    "...###....###...",
    "...##......##...",
    "................",
    "................",
    "................",
]


def player_pressure_plate() -> Image.Image:
    rng = random.Random(0xF1A115)
    img = Image.new("RGBA", (16, 16))
    px = img.load()

    for y in range(16):
        for x in range(16):
            jitter = rng.randint(-9, 9)
            px[x, y] = tuple(min(255, max(0, c + jitter)) for c in BASE) + (255,)

    # A one-pixel bevel: light on the top and left, dark on the bottom and right. The plate model
    # is nearly flat, so without this the block reads as a sticker on the floor.
    for i in range(16):
        px[i, 0] = LIGHT + (255,)
        px[0, i] = LIGHT + (255,)
        px[i, 15] = DARK + (255,)
        px[15, i] = DARK + (255,)

    for y, row in enumerate(FIGURE):
        for x, cell in enumerate(row):
            if cell == "#":
                jitter = rng.randint(-6, 6)
                px[x, y] = tuple(min(255, max(0, c + jitter)) for c in GLYPH) + (255,)

    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / "player_pressure_plate.png"
    player_pressure_plate().save(target, optimize=True)
    print(f"wrote {target}")


if __name__ == "__main__":
    main()
