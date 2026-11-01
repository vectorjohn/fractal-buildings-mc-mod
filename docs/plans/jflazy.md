# Plan: JFLAZY: the junction field on cold arrival, and the cascade a cold chunk really waits for

Lead-coder plan, 2026-10-31, branch `claude/p6-jflazy-plan` (base `claude/run-phase6` at 1f915a1d). Item: JFLAZY (make the
junction field lazy or cheaper on cold arrival), from the QUAYLAZY step 6 recommendation
([regionperf-decision.md](../review/regionperf-decision.md) "After QUAYLAZY"; `.claude/reports/p6-quaylazy-6.md`). Goal behind it:
John's teleport hold "for what the tile requires, max 15 s", revisited once the slowest cold chunk is under about 12 s.

# Summary

**The premise was wrong.** QUAYLAZY step 6 concluded that "the junction field and region layer" make up the 34.7 s slowest cold chunk.
A wall-clock profile of that chunk (two runs on a quiet beebox, below) says otherwise:

- The **junction field is about 3.5 s** of the 35.5 s, plus about 1.6 CPU-s for a second settlement's field further down. The
  **region layer is about 0 s** in that row: the flight before it already decided the region cells.
- **About 24 s (two thirds) is one wait.** The chunk reads a neighbouring tile's ground (CITYQ spill clear). That neighbour's plan
  is `WorldPlanner.trimmedPlan`, which runs MAINSTUB's exit trim (6.25 `ExitTrim`: the cut walk `fullCut` and the link rule
  `gateServed`). The link rule then plans the approaches of the metropolis **and of the settlements at the far ends of its road
  links**: their skeletons, their junction fields, their rim tiles' district plans and quays. All of it runs on one worker thread
  while the chunk thread waits.
- The cold arrival (53-55 s, hold 48-49 s) looks the same: about 9 s of warm, then about 33 s with the chunk threads waiting on the
  link rule and exit cut, then about 10 s of plans and buildings.

**An exact lazy junction field would not pay.** A tile's streets need their whole length graded, and junction levels couple across
the street graph. With safe bounds (world height -64..320), the junctions and streets one tile must read cover 60-82 % of the
field's chunks on the two gate metropolises. Even an oracle that knew the true ground range (60..110) needs 15-36 %, and the 529
chunks that only the edge tests read are about 11 %. The best case is to save about 2-3 s of a 35 s chunk, with a large
determinism risk. **Recommendation: don't build JFLAZY as briefed.** Build CASCADE (below): an exact (byte-identical) cheaper
evaluation of the exit trim and the link rule, run in parallel and warmed early. Keep two small output-neutral junction-field
items (B1, B2) as optional parallel steps.

**Expected gain, honestly bounded** (details at the end): the slowest cold chunk goes from 35 s to about 13-24 s, and the cold
arrival from 54 s to about 25-40 s. **Under 12 s is unlikely without a rule change** (design questions DQ1, DQ2), so the 15 s hold
cap is not reachable from this plan alone. QUAYLAZY over-predicted by 2x; these ranges are wide on purpose.

# What was measured

**How.** A throwaway wall-clock sampler (not committed; step 1 commits a clean version): every 500 ms it records every thread that
has a planner frame, RUN or WAIT, and classifies it by part, with `LINKRULE>` / `EXITCUT>` markers when `ExitTrim.gateServed` /
`ExitTrim.fullCut` is on the stack. Each run also records JFR, through `scripts/profile.sh` with `FB_PERF_VARIANTS=mod`
(`jdk.CPUTimeSample`, stack depth 1024).

**Runs.** Two runs on beebox (12 CPUs, dedicated server, no renderer), commit 1f915a1d plus the sampler. Each was sent when
`ssh beebox job status` showed nothing running or queued. `scripts/quiet-check.sh --beebox` was QUIET before each run and after
the last. Inside the job, quiet-check was QUIET at start and at world end: other-cpu 0.02-0.04, other jobs 0. Load at start was
0.21 and 0.25; at world end 5.10 and 6.21, all from the run's own work. Evidence (forensic, gitignored):
`.claude/reports/p6-jflazy-plan-prof-evidence/1f915a1d9f-20261101T045039Z` and
`.claude/reports/p6-jflazy-plan-prof2-evidence/1f915a1d9f-20261101T050156Z`.

