# Changelog

**Status:** current, reviewed 2026-09-08. **v0.4.0 was uploaded to CurseForge on 2026-09-08 as an
ALPHA**, after v0.3.0 and v0.2.0 on 2026-09-07 and v0.1.0 on 2026-09-05. The project is still Under
Review: a new project is not visible to anyone and its files do not synchronize across CurseForge
until a moderator approves it, so every one of these is uploaded rather than released.

**Alpha is the honest label for the 0.x line**, and the publish task hardcodes it. Ten features now,
three of them (the tool slot strip, the woodcutter's screen and the grass slab's biome tint) with
client code no automated test can reach, and none of it has been through a long play session by
anybody but the author. The tint is the sharpest example the repo has: it shipped rendering flat grey
with all 144 tests passing, and was found by looking at it.

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- **Powered rails from copper.** The same recipe as the gold one, with copper where the gold goes,
  for three rails instead of six. Vanilla gates the only rail that accelerates behind six gold per
  six rails, which is why early rail travel is mostly pushing; copper is the metal the game gives you
  tonnes of and almost nothing to do with.

  **Half the yield is the point rather than an oversight.** A straight swap would leave the gold
  recipe with nothing to offer at all. This way copper buys rails early and gold stays worth using
  once you have it.

  It makes the **ordinary** powered rail. There is no copper rail block, nothing oxidises, and a rail
  made this way is indistinguishable from one made with gold - which is a deliberate narrowing of
  what was originally proposed, since a rail with a different push strength cannot be data.

- **Recompile's tools fit the tool slots.** The Scrap Knife, Prybar, Junk Shovel, all four
  Sledgehammers and the Cutting Torch can be stored in a tool slot and are picked up by the
  auto-swap, which matters more there than in vanilla: recompile's teardown loop switches tools
  constantly and every one of them used to cost a hotbar slot.

  **Recompile is not a dependency and nothing changes without it.** The entries are optional, so on
  an install without it they simply are not there. Nothing to configure, no version check.

  **The Garbage Vacuums are deliberately not included.** They break no blocks, so the auto-swap could
  never pick one and storing one would be using the tool slots as pocket storage - a different
  feature from the one that shipped. That rule is now pinned by a test, alongside the older one that
  keeps weapons out: a thing belongs in a tool slot when it breaks blocks and breaking blocks is its
  job.

## v0.4.0 - 2026-09-08 - "Common Ground"

### Added

- **Grass and mycelium slabs**, which completes the terrain set at eleven families. They were held
  back from the first nine on purpose: the others needed a property, these need a system.

  **They spread and die back the way their full blocks do.** Covered grass reverts to a dirt slab.
  Uncovered grass creeps onto nearby dirt, whether that dirt is a slab or a full block. And a dirt
  slab beside a vanilla grass block greens itself - which is the direction vanilla cannot do on its
  own, because its grass looks for a dirt BLOCK and will never see a slab. Running it backwards from
  the dirt slab covers that with no second mixin.

  **Plants grow on them**, on the seven soil families and on a top or double slab only - a bottom
  slab's surface is half way up its own block, so a flower on one would sprout floating. That needed
  saying in two places at once: which families is a tag question, which halves is a blockstate
  question, and a tag cannot answer the second.

  **The sides show the surface spilling over the edge.** Grass, podzol and mycelium paint their
  fringe across the top of their side texture, so a slab takes the top half of it whichever half of
  the block it is - otherwise a bottom grass slab would be dirt on all four sides with the green
  stopping dead at the top face.

  Spreading only crosses between **matching halves**: a bottom grass slab does not green a top dirt
  slab, because they do not touch. Bonemeal works on a grass slab except as a bottom one, where the
  plants would sprout floating half a block above it. Mycelium is not bonemealable, because vanilla
  mycelium is not either.


- **Terrain slabs.** Nine half blocks of the ground rather than of the things you build with: dirt,
  coarse dirt, rooted dirt, podzol, mud, clay, gravel, sand and red sand. Vanilla gives stairs and
  slabs to bricks and planks and nothing at all to the floor. Three blocks in a row give six slabs,
  which is vanilla's own rate.

  **They behave rather than only looking right.** Gravel and sand fall, and a top slab knocked loose
  lands as a bottom one instead of coming to rest floating with a gap under it. Podzol takes the
  snowy side under snow, but only as a top or double slab, because a bottom slab's upper face is
  half way up its own block and a snow layer above it never touches. Rooted dirt grows hanging roots
  when bonemealed, and refuses as a top slab, where the roots would start eight pixels low and could
  not survive anyway. Mud is two pixels short, so you sink into it exactly as you do a mud block.

  **Mining one gives half of what the whole block gives.** A podzol slab yields a dirt slab, the way
  a podzol block yields dirt. Clay gives two clay balls. **Flint comes only from a DOUBLE gravel
  slab**, which is a whole block's worth, so cutting gravel into slabs and putting it back together
  is exactly neutral - and rolling flint costs you the block, the way it does in vanilla.

  **Gravel is crafted six-for-twelve rather than three-for-six**, at the same rate. Three gravel is
  already this mod's own gravel-to-flint recipe, which is shapeless, so it matches any arrangement
  of three and no three-gravel slab recipe could coexist with it.

  Grass and mycelium were named in the same request and are deliberately not here. They spread, die
  back and carry the snowy state, and none of that has an obvious reading on a half block. They will
  arrive with that behaviour rather than as models that only look like grass. Snow blocks came out
  too: three of them is already vanilla's snow layer recipe, and a snow layer is a stackable partial
  snow block, so vanilla effectively has that one already.


