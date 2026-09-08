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

**Eleven of the twelve. `snow_block` is the one that is not coming**, and that is a judgement rather
than a deferral. A GameTest caught its recipe colliding with vanilla's own: three snow blocks in a
row already make six snow LAYERS. Chasing the collision turned up the better reason to drop it - a
snow layer is already a stackable partial snow block, and four of them is a snow slab in everything
but name. A snow block slab therefore fails this mod's own entry test, which is that a thing should
be something vanilla should plausibly have and does not.

**Grass and mycelium arrived second, and deliberately so.** The other nine needed a PROPERTY: gravel
falls, podzol carries a blockstate, mud has a shorter collision box. These two need a SYSTEM - they
spread, they die back when covered, and grass is bonemealable. Vanilla implements all of it in
`SpreadingSnowyBlock`, which no slab can extend because `SlabBlock` is already the parent, so every
part of it had to be adapted rather than inherited. Shipping them as models with none of that would
have been the decorative grass slab the owner rejected.
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

    `tinted` says the top face and the side overlay take the biome grass colour, and `overlay` names
    the tinted side texture drawn over `side`. Only the grass block has either, and they are why it
    cannot use vanilla's `block/slab` parent like everything else - that parent has no way to express
    a tintindex or a second layer, so a grass slab built on it renders grey.

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
    tinted: bool = False
    overlay: str | None = None

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
    Variant(
        "grass_block", "Grass Block",
        "minecraft:block/grass_block_top", "minecraft:block/grass_block_side",
        "minecraft:block/dirt",
        snowy=True, tinted=True, overlay="minecraft:block/grass_block_side_overlay",
    ),
    Variant(
        "mycelium", "Mycelium",
        "minecraft:block/mycelium_top", "minecraft:block/mycelium_side", "minecraft:block/dirt",
        snowy=True,
    ),
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
