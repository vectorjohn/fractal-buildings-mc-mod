# Roadmap: procedural buildings and cities

A Fabric mod that generates varied, believable buildings and cities from rules
rather than stamped templates. Research and a three.js review loop come first;
worldgen integration is proven with spikes before any architecture is locked
in. (Converted on 2026-10-07 from John's "Procedural Buildings Mod — Roadmap"
doc, kept in [archive/original-roadmap.md](archive/original-roadmap.md) with
the setup prompt. The design sketch and worldgen primer are in
[ARCHITECTURE.md](ARCHITECTURE.md).)

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

## Status

Owned by the coordinator. Item checkboxes below are ticked by the coder of each
item. Per-item work logs are in [log/](log/); run ledgers in [runs/](runs/).

| Phase | State | Gate |
|---|---|---|
| 0. Scaffolding | done 2026-10-08; awaiting gate | builds green; viewer renders the box |
| 1. Prior art research | in progress: 45+ notes; tracks 1, 2, 5, 6 covered; 3, 4 finishing; early skim open | John picks building-generation direction and the integration approach to spike |
| 2. Worldgen spikes | not started | worldgen ADR (approach, size limits, determinism rules) |
| 3. Planner core and three.js viewer | not started | John reviews seed gallery and invariant report |
| 4. Building generator depth | not started | per building type, "would I believe a person built this?" |
| 5. Settlements and cities | not started | cities in ≥5 biome mixes, one straddling a border |
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

---

## Phase 0: Scaffolding (done 2026-10-08; gate: awaiting John)

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

Six tracks run in parallel. Every alternative examined produces a one-page
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

Agents: `assistant` for tracks 1, 2, 3, 5, 6 (web research); `researcher`
(high effort) for track 4 (source reading in its own worktree, may write tiny
probe tests), for notes marked `deep-dive`, and for the synthesis.

- [x] **1.1 Track 1: Minecraft mods** (batches until the list is covered)
- [x] **1.2 Track 2: GDMC**
- [x] **1.3 Track 3: PCG literature**
- [x] **1.4 Track 4: Worldgen internals** (needs 0.5)
- [x] **1.5 Track 5: Region overlays**
- [x] **1.6 Track 6: Aesthetics**
- [ ] **1.7 Checkpoint: early skim**, after the first 8–10 notes: John redirects
  tracks or flags deep-dives. (Research keeps going meanwhile.)
- [ ] **1.8 Landscape synthesis**: a two-page comparison of approaches for
  buildings (grammar vs WFC vs graph-plus-templates hybrid) and for worldgen
  integration (structure-based vs biome overlay vs custom chunk generator; see
  ARCHITECTURE.md), with a recommended direction. `docs/research/synthesis.md`.

**Gate:** John picks the building-generation direction and confirms the
integration approach to spike (recorded as ADRs).

## Phase 2: Worldgen spikes

Small, throwaway proofs of concept answer the integration questions before the
core is designed around them. Each spike runs in its own worktree and ends with
a findings note (`docs/research/spikes/<id>.md`) plus one test that
demonstrates the result. **Spike code is not merged**; its lessons go into an
ADR. Process: coordinator.md, "Spikes".

