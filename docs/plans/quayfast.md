# Plan: QUAYFAST, quays traced run by run (fewer chunks, the same quays)

Lead-coder plan, 2026-11-01, branch `claude/p6-quayfast-plan` (base `claude/run-phase6` at 6e0826c8).
Item: QUAYFAST, the follow-up to QUAYLAZY (a tile traces only the quays that reach it; [plan](quaylazy.md)) that
CASCADE2FIX left open: the perf row "Cold district plan, p95" (the slowest of 7 cold district plans in
`PerfPlannerTest`) is 3.67 s on a quiet beebox against its 3 s ceiling, and almost all of it is one tile, 16,-35,
tracing one river's quay chain. John approved option (A), byte-identical read savings, on 2026-11-01 (coordinator
message). Part (A) is ready to hand to coders: section 4. Part (B), which changes output, is analysis only (section 6).

Words used below:

- **Contour chain**: the line "8 blocks from the water", traced by marching squares on the 8-block lattice (`Quays`).
  The trace is clipped to the settlement's waterfront tiles plus 16 blocks, so a river crossing a city becomes one
  closed ring: along one bank, across the river at the edge of the traced area, back along the other bank.
- **Run**: a stretch of that chain where every column can carry a quay (`quayOk`: dry, low bank, waterfront quarter,
  not squeezed between streets). Runs shorter than 40 columns are dropped. A run is one promenade before grading.
- **Cluster**: runs (today: whole chains) whose columns come within 4 blocks of each other, so their heights depend
  on each other (where two quays overlap, the earlier one sets the level).

# 1. John's question, answered

> "when we did lazy quays (only planning sections), we seemed to plan some arbitrary "section" of a quay that
> apparently had variable length. I was wondering why we can't take that further and only scan the shoreline for
> exactly the chunks (or some other unit) that we need, rather than the whole shoreline. Is that what this is?"

**What the variable-length section is.** It is the *contour chain*, or a cluster of chains: the whole shoreline of
one water body inside the settlement's waterfront area. For a lake it is the ring round the lake. For a river it is
both banks of the river for as far as the settlement's waterfront tiles go, joined at the two ends into one ring.
That is why its length varied: it is as long as that water body's shore inside the city. On the perf tile 16,-35 it is
a ring of 22,393 columns (about 22 km of contour). The tile needs one quay run of 61 columns from it.

**Why a tile traces the whole chain today.** Mostly for no reason the output needs. The code finds a tile's quays by
walking the whole chain, then cutting it into runs, then grading the runs. Two things look as if they need the whole
chain, and neither does:

- *Where the chain starts.* A closed ring is cut into runs after rotating it to start at a column where no quay can
  stand (`QuayTrace.breakRing`). The runs that come out are the stretches between such columns, whatever the
  rotation. Only a ring that is quay-able all the way round depends on its start, and then the run is the whole ring
  anyway.
- *Which runs depend on which.* QUAYLAZY grades whole chains together (a cluster of chains). But a run's heights
  depend only on runs that come within 4 blocks of it (`meetPins`), so the true unit is the cluster of *runs*.

**What really needs the far parts is inside one run.** Grading a run (`Districts.quayParts`, `Grading.profile`) uses
the run from end to end:

- the profile anchors every 8 columns *counted from the run's first column*;
- the grade clamp sweeps the whole run both ways, so a bump at one end can move levels far along it;
- the run is oriented from whichever end has the smaller column key, so both ends must be known;
- runs under 40 columns are dropped, and after a cut, parts under 40 columns are dropped too;
- where two quays overlap, the run that comes first in the canonical order (by smallest column) sets the level.

So **the smallest unit that gives today's quays exactly is the run** (plus the few runs within 4 blocks of it), not the
chain. A run is short: on the perf tiles the longest is 138 columns, against chains of 9,929 and 22,393 columns.

