"""The one owner of `en_us.json`.

**This exists because two generators writing the same file wholesale is a landmine.**
`generate_plates.py` used to build the lang file from its own static table plus the plate names and
write it out. That was fine while it was the only generator with names to contribute. The moment
`generate_terrain_slabs.py` also had names, running the plate generator silently deleted every slab
name, and running the slab generator produced a file the plate generator's own test called a
mismatch. Neither failure mentions lang until you read the diff.

Merging in each generator would have worked and would have been worse: the file's contents would
then depend on which generator ran last, so the committed tree would be reproducible only by running
them in the right order, and nothing would say what that order was.

So instead there is one function that knows every name in the mod, and both generators call it. Run
either one, or both, in any order, and the file that lands is the same.

**A name still cannot be typed into the json by hand.** That is the rule this file preserves rather
than relaxes: the whole file is rewritten from these tables, so anything hand-added disappears on the
next run of either generator, with no error and no diff anyone reads. Add the name here, or add it to
the table the loop below walks.
"""

from __future__ import annotations

import json
from pathlib import Path

from plate_variants import VARIANTS as PLATE_VARIANTS
from terrain_slab_variants import VARIANTS as SLAB_VARIANTS

NS = "flattsthings"

# Names with no table behind them: the creative tab, the key binding, the enchantment, and the
# blocks that are one-offs rather than families.
STATIC_LANG = {
    "itemGroup.flattsthings": "Flatts's Things",
    "key.category.flattsthings.flattsthings": "Flatts's Things",
    "enchantment.flattsthings.blessing": "Blessing",
    "key.flattsthings.toggle_auto_swap": "Toggle Tool Auto-Swap",
    "message.flattsthings.auto_swap.on": "Tool auto-swap on",
    "message.flattsthings.auto_swap.off": "Tool auto-swap off",
    "message.flattsthings.auto_swap.unavailable":
        "Tool auto-swap is turned off in this pack's config",
    "block.flattsthings.woodcutter": "Woodcutter",
    "container.flattsthings.woodcutter": "Woodcutter",
}


def entries() -> dict[str, str]:
    """Every translated name this mod ships, assembled from the tables that own each family."""
    lang = dict(STATIC_LANG)
    for v in PLATE_VARIANTS:
        lang[f"block.{NS}.{v.material}_player_pressure_plate"] = \
            f"{v.display} Player Pressure Plate"
    for v in SLAB_VARIANTS:
        lang[f"block.{NS}.{v.block_id}"] = f"{v.display} Slab"
    return lang


def write(assets_root: Path) -> None:
    """Write the complete file under `assets_root`, which is `assets/flattsthings`."""
    path = assets_root / "lang/en_us.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(entries(), indent=2, sort_keys=True) + "\n",
                    encoding="utf-8", newline="\n")
