#!/usr/bin/env python3
"""Draw the Flatts's Things avatar: the player mark, on a plate, from geometry.

NOT AI OUTPUT, and that is a rule rather than a preference. ModJam 2026's terms say "AI-generated
project avatars and gallery images are not allowed", so the artwork here is drawn cell by cell from
numbers, and the gallery is real in-game screenshots. Recompile's branding.md carries the same
constraint for the same reason.

The mark is the head-and-shoulders figure the pressure plates already carry, imported from
tools/generate_plates.py rather than redrawn, so the avatar and the in-game texture cannot drift
apart. If the plate mark ever changes, this changes with it.

Writes both copies in one run:
  branding/logo.png                  the CurseForge project avatar
  src/main/resources/logo.png        the in-jar mod-list icon, via logoFile in neoforge.mods.toml

Usage
-----
    python branding/compose_logo.py
"""

from __future__ import annotations

import random
import sys
import zlib
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(REPO / "tools"))
from generate_plates import FIGURE  # noqa: E402

BRANDING_OUT = HERE / "logo.png"
IN_JAR_OUT = REPO / "src" / "main" / "resources" / "logo.png"

OUT_SIZE = 400
GRID = 50               # low-res cells across; must divide OUT_SIZE exactly
assert OUT_SIZE % GRID == 0

# Deliberately not the stone plate's own palette. The avatar sits in a grid of other mods' icons and
# has to be picked out at 40 pixels, so the plate reads lighter and the ground darker than either
# does in game.
BG_TOP = (34, 36, 44)
BG_BOTTOM = (52, 55, 66)
PLATE = (150, 152, 160)
PLATE_LIGHT = (186, 189, 198)
PLATE_DARK = (104, 106, 114)
SHADOW = (24, 25, 31)
GLYPH = (58, 60, 70)

PLATE_ORIGIN = 9        # the mark is 16 cells doubled to 32, centred in 50 with a 9-cell margin
SCALE = 2
PLATE_SPAN = len(FIGURE) * SCALE


def draw() -> Image.Image:
    img = Image.new("RGB", (GRID, GRID))
    px = img.load()

    # Ground: a vertical ramp, so the plate has something to sit on rather than floating on flat.
    for y in range(GRID):
        t = y / (GRID - 1)
        px_row = tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))
        for x in range(GRID):
            px[x, y] = px_row

    x0 = y0 = PLATE_ORIGIN
    x1 = y1 = PLATE_ORIGIN + PLATE_SPAN - 1

    # A one-cell drop shadow, offset down-right. Without it the plate reads as a flat sticker, which
    # is the same problem the block texture's bevel solves in game.
    for y in range(y0 + 1, y1 + 2):
        for x in range(x0 + 1, x1 + 2):
            px[x, y] = SHADOW

    # Seeded jitter, so the face reads as stone rather than as flat grey. crc32 rather than hash(),
    # for the reason tools/generate_plates.py carries at length: Python salts string hashing per
    # process, so hash() would redraw the avatar differently on every run.
    rng = random.Random(zlib.crc32(b"flattsthings-avatar"))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            jitter = rng.randint(-8, 8)
            px[x, y] = tuple(min(255, max(0, c + jitter)) for c in PLATE)
    for i in range(x0, x1 + 1):
        px[i, y0] = PLATE_LIGHT
        px[x0, i] = PLATE_LIGHT
        px[i, y1] = PLATE_DARK
        px[x1, i] = PLATE_DARK

    # The mark itself, doubled from the 16x16 the plates carry.
    for row, line in enumerate(FIGURE):
        for col, cell in enumerate(line):
            if cell != "#":
                continue
            for dy in range(SCALE):
                for dx in range(SCALE):
                    jitter = rng.randint(-5, 5)
                    px[x0 + col * SCALE + dx, y0 + row * SCALE + dy] = tuple(
                        min(255, max(0, c + jitter)) for c in GLYPH)

    return img.resize((OUT_SIZE, OUT_SIZE), Image.NEAREST)


def main() -> None:
    logo = draw()
    for target in (BRANDING_OUT, IN_JAR_OUT):
        target.parent.mkdir(parents=True, exist_ok=True)
        logo.save(target, optimize=True)
        print(f"wrote {target.relative_to(REPO)} ({logo.size[0]}x{logo.size[1]})")


if __name__ == "__main__":
    main()
