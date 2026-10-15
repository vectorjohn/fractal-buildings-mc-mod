# Landscape synthesis: building generation and worldgen integration

**Date:** 2026-10-15 · **Author:** researcher · **For:** the Phase 1 gate
([ROADMAP](../ROADMAP.md) 1.8). John picks the building-generation direction
and confirms the integration approach to spike, and both are recorded as ADRs.

This builds on [INDEX](INDEX.md) and [ARCHITECTURE.md](../ARCHITECTURE.md).
`reviewed` and `deep-dive` notes carry the argument; the two `drop` notes
supply numbers only. Three deep-dives are in flight
([split grammars](pcg/split-grammars.md), [CGA](pcg/cga-shape-grammar.md),
[Lithostitched](overlays/lithostitched.md)). **[deep-dive]** marks where they
could change a conclusion. Evidence tags (*measured*, *probe*, *read*,
*inferred*) are as in ARCHITECTURE.md.

## The question

There are two nearly independent choices:

1. How a building is made from a lot: by rules (a grammar), by tile
   constraint solving (WFC), or from authored pieces put together along a
   graph (templates).
2. How the result gets into the world: as structures, as a per-chunk
   feature, as a biome overlay, through a custom generator, or after worldgen
   (track 7).

One thing is fixed whichever way they go: a **pure planner** turns (seed,
region cell, lot) into IR, runs whole per building or per city, and the
result is clipped per chunk. Options are judged on these constraints:

- determinism
- chunk-order independence
- the serial `worldgen` lane and the ±8-chunk box (both *measured*)
- `planner-core` never importing Minecraft
- variety with coherence
- invariants that hold by construction
- licences

## Buildings: options compared

| | A. Shape grammar (split / CGA) | B. WFC / model synthesis, MarkovJunior | C. Graph + templates (jigsaw, Lost Cities parts, RC mazes) |
|---|---|---|---|
| Notes | [split](pcg/split-grammars.md), [CGA](pcg/cga-shape-grammar.md) | [wfc](pcg/wfc-model-synthesis.md), [townscaper](pcg/townscaper.md), [markovjunior](pcg/markovjunior.md) | [jigsaw](worldgen/jigsaw.md), [lost-cities](mods/the-lost-cities.md), [repurposed](mods/repurposed-structures.md), [recurrent-complex](mods/recurrent-complex.md) |
| Determinism, chunk order | One hashed draw per rule per building; built whole, then clipped | Fine for one bounded solve; per-chunk solves depend on generation order | Deterministic |
| Worldgen cost | ms per house | Unbounded without a retry cap | Lowest |
| Variety with coherence | A control grammar decides per floor and bay; flat rule sets become noise (Wonka Fig. 2) | Local only; nothing beyond the tile radius | Coherent within a piece; variety capped by piece count (RS ~3,500 NBTs, identical houses) |
| Invariants | In lot by construction; no interiors | None; must be added on top | From authoring |
| Core boundary, licences | Pure; papers, ideas free (CGA patents unchecked) | MIT, pure | NBT needs MC classes or a converter; others' assets are ND/LGPL |
| Authoring | 250 rules covered Wonka's whole paper | Hundreds of modules (Townscaper) | Thousands of pieces |

## Recommended building direction: a layered rule pipeline, A at the core

Each stage emits material roles and is checked by invariants. These are
ARCHITECTURE.md's "Planner pipeline (hypothesis)" stages, now weighted:

1. **Brief:** style × class × region
   ([style guides](aesthetics/style-guides-westeroscraft-conquest.md)) plus
   inherited flags such as *ruined* or *wealthy*
   ([recurrent-complex](mods/recurrent-complex.md), which John agreed with).
   These, with the split grammar's scoped attributes, are where coherence
   comes from.
2. **Massing:** a **typed Java rule API** in the split/CGA style (split,
   repeat, component; children inside parents; one floor grid; occlusion where
   wings meet; entrance on the street edge). Not a CGA-like language.
3. **Occupancy cells → neighbour-derived modules**
   ([townscaper](pcg/townscaper.md)). This is where authored modules go if
   rules lack charm.
4. **Roofs:** a pitch profile over L∞ distance, max over wings; closed by
   construction ([roof-forms](aesthetics/roof-forms-and-massing.md)).
5. **Façades as depth layers**
   ([depth-and-layering](aesthetics/depth-and-layering.md)).
6. **Interiors:** a room-program graph
   ([merrell](pcg/merrell-residential-layouts.md)), then constrained growth
   ([lopes](pcg/constrained-growth-floor-plans.md)), then a connect pass from
   the entrance ([rooms-and-mazes](pcg/rooms-and-mazes.md)). Rooms are
   reachable by construction.
