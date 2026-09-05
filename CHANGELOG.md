# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

## v0.1.0 - 2026-09-05 - "Only You"

First build. The repo, and the one block it was opened for.

### Added

- **The Player Pressure Plate.** A pressure plate only a player can press. Vanilla has two
  sensitivities and neither is this one: wood fires for any entity, so an arrow you shot at it opens
  your door, and stone fires for any living entity, so a wandering cow does. Everything else about
  the block is vanilla on purpose - the 20-tick hold, the click sounds, a redstone output of 15, and
  the same profile on the floor. Craft it from a stone pressure plate and an ender pearl.
- **The in-world test layer.** Twelve GameTests, run headless by `./gradlew runGameTestServer` and
  gated in CI as a job separate from `build`. Every negative assertion is paired with a vanilla
  control that fires on the same entity, so "a cow does not press it" cannot pass because the cow
  was never placed properly. The suite was driven red before it was trusted: swapping `Player.class`
  for `Entity.class` in the block fails exactly the two feature assertions and leaves the controls
  green.
- **The completeness sweep.** Six tests asserting that nothing reaches a registry without the files
  it needs beside it: a translated name, a blockstate, a 26.1 client item definition, a loot table,
  and a place in the creative tab. Written with one block in the mod deliberately, because the tenth
  thing is too late to start.
- **`tools/make_textures.py`.** Textures are generated, seeded and deterministic, so a texture
  change is a diff you can read rather than a binary you have to trust.