**So: yes, your idea is this plan, taken as far as it goes without changing a single block.** Trace from the tile
along the shore only until the quay-able stretch ends at both sides, and nothing further. On tile 16,-35 that cuts
the quay part of the plan from 2,683 chunk fills to at most 166 (counted, section 2).

**Going finer than a run** (fixed windows or chunks of shoreline, each decided alone) would change the output: the
anchors, the clamp, the orientation, the 40-column rule and the overlap order would all have to become local rules,
with seams that both sides agree on. That is a rule change and would need your OK and an architect review. After the
run-by-run trace it would save little, because runs are already short and the cost that remains is the tile's own
scan, which a finer unit doesn't remove. Section 6 has the details; I don't recommend it now.

# 2. What the counts say (deterministic, no quiet host needed)

All counts from `ColdRowCountTest` (`-Dfb.perf.coldrow=1`, one thread unless a pool is named) at 6e0826c8, local.
Reproduced: window 4,145 fills, 648 walk rounds; 16,-35 2,779 fills (2,683 quays), 505 rounds.

A throwaway probe (not committed) logged every chain each lazy request walked, and the set of chunks a run-by-run
trace would read (the run's cells, its partner scan, its `ok` reads and the tile's seed scan, as boxes, so an upper
bound). The test recorded the chunks each stage actually filled, and the two were intersected:

| Stage | Chains walked (largest) | Runs the tile needs (with their cluster) | Quay fills today, serial | Of those, inside the run-by-run read set | Inside the seed scan box | Inside a screened scan (section 3, A6) | Pool (11 threads) fills today |
|---|---|---|---|---|---|---|---|
| quay tile 15,-37 | 7 (open chain, 9,929 cols, 14 runs, longest 138) | 2 runs, 172 cols | 1,492 | 208 | 200 | 53 | 2,550 |
| sample 16,-35 | 5 (ring, 22,393 cols, 34 runs, longest 112) | 1 run, 61 cols | 2,683 | 166 | 166 | 90 | 6,730 |
| sample 17,-35 | 8 (small rings) | 3 runs, 166 cols | 119 | 78 | 78 | 5 | 228 |
| sample 18,-35 | 3 (rings of 1,209 and 1,257 cols) | 4 runs, 237 cols | 532 | 259 | 259 | 87 | 763 |

What this says:

- **The p95 tile pays for 22 km of shoreline to place 61 columns of quay.** 94 % of its quay fills are chain the tile
  never uses. The probe's read-set estimate contains every fill the stage really made (2,779 of 2,779), so it is a
  sound upper bound.
- **After a run-by-run trace, what is left is the tile's seed scan** (every lattice node within 74 blocks of the
  tile; 166 of the 166 remaining fills on 16,-35). Only a screen on cheap fields (section 3, A6) cuts into that.
- **On the pool, today's walk-ahead boxes read 2.4 times the serial chunks** (6,730 against 2,779 on 16,-35): square
  boxes of about 6 x 6 chunks round a thin contour, 505 times. With short runs there are few boxes left to overreach.
