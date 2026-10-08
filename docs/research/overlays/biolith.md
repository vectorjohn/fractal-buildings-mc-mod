# Biolith

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

TerraformersMC's (gniftygnome) biome-placement library, used by Terrestria,
Traverse, Nature's Spirit and others; an alternative to TerraBlender.
[Repo](https://github.com/TerraformersMC/Biolith) (branch per version; read
`26.3` at 29bfa03, 2026-09-23), [Modrinth](https://modrinth.com/mod/biolith)
(~1.5M downloads). 3.8.0-beta.1 for 26.3 on Fabric, NeoForge, Forge;
3.9.0-alpha for 26.4 snapshots. Still self-described "somewhat experimental".

## Approach

Instead of TerraBlender's separate **regions** (each with its own climate
tree), Biolith works **inside the one vanilla climate tree**, with four
request kinds (code API or datapack JSON): **add** a biome at a climate
point; **remove** one; **replace** a biome wholly or partly, at a rate; add
a **sub-biome** inside a target biome when a criterion matches. Partial
replacement uses one seeded 4-octave OpenSimplex "replacement noise" (base
scale 256 blocks × config) cut into bands by rate. Criteria include climate
**value/deviation/ratio** (e.g. "the inner 30% of this biome's
temperature range"), **neighbor** (the runner-up biome of the tree search),
**alternate**, and boolean combinators.

## Procedural vs templated

Not about buildings. Placement is procedural (climate plus noise); what to
place is authored requests.

## How it hooks into worldgen

Mixins on `MultiNoiseBiomeSource`: rebuilds the parameter list once (removals
filtered, additions appended, replacement/sub-biome targets parked at an
unreachable point so features and structures still register), then wraps
both biome resolvers to post-process the vanilla (or TerraBlender) answer.
Runs before TerraBlender's mixin and calls it when present. Also a surface
(material) rule collector and custom surface builders. Mostly a function of
seed and position, with two caveats: the order of replacements is **saved in
per-world state** (so adding a mod doesn't reshuffle old worlds), and
**BiomePerimeters** (below) caches with documented "minor discontinuities".

## Licence

LGPL-3.0-only ([LICENSE](https://github.com/TerraformersMC/Biolith/blob/26.3/LICENSE)).
Study and depending on it are fine; don't copy code (borrow ideas).

## What to borrow

- **BiomePerimeters**: an API that estimates how far a column is inside its
  biome (8-direction ray march to the edge, horizon up to 256, cached). That's
  the quantity our palette blending at biome borders needs. Its caches make
  results depend on query history, so we'd recompute it in `planner-core` from
  biome probes on a coarse grid, deterministically.
- **Climate-relative criteria** ("centre of the biome's range" vs "edge"):
  a cheap biome-free way to say "core vs fringe", usable for style choice.
- Parking injected entries at an out-of-range point so the registry knows them:
  a trick if we ever add a cosmetic "urban" biome.

## What to avoid

- Biome injection to mark cities: same objection as TerraBlender and
  Lithostitched injectors (see [terrablender.md](terrablender.md),
  [lithostitched.md](lithostitched.md)): city cells would overwrite biomes
  the city should span, and none of these choose a centre or size.
- World-save-dependent ordering: our layout must not depend on saved state.

## Verdict

A third biome-injection route (same tree, post-processing) beside TerraBlender
(regions) and Lithostitched (injectors); not a settlement vehicle, compat
target only; its BiomePerimeters idea is worth reusing for border blending.

**Sources / what I couldn't verify:** read `DimensionBiomePlacement`,
`OverworldBiomePlacement`, `MixinMultiNoiseBiomeSource`, `BiomePerimetersImpl`,
`NeighborCriterion`, the README, Modrinth metadata. Not run; didn't measure
lookup cost or check how it composes with Lithostitched injectors (no
Lithostitched code in Biolith; Tectonic's page says Biolith biome mods work
with it).
