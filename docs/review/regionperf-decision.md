# Region layer performance: decision brief

For John, 2026-10-27 (assistant). The measurements, bisect and fixes so far
are in [PERFORMANCE.md](../PERFORMANCE.md), "Region layer row" (regionperf).
This page doesn't repeat them. It covers who pays the cost and when, what
the cost buys, and the options. Numbers are from the perf worker (12 CPUs, load
about 2) at 83c7a808 unless marked. *Estimate* means it was worked out from
those numbers and not measured.

**2026-10-31:** the timings in sections 1 to 6 were taken before the
quiet-machine rule (CLAUDE.md "Parallel work") and without a check for other
work, so treat them as background, not results. The valid numbers, mod
against vanilla on a quiet perf worker, are in section 7. The options are in
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
| `/tp` into an unvisited metropolis (TPHOLD) | The player sees "Generating terrain…" while `warm` and `warmQuays` run. **The hold gives up after 30 s** and teleports anyway, so on perf-worker-class hardware the player arrives before the work is done and waits on ungenerated chunks for up to about another 75 s on a waterfront city. | From the code (`TeleportHold.timeoutMillis` = 30 s); not measured in game. The accepted tradeoff was "about 4 s" ([PERFORMANCE](../PERFORMANCE.md), "Teleport into an unvisited city"). |
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
| D. (**Done 2026-10-31, about 12 % of the row measured**; checked safe, the region map shows more change than the reason text; see under this table) Skip terrain reads for cells whose dice roll can never settle (roll ≥ 0.65, about 35 % of cells) | Same settlements; only the region map's "reason" text changes | *Guess*: about 15 % of the row | XS to S. Checked 2026-10-31: nothing outside the region map reads those cells (see below). |
| E. Lazy quay tracing (PERFORMANCE "Quays" Improve) | No, if done right | *Guess*: 3 to 10x on the first waterfront tile | M. It has to keep `tile-border-agree`. |
| F. (**Corrected 2026-10-31: does not hold, see the explanation under this table**) Read the junction field from sample columns or per tile neighbourhood instead of whole chunks | No (same reads) or a little (per neighbourhood) | *Guess*: 2 to 5x on the junction part | M. Per neighbourhood risks bringing back junction steps. |
| G. W2 coarse screen for region decisions | Yes: decisions no longer match the viewer or the reviewed cities | *Estimate*: 10x or more on the decision share (ADR 0004: about 4 ms per cell coarse, 40 to 100 ms exact) | Needs an ADR 0003 amendment, and decisions drift under modded terrain. It doesn't help junctions or quays. |
| H. Save the region decisions, junction fields and quays with the world | No | The repeat cost after a restart goes to about 0 | M. A file format and invalidation on `genVersion`. |
| I. Raise the budget to what the work costs now | No | None | XS. Leaves elytra flights and spawn unmeasured. |

**Options D and F explained (assistant, 2026-10-31, from the code; John asked).**

*Two ways we read terrain.* Every height or water value comes from vanilla's terrain noise, and nothing is loaded into the world. (1) **Single columns** (`SparseColumns`, `LiveTerrainProbe.column`, which calls vanilla `getBaseColumn`): about 0.35 to 0.55 ms each. (2) **Whole chunks** (`ColumnMemo` with `ChunkFill.WINDOWED`): all 256 columns of a chunk in about 1.9 ms. So one column costs about a quarter of a chunk, not a whole one (SPARSEREADS in [NOTES](../NOTES.md)). Single columns win when the reads are far apart. Chunks win when more than about 4 columns of the same chunk are read. District reads, the junction field and the quays use chunks. The region lattice, the coarse 32-block lattice and the road stations use single columns (`LiveProbes`).
- *Roads between settlements* (ADR 0004) use only single columns. They never fill a chunk. Route search reads the coarse lattice, one column every 32 blocks (`RoadNet`). Each route then reads one column per station, every 8 blocks along the curve (`Curve.STATION`), about 450 per route. A straight route puts 2 stations in a chunk, which costs about 0.9 ms as single columns against 1.9 ms for the chunk, so single columns are the right choice there. The perf rows count these reads (`region.cold.warm.roadColumns` 14,587; `sparseColumns` 68,343, which mixes region and route-lattice reads), and their time is inside the region row. No row times roads alone, and there is no per-link figure.

