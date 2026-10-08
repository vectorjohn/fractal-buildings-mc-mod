# Architecture

The design as it stands. Parts marked **hypothesis** or **open** wait for the
Phase 1 gate (building direction), the Phase 2 spikes and the worldgen ADR;
settled decisions live in [adr/](adr/). (From John's original roadmap doc,
2026-10-07; brought in line with Phase 1 research, 2026-10-15.)

Evidence tags, where a claim comes from research
([INDEX](research/INDEX.md)): *measured in game* (client game test on a real
world), *probe* (standalone test against vanilla registries, outside the
game), *read* (decompiled 26.3 source or a mod's source, not run),
*inferred* (reasoned from reading, not checked).

## Overview

A pure planner produces one **Building IR** (and, later, city plans). The
three.js viewer and the Fabric adapter are two consumers of that same IR.

```
                 ┌──────────── probes (terrain height, biome) ───────────┐
                 │  synthetic (tests) · recorded dump (viewer/CLI) · live MC │
                 └──────────────────────────┬────────────────────────────┘
 seed, region cell, lot, style ──► planner-core (pure Java) ──► Building IR / city plan
                                                                  │            │
                                                     cli → JSON → viewer    mod adapter
                                                     (three.js, galleries)  (roles → blocks,
                                                                             IR → structure pieces)
```

**Probes are the key seam.** Behind the same interface, the planner can read
synthetic terrain in unit tests, terrain recorded from a real world in the
viewer, or live terrain in Minecraft. Bugs found in-game can therefore be
replayed outside it.

Module rules (also in CLAUDE.md): `planner-core` never imports Minecraft;
generators emit abstract material roles; everything is deterministic from the
seed and position.

## Worldgen primer: where cities fit (confirmed by track 4 unless marked)

The original hypotheses held; track 4 made the limits exact. One-line facts
are in [NOTES.md](NOTES.md) ("Minecraft and Fabric APIs").

**Generation runs in stages, per chunk** (*read*,
[structure-pipeline](research/worldgen/structure-pipeline.md)):

1. **structure_starts:** decides which structures begin in this chunk and lays
   out all their pieces up front (lazily, see below).
2. **structure_references:** each chunk records the starts within ±8 chunks
   whose bounding box overlaps it.
3. **biomes:** stores the chunk's biomes.
4. **terrain:** noise, surface (material) rules and carvers, as one step. The
   beardifier (terrain adaptation) acts here.
5. **features:** places structure pieces, then features, clipped to this chunk.
6. **Lighting, spawning, then full.**

**Structures are decided before the chunk's own biome stage, but that doesn't
matter.** Overworld biomes come from six climate parameters computed from
noise, so structure code can query the biome source anywhere, including a
whole city footprint, without generating chunks (*probe*: ~0.0025 ms per
sample, a 512×512 layer in ~40 ms). Two corrections
([terrain-biome-queries](research/worldgen/terrain-biome-queries.md)):

- Biomes are **3D**: sample at the surface Y (height first, then biome).
  Sampled at y = 64, hills returned `lush_caves` (*probe*).
- Lookups are **order-dependent at exact climate ties** (~0.05% of cells,
  thread-local R-tree state). A fixed non-tie **priming lookup** before each
  query removes it (*probe*: 0 differences across orders and threads). Our
  answer is then deterministic, though not always the chunk's stored biome
  (*measured in game*: differs in 0.03% of cells).

Biome mods that change the biome source (TerraBlender, Biolith,
Lithostitched) change our answers too; probes must not assume vanilla.

**Terrain height comes in two tiers**
([terrain-biome-queries](research/worldgen/terrain-biome-queries.md),
[surface-level-and-flattening](research/worldgen/surface-level-and-flattening.md)):

- `ChunkGenerator.getBaseHeight(x, z, type, heightAccessor, randomState)`: a
  full column scan of `final_density`, pure and thread-safe. ~0.2 ms per
  column (*probe*), 0.43–0.51 ms on the server thread (*measured in game*).
  Matches real ground in 98.2% of land columns, p99 error 2 blocks (*measured
  in game*). Includes aquifer water and terrain mods' density changes;
  excludes carvers, features and the beardifier.
- `preliminary_surface_level`: a smoothed 2D estimate at ~0.0085 ms per column
  (*probe*), but ~12–15 blocks low with p90 error 17 (*measured in game*), and
  it misses Terralith's dunes and arches (*read*,
  [terralith](research/overlays/terralith.md)). A coarse screen for region
  suitability, never a foundation probe.

