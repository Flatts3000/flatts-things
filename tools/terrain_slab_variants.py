"""The one list of terrain slab variants, and the textures each is built from.

This table and the `TERRAIN_SLABS` table in `FTBlocks.java` are the mod's two halves of the same
fact, exactly as `plate_variants.py` is. The same two tests close the loop from both ends: a
registry-walking GameTest fails when the JAVA side is missing a row, and the completeness sweep in
`RegistryCompletenessTests` fails when THIS side is, because a registered block with no blockstate,
model, loot table, lang key or creative-tab slot is what a missing row here produces.

**Nothing is drawn here, unlike the plates.** A slab of dirt is dirt, so the models point at
vanilla's own texture ids and this repo ships no art for the feature at all. That also means the
determinism apparatus the plate generator needs (separate processes, different PYTHONHASHSEED,
pixel comparison) has nothing to guard here: there is no randomness and no image.

**The twelve are a ruling, not a derivation** (owner, 2026-09-07, issue 31). "Terrain set only" was
chosen over the coloured builder sets and over deriving the list from the registry. Nether and End
terrain - soul sand, soul soil, netherrack, end stone, moss, packed mud - were NOT in the twelve and
are deferred rather than rejected. Adding a row here is adding a block; it needs the Java row too.

**Ten of the twelve are here. `grass_block` and `mycelium` are deliberately absent**, and the reason
is the ruling itself rather than an oversight: the owner asked for slabs that behave, not models, and
those two are the only families in the set whose behaviour is a SYSTEM rather than a property. They
spread and die back, they carry the snowy state, and grass is bonemealable into flowers - none of
which has an obvious reading on a half block, and all of which vanilla implements in
`SpreadingSnowyBlock`, a class no slab can extend because `SlabBlock` is already the parent.

Shipping them as tinted models with none of that would be exactly the decorative grass slab that was
rejected on the sibling issue, so they wait for a design rather than arriving half-built. The other
ten need no system: falling, the snowy property and drops are all per-block behaviour that a slab can
carry unchanged.

**Nine, in the end. `snow_block` was dropped too, and that one is a judgement rather than a deferral.**
A GameTest caught its recipe colliding with vanilla's own: three snow blocks in a row already make
six snow LAYERS. Chasing the collision turned up the better reason to drop it - a snow layer is
already a stackable partial snow block, and four of them is a snow slab in everything but name. A
snow block slab therefore fails this mod's own entry test, which is that a thing should be something
vanilla should plausibly have and does not.
"""

from __future__ import annotations

from typing import NamedTuple


class Variant(NamedTuple):
    """One terrain slab.

    `family` is the vanilla block id, so `grass_block` gives `grass_block_slab`.

    `top`, `side` and `bottom` are vanilla texture ids. Most families use one texture on all three
    faces; the layered ones (grass, podzol, mycelium) do not, which is the whole reason these are
    three fields rather than one.

    `batch_recipe` doubles the recipe to six source blocks for twelve slabs. Only gravel needs it,
    and the reason is a collision rather than taste: this mod's own gravel-to-flint recipe is
    SHAPELESS on exactly three gravel, so it matches any arrangement of three - a row, a column, an
    L. No three-gravel shaped recipe can coexist with it, and whichever the recipe manager found
    first would win silently. Six ingredients cannot match a shapeless three, so the collision goes
    away and the 1-to-2 rate is unchanged.

    A column was tried first and failed for exactly this reason, which is worth knowing before
    someone tries it again.

    `snowy` says the family carries vanilla's `snowy` blockstate property, which swaps the side
    texture for snow when a snow layer rests on top. Podzol is the only one of the ten that does -
    verified against `Blocks.java` rather than guessed, because coarse dirt LOOKS like it should and
    is a plain `Block`. A family with this set needs six blockstate variants rather than three, and
    a second pair of models with a snow side.
    """

    family: str
    display: str
    top: str
    side: str
    bottom: str
    snowy: bool = False
    batch_recipe: bool = False

    @property
    def block_id(self) -> str:
        return f"{self.family}_slab"


def _plain(family: str, display: str, texture: str) -> Variant:
    """A family whose six faces are all the same texture, which is most of them."""
    return Variant(family, display, texture, texture, texture)


# Ordered as the ruling listed them, which is also creative-tab order: the dirts, then the two that
# spread, then the loose ones that fall, then the odd solids.
VARIANTS: list[Variant] = [
    _plain("dirt", "Dirt", "minecraft:block/dirt"),
    _plain("coarse_dirt", "Coarse Dirt", "minecraft:block/coarse_dirt"),
    Variant(
        "rooted_dirt", "Rooted Dirt",
        "minecraft:block/rooted_dirt", "minecraft:block/rooted_dirt",
        "minecraft:block/rooted_dirt",
    ),
    Variant(
        "podzol", "Podzol",
        "minecraft:block/podzol_top", "minecraft:block/podzol_side", "minecraft:block/dirt",
        snowy=True,
    ),
    _plain("mud", "Mud", "minecraft:block/mud"),
    _plain("clay", "Clay", "minecraft:block/clay"),
    Variant("gravel", "Gravel", "minecraft:block/gravel", "minecraft:block/gravel",
            "minecraft:block/gravel", batch_recipe=True),
    _plain("sand", "Sand", "minecraft:block/sand"),
    _plain("red_sand", "Red Sand", "minecraft:block/red_sand"),
]