**Caveat.** JFR and the sampler were on, so these are diagnostic runs. Their row values match the unprofiled QUAYLAZY step-6 runs
within 3 %: chunk#0 35.6 / 35.5 s against 34.6-34.9 s; arrival 53.8 / 55.3 s against 52.0-54.0 s; hold 48.1 / 49.2 s against
46.3-48.1 s.

## The slowest cold chunk, chunk#0 (148,-360), both runs

| t (s) | Chunk thread (Worker-Main) | Elsewhere |
|---|---|---|
| 0-3 | `WP.tile`: streets and skeleton (the 8 member cells' skeletons, single-column coarse and biome reads, serial) | nothing |
| 3-6.5 | `WP.ground`: **junction field** of the metropolis | the 4 reads-pool threads filling its chunks |
| 6.5-8.5 | lazy quays of the tile | reads pool |
| 8.5-9.5 | tile plan, the core tile's exit cut, buildings start | workers start the 8 neighbour prefetches |
| **9.5-33** | **WAIT** in `FlightCache.get` (`WorldPlanner.java:304` `plan` of a neighbour, reached from `ground`) | `fractalbuildings-worker-2` computes that neighbour's `trimmedPlan`, **entirely inside `ExitTrim.gateServed`** from t=9 to t=33 (JFR, per second). Another worker waits 24 s in `BoundedCache.getOnce` for the same values |
| 33-35.5 | buildings of the tile | |

The worker CPU in the cold phase was the same in both runs within 2 %. It is only the workers' own CPU; their reads-pool helpers'
CPU (about 65 CPU-s, all chunk fills) has no planner frame and can't be attributed:

| Worker CPU, cold phase | run 1 | run 2 |
|---|---|---|
| link rule (`gateServed`): skeletons and networks of other settlements | 7.4 s | 7.4 s |
| link rule: district plans of rim tiles (approach street levels) | 4.9 s | 5.0 s |
| link rule: quays of those tiles | 4.9 s | 4.8 s |
| link rule: junction field of another settlement | 1.6 s | 1.6 s |
| exit cut walk (`fullCut`): raw plans along exit roads | 6.3 s | 6.2 s |
| the neighbour tiles' own plans and quays | 11.6 s | 11.6 s |
| junction field directly on workers | 0.0 s | 0.1 s |

## The cold arrival (teleport from spawn, TPHOLD, 9 x 9 view FULL), run 2

| t (s) | What runs |
|---|---|
| 0-9 | Warmer (`warm`): region cells for the road graph, intercity road stations, the metropolis's skeletons and network, its junction field (about 4.5-9 s on a worker). Reads pool busy |
| 9-44 | Chunk threads WAIT on `ground` of a neighbour tile. Workers in `LINKRULE>` (other settlements' skeletons, roads, junction field, quays) and `EXITCUT>` (raw plans and quays along exit roads). From about 30 s, workers also wait on skeletons and networks that another thread is computing |
| 9-54 | **One warmer thread runs "streets and skeleton" non-stop**: `warm(x, z, 2 * cellSize)` builds the network and junction field of every settlement within 2 km, one after another, during the hold |
| 42-54 | Buildings and plans of the view's tiles |

## Why the cascade happens (code read at 1f915a1d)

- **The chunk reads a neighbour's ground.** `SpillClear.run` (`mod/.../worldgen/SpillClear.java:137`, called from
  `TilePiece.java:150`), `StreetAirFix.java:62` and `ClaimMask.java:97` read `planner.ground(ix, iz)` and `plan(ix, iz)` of the
  tiles a chunk touches or borders. `ground` needs `plan` = `trimmedPlan` (`WorldPlanner.java:313`).
- **The trim.** For a tile that is not the core tile, each exit road crossing it is handled in `trimmedPlan` (`WorldPlanner.java:320-327`):
  1. `ExitTrim.cutBeyond` (`ExitTrim.java:189`) can prove "the cut lies beyond this tile" only when the road's first m + 1 columns
     all lie in this tile. A road that starts at the hub in the core tile never passes, so every other tile it crosses falls through
     to step 2.
  2. `fullCut` (`ExitTrim.java:171`, memoised per settlement and road in `fullCuts`) walks the road's raw plans outward from the hub
     until the cut.
  3. When the cut lies within the tile, `gateServed` (`ExitTrim.java:165`) asks `exitServedAt` (`WorldPlanner.java:892`).
- **What `exitServedAt` computes.** `drawnApproaches(site)`, which needs `approachRoads(site)`, which builds **every** approach of the
  settlement: `drawApproach`, which needs `streetLevel` on up to 24 stations, which needs the raw plans of the 3 x 3 tiles around
  each station. It then calls `linkState` for each approach's port.
- **What `linkState` computes** (`WorldPlanner.java:800`): `served(a, b)` and `served(b, a)`, which need `computeServed` of both ends,
  which needs `approachRoads` of the **other** settlement, which needs its skeletons, network, junction field, and its rim tiles'
  plans and quays. For a one-sided link it also checks every edge of the far settlement. All of this is pure and cached
  (`linkStates`, `servedLinks`, `rawApproaches`, `nodeCache`), but cold it is serial on whichever thread asks first.

# The junction field: what it computes, and why exact laziness does not pay

`JunctionField.build` (`planner-core/.../settlement/JunctionField.java:323`) does five things:

1. **Whole streets** (`wholeStreets`, line 208): skeleton roads, lattice border and arterial segments, and grid-line runs over the
   network box. The runs come from exact edge tests at 8-block nodes (`borderEdgeOk`, `gridSideEdgeOk`).
2. **Contacts and pads**: footprint overlaps and union-find. Geometry only.
3. **Ground**: the median exact ground of each junction's pad columns.
4. **Levels**: two multi-source shortest-path envelopes over the junction graph (`envelope`, line 622), plus deck floors and gate
   clamps. Edge caps come from the grader's anchors, which depend on water along each street (`anchorsAndDecks`).
5. **Profiles**: `Grading.profile` of every whole street with its pins (reads floor and water on every column).

A tile reads `pins`, `profile` slices, `levelsAt` and `splits` for the streets near it.

**Counts** come from a throwaway probe (`JfProbe`, not committed), kept as `.claude/reports/p6-jflazy-plan-probe/JfProbe*.java` with both runs' sampler logs on recorded terrain. They are distinct 16 x 16 chunks, so no
timing is involved:

| | gate-metropolis-temperate (the perf city s2_m6) | gate-metropolis-mixed (s2_m2) | gate-temperate-city |
|---|---|---|---|
| chunks the field reads | 4,641 (live: 4,924) | 2,454 | 602 |
| first read by: edge tests / pad ground / water along streets | 1,854 / 760 / 2,027 | 1,193 / 352 / 909 | 256 / 60 / 286 |
| read only by the edge tests | 529 (11 %) | 337 | 78 |
| whole streets; junctions; largest component | 558; 435; 419 | 375; 222; 200 | 94; 45; 40 |
| junctions whose lower envelope another junction decides | 29 of 435 (at most 3 hops, rise 33) | 14 of 222 | 3 of 45 |
| one tile's closure, true ground range as bound (reach 16 / 256) | 8-21 % / 21-36 % | 9-23 % / 38-48 % | 49-61 % / 83-90 % |
| one tile's closure, safe bound -64..320 | 60-82 % | 71-79 % | 68-90 % |

- In practice the coupling is weak: few levels are decided by another junction, and only a few hops away. **Exactness, though,
  needs a bound you know before reading.** With the world's height range as the bound, a tile has to explore most of the junction
  graph. The only bound tight enough is the settlement's true ground range, and you get that only by reading every pad: the
  1,255 chunks of pad columns that the field already pays.
- Grading a street needs its whole length: grid runs reach up to 545 columns, and skeleton roads cross the city.
- **Verdict:** at most 2-3 s off one cold chunk, at the price of a dynamic, bound-driven exploration whose correctness rests on
  bounds. **Rejected.** A bounded-radius rule (an output change) would let neighbouring levels break the grade limit. That brings
  back the junction steps the field exists to prevent, so it is rejected as well.
- **The flat-pad fix** (QUESTIONS "Flat junction pad"; [road-refine](../research/spikes/road-refine.md)) changes how overlapping
  streets form pads. **Nothing in this plan changes the field's algorithm**: B1 changes only thread counts, and B2 only how
  edge-test-only chunks are read, both output-neutral. So the plan does not fight that fix. If the pad fix lands first, B2 rebases
  onto it and keeps its equality test.

# Approach: CASCADE, the exit trim and link rule cheaper, in parallel, and early

Every change below must leave every answer byte-identical. The guards are: live reference paths kept as oracles (as QUAYLAZY kept
`settlementQuaysReference`), fresh-planner-per-query equality tests, shuffled orders, saturated-pool tests, and the order test.

- **A1, bounded exit-cut walk.** `trimmedPlan` needs only one of two answers: "the cut is at or beyond index m" (the tile is
  untouched), or the exact cut when it lies inside the tile. Add `ExitTrim.cutWithin(src, id, m, plans)`. It runs the same scan
  as `cut`, outward from the hub, but stops with `BEYOND` once the scan has passed m plus the largest look-ahead the rule uses
  (the lot-free gap `GAP` + `KEEP` + the "unless a quarter lies beyond" check). It returns the exact `Cut` when the cut falls
  earlier. `fullCuts` stays as the memo for the whole cut (CLI, `exitCuts`, `stubs`). Tiles near the hub then stop walking raw
  plans out to the rim. Equality: for every exit road and every m, `cutWithin` agrees with `fullCut` (`BEYOND` iff
  `fullCut.index() >= m`, else equal).
- **A2, short-circuit link rule.** `exitServedAt(site, x, z)` and `served(owner, other)` are boolean questions answered today by
  building every approach. Exact short cuts:
  1. `exitServedAt`: draw only the main approaches whose exit is within 2 blocks of (x, z). If one is drawn (non-null) and its port
     is null or its link is not DROPPED, the answer is true. Otherwise fall back to today's full computation, because alternate
     approaches depend on all the main ones (`used`).
  2. `served(owner, other)`: if the main approach toward `other`'s port is drawn, the answer is true. Otherwise fall back.
  3. `linkState`: evaluate the far settlement's other edges only when needed. They already are only needed for one-sided links,
     so this is unchanged. Keep the order of evaluation; only skip work whose result can't change the answer.

  Each short cut draws one approach per side instead of all of them, and plans only the rim tiles around that approach's
  stations. It still pays the far settlement's skeletons and network (`approachNodes` needs them) and its junction field (the
  rim tiles' plans need it).
- **A3, fan-out of what remains.** These are independent and pure, so they can go on the reads fanout
  (`probes.reads().fanout()`, caller-runs), joined in fixed index order:
  - the skeletons of a settlement's member cells inside `approachNodes` and `computeServed` (as `warm` already does,
    `WorldPlanner.java:519`);
  - the raw plans of the tiles around an approach's first 24 stations, prefetched before the serial `streetLevel` loop;
  - `served(a, b)` and `served(b, a)` of one link;
  - the drawn main approaches of a full `approachRoads`;
  - the raw plans along a `cutWithin` walk, a window of k tiles ahead.

  These are prefetches only: the serial code then reads the same caches in the same order.
