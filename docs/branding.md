# Branding

**Status:** current as of 2026-09-05. One asset exists; the wordmark is deferred and needs a ruling.

**Nothing here is AI output, and that is a rule rather than a preference.** ModJam 2026's terms say
"AI-generated project avatars and gallery images are not allowed", so it constrains this file.
Recompile's `docs/branding.md` carries the same constraint for the same reason.

| Asset | What it is | How it is made |
| --- | --- | --- |
| `branding/logo.png` | 400x400. The CurseForge project avatar, and the in-jar mod-list icon (`src/main/resources/logo.png` is written from the same run) | Drawn cell by cell from numbers by `branding/compose_logo.py` |
| `docs/cf_image_gallery/*.png` | The gallery | Real in-game screenshots, captured through devbridge by `tools/shoot_gallery.py` |

## The avatar

It is the mod's own mark: the head-and-shoulders figure the pressure plates carry, on a plate.

**The figure is imported from `tools/generate_plates.py`, not redrawn.** The avatar and the in-game
texture come from one `FIGURE` constant, so they cannot drift apart. Change the plate mark and the
avatar changes with it.

**The palette is deliberately not the stone plate's own.** In game the plate has to sit quietly among
vanilla blocks; as an avatar it sits in a grid of other mods' icons and has to be picked out at forty
pixels, so the plate reads lighter and the ground darker than either does in world.

**Seeded from `zlib.crc32`, never `hash()`.** Python salts string hashing per process, so `hash()`
would redraw the avatar differently on every run. That exact bug shipped once in the texture
generator; see `docs/player_pressure_plate_spec.md`.

Regenerate with `python branding/compose_logo.py`. It writes both copies.

## The wordmark, and why there isn't one

Recompile composites a full wordmark over its avatar, rendered in the Minecraft Title Generator. Two
reasons this repo has none yet:

1. **The name is long.** "Recompile" is one word and fits. "Flatts's Things" is two words and a
   possessive, and at 400x400 it lands somewhere between cramped and unreadable.
2. **The generator is a manual browser step.** It cannot be scripted, so a wordmark cannot be
   regenerated the way everything else here can.

Left as an open question rather than guessed at. If one is wanted, it needs a decision on whether the
avatar carries text at all, and somebody at the Title Generator.

## The gallery

Real screenshots, `1920x1080`, captured by `tools/shoot_gallery.py` against a running `runClient`.

**Each scene asserts its own premise before the shutter opens.** `02-a-cow-cannot-open-it` checks
four block states - the vanilla plate pressed, its door open, the player plate unpressed, its door
shut - and refuses to capture if any of them is false. That check exists because the first version of
the scene produced a beautifully composed photograph of two shut doors, which proved nothing and
would have shipped captioned as though it proved everything.
