# Terrain height and biome queries at structure-start time

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

What a structure can ask about terrain and biomes before any chunk exists, in
Minecraft 26.3, and whether the answers are deterministic and independent of
chunk order. This decides the live `TerrainProbe` / `BiomeProbe`
implementation.

## What's available in `Structure.GenerationContext`

`chunkGenerator`, `biomeSource`, `climateSampler` (created with caches by
`createStructures`), `biomeResolver`, `randomState`, `heightAccessor`, `seed`,
`chunkPos`, and a seeded `random`. All are seed-derived. None needs a
generated chunk.

## Terrain height (verified in source and by probe)

- `ChunkGenerator.getBaseHeight(x, z, Heightmap.Types, LevelHeightAccessor,
  RandomState)`. `getFirstFreeHeight` is the same call;
  `getFirstOccupiedHeight` is that minus 1. `getBaseColumn` returns the whole
  `NoiseColumn`.
- `NoiseBasedChunkGenerator.iterateNoiseColumn` builds a 1×H×1
  `DensityVolume` and a fresh `NoiseChunk`. It samples `finalDensity` over the
  **whole column**, then scans downward through
  `aquifer.computeSubstance(...)` until the heightmap predicate matches.
- **Included:** noise terrain, aquifer fluids (so `WORLD_SURFACE_WG` stops at
  the water surface; use `OCEAN_FLOOR_WG` for the floor), and any density
  wrapping by terrain mods (it uses the generator's own router).
- **Excluded:** the beardifier (`null`), blending (`Blender.empty()`),
  carvers, features, and other structures. Surface rules don't move height,
  except that whether any surface step adds blocks is unchecked; S2 will
  measure it.
- **Probe:** 0.19–0.20 ms per column on one thread for `WORLD_SURFACE_WG`.
  Over 2,000 columns, 0 differences between a sequential run and a shuffled
  8-thread run on a fresh `RandomState`. `RandomState` is built for sharing
  (`ConcurrentHashMap` noise caches, locked buffer pools).
- **In game** (batch 2 client test, seed 12345, 576 generated chunks,
  6,040 land columns, load 4–6): `getBaseHeight(OCEAN_FLOOR_WG)` equals the
  real ground (top solid non-log, non-leaf block) in **98.2%** of columns.
  p99 |error| is 2, the range is −11 (carvers) to +19 (features). It costs
  0.43–0.51 ms per column on the server thread.

## Biomes (verified in source and by probe)

- `BiomeSource` in 26.3 has **no `getNoiseBiome`**. Lookups go through
  `BiomeResolver`, from `biomeSource.createResolver(climateSampler)` (point)
  or `createResolverForChunk(sampler, qx, qy, qz, sx, sy, sz)`. The latter
  batch-samples all six climate parameters over a quart-grid volume; chunk
  biome filling uses it.
- Biomes are **3D**: `depth` depends on Y. In the probe, a 512×512 area
  sampled at y = 64 returned `lush_caves` under hills. For surface biomes,
  sample at the surface Y (height first, then biome), as vanilla
  `isValidBiome` does at the stub's own Y.
- **Cost:** about 0.0025 ms per sample for a point lookup including climate
  sampling. A 128×128 quart grid (512×512 blocks, one Y layer) took 34–44 ms
  either way. Point and volume results agreed on 16,384 of 16,384 samples.
  Caching on or off made no difference to climate values.
- **Not order-independent at ties.** `Climate.RTree.search` starts from a
  `ThreadLocal lastResult` and keeps it on an equal distance (strict `>`). In
  the probe, 7–11 of 16,384 cells changed biome when the **same thread**
  queried in a different order. All of them were exact fitness ties among the
  7,594 overworld parameter points (round values such as humidity = 3000).
  Vanilla chunk biome filling has the same property.
  - Fix (tested): before each lookup, run one fixed **non-tie priming query**,
    so every search starts from the same leaf. Result: 0 differences across
    sequential, shuffled and 8-thread orders, at 0.0017 ms per lookup.
    `ParameterList.findValueBruteForce` is also deterministic, but costs
    0.041–0.046 ms per lookup.

## Licence

Minecraft code is proprietary (Mojang EULA): study only. The probe code
(throwaway, not merged) used only public Minecraft API.

## What to borrow

- Live probes: `TerrainProbe` → `getBaseHeight(…, OCEAN_FLOOR_WG and
  WORLD_SURFACE_WG …)`. `BiomeProbe` → a primed `BiomeResolver` lookup at the
  surface Y. Sample coarsely: a 16-block grid over 512×512 is 1,024 columns,
  about 0.2 s of height queries.
- Lead, not tested: the 26.x `NoiseRouter.chunkSurfaceLevel`
  (`interpolated(surfaceLevel, 16, 1)`, used by the material/surface system)
  may be a much cheaper 2D height estimate than the full column scan.
- Compute the city plan once in the lazy piece consumer. Cache it
  per region cell (thread-safe, bounded). Recomputing after eviction must give
  the same answer, which is why priming matters.

## What to avoid

- Fine-grained height sampling at start time: 16k columns is about 3 s on the
  serial worldgen lane (see [structure-pipeline.md](structure-pipeline.md)).
- Plan decisions taken from a single biome sample, or from raw
  `getNoiseBiome` without priming.
- Treating `getBaseHeight` as the final surface. Carvers (ravines) and our own
  beardifier change it, so place foundations to tolerate ±a few blocks.

## Hypotheses from ARCHITECTURE.md

- "Biomes are a pure function of seed and position": **mostly confirmed**.
  The exceptions are ties (fixable as above), blending in old worlds, and
  biome mods that change the source.
- "Structures can sample biomes anywhere, including the whole footprint":
  **confirmed**, and cheap (about 40 ms per 512² layer).
- "Base-height queries exist; names to confirm": **confirmed**, names above,
  0.2 ms per column, deterministic.

## Probe tests worth writing after 0.5

- Done in batch 2 (one seed): the height accuracy above. Primed and
  unprimed lookups at the surface Y each differ from the **stored** chunk
  biome in 3 of 9,216 cells (0.03%), matching the tie rate. Stored biomes
  themselves break ties arbitrarily, so priming makes **our** answer
  deterministic, not identical to the chunk.
- Still open: S2 over 10 seeds, including mountains and rivers.

## Verdict

Live probes are viable: height at 0.2 ms per column and biomes at 0.0025 ms
per sample, both pure from the seed. Biome lookups need deterministic
tie-breaking (priming), and height sampling must stay coarse.

**Sources / what I couldn't verify:** read `NoiseBasedChunkGenerator`,
`RandomState`, `BiomeSource`, `BiomeResolver`, `MultiNoiseBiomeSource`,
`Climate` (`Sampler`, `ParameterList`, `RTree`), `Heightmap` and `ChunkAccess`
(26.3). The probe was a throwaway JUnit test in the mod module:
`VanillaRegistries.createWorldLookup()` with code-defined vanilla worldgen and
no data pack, seed 12345. Load was 7–9 on 20 CPUs, under the heavy lock;
timings are indicative only. I did not check that the data-pack overworld is
identical to the code bootstrap, and I did not run anything in game.