Parallel: S1, S2, S4 and S5 side by side; S3 alongside them; S6 after S3
(it measures S3's multi-start city). S4's 2D map needs the viewer from 0.4.

- [ ] **2.1 S1 Biome footprint.** Can a structure sample biomes across a
  512×512 area at start time, and how fast? Evidence: GameTest logging the
  sampled grid; timing per start.
- [ ] **2.2 S2 Terrain probe.** How close is pre-generation height sampling to
  the real surface? Evidence: predicted vs actual heightmap diff over 10 seeds.
- [ ] **2.3 S3 Multi-start city.** Can 2–4 starts share one plan and join
  seamlessly? Evidence: blocks hashed per chunk are identical whatever order
  chunks generate in; no seams at start or chunk borders.
- [ ] **2.4 S4 Region map.** Does the region layer place cities sensibly
  against real biomes? Evidence: dev command dumping region cells plus biomes
  to JSON; 2D map in the viewer. (The dump format becomes a test fixture for
  Phases 3 and 5.)
- [ ] **2.5 S5 Terrain adaptation.** Do vanilla beard modes suffice on hills, or
  do we grade terrain ourselves? Evidence: client-test screenshots of a test
  city on flat, hilly and river terrain.
- [ ] **2.6 S6 Scale and cost.** What do piece counts do to save size and chunk
  generation time? Evidence: measurements at village, town and city scale;
  thread-safety check of the plan cache. (Timing under the heavy lock.)
- [ ] **2.7 Worldgen ADR** (researcher) from the findings: integration approach, practical
  size limits, determinism rules.

**Gate:** the worldgen ADR fixes the integration approach, the practical size
limits and the determinism rules. S3's order-independence test becomes a
permanent regression test (re-implemented properly, in Phase 3 or 6).

## Phase 3: Planner core and three.js viewer

Build the review loop and a deliberately plain first generator: one building on
one lot, viewable from any seed.

Parallel: 3.1 (IR and its JSON schema, with a checked-in sample fixture) comes
first. Then the planner track (3.2 → 3.3 → 3.4, with 3.9–3.11 alongside 3.3)
and the viewer track (3.5 → 3.6, 3.7, 3.8, working against the fixture) run
side by side.

**Planner core (Java, no Minecraft)**

- [ ] **3.1 Building IR**: footprint, floors, rooms, openings, roof, abstract
  material roles; JSON serialisation with a stable, documented schema; a sample
  fixture for the viewer.
- [ ] **3.2 Probes**: interfaces for terrain height and biome; synthetic
  implementation; recorded implementation reading S4's dump.
- [ ] **3.3 Generator v0**: rectangular footprint, 1–3 floors, doors and
  windows, gable roof.
- [ ] **3.4 CLI**: `gen --seed --lot --style` writes JSON.

**Viewer (TypeScript, three.js)**

- [ ] **3.5 Instanced voxel mesh** with orbit controls; colour by role or by
  mapped palette.
- [ ] **3.6 Controls**: seed scrubber, style switch, layer toggles (shell,
  interior, roof).
- [ ] **3.7 Invariant overlay**: failures from the test suite drawn in red.
- [ ] **3.8 Seed gallery**: a 4×4 grid of seeds from fixed camera presets;
  Playwright captures it as PNGs, so agents review the same pictures John does.

**Tests**

- [ ] **3.9 Property tests** over thousands of seeds: every room reachable from a
  door, roof closed, no floating blocks, stays in its lot.
- [ ] **3.10 Determinism**: same seed gives byte-identical JSON.
- [ ] **3.11 Diversity score** across a seed batch, so sameness is measured and
  not just eyeballed.

**Gate:** John reviews the seed gallery and the invariant report.

## Phase 4: Building generator depth

Grow v0 into buildings that read as built by a person, using the technique
chosen at the Phase 1 gate. A likely shape is a hybrid: grammars for massing and
facades, graph-based floor plans, and small hand-authored detail pieces.

Each new rule lands with a property test and a gallery update; the verifier
compares galleries before and after. Parallel: after a short planning pass
(lead-coder, `mode: plan`) fixes the extension points, massing, roofs, facades
and interiors can largely proceed side by side, then building types and styles
fan out one per item.

- [ ] **4.1 Plan**: the generator architecture for the chosen technique, with
  the extension points that let 4.2–4.7 run in parallel.
- [ ] **4.2 Massing**: L, T, U and courtyard footprints; multi-volume buildings;
  towers and wings.
- [ ] **4.3 Roofs**: gable, hip and combinations; dormers; overhangs; pitch per
  style.
- [ ] **4.4 Facades**: bays and rhythm, trim, depth (insets, beams, sills), a
  gradient of materials.
- [ ] **4.5 Interiors**: room graph, stairs, simple furnishing hooks.
- [ ] **4.6 Building types**: house, shop, workshop, tower, temple, wall and
  gate (one item each when scheduled).
- [ ] **4.7 Styles**: a palette and rule set per culture, mapped from biome
  (desert, taiga, plains, jungle, snowy to start; one item each).

**Gate:** per building type, a gallery passes John's "would I believe a person
built this?" review.

## Phase 5: Settlements and cities

Compose buildings into places, still entirely outside Minecraft, using real
terrain and biome dumps from S4.

- [ ] **5.1 Scale ladder**: hamlet, village, town, city. Each step adds
  structure (a square, a main street, districts, walls).
- [ ] **5.2 Layout**: road network (primary and secondary), lot subdivision,
  plazas and landmarks, gates.
- [ ] **5.3 Terrain**: respond to slope, rivers and coast; choose where to level
  ground and where to step buildings.
- [ ] **5.4 Biome-aware style**: each district takes its palette from the biomes
  underneath, blended across borders.
- [ ] **5.5 Viewer**: a 2D plan view over the terrain dump, plus a 3D fly-through
  of a chosen district.
- [ ] **5.6 Determinism**: the city plan is built from the region cell alone, so
  every district start reproduces it identically.

**Gate:** John reviews cities in at least five biome mixes, including one
straddling a border.

## Phase 6: Minecraft integration and release

Wire the proven core into worldgen using the approach fixed in the Phase 2 ADR.

- [ ] **6.1 Adapter**: palette roles to block states; IR to structure pieces,
  clipped per chunk.
- [ ] **6.2 Registration**: structure type, structure set with data-driven
  spacing, region layer, biome tags for where settlements may appear.
- [ ] **6.3 Terrain**: adaptation mode or our own grading, per S5.
- [ ] **6.4 Gameplay hooks**: loot tables, villager workstations and beds,
  `/locate` support. Leave room for a villager AI mod later.
- [ ] **6.5 Config**: frequency, maximum city size, enabled styles.
- [ ] **6.6 Compatibility**: test with popular terrain mods (Terralith,
  Tectonic) and alongside vanilla villages.
- [ ] **6.7 Performance budget**: worldgen time per chunk and save size per
  city, checked in CI.
- [ ] **6.8 Client tests**: fly-through screenshots of fixed seeds.

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
