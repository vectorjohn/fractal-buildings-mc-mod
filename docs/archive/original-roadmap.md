# Procedural Buildings Mod — Roadmap

Oct 7, 2026 · @John

## Goals and principles

Build a Fabric mod that generates varied, believable buildings and cities from rules rather than stamped templates. Research and a three.js review loop come first; worldgen integration is proven with spikes before any architecture is locked in.

**Goals**

- Every building is generated, so no two look alike, while staying recognisably part of a style.
- Cities can span biome boundaries. Each district picks its style from the biomes underneath, giving desert towns, forest towns, and towns that change across a border.
- Everything is deterministic from the world seed, and chunks still generate independently and in any order.
- Every generator output can be reviewed outside Minecraft in a three.js viewer, by you and by agents.

**Principles**

- **Pure core.** Planning code never imports Minecraft. Minecraft is an adapter at the edge.
- **Abstract materials.** Generators emit roles (wall, trim, roof, floor). Palettes map roles to blocks per style or biome.
- **Research, then spikes, then architecture.** Each decision is recorded as a short ADR in the repo.
- **Review gates.** Each phase ends with something you look at and approve before the next one starts.

&#91;embedded content: roadmap · 7 phases, 7 review gates\]

Phases run in order, but Phase 1 never blocks you: each overview reaches you as soon as it is written.

## Worldgen primer: where cities fit

Yes, a city can be planned knowing the biomes it covers. Biomes are a pure function of seed and position, so a structure can sample them anywhere without generating chunks. Everything here is a working hypothesis for Phase 1 track 4 to confirm against the 26.x sources.

**Generation runs in stages, per chunk.** Each chunk moves through these steps in order:

1. **structure\_starts:** decides which structures begin in this chunk and lays out all their pieces up front.
2. **structure\_references:** nearby chunks record which starts overlap them.
3. **biomes:** stores the chunk's biomes.
4. **terrain:** base terrain, surface blocks, carvers.
5. **features:** places features and structure pieces, clipped to this chunk.
6. **Lighting, spawning, then full.**

**Structures are decided before the chunk's own biome stage, but that doesn't matter.** Overworld biomes come from six climate parameters (temperature, humidity, continentalness, erosion, weirdness, depth) computed from noise. Structure code can query the biome source at any coordinate, including the whole footprint of a planned city. Vanilla structures already check biomes when they start.

**Terrain height can also be estimated before generation.** The chunk generator exposes base-height queries that structures like villages use to project to the surface. Exact method names in 26.x are to be confirmed.

**Size is the real constraint.** One jigsaw structure's pieces stay within 128 blocks horizontally of its start (116 with terrain adaptation). Pieces also only reach chunks that hold a reference to the start, and that search radius is believed to be about 8 chunks (to verify). A large city is therefore many coordinated structure starts, not one.

**Terrain adaptation exists.** Structures can reshape surrounding terrain: `beard_thin` (villages, outposts), `beard_box` (ancient cities), `bury` (strongholds), `encapsulate` (trial chambers).

### A second layer above biomes

The proposal is a **settlement region layer**: our own deterministic field, independent of vanilla biomes.

- The world is divided into large region cells (jittered grid or Voronoi), seeded from the world seed. Each cell decides whether it holds a settlement, its centre, size and culture.
- The city plan is a pure function of the cell. It is computed on first use and cached.
- Each district is its own structure start on a sub-grid inside the cell. It reads the shared plan and emits only its own pieces.
- Each district samples the biomes beneath it to choose a palette. A city straddling desert and forest gets sandstone streets on one side and timber on the other, with a blend at the border.

Three ways to integrate, to be compared in Phase 1 and tested in Phase 2:

| Approach | How | Gains | Costs |
| --- | --- | --- | --- |
| Structure-based (proposed) | Custom structure type plus region layer; vanilla biomes untouched | Works with other biome and terrain mods; uses vanilla piece clipping and saving | Must coordinate many starts; bounded by reference radius |
| Biome overlay | Inject an urban biome into the biome source (TerraBlender-style) | Cities affect grass, mobs and surface rules | Invasive; conflicts with biome mods; defeats cities crossing biomes |
| Custom chunk generator | Own generator owns terrain and cities (The Lost Cities model) | Total control over layout and terrain | Poor compatibility; must reimplement or wrap vanilla terrain |

