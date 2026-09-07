# Changelog

**Status:** current, reviewed 2026-09-05. **v0.1.0 was uploaded to CurseForge on 2026-09-05 and is
Under Review.** A new project is not visible to anyone and its files do not synchronize across
CurseForge until a moderator approves it, so this is uploaded rather than released.

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- **Three gravel crafts one flint** (#41). Vanilla drops flint one time in ten, so getting a few
  means mining gravel until the dice cooperate - repetition with no decision in it. Gravel that does
  not roll flint drops as gravel, so with any Fortune shovel you can re-place and re-break until
  every one has become flint; this buys that loop out at three to one. Worse than any Fortune level
  in yield, better than digging unenchanted.

- **Silk touch picks up budding amethyst** (#38), which vanilla never drops with any tool. Note this
  is a deliberate vanilla restriction rather than an oversight: a budding block you cannot take is
  what stops an amethyst farm being picked up and moved, so this makes geodes portable. On by
  default like everything else, and its config switch is there for packs that want vanilla's rule.

- **The enchanted golden apple can be crafted again** (#33), eight gold blocks around an apple,
  exactly as it was before 1.9. Not a rebalance and not an invention: vanilla had this recipe and
  deleted it, leaving an item that exists and can only be found in loot. Off by config
  leaves it that way.

- **A key to turn the auto-swap off and on, bound to Z by default, under its own Flatts's Things
  category in Key Binds.** The config switch is the pack
  author's and applies to everybody; this is yours, and the moment you want it is while standing in
  front of the block that just swapped a tool you did not want. It says which way it went above your
  hotbar, survives death and logout, and cannot re-enable a swap a pack has switched off - it says
  so instead. Pressing it mid-swing gives your own item back rather than stranding it.
- **Every feature has an on/off switch** (`config/flattsthings-common.toml`). A grab bag has to be a
  menu rather than a package deal, so a pack that wants the tool slots and not the pressure plates
  can have exactly that. Off means no recipe and nothing in the creative tab, and the behaviour stops
  running - it never deletes anything, so blocks already placed keep working, tools already in a slot
  stay there, and a switch is always safe to flip back. Turning the auto-swap off mid-swing hands you
  your own item back rather than stranding it. Recipes are gated by a real data-pack condition
  (`flattsthings:feature_enabled`), which is the only version of "off" that is also true in the recipe
  book and in JEI.

- **Tool slots, auto-swap** (#24, third of three slices). Hit a block and the right tool comes out of
  your slots into your hand; stop, and whatever you were carrying comes back. Selection uses vanilla's
  own destroy-speed arithmetic, so a modded pickaxe sorts against a vanilla one correctly. Ties go to
  what you are already holding.
- **Tool slots, in the inventory screen** (#24, second of three slices). Open your inventory and
  there is a strip of five slots under it: put your pickaxe, axe, shovel and hoe somewhere that is
  not your hotbar. Shift-click moves tools in and out. Painted in vanilla's own palette using
  vanilla's slot sprite, so it ships no texture and matches the game it is in.
- **Tool slots, storage layer** (#24, first of three slices). Dedicated per-player slots that are not
  part of inventory space, so a pickaxe, axe, shovel and hoe stop eating four of your nine hotbar
  slots. What fits is the `#flattsthings:tool_slot_valid` tag, which defaults to the vanilla tool
  families and shears, so another mod's pickaxe fits with no compat patch and a pack can widen it in
  a datapack. Nothing is visible in game yet: the screen and the auto-swap are the next two slices.

### Changed

- **Weapons are not tools and no longer fit in a tool slot.** Swords were in
  `#flattsthings:tool_slot_valid` and should not have been: these slots feed the auto-swap, so a
  sword in one is a mod that puts a weapon in your hand while you are mining and takes it away
  again. Pickaxes, axes, shovels, hoes and shears still fit, and a pack can still widen the tag. A
  sword already stored keeps working and moves to your inventory the next time it leaves the slot;
  nothing is deleted.
- **The tool slots moved into the inventory screen, and the separate screen is gone.** They were
  behind a **V** keybind on a screen of their own, which delivered "tools that do not take up
  inventory space" while quietly dropping "in the inventory" - the half that was actually asked for.
  They are now five real slots in vanilla's own inventory menu, drawn as a strip under the panel, so
  clicking, dragging, stack splitting and tooltips are vanilla's own and behave exactly as they do
  everywhere else. Shift-clicking a tool in your inventory sends it to a tool slot. The **V** keybind
  and the `/toolslots` command are removed; nothing else opens.


- **The recipe is a redstone dust, not an ender pearl.** The pearl read well and gated badly: the
  problem these plates solve, a cow opening your door or an arrow tripping your plate, is one you hit
  in your first hours, and a pearl put the fix behind finding endermen. Redstone is what you already
  have in hand the first time you wire a door. A vanilla plate is not made obsolete by the cheaper
  price, because a mob farm still wants a plate that fires for mobs.

### Fixed

- **The auto-swap stopped working on any block you had already dug.** One dig that did not swap - and
  digging with the right tool already in hand is exactly that - left the block's position recorded
  permanently, and every later dig on it silently refused to swap. A dig is now told from the next one
  by position and time rather than position alone. Found in a real client with devbridge's new `mine`
  verb, not by a test: the whole suite dug freshly placed blocks, so `previous` was always empty and
  the second dig was never exercised.

### Notes

- **Fake players do not press these plates**, and the block does nothing to arrange it. This was
  filed as the opposite, on sound reasoning: a fake player is a `Player` and passes both of the
  filters vanilla applies. The conclusion was still wrong, because a NeoForge fake player is never
  added to the level, so the plate's query cannot find it however it is positioned. Established by
  writing the test, which failed against the ruling drafted from the issue. A mod that spawns a real
  player entity would still press it.
- **Furnace-fuel parity on the wooden variants is kept.** The objection was that burning one destroys
  an ender pearl; the recipe change above removes it.
- **The local texture generator is kept over the shared `texgen`.** Adopting it would mean
  contributing a plank style upstream to a repo with no remote, no CI and no tests, for a
  candidate-review workflow that fourteen flat textures do not need.
- **The first four commits will not be rewritten** to add the missing trailers. Every commit since
  carries them.


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
- **The in-world test layer.** Nineteen GameTests run headless by `./gradlew runGameTestServer` and
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

### Internal

None of this is visible in game. It is here because the first release is also the point at which the
repo's own guarantees were established, and each item below exists because something was found to be
wrong.

- **Merged coverage, gated at the 80 percent floor.** GameTests run in their own server JVM, so a
  plain JaCoCo setup sees none of them and reports the plate block at 0% while every branch in it has
  an in-world test. `coverageReport` merges both layers and is the only number worth quoting: 98%
  line, 100% branch. `verifyCoverageInputs` refuses to report against data an earlier build left
  behind, or against no data at all.
- **The recipes and loot tables are tested for working, not for existing.** Twenty-eight generated
  files were only ever checked for presence. A recipe naming an item that does not exist is dropped
  silently during load, which is exactly what a file-existence check cannot catch.
- **Five JUnit tests**, for pure logic where a GameTest is the wrong instrument and a slower one.
- **The resource generator has its own tests** and is provably deterministic across processes.
- **The plates were looked at.** `tools/shoot_plates.py` and `tools/shoot_gallery.py` drive a real
  client through devbridge and photograph the blocks, because a GameTest is a server-state oracle and
  can never see a texture.
- **Two linters:** no em-dashes or en-dashes anywhere, and every markdown file must carry a dated
  status banner.
- **Four CI jobs:** `build`, `gameTest`, `coverage`, `tools`.
- **`-Ptests=<selector>`** on `runGameTestServer`, so one test or a wildcard group can be run instead
  of the whole suite.

### Notes

- Every test in this release was driven RED before being trusted, and it kept paying. The
  vanilla-parity sweep originally compared each plate against the same field its properties were
  copied from, so it agreed by construction and stayed green when a variant was deliberately pointed
  at the wrong vanilla block. The texture generator was documented as deterministic before it was.
  The first gallery photograph was of two shut doors and proved nothing. None of those would have
  been found by reading the code.
- **This section was folded back into v0.1.0 rather than cut as a second version.** Everything above
  was written after the v0.1.0 heading was, but v0.1.0 has never been published anywhere, so the
  first file anyone downloads will contain all of it. A separate v0.2.0 would have described a
  release nobody could have had.
