---
tags: adr
status: proposed
date: "2026-10-16"
author: researcher
approvedBy:
supersededBy:
dependsOn:
  - "[[docs/adr/0002-integration-approach]]"
related:
  - "[[docs/adr/0001-building-direction]]"
research:
  - "[[docs/research/spikes/s1]]"
  - "[[docs/research/spikes/s2]]"
  - "[[docs/research/spikes/s3]]"
  - "[[docs/research/spikes/s4]]"
  - "[[docs/research/spikes/s5]]"
  - "[[docs/research/spikes/s7]]"
  - "[[docs/research/spikes/s8]]"
  - "[[docs/research/spikes/s9]]"
  - "[[docs/research/pool/recommendation]]"
  - "[[docs/research/worldgen/structure-pipeline]]"
  - "[[docs/research/worldgen/surface-level-and-flattening]]"
---

# 0003. Worldgen: district starts over a joined region field, with determinism rules

Cities enter the world as one structure start per district. Each start reads
a lazily built, purely cached city plan, and the plan is placed by a 2D
hashed region field. This ADR fixes the integration approach, the size limits
and the determinism rules. **Draft: the size limits wait on S6.**

## Context

[ADR 0002](0002-integration-approach.md) chose what to spike. Phase 2 has
answered all of it except S6 (scale and cost):

| Spike | Answer that shapes this ADR |
|---|---|
| [S3](../research/spikes/s3.md) multi-start | Yes. 4 and 9 starts share one plan. Owned blocks were identical in 7 orders (8 threads plus a cache evictor among them), and no block was written twice. A road across a district border stays one component. `/locate` works through the placement subclass. Whole-chunk hashes are order-dependent in vanilla itself. |
| [S4](../research/spikes/s4.md) region map | Sensible on 4 seeds, but deciding at the site alone loses half the cities. Use the best of 8 hashed candidate centres. Big cities: join cells, don't grow them. |
| [S5](../research/spikes/s5.md) terrain | `beard_thin` and one rigid piece per building are enough at building scale, a 30-high tower on a 0.72 slope included. Never `bury` or `encapsulate`. |
| [S7](../research/spikes/s7.md) flattening | The accessor wrap of three slots levels terrain to within ±1 in 100 % of columns, vanilla and Terralith + Tectonic. The planner's unwrapped read is exact. |
| [S1](../research/spikes/s1.md), [S2](../research/spikes/s2.md) probes | With priming, biome lookups are order-independent (TerraBlender needs a per-region anchor). `getBaseHeight` is exact on land (99.05–99.92 %). The coarse screen is not. |
| [S8](../research/spikes/s8.md) veto, [S9](../research/spikes/s9.md) pool element | Both work and are order-independent. The tests only partly cover them (no margin test, flat test world). |

## Options

1. **Integration**: (a) structure starts per district; (b) per-chunk feature
   (no ±8-chunk box, no serial lane, but no `/locate`, beard or boxes);
   (c) post-generation (track 7).
2. **Big cities**: (a) grow cells (a radius-1000 city needs cells about
   5,000 across, so cities become rare and uniform); (b) join cells (keep
   1024, a big city claims its neighbours); either way, maybe (c) a
   two-level plan, if whole-city planning stalls the lane.
3. **City decision**: (a) evaluate at the cell's site (no cache, but 26–41
   cities per window); (b) best of K hashed candidates (73–96 cities, but
   probes per cell, so it needs a cache).
4. **Terrain**: (a) beard only; (b) flatten every city; (c) beard, with
   flattening as an optional tool.

## Decision

1a, 2b (2c pending S6), 3b, 4c. The rules:

### Integration

- **W1 Starts.** Use a custom `Structure` with a `RandomSpreadStructurePlacement`
  subclass.
  - `isPlacementChunk` answers "district origin?" from geometry plus a hashed
    per-cell roll: pure, no probes, no world access, 3–36 ns.
  - `getPotentialStructureChunk` returns the district centre.
  - `findGenerationPoint` repeats the origin test, because `StructureCheck`
    skips the placement. It also reads the cached city decision.
  - `/locate` finds the nearest *district*. A city-centre target needs its
    own mechanism.
