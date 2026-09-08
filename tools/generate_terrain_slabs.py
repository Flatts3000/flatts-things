"""Generate every resource for the terrain slabs: blockstates, models, item definitions, loot
tables, recipes, recipe advancements, tags and lang.

Run it with `python tools/generate_terrain_slabs.py`. The table it reads is
`tools/terrain_slab_variants.py`.

**This ships no art.** A slab of dirt is dirt, so the models point at vanilla's texture ids. That is
the whole difference from `generate_plates.py`, and it is why there is no seeded randomness here and
no pixel-comparison test: there is no image to be nondeterministic about.

**The loot tables are the interesting part, and they are not "drops itself".** Mining a grass block
gives you dirt; mining clay gives four clay balls; mining a snow block gives four snowballs. A slab
that dropped itself in those cases would be a slab that is not made of what it looks like, so the
rule applied here is:

    a slab drops HALF of what the full block drops, and where the full block drops a different
    BLOCK, the slab drops that block's slab

which puts a grass slab on the same footing as a grass block: break it and you have dirt, in the
same quantity of world you started with. `DROPS` below states it per family rather than deriving it,
because vanilla's own tables are not derivable from anything.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import ft_lang
from terrain_slab_variants import VARIANTS, Variant

ROOT = Path(__file__).resolve().parent.parent
NS = "flattsthings"
FEATURE = "terrain_slabs"

ASSETS = ROOT / "src/main/resources/assets" / NS
DATA = ROOT / "src/main/resources/data" / NS
VANILLA_DATA = ROOT / "src/main/resources/data/minecraft"


def _set_roots(base: Path) -> None:
    """Point the writers at a different tree, so a test can generate into a temp dir."""
    global ASSETS, DATA, VANILLA_DATA
    ASSETS = base / "src/main/resources/assets" / NS
    DATA = base / "src/main/resources/data" / NS
    VANILLA_DATA = base / "src/main/resources/data/minecraft"


# What one full vanilla block of each family gives you, and therefore what half of one should.
#
#   ("self", None)             drops its own slab, the ordinary case
#   ("slab", "dirt")           drops a DIFFERENT family's slab, because the full block drops that
#                              block (a grass block gives dirt, so half a grass block gives half)
#   ("item", "<id>", 2)        drops a plain item at half the full block's count
#
# Silk touch always returns the slab itself. That is added by the writer rather than stated here,
# because it is the same rule for every family and it is what vanilla does.
DROPS: dict[str, tuple] = {
    "dirt": ("self", None),
    "grass_block": ("slab", "dirt"),
    "mycelium": ("slab", "dirt"),
    "coarse_dirt": ("self", None),
    "rooted_dirt": ("self", None),
    "podzol": ("slab", "dirt"),
    "mud": ("self", None),
    "clay": ("item", "minecraft:clay_ball", 2),
    "gravel": ("self", None),
    "sand": ("self", None),
    "red_sand": ("self", None),
}

# Flint comes from a WHOLE block's worth of gravel, so only a double slab rolls it.
#
# Two earlier versions of this were wrong in different ways and both are worth recording.
#
# The first put flint in a SECOND POOL, so a gravel slab dropped the slab AND flint instead of one
# or the other. Vanilla's own table is a single nested `alternatives` - silk, else flint, else
# gravel - and the branches are exclusive. With Fortune III the fortune ladder tops out at 1.0, so
# every gravel slab broken with a Fortune III shovel returned itself plus a guaranteed flint: the
# block paid for itself and gravel slabs became a free renewable flint source.
#
# The obvious repair, nesting it the way vanilla does, still leaves an exploit. Three gravel craft
# six slabs, so if a half block rolled flint at a whole block's rate you would double your flint per
# gravel by cutting it up first. Halving the chance means inventing numbers Mojang never wrote, so
# the answer is the one that needs no new numbers: a DOUBLE slab is a whole block and rolls exactly
# what gravel rolls, and a half slab returns itself. Cutting gravel up and recombining it is then
# exactly neutral, which is the property worth having.
FLINT_FAMILIES = {"gravel"}


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8", newline="\n")


# --------------------------------------------------------------------------- models


def _box_faces(top_half: bool, overlay: bool) -> dict:
    """The faces of a half-height box, for the one family that cannot use vanilla's slab parent.

    Side UVs are cropped to the matching half of the texture, which is what puts a grass slab's
    dirt-to-grass transition where it belongs instead of squashing the whole gradient into eight
    pixels.

    Culling is asymmetric on purpose: a bottom slab's DOWN face sits on the block boundary and can be
    culled, while its UP face floats mid-block and must not be.
    """
    v0, v1 = (0, 8) if top_half else (8, 16)
    side = {"uv": [0, v0, 16, v1], "texture": "#overlay" if overlay else "#side"}
    if overlay:
        side["tintindex"] = 0
    faces = {d: dict(side, cullface=d) for d in ("north", "south", "east", "west")}
    if overlay:
        return faces
    faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#bottom"}
    faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#top", "tintindex": 0}
    faces["up" if top_half else "down"]["cullface"] = "up" if top_half else "down"
    return faces


def tinted_model(v: Variant, top_half: bool) -> dict:
    """A grass-style slab, built from elements because `block/slab` cannot tint or overlay.

    Two boxes at the same coordinates, exactly as vanilla's own `block/grass_block` does it: the
    first carries the dirt bottom, the tinted top and the plain side, the second lays the tinted
    overlay over the four sides. Copied in structure rather than in numbers, since the numbers are
    the half-height ones.
    """
    lo, hi = (8, 16) if top_half else (0, 8)
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": v.bottom,
            "bottom": v.bottom,
            "top": v.top,
            "side": v.side,
            "overlay": v.overlay,
        },
        "elements": [
            {"from": [0, lo, 0], "to": [16, hi, 16], "faces": _box_faces(top_half, False)},
            {"from": [0, lo, 0], "to": [16, hi, 16], "faces": _box_faces(top_half, True)},
        ],
    }


def plain_model(v: Variant, top_half: bool) -> dict:
    parent = "minecraft:block/slab_top" if top_half else "minecraft:block/slab"
    return {
        "parent": parent,
        "textures": {"bottom": v.bottom, "top": v.top, "side": v.side},
    }


# NOT `block/snow`, which was the obvious guess and is wrong. Vanilla's own podzol blockstate points
# its snowy variant at `block/grass_block_snow`, whose side texture is `block/grass_block_snow` - a
# dedicated snow-over-dirt edge, not the flat snow texture. Checked against the shipped assets after
# the guess produced a side that did not match any vanilla block.
SNOW_SIDE = "minecraft:block/grass_block_snow"


def write_models(v: Variant) -> None:
    build = tinted_model if v.tinted else plain_model
    write_json(ASSETS / "models/block" / f"{v.block_id}.json", build(v, False))
    write_json(ASSETS / "models/block" / f"{v.block_id}_top.json", build(v, True))
    if v.snowy:
        # THE SNOWY PAIR IS NEVER TINTED, even for grass, and that is vanilla's own choice rather
        # than a shortcut. Its `block/grass_block_snow` is a plain `cube_bottom_top` with no
        # tintindex anywhere - the sides are buried in snow and the top is under a snow layer, so
        # the biome colour has nothing left to colour. Building the snowy grass slab from the same
        # plain parent keeps it identical to the block it is half of.
        #
        # The snowy pair swaps the SIDE only, and keeps the family's own top.
        #
        # Vanilla is sloppier here and we are deliberately not copying it: its snowy podzol borrows
        # `grass_block_snow` wholesale, which carries the GRASS top texture. It gets away with that
        # because the top is under a snow layer and unseen. On a BOTTOM slab it would be seen, since
        # the snow sits a half block higher and the top face is exposed - so keeping podzol_top is
        # the correction that the slab shape makes necessary rather than a stylistic preference.
        snowed = v._replace(side=SNOW_SIDE)
        write_json(ASSETS / "models/block" / f"{v.block_id}_snow.json", plain_model(snowed, False))
        write_json(ASSETS / "models/block" / f"{v.block_id}_top_snow.json", plain_model(snowed, True))
    # The double has no model of its own: the blockstate points at the vanilla full block, which
    # every family already has. That is not laziness, it is the only version that cannot drift - a
    # double podzol slab IS a podzol block, and will stay identical to one through any texture pack
    # or any change Mojang makes.
    write_json(ASSETS / "items" / f"{v.block_id}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:block/{v.block_id}"}
    })


def write_blockstate(v: Variant) -> None:
    """Three variants, or six when the family carries the snowy property.

    A missing variant is not a compile error and not a test failure: the game logs one line and
    draws the missing-model cube. So the shape of this has to follow the block's real state
    definition, which is why `snowy` lives in the table rather than only in the Java.
    """
    if not v.snowy:
        variants = {
            "type=bottom": {"model": f"{NS}:block/{v.block_id}"},
            "type=top": {"model": f"{NS}:block/{v.block_id}_top"},
            "type=double": {"model": f"minecraft:block/{v.family}"},
        }
    else:
        variants = {}
        for snowy in (False, True):
            suffix = "_snow" if snowy else ""
            variants[f"type=bottom,snowy={str(snowy).lower()}"] = {
                "model": f"{NS}:block/{v.block_id}{suffix}"}
            variants[f"type=top,snowy={str(snowy).lower()}"] = {
                "model": f"{NS}:block/{v.block_id}_top{suffix}"}
            # A double is a whole block, so it borrows the vanilla block's own snowy model rather
            # than one of ours - which is exactly what a double podzol slab IS.
            # A double IS a whole block, so it uses exactly what vanilla's own block uses. For
            # podzol that is `grass_block_snow` and not `podzol_snow`, which does not exist.
            variants[f"type=double,snowy={str(snowy).lower()}"] = {
                "model": "minecraft:block/grass_block_snow" if snowy
                         else f"minecraft:block/{v.family}"}
    write_json(ASSETS / "blockstates" / f"{v.block_id}.json", {"variants": variants})


# --------------------------------------------------------------------------- loot


def _double_condition(block: str) -> dict:
    return {
        "condition": "minecraft:block_state_property",
        "block": block,
        "properties": {"type": "double"},
    }


def _silk_condition() -> dict:
    return {
        "condition": "minecraft:match_tool",
        "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
        ]}},
    }


def _self_entry(block: str, name: str) -> dict:
    """One slab, or two when it was a double. The count function is how every vanilla slab does it."""
    return {
        "type": "minecraft:item",
        "name": name,
        "functions": [
            {"function": "minecraft:set_count", "count": 2,
             "conditions": [_double_condition(block)]},
            {"function": "minecraft:explosion_decay"},
        ],
    }


def loot_table(v: Variant) -> dict:
    block = f"{NS}:{v.block_id}"
    kind = DROPS[v.family]
    silk = _self_entry(block, block)

    if kind[0] == "self":
        main = _self_entry(block, block)
    elif kind[0] == "slab":
        main = _self_entry(block, f"{NS}:{kind[1]}_slab")
    else:
        _, item, count = kind
        # Half a block gives `count`; a double IS a whole block, so it gives twice that and lands
        # back on vanilla's own number. The second function overwrites the first when the condition
        # matches, which is the same layering every vanilla slab table uses for its own count.
        main = {
            "type": "minecraft:item",
            "name": item,
            "functions": [
                {"function": "minecraft:set_count", "count": count},
                {"function": "minecraft:set_count", "count": count * 2,
                 "conditions": [_double_condition(block)]},
                {"function": "minecraft:explosion_decay"},
            ],
        }

    # NESTED, NOT TWO POOLS. Each pool rolls independently, so a second pool ADDS to whatever the
    # first gave. Vanilla's gravel table is one pool whose branches exclude each other, and that
    # exclusivity is the whole point: rolling flint COSTS you the block.
    otherwise = main
    if v.family in FLINT_FAMILIES:
        otherwise = {
            "type": "minecraft:alternatives",
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "children": [
                {"type": "minecraft:item", "name": "minecraft:flint",
                 "conditions": [
                     {"condition": "minecraft:table_bonus",
                      "enchantment": "minecraft:fortune",
                      "chances": [0.1, 0.14285715, 0.25, 1.0]},
                     _double_condition(block),
                 ]},
                main,
            ],
        }

    pools = [{
        "rolls": 1,
        "entries": [{
            "type": "minecraft:alternatives",
            "children": [
                dict(silk, conditions=[_silk_condition()]),
                otherwise,
            ],
        }],
    }]

    return {"type": "minecraft:block", "pools": pools}


# --------------------------------------------------------------------------- recipes and tags


def write_recipe(v: Variant) -> None:
    """Three blocks in a row give six slabs, which is vanilla's rate for every slab it ships.

    **Gravel takes six for twelve instead, and that is forced.** This mod's own gravel-to-flint
    recipe is SHAPELESS on exactly three gravel, so it matches any arrangement of three and no
    three-gravel shaped recipe can coexist with it: the recipe manager returns whichever it finds
    first, the player gets flint or a slab depending on load order, and nothing anywhere reports a
    problem. Six ingredients cannot match a shapeless three. The rate is identical, the batch is
    just bigger.

    A column was tried first, on the assumption the flint recipe was shaped. It was not, and the
    test caught that too. `every_terrain_slab_has_a_recipe_that_actually_crafts` found both, by
    resolving each recipe through the real lookup rather than checking the file exists.

    The feature condition is the only way to switch a recipe off: there is no runtime call that
    unloads one, and a recipe left loaded would still show in the recipe book and in JEI.
    """
    pattern = ["###", "###"] if v.batch_recipe else ["###"]
    count = 12 if v.batch_recipe else 6
    write_json(DATA / "recipe" / f"{v.block_id}.json", {
        "neoforge:conditions": [{"type": f"{NS}:feature_enabled", "feature": FEATURE}],
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": pattern,
        "key": {"#": f"minecraft:{v.family}"},
        "result": {"id": f"{NS}:{v.block_id}", "count": count},
    })
    write_json(DATA / "advancement/recipes/building_blocks" / f"{v.block_id}.json", {
        "neoforge:conditions": [{"type": f"{NS}:feature_enabled", "feature": FEATURE}],
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_ingredient": {
                "trigger": "minecraft:inventory_changed",
                "conditions": {"items": [{"items": f"minecraft:{v.family}"}]},
            },
            "has_the_recipe": {
                "trigger": "minecraft:recipe_unlocked",
                "conditions": {"recipe": f"{NS}:{v.block_id}"},
            },
        },
        "requirements": [["has_ingredient", "has_the_recipe"]],
        "rewards": {"recipes": [f"{NS}:{v.block_id}"]},
    })


def write_tags() -> None:
    """Mining parity, and joining vanilla's own slab tag.

    Plain string entries rather than `{"id": ..., "required": false}`, and that is the narrow case
    where it is allowed: these blocks are registered unconditionally in Java, so they exist or the
    mod did not load. The optional form is required only for something loaded from data behind a
    feature condition, which would otherwise take the whole vanilla tag down with it when switched
    off.
    """
    ids = [f"{NS}:{v.block_id}" for v in VARIANTS]
    write_json(VANILLA_DATA / "tags/block/mineable/shovel.json", {"values": ids})
    write_json(VANILLA_DATA / "tags/block/slabs.json", {"values": ids})
    write_json(VANILLA_DATA / "tags/item/slabs.json", {"values": ids})


def write_lang() -> None:
    """Delegated to `ft_lang`, the single owner of this file. See that module for why.

    Short version: the plate generator also writes names, and whichever generator ran last used to
    win, deleting the other's. One owner assembling from every table makes the result the same
    whichever you run, and in any order.
    """
    ft_lang.write(ASSETS)


def generate(v: Variant) -> None:
    write_blockstate(v)
    write_models(v)
    write_json(DATA / "loot_table/blocks" / f"{v.block_id}.json", loot_table(v))
    write_recipe(v)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT,
                        help="write into this tree instead of the repo (for tests)")
    args = parser.parse_args()
    if args.out != ROOT:
        _set_roots(args.out)
    for v in VARIANTS:
        generate(v)
    write_tags()
    write_lang()
    print(f"wrote {len(VARIANTS)} terrain slabs")


if __name__ == "__main__":
    main()
