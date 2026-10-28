# WORKERSFLAKE: WorkersShutdownTest flake

Post-merge at 48ea6832 failed `WorkersShutdownTest.anIdlePoolStopsAtOnceAndLeavesNoThreads` (line 34, `assertEquals(before, aliveWorkers())`) in `:mod:test`, host busy.

Cause (by reading; I could not reproduce it before the fix): the test counted all threads named `fractalbuildings-worker-*` JVM-wide before and after, so any other pool alive in the fork (the ORDERSIM classes now share it) or a thread of the previous test still exiting changed the count. It also asserted `shutdown` returned within 2 x GRACE_MS (500 ms), which load can break.

Fix (test only, `Workers` unchanged): each task records its thread; after `shutdown` the test requires `awaitTerminated(10 s)` and joins every recorded thread (10 s), asserting none is alive. The wall-clock bound is gone; the grace behaviour stays covered by the other two tests (the one that ignores the interrupt keeps its 5 s bound, 20x the grace).

Verified: 3 runs of the class with 20 busy loops running: pass. Mutations: no `shutdownNow()` call -> the idle test fails (pool never terminates); `shutdown()` instead of `shutdownNow()` -> the two other tests fail.

The next post-merge run (8d509eed, a superset of 48ea6832) passed, so it was a load or timing flake, consistent with the cause above.
