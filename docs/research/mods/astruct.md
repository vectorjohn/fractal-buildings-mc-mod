# Astruct (Async Structure Generator)

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A small NeoForge library (SkyeShade, ~1,800 lines, 9 commits) that promises
"massive jigsaw structures far beyond vanilla limits" by planning them off
the server thread and placing them after chunks load.
[Source](https://github.com/SkyeShade/Astruct),
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/astruct). 1.21.1
(and a 1.20.1 branch); last commit Jan 2026; ~600 downloads. Effectively a
one-person experiment.

## Approach

Datapack JSON (`worldgen/async_structure/<id>.json`): `start_pool`,
`spacing` (cell size in blocks), `soft_radius_chunks`, `plan_horizon_chunks`,
`budgets.max_steps`, `gen_y` (fixed / min_plus / sea level / surface),
optional `biomes`. Each `spacing` cell gets one centre from a **hash of
(world seed, dimension, structure id, cell)** (SplitMix64-style `mix64`).
A server-tick scanner plans cells within the horizon of each **player**:
vanilla `JigsawPlacement.addPieces` runs in a `CompletableFuture`, the
resulting pieces become queued `PlacementStep`s (template id, origin,
rotation, required chunks) in saved data, and a tick handler places up to
10 per tick with `StructureTemplate.placeInWorld` once a required chunk is
loaded.

## Procedural vs templated

Plain jigsaw: hand-built NBT pools, vanilla assembler. No variation layer;
the pool processors aren't even applied at placement (only the jigsaw
replacement processor).

## How it hooks into worldgen

**Not worldgen at all: post-generation placement into the live level**,
driven by player proximity. Consequences, from the code:
- **Size:** calls `addPieces` directly, bypassing the codec's 128 cap, but
  clamps radius to **64–256 blocks** and depth to **32**. So "far beyond" is
  2× vanilla's radius; `max_open_connectors` and `fallback_pool` are parsed
  but unused (README's "thousands of pieces" is not what the code does).
- **Terrain:** one Y for the whole structure, from a top-down `getBlockState`
  scan for the first non-air block at the centre (trees and player builds
  count). No beardifier, no per-piece fitting: hills clip, valleys float.
- **Determinism:** centres and layout (vanilla seeds the jigsaw by chunk)
  are deterministic; Y and *whether* a cell is ever planned depend on live
  world state and where players went. Pieces overwrite whatever is already
  in generated chunks.
- **Thread safety:** jigsaw expansion off-thread touches the template
  manager and chunk generator; not obviously safe (not tested by me).
- **Biome:** a biome list checked at the centre only.

## Licence

MIT (`LICENSE.txt`, 2025). Free to study and reuse with attribution, though
there is little worth reusing.

## What to borrow

- Hash-derived cell centres per (seed, dimension, id, cell): the same shape
  we want for region/city seeds (see Lithostitched and Lost Cities notes in
  `../overlays/`).
- The idea of an explicit **budget** (max pieces, radius) per plan, and of
  planning ahead of the player with a horizon larger than view distance, if
  we ever plan anything too big for a single `findGenerationPoint`.
- `/locate` integration for a non-vanilla placement system.

## What to avoid

- Post-generation, player-driven placement: order- and visit-dependent,
  overwrites terrain and builds, no terrain blending, can't be pre-generated
  by tools like Chunky without players.
- Claims of scale with no change to the expansion algorithm: big
  settlements need a different planner, not just a different thread.

## Verdict

A cautionary prototype: async post-gen placement buys 2× jigsaw radius at the
cost of determinism and terrain fit; MIT; borrow only the hashed cell centres.

**Sources / what I couldn't verify:** Read the whole `1.21.1` branch source
(`Planner`, `JigsawExpand`, `CenterLocator`, `ProximityPlanner`,
`PlacerEvents`, `StructureDef`, README) and the CurseForge page. Not run;
the 1.20.1 branch not read; whether `placeInWorld` force-loads the other
chunks of a multi-chunk piece not checked.
