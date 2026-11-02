# COLDARRIVAL: cold-arrival perf row and vanilla /locate baseline

Date 2026-10-31, branch `claude/p6-coldarrival`. Input for John's REGIONPERF decision ([regionperf-decision.md](../review/regionperf-decision.md) sections 7 and 8).

## What was done
- `PerfServerHarness` has a second mode, `-Pfb.perfMode=arrival` (`mod/build.gradle` passes `fb.perf.mode`): a mock spectator at spawn waits 40 ticks, then teleports through `ServerPlayer.teleportTo(level, ...)` (the `/tp` path, so TeleportHold can take it over) to the perf metropolis centre (2432, -5696). The clock runs from the call until the player is there and every chunk of the 9 x 9 view (radius 4 = `view-distance=4` in `scripts/perf.sh`) is FULL. It also records the longest server tick, whether TeleportHold engaged, for how long, whether it timed out, and whether the 3 x 3 was FULL at release. Rows `mod.arrival.*` / `base.arrival.*` in `world-arrival-<variant>.json`.
- Why a separate pass: the cold 9 x 9 block rows already use the same site, and any arrival before or after them would warm the region layer for the other. `scripts/perf.sh` runs the pass after the main pair in fresh worlds (`FB_PERF_ARRIVAL=0` skips it); `FB_PERF_VARIANTS` still orders mod/base.
- Base-world `/locate structure #minecraft:village` fresh (`PerfSupport.locateRowsBase`): rows `base.locate.village.fresh.ms` / `.repeat.ms`.
- `mod/perf-budget.json`: four report-only rows (`budget: null`: no ceiling, never fails; still required in a full run). New optional `baseKey` gives the vanilla value; `scripts/perf-check.mjs` prints Vanilla and Mod / vanilla columns (this is the summary table; `perf-merge.mjs` only merges the arrival rows in). Self-test cases added in `scripts/perf-check.test.mjs`.

## Verification
- `node scripts/perf-check.test.mjs`: all ok (incl. report-only row, 30.0x ratio; absent report-only row still fails a full run).
- Local functional smoke of both arrival variants (not a measurement; this host is never quiet): both complete, 81/81 chunks, hold engaged in both.
- `scripts/test-all.sh --no-client` locally: everything passed except planner-core, which failed with `NoSuchFileException ... in-progress-results-generic.bin`: my own mistake, a second test-all started by accident in the same worktree. Rerun `scripts/suite.sh scripts/dev ./gradlew :planner-core:test`: pass.
- Measurement: `scripts/evidence.sh p6-coldarrival-rN scripts/remote.sh -- scripts/perf.sh`, twice, the perf worker (12 CPUs, no renderer), quiet before and at every step, ends "perf: quiet host". Run 1 mod first, run 2 base first (`FB_PERF_VARIANTS="base mod"`).

| Row (the perf worker, quiet) | Mod run 1 / run 2 | Vanilla run 1 / run 2 |
|---|---|---|
| Cold arrival, 9 x 9 FULL | 69.5 s / 71.5 s | 4.7 s / 5.1 s |
| Longest server tick in the wait | 9.2 s / 11.3 s | 0.38 s / 0.44 s |
| TeleportHold | 30.0 s, timed out, 3 x 3 not FULL / same | 3.1 s / 3.3 s, FULL at release |
| `/locate` village fresh (base world) | | 5.26 s / 5.26 s |
| Mod `/locate` settlement fresh, village via veto | 5.34 s, 4.85 s / 5.33 s, 4.88 s | |

Both runs exit 1 on `region.cold.warm.ms` (11.4 s / 11.7 s over its 10 s ceiling) and `locate.fresh.ms` (5.3 s over 5 s): the known REGIONPERF rows, unchanged by this item.

## Findings
- The mod's cold arrival is about 14x vanilla's, in line with the earlier 9 x 9 block ratio (23x), and the 30 s hold never covers it: it times out with the 3 x 3 not yet FULL. The tick stall (9 to 11 s, one tick) did not show in the earlier flight rows or in my local smoke (52 ms): it depends on the host; worth a look in the decision (a server-thread wait inside the chunk pipeline).
- Vanilla's village locate takes the same 5.3 s as ours, so the `/locate` ceiling of 5 s is below vanilla's own cost for a fresh area.
- Vanilla's arrival includes a 3 s TeleportHold: the hold also runs in the base world (hold then releases at 3 x 3 FULL), so the "vanilla" figure is not a bare teleport.

## Assumptions / deviations
- "Server view distance" is 4 in this harness (perf.sh's server.properties), not the vanilla default 10. A real player at 10 loads 441 chunks, not 81.
- "Sent" cannot be observed (the mock connection never acks batches): FULL plus player-arrived is the end point.
- The summary table with the ratio column lives in `perf-check.mjs`, not `perf-merge.mjs` as the brief said.
