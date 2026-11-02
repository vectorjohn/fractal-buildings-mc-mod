# Plan: QUAYLAZY, a lazy quay trace that runs in parallel

Lead-coder plan, 2026-10-31, branch `claude/p6-quaylazy-plan` (base `claude/run-phase6` at 56f47da1).
Item: QUAYLAZY. John decided it on 2026-10-31 at the end of the COLDARRIVAL thread in
[regionperf-decision.md](../review/regionperf-decision.md): section 8 option 4, built as section 6 (a), trace
lazily from the tile that asks, and (c), parallelise per water body. Background: the regionperf brief sections 6
to 8, [profile-metropolis.md](../review/profile-metropolis.md) (quays are 19,854 chunks, 65 % of the city's
chunks), PERFORMANCE.md "Quays front-load probes" and "PARREADS", NOTES "Quays (METROFILL R1 r3)" and
"JUNCTION r3".

# Goal

A waterfront tile's structure start, and the teleport hold for a waterfront tile, should pay only for the quays
near that tile, not for the whole settlement's waterfront. Today that whole trace takes about 20 s on a quiet
The perf worker (`region.cold.quays.ms`) and runs inside a vanilla structure start whenever the warmer hasn't done it
first. **The output must not change at all**: every tile gets the same quay runs, byte for byte, as the current
code. That one property gives us `tile-border-agree`, determinism and "no visual review needed" in a single test.

# What the code does today (checked, Districts.java at 56f47da1)

- `Build.quayWholes()` (Districts.java:2080) takes the settlement-wide value
  `src.shared("quays", exact, this::settlementQuays)` and keeps the runs whose footprint **bounding box** lies
  within `m = MIN_LEN + MARGIN + 2*STEP = 64` blocks of the tile.
- `settlementQuays()` (2110):
  1. `wf` is every owned tile that `hasWaterfront` screens as waterfront. This is cheap: density and quarter
     field only, no exact reads.
  2. `Quays.contour(traced, x0..x1)` runs over the bounding box of `wf` tiles ± PAD. `traced(x, z)` is false
     outside the area and `nearWater(x, z)` inside it. `nearWater` reads the 4-lattice within 8 blocks of
     **every 8-lattice node of the area**.
  3. For each chain: `StreetCols.columns`, then `breakRing` for rings, then `StreetCols.runs(cols, ok)`; runs
     shorter than `MIN_LEN` 40 are dropped.
  4. `canonicalQuays(runs)` orients each run from its smaller end key and sorts the runs by (smallest column
     key, first key, size). Each run is graded in that order with `layers` = the junction field plus **every
     earlier part whose cells fall in its bounding box ± 1** (`quayParts` → `meetPins` → `gradeQuay`).
- `quays()` (2048) turns each whole into pieces. The piece id is `"quay" + first column`, so it comes from the
  geometry. Link lanes are `"k" + cn`, with `cn` counted per tile over the **order of that tile's
  `quayWholes()` list**. They link to `base` = the tile's raws before any quay, which is the street plan only.
- `quayOk` (2033) is pointwise. `junctions().splits` reads the settlement junction field (the street plan),
  never other quays. `nearAbsentBorder` goes through `rules` and is pointwise.
- `meetPins` (1579) reads a layer only at the run's footprint cells (`c-2..c+1`) and the ring around them
  (`c-3..c+2`). A layer with no touching cell adds nothing (`runPins` returns null, 1660). The per-column
  accumulation is sum, count, min and max, so **layer order doesn't matter**. Run R depends on an earlier part
  Q only when some column of Q is within **4 blocks (Chebyshev)** of a column of R.

# Measured: where the reads go (probe, recorded terrain, not committed)

A throwaway probe (temporary hooks in `settlementQuays` plus a test counting the distinct 16 x 16 chunks of
exact reads per phase) ran on the committed recorded fixtures. Code: the scratchpad of this session, not
committed. Recorded terrain, so these are chunk counts, not times:

