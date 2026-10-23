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
| 5. Settlements and cities | done 2026-10-21 (5.9 partial: probe bar not met); gate passed 2026-10-22 (John, REVIEW "Phase 5 gate"; follow-ups STAIRGROUND done, S10 roads must not cross water) | cities in ≥5 biome mixes, one straddling a border |
| 6. Minecraft integration and release | in progress (run 2026-10-22-phase6) | fresh world on three seeds plays well for an hour; first release |
| 7. World-level features (later) | not started | transit (7.1), other-mod integration research (7.2); S10/6.10 may move here |

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
- Live-probe parity (P6 A1): `mod/src/test/.../probe/LiveProbeTest` compares `LiveProbes` with `fixtures/terrain/` dumps on the same columns (floor, water, biome) and the three chunk-fill methods with `getBaseColumn`; it runs in `:mod:test`, so a Minecraft bump shows at once whether the game still matches the reviewed dumps. Timing table: `LiveProbeTiming` (`-Dfb.timing=1`, heavy lock).

- Added in Phase 6 (A3): `core/raster/` (`PieceRaster`, `Shapes`, `Variant`) and `RasterOwnershipTest` over real gate-city districts (one owner per cell, union equals the scene, same cells under any chunk clipping, door and stair facings; writes top-down pictures to `planner-core/build/raster/`); `cellhash.csv` shared by `VariantTest` and the viewer's `cellhash.test.ts`.
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

## Phase 5: Settlements and cities (done 2026-10-21; gate passed 2026-10-22)

Updated: 2026-10-21

Done in run [runs/2026-10-21-phase5.md](runs/2026-10-21-phase5.md); full text in
[archive/roadmap-phase-5.md](archive/roadmap-phase-5.md), plan in
[plans/phase5.md](plans/phase5.md). Places composed outside Minecraft on real
terrain dumps: a region field picks sites (cities biased toward water, ADR 0003)
on a tier ladder (hamlet, village, town, city, metropolis, one program each).
Each region cell gets a cheap, pure skeleton (mask, quarters, hub, A* main
roads, arterials, hashed cell gates so joined cities agree), then lazy
per-district detail (streets, lanes, quays and bridge decks, squares, wall rings
with gates, old-town subdivision, diagonal avenues, lots with briefs). Density
drives class and height (downtown high-rises, halls on squares, houses at the
edge); style follows the biomes underneath, blended across borders. Tools:
`fractal-cli city`, `city-check` (whole-city rules, 0 violations on the 9 gate
windows and synthetic sites), `city-bench`; plans reproduce in any order and on
8 threads; the viewer has a plan view and a district 3D fly-through. Phase 4
gate feedback landed too: hall-first circulation, arches and a bedroom-first
house program (5.11); shop arcades, market halls and shopfront landings (5.12).
Gate pack: [review/phase5/README.md](review/phase5/README.md).

Open known issues that matter later (details in the Polish backlog, 5.x lines):

- 5.9 scale check partial: the probe bar (under 1,000 exact probes per district)
  is not met (4.9k to 8.9k on the gate cities); carried to 6.5/6.7.
- No stairs on steep lanes: they route around or drop; main roads keep their
  cut and fill (5.3).
- Walls are rare on real terrain, and every wall lot is a gatehouse (5.1 D2, 5.7).
- Wall walk (p5-wallwalk): ring lots without a road are now curtain walls and the corners bastions, joined by one wall walk; gatehouses only at roads. Sloping ground: fixed by p5-wallslope (ring walk profile, ramps at the joins; see its log). Open: ring pieces dropped where a plaza reserves the ground (a real gap in the walk, 2 to 3 of 28 pieces in 3 of 20 steep rings), gate lots standing high above the road on steep ground.
- The metropolis downtown plateaus at 60 storeys and has few city blocks (5.7).
- Every city has an old town round the hub (5.13).
- Nothing in the mod maps the district roles (`deck`, paving, road) to blocks yet.
- `SkeletonSource` and `FieldRegionView` must be per-world and thread-safe in
  the mod.

**Gate:** John reviews cities in at least five biome mixes, including one
straddling a border, one larger than 512 × 512, and one with a high-rise
downtown. Passed 2026-10-22 (John, REVIEW "Phase 5 gate").

## Phase 6: Minecraft integration and release

Updated: 2026-10-22

Wire the proven core into worldgen using the approach fixed in the Phase 2 ADR.
Plan: [plans/phase6.md](plans/phase6.md) (steps A1–C3; ten decisions for John, each with a default).

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
- [x] **6.9 Railings on stairs, landings and balconies** (John 2026-10-21): a `rail` IR role on stair flights, landings, balconies and lookouts (front steps up to houses and shops first), mapped to fence or wall blocks per style in the palette; the viewer draws it.
  Done 2026-10-22: `Role.RAIL` (last in the enum), added by `InstancePassV1.rails` on the open sides of every exterior flight and landing (houses, shops, lookouts, hall steps: all go through the site pass); per-style `rail` blocks (medieval dark oak fence, plain and plains oak fence, desert sandstone wall, jungle jungle fence, snowy and taiga spruce fence); the viewer draws it. Log: [log/2026-10-22-p6-railings.md](log/2026-10-22-p6-railings.md), pictures `docs/review/phase6/railings/`. *Known issues:* no balconies exist in the generators yet (nothing to rail), flat roof parapets keep the `fence` role, wall walks are not railed; the mod has no role-to-block mapping yet (6.1 picks up `rail`, fence or wall blocks must connect to neighbours); a flight on a lot edge has no rail on the street side (no free cell in the lot).
