# Custom chunk generator: the `NoiseBasedChunkGenerator` checks; a procedural jigsaw pool element

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

Two follow-ups from [extension-points.md](extension-points.md):

1. What breaks when the overworld generator is not a
   `NoiseBasedChunkGenerator`? A wrapper is forced because that class is
   `final`.
2. Could a custom `StructurePoolElement` put our generated buildings inside
   vanilla villages?

Both are read from decompiled 26.3.

## 1. `instanceof NoiseBasedChunkGenerator` sites (all of them, verified by grep)

| Site | What happens with a wrapping generator |
|---|---|
| `ChunkMap.<init>` | **The level's `RandomState` is built from `NoiseRouterData.none()`** (empty router, stone, sea level 63) instead of the real noise settings. |
| `WorldDimensions.isStableNether` / `isStableEnd` | Nether and End only. The overworld check looks at the biome source, not the generator type. |
| `WorldPresets` (preset detection, `case NoiseBasedChunkGenerator -> NORMAL`) | The world isn't recognised as "normal" in the UI and preset logic. |

The first is the serious one. The level `RandomState` (via
`ServerChunkCache.randomState()`) feeds:

- `createBiomes` and `buildTerrain` (`ChunkStatusTasks`),
- the `ChunkGeneratorStructureState` (structure starts' climate sampler,
  `getBaseHeight`),
- `ServerLevel`'s `StructureCheck` (`/locate`) and its uncached biome resolver,
- `WorldGenRegion`, `JigsawPlacement.generateJigsaw` (`/place`), and the
  spawn origin.

A wrapper must:

- ignore the passed `RandomState` in `createBiomes`, `buildTerrain` and the
  height methods,
- override `createState` to inject its own,
- still mixin `ChunkMap.<init>`, or `/locate`, biome lookups and other mods
  see an empty router.

Lithostitched's own router hook (`ChunkMapMixin` wraps the noise branch of
`RandomState.create`) is also skipped, so terrain packs built on it stop
applying. Inferred: other mods that `instanceof`-check the generator (Tectonic
mixins, C2ME, Distant Horizons; not audited) would also see "not noise".

## 2. A procedural `StructurePoolElement` (verified contract, not built)

- **Registration**: a `StructurePoolElementType` with a `MapCodec`. The
  element is stored inside each `PoolElementStructurePiece` NBT
  (`pool_element` via codec, `PosX/Y/Z`, `rotation`, junctions), so content
  can be **regenerated at `place` time** from a hash of (world seed, position,
  rotation). Only the element config and transform are saved.
- **Contract**: `getSize(manager, rotation)`,
  `getShuffledJigsawBlocks(manager, pos, rotation, random)`,
  `getBoundingBox(manager, pos, rotation)`,
  `place(manager, level, …, position, referencePos, rotation, chunkBB, random,
  liquidSettings, keepJigsaws)`, `getType()`.
- **Hard constraint**: `JigsawPlacement.Placer` asks for target connectors and
  the box at **`BlockPos.ZERO`**, then translates. So a given element's
  footprint and connector layout must be **fixed per rotation**, and cannot
  depend on world position. Variety in footprint means several element
  entries, one per footprint class. Variety in content (rooms, facade,
  roof, palette) is free.
- **Village fit**: vanilla house templates expose a jigsaw named
  `minecraft:building_entrance` (seen in the `plains_small_house_1.nbt`
  strings). Streets presumably target it from the `village/<type>/houses`
  pools (inferred). Our element would provide one `building_entrance` jigsaw on
  its street face, rigid projection, ground delta 1.
- **Getting into the pool**: data-pack override of
  `template_pool/village/*/houses.json` (last pack wins, so it collides with
  other village mods), or Lithostitched `add_template_pool_elements`. Fabric
  API has no pool API.
- `place` gets the per-chunk decoration `random` (different in each chunk). As
  in [structure-pipeline.md](structure-pipeline.md), use our own hash and
  write only inside `chunkBB`.

## Licence

Minecraft: proprietary, study only.

## What to borrow

- The procedural pool element as a **cheap, early in-game showcase**: our
  generator's houses inside vanilla villages, with no placement or city work.
  A good Phase 4/5 demo, and a compat path for "villages, but varied".

## What to avoid

- The custom chunk generator. Besides the costs in
  [extension-points.md](extension-points.md), it degrades vanilla's own
  `RandomState` plumbing unless we also mixin `ChunkMap`.
- Position-dependent footprints in a pool element (the ZERO-position query
  breaks them).

## Hypotheses from ARCHITECTURE.md

- "Custom chunk generator: poor compatibility": **confirmed, and worse than
  stated**. It also breaks vanilla's `RandomState` wiring without a mixin.

## Probe tests worth writing after 0.5

- A one-element procedural pool (a 7×9 box house with a `building_entrance`
  jigsaw) injected into `village/plains/houses`. Server test: generate a
  village chunk and assert our blocks are present. Determinism: the same
  blocks whatever the chunk order.

## Verdict

Drop the custom generator; it is broken by design in 26.x without mixins. A
procedural pool element is viable for fixed-footprint houses with generated
content inside vanilla villages, and worth a small spike.

**Sources / what I couldn't verify:** grep over all 26.3 sources for
`NoiseBasedChunkGenerator`, plus `ChunkMap`, `WorldDimensions`,
`WorldPresets`, `ServerLevel`, `ServerChunkCache`, `ChunkStatusTasks`,
`StructurePoolElement`, `PoolElementStructurePiece`,
`JigsawPlacement.Placer`, vanilla `template_pool/village/plains/houses.json`
and two village NBTs (strings only), and Lithostitched `ChunkMapMixin`. I did
not audit third-party mods for `instanceof` checks, and did not build the
pool element.
