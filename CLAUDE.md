# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

**What this is:** a standalone **NeoForge** mod (MC 26.1.2). A grab bag of blocks, tools and small
features that vanilla never shipped. Mod id / package: `flattsthings` / `com.flatts.flattsthings`.

**Status:** v0.1.0. One family shipped, the **Player Pressure Plates**: a player-only counterpart to
each of the fourteen vanilla `PressurePlateBlock`s (`docs/player_pressure_plate_spec.md`).

## The shape of this mod, and what follows from it

**There is no spine.** Nothing here gates anything else. That is the point of the mod and it is also
its main risk: with no progression to hide behind, each addition is judged alone, and the mod is only
as good as its worst entry. A thing earns its place by being something vanilla should plausibly have
and does not.

**Prefer vanilla parity everywhere except the one thing that is the feature.** The player plates keep
vanilla's hold time, sounds, signal strength, block properties and silhouette, and change only the
sensitivity. A player should be able to guess how a thing behaves from the vanilla block it
resembles, and be right about everything except the part that is new.

**Copy vanilla properties, do not restate them.** `FTBlocks.propertiesOf` uses
`Properties.ofLegacyCopy(vanillaBlock)`. Restating fourteen property chains by hand is how a variant
ends up subtly wrong, and the traps are not where you would guess - crimson and warped are the two
woods that are NOT `ignitedByLava`. Use `ofLegacyCopy`, never `ofFullCopy`: the full copy also
carries `drops` and `descriptionId`, so the block would roll the VANILLA loot table under the
VANILLA name, and both failures read as data problems rather than code ones.

**When a thing comes in variants, the list will exist twice** - once in Java, once in
`tools/plate_variants.py` - and that is accepted rather than engineered away, because the two sides
need different data. It is safe only because two tests close the loop:
`every_vanilla_pressure_plate_has_a_player_counterpart` catches a variant missing from the JAVA side
by walking the registry rather than the list, and `RegistryCompletenessTests` catches one missing
from the GENERATOR side by finding a registered block with no files. Add a variant kind without
both, and the next one silently half-ships.

**Growth is by accretion, so the completeness sweep is load-bearing.**
`gametest/RegistryCompletenessTests` asserts every registered item and block has a translated name, a
blockstate, a 26.1 client item definition, a loot table and a slot in the creative tab. None of those
failures breaks a compile, and all of them are invisible until someone plays. Add to the sweep before
adding a new kind of thing, not after.

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
| Dev client | `./gradlew runClient` |
| Regenerate IntelliJ run configs after `clean` | `./gradlew prepareAllRuns` |
| Regenerate plate resources (textures, models, recipes, lang) | `python tools/generate_plates.py` |

**Never pipe gradle to `tail`/`head` and trust the exit code** - the pipe reports the pager's status
(0) and masks a Gradle failure. Redirect to a file and check `$?`, or use `PIPESTATUS`.

`runGameTestServer` boots a headless server, runs every registered test in a scripted plot, and exits
non-zero on failure. Its pass count includes one vanilla built-in test running in the
`minecraft:default` batch alongside ours, so the reported total is always this mod's **plus one**.

CI (`.github/workflows/ci.yml`) runs `build` and `gameTest` as two independent jobs. **The `build`
job name is load-bearing** if branch protection is ever configured to require that status check.

`unitTest` is enabled in `build.gradle` (moddev's JUnit integration, which runs `src/test/java`
against a loaded mod context) and there are **no JUnit tests yet**, because nothing in the mod is
pure logic so far. Use one the moment something is: no world, no rendering and no server means a
GameTest is the wrong instrument and a slower one.

## Testing conventions that are not optional here

**Pair every negative with a vanilla control.** "A cow does not press the player plate" passes just
as green when the cow was never placed in the plate's box at all. Each negative in
`PlayerPressurePlateTests` is shadowed by a test asserting the equivalent vanilla plate DOES fire on
that same entity, so a positioning mistake fails the control instead of silently passing the feature.

**Drive the game's own entry point, not a walk.** A plate is pressed from
`BlockStateBase.entityInside`, which the game calls out of `Entity.checkInsideBlocks` during
movement. A mock server player is not driven by client packets and a `spawnWithNoFreeWill` mob does
not wander, so waiting for either to step on a plate is a test that passes by timing out. Call
`entityInside` directly with a real entity positioned in a real level.

**Drive a new test RED before trusting it, and mean it.** This is not a formality here; it has already
paid twice. Swapping `Player.class` for `Entity.class` in the block failed exactly the two feature
assertions and left both controls green, as intended. But the vanilla-parity sweep PASSED when a
variant was deliberately pointed at the wrong vanilla block, because it compared each plate against
`variant.vanilla()` - the same field its properties were copied from - so the two agreed by
construction. **An expectation taken from the thing under test is not a test.** It now resolves the
expected block from the material name via the registry, and fails as it should.

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

## Deliberate deviations from the sibling repos

- **No CurseForge publish task yet.** There is no CurseForge project for this mod. Add the
  `net.darkhax.curseforgegradle` task at first release, following `spawn-detective`'s conditional
  form (register a stub unless a project id is configured) rather than recompile's unconditional one.
- **No JEI or Jade compat, no `texgen.toml`.** Nothing here needs them yet. Textures come from
  `tools/make_textures.py`; move to `mc-pack-toolkit`'s texgen if the art gets ambitious.