| Settlement | wf tiles | chains | area-scan chunks (contour) | + run-phase chunks | band of all chains (upper bound) | largest chain band | per-tile lazy (scan box + its chains), min / median / max |
|---|---|---|---|---|---|---|---|
| pt1573 s4_m2 (metropolis) | 125 | 83 | 14,459 | 3,512 | 7,778 | 1,350 | 380 / 1,368 / 2,438 |
| pt1573 s4_m5 | 70 | 28 | 7,697 | 1,539 | 3,761 | 1,884 | 1,552 / 2,212 / 3,633 |
| pt1573 s5_m4 | 11 | 14 | 1,400 | 289 | 854 | 274 | 425 / 640 / 950 |
| w12345 s1_m5 (river town) | 7 | 3 | 897 | 135 | 541 | 483 | 693 / 749 / 774 |
| w12345 sm3_m4 | 79 | 38 | 9,183 | 1,526 | 3,558 | 1,386 | 342 / 1,603 / 2,146 |

Bounding-box overlaps between a run and an earlier quay (possible quay-to-quay meetings) were 5 over the three
pt1573 settlements and 1 over the three w12345 ones.

What this says:

- **The cost is the area scan, not the chains.** The contour evaluates `nearWater` on every node of every
  waterfront tile (+16). That fills every chunk there: 14.5k of the 18k chunks in s4_m2, which matches the
  profile's 19,854 in the live world. A walk along a chain reads only a band about 2 chunks wide.
- **A lazy tile costs about 7 to 13 times less** than the whole trace on the metropolises (median 1,368 against
  about 17,970 chunks for s4_m2). Even a chain that runs round a whole lake costs only its band, never the area
  inside it. The coordinator's framing ("the saving is the chains never touched") undersells it: the saving is
  mostly the area nobody walks.
- The whole path (CLI, viewer, `warmQuays`) cannot avoid the area scan. Matching today's output exactly needs
  every chain, and finding every chain needs the field everywhere, because a small pond shows up only in exact
  reads. The whole path therefore stays and is only parallelised. The mod stops using it.

# Corrections to the coordinator's analysis of (a)

1. **The unit is a cluster of chains, not a single chain.** Grading a run takes the levels of earlier quays it
   touches (the "where it overlaps another quay" rule, JUNCTION). Two chains can touch: the two segments of a
   saddle cell are 5.7 blocks apart, which is within the 4-column reach once rasterised. So the lazy unit is the
   connected component of chains whose runs come within 4 blocks of each other. This is rare (at most 5
   candidates per settlement above) but it has to be handled or determinism breaks.
2. Piece ids are already geometric (`quay<x>_<z>` from the first column). The trace-order risk is the **link
   lane ids `k<n>`**, which are counted per tile in the order of `quayWholes()`. The lazy list must come out in
   exactly the global canonical order: sort by an order key (run sort key, then part index), never by
   chain or visit order.
3. Confirmed: the "squeezed between two streets" test and `quayLink` read only the street plan.
4. The `quayWholes()` filter uses the run's **bounding box**. A curved run whose box reaches the tile but whose
   columns don't would be missed by any local discovery. Such a run adds nothing to the tile: `pieces` keeps
   only columns in the tile ± MARGIN (`keep`, 1221), and street layers clip to the street's box. Step 2 changes
   the filter to "some column within m". That change doesn't alter the output, and the test proves it.
5. The whole-run rules (MIN_LEN, `breakRing`, grading) are applied to complete chains and complete clusters
   only, as the coordinator said.

# Approach

New class `QuayTrace` (planner-core `settlement`), one per settlement and exact probe, kept by
`src.shared("quayTrace", exact, ...)`. Building it is cheap: it only computes the area.

- **Area**: `wf` and the contour's cell window `i0..i1, j0..j1`. The window must be the same one
  `settlementQuays` uses: `x0 = minX - PAD` is lattice aligned, so open chains can end on the window's west and
  north edges, and the walker must stop there too.
