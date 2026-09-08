#!/usr/bin/env python3
"""Build and capture the CurseForge gallery scenes.

REAL IN-GAME SCREENSHOTS, which is a rule rather than a preference: ModJam 2026's terms say
"AI-generated project avatars and gallery images are not allowed". Every image here is the game
rendering the mod, driven through devbridge.

Scenes are built at separated origins so one run produces all of them without tearing down between
shots, and so a re-run overwrites rather than accumulating.

Usage
-----
    ./gradlew runClient          # in another terminal
    python tools/shoot_gallery.py

Images land in run/screenshots/ and are promoted into docs/cf_image_gallery/ by --promote.
"""

from __future__ import annotations

import argparse
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from plate_variants import VARIANTS, block_id  # noqa: E402
from terrain_slab_variants import VARIANTS as SLABS  # noqa: E402
from shoot_plates import GROUND_Y, PLATE_Y, cmd, make_safe, run  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
SHOTS = REPO / "run" / "screenshots"
GALLERY = REPO / "docs" / "cf_image_gallery"


def require(condition: str, claim: str) -> None:
    """Assert a block state, so a scene cannot be captioned as showing what it does not show.

    The first version of scene 2 produced a perfectly composed image of two SHUT doors and would
    have shipped captioned "a cow cannot open it". The vanilla door was shut too, so the picture
    proved nothing. Two causes, both invisible in the render: `tick freeze` was still on from the
    previous scene so nothing ever moved, and a cow summoned exactly at surface level never falls,
    so Entity.checkInsideBlocks never runs and no plate is ever told anything.
    """
    result = run("--player", "@s", "cmd", "--strict",
                 f"execute if block {condition} run tp @s ~ ~ ~", check=False)
    if result is None:
        raise SystemExit(f"scene does not show what it claims: {claim}")


def clear(x0: int, z0: int, x1: int, z1: int) -> None:
    # UNFREEZE FIRST. shot() freezes at capture, so without this every scene after the first is
    # built in a stopped world where nothing falls, settles, or triggers anything.
    cmd("tick unfreeze", strict=False)
    # Set every time the world is unfrozen, not once at the start: `tick freeze` holds the clock, so
    # a scene captured after an earlier freeze inherits whatever hour that one stopped at. The first
    # pass shot the whole gallery at dusk.
    cmd("time set noon", strict=False)
    cmd("weather clear", strict=False)
    cmd(f"forceload remove all", strict=False)
    cmd(f"forceload add {x0} {z0} {x1} {z1}")
    cmd(f"fill {x0} {PLATE_Y} {z0} {x1} {PLATE_Y + 4} {z1} minecraft:air", strict=False)
    cmd(f"fill {x0} {GROUND_Y} {z0} {x1} {GROUND_Y} {z1} minecraft:smooth_stone", strict=False)


def look_from(x: float, y: float, z: float, yaw: float, pitch: float,
              sweep: bool = True) -> None:
    if sweep:
        cmd("kill @e[type=!minecraft:player]", strict=False)
    cmd(f"tp @s {x} {y} {z} {yaw} {pitch}")


def shot(name: str) -> None:
    cmd("tick freeze", strict=False)
    run("hud", "off")
    run("shot", name, "--width", "1920", "--height", "1080")
    print(f"  shot {name}")


def scene_family() -> str:
    """Every variant beside the vanilla plate it is made from, plus the pressed state."""
    first_x, pressed_z, ours_z, theirs_z = -7, 3, 6, 9
    clear(first_x - 1, pressed_z - 2, first_x + len(VARIANTS), theirs_z + 2)
    for index, v in enumerate(VARIANTS):
        x = first_x + index
        cmd(f"setblock {x} {PLATE_Y} {pressed_z} flattsthings:{block_id(v)}[powered=true]")
        cmd(f"setblock {x} {PLATE_Y} {ours_z} flattsthings:{block_id(v)}")
        cmd(f"setblock {x} {PLATE_Y} {theirs_z} minecraft:{v.material}_pressure_plate")
    look_from(first_x + len(VARIANTS) / 2 - 0.5, PLATE_Y + 11, pressed_z - 6, 0, 58)
    shot("01-every-plate")
    return "01-every-plate"


