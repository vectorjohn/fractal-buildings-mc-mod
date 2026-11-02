# p6-simtimeflake: wall-clock asserts in fast tests

`OrderSimHarnessTest.smokeFastOrdersPass` asserted `ms < 10000` (wall) and failed at 10115 ms on a loaded perf worker (4257 ms locally). It, `ScenarioFinderTest.plannerBuildTimeIsSeconds`, `InvariantsTest.speed` and the 60-storey tower in `RoomSplitTest` now assert on the thread's CPU time (`ThreadMXBean.getCurrentThreadCpuTime`) with generous bounds (30 s, 60 s, 20 s, 20 s). Wall time is still printed. Correctness assertions are unchanged. Left alone: `WorkersShutdownTest` (the 250 ms grace is wall time by design; latch-held task, bound 5 s). Other nanoTime uses in tests only print. No ROADMAP item.

Caveat: CPU time of the calling thread undercounts work done on other threads (OrderSim), so the bound is a loose sanity limit, not a speed guard; speed budgets live in `scripts/perf.sh`.

Verified: `:mod:test` (full) and planner-core `InvariantsTest`, `RoomSplitTest` pass. Full planner-core suite not rerun (only those two planner tests changed).
