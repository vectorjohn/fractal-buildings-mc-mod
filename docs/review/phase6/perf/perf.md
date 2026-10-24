# Phase 6 performance budget (6.7): measured values

`scripts/perf.sh` (budget: `mod/perf-budget.json`, plan: [phase6.md](../../../plans/phase6.md) "Budgets"). Seed 987654321, vanilla
terrain, 20 cpus, JDK 25, Minecraft integrated server, lavapipe client (the client does not touch these numbers).
Which run: planner half at loadavg **1.74** (the only run under 2; the other agents' client tests share the machine), world half
at load 0.85 to 3.9 (run 3), then a noisy rerun (load 5 to 22) that only added the vanilla-village locate.
"CI ceiling" is what `perf.sh` fails above; a "miss" is above the budget but under the ceiling.

| What | Budget | Measured (quiet-ish) | Verdict |
|---|---|---|---|
| Cold district plan, median (7 tiles, skeleton warm) | 300 ms | **409 ms** (380 to 472 over runs) | miss, 1.4x |
| Cold district plan, p95 | 1000 ms | 638 ms | ok |
| First tile of a metropolis, cold (skeleton warm) | 1000 ms | 1,291 ms (93,170 exact probe calls, 17 buildings) | miss, 1.3x |
| Prefetched tile: what the lane pays | 20 ms | 0.04 ms | ok |
| Region layer + road network, cold, metropolis | 1000 ms | **4,763 ms** (4,162 to 5,600) | miss, 4 to 5x; 0 when warmed (worker 4.6 s, ahead of the player) |
| Building lookups inline, elytra flight 2.5 km into a city | 5 % | 0.66 to 0.78 % (19 of 2,431) | ok |
| Wall per downtown chunk, mod / none (warmed) | 1.5x | **3.2x** (67 vs 21 ms mean; 46 vs 20 median) | miss |
| Same, cold teleport block | 1.5x | 2.8x mean (87 vs 31 ms); one chunk 4,043 ms (region + skeletons) | miss |
| postProcess per downtown chunk | 5 ms | 1.1 ms cold block, 2.0 ms warmed (3.4 to 4.0 under load) | ok |
| Worst server tick, elytra flight into a city | 250 ms | 337 ms (at 2,311 of 2,500 blocks: arriving at the city) | miss (3,990 ms once at load 22) |
| p95 server tick, flight | 50 ms | 13 ms (vanilla 17 ms) | ok |
| Region-file growth, 162 downtown chunks | +10 % | +62 % to +102 % (1.0 to 1.7 MB, 6 to 10 KB per downtown chunk) | miss (worst case: downtown only) |
| Heap after GC vs no settlements | 500 MB | +51 to +60 MB | ok |
| `/locate #fractalbuildings:settlement`, fresh area / from spawn | 10 s | 1.6 s / 1.1 s | ok |
| `/locate #minecraft:village` (veto), fresh area, server thread | 10 s | 6.7 s fresh (load 5 to 22), 0.4 ms repeated | ok, see below |
| Footprint veto query, cold, 169 cells | - | 7.5 s first call, 58 ms mean over a 12 km walk (10.9 s) | the cost behind the row above |
| Tile recomputes, 3 players / 8 players in different cities (default caches) | 0 | 0 / 0 (21 and 55 tiles, 867 and 2,312 chunk requests, +31 / +69 MB) | ok |

Run it again: `scripts/perf.sh` (10 to 15 minutes, takes the heavy lock). Table of the last run: `mod/build/perf/summary.md`.
