# Worldgen extension points in 26.3 for our three integration options

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

The registries and APIs a Fabric mod can plug into, for each option in
ARCHITECTURE.md (structure-based, biome overlay, custom chunk generator), plus
a fourth hook the source suggests. Minecraft 26.3, Fabric API 0.162.0+26.3
(`fabric-biome-api-v1` 20.1.0; I read the 20.0.9 sources, and the public
class list is identical).

## Approach: what's pluggable (verified in `BuiltInRegistries`)

| Registry (code types; mods register at init) | Vanilla entries | Use for us |
|---|---|---|
| `STRUCTURE_TYPE`, `STRUCTURE_PIECE` | 16 types, about 60 pieces | our district structure and building pieces |
| `STRUCTURE_PLACEMENT` | `random_spread`, `concentric_rings`, `dimension_origin` | region-cell placement (subclass `RandomSpread` for `/locate`) |
| `STRUCTURE_POOL_ELEMENT`, `STRUCTURE_PROCESSOR` | single, legacy, feature, list, empty | optional procedural pool element |
| `DENSITY_FUNCTION_TYPE` | dozens (some registered in loops) | city-cell terrain flattening mask |
| `BIOME_SOURCE` | `fixed`, `multi_noise`, `checkerboard`, `the_end` | overlay option |
| `CHUNK_GENERATOR` | `noise`, `flat`, `debug` | custom generator option |

Data registries (JSON, overridable by data packs): `structure`,
`structure_set`, `template_pool`, `processor_list`, `noise_settings`,
`density_function`, `multi_noise_biome_source_parameter_list`,
`world_preset`, `biome`, and the renamed `material_rule` /
`material_condition` (26.x name for surface rules; `noise_settings` has
`material_rule`).

## How each option hooks in

- **Structure-based**: `StructureType` + `Structure` + pieces (see
  [structure-pipeline.md](structure-pipeline.md)), a `structure_set` with our
  placement, and a biome tag for `biomes`. It needs no other hook, and it works
  under any `ChunkGenerator` whose `createStructures` is vanilla's.
  Caveat: `ChunkGeneratorStructureState.createForNormal` drops sets whose
  structures have no biome in `biomeSource.possibleBiomes()`. Use a broad tag
  (or `#minecraft:is_overworld`) and do our own filtering.
- **Biome overlay**: Fabric API adds biomes only to the **Nether**
  (`NetherBiomes`) and **End** (`TheEndBiomes`). `BiomeModifications` edits
  features, spawns and effects of existing biomes, and **cannot add overworld
  biomes**. The overworld's `multi_noise` uses `"preset":
  "minecraft:overworld"`, a code-built list
  (`MultiNoiseBiomeSourceParameterList.Preset`, `OverworldBiomeBuilder`).
  Injection therefore means one of:
  - replacing it with a full `biomes` list in a data pack (the Terralith
    approach),
  - a mixin or library (TerraBlender, Lithostitched, Biolith; see
    [../overlays/](../overlays/)),
  - a custom `BiomeSource` that wraps the vanilla one.

  All of these break the "cities cross biomes" goal, as ARCHITECTURE.md
  predicts.
- **Custom chunk generator**: `NoiseBasedChunkGenerator` is **`final`**, so a
  generator that wants vanilla terrain must **delegate**. That means
  implementing `codec`, `buildTerrain`, `getBaseHeight`, `getBaseColumn`,
  `getSeaLevel`, `getMinY`, `getGenDepth`, `spawnOriginalMobs` and
  `addDebugScreenInfo` by forwarding. Wrapping hides vanilla's own
  `instanceof NoiseBasedChunkGenerator` checks; which ones exist is not
  audited. It is selected through `world_preset` / dimension JSON, so it
  conflicts with any other mod that replaces the overworld generator.
- **Fourth hook: density wrapping**. `DensityFunction` is an interface:
  `compileSampler(CompileContext)` → `DensitySampler` (`sampleValue`,
  `sampleVolume`), plus `rewriteChildren`, `range`, `domainAxes` and `codec`.
  A mod type such as `cityMask(x, z)` can be registered and spliced into
  `final_density`. The [Tectonic note](../overlays/tectonic.md) argues for
  this, and Lithostitched's `wrap_noise_router` does it. A side effect we
  want: `getBaseHeight` uses the same router, so our height probe would see
  the flattened terrain. 26.x replaced the 1.21 `DensityFunction.compute`
  model with a compiler (`DensityFunctionCompiler`, `SamplerContext`, pooled
  `DensityBuffer`s), so 1.21-era density mods don't port directly.

## Licence

Minecraft code is proprietary (Mojang EULA): study only. Fabric API is
Apache-2.0 (study and reuse fine).

## What to borrow

- Structure-based as the core, plus an **optional** density mask later if S5
  shows the beard can't grade hills. It stays compatible with terrain mods
  that keep `final_density` reachable.
- Biome-tag-driven palettes. Never add an overworld biome.

## What to avoid

- A custom `ChunkGenerator` (forced delegation, world-preset conflicts) and
  biome injection (needs a third-party library, and defeats biome-crossing
  cities).
- Assuming Fabric has a structure API. It has none; use vanilla registries
  plus data JSON (Fabric datagen can emit the JSON).

## Hypotheses from ARCHITECTURE.md

- Integration table (gains and costs): **confirmed**, with sharper reasons:
  no Fabric overworld biome API, and `NoiseBasedChunkGenerator` is final.
- **Unclear**: whether flattening through a density mask composes with
  Terralith and Tectonic in practice. That's a track 5 and spike question.

## Probe tests worth writing after 0.5

- Register a no-op custom `DensityFunction` type wrapping `final_density` via
  a data pack override. Confirm per-chunk block hashes are unchanged (proves
  the hook) and measure the terrain-step cost.
- A structure set with a narrow biome tag: confirm it is dropped from
  `possibleStructureSets` under a single-biome world preset.

## Verdict

Structure-based integration is the only option with first-class hooks and
good compatibility. Keep density wrapping in reserve for terrain grading.
Drop the custom generator and the biome overlay.

**Sources / what I couldn't verify:** read `BuiltInRegistries`,
`ChunkGenerators`, `BiomeSources`, `NoiseBasedChunkGenerator`,
`ChunkGeneratorStructureState`, `DensityFunction`, `DensitySampler`,
`NoiseRouter`, vanilla `world_preset/normal.json`,
`multi_noise_biome_source_parameter_list/overworld.json` and
`noise_settings/overworld.json`, and Fabric `fabric-biome-api-v1` sources
(`BiomeModifications`, `BiomeModificationContext`, `NetherBiomes`,
`TheEndBiomes`, the mixins). I did not write a custom density function or
generator, and did not audit `instanceof NoiseBasedChunkGenerator` call sites.