**Size is the real constraint** (*measured in game* unless marked,
[structure-pipeline](research/worldgen/structure-pipeline.md)):

- Blocks more than **8 chunks** from the start chunk are **silently dropped**,
  for every structure type, not just jigsaw: a piece 20 chunks long kept only
  chunks 0–8. Only jigsaw validates its size (≤ 128 blocks, 116 with terrain
  adaptation, *read*); a custom structure type must enforce it itself.
- **One start per structure per chunk** (*read*). A large city is therefore
  many coordinated starts, not one.
- Starts, references and features run on **one serial `worldgen` lane per
  dimension**: a 20 ms piece builder ran with max concurrency 1. Only biomes
  and terrain run in parallel (*read*). Every millisecond of planning at start
  time stalls that dimension's generation, so the plan is computed once per
  region cell, cached, and height sampling stays coarse: 1,024 columns (a
  16-block grid over 512×512) is ~0.2 s by the probe figure, ~0.5 s at the
  in-game rate.
- Pieces are built **lazily**: `findGenerationPoint` returns a stub and the
  piece builder runs only in `generate`; `/locate` calls
  `findGenerationPoint` from other threads without building pieces (*read*).
  So whether a district start exists must be decidable cheaply from position;
  the full plan belongs in the lazy builder (*inferred*).

**Terrain adaptation exists, and applies to our own pieces** (*read*,
[jigsaw](research/worldgen/jigsaw.md)). `beard_thin` (villages), `beard_box`
(ancient cities), `bury` (strongholds), `encapsulate` (trial chambers). The
beardifier treats **any non-jigsaw piece as a rigid box with ground at its
`minY`**, so a custom structure with `terrain_adaptation: beard_thin` and one
piece per building, box bottom at floor level, gets graded for free within a
12-block kernel (× 0.8 strength; bounding boxes grow by 12 for references).
How far that goes on steep slopes is not measured yet (S5); visible cuts on big
hills are likely (*inferred*).

**Placement rules that keep output order-independent** (*read*):

- The `postProcess` random depends on the structure's index in the registry,
  so other mods shift it: derive all piece randomness from our own hashes.
- Write only inside the chunk's box (`chunkBB`); neighbour writes depend on
  generation order. Don't read the live world in `postProcess` to decide
  anything.
- Two of our starts overlapping in one chunk apply in `LongOpenHashSet` order:
  each block must be owned by exactly one start.

## A second layer above biomes (proposal)

A **settlement region layer**: our own deterministic field, independent of
vanilla biomes. Track 5 found the shape proven elsewhere (TerraBlender's
regions, Lithostitched's domain-warped Voronoi field, Lost Cities' city
field), and no existing layer we could reuse to place cities
([overlays](research/overlays/)).

- The world is divided into large region cells (jittered grid or Voronoi),
  seeded by hashing (world seed, cell, purpose) with a strong mixer, never raw
  coordinates ([greuter](research/pcg/greuter-pseudo-infinite-cities.md);
  Lost Cities' circle mode leaves the seed out, *read*,
  [lost-cities-city-placement](research/overlays/lost-cities-city-placement.md)).
  Each cell decides whether it holds a settlement, its centre, size and
  culture. Leaning: membership as a scalar field with a threshold, with
  biomes as a **multiplier** (oceans and rivers suppress) rather than a
  boundary, plus a height veto from the coarse screen.
- The city plan is a pure function of the cell. It is computed on first use and
  cached (deterministic, bounded, thread-safe); recomputing after eviction must
  give the same bytes. Because it runs whole per cell, the plan may use
  global, sequential algorithms (road growth, agents, scoring) that could
  never run per chunk ([parish-muller](research/pcg/parish-muller-cities.md)).
