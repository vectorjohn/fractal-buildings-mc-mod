# Region layer performance: decision brief

For John, 2026-10-27 (assistant). The measurements, bisect and fixes so far
are in [PERFORMANCE.md](../PERFORMANCE.md), "Region layer row" (regionperf).
This page doesn't repeat them. It covers who pays the cost and when, what
the cost buys, and the options. Numbers are from beebox (12 CPUs, load
about 2) at 83c7a808 unless marked. *Estimate* means it was worked out from
those numbers and not measured.

## 1. What the row measures, and who pays it

The perf row "Region layer + road network, cold, metropolis" times one call:
`WorldPlanner.warm`, on a fresh planner, on one thread, at the centre of
metropolis s2_m6 (2432,-5696, radius 1,430), with a reach of 2 region cells
(2,048 blocks). The budget is 6 s and the ceiling 10 s. It now takes
**36.4 s**. The quay trace that comes after it (`warmQuays`) is measured
separately and takes **69 s**. Run back to back on a worker, as the game
does, the two take **103 s**.

The game makes this same call in two places. Results go into per-world
memory caches that all players share. They are lost when the server
restarts, and they are only needed for chunks that haven't been generated
yet.

| Situation | Who waits, and how long | Status |
|---|---|---|
| `/tp` into an unvisited metropolis (TPHOLD) | The player sees "Generating terrain…" while `warm` and `warmQuays` run. **The hold gives up after 30 s** and teleports anyway, so on beebox-class hardware the player arrives before the work is done and waits on ungenerated chunks for up to about another 75 s on a waterfront city. | From the code (`TeleportHold.timeoutMillis` = 30 s); not measured in game. The accepted tradeoff was "about 4 s" ([PERFORMANCE](../PERFORMANCE.md), "Teleport into an unvisited city"). |
| Walking or riding toward a city | Nothing visible. `RegionWarmer` starts when the city's cells come within 2,048 blocks, which at 4 to 14 blocks/s is minutes ahead. | Inference |
| Elytra into a metropolis | Probably visible on a waterfront city. The warmer's lead at elytra speed is 34 to 68 s (B8), which is less than the quays alone. The regional part is mostly paid already by earlier warms along the flight path; the city's own junction field and quays are not. | Not measured: the perf world half (elytra rows) hasn't run since the regression. |
| Server start, new world | Spawn chunks need the road graph around them, so they pay the regional part inline if spawn is near roads. | Not measured |
| After a restart, a new chunk of a half-built city | That city's whole junction field and quays are paid again. | From the code |

Each player's warm runs as one single-threaded task. Two players
teleporting to two cold cities pay in parallel on separate workers. The pool
has at most 4 workers (CPUs / 4).

## 2. Why it's slow

About 95 % of the time is spent in **Minecraft's own terrain noise**
(`GradientNoise.permute` 52 %, aquifers 18 %). We generate exact terrain for
columns no chunk has reached yet. The time per column hasn't changed; there
are now about 8 times as many columns as before 9911ff15 (JUNCTION r3 and
the 6.10 C1 intercity roads).

| Piece | Work (measured counts) | Share of the 36.4 s | Why it needs exact noise |
|---|---|---|---|
| Intercity road graph: region decisions | 68,343 lattice columns, against 19,302 before C1. To know which roads cross a cell, `warm` asks for the edges of all 25 cells around the point. Each of those needs every settlement within about 3.9 km of the cell. Each of those settlements needs its neighbours within 1.5 km of its rim, and the witnesses whose links must route (ADR 0004 N1, RC). That adds up to roughly 270 cells of a 64-block lattice. | With `intercityRoads` off, warm takes half as long (measured locally), so roads are about **18 s** including the stations below (*estimate*) | Decisions use the same exact height the reviewed cities used. The coarse screen is off by 12 to 16 blocks on average and by 50 to 150 under Terralith or Tectonic ([plan phase 6](../plans/phase6.md), decision 1). Water and peak fractions decide whether a settlement exists at all. |
| Road stations | 14,587 columns: every 8 blocks along each route | (in the 18 s) | The dry rule needs the water depth at every station, and the deck height comes from the ground (ADR 0004 WR1, HT). These replaced the causeways you rejected. |
| Junction field (JUNCTION r1 to r3) | 4,924 whole chunks filled, from 153k reads along all of the city's streets | Most of the other 18 s (*estimate*: the pre-JUNCTION region layer was 4.7 s) | Every junction gets one level that is grade-feasible along each street over the real ground. A wrong ground level brings back the steps between streets. Some of these chunks are reused by the tile plans that follow (memo); how many was not checked. |
| Quays (7.20 R1 r3) | 598k exact reads, traced over every waterfront tile of the city | Not in the row: a separate **69 s** | The quay follows the waterline, so it needs exact water. It is traced for the whole city so that neighbouring tiles cut the quay the same way (`tile-border-agree`). |

