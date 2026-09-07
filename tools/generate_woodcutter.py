"""Draw the woodcutter's base textures.

THE BASE ONLY. The saw blade stays vanilla's `minecraft:block/stonecutter_saw`, on purpose: a blade
is steel whatever bench it is bolted to, it is the part a player already recognises as "this block
cuts things", and redrawing it would be inventing a difference where there is none. What changes is
the body underneath, which was borrowing vanilla plank and log textures and so read as a stonecutter
someone had reskinned.

DRAWN FROM NUMBERS, NOT DERIVED FROM VANILLA. Recolouring Mojang's textures would ship a derivative
of their art; everything here is generated, which is the same reason `generate_plates.py` exists.

Deterministic: seeded with `zlib.crc32`, never `hash()`. Python salts string hashing per interpreter
run, so a `hash()` seed redraws every texture on every invocation - a diff with nothing changed in
it. That exact bug shipped once in the plate generator.
"""
import os
import random
import zlib

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(REPO, "src", "main", "resources", "assets", "flattsthings",
                        "textures", "block")

# A warmer, slightly darker oak than vanilla's planks, so the block reads as its own thing rather
# than as a crafting table that wandered off.
BASE = (168, 133, 84)
DARK = (128, 99, 60)
LIGHT = (196, 161, 108)

# The blade slot on the top face, in the same place vanilla's saw element passes through.
SLOT = (74, 58, 36)


def _shift(colour, amount):
    return tuple(min(255, max(0, channel + amount)) for channel in colour)


def _jittered(colour, rng, spread):
    return _shift(colour, rng.randint(-spread, spread)) + (255,)


def _planks(px, rng, vertical=False):
    """Four boards with offset seams, the same construction the plates use."""
    for y in range(16):
        for x in range(16):
            u, w = (y, x) if vertical else (x, y)
            board = w // 4
            colour = BASE
            if w % 4 == 3:
                colour = DARK
            elif w % 4 == 0:
                colour = _shift(BASE, 8)
            if u == (board * 5 + 3) % 16:
                colour = DARK
            px[x, y] = _jittered(colour, rng, 7)


def _bevel(px):
    """Light along the top and left, dark along the bottom and right, so the body has an edge."""
    for i in range(16):
        px[i, 0] = LIGHT + (255,)
        px[0, i] = LIGHT + (255,)
        px[i, 15] = DARK + (255,)
        px[15, i] = DARK + (255,)


def top() -> Image.Image:
    """The bench top: boards, with the slot the blade rises through."""
    rng = random.Random(zlib.crc32(b"woodcutter_top"))
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    _planks(px, rng)
    # A two-pixel slot down the middle, running the way the saw does. Darkened rather than black so
    # it still reads as a recess in wood rather than a hole through the block.
    for y in range(2, 14):
        for x in (7, 8):
            px[x, y] = _jittered(SLOT, rng, 6)
    # The lip on each side of the slot catches the light, which is what makes it look cut INTO the
    # surface instead of painted on it.
    for y in range(2, 14):
        px[6, y] = _jittered(_shift(BASE, 14), rng, 4)
        px[9, y] = _jittered(_shift(BASE, 14), rng, 4)
    _bevel(px)
    return img


def side() -> Image.Image:
    """The body: plain boards, vertical, so the grain runs the way a bench leg would."""
    rng = random.Random(zlib.crc32(b"woodcutter_side"))
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    _planks(px, rng, vertical=True)
    _bevel(px)
    return img


def bottom() -> Image.Image:
    """The underside. Boards, no slot: nothing passes through here."""
    rng = random.Random(zlib.crc32(b"woodcutter_bottom"))
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    _planks(px, rng)
    _bevel(px)
    return img


FACES = {
    "woodcutter_top": top,
    "woodcutter_side": side,
    "woodcutter_bottom": bottom,
}


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    for name, draw in FACES.items():
        path = os.path.join(TEXTURES, name + ".png")
        draw().save(path)
        print("wrote", os.path.relpath(path, REPO))


if __name__ == "__main__":
    main()