- **The world path** (`PerfCascadeTest`, the plan of chunk 148,-360's tile): quays are 1,463 of 15,936 fills outside
  the link rule; the link rule's 9,378 include other tiles' quays too (the counter files them under the link rule).
  So the world rows should move a little; how much is unknown until step 5 counts it.
- No refills: distinct chunks filled equal fills in every stage, so the 8,192-chunk memo is not evicting.

# 3. Options (A): byte-identical read savings

| Option | What it does | Fills saved on 16,-35 (serial / pool) | Verdict |
|---|---|---|---|
| **A5 Trace runs, not chains** (new) | Walk the contour from a quay-able column of the tile both ways and stop where the quay-able stretch ends; clusters by run, not chain | 2,683 → at most 166 / 6,730 → about 600 (scan-box prefetch of about 528 plus a few walk boxes; estimate) | **Do it** (steps 1, 3) |
| **A6 Screen the seed scan** (new) | Read exact terrain only in scan cells where some column passes the cheap part of `quayOk` (waterfront tile, urban, waterfront quarter: density and quarter fields, no exact reads) | a further 166 → about 90 / about 528 → about 140 in the scan prefetch | **Do it after A5** if step 3's counts show the scan is what's left (step 4) |
| A1 Corridor prefetch | Prefetch only chunks along the contour instead of square boxes | 0 serial / up to 3,951 today, a few hundred after A5 | Moot after A5 |
| A2 Overlap the next prefetch with the walk | Fill the next box asynchronously while the walk works in the current one | 0 (fills may rise); saves round waits: 505 rounds today, tens after A5 | Moot after A5; needs a new non-blocking `Fanout` call |
| A3 Smarter box sizing | Change `QuayTuning.walkAhead` (32) or `walkBoxes` (4) | QUAYLAZY 6 ablation: walk-ahead 0 was 15 % faster and read 31 % fewer chunks on 15,-37 | Re-tune from the ablation rows of the final quiet run (step 5), one-line change |
| A4 Cache shoreline samples across settlements | Share the node memo between settlements | 0: the chunk memo is already one per world, and this row touches one settlement | Rejected |

A5 is exact for the reasons in section 1. The argument, for the coder and the verifier:

1. `StreetCols.columns` rasterises each contour step exactly (steps are 4 or 8 blocks, along an axis or at 45
   degrees), so the columns of part of a chain are a contiguous piece of the chain's columns, and reversing a chain
   reverses its columns.
2. `QuayTrace.runsOf`: for an open chain, `StreetCols.runs(cols, ok)` gives the maximal stretches of `ok` columns (at
   least 2). For a ring, `breakRing` rotates the ring to start just after the first non-`ok` column, so the runs are
   the maximal cyclic stretches, whatever the chain's start. Only a ring with every column `ok` returns itself
   (start-dependent): the walker detects that case and falls back to `chainThrough` (exact by construction).
3. `ok` is pointwise, `canonicalQuays` orients and orders runs from their own columns, and `orderKey` reads only the
   cluster's canonical list (ties share their smallest column, so they share a cluster).
4. Grading a run reads earlier parts only at columns within `MEET_REACH` = 4 blocks of its own (`meetPins`). So the
   connected components of runs under "some columns within 4 blocks" are independent, and grading one alone in
   canonical order gives each run what the global loop gives it. Today's chain clusters are unions of these, so this
   refines what QUAYLAZY already relies on.
5. Quay columns lie in waterfront tiles, at least 16 blocks inside the traced window, so a run never reaches the
   window edge (the walker still stops there, as `chainThrough` does).

# 4. Steps (A)

```
step 1 (run walker, Quays)        ─┐
step 2 (screen predicate, Bank)   ─┼─> step 3 (run-level QuayTrace) ──> step 4 (screened scan, optional) ─┐
step 0 (per-sample perf rows)     ─┘                                 └────────────────────────────────────┴─> step 5 (counts, guards) ─> step 6 (quiet beebox)
```

Steps 0, 1 and 2 run in parallel. Step 3 can start at the same time as 1 and 2 against a stand-in walker (below),
and swaps in step 1's walker when it lands. Step 4 needs 2 and 3. Steps 5 and 6 are last.

## Step 0: per-sample timing rows (S, coder; parallel with everything)

The p95 row is the max of 7 samples (`PerfPlannerTest`, line 199: index `ceil(7 * 0.95) - 1` = 6). Today nobody can
see which tile is second. Files: `mod/src/test/java/dev/fractalbuildings/mod/perf/PerfPlannerTest.java`.

- Put one row per sample into the output map, `district.cold.sample.<ix>_<iz>.ms`, next to the p95 row, and print a
  `district.cold.p95.tile <ix>,<iz>` line naming the tile that set the p95.
- `scripts/perf-check.mjs` judges only keys listed in `mod/perf-budget.json`, and an entry without `budget` is
  report-only (line 39). Add the 7 sample keys as report-only entries so they show in `summary.md` (the sample tiles
  are fixed: ix-3..ix+3 at iz+1 of the centre tile 15,-36, so 12,-35 to 18,-35). Budget-file changes go through the
  coordinator; report-only entries judge nothing.
- Test: `node scripts/perf-check.test.mjs` stays green. The rows themselves are seen in step 6's run; the change is
  printing only, so no timing run is needed here.

## Step 1: the run walker in `Quays` (M, coder; parallel with 0, 2, 3)

Files: `planner-core/.../settlement/Quays.java`, `QuaysTest.java`.

- `static List<Pt[]> segmentsIn(Near near, int ci0, int cj0, int ci1, int cj1)`: every segment of those cells, scan
  order (j outer, i inner), as `seeds` iterates them. Refactor `seeds` to use it (output unchanged).
- A result type, e.g. `record RunWalk(List<int[]> cols, boolean wholeRing)`, and
  `static RunWalk runThrough(Near near, Predicate<int[]> ok, int i0, int j0, int i1, int j1, Pt a, Pt b, int t)`:
  the run that holds column `t` of segment `a → b` (`StreetCols.columns(List.of(a, b)).get(t)`), which must be `ok`.
  1. Forward: the columns after `t` on `a → b`, each tested with `ok`; stop at the first one that fails. If none
     fails, step to the next point after `b`: the other end of the segment, in the cell across `b`'s edge, that holds
     `b` and is not `a → b` itself (the same neighbour logic as `chainThrough`'s `around` loop, including the window
     clamp and saddle cells, where `b` belongs to exactly one of the two segments). Rasterise that step with
     `StreetCols` (its columns after the first), test each, and go on. Stop at the window edge (no segment) too.
  2. Backward: the same from `a`, the columns before `t`.
  3. If the walk comes back to its starting column with every column `ok`, return `wholeRing = true` and no columns.
     The caller then takes the chain from `chainThrough` and the run from `QuayTrace.runsOf` (exact by construction).
  4. Else return `reverse(backward) + [column t] + forward`. Each point's column appears once.
  It reads only the corners of cells the run passes, plus the cell where each end stops.
- Tests (`QuaysTest`, reuse `QuayTraceTest.field(seed)` and `QuayTraceTest.Fake.ok`, which cuts chains into runs):
  - `aRunWalkGivesTheChainsRun`: 200 seeded fields over the 30 x 30 cell window, including open chains on the west
    and north edges. Expected runs: for every chain of `Quays.contour`, `breakRing` (rings) then `StreetCols.runs`
    with no length filter. For the first, middle and last column of every expected run (found on its segment by
    scanning `segmentsIn` of its cell), `runThrough` gives the same column sequence or its reverse.
  - `anAllQuayRingIsReported`: the same fields with `ok = c -> true`: every ring gives `wholeRing`, every open chain
    gives its whole column list.
  - `aRunWalkReadsOnlyItsCells`: wrap `Near` to count distinct nodes asked; at most `4 * cells(run) + 16`.
  - Mutation checks (report them): stop one column early, or skip the saddle check, and the first test fails.

## Step 2: the cheap screen on the bank (S, coder; parallel with 0, 1, 3)

Files: `QuayTrace.java` (interface only), `Districts.java` (the bank), a new test `QuayScreenTest.java` (core,
`world` package, next to `QuayLazyTest`, so it can use `QuaySites`).

- `QuayTrace.Bank`: `default boolean maybeOk(int x, int z) { return true; }`. Contract: true whenever `ok(new int[] {x,
  z})` is; reads no exact terrain.
- `Districts.Build.bank(...)`: implement as the non-exact part of `ok`: the column's tile is in `wf`, `den.urban(x,
  z)`, `den.at(x, z) >= QuarterField.OUTSKIRTS_DENSITY` and `qf.at(x, z, dens) == QuarterKind.WATERFRONT` (the probe
  used exactly this). Check whether `rules.nearAbsentBorder` reads exact terrain; add it only if it doesn't.
  Leaving a condition out keeps the screen safe.
- Tests: `everyQuayColumnPassesTheScreen`: for s1_m5, s5_m4 and (heavy) the pt1573 metropolises of `QuaySites`, every
  column of every reference quay run (`Districts.allQuayRuns` with the reference source) has `maybeOk` true; plus
  1,000 random columns of each settlement's waterfront tiles where `ok` is true. And a counting probe
  (`QuayLazyTest.Counting`) shows `maybeOk` reads no chunk.

## Step 3: a run-level `QuayTrace` (M-L, lead-coder or a strong coder; starts in parallel with 1 and 2)

Files: `QuayTrace.java`, `QuayTraceTest.java`, `QuayLazyTest.java`, `Districts.java` only if the bank needs
something. Until step 1 lands, write a stand-in `runThrough` with the same signature: `chainThrough` from `a`, then
`runsOf` without the length filter, and pick the run holding the column. Step 1 replaces it (one line).

State (all concurrent maps, never iterated for output, as today):

- `okMemo` (column key → Boolean; `ok` is pure per bank, so a race costs nothing);
- `runOfCol` (column key → run id), `runs` (run id → `Run(id, cols, kept)`, `kept` = size at least `MIN_LEN`). The id is
  the run's smallest column key (`QuayTrace.smallest`). Write `runs` before `runOfCol`, as `chainOf` writes `chains`
  before `pointIndex`;
- `runPartners`, `clusterOf`, `clusterMembers`, `clusters`, `gradingBy`: as today, keyed by run ids;
- `chains`/`pointIndex`: kept only for `all()` and the whole-ring fallback.

`near(bank, minX, minZ, maxX, maxZ, m)`:

1. Scan cells and scan-box prefetch exactly as today (`r = m + STEP`).
2. Serially, for each segment of `segmentsIn(scan cells)` and each of its columns inside the tile ± `m`: if
   `ok` (memoised), `runOf(column)`. That is: `runOfCol` hit, else `runThrough` (or the whole-ring fallback), then
   register the run. Collect the ids of kept runs. (Walks are now tens of columns: keep them serial and drop the
   per-seed fan-out; the cluster grading keeps its fan-out through `gradeAll`.)
3. For each kept run, `clusterId(run)`: the component under `partnersOf`. `partnersOf(run)`: the cells within
   `CLUSTER_REACH` of each run column (dedupe cell ranges as today), their segments, their columns within
   `MEET_REACH` (Chebyshev) of a column of this run that are `ok`, `runOf` each; partners are the kept runs other than
   this one. No separate `meets` test is needed: the column test is the meeting test.
4. Grade the clusters (`gradeAll`, unchanged) and keep the parts that touch tile ± `m`, sorted by order key (as today).

`all(bank)`: today's whole contour, `chainOf` for every chain, then register every chain's runs into `runs` and
`runOfCol` (from `runsOf` without the length filter, plus the kept flag), then run clusters and grading as in
`near`. So `all()` and `near()` share one representation, and `warmingFirstChangesNothing` keeps meaning something.

Prefetch: the `memoised` walk-ahead boxes stay as they are (step 5 re-tunes the knob). `chainOf`'s per-point boxes
go with `chainOf`'s use in `near`.

Tests:

- Unchanged and green: every `QuayTraceTest` case (they compare against the global reference, with saddles, visit
  orders, threads and ties); `QuaysTest`; `QuayWindowTest`; every `QuayLazyTest` case, default and heavy (fresh source
  per tile against `withQuayReference(true)`, visit orders, the pool and concurrency cases, `warmingFirstChangesNothing`,
  `warmQuaysTileChangesNothing`); `SettlementGoldenTest`, `DistrictsTest`, `ParallelReadsPlannerTest`, `MainStubTest`.
- New in `QuayTraceTest`: `anAllQuayRingGivesTheGlobalAnswer` (a `Fake` whose `ok` is true everywhere, so rings take
  the fallback), and `runClustersAreFinerThanChainClusters` (on the saddle-heavy fields, at least one field where two
  runs of one chain fall in different clusters: shows the refinement is exercised, not just compiled).
- New cost guard in the default suite, `QuayLazyTest.aTileReadsOnlyItsRuns`: on pt1573 s4_m2, the 9 sampled tiles'
  lazy chunk counts (the existing `chunks(...)` helper, fresh source each) summed, at most 110 % of the value
  recorded when this step lands. Record it in the test with the commit. Keep `aTileReadsAFractionOfTheWholeTrace`.
- Mutation checks (report them): (1) partners by chain instead of by run still passes (it is coarser, not wrong):
  don't count it; instead (2) drop the partner closure → `aTileGetsTheGlobalAnswer` fails on saddle fields;
  (3) skip the whole-ring fallback → `anAllQuayRingGivesTheGlobalAnswer` fails; (4) walk the whole chain again →
  `aTileReadsOnlyItsRuns` fails.
- Output check for the verifier: `npm run city` (all presets) before and after, `diff -r` identical, as CASCADE2FIX's
  verifier did; the planner-heavy layer for the heavy `QuayLazyTest` cases.

## Step 4: screened seed and partner scans (S-M, coder; after 2 and 3; optional)

Do it if step 3's `ColdRowCountTest` shows the seed scan is most of what is left on the slowest tiles (the probe says
it will: 166 of 166 on 16,-35).

Files: `QuayTrace.java`, `QuayTraceTest.java`.

- In `near`: a scan cell is read only if some column position in it (x in [8i, 8i+8], z in [8j, 8j+8], inclusive:
  a segment's columns lie in its closed cell) inside the tile ± `m` has `maybeOk`. Prefetch only the kept cells'
  node boxes (± `QUAY_READ`), merged per row of cells so the box list stays short.
- In `partnersOf`: the same screen on the cells round each run column (positions within `MEET_REACH` of the run).
- Exactness: an `ok` column has `maybeOk`, lies in a closed cell of the scan range and is inside the `m` box, so its
  cell is kept. Nothing else changes.
- Cost: up to 81 `maybeOk` calls per cell, about 1,500 to 1,800 cells per tile. Memoise per column in a map local to
  the request (edges are shared by neighbouring cells). If the count is too high to be cheap, evaluate per cell
  edge first and say so in the log; don't time it locally.
- Tests: all of step 3's, unchanged; in `QuayTraceTest` a `Fake` with a screen that is false on a seeded half of the
  window and `ok` false there too: `aTileGetsTheGlobalAnswer` still holds, and a node counter shows fewer nodes read
  than with the screen off. Mutation: screen only the cell's centre column → a test where a run passes a cell corner
  fails (write that case).

## Step 5: counts, guards and budgets (S, coder; after 3, and after 4 if it is done)

- `ColdRowCountTest` (serial): record the new window fills and walk rounds (`WINDOW_FILLS`, `WINDOW_ROUNDS`), and add
  a per-stage guard that sample 16,-35's quay fills stay at most 400 (probe upper bound 166 after step 3, 90 after
  step 4). Also run it with `-Dfb.perf.coldrow.pool=11` and with `-Dfb.perf.coldrow.pool=11` plus walk-ahead 0, 16
  and 32 (a system property for the knob if there is none), and put the printed fills and rounds in the log (pool
  counts depend on timing: printed, not checked).
- `PerfCascadeTest`: record the new counts by part; lower `cascade.neighbours.chunkFills` in `mod/perf-budget.json`
  if it dropped (budget changes go through the coordinator; the run branch takes them).
- `region.cold.quays.tile.chunkFills` (budget 2,700, ceiling 5,212): expect a large drop (serial 1,492 → at most 208);
  propose new numbers from step 6's quiet run, through the coordinator.
- Wire the `ColdRowCountTest` guards into `scripts/perf.sh --planner-only` or `test-all.sh` if cheap (the
  CASCADE2FIX verifier noted they are opt-in and nothing enforces them). If not cheap, leave a polish line.
- NOTES: one line each for "the quay unit is the run" and the new counts. Log, ROADMAP tick.

## Step 6: quiet beebox measurement (assistant or coder; last)

- `scripts/evidence.sh quayfast-bee-rN scripts/remote.sh -- scripts/perf.sh --planner-only`, twice, each sent only when
  `ssh beebox job status` shows nothing running or queued (HEAVY13 may hold beebox: wait for it), with
  `scripts/quiet-check.sh` before and after (perf.sh does this) and the log ending `perf: quiet host`. Any other run
  is "not valid".
- Then one full `scripts/perf.sh` (world rows: cold chunk, arrival, hold, flight tick, the cascade counts) under the
  same protocol, so the world effect is known too.
- Report: `district.cold.p95.ms` and which tile set it (step 0's rows), the median, `district.cold.cpu.ms`, the
  `region.cold.quays.tile.*` rows, the `ablate.*` rows (walk-ahead 0 / 32 / 64, box1, no scan box), all with host,
  load and commit. Before numbers: CASCADE2FIX's 3,586 / 3,946 ms p95, 49.1 / 53.4 s CPU (same host and protocol).
- If an ablation row shows another walk-ahead clearly better in both runs, change `QuayTuning.walkAhead` in a
  one-line follow-up (it changes no output).

# 5. Expected effect (estimates; the last two plans over-predicted)

QUAYLAZY guessed 53 s → 15 to 30 s for the cold chunk and got no change (the cause was elsewhere). CASCADE guessed 13
to 24 s and landed at the top. This time the cause is counted directly (2,683 of 2,779 fills of the p95 tile come from
the walk this plan removes), so the risk is in what I can't count: the rest of each tile's time, and which tile
becomes the slowest.

- **Tile 16,-35**: serial fills 2,779 → about 260 after step 3 (96 tile plus at most 166 quays), about 190 after step
  4. On the pool, about 6,730 → 600 to 700 (step 3) and 250 to 350 (step 4); walk rounds 505 → tens.
- **Serial window** (neighbour + 7 samples): 4,145 → about 1,300 (step 3), about 1,150 (step 4).
- **"Cold district plan, p95"**: today 3.6 to 3.9 s, which is 16,-35. If that tile then costs about what its
  neighbours cost (the median tile is 0.66 s; tiles with 100 to 600 fills), the p95 becomes the second-slowest
  tile, probably 18,-35. **My estimate: 0.9 to 1.6 s, under the 3 s ceiling; probably still over the 1 s budget.** Not
  measured. Anything over 2 s would mean the remaining time isn't reads, and step 6's per-sample rows will show
  where.
- **CPU of the 7 samples** (49 to 53 s): should fall by roughly the fills of 16,-35's overreach (about 6,000 fills at
  about 2 ms each, so about 12 s of CPU on the pool). Not measured.
- **`region.cold.quays.tile`** (2.2 s, 2,606 pool fills): the quay tile 15,-37 drops to at most 208 serial quay fills;
  expect well under 1 s. Not measured.
- **World rows** (cold chunk 24.6 s, arrival 39 s): a small gain at most. The chunk mostly waits for other tiles'
  plans through the link rule; their quays are part of that but not split out in the counts. I would not predict
  more than 1 to 3 s and would not be surprised by none.

# 6. Options (B): local quay decisions that change the output (analysis; to be continued only if asked)

What it would take to decide quays per fixed shoreline window (for example 64 columns of contour, or a chunk-aligned
window), so a tile never walks past its window plus a fixed margin:

| Today (needs the whole run) | A local replacement | Visible change |
|---|---|---|
| Profile anchors every 8 columns from the run's first column | Anchors at world-fixed positions (for example columns where `(x + z) mod 8 = 0` along the run) | Levels shift by up to a block along every quay; slightly different slopes |
| Grade clamp sweeps the whole run both ways | Clamp limited to K anchors either side; seams pinned to a level both windows compute from the same local terrain | Small kinks at seams where the terrain climbs; possibly more "steep" stretches |
| Orientation from the smaller end key | Orientation from a local rule (for example the direction with the water on the left) | None by itself, but grading order inside a run changes |
| Runs under 40 columns dropped (needs both ends) | "Dropped if its ends are both found within the window plus margin and it is under 40", else kept | Some short quay stubs that are dropped today would appear near window edges, or the reverse |
| After a cut, parts under 40 dropped | The same rule, bounded | As above |
| Overlapping quays: the one first in canonical order sets the level | The smaller column key at the overlap point wins | Rare (saddles); levels at those crossings may change |

Cost and value:

- Every quay's heights would change a little, so every settlement golden, the city presets and the quay screenshots
  change. That needs an architect review against the street rules (road grade 1 in 3 limits in `Grading`, the
  `road-grade` check, "a quay meets each street at its level", DS-45 door rise where lots front quays) and John's OK.
  It is a rule change, not an optimisation.
- After step 3 the gain is small: runs are at most 138 columns on the perf tiles, so a window of 64 to 128 columns
  saves little, and the cost that remains (the tile's seed scan, about 166 fills on 16,-35) is the same either way.
- **Recommendation: don't do (B) now.** Revisit only if step 6 shows a tile whose time is one long run (for example a
  lake ringed by one unbroken promenade of thousands of columns). Status: to be continued if John or the coordinator
  asks; no design question is open until then (below).

# 7. Risks

- **R1 A hidden dependency on the whole chain.** Section 3's argument covers the code read (`runsOf`, `breakRing`,
  `StreetCols`, `canonicalQuays`, `orderKey`, `gradeRuns`, `meetPins`). The guard is the existing oracle: every
  `QuayLazyTest` case compares fresh-source-per-tile output with the verbatim old whole trace, on 8 settlements. If
  one fails, find the dependency; don't loosen the comparison.
- **R2 An all-quay ring.** Takes the fallback (whole chain walk), as today. Only a cost risk; step 3 tests it.
- **R3 `ok` evaluated in a new order.** `ok` is pointwise and its memos (`floorMemo`, `wetMemo`) are pure, so order
  can't matter; `okMemo` is per trace and shared across threads (each thread's bank computes the same value).
- **R4 Losing the per-seed fan-out.** Walks become serial on the caller. They are short, and the scan box is
  prefetched in one parallel call, so this should be cheaper, not dearer. If step 6 shows otherwise, restore a fan-out
  over candidate columns grouped by in-box connectivity (`Quays.distinctSeeds`).
- **R5 The screen (step 4) is wrong somewhere**, for example a quarter field read that differs between `ok` and
  `maybeOk`. Step 2's test checks every reference quay column passes; the lazy-against-reference tests catch the rest.
- **R6 Over-prediction.** Section 5 says what would show it. Step 0's per-sample rows make the next p95 holder visible
  instead of a surprise.

# 8. Assumptions (rows for ASSUMPTIONS.md when implemented)

- The quay trace's unit becomes the run cluster, not the chain cluster; output is unchanged and the old whole trace
  stays as the oracle.
- Chain walks in `near` become serial; only cluster grading fans out.
- The walk-ahead knob is re-tuned from quiet ablation rows only, never from local timings.

# Design questions

- None for (A): the output does not change, so no architect or interior-designer review is needed.
- For (B), only if it is ever taken up (for the `architect`): would world-anchored grading with seam pins every 64 to
  128 columns be acceptable on promenades (small level kinks where the bank climbs), and should short stubs near a
  window edge be kept or dropped?

# For John

- Your idea is this plan: trace only the stretch of shore that can hold the quay the tile needs, not the whole
  shoreline. Done at the level of a run, it changes nothing you'd see and should bring the p95 tile from about 2,800
  chunk reads to about 260.
- Going finer than a run would change how quays look and buys little after this; I recommend against it for now.
