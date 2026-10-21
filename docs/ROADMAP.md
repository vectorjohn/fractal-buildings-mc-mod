# Roadmap: procedural buildings and cities

A Fabric mod that generates varied, believable buildings and cities from rules
rather than stamped templates. Research and a three.js review loop come first;
worldgen integration is proven with spikes before any architecture is locked
in. (Converted on 2026-10-07 from John's "Procedural Buildings Mod — Roadmap"
doc, kept in [archive/original-roadmap.md](archive/original-roadmap.md) with
the setup prompt. The design sketch and worldgen primer are in
[ARCHITECTURE.md](ARCHITECTURE.md). A diagram of the phases, tracks,
dependencies and gates is in [PROCESS.md](PROCESS.md).)

Target: Minecraft 26.3 (Java edition), Fabric, Java 25. Check exact
Loader/Fabric API/Loom versions at https://fabricmc.net/develop/ in Phase 0.

**Goals**

- Every building is generated, so no two look alike, while staying recognisably
  part of a style.
- Cities can span biome boundaries. Each district picks its style from the
  biomes underneath: desert towns, forest towns, towns that change across a
  border.
- Everything is deterministic from the world seed, and chunks still generate
  independently and in any order.
- Every generator output can be reviewed outside Minecraft in a three.js viewer,
  by John and by agents.

**Principles**

- **Pure core.** Planning code never imports Minecraft. Minecraft is an adapter
  at the edge.
- **Abstract materials.** Generators emit roles (wall, trim, roof, floor).
  Palettes map roles to blocks per style or biome.
- **Research, then spikes, then architecture.** Each decision is recorded as a
  short ADR in `docs/adr/`.
- **Review gates.** Each phase ends with something John looks at and approves
  before the next one starts.
- **Parallel by default.** Items are split so several agents can work at once
  (CLAUDE.md, "Parallel work"). Each phase lists what can run side by side.

Each phase section has an `Updated: YYYY-MM-DD` line under its heading:
whoever edits a phase section updates it, so John can see what changed.

## Status

Owned by the coordinator. Item checkboxes below are ticked by the coder of each
item. Per-item work logs are in [log/](log/); run ledgers in [runs/](runs/).

| Phase | State | Gate |
|---|---|---|
| 0. Scaffolding | done 2026-10-08; gate passed 2026-10-14 (John) | builds green; viewer renders the box |
| 1. Prior art research | done; gate passed 2026-10-16 (John signed off in synthesis.md; wants effectively unbounded cities) | John picks building-generation direction and the integration approach to spike |
| 2. Worldgen spikes | done; gate passed 2026-10-16 (John accepted ADR 0003; S6 figures being re-measured) | worldgen ADR (approach, size limits, determinism rules) |
| 3. Planner core and three.js viewer | done 2026-10-16; gate passed 2026-10-18 (John, REVIEW 3.12: "These all look good"; follow-ups: top view tilted, Phase 4 4.2 wings/jetties) | John reviews seed gallery and invariant report |
| 4. Building generator depth | done 2026-10-19; gate passed 2026-10-21 (John, phase4 README: "consider this gate passed and move on"; feedback → 5.11, 5.12) | per building type, "would I believe a person built this?" |
| 5. Settlements and cities | in progress (run 2026-10-21-phase5) | cities in ≥5 biome mixes, one straddling a border |
| 6. Minecraft integration and release | not started | fresh world on three seeds plays well for an hour; first release |

Unscheduled ideas: add them to the Polish backlog or a new phase item via the
assistant.

---

## Testing strategy (applies to every phase)

Each layer has its own test, and most checks run without starting Minecraft.
Each feature lands with tests at the cheapest layer that can catch its bugs.
Commands are in CLAUDE.md, "Build and test commands".

| Layer | Checked by | Runs | Parallel? |
|---|---|---|---|
| 1. Planner rules | JUnit + property tests over thousands of seeds (`planner-core`) | every build, seconds | yes |
| 2. Determinism | byte-identical JSON per seed; chunk-order-independent block hashes (from S3) | every build | yes |
| 3. Look | seed galleries rendered by the viewer via Playwright; agents open the PNGs and compare before/after | on change; reviewed by agents and John | yes |
| 4. Placement in world | Fabric server game tests | every mod build | yes |
| 5. In-game appearance | Fabric client game tests with fixed-seed screenshots (Xvfb + lavapipe) | before each review gate, and on worldgen/rendering changes | **heavy lock** |

Notes:
- Screenshots are compared coarsely (invariant overlay, bounding boxes, colour
  fractions), never pixel-exact: lavapipe output drifts.
- Property tests must be able to fail: each new rule lands with a test that a
  deliberately broken rule fails.
- Shared test infrastructure (synthetic probes, terrain dumps, seed lists,
  gallery presets) is listed here as it's added.
- Added in Phase 5 (T1): gzipped terrain dumps in `fixtures/terrain/` (see its README) made by
  `TerrainDumpTool` (`mod/src/test`, run with `-Dfb.dump=...`, no server); `RecordedProbe`
  reads gzip.
- Added in Phase 3: `SampleScenes.house()` + `viewer/fixtures/sample-house.json`;
  recorded terrain `fixtures/terrain/s4-12345-crop32.json` and `RecordedProbe`;
  `ProbeSpecs` strings (`flat:`, `plane:`, `hill:`, `noise:`, `recorded:`)
  shared by the CLI and tests; `Invariants` + `InvariantReport`, the violations
  fixture and the viewer overlay; golden digests and the determinism lint;
  `fractal-cli check|bench|diversity` over the default 128-cell matrix;
  `cli batch` and the gallery presets (`npm run gallery`, not part of
  `scripts/test-all.sh`: run it on generator or viewer changes).
- Added in Phase 4: `TestMassings` fixtures (incl. `tall50`) dumped by
  `FixtureDumpTest` (`-Dfb.dumpFixtures=DIR`) and `RoofPassTest`
  (`-Dfb.dumpRoofForms=DIR`); `V1Case` + `V1PropertyTest` over each type's
  `TypeLimits.testLots` (turned by `TypeLimits.oriented`, gentle terrains for
  `maxGrade` > 3); golden digests per type in `golden/v1/*.txt`; the
  `entrance-reachable` invariant; replayable failing cases (`ReplayTest`);
  `check`/`bench --types T` and `diversity --type T`; the shared block-id list
  `vanilla-26.3-blocks.txt` for style tests; gallery presets one file per
  type/style (`viewer/gallery-presets/`) with the `cutaway` camera. Gate pack
  numbers (168,000 cases, per-type timing and diversity): `docs/review/phase4/`.

