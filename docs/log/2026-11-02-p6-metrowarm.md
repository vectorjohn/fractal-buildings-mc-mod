# 6.34 METROWARM: the region warmer looks ahead by flight speed

Done on `claude/p6-metrowarm`.

- `RegionWarmer.leadBlocks(speed)` (pure): lead seconds go from 5 (speed <= 10 b/s) linearly to 30 (>= 40 b/s); distance capped at `MAX_LEAD` = 1800 blocks (was 5 s, cap 600). At elytra 60 b/s the ahead point is 1800 blocks out instead of 300.
- Concurrency is unchanged: one warm task and one neighbour task per player, neighbours only on tile change (CASCADE 5b). Warming fills pure caches, so output is unchanged.
- Test: `RegionWarmerLeadTest` (monotone in speed, walker keeps 5 s, elytra >= 1500, cap). WarmingGameTest (warmed vs cold world) covers determinism.

Measurement: see the report section "Measurement" (perf worker, `perf.sh --world-only`, before/after, two runs each).

## Measurement (beebox, `perf.sh --world-only`, each run ended `perf: quiet host`)

Worst server tick during the elytra flight (`mod.flight.tick.max`), p95 in brackets:

| Run | Commit | Worst tick | p95 |
|---|---|---|---|
| before 1 | 61705eea (base) | 4622 ms | 11.26 ms |
| before 2 | 61705eea | 8021 ms (row marked skip(load): loadavg rule, host quiet) | 10.80 ms |
| after 1 | 56ae0ed9 | 299 ms | 13.01 ms |
| after 2 | aedd43bb | 876 ms | 12.23 ms |

The history's two modes (0.3 s or 4-5 s) did not both appear: both "before" runs were the slow mode, both "after" runs fast, 4 runs in all, so this is suggestive, not proof. p95 is unchanged within noise. Ceilings untouched.

## Flake fix: WarmingGameTest "warm refused"

Cause: `Workers.awaitIdle` race, see NOTES. Fixed in the helper, no RegionWarmer change. `AwaitIdleTest` is the control (fails on the old helper). Three `:mod:runGameTest` runs, 46 tests each, all green; merged GLASSSTUB's desert city test is in them.
