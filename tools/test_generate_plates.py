"""Tests for tools/generate_plates.py.

Hand-rolled rather than pytest, following the house convention: a CASES list, ok/FAIL lines, and a
failure count. This repo has never had pytest and one test file is not a reason to add it.

Run from the repo root:

    python tools/test_generate_plates.py
"""

from __future__ import annotations

import filecmp
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

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


CASES = [
    ("output is byte-identical across processes", case_output_is_byte_identical_across_processes),
    ("the generator actually writes its output", case_generator_writes_something),
    ("committed output matches a fresh generation", case_committed_output_matches_a_fresh_generation),
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
