#!/usr/bin/env python3
"""Create the world `./gradlew runClient` boots into.

Why this exists
---------------
`runClient` passes `--quickPlaySingleplayer devworld`. Vanilla's quick play does NOT create a world
when that name is missing - it shows a "Could not find world with the provided identifier" SCREEN.
A screen logs nothing, so from a terminal the symptom is a client sitting at a menu forever and a
devbridge socket that never opens, with nothing to grep for.

And the world cannot be made by the client, because getting the client to make one needs a human at
the GUI, which is the entire thing this tooling exists to avoid. A dedicated server can do it
headlessly, so that is what this drives.

The world is a superflat. This mod has no custom worldgen, and a flat surface is what a comparison
screenshot of fourteen pressure plates wants anyway.

Adapted from recompile's tools/make_dev_world.py, which paid for the two markers below.

Usage
-----
    python tools/make_dev_world.py            # build it if missing
    python tools/make_dev_world.py --force    # regenerate from scratch

The name must match build.gradle's --quickPlaySingleplayer argument and **must not contain a
space**: moddev writes program arguments into build/moddev/clientRunProgramArgs.txt and quotes any
value containing one, so `New World` reaches the game as `"New World"`, quotes included, matching no
directory on disk.
"""
from __future__ import annotations

import argparse
import os
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RUN = REPO / "run"
SAVES = RUN / "saves"
PROPERTIES = RUN / "server.properties"
JDK = Path("C:/Program Files/Java/jdk-25")

DEFAULT_NAME = "devworld"
# Generated under its own name, then copied. A server REBOOTS an existing world rather than
# regenerating it, so writing straight into whatever level-name happens to point at would quietly
# hand back some other world.
GEN_NAME = "devworld_gen"

BUILD_TIMEOUT = 1800
SERVER_TIMEOUT = 300
BOOT_MARKER = "Starting minecraft server"
# RCON SPECIFICALLY, not "Done (" or "For help, type". initServer logs those BEFORE it creates the
# RCON listener, so treating them as ready races the socket and the first connect is refused.
READY_MARKER = "RCON running on"

RCON_PORT = 25575
RCON_PASSWORD = "devworld"


def write_properties() -> None:
    """A minimal server.properties for one throwaway generation run."""
    RUN.mkdir(parents=True, exist_ok=True)
    PROPERTIES.write_text(
        "\n".join([
            f"level-name={GEN_NAME}",
            "level-type=minecraft:flat",
            "online-mode=false",
            "enable-rcon=true",
            f"rcon.port={RCON_PORT}",
            f"rcon.password={RCON_PASSWORD}",
            "spawn-protection=0",
            "max-tick-time=-1",
        ]) + "\n",
        encoding="utf-8",
    )
    (RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")


def kill_tree(process: subprocess.Popen) -> None:
    """Kill gradle AND the server it forked.

    Killing only gradle leaves the server JVM alive holding the world and the RCON port, so the next
    run cannot start and there is no way to stop it short of Task Manager.
    """
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        process.kill()
    try:
        process.wait(timeout=60)
    except subprocess.TimeoutExpired:
        pass


def rcon(command: str) -> None:
    """Send one command. The server closes the connection after each, so never reuse the socket."""
    def pack(request_id: int, kind: int, body: str) -> bytes:
        payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(payload)) + payload

    with socket.create_connection(("127.0.0.1", RCON_PORT), timeout=30) as sock:
        sock.sendall(pack(1, 3, RCON_PASSWORD))
        sock.recv(4096)
        sock.sendall(pack(2, 2, command))
        sock.recv(4096)


def generate(name: str) -> None:
    write_properties()
    gradlew = REPO / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = dict(os.environ, JAVA_HOME=str(JDK))

    process = subprocess.Popen(
        [str(gradlew), "runServer", "--console=plain"],
        cwd=REPO, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )
    booted_at = None
    ready = False
    try:
        assert process.stdout is not None
        for line in process.stdout:
            if BOOT_MARKER in line and booted_at is None:
                booted_at = time.time()
            if READY_MARKER in line:
                ready = True
                break
            if booted_at is None and time.time() - _started > BUILD_TIMEOUT:
                raise SystemExit("timed out before the server started booting")
            if booted_at is not None and time.time() - booted_at > SERVER_TIMEOUT:
                raise SystemExit("server booted but never opened RCON")
        if not ready:
            raise SystemExit("server exited before RCON came up")
        rcon("stop")
        process.wait(timeout=180)
    finally:
        kill_tree(process)

    source = RUN / GEN_NAME
    if not (source / "level.dat").is_file():
        raise SystemExit(f"{source}/level.dat was never written")
    SAVES.mkdir(parents=True, exist_ok=True)
    target = SAVES / name
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(source, target)
    shutil.rmtree(source, ignore_errors=True)
    print(f"world ready: {target}")


_started = time.time()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", default=DEFAULT_NAME)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    destination = SAVES / args.name
    if destination.is_dir() and not args.force:
        print(f"world already exists: {destination} (use --force to regenerate)")
        sys.exit(0)
    generate(args.name)
