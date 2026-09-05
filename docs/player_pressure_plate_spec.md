# Player Pressure Plates

**Status:** shipped in v0.1.0. Fourteen variants, one per vanilla `PressurePlateBlock`.

## The gap

Vanilla has two pressure plate sensitivities, and the block does not choose either one. The
`BlockSetType` does, through `pressurePlateSensitivity()`:

| Plate family | Sensitivity | Fires for |
| --- | --- | --- |
| Wood (oak, spruce, bamboo, ...) | `EVERYTHING` | any `Entity`, dropped items and arrows included |
| Stone, polished blackstone | `MOBS` | any `LivingEntity`, every passive mob included |
| Weighted (gold, iron) | n/a, counts items | dropped item stacks, proportional signal |

Nothing there means "a player, and nothing else". The two failures are ordinary and familiar to
anyone who has built with plates:

- A stone plate at a door is opened by a cow, a chicken or a wandering trader crossing it.
- A wooden plate is fired by an arrow, a dropped item, or a boat drifting over it.

A player-only plate is the missing third row: the one a door, a shop counter, a lobby or a trapped
corridor actually wants.

## Scope

One counterpart per vanilla `PressurePlateBlock`, named `<material>_player_pressure_plate`:

`stone`, `polished_blackstone`, `oak`, `spruce`, `birch`, `jungle`, `acacia`, `cherry`, `dark_oak`,
`pale_oak`, `mangrove`, `bamboo`, `crimson`, `warped`.

**The two weighted plates are deliberately excluded.** `WeightedPressurePlateBlock` is a different
class whose entire function is weighing dropped item stacks into a proportional signal. A
"player-only" version of that is not a variant of it, it is a different block wearing a familiar
name, and it would need its own design (what would the signal even count?). They fall out of the
registry walk naturally, because `WeightedPressurePlateBlock` extends `BasePressurePlateBlock` and
not `PressurePlateBlock`, so no exemption list is needed anywhere.

## The rule

A plate outputs redstone 15 while at least one **player** is inside its touch box, and 0 otherwise.
Everything else is its vanilla counterpart, unchanged:

- Vanilla's `TOUCH_AABB`, unchanged.
- Vanilla's 20-tick hold, scheduled the same way, so it releases the same way.
- Click sounds, game events and neighbour updates from `BasePressurePlateBlock`, untouched.
- Hardness, map colour, note block instrument, flammability, collision and push reaction copied from
  the vanilla block rather than restated.
- The same vanilla tags as the counterpart, so mining behaves identically.

**Spectators do not press it,** because `getEntityCount` filters them, exactly as every vanilla plate
does. **Creative-mode players do press it,** also exactly as every vanilla plate does, so a build
tested in creative behaves the way it will when played.

## Two implementation decisions worth keeping

### Why it extends the abstract parent

The only behaviour that changes is `getSignalStrength`, so subclassing `PressurePlateBlock` and
overriding that one method looks right. It does not compile. Every block must declare a `codec()`,
and `PressurePlateBlock` declares its return type as `MapCodec<PressurePlateBlock>`. Java generics
are invariant, so an override returning `MapCodec<PlayerPressurePlateBlock>` is not a valid covariant
return against it.

`BasePressurePlateBlock` declares `MapCodec<? extends BasePressurePlateBlock>`, which accepts one.
The cost is re-implementing the three trivial `POWERED` methods, which is the whole difference
between the two classes anyway.

### Why `ofLegacyCopy` and not `ofFullCopy`

Properties are taken wholesale from the vanilla plate. Transcribing fourteen property chains by hand
is how a variant ends up subtly wrong, and the differences are not where you would guess: **crimson
and warped are the two woods that are NOT `ignitedByLava`**, because nether wood does not burn.

`ofFullCopy` would be a bug. On top of the behaviour it also copies `drops` and `descriptionId`, so
every plate would roll the **vanilla** plate's loot table and render under the **vanilla** plate's
name. Both would look like data problems rather than code ones. `ofLegacyCopy` takes the behaviour
and leaves identity to be derived from the block's own registry key, which is exactly the split
wanted. It carries a deprecation for being a partial copy; partial is the point.

## Tags

Each variant joins the vanilla tags its counterpart is already in, and this is mining behaviour
rather than bookkeeping. A wooden plate is axe-mineable only because
`#minecraft:wooden_pressure_plates` sits inside `#minecraft:mineable/axe`; the two stone plates are
listed in `#minecraft:mineable/pickaxe` individually rather than through a tag of their own. So:

| File shipped under `data/minecraft/` | Contents |
| --- | --- |
| `tags/block/wooden_pressure_plates.json` | the twelve wood variants |
| `tags/block/stone_pressure_plates.json` | the two stone-like variants |
| `tags/block/mineable/pickaxe.json` | the two stone-like variants |
| `tags/item/wooden_pressure_plates.json` | the twelve wood variants |

`#minecraft:pressure_plates` needs no file: it is defined as the union of the two family tags plus
the weighted pair, so joining the families joins it for free.

**Writing to another namespace's tag is legitimate because tags MERGE across data packs**, rather
than the top file at a path winning outright. That is the opposite of how a recipe at the same path
behaves, and it is why none of these files may ever carry a `"replace"` key.

