# Testing: which layers a change needs

The layers themselves are in [ROADMAP.md](ROADMAP.md), "Testing strategy"; the commands in `CLAUDE.md`, "Build and test commands". This page says **which** of them a given change has to run. The heavy, in-world layers (client game tests, the chunk-order test, perf) cost tens of minutes and hold the one heavy lock, so they run only when a change can affect what they check.

# The rule

- **Run what `scripts/test-scope.sh` says** for your branch (`scripts/test-scope.sh <run branch>`, or a range `A..B`; `--explain` shows why each file maps where it does). It reads only git, in seconds.
  - **required** layers: run them (heavy ones through `scripts/evidence.sh <slug> <command>`, as now).
  - **consider** layers: run them when the printed reason applies to your change, and say in your report which you ran or skipped and why.
  - **UNKNOWN** files (no row in the table): every layer is required until the verifier narrows it; add a row in the same branch.
  - **new block kinds**: a block id the generators never emitted before that the world changes after we write it (falls, flows, needs support, is two blocks, or is shaped by neighbours). That puts the order test back in.
- **The coder** runs the required layers and pins heavy evidence. **The verifier** runs `scripts/test-scope.sh` on the branch itself, checks the coder ran what it asks, and **escalates when unsure** (a "consider" layer that plausibly applies, a change whose reach the paths don't show, such as a building change that moves footprints or claims ground): run the layer, or ask the coordinator for it.
- **Always everything**: the batched heavy run on the run branch and the merge to `main` run every layer whatever the paths say. They catch what the table misses; a miss found there is a new or corrected row.
- The fly-through runs only at gates, on request, or when its own files change.

The mapping lives in one table, [`scripts/test-scope.tsv`](../scripts/test-scope.tsv): a `path` row per area (first match wins), a `content` row per risky block kind, and a `layer` row per layer with its command. Change the table, not the script, when an area's reach changes; `scripts/test-scope.sh --check-table` lists tracked files no row maps. The client layer names (`FB_LAYER=quick|full`) and the order harness follow the TESTLAYERS item; the table's commands are updated when it lands.

# What each area can break

| Area (paths) | Required | Why |
|---|---|---|
| docs, `.claude/`, process scripts | none | |
| building generators (`core/gen/**`, `grammar`, `style`, `ir`, `json`, styles JSON) | fast, gallery (perf-planner, client-quick for interiors: consider) | content of one building inside its lot; order is a core property (below) |
| `core/raster` | fast, gallery (order, client-quick: consider) | exactly what the mod writes; the core proves clip independence |
| `core/world`, `cache`, `probe`, `rand` | fast, client-full, perf, order (consider for `world` and `probe`: the order sim covers them; required for `cache`, `rand`) | the mod's only door into the core; threads and caches |
| `core/settlement`, `terrain`, `road`, `region` | fast, city, client-full (+ order for settlement and terrain, perf) | ground pieces, grading, roads and where settlements go |
| `mod/.../worldgen`, `probe`, `block` | fast, order (consider for the spill, air-clear, tile and lake-veto passes: the order sim covers them), client-full or quick, perf | the write path, spill and foliage passes, placement, threads |
| `mod/.../roads`, `compat`, mixins | fast, client-full (compat, perf: consider; order: consider for roads, required for mixins) | road feature (own order digest), veto |
| `mod/.../gameplay`, `config`, `teleport`, entrypoint, resources | fast, client-quick (server-only: consider) | |
| viewer, CLI | fast, gallery | |
| build files, `flake.*`, `gradle.properties` | everything | versions move vanilla behaviour and mixin targets |

# Where order independence is already proven

Chunk-order independence is mostly a property of the planner core, and the core proves it without Minecraft, in the fast suite:

- `WorldPlannerOrderTest`: plan, buildings and ground of 30 tiles of `gate-metropolis-mixed` are the same bytes forward, reversed, shuffled, on 8 threads, with every cache at capacity 1 to 3 and an evictor thread, and with prefetch on a pool (with mutants).
- `SettlementDeterminismTest`: settlements, district plans and scenes are byte-identical in order, shuffled, on 8 threads over a constantly evicting cache, and each district alone, for every city preset.
- `RasterOwnershipTest`: over real gate-city districts, every cell has one owner and the chunk-by-chunk raster equals the whole (shapes don't depend on the clip); doors and stairs face the right way.
- `GoldenDigestV1Test`, `SettlementGoldenTest`, `DeterminismTest`, `DeterminismLintTest`: run-to-run and JVM-to-JVM stability, and no unordered iteration or wall clock in core sources.
- In the mod build (also fast): `OrderGameTest` (block states the world keeps equal the raster across chunk borders, room air written, hooks in any chunk order), `WarmingGameTest` (prefetch changes speed only), `GameplayGameTest`, `LiveProbeTest` (live probes match the recorded dumps).

So the in-world order test (B2) adds evidence only for the **mod path**: Minecraft's own chunk pipeline calling our pieces in its order on its threads, `BlockMapper` and the write flags, the passes that **read the world** (`SpillClear`, `FoliageClear`, `HookPlacer`'s "is the cell air", `RoadFeature`'s ground scan), and vanilla features of neighbouring chunks writing into our cells.

# Edge cases: when a building change does reach the world

A change to how one building is generated can't change chunk-order behaviour by itself: its cells are a pure function of seed and position, and the core checks that under every clip and order. It can still change what the in-world tests see:

- **Footprint, height or ground claims.** Ground yields to buildings (`PieceRaster.ground`), so a building that grows past its lot or changes its base takes cells from the street, which moves where `SpillClear` and `FoliageClear` run and what `TerrainClientGameTest` measures (void under the footprint, burial). Run client-full when a change moves footprints, foundations or the lot edge.
- **New block kinds** (the `content` rows): gravity blocks, fluids, blocks that need support, two-block blocks, neighbour-shaped blocks. The world can change these after we write them; the core can't see it.
- **Hooks** (interiors): beds, chests and workstations become blocks in `HookPlacer`, which reads the world. `GameplayGameTest` covers it in the fast suite; client-quick when the kind or count of hooks changes.
- **Cost**: buildings are planned during chunk generation, inside the cold district budget, so a slower generator shows in `perf-planner`.

## Engine-free order sim (ORDERSIM)

`mod/src/test/java/dev/fractalbuildings/mod/ordersim/` (plain JUnit in `:mod:test`, so in the fast suite, no server): real `ProtoChunk`s decorated chunk by chunk in six orders (forward, reverse, shuffles, spiral, columns) with the real `TilePiece`, `SpillClear`, `BuildingAirClear`, `StreetAirFix`, `FoliageClear`, `HookPlacer`, `RoadFeature.placeIn` and the lake and boulder veto decisions, against real vanilla features (trees, dark oak, lava lake, forest rock ...). It applies the order test's criteria (orders agree, claimed cells equal the reference raster, spill classified, write contracts) and carries regressions for ORDERFAIL, SPILLDIRT, TREEINORDER, FOLIAGECLUSTER and ROCKVETO plus permanent mutants. About 3 minutes of test time (the figure is in `NOTES.md`); `OrderSimMutantTest` (tests the checks, not the product) is `@Tag("heavy")` and runs with `-PfbTests=heavy` / `scripts/test-all.sh --planner-heavy`, and whenever the sim's checks change.

So `scripts/order-test.sh` (about 10 minutes) is **advisory (`order?`)** for the paths the sim covers (`SpillClear`, `FoliageClear`, `StreetAirFix`, `BuildingAirClear`, `TilePiece`, `LakeVeto`, `mod/roads`, `core/world|settlement|terrain|probe`): run it when the change touches the pipeline, threads, structure data or a new block kind. It stays **required** where the sim cannot vouch:

- build files, `gradle.properties`, the flake (Minecraft or Fabric version bump);
- `scripts/order-test.sh` and `gametest/order/*`, `Mutations`, `PieceLedger`, `RegionWarmer`, `Workers`, `core/cache`, `core/rand` (what the order test checks, and the threads);
- every mixin (the sim does not apply mixins; `RockVetoGameTest` and `mutant-lakes` check they are applied);
- structure starts and placement (`DistrictStructure`, `WorldPlanners`, the `worldgen/*` catch-all, `data/*/worldgen/*`; the VILLROAD join waits for ORDERSIM S8);
- `mod/block/*` and new block kinds (the `content` rows: write flags and neighbour shapes are only partly modelled);
- the real chunk pipeline, block entities and loot.

The batched heavy run and the to-main run still run every layer, so a miss is found there and becomes a new sim scenario or a corrected row.

Examples, `scripts/test-scope.sh A..B` before and after (required layers beyond `fast`):

| Merged item | Before | After |
|---|---|---|
| TREEINORDER (`BuildingAirClear`) | client-full, order, perf | client-full; order: consider |
| FOLIAGECLUSTER (`FoliageClear`) | client-full, order | client-full; order: consider |
| ORDERSIM R3+S5 (`RoadFeature.placeIn`, roads) | client-full, perf | client-full, perf; order: consider |
| SPILLDIRT3 (`SpillKinds`, classifier used only by tests) | client-full, order, perf | order |
| ORDERSIM S0 (`Mutations`, seams) | client-full, order, perf | unchanged |

# What needs the engine

Only Minecraft can show these, so they stay in-world (server game tests where a flat world suffices, client or dedicated-server tests where real terrain is needed):

- vanilla features and carvers from neighbouring chunks writing into our cells after us: trees, rocks, ores, disks, lava lakes, cave air (the `Spill` catalogue), and our passes that clean up after them;
- the real chunk pipeline: start placement, `/locate`, piece bounding boxes, save and reload, worker threads and cache eviction under Minecraft's own scheduling;
- block states and write flags: neighbour-shaped blocks, two-block blocks, falling and flowing blocks, block entities (chests, loot seeds);
- the structure veto against vanilla villages and other worldgen mods (compat matrix);
- live probes against the noise router (cheap: `LiveProbeTest` in the fast suite);
- how it looks and feels: screenshots, the fly-through, performance.

Everything else (layout, rules, determinism, raster ownership, shapes, styles) is checked faster and more completely in the core and the viewer.

# Heavy-tagged planner tests

A planner-core test that costs minutes even alone is tagged `@Tag("heavy")` (JUnit) and left out of `:planner-core:test`, so the default suite stays about 10 minutes. `./gradlew :planner-core:test -PfbTests=heavy` runs only the tagged ones, `-PfbTests=all` runs everything; `scripts/test-all.sh --planner-heavy` runs them after the default run (step `planner-heavy`, about 80 min). It is not part of `--full` (the batched heavy runs skip it); the to-main run uses `--full --planner-heavy --perf`. A test is moved there only when a cheaper variant of the same check stays in the default run (say which in the test's javadoc). Today: `ApproachCacheTest` (every cache at 1 on the two metropolis chunks, about 40 min each; the default run shrinks one layer at a time and uses all-at-1 on the hamlet gate) and `SettlementDeterminismTest.everyPresetPlansTheSameBytesEveryWay` (all ~25 city presets; the default run keeps `synth-city`, `river-town` and `gate-joined-city`) and `DistrictsTest.invariantsHoldOverTheOtherSyntheticSites` (sites 75..149 of the 150; the default run checks 0..74 with the same code). A change to caches, threads or the settlement planner that can reach these should run `-PfbTests=heavy` before the merge to main.
