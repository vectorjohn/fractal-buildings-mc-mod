# TerraBlender

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Glitchfiend's library mod for adding overworld/nether biomes "compatibly" with
the 1.18+ multi-noise biome source; used by Biomes O' Plenty and many others.
[Repo](https://github.com/Glitchfiend/TerraBlender) (branch per MC version,
read `26.3`), [wiki](https://github.com/Glitchfiend/TerraBlender/wiki),
[Modrinth](https://modrinth.com/mod/terrablender). Maintained: releases for
26.1 to 26.3 on Fabric, NeoForge, Forge (26.3 marked beta).

## Approach

It lays an integer **region map** over the world: a 1.12-style zoom-layer
stack (weighted random initial layer, one fuzzy and 3 + `overworld_region_size`
(default 3) normal zooms), so one initial cell is about 512 blocks across,
with ragged edges. Each region (vanilla is index 0; each mod registers
one or more, with a weight) owns its **own climate R-tree**, so a mod
re-maps the same six climate parameters to its own biomes inside its regions.
Biome lookup becomes: region at (x, z), then that region's tree searched with
the vanilla climate point; a `deferred_placeholder` entry falls through to
vanilla.

## Procedural vs templated

Not about buildings. Region layout is procedural (seeded layer noise); biome
choice inside a region is a hand-authored table of climate ranges.

## How it hooks into worldgen

Biome source, by mixin: wraps `MultiNoiseBiomeSource`'s resolver
(`getNoiseBiome` with a positional lookup), extends `Climate.ParameterList`
with the per-region trees, and merges per-mod surface rules
(`MaterialRuleManager`; 26.x appears to call surface rules "material
rules", not checked against sources). Terrain shape is untouched:
since 1.18 it comes from the noise router, not from biomes, so a TerraBlender
region changes biomes and surfaces but not height. Since the hook is in the biome
source, structure code that queries biomes (ours included) sees TerraBlender's
answer. Deterministic: the layers are seeded from the world seed. Newer
versions also accept data-driven regions (`terrablender:region` registry,
`RegionDefinition` JSON).

## Licence

LGPL-3.0-only ([LICENSE](https://github.com/Glitchfiend/TerraBlender/blob/26.3/LICENSE)).
Fine to study; depending on it as a library is fine; copying code into our
mod would pull in LGPL obligations, so borrow ideas only.

## What to borrow

- **A position-keyed layer above climate.** Region boundaries are independent of
  biome boundaries: the same idea as our settlement-region layer, proven at
  scale and cheap per lookup.
- The "deferred to vanilla" fallback: an overlay that changes only what it
  needs and leaves the rest of the world as the base generator would make it.
- F3 debug line showing the region at the cursor: we want the same for
  settlement cells.

## What to avoid

- **Using it to make city regions.** Region shapes are random layer blobs: we
  can't choose a centre or radius, region weight competes with every other
  TerraBlender mod, and the region index sits behind internal mixin interfaces
  (`IExtendedParameterList`), not a public query API. An "urban biome" region
  would replace the biomes beneath the city, which is the
  biome-overlay cost in ARCHITECTURE.md: it defeats cities that cross biomes
  and still doesn't flatten terrain.
- Zoom-layer fields are awkward to evaluate as a pure function of (seed, x, z)
  outside Minecraft; a hashed jittered grid suits `planner-core` better.

## Verdict

Validates "a region layer independent of biomes" but is the wrong vehicle for
cities; compatibility target, not a dependency.

**Sources / what I couldn't verify:** read `LayeredNoiseUtil`, `InitialLayer`,
`MixinParameterList`, `MixinMultiNoiseBiomeSource`, `Region`,
`RegionDefinition`, `TerraBlenderConfig` on branch `26.3` (Oct 2026). The
~512-block cell size is my arithmetic from the zoom count, not measured. Not
run in game. Didn't check how it composes with Lithostitched (Lithostitched has
reflection-based compat code for TerraBlender's trees).