## Recipe

Shapeless, per variant: the matching vanilla plate plus one `minecraft:ender_pearl`.

The pearl is doing design work rather than just adding cost. It is the vanilla item most associated
with a player specifically rather than with mobs or machinery, and it puts the plate after the first
trip out into the world rather than in the first ten minutes, which is about where the problem it
solves starts to matter. There is deliberately **no recipe back**, so the pearl is spent rather than
borrowed. Cheap to change if playtesting says otherwise.

## Where the content comes from

The variant list exists twice, and that is a deliberate trade rather than an oversight. `FTBlocks`
holds it in Java (block set types, vanilla block references); `tools/plate_variants.py` holds it for
the generator (display names, palettes, texture styles). The two need different data, so merging them
would mean one side carrying fields it has no use for.

`python tools/generate_plates.py` writes every per-variant resource from its half: texture,
blockstate, both block models, the client item definition, the loot table, the recipe, and the lang
file. It is seeded and deterministic, so re-running produces byte-identical output and an empty diff, and a
texture change is a palette line you can read rather than a binary you have to trust.

That determinism was claimed before it was true. The seed came from `hash(material)`, and Python
salts string hashing per interpreter run, so every invocation redrew all fourteen textures
differently and produced a fourteen-file diff with nothing actually changed. It now seeds from
`zlib.crc32`. The only way this surfaces is running the generator twice and diffing, which is now
part of the release check.

Adding a plate is one row on each side and one command.

## Tests

`gametest/PlayerPressurePlateTests`, ten tests.

The detailed behaviour tests run against the **stone variant alone**, because all fourteen share one
class and running the same code fourteen times proves nothing new:

| Test | Asserts |
| --- | --- |
| `a_player_presses_the_player_plate` | the feature works |
| `a_mob_does_not_press_the_player_plate` | a cow does not fire it |
| `control_a_stone_plate_does_react_to_the_same_mob` | that cow was positioned to be seen |
| `a_dropped_item_does_not_press_the_player_plate` | a dropped stack does not fire it |
| `control_an_oak_plate_does_react_to_the_same_item` | that item was positioned to be seen |
| `the_player_plate_releases_when_the_player_leaves` | it does not latch on forever |

What IS worth sweeping across all fourteen is the wiring, not the behaviour:

| Sweep | Catches |
| --- | --- |
| `every_variant_presses_for_a_player_and_ignores_a_mob` | a variant registered against the wrong class |
| `every_vanilla_pressure_plate_has_a_player_counterpart` | a variant missing from the Java list |
| `every_variant_matches_its_vanilla_block_properties` | a variant pointed at the wrong vanilla block |
| `every_variant_is_in_the_same_vanilla_tags_as_its_counterpart` | a variant missing a vanilla tag, so it mines wrong |

`gametest/PlateDataTests` covers the generated JSON, which is a different claim from the file
existence the completeness sweep checks:

| Test | Catches |
| --- | --- |
| `every_variant_has_a_recipe_that_actually_crafts` | a recipe silently dropped at load, or crafting the wrong thing |
| `every_variant_drops_itself_when_broken` | a loot table naming the wrong item, or nothing |

Both matter more than they look. All twenty-eight of those files come from one generator, so a single
wrong f-string breaks every one at once, and a recipe naming an item that does not exist is dropped
during load with a log line and no failure anywhere.

The two controls exist because the negatives can pass for the wrong reason. If the entity were never
actually inside the plate's box, nothing would press anything and both negatives would be green; the
controls fail in that case instead.

## What falsification found

Every test here was driven red before being trusted, and it was not ceremony.

- Swapping `Player.class` for `Entity.class` in `getSignalStrength` failed exactly the two feature
  negatives and left all four other behaviour tests green. As intended.
- Removing `bamboo` from the Java list failed `every_vanilla_pressure_plate_has_a_player_counterpart`
  and nothing else. As intended.
- Deleting one blockstate and one loot table failed the two matching completeness sweeps by name. As
  intended, and it is what proves the generator side of the drift loop is covered.
- Deleting the `mineable/pickaxe` contribution failed the tag sweep naming both stone variants. As
  intended.
- **Pointing `warped` at `OAK_PRESSURE_PLATE` did not fail anything.** The parity sweep compared each
  plate against `variant.vanilla()`, which is the same field its properties were copied from, so both
  sides changed together and agreed. The sweep now resolves the expected block from the material name
  through the registry, and the same break fails it on map colour and flammability.

That last one is the reason this section exists. An expectation taken from the thing under test is
not a test, and it looks exactly like a passing one.

## Known limits

- **Fake players count as players.** Any other mod's fake player entity standing in the box presses
  the plate. No decision has been made about whether that is wrong; it is recorded here so the first
  person to hit it knows it was not an oversight.
- **There is no owner or team filter.** Any player presses it, not a specific one. A plate keyed to
  one player is a different block and probably a different design.
- **Fuel parity is inherited, not chosen.** The twelve wooden variants join
  `#minecraft:wooden_pressure_plates` on the item side too, so anything vanilla does with that tag,
  burning them as furnace fuel included, applies to these. That is parity working as intended, but it
  is worth knowing that it was not decided separately.