---

## Phase 0: Scaffolding (done 2026-10-08; gate: awaiting John)

Updated: 2026-10-08

Done in run `docs/runs/2026-10-08-phase0-research.md`; full text in
[archive/roadmap-phase-0.md](archive/roadmap-phase-0.md). Gradle multi-project
(`planner-core` with the `checkNoMinecraft` boundary check, `cli`, `mod` on
Loom 1.18.3 / MC 26.3 / Fabric API 0.162.0+26.3), `viewer/` (Vite + Vitest +
strict TS + three.js), research and ADR templates, a CLI → IR JSON → viewer →
Playwright smoke path, server and client game tests (lavapipe), and
`scripts/test-all.sh` covering all five layers. Known issues: see Polish
backlog (0.1a, 0.1b, 0.4, 0.5, 0.6 lines).

**Gate:** builds green, and the viewer renders the box.

## Phase 1: Prior art research

Updated: 2026-10-16

Eight tracks run in parallel. Every alternative examined produces a one-page
overview the moment it is done, so John can dig into options while research
continues. Nothing waits for a final report. Process: coordinator.md,
"Research".

- One note per project, paper or technique, in
  `docs/research/<track>/<slug>.md`, at most a page.
- The coordinator adds a line to `INDEX.md` as each note lands and merges it to
  `main` promptly.
- John marks a note `deep-dive` to have the track spend more time on it, or
  `drop` to stop.

