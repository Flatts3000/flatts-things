"""The committed wood cutting recipes must match what the generator writes.

Same reason as the cauldron generator's test: nothing else notices if WOODS or SHAPES drifts from the
committed JSON, and the failure is a wood family that silently cannot be cut, or a recipe file for a
family the generator no longer knows about.

A plain byte comparison is enough here, unlike the plate generator's. That one writes PNGs, and
Pillow and zlib emit different compressed bytes for identical pixels across versions and platforms.
This writes only JSON, so bytes are the right question.
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "tools"))

import generate_woodcutting  # noqa: E402

TARGETS = (
    [os.path.join(generate_woodcutting.RECIPES, name + ".json")
     for name, _, _ in generate_woodcutting.recipes()]
    + [os.path.join(generate_woodcutting.ADVANCEMENTS, name + ".json")
       for name, _, _ in generate_woodcutting.recipes()]
)


def main():
    # Two files per recipe: the recipe and the advancement that unlocks it.
    expected = len(generate_woodcutting.WOODS) * len(generate_woodcutting.SHAPES) * 2
    if len(TARGETS) != expected:
        print("FAIL: expected one file per wood per shape ({}), got {}".format(
            expected, len(TARGETS)))
        return 1

    before = {}
    for path in TARGETS:
        if not os.path.exists(path):
            print("FAIL: missing", os.path.relpath(path, REPO))
            print("Run `python tools/generate_woodcutting.py` and commit the result.")
            return 1
        with open(path, "rb") as handle:
            before[path] = handle.read()

    generate_woodcutting.main()

    failures = [os.path.relpath(p, REPO) for p in TARGETS
                if open(p, "rb").read() != before[p]]
    if failures:
        print("FAIL: the committed tree does not match a fresh generation:")
        for name in failures:
            print("   ", name)
        print("Run `python tools/generate_woodcutting.py` and commit the result.")
        return 1

    # AND NOTHING ELSE MAY BE LEFT BEHIND. Removing a wood from the table would leave its two recipe
    # files on disk, still loading, still cuttable, with nothing regenerating them - the drift a
    # byte comparison over the CURRENT table cannot see because it never looks at that file.
    on_disk = {name for name in os.listdir(generate_woodcutting.RECIPES)
               if name.endswith("_stonecutting.json")}
    on_disk |= {name for name in os.listdir(generate_woodcutting.ADVANCEMENTS)
                if name.endswith("_stonecutting.json")}
    wanted = {os.path.basename(path) for path in TARGETS}
    orphans = sorted(on_disk - wanted)
    if orphans:
        print("FAIL: stonecutting recipes on disk that the generator no longer writes:")
        for name in orphans:
            print("   ", name)
        return 1

    # Every recipe has to carry the feature condition, or the switch does not turn it off.
    ungated = []
    for path in TARGETS:
        with open(path, encoding="utf-8") as handle:
            payload = json.load(handle)
        conditions = payload.get("neoforge:conditions", [])
        if not any(c.get("feature") == generate_woodcutting.FEATURE for c in conditions):
            ungated.append(os.path.relpath(path, REPO))
    if ungated:
        print("FAIL: recipes with no feature condition, so the switch cannot turn them off:")
        for name in ungated:
            print("   ", name)
        return 1

    print("ok: {} wood cutting recipes match a fresh generation and are all gated".format(
        len(TARGETS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
