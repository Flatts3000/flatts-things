# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

**What this is:** a standalone **NeoForge** mod, MC 26.1.2 / NeoForge 26.1.2.76 / Java 25, Gradle
9.5.1 via moddev 2.0.141. A grab bag of blocks, tools and small features vanilla never shipped. Mod
id / package: `flattsthings` / `com.flatts.flattsthings`.

**Status:** v0.1.0. One family shipped, the **Player Pressure Plates**: a player-only counterpart to
each of the fourteen vanilla `PressurePlateBlock`s (`docs/player_pressure_plate_spec.md`).

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

**Port 8610 is claimed for this repo** in `~/.claude/port_registry.yaml`. There is deliberately no
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
- **A block's tags** come from `BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()`; there is no
  `getTags()` on `BlockBehaviour`.

## Deliberate deviations from the sibling repos

- **No CurseForge publish task yet.** There is no CurseForge project for this mod. Add the
  `net.darkhax.curseforgegradle` task at first release, following `spawn-detective`'s conditional
  form (register a stub unless a project id is configured) rather than recompile's unconditional one.
- **No JEI or Jade compat, and no `texgen.toml`.** Nothing here needs them yet. Textures come from
  `tools/generate_plates.py`; move to `mc-pack-toolkit`'s texgen if the art gets ambitious.
- **No devbridge port claimed.** The sibling mods each claim one in `~/.claude/port_registry.yaml`.
  Claim one here before wiring devbridge, rather than taking its 25580 default.
