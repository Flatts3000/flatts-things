#!/usr/bin/env python3
"""Tests for tools/check_dashes.py.

Hand-rolled, per house convention: a CASES list, ok/FAIL lines, a failure count.

The fixtures are written to a temp directory rather than committed. Trashlands commits its
dash fixtures and excludes them from the walk, which works; writing them at run time avoids
needing the exclusion at all, and an exclusion is one more thing that can quietly grow to cover
a file somebody actually wanted checked.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LINTER = ROOT / "tools" / "check_dashes.py"

EM = "\u2014"
EN = "\u2013"


def _run(*paths: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(LINTER), *[str(p) for p in paths]],
        capture_output=True, text=True,
    )


def _fixture(tmp: str, name: str, body: str) -> Path:
    path = Path(tmp) / name
    path.write_text(body, encoding="utf-8")
    return path


def case_clean_text_passes() -> list[str]:
    with tempfile.TemporaryDirectory() as tmp:
        clean = _fixture(tmp, "clean.md", "A hyphen-joined phrase, a comma, and a colon: fine.\n")
        result = _run(clean)
        if result.returncode != 0:
            return [f"clean text was rejected: {result.stdout.strip()}"]
    return []


def case_em_dash_is_rejected() -> list[str]:
    with tempfile.TemporaryDirectory() as tmp:
        bad = _fixture(tmp, "em.md", f"This sentence {EM} which has an em-dash {EM} must fail.\n")
        result = _run(bad)
        if result.returncode == 0:
            return ["an em-dash was accepted"]
        if "em-dash" not in result.stdout:
            return [f"rejected, but did not name the em-dash: {result.stdout.strip()}"]
        if ":1:" not in result.stdout:
            return [f"rejected, but did not report a line number: {result.stdout.strip()}"]
    return []


def case_en_dash_is_rejected() -> list[str]:
    with tempfile.TemporaryDirectory() as tmp:
        bad = _fixture(tmp, "en.md", f"A range like 3{EN}5 must fail too.\n")
        result = _run(bad)
        if result.returncode == 0:
            return ["an en-dash was accepted"]
        if "en-dash" not in result.stdout:
            return [f"rejected, but did not name the en-dash: {result.stdout.strip()}"]
    return []


def case_the_repo_is_clean() -> list[str]:
    """The check is worthless if the repo cannot pass it, and this is what CI actually runs."""
    result = _run()
    if result.returncode != 0:
        return [line for line in result.stdout.splitlines() if line.strip()][:10]
    return []


CASES = [
    ("clean text passes", case_clean_text_passes),
    ("an em-dash is rejected, with a line number", case_em_dash_is_rejected),
    ("an en-dash is rejected", case_en_dash_is_rejected),
    ("the repo itself is clean", case_the_repo_is_clean),
]


def main() -> int:
    failures = 0
    for name, case in CASES:
        problems = case()
        if problems:
            failures += 1
            print(f"FAIL  {name}")
            for problem in problems:
                print(f"        {problem}")
        else:
            print(f"ok    {name}")
    print(f"\n{len(CASES) - failures}/{len(CASES)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
