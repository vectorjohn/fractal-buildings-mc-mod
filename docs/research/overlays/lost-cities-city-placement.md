# The Lost Cities: where cities go (biomes and height)

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

How McJty's [Lost Cities](https://github.com/McJtyMods/LostCities) decides
which chunks are city, and how that interacts with biomes and terrain height.
Generation, assets and determinism are in the track 1 note,
[../mods/the-lost-cities.md](../mods/the-lost-cities.md). Read branch
`26.2_fabric` at `7e7d102` (Oct 2026); maintained for 26.2 on Fabric and
NeoForge.

## Approach

A per-chunk **city factor** in [0, 1] decides everything; a chunk is city when
it's above `cityThreshold` (0.2). Two modes: **city circles** (default
`cityChance` 0.01: each chunk may be a centre with a radius of 50–128 blocks;
the factor is the sum of `(r - d) / r` over nearby centres), or a **Perlin
rarity map** (`cityChance = -1`: 2D Perlin, scale/offset tunable). The factor is
then zeroed outside `cityMinHeight`..`cityMaxHeight` (50–150) of the chunk's
estimated surface, multiplied by a **per-biome multiplier** from the world
style (ocean ×0.1, river ×0.3 by default), and scaled by distance from spawn.

## Procedural vs templated

Placement is fully procedural. Biomes only weight it: the city field itself
ignores biome boundaries, so a city crosses biomes freely; the biome just makes
some chunks less likely to be city (edges get ragged along oceans and
rivers).

## How it hooks into worldgen

No biome or region injection. The city field is a pure function evaluated per
chunk inside the `RAW_GENERATION` feature. **Biome** is read once per chunk at
the centre, at estimated surface height, and cached; it picks the city style
(e.g. desert/badlands strongly weighted to a sandy style) and the multiplier.
Streets take the majority style of connected chunks so they don't flicker
where two cities meet. **Height** comes from `getBaseHeight` (OCEAN_FLOOR_WG)
at 4 points per chunk, or a faster re-implementation of the noise column
(`HeightGenOpt`); it is optionally averaged over neighbouring city chunks and
quantised into **city levels** (thresholds 75, 83, ... blocks), and the terrain
is then flattened to the level.

## Licence

MIT ([LICENSE](https://github.com/McJtyMods/LostCities/blob/26.2_fabric/LICENSE)).
Study and borrowing are allowed with attribution.

## What to borrow

- **City membership as a scalar field** (sum of radial falloffs, or noise)
  with a threshold: smooth edges, cities merge naturally, and the core can
  compute it with no Minecraft at all.
- **Biome as a multiplier on the field, not a boundary**: exactly how a
  city should span biomes; oceans and rivers just suppress building.
- **Biome to style by weighted selector**, sampled at the centre, plus
  "streets take the majority style" to avoid seams.
- **Height veto and quantised levels from base-height probes**: a model for
  our terrain probe and for terraced districts.

## What to avoid

- **City centres and radii ignore the world seed** in circle mode:
  `new Random(chunkZ * 797003437L + chunkX * 295075153L)` (also in
  `ApproximateCityPotential`). Same centre layout in every world, modulo height
  and biome vetoes. Always hash (seed, x, z, purpose).
- Scanning every chunk within `maxRadius` for centres on each query is O(r²)
  per chunk; a jittered grid of cells (one candidate per cell) bounds it.
- One biome sample per chunk centre: a city straddling a border flips style
  per chunk; we want blending (ARCHITECTURE.md).
- Flattening to coarse levels ignores the landscape (track 1 note).

## Verdict

The best model for placing cities across biomes: a seeded city field with biome
multipliers and height vetoes, all position-only, no biome-source changes. Port
the idea into our region layer, seeded properly.

**Sources / what I couldn't verify:** read `City`, `CityRarityMap`,
`BiomeInfo`, `ChunkHeightmap`, `BuildingInfo` (city level), `WorldStyle`,
`LostCityProfile` defaults, `worldstyles/standard.json`. Not run; the
seed-independence of centres is from reading code, not observed in game.
Didn't trace the sphere/space or floating/cavern profiles, or the hierarchical
street planner's own city-potential map.
