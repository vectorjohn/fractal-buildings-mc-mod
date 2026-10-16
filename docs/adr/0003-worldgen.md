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
  - "[[docs/research/spikes/s6]]"
  - "[[docs/research/spikes/s7]]"
  - "[[docs/research/spikes/s8]]"
  - "[[docs/research/spikes/s9]]"
  - "[[docs/research/pool/recommendation]]"
  - "[[docs/research/worldgen/structure-pipeline]]"
---

# 0003. Worldgen: district starts, a two-level plan, and determinism rules

Cities enter the world as one structure start per district. Every start
reads a pure, cached two-level plan, and a 2D hashed region field places the
cities. City size has no design limit.

## Context

[ADR 0002](0002-integration-approach.md) chose what to spike. The Phase 2
spikes answered it:

- [S3](../research/spikes/s3.md): 4 and 9 starts share one plan. The blocks
  they own are identical in 7 orders.
- [S4](../research/spikes/s4.md): deciding at the site loses half the
  cities. Join cells rather than growing them.
- [S5](../research/spikes/s5.md): `beard_thin` is enough at building scale.
- [S7](../research/spikes/s7.md): flattening works.
- [S1](../research/spikes/s1.md) and [S2](../research/spikes/s2.md): the
  probes are deterministic, and accurate where it matters.
- [S8](../research/spikes/s8.md) and [S9](../research/spikes/s9.md): the
  veto and the pool element work.
- [S6](../research/spikes/s6.md): the plan has no ceiling if it is two-level.
  Prefetch hides the serial lane. Pieces must save references.

**S6's numbers come from a shared machine** (loadavg 5–20). Its lane totals
repeat within 3 %, but wall-clock figures vary ±25 %. They need a
quiet-machine re-measure before they are final. S6 also used a stand-in
generator, and real generators are expected to cost 5–20× more.

## Options

1. **Integration.** (a) Structure starts per district. (b) A per-chunk
   feature: no box limit and no serial lane, but no `/locate`, no beard and
   no boxes. (c) Post-generation.
2. **Plan.** (a) One monolithic plan per city. It costs about 0.5 s per
   Mblock²: 0.76 s at 1024², 34 s at 8192². (b) Two levels: a skeleton plus
   lazy district detail.
3. **Big cities.** (a) Grow region cells: a radius-1000 city needs cells
   about 5,000 blocks across. (b) Join 1024 cells.
4. **City decision.** (a) Decide at the cell's site: no cache, but only
   26–41 cities per window. (b) Best of 8 hashed candidates: 73–96 cities,
   but the decision needs a cache.
5. **Terrain.** (a) Beard only. (b) Flatten every city. (c) Beard, with
   flattening as a tool.

## Decision

**1a, 2b, 3b, 4b, 5c.**

### Integration

- **W1 Starts.** A custom `Structure` with a `RandomSpreadStructurePlacement`
  subclass.
  - `isPlacementChunk` decides "district origin?" from geometry plus a hashed
    roll. It makes no probes and takes 3–36 ns.
  - `getPotentialStructureChunk` returns the district centre.
  - `findGenerationPoint` repeats the origin test, because `StructureCheck`
    skips the placement. It then reads the cached city decision.
  - `/locate` finds a district, not the city centre.
- **W2 Suitability is decided per city, never per start.** One cached
  decision covers every district (no per-start `isValidBiome`).
  - It uses the **coarse screen** (12–17 ms per decision). With exact
    heights it costs 140–185 ms, and `/locate` takes 3–34 s instead of
    0.1–0.3 s.
- **W3 Pieces save references and their brief, never IR.**
  - A reference costs 13 B per piece.
  - Stored IR costs 11–24 KB per tower, so a 58-tower downtown start fills
    the 1 MB region slot.
  - A piece carries its brief (about 40–60 B), or the plan entry is pinned
    while a start is live. Without that, a thrashing cache rebuilt a 68 ms
    district plan on every `postProcess` (119 s in total).
- **W4** The structure step is `top_layer_modification`. At the default step,
  trees overwrote our blocks.