- **A4, warm the cascade early, and stop the warmer from competing during a hold.** In `RegionWarmer.warmAt`
  (`mod/.../RegionWarmer.java:120`):
  1. Right after the destination tile's plan, and before `prefetch`, run `planner.plan` of the 8 neighbours on the warmer pool
     (that is what pays the trim). The point is to start the link rule for the destination settlement's links at t = 0, beside
     the skeleton and junction-field work, instead of after it.
  2. Order `warm`'s settlement loop by distance from the point. The destination settlement goes first, then the settlements its
     links reach, then the rest.
  3. Under a teleport or portal hold, warm only the destination settlement and its link neighbours. The other settlements within
     2 km wait until the hold ends.

  This needs a small public `WorldPlanner.linkNeighbours(site)` (the far ends of `roads.edgesOf(graphNode(site))`).
- **B1 (optional), reads threads.** The reads pool is a third of the CPUs, at most 6 (`Workers.readThreads`; 4 on beebox). While
  the chunk threads wait, cores sit idle. Measure `-Dfractalbuildings.readThreads=4/8/11` on quiet beebox and keep the best
  default. No code change unless the default moves.
- **B2 (optional), sparse edge reads.** The 529 chunks only the edge tests read could be single-column reads (0.35-0.55 ms each)
  instead of chunk fills (1.9 ms). That saves at most about 0.5-1 CPU-s per metropolis field. Low priority; only if B1 shows the
  field still on the critical path.