- **Node memo**: a `ConcurrentHashMap<Long, Boolean>` of `traced` values (pure, so a race costs nothing).
- **Chains**: `Quays.chainThrough(near, window, seed)` (step 1) returns exactly the `List<Pt>` that
  `Quays.contour(window)` returns for that chain: same start, same direction. The chain id is the key of its
  smallest point. A `pointIndex` map from point to chain id stops a chain being walked twice. Each chain keeps
  its runs (`breakRing` + `StreetCols.runs(ok)` + MIN_LEN), computed once.
- **Clusters**: start from the chains a tile needs. For each run column, scan the contour cells within
  `CLUSTER_REACH = STEP = 8` blocks for other chains, walk them, and join two chains when one run column of
  each is within 4 blocks (Chebyshev). The cluster id is the smallest chain id. A cluster is graded once
  (`ConcurrentHashMap<Long, FutureTask<...>>`, caller-runs) by the bank's grading loop over the canonical order
  of its runs. That loop is the same code as today's loop from `JunctionField jf = junctions()` to the end.
- **Tile request** `near(bank, tile, m)`:
  1. Scan the cells of the tile window ± (m + STEP), clamped to the cell window, for segment points (seeds).
  2. Find each seed's chain, then the clusters, then grade them.
  3. Keep the wholes with a column within m, sorted by order key.
- **Whole request** `all(bank)`: today's full `Quays.contour` with row prefetch, then every chain, every
  cluster, graded and sorted by order key. `warmQuays` and the CLI use it.
- **Order key** on each whole: `{qsmallest(run), qkey(run.first), run.size, partIndex}`. In today's code, `out`
  is the runs in canonical order, each run's parts in `quayParts` recursion order. Sorting by this key gives
  the same sequence restricted to any subset.
- **The reference stays**: today's `settlementQuays` is kept verbatim as `settlementQuaysReference()` and
  selected by `SkeletonSource.Direct.withQuayReference(true)`. Every QUAYLAZY test compares the lazy result with
  it, live, for good. That is about 70 lines of duplication, the price of an oracle (assumption below).
- **(c) Parallel**: chain walks and cluster gradings fan out on `src.reads().fanout()`. `Build` is not
  thread-safe (its `floorMemo`/`wetMemo` are HashMaps, and `meetBad` and `meetConflict` are fields), so each
  piece runs with its own `new Build(src, ix, iz, exact, false)` (no water marking, cheap) as its bank. The
  walker also prefetches a box ahead of itself (the live probe fills a whole chunk per first read, so a walk
  that reads one node at a time is serial chunk fills otherwise). Results are merged only through the sorted
  order key and the id-keyed memos, so no thread timing reaches the output.
- **Mod**: `RegionWarmer.warmAt` calls `warm(x, z, r, false)` (no whole trace) and then plans the point's tile
  synchronously, which pays that tile's lazy quays on the worker, before the async neighbour prefetch. The
  teleport hold (`warmPoint`, TPHOLD15) then waits for exactly "the region, network, junction field and the
  quay clusters touching the destination tile".

**Rejected alternatives:**
- *Per-chain only (the coordinator's draft)*: breaks determinism where two chains meet (saddles).
- *Drop the quay-to-quay level rule so chains become independent*: changes the output, can leave overlapping
  quays at different heights (JUNCTION), and needs an architect review and a re-review of cities. Kept only as
  a fallback if the cluster closure proves too costly, which the data says it won't (clusters are nearly
  always one chain).
- *Coarse water screen to find chains for the whole path*: misses small exact-only water and changes the
  output (section 6 (d)).
- *Keep the warmer tracing the whole city*: the warmer competes with chunk generation, and a whole trace on
  approach was 20 s of worker time for quays the player may never reach. Per-tile lazy work through the
  existing tile and neighbour prefetch covers what the player sees.

# Steps

```
step 1 (walker)  ─┐
step 2 (prep)    ─┼─> step 3b (wire lazy) ─┬─> step 4 (parallel) ─┐
step 3a (trace)  ─┘                        └─> step 5 (mod)      ─┴─> step 6 (verify + measure)
```

Steps 1, 2 and 3a run in parallel. Steps 4 and 5 run in parallel after 3b. Step 5 also waits for TPHOLD15 to
merge, because it touches the `RegionWarmer.warmAt` path that TPHOLD15's hold relies on.

