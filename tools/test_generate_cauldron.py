"""The committed cauldron data must match what the generator writes.

Both halves of this loop were missing: nothing checked that PAIRS still described the committed
JSON, so appending a pair and forgetting to run the generator, or hand-editing the JSON without
touching PAIRS, left CI green and the item in neither the tag nor the data map.

A plain byte comparison is enough here, unlike the plate generator's: that one writes PNGs, and
Pillow and zlib emit different compressed bytes for identical pixels across versions and platforms.
This writes only JSON, so bytes are the right question.
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "tools"))

import generate_cauldron  # noqa: E402

TARGETS = [
    os.path.join(generate_cauldron.DATA, "data_maps", "item", "cauldron_transform.json"),
    os.path.join(generate_cauldron.DATA, "tags", "item", "cauldron_transformable.json"),
]


def main():
    before = {}
    for path in TARGETS:
        if not os.path.exists(path):
            print("FAIL: missing", os.path.relpath(path, REPO))
            return 1
        with open(path, "rb") as handle:
            before[path] = handle.read()

    generate_cauldron.main()

    failures = []
    for path in TARGETS:
        with open(path, "rb") as handle:
            if handle.read() != before[path]:
                failures.append(os.path.relpath(path, REPO))

    if failures:
        print("FAIL: the committed tree does not match a fresh generation:")
        for name in failures:
            print("   ", name)
        print("Run `python tools/generate_cauldron.py` and commit the result.")
        return 1

    # And the two files have to describe the same set, or an item is in the tag with nothing to
    # become, or has a transform the cauldron never looks at.
    with open(TARGETS[0], encoding="utf-8") as handle:
        mapped = set(json.load(handle)["values"])
    with open(TARGETS[1], encoding="utf-8") as handle:
        tagged = set(json.load(handle)["values"])
    if mapped != tagged:
        print("FAIL: the tag and the data map disagree.")
        print("  in the tag only:", sorted(tagged - mapped))
        print("  in the map only:", sorted(mapped - tagged))
        return 1

    print("ok: {} cauldron transforms, tag and data map agree".format(len(mapped)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