def scene_the_point() -> str:
    """The mod in one image: two iron doors, two cows, one door stays shut.

    Nothing here is staged with a forced blockstate. Both cows are summoned onto their plate with
    NoAI so they hold still, and whichever door opens, opens because the plate saw what it saw.
    """
    ox = 40
    clear(ox - 4, -2, ox + 8, 8)

    for index, (plate, dx) in enumerate(
            [("minecraft:stone_pressure_plate", 0), ("flattsthings:stone_player_pressure_plate", 5)]):
        x = ox + dx
        # A doorway: two jambs and a lintel, so the door reads as a door.
        # The plate sits directly in front of the door. A plate weakly powers its NEIGHBOURS, so a
        # gap block between the two carries nothing and the door never moves - which is how the
        # first build of this scene produced a shut vanilla door with a genuinely pressed plate.
        cmd(f"fill {x - 1} {PLATE_Y} 4 {x - 1} {PLATE_Y + 2} 4 minecraft:polished_andesite")
        cmd(f"fill {x + 1} {PLATE_Y} 4 {x + 1} {PLATE_Y + 2} 4 minecraft:polished_andesite")
        cmd(f"fill {x - 1} {PLATE_Y + 3} 4 {x + 1} {PLATE_Y + 3} 4 minecraft:polished_andesite")
        cmd(f"setblock {x} {PLATE_Y} 4 minecraft:iron_door[facing=south,half=lower]")
        cmd(f"setblock {x} {PLATE_Y + 1} 4 minecraft:iron_door[facing=south,half=upper]")
        cmd(f"setblock {x} {PLATE_Y} 3 {plate}")
        # Summoned ABOVE the plate so it FALLS onto it, and deliberately WITHOUT NoAI.
        #
        # Three details, each of which cost a wrong image.
        #
        # It falls, because a cow placed exactly at surface level is already at rest, and a body that
        # never moves never calls Entity.checkInsideBlocks, so no plate is ever told anything.
        #
        # It keeps its AI, because NoAI looks like the obvious way to hold a mob still for a photo
        # and also stops it falling - a NoAI cow floats where it was placed, touching nothing. The
        # first attempt used NoAI at surface level and produced two shut doors.
        #
        # Its movement speed is zeroed instead, which leaves physics running while removing the
        # walking. An ordinary cow wanders about six blocks in three seconds and is long gone from
        # the plate before the shutter.
        cmd(f"summon minecraft:cow {x + 0.5} {PLATE_Y + 0.7} 3.5 "
            '{Silent:1b,PersistenceRequired:1b,'
            'attributes:[{id:"minecraft:movement_speed",base:0.0}]}')

    # Let them land and let the plates react, then freeze so the cow cannot wander back off the
    # plate between the assertion and the shutter.
    time.sleep(1.5)
    cmd("tick freeze", strict=False)
    require(f"{ox} {PLATE_Y} 3 minecraft:stone_pressure_plate[powered=true]",
            "the vanilla plate should be pressed by its cow")
    require(f"{ox} {PLATE_Y} 4 minecraft:iron_door[open=true]",
            "the vanilla door should be open")
    require(f"{ox + 5} {PLATE_Y} 3 flattsthings:stone_player_pressure_plate[powered=false]",
            "the player plate must NOT be pressed by its cow")
    require(f"{ox + 5} {PLATE_Y} 4 minecraft:iron_door[open=false]",
            "the player door must stay shut")

    look_from(ox + 2.5, PLATE_Y + 3, -4, 0, 12, sweep=False)
    shot("02-a-cow-cannot-open-it")
    return "02-a-cow-cannot-open-it"


def scene_redstone() -> str:
    """The plate is an ordinary redstone source: pressed, it lights a lamp."""
    ox = 80
    clear(ox - 4, -2, ox + 8, 8)
    for dx, powered in ((0, "false"), (4, "true")):
        x = ox + dx
        cmd(f"setblock {x} {PLATE_Y} 3 "
            f"flattsthings:stone_player_pressure_plate[powered={powered}]")
        cmd(f"setblock {x} {GROUND_Y} 4 minecraft:redstone_lamp")
        cmd(f"setblock {x} {GROUND_Y} 3 minecraft:redstone_lamp")
    look_from(ox + 2, PLATE_Y + 3, -2, 0, 28)
    shot("03-redstone")
    return "03-redstone"