7. **Detail:** bounded rewrite passes with a known-good fallback; WFC only
   inside pinned borders.
8. **Palette:** a role → gradient of 3–5 blocks, picked by a deterministic
   weathering field ([palettes](aesthetics/palettes-and-gradients.md)).

**Templates shrink to small role-level terminals** (door surrounds, window
kinds, chimney caps) in code or JSON, with no NBT.

**Why A:** only A gives "in the lot" and per-rule determinism for free. "Would
a person build this" needs global structure (street-facing entrance, joined
wings, reachable rooms), which B lacks. C contradicts goal 1. C's graph
survives as the room program and its pieces as terminals.

**City layout** is not a gate choice; ARCHITECTURE.md's per-cell pipeline
stands. John's comments on [moogs](mods/moogs-structures.md) fit it:

- A scalar city field
  ([lost-cities-placement](overlays/lost-cities-city-placement.md)) drives
  class and height from the centre to the edge, with a scatter layer beyond
  the threshold.
- Frequency per building type goes into config (6.5).

## Integration: options compared

| | Size | Chunk order | `/locate`, boxes, beard | Compatibility | Verdict |
|---|---|---|---|---|---|
| **Structures, one start per district** ([pipeline](worldgen/structure-pipeline.md), [extension-points](worldgen/extension-points.md)) | ±8 chunks per start; one start per structure per chunk | Pure starts, clip to `chunkBB`, one owner per block | All free; the beard grades custom pieces as rigid boxes (*read*) | Any generator using vanilla `createStructures` | **Adopt; S3 decides** |
| **Per-chunk feature** ([lost-cities](mods/the-lost-cities.md)) | Unbounded | Each chunk writes its share of a pure plan | None; vanilla structures overlap it | Good (*inferred*) | **Fallback** if S3/S6 fail |
| Biome overlay ([terrablender](overlays/terrablender.md)) | — | Fine | n/a | No Fabric overworld API; replaces biomes a city should span | Drop |
| Custom generator ([custom-generator](worldgen/custom-generator-and-pool-element.md)) | Unbounded | Fine | Empty-router `RandomState` without a mixin (*read*) | Preset clashes; Lithostitched packs stop applying | Drop |
| Post-generation, track 7 ([overview](postgen/overview.md)) | Unbounded | Needs a plan read from noise; timing depends on players | None | DH shows unsaved LODs | **Backup / add-on**, ~40 chunks/s |

## Recommended integration: structure-based, district starts over a region plan

- **Region cells:** a jittered grid seeded by hash. The city field is
  multiplied by biome and vetoed by height. The plan is built whole per cell,
  lazily in the piece builder, cached, and byte-identical after eviction
  ([greuter](pcg/greuter-pseudo-infinite-cities.md)).
- **District starts on a fixed sub-grid:** "district origin?" is cheap from
  position, for placement and `/locate` on other threads. Per-building starts
  would put the full plan into the placement test (*inferred*).
- **What a district owns:** one rigid piece per building (box bottom at floor
  level, so `beard_thin` grades it), plus the road and wall segments in its
  box. City-wide features are cut at district borders, so ±8 chunks limits a
  piece, not a feature. A district is about 250 blocks across inside the
  12-block beard margin.
- **Pieces save references** (cell, building id), and `postProcess`
  regenerates content from the plan. Risk (*inferred*): a config or version
  change mid-world changes the plan and leaves seams. S6 weighs this against
  storing the IR.
- **Planning cost:** a coarse screen from `preliminary_surface_level` (+15
  bias), with `getBaseHeight` per lot only. 1,024 columns at the in-game
  0.43–0.51 ms take ~0.5 s once per city, against ~25 s to generate its
  ~1,000 chunks. A pure plan can be warmed off the lane.
- **Terrain: adapt first.** Reject steep lots
  ([jeb-gen](gdmc/jeb-gen-2023.md)), step the foundations, use the beard. Add
  fill-first flattening (S7) only if S5 fails.
- **No hard dependencies:** the veto is our own two-site mixin (S8)
  **[deep-dive]**. The same plan feeds the feature and post-generation
  adapters, so a fallback costs an adapter, not a redesign.

## Where the notes disagreed

1. **Custom generator as "the Lost Cities model":** fixed in ARCHITECTURE.md.
2. **Wrapped `getBaseHeight`, wanted or a cycle?** Proposed:
   - The planner reads unwrapped density.
   - The flattening mask depends on the region field only (membership, plus a
     target level from the coarse screen), never on the lot plan. That means
     no cycle, and no planner on terrain workers.
   - Pieces take heights from the plan.
   - Vanilla and other mods seeing the flattened heights is wanted.