- **The woodcutter takes logs.** It only took planks, so a log put in the input offered nothing at
  all. A log now cuts into its planks at exactly the rate a crafting bench gives - four, or two for
  bamboo, which is the one family that breaks the pattern - and into a stripped log or a bark block
  one for one. Bark and stripped forms are accepted as inputs too, so anything in a wood family can
  go on the bench. Stripping costs an axe its durability in vanilla and costs the saw nothing; bark
  is a quarter cheaper than the bench, which is the same size of saving the stonecutter already
  grants on stairs.

- **Sticks, buttons and shelves** on the woodcutter, from an audit of everything a crafting bench
  makes out of wood alone. Two sticks per plank, one button per plank, one shelf per stripped log -
  every one of them the bench's own rate.

  **What is deliberately absent is the more interesting half.** A cut consumes exactly one item, so
  anything costing more than a plank on a bench would come out proportionally cheaper: a door or a
  pressure plate twice, a trapdoor three times, a fence gate or a boat five. Doors, trapdoors,
  pressure plates, fences, fence gates, signs and boats are all left on the crafting table for that
  reason - the same reason vanilla's stonecutter cuts nothing costing more than one stone.

## v0.3.0 - 2026-09-07 - "Against the Grain"

### Added

- **The woodcutter** (#53): a saw bench for planks. Put planks in, pick a shape, take it out - stairs
  or slabs, at the ratios a stonecutter already uses for stone. One plank per stair, against one and
  a half on a crafting bench; one plank per two slabs, which is the bench rate anyway. Twelve wood
  families. Crafted from planks and an iron ingot.

  The saving is small and only on stairs; the point is making one stair without laying six planks out
  in a grid, and switching shapes with one click. Vanilla has a cutter for stone and nothing for wood,
  which is not a principle - it is where Mojang stopped.

- **Combine a chestplate and an elytra on an anvil** (#37). The chestplate keeps its armour, its
  enchantments, its trim and its name, and gains the gliding. One chest slot does both jobs.
  The elytra is consumed, and any enchantments on the elytra go with it - the chestplate is the item
  that survives. Flight then wears the CHESTPLATE - so the armour keeping you alive is
  the thing being worn down, and it stops gliding one durability before it breaks exactly as a
  vanilla elytra does. Note this removes a trade Mojang has kept on purpose for years, which is why
  it has a switch like everything else here.

### Fixed

- **The tool slots turned up on the creative inventory's hotbar row.** Their silhouettes drew over
  hotbar slots one to five on the Survival Inventory tab, with no strip anywhere. The creative screen
  does not have its own slots for that tab - it repositions every inventory slot by index, and the
  tool slots are appended past the end of what it expects. They are now hidden on any screen that has
  not laid them out, which also means another mod's inventory screen cannot inherit them.

  Only the drawing overlapped; a click always went to the hotbar slot underneath.

## v0.2.0 - 2026-09-07 - "Sleight of Hand"

### Added

- **Dip a stack in a water cauldron to transform it** (#36). Concrete powder sets to concrete and
  dirt becomes mud, a whole stack at a time, for one of the cauldron's three levels. Both already
  work in vanilla - powder against a water SOURCE block, dirt under a water bottle - so this is the
  bulk form of something you can already do rather than a new capability: the vanilla loop is carry a
  bucket, place a source, place powder against it one block at a time, break the source. Washing dye
  and filling bottles are untouched, and turning the feature off gives you an ordinary cauldron back.
  Which items convert is the `#flattsthings:cauldron_transformable` tag and what they become is the
  `flattsthings:cauldron_transform` data map, so a pack adds its own with no code.

- **Three gravel crafts one flint** (#41). Vanilla drops flint one time in ten, so getting a few
  means mining gravel until the dice cooperate - repetition with no decision in it. Gravel that does
  not roll flint drops as gravel, so with any Fortune shovel you can re-place and re-break until
  every one has become flint; this buys that loop out at three to one. Worse than any Fortune level
  in yield, better than digging unenchanted.

- **Silk touch picks up budding amethyst** (#38), which vanilla never drops with any tool. Note this
  is a deliberate vanilla restriction rather than an oversight: a budding block you cannot take is
  what stops an amethyst farm being picked up and moved, so this makes geodes portable. On by
  default like everything else, and its config switch is there for packs that want vanilla's rule.

- **Enchant a golden apple to make an enchanted golden apple** (#47). The item's name says what it
  is: a golden apple that has been enchanted. Put one in an enchanting table, spend the levels and
  lapis, and take out the real thing. It needs a full ring of bookshelves - the offer does not appear
  at a bare table - and the enchantment is called Blessing where the table shows it. A Blessing book,
  which the table can roll onto a book like any other enchantment, does the same thing on an anvil.

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

- **The player pressure plates could not be crafted on packs that limit crafting** (#43). They
  shipped with no recipe-unlock advancement, so the recipes never appeared in the recipe book, and
  with `gamerule doLimitedCrafting true` they were entirely uncraftable - the feature looked switched
  on and did nothing. All fourteen now ship one.

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