| Track | Questions | Starting list |
|---|---|---|
| 1. Minecraft mods | How do existing mods make large or varied settlements? Where do they hit limits? | [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities), YUNG's structure mods, ChoiceTheorem's Overhauled Village, Towns and Towers, Repurposed Structures, Moog's structures, Recurrent Complex, [Astruct](https://www.curseforge.com/minecraft/mc-mods/astruct) |
| 2. GDMC | What settlement generators exist that adapt to real terrain? | [GDMC-HTTP](https://modrinth.com/mod/gdmc-http-interface) and GDPC, competition entries and write-ups |
| 3. PCG literature | Which building and city techniques give variety with coherence? | Parish and Müller (city layout), CGA shape grammars, split grammars, WFC and model synthesis, Townscaper, Watabou's city generator, residential floor-plan generation |
| 4. Worldgen internals | How do 26.x structure, placement, biome-source and terrain-height APIs actually work? | Decompiled sources in the dev env, Fabric worldgen APIs, [jigsaw](https://minecraft.wiki/w/Jigsaw_structure) and [worldgen](https://minecraft.wiki/w/World_generation) wiki pages; confirm the hypotheses in ARCHITECTURE.md "Worldgen primer" |
| 5. Region overlays | How have mods layered new regions over vanilla biomes? | TerraBlender, Lithostitched, Terralith, The Lost Cities' city/biome handling |
| 6. Aesthetics | What makes a Minecraft build read as real? | Builder guides on depth, palettes, gradients and roof pitch; vanilla village variants as a baseline |
| 7. Out-of-worldgen generation (**backup option**) | Could we pregenerate chunks far out, then plan cities on the finished terrain and write them in, free of worldgen threading and size limits? At what cost? | Distant Horizons, Chunky and other pregenerators, C2ME (far-chunk generation speed), mods that place structures into already-generated chunks, chunk tickets and forced loading, save/lighting/neighbour-update effects of writing into finished chunks, coexistence with the structure-based approach |
| 8. Precomputed building pools (added 2026-10-16; runs alongside Phase 2) | Can the generic part of building generation be computed off the serial worldgen lane (at first launch or in worker threads) into a pool that worldgen draws from deterministically? What does that cost in variety, memory and determinism, and what does it buy over prefetching the exact per-cell plan? | John's proposal (2026-10-16); vanilla stronghold-ring precompute and template cache; Lost Cities 26.x planning service and persisted cache; S6 cost figures |

Agents: `assistant` for tracks 1, 2, 3, 5, 6 (web research); `researcher`
(high effort) for track 4 (source reading in its own worktree, may write tiny
probe tests), for notes marked `deep-dive`, and for the synthesis; `assistant`
for track 7, which John wants kept as a backup to structure-based generation;
`researcher` for track 8.

- [x] **1.1 Track 1: Minecraft mods** (batches until the list is covered)
- [x] **1.2 Track 2: GDMC**
- [x] **1.3 Track 3: PCG literature**
- [x] **1.4 Track 4: Worldgen internals** (needs 0.5)
- [x] **1.5 Track 5: Region overlays**
- [x] **1.6 Track 6: Aesthetics**
- [ ] **1.9 Track 7: Out-of-worldgen generation** (backup option, added
  2026-10-15): generate far chunks ahead of the player, as Distant Horizons
  and pregenerators do, then plan and place cities on the finished terrain.
  Notes in `docs/research/postgen/`.
- [ ] **1.10 Track 8: Precomputed building pools** (added 2026-10-16, runs in
  parallel with Phase 2): John's idea of pre-generating a deterministic pool of
  candidate buildings off the worldgen lane, drawn from by a pure choose
  function; the pool must be a pure cache, never a source of truth. Notes in
  `docs/research/pool/`; feeds S6 (what to measure) and Phase 3 (a generator
  interface that keeps "pool as cache" possible).
- [ ] **1.7 Checkpoint: early skim**, after the first 8–10 notes: John redirects
  tracks or flags deep-dives. (Research keeps going meanwhile.)
- [x] **1.8 Landscape synthesis**: a two-page comparison of approaches for
  buildings (grammar vs WFC vs graph-plus-templates hybrid) and for worldgen
  integration (structure-based vs biome overlay vs custom chunk generator; see
  ARCHITECTURE.md; plus track 7's out-of-worldgen generation as the backup),
  with a recommended direction. `docs/research/synthesis.md`.
  *Done 2026-10-15:* recommends a grammar-led layered pipeline and structure-based district starts (per-chunk feature as fallback); gate decisions B1–B4, I1–I6 at the end; deep-dives (split/CGA, Lithostitched) still to fold in.

**Gate:** John picks the building-generation direction and confirms the
integration approach to spike (recorded as ADRs).

## Phase 2: Worldgen spikes

Updated: 2026-10-16

Small, throwaway proofs of concept answer the integration questions before the
core is designed around them. Each spike runs in its own worktree and ends with
a findings note (`docs/research/spikes/<id>.md`) plus one test that
demonstrates the result. **Spike code is not merged**; its lessons go into an
ADR. Process: coordinator.md, "Spikes".

Parallel: S1, S2, S4 and S5 side by side; S3 alongside them; S6 after S3
(it measures S3's multi-start city). S4's 2D map needs the viewer from 0.4.
S7–S9 (from track 4) run alongside all of these; S7 pairs with S5, whose
result decides whether flattening is needed.

- [x] **2.1 S1 Biome footprint** (shrunk after track 4, ADR 0002). Already
  answered for vanilla (*probe*, `research/worldgen/terrain-biome-queries.md`):
  ~0.0025 ms per lookup, ~40 ms per 512² layer, sample at the surface Y,
  and a priming lookup makes answers independent of order and thread. Left
  open: (a) does priming still give identical answers in two orders and on 8
  threads when a mod changes the biome source: TerraBlender (one climate tree
  per region, so one priming query may not prime them all), Lithostitched
  injectors, Terralith's parameter list? (b) the in-game cost per lookup on
  the worldgen lane (only the probe timed biomes; heights cost 2.2–2.5× more in
  game), stated per km² for large cities. Evidence: server test per mod set
  with a primed grid in two orders and on 8 threads (0 differences); in-game
  timing under the heavy lock.
- [x] **2.2 S2 Terrain probe** (shrunk after track 4, ADR 0002). Already
  measured for vanilla, one seed (12345): `getBaseHeight(OCEAN_FLOOR_WG)`
  matches real ground in 98.2% of land columns (p99 2 blocks);
  `preliminary_surface_level` reads 12–15 low (p90 17). Left open: (a) 10
  seeds chosen to include mountains, rivers and coasts; (b) the same under
  Terralith and Tectonic, alone and together (their `final_density` differs,
  and the coarse screen misses Terralith's dunes and arches); (c) whether the
  coarse screen's bias is stable enough for a fixed +15 correction per seed
  and mod set; (d) whether surface rules ever add blocks above
  `getBaseHeight`. Evidence: predicted vs actual diff per seed and mod set
  (exact: % equal, p99; coarse: bias, p90) and in-game cost per column. Share
  the Terralith + Tectonic setup with S7.
- [x] **2.3 S3 Multi-start city.** Can 2–4 starts share one plan and join
  seamlessly? Evidence: blocks hashed per chunk are identical whatever order
  chunks generate in; no seams at start or chunk borders. Also: a road that
  crosses a district border (each side owned by one start) meets without a
  seam, and `/locate` finds a district start through a
  `RandomSpreadStructurePlacement` subclass.
- [x] **2.4 S4 Region map.** Does the region layer place cities sensibly
  against real biomes? Evidence: dev command dumping region cells plus biomes
  to JSON; 2D map in the viewer. (The dump format becomes a test fixture for
  Phases 3 and 5.)
- [x] **2.5 S5 Terrain adaptation.** Do vanilla beard modes suffice on hills, or
  do we grade terrain ourselves? Evidence: client-test screenshots of a test
  city on flat, hilly and river terrain.
- [x] **2.6 S6 Scale and cost.** How large can a city go, and what does it
  cost? John's target is effectively unbounded cities; 512×512 is the floor
  to prove, not a cap (ADR 0002, I6). Evidence (timing under the heavy lock,
  load recorded): plan time against city size (512², 1024², 2048², and on
  while it holds), split into coarse screen, exact heights and layout; per
  district: start, piece build and `postProcess` time and save size, with
  pieces saving references vs stored IR (include a tall building); plan-cache
  memory per cell; the city size at which the first-touch stall on the serial
  lane becomes noticeable, and whether a two-level plan (city skeleton, lazy
  district detail) is needed past it; thread-safety of the cache; evict a
  cell's plan, regenerate it and compare bytes.
- [x] **2.7 S7 Terrain flattening** (fallback if S5's beards fall short). Can
  we level city cells by wrapping `final_density` together with
  `chunk_surface_level` and the aquifer `surface_level`, without the plan/mask
  cycle? Evidence: client-test screenshots of a cut and a fill on a hill cell,
  `final_density` alone (expect bare stone, flooding) vs all three wrapped;
  `getBaseHeight` matches the levelled ground. See
  `research/worldgen/surface-level-and-flattening.md`.
- [x] **2.8 S8 Structure veto.** Does one tag-driven predicate
  (`fractalbuildings:excluded_from_cities`) keep foreign surface structures out
  of city cells at both start generation (`tryGenerateStructure`) and
  `/locate` (`StructureCheck.canCreateStructure`)? Evidence: server test with a
  fixed city cell over a known village chunk: no village start, and `/locate`
  skips it. See `research/worldgen/keeping-structures-out.md`.
- [x] **2.9 S9 Procedural pool element.** Can a custom `StructurePoolElement`
  put our generated houses inside vanilla villages (footprint fixed per
  element, content regenerated at `place` from seed and position)? Evidence:
  server test that a village chunk holds our blocks, identical whatever the
  chunk order. See `research/worldgen/custom-generator-and-pool-element.md`.
  Also a compat check: with Lithostitched installed, its global jigsaw
  redirect (`breaks_seed_parity`, default true) runs our element too; our
  blocks must still match, and note whether vanilla villages change.
- [x] **2.10 Worldgen ADR** (researcher) from the findings: integration approach, practical
  size limits, determinism rules.

**Gate:** the worldgen ADR fixes the integration approach, the practical size
limits and the determinism rules. S3's order-independence test becomes a
permanent regression test (re-implemented properly, in Phase 3 or 6).

## Phase 3: Planner core and three.js viewer (done 2026-10-16; gate passed 2026-10-18)

Updated: 2026-10-18

Done in run `docs/runs/2026-10-16-phase3.md`; full text in
[archive/roadmap-phase-3.md](archive/roadmap-phase-3.md), plan in
[plans/phase3.md](plans/phase3.md). The review loop works end to end: IR
`fb-scene/1` (schema in `docs/IR.md`), synthetic and recorded probes,
generator v0 (one plain gabled house per lot), the CLI (`gen`, `batch`,
`check`, `bench`, `diversity`), the three.js viewer with controls, invariant
overlay and a Playwright seed gallery (`npm run gallery`), property tests
(1,280,000 cases, 0 failures), determinism tests with golden digests, and a
diversity floor. Gate pack in `docs/review/phase3/`.

Open known issues that matter later (details in the Polish backlog, 3.x lines):
no front steps, so the door is unreachable from lower ground on slopes (3.3);
no world-height cap near y 319 (3.3); roof slopes render as one colour in the
viewer, only the stripes show (3.8); a failing property case can't be replayed
via the CLI (3.4).

**Gate:** John reviews the seed gallery and the invariant report. *Passed
2026-10-18* (REVIEW.md 3.12; no ADR settled by this gate).

## Phase 4: Building generator depth (done 2026-10-19; gate passed 2026-10-21)

Updated: 2026-10-19

Done in run [runs/2026-10-18-phase4.md](runs/2026-10-18-phase4.md); full text in
[archive/roadmap-phase-4.md](archive/roadmap-phase-4.md), plan in
[plans/phase4.md](plans/phase4.md). Generator v1 (the CLI default; v0 stays frozen
beside it) follows [ADR 0001](adr/0001-building-direction.md): volume massing
(L/T/U/courtyard plans, wings, jetties, setback stacks), then roofs (gable, hip,
shed, flat with parapet, dormers), facades (bays, trim, bands), interiors (core
first, room graph, stairs, furnishing hooks) and a site pass (front steps, the
`entrance-reachable` invariant). Nine building types (house, tower, workshop,
shop, temple, wall and gate, high-rise up to y 319, city block, hall) and five
biome styles (plains, desert, taiga, snowy, jungle). The viewer shades roof
slopes, reads style colours, has a cutaway camera and one gallery preset per
type/style. Gate pack: [review/phase4/README.md](review/phase4/README.md)
(168,000 invariant cases, 0 failing; per-type diversity and timing).

Open known issues that matter later (details in the Polish backlog, 4.x lines):

- One ground level per building: per-wing ground levels wait for 6.3, so city
  blocks only accept ground up to 1 in 16 (4.6 city block).
- Jetties past the lot edge (over a street) wait for Phase 5's street rules (4.2).
- No spires or crowns beyond flat + parapet (4.3, 4.6 tower, 4.6 high-rise).
- Dormers only on 1/1 slopes, so steep-roofed styles carry none (4.3, 4.7 taiga / snowy).
- No veranda or porch pass (4.7 jungle).
- `medieval` is chosen only by name; plains took its biomes (4.7 medieval).

**Gate:** per building type, a gallery passes John's "would I believe a person
built this?" review, including the high-rise, city-block and hall classes.
*Passed 2026-10-21* (John: move on without being perfect; iterate on buildings
again once settlements and the game show real problems). His gate feedback
became items 5.11 and 5.12.

## Phase 5: Settlements and cities

Updated: 2026-10-21

Compose buildings into places, still entirely outside Minecraft, using real
terrain and biome dumps from S4. Cities have no fixed size cap; 512 × 512 is
the first size to prove (ADR 0002, I6). The plan has two levels: a cheap city
skeleton, then district detail built lazily (ARCHITECTURE.md, "City scale").

- [ ] **5.1 Scale ladder**: hamlet, village, town, city, metropolis. Each step
  adds structure (a square, a main street, districts, walls, a downtown);
  no step has a fixed size cap.
  - R1 (2026-10-21): the tier ladder (`Tiers`), region field and planner, and `fractal-cli region` are in `core/region/`; the per-tier programs (D1-D3) are still to do, so this stays open.
  - D0 (2026-10-21, log `docs/log/2026-10-21-p5-d0-pipeline.md`): the city pipeline `CityPlanner` (skeleton, every district, briefs, scenes), `fractal-cli city --scenes ... --check`, `city-check` (markdown report over cells, random or gate-window sites), the whole-city rules `roads-connected` (lots reach the hub) and `tile-border-agree`, `viewer/city-presets/` (one per T2 gate window, T1 river-town and mixed-city, a synthetic city), `npm run city` / `npm run city-gallery`, `e2e/city.spec.ts`. Tests: `CityPlannerTest`, `SettlementChecksTest` (city rules and mutants), `CityCheckTest`. *Known issues (upstream, found by `city-check`):* hub squares dropped on rough ground leave gaps in the main roads and hall lots without frontage, so `roads-connected` flags 6 of 9 gate cities (12 pieces) and about a third of random synthetic sites; scene roads differ by 1-2 blocks in y at some tile borders (`road-grade`, 4 in 9 gate cities with 2 scenes each); a hub hall lot can overlap a port stub (`lots-disjoint`, 4 of 400 sites). `CityCheckTest` ignores `roads-connected,road-grade,lot-frontage` until those are fixed (drop the ignore then).
  - D1 (2026-10-21, log `docs/log/2026-10-21-p5-d1-villages.md`): hamlet and village programs (`core/settlement/tiers/`, selected by `TierPrograms`): straight axis-aligned main street through the hub on the gentler axis, hamlet 3 to 8 buildings on a 22 to 40 block track with no square, lanes or ports; village 10 to 60 buildings with a width-4 main street, a cross street on half, a green and a temple (70 %) or hall (30 %) facing it, lanes; presets `hamlet-river`, `village-river`, `hamlet-mixed`, `village-mixed`. *Known issues:* on very rugged ground (about 1 in 400 villages) district detail moves an ungradable green sideways off the street, so its hall is a separate street piece (`roads-connected`, already in the suppressed set).
  - D2 (2026-10-21, log `docs/log/2026-10-21-p5-d2-town.md`): town program (`TownProgram`: 3 to 5 radial main roads, market square and hall from the default hub landmarks, frontage streets) and the wall ring `WallRing` (D3 reuses it for old towns): on 40 % of towns a closed axis-aligned ring at 0.5 to 0.7 of the radius (each side pulled in on its own where water or cliffs are), cut into `wall` lots (24 to 64 long, 7 to 11 deep) and corner `tower` lots, a `wall` gate lot (23 long, passage on the road) wherever a main road crosses. Tests: `TownProgramTest` (60 towns: every crossing has a gate, ring pieces inside one tile and all present as lots, district scenes build with no violations, passage columns lie on the road). *Known issues:* where the ground is wet or steep a town gets no ring (region-planner towns about 1 in 5, none on the nine T2 gate windows in 40 seeds each, the river window) and a ring that is found can lose 0 to 2 lots to ponds or streams that the 32-block coarse lattice misses (`wall lot ... dropped` notes), leaving gaps; the ring is built only by the cell that holds the centre and only over columns that cell owns (cities of joined cells need the ring by D3); a gate for roads wider than 5 (arterials) would need a wider passage than the wall type has.
- [x] **5.2 Layout**: road network (primary and secondary), lot subdivision,
  plazas and landmarks, gates. Split into the city skeleton (arterials on
  position-hashed lines, district borders drawn around big lots, coarse
  height screen) and lazy per-district detail (lots, briefs, exact heights).
  *SK landed (2026-10-21, log `docs/log/2026-10-21-p5-sk-skeleton.md`): `Skeletons` builds a cell skeleton (mask, density, quarters, hub, gates, A* main roads, arterials, hub landmarks, district tiles) and merges a settlement into `fb-settlement/1`; `fractal-cli city`; DL ticks this item.*
  *DL done (2026-10-21, log `docs/log/2026-10-21-p5-dl-layout.md`): `Districts.plan` plans a tile from the skeletons and exact terrain: skeleton roads, pointwise arterials and plain border streets, grid lines shared along rows of tiles, ports and lanes, plazas, hall and temple lots, whole-block and frontage lots with briefs (`airRights`, `frontY`); `fractal-cli city --districts [--scene]`. Tests: `DistrictsTest` (150 synthetic sites, 6873 tiles, 183k lots, 11k border pairs: every DL rule holds; shuffled order and fresh sources give the same JSON; recorded river window; mutants), `SettlementChecksTest`, `CityCommandTest.districtFixturesAreCurrent`, `viewer/e2e/districts.spec.ts`. *Known issues:* skeleton roads over steep ground keep their cut and fill (noted, not rerouted); road y can differ a block at a tile border; lots only along axis-aligned runs; the lake-rich real-terrain city has few lots (patchy skeleton mask, SK); brief storeys use the district quarter, not the lot's.*
  *Hub square on real terrain (2026-10-21, log `docs/log/2026-10-21-p5-hub-square.md`): a hub square that does not grade on exact terrain now moves (nearest first, up to 160 blocks inside its tile) or shrinks to 16 blocks, preferring spots with dry room for the hall and temple; all nine gate windows get their square (was 3 of 9), 0 violations. A moved square gets a lane to each nearby road piece (`plazaLanes`), the hub for the roads-connected check is the square itself (`CityPlanner`), and hall and temple lots are placed after those lanes with relief <= 3; so hall and temple still miss on 8 of the 9 gate windows (hall on 4). Test: `HubSquareGateTest`.*
  *ROADS (2026-10-21, log `docs/log/2026-10-21-p5-roads.md`): the whole-city defects D0 exposed are fixed and `CityCheckTest` ignores no rule: lots only on street pieces that reach a live skeleton road (`RoadNetwork`, from every member skeleton), edge tiles in cells without a settlement claimed by majority and main roads planned through every tile up to the last urban one, hall and temple placed after the streets (else on the nearest reachable street of the hub tile), squares joined to their roads, bridge decks as lower bounds, and every road shared by two tiles graded once so both build it at the same height. `city-check` with no ignore: 0 violations on the 9 gate windows, 1120 synthetic sites and both world maps. Tests: `CityRoadsTest`, `HubSquareGateTest`, `CityCheckTest`, `TerrainToolkitTest`. *Known issues:* port-stub lanes graded per tile (1 block at 27 columns); arterial chains with no main-road link stay empty; metropolis-mixed has no hall (lake hub tile); earthwork up about 40 % on the gate windows.*
- [x] **5.3 Terrain**: respond to slope, rivers and coast; choose where to level
  ground and where to step buildings.
  *TA groundwork landed (`core/terrain/`: `WaterField`, `LatticePath`, `Grading`, `LotGround`).*
  *TR done (2026-10-21, log `docs/log/2026-10-21-p5-tr-terrain.md`): (1) the urban mask takes ground up to 3 in 4 (was 1 in 2; roads keep 1 in 2 for their cost), so real hills are less patchy; (2) arterials run only where coarse samples 32 blocks apart differ by at most 1 in 2; (3) waterfront quays: `RoadKind.QUAY` promenades (width 4, stone paving in scenes) along the water of the waterfront quarter, on low banks only, with link lanes, pointwise so tiles agree (`Quays` marching squares + `Districts.quays`); (4) role `deck` for bridge decks and piers (palettes, viewer); (5) `city-check` prints a terrain-response table (`TerrainStats`: urban %, lots, `empty:steep`, cut and fill k blocks, steep roads, bridge and quay pieces, quay lots). On the 9 gate windows lots rose from 185..2248 to 207..2459 (+5 to +24 %; the badlands +24 %), the urban mask share from 55..91 % to 70..97 %, and the badlands cut and fill fell from 134k/159k to 72k/49k blocks (elsewhere about level). Tests: `QuaysTest`, `WaterfrontTest` (4 gate cities: 192 quay pieces, none wet, 0..4 blocks over the water, 84 quay-front lots of 1301, tile borders agree; scene paving and deck roles), `PlanDraw` unit tests, three presets with 3D frames. *Known issues:* no stairs (steep lanes route around or are dropped; main roads and arterials keep their cut and fill where the fine terrain is steeper than the coarse lattice shows, 7 to 90 street pieces per city); quays hug every waterfront bank in the quarter, so some run past no lots (quay-front lots are 4 to 12 % of a city's lots on the gate windows; 15 of 207 in the temperate one); a road crossing a tile no district plans leaves a gap (ROADS).*
- [x] **5.4 Biome-aware style**: each district takes its palette from the biomes
  underneath, blended across borders.
  *Done (ST `StyleField` + DB `Briefs.fill`, log `docs/log/2026-10-21-p5-db-briefs.md`): per-lot style by a hashed vote over a 48-block disk; on the split sample 27/28 plains-side and 12/12 desert-side lots follow their side; on the w1-mixed dump 8 % of lots differ from their own biome (border blend).*
- [x] **5.5 Viewer**: a 2D plan view over the terrain dump, plus a 3D fly-through
  of a chosen district; the plan view stays usable for cities of several
  thousand blocks (skeleton overview, district detail on demand).
  *V1 landed: the plan view (`viewer/plan.html`, see VIEWER.md "Plan view"). V2 landed: the district 3D view and fly-through (VIEWER.md "District 3D view"; face meshing, terrain heightfield, water, ground boxes, `?camera=fly&stops=`). Known issues: the storeys slider does nothing on the face-mesh path; hook markers are not drawn there.*
- [x] **5.6 Determinism**: the skeleton is built from the region cell alone and
  each district's detail from (cell, district), so every district start
  reproduces them identically in any order.
  *SK: the skeleton is a pure function of the region cell (built in shuffled order with fresh probes it gives byte-identical plans, `SkeletonsTest`; goldens `golden/settlement/`); DT ticks this item.*
  *DT landed: `SettlementDeterminismTest` plans every `viewer/city-presets` preset in order, with shuffled cells and districts, on 8 threads over a 2-cell evicting skeleton cache, and each district alone in a new source, and compares settlement, district and scene bytes; three mutant sources (hidden counter, neighbour-reading skeleton, hash-order) must be caught. Knobs: `-Dfb.det.presets=a,b`, `-Dfb.det.full=true`. Lint covers all of `main`, with a check that settlement/region/probe/style are in it.*
- [ ] **5.7 Density and skyline**: building class and height follow distance
  to the centre and to arterials (downtown high-rises and blocks, halls on
  squares, houses at the edge).
- [x] **5.8 Cities across cells**: neighbouring settled cells meet at hashed
  gates on their shared border, so roads join without either plan reading
  the other; grow cells or join cells per the worldgen ADR (2.10).
  *SK: cell gates agree from the cell pair alone (`cell-gate-agree` over 200 synthetic sites of 2 to 6 cells, also under a ±20 height error); MC2 ticks this item.*
  *MC2 done 2026-10-21* (log `docs/log/2026-10-21-p5-mc2-cells.md`): `city --cell` plans every settled cell (solo hamlets/villages/towns and dropped claimants get a one-cell site), joined cities from any member cell with identical bytes, cell by cell, shuffled and on 8 threads with evicting caches (`MultiCellCityTest`); the join view and the skeleton source use bounded pure LRU caches (`BoundedCache`); on a fine window `--cell` says what the window cannot answer and points at `--site`. *Known issues:* the T2 fine windows still need `--site` (the join rule reads up to ~3 x max radius around a cell); `--cell` on a recorded world dump prints a "cannot see outside the dump" note for every city-or-larger cell.
- [ ] **5.9 Scale check**: plan time and memory per district, measured outside
  the game at 512², 2048² and a multi-cell city; flat per-district cost is the
  bar (feeds 6.5 and 6.7).
  *SC partial 2026-10-21* (log `docs/log/2026-10-21-p5-sc-scale.md`, tables `docs/review/phase5/scale.md`; `fractal-cli city-bench` + `scripts/city-bench.sh`): per-district time flat and skeleton < 50 ms met; probe bar (< 1,000 exact probes per district plan) not met (1.5k–3k on big gate cities, 5.9k–11.7k synthetic; scenes 70k–990k exact columns) — carried to 6.5/6.7. Median district plan+brief 0.7 to 2 ms on the recorded gate windows from a 477-radius city to the 8-cell metropolis (flat per lot, 0.2 to 0.3 ms), skeleton 2 to 11 ms per cell, a 484-district 4096^2 city plans in 2.8 s in a 64 MiB heap. *Known issues:* the 1.5 x time bar holds on recorded terrain and without scenes (synthetic 512^2 to 2048^2 1.1 x), but is 1.8 x on synthetic terrain with scenes in the JVM; "exact probes" count `floorY` only (`waterDepth` uncounted), so live cost may be underestimated; skeleton cache capacity below 4 costs 1.5 to 4.8 x.
- [x] **5.10 Water bias**: city sites prefer rivers, lakes and coasts, via a
  distance-to-water term (coarse biome and height probes) in the region
  field's best-of-8 candidate score (ADR 0003, R1); villages and hamlets stay
  unbiased (John, 2026-10-16). Stays a pure function of (seed, position) and
  cheap (S6: coarse suitability ~11 ms per cell). Evidence: S4-style region
  maps before and after, showing city sites shifting toward water.
  Done (R2, [log](log/2026-10-21-p5-r2-water.md)): maps in `docs/review/phase5/region/`.
  *Known issues:* the plan's "+50 % of city sites within 256 blocks of water" is not
  measurable on the two world dumps (94 % are already within 256 before); measured at 64 blocks
  it is 31 % -> 46 %, count +9 %. Boundary cells of a window lose the big tiers (wider reach).

- [x] **5.11 Interior circulation** (Phase 4 gate feedback, John 2026-10-19):
  houses should mostly reach their rooms through a hall, landing or central
  room rather than through a chain of other rooms (house-medieval seed 7: a
  row of rooms walked through one after another). Rooms get a purpose that
  drives adjacency (public rooms off the entry or hall, private rooms off a
  hall; walk-through only where it makes sense), keeping some oddness in
  moderation. Also open connections: archways or open plan between some
  public rooms instead of doors. Evidence: a "rooms reached only through two
  or more other rooms" measure before and after, and cutaway galleries.
  *Done 2026-10-21* (log `docs/log/2026-10-21-p5-circulation.md`): rooms hang off a hub (entry, stair or corridor room) in 85 % of plans, 15 % keep the random chain; floors are planned up to 8 times for fewer, bigger rooms when a chain would result; the room a wing opens into is a hall/landing; new IR opening kind `arch` (40 % of doors between public rooms; 1-3 wide, trim lintel). Rooms behind two ordinary rooms (1500 houses per style): 1.5-5.2 % -> 0.12-0.27 %. Tests: `CirculationTest` (metric, archways, oddness, determinism). GEN_VERSION not bumped; goldens regenerated. *Known issues:* shops still 2-3 % (shop -> back room -> stair); remaining house chains cross masses (strip door positions are fixed before the plan).
  *Follow-up (John, REVIEW 2026-10-21):* some houses lost bedrooms and gained long skinny rooms (seed 9); keep rooms nearer square and give houses more bedrooms before a second room of any other kind, not always (item CIRC2: room aspect limit, bedroom-first program, bedrooms per house before and after).
  *CIRC2 done 2026-10-21* (log `docs/log/2026-10-21-p5-circ2.md`): re-plan attempts are scored (deep rooms, long thin rooms, needlessly big rooms), long thin rooms are cut across their length, and a house floor gives its spare rooms to a missing kitchen/living room first and then to bedrooms (15 % seeded repeats). Houses (medieval, 1500): bedrooms/house 1.35 -> 1.81, rooms with aspect > 2:1 11.8 % -> 5.8 %, rooms behind >= 2 rooms 0.20 % -> 0.31 %. *Known issues:* the deep-room share crept up slightly (highrise 3.6 -> 4.9 %, shop 3.2 -> 3.9 %); 5-8 % of house rooms still exceed 2:1 where the mass is a narrow wing and cannot be cut.
  *House program done 2026-10-21* (log `docs/log/2026-10-21-p5-house-program.md`): a whole-house pass across masses and storeys (`RoomProgram.refineBuilding`) gives every house of 2+ rooms one ground-floor kitchen and, with 2+ ground rooms, one living room; houses without a kitchen 48-57 % -> 0 % (3+ rooms: <= 3.8 %, huts with a loft), bedrooms/house -0.14.
- [x] **5.12 Open ground storeys** (Phase 4 gate feedback, John 2026-10-19):
  shops and workshops may have a covered outdoor area at street level (an
  arcade, a ground floor open on posts, a bazaar-like market floor); shop
  display windows sit at street level (prefer level frontages, or step the
  shopfront down to the street, rather than a window at the top of the front
  steps). Evidence: shop and workshop galleries before and after; invariants
  still 0 failing.
  *Done 2026-10-21* (log `docs/log/2026-10-21-p5-open-ground.md`): shop arcade 30 %, market hall on posts 20 %, enclosed ~50 %; workshop covered porch 40 %; shops reject lots steeper than 1 in 12 (level frontage). *Known issues:* the market hall floor is empty (no stalls); a city planner must catch the shop slope rejection.
  *Follow-up (John, REVIEW 2026-10-21):* a shopfront lifted above the street needs a landing or balcony at door level so the display window does not sit at the top of a flight of steps (item SHOPLAND).
  *SHOPLAND done 2026-10-21* (log `docs/log/2026-10-21-p5-shopland.md`): lifted shopfronts (door more than 1 above the ground) get a landing at door level across the front, the flight down at one end and its foot on the lot front row (r2); market halls are set back for a 2-deep deck on posts (open floor stays open). *Known issues:* no railing; enclosed-shop landings are solid; a market hall on ground that also falls away by more than 1 (lift over 5) may keep the plain flight.
- [ ] **5.13 Irregular cities** (John 2026-10-21): old-town subdivision and diagonal avenues; plan steps IRR1 and D3.

**Gate:** John reviews cities in at least five biome mixes, including one
straddling a border, one larger than 512 × 512, and one with a high-rise
downtown.

## Phase 6: Minecraft integration and release

Updated: 2026-10-21

Wire the proven core into worldgen using the approach fixed in the Phase 2 ADR.

- [ ] **6.1 Adapter**: palette roles to block states; IR to structure pieces,
  clipped per chunk.
- [ ] **6.2 Registration**: structure type, structure set with data-driven
  spacing, region layer, biome tags for where settlements may appear.
- [ ] **6.3 Terrain**: adaptation mode or our own grading, per S5.
- [ ] **6.4 Gameplay hooks**: loot tables, villager workstations and beds,
  `/locate` support. Leave room for a villager AI mod later.
- [ ] **6.5 Config**: frequency, maximum city size (a default from S6, not a design cap; ADR 0002), enabled styles.
- [ ] **6.6 Compatibility**: test with popular terrain mods (Terralith,
  Tectonic) and alongside vanilla villages.
- [ ] **6.7 Performance budget**: worldgen time per chunk and save size per
  city, checked in CI.
- [ ] **6.8 Client tests**: fly-through screenshots of fixed seeds.
- [ ] **6.9 Railings on stairs, landings and balconies** (John 2026-10-21): a `rail` IR role on stair flights, landings, balconies and lookouts (front steps up to houses and shops first), mapped to fence or wall blocks per style in the palette; the viewer draws it.

**Gate:** a fresh world on three seeds plays well for an hour; then a first
public release.

---

## Polish backlog

Non-breaking known issues from verified items, one line each with the item id.
Fixed opportunistically or scheduled as items.

- 0.1b: viewer irToVoxels ordering test only varies x; y/z sort keys untested (a mutation survived). Add a multi-row case.
- 0.1b: viewer `npm run dev` never run; 0.4 screenshot is the first in-browser check.
- 0.1a: `checkNoMinecraft` also matches forbidden package names in comments/javadoc (a doc comment mentioning net.minecraft fails the build).
- 0.1a: cli/Main.java has an unused `java.util.HashMap` import.
- 0.6: test-all.sh layer detection for 0.4/0.5 (determinism, screenshot npm script names, game-test count regex, client layer) untested until they land; count regex may print "no count in log".
- 0.6: running test-all.sh while already holding the heavy lock deadlocks (documented in --help).
- 0.4: `Planner.smokeBox(seed)` ignores the seed; `--lot`/`--style` accepted but unused (smoke only).
- 0.5: test-all client layer prints "no count in log" instead of a test count.
- 0.5: `mod/build/screenshots/` is never cleared and the copy runs only on success, so a failed client run can leave a stale hello-hut.png that looks current; raw shots also duplicated in mod/build/run/clientGameTest/screenshots/.

- 3 (plan): `SampleScenes` (test helper) ships in planner-core main jar.
- 3.2: biome lookup between lattice points untested (a +step/2 rounding mutant survived); ProbeSpecs has no `terraces:` form; `SyntheticTerrain.hill` computes `x - cx` in int before widening (overflow at extreme coords).
- 3.1: IrValidator doesn't check storey ≥ 0 (except roof −1) or site-box containment.
- 3.5: `buildSceneObject` has no unit test (group names, palette colouring).
- 3.9a: shell-closed/in-lot/in-world mutants assert presence, not exclusivity; `no-gap-below` cell order is (z,x), class comment says (y,z,x); `ir-valid` has no mutant through `Invariants.check`; `no-gap-below` flags buried floors (basements will need a rule change).
- 3.3: no front steps (door unreachable from lower ground on slopes); no world-height cap near y 319.
- 3.6: in `?fixture=` mode the seed box is empty while the slider sits at 0.
- 3.8: the flat-terrain `top` view shows a thin line across the grass (e.g. plain-flat seed 5): a viewer rendering artifact, not the generator (the V4 log calls it a site-box edge, which is wrong).
- 3.8: gallery noise terrain is the same patch in every cell (one `noise:` spec, same lot position), so terrain variety isn't reviewed.
- 3.8: flat-shaded `top` view reads each roof as one colour block (ridge and pitch not visible).
- 3.4: `check` exit-1 path (a scene with violations) is untested.
- 3.4: a failing property case can't be replayed via the CLI (no flags for the test's terrain or lot position).
- 4.3: dormers only on 1/1 slopes; shed rises at most 3 then plateaus; no crowns or spires beyond flat + parapet; a lower roof meeting a taller wall is a plain lean-to (no hip against the wall); overhang-past-envelope mutant not expressible (clip is by array bounds).
- 4.1 (plan): "Golden digests" section says the verifier regenerates golden files after a merge; the verifier rules have coders regenerate before verification.
- 4.2: jetties and setbacks only on the main stack; wings attach only to the main block (no wing-on-wing); `MassingOps.stack` unused; no jetty when the wing blocks every side; `TestMassings` M fixtures sit before `tall50`.
- 4.3: `RoofField.distance` is O(cells²) per roof group (watch on big lots); a parapet stops one cell short of a taller wall (gap).
- 4.4: half-timber braces not done (posts and rails only); material gradient by role only; the band rule "never the same variant as below" compares keys, not tiling; 2-wide windows only in even bays of 4+; windows beside a core or partition are not cut.
- 4.5: a furnishing hook can sit under a window; the courtyard door-count assert is a lower bound; the entry room is not forced onto the corridor; small centred cores leave one stair hall per floor.
- 4.8: removing only the top step block isn't caught (foundation tops form a flight); `hill:` probe has no centre option; a type with front margin 0 gets no steps.
- 4.9: only the `top` e2e catches a broken roof tint; `render.ts` per-voxel shade wiring untested; cutaway partitions hard to see at gallery scale; `top` camera perspective-skewed on 250-block towers.
- 4.6 house: `roomy()` rejects small upper floors, so more houses are single-storey (21/32 on small envelopes, was 16/32).
- 4.6 tower: no slit windows (window holes are plain 1-wide openings of the style's height); no spire; uniform window bands. Watchtower variants (gate follow-up): stilted lookouts only where the envelope is at least 7 wide and the stair (one step per block of leg height, along the front wall) fits; the way up is the site pass's front steps (a foundation-backed ramp, no rail); dormer panes stay glass in buildings with window holes; hole windows are not yet a style choice anywhere but the tower.
- 4.6 workshop: lots under 12x12 get the hall only; no chimney, forge geometry or yard props; the cart door is the facade's 2x2 double door (no taller lintel); cluttered roofs.
- 4.6 shop: envelopes under 7 wide unsupported; no awning or sign (needs a free cell in front of the wall; site step).
- 4.6 temple: windows repeat per grid storey on the open nave and tower (needs tall windows), so bell towers read as tall houses.
- 4.6 wall: flat along its length on slopes (one slab at the highest ground); plain low curtains; no extra wall towers or stairs to the walk; towers don't project; gate is a plain door box, not an arch; dead loop in `roofsAreFlatWithGapsInTheParapet`.
- 4.6 high-rise: tiers are nested rectangles (no corner towers or twin shafts); crown always a flat box (no pyramid or spire); podium not always distinct.
- 4.6 hall: no crossing tower on cathedrals; arcade is glazed arches (invisible at gallery zoom), not doors; halls ignore the style's massing plans; tall exposed foundations on slopes; no 200x200 preset (buildings capped 150x120); roof test allows rise 10 vs design 7.
- 4.6 city block: gentle ground only (steeper than 1 in 16 rejected) until per-wing ground levels (6.3); minimum lot 52x52; no jetties or setbacks on units; one storey grid across units; `check` stops the whole run at the first rejected lot.
- 4.7 plains: wall gradient (birch/oak/plaster) reads noisy per voxel.
- 4.7 desert: yellow terracotta in the wall gradient makes walls look speckled; `DesertStyleTest` and `TaigaStyleTest` keep their own block whitelists (switch to the shared `vanilla-26.3-blocks.txt`).
- 4.7 taiga / snowy: dormers need 1/1 slopes, so 2/1 roofs carry none; snowy's window-density assertion only catches a big facade change; 4-block roof gradient noisy per voxel.
- 4.7 jungle: no verandas or FENCE railings (needs a porch pass); no raised ground floors.
- 4 (docs): NOTES has the "Facades (4.4)" line twice; ASSUMPTIONS has the wall-margins and workshop rows twice; commit a72a23f's subject says T-workshop but holds the T-wall squash.
- 4.7 medieval: a style chosen only by name (plains took its biomes; still requestable with `--style medieval`); keep, rename or give it biomes back (gate pack question 15).
- FLK (viewer): a superseded rebuild still assigns `source`/`sourceKey`; stale errors still publish (check `isLatest` in the catch).
- 4.7 plains (John, REVIEW 2026-10-21): plains palette: smoother, more similar wall blocks (less speckle); subtle shading instead of dark and light scatter.
- 5 (John, REVIEW 2026-10-21, later phase): biome-specific building forms beyond palette and roof pitch (desert courtyard and flat roofs, jungle raised floors and verandas, snowy steep roofs and small windows).
- Balconies on residential and commercial buildings, including high-rises (John, REVIEW 2026-10-21, 5.11 entry); later iteration.

## Open questions and risks

- **Core language:** Java keeps one codebase with the mod; Kotlin is pleasanter
  but adds a runtime dependency. A TypeScript prototype would be faster to
  iterate but means porting later. Keep in mind what's best represented in
  Claude's training data. (Current assumption: Java.)
- **City size vs reference radius:** if coordinated starts prove awkward, a
  custom structure placement or deferred placement (as Astruct does) may be
  needed.
- **Plan cache under worldgen threads:** must be deterministic, bounded and
  thread-safe.
- **Hard terrain:** mountains, rivers and coastlines may need
  settlement-specific rules or simply avoidance.
- **Mod compatibility:** terrain overhaul mods change heights and biomes; probes
  must not assume vanilla.
- **Version churn:** Minecraft is on 26.3, and worldgen APIs move between
  releases. Keep the adapter thin.
- **Licences:** study code only as each mod's licence allows; record the licence
  in every one-pager.
- **Scope creep:** villager AI, quests and interiors-as-gameplay belong to later
  projects.

## Sources

- [World generation](https://minecraft.wiki/w/World_generation), Minecraft Wiki: generation steps, climate parameters
- [Jigsaw structure](https://minecraft.wiki/w/Jigsaw_structure), Minecraft Wiki: size limits, terrain adaptation modes
- [Automated Testing](https://docs.fabricmc.net/develop/automatic-testing), Fabric docs: JUnit, server and client game tests
- [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities), CurseForge listing
- [Astruct](https://www.curseforge.com/minecraft/mc-mods/astruct), CurseForge listing
- [GDMC HTTP Interface](https://modrinth.com/mod/gdmc-http-interface), Modrinth listing