## 3. What each piece buys

- **Intercity roads** ([ADR 0004](../adr/0004-roads-between-settlements.md),
  ROADMAP 6.10). Settlements are joined by dry roads with short crossings:
  about 75 to 80 % of settlements are linked, there are no causeways, and
  roads run through terraced cuts. You said "the network looks great"
  (S10). Cutting the graph's reach drops or reroutes links near the edge of
  what was read, and would break the rule that every chunk gets the same
  answer. Coarse heights would put decks in lakes and float them above the
  ground under modded terrain.
- **Junction field** (JUNCTION, backlog 6.3). Where streets meet, they meet
  at one level. `GroundWalkTest`'s step counts went from 370 / 229 to
  107 / 18. Without it, streets meet with 2 to 20 block steps.
  A field read over fewer chunks is fine if it reads the same ground; a
  coarser ground brings the steps back.
- **Quays** (7.20 METROFILL R1). There is one quay line per city, and tiles
  agree at their borders. If each tile traced its own, the quays would show
  seams. Tracing lazily, only the chains that reach the tile being asked
  for, would give the same quays for less work up front.

## 4. Options

| Option | Changes output? | Speed-up | Cost and risk |
|---|---|---|---|
| A. Make the teleport hold wait as long as the work takes: timeout about 150 s, and a message such as "Planning city…" | No | None, but the player no longer lands in void | XS. A long wait on a cold far teleport, which happens rarely. |
| B. Have the hold wait only for what the destination needs: the destination cell's edges, the city network and junctions, and the quays only if the destination tile is waterfront. Warm the rest after release. | No | Hold *estimate*: non-waterfront about 15 to 25 s instead of 103 s; waterfront still includes the quays | S. Chunks further out appear later, as in vanilla. |
| C. Run the work in parallel: region decisions and routes are pure and independent, and junctions and quays can run side by side | No | *Estimate*: 2 to 3x on 3 to 4 workers. Hold becomes max(36, 69) s instead of the sum before any other change | S to M. Vanilla noise must be safe to call from several threads (the game already does), and it competes with chunk generation threads. |
| D. Skip terrain reads for cells whose dice roll can never settle (roll ≥ 0.65, about 35 % of cells) | Same settlements; only the region map's "reason" text changes | *Guess*: about 15 % of the row | XS to S. Need to check that the join rule doesn't read those cells' water or peak values. |
| E. Lazy quay tracing (PERFORMANCE "Quays" Improve) | No, if done right | *Guess*: 3 to 10x on the first waterfront tile | M. It has to keep `tile-border-agree`. |
| F. Read the junction field from sample columns or per tile neighbourhood instead of whole chunks | No (same reads) or a little (per neighbourhood) | *Guess*: 2 to 5x on the junction part | M. Per neighbourhood risks bringing back junction steps. |
| G. W2 coarse screen for region decisions | Yes: decisions no longer match the viewer or the reviewed cities | *Estimate*: 10x or more on the decision share (ADR 0004: about 4 ms per cell coarse, 40 to 100 ms exact) | Needs an ADR 0003 amendment, and decisions drift under modded terrain. It doesn't help junctions or quays. |
| H. Save the region decisions, junction fields and quays with the world | No | The repeat cost after a restart goes to about 0 | M. A file format and invalidation on `genVersion`. |
| I. Raise the budget to what the work costs now | No | None | XS. Leaves elytra flights and spawn unmeasured. |

## 5. Recommendation

1. **Loosen the requirement for cold far teleports**, which are rare. Accept
   a hold of up to about a minute into an unvisited metropolis. Do A now so
   the player never lands in void.
2. **Schedule B, C and D**, which don't change output and are cheap.
   Together they should bring a typical hold to about 10 to 15 s (*estimate*).
   Re-budget the row after that. The count rows already in
   `mod/perf-budget.json` keep the work from growing silently in the
   meantime.
3. **Do E next.** Quays are now the largest single cost on waterfront
   cities.
4. **Keep exact noise. Don't take G.** It trades away the match with what
   you reviewed, and it doesn't touch two of the three costs.
5. **Before deciding anything about travel**, run the perf world half (the
   elytra flight into a metropolis, heavy). That is the case players hit in
   normal play, and it is unmeasured since the regression.
