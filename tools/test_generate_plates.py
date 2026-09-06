"""Tests for tools/generate_plates.py.

Hand-rolled rather than pytest, following the house convention: a CASES list, ok/FAIL lines, and a
failure count. This repo has never had pytest and one test file is not a reason to add it.

Run from the repo root:

    python tools/test_generate_plates.py
"""

from __future__ import annotations

import filecmp
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from plate_variants import VARIANTS, block_id, family  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
GENERATOR = ROOT / "tools" / "generate_plates.py"


def _generate_into(target: Path, hash_seed: str) -> None:
    """Run the generator in its OWN process, with a chosen PYTHONHASHSEED.

    Both details are load-bearing and neither is obvious.

    A separate process is required because Python's string hash salt is fixed for the life of an
    interpreter. Calling the generator twice in-process would produce identical output even with the
    original `hash(material)` seed, so an in-process test would have passed against the very bug this
    file exists to catch, and been worth nothing.

    Choosing the seed rather than letting Python randomise it turns "almost certainly catches it"
    into "always catches it". Two random salts could in principle collide; 1 and 2 cannot.
    """
    env = dict(os.environ, PYTHONHASHSEED=hash_seed)
    subprocess.run(
        [sys.executable, str(GENERATOR), "--out", str(target)],
        check=True, capture_output=True, text=True, env=env,
    )


def _files_under(root: Path) -> set[str]:
    return {
        str(p.relative_to(root)).replace("\\", "/")
        for p in root.rglob("*") if p.is_file()
    }


def case_output_is_byte_identical_across_processes() -> list[str]:
    """The regression anchor for the seed bug fixed in 4080068.

    The generator seeded from `hash(v.material)`, so every invocation redrew all fourteen textures
    and produced a fourteen-file binary diff with nothing changed. Reverting the seed to `hash(...)`
    must fail this.
    """
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        a, b = Path(tmp) / "a", Path(tmp) / "b"
        _generate_into(a, "1")
        _generate_into(b, "2")

        files_a, files_b = _files_under(a), _files_under(b)
        for missing in sorted(files_b - files_a):
            problems.append(f"only the second run wrote {missing}")
        for missing in sorted(files_a - files_b):
            problems.append(f"only the first run wrote {missing}")

        for name in sorted(files_a & files_b):
            if not filecmp.cmp(a / name, b / name, shallow=False):
                problems.append(f"{name} differs between runs")
    return problems


def case_generator_writes_something() -> list[str]:
    """A generator that wrote nothing would pass the comparison above trivially."""
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        _generate_into(out, "1")
        written = _files_under(out)
        pngs = [f for f in written if f.endswith(".png")]
        if len(pngs) != 14:
            problems.append(f"expected 14 textures, got {len(pngs)}")
        if len(written) < 60:
            problems.append(f"expected at least 60 generated files, got {len(written)}")
    return problems


def case_committed_output_matches_a_fresh_generation() -> list[str]:
    """The committed tree is what ships, so it must still be what the generator produces.

    <b>This compares PIXELS for PNGs, not bytes, and that distinction was found the hard way.</b>
    The first version of this ran `git diff --exit-code` after regenerating, and it failed on CI
    while passing locally: all fourteen textures differed between a Windows machine and a Linux
    runner. The pixels were identical. Pillow and zlib produce different compressed bytes for the
    same image across versions and platforms, so PNG byte equality is a claim about the encoder, not
    about the texture.

    The seed determinism proven above is per-machine and real. Cross-machine PNG byte equality is
    not achievable and is not worth chasing; what matters is that the image is unchanged.

    Practical consequence worth knowing: after running the generator, `git status` may show every
    texture as modified with no actual change. That is the encoder, not a regression. This test is
    the signal, not the diff.
    """
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        fresh = Path(tmp) / "fresh"
        _generate_into(fresh, "1")

        for name in sorted(_files_under(fresh)):
            committed = ROOT / name
            if not committed.exists():
                problems.append(f"{name} is generated but not committed")
                continue
            if name.endswith(".png"):
                with Image.open(committed) as a, Image.open(fresh / name) as b:
                    if a.size != b.size or a.mode != b.mode:
                        problems.append(f"{name} changed shape ({a.size}{a.mode} -> {b.size}{b.mode})")
                    elif list(a.getdata()) != list(b.getdata()):
                        problems.append(f"{name} pixels differ from the committed texture")
            elif committed.read_bytes() != (fresh / name).read_bytes():
                problems.append(f"{name} differs from the committed file")
    return problems


