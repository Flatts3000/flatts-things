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

import argparse
import json
import random
import sys
import zlib
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from plate_variants import VARIANTS, Variant, block_id, family, vanilla_plate  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
NS = "flattsthings"

# Rebound by _set_roots so the generator can write somewhere other than the repo. That exists for
# tools/test_generate_plates.py, which has to run this twice and compare, and must not do that by
# writing over the committed tree.
ASSETS = ROOT / "src/main/resources/assets/flattsthings"
DATA = ROOT / "src/main/resources/data/flattsthings"
VANILLA_DATA = ROOT / "src/main/resources/data/minecraft"


def _set_roots(base: Path) -> None:
    """Point every output path at `base` instead of the repo root."""
    global ASSETS, DATA, VANILLA_DATA
    ASSETS = base / "src/main/resources/assets/flattsthings"
    DATA = base / "src/main/resources/data/flattsthings"
    VANILLA_DATA = base / "src/main/resources/data/minecraft"

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
    # Shapeless, and deliberately NOT reversible: there is no recipe back to the vanilla plate.
    #
    # REDSTONE, NOT AN ENDER PEARL (ruling 2026-09-05, issue #6). The pearl read well and gated
    # badly. The problem this block solves - a cow opening your door, an arrow tripping your plate -
    # is one players hit in their first hours, and a pearl puts the fix behind finding endermen, so
    # the annoyance outlived the solution by a long way. Redstone is what a player already has in
    # hand the first time they wire a door, and it says what the block is: an ordinary redstone
    # component with one property tuned differently.
    #
    # Cheapness is not a risk here. A vanilla plate is not made obsolete by this one; they do
    # different jobs, and a mob farm still wants a plate that fires for mobs.
    #
    # THE CONDITION IS WHAT MAKES THE CONFIG SWITCH REAL. There is no runtime call that removes a
    # loaded recipe, so a switch that only hid the item would leave it craftable, in the recipe book
    # and in JEI. NeoForge evaluates conditions while reading the file, so a recipe whose feature is
    # off is never loaded at all. The type name is registered by FTConditions and is part of this
    # data format: renaming it there silently drops all fourteen recipes.
    write_json(DATA / f"recipe/{bid}.json", {
        "neoforge:conditions": [
            {"type": f"{NS}:feature_enabled", "feature": "player_pressure_plates"},
        ],
        "type": "minecraft:crafting_shapeless",
        "category": "redstone",
        "ingredients": [vanilla_plate(v), "minecraft:redstone"],
        "result": {"id": f"{NS}:{bid}", "count": 1},
    })


# Keys that are not per-variant. The generator owns en_us.json wholesale, so anything hand-added to
# that file is silently erased on the next run - which is exactly the kind of quiet loss the rest of
# this repo is built to prevent. New non-plate strings go here.
STATIC_LANG = {
    "itemGroup.flattsthings": "Flatts's Things",
    "key.flattsthings.toggle_auto_swap": "Toggle Tool Auto-Swap",
    "message.flattsthings.auto_swap.on": "Tool auto-swap on",
    "message.flattsthings.auto_swap.off": "Tool auto-swap off",
    "message.flattsthings.auto_swap.unavailable":
        "Tool auto-swap is turned off in this pack's config",
}


def write_lang() -> None:
    entries = dict(STATIC_LANG)
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out", type=Path, default=ROOT,
        help="repo root to write under (default: this repo). Used by the determinism test.")
    args = parser.parse_args()
    _set_roots(args.out)

    for v in VARIANTS:
        generate(v)
    write_lang()
    write_tags()
    print(f"generated resources for {len(VARIANTS)} plate variants")


if __name__ == "__main__":
    main()
