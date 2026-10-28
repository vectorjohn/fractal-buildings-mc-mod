# SPARSEREADS: reading fewer terrain columns

For John, 2026-10-28 (coder, branch `claude/p6-sparsereads`). You asked whether reading half of the
columns (a checkerboard) or a third of them gives the same shape of terrain for road placement, and
said to nix it if it gives bad results. I measured it. **Recommendation: nix it. No sampling mode is
worth turning on for any consumer.** Nothing is on by default; the switch exists only as an opt-in
probe wrapper plus a measurement harness (section 6). Numbers marked *model* come from unit costs
measured earlier, not from a game run.

## 1. The short answer

- **Shape is fine; decisions are not.** The interpolated height is within about 0.1 to 0.2 blocks of
  exact on average, and junction levels, quay lines and lots mostly come out the same. But the road
  graph and the settlement decisions are threshold decisions (a lattice node is wet or dry, a
  settlement's water share is under or over a limit, two routes cost nearly the same). One flipped
  node moves a route by tens of blocks and adds or drops a whole link.
- **The saving is mostly not there.** What costs time is the *chunk fill* (1.9 ms for 256 columns:
  0.008 ms a column) and the single lattice column (about 0.35 ms). A chunk fill is cheaper than
  reading more than 5 columns one by one, and the consumers that read the most (the junction field,
  the quay trace) already touch about 14 to 17 columns of every chunk they touch. Reading every other
  column does not skip any chunk, and the line-shaped consumers need *more* reads, not fewer, because
  an unread column on a street needs its neighbours, which lie off the street.
- **Best case for any single change is under 10 % of the cold metropolis row** (the lattice and
  station reads, which are the only reads that are paid column by column), and it changes the roads.

## 2. Which consumer needs what

| Consumer | Reads | Needs exact? | Why |
|---|---|---|---|
| Region decisions (`RegionPlanner`, intercity graph) | 64-block lattice, single columns, 68k cold | **Yes** | Floor, water and peak shares against fixed limits and a dice roll decide whether a settlement exists, its tier and radius (ADR 0004; plan phase 6, decision 1: the coarse screen is off by 12 to 16 blocks and was rejected). |
| Route search (`RoadNet.node`) | the same lattice | **Yes**, in effect | Wet or dry and floor per node feed an A* cost. Equal-cost alternatives over flat ground flip on a one-block change. |
| Road stations (`Profile.build`) | every 8 blocks along a route, 14.6k cold | Wet or dry: **yes**. Height: no | The dry check needs every wet station (a river 8 to 15 blocks wide is one or two stations). The deck is the mean over 17 stations, so height errors wash out. |
| Junction field (`JunctionField`) | whole streets, chunk fills (4.9k chunks cold) | Heights: **mostly no**. Cost: it is a chunk-fill cost | One level per junction, grade-feasible; the rule `junction-height` and the DS-46 "streets stay within 2 of the ground" are about the plan, but the plan is only as good as the ground it was graded on. |
| Street grading (`Grading`, `StreetRules`) | per column along streets | **Yes** (steps over 1, DS-46 cut and fill 2 and 4) | A road column one block off is a step; GroundWalk counts neighbours that differ by more than 1. |
| Quay trace (`Districts.quayWholes`) | water test over every waterfront tile, 600k calls cold, about 19k chunks on the metropolis | Water or land: **yes**. Height: no | The quay follows the waterline, so every read it makes is on the shore, where interpolation is not allowed anyway (see 3). |
| Lot ground and doors (`SiteGround`, `LotGround`, DS-45 door rise, DS-46 fill-face) | per lot footprint | **Yes** | Door rise is measured against the street surface in front, limits are 2 and 3 blocks; foundations and cuts are exact heights. |
| Biome | surface column | Nearest is enough | Already one read per lattice node. |

## 3. What I built (all opt-in, default off)

`SparseTerrain` (`planner-core/.../probe/SparseTerrain.java`) wraps any `TerrainProbe`. On a lattice of
spacing `step` (1 for block columns, 64 for the region lattice):

- **`checker`** reads the points with `i + j` even (half). An unread point has all four neighbours read.
- **`third`** reads `(i + j) mod 3 = 0` (a third, on a diagonal so that every unread point has a read
  neighbour at distance 1: two of them).
- An unread point takes the **mean of its read neighbours** (floor and water depth, rounded half up).
  I chose a mean of neighbours over nearest-value because it has no direction bias (nearest would copy
  the first neighbour in some order) and keeps slopes; over bilinear on a coarse grid because that
  needs 4 reads per column and the point of the exercise is fewer reads.
- **Water and land are never interpolated across.** If the read neighbours disagree on wet or dry
  (depth over 0, and depth of 2 or more, the region layer's water test) the column is read exactly.
  A lake surface stays flat (the mean of equal surfaces) and a dry column never gains water. Optional
  `maxSpread`: also read exactly where the neighbours' floors differ by more than that (cliffs); the
  `-s3` and `-s16` rows below.
- Biome of an interpolated lattice point is copied from its first read neighbour (no id arithmetic).
- Probe-spec modifier `+sparse:<checker|third>[:<spread>]` (`recorded:FILE+sparse:checker`), and
  `city-check --terrain-mod +sparse:checker` for the gate windows. Unit tests:
  `SparseTerrainTest` (read share, bounds, shores, cliffs, spec; breaking the wet/dry guard fails the
  shore test).

Files touched outside the new ones: `ProbeSpecs` (one `case`), `CityCheckCommand` and `Main` (one
option). **None of PARREADS' files** (`ColumnMemo`, `LiveTerrainProbe`, `SparseColumns`, `ChunkFill`,
`LiveProbes`, `WorldPlanner`) are touched, so the two merge cleanly.

## 4. Results

Harness: `SparseReadsTool` (6). Reads are *distinct* columns and distinct 16 by 16 chunks asked of the
real terrain, as the live memo would see them (a repeated read is free). Raw tables are in
[sparse-reads-data/](sparse-reads-data/).

### 4.1 City pipeline, nine gate windows, all exact consumers sparse

Each gate window planned as the game plans it (`WorldPlanner.forSite`): `warm` (street network and
junction field = J), `warmQuays` (Q), then every tile's plan, buildings and ground (T). Totals over the
nine windows, step-8 dumps (the committed ones):

| mode | J columns / chunks | Q columns / chunks | T columns | lots (built) | tiles with a step over 1 / over 3 | road cells more than 2 off the real ground |
|---|---|---|---|---|---|---|
| exact | 188,456 / 13,158 | 1,003,079 / 61,043 | 4,440,917 | 10,411 (9,166) | 107 / 11 | 173,272 of 1,095,701 (15.8 %) |
| checker | 283,252 / 19,748 | 1,045,841 / 54,908 | 2,263,222 | 10,400 (9,148) | 117 / 11 | 177,035 of 1,098,833 (16.1 %) |
| third | 193,313 / 19,884 | 1,560,106 / 54,970 | 1,536,012 | 10,413 (9,147) | 110 / 10 | 178,090 of 1,100,660 (16.2 %) |

The metropolis `gate-metropolis-temperate` (the perf row's city), exact against `checker`: J 69,807
columns / 4,641 chunks against 104,529 / 6,928; Q 314,851 / 18,979 against 329,989 / 16,785; T
1.69 M against 0.85 M columns. The `-s3` guard changes nothing measurable.

Reading half of the *tile* columns (T) does halve T's columns, but those chunks were already filled by
J and Q (on the metropolis T adds 795 chunks to the 23,620 that J and Q fill), so a live probe saves nothing.

**Same on real noise.** The step-8 dumps are bilinear (smooth), so I re-dumped four of the windows
(temperate, snow, coast, border) at step 2 with the vanilla noise (`TerrainDumpTool`, not committed)
and reran: same pattern. J 37,690 / 2,697 exact against 56,827 / 4,199 checker; Q chunks 16,207 against
14,721; steps over 1: 20 / 24 / 22; road cells more than 2 off: 16.5 / 16.8 / 17.2 %
([city-step2.md](sparse-reads-data/city-step2.md)).

**Model of the live cost** (metropolis `s2_m6`, a = 0.35 ms for a single column, b = 1.9 ms for a
windowed chunk fill; a is what the 36 s row implies, b is A1's number; *model*, not measured in game):

| consumer | exact | checker | third |
|---|---|---|---|
| J, by chunk fills | 8.8 s | 13.2 s | 13.2 s |
| J, by single columns | 24 s | 37 s | 25 s |
| Q, by chunk fills (before memo refills; the real trace takes 56 to 70 s) | 36 s | 32 s | 32 s |

A sparse live probe would have to read columns one by one, which is dearer than the chunk fill for
every row of J and Q (a chunk fill beats single columns above b / a = 5.4 columns per chunk; J and Q
read 14 to 17). The only column count that falls is T's, and T is chunks J and Q already filled.

### 4.2 Quay layout and junction levels (same runs)

| | junction levels, mean / worst difference | junctions off by more than 1 | quay columns on both / in exact | quay height, mean difference |
|---|---|---|---|---|
| checker, step 8 | 0.07 to 0.15 / 1 to 5 | 0 to 2 per window | 97.5 to 100 % | 0.02 to 0.08 |
| third, step 8 | 0.12 to 0.26 / 1 to 5 | 0 to 5 per window | 91 to 98.5 % | 0.05 to 0.15 |
| checker, step 2 | 0.02 to 0.10 / 1 | 0 | 99.9 to 100 % | 0.01 to 0.07 |
| third, step 2 | 0.02 to 0.23 / 1 | 0 | 93 to 99 % | 0.05 to 0.10 |

So the quay line and the junction levels do survive `checker`. Lots are less stable: with `third`, 22 to
37 % of lots differ in box or type (the tile's street heights move by one and the lot layout reflows),
and a lot's front height differs by more than 1 for 59 lots on the temperate city and 591 on the
metropolis.

### 4.3 city-check on the nine gate windows (self-consistent plans, step 8)

All three modes give **0 violations** (every rule, including `junction-height`, `bridge-end-earth`,
`tile-border-agree`). The report-only counts move a little:

| | exact | checker | third |
|---|---|---|---|
| door-rise warnings (sites) | 1 | 2 | 1 |
| shop doors within DS-45 | 168 / 171 | 168 / 171 | 177 / 178 |
| house doors within DS-45 | 73 / 75 | 70 / 73 | 69 / 71 |
| gate-cut over 4 | 2 | 2 | 3 |
| road-sliver warnings | 28 | 31 | 26 |
| steep roads / bridges / quays | 1,108 / 1,361 / 432 | 1,123 / 1,354 / 434 | 1,153 / 1,351 / 453 |
| cut / fill (thousand blocks) | 8,687 / 26,736 | 8,687 / 26,797 | 8,755 / 26,915 |

`GroundWalkTest.junctionStepsDoNotGetWorse` keeps a ratchet of 107 tiles with a step over 1: `checker`
gives 117, so it would fail the ratchet on the same windows. The plan is self-consistent either way
(city-check measures against the plan's own terrain); the real ground still differs by the amounts in
4.1 and 4.2.

### 4.4 Region lattice and road graph (the two world dumps, 384 x 384 nodes)

The lattice is the exact data here (the dump is the real value at every node), so this one is a clean
test. Sparse applied to the lattice reads of the region planner and the route search (`RoadNet`):

| world | mode | lattice columns read (exact: 59,614 and 61,593) | settlements: same / differ of 152 and 150 | links only in exact / only in sparse | route mean offset, worst route max | crossings on the common links, exact / sparse |
|---|---|---|---|---|---|---|
| 12345 | checker | 55,065 (-8 %) | 126 / 26 | 19 / 19 | 54 / 687 blocks | 26 / 25 |
| 12345 | third | 37,545 (-37 %) | 102 / 50 | 39 / 21 | 67 / 716 | 14 / 11 |
| 12345 | checker-s16 | 61,839 (+4 %) | 133 / 19 | 13 / 12 | 32 / 606 | 35 / 30 |
| 987654321 | checker | 57,822 (-6 %) | 125 / 25 | 26 / 23 | 42 / 379 | 23 / 15 |
| 987654321 | third | 39,272 (-36 %) | 107 / 43 | 34 / 28 | 78 / 528 | 17 / 14 |

Only the route search made sparse (settlements stay exact, so only routes move; `routes-*` rows in
[region.md](sparse-reads-data/region.md)): checker saves 3 to 5 % of the union of reads, third 11 to 12 %; 15 to
21 of 73 links change (checker), mean route offset 30 blocks. With the `s16` guard the offset falls to
11 to 16 blocks and 10 to 13 links still change, and no reads are saved.

### 4.5 Station reads along a route (`Profile.build`)

The 140 exact routes of the two world dumps replayed on 4-block terrain windows cut around them
(`TerrainDumpTool`; 67 are dry-ok on the real ground). Every second (k = 2) or third (k = 3) station
read, the others interpolated along the route (wet/dry disagreement reads the station):

| k | station reads | wet flags that differ | crossings found (exact 43) | deck difference mean / max | verdict (ok or not) differs |
|---|---|---|---|---|---|
| 2 | -45 % | 58 | 32 | 0.3 / 19 | 0 of 140 |
| 3 | -60 % | 106 | 21 | 0.5 / 19 | 2 of 140 |

A crossing narrower than two read stations goes unseen (a quarter of them at k = 2, half at k = 3), so
the road would run through the water without a deck. The 14.6k station columns are about 14 % of the
row (5 s of 36), so even k = 3 would save about 3 s (*model*).

### 4.6 Plan pictures

`plan.png` for two cities, exact against `checker` and `third`:
[coast city](sparse-reads-data/plan-coast-city-exact.jpg),
[checker](sparse-reads-data/plan-coast-city-checker.jpg),
[third](sparse-reads-data/plan-coast-city-third.jpg);
[badlands](sparse-reads-data/plan-desert-badlands-exact.jpg),
[checker](sparse-reads-data/plan-desert-badlands-checker.jpg),
[third](sparse-reads-data/plan-desert-badlands-third.jpg). I opened the coast city (exact and
checker) and the badlands city (exact and third): at plan scale the main streets, arterials, squares,
gates and links are in the same places. The badlands `checker` picture is pixel-identical to exact
(its `settlement.json` still differs); the coast city's differ in a handful of lots at the picture's
zoom (for example one lot beside the north-east street). The plan view cannot show a one-block step,
which is where the sparse plans differ (4.1).

## 5. Recommendation per consumer

| Consumer | Recommendation | Reason |
|---|---|---|
| Region decisions | **Keep exact** | 17 to 33 % of settlements change with checker or third. |
| Route search lattice | **Keep exact** | At most 12 % fewer reads; 15 to 30 links in 73 change, routes move 30 blocks on average. |
| Road stations | **Keep exact** | k = 2 loses a quarter of the crossings; saving about 2 s of 36 (*model*). |
| Junction field | **Keep exact** | Reads go up 50 % (checker) and chunks touched go up 50 %; it is chunk-fill bound. |
| Street grading, lots, doors | **Keep exact** | DS-45, DS-46 and the step ratchet are exact-height rules; the lot layout reflows. |
| Quay trace | **Keep exact, fix it another way** | The layout survives `checker` (99.9 % of columns, 0.03 blocks) but only about 10 % fewer chunks are touched (my reading: the trace's reads sit on the shore, where the wet/dry guard forces an exact read; I did not check this). Lazy tracing (regionperf option E) or per-water-body parallelism (PARREADS) attack the 19k chunks directly. |

**Where this would pay.** Only a sparse *fill*: evaluating vanilla's noise on the 4 by 4 cell corners
of a chunk (the generator itself interpolates density between corners) and not on every block would
cut the chunk cost, and would help the junction field and quays. That is a change to `ChunkFill`
(PARREADS' file) and a parity test against `getBaseColumn`; I did not try it. Until then the levers
are fewer chunks (lazy quays, B/C/D in [regionperf-decision.md](regionperf-decision.md)) and more
workers (PARREADS).

**Keep or delete the code?** It is small, opt-in and tested. I would keep it until PARREADS and lazy
quays land and then delete it if nothing uses it; your call.

## 6. Method, limits, how to re-run

- **Harness.** `planner-core/src/test/java/dev/fractalbuildings/core/world/SparseReadsTool.java` (a
  `main`, not a test; the `settlement/SparseHooks` shim reaches one package-private method).
  Build with `nix develop -c ./gradlew :planner-core:testClasses`, then
  `java -Xmx12g -cp planner-core/build/classes/java/main:planner-core/build/resources/main:planner-core/build/classes/java/test:planner-core/build/resources/test dev.fractalbuildings.core.world.SparseReadsTool city|region|routes|stations OUT [...]`
  from `planner-core/`. `FB_SPARSE_ONLY=a,b` limits windows; `FB_SPARSE_FIXTURES=DIR` swaps in finer
  dumps with the same names and a `gate-sites.tsv`. `stations OUT WORLD DETAIL...` takes the window
  dumps. Finer terrain windows: the `TerrainDumpTool` recipe in
  [fixtures/terrain/README.md](../../fixtures/terrain/README.md). City checks:
  `fractal-cli city-check --seeds 0..0 --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:2 --door-rise --terrain-mod +sparse:checker`.
- **Pictures.** Temporary presets `viewer/city-presets/sparse-*.json` (terrain spec with
  `+sparse:checker`), then `FB_CITIES=... CITY_GALLERY=1 npx playwright test e2e/city.spec.ts`. The
  presets are not committed.
- **Limits.** Time is a *model*: planner time on recorded dumps is not the cost (a recorded column is
  free), so I counted distinct columns and chunks and priced them with A1's 1.9 ms per windowed chunk
  and the 0.35 ms per single column that the 36 s row implies (A1 measured 0.55 ms under load). The
  conclusion does not change for any single-column cost above about 0.3 ms: that is where a column
  beats a chunk. The gate windows are step-8 bilinear dumps (smooth, so favourable to sparse); the
  step-2 reruns of four windows and the region lattice (exact by construction) are the checks.
  Metropolis `s2_m6` only in the step-8 run. No game run, no heavy layer.
