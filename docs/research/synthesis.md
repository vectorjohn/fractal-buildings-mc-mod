---
tags: research
track: synthesis
date: "2026-10-15"
author: researcher
licence: none
---

# Landscape synthesis: building generation and worldgen integration

**For:** the Phase 1 gate ([ROADMAP](../ROADMAP.md) 1.8). John picks the
building-generation direction and confirms which integration approach to
spike; both are then recorded as ADRs.

This builds on [INDEX](INDEX.md) and [ARCHITECTURE.md](../ARCHITECTURE.md).
`reviewed` and `deep-dive` notes carry the argument; the two `drop` notes
supply numbers only. Two deep-dives are folded in:
[grammars-deep-dive](pcg/grammars-deep-dive.md) (split grammars and CGA) and
[lithostitched-deep-dive](overlays/lithostitched-deep-dive.md). Evidence tags
(*measured*, *probe*, *read*, *inferred*) mean what they do in
ARCHITECTURE.md. Revision 2, same day.

## The question

There are two choices, and they are nearly independent:

1. **How a building is made from a lot:** by rules (a grammar), by solving
   tile constraints (WFC), or by assembling authored pieces along a graph
   (templates).
2. **How the result gets into the world:** as structures, as a per-chunk
   feature, through a biome overlay, through a custom generator, or after
   worldgen (track 7).

Fixed whatever John picks: a **pure planner** turns (seed, region cell, lot)
into IR, run whole and clipped per chunk. The criteria are determinism, chunk
order, the serial lane and ±8-chunk box (*measured*), the core boundary,
variety with coherence, invariants by construction, and licences.

## Buildings: options compared

|x| A. Shape grammar (split / CGA) | B. WFC / model synthesis, MarkovJunior | C. Graph + templates (jigsaw, Lost Cities parts, RC mazes) |
|---|---|---|---|
| Notes | [split](pcg/split-grammars.md), [CGA](pcg/cga-shape-grammar.md), [deep-dive](pcg/grammars-deep-dive.md) | [wfc](pcg/wfc-model-synthesis.md), [townscaper](pcg/townscaper.md), [markovjunior](pcg/markovjunior.md) | [jigsaw](worldgen/jigsaw.md), [lost-cities](mods/the-lost-cities.md), [repurposed](mods/repurposed-structures.md), [recurrent-complex](mods/recurrent-complex.md) |
| Determinism, chunk order | Each rule is chosen by weighted rendezvous hashing, keyed by the scope that should agree. A building is derived whole, then clipped | Fine within one bounded solve. Solving per chunk depends on generation order | Deterministic |
| Worldgen cost | ms per house (*inferred*) | Unbounded without a retry cap | Lowest |
| Variety with coherence | Scoped draws decide per floor and per bay. A flat rule set turns into noise (Wonka, Fig. 2) | Local only | Coherent within a piece. Variety is capped by the piece count (RS has ~3,500 NBTs and identical houses) |
| Invariants | "In the lot" holds by construction for split/repeat/comp only. Massing and projections need a hull check | None | Come from authoring |
| Core boundary, licences | Pure. Papers: ideas are free (2003–06 patents likely expired, *inferred*) | MIT, pure | NBT needs MC classes or a converter. Other mods' assets are ND or LGPL |
| Authoring | 250 rules and 40 attributes made every building in Wonka's paper | Hundreds of modules (Townscaper) | Thousands of pieces |

## Recommended building direction: volume massing, then a grammar for façades

The pipeline runs in fixed phases. Each phase reads only frozen snapshots of
earlier phases (PGA), so the order of derivation inside a phase can't change
the output ([grammars-deep-dive](pcg/grammars-deep-dive.md)). Every phase
emits roles and is checked against invariants.

1. **Brief and early draws:** style × class × region
   ([style guides](aesthetics/style-guides-westeroscraft-conquest.md)) plus
   inherited flags such as *ruined*
   ([recurrent-complex](mods/recurrent-complex.md); John agreed). Roof form
   and door scheme are drawn first, because parity flows up into the
   massing dimensions.