- [ ] **S10 Spike: roads between settlements** (approved by John 2026-10-22, REVIEW "D1 hamlet and village"; running now beside the start of Phase 6, two halves in parallel: the planner on the recorded world dumps, the mod feature in a noise world). Question, pass bar and fallbacks: [plans/intercity-roads.md](plans/intercity-roads.md), "The spike (S10)". Spike code is not merged: a findings note in `docs/research/spikes/`, lessons into an ADR (the road feature needs ADR 0004 or an amendment to 0003).
- [ ] **6.10 Roads between settlements** (John 2026-10-21; after S10): a region-scale road graph (relative neighbourhood graph over resolved settlements), each link routed once on the coarse lattice and cached per edge; SK required exits and approaches aimed at the links; a road layer in the region map and plan view; in the mod, a per-chunk road feature with its own cut and fill. Plan: [plans/intercity-roads.md](plans/intercity-roads.md). The planner half needs no gate; the mod half needs 6.1 and 6.2. May move to Phase 7 if Phase 6 is to stay lean for the first release.

**Gate:** a fresh world on three seeds plays well for an hour; then a first
public release.

---

## Phase 7: World-level features (later)

Updated: 2026-10-22

After the first release; no plan yet. Items wait for Phase 6 unless noted.

- [ ] **7.1 Transit: subways and surface railways** (John 2026-10-21, REVIEW "Whole-city roads"): a transit layer in city planning. Stations in dense districts (downtown and city-block tiers first), lines along arterials and main roads, minecart rails underground (vanilla rails and powered rails, no other mod needed), surface or elevated lines between districts, and later between settlements along 6.10's links. Prior art: [lost-cities-road-planning.md](research/overlays/lost-cities-road-planning.md) (global hashed lines clipped to the city mask, pure per-chunk queries: the shape for lines; Lost Cities' highways already route round its railway corridors); Lost Cities' own railway and station planner is not researched yet. Needs a research note and a plan first.
- [ ] **7.2 Research: integration with other mods, Create first** (John 2026-10-21, REVIEW "Downtown skyline and avenues"): high-rises are hard to climb on foot; with Create they could have working elevator shafts. Check Create's support for our Minecraft version, how its contraptions could be placed at worldgen, and optional-dependency loading on Fabric. Cheap step before then: leave a shaft-sized space in high-rise cores for a later elevator.

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
- 5.1 D1: on very rugged ground (about 1 in 400 villages) district detail moves an ungradable green sideways, so its hall is a separate street piece.
- 5.1 D2: no town ring where the ground is wet or steep (about 1 in 5 region towns); a ring can lose 0 to 2 lots to ponds the 32-block lattice misses; ring built only by the cell holding the centre; no gate wider than 5.
- 5.1 ROADS2: the street lattice puts a 5-wide street on every urban tile border (a rectangular frame, 130 to 190 blocks apart) and drops what no street reaches (quays, unlinkable arterials, floating squares); towns got it too, so organic port lanes are gone and lot counts moved (flat towns about twice, a hilly town about a quarter fewer); strict-slope terrain leaves lattice gaps that cost lots. See the log and REVIEW "ROADS2".
- 5.1 ROADS3: roads ramp at 1 in 3 to a gate on steep ground (extra cut and fill; gate-metropolis-temperate cut and fill 666k → 792k, partly the restored arterials); blocks on real terrain stay rare (water and slope veto the street rings and the lots); fabric density moved city arterials slightly; flat 50-70 belts are about 40 % blocks (REVIEW "ROADS3").
- 5.2 DL: skeleton roads over steep ground keep cut and fill; road y can differ a block at a tile border; lots only along axis-aligned runs; lake-rich real-terrain city has few lots; brief storeys use the district quarter.
- 5.2 DL r2: port-stub lanes graded per tile; arterial chains with no main-road link stay empty; metropolis-mixed has no hall (lake hub tile); earthwork up about 40 % on the gate windows.
- 5.3 TR: no stairs (steep lanes route around or drop; 7 to 90 street pieces per city keep cut and fill); quays run past no lots on some banks; a road crossing an unplanned tile leaves a gap.
- 5.7 D3: diagonal avenue frontage is empty; walled cities have 5-wide avenues; old-town walls only where the centre cell owns the ring; (the 60-storey plateau is fixed by p5-skyline: see its log) city blocks stay near absent (whole street blocks of 52 and more with a street on all four sides are rare).
- 5.8 MC2: T2 fine windows still need `--site`; `--cell` on a recorded world dump prints a "cannot see outside the dump" note for each city-or-larger cell.
- 5.9 SC: 1.5x time bar fails on synthetic terrain with scenes (1.8x); "exact probes" count `floorY` only; probe bar (under 1,000 per district) not met; skeleton cache below 4 costs 1.5 to 4.8x.
- 5.10 R2: "+50 % of city sites within 256 blocks of water" unmeasurable on the world dumps; boundary cells of a window lose the big tiers.
- 5.11 CIRC: shops still 2-3 % chain through a back room; deep-room share crept up (highrise 4.9 %, shop 3.9 %); 5-8 % of house rooms exceed 2:1 on narrow wings.
- 5.11 OPENPLAN (p5-openplan, done): kitchens open onto living areas by style (`interior.openPercent`: plain 85 % of kitchen links, medieval 22 %); see [log](log/2026-10-21-p5-openplan.md). Known: no dining use exists yet, so "living-dining" open plan is living-hall/entry only; wide archways are 2-4 cells, never a full-wall removal.
- 5.11 OPENPLAN (John, REVIEW 2026-10-22): lintels over wide openings as top-half slabs where the style's wall or trim material has a slab, and arches as upside-down stairs; needs slab and stair shapes in the palette mapping (6.1).
- 5.12: market hall floor is empty (no stalls); a city planner must catch the shop slope rejection; no railing; enclosed-shop landings are solid.

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
