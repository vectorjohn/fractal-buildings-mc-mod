# Plan: dense city cores are filled (METROFILL)

Status: plan, not started (2026-10-26). Author: lead-coder. ROADMAP item
**7.20 METROFILL**. Rule: "Dense cores are filled" (approved by John
2026-10-26; an assistant is filing it under the next DS number, so this plan
refers to it by name). Design input: the architect's answer
[metro-sparse-architect.md](../review/reports/metro-sparse-architect.md)
(measurements, causes, targets, a 5-point spec, three `city-check` checks)
and its prototype scripts in
[metro-sparse-architect/](../review/reports/metro-sparse-architect/fill-metrics.js).
John on diagonals (2026-10-26): first make the grid blocks next to a diagonal
avenue fill up to it (flatiron corners, pocket-park triangles); removing
diagonals is a separate step behind a switch, decided by him once he has seen
the density results ("It's minecraft, we don't do diagonals!").

# What happens today (checked 2026-10-26, base bceeefb7)

I re-ran the architect's measurement on `gate-metropolis-mixed`
(`city --seed 987654321 --terrain recorded:fixtures/terrain/gate-metropolis-mixed.json.gz --site 2304,-1920,metropolis,1126 --districts`,
18.7 s, 1523 built lots) and got the same numbers as the architect: core
(r < 0.25 R) is 18.6 % roads, 25.1 % lots and 51.8 % empty; 45.7 % of the core
street edge has a lot behind it; arterial plus main is 47 % of street length
(arterial 20.6 km of 59 km); 10.6 % of road-to-road gaps are empty slivers of
12 or less, the top pairs being main+street (150), arterial+main (96) and
lane+main (55); 184 dead ends; **1 `block` lot**.

Code facts the steps depend on (all in `planner-core`, package
`core.settlement` unless noted):

- `Districts.Build.run()` order: landmarks and plazas, wall lots, skeleton
  streets, border streets, grid streets, `finishBase`, `subdivide`, `ports`,
  `plazaLanes`, `hallLots`, `quays`, `frontageLanes`, `avenueLanes`, then
  `markLotEligible`, `landmarkLots`, **`blockLots`**, **`frontageLots`**,
  `landmarkFallback`, `avenueGreens`, `assemble`. Lots are axis-aligned
  rectangles; `paintRect(r, LOT)` marks the raster; `free(x, z)` tests it.
- `frontageLots` → `placeAlong(Frame, Raw)` per side of each straight axis run.
  `Frame.depth(u)` counts free cells away from the street up to `MAX_DEPTH`
  (44). `tryLot` picks a type with `ClassTable.pick`, `dims` takes one of the
  type's `testLots` (weighted to the first) plus 0-4 blocks of depth: lots end
  up at the type's minimum depth (house 9, shop 17, high-rise 23 median).
  After a lot, `placeAlong` leaves a 2-4 block gap one time in four
  (`"lot-gap"` hash); a failed `tryLot` skips 4 columns.
- `blockLots()` only tries rectangles from `StreetRules.gridLines` intervals
  and requires `ringIsStreet` (every column of the ring round the block is
  street). I instrumented it on the run above (not committed): of 501
  candidate blocks, 485 failed `ringIsStreet` (118 with no street on the ring
  at all, about 200 with one or two sides missing, **53 with under 20 % of the
  ring missing**), 6 failed on size, and only 5 reached the class-table draw.
  The class table is not the bottleneck (block weight 40-45 % in rows 50-85).
- Diagonal avenues: `CityProgram.directions` (diagonals on
  `DIAGONAL_PCT` = 75 % of cities), `CityProgram.octilinear` (diagonal +
  axis legs), A* fallback `DefaultProgram.route` → `LatticePath.find`, which is
  8-connected. In district detail, `avenueLanes` puts cut lanes off diagonal
  runs **in grid land** (it skips organic frontage land), `avenueGreens` puts
  at most 2 greens of 16-20 per tile. `frontageLots` skips diagonal runs.
- Arterials: `Arterials.is(segment)` (probability 1 at fabric density 80;
  metropolis outer ring always). In lattice tiers `StreetRules.borderKind`
  reads `RoadNetwork.kind(s)`, which marks a present segment arterial when
  `Arterials.is` says so, else a plain 5-wide border street. CityProgram's
  crossing squares need two arterial segments at the crossing.
- Grid lines: `StreetRules.gridLines` (pitch 62-100 from the tile line),
  drawn per 8-block edge where `gridSideEdgeOk` holds. Pointwise.
- Quays: `Build.quayOk` (waterfront quarter, low dry bank, clear of water),
  contour runs shared by every tile that sees them (`quayWholes`), plus one
  `quayLink` lane each. Quays and border/grid streets must stay pointwise:
  both tiles at a border list the same street (`tile-border-agree`).
- Generators: the envelope is the lot minus the type's margins, placed at the
  front margin (`InstancePassV1` lines 46-56). House massing caps the main block
  at 11 deep and wings at 12, placed near the front, so a deep house lot already
  gets a rear garden. **Shop massing** draws the main block `range(min(D, 11), D)`
  deep, so a 44-deep shop lot gives a 42-deep box; **high-rise** podiums take
  nearly the whole envelope (`D - 0..3`). So deep lots need a depth cap the
  generator honours, or shops and podiums balloon.
- `SpecPolicyV1.spec` and `Briefs.fillLot` measure relief over the **whole
  lot** (type rejection, `empty:steep`): a deeper lot on a slope flips types.
- `city-check` (`cli/CityCheckCommand`) already has report-only "design
  warnings" (`HighriseChecks`) and a per-site terrain table built by
  `TerrainStats.of(city, terrain)`; the fill section copies both shapes.

