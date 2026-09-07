# Flatts's Things

**Status:** v0.2.0 uploaded to CurseForge 2026-09-07 as an alpha. The project is still Under Review,
so nothing is downloadable by anyone yet. Six features. Last reviewed 2026-09-07.

A grab bag of blocks, tools and small features that vanilla Minecraft never shipped.

There is no tech tree here and nothing gates anything else. Each thing stands on its own, which
means each thing has to be worth adding on its own. A mod like this is only as good as its worst
entry.

**MC 26.1.2 / NeoForge / Java 25.**

## What's in it

### Player Pressure Plates

A player-only counterpart to every vanilla pressure plate. Fourteen of them, one per vanilla plate,
each crafted shapelessly from that plate plus a redstone dust.

Vanilla ships two sensitivities and neither is this one. A wooden plate fires for any entity, so an
arrow you shot at it opens your door. A stone plate fires for any living entity, so a wandering cow
does. Neither can say "a player, and nothing else", which is what a door, a shop counter or a
trapped corridor actually wants.

Each variant is its vanilla counterpart in every respect but that: same hardness, map colour, note
block instrument, flammability, click sounds, 20-tick hold, redstone output of 15, and the same
vanilla tags, so a wooden one is still axe-mineable and a stone one still pickaxe-mineable.
Properties are copied from the vanilla block rather than restated, so they cannot drift. The texture is the one
visible difference, carrying a small figure so you can tell the two apart on the floor.

![Every player pressure plate beside its vanilla counterpart](docs/img/plates_comparison.png)

Front to back: the pressed state, the player plates, and the vanilla plates they are made from.

The two **weighted** plates have no counterpart. `light_weighted_pressure_plate` and
`heavy_weighted_pressure_plate` are a different block that counts dropped item stacks and outputs a
proportional signal; "player-only" has no meaning for a block whose whole job is weighing items.

### Tool slots

Five dedicated slots for your tools, under the inventory panel, that are not part of inventory
space. A pickaxe, axe, shovel and hoe stop eating four of your nine hotbar slots.

![The tool slots under the vanilla inventory panel](docs/img/tool_slots_screen.png)

They are real slots in vanilla's own inventory menu rather than a screen of their own, so clicking,
dragging, stack splitting and tooltips are the game's and behave the way they do everywhere else.
Shift-click moves a tool in or out. Four of the five carry a faint outline of what belongs in them;
the fifth is deliberately blank, because it is the free one.

What fits is the `#flattsthings:tool_slot_valid` item tag, which defaults to the vanilla tool
families plus shears, so another mod's pickaxe fits with no compat patch and a pack can widen it in
a data pack. **Weapons are deliberately not in it.** These slots feed the auto-swap below, and a
storable sword would mean a mod that puts a weapon in your hand while you are mining.

**The strip is not on the creative inventory tab**, which is a different screen with a different
menu. That is deliberate rather than unfinished: a creative player has every item in the game two
clicks away. Stored tools are untouched and come back in survival.

### Tool auto-swap

Start breaking a block and the right tool comes out of your slots into your hand. Stop, and whatever
you were carrying comes back.

Selection is vanilla's own destroy-speed arithmetic, so a modded pickaxe sorts against a vanilla one
correctly and a tie goes to what you are already holding. The swap happens once, at the start of a
dig, because changing the held item resets destroy progress.

**Z toggles it**, under this mod's own category in Key Binds, and it says which way it went above
your hotbar. Your preference survives death and logout. It is your switch, not the pack's: it cannot
re-enable a swap a pack has turned off, and says so rather than pretending to toggle.

### Enchant a golden apple into an enchanted golden apple

The item's name says what it is, and vanilla has had no way to make one since 1.9. Put a golden
apple in an enchanting table, spend the levels and the lapis, and take out the real thing.

The offer is called **Blessing** and needs thirty levels, so a bare table cannot reach it and a full
ring of bookshelves is the price of admission. A Blessing book, which a table can roll onto a book
like any other enchantment, does the same job on an anvil.

