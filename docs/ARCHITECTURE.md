# Architecture

The design as it stands. Parts marked **hypothesis** or **open** wait for the
Phase 2 spikes and the worldgen ADR (2.10); settled decisions live in
[adr/](adr/): the building direction in
[ADR 0001](adr/0001-building-direction.md) and the integration approach to
spike in [ADR 0002](adr/0002-integration-approach.md), both accepted at the
Phase 1 gate (2026-10-16). The worldgen decisions are in
[ADR 0003](adr/0003-worldgen.md) (accepted at the Phase 2 gate, 2026-10-16),
which supersedes 0002. (From John's original roadmap doc, 2026-10-07;
brought in line with Phase 1 research, 2026-10-15.)

**Size targets (ADRs 0001, 0003):** cities have **no fixed size cap**; the
practical limit is chunk generation time. Buildings of every
size are first-class, from cottages to halls, city blocks and towers up to
the y 319 build ceiling.

Evidence tags, where a claim comes from research
([INDEX](research/INDEX.md)): *measured in game* (client game test on a real
world), *probe* (standalone test against vanilla registries, outside the
game), *read* (decompiled 26.3 source or a mod's source, not run),
*inferred* (reasoned from reading, not checked).

## Overview

A pure planner produces one **Building IR** (and, later, city plans). The
three.js viewer and the Fabric adapter are two consumers of that same IR. Its JSON
schema is in [IR.md](IR.md).

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
  query removes it (*probe*: 0 differences across orders and threads); under
  TerraBlender with more than one region, prime with a per-region anchor
  instead ([S1](research/spikes/s1.md); [ADR 0003](adr/0003-worldgen.md) D3). Our
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
  [terralith](research/overlays/terralith.md)). Not used in the game; kept
  only as a fallback screen for the region layer if Phase 6's B8 region
  budget fails, never a foundation probe ([ADR 0003](adr/0003-worldgen.md)
  W2, amended 2026-10-22).

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
  region cell, cached, and warmed off the lane. Heights are exact
  everywhere (the dump definition), made affordable by a per-chunk memo, a
  windowed batched chunk fill (1.9 ms a chunk against 92–142 ms for 256
  single columns), background warming and cache retention
  ([ADR 0003](adr/0003-worldgen.md) W2).
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
  boundary, plus a height veto on exact heights, decided once per city and
  cached. The coarse screen's bias is not stable ([S2](research/spikes/s2.md));
  it returns only as a region-layer fallback if B8's budget fails, culling
  with a margin of at least 20 blocks ([ADR 0003](adr/0003-worldgen.md) W2,
  D4).
- Field shape ([lithostitched-deep-dive](research/overlays/lithostitched-deep-dive.md)):
  2D cells (a 3D Voronoi sliced at y = 0 leaves sliver cells, *probe*), a
  full 64-bit seed hash (Lithostitched truncates to `int`), per-cell decisions
  taken from the best of 8 hashed candidate centres (deciding at the site
  alone lost half the cities, [S4](research/spikes/s4.md)), pure but cached
  ([ADR 0003](adr/0003-worldgen.md) R1, D6), and the exact distance to the
  city footprint's edge for the flatten-mask fade and the veto margin
  (F2 − F1 only bounds it). Exposed as a `fractalbuildings:settlement` 2D density-function type
  so packs (with Lithostitched or not) can use our cities.
- The city plan is a pure function of the cell. It is computed on first use and
  cached (deterministic, bounded, thread-safe); recomputing after eviction must
  give the same bytes. Because it runs whole per cell (at least the skeleton,
  see "City scale" below), the plan may use global, sequential algorithms
  (road growth, agents, scoring) that could never run per chunk ([parish-muller](research/pcg/parish-muller-cities.md)).
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

**City scale: a two-level plan** ([ADR 0003](adr/0003-worldgen.md) P1,
measured by [S6](research/spikes/s6.md)). The ±8-chunk box bounds a district
(at most 14 × 14 chunks, 224 blocks, with the beard), not a city. What
doesn't scale for free is a monolithic plan on the serial lane: about 0.5 s
per Mblock² with exact heights, ~2.5 s at 2048² and 29.5 s at 8192²
(S6, quiet machine, stand-in generator). So the plan has two levels:

- **City skeleton** (built first, warmed ahead): exact heights on a 32-block
  lattice (memoised single columns), roads on
  position-hashed lines (as Lost Cities' arterials,
  [lost-cities-road-planning](research/overlays/lost-cities-road-planning.md)),
  district borders, and the big lots that districts must be drawn around.
  Any district can read it without building its neighbours.
- **District detail** (lazy): exact heights, lots and building briefs, built
  per district in the lazy piece builder and warmed off the lane where
  possible. Save size and cache memory grow linearly per district.
