# Structure pipeline in 26.3

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

How a structure goes from "this chunk may hold one" to blocks in the world, read
from Loom-decompiled Minecraft 26.3 (`genSources`, unobfuscated Mojang names).
Class and method names below are from that source. Line numbers are left out
because they shift between decompiles.

## Approach (how it really works)

- **Stages** (`ChunkStatus`, `ChunkPyramid.GENERATION_PYRAMID`): empty →
  `structure_starts` → `structure_references` → `biomes` → `terrain` (noise,
  surface and carvers in one step) → `features` → `initialize_light` → `light`
  → `spawn` → `full`. `references`, `biomes`, `terrain` and `features` each
  require `structure_starts` at **radius 8**, and `features` requires
  `terrain` at radius 1 (`blockStateWriteRadius(1)`).
  `ChunkStatus.MAX_STRUCTURE_DISTANCE = 8`.
- **Placement test** (`ChunkGenerator.createStructures`): for each
  `StructureSet` in `ChunkGeneratorStructureState.possibleStructureSets()`
  (registry order), call `placement.isStructureChunk(state, cx, cz)`. If it
  passes, pick a structure by weight (`setLargeFeatureSeed(seed, cx, cz)`) and
  call `Structure.generate`. A chunk holds **one start per `Structure`**
  (`ChunkAccess.structureStarts` is a `Map<Structure, StructureStart>`), and a
  set is skipped if any of its structures already has a start there.
- **Placements**: an interface dispatched through
  `BuiltInRegistries.STRUCTURE_PLACEMENT` (`random_spread`,
  `concentric_rings`, new `dimension_origin`). `random_spread` has
  `spacing`/`separation` ≤ 4096 chunks, `linear` or `triangular` spread, a
  `salt`, and `frequency` with four reducers. Vanilla villages use 34/8.
  `exclusion_zone` (now `@Deprecated`) tests whether the other set's
  *placement* hits within ≤ 16 chunks, not whether it actually spawned.
  `concentric_rings` positions are computed once, asynchronously, with a
  biome search.
- **Start = all pieces, up front.** `Structure.generate` builds a
  `GenerationContext` with
  `random = setLargeFeatureSeed(seed, chunkX, chunkZ)` and calls
  `findGenerationPoint`. The biome is checked **at the stub position only**,
  in 3D (`isValidBiome`). The stub's piece builder then produces a
  `PiecesContainer`, saved as NBT `Children` in the start chunk. Save size
  scales with piece count times per-piece data.
- **References** (`ChunkGenerator.createReferences`): each chunk scans chunks
  ±8 around it and adds a reference when a start's bounding box intersects it.
  That box is `pieces ∪`, inflated by 12 if `terrain_adaptation` ≠ none
  (`Structure.adjustBoundingBox`).
- **Placement** (`ChunkGenerator.applyBiomeDecoration`, features step): per
  `GenerationStep.Decoration`, structures first, then that step's features.
  `StructureStart.placeInChunk` calls `postProcess(level, …, random, chunkBB,
  chunkPos, referencePos)` on each piece whose box meets `chunkBB` (the
  centre chunk, full height). `random` is seeded from (chunk decoration seed,
  the structure's **index among all registered structures in that step**,
  step), so other mods' structures shift it.

## Size limits (verified)

- **Any structure**, not only jigsaw: blocks more than 8 chunks from the start
  chunk are never referenced, so they are **silently dropped** (no error).
  **Measured in game** (batch 2 probe): a custom piece 20 chunks long got
  its blocks in chunks dx 0–8 and none in dx 9–19.
  The safe box is roughly the start chunk ±128 blocks, including the +12
  adaptation margin.
- Jigsaw validates this in its codec (see [jigsaw.md](jigsaw.md)). A custom
  `Structure` type gets **no validation**: we must enforce it ourselves.

## What a custom structure type must implement

- A `StructureType<S>` (registered `MapCodec`) and a `Structure` subclass
  with a codec using `settingsCodec`, which gives biomes, step,
  `terrain_adaptation` and spawn overrides.