## Architecture sketch

A pure planner produces one Building IR. The three.js viewer and the Fabric adapter are two consumers of that same IR.

&#91;embedded content: architecture · planner core, probes, two outputs\]

Probes are the key seam. Behind the same interface, the planner can read synthetic terrain in unit tests, terrain recorded from a real world in the viewer, or live terrain in Minecraft. Bugs found in-game can therefore be replayed outside it.

## Phase 0: Scaffolding

A short phase: set up the repo so research notes and spikes have a home.

- [ ] Repo layout: `planner-core/` (pure Java, no Minecraft), `cli/`, `viewer/` (TypeScript + three.js), `mod/` (Fabric), `docs/research/`, `docs/adr/`.
- [ ] `CLAUDE.md`: module boundaries (core never imports `net.minecraft`), build and test commands, the review conventions below.
- [ ] Templates: a research one-pager (§ Phase 1) and a short ADR (context, options, decision, consequences).
- [ ] `docs/research/INDEX.md`: one line per note, status, and link. Coordinator keeps it current.
- [ ] Smoke path: the CLI writes a hard-coded 5×5×5 box as JSON, and the viewer renders it.

**Gate:** builds green, and the viewer renders the box.

## Phase 1: Prior art research

Six tracks run in parallel as subagents. Every alternative examined produces a one-page overview the moment it is done, so you can dig into options while research continues. Nothing waits for a final report.

**How notes flow to you**

- One note per project, paper or technique, in `docs/research/<track>/<slug>.md`, at most a page.
- The coordinator adds a line to `INDEX.md` as each note lands: name, one-line verdict, status (`new`, `reviewed by John`, `deep-dive`).
- Mark a note `deep-dive` to have the track spend more time on it. Mark it `drop` to stop.

**One-pager template:** what it is (link), approach in three sentences, procedural vs templated, how it hooks into worldgen, licence, what to borrow, what to avoid, verdict.

