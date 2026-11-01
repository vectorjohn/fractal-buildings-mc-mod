# CASCADE 2fix: the cold district p95 FAIL after CASCADE step 2

Item: diagnosis of HEAVY12's FAIL (perf row "Cold district plan, p95", 5,660 ms against ci 3,000 on a quiet beebox,
bisected to bfa3f27d, CASCADE step 2, the bounded exit-cut walk). Plan: [plans/jflazy.md](../plans/jflazy.md), step 2.

## Cause

Step 2 adds no work to this path; it moves work that an earlier, unbudgeted row used to pay into one of the 7 timed samples.

- The row times 7 tiles (ix-3..ix+3, iz+1 of the metropolis s2_m6's centre tile 15,-36) on a planner that has already planned the
  centre tile and its east neighbour 16,-36 (`district.cold.neighbour.ms`, no budget).
- Sample 16,-35 has quays. Its plan needs a lazy quay chain trace (QUAYLAZY: the whole bank contour through the tile), 2,683 chunk
  fills of the 2,779 the tile pays, all under `QuayTrace.near -> chainOf -> Quays.chainThrough -> nearWater`.
- Before step 2, the neighbour 16,-36 could not prove road r2_m6_4's cut beyond the tile from its own plan, so it ran the whole-road
  `fullCut`, which read the raw plans of 17,-36 and 18,-36; one of them traced that same chain (2,724 fills under the neighbour). The
  quay memo is per settlement, so 16,-35 then found it traced.
- After step 2 the bounded walk proves r2_m6_4 beyond (m = 327) from 16,-36 and 17,-36 alone, no `fullCut` runs at all in the
  sequence, and 16,-35 pays the chain trace itself.

Counts (serial, no reads pool, deterministic; `ColdRowCountTest`, busy host, counts only):

| stage | fills, default (walk) | fills, `-Dfb.exitcut.reference=true` |
|---|---|---|
| neighbour 16,-36 | 154 | 3,305 (fullCut r2_m6_4 = none) |
| sample 16,-35 | 2,779 (2,683 quays) | 124 |
| other 6 samples | 1,212 | 852 |
| neighbour + 7 samples | 4,145 | 4,281 |
| whole-road fullCut calls | 0 | 2 (r2_m6_4, r2_m6_6, both none) |

The verifier's quiet beebox runs agree: `district.cold.neighbour.ms` fell from 8,912 ms (af15ad4f) to 1,615 ms (bfa3f27d), while the
p95 rose by about 4.5 s. The worst cold tile in the sequence got cheaper (8.9 s -> 5.7 s); the budgeted row got dearer because the
cost landed inside it. The verifier's guesses (walk fails then `fullCut` runs; per-call memo) do not happen here: every walk in the
sequence returned true.

So the row's 1.1 s since QUAYLAZY 5 was an artifact of the old exit cut prepaying this chain in the neighbour row. The real cold
cost of planning 16,-35 with only the skeleton warm is about 5.7 s on beebox, almost all one quay chain trace.

## Why that chain trace is slow on the reads pool

With the game's reads pool (`-Dfb.perf.coldrow.pool=11`), sample 16,-35 waited for 3,760 walk-ahead prefetch rounds. `Quays.chainThrough`
walks the contour breadth-first, so its two ends alternate, and `QuayTrace.memoised` remembered only one prefetched box: almost every read at
the far end missed it, re-centred the box and paid one parallel round for a sliver of new chunks. The chain was latency-bound on those rounds
(the pool also filled 7,451 chunks to the serial 2,779: prefetch boxes reach past the contour).

## What changed

- No exit-cut change: nothing there to fix, and restoring the old path would put the 8.9 s back on the neighbour.
- `QuayTrace.memoised` remembers the last `WALK_BOXES = 4` prefetched boxes (`QuayTuning.walkBoxes`, 1 restores the old single box). A
  prefetch is a hint, so answers and serial fills are unchanged (4,145). Rounds, pool of 11 (busy host, counts): 16,-35 3,760 -> 505;
  quay tile 15,-37 717 -> 267. 2 boxes already give 538, 8 give 502. A directional lead (box stretched 96 blocks along the walk) was
  tried first and did nothing for rounds (3,760) while doubling fills: the alternation, not the box size, was the cause.
- `ColdRowCountTest` (opt-in, `-Dfb.perf.coldrow=1`, about a minute): the cold district sequence of `PerfPlannerTest` as counts per stage
  (fills by planner part, walk rounds, new raw plans, fullCut and walk entries); `-Dfb.perf.coldrow.pool=N` runs it on a reads pool
  (printed only). Guards (serial, deterministic): the window (neighbour plus 7 samples) stays within 110 % of 4,145 fills and of 648
  walk rounds. Mutations: `fullCuts` computed for every exit road in `trimmedPlan` -> 17,389 fills, fails; one walk box -> 4,238 rounds,
  fails.
- `PerfPlannerTest`: ablation row `ablate.box1.*` (the quay tile with one walk box).

## Verification

- `ColdRowCountTest` serial: pass (4,145 fills, 648 rounds); planner-core `--tests '*Quay*'` 35/35.
- `scripts/test-all.sh --no-client` at 995ae8c2: OK (planner-core 1040/1055, 15 skipped; cli 38/38; determinism 17/17; mod 132/140 +
  42/42 server game tests; viewer 142/142).
- Quiet beebox `scripts/perf.sh --planner-only` at 995ae8c2 (radeonsi host, planner rows only, quiet-check QUIET before and after,
  `perf: quiet host`), two runs:

| run | p95 | median | neighbour | district CPU | load at start |
|---|---|---|---|---|---|
| HEAVY12 bisect, bfa3f27d (before) | 5,690 ms | 706 | 1,615 | 44.3 s | quiet |
| r1 (16:21Z) | 3,946 ms | 696 | 1,997 | 53.4 s | 1.31 |
| r2 (16:48Z) | 3,586 ms | 660 | 1,728 | 49.1 s | 0.01 |
| experiment, WALK_AHEAD 48 (not kept) | 4,287 ms | 891 | 1,679 | 60.5 s | 0.27 |

The p95 still FAILs its ci of 3,000 ms. The rest of 16,-35's cost is the chain trace itself: on the pool it fills about 6,700 chunks
(2,779 serial; square prefetch boxes reach well past the contour's corridor) and the 7-sample CPU rose to about 50 s, so the trace is now
CPU- as much as latency-bound; a larger box was worse.

## Open

- The p95 row: the next lever is the cold quay chain trace of one tile (prefetch only the contour's corridor instead of square boxes, or
  fill the next box while the walk works in the current one), a QUAYLAZY follow-up, or a decision on the row. Report options in
  `.claude/reports/p6-cascade-2fix.md`.
