# Post-generation placement (writing structures into finished chunks)

**Track:** postgen · **Date:** 2026-10-15 · **Author:** assistant

## What it is

How mods and vanilla write buildings into chunks that are already at `FULL`:
vanilla `/place` (26.3 `PlaceCommand`), [WorldEdit](https://github.com/EngineHub/WorldEdit)
pasting, [Recurrent Complex](https://github.com/Ivorforce/RecurrentComplex)
(1.12.2, unmaintained for modern MC) with its `/rcretro` retrogen, and
MineColonies/[Structurize](https://github.com/ldtteam/Structurize) (blueprints
placed into the live world, often block by block). Vanilla's own
`BelowZeroRetrogen` (1.18 upgrade of old chunks) is the in-engine precedent.

## Approach

- **Vanilla `/place structure`** (read in 26.3): `structure.generate(...)` at
  the target chunk, then `checkLoaded` (all chunks in the bounding box must be
  loaded, else "position not loaded"), then `start.placeInChunk(level, …)`
  for **every chunk in the box** on the live `ServerLevel`. So
  `StructurePiece.postProcess` works on a live level (it takes a
  `WorldGenLevel`, which `ServerLevel` implements) and **has no 8-chunk
  limit**. It does not record the start in any chunk's `structureStarts` or
  references, so `/locate`, structure-bound mob spawns and
  `terrain_adaptation` (the beardifier runs in noise) don't apply.
- **`/place template`**: `StructureTemplate.placeInWorld(level, …, flags)`
  with flags `2` (send to clients), or `2|816` in strict mode (skip
  `onPlace`, block-entity side effects, shape updates, drops). Entities are
  added unless ignored.
- **WorldEdit** (GPL-3.0; 26.x code in `worldedit-core-mc`): calls
  `LevelChunk.setBlockState(pos, state, flags)` directly, then applies
  optional "side effects": `LIGHTING` (`lightEngine.checkBlock`), `NEIGHBORS`
  (neighbour notify + shape updates, recursion 512), `UPDATE` (`onPlace`),
  `VALIDATION` (fix states from neighbours, off by default), `ENTITY_AI`
  (pathfinding notify), `EVENTS`, plus internal `POI_UPDATE` and `NETWORK`
  (mark the block changed for clients). Turning them off is the fast path.
- **What `LevelChunk.setBlockState` already does** (26.3): updates the four
  heightmaps (`MOTION_BLOCKING`, `…_NO_LEAVES`, `OCEAN_FLOOR`,
  `WORLD_SURFACE`), sky-light sources, queues a light check when light
  properties differ, swaps block entities, and marks the chunk unsaved. So
  heightmaps, lighting and saving come for free; neighbour updates, client
  sync and POIs depend on the flags or the `Level.setBlock` wrapper.
- **Recurrent Complex** (MIT): a world `SavedData` maps chunk → planned
  structures; each chunk's population first "complements" (writes the part of
  every already-planned structure that falls in it), then plans new ones.
  `/rcretro` replays this over every chunk in the region files.
  Synchronised on the whole data object; results depend on chunk order.

## Procedural vs templated

Not applicable (placement mechanics).

## How it hooks into worldgen

After it. Requirements: the chunks must be loaded (a loading ticket, see
[pregenerators.md](pregenerators.md)); writes happen on the server thread.

## Licence

Vanilla: decompiled for study only. WorldEdit: GPL-3.0; Structurize and
MineColonies: GPL-3.0 (study, don't copy). Recurrent Complex: MIT (may reuse
with attribution).

## What to borrow

- `/place structure`'s shape: build pieces once, then `placeInChunk` per
  chunk on the live level, so our existing `StructurePiece`s could be reused
  for post-gen with no size limit.
- WorldEdit's flag discipline: skip `onPlace`/shape updates while writing a
  whole building, then light and send once.
- Writing while the chunk is loaded by a ticket but **not in any player's view**
  means no visible pop-in and no per-block packets.

## What to avoid

- Writing into chunks players can see: each change is a block-update packet,
  and a city is millions of blocks (pop-in, lag).
- RC's order-dependent plan-then-complement (breaks our determinism rule).
- Neighbour updates during bulk writes (water flows into half-built rooms,
  gravel falls, redstone fires).

## Verdict

Writing into finished chunks is well-trodden and the 26.3 API handles
heightmaps, light and saving; the open problems are load cost and doing it
out of the player's sight, not correctness.

**Sources / what I couldn't verify:** read decompiled 26.3 `PlaceCommand`,
`LevelChunk.setBlockState`, `ServerChunkCache`; WorldEdit `WorldNativeAccess`,
`CoreMcWorldNativeAccess`, `SideEffect`; RC `WorldGenStructures`,
`CommandRetrogen`. **To confirm in 26.3 (researcher):** whether
`ChunkAccess.setStartForStructure`/`addReferenceForStructure` on a `LevelChunk`
persist and make `/locate` and structure spawns work post hoc; what
`Block.UPDATE_*` flag set is safest for bulk writes; whether light is correct
across chunk edges when a neighbour isn't loaded; entity placement into
unticked chunks. Millénaire (villages placed into existing terrain) was not
checked: source licence unknown. Structurize was not read.