- Each district is its own structure start on a sub-grid inside the cell, in
  its own chunk, its pieces kept within ±8 chunks of it. It reads the shared
  plan and emits only its own pieces. Placement: a subclass of
  `RandomSpreadStructurePlacement` answering "is this chunk a district origin?",
  which keeps `/locate` working (*read*,
  [structure-pipeline](research/worldgen/structure-pipeline.md)).
- Each district samples the biomes beneath it to choose a palette. A city
  straddling desert and forest gets sandstone streets on one side and timber on
  the other, with a blend at the border: distance-to-biome-edge computed in the
  core from coarse biome probes (Biolith's BiomePerimeters idea, made
  deterministic, [biolith](research/overlays/biolith.md)), palettes chosen
  through `c:` convention biome tags so biome mods map too
  ([terralith](research/overlays/terralith.md)).
- **Open:** roads that cross region-cell edges need agreed endpoints (hashed
  gates on cell borders, or Lost Cities' world-wide hashed arterial lines,
  [lost-cities-road-planning](research/overlays/lost-cities-road-planning.md)).

## Integration options

Research narrowed the original three ways to integrate to one base approach
plus three optional hooks. The worldgen ADR decides after the Phase 2 spikes;
the "Research" column is the current leaning, not a decision.

| Base approach | How | Gains | Costs | Research |
| --- | --- | --- | --- | --- |
| Structure-based (proposed) | Custom structure type and placement plus region layer; vanilla biomes untouched | Works with biome and terrain mods; vanilla clipping, saving, `/locate`, terrain adaptation | Many coordinated starts; ±8-chunk limit; serial lane | Only option with first-class hooks ([extension-points](research/worldgen/extension-points.md)). Leaning: **adopt** |
| Per-chunk feature (The Lost Cities today) | One `Feature` at `RAW_GENERATION` in every biome; each chunk builds its share of a pure plan | No size limit | No `/locate`, no structure boxes, no beardifier; vanilla structures overlap it | Fallback if coordinated starts fail S3/S6 ([the-lost-cities](research/mods/the-lost-cities.md)) |
| Biome overlay | Inject an urban biome (TerraBlender, Lithostitched, Biolith) | Cities affect grass, mobs, surface | Fabric API can't add overworld biomes; needs a library; replaces the biomes a city should span | Leaning: **drop** ([terrablender](research/overlays/terrablender.md)) |
| Custom chunk generator | Own generator wrapping vanilla terrain | Total control | `NoiseBasedChunkGenerator` is final; any other generator gets an empty-router `RandomState` (breaks `/locate`, biome lookups) without a mixin; world-preset conflicts | Leaning: **drop** ([custom-generator-and-pool-element](research/worldgen/custom-generator-and-pool-element.md)) |

(The table used to call the custom generator "the Lost Cities model"; current
Lost Cities is the per-chunk feature row.)

| Hook (with structure-based) | What it's for | Status |
| --- | --- | --- |
| **Density-function wrapping** (the "fourth hook") | Flatten or terrace city cells when the beard isn't enough | Open: a Phase 2 spike, judged against S5 (below) |
| **Structure-exclusion veto mixin** | Keep villages, outposts and other surface structures out of cities | Leaning: adopt; a Phase 2 spike to confirm (below) |
| **Procedural `StructurePoolElement`** | Our generated houses inside vanilla villages: an early in-game showcase with no city work | Worth a small spike ([custom-generator-and-pool-element](research/worldgen/custom-generator-and-pool-element.md)); footprint fixed per element, content free |

**Flattening (open)**
([surface-level-and-flattening](research/worldgen/surface-level-and-flattening.md),
[tectonic](research/overlays/tectonic.md),
[lithostitched](research/overlays/lithostitched.md)). Register our own
density-function types (`cityMask`, `level`, position-only and `cache_2d`) and
wrap router slots with them, never override the router files (that fights
Terralith and Tectonic). What is known, mostly *read* and *inferred*, none of
it run yet:

- Wrap **three slots with the same mask**: `final_density`,
  `chunk_surface_level` and the aquifer `surface_level` (the last two both
  derive from `preliminary_surface_level` in vanilla). With `final_density`
  alone, cuts more than a few blocks deep come out as bare stone (surface rule
  gated off) and may flood (aquifer sees cave air).
- **Prefer fill to cut**; fade the mask over ~32–64 blocks; zero over water and
  in Terralith's forced-flooded cliff zones; leave caves alone.
- **The plan reads unwrapped terrain.** A wrapped `getBaseHeight` would call
  back into a mask that reads the plan; keep the original function (as
  Lithostitched does) or use the coarse screen.
- Hook: our own mixin, or Lithostitched's `wrap_noise_router` /
  `wrap_aquifers` (MIT, but a dependency). Leaning: our own, with Lithostitched
  modifiers as optional compat.

**Keeping other structures out** (*read*, not run,
[keeping-structures-out](research/worldgen/keeping-structures-out.md)).
Data-pack exclusion zones and biome tags can't express region cells. Leaning:
two small veto mixins, on `ChunkGenerator.tryGenerateStructure` and
`StructureCheck.canCreateStructure`, so generation and `/locate` agree. They
veto structures in our tag (default: surface structures) when the start
position plus a margin falls in a city cell, deciding from seed and position
only. Later option: found cities on vanilla village sites instead of
excluding them.

**Backup option: post-generation placement** (John's idea, not yet
researched; track 7, `docs/research/postgen/`). Generate chunks far ahead of
the player, Distant Horizons-style, and place a whole city into the
already-generated chunks outside worldgen time, with our own code. That
would step around the ±8-chunk limit and the serial lane, at the price of
questions track 7 has to answer: staying a function of seed and position
(not of which chunks exist or where players went), terrain fit without the
beardifier, and cost. The one related note so far,
[astruct](research/mods/astruct.md), is a player-driven version of this and a
cautionary example, not a verdict on the idea.

## Planner pipeline (hypothesis)

What the building and city tracks point to, pending the synthesis (1.8) and
the Phase 1 gate, which picks the building-generation direction. Each stage
emits roles, not blocks, and is checked by invariants (reachable rooms,
closed roofs, supported floors, inside the lot).

- **City, per region cell:** districts grown from seeds with a height-change
  cost so borders follow ridges and water
  ([grimoire-tome](research/gdmc/grimoire-tome.md),
  [watabou](research/pcg/watabou-city-generators.md)) → road hierarchy with
  slope-aware A* ([jeb-gen-2023](research/gdmc/jeb-gen-2023.md)) → blocks →
  lots by recursive bisection with street access
  ([parcel-subdivision](research/pcg/parcel-subdivision.md)) → a brief per lot
  (style × class × region,
  [style-guides](research/aesthetics/style-guides-westeroscraft-conquest.md)).
- **Building, per lot:** massing by a typed Java rule API in the spirit of
  split/CGA grammars, children inside parents
  ([cga-shape-grammar](research/pcg/cga-shape-grammar.md),
  [split-grammars](research/pcg/split-grammars.md)) → coarse occupancy cells
  with neighbour-derived modules ([townscaper](research/pcg/townscaper.md)) →
  façades as depth layers
  ([depth-and-layering](research/aesthetics/depth-and-layering.md)) → roofs as
  a pitch profile over distance to the footprint edge
  ([roof-forms-and-massing](research/aesthetics/roof-forms-and-massing.md)) →
  interiors by constrained growth plus a connect pass from the entrance
  ([constrained-growth-floor-plans](research/pcg/constrained-growth-floor-plans.md),
  [rooms-and-mazes](research/pcg/rooms-and-mazes.md)) → bounded detail passes
  with a fallback (WFC or rewrite rules, never the main generator,
  [wfc-model-synthesis](research/pcg/wfc-model-synthesis.md)).
- **Palette:** each role maps to an ordered 3–5 block gradient; a
  deterministic weathering field picks the step
  ([palettes-and-gradients](research/aesthetics/palettes-and-gradients.md)).