def case_every_variant_is_in_exactly_one_tag_family() -> list[str]:
    """Tag membership is mining behaviour, and a variant in neither family loses its tool.

    A wooden plate is axe-mineable only because #minecraft:wooden_pressure_plates sits inside
    #minecraft:mineable/axe. A variant that fell out of both lists would still be breakable, just at
    the wrong speed with the wrong tool - and the in-game tag sweep catches it only after a full
    server boot, which is a slow way to learn about a one-line typo here.
    """
    problems: list[str] = []
    wood = {v.material for v in VARIANTS if family(v) == "wood"}
    stone = {v.material for v in VARIANTS if family(v) == "stone"}

    for overlap in sorted(wood & stone):
        problems.append(f"{overlap} is in both families")
    missing = {v.material for v in VARIANTS} - (wood | stone)
    for name in sorted(missing):
        problems.append(f"{name} is in neither family")
    if not stone:
        problems.append("the stone family is empty, so the pickaxe tag would ship with no entries")
    return problems


def case_no_generated_file_replaces_a_vanilla_tag() -> list[str]:
    """One key would turn a contribution into a replacement and delete vanilla's own entries.

    Tags MERGE across data packs, which is what makes writing under data/minecraft/ legitimate at
    all. A `"replace": true` in any of those four files silently discards every vanilla plate from
    the tag it joins, and the mod would still load.
    """
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        _generate_into(out, "1")
        vanilla = out / "src/main/resources/data/minecraft"
        files = sorted(vanilla.rglob("*.json")) if vanilla.is_dir() else []
        if not files:
            problems.append("no vanilla tag contributions were written at all")
        for path in files:
            payload = json.loads(path.read_text(encoding="utf-8"))
            if "replace" in payload:
                problems.append(f"{path.name} carries a 'replace' key")
            if not payload.get("values"):
                problems.append(f"{path.name} has no values")
    return problems


def case_every_block_has_the_26_1_asset_set() -> list[str]:
    """26.1 splits the client item definition out of the model, and missing it is invisible.

    Without assets/<ns>/items/<name>.json the placed block looks perfect and the held item is the
    missing texture. The in-game sweep checks the same thing, but this one fails in a second rather
    than after a server boot, and it also pins the paths themselves.
    """
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        _generate_into(out, "1")
        root = out / "src/main/resources"
        for v in VARIANTS:
            bid = block_id(v)
            required = [
                f"assets/flattsthings/blockstates/{bid}.json",
                f"assets/flattsthings/models/block/{bid}.json",
                f"assets/flattsthings/models/block/{bid}_down.json",
                f"assets/flattsthings/items/{bid}.json",
                f"assets/flattsthings/textures/block/{bid}.png",
                f"data/flattsthings/loot_table/blocks/{bid}.json",
                f"data/flattsthings/recipe/{bid}.json",
            ]
            for relative in required:
                if not (root / relative).is_file():
                    problems.append(f"{bid}: missing {relative}")
    return problems


