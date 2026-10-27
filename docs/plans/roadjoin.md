# Plan: 6.15 ROADJOIN, roads between settlements meet the streets

Lead-coder diagnosis and plan, 2026-10-27, branch `claude/p6-roadjoin` (base `claude/run-phase6` at 1008ce76).
Item: [ROADMAP 6.15](../ROADMAP.md) (grep `ROADJOIN`); John's playtest notes:
[REVIEW.md](../review/phase6/playtest-comments/REVIEW.md), world seed 1709797029209580947, build e237e451.

# Goal

A road between settlements (an ADR 0004 graph route, plus the *approach* that joins it to a
required exit) must reach the settlement's street network: no height step, no gap, no deck at
water level, no exit the town never builds. The approach is the road layer's piece from the
exit gate to the port 64 blocks outside the rim (`WorldPlanner.approachRoads`, drawn by
`RoadFeature`); every cause found is in that piece or in where its exit sits. The graph routes
themselves join their approaches correctly (`WorldPlannerRoadsTest` checks the port end, +-1).

# How it was diagnosed

- Terrain dumps of the playtest world (`TerrainDumpToolTest.dump`), the planner run on them
  through a new harness (`JoinWorld`, `RoadJoinProbeTest`, both in `planner-core/src/test/.../world/`),
  and the **saved playtest world itself** read block by block (`scripts/McaTop.java`, a region-file
  reader; John's map is at `/home/agent/kaiju-survival-server/run/world`).
- At the hamlet join the planner on the 2-block crop and the saved world agree block for block
  (approach top 69, street top 64-65, columns x 3598-3617, z 1437-1441). So the CLI/planner
  reproduces the game here, and the fixes can be tested in planner-core.
- Breadth: the same measurement over the world-12345 map (`FB_ROADJOIN=w12345`, the five cells of
  `WorldPlannerRoadsTest`) and every settlement of the playtest window.

Commands (all in planner-core, seconds each; env vars are not Gradle inputs, keep `--rerun`):

```sh
FB_ROADJOIN=1 FB_ROADJOIN_CELLS=all nix develop -c ./gradlew :planner-core:test --rerun --tests '*RoadJoinProbeTest.probe' -i
FB_ROADJOIN=w12345 nix develop -c ./gradlew :planner-core:test --rerun --tests '*RoadJoinProbeTest.probe' -i
FB_ROADJOIN=1 FB_ROADJOIN_AT="3205,1685,24" nix develop -c ./gradlew :planner-core:test --rerun --tests '*RoadJoinProbeTest.at' -i
FB_ROADJOIN=1 FB_ROADJOIN_MAP="3264,1856,128,4" nix develop -c ./gradlew :planner-core:test --rerun --tests '*RoadJoinProbeTest.map' -i
nix develop -c java scripts/McaTop.java /home/agent/kaiju-survival-server/run/world/dimensions/minecraft/overworld/region 3598 1428 3630 1450
```

`JoinWorld.joins` reports per approach: `drawn` / `masked` columns (masked = inside a district
mask, so the district draws or owns them, D5), `contacts` (4-neighbour pairs of a drawn approach
column and a district street, deck or plaza column), `maxStep` over the contacts, `gap` (blocks
between the nearest drawn approach column and the nearest district surface), `wetLow` (drawn
columns over water whose top is not above the water's top block) and whether the exit's tile is
planned.

# Causes, with evidence

Baseline (current code). Playtest fixtures (`FB_ROADJOIN=1`): 11 approaches. world-12345: 15.

| Approach | Exit | maxStep | gap | wetLow | exit tile | Cause |
|---|---|---|---|---|---|---|
| playtest `k3_1_0` (hamlet s3_1) | 3616,1440 | **5** (69 vs street 64) | 0 | 0 | planned | A |
| playtest `k3_1_1` | 3680,1408 | 1 | 0 | 0 | planned | ok |
| playtest `k2_1_0` (metropolis s1_2) | 3200,1696 | **7** (62 vs deck 69) | 0 | **20** | planned | A, B |
| playtest `k2_1_1` | 3264,1856 | none | **none** | 3 | **UNPLANNED** | C |
| playtest `k4_4_0` (s64 data) | 4576,4480 | none | **15** | 0 | planned | D |
| playtest `k4_5_0` (s64 data) | 4640,5728 | none | **8** | 0 | planned | D |
| playtest others (5) | | 1, 2, 3, 3, 3 | 0 | 0-100 | planned | A (B?) |
| world-12345 (15) | | 3 at most 1; 11 at 2-15; 1 no contact | 0 | `k2_1_2` **279** | `k2_1_1` UNPLANNED | A, B, C |

(`wetLow` on s64-only data, e.g. playtest s4_1's 100 and 77, may partly be interpolation; the
saved world at s4_1's planner exit has no road at all, so those rows are logic reproductions, not
John's spots. Rows on the 2-block crop are exact.)

**A. Nothing plans one height at the handover** (John's 21.26.27, the "2-3 block step" at
3623,74,1443; measured 4-5). `Profile.approachDeck` (planner-core `road/Profile.java` 70-86) pins
only the *port* end of an approach to the graph road's deck (`RoadNet.endDeck`); its exit end is
the mean of the natural surface over +-8 stations. The district street at the gate is graded by
the district layer (`Districts.Build.grade`, `Grading.profile`) and can sit in a cut or on fill:
hamlet s3_1's path `r3_1_0` carries the note "needs more than 4 blocks of cut or fill on a steep
stretch" and ends at top 64-65 where the ground is 69. Neither side reads the other, so they meet
with a step. Saved world, x 3610-3620, z 1436-1442: dirt path/gravel at y 69 meets gravel at 64-65.
It is systemic: 11 of the 14 world-12345 approaches that touch a street, and 6 of the 8 playtest ones, meet it with a step of 2 or more.

**B. Approaches ignore the water rule** (21.31.49 at 3201,70,1683, "bridge ... don't meet up";
probably also the 21.35.56 / 21.36.39 berms, see below). ADR 0004 N5 says approaches follow the
water rule too; the code does not. `LinkExits.approach` tests water only at its 32-block lattice
nodes (`c.terrain.water`, snapped; the skeleton may not read off-lattice columns, ADR 0003 D4, and
`CoarseProbe` throws if it tries), and `approachDeck` has no dry check, no crossing and no
clearance: over water its "surface" is the water's, so `RoadSurface` lays a DECK at water level
(WR3 makes wet road columns deck). Metropolis approach `k2_1_0` runs ~25 blocks along a river with
its top at 62-63 on water whose top is 62, and starts beside the district's own 3-block bridge stub
at 65 (the street end `r2_1_2` is wet at the exit). Saved world: oak planks at y 62-63 in water
beside oak planks at 65 at x 3203-3205, z 1692-1700, exactly the screenshot.

**C. An exit can lie in a tile no settlement plans** (likely 21.19.18, "road stops well short of a
tiny town"; no coordinates, so not located). `LinkExits.exitFor` (and the hashed exits,
`DefaultProgram.pick`) pick the exit by *cell* ownership (`c.owned`, `CellGeometry.owns`), but
districts are planned by *tile* ownership (`Districts.owns`: the settlement of the cell holding the
tile centre). Metropolis exit 3264,1856 lies in tile (20,11), box 3216,1776..3360,1936, whose centre
cell (3,1) is hamlet s3_1's; `Districts.owns(metropolis)` is false and the hamlet does not plan it
either. So the last ~150 blocks of main road `r2_1_3` ((3168,1984) to the exit) and the exit are never
drawn; the approach starts in bare ground 100+ blocks from the last street (map at 3264,1856 r128).
Same in world-12345 `k2_1_1`. Related to 6.16 DEADMAIN (a main road ending where nothing continues).

**D. Approaches are routed over the district's lots.** `LinkExits.approach` knows the coarse mask
but not lots; road columns inside a district mask (ground boxes, building boxes **and lots**,
`WorldPlanner.districtColumns`) are the district's (D5, MF2), so an approach over lots is simply
missing from the street to the lots' far edge. `k4_4_0` leaves its exit back through the village
(the lattice search detours) and first appears 15 blocks from any street; `k4_5_0` leaves a
mid-street exit diagonally over the lots beside it, 8 blocks short.

**Not located: 21.35.56 "bridge going right into a berm" and 21.36.39 "landslide" (dirt mound by a
bridge approach).** Night shots without coordinates. Graph-route crossings in the window are flat at
bank height (`RoadJoinProbeTest.crossings`: decks 67-69 on banks 66-70), so they are not the berm.
The likely mechanism is A/B at a bridge: an approach or street end beside a district deck at a
different height, where `RoadSurface` builds a 1:1 dirt embankment (up to `FILL_REACH` 8, top block
`nativeTop`, dirt below) or a terrace to bridge the gap. Ask John for coordinates (F3) or re-check
these spots after steps 1-4 (step 5).

**Not causes:** the graph route's port end (pinned, +-1); the route's own crossings; the
foreign-structure yield (no structures near these spots).

# 6.17 ROADCLEAR: same cause?

No, as far as the code shows. ROADCLEAR candidate (b) was "the road's planned ground below the live
terrain". At every ROADJOIN spot the approach sits at or above the natural ground, and where a road
column is planned below the ground `RoadFeature.draw` clears that column all the way up to its
scanned ground (`clearTo = max(g, surf + HEADROOM)`) and terraces 12 blocks either side, so a deep
plan alone gives an open cut, not a tunnel. A tunnel needs solid blocks above a road column that the
ground scan did not count (`g` below the live top there), or terrain beyond the terrace reach
overhanging the road. So the ROADJOIN fixes do not cover ROADCLEAR; its own `RECORD_STATS`
diagnosis (it records `scan` and `ground` per column) should look for road columns whose scan top
is below the live top. One shared touch point: step 1 makes some approaches follow a street down
into a cut (hamlet g0: 5 blocks), so more approach columns will be terraced; ROADCLEAR's test
should include such a column.

# Fix steps

Harness, fixtures and tools are committed with this plan, so every step can start at once:

- `fixtures/terrain/pt1709-world-s64.json.gz` (the playtest world's region map) and
  `pt1709-join-s2.json.gz` (2-block crop x 3136..3776, z 1344..1920). Regenerate with
  `-Dfb.dump=1709797029209580947:-12288:-12288:64:384:384:pt1709-world-s64,1709797029209580947:3136:1344:2:321:289:pt1709-join-s2`.
- `JoinWorld.playtest()` (planner on those), `JoinWorld.joins(w, cx, cz)` (the measurement above),
  `WorldPlannerRegionTest.planner(...)` for world 12345; package-private `WorldPlanner.probes()`
  and `WorldPlanner.source(site)` for tests.
- `RoadJoinProbeTest` (opt-in, prints the tables and maps) and `scripts/McaTop.java`.

Corpus for every step's test: playtest cells `3,1` (hamlet s3_1) and `1,2` (metropolis s1_2) on
`JoinWorld.playtest()`, plus the world-12345 cells of `WorldPlannerRoadsTest.CELLS`. Assert on
corpus-wide properties, and name specific approaches only on the hamlet (its exits do not move in
step 3). Each step: run `scripts/test-scope.sh claude/run-phase6` for the layers; fixtures that
snapshot skeletons or approaches (`SampleSettlementTest`, viewer settlement fixtures) may need
regenerating when exits move (step 3), say so in the report.

## Step 1 (A): pin the approach's exit end to the street (S-M)

Files: `planner-core/.../world/WorldPlanner.java` (`approachRoads`), `road/Profile.java`
(`approachDeck`), `settlement/Street.java` or `Briefs.java` (a public level lookup).

1. Add a public lookup of a street's level near a column: `Briefs.nearest(Street, px, pz)` exists
   (package-private, returns {Chebyshev distance, interpolated level}); expose it, e.g.
   `public int[] Street.nearest(int px, int pz)` delegating to it.
2. In `WorldPlanner`, `OptionalInt streetLevel(SettlementSite site, int x, int z)`: over the tiles of
   the 3 x 3 around `grid.tileOf(x, z)` that `tile(ix, iz)` says this site plans, every street of
   `plan(ix, iz).plan().streets()`; take the street whose nearest centre-line column is within
   `width / 2 + 1` of (x, z), nearest first, ties by street id. Plazas: if (x, z) lies in a plaza
   rect, its `y()`. Empty when none.
3. `approachRoads`: for each approach, pin every leading station that lies on a district street
   (`streetLevel` present at the station's rounded column), and at least station 0 when the exit has
   a street level, to that level. Give `Profile.approachDeck` a `SortedMap<Integer,Integer> pins`
   (station index to deck) in place of the single `endDeck`, keeping the port pin last; `limit()`
   already leaves pinned stations alone.
4. Feasibility: when two pins are further apart in height than `MAX_STEP` per station allows,
   `limit()` leaves a jump. Count it (`Approach` gets an `int steepJoins`), log it in the C1
   diagnostics line, and let the test report it; do not drop either pin (the port pin is the graph
   road's contract).
5. No cycle: the approach deck now reads the exit tile's plan; the plan reads the skeleton and never
   the approaches' decks. Keep it so (step 4 reads approach *geometry* only).

Test (new `ApproachJoinTest`, planner-core world package):
- hamlet `k3_1_0` on `JoinWorld.playtest()`: `maxStep <= 1` (now 5). The red-green check.
- corpus: every approach with a planned exit tile and `gap == 0` has its *exit contact* (contacts
  whose approach column is within `2 * width` of the drawn column nearest station 0) at step `<= 1`;
  print the other contacts' steps (advisory: an approach running beside a street elsewhere).
- `steepJoins == 0` on the corpus (if not, report the cases; see risks).
- mutation: remove the exit pin, the hamlet assertion fails.
- unchanged: `WorldPlannerRoadsTest` (port end +-1), `WorldPlannerOrderTest`, determinism tests.

## Step 2 (B): approaches follow the water rule (M; after step 1, same files)

Files: `road/Profile.java` (or a new `road/ApproachProfile.java`), `world/WorldPlanner.java`.
The exact reads belong here, not in the skeleton (ADR 0003 D4).

1. `Profile.approach(st, exact, pins)`: `build`'s wet-station detection and exact crossing
   measurement (WR1, WR2: runs under `MIN_CROSSING_GAP` merge, length measured block by block,
   `MAX_CROSSING` 20), the deck as `approachDeck` (mean, then pins, then `limit`), each crossing flat
   at the higher bank's deck and pinned, `span` flags for the raster (crossing columns become DECK
   whatever the ground). A wet station 0 (an exit on a shore) is a crossing that starts at the
   street's pin, not a failure. Fail on a run over 20 or a wet port end.
2. On failure, `WorldPlanner` reroutes the approach: a `LatticePath` search on the 32-block lattice
   from the exit to the port's lattice point, cost as `LinkExits.approach` (water and peaks forbidden
   at nodes through `probes.coarse()`, the dy limit), plus the lattice nodes round each failing
   station banned, as `RoadNet.buildRoute` bans (at most `RoadNet.MAX_REROUTES`). Bounds: the box of
   exit and port grown by 128. The first attempt is always the skeleton's own polyline, so dry
   approaches do not change.
3. When every attempt fails, keep the attempt with the fewest wet blocks, still with its crossings'
   decks, and count it (`Approach.wetFailures`); never fill water (WR3 holds in `RoadSurface`).
4. ROADS1 (7.10) gives graph routes one deck block per route (`RoadNet.Col.deck`); an approach's
   crossing should take its link route's material once ROADS1 is merged (follow-up line if not).

Test (`ApproachWaterTest`):
- corpus: no drawn approach column over water outside a crossing (`wetLow == 0`, and every wet drawn
  column has `crossing()` true); every crossing at most 20 blocks; `wetFailures == 0`.
  Playtest `k2_1_0` now has 20, world-12345 `k2_1_2` 279.
- a synthetic terrain unit test: a straight approach over a 10-block river gets one flat crossing at
  bank height; over a 30-block river it is rerouted (or fails and is counted).
- mutation: skip the dry check, the corpus assertion fails.

## Step 3 (C): exits only in tiles the settlement plans (S; after DEADMAIN merges)

Files: `settlement/LinkExits.java` (`exitFor`), `settlement/DefaultProgram.java` (`pick`),
`settlement/SkeletonContext.java`, `check/SettlementChecks.java`, maybe `tiers/CityProgram.java`.
**6.16 DEADMAIN (`claude/p6-deadmain`) changes these same methods** (shore-free `solid` exits): do
this step on top of it once it is merged into `claude/run-phase6`.

1. `SkeletonContext.tileOwned(x, z)`: `Districts.owns(view, site, grid, siteHubTile, ix, iz)` for
   the column's tile, memoised per tile; `siteHubTile` as `Skeletons.districts` computes it (the hub's
   tile when the cell holds the centre, else null). The context needs the `RegionView`.
2. `exitFor` and `pick` skip columns whose tile is not owned (a first pass; fall back to today's
   choice when no column qualifies, so a settlement never loses a required exit).
3. New rule `exit-unplanned` in `SettlementChecks`: an EXIT gate whose tile is not among the
   settlement's district ids; wire it where the other skeleton rules run (city-check, `--check`).
   Advisory count `main-unplanned`: main-road columns in tiles no one plans (a gap mid-road; report,
   do not fix here).

Test: `JoinWorld.joins` `exitTilePlanned` true for every corpus approach (playtest `k2_1_1` and
world-12345 `k2_1_1` are UNPLANNED now); `exit-unplanned` 0 on `city-check --seeds 1 --sites
gates:fixtures/terrain/gate-sites.tsv --scenes auto:2` and on `GateTerrainTest`; a unit test where a
cell's best exit column lies in a tile whose centre cell belongs to another settlement.

## Step 4 (D): keep lots off the approaches (M; parallel with steps 1-2)

Files: `settlement/Districts.java` (lot placement), `settlement/LinkExits.java` (`approach` cost),
a shared helper for the approach geometry.

1. Extract the approach's node list (the skeleton polyline plus the exact port, as
   `WorldPlanner.approachRoads` builds it now, lines ~446-453) into one public static helper, e.g.
   `road/Approaches.nodes(Road approach, RoadPort port)`, used by both `WorldPlanner` and `Districts`,
   so both see the same curve. Step 1 must not move those lines (merge order: either).
2. `Districts`: the columns of every approach of the settlement (`RoadNet.raster(Curve.stations(nodes),
   zeros, no spans, width + 2)`, geometry only, no exact reads) are reserved in the tile: no lot,
   building or plaza over them. Find where lots are cut and copy how an existing
   obstacle (a street footprint, a wall, a quay) keeps lots off; not checked yet which of those exists. Streets may cross them (a junction).
3. `LinkExits.approach`: add a cost for lattice nodes in the urban or open mask other than the start
   (a penalty, not a ban, so tight towns still get an approach), so approaches leave town instead of
   looping back through it (`k4_4_0`).
4. Generator change: gallery and city-gallery look (a town with an approach), architect review.

Test (`ApproachLotsTest`): corpus: no approach column inside a lot or building box (the district
mask may cover an approach column only with a street, deck or plaza ground box), and `gap == 0` for
every approach with a planned exit tile (playtest `k4_4_0` 15, `k4_5_0` 8 now, s64 data). Print the
lots removed per settlement; look at a city-gallery town before and after.

## Step 5: in-game check (S, heavy; after 1-4 merge)

An opt-in client game test (`FB_ROADJOIN=1`, like `FB_FLY`) on seed 1709797029209580947: teleport to
the hamlet gate (3616, 1440), the metropolis exits (3200, 1696) and wherever step 3 moved the second
one, plus John's 21.31.49 spot; screenshot each; read the world's top blocks along the planner's
approach and street columns there and assert a step of at most 1. Run through `scripts/evidence.sh`
under the heavy lock. Alternatively run `scripts/server-only-test.sh --seed 1709797029209580947`,
then `scripts/McaTop.java` on `mod/build/run/serverOnly/world` around those spots. Put the screenshots
in `docs/REVIEW.md` for John, with a request for the coordinates of 21.35.56 and 21.36.39.

## Parallel tracks

| Track | Steps | Starts | Files |
|---|---|---|---|
| alpha | 1, then 2 (one coder, or two in sequence) | now | `WorldPlanner.approachRoads`, `Profile`, `Street`/`Briefs` |
| beta | 3 | after DEADMAIN is merged | `LinkExits.exitFor`, `DefaultProgram.pick`, `SkeletonContext`, `SettlementChecks` |
| gamma | 4 | now | `Districts` (lots), `LinkExits.approach`, new approach-nodes helper (touches 3 lines of `WorldPlanner.approachRoads`) |
| final | 5 | after alpha, beta, gamma | gametest, evidence |

alpha and gamma both touch `WorldPlanner.approachRoads` (gamma replaces the node-building lines with
the helper call; alpha changes the deck call below them): a small textual merge. beta and gamma both
touch `LinkExits` (different methods). Every step's test reads `JoinWorld`, so they do not depend on
each other's code, only on the fixtures committed here.

# Overlap with neighbouring items

- **ROADS1 (`claude/p7-roads1`, 7.10):** adds route materials to `RoadNet.Col` and a 5-arg `raster`;
  approaches keep the 4-arg `raster` (biome pick). Step 2's crossing decks should use the link
  route's deck block after ROADS1 lands. No conflict in the ROADJOIN files except `RoadSurface`
  (ROADS1 adds `Plan.block`; ROADJOIN does not edit `RoadSurface`).
- **6.16 DEADMAIN (`claude/p6-deadmain`):** moves exits and cell gates off shores (`solid`) and adds
  `main-dead-end` / `bridge-dead-end` rules. It removes some wet-exit cases of B (the metropolis exit
  3200,1696 next to a deck stub) but not approaches crossing water further out, and it does not touch
  heights, tile ownership or lots. Step 3 edits the same methods: sequence after it.
- **6.17 ROADCLEAR:** different cause (above); no shared fix.
- **VILLROAD (`claude/p6-villroad`):** joins roads to vanilla villages in `RoadFeature`/`VillageRoads`;
  no shared files with steps 1-4. **Its branch head (7be31895) has a bug that disables every road:**
  `RoadFeature.draw` sets `boolean any = false;` and then `if (!any) return false;` with the loop that
  set it removed, so no chunk draws a road. Flagged to the coordinator.

# Design questions (for the architect)

1. A street that sits in a cut at its gate (hamlet s3_1 g0: 5 blocks below the ground, a known
   "more than 4 blocks of cut" note): should the link road follow it down (a sunken lane with
   terraced sides, the step 1 default) or should the street climb to meet the ground at the gate?
2. The approach's run through town: a corridor with no lots (the step 4 default) or a street with
   frontage (houses along the road out of town, the district drawing it)?
3. Approach crossings (step 2): a flat deck at the higher bank (WR2) can sit 1 block over the water;
   is that acceptable for a small stream, and which deck block (style planks, or the route's material
   per DS-44)?

# Risks and open questions

- **Infeasible pins** (step 1): street level and port deck too far apart for 1 in 4 over the
  approach's length. Assumption: rare (approaches are 64+ blocks, 16+ blocks of rise); counted and
  reported, not hidden. If the corpus shows any, the fallback is a steeper grade on the approach's
  first 16 blocks (still at most 1 in 2) rather than a step.
- **Plan cost** (step 1): an approach now plans its exit tile. Those tiles are planned anyway when
  their chunks generate; the order of work changes, not the total. Check `scripts/perf.sh
  --planner-only` is within budget if the coordinator wants a number.
- **Exit moves** (step 3) change skeletons, so snapshot fixtures and city-gallery output change.
  Expected; regenerate and look.
- **s64-only rows** (the s4_x settlements) are logic reproductions, not John's spots (the saved world
  at s4_1's planner exit holds no road). The crop rows match the saved world.
- **The two berm screenshots** stay unconfirmed until John gives coordinates or step 5 revisits.