- **W5 Terrain adaptation.**
  - One rigid piece per building, or per wing on a slope, with the box bottom
    at floor level and `beard_thin`.
  - Foundations are our own blocks, below the box.
  - Never use `bury` or `encapsulate`.
  - Don't reject steep lots for large buildings: on S5's steep site that
    dropped the hall and most of the houses.
- **W6 Flattening is an optional tool** for plazas, level streets and water
  edges. It uses S7's mixin on the `noiseRouter()` and `aquifers()`
  accessors.
  - Wrap `final_density`, `chunk_surface_level` and the aquifer
    `surface_level` with one mask. Never wrap `preliminary_surface_level`.
  - Prefer fill to cut.
  - A depth band is needed, because full replacement removes caves, ores and
    the beard.
  - The mask reads only the region field and the skeleton.
  - Untested: sea level, how it looks under terrain mods, and the depth band
    itself.
- **W7 Veto.** Two `@Inject(HEAD, cancellable)` mixins share one predicate
  (`tryGenerateStructure`, `StructureCheck.canCreateStructure`). The
  predicate: the structure is in `fractalbuildings:excluded_from_cities`,
  and its start chunk lies inside the city footprint plus a margin.
  - The margin must be at least the structure's reach: villages 80, jigsaw
    128 + 12.
  - The distance to the edge is exact. F2 − F1 only bounds it (S4).
  - The margin is **untested**.
  - The mixins hook private methods: re-check them with `javap -p` on every
    Minecraft bump.
- **W8 Pool element** (S9). Showcase and compat for vanilla villages, not the
  city path.
  - The `houses.json` override collides with other packs.
  - Lithostitched changes every village.
  - The real-pipeline test is still owed.
- **Fallback: not forced.** S6 found no ceiling, so the per-chunk feature
  stays only as a contingency.

### Plan and region field

- **P1 Two-level plan.**
  - The **skeleton** is the coarse screen on a 32-block stride, plus arterial
    roads hashed from (seed, axis, line index).
    - It is computed per tile of at most 4096², which takes 83 ms and 2 B per
      1024 blocks².
    - Tiles agree without coordination. A gate on a cell border comes from a
      hash of the edge key: 0 mismatches over 9,800 edges.
  - **District detail** makes exact probes only at building sites. It costs
    40–100 ms, whatever the city size.
  - A district is computable from the skeleton alone.
- **P2 Prefetch, don't pool.**
  - Four workers start building a district's IR as soon as its start is
    created.
  - At walk, sprint and elytra speed, no IR lookup ran inline (0 of 244).
    The cold downtown lane fell from 6.5 s to 9 ms.
  - A Chunky-style pregeneration ran 12 of 469 lookups inline.
  - **A teleport has no lead** and pays the cold cost. Mitigation, untested:
    build the skeleton shell first.
  - The pooling rule (more than 5 % of lookups inline at elytra, or more than
    10 % of downtown lane time) did not trigger with the stand-in. **Check it
    again with the real generator.**
- **R1 Field.** 2D hashed Voronoi, cell size 1024, jitter 0.7, a full 64-bit
  seed hash, biome as a multiplier through `c:` tags.
  - Each cell decides from the **best of 8 hashed candidate centres** on a
    snapped 64-block lattice. **This changes the Phase 1 gate's "evaluate
    once at the site, with no cache"**: the decision is still pure, but it is
    cached (D6).
- **R2 Big cities join cells (untested).** A cell's hashed type and radius
  claim the cells whose sites lie within that radius. On a conflict the
  higher hashed priority wins, and the loser is dropped.
  - Open for Phase 5: does a dropped claimant release its cells? The rule
    must stay pure and look only within a bounded radius.
- **R3 Fixture.** The fixture format is `fb-region-dump/1`. Live and recorded
  probes must use one height predicate: S4 read `getBaseColumn`.

### Practical size limits

- **City size: none in the design.** The practical limit is chunk
  generation.
  - 4096² is 65k chunks, about 40 min at 25 chunks/s; 8192² is about 3 h.
  - **6.5's default:** unbounded, with a soft warning above 8192².
- **District:** at most 14 × 14 chunks (224 blocks), with the start at the
  centre: ±8 chunks less the 12-block beard. 12 and 16 chunks were tested,
  the 16 without a beard. Buildings never cross a district border.