**Rejected alternatives:**

- *An exact lazy junction field* (the brief): numbers above.
- *A bounded-radius junction field* (an output change): breaks the grade limit between neighbouring junctions.
- *Read neighbours' untrimmed ground in the mod*: the seam would differ from the neighbour's own trimmed build.
- *Take `streetLevel` from the junction field's whole-street profile*: the nearest street to a station can be a lane, a sub-street
  or a plaza that only the tile plan knows. That can't be proved without the plan.
- *Raise the hold cap and stop*: John rejected the slow path (regionperf section 7).

# Steps

```
step 1 (tooling + count harness) ──┬─> step 2 (A1 cut walk) ──┐
                                   ├─> step 3 (A2 short cut) ─┴─> step 4 (A3 fan-out) ─┐
step 5 (A4 warmer order) ──────────┤                                                   ├─> step 7 (verify + measure)
step 6 (B1 reads threads) ─────────┘ (needs a quiet beebox; any time)                  ┘
```

Steps 1, 5 and 6 can start at once. Steps 2 and 3 run in parallel after step 1: they touch different methods (`ExitTrim` and the
`WorldPlanner` link-rule methods). Step 4 waits for 2 and 3, because it fans out the code they change. Step 5 touches only
`RegionWarmer` plus one small `WorldPlanner` accessor. B2 is optional, after step 7.

