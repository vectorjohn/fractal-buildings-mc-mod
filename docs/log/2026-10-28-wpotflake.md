# WPOTFLAKE

Flake: `WorldPlannerOrderTest.eightThreadsTinyCachesAndAnEvictor` TimeoutException (line 139 = the 5 min `f.get`) on the perf worker, twice.

Diagnosis: not a deadlock. With capacity-1 caches every lookup recomputes, so 8 walkers are compute-bound: 62 s locally at load 14, 87 s, and 231 s at load 27 (24 busy-loop processes, 6 min Gradle run), close to the old 5 min total bound. TESTSPEED changes nearby did not touch this test.

Fix: replaced the total bound by a progress-based hang detector (`awaitWithHangDetector`): fails with a full thread dump if no tile finishes for 180 s, or after 30 min overall. New test `theHangDetectorFailsWithAThreadDump` (a never-finishing task, 1 s stall) shows it fails loudly with stacks. Determinism assertions unchanged.
