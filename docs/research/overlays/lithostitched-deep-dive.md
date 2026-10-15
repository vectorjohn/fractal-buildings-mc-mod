---
tags: research
track: overlays
date: "2026-10-15"
author: researcher
licence: MIT
---

# Lithostitched, deep-dive: region field, spawn conditions, router wrapping

## What it is

A second page for [lithostitched.md](lithostitched.md). Read: branch `26.3` at
9a12e65 (2026-10-14, v2.0.6, after the first note's 0083d79), the
[wiki](https://github.com/Apollounknowndev/lithostitched/wiki) (2026-09-02,
lags the code), and decompiled 26.3 (`:mod:genSources`). Tags: **R** read in
source, **P** probe run by me, **I** inferred.

## Approach

**Region field (R).** `lithostitched:region/overworld` is an ordinary density
function: `0.5·(1 + fast_noise)`, where the noise is FastNoiseLite (MIT,
bundled as `FNL`) cellular, `cell_value`, Euclidean, jitter 1, frequency
0.002, salt 304658814. Each Voronoi cell gets one hashed value in [0, 1].
The domain warp is `shift_x/z = cache(30·noise(region/offset, 2 octaves,
~128-block wavelength) + vanilla shift_a/b)`, so borders wobble by roughly
±30 blocks (amplitude *I*). Regions (`lithostitched/region/*.json`: dimension,
biome set, weight) split [0, 1] by weight, **per biome**:
`RegionManager` builds, for each biome, a `TreeMap` of the regions that list
it, sorted by id, and does `floorEntry(clamp(v)·total + 1)`. A region is
therefore "cell value × the biome's region list", not a set of cells. Only
biome injectors (`region` field in `ParameterMap`) and the F3 line read it.

- **Deterministic, chunk-order independent (R):** it's a pure function of
  (seed, x, z), sampled through a per-resolver `DensitySamplerSet` whose caches
  don't change values.
- **Two seeding flaws:** (1) `FastNoiseConfig.bind` does `SetSeed((int) seed +
  salt)`, so seeds that differ only in the high 32 bits give the same layout
  (**P**: 1000/1000 identical samples for `s` and `s + 2^32`). (2) The seed is
  bound into a shared registry object at server start (R).
- **Slivers (P):** `fast_noise` always calls the 3D `GetNoise(x, 0, z)`. A
  planar slice of 3D Voronoi gives small cells where a y = ±1 site wins. Over
  40k × 40k blocks, 17.4% of cells were under ¼ of the mean area (p10
  equivalent diameter 121 blocks, median 554). True 2D cellular: 2.1%, p10 448.
- **Cost (P):** 150 ns per query for 3D, 65 ns for 2D (random access, load
  ~2.5), plus two 2-octave noises for the warp (not measured). Negligible.
- **A second Voronoi type**, `lithostitched:cellular` (R): a jittered grid
  (`grid_dimensions`), with each cell seeded by
  `setLargeFeatureWithSalt(WORLD_SEED, gx, gz, salt)` (full 64-bit seed, but
  a static global). It returns cell value or F1/F2 distances, plus a
  `condition`: a density function sampled **at the cell's site**, with a
  fallback value. Jitter 1 with a 3×3 search isn't exact Voronoi (*I*), and the
  condition is skipped for `cell_value`.

## Procedural vs templated

Library: procedural fields, authored JSON on top.

## How it hooks into worldgen

**Spawn conditions (R).** `set_structure_spawn_condition` replaces the
structure's `Holder.Reference` value with a `DelegatingStructure` (settings
copied) and adds it to `byValue`, but not to `toId`.
`findValidGenerationPoint` runs the delegate, then tests a
`PlacementCondition` at the **stub position only**. Generation and
`StructureCheck` (`/locate`) both call it, so they agree. Conditions:
`sample_density` (any DF, through `RandomState.samplersWithContext`; default
min is now `-Float.MAX_VALUE`, which retires the first note's `MIN_VALUE`
gotcha), `sample_noise_router`, `sample_aquifers`, `in_biome`,
`height_filter` (calls `getFirstFreeHeight`, a full column scan), and `grid`
(N×N of any condition; a 5×5 height grid is ~25 scans, ~12 ms on the serial
lane, *I* from the 0.5 ms in-game figure), plus `all_of`, `any_of`, `not`,
`offset`, `multiple_of`. The same conditions gate features (`condition`
placement modifier).

**Router wrapping (R), which settles the ARCHITECTURE contradiction.**
`ChunkMapMixin` `@WrapOperation`s `RandomState.create` in `ChunkMap.<init>`
and **replaces the router and `Aquifer.Config` inside the shared
`NoiseGeneratorSettings`** before the `RandomState` is built. The wrappers:
`wrap_noise_router` (8 slots: temperature … `chunk_surface_level`,
`final_density`) and `wrap_aquifers` (6 slots, including `surface_level`),
sorted by priority. `wrapped_marker` stands for the chain so far,
`original_marker` for the pre-wrap function. `MergedFunction` keeps
`original` **only** for marker substitution and for the codec. The game
samples `full`, so `getBaseHeight`, aquifers and structures all see wrapped
terrain. ARCHITECTURE's "keep the original function (as Lithostitched does)"
is wrong.

In vanilla 26.3 (R, `NoiseRouterData.overworld/overworldAquifers`),
`preliminary_surface_level` is **not a router slot**. It is a registry entry
referenced by the `chunk_surface_level` entry (`interpolated(…, 16, 1)`), by
`Aquifer.Config.surface_level`, and by a debug line. Nothing else reads it.

