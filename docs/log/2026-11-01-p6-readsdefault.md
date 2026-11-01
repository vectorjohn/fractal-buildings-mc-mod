# READSDEFAULT: reads pool default

`Workers.readThreads()` default changed from `clamp(cpus/3, 1, 6)` to `clamp(cpus-1, 1, 11)`, from the CASCADE 6 measurement (regionperf-decision.md section 7). The `-Dfractalbuildings.readThreads` override still wins (checked first, unchanged). `LiveReadsTest.readThreadsDefaultIsBounded` upper bound 6 -> 11. No other test or perf budget assumes the old size (`WorkersShutdownTest` uses `readThreads()` itself; perf fixtures are recorded values).

Thread totals (worker pool unchanged, `defaultSize` = clamp(cpus/4, 1, 4)): 20 cpus: 4 + 11 = 15 pool threads; 12 cpus: 3 + 11; 8 cpus: 2 + 7; 4 cpus: 1 + 3. The reads threads only run during a cold planner's parallel terrain reads (caller-runs fanout), so they are idle otherwise. Both pools together stay below cpus on 12+ cpus, but the chunk threads compete during a cold arrival. No timings taken (CASCADE 7 re-measures). Assumption row added.
