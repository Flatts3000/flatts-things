# Changelog

**Status:** current. v0.1.0 is not released anywhere; there is no CurseForge project yet (#11). Last reviewed 2026-09-05.

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- **Coverage instrumentation, and the merged report that makes it mean anything.** GameTests run in
  their own server JVM, so a plain JaCoCo setup sees none of them and would report the plate block at
  0% while every branch in it has an in-world test. The agent is attached to the GameTest run behind
  `-PgameTestCoverage` and `coverageReport` merges both layers. Merged coverage went 96% to 98% line
  and 91% to 100% branch over the pass.
- **The recipes and loot tables are now tested for working, not for existing.** Twenty-eight
  generated files were only ever checked for presence. `every_variant_has_a_recipe_that_actually_crafts`
  resolves each through the real crafting lookup, and `every_variant_drops_itself_when_broken` runs
  the real loot table. A recipe naming an item that does not exist is dropped silently during load,
  which is exactly what the old existence check could not catch.
- **The first JUnit tests**, for `FTBlocks.plate()`'s throw branch and the invariants of the variant
  table. Pure logic, so a GameTest was the wrong instrument.
- **`-Ptests=<selector>`** on `runGameTestServer`, so a single test or a wildcard group can be run
  instead of the whole suite.

## v0.1.0 - 2026-09-05 - "Only You"

First build. The repo, and the family of blocks it was opened for.

### Added

- **Player Pressure Plates: a player-only counterpart to every vanilla pressure plate.** Fourteen
  of them, crafted shapelessly from the vanilla plate plus an ender pearl. Vanilla has two
  sensitivities and neither is this one: wood fires for any entity, so an arrow you shot at it opens
  your door, and stone fires for any living entity, so a wandering cow does. Each variant is its
  counterpart in every other respect - hardness, map colour, instrument, flammability, click sounds,
  the 20-tick hold and a signal of 15 - because the sensitivity is the whole feature.
- **The two weighted plates deliberately have no counterpart.** They are a different block that
  weighs dropped item stacks and outputs a proportional signal, and "player-only" means nothing for
  a block whose whole job is counting items.
- **Properties are copied from the vanilla block, not restated.** Transcribing fourteen property
  chains is how a variant ends up subtly wrong, and the traps are not where you would guess: crimson
  and warped are the two woods that do NOT burn. `ofLegacyCopy` is used rather than `ofFullCopy`,
  because the full copy also carries `drops` and `descriptionId` and would leave every plate rolling
  the vanilla loot table under the vanilla name.
- **Vanilla tag parity, which is mining behaviour rather than bookkeeping.** Each variant joins the
  tags its counterpart is in, so a wooden player plate is axe-mineable and a stone one is
  pickaxe-mineable. Missing that leaves a plate breakable at the wrong speed with the wrong tool,
  which nothing else would have caught. A sweep compares the two tag SETS rather than a written-down
  list, so a tag vanilla adds in a future version fails it instead of being missed.
- **The in-world test layer.** Sixteen GameTests run headless by `./gradlew runGameTestServer` and
  gated in CI as a job separate from `build`. Every negative is paired with a vanilla control that
  fires on the same entity, so "a cow does not press it" cannot pass because the cow was never
  placed properly.
- **Two sweeps that close the drift loop from both ends,** because the variant list exists twice -
  once in Java, once in the generator. `every_vanilla_pressure_plate_has_a_player_counterpart` walks
  the registry rather than the list, so a wood type added by a future Minecraft version fails it
  rather than being missed. `RegistryCompletenessTests` catches the other direction, where the block
  exists and its files do not.
- **`tools/generate_plates.py`.** Textures, blockstates, models, client item definitions, loot
  tables, recipes and the lang file all come from one table, so a new plate is one row rather than
  seven files. Deterministic and seeded, so a texture change is a palette diff you can read.

### Notes

- Every test in this release was driven RED before being trusted. That found a real defect: the
  vanilla-parity sweep originally compared each plate against the same field its properties were
  copied from, so it agreed by construction and stayed green when a variant was deliberately pointed
  at the wrong vanilla block. It now resolves the expected block from the material name instead.
