# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

**What this is:** a standalone **NeoForge** mod, MC 26.1.2 / NeoForge 26.1.2.76 / Java 25, Gradle
9.5.1 via moddev 2.0.141. A grab bag of blocks, tools and small features vanilla never shipped. Mod
id / package: `flattsthings` / `com.flatts.flattsthings`.

**Status:** v0.4.0 uploaded to CurseForge 2026-09-08 as an **alpha**, after v0.3.0 and v0.2.0 on
2026-09-07 and v0.1.0 on 2026-09-05. The project is still Under Review, so nothing is visible to
players yet. **Ten features on `main`**, all of them in an uploaded build. Last reviewed
2026-09-08.

| Feature | Config id | Where it is written up |
| --- | --- | --- |
| Player Pressure Plates | `player_pressure_plates` | `docs/player_pressure_plate_spec.md`, shipped in v0.1.0 |
| Tool slots | `tool_slots` | the mixin section below |
| Tool auto-swap | `tool_auto_swap` | the two-switches section below |
| Enchant a golden apple | `enchanted_golden_apple` | the no-second-mixin section below |
| Silk touch takes budding amethyst | `silk_touch_budding_amethyst` | a loot modifier; see the 26.1 loot notes |
| Three gravel to one flint | `gravel_to_flint` | one recipe file, no Java |
| Cauldron transforms | `cauldron_transforms` | the cauldron section below |
| Armoured elytra | `armored_elytra` | the components section below |
| The woodcutter | `wood_cutting` | the woodcutter section below |
| Terrain slabs | `terrain_slabs` | the terrain slab section below |
| Powered rails from copper | `copper_powered_rails` | one recipe file, no Java |

**Derive that list from `FTConfig.features()` rather than trusting the table**, which is this file's
own standing advice about lists that read as complete. The previous banner here said "one family
shipped" for two days after five more had.

