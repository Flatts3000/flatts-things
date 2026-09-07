"""The committed woodcutter textures must match what the generator draws.

COMPARED BY PIXEL, NOT BY BYTE, and that distinction is load-bearing here. Pillow and zlib emit
different compressed bytes for identical pixels across versions and platforms, so a byte comparison
passes on Windows and fails on a Linux runner with every pixel identical - which is exactly what
happened to the plate generator once. The JSON generators in this repo can compare bytes because
JSON has no compressor in the way; PNGs cannot.

Also checks the seed is stable across PROCESSES. Python salts string hashing per interpreter run, so
a generator seeded from hash() redraws everything on every invocation and a same-process double run
would not notice.
"""
import os
import subprocess
import sys

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "tools"))

import generate_woodcutter  # noqa: E402


def pixels(path):
    with Image.open(path) as img:
        return list(img.convert("RGBA").getdata())


def main():
    before = {}
    for name in generate_woodcutter.FACES:
        path = os.path.join(generate_woodcutter.TEXTURES, name + ".png")
        if not os.path.exists(path):
            print("FAIL: missing", os.path.relpath(path, REPO))
            print("Run `python tools/generate_woodcutter.py` and commit the result.")
            return 1
        before[name] = pixels(path)

    # A SEPARATE PROCESS, under a different hash salt. That is the whole point: a same-process
    # rerun shares one salt and would pass against a hash()-seeded generator.
    env = dict(os.environ, PYTHONHASHSEED="12345")
    subprocess.run([sys.executable, os.path.join(REPO, "tools", "generate_woodcutter.py")],
                   check=True, capture_output=True, env=env)

    drifted = []
    for name in generate_woodcutter.FACES:
        path = os.path.join(generate_woodcutter.TEXTURES, name + ".png")
        if pixels(path) != before[name]:
            drifted.append(name)

    if drifted:
        print("FAIL: these textures are not what the generator draws:")
        for name in drifted:
            print("   ", name)
        print("Run `python tools/generate_woodcutter.py` and commit the result,")
        print("or the seed is not stable across processes.")
        return 1

    # The saw is deliberately NOT ours. If a woodcutter_saw.png ever appears, the model has to be
    # updated to use it or it is dead weight nothing references.
    stray = os.path.join(generate_woodcutter.TEXTURES, "woodcutter_saw.png")
    if os.path.exists(stray):
        print("FAIL: woodcutter_saw.png exists, but the model uses vanilla's blade on purpose.")
        return 1

    print("ok: {} woodcutter textures are stable across processes".format(
        len(generate_woodcutter.FACES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