## Step 1: cascade count harness and the sampler (S; parallel with 5 and 6)

Files: `mod/src/test/.../perf/PerfReachTest.java` (or a sibling `PerfCascadeTest`), `mod/src/gametest/.../perf/PerfCore.java`,
`PerfServerHarness.java`, `scripts/JfrAttrib.java`, `mod/perf-budget.json`.

- **Sampler, behind `FB_PERF_SAMPLE=1`.** Port the throwaway sampler (`PerfCore.sampler`, `jfPart`) from `.claude/reports/p6-jflazy-plan-sampler.patch` (a diff against 1f915a1d; gitignored, on this machine) and forward the variable
  through `scripts/perf.sh` and `mod/build.gradle` as `FB_PERF_QUAYTRACE` is forwarded. Output lines:
  `[JFPROF] <label> t=<s> <thread> RUN|WAIT <part> @ <top frame>`, started per cold chunk and for the arrival.
- **JfrAttrib and PerfReachTest categories.** Add "link rule" (`ExitTrim.gateServed` on the stack) and "exit cut" (`fullCut`,
  later `cutWithin`), ranked above "quays" and "junction field".
- **Cascade count test** (live noise, as `PerfReachTest`; enabled by `-Dfb.perf.cascade=1`):
  1. Build a fresh planner over `LiveProbes` with no eviction.
  2. Call `plan` on the tile of chunk (148,-360) (block 2376,-5752).
  3. Call `ground` on each of its 8 neighbours.

  Count per step: raw plans computed (per settlement id), approaches drawn, `computeServed` calls, settlements whose skeletons
  were built, junction fields built, and chunk fills by part. Write `mod/build/perf/cascade.md`. Counts are deterministic: no
  quiet host needed. Record the baseline at the base commit in the log.
- **Perf rows.** `cascade.neighbours.chunkFills` and `cascade.neighbours.rawPlans`, from the same calls in `PerfPlannerTest`, with
  ci = 1.2 x the first value. They are count rows, always enforced, so a change that brings the cascade back fails without a
  timing.
- **Tests:** the new test runs and prints the table. `node scripts/perf-check.test.mjs` stays green.
- **Done when:** the baseline table is in the log and the count rows are in `perf-budget.json` (report-only until step 7).

## Step 2: A1, bounded exit-cut walk (M; after 1, parallel with 3)

Files: `planner-core/.../settlement/ExitTrim.java`, `WorldPlanner.java` (`trimmedPlan`), `MainStubTest.java` or a new
`ExitCutWalkTest.java`.