- **Resolution:** wrap the three consumer slots (router `final_density`,
  router `chunk_surface_level`, aquifer `surface_level`), never the shared
  `preliminary_surface_level` entry. That entry then stays unwrapped and
  remains a valid coarse screen. Wrapping it would wrap the screen too, which
  is the cycle.
- **Exact unwrapped heights:** capture the original `final_density` before
  wrapping and scan it ourselves. 26.3's `RandomState.getSampler(df)`
  compiles **any** DF on demand (lock, `ConcurrentHashMap` cache, R). Not run.
- **Version caveats:** `wrap_aquifers` was only registered on 2026-10-11
  (released in 2.0.6, 2026-10-13). Before 2026-10-10, markers nested below the top level weren't
  rewritten (the rule didn't recurse), so 2.0.5 is unusable for this.
- **Cheaper masks:** a mask DF declaring `domainAxes` X|Z is evaluated once
  per column by the 26.3 compiler's `SLICE_UNIFORM_AXES` pass (R; cost not
  measured).

**Registration and footprint (R).** One Stonecutter codebase. Fabric: a
`ModInitializer` plus Apollib (Apollo's library, jar-in-jar) for registries
and config. NeoForge: `[26.3, 26.4)`. The mixin config is `required` with
`defaultRequire 1`: about 75 common, 4 client and 1 server mixins, plus a
class tweaker. Modifiers and injectors are applied by mixins on
`initServer` before `loadLevel` (Dedicated, Integrated, GameTest), not by
Fabric events.

Notably, `JigsawStructureMixin` `@Redirect`s **every** jigsaw structure to
its own `AlternateJigsawGenerator` (box octree). With the config default
`breaks_seed_parity: true`, pool-fallback handling differs from vanilla. So
installing it can change vanilla villages, and it would also run our
`StructurePoolElement` spike.

**Composition (R).** The biome source is wrapped unless its type is in
`#cannot_inject_into` (fixed, checkerboard). Blueprint is unwrapped and
TerraBlender's trees are patched by reflection (exceptions swallowed).
Data-pack router overrides (Tectonic, Terralith) load first, so wrappers sit
on top of them. Ties in priority fall back to `HashMap` order. *I*: two
dimensions sharing one settings object would share, or double, the wrappers.
`instanceof JigsawStructure` checks fail on delegated structures.

## Licence

MIT: `fabric.mod.json`, `neoforge.mods.toml` and Modrinth; still no LICENSE
file at 9a12e65. The bundled FastNoiseLite is MIT (Jordan Peck). We may study
and borrow, with attribution.

## What to borrow

Planner-core sketch (pure, no Minecraft):

```java
record CellId(int gx, int gz) {}
record CellSample(CellId cell, double d1, double d2) { double edge() { return (d2 - d1) / 2; } }
final class SettlementField {          // seed, cell size S≈512, jitter≤0.5, warp A≈32
  CellSample sample(int x, int z);     // warp (own 2D value noise), then 2D 3×3 nearest site
  Site site(CellId c);                 // splitmix(seed, gx, gz, "site"): centre, value, radius, culture
  boolean settled(CellId c, Probes p); // evaluated ONCE at the site: chance × biome multiplier, height veto
}
```

- **The field as a density function, reused everywhere:** placement,
  exclusion veto (`edge() > margin`), flatten mask (fade on `edge()`) and
  palettes all read one pure field.
- **Cell decisions evaluated at the site** (their `CellCondition`), so every
  chunk agrees without a cache.
- **F2 − F1 edge distance** for soft borders. Warp only the lookup, never the
  sites.
- **Publish, don't consume:** register `fractalbuildings:settlement` as a 2D
  DF type. Packs with Lithostitched can then gate structures or inject biomes
  on our cities, with no dependency for us.
- **Wrapper shape:** priority-ordered wrapping of slots, never overriding
  router files.

## What to avoid

- 3D cellular sliced at y = 0 (slivers); `(int)` seed truncation;
  `WORLD_SEED`-style statics and seeds bound into shared registry objects.
- Mutating shared settings or registry holders, and `HashMap` tie order.
- Biome-keyed region weights: a city cell must not change identity across a
  biome border.
- Stub-only conditions as our only veto: villages are larger than their
  start, so the margin must be in the field.

## Verdict

Borrow the field design (2D, hashed, 64-bit seed, decisions at the site,
F2 − F1 edges) and the slot-wrapping model. Don't depend on Lithostitched:
our needs are about 3 small mixins, while it brings about 80 required
mixins, a global jigsaw replacement and week-old fixes in exactly the hooks
we'd use. Compat means exposing our field as a DF.

**Against my own view:** players of Tectonic and Terralith (mod) already have
it (24.7M downloads). Its modifiers give pack makers tuning for free, and
S7 under Tectonic will be ordered against its wrappers anyway. I'd change my
mind if S7 needs more hooks than the three slots (blending, beardifier), or
if exposing our DF turns out insufficient for pack makers.

**Sources / what I couldn't verify:** files named above, plus `ChunkMap`,
`RandomState`, `DensityFunctionCompiler`, `DfRewriteRule`,
`NoiseBasedChunkGenerator.iterateNoiseColumn`, `Beardifier` and
`MappedRegistry` (26.3). Probes: throwaway FNL copy in the scratchpad, not
committed. Nothing ran in game. Warp amplitude, the 3×3 inexactness and the
cost of the 2D slice are not measured. I didn't check Tectonic's 26.3
modifier list.