### Silk touch picks up budding amethyst

Vanilla drops nothing for budding amethyst with any tool. This lets silk touch take it.

**This one changes the game rather than adding to it**, and the restriction it lifts is deliberate:
a budding block you cannot take is what stops an amethyst farm being picked up and moved. It ships on
like everything else, and its switch is there for packs that want vanilla's rule back.

### Three gravel makes one flint

Vanilla drops flint one time in ten, so getting a few means mining gravel until the dice cooperate.
Worse, gravel that does not roll flint drops as gravel, so with any Fortune shovel you can re-place
and re-break the same stack until every piece has become flint. This buys that loop out at three to
one: worse than any Fortune level in yield, better than digging unenchanted.

### Cauldron transforms

Dip a stack in a water cauldron and it comes out as something else. Concrete powder sets to
concrete; dirt becomes mud.

Both are things vanilla already lets you do the slow way. Concrete powder only sets against a water
**source block**, so the loop is carry a bucket, place a source, place powder against it one block at
a time, break the source, move on - a chore with no decision in it. Mud already comes from a water
bottle on dirt, one block at a time. The cauldron is the bulk version of both: a whole stack per
right-click, for one of the cauldron's three levels.

Washing dye off leather and filling bottles work exactly as they always did, and turning this off
gives you a completely ordinary cauldron. (A pack widening the tag should avoid items that already
have a cauldron use of their own, which this would take over.)

Packs extend it without code: which items react is the `#flattsthings:cauldron_transformable` item
tag, and what each becomes is the `flattsthings:cauldron_transform` data map.

## Every feature has a switch

`config/flattsthings-common.toml` carries one boolean per feature, all on by default.

A grab bag has to be a menu rather than a package deal, so a pack that wants the tool slots and not
the pressure plates can have exactly that. **Off means no new ones and never deletion:** a disabled
feature loses its recipe and its creative tab entry and stops running, while blocks already placed
keep working and tools already in a slot stay in it. A switch is always safe to flip back.

Recipes are turned off by a real data-pack condition rather than by hiding the item, which is the
only version of "off" that is also true in the recipe book and in JEI.

## Build and test

The system `JAVA_HOME` on the dev machine points at a JDK that does not exist, so **every** gradle
invocation needs it overridden:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build
```

| Task | Command |
| --- | --- |
| Compile only (fast feedback) | `./gradlew compileJava` |
| Full build + jar | `./gradlew build` |
| In-world GameTests (the real test layer) | `./gradlew runGameTestServer` |
| One test, or a wildcard group | `./gradlew runGameTestServer -Ptests=flattsthings:a_player_presses_the_player_plate` |
| Unit tests | `./gradlew test` |
| Merged coverage, both layers | `./gradlew test runGameTestServer -PgameTestCoverage coverageReport` |
| Dev client | `./gradlew runClient` |
| Fetch JEI + Jade into `run/mods` (dev client only) | `./gradlew fetchDevMods` |
| Regenerate IntelliJ run configs after `clean` | `./gradlew prepareAllRuns` |
| Regenerate plate resources (textures, models, recipes, tags, lang) | `python tools/generate_plates.py` |
| Build the dev world (once) | `python tools/make_dev_world.py` |
| Check for banned dashes | `python tools/check_dashes.py` |
| Check docs carry a dated status banner | `python tools/check_docs.py` |
| Redraw the logo | `python branding/compose_logo.py` |
| Capture the CurseForge gallery | `./gradlew runClient`, then `python tools/shoot_gallery.py --promote` |
| Publish to CurseForge (manual, needs a token) | `./gradlew publishCurseForge` |
| Screenshot every plate beside its vanilla counterpart | `./gradlew runClient`, then `python tools/shoot_plates.py` |

**Never pipe gradle to `tail` or `head` and trust the exit code.** The pipe reports the pager's
status (0) and masks a Gradle failure. Redirect to a file and check `$?`, or use `PIPESTATUS`.

## License

MIT.