2. **Massing:** volumes assembled inside the lot hull (main mass plus wings),
   with **one floor grid**. This is the one stage that is **checked** for
   containment rather than contained by construction. CGA doesn't use splits
   for massing either (§8).
3. **Roofs:** a heightfield over the union of volumes; closed by construction
   ([roof-forms](aesthetics/roof-forms-and-massing.md)).
4. **Façades by grammar:**
   - `Comp` turns each exposed face into a 2D mask, then split/repeat lays out
     floors and bays once per face, so windows line up.
   - An **exact integer split** places leftover blocks in mirrored pairs; it
     came out symmetric on 28 of 28 widths (*probe*). Walls 5–9 wide get their
     own rule.
   - Projections go through one hull-checked `project()`.
   - Walls are built in depth layers
     ([depth-and-layering](aesthetics/depth-and-layering.md)).
5. **Interiors:** a room graph ([merrell](pcg/merrell-residential-layouts.md))
   → constrained growth ([lopes](pcg/constrained-growth-floor-plans.md)) with
   partitions on pier lines → a connect pass from the entrance
   ([rooms-and-mazes](pcg/rooms-and-mazes.md)).
6. **Openings and detail:** only on free mask tiles, then bounded rewrite
   passes with a fallback. WFC only inside fixed borders.
7. **Palette:** gradients picked by a weathering field
   ([palettes](aesthetics/palettes-and-gradients.md)).

**Rules are Java classes with JSON parameters, not a language.** CGA++ had to
add shapes as values, events and alternatives to CGA; Java has all three, and
Eger's Minecraft box-split grammar writes rules as functions too. **Templates
shrink to small role-level terminals** (no NBT); the template graph survives
only as the room program.

**Why A:**

- Only A gives determinism per rule and containment for façades.
- B has no global structure: no entrance facing the street, no joined wings,
  no guarantee rooms are reachable.
- C contradicts goal 1.

**City layout** isn't a gate choice. John's [moogs](mods/moogs-structures.md)
comments fit it:

- a city field drives class and height from centre to edge, with a scatter
  layer beyond;
- how often each building type appears is a config setting (6.5).

## Integration: options compared

|x| Size | Chunk order | `/locate`, boxes, beard | Compat | Verdict |
|---|---|---|---|---|---|
| **Structures, one start per district** ([pipeline](worldgen/structure-pipeline.md), [extension-points](worldgen/extension-points.md)) | ±8 chunks per start; one start per structure per chunk | Pure starts; clip to `chunkBB`; one owner per block | All free; the beard grades custom pieces (*read*) | Any generator that uses vanilla `createStructures` | **Adopt; S3 decides** |
| **Per-chunk feature** ([lost-cities](mods/the-lost-cities.md)) | Unbounded | Each chunk writes its share of a pure plan | None; vanilla structures overlap | Good (*inferred*) | **Fallback** if S3 or S6 fail |
| Biome overlay ([terrablender](overlays/terrablender.md)) | — | Fine | n/a | No Fabric overworld API; replaces the biomes a city should span #review @assistant why? how does it replace, seems like an overlay by definition is additive. -- @John | Drop |
| Custom generator ([custom-generator](worldgen/custom-generator-and-pool-element.md)) | Unbounded | Fine | Empty-router `RandomState` without a mixin (*read*) | Clashes with presets | Drop |
| Post-generation, track 7 ([overview](postgen/overview.md)) | Unbounded | Plan must come from noise; timing depends on players | None | DH shows unsaved LODs | **Backup / add-on**, ~40 chunks/s |

## Recommended integration: structure-based, district starts over a region plan

