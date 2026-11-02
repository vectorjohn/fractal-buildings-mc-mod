# CASCADE step 4: A3, fan-out

Branch `claude/p6-cascade-4`, base `claude/run-phase6`. Step 4 of [plans/jflazy.md](../plans/jflazy.md). No timings taken (step 7 measures on a quiet perf worker).

## Done

`WorldPlanner.prefetch(n, piece)` runs independent pieces on `probes.reads().fanout()` (the PARREADS caller-runs fanout over the mod's reads pool; it does nothing when the fanout is serial, so the CLI, tests and the count harness run exactly the old code). A piece only fills a memo; its failure is dropped and the serial code, which asks again in its own order, meets it. Prefetches added:

- `linkState`: `served(a,b)` and `served(b,a)` side by side.
- `approachRoads`: the main approaches (`drawOne`, memoised) side by side, then the old serial merge in index order (alternates stay serial: they depend on `used`).
- `drawnApproaches`: the `linkState` of each approach's edge.
- `computeApproachNodes`: the settlement's cell skeletons.
- `drawApproach`: `rawPlan` of the tiles around the first 24 stations, before the serial `streetLevel` loop.
- `ExitTrim.cutBeyondWalk`: new `Plans.ahead(tiles)` hint (default no-op), called every `WALK_WINDOW` = 48 columns with the tiles the next window reads; `trimmedPlan`'s lookup fans them out.

Executor and deadlock argument: the reads pool, not the worker pool the warmer and chunk jobs run on, so the warmer is never starved. `Fanout.over` is caller-runs: the caller takes pieces from the same counter and waits only for pieces another thread is running. Pieces call `drawOne`, `rawPlan`, skeletons, `served` and `linkState` of other keys; none waits on a memo flight owned by its own caller (`rawPlan` never reads the link rule; `linkState`'s own prefetch asks `served`, not `linkState`). A flight owner is always running, so every wait ends. Nested fan-outs (rawPlan -> junction field reads) are the same caller-runs shape.

## Verification

- `CascadeFanoutTest` (3 tests): first 3 settlements with a served gate of pt1573 and w12345: every gate answer, link state and trimmed tile plan (7x7 tiles) on a 6-thread pool with random start delays equals the serial planner, in file order and two shuffled orders, one round called from inside a pool thread; a saturated pool (4 threads held, caller does all pieces); 8 threads released together on one planner asking different tiles of one settlement, 3 rounds. Asserts the fanout was really called with several pieces.
- Mutation: `drawOne` returning null for index 0 only on a pool planner fails `aPoolGivesTheSameAnswersInAnyOrder`. (Clearing `approachRoads` did not fail it: with the short cut, true answers never reach `approachRoads`; only alternates would show. Not a gap in this change.)
- Counts (`PerfCascadeTest`, serial harness, tile 14,-36): identical with and without the change (chunk fills 15,936 after plan, 16,460 after neighbours; raw plans 31 / 38), as the serial fanout skips every prefetch. The step 3 log's 16,248 / 40 come from an earlier tree; at 04fa4cfa on this host the harness gives 15,936 / 31 (not investigated). On a pool the work is the same plus at most one 48-column window of tiles past an early walk stop.
- Layers: see the report.