- **W2 Suitability per city, never per start.** All districts of a city
  read one decision from the region layer, using broad biome tags. A start
  never fails on its own (no per-start `isValidBiome`): one ocean district
  would cut the road network.
- **W3 One plan per city, built lazily and cached.** The lazy piece builder
  asks for the plan. It is keyed by the owning cell (R1) and obeys the
  pure-cache contract (D6). Each district emits only the pieces in its own
  box. Pieces save references (cell, district, kind, index, baseY; about
  20 B per piece). *Pending S6:* references or stored IR.
- **W4 Step `top_layer_modification`.** With the default step, vegetation
  overwrote 2–4 of our blocks in 3 of 5 orders (S3).
- **W5 Terrain adaptation.**
  - One rigid piece per building (per wing for stepped large buildings),
    with the box bottom at floor level and `terrain_adaptation: beard_thin`.
  - Foundations are our own blocks below the box, written in `postProcess`.
  - Never `bury` or `encapsulate` for surface styles.
  - **Don't reject steep lots for large buildings.** On S5's steep site,
    rejection dropped the hall and most of the houses. Use it only for
    optional small infill. This replaces
    ADR 0002 I2's "reject steep lots".
- **W6 Flattening is an optional tool**, not the default. Use it for plazas,
  level streets and water edges, where the beard falls short (12-block
  kernel, one ground plane per piece). The method is S7's mixin on the
  `NoiseGeneratorSettings.noiseRouter()` and `aquifers()` accessors:
  - Wrap three slots with one mask: `final_density`, `chunk_surface_level`
    and the aquifer `surface_level`. **Never wrap
    `preliminary_surface_level`.**
  - Prefer fill to cut.
  - A depth band is required. A full replacement inside the mask drops caves,
    ores and the beard.
  - Use our own mixin; Lithostitched is not a dependency.
  - The mask may read the region field and the city skeleton (both built
    from unwrapped probes, D4), never lot or building detail.
  - Untested: flooding near sea level, the look under terrain mods, the
    depth band itself.
- **W7 Veto foreign structures.** Two `@Inject(HEAD, cancellable)` mixins share
  one predicate: `ChunkGenerator#tryGenerateStructure` and
  `StructureCheck#canCreateStructure`. The predicate is: structure in tag
  `fractalbuildings:excluded_from_cities`, and the start chunk within the
  city footprint plus a margin.
  - The margin must cover the foreign structure's reach: villages 80, the
    jigsaw cap 128 + 12.
  - Edge distance is exact: distance to the bisectors, or to the radius for
    joined cities. S4 found that F2 − F1 only bounds it.
  - Both hooks are private methods. Re-check their signatures with
    `javap -p` on every Minecraft bump.
  - **The margin is untested** (T3).
- **W8 Pool element for vanilla villages** (S9). This is a showcase and
  compat path, not the city path.
  - Footprint is fixed per element. Content is hashed from (seed, position,
    rotation, w, d), and the supplied `RandomSource` is ignored.
  - It is wired in through a `houses.json` override, which collides with
    other packs that override the same file.
  - Lithostitched (`breaks_seed_parity: true`) changes every village's layout.
  - **A real noise-world pipeline test is still owed** (T2).
- **Fallback:** the per-chunk feature adapter stays the fallback, because the
  same pure plan feeds it. *Pending S6:* whether lane or piece cost forces it.
- Known limits: a district start is made only when its chunk generates, so
  chunks generated before the mod was added stay empty. Only one start per
  `Structure` type per chunk is allowed, so a second layer (walls) needs its
  own type.

### Region field