def scene_terrain_slabs() -> str:
    """Every terrain slab beside the block it is half of.

    The comparison is the point. A slab of dirt has to read as dirt, and the only way to show that
    is to put the two side by side - which is also the check that catches a wrong texture id, since
    the mod draws no art for these and points at vanilla's own textures.
    """
    first_x = -(len(SLABS) // 2)
    blocks_z, bottom_z, top_z = 3, 6, 9
    # CLEARED WELL WIDER THAN THE SUBJECT. The first capture had two stray blocks from an unrelated
    # experiment sitting in the foreground corners, and patches of grass the mod's own slabs had
    # spread onto the surrounding dirt while it was being tested. Both were outside a clear sized to
    # the scene, and both are the sort of thing that only shows up once the picture is taken.
    clear(first_x - 14, blocks_z - 12, first_x + len(SLABS) + 14, top_z + 10)

    for index, v in enumerate(SLABS):
        x = first_x + index
        # Smooth stone under every column: three of these families fall, and a scene built over air
        # would photograph them mid-drop.
        for z in (blocks_z, bottom_z, top_z):
            cmd(f"setblock {x} {GROUND_Y} {z} minecraft:smooth_stone", strict=False)
        cmd(f"setblock {x} {PLATE_Y} {blocks_z} minecraft:{v.family}")
        cmd(f"setblock {x} {PLATE_Y} {bottom_z} flattsthings:{v.block_id}[type=bottom]")
        cmd(f"setblock {x} {PLATE_Y} {top_z} flattsthings:{v.block_id}[type=top]")

    require(f"{first_x} {PLATE_Y} {bottom_z} flattsthings:{SLABS[0].block_id}[type=bottom]",
            "the first terrain slab is a bottom slab")
    look_from(first_x + len(SLABS) / 2 - 0.5, PLATE_Y + 7, blocks_z - 8, 0, 36)
    shot("04-terrain-slabs")
    return "04-terrain-slabs"


def scene_woodcutter() -> str:
    """The woodcutter raised on a plinth, with a log going in and its cuts coming out.

    Laid out flat the first time, which read as a scattered row of brown blocks with the bench lost
    among them. The bench is the product here, so it sits a block higher than everything else and
    the material reads left to right: log in, planks and the shapes they cut into, out.
    """
    clear(-18, -14, 18, 16)
    for x in range(-9, 10):
        for z in range(0, 10):
            cmd(f"setblock {x} {GROUND_Y} {z} minecraft:smooth_stone", strict=False)

    # The bench, up a step so it is not one silhouette among many.
    cmd(f"setblock 0 {PLATE_Y} 6 minecraft:smooth_stone")
    cmd(f"setblock 0 {PLATE_Y + 1} 6 flattsthings:woodcutter")

    # In on the left, out on the right, each on its own plinth so the row reads as a sequence.
    # POSITIVE X IS SCREEN-LEFT here, because the camera faces +Z. Laying the input out at negative
    # x read the sequence backwards in the first capture: the products came first and the log last.
    going_in = ("oak_log", "oak_planks")
    coming_out = ("oak_stairs", "oak_slab", "oak_button", "stripped_oak_log")
    for index, block in enumerate(going_in):
        x = 4 - index
        cmd(f"setblock {x} {PLATE_Y} 6 minecraft:smooth_stone")
        cmd(f"setblock {x} {PLATE_Y + 1} 6 minecraft:{block}")
    for index, block in enumerate(coming_out):
        x = -2 - index
        cmd(f"setblock {x} {PLATE_Y} 6 minecraft:smooth_stone")
        cmd(f"setblock {x} {PLATE_Y + 1} 6 minecraft:{block}")

    require(f"0 {PLATE_Y + 1} 6 flattsthings:woodcutter", "the woodcutter is placed")
    look_from(0.5, PLATE_Y + 3.1, 1.6, 0, 13)
    shot("05-the-woodcutter")
    return "05-the-woodcutter"


SCENES = [scene_family, scene_the_point, scene_redstone, scene_terrain_slabs, scene_woodcutter]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--promote", action="store_true",
                        help="copy the captures into docs/cf_image_gallery/")
    args = parser.parse_args()

    make_safe()
    names = [scene() for scene in SCENES]

    if args.promote:
        GALLERY.mkdir(parents=True, exist_ok=True)
        for name in names:
            source = SHOTS / f"{name}.png"
            if not source.is_file():
                raise SystemExit(f"{source} was never written")
            shutil.copy2(source, GALLERY / f"{name}.png")
            print(f"  promoted {name}.png")


if __name__ == "__main__":
    main()