def case_textures_are_shaped_and_legible() -> list[str]:
    """16x16, fully opaque, and the glyph actually contrasts with the plate it sits on.

    The glyph colour is derived by pushing away from the base luminance rather than being set per
    variant, so pale_oak and dark_oak are the boundary cases: a wrong sign in that branch produces a
    texture where the figure is invisible, which no other check would notice and which is the whole
    reason the texture exists.
    """
    problems: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        _generate_into(out, "1")
        blocks = out / "src/main/resources/assets/flattsthings/textures/block"
        for v in VARIANTS:
            path = blocks / f"{block_id(v)}.png"
            with Image.open(path) as img:
                if img.size != (16, 16):
                    problems.append(f"{v.material}: {img.size}, expected (16, 16)")
                    continue
                if img.mode != "RGBA":
                    problems.append(f"{v.material}: mode {img.mode}, expected RGBA")
                    continue
                px = img.load()
                if any(px[x, y][3] != 255 for x in range(16) for y in range(16)):
                    problems.append(f"{v.material}: has transparent pixels")

                def luma(c):
                    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]

                # Row 5 crosses the head, row 2 is plate only. Both come from FIGURE in the
                # generator; if that mark moves, this moves with it.
                glyph = sum(luma(px[x, 5]) for x in range(6, 10)) / 4
                plate = sum(luma(px[x, 2]) for x in range(6, 10)) / 4
                if abs(glyph - plate) < 25:
                    problems.append(
                        f"{v.material}: glyph and plate differ by only {abs(glyph - plate):.0f} "
                        "luma, the figure will not read")
    return problems


def case_every_recipe_is_gated_on_the_feature_switch():
    """Every plate recipe carries the config condition, and names the right feature.

    A config switch that hides an item but leaves its recipe loaded is not a switch: the block is
    still craftable, still in the recipe book and still in JEI. There is no runtime call that
    removes a loaded recipe, so the condition in the file IS the off switch, and a recipe generated
    without one is silently always-on.

    The feature name is checked against the Java side rather than against a literal here, because a
    condition naming a feature that does not exist fails at data pack load with no way to see it
    coming from this side.
    """
    problems: list[str] = []
    java = (ROOT / "src/main/java/com/flatts/flattsthings/config/FTConfig.java").read_text(
        encoding="utf-8")
    expected = "player_pressure_plates"
    if f'PLAYER_PRESSURE_PLATES = "{expected}"' not in java:
        problems.append(
            f"FTConfig no longer defines the feature id '{expected}'; every generated recipe "
            "names it and would stop loading")
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        _generate_into(out, "1")
        recipes = out / "src/main/resources/data/flattsthings/recipe"
        for v in VARIANTS:
            path = recipes / f"{block_id(v)}.json"
            recipe = json.loads(path.read_text(encoding="utf-8"))
            conditions = recipe.get("neoforge:conditions")
            if not conditions:
                problems.append(
                    f"{path.name}: no neoforge:conditions, so the switch cannot turn it off")
                continue
            named = [c.get("feature") for c in conditions
                     if c.get("type") == "flattsthings:feature_enabled"]
            if expected not in named:
                problems.append(
                    f"{path.name}: expected a flattsthings:feature_enabled condition on "
                    f"'{expected}', found {conditions}")
    return problems


CASES = [
    ("output is byte-identical across processes", case_output_is_byte_identical_across_processes),
    ("the generator actually writes its output", case_generator_writes_something),
    ("committed output matches a fresh generation", case_committed_output_matches_a_fresh_generation),
    ("every variant is in exactly one tag family", case_every_variant_is_in_exactly_one_tag_family),
    ("no generated file replaces a vanilla tag", case_no_generated_file_replaces_a_vanilla_tag),
    ("every block has the 26.1 asset set", case_every_block_has_the_26_1_asset_set),
    ("textures are shaped and legible", case_textures_are_shaped_and_legible),
    ("every recipe is gated on the feature switch", case_every_recipe_is_gated_on_the_feature_switch),
]


def main() -> int:
    failures = 0
    for name, case in CASES:
        problems = case()
        if problems:
            failures += 1
            print(f"FAIL  {name}")
            for problem in problems[:10]:
                print(f"        {problem}")
            if len(problems) > 10:
                print(f"        ... and {len(problems) - 10} more")
        else:
            print(f"ok    {name}")
    print(f"\n{len(CASES) - failures}/{len(CASES)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