| Track | Questions | Starting list |
| --- | --- | --- |
| 1. Minecraft mods | How do existing mods make large or varied settlements? Where do they hit limits? | [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities), YUNG's structure mods, ChoiceTheorem's Overhauled Village, Towns and Towers, Repurposed Structures, Moog's structures, Recurrent Complex, [Astruct](https://www.curseforge.com/minecraft/mc-mods/astruct) |
| 2. GDMC | What settlement generators exist that adapt to real terrain? | [GDMC-HTTP](https://modrinth.com/mod/gdmc-http-interface) and GDPC, competition entries and write-ups |
| 3. PCG literature | Which building and city techniques give variety with coherence? | Parish and Müller (city layout), CGA shape grammars, split grammars, WFC and model synthesis, Townscaper, Watabou's city generator, residential floor-plan generation |
| 4. Worldgen internals | How do 26.x structure, placement, biome-source and terrain-height APIs actually work? | Decompiled sources in the dev env, Fabric worldgen APIs, [jigsaw](https://minecraft.wiki/w/Jigsaw_structure) and [worldgen](https://minecraft.wiki/w/World_generation) wiki pages |
| 5. Region overlays | How have mods layered new regions over vanilla biomes? | TerraBlender, Lithostitched, Terralith, The Lost Cities' city/biome handling |
| 6. Aesthetics | What makes a Minecraft build read as real? | Builder guides on depth, palettes, gradients and roof pitch; vanilla village variants as a baseline |

Track 4 needs the dev environment. It runs as a source-reading agent in its own worktree and may write tiny probe tests. The other tracks are web research.

**Checkpoints**

1. **Early skim**, after the first 8–10 notes: you redirect tracks or flag deep-dives.
2. **Landscape synthesis:** a two-page comparison of approaches for buildings (grammar vs WFC vs graph-plus-templates hybrid) and for worldgen integration, with a recommended direction.

**Gate:** you pick the building-generation direction and confirm the integration approach to spike.

## Phase 2: Worldgen spikes

Small, throwaway proofs of concept answer the integration questions before the core is designed around them. Each spike runs in its own worktree. It ends with a findings note plus one test that demonstrates the result. Spike code is not merged; its lessons go into an ADR.

| Spike | Question | Evidence it must produce |
| --- | --- | --- |
| S1 Biome footprint | Can a structure sample biomes across a 512×512 area at start time, and how fast? | GameTest logging the sampled grid; timing per start |
| S2 Terrain probe | How close is pre-generation height sampling to the real surface? | Predicted vs actual heightmap diff over 10 seeds |
| S3 Multi-start city | Can 2–4 starts share one plan and join seamlessly? | Blocks hashed per chunk are identical whatever order chunks generate in; no seams at start or chunk borders |
| S4 Region map | Does the region layer place cities sensibly against real biomes? | Dev command dumping region cells plus biomes to JSON; 2D map in the viewer |
| S5 Terrain adaptation | Do vanilla beard modes suffice on hills, or do we grade terrain ourselves? | Client-test screenshots of a test city on flat, hilly and river terrain |
| S6 Scale and cost | What do piece counts do to save size and chunk generation time? | Measurements at village, town and city scale; thread-safety check of the plan cache |

**Gate:** a worldgen ADR fixes the integration approach, the practical size limits and the determinism rules. S3's order-independence test becomes a permanent regression test.

## Phase 3: Planner core and three.js viewer

Build the review loop and a deliberately plain first generator: one building on one lot, viewable from any seed.

**Planner core (Java, no Minecraft)**

- Building IR: footprint, floors, rooms, openings, roof, and abstract material roles.
- Probe interfaces for terrain height and biome, so the planner runs with synthetic terrain, recorded terrain from S4's dump, or live Minecraft.
- Generator v0: rectangular footprint, 1–3 floors, doors and windows, gable roof.
- CLI: `gen --seed --lot --style` writes JSON.

**Viewer (TypeScript, three.js)**

- Instanced voxel mesh with orbit controls; colour by role or by mapped palette.
- Seed scrubber, style switch, layer toggles (shell, interior, roof).
- Invariant overlay: failures from the test suite drawn in red.
- Seed gallery: a 4×4 grid of seeds from fixed camera presets.
- Playwright captures the gallery as PNGs, so agents review the same pictures you do.

**Tests**

- Property tests over thousands of seeds: every room reachable from a door, roof closed, no floating blocks, stays in its lot.
- Determinism: same seed gives byte-identical JSON.
- A simple diversity score across a seed batch, so sameness is measured and not just eyeballed.

**Gate:** you review the seed gallery and the invariant report.

## Phase 4: Building generator depth

Grow v0 into buildings that read as built by a person, using the technique chosen at the Phase 1 gate. A likely shape is a hybrid: grammars for massing and facades, graph-based floor plans, and small hand-authored detail pieces.

- **Massing:** L, T, U and courtyard footprints; multi-volume buildings; towers and wings.
- **Roofs:** gable, hip and combinations; dormers; overhangs; pitch per style.
- **Facades:** bays and rhythm, trim, depth (insets, beams, sills), a gradient of materials.
- **Interiors:** room graph, stairs, simple furnishing hooks.
- **Building types:** house, shop, workshop, tower, temple, wall and gate.
- **Styles:** a palette and rule set per culture, mapped from biome (desert, taiga, plains, jungle, snowy to start).

Each new rule lands with a property test and a gallery update. Agents compare galleries before and after a change.

**Gate:** per building type, a gallery passes your "would I believe a person built this?" review.

## Phase 5: Settlements and cities

Compose buildings into places, still entirely outside Minecraft, using real terrain and biome dumps from S4.

- **Scale ladder:** hamlet, village, town, city. Each step adds structure (a square, a main street, districts, walls).
- **Layout:** road network (primary and secondary), lot subdivision, plazas and landmarks, gates.
- **Terrain:** respond to slope, rivers and coast. Choose where to level ground and where to step buildings.
- **Biome-aware style:** each district takes its palette from the biomes underneath, blended across borders.
- **Viewer:** a 2D plan view over the terrain dump, plus a 3D fly-through of a chosen district.
- **Determinism:** the city plan is built from the region cell alone, so every district start reproduces it identically.

**Gate:** you review cities in at least five biome mixes, including one straddling a border.

## Phase 6: Minecraft integration and release

Wire the proven core into worldgen using the approach fixed in the Phase 2 ADR.

- **Adapter:** palette roles to block states; IR to structure pieces, clipped per chunk.
- **Registration:** structure type, structure set with data-driven spacing, region layer, biome tags for where settlements may appear.
- **Terrain:** adaptation mode or our own grading, per S5.
- **Gameplay hooks:** loot tables, villager workstations and beds, `/locate` support. Leave room for the villager AI mod later.
- **Config:** frequency, maximum city size, enabled styles.
- **Compatibility:** test with popular terrain mods (Terralith, Tectonic) and alongside vanilla villages.
- **Performance budget:** worldgen time per chunk and save size per city, checked in CI.
- **Client tests:** fly-through screenshots of fixed seeds as golden images.

**Gate:** a fresh world on three seeds plays well for an hour; then a first public release.

## Testing, review and agent workflow

Each layer has its own test, and most checks run without starting Minecraft.

| Layer | Checked by | Runs |
| --- | --- | --- |
| Planner rules | Property tests over thousands of seeds | Every build, seconds |
| Determinism | Byte-identical JSON per seed; chunk-order-independent block hashes (from S3) | Every build |
| Look | Seed galleries rendered by the viewer via Playwright | On change, reviewed by agents and you |
| Placement in world | Fabric server GameTests | Every build |
| In-game appearance | Fabric client game tests with fixed-seed screenshots | Before each review gate |

**Agent roles**

- **Coordinator:** (coordinator) owns this roadmap, `INDEX.md` and the ADRs; assigns work; never merges spike code.
- **Research agents:** (lead-coder) one per Phase 1 track; write one-pagers.
- **Source reader:** (lead-coder) reads decompiled 26.x code in a worktree; answers API questions with file and line references.
- **Builders:** (coder, lead-coder in rare cases) planner, viewer and mod work on `claude/*` branches; 
- **Reviewer:** (verifier) a fresh agent compares galleries before and after each generator change. Also code reviews / validating tests run. This is the agent that gives the stamp of approval and merges.

Coordinator and Reviewer are free to merge into upstream branch. Any agent is free to merge changes from parent branches.

## Open questions and risks

- **Core language:** Java keeps one codebase with the mod; Kotlin is pleasanter but adds a runtime dependency. A TypeScript prototype would be faster to iterate but means porting later. Keep in mind what might be best represented in Claude training data.
- **City size vs reference radius:** if coordinated starts prove awkward, a custom structure placement or deferred placement (as Astruct does) may be needed.
- **Plan cache under worldgen threads:** must be deterministic, bounded and thread-safe.
- **Hard terrain:** mountains, rivers and coastlines may need settlement-specific rules or simply avoidance.
- **Mod compatibility:** terrain overhaul mods change heights and biomes; probes must not assume vanilla.
- **Version churn:** Minecraft is now on 26.3, and worldgen APIs move between releases. Keep the adapter thin.
- **Licences:** study code only as each mod's licence allows; record the licence in every one-pager.
- **Scope creep:** villager AI, quests and interiors-as-gameplay belong to later projects.

## Sources

- [World generation](https://minecraft.wiki/w/World_generation), Minecraft Wiki: generation steps, climate parameters
- [Jigsaw structure](https://minecraft.wiki/w/Jigsaw_structure), Minecraft Wiki: size limits, terrain adaptation modes
- [Automated Testing](https://docs.fabricmc.net/develop/automatic-testing), Fabric docs: JUnit, server and client game tests
- [The Lost Cities](https://www.curseforge.com/minecraft/mc-mods/the-lost-cities), CurseForge listing
- [Astruct](https://www.curseforge.com/minecraft/mc-mods/astruct), CurseForge listing
- [GDMC HTTP Interface](https://modrinth.com/mod/gdmc-http-interface), Modrinth listing
