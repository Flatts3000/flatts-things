"""The one list of plate variants, and the palette each is drawn from.

This table and the `VARIANTS` table in `FTBlocks.java` are the mod's two halves of the same fact,
which is a drift risk and is handled by tests rather than by hoping. Two GameTests close the loop
from both ends: `every_vanilla_pressure_plate_has_a_player_counterpart` fails when the JAVA side is
missing an entry, and the completeness sweep in `RegistryCompletenessTests` fails when THIS side is,
because a registered block with no blockstate, model, loot table, lang key or creative-tab slot is
exactly what a missing row here produces.

Colours are chosen to evoke each vanilla material, not sampled from it. Nothing Mojang drew is read
or shipped by this repo; the textures are generated whole from the palettes below.
"""

from __future__ import annotations

from typing import NamedTuple


class Variant(NamedTuple):
    """One plate. `material` is the vanilla prefix, so the block id is <material>_player_pressure_plate."""

    material: str
    display: str
    style: str          # plank | vertical | noise
    base: tuple[int, int, int]
    dark: tuple[int, int, int]
    light: tuple[int, int, int]


# Order is the creative-tab order: the two stone-likes, then the woods in vanilla's wood order.
VARIANTS: list[Variant] = [
    Variant("stone", "Stone", "noise", (124, 124, 128), (98, 98, 104), (146, 146, 150)),
    Variant("polished_blackstone", "Polished Blackstone", "noise",
            (53, 49, 58), (38, 35, 43), (72, 67, 78)),
    Variant("oak", "Oak", "plank", (176, 139, 84), (140, 108, 62), (198, 161, 106)),
    Variant("spruce", "Spruce", "plank", (122, 90, 50), (94, 68, 36), (146, 111, 66)),
    Variant("birch", "Birch", "plank", (200, 179, 125), (166, 146, 98), (219, 201, 152)),
    Variant("jungle", "Jungle", "plank", (168, 120, 90), (134, 92, 68), (190, 145, 113)),
    Variant("acacia", "Acacia", "plank", (176, 93, 51), (140, 71, 37), (198, 117, 74)),
    Variant("cherry", "Cherry", "plank", (224, 174, 178), (190, 140, 145), (238, 199, 202)),
    Variant("dark_oak", "Dark Oak", "plank", (75, 50, 24), (55, 36, 16), (97, 68, 38)),
    Variant("pale_oak", "Pale Oak", "plank", (232, 220, 203), (198, 185, 168), (245, 238, 227)),
    Variant("mangrove", "Mangrove", "plank", (119, 51, 58), (92, 38, 44), (145, 70, 77)),
    Variant("bamboo", "Bamboo", "vertical", (198, 179, 78), (162, 145, 56), (219, 203, 112)),
    Variant("crimson", "Crimson", "plank", (106, 52, 75), (82, 38, 57), (130, 70, 95)),
    Variant("warped", "Warped", "plank", (43, 108, 104), (30, 82, 79), (62, 132, 127)),
]


# The two stone-likes. Kept as an explicit set rather than inferred from `style`, because which
# vanilla tags a plate belongs to is a fact about its MATERIAL, not about how its texture is drawn,
# and tying the two would break the day a stone-family plate wants a plank-style texture.
STONE_FAMILY = {"stone", "polished_blackstone"}


def family(v: Variant) -> str:
    """"stone" or "wood" - decides which vanilla tags the variant joins."""
    return "stone" if v.material in STONE_FAMILY else "wood"


def block_id(v: Variant) -> str:
    return f"{v.material}_player_pressure_plate"


def vanilla_plate(v: Variant) -> str:
    return f"minecraft:{v.material}_pressure_plate"
