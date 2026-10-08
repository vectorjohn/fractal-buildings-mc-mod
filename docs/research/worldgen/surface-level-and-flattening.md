# Preliminary surface level as a height probe, and what flattening must wrap

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

In 26.x the overworld noise router has a cheap 2D surface estimate,
`minecraft:overworld/preliminary_surface_level`, and a smoothed copy,
`chunk_surface_level` (`interpolated`, 16×1 cells). Two questions:

- Is it a usable height probe?
- What would a city-flattening density wrapper have to wrap?

This includes Lithostitched's `wrap_noise_router`, the hook a flattening spike
would use (see [../overlays/tectonic.md](../overlays/tectonic.md) and
[../overlays/lithostitched.md](../overlays/lithostitched.md)).

## How it works (verified in 26.3 source)

- `NoiseRouterData.preliminarySurfaceLevel(offset, factor, amplified)` is a
  `find_top_surface` over a **smoothed** density: offset/factor gradient plus
  slides, with **no** `base_3d_noise`, jaggedness or caves. It searches down
  from an upper bound in **8-block** steps, to y = −64.
- Consumers:
  - **Aquifers**: `Aquifer.Config.surface_level`. `computeFluid` treats
    `preliminary + 8` as "the surface". Air below it counts as cave space that
    a local aquifer may flood.
  - **Material (surface) rules**: via `chunk_surface_level`.
    `material_rule/overworld.json` runs the whole `overworld/surface` rule
    (grass, dirt, sand…) only under `above_preliminary_surface`, which is
    `blockY >= floor(preliminary) + surfaceDepth - 8`
    (`MaterialRuleContext.getMinSurfaceLevel`).
- **Not used** by `getBaseHeight`, which scans `final_density` (see
  [terrain-biome-queries.md](terrain-biome-queries.md)).

## Probe results (standalone JUnit)

Setup: vanilla code registries, seed 12345, 3,000 random columns in ±20,000,
compared with the top solid block of `getBaseColumn`. Of these, 1,571 were
land columns above sea level. Load was 10–12 on 20 CPUs.

| | cost per column | error vs real ground (land) |
|---|---|---|
| `preliminary_surface_level` | **0.0085 ms** | biased **−14.8** (always below); bias-corrected median 2.8, p90 8.2, p99 35, max 72 |
| `getBaseColumn` / `getBaseHeight` | 0.2–0.4 ms | exact pre-carver ground |

At points, `chunk_surface_level` differs from `preliminary` by > 0.5 blocks in
969 of 3,000 columns. That is expected from its 16-block interpolation.

In game (batch 2 client test, real data pack, 6,040 land columns of 576
generated chunks), the result was similar: preliminary minus real ground had
mean −12.4, p10 −17, p90 −8, |error| p90 17 and p99 22.

## What a flattening wrapper must wrap

Wrapping `final_density` alone is **not enough** (inferred from the source;
not yet run):

1. **Cutting** a city cell more than about 3–8 blocks below the preliminary
   surface leaves **bare stone**: the surface rule is gated off.
2. Aquifers may treat the cut as cave air and **flood it** with local water.
3. So a spike must wrap three things with the same mask:
   - `final_density`,
   - `chunk_surface_level`,
   - the aquifer `surface_level` (both are `preliminary_surface_level`;
     overriding that one registry entry covers both in vanilla).

   **Raising** ground (fill) is safe for surface rules; we should prefer fill
   over cut.

## Lithostitched hook (read: `26.3` branch, commit 0083d79)

- `wrap_noise_router { dimension, target, wrapper_function, priority }`
  targets any router slot, including `chunk_surface_level` and
  `final_density`.
- `wrap_aquifers` targets `surface_level`, `barrier` and others.
- Wrappers are sorted by priority. A marker density function inside the
  wrapper is replaced by the wrapped function
  (`DensityFunctionInjectorHelper.wrap`, `rewriteChildren`).
- Applied in `ChunkMapMixin`, which `@WrapOperation`s
  `RandomState.create(...)` in `ChunkMap.<init>`. It **mutates the shared
  `NoiseGeneratorSettings`** through an accessor, so `getBaseHeight` and
  structure starts also see the wrapped terrain.

## Licence

Minecraft: proprietary, study only. Lithostitched: MIT (declared in
`fabric.mod.json`; no LICENSE file), study and borrowing allowed.

## What to borrow

- `preliminary_surface_level` (+15 bias) as a **coarse screen**: region
  suitability, slope, mountain veto. It costs 1,024 samples per 512² at
  under 10 ms. Use `getBaseHeight` only for lots.
- The Lithostitched modifier shape (target, priority, marker wrapper), as an
  optional dependency or re-implemented in our own mixin.

## What to avoid

- **The cycle.** If the city plan reads heights and the mask reads the plan,
  wrapped `getBaseHeight` calls back into the mask. The plan must sample the
  **unwrapped** density (keep the original function, as Lithostitched's
  `MergedDensityFunction.original` does) or `preliminary_surface_level`.
- Cuts deeper than about 5 blocks without wrapping the surface level.

## Hypotheses from ARCHITECTURE.md

- "Terrain height can be estimated before generation": **confirmed**. There
  are now two tiers: exact column (0.2–0.4 ms) and preliminary (0.0085 ms,
  ±8 at p90 after bias).
- The integration table doesn't cover density wrapping. **Unclear** until a
  spike runs it, together with S5.

## Probe tests worth writing after 0.5

- Flattening spike: a Lithostitched or own wrapper on `final_density` only,
  versus all three slots, on a hill cell. Screenshot the bare-stone and flooded
  cuts in the first case.

## Verdict

`preliminary_surface_level` is a good cheap screen but not a foundation probe.
City flattening must wrap the surface level as well as `final_density`, and
must avoid the plan/mask cycle.

**Sources / what I couldn't verify:** read `NoiseRouterData`
(`preliminarySurfaceLevel`, overworld router), `NoiseRouter`, `Aquifer`
(`computeFluid`, `surfaceLevel`), `MaterialRuleContext`,
`AbovePreliminarySurfaceCondition` and vanilla
`material_rule/overworld.json` (26.3), plus Lithostitched
`WrapNoiseRouterModifier`, `WrapAquifersModifier`/`AquiferTarget`,
`DensityFunctionInjectorHelper` and `ChunkMapMixin`. Flood and bare-stone
effects are inferred from the code, not run. Timings are standalone and
noisy.