- **Pieces:** keep a start under about 10k pieces. A district can't reach
  more than about 11k, and the region slot overflows at about 80k
  references.
- **Lane budget:**
  - Writes cost about 100 ns per block, so 3–5 ms per downtown chunk.
  - About 200 exact probes per district, unless prefetched.
  - Everything a start does on first touch must fit the lead: about 26
    blocks of travel, which is 0.8–1 s at elytra speed.
- **Memory:** compact IR, at 2.2 MB per downtown district. An LRU of 64
  downtown districts is about 140 MB, before any typical-floor memo.

### Determinism rules

- **D1 Hash everything.** Every value comes from (seed, position, purpose)
  through a 64-bit mixer. Pieces ignore their `random` argument. No shared
  counters.
- **D2 Core math is bit-exact:** integers or `StrictMath`, never
  `Math.sin`, `cos` or `pow`. S4's port must change this.
- **D3 Biome lookups are primed with a fixed anchor.** Under TerraBlender
  with more than one tree, prime with a per-region anchor. Derive it from
  (seed, region index), keep it fixed for the world, and detect TerraBlender
  at runtime.
- **D4 Heights.**
  - `getBaseHeight(OCEAN_FLOOR_WG)` is exact on land, and an upper bound
    there.
  - Check the water surface over frozen oceans, where icebergs reach +99.
  - The coarse screen is for culling and skeleton use only, with a margin of
    at least 20. Never use it for a floor height.
  - Planner probes read **unwrapped** terrain.
  - The plan never re-probes after the beard.
- **D5 Ownership.**
  - District boxes partition the plane.
  - A road is drawn by the district that owns the block.
  - Pieces clip to `chunkBB` and to their district box.
  - Our air is not ours: foreign leaves get in.
- **D6 The pure-cache contract.** The plan, cell decisions and IR are pure
  caches. Each is keyed by everything it depends on, `genVersion` included.
  - Workers may prefetch. The lane joins the in-flight future, or computes
    the value itself.
  - **No result depends on cache state.** S6 showed this: byte-exact with
    live heights under 122k evictions.
  - Use one future per key. Never run a long build inside
    `computeIfAbsent`.
- **D7 Test criterion: the blocks we own, exactly.** Check our ledger or
  write digest, plus a raster of the global plan with solid blocks exact.
  - Whole-chunk hashes don't work: vanilla differs in 455–575 of 576 chunks.
  - Put the test cell away from spawn.
  - Keep the mutants.

### Permanent regression tests

- **T1** S3's order test (the gate requirement), with S6's evict-and-compare
  and its mutants, in Phase 3 or 6.
- **T2** S9 in a real noise world, with a Lithostitched matrix.
- **T3** S8's margin test, with a village just outside a city, plus a
  `/locate` smoke test.
- **Also:**
  - S1 priming over the supported mod sets.
  - S4's field tests, with a priming case at quart resolution.
  - S6's acceptance numbers as Phase 4 assertions: skeleton under 100 ms,
    detail under 100 ms, constant first-touch.
  - Lane counters behind a flag.
  - 3.10.

## Consequences

- **0003 replaces parts of 0002:** I2's "reject steep lots" and "flatten only
  if S5 fails", I3's "F2 − F1 margin", and the open choice between growing
  and joining cells.
- **ARCHITECTURE needs updates:** deciding at the site, the single priming
  lookup, "~250-block" districts, the estimate of 8 s at 2048² (it is about
  2.5 s), and the coarse-screen height veto.
- **What gets easier:** one plan feeds structures, the fallback and the pool
  element, with no coordination between districts.
- **What gets harder:**
  - Teleporting into a downtown.
  - The join rule is unproven.
  - Coarse-screen cost under Tectonic is unmeasured for suitability and the
    skeleton (S2 put it above an exact probe there). Measure it in Phase 5,
    or fall back to sparse exact probes.
  - Uphill cuts of 5–12 blocks (REVIEW).
  - A `genVersion` change mid-world leaves seams.
- **Revisit:**
  - Use 1b if the real generator outruns 4 workers at elytra speed.
  - Pool components if the pooling rule triggers with the real generator.
  - Use 3a if joins can't stay local.
