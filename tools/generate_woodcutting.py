"""Write the wood stonecutting recipes and their unlock advancements.

These are a recipe type of this mod's own, read by the woodcutter block. An earlier version of this
generator wrote `minecraft:stonecutting` recipes so that the VANILLA stonecutter would cut wood; that
was rejected (owner, 2026-09-07) because a stone saw cutting planks is the wrong block for the job.
Nothing here touches the stonecutter now. The families below are the twelve that have planks,
stairs and a slab in 26.1; they are listed here rather than derived because the generator has no
registry to walk, and the GameTest closes the loop from the other end by resolving every one of these
through the real recipe lookup.

RATIOS ARE COPIED FROM STONE, NOT INVENTED. Vanilla cuts one stone into one stair (against six for
four on a bench, so a third cheaper) and one stone into two slabs (exactly the bench rate). Wood gets
the same deal and no better, because parity with the stonecutter is the entire argument for this
existing.

ADVANCEMENTS ARE NOT OPTIONAL, and the sweep that says so was written after fourteen plate recipes
shipped without them (#43). A recipe with no unlock advancement never appears in the recipe book, and
under `doLimitedCrafting` it cannot be used at all - the feature looks switched on and does nothing.
`every_mod_recipe_is_unlocked_by_an_advancement` caught these the moment they were added, which is
the whole reason that test exists.
"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "flattsthings")
RECIPES = os.path.join(DATA, "recipe")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes", "building_blocks")

FEATURE = "wood_cutting"

WOODS = [
    "acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak",
    "jungle", "mangrove", "oak", "pale_oak", "spruce", "warped",
]

# (suffix, count). Deliberately only the two shapes nobody argues about: a door or a sign has its own
# material cost and is not obviously "cutting".
SHAPES = [("stairs", 1), ("slab", 2)]

# A LOG BELONGS ON A SAW BENCH, and the first version did not take one - reported with a screenshot
# of a log sitting in the input offering nothing.
#
# Each family is (planks, planks per log, log, bark, stripped log, stripped bark). The forms are not
# uniform and the names are the whole reason this is a table: the nine ordinary woods have
# log/wood/stripped, the two nether ones have stem/hyphae, and BAMBOO has neither a bark form nor
# vanilla's four-planks rate - a bamboo block yields two.
#
# RATES, EACH ANCHORED TO A BENCH RECIPE:
#   * planks: exactly vanilla's, which takes the whole #<family>_logs tag, so every form gives the
#     same count. Four, or two for bamboo.
#   * stripping and barking: one for one. Vanilla charges four logs for three wood, so this is a
#     25 percent saving - the same size of saving the stonecutter already grants on stairs, which is
#     the ratio this whole feature is argued from. Stripping is free with an axe anyway, at the cost
#     of durability the saw does not charge.
#
# Nothing turns planks back into logs in vanilla, checked, so none of this closes a loop.
LOG_FAMILIES = [
    ("acacia", 4, "acacia_log", "acacia_wood"),
    ("birch", 4, "birch_log", "birch_wood"),
    ("cherry", 4, "cherry_log", "cherry_wood"),
    ("dark_oak", 4, "dark_oak_log", "dark_oak_wood"),
    ("jungle", 4, "jungle_log", "jungle_wood"),
    ("mangrove", 4, "mangrove_log", "mangrove_wood"),
    ("oak", 4, "oak_log", "oak_wood"),
    ("pale_oak", 4, "pale_oak_log", "pale_oak_wood"),
    ("spruce", 4, "spruce_log", "spruce_wood"),
    ("crimson", 4, "crimson_stem", "crimson_hyphae"),
    ("warped", 4, "warped_stem", "warped_hyphae"),
    ("bamboo", 2, "bamboo_block", None),
]

CONDITION = [{"type": "flattsthings:feature_enabled", "feature": FEATURE}]


def cut(source, result, count):
    """One recipe: a name, its json, and the item it is unlocked by holding."""
    name = "{}_from_{}_woodcutting".format(result, source)
    return name, {
        "neoforge:conditions": CONDITION,
        "type": "flattsthings:wood_cutting",
        "ingredient": "minecraft:" + source,
        "result": {"id": "minecraft:" + result, "count": count},
    }, source


def log_cuts():
    """Every cut that starts from a log, a bark block, or either stripped."""
    for family, per_log, log, bark in LOG_FAMILIES:
        planks = family + "_planks"
        stripped_log = "stripped_" + log
        forms = [log, stripped_log]
        if bark is not None:
            forms += [bark, "stripped_" + bark]

        # Every form makes planks, which is exactly what vanilla's shapeless recipe does.
        for form in forms:
            yield cut(form, planks, per_log)

        # And one step along the chain, where there is one to take. A stripped bark block is the end
        # of it: there is nothing left to take off.
        yield cut(log, stripped_log, 1)
        if bark is not None:
            yield cut(log, bark, 1)
            yield cut(bark, "stripped_" + bark, 1)
            yield cut(stripped_log, "stripped_" + bark, 1)


def recipes():
    """Yield (name, recipe json, unlock item) for every cut this mod ships."""
    for wood in WOODS:
        for suffix, count in SHAPES:
            yield cut(wood + "_planks", "{}_{}".format(wood, suffix), count)
    yield from log_cuts()


def advancement(name, source):
    """The unlock advancement for one recipe: hold what it cuts, learn the cut."""
    recipe = "flattsthings:" + name
    return {
        "neoforge:conditions": CONDITION,
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_planks": {
                "conditions": {"items": [{"items": "minecraft:" + source}]},
                "trigger": "minecraft:inventory_changed",
            },
            "has_the_recipe": {
                "conditions": {"recipe": recipe},
                "trigger": "minecraft:recipe_unlocked",
            },
        },
        "requirements": [["has_the_recipe", "has_planks"]],
        "rewards": {"recipes": [recipe]},
        "sends_telemetry_event": False,
    }


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def main():
    written = 0
    for name, payload, source in recipes():
        write(os.path.join(RECIPES, name + ".json"), payload)
        write(os.path.join(ADVANCEMENTS, name + ".json"), advancement(name, source))
        written += 1
    print("wrote {} wood cutting recipes and their advancements".format(written))


if __name__ == "__main__":
    main()
