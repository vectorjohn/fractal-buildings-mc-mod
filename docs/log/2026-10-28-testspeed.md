# TESTSPEED: planner-core suite time (2026-10-28)

## Why it was slow
`ApproachCacheTest` compares the approach road columns of four chunks between a default-cache planner and `Caches.all(1)` (strict LRU, every layer at one entry). Alone on this host (load ~5) it took **2555 s**; the 30 to 50 minutes in verifier runs is the test itself, not contention (postmerge run: 2190 s, metrofill-r2: 300 s on another load). With all caches at 1, `roadColumnsAround` replans the settlement's tiles/skeletons/plans for each of the nine neighbour chunks and each settlement near, and each of those recomputes its dependencies in turn. Measured on chunk 200,106 (metropolis), one `roadColumnsAround`:

| caches | time |
|---|---|
| defaults | 1.4 s |
| tiles 1 / skeletons 1 / sources 1 / plans+buildings+ground 1 (one layer) | 1.7 / 1.8 / 3.6 / 3.8 s |
| region 1 | 15 s |
| all 2 | 36 s |
| tiles, sources, skeletons, plans, buildings, ground at 1 (region default) | 246 s |
| all 1, 226,90 (hamlet gate) / 227,90 | 12 to 30 s / 6 s |
| all 1, 204,110 (metropolis) | > 800 s (not finished) |

## Fix
- `ApproachCacheTest`: default tests shrink one layer at a time (tiles, sources, skeletons, region, plans+buildings+ground) over all four chunks (55 s) and use all-at-1 on the hamlet gate chunks (24 s); the original all-at-1 case on the two metropolis chunks is `@Tag("heavy")`. Class: 2555 s to 80 s.
- Mutation: in `WorldPlanner.roadColumnsAround`, drop the first column of an approach chunk when `caps.region() == 1` -> both default tests fail (2 failed); reverted. (Before the change the same mutation would have failed the single test after 42 min.)
- Heavy tag: root `build.gradle` excludes `heavy` by default; `-PfbTests=heavy` includes only them, `-PfbTests=all` everything. `scripts/test-all.sh --full` runs `:planner-core:test -PfbTests=heavy` as step `planner-heavy`. CLAUDE.md, docs/TESTING.md updated.
- `SquareLotsTest`: both statistics tests planned and filled the same sites; the first 150 are now memoised (`filled(i)`), the share test uses 250 sites instead of 400 (eligible squares 112, share 0.63, bounds 0.45 to 0.75 unchanged). 540 to 910 s -> 139 s.
- `SettlementDeterminismTest`: the preset test ran ~25 city presets (500 to 1070 s). Default is now `DEFAULT` (synth-city, river-town, gate-joined-city; 8 to 12 s each); `everyPresetPlansTheSameBytesEveryWay` is `@Tag("heavy")`. Class 1070 s to ~70 s. Mutants stay in the default run.

## Slowest classes before (postmerge XML, s) and left
ApproachCache 2190, SquareLots 538, SettlementDeterminism 494, Districts 199, GroundWalk 154, WallWalk 134, BuildingCellExtent 133, WorldPlannerOrder 106, LotRail 74, TownProgram 69. Follow-ups: DistrictsTest (200 to 325 s), GroundWalkTest, WallWalkTest, BuildingCellExtentTest, WorldPlannerOrderTest (100 to 260 s each) all re-plan synthetic sites through `DistrictsTest.planned`; a shared cross-class memo is not possible with per-class forks, but a smaller default site count with the rest heavy-tagged would work. Not done here.

Suite wall time: see the report.
