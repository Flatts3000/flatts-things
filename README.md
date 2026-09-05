# Flatts's Things

A grab bag of blocks, tools and small features that vanilla Minecraft never shipped.

There is no tech tree here and nothing gates anything else. Each thing stands on its own, which
means each thing has to be worth adding on its own. A mod like this is only as good as its worst
entry.

**MC 26.1.2 / NeoForge / Java 25.**

## What's in it

### Player Pressure Plate

A pressure plate only a player can press.

Vanilla ships two sensitivities and neither is this one. A wooden plate fires for any entity, so an
arrow you shot at it opens your door. A stone plate fires for any living entity, so a wandering cow
does. Neither can say "a player, and nothing else", which is what a door, a shop counter or a
trapped corridor actually wants.

Everything else about it is vanilla on purpose: the same 20-tick hold, the same click sounds, the
same redstone output of 15, the same look on the floor. The sensitivity is the whole feature.

Craft it from a stone pressure plate and an ender pearl.

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
| Dev client | `./gradlew runClient` |
| Regenerate IntelliJ run configs after `clean` | `./gradlew prepareAllRuns` |
| Regenerate block textures | `python tools/make_textures.py` |

**Never pipe gradle to `tail` or `head` and trust the exit code.** The pipe reports the pager's
status (0) and masks a Gradle failure. Redirect to a file and check `$?`, or use `PIPESTATUS`.

## License

MIT.