- `findGenerationPoint(ctx)` returns `Optional<GenerationStub(pos,
  Consumer<StructurePiecesBuilder>)>`. The consumer runs **lazily**, only in
  `generate`. `StructureCheck.canCreateStructure` (used by `/locate` and
  explorer maps) calls `findValidGenerationPoint` from **other threads**
  without building pieces.
- A `StructurePieceType` (registered in `BuiltInRegistries.STRUCTURE_PIECE`;
  `load(context, tag)`) and a `StructurePiece` subclass. That means
  `addAdditionalSaveData`, a `(type, CompoundTag)` constructor and
  `postProcess`. Write only inside `chunkBB`: `WorldGenRegion` allows
  neighbour writes, but those depend on generation order.
- Optional: `afterPlace`, `StructureSet` JSON, and a biome tag.

## Licence

Minecraft code is proprietary (Mojang EULA): study only, never copy. Fabric
API is Apache-2.0.

## What to borrow

- One district = one start in its own chunk, with its box kept inside ±8
  chunks (we enforce this). The plan is computed in the lazy consumer, never in
  `findGenerationPoint`.
- A custom `StructurePlacement` that answers "is this chunk a district origin
  of a region cell?" (pure from seed and position). Subclass
  `RandomSpreadStructurePlacement` (not final) and override
  `getPotentialStructureChunk` to keep `/locate` working:
  `findNearestMapStructure` only handles `instanceof` `ConcentricRings` and
  `RandomSpread`.
- Derive all piece randomness from our own hash, never the `postProcess`
  random.

## What to avoid

- Overlapping pieces from two of our starts in one chunk. Their order is
  `LongOpenHashSet` iteration order (reproducible but fragile), so each block
  should be owned by exactly one start.
- Heavy work at start time. The synchronous steps (starts, references,
  features) run on one `ConsecutiveExecutor("worldgen")` lane per level
  (`ChunkMap`). Only biomes and terrain go to `Util.backgroundExecutor()`. A
  slow start stalls that level's generation. **Measured** (batch 2): a
  20 ms sleep in our lazy piece builder ran with max concurrency 1 across 11
  worker threads, and 100 chunks took 1.08 s longer with 1.22 s of total
  sleep.
- Reading the live world in `postProcess` to make decisions. Neighbour
  chunks' features may already have written into our chunk.

## Hypotheses from ARCHITECTURE.md

- Stage list: **confirmed**. Noise, surface and carvers are one `terrain`
  step.
- "Starts lay out all pieces up front": **confirmed**.
- "References via ~8-chunk search": **confirmed**, exactly 8, box-based, and
  it applies to every structure type.
- "Features step places pieces clipped to the chunk": **confirmed**
  (`chunkBB`).
- "A large city is many starts": **confirmed**. Add: one start per structure
  per chunk.

## Probe tests worth writing after 0.5

- Done (batch 2): the ±8-chunk clip and the serial lane, see above. Keep
  the clip test as a regression test.
- Per-chunk block hashes for two generation orders (S3 baseline) with a
  vanilla village. This measures vanilla's own order-dependence before we
  judge ours.

## Verdict

Structure-based integration fits: starts are pure, pieces are clipped per
chunk, placement is pluggable. Hard limits: ±8 chunks per start and one start
per structure per chunk.

**Sources / what I couldn't verify:** read `ChunkStatus`, `ChunkPyramid`,
`ChunkStatusTasks`, `ChunkGenerator`, `ChunkGeneratorStructureState`,
`Structure`, `StructureStart`, `StructurePiece`, `StructurePieceType`,
`PiecesContainer`, the `placement/*` classes, `StructureManager`,
`StructureCheck`, `ChunkAccess`, `WorldGenRegion`, `ChunkMap` and
`AbstractConsecutiveExecutor` (26.3), plus the vanilla
`structure_set/*.json`. The [World generation](https://minecraft.wiki/w/World_generation)
wiki page agrees with the source. Batch 2 ran the clip and serial-lane probes in a client game test
(seed 12345, normal world, load 4–6). Save size is still unmeasured.