# Goal

In city and metropolis areas of density 70 and up, the land between roads is
filled: lots reach the middle of each block, the street edge is built without
planned gaps, diagonal avenues have built or planted edges, and the road
layers that add paving without frontage are thinned. Targets (architect's
numbers, dry land in the disk): core roads 22-30 %, lots 55-65 %,
plazas/parks 5-10 %, empty ≤ 10 %, frontage built ≥ 85 %; arterial plus main
≤ 25 % of street length; empty slivers ≤ 2 % of gaps. The three checks
(`core-fill`, `road-sliver`, `road-hierarchy`) start as report-only warnings
and become violations in the last step, once the 9 gate windows meet them.
Towns, villages and hamlets do not change (every fill rule is gated on
`tier.atLeast(CITY)`), and DS-18's loose outer ring stays loose.

# Approach and key choices

1. **Measure first** (M0), in Java, from the plan alone: the architect's
   rasters and gap scan ported into a core class, zones by **density band**
   (what the rule says) as well as by ring (to compare with the architect's
   tables). Every later step reports its before/after table.
2. **Depth after frontage.** Lots are still packed along the street first, at
   the type's own size; a second pass grows each lot backwards to the block
   middle. Rejected: picking a deep lot in `dims` directly, because corner lots
   would then run 44 deep along the side street and break that street's
   frontage, and the first street processed would take the whole block.
3. **The parcel is deep, the building is not.** A lot gains `depthCap` (rows
   from the front lot line the building may use); the rest is the lot's rear
   yard. The spec policy shrinks the envelope to it and measures relief over it.
   Rejected: a separate "yard" plan element (a second rectangle per lot, more
   JSON, viewer and mod surface, and nothing for the generator to say about
   it); and letting generators decide alone (shops and podiums would balloon,
   see above). Above density 70 the cap is larger and the brief asks for a
   rear wing, so buildings, not only yards, take depth.
4. **Thin roads pointwise.** Every road rule stays a function of seed and
   position (tile-border agreement). Arterials keep a fixed parity of tile
   lines; grid lines and lattice border streets yield to a parallel skeleton
   road; quays need room for a lot row inland. Only lane pruning works per tile,
   and only on lanes that do not reach the tile border band.
5. **Diagonals: fill first, remove behind a switch.** Lots on the grid streets
   reach the avenue (rectangles, so a staircase), the leftover triangles become
   small plazas with a diagonal edge (a new `Plaza.tri` field). Removing
   diagonals (axis-only avenues and routes) is a program flag, off by default,
   compared side by side for John.
6. **Whole blocks from what is really there.** Find blocks as flood-filled
   free regions bounded by streets (any street, not only grid intervals) and
   accept a ring that is street on almost all of its length.

# Steps

`[P]` = can start at once, in parallel with the other `[P]` steps. "After X"
means X merged into the run branch. Every step: run M0's measurement before
and after on the 9 gate windows (command in M0), put the two tables for both
metropolises and `gate-temperate-city` in its log, and report the planner time
(`city-check` ms) of the two metropolises. Every step that changes plans
regenerates the generated fixtures it touches (list in "Goldens and fixtures")
in its own branch; on a merge conflict in a generated file, regenerate, never
hand-merge.

## M0 [P] Fill metrics, the `fill` section and the three checks (report only)