- **R1 Field.** A 2D hashed Voronoi field: cell size 1024, jitter 0.7.
  - Seeds come from a full 64-bit hash. S4's mutation test caught an `int`
    cast.
  - Biome acts as a multiplier through `c:` tags.
  - Each cell decides from the **best of 8 hashed candidate centres**,
    sampled on a snapped 64-block lattice, with centres at least 0.15 S
    from the cell edge. **This changes the Phase 1 gate's "evaluate once at
    the site, no cache"**: the decision now reads probes, so it is cached
    (D6), but it stays a pure function of the cell.
- **R2 Unbounded cities by joining cells (untested).**
  - A cell's hashed type and radius claim every cell whose site lies within
    that radius. A claimed cell holds no city of its own.
  - Conflicts go to the higher hashed priority, and the loser is dropped, not
    shrunk. The lookup covers about 7 × 7 cells.
  - Open for Phase 5: does a dropped or unsuitable claimant release its
    cells? The rule must stay a pure function over a bounded radius, with no
    chains.
  - Adjacent separate cities meet at hashed gates on shared borders
    (ARCHITECTURE).
- **R3 Fixture:** `fb-region-dump/1` (S4). A recorded 64-block grid replays every
  decision exactly. The live and recorded probes must use **one** height
  predicate: S4 read `getBaseColumn` (first non-air, non-fluid block), while
  production uses `getBaseHeight(OCEAN_FLOOR_WG)`.

### Size limits and cost (pending S6)

Settled now:

- **A district is at most 14 × 14 chunks (224 blocks)**, with its start at
  the centre chunk: ±8 chunks less the 12-block beard margin. 12 × 12 was
  tested. Buildings never cross a district border.
- **The number of districts per city is not limited** by the pipeline.
  Placement is O(1) and stateless.
- A city held in one 1024 cell has a mean radius of about 220. Larger cities
  join cells.

Costs, single thread, measured under load:

| Item | Cost |
|---|---|
| Exact height | 0.32–0.93 ms per column (1.3–3.6 s/km² on a 16-block grid) |
| Primed biome lookup | 9–15 µs (0.6–1.3 s/km² at quart resolution, about 40 ms/km² at 16 blocks) |
| One cell decision | ≤ 400 lattice columns, about 0.1–0.4 s (*derived*, not measured) |

**Pending S6.** The numbers that make these rules concrete:

1. Plan time against city size (512², 1024², 2048², then larger), split into
   screen, exact heights and layout. Find the size at which the first-touch
   stall on the serial lane becomes noticeable. That decides whether the
   two-level plan (2c) is required, and gives 6.5's default maximum city
   size (a default, not a design limit).
2. Per district: start creation, piece build and `postProcess` time, and
   save size, for references against stored IR, with a tall building. That
   settles W3.
3. Plan-cache memory per cell, which sets the LRU bound. Also thread safety,
   and evict/regenerate byte equality **with heights in the plan** (S3's
   plan was 2D only).
4. The track 8 list: lane time per district by part (cold and warm), ns per
   block write, prefetch lead (walking, sprinting, elytra, pregeneration),
   worker contention, and IR memory (compact against object). These settle
   the pooling rule (> 10 % of downtown lane time, or > 5 % inline at elytra
   speed) and the worker count.
5. Whether anything forces the per-chunk fallback.

Also owed, outside S6's brief: a measured cell-decision cost, and `/locate`
over many unsuitable cells (each touches a cold decision).

### Determinism rules

- **D1 Hash everything.** Every value comes from (world seed, position,
  purpose) through a full 64-bit mixer. A piece ignores its `random`
  argument (S9's mutation test caught a mix-in). No shared counters (S3's
  mutation test caught one).
- **D2 Use bit-exact math in the core**: integer math or `StrictMath`.
  Never `Math.sin`/`cos`/`pow`, which may use platform intrinsics. S4 used
  `Math.sin`/`cos`, so the port must change that.
- **D3 Prime biome lookups.** Make a fixed anchor lookup before each query.
  Under TerraBlender with more than one tree:
  - Find the target's region index, then prime with a **per-region anchor**.
  - Derive anchors from (seed, region index) with a fixed scan order, and
    never change them within a world.
  - Detect TerraBlender at runtime through a small adapter.
