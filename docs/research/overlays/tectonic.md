# Tectonic

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Apollo's terrain overhaul (continents, huge mountain ranges, deep oceans,
underground rivers), as a datapack and a Fabric/NeoForge mod that needs
Lithostitched. [Repo](https://github.com/Apollounknowndev/tectonic),
[Modrinth](https://modrinth.com/mod/tectonic) (~17M downloads). Releases for
26.3 exist (3.0.31, 2026-09-27); the public source lags: newest branch
`new-islands` (2026-09-20, v3.0.27) builds 1.21.1, 26.1, 26.2 only. I read that.

## Approach

All terrain shaping is **data**: the pack overrides
`minecraft:noise_settings/overworld` and every
`density_function/overworld/noise_router/*` file. Height is the vanilla 1.18+
model rebuilt: a 2D **offset** spline (`terrain_spline/offset/final`, inside
`flat_cache`/`cache_2d`) plus a Y gradient gives `depth`; `factor` and
jaggedness scale 3D noise; `final_density` = min(base terrain, caves) +
noodles + underground rivers + lava tunnels. Local character comes from
**regions**: a selector noise picks one of four region splines (club, diamond,
heart, spade), and spade uses flat spline segments (derivative 0) to make
**mesa-like tiers** (`spade/lower_tier`, `upper_tier`). Java code is small
(43 files): config-backed density function types (`config_noise`,
`config_clamp`, `config_constant`), a height-limits modifier, and fix-up mixins.

## Procedural vs templated

Fully procedural noise; the authored part is spline tables and config numbers.

## How it hooks into worldgen

Registry overrides of vanilla worldgen JSON (last pack wins), plus three
Lithostitched modifiers (height limits, ice, islands). It does **not** use
`wrap_noise_router`. Compat is handled by shipping variants: an overlay for
Terralith ("Terratonic"; not opened, Stardust-derived), one for CliffTree.
Biome mods that go through TerraBlender or Biolith work, since biomes still
come from the climate parameters Tectonic computes. Mixins touch structure
pieces (lowers ocean monuments), blending, noise seeding, the lava level.
Deterministic: pure density functions.

## Licence

`fabric.mod.json` and Modrinth say **MIT**; the repo's `LICENSE` file is
**CC0-1.0**. Both allow study and reuse; cite as MIT with attribution.

## What to borrow

- **The answer to "could the noise router flatten city cells": yes, and the
  cheap place is `final_density`, not `depth`.** Tectonic's `final_density`
  references its own `tectonic:depth`, not the router's `depth` slot, so
  wrapping the router's `depth` would not flatten it. A wrapper of the form
  `lerp(cityMask(x,z), original_final, (level(x,z) - y) * k)` composes on top
  of any terrain pack. `cityMask` and `level` would be our own
  density-function types: `cache_2d`, position-only, fed by the settlement
  region field (ARCHITECTURE.md). Hook via Lithostitched `wrap_noise_router`
  or our own mixin on the noise settings.
- **Terraces from flat spline segments**: plateaus with derivative 0 then a
  steep step; the same trick on `level(x,z)` gives terraced districts instead
  of one flat slab (cf. Lost Cities' quantised levels).
- Config-backed density functions (`config_constant`): exposing city flatness
  and level spacing as config without rewriting JSON.

## What to avoid

- Overriding the vanilla router JSON ourselves: it would fight Tectonic,
  Terralith and every overhaul; only a wrapper composes.
- A hard mask edge (cliffs), or lerping across water: the mask must fade over
  ~32–64 blocks, keep caves/aquifers (apply only near the surface), and go
  to 0 over oceans/rivers. Unchecked: whether `preliminary_surface_level` (used
  by some height estimates) also needs wrapping so structures see the flattened
  height; track 4's 26.3 reading should settle this.
- Costs: `final_density` is evaluated per cell of every chunk; a `cache_2d`
  mask is one lookup per column, probably small, but it's unmeasured.

## Verdict

Feasible and composable: wrap `final_density` with a cached city mask (spike
candidate for 2.x); Tectonic itself is a compat target to test with, not a
dependency.

**Sources / what I couldn't verify:** read the Java tree, the router JSON
(`final_density`, `initial_density_without_jaggedness`, `depth`,
`offset/final`, `offset/regions`, region splines), the stonecutter config, the
Modrinth page, on branch `new-islands` (f33a29d). 26.3 source not public;
`overlay.terratonic` not opened (Stardust Labs content). Not run; the wrapper is
a design sketch, not tested; cost not measured.