3. **Which slots to wrap:** [surface-level](worldgen/surface-level-and-flattening.md)
   supersedes the others: `final_density`, `chunk_surface_level` and the
   aquifer `surface_level`. Wrap them at the router slots, not the
   `preliminary_surface_level` entry, which Terralith and Tectonic replace.
   This is unchecked, so S7 runs under both.
4. **Height budget:** use the in-game 0.43–0.51 ms per column; the 0.2 ms
   probe figure is a best case.
5. **Tectonic's hooks:** three Lithostitched modifiers, not
   `wrap_noise_router` ([tectonic](overlays/tectonic.md)) **[deep-dive]**.

## What the spikes must confirm (Phase 2)

| Spike | Must show | Decides |
|---|---|---|
| S3 | Shared-plan district starts give the same per-chunk hashes in two generation orders, with no seams. **Add:** a road crossing a district border, and `/locate` through a `RandomSpread` subclass | **The hinge:** structures vs feature |
| S6 | Save size, reference pieces vs stored IR; lane time per city; cache thread safety. **Add:** an evict-and-regenerate byte compare | City size, piece granularity |
| S5 | `beard_thin` on per-building pieces, on hill and river terrain; the step left at the box edge | Whether S7 is needed |
| S7 | Fill first, three slots, a mask from the region field, **under Terralith + Tectonic** | Terrain hook |
| S8 | No foreign start at either call site; `/locate` agrees | Exclusion |
| S4 | City sizes, the centre-to-edge gradient, biome-crossing cities | Placement |
| S1, S2 | Mostly answered (biomes over 512² in ~40 ms, *probe*; height 98.2%, p99 2 blocks, *measured*, 1 seed). **Shrink** to 10 seeds plus terrain mods | Plan inputs |
| S9 | A showcase; lowest priority | — |

The critical path is S3 → S6 and S5 → S7. The building direction is tested
by the Phase 3 gallery and the 3.11 diversity score, not by a spike.

## The case against, and what would change it

- **Templates:** T&T (18M downloads) and CTOV are all templates, and a
  hand-built house passes "would a person build this" most easily. **If**
  Phase 4 galleries fail that review while diversity is fine, shift weight to
  authored modules at stage 3. That is a shift, not a restart.
- **WFC:** rich detail from few examples, and native to voxels. **If** its
  bounded passes rarely fall back and look better than rewrite rules, widen
  its role.
- **[deep-dive]** If integer rounding makes small splits vanish or double
  (inferred, untested), massing moves to the occupancy stage and the grammar
  keeps façades only. If a data-driven rule format proves cheap, revisit B2.
- **Per-chunk feature:** no size limit and no coordination; order-independent
  by construction; proven by Lost Cities; same serial lane, same cost. **If**
  S3's seams can't be fixed by ownership, or S6 finds pieces or saves too
  heavy, switch adapters and add our own locate command.
- **Post-generation:** no limits, but throughput-bound and late for fast
  players. Promote it **only if** both other paths fail, or for an admin
  "build the capital" command.

## Decisions for John at the gate (recommended answer in bold)

- **B1 Building direction:** **the layered rule pipeline**, led by the
  grammar. Not led by WFC or by templates.
- **B2 Rules:** **a typed Java API, with styles, palettes and briefs as
  JSON**; no grammar language yet **[deep-dive]**.
- **B3 Authored content:** **small role-level terminals only, no NBT.**
- **B4 Phase 4 interiors:** **rooms, doors and stairs, reachable by
  construction; furniture as hooks.**
- **I1 Spike approach:** **structure-based, one start per district.** The
  per-chunk feature is the fallback; stop work on the overlay and the custom
  generator.
- **I2 Terrain:** **adapt first; fill-only flattening only if S5 fails;** no
  terraces.
- **I3 Foreign structures:** **veto surface structures in cities**; found
  cities on village sites later.
- **I4 Dependencies:** **none required;** Lithostitched compat as optional
  data **[deep-dive]**.
- **I5 Track 7:** **a backup; park batch 2 until S3 and S6 report.**
- **I6 v1 scale:** **cities up to ~512 × 512 blocks** (4–9 districts) until
  S6 has measured.
- **Spikes:** **add the S3 and S6 extras; shrink S1 and S2.**

ADRs: 0001 for the building direction (B1–B4) and 0002 for the integration
approach to spike (I1–I6). The worldgen ADR (2.10) finalises 0002 after the
spikes.