- **D4 Heights.**
  - `getBaseHeight(OCEAN_FLOOR_WG)` is exact on land, and an upper bound of
    solid ground: keep ±a few blocks for powder snow, carvers and features.
  - Over frozen oceans, add a water-surface check (icebergs reach +99).
  - The coarse screen only culls, with a margin of at least 20. Never use it
    for a placement height, and skip it under Tectonic, where it costs more
    than the exact probe.
  - The plan picks its own floor heights and never re-probes after the beard.
  - Every planner probe reads **unwrapped** terrain, so flattening (W6) has
    no cycle.
- **D5 Ownership by construction.**
  - District boxes partition the plane.
  - Global features (roads) are one list in the plan, and each block is
    drawn by the district that owns it.
  - Pieces clip to `chunkBB` and to their district box.
  - Never assume we own our air volumes (foreign leaves get in).
- **D6 The pure-cache contract** ([recommendation](../research/pool/recommendation.md)).
  - Plan, cell decisions and IR are pure caches, keyed by everything they
    depend on (seed, cell or spec, `genVersion`, config that changes output).
  - Workers may prefetch. The lane joins an in-flight future or computes the
    value itself.
  - **No result depends on cache state.** Eviction is always safe.
  - Use a future per key. Don't run a long build inside
    `ConcurrentHashMap.computeIfAbsent`: a cell decision that reads its
    neighbours' decisions would update the map recursively, which is
    disallowed.
- **D7 Order-independence criterion.** Every write our pieces make (the
  ledger) is checked exactly, plus a reference raster from the global plan,
  solid blocks exact.
  - S3 measured 0 mismatches with W4, so its suggested 0.1 % tolerance is
    dropped.
  - **Whole-chunk hashes are unusable**: vanilla differs in 455–575 of 576
    chunks between orders.
  - Put the test cell away from spawn chunks, and keep the three S3 mutants.

### Permanent regression tests

- **T1 S3 order test** (gate requirement), rebuilt in Phase 3 or 6. It is a
  client game test with a fresh world per order, 7 orders including
  threads plus an evictor, the ledger and the raster (D7), and the mutants.
- **T2 S9 real-pipeline test.** Use a noise world through `createStructures`
  and decoration. Count non-leaf foreign blocks in our boxes. Add a
  Lithostitched matrix, including `breaks_seed_parity: false`.
- **T3 S8 margin test.** A village starting just outside a city, whose box
  reaches in. Plus a smoke test of the `/locate` command.
- **Also:** the S1 priming test over the supported mod sets; S4's field tests
  in `planner-core`, with a quart-resolution priming case (S4 could not catch
  a missing prime); 3.10 (IR identical across cold, warm, evicted and
  prefetched caches); S7's test if W6 is used.

## Consequences

- This ADR replaces parts of ADR 0002: I2's "reject steep lots" and
  "flatten only if S5 fails", and I3's "F2 − F1 margin". It also settles
  0002's open question of growing against joining cells.
  - ARCHITECTURE still needs edits: decide-at-site, the single priming
    lookup, district "~250 blocks", and the coarse-screen height veto.
- What becomes easier: one plan per city feeds structures, the fallback
  feature and the pool element alike. Districts don't coordinate.
- What becomes harder:
  - Cell decisions now read probes, so they need a cache and a cost budget.
  - The join rule is unproven.
  - Uphill cut faces of 5–12 blocks beside big buildings go to John (REVIEW).
  - A `genVersion` change mid-world leaves seams between old and new chunks.
- **Revisit:**
  - If S6 finds that the lane cost per district can't be hidden by prefetch,
    use 1b. That is the strongest case for the feature: no box limit and no
    serial lane.
  - If join rules can't stay local, use 2a with larger cells.
  - If best-of-8 costs too much per cell, use 3a with more, smaller cities.
