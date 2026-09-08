# CurseForge project page

**Status:** current for v0.5.0, pasted into the console on 2026-09-08. Project **1683375**, slug
**`flattss-things`** (two s's). This file stays the source of truth: edit here, then paste.

**Three claims that stood here were wrong, and the console said so the moment anybody looked.** It
said the page had never been pasted - a v0.3.0-era version was live, reading "Nine features". It said
the project was Under Review - it is **Approved**, has been for a while, and had already been
downloaded 99 times. And it said the gallery still showed only plates, which was true of the console
but not of this repo, where `04-terrain-slabs.png` and `05-the-woodcutter.png` had existed since
2026-09-08 11:02.

**Paste the BODY only** - everything between the two rules below, ending at "Not in yet". The
Listing metadata table beneath is notes for filling in console fields, and it was pasted into the
public description by mistake once already. CurseForge shows the game version, loader, licence and
source link as fields of its own.

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

### Slabs of the ground

Dirt, grass, mycelium, coarse dirt, rooted dirt, podzol, mud, clay, gravel, sand and red sand, at
half height. Eleven of them.

Vanilla has a slab of everything you build with and none of what you stand on, so a path that steps
up half a block has to stop being ground.

They keep doing what the block does. Grass spreads onto them and dies back under cover, and a dirt
slab left beside a grass block greens over on its own. Sand and gravel fall. You sink into mud. Grass
takes its colour from the biome, the way the block does, so a slab in a swamp and a slab in a plain
are not the same green.

Snow lies on a top slab and turns it white. It does not on a bottom slab, because the snow above is
not touching it.

Three across a bench makes six, the way every slab is made. Gravel is the exception at six for
twelve, because three gravel on a bench is already flint.

### Powered rails from copper

The same recipe as the gold one, with copper where the gold goes. Three rails instead of six.

Vanilla puts the only rail that speeds a cart up behind six gold for six rails, which is why early
rail is mostly pushing. Copper is the metal you have tonnes of and little to do with.

Half the yield is what keeps gold worth using. What comes out is the ordinary powered rail: no new
block, nothing oxidises, and you could not tell it from one made with gold.

### Every one of these has an off switch

`config/flattsthings-common.toml`, one line each. Off means no recipe and nothing in the creative
tab, and the behaviour stops. It never removes anything you have already built or stored.

### Not in yet

Eleven features, and each one had to earn its place on its own. More will follow the same way.

---

## Listing metadata

| Field | Value |
| --- | --- |
| Slug | `flattss-things` |
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
| `04-terrain-slabs.png` | Every terrain slab beside the block it is half of, bottom and top |
| `05-the-woodcutter.png` | The bench with a log, planks, and the cuts it offers |

`02` is the one that explains the mod. Lead with it if only one image is shown.

**All five were uploaded to Media on 2026-09-08**, with titles and captions, and `02` set as the
Feature Media so it leads. Before that the console gallery was **empty** - not plates-only, as the
banner at the top of this file guessed before anybody opened it, but nothing at all.

**Images must be UNDER 2 MB or the console refuses them**, one at a time, with
"File is too large, upload files smaller than 2 MB". Two of these were over it at 2.61 and 2.17 MB.
They were re-encoded from RGBA to RGB - the alpha channel was fully opaque in all five, so dropping
it is lossless, and it was checked pixel by pixel afterwards. That took the largest to 1.78 MB.
`shoot_gallery.py` still writes RGBA and does not know about the cap, so this will recur on the next
capture.

**Do not upload through the file input the page offers first.** There are two, and the obvious one is
the project FILE uploader: it accepts the PNG, navigates to Upload New File, and pre-fills a release
of the mod with a screenshot as the jar. It was cancelled without submitting. The right input is the
one inside the Media dropzone. Capturing more is a client run (`./gradlew runClient`, then
`python tools/shoot_gallery.py --promote`) and cannot be done headlessly.

**Six features still have no picture**, and the ones worth taking next, in order:

| Would show | Why it is worth a slot |
| --- | --- |
| The tool slot strip under the inventory panel | The one feature nobody can picture from a sentence. `docs/img/tool_slots_screen.png` already exists and could be promoted |
| A tool swapping in mid-dig | The swap is invisible when it works, so a still of the hand mid-block is the only way to show it happened |
| A golden apple in the enchanting table showing Blessing | Answers "how" and "what does it cost" in one frame |
| A cauldron of concrete powder before and after | The clearest before/after in the mod |