**The five plate questions were ruled on 2026-09-05**, delegated by the owner rather than answered
individually, and recorded with their reasoning in `docs/player_pressure_plate_spec.md` under
Decisions. In short: the recipe is redstone rather than an ender pearl (#6), fake players do not
press the plate and the block does nothing to arrange that (#7), fuel parity is kept (#8), and the
local texture generator is kept over texgen (#9). A delegated ruling is still a ruling; overturn one
by writing the reversal down beside it, not by deleting the reasoning. **Those five are not the only
open questions any more** - the backlog carries live ones, #35 among them.

## Build and test

The system `JAVA_HOME` on this machine is stale and points at a nonexistent Adoptium JDK 17, so
**every** gradle invocation needs it overridden:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build
```

| Task | Command |
| --- | --- |
| Compile only (fast feedback) | `./gradlew compileJava` |
| Full build + jar | `./gradlew build` |
| In-world GameTests (the real test layer) | `./gradlew runGameTestServer` |
| **One test, or a wildcard group** | `./gradlew runGameTestServer -Ptests=flattsthings:a_player_presses_the_player_plate` |
| Unit tests | `./gradlew test` |
| Merged coverage, both layers | `./gradlew test runGameTestServer -PgameTestCoverage coverageReport` |
| Dev client | `./gradlew runClient` |
| Regenerate IntelliJ run configs after `clean` | `./gradlew prepareAllRuns` |
| Regenerate plate resources (textures, models, recipes, tags, lang) | `python tools/generate_plates.py` |
| Fetch JEI + Jade into `run/mods` (dev client only) | `./gradlew fetchDevMods` |

`-Ptests` takes vanilla's own namespaced-id selector and accepts wildcards
(`-Ptests=flattsthings:every_*`). It is wired through `programArguments` in `build.gradle` because
Gradle's `--args` is **not** forwarded to the game by a moddev run.

**Never pipe gradle to `tail`/`head` and trust the exit code** - the pipe reports the pager's status
(0) and masks a Gradle failure. Redirect to a file and check `$?`, or use `PIPESTATUS`.

`runGameTestServer` boots a headless server, runs every registered test in a scripted plot, and exits
non-zero on failure. **Its pass count includes one vanilla built-in test** running in the
`minecraft:default` batch alongside ours, so the reported total is always this mod's **plus one**.

CI (`.github/workflows/ci.yml`) runs `build` and `gameTest` as two independent jobs. **The `build`
job name is load-bearing** if branch protection is ever configured to require that status check.

### Reading the coverage number

Neither layer is the truth alone. `jacocoTestReport` sees only JUnit and reports the block package at
almost nothing, when nearly all of it has thorough in-world coverage; `gameTest.exec` sees only the
GameTests and misses the pure logic the unit layer exists for. **`coverageReport` merges them, and is
the only number worth quoting.** The `coverage` CI job runs it and `coverageVerification` gates the
merged line rate at the 80 percent floor.

`verifyCoverageInputs` guards it, and both of its checks exist because the naive versions were wrong:

- **It asks whether the producing task ran in THIS build**, not whether a flag was passed. The first
  guard only fired when `-PgameTestCoverage` was absent, which left the hole it was written to close:
  run the GameTests, watch one fail, fix the code, then run `test coverageReport -PgameTestCoverage`
  without the GameTests, and the pre-fix data is merged and reported as current.
- **It fails when there is no data at all.** JaCoCo marks `executionData` `@SkipWhenEmpty`, so a bare
  `./gradlew coverageReport` on a clean tree is skipped NO-SOURCE and exits 0 having measured
  nothing, which reads exactly like success.

So run it as one invocation: `./gradlew test runGameTestServer -PgameTestCoverage coverageReport`.

**`InventoryMenuMixin` reads as 0 percent and is not uncovered.** A mixin's code executes inside
`InventoryMenu`, so JaCoCo attributes it to a vanilla class the report does not measure and the
mixin's own class shows nothing. What proves it ran is `ToolSlotTests`, which pins the menu's slot
count at 51: without the mixin it is 46 and that test fails. Do not chase this number, and do not
read it as a gap.

**The report and the gate measure different sets on purpose.** `client/**` never loads on the server
the tests run on, so no GameTest and no JUnit test can reach a line of it. The report counts it
anyway, because hiding it would make the headline look better while removing the evidence that those
lines have no automated coverage at all. `coverageVerification` excludes it, because a floor that
fails for reasons nobody can act on is a floor people learn to bypass. The gap between the two
figures is exactly the client surface, and the only thing that closes it is looking at the screen.

### Checking an API against the real 26.1 source

After any build, the decompiled game sources sit at
`build/moddev/artifacts/minecraft-patched-<version>-sources.jar`. 26.1 renamed and moved enough that
guessing is unreliable - unzip the class you need and read it rather than working from memory. Every
26.1 note at the bottom of this file came from doing exactly that.

## Architecture

**The registry spine is four calls in one constructor, and the order is load-bearing.**
`FlattsThings` wires `FTBlocks` -> `FTItems` -> `FTCreativeTabs` -> `FTGameTests`. Blocks before
items because block-items reference their block; the tab after items because it accepts them.

**Content comes from a table, not from repetition.** `FTBlocks.VARIANTS` is a list of
`PlateVariant(material, setType, vanillaBlock)` and a static block registers one plate per row.
`FTItems` and `FTCreativeTabs` both iterate that same list, so the creative-tab order is the table
order. Adding a plate is one row.

**GameTest registration is a two-step dance in 26.1.** A test is a body
(`Consumer<GameTestHelper>` in `Registries.TEST_FUNCTION`) *plus* metadata (a `TestData` carried by a
`FunctionGameTestInstance` registered at `RegisterGameTestsEvent`). `FTGameTests.test(name, maxTicks,
body)` hides both halves, so each domain class is bodies plus one `register()` line called from
`FTGameTests.register`. Tests live under `src/main/java/.../gametest/` because GameTest registration
requires it, and are excluded from coverage for that reason.

## The shape of this mod, and what follows from it

**There is no spine.** Nothing here gates anything else. That is the point of the mod and also its
main risk: with no progression to hide behind, each addition is judged alone, and the mod is only as
good as its worst entry. A thing earns its place by being something vanilla should plausibly have and
does not.

**Prefer vanilla parity everywhere except the one thing that is the feature.** The player plates keep
vanilla's hold time, sounds, signal strength, block properties, tags and silhouette, and change only
the sensitivity. A player should be able to guess how a thing behaves from the vanilla block it
resembles, and be right about everything except the part that is new.

**Copy vanilla properties, do not restate them.** `FTBlocks.propertiesOf` uses
`Properties.ofLegacyCopy(vanillaBlock)`. Restating fourteen property chains by hand is how a variant
ends up subtly wrong, and the traps are not where you would guess - crimson and warped are the two
woods that are NOT `ignitedByLava`. Use `ofLegacyCopy`, never `ofFullCopy`: the full copy also
carries `drops` and `descriptionId`, so the block would roll the VANILLA loot table under the VANILLA
name, and both failures read as data problems rather than code ones.

**Parity includes tags, and that is mining behaviour rather than bookkeeping.** A wooden plate is
axe-mineable only because `#minecraft:wooden_pressure_plates` sits inside `#minecraft:mineable/axe`.
The mod ships additions to vanilla tag files under `data/minecraft/`, which is legitimate because
**tags MERGE across data packs** rather than the top file at a path winning - the opposite of how a
recipe at the same path behaves. None of those files may ever carry a `"replace"` key.

**But merging is not the only thing that can go wrong with a tag, and the sentence above was for a
while the whole story here.** A plain string in `values` is a REQUIRED entry, and
`TagLoader.tryBuildTag` drops the ENTIRE tag if any required entry is missing - the vanilla entries
with it - logging one line and failing nothing. So a required entry naming something this mod loads
from data behind a feature condition is a way to break a vanilla system for everyone who switches
that feature off. It shipped once: `#minecraft:in_enchanting_table` listed a conditionally-loaded
enchantment, and switching that feature off would have stopped every enchanting table in the game
from offering anything to anybody.

**So: anything added to a tag this mod does not own must be `{"id": "...", "required": false}`,
unless the thing named is registered unconditionally in Java.** The plate tags are the unconditional
case - the blocks exist or the mod did not load. `everyEntryAddedToAVanillaTagIsOptional` pins it.

**When a thing comes in variants, the list exists twice** - once in Java, once in
`tools/plate_variants.py` - and that is accepted rather than engineered away, because the two sides
need different data. It is safe only because two tests close the loop:
`every_vanilla_pressure_plate_has_a_player_counterpart` walks the registry rather than the list and
catches a variant missing from the JAVA side, and `RegistryCompletenessTests` catches one missing
from the GENERATOR side by finding a registered block with no files. Add a new variant *kind* without
both, and the next one silently half-ships.

**Generated resources are committed, and the generator must stay deterministic** - but be precise
about which determinism, because two claims live here and only one is true.

**Seed determinism is real and enforced.** The seed once came from `hash(material)`, and Python salts
string hashing per interpreter run, so every invocation redrew all fourteen textures.
`tools/test_generate_plates.py` runs the generator twice in separate processes under different
`PYTHONHASHSEED` values and compares bytes. The separate processes are the whole point: a
same-process double-run shares one hash salt and would have passed against the original bug.

**Cross-machine PNG byte equality is NOT true and is not worth chasing.** Pillow and zlib emit
different compressed bytes for identical pixels across versions and platforms. Found when a
`git diff --exit-code` CI step passed on Windows and failed on the Linux runner with all fourteen
textures differing and every pixel identical.

**So `git status` after regenerating is not a signal.** It may show every texture modified with
nothing changed. Run `python tools/test_generate_plates.py`, which compares PNGs by pixel and
everything else by byte. That is the check; the diff is noise.

The `tools` CI job runs it.

## Every feature is switchable, and what that costs

`FTConfig` defines one boolean per feature in `config/flattsthings-common.toml`. Adding a feature
without adding a switch is not finishing it.

**COMMON rather than SERVER, deliberately.** These are content switches: they decide whether a recipe
loads and whether an item is in the creative tab. The creative tab builds its contents on the client
at startup, outside any world, where a SERVER config is not loaded - reading one there throws. Nothing
here needs a per-world or synced value.

**"Off" means no NEW ones, and never deletion.** A disabled feature loses its recipe and its creative
tab entry and stops running. Placed blocks keep working and stored tools stay stored. A switch that
ate somebody's build would not be reversible by flipping it back.

**One exception, and it is the woodcutter** (2026-09-07). Every other feature here is behaviour
attached to a block that does something on its own, so "keeps working" and "stops running" do not
collide. A workstation IS its recipes: switch `wood_cutting` off and a placed woodcutter stays
placed, stays breakable and gives its item back, but opens a menu with nothing in it. That is the
honest reading of "the behaviour stops running" for a block whose behaviour is a recipe list, and it
is written here rather than discovered - a pack author flipping the switch should know that the
benches in their world go quiet rather than vanish. Nothing is deleted; turning it back on restores
everything.

**A recipe can only be turned off in data.** There is no runtime call that removes a loaded recipe, so
hiding the item would leave it craftable, in the recipe book and in JEI. `tools/generate_plates.py`
writes a `flattsthings:feature_enabled` condition into all fourteen recipes and NeoForge drops the
recipe while reading it. The condition's registered name is part of that data format: renaming it in
`FTConditions` silently drops every recipe naming the old one, because an unknown condition type
cannot be reported without refusing to load the world.

**Gating a live swap has to unwind it.** While a swap is in progress the player's own item exists only
in the attachment, so a gate that merely stopped new swaps would strand it the instant somebody edited
the config. `ToolSwapper.onPlayerTick` unwinds when the switch goes off.

### Two switches for the auto-swap, and both must say yes

`FTConfig.toolAutoSwap()` is the pack author's and applies to everyone. `AUTO_SWAP_WANTED` is the
player's own attachment, flipped by a key (Z), serialised and `copyOnDeath` because a preference that
resets when you die is not a preference. `ToolSwapper.swapping(player)` reads both, in one place, so
that a caller cannot check one and forget the other - which would look like a key that works
everywhere except the one path nobody tested.

**The pack's off always wins**, pinned by `the_key_cannot_re_enable_a_swap_the_pack_switched_off`.
Separate switches are only safe if that holds; otherwise a key quietly restores a feature a pack
deliberately removed. When the pack has it off the key says so rather than pretending to toggle,
because a player flipping a setting that will not take effect has no other way to find out why.

**The payload carries nothing, not even the new value.** Sending the state the client thinks it wants
means trusting a client about its own setting, and two presses arriving out of order leave the sides
disagreeing. A bare "flip it" cannot disagree: the server owns the value and reports what it became.

**The key press is testable, and testing it found a bug immediately.** devbridge's `key` verb drives
`KeyMapping.set` + `KeyMapping.click` the way vanilla's own `KeyboardHandler` does, and reports what
the key is bound to. The binding was V, on the belief that vanilla does not use V; the first press
reported `bound to key.debug.dumpVersion, key.flattsthings.toggle_auto_swap`. Vanilla's F3 chords are
ordinary key mappings and collide for real.

**The binding sits in this mod's own category**, not vanilla's Gameplay, so a player can find it
without knowing its name. `KeyMapping.Category` is constructed and passed to
`RegisterKeyMappingsEvent.registerCategory` - `KeyMapping.Category.register` is deprecated in 26.1.
The label is `id.toLanguageKey("key.category")`, so the identifier and the lang key are one fact
stated twice; `FlattsThings.KEY_CATEGORY` is the single source and `KeyCategoryTest` checks the lang
file against it. Get that wrong and the screen renders the raw key, which no other test here would
see.

**Probe before choosing a default binding**, with `gamebridge key <k> --check`, which reports the
owners and presses nothing. In a client with JEI and Jade: G, H, N, B, C, X, T and V are vanilla's,
R, U and F are JEI's. Z is free and the only unbound key within reach of WASD. Do not reason about
which keys are free - the answer depends on what else is loaded.

### The config is global, and GameTests run concurrently

**Tests in one environment run at the same time; environments run one after another.** Almost
everything here is per-player or per-plot and does not care. A config switch is one global value, so a
test that turns one off turns it off for every test running beside it - including tests that never
mention the config.

That surfaced twice. First as `switching_the_swap_off_mid_swing_returns_the_item` failing on its own
premise, with the switch pulled out from under it by a sibling. Then, after the config tests were
moved into one shared "isolated" environment, as the same failure again: **a shared environment for
the isolated tests is not isolation.** Each config test now gets an environment of its own via
`FTGameTests.aloneIn(name)`, and environments are registered from what the specs ask for rather than
from a list kept in step by hand.

The near-miss worth remembering is `every_mod_item_is_in_the_creative_tab`, which reads the same
global creative tab a config test empties. It would have failed for a reason nobody could reproduce.

### A switch nobody flips is a switch nobody has checked

Every other test runs with everything on, which is exactly the state in which a gate reading the wrong
switch, or sitting on a path that never runs, passes. `ConfigGateTests` turns one off and asserts the
behaviour stops; `FTConfig.switchFor` exists for that and gameplay code must not call it.

All four were driven red. That found a real defect in one of them: the mid-swing test originally
called `ToolSwapper.swapIn` directly, which leaves no dig on record, so the tick handler unwound
through its "a live swap with no dig on record is stranded" branch and **the test passed with the
config gate deleted**. It now swaps in through `player.getDestroySpeed`, the call vanilla itself makes
while a block is being broken.

**The unit layer CAN answer some of it, and this file said otherwise for a while.** It claimed no
config is loaded in JUnit. moddev's JUnit integration boots a mod context and `SPEC.isLoaded()` is
true there - measured with a probe after coverage showed the unloaded branch of `enabled` was never
executed by either suite. The test built on that belief asserted nothing at all for every feature on
every run.

**`ConfigGateTests` is still the right home for the gates**, for a different reason than the one
first written down: flipping a switch in JUnit would prove `FTConfig` reads its own map, not that the
code consulting it stops doing anything. That second claim is the one worth making and it needs a
running server.

**The unloaded fallback in `enabled` is therefore unreachable from either suite** and is left
uncovered on purpose. It exists for a context neither suite creates.

## Testing conventions that are not optional here

**Growth is by accretion, so the completeness sweep is load-bearing.**
`gametest/RegistryCompletenessTests` asserts every registered item and block has a translated name, a
blockstate, a 26.1 client item definition, a loot table and a slot in the creative tab. None of those
failures breaks a compile and all are invisible until someone plays. Add to the sweep before adding a
new kind of thing, not after.

**File existence is not behaviour.** That sweep proves a recipe and a loot table are *present*;
`gametest/PlateDataTests` proves they *work*, by resolving each recipe through the real crafting
lookup and running each loot table through `Block.getDrops`. A recipe naming an item that does not
exist is dropped silently during load, with a log line and no failure anywhere.

**Pair every negative with a vanilla control.** "A cow does not press the player plate" passes just
as green when the cow was never placed in the plate's box at all. Each negative in
`PlayerPressurePlateTests` is shadowed by a test asserting the equivalent vanilla plate DOES fire on
that same entity, so a positioning mistake fails the control instead of silently passing the feature.

**Drive the game's own entry point, not a walk.** A plate is pressed from
`BlockStateBase.entityInside`, which the game calls out of `Entity.checkInsideBlocks` during
movement. A mock server player is not driven by client packets and a `spawnWithNoFreeWill` mob does
not wander, so waiting for either to step on a plate is a test that passes by timing out. Call
`entityInside` directly with a real entity positioned in a real level.

**Use JUnit the moment something is pure.** No world, no rendering and no server means a GameTest is
the wrong instrument and a slower one. `src/test/java` runs against a loaded mod context through
moddev's JUnit integration, so registries are real rather than mocked.

**Drive a new test RED before trusting it, and mean it.** This is not a formality here; it has caught
two real defects that a green suite hid.

- The vanilla-parity sweep PASSED when a variant was deliberately pointed at the wrong vanilla block,
  because it compared each plate against `variant.vanilla()` - the same field its properties were
  copied from - so the two agreed by construction. **An expectation taken from the thing under test
  is not a test.** It now resolves the expected block from the material name through the registry.
- The generator's determinism was documented before it was true (see above).

## Writing things down

**Every markdown file opens with a dated `**Status:**` banner**, and `tools/check_docs.py` fails the
build if one is missing or older than 180 days. That is the house convention from all four sibling
repos, and it exists because undated claims decay silently: recompile's own GitHub description still
reads "Design phase, no code yet" at v0.18.0, and its CLAUDE.md claims a branch protection rule the
repo does not have.

**Date decisions inline, in the house form** - `(owner, 2026-09-05)` - directly after the sentence they
settle.

**Annotate superseded reasoning, do not delete it.** When a decision is overturned, the old argument
stays and is marked wrong, the way recompile's `market_spec.md` says a prior invariant "was true...
until now, and anything reasoning from it is reasoning from a fact that has expired". A doc that
silently rewrites itself teaches nobody why the change happened, and the next person re-derives the
rejected option from scratch.

**Distrust this file's own lists.** It has already been wrong three times in one day: it claimed
there were no JUnit tests when there were five, pointed at a `tools/make_textures.py` that had been
renamed, and asserted the generator was deterministic before it was. Derive a set from the code when
you can; a sentence that reads as complete is the failure mode.

## House style, enforced

**ASCII punctuation only: no em-dashes, no en-dashes.** `tools/check_dashes.py` walks every authored
file (markdown, Java, Python, lang JSON, workflows, gradle) and the `tools` CI job runs it. This is
the house rule from the pack repos, where it is likewise lint-checked rather than trusted.

It is not only style. An em-dash in a `.ps1` breaks Windows PowerShell 5.1, because BOM-less UTF-8
gets parsed as ANSI.

The toolkit's `quest-voice` linter was considered and not adopted: it treats em-dashes as hard errors
already, but its soft tells (tricolons, forced enthusiasm, uniform sentence length) fire noisily on
javadoc and build files, and narrowing it means changing a shared repo that has no CI and no tests.
Revisit if this mod ever ships player-facing prose beyond item names.

## Visual verification

**A GameTest is a server-state oracle and never a UI oracle.** Tint, texture path, UV, render type,
model resolution and GUI layout are all invisible to it. Productive Frogs shipped a slime that
rendered opaque grey with every GameTest passing, and this mod had the same blind spot: fourteen
textures verified as a contact sheet and by the game loading them, never once looked at on a block.

devbridge closes it. It is a dev-only mod plus the `gamebridge` CLI, talking JSON over a loopback
socket, and it exists because a singleplayer integrated server has no RCON socket and a dedicated
server has no framebuffer.

```bash
python tools/make_dev_world.py     # once; builds run/saves/devworld headlessly via runServer
./gradlew runClient                # in one terminal
python tools/shoot_plates.py       # in another; builds the scene and captures
```

**Port 8610 is claimed for this repo** in `~/.claude/port_registry.yaml`. (An earlier version of
this file also said, further down, that no port was claimed. It was wrong from the day devbridge was
wired up; the claim above is the true one. Kept as a note rather than deleted, because this file has
now been wrong four times and the pattern - a list that reads as complete - is worth seeing.) There is deliberately no
default port on either side: a shared one once had Trashlands' verifier connect to Recompile's dev
client and report a clean pass about the wrong world. The registry cannot detect a clash here,
because the ports helper enumerates IPv4 and devbridge binds `getLoopbackAddress()`, which is `::1`
on this machine - so **dial `localhost`, never the `127.0.0.1` literal**, and trust `gamebridge ping`
over the registry.

**The jar is never a dependency.** It lives in `run/mods/`, which is gitignored, so it cannot ship.
Rebuild it from `F:\devbridge` with `./gradlew build`.

### What this cost to learn

**The first screenshot this pipeline produced was a death screen.** Every command reported success,
the script exited 0, the PNG existed, and it read "You Died! Dev was slain by Slime" - a superflat
spawns slimes in quantity and nothing in the pipeline had an opinion about whether the player was
alive. That is the same shape as the bug the tool exists to catch. `make_safe()` now clears the death
screen, goes peaceful, drops to spectator and sweeps entities.

**Minecraft reports "already in the desired state" as failure.** `difficulty peaceful` when already
peaceful, `fill` when the blocks already match, `forceload add` when the chunks already are - all
report failure, so `--strict` made the script die on its own previous success. Commands whose no-op
is legitimate pass `strict=False`; the scene is wiped to air first so everything else stays strict.

**`gamerule doDaylightCycle` does not parse in 26.1**, under that name or `minecraft:do_daylight_cycle`.
Not chased, because `tick freeze` stops time, weather and every other tick anyway.

## Releasing

CurseForge project **1683375**, slug `flatts-things`. **v0.1.0 was uploaded on 2026-09-05, v0.2.0 and
v0.3.0 on 2026-09-07; the project is still Under Review.** A new project is invisible and its files do not
synchronize until a moderator approves it, so an upload is not yet a release.

**Every 0.x upload is an ALPHA and the task hardcodes it** (`primary.releaseType = 'alpha'`). That is
a claim about confidence rather than about features: the tool slots ship a screen no automated test
can look at, and the coverage gate excludes `client/**` for exactly that reason.

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew publishCurseForge
```

**Manual, never CI.** A release is a decision, not a push event.

Before running it:

1. Bump `mod_version` in `gradle.properties`.
2. Rename `## Unreleased` in `CHANGELOG.md` to `## v<version> - <date> - "<Nickname>"`. **That heading
   format is load-bearing**: the task matches `## v<version>` literally and stops at the next
   `## v`, so a renamed heading silently ships "Release x.y.z" as the entire changelog.
3. Put `CURSEFORGE_API_KEY=<token>` in `.env` at the repo root. It is gitignored; see `.env.example`.
   The task also accepts the env var or a `cfApiToken` gradle property. Without any of them it throws
   before uploading anything.
4. Refresh the gallery if the visuals changed: `python tools/shoot_gallery.py --promote`.

**Two parts of a release the API cannot do, so they are manual every time.** The upload API takes a
file and its changelog and nothing else: the **description** and the **gallery images** are console
work. Paste `docs/curseforge_page.md` into Description, and drag `docs/cf_image_gallery/*.png` into
Media. Sky Frogs has the same shape of problem with its server-pack flag and solves it by writing the
manual step into the checklist rather than hoping somebody remembers.

**The publish task is registered conditionally and that is load-bearing rather than tidy.**
CurseForgeGradle resolves the project id while *configuring* the task, so registering it
unconditionally breaks every Gradle invocation, `build` included, on any clone where the id is unset.
The `else` branch registers a task that explains what to set.

## 26.1 API notes worth keeping

- **Registries use the factory form.** `registerBlock(name, factory, propsSupplier)`, because 26.1
  sets the `ResourceKey` on Properties before the block constructor runs. The older
  `new Block(props)` form breaks.
- **`ResourceLocation` is `net.minecraft.resources.Identifier`.**
- **Item models moved.** A block needs `assets/<ns>/blockstates/`, `assets/<ns>/models/block/` AND
  `assets/<ns>/items/<name>.json` (the client item definition). Without the last one the placed block
  looks perfect and the held item is the missing texture.
- **Data paths are singular:** `data/<ns>/loot_table/`, `data/<ns>/recipe/`.
- **Recipe ingredients are plain id strings**, not `{"item": ...}` objects, and the result is
  `{"id": ..., "count": ...}`.
- **`Item.getName()` takes an `ItemStack`.** For a lang-key check use
  `Component.translatable(item.getDescriptionId())`.
- **Subclassing a concrete vanilla block is often blocked by its codec.** `PressurePlateBlock`
  declares `codec()` as `MapCodec<PressurePlateBlock>`; generics are invariant, so no subclass can
  override it. Extend the abstract parent instead, which declares a wildcard.
- **`makeMockServerPlayerInLevel()` is deprecated for removal but is the only way to get a real
  player into a GameTest.** It comes up in CREATIVE, not survival - set the game mode explicitly if
  anything under test reads it. `makeMockPlayer()` is never added to the level, so an entity query
  will not find it.

  **Three more things about that player, each of which has now cost a debugging session.**

  **It stands at the world ORIGIN, not in your plot.** `helper.absolutePos` gives you the structure;
  the player is at 0, 0 until you `snapTo` it. Anything it drops lands in a chunk nothing loaded, and
  `getEntitiesOfClass` does not index entities there - so the item is real, the query answers empty,
  and it reads exactly like the mod deleting it. `a_tool_with_nowhere_to_go_is_dropped_not_eaten`
  spent a session on that.

  **`PlayerTickEvent` does not fire for it, and the reason is narrower than it looks.** The event is
  posted from the head and tail of `Player#tick()`, and `ServerPlayer` does NOT call `super.tick()`
  from its own `tick()` - only from `doTick()`, which is called from
  `ServerGamePacketListenerImpl#tick`. The mock player *has* a `Connection` and an `EmbeddedChannel`
  and is placed through `placeNewPlayer`; what it lacks is a listener the server ticks. So post the
  event by hand when testing a tick handler, and say that is what you are doing.

  **What DOES run is `ServerPlayerGameMode.tick()`,** from `ServerPlayer.tick()` via the level's
  ordinary entity ticking, and `tickCount` advances with it. Two consequences, both of which were
  written down backwards here first:

  - Elapsed-time logic can be tested for real. Let ticks pass, then post the tick event; do not fake
    the clock.
  - After a `START_DESTROY_BLOCK`, the game mode posts `BreakSpeed` **every tick**, so a dig record
    is refreshed by the server rather than going stale.

  **A dig still never finishes**, which is a third thing again: `ServerPlayerGameMode.tick` only
  calls `incrementDestroyProgress` while `isDestroyingBlock`. Removing the block needs
  `hasDelayedDestroy`, which `STOP_DESTROY_BLOCK` sets, or an insta-mine. To break a block in a test,
  call `gameMode.destroyBlock(pos)`.

  **Creative hides item accounting completely.** `Inventory.add` returns TRUE for a creative player
  whatever the state of the inventory - `hasInfiniteMaterials` sets the stack to zero and reports
  success - so a full inventory is not a state a creative player can be in, and any "where did this
  item go" branch is unreachable. Same shape as the game-mode trap above, one layer down.
- **A block's tags** come from `BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()`; there is no
  `getTags()` on `BlockBehaviour`.

### Global loot modifiers are discovered, not listed, in 26.1

**`data/neoforge/loot_modifiers/global_loot_modifiers.json` is gone and writing one is worse than
useless.** That file was Forge's registry of which modifiers are active. 26.1's
`LootModifierManager` is a `SimpleJsonResourceReloadListener` over `loot_modifiers/`, so it loads
**every** JSON in `data/<any namespace>/loot_modifiers/` directly - and the old list file, being in
that folder, is then parsed as a modifier and fails:

```
Couldn't parse data file 'neoforge:global_loot_modifiers' from
'neoforge:loot_modifiers/global_loot_modifiers.json': No key type in MapLike[...]
```

That error is logged on every data pack load and nothing else fails, so it sits in the log
unnoticed. Written down because it cost a wrong conclusion: emptying the list file was used as the
red drive for a loot modifier test, the test kept passing because the list is not read, and the
honest-looking reading of that was "the test is a false green". The test was fine. Delete the
modifier's own file to drive it red.

**Prefer a modifier to overriding a vanilla loot table.** Both work. An override wins outright over
any other mod touching the same block and silently suppresses a drop Mojang adds later; a modifier
composes. `neoforge:add_table` plus a `neoforge:loot_table_id` condition needs no Java at all.

### The client and networking layer moved a long way in 26.1

Anything written against an older version will not compile, and the renames are not guessable. All of
these were found by reading the 26.1 sources after the obvious version failed.

| Older API | 26.1 |
| --- | --- |
| `GuiGraphics` | `GuiGraphicsExtractor`. The whole render path is an "extract" model now |
| `Screen.renderBg(...)` | `Screen.extractBackground(GuiGraphicsExtractor, int, int, float)` |
| `this.imageWidth = 176` in the body | **final**; pass through `super(menu, inventory, title, width, height)` |
| `new KeyMapping(name, type, key, "key.categories.inventory")` | takes a `KeyMapping.Category`, e.g. `KeyMapping.Category.INVENTORY` |
| `@EventBusSubscriber(bus = Bus.MOD)` | no `bus` argument at all; routing is by event type |
| `PacketDistributor.sendToServer(...)` | `ClientPacketDistributor.sendToServer(...)`; `PacketDistributor` is server-to-client only |
| `IMenuTypeExtension.create(Menu::new)` | the factory is `IContainerFactory`, three arguments including a `RegistryFriendlyByteBuf` |
| `player.displayClientMessage(text, true)` | `player.sendOverlayMessage(text)` for the action bar; `sendSystemMessage(text)` for chat |

The renames above were all found while building a standalone tool-slot screen. **That screen is
gone** - the slots live in vanilla's inventory now - but the table stays, because every row is a
26.1 rename that any client code will hit.

**Screens here are painted, not textured.** `graphics.fill(...)` for the panel in vanilla's palette,
and `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("container/slot"), ...)`
for the slots. That inherits the vanilla look exactly and ships no art asset that a resource pack
could leave stranded.

## The tool slots are in vanilla's inventory, and that needs a mixin

**This is the only mixin in the repo, and none of the four sibling mods has one.** Do not add a
second without the same kind of reason.

**The feature was built the wrong way first.** The ask was tools that live in the inventory without
taking inventory space. Version one put them on a screen of their own behind a **V** key, which
delivered the second half and quietly dropped the first, and it was chosen precisely to avoid the
problem below. The owner found it by opening their inventory and seeing nothing. A trade that avoids
the hard part by dropping the named feature is not a trade, and the reasoning written in the code at
the time read as sound while being exactly wrong.

**NeoForge exposes no hook for adding slots to `InventoryMenu`.** There is no event; the events
directory has nothing between `PlayerContainerEvent` and `ContainerScreenEvent` that touches slots.
The two ways in are a mixin on the constructor or an access transformer plus adding the slots after
the fact. **The mixin wins because the menu is not built once**: it is rebuilt on join, on respawn
and on a dimension change, on both the client and the server. An access transformer needs a hook for
each of those, and missing one leaves the two sides disagreeing about how many slots exist.
`InventoryMenu` is synchronised by slot INDEX, so a disagreement moves items into the wrong slots
rather than merely looking wrong. A constructor runs for every rebuild on both sides and cannot be
missed.

**Appended at the end, indices 46 to 50.** Vanilla's `quickMoveStack` decides what a shift-click does
from hardcoded index ranges up to 45. Inserting anywhere earlier shifts the armour, inventory and
offhand out from under those ranges and breaks shift-clicking across the whole screen. Appending
leaves every range intact, and the method's final `else` already moves an unrecognised index into the
inventory, so shift-clicking a tool OUT works with nothing patched. Shift-clicking one IN does not,
and is the mixin's second injection.

**The config switch acts on `Slot.isActive()`, never on whether the slots exist.** The obvious
implementation - add them only when the feature is on - is the desync above with a config file
attached. The count must be identical on both sides whatever the config says; `isActive` then decides
whether a slot can be seen or touched, and vanilla honours it in the three places that matter
(rendering, the empty-slot icon, and `findSlot`, which is what a click looks through). A mismatched
config then costs nothing worse than one side refusing a click.
`switching_the_slots_off_hides_them_without_removing_them` pins the count.

**Only the background is ours to draw.** The slots are real slots, so the game draws the items,
highlights the hovered one, shows tooltips and handles clicks unasked. What is missing is what a
texture would provide, because vanilla's inventory texture stops at the bottom of its own panel.
`ToolSlotStrip` paints it on `ScreenEvent.Render.Background` - **not**
`ContainerScreenEvent.Render.Foreground`, which is the obvious hook and is wrong: 26.1 fires it after
the slots are drawn, so the panel would cover the items sitting in it. There is no
`ContainerScreenEvent.Render.Background` in 26.1; its own javadoc points at the screen event instead.
That hook is not translated by `leftPos`/`topPos`, unlike the foreground one.

**Below the panel rather than inside it, and not on the right.** Vanilla's inventory is 176 wide and
the free space inside fits three slots at most, so five cannot go in without moving vanilla's own
widgets - which is how an inventory screen ends up broken for everyone who added anything else. The
right-hand edge is where JEI puts its item list, and this mod ships no JEI integration on purpose.

**Weapons are deliberately not in `#flattsthings:tool_slot_valid`.** Swords were, at first, and it
read as harmless - a sword is held in the hand and has a durability bar like everything else in the
tag. It is not harmless: these slots feed the auto-swap, so a storable sword means a mod that puts a
weapon in your hand while you are mining and takes it away again. Tools go here; fighting is the
player's business. `a_weapon_does_not_belong_in_a_tool_slot` pins the shipped default, and there is
no sword outline for the same reason - an outline promising one would be an invitation the slot then
refuses. The fifth slot has no outline at all, because it is the free one.

### The tool slots are shown only on a screen that laid them out

**This was written here twice as "the creative inventory simply does not get them", and both times
that was wrong.** `CreativeModeInventoryScreen` does not have its own slots for the Survival
Inventory tab - `selectTab` walks EVERY slot of `player.inventoryMenu` and repositions it from its
index:

```java
int pos = ix - 9;
x = 9 + (pos % 9) * 18;
if (ix >= 36) { y = 112; } else { y = 54 + (pos / 9) * 18; }
```

Ours are indices 46 to 50, so they landed at x = 27, 45, 63, 81, 99 and **y = 112, the hotbar row**,
drawing their silhouettes over hotbar slots one to five. **The overlap was in drawing only** - an
earlier version of this section said they took clicks there, and that is wrong:
`getHoveredSlot` returns the FIRST active slot in list order, and creative's own hotbar wrappers sit
at those exact coordinates at indices 37 to 41, ahead of ours. `SlotWrapper` delegates
`getNoItemIcon()` and `isActive()` straight through, so everything about them came along;
only `ToolSlotStrip`, which correctly refuses to paint on anything that is not an `InventoryScreen`,
stayed behind. The owner found it by opening the creative inventory.

**Moving them is not available. `Slot.x` and `Slot.y` are final in 26.1.** Vanilla parks its own
crafting slots off-screen by CONSTRUCTING wrappers at -2000, which only the screen building the list
can do. What is left is `isActive()`, which the game consults for drawing the slot, for its
empty-slot icon, and in `findSlot` - so a slot answering false is invisible and unclickable wherever
somebody else has put it.

**So the rule is: a screen either gets the panel AND the slots, or neither.** `ToolSlotStrip` sets
`ToolSlotDisplay` on every frame from the same check that decides whether to paint, before its early
return, and clears it whenever any screen initialises so that a screen which never draws a background
cannot inherit a stale yes.

**Both halves, and the first attempt only did one.** Hiding a slot is presentation; it does not stop
items being routed into it. `moveItemStackTo` consults `mayPlace` and never `isActive`, and the
creative inventory forwards a shift-click straight into `InventoryMenu.quickMoveStack`
(`slotClicked` -> `player.inventoryMenu.clicked` with `QUICK_MOVE`), so `InventoryMenuMixin`'s
quick-move injection has to read `ToolSlotDisplay` too. Without it, hiding the slots made things
WORSE than the bug: a shift-clicked tool left the visible inventory and arrived in a slot nothing
draws, recoverable only by switching to survival. **Fail-safe rather than fail-broken**, and that is the whole point - the bug was not that the
creative screen was unhandled, it was that an unhandled screen showed the slots anyway. Another mod's
inventory screen, or a vanilla one that does not exist yet, now gets nothing instead of five
silhouettes in somebody else's hotbar. `a_tool_slot_is_inert_on_a_screen_that_did_not_lay_it_out`
pins the rule; the client wiring that feeds it is the untestable half.

**Set every frame rather than when a screen opens**, deliberately: the creative inventory swaps its
slot list on a tab change without reopening, so a value latched at open time is stale exactly when it
matters.

**The creative inventory still shows no tools, and that part of the old ruling stands** - it is now
true rather than merely intended. Giving it real ones means a SECOND mixin on a different class with
its own hardcoded index ranges, and the argument justifying the first does not transfer:
`InventoryMenu` is rebuilt on join, respawn and dimension change on both sides, so a constructor
injection is the only thing that cannot be missed. Nothing of that kind is true here, and a creative
player has every item in the game two clicks away.

## Making a vanilla item do something new, without a second mixin

The enchanted golden apple is made by enchanting a golden apple at a table. That needs three things
a vanilla item does not do, and **none of them turned out to need a mixin** - worth recording,
because the first instinct was that all three did.

**An item is enchantable only if it has the `minecraft:enchantable` component.** `ItemStack
.isEnchantable` checks for it, and a golden apple has none, so the table ignores it. NeoForge's
`ModifyDefaultComponentsEvent` (mod bus) adds a component to a VANILLA item, which is the supported
way to change one without touching its class.

**The table offers only what some enchantment supports.** `EnchantmentMenu` builds its three options
from `#minecraft:in_enchanting_table` filtered by each enchantment's supported items, so a new
data-driven enchantment whose only supported item is the golden apple is what makes an offer exist.
Its `min_cost` is the balance control: an offer appears only when the slot's level lands inside
`[min_cost, max_cost]`, so a minimum of 30 means a bare table cannot reach it and a full ring of
bookshelves is the price of admission. Joining the vanilla tag is safe because **tags merge**.

**Enchanting normally leaves the same item carrying an enchantment**, and this feature needs a
different item. `EnchantmentMenu.clickMenuButton` fires `PlayerEnchantItemEvent` immediately after
putting the enchanted stack back, and before the menu recomputes its offers, so a handler can swap
the slot and everything downstream sees the new item.

**The obvious route was a mixin and it was not needed.** NeoForge added
`IItemExtension.applyEnchantments` precisely so an item can transform itself when enchanted - it is
how a book becomes an enchanted book - but reaching it for a vanilla item means a mixin, and this
repo has exactly one with a written reason. The event lands in the same place. **Look for the event
before reaching for a second mixin.**

**The handler deliberately does NOT re-check the config.** The switch acts where the enchantment is
loaded, so with it off the table never offers Blessing and the handler is unreachable. Checking again
would be worse than redundant: the config is editable at runtime while the enchantment is only
removed on a data pack reload, so a switch flipped mid-session leaves the offer standing - and
`clickMenuButton` takes the player's levels BEFORE this event and their lapis AFTER it. An early
return there charges somebody for a golden apple carrying an inert enchantment. Finish what the table
started; the gate is upstream.

**A book bypasses `supported_items` entirely.** `isPrimaryItemFor` is
`isPrimaryItem(stack) || stack.is(Items.BOOK)`, so any enchantment in the table's tag can roll onto a
book whatever it claims to support. A Blessing book was then a paid-for dead end - applied on an
anvil it gave a golden apple carrying an inert enchantment, because nothing posts
`PlayerEnchantItemEvent` outside the enchanting table. Closing that needed either a mixin on a
NeoForge default method or an `AnvilUpdateEvent` handler that makes the book work. It works.

**Guard on the enchantment, not just the item.** Another mod could make golden apples take an
enchantment of its own; turning that into an enchanted golden apple would be this mod quietly eating
somebody else's feature.

## Adding to a vanilla block's interactions, without a mixin or an event of ours

The cauldron transforms (concrete powder sets, dirt becomes mud) hang off **vanilla's own extension
point**, and finding it was most of the work.

**`AbstractCauldronBlock.useItemOn` asks a `CauldronInteraction.Dispatcher`**, and vanilla keeps one
per fill state - `empty`, `water`, `lava`, `powder_snow`. NeoForge's `RegisterCauldronInteractionEvent
.Interaction` (mod bus) puts an entry in a named one. Reaching for `UseItemOnBlockEvent` instead would
have worked and would have run this mod's code on every right click on every block in the game to
answer "no".

**Register against a TAG, not an item, and that is what makes it extensible.** The dispatcher is built
once at startup, long before data packs load, so a per-item registration would freeze the list. But
`Dispatcher.get` evaluates `stack.is(tag)` at click time and tag contents ARE data pack material. So
one tag registration at startup gives packs a seam they can widen later.

**A tag entry SHADOWS a vanilla item interaction rather than sitting beside it**, and the first
version of this section said the opposite. `Dispatcher.get` walks its tag map first and returns on
the first hit, consulting the per-item map only if nothing matched - so any item in the tag routes to
this mod, and declining returns `TRY_WITH_EMPTY_HAND` rather than falling through to whatever vanilla
registered. Nothing shipped is affected, because concrete powder and dirt have no vanilla cauldron
interaction, so the config switch really does give a vanilla cauldron back. **But "it only ADDS
entries" is not true of the mechanism**, and a pack that tags a shulker box or a dyed leather item
stops it being washable, with the feature's own off switch unable to restore it. Vanilla registers
`#minecraft:cauldron_can_remove_dye` into the same `HashMap`, and two tags matching one item resolve
in no defined order.

**Which dispatcher you register to is a gameplay decision.** `registerToAll` is one word away and
would let an EMPTY cauldron transform things out of nothing.
`an_empty_cauldron_transforms_nothing` pins the water-only choice - and pins it for that reason
rather than the one first written on it, which claimed it covered the water-level check. It does not:
an empty cauldron is a different block on a different dispatcher, so the interaction is never
consulted and that check is never reached. Found by driving it red.

**Vanilla's statistics and game events are part of parity here.** Every vanilla water-cauldron
interaction awards `Stats.USE_CAULDRON` and `Stats.ITEM_USED`, and fires `FLUID_PICKUP` when it takes
water OUT against `FLUID_PLACE` when it puts water in. Getting the event backwards is invisible until
somebody points a calibrated sculk sensor at a cauldron.

**`Inventory.add` does NOT silently swallow a creative player's stack in general**, and a comment here
said it did. It zeroes the stack and returns true only when the add loop made no progress at all;
with room it stores normally. The creative branch in `CauldronTransforms` is still right, on vanilla
parity grounds (`ItemUtils.createFilledResult` leaves a creative player's input alone), but a right
branch with a wrong reason attached is worse than no reason, because the next person reasons from it.

**`ItemStack.CODEC` cannot be used in a data map.** Data maps are read before item components are
populated, so it fails with `Item minecraft:white_concrete does not have components yet`, the whole
map fails to load, and the feature silently does nothing with one ERROR line in the log. Store an item
id and a count and build the stack at use time.

**A data map rather than a recipe type, deliberately.** #36 assumed this would define the format #34
(brewing in a cauldron) would reuse. It does not and should not: brewing needs several ingredients, a
potion result and a heat source, while this is one item in and one item out keyed on the item, which
is exactly a data map's shape. Building a recipe type here to serve a feature nobody has designed yet
would have been speculation with ceremony.

**Declining an interaction is not the same as nothing happening.** When no interaction claims the
click, vanilla carries on to the item, so a block item gets PLACED above the cauldron. That is
vanilla's business rather than this feature's, but it eats one item, and it made two negative tests
fail for a reason that had nothing to do with cauldrons. They now fill the space above first.

## A component can be the whole feature

The armoured elytra is a chestplate with `minecraft:glider` set on it. No new item, no model, no
mixin, and no client code at all.

**Gliding in 26.1 is a marker component and the check is slot-generic:**

```java
public static boolean canGlideUsing(ItemStack itemStack, EquipmentSlot slot) {
    if (!itemStack.has(DataComponents.GLIDER)) return false;
    Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
    return equippable != null && slot == equippable.slot() && !itemStack.nextDamageWillBreak();
}
```

The elytra is not special. It is an item with 432 durability and
`.component(DataComponents.GLIDER, Unit.INSTANCE)`. `LivingEntity.canGlide` scans EVERY equipment
slot for any item with that component whose `Equippable` slot matches where it is worn, and flight
damage goes to whatever is gliding: `getItemBySlot(slotToDamage).hurtAndBreak(1, ...)`.

**So the whole feature falls out, and four design decisions with it.** The result renders as a
chestplate because it IS one; it keeps its armour value, its enchantments and its trim because
nothing copied them anywhere; it has one durability pool because there is only one item; and it stops
gliding at one durability left because `nextDamageWillBreak` is already in the check.

**The issue proposed the opposite** - a component on the ELYTRA carrying the chestplate - and listed
five decisions that were mostly artefacts of that shape. Worth remembering as a pattern rather than a
one-off: **before designing a container, check whether the behaviour you want is already a component
you can set on the thing you already have.** The wrong design was the plausible one.

**Look for the tag, too.** `#minecraft:chest_armor` already lists all seven chestplates, including
26.1's new copper one, so a modded chestplate that joins it works with no compat patch.

## Testing a payload handler

The server side of the Z key had zero coverage for a while, because the only caller is the network
layer delivering a packet and a headless test has no client to send one. It is now covered by a fake.

**`IPayloadContext` has SEVEN abstract methods** in 26.1.2.76; everything else on it is a default.
The issue that filed this guessed nineteen and deferred the work on that basis, which is the argument
for counting before estimating - and then the PR making that argument asserted a line count nobody
had counted either, and a review caught it. The lesson does not exempt the person stating it.

`gametest/FakePayloadContext` returns the player, runs `enqueueWork` inline, and **throws for
everything else** rather than returning null, so a handler reaching for something the fake does not
model fails by name instead of somewhere downstream.

**That seven has an expiry date.** `IPayloadContext` is `@ApiStatus.NonExtendable`, so NeoForge may
add to it in a patch release; a bump that does will break `compileJava` on the fake. Loud, one place,
one line to fix, and worth knowing before it happens.

**`FTPayloads.onToggleAutoSwap` is public purely so a test can call it**, the same trade
`FTConfig.switchFor` makes. Package-private would be tighter and was rejected for a specific reason:
it would force the test into the `network` package, and the coverage gate excludes `gametest/**`
only, so the test class would then be measured as production code.

**A mock player's outbound packets ARE readable, and this section first said they were not.**
`makeMockServerPlayerInLevel` builds a real `Connection` over an `EmbeddedChannel`, so
`player.connection.getConnection().channel()` casts to `EmbeddedChannel` and `outboundMessages()`
holds everything the server sent it. Clear it first: joining queues twenty-odd packets before
anything a test cares about.

That is how the action bar line the Z key answers with is asserted, translation key and all. It
matters more than it looks: the message is the ONLY thing the pack-off branch changes, because
everything else the key protects is enforced a layer down in `ToolSwapper.swapping`. Without reading
the packet, a handler that kept its guard and reported the wrong state passed every assertion.

**The claim that it could not be read survived one probe.** Check before documenting a limit; this
file has now been wrong about what a test can see more than once.

## A workstation of our own, and what vanilla will not lend you

The woodcutter is a block, a menu, a screen and a recipe type. It was **not** built that way first:
version one wrote `minecraft:stonecutting` recipes so the vanilla stonecutter would cut wood, which
works - `StonecutterMenu` has no ingredient restriction at all, it looks up `RecipeType.STONECUTTING`
and nothing else - and was rejected (owner, 2026-09-07) because a stone saw is the wrong block to be
cutting planks on. Written down because the cheap version is genuinely tempting and genuinely wrong.

**`RecipeAccess` is not extensible, and that decides the menu's whole design.** Vanilla's cutter
reads `level.recipeAccess().stonecutterRecipes()` - a prebuilt list the server syncs so the client
can draw the buttons. That interface declares exactly one `stonecutterRecipes()`, and
`RecipeManager`'s property sets are a fixed `Map.of`. A custom cutter cannot reuse any of it.

**So the options are real slots.** `WoodcutterMenu` puts each possible result in a container and adds
it as a display slot that refuses pickup and placement. Menus already sync slot contents, so the
client learns the options for free, and there is no second recipe-syncing mechanism that can drift
out of step with the server. The screen turns a click on one into `clickMenuButton`, which is the
route vanilla's cutter uses anyway.

**The recipe lookup is server-only.** A client has no full recipe manager in 26.1, so `refreshOptions`
sits behind an `isClientSide` guard; calling it on the client would find nothing and blank the list.

**`StonecutterBlock` cannot be subclassed**, the same codec-invariance trap `PressurePlateBlock` has:
`codec()` returns `MapCodec<StonecutterBlock>`. There the answer was to extend the abstract parent;
here there is no abstract cutter, so `WoodcutterBlock` extends `Block` and copies the shape.

**`RecipeBookCategories` is a list of static fields, not a registry**, so a new cutting category is
not registrable and the recipes borrow the stonecutter's. It decides only which heading the recipe
book files them under.

**The base is ours, the blade is vanilla's** (owner, 2026-09-07). `tools/generate_woodcutter.py`
draws the top, side and bottom - boards, a bevel, and a slot down the middle where the saw rises -
and the model keeps `minecraft:block/stonecutter_saw` for the blade itself. A blade is steel whatever
bench it is bolted to, and it is the part a player already reads as "this cuts things"; redrawing it
would invent a difference that is not there.

**Drawn from numbers rather than recoloured from vanilla**, which matters for more than tidiness:
recolouring Mojang's textures would ship a derivative of their art. Same reason `generate_plates.py`
exists. Seeded with `zlib.crc32` and never `hash()`, and `tools/test_generate_woodcutter.py` compares
by PIXEL across a separate process - bytes are the wrong question for a PNG, as the plate generator's
own test explains at length.

**The screen still ships no art** and still cannot be tested: it blits vanilla's stonecutter
background and button sprites, and `client/**` is excluded from the coverage gate because nothing
automated reaches it. It wants a look through devbridge.

## Slabs of the ground, and the three traps in them

The terrain slabs are nine half blocks of dirt, gravel, sand and the rest. The feature is small
because of one fact that had to be checked rather than assumed, and awkward in three places that
nothing would have caught.

**`SlabBlock` CAN be subclassed, unlike the other two vanilla blocks this mod wanted to extend.**
`PressurePlateBlock` declares `codec()` as `MapCodec<PressurePlateBlock>` and `StonecutterBlock` the
same, and generics are invariant, so neither can be subclassed - both are written up above as traps.
`SlabBlock` declares `MapCodec<? extends SlabBlock>`, a wildcard, so it is open. **Check the codec
before assuming the trap generalises**: had it been assumed, the type property, the shape,
waterlogging and the doubling rule would all have been reimplemented by hand for no reason.

**A falling slab cannot inherit falling.** `FallingBlock` extends `Block` and so does `SlabBlock`, so
`FallingTerrainSlabBlock` copies the falling half - a scheduled tick, an `isFree` check, one call to
`FallingBlockEntity.fall`. Copying the smaller half is the only option and falling is much the
smaller. One deliberate difference: **a TOP slab falls as a BOTTOM one.** `FallingBlockEntity` carries
the state it was handed and places it unchanged, so a top slab would land as a top slab, floating with
a gap underneath and resting on nothing.

**A BOTTOM slab can never be snowy, and copying `SnowyBlock` literally gets this wrong.** Vanilla asks
what is in the block above. That is right for a top slab, whose upper face IS the block boundary, and
wrong for a bottom slab, whose upper face is half way up its own position - a snow layer in the block
above floats eight pixels clear of it. Same shape of error in `RootedDirtSlabBlock`, which refuses
bonemeal on a top slab because hanging roots need a sturdy face at the boundary and a top slab has
none; growing them would consume the bonemeal for roots that immediately pop off.

**And in `MudSlabBlock`, where the mistake is the opposite direction.** Vanilla's `MudBlock` returns a
FULL BLOCK for its support and visual shapes, which is true of a mud block and false of a mud slab.
Restating it would tell the game a half block is a whole one. Only the COLLISION shape is short.

**What a falling slab does when it lands on another slab, which devbridge was used to answer.** It
pops as an ITEM rather than stacking or merging, and that is vanilla's behaviour rather than a defect
here. An entity resting on a half-height slab has its feet at y+0.5, so `blockPosition()` floors to
the slab's OWN position; `FallingBlockEntity` then asks that block whether it may be replaced,
passing a `DirectionalPlaceContext` holding `ItemStack.EMPTY`. `SlabBlock` allows the merge only when
the held item matches, nothing is held, so it declines and the entity takes the drop-as-item branch.

**A merge into a double would be nicer and is not available.** Reaching it means overriding
`canBeReplaced` to accept an empty stack, and an empty stack carries no information about what is
falling - so the same override would let an anvil or a pointed dripstone delete the slab instead of
landing on it. Losing a block to a falling anvil is a worse bug than making somebody pick an item up.

`a_falling_slab_landing_on_its_own_kind_is_not_destroyed` pins the only outcome that would be a real
defect, which is the slab silently vanishing - no error, no log line, no drop, and nothing else in
the suite would see it.

### Spreading runs in two directions and only one of them can be pushed

Grass and mycelium slabs came second, and the reason is the split that runs through this whole
feature: the other nine families needed a PROPERTY, these two need a SYSTEM. Vanilla puts all of it
in `SpreadingSnowyBlock`, which no slab can extend because `SlabBlock` is already the parent.

**The push is easy and the pull is the interesting half.** `SpreadingTerrainSlabBlock` converts
nearby dirt, slab or full block, by adapting vanilla's 3x5x3 walk. What it cannot do is the reverse:
vanilla's own `GrassBlock` looks for `Blocks.DIRT` and will never see a dirt slab, and there is no
event or data hook that changes what it looks for. So `DirtTerrainSlabBlock` random-ticks and pulls -
it looks for a vanilla grass or mycelium block near it and converts ITSELF. Between the two, every
combination works and no second mixin is needed.

**That is why the dirt slab random-ticks when vanilla dirt does not.** A block that has to notice its
neighbours has to be given a moment to look.

**Spreading only crosses between matching halves.** A bottom grass slab and a top dirt slab have half
a block of air between them; grass creeping across would be growing on a surface nothing rests on.

### `canStayAlive` asks the wrong question about a slab, and the answer is catastrophic

**Every TOP grass slab died back to dirt on its first random tick, under open sky.** Vanilla's check
is `LightEngine.getLightBlockInto(state, aboveState, UP, ...)`: how much light is blocked ENTERING
this block from above. For a full grass block that is the same question as "is my surface covered",
because the surface and the block boundary are the same plane.

For a slab they are different questions. **`SlabBlock.useShapeForLightOcclusion()` returns true**, so
the engine computes real shape occlusion instead of taking the opaque-block shortcut - and a top
slab's own material seals its own top face. Passed its own state, the check reported 15 with nothing
above it at all.

The fix is to measure with a full block of dirt standing in for the slab, which asks what the block
ABOVE does and is what vanilla is actually measuring. **Nothing in the suite saw this**, because every
other spreading test happened to use a bottom slab; it surfaced only when a NEGATIVE test refused to
fail during a red drive. A test that cannot fail is worth chasing down even when everything is green.

### No tilling on slabs, and the ruling agrees with the code by accident

**(owner, 2026-09-08)** A hoe does nothing to a terrain slab, on any half. That was already true
before the ruling, for a reason nobody chose.

**`HoeItem.TILLABLES` is NOT that reason, and the first version of this section said it was.** That
map carries `@Deprecated` and the javadoc "Forge: This map is patched out of vanilla code"; a grep of
the whole patched tree finds it in exactly one place, its own declaration. Nothing reads it, so a
`put` into it changes nothing at all. The section was written after reading the map and stopping
there - the `@Deprecated` was on screen at the time and went unfollowed.

**What actually decides it is `IBlockExtension.getToolModifiedState`.** `HoeItem.useOn` asks
`level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, false)` and does
nothing when that is null. The default implementation hardcodes `GRASS_BLOCK`, `DIRT_PATH`, `DIRT`,
`COARSE_DIRT` and `ROOTED_DIRT`, and a slab of any of them is not among them.

**So there are two live seams, and both are open**: overriding `getToolModifiedState` on a block, and
`UseItemOnBlockEvent`. The second matters most here, because this repo's stated preference is to
change vanilla item behaviour through events rather than mixins - the enchanted apple and the
cauldron transforms both do exactly that, and `CauldronTransforms` names `UseItemOnBlockEvent`. The
most plausible way this mod ever tills a slab is the way it already adds behaviour everywhere else.

`a_hoe_tills_dirt_and_refuses_a_dirt_slab` pins it **through `ItemStack.useOn`**, which is where
`UseItemOnBlockEvent` is posted, rather than through `Item.useOn`, which skips it. Calling the item
directly would have left the suite green against the one regression worth fearing. It also carries a
**vanilla control**, because a test that only checks the hoe did nothing passes just as well when the
hoe was never swung, the position was wrong, or the API moved.

**The shovel is the opposite case, and saying the two were "keyed the same way" got both wrong.**
`ShovelItem.FLATTENABLES` IS still read, by `getShovelPathingState`, which `IBlockExtension` calls. A
`put` there really would work. Slabs are absent from it, which is why shovelling one into a dirt path
does nothing - but that is a live map, not a dead one.

### A tintindex names a tint slot; something has to fill it

**The grass slab shipped rendering flat white-grey next to a green vanilla grass block.** Its model
carried `tintindex: 0` on the top face and the side overlay, copied faithfully from vanilla's own
`block/grass_block`. What it did not carry, because a model cannot, is anything that FILLS that slot.
Vanilla fills the grass block's from `BlockTintSources.grassBlock()` in `BlockColors`; a modded block
gets nothing by default, so the raw `block/grass_block_top` texture rendered as drawn, which is
greyscale.

`client/TerrainSlabColors` registers the same source vanilla does, through
`RegisterColorHandlersEvent.BlockTintSources`. Verified against `BlockColors.java:27`, which is
`colors.register(List.of(BlockTintSources.grassBlock()), Blocks.GRASS_BLOCK)` - the identical call.
That is what makes biome colour and cross-biome blending right by construction rather than by
coincidence: `grassBlock().colorInWorld` resolves `BiomeColors.getAverageGrassColor(level, pos)` per
position. The same grep confirms vanilla registers NO tint for mycelium or podzol, which paint their
colour into the texture, so only grass is registered here.

**The ITEM needs its own tint, and in 26.1 that is data rather than code.** A block tint source
colours the block in the world and does nothing for the icon in a hand. Vanilla's own
`items/grass_block.json` carries a `tints` entry with a fixed temperature and downfall for the
neutral out-of-world green, and the generator writes the same.

**No test in this repo could have caught any of it.** Tint is applied during chunk baking on the
client, so the server has no opinion and every GameTest passes either way - which is precisely why
`client/**` is excluded from the coverage gate. It was found by putting a slab next to its block in a
dev client and looking at the two. `test_generate_terrain_slabs` now closes the DATA half (a tinted
model must have a tinted item); the Java registration is still only checkable by eye.

**Two false starts worth knowing about**, because both wasted a screenshot each. `TaskStop` on the
gradle wrapper does NOT kill the spawned client, so two were running and `gamebridge` was talking to
the stale one - the fix made no visible difference twice in a row while being correct on disk. And
`fillbiome` does not repaint a chunk the client has already baked, so a biome comparison shot shows
the old colours however many biomes you set.

### The sides of a layered slab use the TOP half of the texture

**(owner, 2026-09-08)** Grass, podzol and mycelium paint a fringe of the surface material across the
top of their side texture. Vanilla's `block/slab` parent crops a bottom slab's sides to the BOTTOM
half of the texture, which is right for a uniform texture and wrong for these: a bottom grass slab
would show nothing but dirt on all four sides, with the green stopping dead at the top face.

Taking the top half whichever half of the block it is makes the surface read as spilling over the
edge. Vanilla has no layered slab to copy, so there is no precedent either way and this is a call.
It is also why those three families need element geometry at all - that parent hardcodes its UVs.

### The recipe collision, and why a file-existence test would have shipped it

`every_terrain_slab_has_a_recipe_that_actually_crafts` resolves each recipe through the real crafting
lookup, and found two collisions that no file check could see:

- **Three snow blocks is already vanilla's snow LAYER recipe.** Chasing it turned up the better
  reason to drop the family entirely: a snow layer is already a stackable partial snow block, so
  vanilla effectively has a snow slab, and this mod's entry test is that a thing should be something
  vanilla should plausibly have and does NOT.
- **Three gravel is already this mod's own `gravel_to_flint`, and it is SHAPELESS.** So it matches any
  arrangement of three - a row, a column, an L - and no three-gravel shaped recipe can coexist with
  it. A column was tried first on the assumption the flint recipe was shaped; it was not. Gravel now
  takes **six for twelve**, the same 1-to-2 rate in a bigger batch, because six ingredients cannot
  match a shapeless three.

**Two shaped recipes with the same pattern do not merge and do not error.** The recipe manager returns
whichever it finds first, so the player gets flint or a slab depending on load order and nothing
anywhere reports a problem.

### One owner for `en_us.json`

**Two generators writing the same file wholesale is a landmine, and it was armed for about an hour.**
`generate_plates.py` built the lang file from its own static table plus the plate names and wrote it
out, which was fine while it was the only generator with names. The moment the slab generator also had
names, running the plate generator silently deleted every slab name.

Merging in each generator would have worked and would have been worse: the contents would then depend
on which generator ran last, so the committed tree would be reproducible only in the right order and
nothing would say what that order was. `tools/ft_lang.py` is now the single owner, assembling from
every table, and both generators call it. Run either, or both, in any order, and the same file lands.
The rule that a name is never typed into the json by hand is unchanged - it is what this preserves.

## Events that only fire on one side

**`PlayerInteractEvent.LeftClickBlock` is CLIENT ONLY in 26.1.** It is posted from
`MultiPlayerGameMode` and nothing posts it server-side, so a server-side handler for it never runs.
It reads as exactly the right hook for "the player started digging" and it is not one.

That cost a working-looking implementation. The test that caught it drove
`ServerPlayerGameMode.handleBlockBreakAction` - vanilla's own break path - rather than posting the
event by hand, and posting by hand had hidden the bug completely.

**So before hooking an event, check where it is actually posted.** `grep` the NeoForge sources for
the hook that constructs it and see which class calls that hook. The auto-swap ended up on
`PlayerEvent.BreakSpeed`, which fires server-side every tick of a dig, acted on only when the block
position changes so that it means "the start of a dig".

## A commit message can close an issue by accident

**GitHub reads only the word immediately before an issue reference**, not the sentence around it. So
`FILED RATHER THAN FIXED: #49` closed #49 the moment that PR merged, in a commit whose whole point
was to say the work had NOT been done. It had to be reopened by hand.

This repo's commit style makes it likelier than usual: bodies here lead with capitalised summary
phrases, and several natural ones end in a closing keyword - `FIXED`, `CLOSED`, `RESOLVED`.

**QUOTING THE PHRASE IS ENOUGH TO FIRE IT, and that is not a hypothetical.** The commit that first
added this section quoted the offending line to explain it, and closed the issue a second time.
Quotation marks are not an escape, and neither is surrounding prose saying the opposite.

So: when writing about an issue anywhere near a closing keyword, **do not put the hash there at
all.** Write "issue 49". Reserve `#49` for places where a keyword is nowhere near it, or where
closing is actually what you want.

**IT HAS NOW HAPPENED A THIRD TIME, in a PR body rather than a commit, and after this section was
written.** The terrain slab PR carried the sentence "This does NOT close #31" - written deliberately,
to tell a human reader the work was partial - and merging it closed issue 31. The negation is
invisible to GitHub, which reads only the word before the reference. **A PR body is subject to the
same rule as a commit message**, and this section did not say so; it does now. The reopened issue
carries the note.

The pattern across all three is the same and is worth naming: every one was written by somebody who
was thinking ABOUT the closing behaviour at the time. Explaining the trap, quoting the trap, and
denying the trap all fire it.

## The commit trailers, and why the first four lack them

Every commit from `f084abb` onward carries `Co-Authored-By` and `Claude-Session`. The first four do
not, and **they will not be rewritten** (ruling 2026-09-05, issue #5).

This reverses the recommendation in that issue, which argued for rewriting on the grounds that it
would never be cheaper than while the repo was hours old. The facts moved: the repo is public, a
CurseForge file now points at it, and four merged PRs and their CI runs reference those SHAs. That
turns a tidy-up into a force-push of a public history for cosmetic uniformity, which is not a trade
worth making. The convention is established going forward, which is what it was for.

## Deliberate deviations from the sibling repos

- **No JEI or Jade compat CODE, but both are in the dev client.** There is no integration source in
  this repo and nothing compiles against either. `./gradlew fetchDevMods` downloads them into
  `run/mods` (gitignored, the same route devbridge takes) and the client run depends on that task.
  They are there so a human can see what a player in a pack sees - above all whether the plate
  recipes actually LOADED, which is what the config's recipe condition changes and the one thing no
  headless test can look at.
- **No `texgen.toml`.** Textures come from `tools/generate_plates.py`; move to `mc-pack-toolkit`'s
  texgen if the art gets ambitious.
