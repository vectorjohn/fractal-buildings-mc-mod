# Region layer performance: decision brief

For John, 2026-10-27 (assistant). The measurements, bisect and fixes so far
are in [PERFORMANCE.md](../PERFORMANCE.md), "Region layer row" (regionperf).
This page doesn't repeat them. It covers who pays the cost and when, what
the cost buys, and the options. Numbers are from beebox (12 CPUs, load
about 2) at 83c7a808 unless marked. *Estimate* means it was worked out from
those numbers and not measured.

**2026-10-31:** the timings in sections 1 to 6 were taken before the
quiet-machine rule (CLAUDE.md "Parallel work") and without a check for other
work, so treat them as background, not results. The valid numbers, mod
against vanilla on a quiet beebox, are in section 7. The options are in
section 8, which replaces the recommendations of sections 5 and 7 (John
rejected the 60 s one).

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

## 6. Update 2026-10-28: the 70-120 s cold chunk is the quay trace

Measured by COLDCHUNK (`.claude/reports/p6-coldchunk.md`), and seen again in the HEAVY4 and HEAVY5 perf runs.

- In a cold metropolis, the first chunk asked waits 70-120 s. The other 80 chunks take a median of about 72 ms.
- **Where the time goes:** a single worker runs the settlement-wide quay trace (`Districts.plan` → quays → `waterDepth` → exact terrain fill). That is about 600k exact reads, single-threaded, 56-70 s (the `region.cold.quays.ms` row). It is separate from the ~30 s region layer + roads row above. There is no lock contention.
- **Origin:** METROFILL R1 r3, which made the first waterfront tile pay for the whole city's quays.
- **Options:**
  - (a) trace lazily, from the tile that asks;
  - (b) warm the quays on the worker pool before the first start, as the teleport hold does;
  - (c) parallelise the trace per water body;
  - (d) use a coarser water test.
- **What each option changes:**
  - (b) is cheapest to build, but it moves the wait to settlement start, so it doesn't remove it.
  - (a) and (c) cut the cost itself.
  - (d) trades some quay accuracy for speed.

## 7. Measured 2026-10-31 on a quiet beebox: mod against vanilla

Rewritten 2026-10-31 (assistant, QUIETPERF). The HEAVY6 table that stood here was not a measurement: see the last line of this section.

**How it was measured.** `scripts/perf.sh`, twice (plus a third planner-only run for the region rows), on beebox (12 CPUs, AMD GPU; the world half runs on a dedicated server with no renderer). Each run was sent when `ssh beebox job status` showed nothing else running or queued. It was checked every minute during the run and again after: nothing else ran on beebox at any point. Run 2 also ran `scripts/quiet-check.sh` inside the job before and after each step (no other job, other processes under 0.05 cores). Run 1 did the mod world first, run 2 the vanilla base first. The product code is the same as run-branch 64d76a52 in both (the branch commits differ only in scripts and docs). The load is the 1-min loadavg sampled just before each step. It includes the run's own build and threads, so it is recorded, not judged. Evidence (forensic source, not tracked): `.claude/reports/quietperf-bee-r1-evidence/d43e5638cc-20261031T070329Z/` and `quietperf-bee-r2-evidence/3ee0a7b1ab-20261031T071801Z/`.

**The vanilla baseline** is the same server, seed (987654321) and path with the region layer's frequency at 0: the same terrain with no settlements, roads or quays (`base.*` rows). Rows marked "no vanilla equivalent" are work vanilla never does, so their only reference is the budget.

| What is timed (start → end) | Mod, run 1 / run 2 | Vanilla, run 1 / run 2 | Mod ÷ vanilla | Budget / ceiling |
|---|---|---|---|---|
| Slowest chunk of a 9 x 9 block at the metropolis centre, cold: `getChunk(FULL)` call → chunk FULL, server thread, no player there | 53.3 s / 53.9 s | 0.33 s / 0.32 s | about 165x | none |
| The whole 9 x 9 block, cold (81 chunks one after another) | 59.7 s / 60.3 s | 2.65 s / 2.58 s | about 23x | none |
| Median chunk of that block, cold | 39 ms / 39 ms | 27 ms / 26 ms | 1.5x | none |
| Mean chunk of the next 9 x 9 block, after the warmer ran | 56 ms / 56 ms | 26 ms / 25 ms | 2.2x | 3x / 4.5x |
| Worst server tick, elytra-speed flight to the gate city | 2.07 s / 0.78 s | 0.19 s / 0.22 s | 3.5x to 11x | 3 s / 6 s |
| p95 server tick, same flight | 10.4 ms / 10.2 ms | 10.3 ms / 9.3 ms | about 1x | 50 / 100 ms |
| Heap after GC, after the flight and 162 city chunks | +156 MB / +150 MB over vanilla | | | 500 / 1000 MB |
| Region layer + roads, cold (`region.cold.warm`): one `WorldPlanner.warm` call in a JUnit test, no server | 14.8 s / 11.6 s (run 3: 11.7 s) | no vanilla equivalent | | 6 s / 10 s |
| Quay trace of the metropolis (`region.cold.quays`), same test | 21.5 s / 19.9 s (run 3: 20.8 s) | no vanilla equivalent | | none |
| Warm + quays on a worker, as the warmer runs them (`region.warmed.worker`) | 30.6 s / 31.0 s (run 3: 31.9 s) | no vanilla equivalent | | none |
| `/locate structure #fractalbuildings:settlement`, fresh area, server thread | 5.27 s / 5.21 s | not measured in the base world | | 3 s / 5 s |
| `/locate` settlement from spawn | 1.67 s / 1.70 s | not measured | | 2 s / 4 s |
| `/locate structure #minecraft:village`, fresh area (goes through our veto) | 4.86 s / 4.87 s | not measured in the base world | | 10 s / 20 s |
| First tile of the metropolis, cold, planner | 1.21 s / 1.24 s | no vanilla equivalent | | 1 s / 4 s |
| Load just before the planner step / the mod world / the base world | 4.3 / 2.3 / 3.3 (run 1); 1.9 / 3.5 / 2.0 (run 2) | | | |