*D. What a region cell does* (`RegionPlanner.decide`). Each cell of the region grid first draws a dice roll from the seed (`region/roll`, no terrain). Then it tries 8 candidate centres. For each one it samples a disk of up to about 80 points on the 64-block lattice: biome, water, and height, to count the water share, the "peak" share (mountain biome, or ground at y 140 or above) and how good the biomes are. Towns and larger also measure the distance to water. The cell settles only if water and peaks are under their caps, the biome score (`suitability`, 0 to 1) is at least 0.35, and `roll < 0.65 x suitability`. The best biome is worth 1.0 (`region/biome-classes.json`), so a cell that rolls 0.65 or more can never settle, whatever its terrain. That is about 35 % of cells. D would skip the terrain reads for those cells.
- *What the join rule is.* A cell's own decision is only half the story, because a big settlement can be larger than one cell. The join rule (ADR 0003 R2, `FieldRegionView`) decides which settlement each cell finally belongs to. A cell that settled as a large tier (city or metropolis, drawn radius over 0.4 of a cell) is a *claimant*: it claims every neighbouring cell whose centre point lies inside its radius. When two claims overlap, a seeded priority picks the winner, and the loser decides alone. A claimed cell becomes a *member*: it holds part of its owner's city and is not a settlement of its own. A cell nobody claims keeps its own decision. **Why this was a risk:** the join rule looks at many cells around the one being asked about, including cells that will never settle. If any step used such a cell's water share, peak share, suitability or chosen centre (for example, to see whether a city could spread onto it), skipping that cell's terrain reads would change which settlements exist.
- *Every place that reads a cell's decision, and what guards it* (grep of every `CellDecision` field accessor and every `decide` / `decision` caller in `planner-core`, `mod` and `cli` main code, run-branch 441133fb; line numbers from that commit). A never-settling cell's decision would still exist under D, with `settled = false` and the same tier. Only its terrain numbers would be missing. The tier is drawn from the seed alone (`Tiers.tier`), not from terrain.
  1. `RegionPlanner.decide` (`region/RegionPlanner.java:177-229`). This is where the numbers are made. The roll is drawn at :177 before any terrain read. Settling needs `roll < chance` (:227), and `chance = baseChance * bestBase` (:219) is at most 0.65 x 1.0. The coastal factor at :220 only lowers it. D would return early after :177 when `roll >= baseChance`.
  2. Join rule, claims (`region/FieldRegionView.java:145-160`, `computeClaim`). It calls `decision(k)`, then `if (!d.settled()) return NONE;` (:149) before it reads the centre (:152-154). A never-settling cell is never a claimant, and nothing about it is read past that line.
  3. Join rule, priority between claims (`FieldRegionView.java:207-220`, `computeSurvives`). It compares only claims that passed :149, through `claim(k) != NONE` (:186).
  4. Join rule, the final answer for a cell (`FieldRegionView.java:230-241`, `compute`). An unclaimed cell gets `decision(k).sizedAlone()` (:237). `sizedAlone` returns the decision unchanged for an unsettled cell: `if (!settled || ...) return this;` (`region/CellDecision.java:49`). A member gets its own `decision(k)` attached (:241), but the settlement it belongs to is built from the owner's claim (:239). Nothing here reads the member's numbers. It only carries them to the callers below.
  5. Settlements for districts and the game (`settlement/FieldSiteView.java:41-55`). When a cell has no owner: `if (!d.settled()) return Optional.empty();` (:45), before reading the centre and radius (:47). When it does: the tier, centre and radius all come from the owner (:53-55). The owner always passed :149.
  6. Road graph, which settlements get linked (`road/RoadNet.java:190-193`). `if (!d.settled() || r.claimedBy() != null) return Optional.empty();` (:192), before reading the centre, radius, tier and port (:193). The other `view.cell` calls (:251-252, :289-292) read only who owns a cell and its cell list, never a decision's numbers.
  7. Region map (`region/RegionMap.java:34-43` collects every cell's decision; :111-121 writes all of its numbers to JSON). This is the **one reader with no guard**. It feeds the CLI `region` command (`cli/.../RegionCommand.java:58` counts settled cells only) and the viewer's `region.html`. The mod never calls it.
  - Nothing else reads these values. The other `radius()`, `centre()` and `port()` hits in the grep belong to `SettlementSite`, `Approach` and `Settlement` objects, which are only built from settled cells (steps 5 and 6).
- **Done 2026-10-31 (SKIPROLL, [log](../log/2026-10-31-p6-skiproll.md)).** The map shows reason "roll" with empty numbers for the skipped cells. Measured on the perf worker, `perf.sh --planner-only`: one pair was quiet before and after the row (CLAUDE.md quiet rule): region row (`region.cold.warm.ms`) 11.77 s before, 10.34 s after (about 12 %); the second pair (11.60 / 10.35 s) is NOT a measurement (another job started during the run; perf.sh marked it so). One clean pair only: re-measure in the next quiet heavy batch. The region row is still over its 10 s CI ceiling (it was on base too); `sparseColumns` 68,343 to 51,054 (-25 %). `SkipRollTest` checks settled flags, claims, sites and the road graph are identical on pt1573, w12345 and w987.
- **Risk closed: in the game, in districts and on the road graph, a cell that rolled 0.65 or more is only ever asked "did you settle?", and the answer is no either way. Its water, peak, suitability and centre reach only the region map, so D changes no settlement, member cell or road.**
- *What does change:* the region map (CLI `region`/`city`/`roads`, viewer `region.html`). It colours each cell by `reason`: water (light blue), peak (white), unsuitable (brown), roll (dark grey). The map would show "roll" for every skipped cell, including ones that show water or peak today. The cell's water, peak, suitability and best-centre numbers in the map JSON would be empty too. Two tests read the reason. The game never reads it. Doing the skip only in the game and keeping full reads in the CLI would leave the map unchanged as well.
- *Speed-up:* the reads are single columns, so every skipped column saves its own cost (unlike F). But the 68,343 column count also includes the route search's 32-block lattice and the skeleton's coarse rasters, and no counter separates them. *Guess:* 10 to 20 % of the region row, depending on that split. The 15 % in the table is in that range.

*F. What the junction field is* (`JunctionField`, JUNCTION backlog 6.3, rounds 1 to 3). Before it, each street was graded over the ground on its own, so two streets could meet 2 to 20 blocks apart (229 of 670 gate tiles had a step over 3). The field finds every place where whole streets overlap (crossings, a street ending on another), gives each junction one level (the median ground there, adjusted so that every street between two junctions can make the rise at the grader's 1 in 3), and pins all of them for the whole settlement at once. Every tile then reads the same pins (`GroundWalkTest` steps went from 370 / 229 to 107 / 18). To do that it reads, from the exact chunk probe: the water at **every centre column of every street** (for bridge decks), the ground at every junction column, each street's grading samples, and the end points of the street edges it tests. In a metropolis streets run every few tens of blocks, so that touches nearly every chunk: 153k reads land in 4,924 chunks, 14 to 17 or more columns per chunk.
- *"Sample columns"* means reading only those columns one at a time instead of filling their chunks. Under the cost above that is slower, not faster: 14 or more columns at about 0.45 ms is about 6 ms, against 1.9 ms for the chunk. SPARSEREADS measured exactly this: thinning these reads skips no chunks. A cheaper per-column path doesn't fix it. Vanilla `getBaseHeight` (about 0.2 ms, [NOTES](../NOTES.md) 1.4) is still a full-column scan and needs two calls for surface and floor. A coarser noise level isn't exact, which would be option G for streets and would bring back steps. Fewer reads would also change the output: skipping the water test at some columns moves bridge decks. **So the "same reads" form of F has no speed-up. The 2 to 5x guess does not survive.**
- *"Per tile neighbourhood"* means building the field from the tiles around the one asked for, instead of the whole city. That moves work off the first tile rather than removing it: later tiles fill the rest, and many of the same chunks are read again by tile grading anyway. It changes the output because a junction's level depends on other junctions along the streets (the grade limit is passed along the street graph: `envelope`, two shortest-path sweeps). Two tiles that see different sets of junctions would pin the same street at different heights, which means a step at the tile border. That is the "border problem" in the [JUNCTION log](../log/2026-10-23-p6-junction.md). A neighbourhood is exact only if it is wider than every ramp a pin can cause (3 blocks per block of height difference). *Guess*, not proven: that is a few hundred blocks on hilly ground, close to the whole city.
- *Net:* F as written is not worth scheduling. The junction cost falls with C (parallel, already done in part by PARREADS) or H (saved with the world).

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

## 7. Measured 2026-10-31 on a quiet perf worker: mod against vanilla

Rewritten 2026-10-31 (assistant, QUIETPERF). The HEAVY6 table that stood here was not a measurement: see the last line of this section.

**How it was measured.** `scripts/perf.sh`, twice (plus a third planner-only run for the region rows), on the perf worker (12 CPUs, AMD GPU; the world half runs on a dedicated server with no renderer). Each run was sent when `ssh <perf worker> job status` showed nothing else running or queued. It was checked every minute during the run and again after: nothing else ran on the perf worker at any point. Run 2 also ran `scripts/quiet-check.sh` inside the job before and after each step (no other job, other processes under 0.05 cores). Run 1 did the mod world first, run 2 the vanilla base first. The product code is the same as run-branch 64d76a52 in both (the branch commits differ only in scripts and docs). The load is the 1-min loadavg sampled just before each step. It includes the run's own build and threads, so it is recorded, not judged. Evidence (forensic source, not tracked): `.claude/reports/quietperf-bee-r1-evidence/d43e5638cc-20261031T070329Z/` and `quietperf-bee-r2-evidence/3ee0a7b1ab-20261031T071801Z/`.

**The vanilla baseline (WRONG, see the correction below the table; it was the mod with frequency 0):** the same server, seed (987654321) and path with the region layer's frequency at 0: the same terrain with no settlements, roads or quays (`base.*` rows). Rows marked "no vanilla equivalent" are work vanilla never does, so their only reference is the budget.

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
| `/locate structure #fractalbuildings:settlement`, fresh area, server thread | 5.27 s / 5.21 s (COLDARRIVAL runs 1 / 2: 5.34 s / 5.33 s) | vanilla village locate, same place: 5.26 s / 5.26 s | | 3 s / 5 s |
| `/locate` settlement from spawn | 1.67 s / 1.70 s | not measured | | 2 s / 4 s |
| `/locate structure #minecraft:village`, fresh area (goes through our veto) | 4.86 s / 4.87 s | not measured in the base world | | 10 s / 20 s |
| **Cold arrival (COLDARRIVAL, 2 runs, the quiet perf worker):** a player at spawn teleports (`/tp` path, so the 30 s TeleportHold can engage) into the metropolis centre of a fresh world; clock stops at "teleport done and every chunk of the 9 x 9 view (radius 4 = server view-distance 4) is FULL". Run 1 mod-first, run 2 base-first | 69.5 s / 71.5 s | 4.7 s / 5.1 s | about 14.6x / 13.9x | none (report-only) |
| Longest server tick during that wait | 9.2 s / 11.3 s | 0.38 s / 0.44 s | about 24x / 25x | none |
| TeleportHold engaged? for how long | yes, 30.0 s / 30.0 s, both **timed out** (destination 3 x 3 not FULL at release) | yes, 3.1 s / 3.3 s, released with chunks FULL | | 30 s limit |
| `/locate structure #minecraft:village`, fresh area, **base world** (the vanilla baseline of the two `/locate` rows above; 5.26 s / 5.26 s) | | 5.26 s / 5.26 s | our settlement locate 1.02x, our veto-ed village locate 0.92x | none |
| First tile of the metropolis, cold, planner | 1.21 s / 1.24 s | no vanilla equivalent | | 1 s / 4 s |
| Load just before the planner step / the mod world / the base world | 4.3 / 2.3 / 3.3 (run 1); 1.9 / 3.5 / 2.0 (run 2) | | | |

**Correction 2026-10-31 (PERFBASE): the "vanilla" column above was not vanilla.** `scripts/perf.sh` ran its base world as the mod with the region layer's frequency at 0. Before SKIPROLL that world still did region reads for the village veto, so its village `/locate` cost 5.26 s; after SKIPROLL it skips them (0.25 s). The "mod ÷ vanilla" ratios in this section, in COLDARRIVAL and in the HEAVY10 report therefore compared against a moving, non-vanilla baseline. Since PERFBASE the base half is `tools/vanilla-server`: Minecraft + Fabric Loader + Fabric API and the measuring harness only (no fractalbuildings jar, mixin, structure or log line; `perf.sh` fails the run if the base log mentions the mod). Same seed, coordinates and rows. Corrected numbers, **two `scripts/perf.sh` runs on the perf worker (12 CPUs, dedicated server, no renderer), quiet both times** (`quiet-check`: no other jobs, other-cpu 0.03 to 0.05; loadavg 1.1 to 4.0, all from the run's own work), commit `eb35c7664c`, evidence `p6-perfbase-evidence/eb35c7664c-20261031T183830Z` and `...T185453Z`:

| What is timed | Mod, run 1 / 2 | Vanilla, run 1 / 2 | Mod ÷ vanilla |
|---|---|---|---|
| Cold 9 x 9 block at the metropolis, whole block | 49.7 s / 49.6 s | 2.35 s / 2.35 s | about 21x |
| Slowest chunk of it | 38.9 s / 38.7 s | 0.20 s / 0.18 s | about 190x to 215x |
| Median chunk of it | 39 ms / 40 ms | 24 ms / 25 ms | 1.6x |
| Mean chunk of the next block (warmed) | 63.6 ms / 62.1 ms | 23.7 ms / 23.6 ms | 2.7x / 2.6x |
| Worst server tick, flight to the gate city | 914 ms / 901 ms | 135 ms / 170 ms | 6.8x / 5.3x |
| p95 server tick, same flight | 10.6 ms / 11.1 ms | 9.7 ms / 9.6 ms | about 1.1x |
| Heap after GC | 482 MB / 480 MB | 290 MB / 290 MB | +192 MB / +190 MB |
| Region-file growth, 162 chunks | 2.26 MB / 2.26 MB | 1.70 MB / 1.72 MB | +32 % / +31 % |
| Village `/locate`, fresh area (through our veto) | 3.92 s / 3.93 s | 0.23 s / 0.30 s | about 17x / 13x |
| Settlement `/locate`, fresh area (no vanilla equivalent; vs vanilla village locate) | 4.91 s / 4.89 s | 0.23 s / 0.30 s | about 21x / 16x |
| Cold arrival (teleport to the metropolis centre, 9 x 9 view FULL) | 69.9 s / 68.9 s | 4.5 s / 3.6 s | about 15.6x / 19.0x |
| Longest server tick during that arrival | 9.5 s / 8.5 s | 96 ms / 78 ms | about 98x / 109x |

What changed against the old table: vanilla's `/locate` is 0.25 s, not 5.26 s, so the "our locate costs the same as vanilla (1.02x)" reading was wrong: it is 13x to 21x. Vanilla's cold block is about 2.4 s (was 2.6 s), its arrival 3.6 to 4.5 s (was 4.7 to 5.1 s). The mod-side numbers moved with later work (QUAYLAZY, SKIPROLL), not with this change. Vanilla arrival varies 0.9 s between its two runs; the vanilla `/locate` 0.06 s.

- **Cold arrival.** No row times "teleport → first frame" or "teleport → chunks around the player ready". The closest is the cold 9 x 9 block, and against vanilla it is the clear outlier: one chunk waits about 53 s where vanilla takes a third of a second, and the block takes a minute where vanilla takes under 3 s. The other 80 chunks cost about the same as vanilla (median 1.5x). The wait is the settlement-wide work a structure start waits for: the junction field and the quay trace (section 6).
- **Spread between the runs**: the region row was 14.8 s in run 1 and 11.6 s / 11.7 s in runs 2 and 3 (run 3 was planner-only, `quietperf-bee-r3-evidence/`). Run 1's planner step started at load 4.3, right after its own build. So about 11.7 s is typical and run 1 is high. The worst flight tick differs by 2.7x between runs 1 and 2. Everything else agrees within 5 %. Before PARREADS, on the same quiet perf worker, the region row was 36.4 s, the quays 69.5 s and the warmer path 103 s (5209d507, [PERFORMANCE](../PERFORMANCE.md) PARREADS).
- **These are the perf worker numbers only.** They aren't comparable with local ones, and the old budgets were set from local runs.
- **Not valid (busy host).** All of HEAVY6, which was this section's earlier table: locally the 15-min load was 21 at the start and the world half ran at load 6, and there was no check on the perf worker for other jobs. Also not valid: the teleport-hold give-ups (a client test at load about 45), DEADPORT's verifier run (load 2 to 5, unchecked), and the "about 37 s before PARREADS". None of them is used above.

~~**Recommendation for 0.1.0 (release-plan question 4):** accept a cold-arrival wait of up to 60 s. [...]~~ Rejected by John 2026-10-30. The options are below.
  - @John 2026-10-30: NO.gif. What are these numbers compared to?
    - @assistant 2026-10-31: Mostly to nothing but their budgets, and none of them were valid measurements. What each row of the old table was: (1) "Worst cold chunk" was the wall time of one `getChunk(FULL)` call on a dedicated server, the slowest of a 9 x 9 block at the metropolis. There was no teleport and no client, so it was never "the wait a player teleporting in sees". It has a vanilla baseline (`base.cold.chunk.max`, the same block with settlements off), which the table left out. (2) The region layer, the quays, the first tile and the district plan are planner calls in a JUnit test. Vanilla does no such work, so there is no baseline, only the budget. (3) The `/locate` rows run only in the mod world, so there is no vanilla baseline. (4) The flight tick has a vanilla baseline (`base.flight.tick.max`), also left out. (5) Heap was already mod minus vanilla. Hosts and load: "Local, quiet" wasn't quiet (15-min load 21 at the start, the world half at load 6, other work unchecked). The perf worker wasn't checked for other jobs. The teleport-hold line came from a client test at load 45. Under the rule now in CLAUDE.md ("Parallel work": no other job or agent work on the host; measure on the perf worker), none of it counts. The table above replaces it: the perf worker only, quiet, mod against vanilla, run twice.

### After QUAYLAZY (2026-11-01, the quiet perf worker)

QUAYLAZY (lazy quay trace: a tile traces only the quays that reach it, and the region warmer plans the destination tile instead of tracing the whole waterfront, see [the plan](../plans/quaylazy.md)) is merged. Measured on the perf worker (12 CPUs, dedicated server, no renderer), each run sent when `ssh <perf worker> job status` showed nothing running or queued, `scripts/quiet-check.sh` before and after (no other job, other processes 0.02 to 0.04 cores), every run ending `perf: quiet host`. The load is the 1-min loadavg sampled just before the planner step / the mod world: 2.3 to 3.8 / 1.6 to 2.3 (it is the run's own build and threads). One run (`p6-quaylazy-6-bee-r1`) was **not valid**: another agent's the perf worker job started during it, so it is not used. Evidence (forensic source, not tracked): `.claude/reports/p6-quaylazy-6-bee-{before,r2,r3,r4,r5,whole}-evidence/`.

Four runs of the final code (r2, r3, r4 at commits 0ff88517 / 50bb3417, r5 at a5b91c8c; the later commits only change the measuring code), one run of the same code with the warmer's old whole-city trace switched back on (`FB_PERF_QUAYTRACE=whole`, world half only: the same server, same commit, so this is the clean before / after), and one run of the older commit 98259161 (before QUAYLAZY 3b to 5, **and before SKIPROLL (region cells that never settle skip terrain reads) and PERFBASE (a real vanilla server as the base world)**: its numbers include SKIPROLL's savings and its "vanilla" column is the old frequency-0 mod, so only its planner rows and mod rows are quoted).

| What is timed | Whole trace (same code, `quayTrace=whole`) | Lazy, runs r2 / r3 / r4 / r5 | Vanilla |
|---|---|---|---|
| Quay trace of the whole metropolis (`region.cold.quays`, JUnit, kept as the oracle) | 20.4 / 19.8 / 19.7 / 20.3 s | same code, same numbers | none |
| **Lazy quays of one tile** (`region.cold.quays.tile`, new; the owned waterfront tile nearest the centre, tile 15,-37) | none | **2.25 / 2.17 / 2.15 / 2.21 s**, 2,606 / 2,606 / 2,611 / 2,606 chunk fills | none |
| Region warm + the point's tile plan, on a worker, as the warmer now runs it (`region.warmed.worker`; before, the same row ran the warm with the whole trace) | 29.0 to 29.7 s (old form) | 10.07 / 10.04 s (r4, r5) | none |
| Slowest chunk of the cold 9 x 9 block (`cold.chunk.max`) | 34.7 s | 34.9 / 34.9 / 34.6 / 34.7 s | 0.18 to 0.32 s |
| Mean chunk of that block | 571 ms | 572 / 570 / 574 / 573 ms | 29 to 32 ms |
| Cold arrival, 9 x 9 view FULL (`arrival.cold.metro`) | 72.0 s | 52.7 / 52.3 / 52.0 / 54.0 s | 4.4 s |
| TeleportHold (cap 90 s): how long it held | 66.5 s | 46.9 / 46.5 / 46.3 / 48.1 s, released with the chunks FULL, never timed out | 3.1 s |
| Longest server tick during that arrival | 0.66 s | 0.65 / 0.47 / 0.61 / 0.60 s | 0.05 to 0.50 s |
| Worst server tick, flight to the gate city | 0.90 s | **5.01 / 4.98 / 0.30 / 0.80 s** (bimodal, see below) | 0.13 to 0.16 s |
| `/locate` settlement, fresh area | 4.88 s | 4.96 / 4.92 / 4.87 s | 0.22 to 0.28 s |
| Region warm (`region.cold.warm`, JUnit) | | 10.4 / 10.3 / 10.5 s (ceiling 10 s: still FAIL, unchanged by this work) | none |

Before the series (98259161, a quiet run of the same protocol, includes the pre-SKIPROLL region work): whole quays 20.8 s, warmed worker 32.1 s, slowest chunk 54.1 s, cold arrival 71.0 s with the **30 s hold timing out**, longest tick during arrival 10.6 s, region warm 11.6 s. The PERFBASE-era pair (after SKIPROLL, before QUAYLAZY 3b: slowest chunk 38.9 s, arrival 69 to 70 s, longest tick 8.5 to 9.5 s) is the closer "before" for the chunk rows.

What it says:

- **The lazy trace works for what it targets.** One waterfront tile costs 2.2 s and 2,606 chunks against about 20 s for the whole trace; the warmer's job fell from 29 to 10 s; the cold arrival fell about 18 s (72 to 53 s, same code) and the hold from 66 to 47 s.
- **The slowest cold chunk did not move** (34.7 s with the whole trace, 34.7 s lazy). It is not the quays. It is the settlement-wide work a structure start waits for on the chunk thread (the junction field and region layer, section 6), which neither trace choice changes. The plan's guess (53 s down to 15 to 30 s) assumed quays were most of it: from the PERFBASE-era 38.9 s they are about 4 s.
- **The cold arrival is still 12x vanilla and the hold is 47 s**, so the TeleportHold cap cannot go to 15 s yet (end of this section).
- **Worst flight tick** is bimodal: two runs at 5.0 s (budget 3 s, ceiling 6 s), two at 0.3 and 0.8 s; the whole-trace run 0.9 s; the older commit 2.2 s. One tick of a chunk that waits for a cold tile on the main thread, depending on whether the warmer got there first. The 5 s mode is the plan's risk R6 (a flight pays per tile instead of once ahead). Not proven a regression (the old code also had 2.2 s), not fixed here.
- **Ablations** of one tile, same planner, fresh each, runs r2 / r3 / r4 / r5 agree within 5 %: with the reads pool 2.21 to 2.27 s and 2,603 to 2,606 chunk fills; serial (no pool) 3.56 to 3.69 s and 1,492 fills (the pool is 1.6x faster and reads 1.7x more chunks); walk-ahead 0: 1.86 to 1.93 s and 1,788 fills; walk-ahead 32 (shipped): 2.2 s and 2,606; walk-ahead 64: 2.57 to 2.69 s and 3,347; without the one-call scan-box prefetch: 2.31 to 2.41 s and 2,570 to 2,580 fills (the box over-reads little). **Walk-ahead 0 was fastest here** (15 % under shipped, 31 % fewer fills): the walk-ahead hint seems not to pay on this tile; one tile on one seed, so it is a finding, not a change. Building a fork's bank costs 3 to 18 ms in total for 38 to 44 forks of one tile (negligible).
- The hunt for a wrongly aimed warm: only `region.cold.quays.tile.chunkFills` (budget 2,700, ceiling 5,212) can catch a warm of the wrong tile, and only in the planner row; the world rows (arrival, hold) would show a wrong tile as the old numbers.

### What this means for the hold cap (TeleportHold, TPHOLD15)

A cold metropolis arrival now holds 46 to 48 s on a quiet perf worker (the hold ends when the warmer finishes and the chunks of the 9 x 9 view are FULL; whole trace: 66 s). A cap of 15 s would release the player about 30 s before the destination exists and freeze the server tick for the rest (the slowest chunk is 34.7 s); 90 s holds with margin, 60 s would too. See the proposal in the p6-quaylazy-6 report: keep 90 s (or 60 s) until the settlement-wide junction field is lazy; 15 s needs the slowest cold chunk under about 12 s.

### Reads pool size (B1, 2026-11-01, the quiet perf worker)

B1 is step 6 of the [JFLAZY plan](../plans/jflazy.md) (which became CASCADE: making the exit-road trim and the link rule cheaper). It asks whether a bigger reads pool helps. The reads pool is PARREADS's helper threads (parallel terrain reads), which fill vanilla terrain chunks while a cold settlement is planned. Today it gets a third of the CPUs, capped at 6, which is 4 threads on the perf worker's 12 CPUs. **How it was measured:** `scripts/perf.sh --world-only` six times on the perf worker (dedicated server, no renderer), at commit c468dd41 (before CASCADE steps 1 and 5). The pool size was set with `-Dfractalbuildings.readThreads=N` (N = 4, 8, 11, two runs each, interleaved 4, 8, 11, 4, 8, 11), and `jcmd` read the property back on the running server each time. Each run was sent only when `ssh <perf worker> job status` showed nothing running or queued. `scripts/quiet-check.sh --worker perf` was QUIET before and after each run, and inside each run at the start and at world end (no other job, other processes 0.02 to 0.05 cores). Every run ended `perf: quiet host`. The loadavg sampled just before the mod world / the arrival pair was 1.4 / 3.1 and 5.9 / 3.0 (N = 4), 2.9 / 4.5 and 5.3 / 2.4 (N = 8), 5.6 / 5.6 and 3.5 / 3.0 (N = 11); it is the series' own back-to-back work, so it is recorded, not judged. Evidence (forensic source, not tracked): `.claude/reports/p6-cascade-6-n{4,8,11}-r{1,2}-evidence/`, the last directory of each.

| What is timed (runs 1 / 2) | N = 4 (today) | N = 8 | N = 11 | Vanilla (same runs) |
|---|---|---|---|---|
| Slowest chunk of the cold 9 x 9 block (`mod.cold.chunk.max`) | 34.85 / 34.93 s | 30.81 / 30.88 s | 30.52 / 30.65 s | 0.17 to 0.21 s |
| Mean chunk of that block | 576 / 576 ms | 519 / 516 ms | 513 / 517 ms | |
| Cold arrival, 9 x 9 view FULL (`arrival.cold.metro`) | 52.30 / 52.76 s | 47.60 / 47.85 s | 47.67 / 46.45 s | 4.2 to 4.6 s |
| TeleportHold, how long it held (`arrival.hold`) | 46.41 / 47.30 s | 42.26 / 42.75 s | 42.66 / 41.31 s | |
| Longest server tick during that arrival (`arrival.maxTick`) | 0.58 / 0.60 s | 0.66 / 0.64 s | 0.86 / 0.76 s | |
| Worst server tick, flight to the gate city (`mod.flight.tick.max`) | 0.87 / **5.32** s | **4.68 / 4.25** s | 0.28 / 0.39 s | |
| p95 server tick, same flight | 11.5 / 10.2 ms | 10.6 / 10.6 ms | 10.5 / 12.9 ms | |

- **A gain shows in both runs, at N = 8 and at N = 11.** The slowest cold chunk drops by about 4.0 s (12 %), and the arrival and the hold by about 4.5 to 5 s (9 to 10 %). N = 11 is within 0.3 s of N = 8 on the chunk and arrival rows: the pool saturates at about 8 threads here.
- **The worst flight tick is the known two-mode row** (QUAYLAZY above: one chunk waits on the main thread for a tile the warmer has not reached). Its 4 to 5 s mode always came at the same point of the flight (1,978 blocks flown): once at N = 4, in both runs at N = 8, and in neither run at N = 11. Two runs per size can't show whether the pool size causes this. By the rule set for this step (a gain in both runs, and the flight tick no worse), N = 11 qualifies and N = 8 does not clearly.
- **Cost:** the longest arrival tick rises from about 0.6 s to about 0.8 s at N = 11 (no budget on that row). Only a 12-CPU dedicated server was measured, with no client on the same machine.
- These numbers predate CASCADE steps 1 and 5 (the warmer's order changed in step 5). CASCADE step 7 measures again.


### After CASCADE (2026-11-01, the quiet perf worker)

CASCADE ("the cascade a cold chunk waits for": CASCADE steps 2 to 5 made the exit-road trim and the link rule cheaper, ran what is left in parallel on the reads pool, and made the region warmer start that work first, all with byte-identical output; the [plan](../plans/jflazy.md)) is merged, and so is READSDEFAULT (reads pool default cpus - 1, up to 11). **Valid numbers only.** `scripts/perf.sh` (full run) twice on the perf worker (12 CPUs, dedicated server, no renderer) at commit 4de791c5 (= run branch 98f07884 plus the count budgets), each sent when `ssh <perf worker> job status` showed nothing running or queued, `scripts/quiet-check.sh` QUIET before and after, both runs ending `perf: quiet host` (other processes 0.03 to 0.04 cores; 1-min loadavg just before the world half 2.75 / 3.17, at its end 5.98 / 6.77, all the run's own work). The mod world and the vanilla base world ran in the same run each time.

| What is timed | Before CASCADE (c468dd41, 4 reads threads) | READSDEFAULT alone (c468dd41, 11 threads) | **After CASCADE, runs 1 / 2 (11 threads)** | Vanilla, same runs |
|---|---|---|---|---|
| Slowest chunk of the cold 9 x 9 block (`mod.cold.chunk.max`) | 34.9 s | 30.6 s | **24.5 / 24.7 s** | 0.20 / 0.18 s |
| Mean chunk of that block | 576 ms | 515 ms | 414 / 417 ms | |
| Cold arrival, 9 x 9 view FULL (`arrival.cold.metro`) | 52.5 s | 47.1 s | **39.2 / 39.0 s** | 3.5 / 4.5 s |
| TeleportHold: how long it held (`arrival.hold`) | 46.9 s | 42.0 s | **33.2 / 32.8 s** | |
| Longest server tick during that arrival (`arrival.maxTick`) | 0.59 s | 0.8 s | 0.75 / 0.80 s | 56 / 55 ms |
| Worst server tick, flight to the gate city (`mod.flight.tick.max`) | 0.87 / 5.32 s | 0.28 / 0.39 s | **0.91 / 0.92 s** (at 1,978 blocks flown, as before) | 127 ms |
| p95 server tick, same flight | 11.5 / 10.2 ms | 10.5 / 12.9 ms | 10.0 / 10.8 ms | 9.8 ms |
| Region warm + roads, cold, JUnit (`region.cold.warm`) | | | 9.16 / 9.27 s (budget 6 s, ceiling 10 s: miss, not a failure) | none |
| Region warm + the point's tile plan, as the warmer runs it (`region.warmed.worker`) | | | 8.55 / 8.61 s | none |
| `/locate` settlement, fresh area | | | 3.36 / 3.35 s (budget 3 s: miss) | 0.31 s (village) |

The "before" columns are the c468dd41 runs of the B1 table above (run 1 / run 2 averaged where one number is shown). The two new runs agree within 1 % on the chunk, arrival and hold rows.

What it says:

- **CASCADE took about 6 s off the slowest chunk (30.6 to 24.6 s, the 11-thread baseline) and 8 s off the cold arrival (47.1 to 39.1 s); against the original 4-thread numbers that is 10 s (-30 %) and 13 s (-25 %).** The plan guessed 13 to 24 s for the chunk and 25 to 40 s for the arrival: the arrival is inside its range, the chunk is at the top of it. It is still about 120x vanilla on the chunk and 9 to 11x on the arrival.
- **What the slowest chunk still waits for** (one diagnostic run with the wall-clock sampler on, `FB_PERF_SAMPLE=1`, same commit and host; JFR also on, so 25.1 s instead of 24.6; chunk 0 of the block, tile 14,-36): the chunk thread spends about 2.5 s on the tile's streets and skeleton, 2.5 s on the junction field, 1.5 s on quays, about 3 s on buildings, and **about 14 s (of the 25) waiting for a neighbouring tile's plan** (8.5 to 22 s), which is the link rule: other settlements' tile plans and quays (`rawPlan`, 45 thread-seconds of sampled work, all 11 reads threads and 2 workers busy for the whole wait), and the link states (about 10 thread-seconds). **The exit-cut walk no longer shows up at all** (no sample on a `fullCut` or `cutWithin` frame in the whole run); step 2 removed it. The link rule was 24 s of the old 35 s chunk; it is now about 14 s of 25 s, and it is now spread over all the cores instead of one.
- **What is left is the link rule's real work**: the neighbour tiles' plans, which need the far settlements' skeletons and quays. Two shortcuts that no longer apply (DQ1 and DQ2 of the plan: let the trim ignore the far settlement's side of a link, or decide "an approach exists" without street levels) would cut that, but they change output; not asked yet. By the plan's own rule they are asked only when step 7 lands above 15 s, and it does (24.6 s).
- **The two-mode flight tick did not appear in three runs** (0.91, 0.92 and 1.09 s with the sampler on; always at 1,978 blocks flown, the same point as the 4 to 5 s mode of before). Before, the mode showed in 5 of the 10 earlier quiet runs (QUAYLAZY step 6 and the B1 table above). Three low runs are not proof it is gone (the chance of 3 of 3 by luck at a half-and-half rate is 1 in 8); keep watching.
- **The warm settle and the reads pool**: the perf "settle" after the flight took 4.99 and 4.46 s, the same as in every earlier run (4.99 s in the B1 runs), so CASCADE 5b's wait for neighbour plans costs the measurement nothing. Tile plans on reads-pool threads did not visibly starve the terrain reads: the mean cold chunk fell 28 % (576 to 414 ms) and the median chunk is 60 ms; there was no sign of a slower ordinary chunk, but no run isolated it either.
- **Planner rows (after CASCADE 2fix, 3ca5a468 merged; one `perf.sh --planner-only` on the quiet perf worker at 72917c94, `perf: quiet host`, load 0.27 before, 2.3 after): "Cold district plan, p95" is still 3.67 s (ceiling 3 s, FAIL; median 668 ms, budget 300 ms; before CASCADE step 2 about 1.15 s). Step 2 removed a `fullCut` whose quay-chain trace the sample tile 16,-35 reused; 2fix cut it from 5.7 s. Known issue, John decides between a QUAYLAZY follow-up and a different ceiling. The cascade count rows are unchanged by 2fix (16,460 and 38, ok), the region layer and the lazy-quay tile rows are ok. The world rows above were measured before 2fix; it only changes the walk-ahead boxes, but they were not re-run.**

### After QUAYFAST (2026-11-02, quiet perf worker)

QUAYFAST ("quays traced run by run": the quay trace walks each stretch of waterfront once as a run, with a cheap screen that skips cells that cannot hold a quay; byte-identical output; the [plan](../plans/quayfast.md)) is merged through step 4. **Valid numbers only:** two full `scripts/perf.sh` runs on the perf worker (12 CPUs, AMD GPU via radv, dedicated server, no renderer in the rows), commit 05142f53, each ended `perf: quiet host` (window 1,202 and 1,260 s, no other job on the worker, loadavg sampled just before 1.77 and 1.42). A third run before them was discarded as not a measurement (another process used 3.5 cores for 6 minutes).

| What is timed | CASCADE 7 / HEAVY13 | **After QUAYFAST, runs 1 / 2** | Vanilla, same runs |
|---|---|---|---|
| Cold district plan, p95 (`district.cold.p95.ms`, ceiling 3 s) | 3.67 to 3.72 s | **5.96 / 5.97 s (FAIL)** | |
| Same, median | 668 ms | 781 / 711 ms | |
| Same, CPU (`district.cold.cpu.ms`) | 49 to 53 s | 29.5 / 28.4 s | |
| The 7 sample tiles 12 to 18 at z -35 | | 598 / 535 / 693 / 972 / 781 / 1,510 / **5,961** ms; 646 / 511 / 685 / 1,084 / 711 / 1,525 / **5,967** ms | |
| First tile of a metropolis, cold | | 890 / 930 ms | |
| Region warm + roads, cold (`region.cold.warm`) | 9.2 s | 9.22 / 9.24 s (6 s budget, 10 s ceiling) | |
| Quays of the owned tile (`region.cold.quays.tile.ms`) | 2.2 s | 0.95 / 0.97 s, 151 chunk fills (was 2,606) | |
| Cold arrival, 9 x 9 view FULL | 42.3 s (HEAVY13), 39 s (CASCADE 7) | **35.4 / 34.9 s** | 4.7 / 3.9 s |
| TeleportHold held | 37.0 s (HEAVY13), 33 s (CASCADE 7) | **28.7 / 28.6 s** | |
| Slowest chunk of the cold block | 24.5 s (CASCADE 7) | 26.1 / 28.4 s | 0.2 s |
| Flight tick max / p95 | 0.91 s / 10 ms | **4.52 / 4.07 s** / 9.9 / 11.1 ms | 127 ms |
| `/locate` settlement, fresh area | 3.35 s | 3.33 / 3.31 s | 0.21 to 0.25 s (village) |

What it says:

- **The district p95 is not under 3 s; it got worse, from 3.7 to 5.96 s.** The counts did what the plan said (the 8-tile window needs 1,066 chunk fills, was 4,145; the CPU fell about 45 %), but one tile, 18,-35, takes 5.96 s in both runs while the other six take 0.5 to 1.5 s (the old p95 holder, 16,-35, now 0.7 to 0.8 s). Stack sampling of that tile (locally, same 6 s serial) puts 15 of 19 samples inside the new cheap screen: it reads biome noise per cell, uncached, and on this tile that costs more than the fills it saved. Caching or batching those reads per coarse cell is the obvious next step (not done; no output change).
- **The cold arrival is 4 to 5 s faster than HEAVY13 (42.3 to 35.4 / 34.9 s) and 4 s faster than CASCADE 7 (39 s); the hold is 28.6 s (was 33 to 37 s).** The 3 to 4 s arrival rise HEAVY13 showed is gone. The slowest single chunk is 2 to 4 s slower than in CASCADE 7 (26.1 / 28.4 against 24.5 s), so the arrival gain comes from the other chunks, not from the slowest one.
- **The two-mode flight tick is back** (4.5 and 4.1 s, at about 2,360 blocks flown; CASCADE 7 saw 0.9 s). It was in 5 of 10 earlier quiet runs, so 2 of 2 is not new information.
- The region row is unchanged (9.2 s) and `/locate` too: QUAYFAST does not touch them.

For the district-ceiling question: the 3 s ceiling is missed by a factor of two by one tile, and the cause is known and local to the screen's biome reads; fixing that is a smaller job than moving the ceiling.
The hold cap (TeleportHold, TPHOLD15) and the district-plan ceiling: see the questions in [QUESTIONS.md](../QUESTIONS.md) (the TeleportHold cap line; John decided 2026-11-01: 60 s, now the default). The numbers: a cold metropolis arrival holds 32.8 to 33.2 s, the slowest chunk is 24.6 s. A 15 s cap releases the player about 18 s before the destination is ready.

## 8. Options for the cold-arrival wait (for John)

The numbers are from section 7 (the perf worker, quiet). "Wait" means the cold 9 x 9 block at the metropolis: about 60 s with the mod against 2.6 s in vanilla. Its slowest chunk is about 53 s against 0.33 s.

| Option | What the player sees | Cost to build | Risk |
|---|---|---|---|
| 1. Keep the 30 s teleport hold and the current work | A cold far teleport into a metropolis waits 30 s at "Generating terrain", then lands among chunks that keep arriving for about another 25 to 30 s (*estimate*, from the 53 s chunk) | None | The 30 s limit is already below the measured wait, so landing in ungenerated terrain is normal for metropolises |
| 2. Raise the hold to cover the measured wait (about 60 s) | Up to a minute on "Generating terrain", then a ready city. About 23 times vanilla's wait for the same block | XS (one constant and its test) | Makes the slow path official. Nothing gets faster |
| 3. Hold only for what the destination tile needs (section 4 B) | Shorter hold away from water. Waterfront tiles still wait for the quays (about 20 s of planner time) | S | Chunks further out appear later, as in vanilla |
| 4. Take the quay trace out of the chunk pipeline, or make it lazy (sections 4 E and 6 a) | The cold chunk drops by most of the quay share. How much is not measured: 20 s of the 31 s warmer path is quays | M | Must keep `tile-border-agree` (quays cut the same way on both sides of a tile border) |
| 5. Cheaper region work (section 4 D, F) | Shorter region row (11.6 to 14.8 s now, ceiling 10 s) | S to M each | F may bring back junction steps |
| 6. Save warmed data with the world (section 4 H) | Nothing on the first visit. No second payment after a restart | M | File format, invalidation on `genVersion` |
| 7. Raise the region row's ceiling to what the perf worker measures, or set ceilings per host | Nothing | XS | Hides growth unless the count rows stay (they do) |

What you'd decide: (a) the acceptable cold-arrival wait, either in seconds or as a multiple of vanilla's wait for the same chunks; (b) whether 0.1.0 waits for 3 and/or 4, or ships with 1 or 2 and does them after; (c) the region row's ceiling (7, or after 5). Missing before (a) can be set from a real number: a perf row that teleports the mock player into the metropolis and times "teleport → every chunk in view distance is FULL" in both worlds. It isn't measured today; it's a small test-harness item.

**COLDARRIVAL addendum (2026-10-31, assistant/coder).** The four rows above marked COLDARRIVAL come from a new second pass of `scripts/perf.sh` (fresh worlds, `-Pfb.perfMode=arrival`, `PerfServerHarness.arrivalTick`), so the earlier cold-block rows are unchanged. Both runs on the perf worker, each started with `scripts/quiet-check.sh --worker perf` quiet and quiet at every step (`quiet-check` lines: other-cpu 0.03 to 0.05, other-jobs 0; 1-min load at the start of the world half 1.75 / 1.50, and at the start of the mod / base arrival JVMs 3.1 / 4.2 (run 1) and 4.1 / 4.0 (run 2), which is the run's own build and threads). Evidence (forensic source, not tracked): `.claude/reports/p6-coldarrival-r1-evidence/7fd247f8c8-20261031T080227Z/` and `p6-coldarrival-r2-evidence/7fd247f8c8-20261031T083831Z/`. What this adds to section 8: the wait to "chunks in view ready" is about 70 s with the mod against about 5 s in vanilla, the 30 s hold always gives up (so a player lands in a city that is 40 s from finished), and the server stalls for 9 to 11 s in one tick while waiting (vanilla 0.4 s). The mock connection never acks chunk batches, so "sent" is not measured: the figures are server-side readiness. Vanilla's own wait here includes the hold (3 s) because the mod's hold also runs in the base world (it holds, then releases when the 3 x 3 is FULL).

- @John 2026-10-31: It sounds like the hold when teleporting in doesn’t really protect you from much. Vanilla itself shows loading terrain. So long as you don’t drop directly on void, I think we might actually _lower_ the hold, even to like 15s. And then teleport. Or maybe more like option 8-3: hold for what the tile requires. With a max of 15 seconds. Then I think we should also try number 8-4 - 6a and 6c.
  - @coder 2026-10-31 (TPHOLD15 = the teleport-hold change; TPHOLD = the hold itself): Done: the hold's limit is now 15 s (`TeleportHold.DEFAULT_TIMEOUT_MILLIS`). You asked whether you drop onto void. You do not: a survival player teleported into a cold metropolis with the hold released early (limit 3 s, then the real 15 s, cold world, seed 987654321) took no damage, never went below the surface and ended up standing once chunks arrived (client game test `TeleportHoldClientGameTest`, on the local iGPU and on the perf worker). But the reason is not that vanilla's loading screen covers it. Vanilla loads the chunk you are placed in synchronously on the server thread, so the whole server tick stops until that chunk is generated: "Can't keep up! Running 40325ms behind" on the perf worker after the 15 s limit (178 s with the 3 s limit on the local iGPU). In single player the game freezes with no action bar message. The async 3 x 3 hold is what avoids that. Option 3 ("hold for what the tile requires", capped at 15 s) is built behind `-Dfractalbuildings.teleportHoldReleaseOnPlan=true`: it ends the hold when the destination tile is planned and warmed instead of when the 3 x 3 chunks are FULL. It is off by default because the freeze after release is the same mechanism and I did not get a measurement where the plan finished inside 15 s (here the warm never did), so I cannot say it shortens the stall. Your call: keep the 15 s cap with the freeze afterwards (what ships now), turn option 3 on, or make the stall itself shorter (options 4 to 6 above: it is the chunk generation, not the hold, that takes the time).
  - @coder 2026-10-31 (r2): Per your decision (option 1) the hold cap is now 90 s, not 15 s. It still ends as soon as the 3 x 3 chunks are FULL, so the cap only matters on cold metropolis arrivals (about 70 s). The 15 s experiment above stays as the reason: a short cap turns into a server stall. "Hold for what the tile needs" stays built but off.
