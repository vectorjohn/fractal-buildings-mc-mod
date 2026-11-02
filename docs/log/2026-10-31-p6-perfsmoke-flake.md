# p6-perfsmoke-flake

Flake: `PerfServerHarnessSmokeTest` failed "not ready after 132947 ms on tick 13" at host load 25-34. Cause: its first `flyTo` is a 3000-block teleport that `TeleportHold` holds until the 3x3 chunks at the target are FULL (up to a 90 s cap); the test's own wall budget was 120 s, leaving 30 s for 60 hops on a server managing 13 ticks in 130 s.

Fix (test-only): the budget is `TestArea.WAIT_MILLIS` (600 s; made public) like the other wall-bounded tests. The check is unchanged (60 hops, flew past x 3050, chunk view followed). The wait was already a condition, not a tick count, so nothing else needed changing; production code untouched.

Verification: see the report; filtered run `-Pfb.gametestFilter=fractalbuildings-test:perf_server_harness_smoke_test_*` pinned with `taskset -c 5`, repeated runs, and `scripts/test-all.sh --no-client`.

Server-thread question (coordinator): in the failing batch (post-merge of fb2b608e) every test's chunk wait reports 7-15 ticks over 77-160 s. jstack samples of "Server thread" during a batch run here (every 3 s) show test bodies on the thread (TestArea.run body, VetoWorld.generateStarts, OrderGameTest.with, StartSupport.place/clear) with vanilla noise/biome sampling beneath them; no frame of production worldgen/warmer code blocking it. So the slow ticks are the test batch running multi-second bodies inside ticks (plus host load), not a production stall; this run's batch ticked normally (7786 ticks per area wait). It does not explain the perf worker cold-arrival 9-11 s ticks (COLDARRIVAL), which come from a different run (no mock-player batch).
