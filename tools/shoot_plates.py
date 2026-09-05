#!/usr/bin/env python3
"""Build the plate comparison scene in a running dev client and screenshot it.

Why this exists
---------------
A GameTest is a server-state oracle and never a UI oracle: tint, texture path, UV, render type and
model resolution are all invisible to it. Productive Frogs shipped a slime that rendered opaque grey
with every GameTest passing. This mod's fourteen textures had the same blind spot - they were
verified as a contact sheet and by the game loading them, and never once looked at on a block.

This places every player plate beside its vanilla counterpart, freezes the world so two captures of
the same scene are identical, hides the HUD, and takes one shot.

Usage
-----
    ./gradlew runClient          # in another terminal, with tools/make_dev_world.py already run
    python tools/shoot_plates.py

The screenshot lands in run/screenshots/ and the chosen one is committed under docs/img/.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from plate_variants import VARIANTS, block_id  # noqa: E402

PORT = "8610"
# The console script is not on PATH in every shell here; resolve it rather than assume.
GAMEBRIDGE = Path("C:/Users/User/AppData/Roaming/Python/Python312/Scripts/gamebridge.exe")

# The superflat surface. The player's feet sit at -60, so blocks go at -60 and the ground is -61.
GROUND_Y = -61
PLATE_Y = -60
PRESSED_Z = 3
OURS_Z = 6
THEIRS_Z = 9
FIRST_X = -7


def run(*args: str) -> str:
    result = subprocess.run(
        [str(GAMEBRIDGE), "--devbridge", PORT, "--host", "localhost", *args],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"gamebridge {' '.join(args)} failed:\n{result.stdout}{result.stderr}")
    return result.stdout


def cmd(command: str, strict: bool = True) -> str:
    """Run a game command as the player.

    Strict by default, because a quiet failure would leave a hole in the row and the screenshot
    would still be taken. Pass strict=False only where "nothing to do" is a legitimate outcome -
    Minecraft reports those as failures ("No blocks were filled", "No chunks were marked for force
    loading"), so a strict re-run would die on its own leftovers rather than on a real problem.
    """
    args = ["--player", "@s", "cmd"] + (["--strict"] if strict else []) + [command]
    return run(*args)


def make_safe() -> None:
    """Survive long enough to take the picture.

    THE FIRST RUN OF THIS SCRIPT PRODUCED A DEATH SCREEN. Every command reported success, the script
    exited 0, and the PNG was "You Died! Dev was slain by Slime" - a superflat spawns slimes in
    quantity, and nothing in the pipeline had any opinion about whether the player was alive. That is
    the same shape as the bug this whole tool exists to catch: green everywhere, wrong output.

    So: clear the death screen if it is up, remove the reason it appeared, and drop into spectator,
    which also gets the hand and the hotbar out of the frame.
    """
    if "DeathScreen" in run("screen"):
        run("click", "--text", "Respawn")
    # All three are DESIRED-STATE commands, so "already peaceful" and "already spectator" are
    # successes that Minecraft reports as failures. Lenient on purpose; the screenshot is the
    # verification that matters here, which is the entire premise of this tool.
    cmd("difficulty peaceful", strict=False)
    cmd("gamemode spectator", strict=False)
    cmd("kill @e[type=!minecraft:player]", strict=False)


def build_scene() -> None:
    x0, x1 = FIRST_X - 1, FIRST_X + len(VARIANTS)
    # FORCELOAD FIRST. A freshly booted singleplayer world only has chunks loaded around the player,
    # and `fill`/`setblock` outside them fails with "That position is not loaded" - which --strict
    # turns into an error here, but which would otherwise leave holes in the row and still take the
    # screenshot.
    cmd("forceload remove all", strict=False)
    cmd(f"forceload add {x0} {PRESSED_Z - 2} {x1} {THEIRS_Z + 2}")

    # WIPED TO AIR FIRST, so every write below is a real change and can stay strict. Re-running
    # against an already-built scene would otherwise report "No blocks were filled" and die on its
    # own previous success.
    cmd(f"fill {x0} {PLATE_Y} {PRESSED_Z - 2} {x1} {PLATE_Y + 3} {THEIRS_Z + 2} minecraft:air",
        strict=False)

    # A neutral floor under both rows, so the comparison is about the plate rather than about grass.
    cmd(f"fill {x0} {GROUND_Y} {PRESSED_Z - 1} {x1} {GROUND_Y} {THEIRS_Z + 1} "
        f"minecraft:smooth_stone", strict=False)

    for index, variant in enumerate(VARIANTS):
        x = FIRST_X + index
        cmd(f"setblock {x} {PLATE_Y} {OURS_Z} flattsthings:{block_id(variant)}")
        cmd(f"setblock {x} {PLATE_Y} {THEIRS_Z} minecraft:{variant.material}_pressure_plate")
        # The pressed model, set through the blockstate rather than by standing on it. A spectator
        # cannot press a plate - getEntityCount filters spectators, exactly as it does for vanilla -
        # so the only way to see powered=true from here is to place it that way.
        cmd(f"setblock {x} {PLATE_Y} {PRESSED_Z} "
            f"flattsthings:{block_id(variant)}[powered=true]")


def compose(pitch: float, height: float, distance: float) -> None:
    # No gamerule calls here on purpose. `doDaylightCycle` does not parse in 26.1 under that name or
    # under minecraft:do_daylight_cycle, and chasing the new spelling is not worth it: `tick freeze`
    # below stops time, weather and every other tick anyway, which is the whole reason it is used.
    cmd("time set noon")
    cmd("weather clear")
    centre_x = FIRST_X + (len(VARIANTS) - 1) / 2
    # Swept again right before the capture. Mobs and items keep arriving while the scene is being
    # built, and the first clean shot still had half a dozen entities floating over it.
    cmd("kill @e[type=!minecraft:player]", strict=False)
    cmd(f"tp @s {centre_x + 0.5} {PLATE_Y + height} {PRESSED_Z - distance} 0 {pitch}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", default="plates_comparison")
    parser.add_argument("--pitch", type=float, default=40.0)
    parser.add_argument("--height", type=float, default=6.0)
    parser.add_argument("--distance", type=float, default=7.0)
    args = parser.parse_args()

    make_safe()
    build_scene()
    compose(args.pitch, args.height, args.distance)

    # Freeze before capturing. A ticking world animates clouds and mobs, so two shots of the same
    # scene differ and a before/after comparison becomes unreadable.
    cmd("tick freeze")
    run("hud", "off")
    cmd("forceload remove all", strict=False)
    print(run("shot", args.name, "--width", "1920", "--height", "1080").strip())


if __name__ == "__main__":
    main()
