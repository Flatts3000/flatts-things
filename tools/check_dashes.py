#!/usr/bin/env python3
"""Fail on em-dashes and en-dashes in anything this repo authors.

The house rule is ASCII punctuation only. In the pack repos it is enforced rather than trusted -
Trashlands walks its quest tree with a `check_dashes`, and Sky Frogs enforces the same thing as
`Q-NO-DASHES` in its quest validator. This repo trusted it until now.

There is a second, non-style reason it matters here: an em-dash in a `.ps1` breaks Windows
PowerShell 5.1, because BOM-less UTF-8 gets parsed as ANSI.

Why a local check rather than the toolkit's linter
--------------------------------------------------
`mc-pack-toolkit/quest-voice/lint_quest_voice.py` already treats em-dashes as hard errors, and
reusing it was considered. It is tuned for quest prose, and its soft tells - tricolons, forced
enthusiasm, uniform sentence length - fire noisily on javadoc and on a build file. Adopting it would
mean either a new mode upstream in a repo with no CI and no tests, or living with the noise. A
thirty-line check that does one thing is the better trade here. Revisit if this mod ever ships
player-facing prose beyond item names.

Usage
-----
    python tools/check_dashes.py            # the repo's authored files
    python tools/check_dashes.py FILE ...   # just these
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BANNED = {
    "\u2014": "em-dash (U+2014)",
    "\u2013": "en-dash (U+2013)",
}

# Authored text only. build/ and run/ are generated or third-party, gradle/wrapper is vendored, and
# the decompiled game sources are certainly not ours to lint.
ROOTS = ["README.md", "CLAUDE.md", "CHANGELOG.md", "docs", "src", "tools", ".github"]
SUFFIXES = {".md", ".java", ".py", ".json", ".yml", ".yaml", ".gradle", ".toml", ".properties"}
SKIP_DIRS = {"build", "run", ".git", ".gradle", "__pycache__", "img"}


def authored_files() -> list[Path]:
    found: list[Path] = []
    for name in ROOTS:
        path = ROOT / name
        if path.is_file():
            found.append(path)
            continue
        if not path.is_dir():
            continue
        for candidate in path.rglob("*"):
            if not candidate.is_file() or candidate.suffix.lower() not in SUFFIXES:
                continue
            if SKIP_DIRS & set(candidate.relative_to(ROOT).parts):
                continue
            found.append(candidate)
    return sorted(found)


def check(paths: list[Path]) -> list[str]:
    problems: list[str] = []
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            for character, label in BANNED.items():
                column = line.find(character)
                if column >= 0:
                    display = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
                    problems.append(f"{display}:{number}:{column + 1}: {label}")
    return problems


def main(argv: list[str]) -> int:
    paths = [Path(a).resolve() for a in argv] if argv else authored_files()
    problems = check(paths)
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} banned dash(es). Use a hyphen, a comma, a colon, or restructure.")
        return 1
    print(f"ok: no banned dashes in {len(paths)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
