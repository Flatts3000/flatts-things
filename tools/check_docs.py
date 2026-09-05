#!/usr/bin/env python3
"""Fail when a markdown file has no dated status banner, or when the date has gone stale.

Why
---
Undated documentation decays silently, and this family has the evidence. Recompile's GitHub
description still reads "Design phase, no code yet" at v0.18.0, and its CLAUDE.md claims main
requires a `build` status check when the repo has no branch protection at all. This repo's own
CLAUDE.md was wrong three times in a single day.

All four sibling repos answer this with a `**Status:**` line carrying a date, and a recurring
`docs: SCRUB` pass that re-checks the claims. This makes the first half mechanical: a doc without a
banner, or with one older than the window, fails the build rather than waiting to be noticed.

It cannot check whether the CONTENT is true. That is what SCRUB is for. It checks that somebody
claimed a date.

Usage
-----
    python tools/check_docs.py [--max-age DAYS]
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {"build", "run", ".git", ".gradle", "__pycache__", "img"}
# Community boilerplate is not a living claim about this repo and does not go stale.
EXEMPT = {"CODE_OF_CONDUCT.md", "CONTRIBUTING.md", "SECURITY.md", "LICENSE.md"}

BANNER = re.compile(r"\*\*Status:\*\*(?P<body>.*?)(?:\n\n|\Z)", re.S)
DATE = re.compile(r"(20\d{2})-(\d{2})-(\d{2})")


def markdown_files() -> list[Path]:
    found = [p for p in ROOT.glob("*.md")]
    docs = ROOT / "docs"
    if docs.is_dir():
        found += [p for p in docs.rglob("*.md")]
    return sorted(
        p for p in found
        if p.name not in EXEMPT and not (SKIP_DIRS & set(p.relative_to(ROOT).parts))
    )


def check(max_age_days: int) -> list[str]:
    today = dt.date.today()
    problems: list[str] = []
    for path in markdown_files():
        text = path.read_text(encoding="utf-8")
        # The banner has to be near the top, not buried in a later section.
        head = "\n".join(text.splitlines()[:12])
        match = BANNER.search(head)
        display = path.relative_to(ROOT)
        if not match:
            problems.append(f"{display}: no '**Status:**' banner in the first 12 lines")
            continue
        found = DATE.search(match.group("body"))
        if not found:
            problems.append(f"{display}: status banner carries no YYYY-MM-DD date")
            continue
        stamped = dt.date(int(found.group(1)), int(found.group(2)), int(found.group(3)))
        age = (today - stamped).days
        if age > max_age_days:
            problems.append(f"{display}: status is {age} days old, review it and restamp")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max-age", type=int, default=180)
    args = parser.parse_args()

    problems = check(args.max_age)
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} doc(s) without a current status banner.")
        return 1
    print(f"ok: {len(markdown_files())} doc(s) carry a dated status banner")
    return 0


if __name__ == "__main__":
    sys.exit(main())
