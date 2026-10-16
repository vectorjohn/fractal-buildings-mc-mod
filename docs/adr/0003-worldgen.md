---
tags: adr
status: accepted
date: "2026-10-16"
author: researcher
approvedBy: "John, 2026-10-16"
supersededBy:
dependsOn:
related:
  - "[[docs/adr/0002-integration-approach]]"
  - "[[docs/adr/0001-building-direction]]"
research:
  - "[[docs/research/spikes/s1]]"
  - "[[docs/research/spikes/s2]]"
  - "[[docs/research/spikes/s3]]"
  - "[[docs/research/spikes/s4]]"
  - "[[docs/research/spikes/s5]]"
  - "[[docs/research/spikes/s6]]"
  - "[[docs/research/spikes/s7]]"
  - "[[docs/research/spikes/s8]]"
  - "[[docs/research/spikes/s9]]"
  - "[[docs/research/pool/recommendation]]"
  - "[[docs/research/synthesis]]"
  - "[[docs/research/worldgen/structure-pipeline]]"
  - "[[docs/research/worldgen/extension-points]]"
  - "[[docs/research/worldgen/terrain-biome-queries]]"
  - "[[docs/research/worldgen/surface-level-and-flattening]]"
  - "[[docs/research/worldgen/keeping-structures-out]]"
  - "[[docs/research/overlays/lithostitched-deep-dive]]"
  - "[[docs/research/overlays/terrablender]]"
  - "[[docs/research/overlays/lost-cities-road-planning]]"
  - "[[docs/research/mods/the-lost-cities]]"
  - "[[docs/research/postgen/overview]]"
---

# 0003. Worldgen: district starts, a two-level plan, and determinism rules

Cities enter the world as one structure start per district. Every start reads
a pure, cached two-level plan, and a 2D hashed region field places the cities.
City size has no design limit. This ADR **supersedes
[ADR 0002](0002-integration-approach.md)** and restates every 0002 decision
still in force, so 0002 is history only. John accepted the proposed
decisions as written (2026-10-16), and answered the gate's questions: 0003
supersedes 0002, dropped big cities release their cells (R2), and S6 is
re-measured on a quiet machine.

## Context

- **Limits** ([structure-pipeline](../research/worldgen/structure-pipeline.md)):
  blocks beyond ±8 chunks of a start are silently dropped; one start per
  structure per chunk; starts, references and features share one serial
  `worldgen` lane per dimension; `/locate` runs placement on other threads
  without building pieces.
- **John's target** (Phase 1 gate): effectively unbounded cities ("512 is
  too small").
- **The spikes:** [S3](../research/spikes/s3.md): 4 and 9 starts share one
  plan, and their blocks are identical in 7 orders.
  [S4](../research/spikes/s4.md): deciding at the cell's site loses half the
  cities; join cells rather than grow them. [S5](../research/spikes/s5.md):
  `beard_thin` is enough at building scale. [S7](../research/spikes/s7.md):
  flattening works, also under Terralith + Tectonic.
  [S1](../research/spikes/s1.md), [S2](../research/spikes/s2.md): the probes
  are deterministic, and accurate where it matters.
  [S8](../research/spikes/s8.md), [S9](../research/spikes/s9.md): the veto
  and the pool element work. [S6](../research/spikes/s6.md): a two-level plan
  has no ceiling; prefetch hides the serial lane; pieces must save
  references.
- **Figures marked [S6] are pending a quiet-machine re-measure** (John,
  2026-10-16). S6 ran at loadavg 5–20: lane totals repeat within 3 % and
  byte counts within 0.03 %, but wall-clock, chunks/s and lead times vary
  ±25 %. S6 also used a stand-in generator; real ones are expected to cost
  5–20× more. The decisions stand; only the figures may change.

## Options

