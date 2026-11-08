# Eternal Return on Horizon: first slice

A feasibility slice of the Eternal Return Fabric mod (1.21.1) for [Horizon](https://github.com/horizon-rust/horizon)
(a Rust remake of Minecraft Java 26.3, version 0.2). The findings are in the Fabric repo's `docs/horizon-port.md`.

| Folder | What it is | What it does in the game |
|---|---|---|
| `content-mod/` | a content mod (`.hmod`, data only) | the 64 wool and concrete stairs and slabs (crafting and stonecutter recipes, drops, an "Eternal Return" creative tab) and boulders of stone, granite, diorite and andesite scattered over the land of every overworld biome |
| `native-mod/` | a native mod (`.hnmod`, Rust compiled into the game) | leaves hold on up to 9 blocks from a log before they decay, as in the Fabric mod (vanilla: 6) |
| `tools/build_content.py` | generator | writes `content-mod/assets` and `content-mod/data` from the Fabric mod's own generated resources |

## Build and run

1. `python tools/build_content.py [path to the Fabric repo]` (default: the sibling `../enchanting-overhaul`).
2. Content mod: with `cargo-horizon-mod`, `cargo horizon-mod run` in `content-mod/`; or copy the `content-mod` folder
   into the game folder's `horizon-mods/` and turn it on in Options > Mods (restart: it adds blocks).
3. Native mod: copy `native-mod/` into the game folder's `horizon-native-mods/`; the Horizon launcher builds a game
   with it (its own Rust toolchain, a few minutes the first time). It pins Nightly 2026-11-07.

## Limits of this slice

- The boulders use vanilla's `forest_rock` feature (the giant taiga's lumps), not the Fabric mod's boulder templates
  with each biome's stone: Horizon has no hook for a mod's own feature types yet.
- The leaf rule checks again on each random tick of a leaf at vanilla's distance 7 (vanilla's distance property
  stops at 7), so very wide crowns cost a little more than in the Fabric mod.
- Wool stairs and slabs copy oak stairs' and slabs' behaviour (sounds and hardness are wool's); concrete copies stone's.
