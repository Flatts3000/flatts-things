"""Check the terrain slab generator against the committed tree.

Run it with `python tools/test_generate_terrain_slabs.py`. The `tools` CI job runs it too.

**Byte comparison is the right question here, and it is NOT for the plates.** `test_generate_plates`
goes to considerable trouble to compare PNGs by pixel across two processes, because Pillow and zlib
emit different compressed bytes for identical images across versions and platforms. This generator
writes only JSON, from a table, with no randomness and no image anywhere - so bytes are exactly the
claim worth making, and a mismatch means a real difference.

Three cases, each closing a hole that has actually cost this repo something:

1. **The committed tree still matches a fresh generation.** Catches a generated file edited by hand,
   which is the failure the lang file has already had: the writer rewrites it wholesale, so a key
   typed in by hand disappears on the next run and nobody notices until a name renders as its key.

2. **No orphans.** A file for a family that is no longer in the table is invisible to every other
   check: the game loads it, the completeness sweep is satisfied by it, and the tests pass. This
   repo has already lost time to exactly that - removing rows from a generator without deleting the
   files they had written left the old resources on disk and the suite stayed green.

3. **The DROPS map covers the table exactly.** A family with no entry raises a KeyError during
   generation, which is loud; a LEFTOVER entry raises nothing at all and silently describes a block
   that no longer exists.
"""

from __future__ import annotations

import filecmp
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GENERATOR = Path(__file__).resolve().parent / "generate_terrain_slabs.py"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_terrain_slabs import DROPS  # noqa: E402
from terrain_slab_variants import VARIANTS  # noqa: E402

# Every directory the generator writes into, relative to the repo root.
WRITTEN = [
    "src/main/resources/assets/flattsthings/blockstates",
    "src/main/resources/assets/flattsthings/models/block",
    "src/main/resources/assets/flattsthings/items",
    "src/main/resources/data/flattsthings/loot_table/blocks",
    "src/main/resources/data/flattsthings/recipe",
    "src/main/resources/data/flattsthings/advancement/recipes/building_blocks",
]

FAMILIES = {v.family for v in VARIANTS}
BLOCK_IDS = {v.block_id for v in VARIANTS}


def _slab_files(directory: Path) -> set[str]:
    """Files in `directory` that this generator owns, which is anything ending in `_slab`."""
    if not directory.is_dir():
        return set()
    return {
        f.name for f in directory.iterdir()
        if f.is_file() and (f.stem.endswith("_slab")
                            or f.stem.endswith("_slab_top")
                            or f.stem.endswith("_slab_snow")
                            or f.stem.endswith("_slab_top_snow"))
    }


def test_committed_tree_matches_a_fresh_generation() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp)
        # The lang writer merges into whatever is already there, so it needs the real file to
        # start from or it would write a file containing only the slab keys.
        lang_src = ROOT / "src/main/resources/assets/flattsthings/lang/en_us.json"
        lang_dst = out / "src/main/resources/assets/flattsthings/lang/en_us.json"
        lang_dst.parent.mkdir(parents=True, exist_ok=True)
        lang_dst.write_bytes(lang_src.read_bytes())

        subprocess.run([sys.executable, str(GENERATOR), "--out", str(out)],
                       check=True, capture_output=True)

        differences: list[str] = []
        for rel in WRITTEN:
            for name in _slab_files(out / rel):
                fresh = out / rel / name
                committed = ROOT / rel / name
                if not committed.exists():
                    differences.append(f"{rel}/{name} was generated but is not committed")
                elif not filecmp.cmp(fresh, committed, shallow=False):
                    differences.append(f"{rel}/{name} differs from a fresh generation")

        if differences:
            raise AssertionError(
                "the committed tree does not match the generator:\n  "
                + "\n  ".join(differences))
    print("ok: the committed tree matches a fresh generation")


def test_no_orphan_files() -> None:
    """A file for a family the table no longer lists. Nothing else in the repo would notice."""
    orphans: list[str] = []
    for rel in WRITTEN:
        for name in _slab_files(ROOT / rel):
            stem = name.rsplit(".", 1)[0]
            for suffix in ("_top_snow", "_snow", "_top"):
                if stem.endswith(suffix):
                    stem = stem[: -len(suffix)]
                    break
            if stem not in BLOCK_IDS:
                orphans.append(f"{rel}/{name}")

    if orphans:
        raise AssertionError(
            "files for terrain slabs that are not in the table - the game will still load these,"
            " and every other check here passes with them present:\n  " + "\n  ".join(orphans))
    print(f"ok: no orphan files for {len(BLOCK_IDS)} slabs")


def test_drops_covers_the_table_exactly() -> None:
    missing = FAMILIES - set(DROPS)
    leftover = set(DROPS) - FAMILIES
    if missing:
        raise AssertionError(f"families with no DROPS entry: {sorted(missing)}")
    if leftover:
        raise AssertionError(
            f"DROPS entries for families that no longer exist: {sorted(leftover)}")
    print(f"ok: DROPS covers exactly the {len(FAMILIES)} families in the table")


def test_lang_has_a_name_for_every_slab() -> None:
    lang = json.loads(
        (ROOT / "src/main/resources/assets/flattsthings/lang/en_us.json").read_text("utf-8"))
    missing = [b for b in sorted(BLOCK_IDS) if f"block.flattsthings.{b}" not in lang]
    if missing:
        raise AssertionError(f"terrain slabs with no translated name: {missing}")
    print(f"ok: all {len(BLOCK_IDS)} slabs have a translated name")


if __name__ == "__main__":
    test_committed_tree_matches_a_fresh_generation()
    test_no_orphan_files()
    test_drops_covers_the_table_exactly()
    test_lang_has_a_name_for_every_slab()
    print("terrain slab generator: all checks passed")