- **Cold arrival.** No row times "teleport → first frame" or "teleport → chunks around the player ready". The closest is the cold 9 x 9 block, and against vanilla it is the clear outlier: one chunk waits about 53 s where vanilla takes a third of a second, and the block takes a minute where vanilla takes under 3 s. The other 80 chunks cost about the same as vanilla (median 1.5x). The wait is the settlement-wide work a structure start waits for: the junction field and the quay trace (section 6).
- **Spread between the runs**: the region row was 14.8 s in run 1 and 11.6 s / 11.7 s in runs 2 and 3 (run 3 was planner-only, `quietperf-bee-r3-evidence/`). Run 1's planner step started at load 4.3, right after its own build. So about 11.7 s is typical and run 1 is high. The worst flight tick differs by 2.7x between runs 1 and 2. Everything else agrees within 5 %. Before PARREADS, on the same quiet beebox, the region row was 36.4 s, the quays 69.5 s and the warmer path 103 s (5209d507, [PERFORMANCE](../PERFORMANCE.md) PARREADS).
- **These are beebox numbers only.** They aren't comparable with local ones, and the old budgets were set from local runs.
- **Not valid (busy host).** All of HEAVY6, which was this section's earlier table: locally the 15-min load was 21 at the start and the world half ran at load 6, and there was no check on beebox for other jobs. Also not valid: the teleport-hold give-ups (a client test at load about 45), DEADPORT's verifier run (load 2 to 5, unchecked), and the "about 37 s before PARREADS". None of them is used above.

~~**Recommendation for 0.1.0 (release-plan question 4):** accept a cold-arrival wait of up to 60 s. [...]~~ Rejected by John 2026-10-30. The options are below.
  - @John 2026-10-30: NO.gif. What are these numbers compared to?
    - @assistant 2026-10-31: Mostly to nothing but their budgets, and none of them were valid measurements. What each row of the old table was: (1) "Worst cold chunk" was the wall time of one `getChunk(FULL)` call on a dedicated server, the slowest of a 9 x 9 block at the metropolis. There was no teleport and no client, so it was never "the wait a player teleporting in sees". It has a vanilla baseline (`base.cold.chunk.max`, the same block with settlements off), which the table left out. (2) The region layer, the quays, the first tile and the district plan are planner calls in a JUnit test. Vanilla does no such work, so there is no baseline, only the budget. (3) The `/locate` rows run only in the mod world, so there is no vanilla baseline. (4) The flight tick has a vanilla baseline (`base.flight.tick.max`), also left out. (5) Heap was already mod minus vanilla. Hosts and load: "Local, quiet" wasn't quiet (15-min load 21 at the start, the world half at load 6, other work unchecked). beebox wasn't checked for other jobs. The teleport-hold line came from a client test at load 45. Under the rule now in CLAUDE.md ("Parallel work": no other job or agent work on the host; measure on beebox), none of it counts. The table above replaces it: beebox only, quiet, mod against vanilla, run twice.

## 8. Options for the cold-arrival wait (for John)

The numbers are from section 7 (beebox, quiet). "Wait" means the cold 9 x 9 block at the metropolis: about 60 s with the mod against 2.6 s in vanilla. Its slowest chunk is about 53 s against 0.33 s.

| Option | What the player sees | Cost to build | Risk |
|---|---|---|---|
| 1. Keep the 30 s teleport hold and the current work | A cold far teleport into a metropolis waits 30 s at "Generating terrain", then lands among chunks that keep arriving for about another 25 to 30 s (*estimate*, from the 53 s chunk) | None | The 30 s limit is already below the measured wait, so landing in ungenerated terrain is normal for metropolises |
| 2. Raise the hold to cover the measured wait (about 60 s) | Up to a minute on "Generating terrain", then a ready city. About 23 times vanilla's wait for the same block | XS (one constant and its test) | Makes the slow path official. Nothing gets faster |
| 3. Hold only for what the destination tile needs (section 4 B) | Shorter hold away from water. Waterfront tiles still wait for the quays (about 20 s of planner time) | S | Chunks further out appear later, as in vanilla |
| 4. Take the quay trace out of the chunk pipeline, or make it lazy (sections 4 E and 6 a) | The cold chunk drops by most of the quay share. How much is not measured: 20 s of the 31 s warmer path is quays | M | Must keep `tile-border-agree` (quays cut the same way on both sides of a tile border) |
| 5. Cheaper region work (section 4 D, F) | Shorter region row (11.6 to 14.8 s now, ceiling 10 s) | S to M each | F may bring back junction steps |
| 6. Save warmed data with the world (section 4 H) | Nothing on the first visit. No second payment after a restart | M | File format, invalidation on `genVersion` |
| 7. Raise the region row's ceiling to what beebox measures, or set ceilings per host | Nothing | XS | Hides growth unless the count rows stay (they do) |

What you'd decide: (a) the acceptable cold-arrival wait, either in seconds or as a multiple of vanilla's wait for the same chunks; (b) whether 0.1.0 waits for 3 and/or 4, or ships with 1 or 2 and does them after; (c) the region row's ceiling (7, or after 5). Missing before (a) can be set from a real number: a perf row that teleports the mock player into the metropolis and times "teleport → every chunk in view distance is FULL" in both worlds. It isn't measured today; it's a small test-harness item.
