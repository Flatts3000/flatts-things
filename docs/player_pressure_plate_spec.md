# Player Pressure Plate

**Status:** shipped in v0.1.0.

## The gap

Vanilla has two pressure plate sensitivities, and the block does not choose either one. The
`BlockSetType` does, through `pressurePlateSensitivity()`:

| Plate family | Sensitivity | Fires for |
| --- | --- | --- |
| Wood (oak, spruce, bamboo, ...) | `EVERYTHING` | any `Entity`, including dropped items and arrows |
| Stone, polished blackstone | `MOBS` | any `LivingEntity`, including every passive mob |
| Weighted (gold, iron) | n/a, counts items | dropped item stacks |

Nothing in that set means "a player, and nothing else". The two failures are ordinary and both are
familiar to anyone who has built with plates:

- A stone plate at a door is opened by a cow, a chicken or a wandering trader that walks over it.
- A wooden plate is fired by an arrow, a dropped item, or a boat drifting across it.

A player-only plate is the missing third row. It is the one a door, a shop counter, a lobby or a
trapped corridor actually wants.

## The rule

The plate outputs redstone 15 while at least one **player** is inside the plate's touch box, and 0
otherwise. Everything else is vanilla:

- The touch box is vanilla's `TOUCH_AABB`, unchanged.
- The hold is vanilla's 20 ticks, scheduled the same way, so it releases the same way.
- The click sounds, game events and neighbour updates come from `BasePressurePlateBlock` untouched.
- Block properties are `stone_pressure_plate` verbatim: `noCollision`, strength 0.5, BASEDRUM
  instrument, destroyed by pistons.

**Spectators do not press it,** because `getEntityCount` filters them, exactly as every vanilla plate
does. **Creative-mode players do press it,** also exactly as every vanilla plate does, so a build
tested in creative behaves the way it will when played.

## Why it extends the abstract parent

The only behaviour that changes is `getSignalStrength`, so subclassing `PressurePlateBlock` and
overriding that one method looks right. It does not compile. Every block must declare a `codec()`,
and `PressurePlateBlock` declares its return type as `MapCodec<PressurePlateBlock>`. Java generics
are invariant, so an override returning `MapCodec<PlayerPressurePlateBlock>` is not a valid
covariant return against it.

`BasePressurePlateBlock` declares `MapCodec<? extends BasePressurePlateBlock>`, which accepts one.
The cost is re-implementing the three trivial `POWERED` methods, which is the whole difference
between the two classes anyway.

## Recipe

Shapeless: one `minecraft:stone_pressure_plate` plus one `minecraft:ender_pearl`.

The ender pearl is doing real work in the design and not just adding cost. It is the vanilla item
most associated with a player specifically rather than with mobs or machinery, and it puts the plate
after the first trip out into the world rather than in the first ten minutes, which is about where
the problem it solves starts to matter. It is cheap to change if playtesting says otherwise.

## Tests

`gametest/PlayerPressurePlateTests`, six tests:

| Test | Asserts |
| --- | --- |
| `a_player_presses_the_player_plate` | the feature works |
| `a_mob_does_not_press_the_player_plate` | a cow does not fire it |
| `control_a_stone_plate_does_react_to_the_same_mob` | that cow was positioned to be seen |
| `a_dropped_item_does_not_press_the_player_plate` | a dropped stack does not fire it |
| `control_an_oak_plate_does_react_to_the_same_item` | that item was positioned to be seen |
| `the_player_plate_releases_when_the_player_leaves` | it does not latch on forever |

The two controls exist because the negatives can pass for the wrong reason. If the entity were never
actually inside the plate's box, nothing would press anything and both negatives would be green. The
controls fail in that case instead.

The suite was driven red on purpose before being trusted: replacing `Player.class` with
`Entity.class` in `getSignalStrength` fails exactly the two feature negatives and leaves all four
other tests green.

## Known limits

- **Fake players count as players.** Any other mod's fake player entity standing in the box will
  press the plate. No decision has been made about whether that is wrong; it is recorded here so the
  first person to hit it knows it was not an oversight.
- **There is no owner or team filter.** Any player presses it, not a specific one. A plate keyed to
  one player is a different block and probably a different design.
