# The Lost Cities (McJty)

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A mod that fills worlds with ruined modern cities, highways and
railways. [CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities),
[source](https://github.com/McJtyMods/LostCities), developer docs at
[mcjty.eu](https://mcjty.eu/docs/mods/lost-cities/city_generation)
(asset structure, city generation, thread safety). City/biome *selection* is
covered by track 5 (see [`../overlays/`](../overlays/)); this note is about
generation and integration. Maintained (26.2 Fabric and NeoForge).

## Approach

The world is a grid of 16×16 chunks; a city factor (seeded
city circles or Perlin noise, scaled by biome multipliers and height limits)
decides which chunks are city, and each city chunk gets one ground level plus a
`cityLevel` step of **6 blocks**. Streets come from a three-tier grid
(primary roads every 8–32 chunks, 0–2 secondary per axis, short tertiary
spurs), and every remaining chunk is a building, park, fountain or street. A
building is a stack of **parts** (character grids, normally 16×16 × 6-high
floors plus cellars) chosen per floor from the building's list, with
**palettes** mapping characters to blocks and weighted **variants**;
**multibuildings** tile ordinary buildings over a `dimx × dimz` chunk
rectangle.

## Procedural vs templated

Procedural at the city scale (zoning, street
grid, levels, highways, rails, damage/explosions, rubble), templated at the
building scale: every floor is a hand-authored part, randomised only by
part choice, floor count and palette variants. All assets are data-pack JSON
(13 registries: parts, buildings, multibuildings, palettes, variants,
conditions, city styles, world styles, scattered, predefined, stuff, …), and
there is an in-game part editor/exporter.

## How it hooks into worldgen

Not a jigsaw structure and (in current versions) not a
custom chunk generator: a single `Feature` (`lostcities:lostcities`) injected
into every overworld biome at the `RAW_GENERATION` step via Fabric biome
modifications. For its centre chunk it rewrites the whole column: levels
terrain to the city height, then places parts through a `ChunkDriver`. To know
neighbours' heights without generating them, it **re-runs the noise column**
(`HeightGenOpt`, a copy of vanilla's `NoiseChunk` interpolation) at sample
points of neighbouring chunks. Because everything is chunk-aligned, there is no
structure size limit to work around: a city is unbounded; a multibuilding is a
few chunks.

**Determinism.** Explicitly designed for it: decisions depend only on seed,
dimension, chunk coordinates, profile and immutable assets, never on query
order or cache warmth. Multi-chunk things (multibuildings, scattered areas) are
resolved from a canonical origin (fixed `areasize` grid), not from whichever
chunk generates first. Per-chunk mutable state is in a per-thread
`GenerationContext`; cross-edge post-processing (vines) uses ordered striped
locks over the 3×3 neighbourhood. `BuildingInfo` memoisation behind one
per-dimension lock is its known throughput bottleneck (`plan_performance.md`).

**Biome adaptation.** World styles pick city styles by biome; city styles and
palettes swap materials and building sets. Terrain is flattened, not adapted
to: cities sit on terraces with smoothed edges.

## Licence

MIT (repo `LICENSE`). Code may be studied and borrowed with
attribution; assets are in the same repo and, as far as the licence file
says, also MIT (not checked file-by-file).

## What to borrow

- Chunk-grid zoning: a cheap, order-independent way to plan an unbounded city
  at chunk resolution, with canonical-origin resolution for multi-chunk items.
- Fixed floor module (6 blocks) so doors, streets, rails and neighbours align.
- Sampling neighbour terrain height from the noise router instead of reading
  generated chunks; a model for our live terrain probe.
- Data-driven palettes with weighted variants and damage substitutes; the part
  editor/exporter as a workflow idea.
- Its determinism and thread-safety rules read almost exactly like ours.

## What to avoid

- Building-scale templating: every building is a 16×16 stack of authored
  floors, so variety is bounded by the part library and everything reads as
  chunk-aligned boxes.
- Flattening the terrain to terraces; cities ignore the landscape.
- Re-implementing vanilla `NoiseChunk` internals: brittle across releases.
- A per-chunk `Feature` that overwrites whole columns does not interact with
  vanilla structure placement (no `/locate`, no structure bounding boxes,
  villages can overlap); fine for "cities everywhere", awkward for settlements
  that should coexist with vanilla.

## Verdict

Best prior art for chunk-independent city-scale planning; wrong model for building variety.

Suggested deep-dive if we choose a non-structure integration: the feature
hook, neighbour height sampling, and canonical-origin planning.

**Sources / what I couldn't verify:** Read branch `26.2_fabric` at 7e7d102
(Oct 2026: worldgen sources, `docs/fabric.md`, `plan_performance.md`) and the
mcjty.eu pages on assets, city generation and thread safety. Not checked:
behaviour in game (not run); whether the Fabric port still
has a custom chunk generator option for floating/cavern landscapes (the
`LandscapeType` config exists; not traced); exact `areasize` default for
multibuildings; asset licence beyond the root `LICENSE`.
