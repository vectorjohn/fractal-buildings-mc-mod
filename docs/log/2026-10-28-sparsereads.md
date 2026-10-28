# 2026-10-28 SPARSEREADS: read fewer exact terrain columns (spike-first)

John asked whether a checkerboard (half) or one-in-three read pattern would still give the road planner the same terrain shape. Measured; **no mode pays, nothing is on by default.** Full results and per-consumer recommendations: [review/sparse-reads.md](../review/sparse-reads.md).

## Done

- `core/probe/SparseTerrain`: opt-in `TerrainProbe` wrapper (`checker`, `third`; mean of read neighbours; a column whose read neighbours disagree on wet or dry, or on floor by more than an optional `maxSpread`, is read exactly), `SparseTerrain.region(..)` for the 64-block lattice, spec modifier `+sparse:<mode>[:<spread>]`, `city-check --terrain-mod`. `SparseTerrainTest` (5 tests).
- Harness `planner-core/src/test/.../world/SparseReadsTool` (a `main`; `city`, `region`, `routes`, `stations`) with `settlement/SparseHooks` (one public handle on `Districts.quayRuns`). Raw outputs in `docs/review/sparse-reads-data/`.
- Touched none of PARREADS' files (`ColumnMemo`, `LiveTerrainProbe`, `SparseColumns`, `ChunkFill`, `LiveProbes`, `WorldPlanner`); only `ProbeSpecs`, `CityCheckCommand`, `Main` outside new files.

## Verified

- `SparseTerrainTest` 5/5; mutation: removing the wet/dry guard fails `shoreIsNeverSmoothedAcrossAndSurfacesStayFlatInALake`.
- Harness runs: city pipeline on the nine gate windows (step-8 dumps) and on four re-dumped at step 2 (vanilla noise); region lattice and road graph on the two world dumps; 140 routes replayed on 4-block windows; `city-check` on the nine gate windows with `exact`, `checker`, `third` (0 violations in all three); plan pictures of two cities.
- Machine load 33 to 53 throughout, so planner times on the dumps are not used (recorded columns are free anyway); the harness counts distinct columns and chunks.

## Findings

- A windowed chunk fill is 0.008 ms a column, a single column 0.35 ms: thinning a chunk-resident consumer saves nothing and thinning a line-shaped one adds reads (J: +50 % columns and chunks with checker).
- Region decisions and routes are threshold-sensitive: 17 to 33 % of settlements and 15 to 30 of 73 links change.
- Stations: k = 2 loses a quarter of the crossings, k = 3 half.
- Shape survives (junction level mean difference 0.1, quay layout 97.5 to 100 % of columns with checker); the plan does not (lots reflow: 22 to 37 % differ with third).

## Surprises

- Only the harness's first run hung: a `main` that throws in a pool thread leaves the JVM alive (non-daemon pool); and `BiomeClass` needs `build/resources/main` on the classpath. Both in NOTES.
- I ran the fine-window pass once against the old compiled class (it printed the step-8 numbers again); caught because the temperate-city row was byte-identical, rebuilt and rerun.

## Not done

- No in-game measurement (model only, see ASSUMPTIONS). The sparse *fill* idea (noise at the 4 x 4 cell corners) is not tried: it is a `ChunkFill` change, PARREADS' file.
