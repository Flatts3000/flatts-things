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

CONDITION = [{"type": "flattsthings:feature_enabled", "feature": FEATURE}]


def recipes():
    """Yield (name, recipe json, wood) for every family and shape."""
    for wood in WOODS:
        for suffix, count in SHAPES:
            name = "{}_{}_from_{}_planks_woodcutting".format(wood, suffix, wood)
            yield name, {
                "neoforge:conditions": CONDITION,
                "type": "flattsthings:wood_cutting",
                "ingredient": "minecraft:{}_planks".format(wood),
                "result": {"id": "minecraft:{}_{}".format(wood, suffix), "count": count},
            }, wood


def advancement(name, wood):
    """The unlock advancement for one recipe: hold the planks, learn the cut."""
    recipe = "flattsthings:" + name
    return {
        "neoforge:conditions": CONDITION,
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_planks": {
                "conditions": {"items": [{"items": "minecraft:{}_planks".format(wood)}]},
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
    for name, payload, wood in recipes():
        write(os.path.join(RECIPES, name + ".json"), payload)
        write(os.path.join(ADVANCEMENTS, name + ".json"), advancement(name, wood))
        written += 1
    print("wrote {} wood cutting recipes and their advancements".format(written))


if __name__ == "__main__":
    main()