## Step 1: the chain walker in `Quays` (S; parallel with 2 and 3a)

Files: `planner-core/.../settlement/Quays.java`, `QuaysTest.java`.

- Refactor `contour(near, minX, minZ, maxX, maxZ)` into a private `contourCells(near, cells)`. `cells` is the
  cell keys in **scan order** (j outer, i inner, which is today's loop). Its body is the current one: links in
  scan order, then the two passes over `adj`. `contour(window)` calls it with every cell of the window. The
  output must be unchanged; existing `QuaysTest` stays green.
- `static List<Pt> seeds(Near near, int ci0, int cj0, int ci1, int cj1)`: one point per segment of the cells in
  that cell range, in scan order.
- `static List<Pt> chainThrough(Near near, int i0, int j0, int i1, int j1, Pt seed)`:
  1. Walk point to point from the seed in both directions. At point p in cell C, the segment of C holding p
     gives the next point q. Cross into the cell on the other side of q's edge if it is inside the window, and
     stop at the window edge or when the walk is back at the seed.
  2. Collect the visited cells and call `contourCells(near, visited sorted in scan order)`.
  3. Return the output chain that contains the seed. A saddle cell on the path also emits a fragment of
     another chain; ignore it.

  This reuses `contour`'s own ordering code, so start and direction match by construction.
- Tests (`QuaysTest`):
  - `aWalkFromAnyPointGivesTheContoursChain`: 200 seeded random fields (value noise thresholded at several
    levels, which gives many saddles) over a 30 x 30 cell window. Include fields true on the window's west and
    north edges, which gives open chains. For every chain of `contour`, and for 3 of its points (first, middle,
    last), `chainThrough` equals the chain (same list, `equals`).
  - `aWalkReadsOnlyItsCells`: wrap `Near` to count distinct nodes asked. Fewer than `4 * cells(chain) + 8`.
    This is the "no area scan" guard.
  - `aSaddleWalkKeepsItsOwnChain`: the existing saddle fixture, one walk per side.
  - Mutation check (report it): reverse the walk's emitted ring direction, or skip the scan-order sort, and
    the first test fails.

## Step 2: Districts preparation (S; parallel with 1 and 3a)

Files: `Districts.java`, `SkeletonSource.java`, new test helper `QuaySites.java` (core test, `settlement` or
`world` package), new test `QuayFilterTest.java`.

- Move `Build.QuayWhole` to a package-level record `QuayWhole(List<int[]> cols, int[] y, boolean[] steep,
  long[] order)`. `order` is the key above, filled in by the grading loop: run key from `canonicalQuays`' sort
  fields, and the part index in `quayParts` append order.
- Extract the grading loop (Districts.java:2167-2180) into `Build.gradeRuns(List<List<int[]>> runs)`, which
  calls `canonicalQuays` itself. Make `breakRing` static with the `ok` predicate as a parameter.
- Copy today's `settlementQuays` verbatim as `settlementQuaysReference()`. `SkeletonSource` gets
  `default boolean quayReference() { return false; }`, and `Direct` gets `withQuayReference(boolean)`. Until
  step 3b, both paths run the reference.
- Change `quayWholes()`' filter from "footprint box within m" to "some footprint cell within m of the tile
  window": a Chebyshev test per column against the window expanded by `m + WIDTH/2`. This doesn't change
  `Detail`, because nothing outside `keep` reaches the tile.
- `QuaySites`: the waterfront settlements of the test worlds, each as (planner, site). Built with
  `JoinWorld.pt1573()` (s4_m2, s4_m5, s5_m4), the w12345 river planner of `ParallelReadsPlannerTest.base()`
  (s1_m5, sm1_m4, sm3_m4), and w987654321 through `GateWorld.load("gate-metropolis-mixed")` and
  `GateWorld.load("gate-coast-city")`. For each, give the owned waterfront tiles. List what you find in the log:
  the w987 site ids aren't checked yet. Assert each site has at least one quay run, or the fixture covers
  nothing.
- `QuayFilterTest.theColumnFilterKeepsEveryPlan`: record `DistrictJson.write(Districts.plan(...))` for every
  owned waterfront tile of s1_m5 and s5_m4 on the **base commit** with the box filter, as a committed digest
  list `planner-core/src/test/resources/quaylazy/filter-digests.tsv` (sha-256 per tile; record mode
  `-Dfb.quaylazy.record=true`). Then assert equality with the column filter. The metropolises go in a
  `@Tag("heavy")` variant. Step 6 deletes the digest file once step 3b's live tests cover it.
- Done when: `:planner-core:test` is green, the digests are equal, and the reference is selectable.

## Step 3a: `QuayTrace` over a bank interface (M; parallel with 1 and 2)

Files: new `planner-core/.../settlement/QuayTrace.java`, `QuayTraceTest.java`.

- `interface Bank { boolean near(int x, int z); boolean ok(int[] col); List<QuayWhole> grade(List<List<int[]>>
  runs); void prefetch(List<int[]> boxes); }`. `near` is the raw `traced` value without memo. `grade` grades one
  cluster's runs in canonical order and sets `order`.
- `QuayTrace(Area area)` with `Area(TreeSet<Long> wf, int i0, int j0, int i1, int j1)`;
  `List<QuayWhole> near(Bank, int minX, int minZ, int maxX, int maxZ, int m)`; `List<QuayWhole> all(Bank)`, as
  in "Approach". Use `Quays.chainThrough` and `Quays.seeds` from step 1. Until step 1 lands, put in a stand-in
  with the same signature: contour the whole window and pick the chain holding the seed. Step 1 replaces it.
- State: node memo, `pointIndex`, `chains` (id → chain + runs), `partners` (chain id → sorted partner ids),
  cluster futures. All concurrent maps, **never iterated for output**: outputs are always sorted by order key
  or chain id (CLAUDE.md determinism rule).
- `CLUSTER_REACH = Quays.STEP` and `MEET_REACH = 4`, with a static check that
  `CLUSTER_REACH >= MEET_REACH + Quays.STEP / 2`, plus a comment citing the `meetPins` footprint and ring
  arithmetic above.
- Tests (`QuayTraceTest`, fake bank, no terrain):
  - The fake grade is strongly order dependent: for each run in canonical order, its `y[i]` is a hash of its
    columns plus the `y` of every earlier part with a column within 4 blocks of column i. Use a real
    `canonicalQuays`-style order.
  - The reference is the obvious global computation: `contour(window)`, then all runs, then the fake grade
    over all of them.
  - `aTileGetsTheGlobalAnswer`: on 100 seeded fields, including checkerboard-heavy ones where chains meet at
    saddles, and for every 20 x 20-cell "tile" in the window: `near(tile)` equals the reference filtered by
    "column within m", in order (compare `cols`, `y` and `order`).
  - `theVisitOrderDoesNotMatter`: one `QuayTrace`, tiles in 3 seeded shuffles. Every tile's answer is equal.
  - `allEqualsTheReference`.
  - Mutation checks (report them): no partner closure → `aTileGetsTheGlobalAnswer` fails on the saddle fields.
    Sorting by chain id instead of order key → it fails too.

## Step 3b: wire the lazy trace into `Districts` (M; after 1, 2 and 3a)

Files: `Districts.java`, `QuayWindowTest.java`, new `QuayLazyTest.java`.

- `Build.quayArea()` computes `wf` and the window (code from `settlementQuays`' first loop).
  `Build.bank()` is an adapter:
  - `near` is the old `traced` body without prefetch;
  - `ok` is the old `ok`;
  - `grade` is `gradeRuns`;
  - `prefetch` goes to `src.reads().prefetch`.
- `quayWholes()`:
  - With `src.quayReference()`, the old shared value plus the new column filter.
  - Otherwise `src.shared("quayTrace", exact, () -> new QuayTrace(quayArea())).near(bank(), tile, m)`. Before
    the scan, prefetch the scan box ± `QUAY_READ` in one call (PARREADS style).
- `warmQuays` calls `all(bank())` on the same shared `QuayTrace`.
- Tests (`QuayLazyTest`; everything over `QuaySites`):
  - `everyWaterfrontTileSeesTheReferenceQuays`: for each owned waterfront tile, `Districts.quayRuns` from a
    **fresh lazy source per tile** equals the list from one reference source. A fresh source per tile is the
    worst case for independence. Also assert that no two runs share a canonical sort key. A tie would make the
    stable sort's input order matter (risk R2).
  - `theVisitOrderDoesNotMatter`: one shared lazy source, tiles in 3 seeded shuffles.
  - `theLazyPlanEqualsTheReferencePlan`: compare `DistrictJson.write(Districts.plan(...))` for every owned
    waterfront tile, lazy against reference: s1_m5 and s5_m4 by default, all metropolises `@Tag("heavy")`.
  - `warmingFirstChangesNothing`: `warmQuays` then tile requests equal tile requests alone.
  - `aTileReadsAFractionOfTheWholeTrace` (the deterministic cost guard): on s4_m2 with a probe that counts
    distinct 16 x 16 chunks read, the median waterfront tile's lazy chunk count, each tile from a fresh source,
    must be at most 25 % of the reference trace's count. The probe saw about 8 %. It fails if anyone brings
    back an area scan.
  - `QuayWindowTest`: keep both pairs, which now run the lazy path (fresh source per tile, so it tests
    independence directly). Add the adjacent waterfront tile pairs of s4_m2 that a quay crosses (find them in
    the test, at least 3).
- Done when: all of the above pass, `ParallelReadsPlannerTest`, `MainStubTest`, `SettlementGoldenTest` and
  `DistrictsTest` are unchanged and green, and `scripts/test-scope.sh` layers pass.
- *Done 2026-10-31 (log 2026-10-31-p6-quaylazy-3b). Deviations:* the s4_m2 tile-pair border test lives in `QuayLazyTest` (heavy; it needs `QuaySites`), not `QuayWindowTest`; the cost guard samples 9 tiles of s4_m2; `canonicalQuays` got the R2 column-sequence tiebreak (both paths; no fixture has a tie) and the order key a tie rank `{smallest, first, size, tie, part}`; the chain source is built per request over the caller's bank (a bank is one thread's `Build`), so step 4 can build one per fanned-out piece (`Bank.fork()`) without touching the trace.

## Step 4: (c), parallel walks and cluster grading (S-M; after 3b, parallel with 5)

Files: `QuayTrace.java`, `Districts.java` (bank per piece), tests.

- In `near()`:
  1. Dedupe seeds by their in-box connectivity: seeds joined by segments inside the scanned box are the same
     chain.
  2. Walk the distinct ones with `bank.fanout().each(n, i -> ...)`, each piece using its own bank. `Bank` gets
     `Fanout fanout()` and `Bank fork()`: in Districts, `fork()` builds a `new Build(src, tile.ix, tile.iz,
     exact, false)`.
  3. Grade the clusters the same way.
- Walk prefetch: when a walk reaches a point outside its last prefetched box, prefetch the box of ±
  `WALK_AHEAD = 32` blocks round it (about 5 x 5 chunks, filled in parallel). Keep `WALK_AHEAD` a constant.
  Step 6 reports chunk fills with and without it, because a box over-reads compared with the band.
- In `all()`: grade the clusters in parallel. The contour stays serial with its row prefetch.
- Tests:
  - `aPoolGivesTheSameQuays`: reuse `ParallelReadsPlannerTest.PoolReads` (make it package-visible or move it
    to a shared test helper). The lazy path on a 6-thread pool equals the serial run for s1_m5 and s5_m4,
    called from the test thread and from inside a pool thread, with the pool free and with it saturated, as
    PARREADS did.
  - `concurrentTilesShareOneTrace`: 8 threads start together (latch) and each plans a different waterfront
    tile of s4_m2 on **one shared lazy source**. Every result equals the reference. 5 rounds, fresh source
    each. This fails with a HashMap in shared state or a broken cluster future.
  - The existing `ParallelReadsPlannerTest` stays green.

## Step 5: mod wiring, perf rows, attribution (S; after 3b and TPHOLD15's merge, parallel with 4) - DONE (claude/p6-quaylazy-5, see log 2026-10-31-p6-quaylazy-5)

Files: `mod/.../worldgen/RegionWarmer.java`, `planner-core/.../world/WorldPlanner.java`,
`mod/src/test/.../perf/PerfPlannerTest.java`, `mod/perf-budget.json`, `scripts/JfrAttrib.java`,
`mod/src/test/.../perf/PerfReachTest.java`.

- `RegionWarmer.warmAt`:
  1. Call `planner.warm(x, z, radius, false)`.
  2. If `planner.tile(ix, iz)` is present, call `planner.plan(ix, iz)` synchronously. This is the lazy quays
     for that tile.
  3. Then the existing `prefetch`.

  `warmPoint` inherits this, so with TPHOLD15's `releaseOnPlan` the hold ends after the destination tile's
  plan. Ask the TPHOLD15 coder, through the coordinator, not to change `warmAt`. If TPHOLD15 adds its own
  tile-plan wait, merge the two into this one.
- `WorldPlanner.warm(..., true)` and `warmQuays` stay (whole path: tests, `ParReadsDigest`, `WarmingGameTest`,
  `PerfSupport:201`). Javadoc: the mod no longer uses them. Optional escape hatch: a
  `-Dfractalbuildings.quayTrace=whole` flag that makes the mod call `warm(..., true)` again. It changes no
  output, only timing.
- `PerfPlannerTest`:
  - After `region.cold.warm.ms` on the cold planner, pick T = the owned waterfront tile of the metropolis
    nearest its centre (ties by tile key) and print it.
  - New rows: `region.cold.quays.tile.ms`, the lazy quays of T through a new
    `WorldPlanner.warmQuaysAt(blockX, blockZ)` (the lazy request for the tile at the point, per owning
    settlement), and `region.cold.quays.tile.chunkFills` (the `lastProbes.chunkFills()` delta; serial planner,
    so it's deterministic).
  - Measure the whole trace `region.cold.quays.ms` on a **separate fresh planner** after the tile rows, so the
    lazy row doesn't warm its memo.
- `perf-budget.json`: `region.cold.quays.tile.ms` report-only until step 6 sets a budget from a quiet run, and
  `region.cold.quays.tile.chunkFills` with ci = 2 x the first value (deterministic). Any budget change goes
  through the coordinator.
- `JfrAttrib.java:71` and `PerfReachTest.java:62`: also count `c.endsWith(".QuayTrace")` as "quays".
- Tests: `:mod:test`, server game tests (`WarmingGameTest`, `TeleportHoldGameTest`), and the client quick layer
  (TeleportHold) through `scripts/test-scope.sh`.

## Step 6: verification and measurement (assistant or coder; after 4 and 5) - MEASURED (claude/p6-quaylazy-6, log 2026-11-01-p6-quaylazy-6; the planner-heavy and full layers are the verifier's)

- Correctness, on the final merged commit:
  - `scripts/test-all.sh --full` (which includes the order test, 17/17, and the determinism layer) through
    `scripts/anywhere.sh`;
  - `scripts/test-all.sh --planner-heavy` for the heavy `QuayLazyTest` and `QuayFilterTest` metropolis cases
    and `SettlementDeterminismTest`;
  - the ORDERSIM sims in `:mod:test`.
  - Then delete `filter-digests.tsv` and `QuayFilterTest` (the live lazy-against-reference tests cover them).
- Measurement, **the quiet perf worker only** (CLAUDE.md, regionperf section 7 protocol):
  - Send each run when `ssh <perf worker> job status` shows nothing running or queued. Run `scripts/quiet-check.sh`
    inside the job, and `scripts/evidence.sh quaylazy-bee-rN scripts/remote.sh -- scripts/perf.sh`, twice.
  - The same session also runs the **before** commit (the run-branch merge-base), so before and after share
    host and protocol.
  - Report each row with host, renderer and load, mod against base:
    - `region.cold.quays.ms` (whole; before ~20 s);
    - `region.cold.quays.tile.ms` and `.chunkFills` (new);
    - `region.warmed.worker.ms` (before ~31 s);
    - `mod.cold.chunk.max.ms` against `base.cold.chunk.max.ms` (before 53 s against 0.33 s);
    - `arrival.cold.metro.ms`, `arrival.hold.ms` and `arrival.maxTick.ms` against their base rows;
    - the flight tick rows.
  - Any number from a non-quiet run is labelled "not valid".
- Docs:
  - PERFORMANCE.md: a QUAYLAZY entry under "Quays front-load probes", plus "Open, needs John" if the cold
    chunk is still far over;
  - NOTES lines (the order key, the cluster rule, "the cost is the area scan");
  - the log;
  - the ROADMAP tick.
  - No REVIEW item: the output is unchanged. An optional one for John to feel a cold teleport into a waterfront
    metropolis.

# Expected effect (estimates, not measured)

- The quay part of a cold waterfront tile: from the whole trace (about 20 s on the quiet perf worker) to its lazy share.
  Assuming cost scales with chunks (7 to 13 times fewer on recorded s4_m2), that is about 1.5 to 3 s before
  step 4 and less after it. A long coast chain is the worst case: s4_m5's worst tile is 3.6k chunks, about 2.5
  times fewer than whole.
- `mod.cold.chunk.max.ms` (53 s): drops by most of the quay share, to perhaps 15 to 30 s. **It will not reach
  vanilla.** The rest is the settlement's junction field and the region layer, which a structure start still
  waits for. Lazy junction fields would be the next item; that is the coordinator's call.
- TPHOLD15 15 s cap, waterfront destination: region warm, junction field, lazy quays and the tile plan come to
  roughly 10 + 2 + 1 s on the perf worker, so it is borderline. Step 6 measures it (`arrival.hold.ms`).

# Risks

- **R1 Hidden whole-settlement dependency** not visible in the code read above. The fresh-source-per-tile
  lazy-against-reference test over 8 or more settlements is the guard. If it fails, find the dependency; don't
  loosen the comparison.
- **R2 Ties in the canonical run order** would make the order depend on chain order. Step 3b asserts there
  are none in the fixtures. If one appears, add a total tiebreak (the full column sequence) to *both* paths;
  that change is output neutral where no ties exist.
- **R3 Duplicate walks under races** waste reads but give the same answer. Step 4's in-box dedupe and
  `pointIndex` keep it rare. Step 6 reports `chunkFills` with a pool against serial.
- **R4 The source cache evicts a `QuayTrace`** (WorldPlanner's skeleton cache): the lazy work is redone, as the
  whole trace is today.
- **R5 Total CPU over a whole visit** stays at about one whole trace (the node memo and the chunk memo are
  shared), but it is spread over tiles instead of front-loaded. A player who explores every tile pays about
  the same in total, never in one wait.
- **R6 Moving the mod's warmer off the whole trace** means a flight into a waterfront city pays per tile
  through prefetch rather than once ahead. Step 6's flight rows check it. If they get worse, the warmer can
  call `all()` in the background *after* the point's tile is planned (never before it).

# Assumptions (rows for ASSUMPTIONS.md when implemented)

- The old trace stays in main as `settlementQuaysReference()`, a permanent test oracle (about 70 lines).
- The mod's warmer no longer traces a settlement's whole waterfront; per-tile lazy quays through the tile and
  neighbour prefetch replace it.
- The CLI and viewer keep the whole path through `warmQuays`/`all()`. Their output is identical either way.

# For John

Nothing blocks. The output is byte-identical by construction, and the tests enforce it. The one tradeoff is
the warmer change (R6). It is cheap to reverse and measured in step 6.

# Design questions

None for the architect or interior designer: no geometry changes. The only fallback that would change looks
(drop quay-to-quay meeting levels) is rejected above. It would go to the architect if the cluster rule ever
proved too costly.
