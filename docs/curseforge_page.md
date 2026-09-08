# CurseForge project page

**Status:** rewritten for v0.2.0 on 2026-09-07 and **NOT yet pasted into the console.** Project
**1683375**, slug `flatts-things`. v0.1.0 and v0.2.0 are uploaded and the project is Under Review.
**The gallery has not been uploaded yet, and still shows only the plates** - five of the six features
have never been photographed. This file stays the source of truth: edit here, then paste.

**Voice: what it is like to play, not what it contains.** Short, image-led, written from inside the
game. No selling, no marketing verbs, nothing personified. Every line has to be true of the current
release. The reference is `../mc-pack-toolkit/quest-voice/voice_spec.md`.

**Claim discipline.** Everything below describes what ships today. Anything designed but unbuilt goes
under "Not in yet" or is left out.

---

## Flatts's Things

Blocks, tools and small features that vanilla never shipped.

There is no tech tree here and nothing gates anything else. Take the whole set or none of it.

### Player Pressure Plates

A pressure plate only a player can press. One for every vanilla plate, fourteen in all.

Vanilla gives you two choices and neither is this one. A wooden plate fires for anything that touches
it, so an arrow you shot at your own door opens it. A stone plate fires for anything alive, so the
cow that wandered into your base opens it too.

Put a player plate in front of the door instead. The cow stands on it and nothing happens.

Everything else about it is the plate you already know: the same hold, the same click, the same
redstone out of it, the same profile underfoot. It mines with the same tool, burns in the same
furnace, and takes the same piston shove. Only the sensitivity changed.

Craft one from the plate you have and a redstone dust.

### Tool slots

Five slots under your inventory, for tools, that are not part of your inventory space. The pickaxe,
the axe, the shovel and the hoe stop taking four of your nine hotbar slots.

They are slots in the inventory screen you already open. Clicking, dragging and shift-clicking work
the way they do everywhere else, because they are ordinary slots. Four of them show a faint outline
of what belongs in them. The fifth is blank; it is the free one.

Swords do not fit, and that is deliberate. These slots feed the swap below, and a stored sword would
mean something putting a weapon in your hand while you are mining.

### The right tool, without asking for it

Start breaking a block and the tool for it comes into your hand. Stop, and whatever you were carrying
comes back.

It picks by the same arithmetic the game uses to decide how fast a block breaks, so another mod's
pickaxe sorts correctly against a vanilla one, and a tie leaves what you are already holding alone.

Press Z to turn it off and on. It says which way it went above your hotbar, and it remembers across
death and logout.

### An enchanted golden apple, enchanted

Put a golden apple in an enchanting table, spend the levels and the lapis, and take out the real
thing. The offer is called Blessing, and it needs thirty levels, so a bare table cannot reach it.

A Blessing book works too, on an anvil, one apple at a time.

### Silk touch takes budding amethyst

Vanilla drops nothing for it, with any tool. This lets silk touch pick it up, and a geode becomes
something you can move.

That restriction is deliberate in vanilla, and lifting it is the whole feature. The switch is there
for anyone who wants it back.

### Three gravel, one flint

Vanilla gives you flint one gravel in ten, and the gravel that does not roll flint drops as gravel,
so a Fortune shovel and a wall to re-place it against turns every stack into flint eventually. This
buys that loop out: three gravel on a bench, one flint, no dice.

### Concrete and mud in a cauldron

Dip a stack of concrete powder in a water cauldron and it sets. Dirt comes out as mud.

Both are things you can already do one block at a time, against a water source or with a bottle. The
cauldron does the stack, for one of its three levels.

### Armoured elytra

Put a chestplate and an elytra on an anvil. The chestplate comes back gliding, and keeps its armour,
its enchantments, its trim and its name.

The elytra is used up, and anything enchanted on the elytra goes with it. From then on flying wears
the chestplate, and it stops gliding one durability before it breaks, the way an elytra does.

This one takes away a choice the game makes you make. Turn it off if you want that choice back.

### The woodcutter

A saw bench for wood. A log cuts into planks, or strips, or turns to bark. A stripped log makes a
shelf. Planks make stairs, slabs, buttons or sticks.

Everything comes out at the rate a crafting table would give you. Doors and fences stay on the
crafting table, where they cost what they should.

One plank makes one stair, where a crafting bench wants six for four. The game has a cutter for
stone and nothing for wood.

Craft it from planks and an iron ingot.

### Every one of these has an off switch

`config/flattsthings-common.toml`, one line each. Off means no recipe and nothing in the creative
tab, and the behaviour stops. It never removes anything you have already built or stored.

### Not in yet

Eleven features, and each one had to earn its place on its own. More will follow the same way.

---

## Listing metadata

| Field | Value |
| --- | --- |
| Slug | `flatts-things` |
| Categories | Redstone, Utility & QoL |
| Minecraft | 26.1.2 |
| Loader | NeoForge |
| Java | 25 |
| Environment | Client and Server |
| Release type | Alpha for 0.x |
| Licence | MIT |
| Source | https://github.com/Flatts3000/flatts-things |

## Gallery, in order

| File | What it shows |
| --- | --- |
| `01-every-plate.png` | All fourteen, pressed and unpressed, beside the vanilla plates they are made from |
| `02-a-cow-cannot-open-it.png` | Two doors, two cows. The vanilla plate opens its door and the player plate does not |
| `03-redstone.png` | An ordinary redstone source: pressed, it lights a lamp |

`02` is the one that explains the mod. Lead with it if only one image is shown.

**The gallery is a release behind.** Those three are all plates, and five features have shipped since
without a picture between them - above all the tool slots, which are the hardest to understand from
words and the easiest to show. Capturing more is a client run
(`./gradlew runClient`, then `python tools/shoot_gallery.py --promote`) and cannot be done headlessly.
A candidate list, in the order they would earn a slot:

| Would show | Why it is worth a slot |
| --- | --- |
| The tool slot strip under the inventory panel | The one feature nobody can picture from a sentence. `docs/img/tool_slots_screen.png` already exists and could be promoted |
| A tool swapping in mid-dig | The swap is invisible when it works, so a still of the hand mid-block is the only way to show it happened |
| A golden apple in the enchanting table showing Blessing | Answers "how" and "what does it cost" in one frame |
| A cauldron of concrete powder before and after | The clearest before/after in the mod |