- Read `ExitTrim.cut` and its helpers (`lastServed`, `GAP`, `KEEP`, the "quarter lies beyond" check, `wallColumn`, decks) and write
  down the largest look-ahead past a candidate cut column, as a constant with a comment that cites the lines. Then implement
  `cutWithin(src, roadId, m, plans)` → `Cut` or `BEYOND`, sharing the scan with `cut` (refactor `cut` into a scan with a stop
  index, so there is one copy of the rule).
- `trimmedPlan`: replace `fullCuts.getOnce(...)` with `cutWithin` memoised per (settlement, road, m), keeping the order of the
  `continue` tests. The `gateServed` call keeps its place, after the cut is known to fall in the tile.
- Keep `fullCut` and `fullCuts` for `exitCuts` and `stubs`.
- **Tests:**
  - `aBoundedWalkAgreesWithTheFullCut`: for every exit road of every settlement in `QuaySites.all()`, the pt1573 and w12345 step-64
    worlds and the 9 gate windows, and for every m from 0 to the road's length: `BEYOND` iff `fullCut == null ||
    fullCut.index() >= m`, else the cuts are equal.
  - `trimmedPlansUnchanged`: `DistrictJson.write(plan(ix, iz))` for every owned tile of s1_m5, s5_m4 and gate-temperate-city equals
    the same with the old `trimmedPlan`. Keep it selectable by `-Dfb.exitcut.reference=true` for good, as QUAYLAZY did.
    Metropolises go under `@Tag("heavy")`.
  - `MainStubTest` unchanged and green.
  - Mutation check (report it): set the look-ahead constant to 0 and `aBoundedWalkAgreesWithTheFullCut` fails.
- Step 1's count row `rawPlans` must drop. Record before and after.

## Step 3: A2, short-circuit link rule (M; after 1, parallel with 2)

Files: `WorldPlanner.java` (`exitServedAt`, `served`, `computeServed`, a new `drawOne`), a new `LinkShortCutTest.java`.

- `drawOne(site, owner, ApproachNodes an)`: a memoised per-approach `drawApproach` (key: site id plus approach index), so the full
  `approachRoads` reuses it. `approachRoads` builds from `drawOne` in index order, then the alternates as today.
- `exitServedAt` and `served`: short cuts as in "Approach" A2, falling back to today's code. **No answer may depend on which path
  ran.** Prove it in the test, not in prose.
- Keep the full path selectable (`-Dfb.linkrule.reference=true`) as the oracle.
- **Tests (`LinkShortCutTest`):**
  - `everyGateAnswerEqualsTheFullRule`: for every settlement of the step-64 worlds (pt1573, w12345, w987654321) and every exit gate,
    `exitServedAt` on a fresh planner equals the reference planner's answer.
  - `everyLinkStateEqualsTheFullRule`: for every edge of those worlds' road graphs, `linkState` from a fresh planner per edge
    equals the reference.
  - `theAskingOrderDoesNotMatter`: one planner, the edges in 3 seeded shuffles; every answer is equal.
  - `trimmedPlansUnchanged`, as in step 2.
  - The order test of the cut tiles (`MainStubTest`) stays green.
  - Mutation check: make the short cut answer true for a drawn approach whose link is DROPPED; `everyGateAnswerEqualsTheFullRule`
    fails on at least one fixture gate. If no fixture gate has a dropped link, find one in the step-64 sweep and pin it.

## Step 4: A3, fan-out (S-M; after 2 and 3)

Files: `WorldPlanner.java`, `ExitTrim.java` (walk prefetch window), tests.

- Fan out the five prefetches of A3 on `probes.reads().fanout()` (caller-runs). Each piece only fills pure, cycle-free caches
  (`rawPlans`, `nodeCache`, `servedLinks`, skeletons). Document in a comment why none of them can wait on the thread that
  started it: `rawPlan` never reads the link rule (`WorldPlanner.java:308`).
- **Tests:**
  - `aPoolGivesTheSameAnswers`: reuse `QuayLazyTest.SlowPool` and `ParallelReadsPlannerTest.PoolReads`. Every
    gate answer, link state and trimmed plan of s1_m5, s5_m4 and the gate city on a 6-thread pool with random delays equals the
    serial run. Call from the test thread and from inside a pool thread, with the pool free and with it saturated.
  - `concurrentNeighboursShareOneCascade`: 8 threads start together (latch) and each asks `ground` of a different neighbour of one
    tile on one shared planner. Every result equals the serial reference. 5 rounds, a fresh planner each.
  - Order test (`scripts/order-test.sh`) and the ORDERSIM sims in `:mod:test`.

## Step 5: A4, the warmer warms the cascade first (S; parallel with 1-4)

Files: `mod/.../worldgen/RegionWarmer.java`, `planner-core/.../world/WorldPlanner.java` (`linkNeighbours`, settlement order in
`warm`), `mod/src/gametest/.../WarmingGameTest.java`.

- `warm`: visit found settlements by (distance from the point, id) instead of id order. **This changes no answer**, only the order
  in which pure caches fill.
- `warmAt`: after `planner.plan(ix, iz)`, submit `plan(nx, nz)` of the 8 neighbours to the workers. Under a hold (TPHOLD or
  PortalHold active for this point; add a flag to `warmPoint`), warm only the destination settlement and `linkNeighbours`, then
  the rest after release.
- **Tests:** `WarmingGameTest` and `TeleportHoldGameTest` green; a unit test that `warm` visits the settlements in distance order;
  the client quick layer (TeleportHold) through `scripts/test-scope.sh`.

## Step 6: B1, reads-pool size (S, measurement only; any time a quiet beebox is free)

- Run `scripts/perf.sh --world-only` on quiet beebox with `JAVA_TOOL_OPTIONS=-Dfractalbuildings.readThreads=N` for N = 4
  (today's default there), 8 and 11. Check that the property reaches the server JVM (it is read in `Workers.readThreads`).
- Run each N twice, same protocol as QUAYLAZY step 6: quiet-check before and after, nothing else queued.
- Report `mod.cold.chunk.max`, the arrival rows and the flight tick rows.
- Change the default only if a gain shows in both runs and the flight tick does not get worse. That is a one-line change, with
  a row in `docs/ASSUMPTIONS.md`.

## Step 7: verification and measurement (assistant or coder; after 4, 5 and 6)

- **Correctness**, on the final merged commit:
  - `scripts/test-all.sh --full` (order test included) through `scripts/anywhere.sh`;
  - `--planner-heavy` for the heavy equality cases;
  - `city-check` on the 9 gate windows (0 new violations).
- **Counts:** step 1's cascade table before and after. Set the count rows' ci to 1.2 x the new values.
- **Timing**, on a quiet beebox only, as QUAYLAZY step 6:
  - two `scripts/perf.sh` runs and one `FB_PERF_SAMPLE=1 scripts/profile.sh` run;
  - same-session before and after;
  - rows: `mod.cold.chunk.max.ms`, `arrival.cold.metro.ms`, `arrival.hold.ms`, `arrival.maxTick.ms`, the flight tick rows,
    `region.cold.warm.ms`;
  - the new chunk#0 timeline, and what is now its longest segment.
- **Docs:**
  - a PERFORMANCE entry;
  - regionperf-decision.md "After CASCADE";
  - NOTES lines (the cascade, the short-cut rules);
  - the TPHOLD15 QUESTIONS line updated with the new numbers.

# Expected gain (estimates; not measured)

The base is the profiled chunk#0, 35.5 s: about 3 s skeletons + 3.5 s junction field + 2 s quays + 1 s tile + 24 s cascade wait +
2.5 s buildings.

| Change | Optimistic | Pessimistic | Why unsure |
|---|---|---|---|
| A1, bounded cut walk | the `EXITCUT` share disappears for tiles near the hub (6 CPU-s) | little, if this neighbour's cut really lies inside it | which neighbour and road cause the wait is not recorded; step 1 records it |
| A2, short-circuit link rule | the cascade drops to one approach per side: the far settlement's skeletons and network + 1-3 rim tile plans, about 5-8 s serial | no gain, if the deciding approach is not drawn and the full fallback runs | the number of links and how often the short cut applies are unknown until step 1 |
| A3, fan-out | what remains drops 2-3x (4 reads threads + idle workers) | 1.3x, if one long serial branch (one settlement's skeletons) dominates | branch widths unknown |
| A4, warm early (arrival only) | the cascade overlaps the 9 s warm: up to -9 s on arrival | none on the cold-chunk row (it runs no warmer) | |
| B1, reads threads | junction field 3.5 → 2 s, quays faster | none, or worse flight ticks | contention with vanilla chunk threads |

- **Slowest cold chunk:** about 13-24 s (now 35.5 s).
- **Cold arrival:** about 25-40 s (now 54 s); hold about 20-35 s.
- **Not reachable from here:** the 12 s target and the 15 s cap. The remaining floor is about 3 s of skeletons + 2-3.5 s of
  junction field + 2 s of quays + 1 s of tile + 2.5 s of buildings ≈ 10-12 s before any cascade at all. Getting well under that
  needs a rule change (DQ1, DQ2) or saving warmed data with the world (regionperf option 6).
- **What would prove this wrong:** step 1's counts. If they show the cascade is mostly the far settlement's skeletons (which no
  short cut avoids), A2 gains little and DQ1 becomes the main lever. Step 1 is cheap and comes first for that reason.

# Risks

- **R1, a hidden input to the short cut.** An approach's drawn state could depend on another approach (for example, a shared
  `used` list that also affects main approaches). Guard: the full-rule oracles and the fresh-planner equality over three worlds.
  If one fails, find the input; don't loosen the comparison.
- **R2, look-ahead too short in A1.** The bounded walk would stop before a rule that looks further ahead. Guard: the exhaustive
  every-m comparison and its mutation check.
- **R3, fan-out deadlock or starvation.** A piece waits on a `getOnce` held by a thread that waits for that piece. That needs a
  cycle in the cache dependencies, and `rawPlan` never reads the link rule, so there is none. Guard: the saturated-pool and
  latch tests. A hang fails the test by timeout (give each `@Timeout(300)`).
- **R4, warmer order changes timing, not answers.** Guard: `WarmingGameTest` and the order test.
- **R5, more total CPU over a visit.** The short cuts add memo entries and may compute one approach twice (short cut, then full
  fallback). Bounded by one extra `drawApproach` per gate. Step 7 reports total chunk fills.
- **R6, the profile is of one city and one chunk.** The perf metropolis s2_m6 is not every city. Step 1's harness should also run
  on gate-metropolis-mixed (2304,-1920) and a town.

# Design questions (for the architect, via the coordinator)

- **DQ1.** MAINSTUB trims an exit road when its gate is not served, and "served" includes the far settlement's side (`linkState`
  drops one-sided links, 6.22). Would it be acceptable for the trim to depend only on this settlement's own drawn approach from
  that gate, and ignore the link state? Then a tile never plans another settlement. Effect: in the rare one-sided-link case, a
  dropped link's approach road would keep its untrimmed main road up to the gate. A road to a road that was dropped is a "road to
  nowhere" again, which is what MAINSTUB fixed. Ask only if step 7 lands above about 15 s.
- **DQ2.** Could the link rule decide "an approach exists" from its geometry alone (route nodes, coarse water), without the
  street-level pins that need the far settlement's district plans? Effect: an approach that today fails the water rule WR2/WR4
  only because of its pinned deck would count as served. That is rare, and it would leave a stub drawn where none is today. Same
  condition for asking.

No interior-designer questions.

# Assumptions (rows for ASSUMPTIONS.md when implemented)

- JFLAZY as briefed (an exact lazy junction field) is not built; the profile and the closure counts above are the reason.
- The reference paths for the exit cut and the link rule stay in main as permanent test oracles.
- The warmer visits settlements by distance, and under a hold warms only the destination settlement and its link neighbours.

# For John

- The junction field is not what makes a cold metropolis slow: it is about 3.5 s of the 35 s. Two thirds of the wait is the "road
  to nowhere" trim (MAINSTUB) checking, for a tile next to the one you stand in, whether the road through it is served by the
  next town over. Answering that plans parts of the next town.
- The plan makes that check cheaper and parallel without changing what gets built. Expect the slowest chunk at about 13-24 s, not
  under 12 s. Getting under 12 s probably needs one of the two rule changes above, and both bring back a few roads to nowhere.
- Nothing needs your decision yet. DQ1 and DQ2 come back only if the measured result stays above about 15 s.