- **Region cells**
  ([lithostitched-deep-dive](overlays/lithostitched-deep-dive.md)):
  - a **2D** warped jittered grid (3D Voronoi sliced at y = 0 left 17%
    slivers, *probe*);
  - a **full 64-bit** seed hash;
  - decisions evaluated **once at the cell's site**, so every chunk agrees
    without a cache;
  - the **F2 − F1 edge distance** for the mask fade and the veto margin.

  The city plan is built whole per cell, lazily in the piece builder, and
  cached; it must come back byte-identical after eviction
  ([greuter](pcg/greuter-pseudo-infinite-cities.md)).
- **District starts on a fixed sub-grid**, so "is this a district origin?" is
  cheap to answer from position, for placement and for `/locate` on other
  threads. Starts per building would put the whole plan into the placement
  test (*inferred*).
- **What a district owns:**
  - one rigid piece per building, box bottom at floor level, which
    `beard_thin` grades;
  - the road and wall segments inside its box. City-wide features are cut at
    district borders, so ±8 chunks limits a piece, not a feature. A district
    is ~250 blocks across.
- **Pieces save references** (cell, building id); `postProcess` regenerates
  them from the plan.
  - Risk (*inferred*): a config or version change mid-world leaves seams.
  - Rendezvous choice limits a newly added rule to the buildings where it
    wins.
  - S6 weighs this against storing the IR in each piece.
- **Cost:** a coarse screen from `preliminary_surface_level` (+15 bias) plus
  exact heights per lot. About 0.5 s per city at the in-game 0.43–0.51 ms per
  column, against about 25 s to generate its ~1,000 chunks. The plan can be
  warmed off the lane.
- **Terrain, adapt first:** reject steep lots
  ([jeb-gen](gdmc/jeb-gen-2023.md)), step foundations, beard. Fill-first
  flattening (S7) only if S5 fails.
- **No dependencies:**
  - our own two-site veto mixin (S8);
  - we **publish `fractalbuildings:settlement` as a 2D density-function
    type**, so Lithostitched packs can gate structures or inject biomes on our
    cities. Lithostitched itself brings ~80 required mixins, a global jigsaw
    replacement, and fixes only a week old in exactly the hooks we'd use.

  The same plan feeds the feature and post-generation adapters, so a fallback
  costs an adapter, not a redesign.

## Where the notes disagreed (resolved)

1. **Wrapped `getBaseHeight`:** Lithostitched replaces the router in the
   shared settings, so `getBaseHeight` *does* see wrapped terrain. It does
   **not** keep the original (*read*). The resolution:
   - the mask depends on the region field only, never on the lot plan, so
     there is no cycle;
   - the plan reads the unwrapped coarse screen, or exact heights from the
     original `final_density`, captured before wrapping and compiled through
     `RandomState.getSampler` (*read*, not run);
   - pieces take their heights from the plan.
2. **Flattening slots:** wrap router `final_density`, router
   `chunk_surface_level` and aquifer `surface_level`. Never wrap the shared
   `preliminary_surface_level` entry: those consumers are its only readers,
   so leaving it alone keeps it a clean coarse screen. The Lithostitched route
   needs 2.0.6+ (`wrap_aquifers`). ARCHITECTURE.md and
   [surface-level](worldgen/surface-level-and-flattening.md) are now
   corrected.
3. **Also settled:**
   - the custom generator row is fixed;
   - budgets use the in-game height cost.

   Still open: Tectonic's 26.3 modifiers, which S7 runs under anyway.

## What the spikes must confirm (Phase 2)