- **Across cells:** neighbouring settled cells meet at **hashed gates on
  their shared border**, so roads agree without either plan reading the
  other.
- **Join cells, don't grow them** ([ADR 0003](adr/0003-worldgen.md) R2,
  from S4): a big city's hashed radius claims the neighbouring cells whose
  sites it covers; the higher hashed priority wins a conflict, and a dropped
  or unsuitable claimant releases its cells. Pure, over a bounded radius;
  the exact rule is Phase 5's (5.8). S6 found no ceiling: 6.5's default is
  unbounded, with a soft warning above 8192².

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

- Wrap **three slots with the same mask**: router `final_density`, router
  `chunk_surface_level` and the aquifer `surface_level`. Leave the shared
  `preliminary_surface_level` entry alone: in vanilla only those last two
  read it, so it stays unwrapped (*read*,
  [lithostitched-deep-dive](research/overlays/lithostitched-deep-dive.md)).
  With `final_density` alone, cuts more than a few blocks deep come out as
  bare stone (surface rule gated off) and may flood (aquifer sees cave air).
- **Prefer fill to cut**; fade the mask over ~32–64 blocks; zero over water and
  in Terralith's forced-flooded cliff zones; leave caves alone.
- **The plan reads unwrapped terrain.** Wrapping replaces the router in the
  shared settings, so `getBaseHeight` sees wrapped terrain. The mask depends
  on the region field only, never on the lot plan. The plan uses exact
  heights from the original `final_density` captured before wrapping and
  compiled with `RandomState.getSampler` (*read*, not run).
- Hook: our own mixin, or Lithostitched's `wrap_noise_router` /
  `wrap_aquifers` (MIT, but a dependency, and `wrap_aquifers` needs 2.0.6+).
  Leaning: our own; compat by publishing our field as a density-function type
  (region layer, above).

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

## Planner pipeline

The building pipeline is decided ([ADR 0001](adr/0001-building-direction.md):
volume massing, then a typed split grammar for floors and façades); the city
stages are still a **hypothesis**. Each stage emits roles, not blocks, and is
checked by invariants (reachable rooms, closed roofs, supported floors,
inside the lot).

- **City, per region cell** (skeleton first, district detail lazily; see
  "City scale" above): districts grown from seeds with a height-change
  cost so borders follow ridges and water
  ([grimoire-tome](research/gdmc/grimoire-tome.md),
  [watabou](research/pcg/watabou-city-generators.md)) → road hierarchy with
  slope-aware A* ([jeb-gen-2023](research/gdmc/jeb-gen-2023.md)) → blocks →
  lots by recursive bisection with street access
  ([parcel-subdivision](research/pcg/parcel-subdivision.md)) → a brief per lot
  (style × class × region,
  [style-guides](research/aesthetics/style-guides-westeroscraft-conquest.md)).
- **Building, per lot:** massing by volume assembly inside the lot hull (a
  checked stage) and one floor grid, then split/repeat/comp in a typed Java
  rule API for floors, bays and façades
  ([grammars-deep-dive](research/pcg/grammars-deep-dive.md)) → coarse occupancy cells
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
- **Large buildings** ([ADR 0001](adr/0001-building-direction.md); towers,
  halls and city blocks are classes of their own, not an extension of houses):
  - *Setback massing:* a tower is stacked volumes, each inside the one below
    (Greuter's "wedding cake",
    [greuter](research/pcg/greuter-pseudo-infinite-cities.md)), so upper
    floors are supported by construction; podium, shaft and crown are bands.
    Halls and blocks are unions of volumes, as for houses.
  - *Storey grid:* one grid of storey heights per building (taller ground
    floor, repeated typical floors); façade splits run once per band, so
    windows align over any number of storeys.
  - *Core first:* the vertical core (stairs, later lifts) is placed before the
    floors and kept through all of them; each floor grows around it, so
    reachability holds per floor
    ([constrained-growth-floor-plans](research/pcg/constrained-growth-floor-plans.md)).
  - *Limits:* the build ceiling is y 319 (*read*, 26.3 data), about 60 storeys
    of 4 blocks above sea level. The ±8-chunk box bounds the footprint, not
    the height: a building lies in one district (at most 224 blocks across,
    [ADR 0003](adr/0003-worldgen.md)), so footprints up to ~200 blocks need
    district borders drawn around big lots (*inferred*).
  - *Cost:* block count, not rule count, dominates (~125k blocks of walls and
    floors for a 40×40×240 tower, arithmetic); interiors may be generated per
    floor from (building, floor) hashes rather than cached whole (*inferred*).
- **Palette:** each role maps to an ordered 3–5 block gradient; a
  deterministic weathering field picks the step
  ([palettes-and-gradients](research/aesthetics/palettes-and-gradients.md)).