- New `core/check/FillStats.java`:
  `static FillStats of(CityPlanner.City city, DensityField den, CoarseTerrain water)`
  (check the exact accessors: `src.density()`, `den.terrain()`; water from the
  coarse terrain like the prototype, so numbers match it). One byte raster over
  the plan box: road (from `SettlementChecks.footprint(street)` of every
  district street), lot (`builds()` lots), steep lot (`empty:steep`), plaza.
  Measured over dry columns inside the site disk:
  - zones by **density band** (`den.at`: ≥ 70 core, 50-69 middle, < 50 edge)
    and by **ring** (0-0.25-0.5-0.75-1 R): land, road %, lot %, plaza %,
    steep %, empty %;
  - frontage: share of non-road columns 4-adjacent to a road that are lot or
    plaza, per zone and per road kind;
  - street centre-line length per kind, **clipped to each district's own tile
    rect** (`DistrictInfo` x, z, w, d), because each tile lists the streets
    of its 8-block margin too (the prototype double-counts these); arterial
    plus main share;
  - slivers: run the row and column gap scan over every row and column (the
    prototype samples every 4th), mark the cells of empty road-to-road runs of
    length ≤ 12; a 4-connected component of marked cells with a bounding-box
    long side ≥ 32 is one sliver; count, total area, and the share of empty
    gaps ≤ 12 among all gaps (the prototype's number);
  - dead ends (a street end whose end column has no other street's footprint
    within width/2 + 2), `block` lot count, median lot depth per type in the
    core.
  - `List<Violation> checks(Tier tier)`: `core-fill` (city or metropolis,
    core zone: empty > 15 % or frontage < 80 %), `road-sliver` (one per
    sliver, with its box), `road-hierarchy` (city or metropolis: arterial plus
    main > 30 % of street length). Numbers as constants in `FillStats`,
    javadoc pointing to the rule.
- `CityCheckCommand`: build `FillStats` per site after planning; add a `## Fill`
  section (one table per zone kind: site, land, road, lot, plaza, empty,
  frontage; then a line per site with arterial+main %, sliver count, dead
  ends, block lots); add the three checks to **warnings** (the
  `HighriseChecks` path, never failing). New option `--fill-json FILE`
  writing the stats of every site (sorted keys) for before/after diffs.
  With `--scenes N > 0`, add "roof cover %": columns under any building voxel
  in the scene districts over their dry land (what reads from the air; yards
  count as lots in the main table, not as roof).
- Baseline: `fractal-cli city-check --seeds 987654321 --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:0 --out docs/review/metrofill/fill-before.md --fill-json docs/review/metrofill/fill-before.json`
  (check how `gates:` treats `--seeds`; the TSV carries seeds). Commit both
  files. If the 9 sites take more than ~5 minutes, say so.
- Tests (`FillStatsTest`, hand-built `DistrictPlan`s, no terrain): two
  parallel streets 10 apart and 60 long with nothing between → one
  `road-sliver`; a lot between them → none; 10 apart but only 20 long → none.
  A street with lots along half of one side → frontage 25 % for the two sides.
  A street listed by two tiles (margin overlap) is counted once in length.
  Cross-check (`FillStatsParityTest` or a log entry): on
  `viewer/fixtures/settlement/districts/synth-city` the ring table is within
  1 point of `node fill-metrics.js` (run it once, write the numbers into the
  test with a comment). Mutation: drop the tile clip → the length test fails.
- Files: new class and tests, `CityCheckCommand`, `CityCheckTest` (section
  present, warnings listed, exit code unchanged). No planner change.

## G0 [P after SHOPLOW S4 merges] Deep lots in the generators: `depthCap`

S4 adds `LotBrief.square` / `Brief.square`; follow its pattern and start from a
run branch that has it (it touches `LotBrief`, `Brief`, `DistrictJson`,
`ShopMassing`, `ir.ts`).

- `LotBrief.depthCap` and `Brief.depthCap`, int, **0 = none** (whole lot, as
  today). Meaning: the building may use only the first `depthCap` rows of the
  lot from its front edge, front margin included. Written in `DistrictJson`
  only when > 0 (old fixtures stay byte-identical), read back optional; viewer
  `ir.ts` type and plan view: draw a thin line at the cap inside the lot.
- `SpecPolicyV1.spec`: `ld = min(ld, depthCap)` when set, before the envelope
  and the too-small test; relief (`reliefOk`, `maxFloor`, `minFloor`) over the
  capped part only. `Briefs.fillLot` and `Briefs.buildable`: relief and size
  over the capped part (factor a `buildRect(LotBrief)` helper; one helper for
  both so planner and generator agree). `GeneratorV1` setback retries keep the
  cap. The lot in the scene IR stays the full parcel (in-lot holds in the yard).
- No separate "deep" flag: a shop is deep when `depthCap >= ShopType.DEEP_FROM`
  (24, agent number). Shop massing when deep: rear wing chance 2/5 → 4/5, wing depth up to
  `min(D - z0 - md - 2, 14)` (leave ≥ 2 rows behind it inside the envelope as
  the back margin; the yard is behind the envelope anyway). High-rise: no
  change (the cap bounds the podium). House: no change (front-placed, wings to
  12). Workshop: check `WorkshopMassing` on a {16, 44} lot capped at 20; cap
  the yard only if it balloons.
- Tests: `DeepLotTest` (V1PropertyTest style) for house, shop, workshop,
  high-rise, tower on lots {testLot w, 44} with `depthCap` = the test lot
  depth, 20 seeds each: invariants pass; every building voxel lies in rows
  [0, depthCap) of the lot (plus the air strip); a shop with `depthCap` 30 has
  a rear wing in at least half the seeds. `depthCap` 0 gives byte-identical
  output (`GoldenDigestV1Test` unchanged). A lot with a 12-block cliff in its
  rear rows: `SpecPolicyV1.reliefOk` true with the cap, false without; same for
  `Briefs.buildable`. `DistrictJson` round trip with and without the field.
  Mutation: ignore the cap in `SpecPolicyV1` → the voxel-row test fails.
- Files: `LotBrief`, `Brief`, `DistrictJson`, `SpecPolicyV1`, `Briefs`,
  `GeneratorV1`, `ShopMassing`, `viewer/src/ir.ts` and the plan renderer,
  tests. No planner placement change; no fixture change.

## DX [P] Diagonal switch, inert by default

- `TierProgram`: `default boolean diagonals() { return true; }`.
  `CityProgram(boolean diagonals)` (default constructor true) overrides it.
  `TierPrograms.of(Tier, PlanOptions)` with a new record
  `PlanOptions(boolean diagonals)`, `PlanOptions.DEFAULT` = diagonals on;
  `TierPrograms.of(Tier)` keeps calling it with the default (the mod and
  every existing caller unchanged). No global or system property.
- With `diagonals() == false` (city and metropolis only):
  `CityProgram.directions` uses the axis directions (`start` even);
  `octilinear` offers only axis routes (straight, or two axis legs: x then z,
  z then x); `DefaultProgram.route`'s A* passes a new
  `LatticePath.find(..., boolean diagonal)` overload, 4-neighbour expansion and
  Manhattan heuristic when false (keep the old signature delegating with
  true). District detail `frontageLanes`, `quayLane`, port and plaza lanes in
  those tiers also pass false (check each `LatticePath.find` call in
  `Districts`). Quays keep following the shore (natural edges); region roads
  (ADR 0004) are untouched.
- CLI: `--diagonals on|off` on `city`, `city-check` and `sample-city` (builds
  `SkeletonSource.Direct` with `TierPrograms.of(tier, opts)`); viewer
  `city-presets/*.json` key `"diagonals": "off"` passed through by the city
  scripts (`viewer/scripts/`, check the names).
- Tests: with the default, all goldens and fixtures byte-identical (the step
  is inert). With off, on the synthetic city (`SkeletonsTest.syntheticCase`)
  and `d3-city-avenues`' window: no street of any city tile has a diagonal run
  (`Districts.diagonalRuns` empty), every settlement rule passes
  (`CityPlanner.check` empty), `SettlementDeterminismTest`-style any-order
  equality holds. `LatticePathTest`: 4-neighbour path between two points has no
  diagonal step and the same length as Manhattan on flat ground.
- Files: `TierProgram`, `TierPrograms`, new `PlanOptions`, `CityProgram`,
  `DefaultProgram`, `LatticePath`, `Districts` (lane calls), CLI commands,
  viewer city scripts, tests.

## R1 [P] Arterials on every second tile line

- `Arterials.compute`: in a city or metropolis, a segment is arterial only when
  its line has the site's parity: `Math.floorMod(s.line() + phase(axis), 2) == 0`
  with `phase(axis) = Hash64.below(Hash64.hash(seed, "arterial-phase", axis), 2)`:
  world-wide per axis, so every tile and every settlement (a border between
  two settlements' tiles) agrees without talking. Metropolis outer ring
  unchanged (always arterial). The other segments stay present lattice
  segments and are drawn as plain border streets (`RoadNetwork` already does
  that for non-arterial present segments). Below density 80 keep the existing
  probability on top of the parity.
- Crossing squares need two arterials at a crossing: they now occur only where
  two arterial lines cross. Accept the drop and report the square count before
  and after (architect question A9 below if it halves).
- Tests (`ArterialsTest` or `CityRoadsTest`): on the synthetic city, every
  arterial segment off the outer ring is on a line of the parity on its axis;
  no two parallel arterial lines are adjacent; `cityRoadsConnected` and
  `CityPlanner.check` still empty. Metric: `road-hierarchy` share drops on the
  gates (log it). Mutation: remove the parity test → the first assertion fails.
- Files: `Arterials`, tests, regenerated settlement goldens and fixtures
  (squares move).

## R2 (after M0) Parallel roads yield

Pointwise rules, measured by M0's sliver count and pair table:

- `RoadNetwork.build` keeps the skeleton main-road raster it already paints
  (`mains`), split by run direction: `mainsX` (runs along x), `mainsZ`,
  `mainsDiag`. Query `boolean parallelMain(int axis, int pos, int from, int to, int reach)`:
  at least half the 8-block nodes of the stretch have a main-road column of the
  same direction within `reach` across the line (not counting columns where a
  main crosses it).
- **Grid lines**: `StreetRules.gridSideEdgeOk` also returns false when
  `network.parallelMain(axis, pos, a, b, GRID_YIELD)` (an 8-block edge is dropped).
  `GRID_YIELD` = half the minimum lot-row room: start at 16 centre to centre
  plus half widths (`16 + 5/2 + mainWidth/2`); try 16/24/30 and keep the value
  with the fewest slivers that does not drop the core lot share.
- **Lattice border streets**: `RoadNetwork.eligible` leaves out a plain
  (non-arterial) segment with `parallelMain` over at least half its length.
  Connectivity holds because mains are live roots of the flood.
- **Arterial beside a main road** (96 slivers on the mixed metropolis): the
  architect says keep the higher-class road, i.e. the arterial. Dropping a
  stretch of a skeleton main road splits a route that carries gates and region
  links, so this plan does the opposite: an arterial segment with
  `parallelMain` over at least half its length is left out of the lattice
  (the main road takes its role). Architect question A3. Optional R2b if the
  architect insists: in `CityProgram`/`DefaultProgram.route` add a cost to
  lattice steps that run parallel 16 off a tile line (so main roads either sit
  on the line or keep away), measured by the same table.
- **Lanes**: `laneRaw` callers (`frontageLanes`, `plazaLanes`, `quayLink`,
  ports, `cutLane`) reject a lane whose footprint runs parallel to another
  street within 12 for more than 16 blocks (a per-tile test on the raster:
  walk the lane, count columns whose side cell at distance ≤ 12 + width/2 is
  street of a run in the same direction).
- Tests: `StreetRulesTest`: a synthetic source with a main road along z at
  x = 112 and a grid line at x = 124: the grid edges beside it are dropped;
  grid line at x = 176 kept; a main crossing the grid line at right angles
  does not drop it. `RoadLatticeTest`: a border street with a parallel main
  12 away is absent and `roads-connected` holds. On the synthetic city and the
  two metropolises (city-check): no new violation, sliver count down (log
  the pair table before/after). Mutation: `GRID_YIELD` 0 → the first test fails.
- Files: `RoadNetwork`, `StreetRules`, `Districts` (lane test), tests,
  regenerated fixtures.

## R3 (after M0) Quays only where lots face them; lanes only where they serve

- **Quays**: `Build.quayOk` (pointwise) also requires room for a lot row
  inland: no skeleton road, lattice border street or grid street within
  `QUAY_ROOM = Quays.WIDTH / 2 + 16` on the land side of the column. Implement
  as `StreetRules.baseStreetNear(exact, x, z, r)` from the pointwise pieces
  (network main raster from R2, or paint mains here if R2 is not merged yet;
  `borderKind` for tile lines within r; `gridLines` + `gridSideEdgeOk` for grid
  lines within r). The land side is the side away from the nearest water
  column. Quay runs still need `Quays.MIN_LEN`.
- **Lanes that serve nothing** (per tile, after `frontageLots`): in city and
  metropolis tiles, a lane (`RoadKind.LANE`, not fixed) that no lot fronts
  (`LotBrief` front street = it), that no plaza or quay depends on (its own
  `quayLink`/`plazaLanes` lanes are kept while their target exists) and whose
  footprint stays out of the tile's border band (`MARGIN` + 3: ports must stay
  identical across the border) is removed, its cells un-painted, and
  `frontageLots` runs once more on the streets next to the freed cells.
  `roads-connected` must still hold (removing a lane with no lot cannot cut
  anything off unless a plaza hangs on it, which is excluded).
- Tests: `QuaysTest`: a bank with a grid street 10 inland → no quay; 30 inland
  → quay; `QuayOrderTest` still passes. A district test over the synthetic
  city: after planning, every lane in a city tile fronts at least one lot,
  links a plaza or quay, or touches the border band. City-check on the gates:
  quay dead ends and quay frontage (M0 per-kind frontage) logged before/after.
- Files: `Districts` (`quayOk`, new `pruneLanes` called from `run`),
  `StreetRules`, tests, regenerated fixtures.

## W1 (after M0) Whole-block lots from the real blocks

- `blockLots()` candidates: flood the free cells of the tile raster (not
  street, plaza, reserved, wet, lot) into 4-connected regions; a region whose
  bounding box is 52 to 100 on both sides, lies inside the tile (`tr`) and
  covers at least 90 % of its box gives the candidate rectangle (the box
  shrunk to its largest free rectangle; a simple shrink-from-each-side loop is
  enough). Keep the old grid-interval candidates first (byte-identical where
  they already worked), then the flooded ones, deduplicated.
- `ringIsStreet` → `ringStreetShare(r)`: accept when at least 90 % of the ring
  is street and each side is at least 75 % street (both constants named; agent
  numbers). The front picked by hash must be a street-side column (it is today
  via `streetAt`); keep the `lotOk` test.
- Density: keep `BLOCK_DENSITY` 50 (ROADS3 chose it) and the class table as
  is; report how many core and middle blocks become `block` lots. If the core
  share stays below a quarter, raise the block weight in the ≥ 70 rows in this
  step (architect: "up to about a third of core blocks").
- Tests: on the synthetic city (`synth-city` fixture site): at least N block
  lots in density ≥ 50 tiles, N set from the first run with a comment (today
  the fixture has a few); each block lot's ring is ≥ 90 % street and each side
  ≥ 75 %; `lot-frontage`, `lots-disjoint`, `lot-in-tile` hold. Mutation: share
  back to 100 % → the count assertion fails.
- Files: `Districts` (`blockLots`, `ringStreetShare`), tests, regenerated
  fixtures.

## L1 (after G0) Lots fill to the block middle; no frontage gaps (rules 1 and 2)

All of this only in city and metropolis tiles, keyed on the lot's own density
(`LotBrief.density`, the `BuiltEdge`-capped value, so the built edge stays
loose per DS-18).

- **No planned gaps** (`placeAlong`): the `"lot-gap"` probability fades from
  1 in 4 at density ≤ 30 to 0 at ≥ 50 (linear; keep the same hash and draw so
  below 30 nothing changes). At density ≥ 50 a failed `tryLot` steps 1 column,
  not 4; when it still leaves columns unused between two lots (or at the run
  end) the previous lot of this frame widens over them if its depth fits there
  (replace the `LotBrief` in `lots` at the same index and id, repaint), as
  `dims` already does for a last strip under `MIN_W`.
- **Deepen pass**, new `deepenLots()` called in `run()` right after
  `frontageLots(finals)` (before `landmarkFallback`): for every frontage lot
  of density ≥ 50 in creation order:
  - target depth: scan from the lot's back edge along its depth direction, at
    the middle column of its width, for the next street cell (up to
    `2 * MAX_DEPTH + 11`). Found at gap `g` (front lot line to the far street's
    edge): target `min(MAX_DEPTH, g / 2)`; the lot whose street id sorts
    first takes `ceil`, so two back-to-back rows meet with no strip. If the
    far street is a diagonal run (DG1 uses this), target `min(MAX_DEPTH, g)`.
    No street found (open land behind): target `min(MAX_DEPTH, d + 8)` (a
    garden).
  - grow the lot one row at a time while the new row is free over the lot's
    whole width, dry, and the lot stays `Briefs.buildable` for its type over its
    build part (relief is over the capped part since G0, so this rarely stops
    it). Repaint, replace in `lots` at the same index.
  - set `depthCap`: the depth the lot had before deepening plus, at density
    ≥ 70, room for a rear wing: house and workshop `d0`; shop
    `min(d, max(d0, 30))`; high-rise `min(d, 30)`; tower `d0`; leave
    `REAR_YARD_MIN = 4` rows behind the cap (agent numbers, architect question
    A1). A lot that did not grow keeps `depthCap` 0.
- Corner lots: frontage first means the side street's lots exist before the
  deepen pass, so a corner lot cannot run down the side street's frontage. Its
  yard can still face the side street over its depth; Y1 walls it.
- Tests (`FillDepthTest`, synthetic city and `fly-12345-coast-city` from
  `CityFillTest.city`): among density ≥ 50 frontage lots whose middle column has
  a far street within 2 × 44 + 11, at least 90 % reach `g / 2 - 2`; the empty
  strip between two back-to-back lots (scan from each lot's back edge to the
  next lot or street) is ≤ 8 for at least 95 % of lots; no `lot-gap` at
  density ≥ 50 (count it in `notes`); every grown lot has `0 < depthCap ≤ d -
  REAR_YARD_MIN` or `depthCap == d` when it did not need a yard. Control:
  `SmallTiersTest` and the river-town district fixture (a town) are
  byte-identical. Determinism: `tilesInAnyOrderFromFreshSourcesGiveTheSameJson`
  over the synthetic city. Mutation: fill threshold 101 → the depth test fails.
- Look: city gallery plans of both metropolises before/after (`plan.png`);
  one district scene street frame per metropolis (rear wings visible from a
  cross street).
- Files: `Districts` (`placeAlong`, `tryLot`/`dims` only for the step rule,
  new `deepenLots`), tests, regenerated fixtures and goldens.

## DG1 (after L1) Grid blocks fill up to the diagonal avenue

- `avenueLanes`: a cut lane is laid only where the free depth from the grid
  street to the avenue band at that point is more than `MAX_DEPTH + 16` (a
  triangle too deep for lots from the grid street alone); elsewhere none.
  Organic frontage land is unchanged (it never had cut lanes).
- Lots on the grid streets in blocks cut by the avenue: `placeAlong` already
  packs them; with L1's deepen pass the far street is the avenue, so they
  grow to the band (target `g`, see L1). Lots narrower than usual near the
  avenue are fine: `dims` takes the minimum depth over the lot width, so the
  last lots before the tip form a staircase down to `MIN_DEPTH_ANY`.
- Corner tip: where a grid street meets the avenue at 45 degrees, the cell
  triangle left after the last lot (legs under about 16) is left for DG2's
  pocket park. A real flatiron building (stepped diagonal facade) is DG3,
  optional, waiting on the architect (A5).
- Tests: update `AvenueDistrictsTest`
  (`diagonalAvenuesGetCutLanesWithLotsAndEveryRuleHolds` asserts cut lanes
  exist: it becomes "cut lanes only where the triangle is deeper than
  MAX_DEPTH + 16, and every rule holds"); new assertion on the same site: for
  lots fronting a grid street whose deepen scan met a diagonal run, the far
  corner is within 1 of the band. City-check: main (avenue) frontage share
  and lane length logged before/after on the cities with diagonals
  (`d3-city-avenues` preset window plus the gates that have them).
- Files: `Districts` (`avenueLanes`, `deepenLots` far-street kind), tests,
  regenerated fixtures.

## DG2a [P with DG1, after M0] Triangular plazas: `Plaza.tri`

- `Plaza` gains `String tri`: null (rectangle, as today) or one of
  `ne`, `nw`, `se`, `sw`: the plaza is the half of its square bounding box
  (`w == d` required) on that corner's side of the diagonal, diagonal cells
  included. `Plaza.covers(x, z)` (new) is the one test everyone uses.
- Through the stack: `DistrictJson`/`SettlementJson` write `tri` only when set;
  `Grading.plaza` (or its caller in `DistrictScenes.groundBoxes`) emits one
  box per row for a triangle; `SettlementChecks` (`lotsDisjoint`, plaza in
  tile, floating plazas, `cityRoads` paint) use `covers`; `Briefs.streetMask`
  and `frontY` use `covers`; FillStats uses it; viewer plan and 3D views draw
  the triangle; the mod takes plazas from the scene's ground boxes (check
  `WorldPlanner` ground boxes and `SpillClear`), so it should need no change,
  but test it.
- Tests: JSON round trip; ground boxes of a `tri` plaza cover exactly its
  cells (count n(n+1)/2); `lots-disjoint` passes for a lot in the other half of
  the square and fails for one overlapping the triangle; a viewer vitest on
  the triangle geometry; a server game test or `WorldPlanner` unit test that
  a district with a triangular plaza paves exactly its cells.
- Files: `Plaza`, JSON classes, `Grading`/`DistrictScenes`,
  `SettlementChecks`, `Briefs`, viewer plan/3D, tests. Uses a hand-built
  fixture plan, so it does not wait for DG1.

## DG2b (after DG1 and DG2a) Pocket parks and verges along the avenue

- New `avenueWedges(finals)` after `deepenLots`, replacing the 2-per-tile cap
  of `avenueGreens` (keep `avenueGreens` for squares ≥ 16 left over): along
  each diagonal run of a main road in a city tile, the free cells between the
  band and the lots (the staircase notches, and the triangle at a tip) are cut
  into the largest `tri` plazas that fit (legs 3 to 20), each flush with the
  band; kind `green` for legs ≥ 8 (pocket park), `verge` below (a planted
  strip; check the plaza kinds the viewer and mod know and add `verge` to
  both if needed; PLAZAS dresses them later). Graded with `Grading.plaza`.
- Tests: on `d3-city-avenues`, along each diagonal run in a density ≥ 50
  tile at least 80 % of the band's edge cells are next to a lot or plaza (the
  M0 per-kind frontage of `main` on that site goes from ~30 % to ≥ 80 %);
  every rule holds; any-order determinism.