| Spike | Must show | Decides |
|---|---|---|
| S3 | Shared-plan district starts give the same per-chunk hashes in two generation orders, with no seams. **Add:** a road crossing a district border, and `/locate` through a `RandomSpread` subclass | **The hinge:** structures vs feature |
| S6 | Save size of reference pieces vs stored IR; lane time per city; thread safety of the cache. **Add:** evict, regenerate and compare bytes | City size, piece granularity |
| S5 | `beard_thin` on per-building pieces, on hill and river terrain; the step left at the box edge | Whether S7 is needed |
| S7 | Fill first; three slots wrapped; mask from the field (F2 − F1 fade); **under Terralith + Tectonic** | Terrain hook |
| S8 | No foreign start at either call site; `/locate` agrees; margin from the field | Exclusion |
| S4 | 2D field: city sizes, gradient from centre to edge, biome-crossing cities | Placement |
| S1, S2 | Mostly answered: biomes over 512² in ~40 ms (*probe*); height 98.2%, p99 2 blocks (*measured*, 1 seed). **Shrink** to 10 seeds plus terrain mods | Plan inputs |
| S9 | A showcase. **Add a compat check:** with Lithostitched installed, its global jigsaw redirect (`breaks_seed_parity`, default true) runs our element and can change villages | — |

The critical path is S3 → S6 and S5 → S7. The building direction is tested
by the Phase 3 gallery and the 3.11 diversity score, starting with the
`IntSplit` and `Choice` property tests.

## The case against, and what would change it

- **Templates:** T&T (18M downloads) and CTOV are all templates, and a
  hand-built house passes "would a person build this" most easily. **If**
  Phase 4 galleries fail that review while diversity is fine, shift weight to
  authored modules over an occupancy grid ([townscaper](pcg/townscaper.md)).
- **Occupancy-cell massing** gives more organic forms. **If** 3.11 shows
  sameness in massing, switch; it outputs the same voxel union.
- **A data rule format:** **if** style work stalls on recompiles or
  third-party style packs become a goal, add JSON over the same operators,
  still with no parser.
- **Per-chunk feature:** no size limit, no coordination, order-independent by
  construction, and it runs on the same lane. **If** S3's seams can't be
  fixed by ownership, or S6 finds pieces too heavy, switch adapters.
- **Lithostitched as a dependency:** players of Tectonic and Terralith
  already have it. **If** S7 needs more than three hooks (blending, the
  beardifier), or our density function proves too little for pack makers,
  reconsider. Post-generation is promoted only if both base paths fail.

## Decisions for John at the gate (recommended answer in bold)

- **B1 Building direction:** **massing by volume assembly inside the lot hull
  (with a containment check) and one floor grid per building; a
  split/repeat/comp grammar for floors, bays and façades; roofs as a
  heightfield; interiors from a room graph.** Not led by WFC or templates.
- **B2 Rules:** **a typed Java API (exact integer split, rendezvous choice,
  phase snapshots) with styles, palettes and briefs as JSON parameters**; no
  grammar language.
- **B3 Authored content:** **small role-level terminals only, no NBT.**
- **B4 Phase 4 interiors:** **rooms, doors and stairs, reachable by
  construction; furniture as hooks.**
- **I1 Approach to spike:** **structure-based, one start per district.** The
  per-chunk feature is the fallback. Stop the overlay and custom-generator
  work.
- **I2 Terrain:** **adapt first; fill-only flattening only if S5 fails;** no
  terraces.
- **I3 Foreign structures:** **veto surface structures in cities with our own
  mixin**, with the margin taken from the field's edge distance.
- **I4 Dependencies and compat:** **none required; expose
  `fractalbuildings:settlement` as a 2D density-function type** instead of
  shipping Lithostitched modifiers.
- **I5 Track 7:** **a backup; park batch 2 until S3 and S6 report.**
- **I6 Scale in v1:** **cities up to ~512 × 512 blocks** until S6 says
  otherwise. @assistant 512 is too small. Is S6 deciding if we can go bigger? why that limit? -- @John
- **Spikes:** **add the S3, S6 and S9 extras above; shrink S1 and S2.**

> **success** Success
> This direction looks good to me. I have some notes and questions, but I wouldn’t change this at this moment. I give the go-ahead. -- @John

ADRs: 0001 covers the building direction (B1–B4), and 0002 the integration
approach to spike (I1–I6). The worldgen ADR (2.10) finalises 0002 after the
spikes.