1. **Integration.** (a) Structure starts per district. (b) A per-chunk
   feature (Lost Cities): no box limit, no serial lane, but no `/locate`,
   beard or boxes. (c) Post-generation (track 7): unbounded, but timing
   depends on players. (d) A biome overlay: replaces biomes, shapes no
   terrain, no Fabric API. (e) A custom chunk generator: clashes with world
   presets.
2. **Plan.** (a) One monolithic plan per city: about 0.5 s per Mblock², 2.5 s
   at 2048², 34 s at 8192² [S6]. (b) Two levels: a skeleton plus lazy
   district detail.
3. **Big cities.** (a) Grow region cells: a radius-1000 city needs cells
   about 5,000 blocks across. (b) Join 1024-block cells.
4. **City decision.** (a) At the cell's site: no cache, but only 26–41 cities
   per window. (b) Best of 8 hashed candidates: 73–96 cities, with a cache.
5. **Terrain.** (a) Beard only. (b) Flatten every city. (c) Beard, with
   flattening as a tool.
6. **Dependencies.** (a) None required; publish our own density function.
   (b) Require Lithostitched: about 80 required mixins and a global jigsaw
   replacement.

## Decision

**1a, 2b, 3b, 4b, 5c, 6a.**

### Integration

- **W1 Starts.** A custom `Structure` with a `RandomSpreadStructurePlacement`
  subclass. `isPlacementChunk` decides "district origin?" from geometry and a
  hashed roll, with no probes (3–36 ns); `getPotentialStructureChunk` returns
  the district centre; `findGenerationPoint` repeats the origin test
  (`StructureCheck` skips the placement), then reads the cached city
  decision; the lazy piece builder reads the cached plan. `/locate` finds a
  district, not the city centre.
- **W2 Suitability is decided per city, never per start**, by one cached
  decision on the **coarse screen** (12–17 ms [S6]); exact heights cost
  140–185 ms and make `/locate` 3–34 s instead of 0.1–0.3 s [S6].
- **W3 Pieces save references and their brief, never IR.** A reference is
  13 B per piece [S6]; stored IR is 11–24 KB per tower, so a 58-tower
  downtown fills the 1 MB region slot [S6]. A piece carries its brief (about
  40–60 B) or the plan entry is pinned while a start is live: a thrashing
  cache otherwise rebuilt a 68 ms district plan per `postProcess` (119 s in
  total) [S6].
- **W4** Structure step `top_layer_modification`; at the default step, trees
  overwrote our blocks.
