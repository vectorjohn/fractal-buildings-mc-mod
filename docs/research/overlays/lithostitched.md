# Lithostitched

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Apollo's data-driven worldgen library: JSON "worldgen modifiers", biome
injectors, extra structure, placement and density-function types, used by
Terralith (mod version), Tectonic and many structure packs.
[Repo](https://github.com/Apollounknowndev/lithostitched) (branch per version,
read `26.3` and the multi-version `cloche` default branch),
[Modrinth](https://modrinth.com/mod/lithostitched). Maintained: 2.0.5 for 26.3
(Fabric, NeoForge) released 2026-10-08.

## Approach

Packs drop `lithostitched:worldgen_modifier` JSON files that patch registries
after load: add/remove features and spawns, add template-pool elements,
replace biome climate/effects, wrap density functions or the noise router,
add surface (material) rules, and **set a spawn condition on any structure**.
**Biome injectors** (`add_points`, `replace_partially`, `replace_fully`,
`force_placement`, `dispatch_alternate_layout`) rewrite the biome layout, with
conditions on climate parameters, **any density function**, or a **region**.
A region is a weighted slice of a region density function, by default a
domain-warped **cellular (Voronoi) noise**, `frequency 0.002` (cells about 500
blocks), sampled per biome.

## Procedural vs templated

Library, not content. Its region field and conditions are procedural noise;
what goes in them is authored JSON.

## How it hooks into worldgen

Wraps the dimension's biome source in an `InjectorBiomeSource` that delegates
to the vanilla `MultiNoiseBiomeSource` and post-processes each quart lookup
(region, then injectors in priority order); also edits the vanilla parameter
list and R-tree in place, with reflection to patch TerraBlender's trees.
Structure gating works differently: `set_structure_spawn_condition` swaps the
structure registry entry for a `DelegatingStructure` that tests a
`PlacementCondition` (`sample_density`, `sample_noise_router`, `in_biome`,
`grid`, `height_filter`, boolean combinators) before delegating to the real
structure. Terrain: `wrap_noise_router` / `wrap_density_function` change the
density functions themselves (Tectonic, same author, MIT, requires
Lithostitched; I didn't check which modifiers it uses).
Everything stays a pure function of seed and position.

## Licence

MIT (declared in `fabric.mod.json` and on Modrinth; no LICENSE file in the
repo). Study and borrowing are allowed with attribution.

## What to borrow

- **Regions as a density function.** A city region can be a seeded Voronoi
  cell field like theirs, evaluated anywhere, consumed by several systems at
  once.
- **Gating a structure by a field**, not by biome: our district starts can
  ask "is this inside a settlement cell?" the same way.
- **Shaping terrain from the same field** (wrap the noise router so city
  cells are gentler) is a real option beyond `beard_thin`, and stays
  deterministic because it is position-only. Costly and conflict-prone, so a
  candidate spike, not a default.
- Optional compat: shipping Lithostitched modifiers as data so pack makers can
  tune where cities go.

## What to avoid

- Biome injection to mark cities: same objection as TerraBlender (replaces the
  biomes the city should span, and `getNoiseBiome` cost grows per injector).
- Registry-swapping and reflection into other mods' internals (TerraBlender,
  Blueprint): powerful but fragile; we shouldn't need it.
- A hard dependency for core behaviour; the field we need is ~50 lines of our
  own code.

## Verdict

The closest match to our "settlement layer": a Voronoi region field plus
field-gated structures, all data-driven. Borrow the design, maybe offer
compat; don't depend on it.

**Sources / what I couldn't verify:** read `InjectorBiomeSource`,
`RegionManager`, `Region`, `ParameterMap`, `ReplacePartially`,
`SetStructureSpawnConditionModifier`, `SampleDensityPlacementCondition`, the
default `region/overworld` JSONs (26.3 and `cloche`). Not run. Cell size is
1/frequency, not measured. (The `Double.MIN_VALUE` default for
`sample_density`'s `min_inclusive` is stale: at 9a12e65 (2.0.6) it is
`-Float.MAX_VALUE`, see [the deep-dive](lithostitched-deep-dive.md).)
