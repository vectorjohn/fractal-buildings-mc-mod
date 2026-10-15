# Distant Horizons

**Track:** postgen · **Date:** 2026-10-15 · **Author:** assistant

## What it is

A level-of-detail (LOD) rendering mod that draws terrain far past vanilla render
distance ([repo](https://gitlab.com/distant-horizons-team/distant-horizons),
core in a [submodule](https://gitlab.com/jeseibel/distant-horizons-core)).
Actively maintained; the main repo has a `versionProperties/26.3.0.properties`
and commits from 2026-10-14. Fabric, Forge, NeoForge (and old 1.7/1.12 ports).

## Approach

- It needs block data for chunks the player never visits. Two producers, set by
  `generatorPlan` (default `SURFACE_THEN_CHUNKS`): a fast **rough surface**
  generator (`DhRoughSurfaceGenerator`, no trees/structures), then a **chunk
  generator** that fills in detail.
- The chunk generator has modes (`EDhApiDistantGeneratorMode`, default
  `FEATURES`): `PRE_EXISTING_ONLY` (read saved chunks only), `FEATURES` (DH's
  own pipeline), `INTERNAL_SERVER` (ask the real server).
- **`FEATURES` mode** (`DhChunkGenerator`) re-runs vanilla's steps itself on
  `ProtoChunk`s in its own thread pool: structure starts, references, biomes,
  noise, surface, carvers, features, then its own lighting. It wraps them in
  fake objects (`DhLitWorldGenRegion`, `WorldGenStructFeatManager`,
  `DummyLightEngine`). The output is **never saved to region files**; it only
  feeds LODs. Structure starts run under a global lock
  (`STRUCTURE_PLACEMENT_LOCK`, "prevent some concurrency issues"). A comment
  records the trade-off: a border of 2 and radius 14 gave "more accurate
  structure generation" but "extreme server lag"; they ship border 0 / radius
  8, i.e. structures can be cut off in LODs. The config text and enum javadoc
  disagree on whether `FEATURES` includes structures.
- **`INTERNAL_SERVER` mode** (`DhInternalServerGenerator`) is the "real"
  path: on the server thread it adds a custom `TicketType(0, FLAG_LOADING)`
  ticket at radius 0, runs the distance manager, schedules the chunk holder to
  `ChunkStatus.FULL`, reads the result, then removes the ticket and ticks the
  chunk map so the chunk unloads and saves. Javadoc: "most compatible and will
  generate structures correctly, but may cause server/simulation lag", and
  "DOES save generated chunks to Minecraft's region files". It warns that
  without C2ME, vanilla's chunk system gives "low CPU usage and slow world gen".
- Throttles: `limitIfServerUnhealthy` (pause when ticks run long),
  `pauseIfMovingQuickly`. Threads default to 50% of cores.
- Distances: `lodChunkRenderDistanceRadius` default **256 chunks** (max 4096).
  A 512-chunk radius is ~1M chunks; nobody fully generates that with real
  chunks. Players accept LODs that fill in over minutes.

## Procedural vs templated

Not applicable (renderer; reproduces vanilla output).

## How it hooks into worldgen

Beside worldgen, not in it. Either a parallel shadow pipeline (not saved) or
plain chunk tickets on the live server (saved). Also exposes a
world-generator override API (`IDhApiWorldGenerator`, return types
`VANILLA_CHUNKS`, `API_CHUNKS`, `API_DATA_SOURCES`) so another mod can supply
LOD data.

## Licence

**LGPL-3.0** (`LICENSE.LESSER.txt` + `LICENSE.txt`; file headers say "GNU LGPL
v3"); logos CC BY-SA 4.0. We may study it; copying code would put that code
under LGPL obligations, so take ideas only.

## What to borrow

- The `INTERNAL_SERVER` pattern is the template for "make real chunks exist
  ahead of the player": custom loading-only ticket, schedule to `FULL`, release
  the ticket so the chunk saves and unloads. The same calls exist as public
  methods in 26.3 (see [pregenerators.md](pregenerators.md)).
- The throttles (pause when the server is behind, or the player is fast).
- Its LOD API: cities planned ahead could be shown in the distance before their
  chunks exist (batch 2 subject).

## What to avoid

- A shadow copy of vanilla's pipeline: they needed fake regions, global locks
  and still lose structure edges. Real chunk generation is the only
  "correct" source of truth for planning on finished terrain.
- Assuming far generation is cheap: real generation is ~40 chunks/s in vanilla
  regardless of thread count (see pregenerators note).

## Verdict

Confirms people accept generating hundreds of chunks out, but **as LODs**; its
real-chunk mode is a ticket loop with known lag. Borrow the ticket pattern and
throttles; possible later integration (show planned cities as LODs).

**Sources / what I couldn't verify:** read the main repo and core at HEAD
(2026-10-14): `DhInternalServerGenerator`, `DhChunkGenerator`,
`StepStructureStart`, `EDhApiDistantGeneratorMode`, `Config` (WorldGenerator,
MultiThreading). Did not run it. No first-party generation-speed numbers found;
the wiki pages didn't render through the fetch tool.