- **W5 Terrain: the S5 way** (John kept it, 2026-10-16). One rigid piece per
  building (per wing on a slope), box bottom at floor level, `beard_thin`;
  foundations are our own blocks below the box; big buildings step their
  bays down the slope. A district also owns the road and wall segments in
  its box. Never `bury` or `encapsulate`. Don't reject steep lots for large
  buildings (on S5's steep site that dropped the hall and most houses);
  rejecting is only for optional small fill buildings. No terraced city
  areas. The 5–12-block uphill cut faces are accepted.
- **W6 Flattening is an optional tool** for plazas, level streets and water
  edges, through S7's mixin on the `noiseRouter()` and `aquifers()`
  accessors. Wrap `final_density`, `chunk_surface_level` and the aquifer
  `surface_level` with one mask; never `preliminary_surface_level`. Prefer
  fill to cut; John found S7's cut and fill acceptable. The mask reads only
  the region field and the skeleton. Needs a depth band (full replacement
  removes caves, ores and the beard). Untested: sea level, the depth band.
- **W7 Veto.** Two `@Inject(HEAD, cancellable)` mixins
  (`tryGenerateStructure`, `StructureCheck.canCreateStructure`) share one
  predicate: the structure is in `fractalbuildings:excluded_from_cities` and
  its start chunk lies within the city footprint plus a margin of at least
  its reach (villages 80, jigsaw 128 + 12). The edge distance is exact
  (F2 − F1 only bounds it, S4). The margin is **untested**. Re-check the
  private targets with `javap -p` on every Minecraft bump.
- **W8 Pool element** (S9): showcase and vanilla-village compat, not the city
  path. The `houses.json` override collides with other packs; Lithostitched
  changes every village; the real-pipeline test is owed.
- **W9 No required dependencies** (from 0002 I4). We publish
  `fractalbuildings:settlement` as a **2D density-function type**, so packs,
  with Lithostitched or without, can gate structures or inject biomes on our
  cities. We don't ship Lithostitched modifiers or data.
- **W10 Backups.** The per-chunk feature (1b) is a contingency only: S6 found
  no ceiling. Post-generation (track 7, 0002 I5) stays a backup, promoted
  only if both structures and the feature fail; its batch 2 waited for S3 and
  S6, which have now reported. The overlay (1d) and custom generator (1e)
  are dropped.

### Plan and region field

- **P1 Two-level plan.** The **skeleton** is the coarse screen on a 32-block
  stride plus arterials hashed from (seed, axis, line index), computed per
  tile of at most 4096² (83 ms, 2 B per 1024 blocks² [S6]). A gate on a cell
  border comes from a hash of the edge key (0 mismatches over 9,800 edges),
  so tiles and cells agree without coordination. **District detail** probes
  exactly only at building sites: 40–100 ms whatever the city size [S6]. A
  district is computable from the skeleton alone.
- **P2 Prefetch, don't pool.** Four workers build a district's IR from start
  creation. At walk, sprint and elytra speed 0 of 244 IR lookups ran inline;
  the cold downtown lane fell from 6.5 s to 9 ms; a Chunky-style pregen ran
  12 of 469 inline [S6]. **A teleport has no lead** and pays the cold cost
  (mitigation, untested: build the skeleton shell first). Pool only if, with
  the real generator, more than 5 % of lookups run inline at elytra speed or
  inline time exceeds 10 % of the downtown lane.
- **R1 Field.** 2D hashed Voronoi, cell size 1024, jitter 0.7, a full 64-bit
  seed hash, biome as a multiplier through `c:` tags. Each cell decides from
  the **best of 8 hashed candidate centres** on a 64-block lattice. This
  replaces the Phase 1 "evaluate once at the site, no cache": the decision
  is still pure, but cached (D6).
- **R2 Big cities join cells.** A cell's hashed type and radius claim the
  cells whose sites lie within that radius; on a conflict the higher hashed
  priority wins and the loser is dropped whole. **A dropped or unsuitable big
  city releases the cells it claimed** (John, 2026-10-16): they, and the
  loser's own cell unless a winner claims it, decide alone as one-cell
  settlements. The rule must be pure: decided from the seed and positions
  within a bounded radius, never from generation order. Proposed mechanism,
  untested (5.8 settles it): a suitable claimant survives if no suitable
  claimant whose claimed cells overlap its own has a higher priority. That is
  one round, read within about 3 × the largest radius R (S4: R = 2,400, so
  about 15 × 15 cells), with suitability screened only for large types and
  cached. A greedy cascade, where each drop can free a third claimant, has
  no bounded radius and is ruled out; k rounds would read (2k + 1) × R.
  Joined cities' roads meet at the hashed border gates (P1).
- **R3 Fixture** `fb-region-dump/1`. Live and recorded probes use one height
  predicate (S4 read `getBaseColumn`).
- **For Phase 5, not decided:** John (S4 review) notes that people put cities
  near water; joined cities often reach it, villages go anywhere. A water
  term in the best-of-8 score would keep the decision pure.

### Practical size limits

- **City: no limit in the design**; the practical one is chunk generation:
  4096² is 65k chunks, about 40 min at 25 chunks/s; 8192² about 3 h [S6].
  6.5's default: unbounded, with a soft warning above 8192².
- **District:** at most 14 × 14 chunks (224 blocks), start at the centre (±8
  chunks less the 12-block beard; S3). Buildings never cross a district
  border, so a footprint is under 224 blocks (0001 estimated about 200).
- **Pieces:** keep a start under about 10k pieces; a district can't reach
  about 11k, and the region slot overflows at about 80k references [S6].
- **Lane budget:** writes about 100 ns per block, 3–5 ms per downtown chunk;
  about 200 exact probes per district unless prefetched; first touch must
  fit the lead of about 26 blocks of travel, 0.8–1 s at elytra [S6].
- **Memory:** compact IR, 2.2 MB per downtown district; an LRU of 64 is
  about 140 MB before any typical-floor memo [S6].

### Determinism rules

- **D1 Hash everything** from (seed, position, purpose) through a 64-bit
  mixer. Pieces ignore their `random` argument. No shared counters.
- **D2 Core math is bit-exact:** integers or `StrictMath`, never `Math.sin`,
  `cos` or `pow` (S4's port must change this).
- **D3 Biome lookups are primed with a fixed anchor**; under TerraBlender
  with more than one region, with a per-region anchor from (seed, region
  index), fixed per world; detect TerraBlender at runtime.
- **D4 Heights.** `getBaseHeight(OCEAN_FLOOR_WG)` is exact on 99–99.9 % of
  land and an upper bound of solid ground there; over frozen oceans icebergs
  rise up to +99 above it, so also check the water surface. The
  coarse screen is for culling and the skeleton only, with a margin of at
  least 20, never a floor height. Planner probes read **unwrapped** terrain,
  and the plan never re-probes after the beard.
- **D5 Ownership.** District boxes partition the plane; a road is drawn by
  the district that owns the block; pieces clip to `chunkBB` and their
  district box. Our air is not ours: foreign leaves get in.
- **D6 The pure-cache contract.** The plan, cell decisions and IR are pure
  caches keyed by everything they depend on, `genVersion` included. Workers
  may prefetch; the lane joins the in-flight future or computes the value.
  **No result depends on cache state** (S6: byte-exact with live heights
  under 122k evictions). One future per key; no long build inside
  `computeIfAbsent`.
- **D7 Test criterion: the blocks we own, exactly** (ledger or write digest,
  plus a raster of the plan with solid blocks exact). Whole-chunk hashes
  don't work: vanilla differs in 455–575 of 576 chunks. Test cell away from
  spawn; keep the mutants.

### Permanent regression tests

- **T1** S3's order test with S6's evict-and-compare and mutants (Phase 3 or
  6). **T2** S9 in a real noise world, with a Lithostitched matrix. **T3**
  S8's margin test with a village just outside a city, plus `/locate`.
- Also: S1 priming over the supported mod sets; S4's field tests with a
  quart-resolution priming case; S6's acceptance numbers as Phase 4
  assertions (skeleton and detail under 100 ms each, constant first touch
  [S6]); lane counters behind a flag; 3.10.

## Consequences

- **Changed from 0002:** I2's "reject steep lots" (now small fill only) and
  "flatten only if S5 fails" (now a tool); I3's F2 − F1 margin (now exact);
  I6's 512² floor and ~250-block districts (now no limit, 14 × 14 chunks);
  grow versus join (join). Still in force and restated: I1 (W1, W3, W5,
  W10), I4 (W9), I5 (W10), I6's two-level plan and border gates (P1, R2).
- **ARCHITECTURE updated:** best-of-8 decisions, per-region priming, 14 × 14
  districts, 2.5 s at 2048² with the two-level plan, the coarse-screen veto.
- **Easier:** one plan feeds structures, the fallback and the pool element,
  with no coordination between districts.
- **Harder:** teleporting into a downtown; the join rule is unproven; the
  coarse screen's cost under Tectonic is unmeasured for suitability and the
  skeleton (S2 put it above an exact probe there; measure in Phase 5 or use
  sparse exact probes); a `genVersion` change mid-world leaves seams
  (rendezvous choice limits them to the buildings where a new rule wins).
- **Revisit:** 1b if the real generator outruns 4 workers at elytra speed;
  pool components if the pooling rule triggers; 3a if joins can't stay
  local; 6b if flattening needs more than our three hooks or pack makers need
  more than our density function.
