# 2026-10-27 p6-regionperf: why the region-layer perf row is 45 s, not 4.7 s

- Bench: a trimmed copy of `PerfPlannerTest` (nearest, fresh planner, `warm`, count exact probes), run with `-Dfb.perf=1 :mod:test --tests` at each point on this host (load 17 to 55, so wall time varies 2x on one commit: 60 s vs 116 s at 185k probes). Probe count is the deterministic metric.
- Bisect (probes of `warm` on the metropolis): e9b92460 (perf added) 169,522; 4516ce4b~1 184,773; 0df947c6 (CITYQ) 184,773; 4516ce4b (METROFILL R1) 781,997; HEAD 781,816. Cause: R1 r3 added `Districts.warmQuays` inside `WorldPlanner.warm`.
- Fix: `WorldPlanner.warmQuays` split from `warm`; `RegionWarmer.warmAt` calls both; the perf test, WarmingGameTest and PerfClientGameTest too. After: 184,146 probes. The quays cost the same wherever they run, so the teleport hitch is unchanged; ahead-of-time warming by the RegionWarmer still covers them.
- Verified: `:planner-core:test --tests *WorldPlanner* *QuayWindowTest*` and mod test/gametest compile pass. Server game tests and client perf not run.
- Not done: memoising `exact()` columns inside the quay trace (see PERFORMANCE.md).

## Round 2 (lead-coder, after the verifier found 45 s on beebox with the split)

- The round-1 claim (only quays moved into the row) was wrong. Time bisect with a bench of just `warm` (scratchpad, not committed; local host, load 10 to 58, alternating points): 9911ff15^1 5.6 / 6.6 s, 0 exact probes, 19,302 lattice columns; 9911ff15 62 s; e9b92460 52 / 66 s; 701a96d6 57 s. B8 measured its 4.7 s before merging 9911ff15 (JUNCTION r3 + C1 intercity roads) and never re-ran, so the row was never 4.7 s on the run branch.
- JFR on 701a96d6: about 95 % vanilla noise. Counters: 68,343 lattice columns (intercity graph region decisions; 297,870 region reads vs 52,869 before C1), 14,587 road-station columns, 4,924 distinct chunks / 6,327 fills (junction field, street rules). `intercityRoads` off: 36 s vs about 70 s at the same load.
- Fixes: `LiveProbes.world(exact)` used by `WorldPlanners`, `PerfPlannerTest`, `LiveWarmTiming`, `VetoGameTest` (the perf test had road stations on the chunk memo); `DEFAULT_CHUNKS` 8192; three count rows in `mod/perf-budget.json`; the perf test's worker path calls `warmQuays` too; `warm`'s javadoc back above `warm`. Tried and dropped: a lattice biome memo (climate under 4 %).
- Beebox perf --planner-only at 83c7a808: row 36,360 ms (was 45,240), count rows 4,924 / 68,343 / 14,587 (ok), FAIL on the timing ceiling; evidence `.claude/reports/p6-regionperf-evidence/83c7a808f1-20261027T161611Z/`. test-all --no-client OK locally.
- Still over the ceiling: the budget needs John (PERFORMANCE.md "Region layer row").
