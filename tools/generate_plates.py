"""Generate every per-variant resource for the player pressure plates.

Textures, blockstates, block models, client item definitions, loot tables, recipes and the lang
file are all produced from `tools/plate_variants.py`, so adding a plate is one row rather than
seven files. Run from the repo root:

    python tools/generate_plates.py

Deterministic: noise is seeded per variant, so re-running produces byte-identical files and an
empty diff. Everything it writes IS committed - the script is the source, the files are the build
input, and reviewing a texture change means reading a palette line rather than trusting a binary.
"""

from __future__ import annotations

import json
import random
import sys
import zlib
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from plate_variants import VARIANTS, Variant, block_id, family, vanilla_plate  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/flattsthings"
DATA = ROOT / "src/main/resources/data/flattsthings"
VANILLA_DATA = ROOT / "src/main/resources/data/minecraft"
NS = "flattsthings"

# A head-and-shoulders mark: the clearest "this one is about people" read available in 16 pixels.
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


def _luma(c: tuple[int, int, int]) -> float:
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def _shift(c: tuple[int, int, int], amount: int) -> tuple[int, int, int]:
    return tuple(min(255, max(0, channel + amount)) for channel in c)


def _jittered(c: tuple[int, int, int], rng: random.Random, spread: int) -> tuple[int, int, int, int]:
    j = rng.randint(-spread, spread)
    return _shift(c, j) + (255,)


def _draw_planks(px, v: Variant, rng: random.Random, vertical: bool) -> None:
    """Four boards with offset seams. Vertical flips the whole thing for bamboo."""
    for y in range(16):
        for x in range(16):
            u, w = (y, x) if vertical else (x, y)
            board = w // 4
            colour = v.base
            if w % 4 == 3:
                colour = v.dark                       # the groove between boards
            elif w % 4 == 0:
                colour = _shift(v.base, 8)            # a highlight along the top edge of each board
            # One seam per board, moved along so the boards do not line up into a grid.
            if u == (board * 5 + 3) % 16:
                colour = v.dark
            px[x, y] = _jittered(colour, rng, 7)


def _draw_noise(px, v: Variant, rng: random.Random) -> None:
    for y in range(16):
        for x in range(16):
            px[x, y] = _jittered(v.base, rng, 10)


def texture(v: Variant) -> Image.Image:
    # crc32, NOT hash(). Python salts string hashing per interpreter run unless PYTHONHASHSEED is
    # set, so seeding from hash() regenerated every texture differently on every invocation - a
    # fourteen-file diff each time the script was run, with nothing actually changed. Caught by
    # running the generator twice and diffing, which is the only way this shows up.
    rng = random.Random(zlib.crc32(v.material.encode("utf-8")))
    img = Image.new("RGBA", (16, 16))
    px = img.load()

    if v.style == "noise":
        _draw_noise(px, v, rng)
    else:
        _draw_planks(px, v, rng, vertical=(v.style == "vertical"))

    # A one-pixel bevel, light top-left and dark bottom-right. The plate model is nearly flat, so
    # without this the block reads as a sticker painted on the floor.
    for i in range(16):
        px[i, 0] = v.light + (255,)
        px[0, i] = v.light + (255,)
        px[i, 15] = v.dark + (255,)
        px[15, i] = v.dark + (255,)

    # The glyph is pushed AWAY from the base luminance rather than set to a fixed colour, so it
    # stays legible on pale oak and on dark oak without a per-variant tuning knob.
    glyph = _shift(v.base, -70 if _luma(v.base) > 140 else 78)
    for y, row in enumerate(FIGURE):
        for x, cell in enumerate(row):
            if cell == "#":
                px[x, y] = _jittered(glyph, rng, 5)

    return img


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8", newline="\n")


def generate(v: Variant) -> None:
    bid = block_id(v)

    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    texture(v).save(ASSETS / f"textures/block/{bid}.png", optimize=True)

    write_json(ASSETS / f"blockstates/{bid}.json", {
        "variants": {
            "powered=false": {"model": f"{NS}:block/{bid}"},
            "powered=true": {"model": f"{NS}:block/{bid}_down"},
        }
    })
    for suffix, parent in (("", "pressure_plate_up"), ("_down", "pressure_plate_down")):
        write_json(ASSETS / f"models/block/{bid}{suffix}.json", {
            "parent": f"minecraft:block/{parent}",
            "textures": {"texture": f"{NS}:block/{bid}"},
        })
    write_json(ASSETS / f"items/{bid}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:block/{bid}"}
    })
    write_json(DATA / f"loot_table/blocks/{bid}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    # Shapeless, and deliberately NOT reversible: there is no recipe back to the vanilla plate, so
    # the ender pearl is spent rather than borrowed.
    write_json(DATA / f"recipe/{bid}.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [vanilla_plate(v), "minecraft:ender_pearl"],
        "result": {"id": f"{NS}:{bid}", "count": 1},
    })


def write_lang() -> None:
    entries = {"itemGroup.flattsthings": "Flatts's Things"}
    for v in VARIANTS:
        entries[f"block.{NS}.{block_id(v)}"] = f"{v.display} Player Pressure Plate"
    write_json(ASSETS / "lang/en_us.json", entries)


def write_tags() -> None:
    """Join the vanilla tags each counterpart is already in.

    This is mining parity, not decoration. A wooden plate is axe-mineable only because
    #minecraft:wooden_pressure_plates is inside #minecraft:mineable/axe, and the two stone plates are
    listed in #minecraft:mineable/pickaxe individually rather than through their own tag - so joining
    the wood tag is enough for the twelve, and the two need a pickaxe entry as well. Joining the two
    family tags also puts every plate in #minecraft:pressure_plates for free, because that tag is
    defined as the union of them plus the weighted pair.

    These files sit under data/minecraft/ in this mod's own jar. Tags MERGE across data packs rather
    than the top file winning, which is what makes adding to another namespace's tag legitimate here
    and is the opposite of how a recipe at the same path would behave. No "replace" key, ever.
    """
    wood = [f"{NS}:{block_id(v)}" for v in VARIANTS if family(v) == "wood"]
    stone = [f"{NS}:{block_id(v)}" for v in VARIANTS if family(v) == "stone"]

    write_json(VANILLA_DATA / "tags/block/wooden_pressure_plates.json", {"values": wood})
    write_json(VANILLA_DATA / "tags/block/stone_pressure_plates.json", {"values": stone})
    write_json(VANILLA_DATA / "tags/block/mineable/pickaxe.json", {"values": stone})
    write_json(VANILLA_DATA / "tags/item/wooden_pressure_plates.json", {"values": wood})


def main() -> None:
    for v in VARIANTS:
        generate(v)
    write_lang()
    write_tags()
    print(f"generated resources for {len(VARIANTS)} plate variants")


if __name__ == "__main__":
    main()
