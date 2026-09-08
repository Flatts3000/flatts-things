#!/usr/bin/env python3
"""Build the terrain slab scene in a running dev client and screenshot it.

Why this exists
---------------
A GameTest is a server-state oracle and never a UI oracle. Every one of the 128 tests can pass while
a slab renders as the missing-model cube, shows the wrong face on top, or draws its top half at the
wrong height - none of which is server state. This mod has had that blind spot before, and the whole
devbridge pipeline exists because of it.

The terrain slabs are a sharper case than the plates were, because **no texture was drawn for them**.
Every model points at a vanilla texture id, so the failure mode is not "the art is ugly", it is "the
id was wrong and the game silently substituted the missing texture". Nothing but looking finds that.

What each scene is for
----------------------
`families` puts all nine side by side in four rows - the vanilla block, then a bottom, top and
double slab of it. Four things are checkable at a glance: that no cube is the missing-model texture,
that podzol and rooted dirt show the right face on top rather than dirt, that a double slab is
indistinguishable from the vanilla block above it, and that top and bottom slabs sit at the heights
their names claim.

`podzol_snow` is the one blockstate with a variant that a static grid cannot show: a snow layer on
a TOP slab should swap the side texture, and the same layer over a BOTTOM slab should not, because
the snow is not touching it. Both are placed in one frame so the difference is the picture.

Usage
-----
    ./gradlew runClient -PlockInput      # in another terminal
    python tools/shoot_terrain_slabs.py
    python tools/shoot_terrain_slabs.py --scene podzol_snow
"""
from __future__ import annotations

import argparse
import subprocess
import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from terrain_slab_variants import VARIANTS  # noqa: E402

PORT = "8610"
# The console script is not on PATH in every shell here; resolve it rather than assume.
GAMEBRIDGE = Path("C:/Users/User/AppData/Roaming/Python/Python312/Scripts/gamebridge.exe")

# The superflat surface. The player's feet sit at -60, so blocks go at -60 and the ground is -61.
GROUND_Y = -61
BASE_Y = -60
FIRST_X = -8

# One row per form, front to back, so the eye reads block then its three slab states.
ROW_VANILLA = 3
ROW_BOTTOM = 5
ROW_TOP = 7
ROW_DOUBLE = 9