- Files: `Districts`, tests, regenerated fixtures.

## Y1 (after G0 and L1; architect answer A2 first) Yards look like yards

- In the generator's site pass (`InstancePassV1`, where front steps and rails
  are placed; read SHOPLOW S1/S2 changes first, they own the slab and cut code):
  for a lot with `depthCap > 0`, the yard (rows ≥ depthCap, and the side strips
  beside the building within the lot) gets a boundary on its back and side lot
  lines and on any yard side that faces a street: style-dependent per the
  architect's answer (garden wall, hedge `leaves`, fence), one block high on
  the ground (`floorY + 1`, stepping with the terrain, never floating), with a
  gate on the street side if the yard faces one. Ground cover inside left as
  terrain (grass) at density 50-69; at ≥ 70 a paved service court
  (style's `paving` role) if the architect says so.
- Tests: invariants (`no-floating`, `in-lot`) over `DeepLotTest`'s cases; a
  yard boundary cell exists on every yard lot line; no boundary cell in rows
  < depthCap; viewer screenshot of one deep-lot street (open it).
- Files: `InstancePassV1` or a new `YardPass`, style role if needed, tests.

## DX2 (after L1, W1, R1-R3, DG1-DG2b) Diagonals: the comparison for John

- Plan the 2 metropolises, `gate-temperate-city` and `d3-city-avenues` twice:
  `--diagonals on` and `off` (city gallery: two presets per city, e.g.
  `gate-metropolis-mixed` and `gate-metropolis-mixed-nodiag`), with M0's fill
  table for both. Put the paired `plan.png` and overview frames in
  `docs/review/metrofill/` and a REVIEW.md entry asking John to choose.
- If John chooses off: flip `PlanOptions.DEFAULT` (one line), regenerate
  goldens and fixtures, and the diagonal-only code (`avenueLanes`,
  `avenueGreens`, `avenueWedges`, `Plaza.tri` users) stays for the `on` switch
  (cheap to keep; delete later if John wants it gone).
- Files: city presets, review folder, REVIEW.md. No code change unless John
  has chosen.

## F (last) Checks become violations; goldens; perf; reviews

- When the 9 gates meet the checks (city and metropolis sites only), move
  `core-fill`, `road-sliver` and `road-hierarchy` from warnings to problems in
  `CityCheckCommand` and add them to `CityPlanner.check` if cheap enough
  (FillStats over a metropolis must stay under ~1 s; else city-check only).
  A gate that cannot meet a check: record why (water, slope) and use
  `--ignore` for that rule in the scripted gate run, or adjust the check's
  zone (say which) and ask the architect.
- Final `fill-after.md` / `.json` next to the baseline; the log carries the
  before/after table for all 9 gates.
- Perf (heavy lock; `scripts/evidence.sh metrofill scripts/perf.sh`): compare
  `district.cold.median.ms` (409 ms before, budget 300, ci 1500),
  `district.cold.p95.ms`, `warm.chunk.ratio`, `warm.postProcess.mean.ms` and
  `region.growth.ratio` with the last numbers in
  [perf.md](../review/phase6/perf/perf.md); planner-only first
  (`scripts/perf.sh --planner-only`) after L1 and W1, since those add the most
  building volume. Any ci miss goes to `docs/PERFORMANCE.md` and the
  coordinator; do not loosen budgets.
- Galleries: `npm run city-gallery` with `FB_CITIES` = the 9 gates (plans plus
  two district scenes each), compared with the run-branch gallery from before
  M0; open the plans and one street frame per metropolis.
- Reviews: architect (plans, overviews, street frames; the rule's checks;
  diagonal pair if DX2 is still open), interior-designer (deep shop with rear
  wing, high-rise podium at cap 30, block lots: `floorplan --in` on two
  district scenes).

# Order and parallelism

- **Wave 1**: M0, DX, R1 in parallel (disjoint files: `check/` + CLI
  city-check; `TierProgram`/`CityProgram`/`LatticePath` + CLI flags; `Arterials`).
  G0 joins as soon as SHOPLOW S4 is merged. M0 and DX both touch the CLI
  (city-check options): small merge.
- **Wave 2** (after M0; L1 after G0): L1, W1, R2, R3 in parallel. All four
  touch `Districts.java` but in different methods (`placeAlong`/`deepenLots`;
  `blockLots`; lane callers; `quayOk`/`pruneLanes`); `run()` gets two new
  calls (trivial merge). Merge them one at a time; each later branch merges
  the run branch and regenerates fixtures. DG2a can run here too (fixture
  plan, no `Districts` change).
- **Wave 3**: DG1 (after L1), Y1 (after G0, L1 and the architect's A2), then
  DG2b (after DG1 and DG2a).
- **Wave 4**: DX2 (for John), F (checks, goldens, perf, galleries, reviews).
- Rough sizes: M0 M, G0 S-M, DX S-M, R1 S, R2 M, R3 M, W1 S-M, L1 M-L, DG1 S-M,
  DG2a M, DG2b S-M, Y1 M, DX2 S, F M.

# Goldens and fixtures

Generated, never hand-edited; each step regenerates what it changes:

- `planner-core/src/test/resources/golden/settlement/*.txt`
  (`SettlementGoldenTest`; copy from `build/golden/settlement/`): skeleton
  plans. Changed by R1 (arterials drive crossing squares) and by DX only if
  the default flips. Not by district-detail steps.
- `viewer/fixtures/settlement/districts/{river-town,mixed-city,synth-city}`
  (`CityCommandTest.districtFixturesAreCurrent`; commands in their README):
  every planner step except G0, DX (default), DG2a. `river-town` is a town
  and must not change from L1/W1/DG (control).
- `viewer/fixtures/settlement/sample` (`SampleSettlementTest`; regenerate with
  `sample-city --out-dir viewer/fixtures/settlement/sample`).
- `viewer/fixtures/settlement/skeleton/*` (README there): R1.
- Building goldens (`GoldenDigestV1Test`, `golden/v1/*.txt`): unchanged by G0
  (depthCap 0 = old output). Y1 changes nothing without a cap.
- Scene fixtures (`SceneFixtureTest`) and `ViolationsFixtureTest`: check
  after each step; regenerate per their headers.
- City presets in `viewer/city-presets/` are inputs; DX2 adds `-nodiag` ones.

# Interactions with work in flight

- **SHOPLOW** (7.17; S2-S9 on `claude/p7-shoplow-*`): S4 adds
  `LotBrief.square`/`Brief.square` and edits `ShopMassing`: G0 starts after S4
  merges and follows its field pattern. S3 edits `Briefs.fillLot` (shop front
  rise, `empty:bank`): G0's `buildRect` relief helper touches the same method;
  merge after S3/S2, keep S3's front-rise logic on the full front edge (the
  front is the same with or without a cap). S2 (dig-in slab and cut, in
  `InstancePassV1`) owns the slab: Y1 reads the slab level from S2's result
  and must not move it. DS-45 door rise is unaffected by depth (the door is on
  the front).
- **7.19 FALLSTREETS** (not started): "a stretch with more than 2 of cut or
  fill gets no lots" will remove frontage on purpose. L1's "no gaps" rule is
  about planner gaps only; FALLSTREETS' exclusions must show in M0 as their
  own frontage reason (add an `excluded` count when it lands) so `core-fill`
  does not fight it. Cities are mostly on gentle ground (arterials need 3 in 4
  at most), so the overlap is small.
- **ROADS1** (7.10, road materials by kind): R1 turns half the arterials into
  plain border streets, which changes their material (cobbled deepslate →
  street concrete). That is intended; the ROADS1 tests keyed on kinds keep
  passing. DG2's `verge`/`green` triangles need the plaza paving role ROADS1
  sets for plazas (DS-2 calm paving). Merge order free; whoever merges second
  regenerates.
- **VILLROAD**: villages only (`RoadFeature`, `VillageJoin`): no overlap.
- **7.16 slope term** (region screen): moves which settlements exist and the
  join goldens. City-check gates use explicit sites (`gate-sites.tsv`), so
  M0's baselines stay comparable; whoever merges second regenerates
  `golden/region` and settlement goldens.
- **PERFSRV / B8** (perf harness): more and bigger buildings per tile. Watch
  the rows listed under F; PERFSRV's server-side numbers will move too, so
  take F's perf run after PERFSRV merges if it is close.
- **DEEPFLATS** (high-rise deep plates): L1 caps high-rise envelopes at 30
  deep, inside the tested range (test lots up to 44); no new plate depth.

# Risks and open points (assumption if nobody answers)

- *Yards read as empty grass from the air.* The rule counts parcels, so the
  metrics can pass while the plan still looks sparse. M0 adds roof cover on
  scene districts; L1's rear wings and Y1's boundaries are the answer.
  Assume parcels count (the architect's definition) and ask John (J2).
- *Perimeter blocks everywhere in the middle ring* (density 50-70 rows have
  block weight 40). Assume that is fine (ROADS3 chose 50); measure the share.
- *Arterial thinning cuts crossing squares* roughly in half. Assume fine; if
  the architect wants them, add squares at arterial × border-street crossings.
- *R2 dropping arterials beside main roads* contradicts "keep the higher
  class". Assume the arterial yields (A3).
- *Deepening on slopes*: with relief over the capped part, steep yards are
  allowed. Assume a sloping garden is fine; Y1's boundary steps with the
  ground.
- *Planner time*: the deepen pass and the flood are linear in the tile raster;
  R2's `parallelMain` is a raster lookup. Expect under 10 % on city-check ms;
  report it per step.
- *Diagonal-off routes*: 4-neighbour A* gives staircase-free but longer main
  roads, and some cells may lose a route (`MIN_RAY`, water). Assume the
  existing fallbacks (two-leg routes, then A*) cover it; DX's tests check that
  every rule still holds.

# Design questions

For the **architect**:

- A1. Depth caps and the minimum rear yard: agent numbers are `REAR_YARD_MIN`
  4, shop cap 30 at density ≥ 70 (main block plus rear wing), high-rise 30,
  houses and workshops at their drawn depth. Right numbers? Should the middle
  band (50-69) also get rear wings, or yards only?
- A2. What a yard looks like per tier and style: garden wall, hedge or fence,
  height 1 or 2; a paved service court above 70 or always garden; a gate on a
  street-facing yard side?
- A3. Arterial beside a main road within 16: plan lets the arterial segment
  yield (the main road is a continuous skeleton route). Acceptable, or must
  the main road move (R2b)?
- A4. Avenue wedges: green verge vs paved apron, the smallest triangle worth
  keeping as a plaza, `verge` as a new plaza kind?
- A5. Flatiron buildings (stepped 45° facade on the tip lot): wanted now (DG3),
  later, or never in Minecraft?
- A6. `block` lots from 52 across (the block type's minimum) or 40 (your
  spec; needs smaller block margins and a smaller courtyard share). And keep
  `BLOCK_DENSITY` 50 or go back to 70?
- A7. Ring tolerance for a whole-block lot: 90 % of the ring street and 75 %
  per side. A side without street then has units whose doors face a
  neighbour: acceptable, or should those units face inward/sideways?
- A8. `GRID_YIELD` (parallel-drop reach): 16 edge to edge as you wrote, or
  larger so a grid line next to an axis avenue goes entirely?
- A9. Crossing squares fall with arterials on every second line: add squares
  at arterial × border-street crossings to keep the count?

For the **interior-designer**:

- I1. Shop rear wing ("back house") at density ≥ 70: what rooms (store room,
  workshop, kitchen, flat)? Windows into the yard satisfy DS-25 with a yard of
  4 behind and 2 beside?
- I2. High-rise podium 30 deep: back-of-house rooms (storage, plant) without
  windows acceptable under DS-24/DS-25, or must the podium step back above
  the ground floor?

# Questions for John

- J1. Diagonals: the switch lands off-by-default-inert (diagonals stay on).
  After DX2 you get paired plans and frames of the two metropolises and two
  cities with and without diagonals, with the fill numbers. Assumption until
  then: diagonals stay on.
- J2. "Filled" counts the parcel (the building plus its yard or court), as the
  architect defined it; from the air a yard is green unless it has a rear
  wing. Is that what you meant, or do you also want a roof-cover target for
  the core (for example at least 40 % of core land under buildings)?
  Assumption: parcels count; roof cover is reported, not checked.
