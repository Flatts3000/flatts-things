"""Write the cauldron transform data map and its tag.

The pairs live here rather than in Java for the same reason the plate variants do: the two sides
need different data, and a table beats seventeen hand-written JSON files that can drift from each
other. Run it after changing PAIRS; the output is committed.
"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "flattsthings")

COLOURS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]

# (input item, result item). Concrete first, in vanilla's own dye order, then mud.
PAIRS = [(f"minecraft:{c}_concrete_powder", f"minecraft:{c}_concrete") for c in COLOURS]
PAIRS.append(("minecraft:dirt", "minecraft:mud"))


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    return path


def main():
    written = [
        write(os.path.join(DATA, "data_maps", "item", "cauldron_transform.json"), {
            "values": {
                source: {"result": result} for source, result in PAIRS
            }
        }),
        # NOT "replace": this tag is ours, but a pack widening it must not have its additions
        # dropped, and tags merge.
        write(os.path.join(DATA, "tags", "item", "cauldron_transformable.json"), {
            "values": [source for source, _ in PAIRS]
        }),
    ]
    for path in written:
        print("wrote", os.path.relpath(path, REPO))


if __name__ == "__main__":
    main()