def run(*args: str, check: bool = True) -> str | None:
    result = subprocess.run(
        [str(GAMEBRIDGE), "--devbridge", PORT, "--host", "localhost", *args],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        if not check:
            return None
        raise SystemExit(f"gamebridge {' '.join(args)} failed:\n{result.stdout}{result.stderr}")
    return result.stdout


def cmd(command: str, strict: bool = True) -> str:
    """Run a game command as the player.

    Strict by default. Pass strict=False only where "nothing to do" is legitimate: Minecraft reports
    an already-satisfied desired state as a failure, so a strict re-run dies on its own leftovers.
    """
    args = ["--player", "@s", "cmd"] + (["--strict"] if strict else []) + [command]
    return run(*args)


# Commands a scene wants run AFTER compose(). Falling blocks need this: compose sweeps entities to
# clear mobs out of the frame, and a FallingBlockEntity is an entity - so a scene that drops
# something before the sweep gets a picture of an empty platform and no error anywhere.
DEFERRED: list[str] = []


def make_safe() -> None:
    """Survive long enough to take the picture. See shoot_plates for the death-screen story."""
    if "DeathScreen" in run("screen"):
        run("click", "--text", "Respawn")
    # UNFREEZE FIRST, and this cost a screenshot. `tick freeze` persists in the world, so a scene
    # left frozen by an earlier run means `weather clear` and `time set noon` are applied to a world
    # that never ticks - the rain level lerps toward zero only while ticking, so the first shot came
    # out in heavy rain with both commands reporting success.
    cmd("tick unfreeze", strict=False)
    cmd("difficulty peaceful", strict=False)
    cmd("gamemode spectator", strict=False)
    cmd("kill @e[type=!minecraft:player]", strict=False)


def prepare(x0: int, x1: int, z0: int, z1: int) -> None:
    # FORCELOAD FIRST. A freshly booted world has chunks only around the player, and setblock
    # outside them fails with "That position is not loaded" - which strict turns into an error, and
    # which would otherwise leave holes in the row and still take the screenshot.
    cmd("forceload remove all", strict=False)
    cmd(f"forceload add {x0 - 6} {z0 - 4} {x1 + 8} {z1 + 4}")
    # Wiped to air first so every write below is a real change and can stay strict.
    # WIDER THAN THE SCENE, because the plate shoot builds at overlapping coordinates and its
    # leftovers turned up in the first frame of this one. Clearing only what this script writes is
    # not enough when another script shares the neighbourhood.
    # FROM THE GROUND UP, not from BASE_Y up. The first corrected frame still had a stray platform
    # in it because the plate scene's smooth-stone FLOOR sits a layer below the blocks this was
    # wiping, so clearing "the scene" left the thing the scene stood on.
    cmd(f"fill {x0 - 6} {GROUND_Y} {z0 - 4} {x1 + 8} {BASE_Y + 4} {z1 + 4} minecraft:air",
        strict=False)
    cmd(f"fill {x0} {GROUND_Y} {z0} {x1} {GROUND_Y} {z1} minecraft:smooth_stone", strict=False)


def scene_families() -> tuple[float, int]:
    x0, x1 = FIRST_X - 1, FIRST_X + len(VARIANTS)
    prepare(x0, x1, ROW_VANILLA - 2, ROW_DOUBLE + 2)

    for index, v in enumerate(VARIANTS):
        x = FIRST_X + index
        slab = f"flattsthings:{v.block_id}"
        # GRAVEL AND SAND WOULD FALL. Three of the nine are FallingTerrainSlabBlock, and a scene
        # built on air would have them on the floor by the time the shot is taken - which is the
        # feature working correctly and ruins the picture. Each sits on smooth stone for that
        # reason, which also puts every row on the same neutral ground.
        for row in (ROW_VANILLA, ROW_BOTTOM, ROW_TOP, ROW_DOUBLE):
            cmd(f"setblock {x} {GROUND_Y} {row} minecraft:smooth_stone", strict=False)

        cmd(f"setblock {x} {BASE_Y} {ROW_VANILLA} minecraft:{v.family}")
        cmd(f"setblock {x} {BASE_Y} {ROW_BOTTOM} {slab}[type=bottom]")
        cmd(f"setblock {x} {BASE_Y} {ROW_TOP} {slab}[type=top]")
        cmd(f"setblock {x} {BASE_Y} {ROW_DOUBLE} {slab}[type=double]")

    return FIRST_X + (len(VARIANTS) - 1) / 2, ROW_VANILLA


def scene_podzol_snow() -> tuple[float, int]:
    """The snowy blockstate, and the case the grid cannot show.

    A snow layer on a TOP slab swaps the side texture, because the snow rests directly on it. The
    same layer above a BOTTOM slab must NOT, because the slab's upper face is half way up its own
    position and the snow is eight pixels clear of it. Both in one frame, so the difference is the
    picture rather than a claim about it.
    """
    x0, x1 = -3, 5
    prepare(x0, x1, ROW_VANILLA - 2, ROW_VANILLA + 4)
    slab = "flattsthings:podzol_slab"

    for x, kind in ((-1, "top"), (2, "bottom")):
        cmd(f"setblock {x} {GROUND_Y} {ROW_VANILLA} minecraft:smooth_stone", strict=False)
        cmd(f"setblock {x} {BASE_Y} {ROW_VANILLA} {slab}[type={kind}]")
        cmd(f"setblock {x} {BASE_Y + 1} {ROW_VANILLA} minecraft:snow")

    # The vanilla block under the same snow, as the control. If OUR top slab does not match this
    # one's side, the blockstate is wrong; if our bottom slab DOES match it, the type check is not
    # working and the naive copy of SnowyBlock shipped after all.
    cmd(f"setblock 4 {GROUND_Y} {ROW_VANILLA} minecraft:smooth_stone", strict=False)
    cmd(f"setblock 4 {BASE_Y} {ROW_VANILLA} minecraft:podzol")
    cmd(f"setblock 4 {BASE_Y + 1} {ROW_VANILLA} minecraft:snow")
    return 1.5, ROW_VANILLA


def scene_falling() -> tuple[float, int]:
    """The three falling families, dropped, so the fall can be LOOKED at rather than asserted.

    `a_gravel_slab_falls_when_nothing_holds_it_up` proves the server moves the block. It cannot say
    anything about what the falling entity looks like on the way down, and `FallingBlockEntity`
    renders whatever BlockState it is carrying - so the question "does a falling slab look like a
    falling slab, or like a full cube" is exactly the kind a GameTest is blind to.

    Combine with --fall-delay to choose the moment: a short delay catches them in the air, a long
    one shows where they came to rest.
    """
    x0, x1 = -6, 6
    prepare(x0, x1, ROW_VANILLA - 3, ROW_VANILLA + 5)
    falling = [v for v in VARIANTS if v.family in ("gravel", "sand", "red_sand")]

    for index, v in enumerate(falling):
        x = -3 + index * 3
        # DEFERRED past compose, because compose kills entities and a falling block IS one. The
        # first attempt dropped them here and photographed an empty platform.
        #
        # Starts as a TOP slab on purpose: if the normalisation works it lands as a BOTTOM one, so
        # the landed shot shows whether that held.
        DEFERRED.append(
            f"setblock {x} {BASE_Y + 5} {ROW_VANILLA} flattsthings:{v.block_id}[type=top]")
        # A reference of the same family that is NOT falling, one block over, so the shape of the
        # thing in the air can be compared with the shape it is supposed to be.
        cmd(f"setblock {x + 1} {GROUND_Y} {ROW_VANILLA} minecraft:smooth_stone", strict=False)
        cmd(f"setblock {x + 1} {BASE_Y} {ROW_VANILLA} flattsthings:{v.block_id}[type=bottom]")

    return 0.0, ROW_VANILLA


SCENES = {"families": scene_families, "podzol_snow": scene_podzol_snow,
          "falling": scene_falling}


def compose(centre_x: float, front_z: int, pitch: float, height: float, distance: float) -> None:
    # No gamerule calls: doDaylightCycle does not parse in 26.1 under that name, and `tick freeze`
    # below stops time, weather and every other tick anyway.
    cmd("time set noon")
    cmd("weather clear")
    # Let the world tick long enough for the rain to lerp out before freezing it. Clearing the
    # weather is instant in server state and gradual on screen.
    time.sleep(3)
    # Swept again right before the capture. Mobs keep arriving while the scene is built.
    cmd("kill @e[type=!minecraft:player]", strict=False)
    cmd(f"tp @s {centre_x + 0.5} {BASE_Y + height} {front_z - distance} 0 {pitch}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scene", choices=sorted(SCENES), default="families")
    parser.add_argument("--name")
    parser.add_argument("--pitch", type=float, default=30.0)
    parser.add_argument("--height", type=float, default=5.0)
    parser.add_argument("--distance", type=float, default=9.0)
    parser.add_argument("--fall-delay", type=float, default=0.0,
                        help="seconds to let the world tick before freezing, for the falling scene")
    args = parser.parse_args()

    make_safe()
    centre_x, front_z = SCENES[args.scene]()
    compose(centre_x, front_z, args.pitch, args.height, args.distance)
    for command in DEFERRED:
        cmd(command)
    if args.fall_delay:
        time.sleep(args.fall_delay)

    # Freeze before capturing: a ticking world animates clouds, so two shots of one scene differ.
    cmd("tick freeze")
    run("hud", "off")
    cmd("forceload remove all", strict=False)
    print(run("shot", args.name or f"terrain_slabs_{args.scene}",
              "--width", "1920", "--height", "1080").strip())


if __name__ == "__main__":
    main()
