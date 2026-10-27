# TESTLAYERS: client test layers, B2 off the client, worker shutdown (2026-10-26)

Branch `claude/p6-testlayers` (base run-phase6 `bceeefb7`, then merged run-phase6 for `scripts/test-scope.*`).

## What changed

- **B2 on dedicated servers.** `DistrictOrderClientGameTest` is gone. `gametest/order/OrderServerHarness` (same measuring code, same `Run.problems`
  checks, same mutants) is loaded through `-Dfb.testHook` (added to `FractalBuildingsMod`, text identical to PERFSRV's hook so the merge is clean),
  waits for `SERVER_STARTED`, runs ONE run label on the server thread (`server.execute`, like `runOnServer`), writes
  `<label>.{verdict,report,run.json}` and halts. `:mod:runOrderServer` (loom run, `-Pfb.orderRun=<label>`, own dir `mod/build/run/order/<label>`).
  `scripts/order-test.sh`: phase 1 (forward, clean-centre, config, six mutants) then phase 2 (six other orders, reverse-centre, which compare with the
  phase 1 baselines through `forward.run.json` / `clean-centre.run.json`), `--jobs 6` Gradle processes at a time, ports 25610+, no heavy lock.
  `max-tick-time=-1` (a run is one long server task). EULA: needs a human-made `mod/build/run/order/eula.txt` (worktree or main checkout); the script
  copies that file into the run dirs and stops with the one-line command when it is missing (exit 3). It never writes eula=true.
- **Client layers.** `compat/ClientLayers` (`FB_LAYER=quick|full`; Gradle run directly = full, the scripts default to quick) skips Roads, Foliage and
  Compat in quick; `FB_ONLY=<TestClass>` is the same code as `claude/p6-villroad` 7be31895 (identical text in `CompatOnly.skip`, so it merges) and
  also works for the compat test. `test-all.sh --full` = full layer + order test; `client-test.sh` logs the layer.
- **Worker shutdown.** Cause: planner tasks are CPU work with no interrupt checks (a cold tile plan is seconds, more under client load), so
  `shutdownNow` + `awaitTermination(10 s)` on the server thread waited for the running step. Now `Workers.shutdown` waits 250 ms, a daemon reaper
  reports a straggler after 120 s; `WorldPlanner.prefetch` and `RegionWarmer` check the interrupt flag between steps. `WorkersShutdownTest` (3 tests;
  setting the grace back to 10 s fails `aTaskThatIgnoresTheInterruptDoesNotHoldTheStopper`). B4's `shuttingTheWorkersDownLeavesNoThreads` unchanged.
- Docs: CLAUDE.md test lines, ROADMAP testing table (layer 4b) and notes, verifier/coder agent files (layers), `scripts/test-scope.tsv` layer rows
  (names already matched; descriptions updated; `--check-table`: 0 unmapped), `scripts/evidence.sh` also collects `build/order-test`.

## Verification

- `scripts/test-all.sh --no-client` after merging run-phase6 (205215ca): planner-core 808/811 (3 skipped), cli 36, determinism 17, mod 37/41 + 35 server game tests, viewer 139, viewer-shots OK.
- `WorkersShutdownTest` 3/3; with the grace set back to 10 s `aTaskThatIgnoresTheInterruptDoesNotHoldTheStopper` fails.
- Quick client layer (`FB_LAYER=quick scripts/client-test.sh`, Intel, pinned in `.claude/reports/p6-testlayers-quick-evidence/a03c08fbe3-*`): passed; log 17:31 to 17:44 = 13.6 min (was ~40 min for the whole suite; machine load 30 to 55), Roads, Foliage and Compat skipped by the layer, 7 world shutdowns and no "workers did not stop" line (was 9 of 9).
- NOT RUN: the order harness itself (needs the human-made EULA file, see above, and the machine was reserved) and a full-layer run. The order test is unproven at runtime: `OrderServerHarness` compiles and `processGametestResources` accepts it, `order-test.sh` stops cleanly on the missing EULA (exit 3).

## Round 2 (2026-10-27): merged run-phase6, ran the layers
- Merged `claude/run-phase6` (conflicts in `client-test.sh`, `test-all.sh`, `coder.md`, `verifier.md`: both sides kept; the lock line now names the worker GPU renderer, layer and FB_ONLY). All client tests still call `CompatOnly.skip`/`ClientLayers`.
- `FB_LAYER=quick scripts/client-test.sh` on beebox (AMD radeonsi, job 16): exit 0, BUILD SUCCESSFUL in 11 m 25 s; log shows Compat, Roads, Foliage skipped by `[layers]`, FlyThrough off.
- `FB_ONLY=HelloClientGameTest scripts/client-test.sh` on beebox (job 17): exit 0, 28 s; the other seven tests log `skipped (FB_ONLY=...)`.
- `scripts/order-test.sh` locally (no renderer, not heavy): 11 of 16 pass, 5 fail with the same 52 `unknown` cells (see NOTES); reproduced on rerun. `order-test.sh` now kills its servers on TERM/INT.
