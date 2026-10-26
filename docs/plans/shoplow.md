# Plan: doors near the street (SHOPLOW)

Status: plan, not started (2026-10-26). Author: lead-coder. ROADMAP item
**7.17 SHOPLOW**; rule [DS-45](../design/STANDARDS.md) "Doors sit near the
street" as widened by John on 2026-10-26 (shops, houses, workshops, with
deliberate house variants; plan revised to its accepted text the same day),
which amends DS-7 and leans on DS-4,
DS-6, DS-8, DS-24 and DS-25. Design input: the architect's answer
[shoplow-architect.md](../review/reports/shoplow-architect.md) (causes, measurements, an 8-point
spec) and John's two refinements, passed on by the coordinator:

- (2026-10-26) the house extension is approved: houses rise at most 3 on a
  plain flight, 4-5 with a landing or porch at the door, and dig in above
  that; shops at most 2 (4 off a shared walkway, a later item); workshops
  get the same treatment.
- (2026-10-26) "make the rule not happen all the time": shops stay strict;
  for houses, digging in is the default but not the only outcome. A
  deterministic per-lot choice between (a) dig in, (b) posts on the downhill
  side with the door at street level (Portland's downhill lots), (c) on uphill
  lots a raised house with a long stair broken by landings, railed per DS-8.

# What happens today (checked 2026-10-26, base c5327e23)

- `InstancePassV1.placeChecked` puts the slab at the **highest** `floorY` under
  the footprint (volumes starting at y 0), fills lower columns with
  `FOUNDATION`, lifts the slab further if a door's outside cell is higher, then
  `steps()` routes a flight from each entrance door down to the terrain inside
  the lot (landings only where `TypeLimits.stoop`, i.e. shops).
- Door "rise" (the architect's measure): door standing level
  `oy + door.box.y` minus the street surface in front (`frontY`, first free
  y). On level ground the rise is 1. Shops: 11 of 34 settlement shops rise 4+
  (enclosed shops on lots rising 3-4 from the street, and market halls on 3-5
  block legs with rise 5-7). Houses 23 of 123 rise 4+, workshops 11 of 39.
- `SHOP_GRADE` (`maxGrade` 12) already keeps shop lots to a whole-lot relief of
  about 2-4, but the street in front can sit up to 4 below the lot's front row
  (graded cut), so the rise from the street can still reach 5-8.
- **The five hill-town houses with the street 10-14 above their floor**
  (seed 1, `fly-hill-town`; diagnosed for this plan with
  `city --districts --scenes dm10_31,dm9_29,dm9_28`): `frontY` is right.
  The streets in front (`rm2_4_0/1` in dm10_31, `b129_m1424` in dm9_29) start
  at a junction high on a ridge (y 87) and come down at the grade limit, one
  block per four, while the ground falls faster, so they stand on an
  embankment of earth (`GROUND` boxes) 12-14 high along the lot fronts
  (`dm10_31.l0`: ground 70-73, street surface 83; `dm9_29.l10`: ground 65-69,
  street 79-82). The CLI says so: "street rm2_4_0 needs more than 4 blocks of
  cut or fill on a steep stretch". **The doors are not reachable from their
  street**: the fill ends in a vertical earth face at the lot line; the door's
  steps reach the lot's own ground, so `entrance-reachable` passes (DS-6's
  street-to-door half is untested). The cause is street grading, not the slab.
  This plan leaves such lots empty (step S3) and proposes a separate streets
  item for the grading.

# Does this need an ADR change?

No. ADR 0003 **W5** ("Terrain: the S5 way", accepted) fixes the mechanism: one
rigid piece per building, box bottom at floor level, `beard_thin`,
foundations are our own blocks, no `bury`, no terraced city areas, and "the
5-12-block uphill cut faces are accepted". It does not say where the floor
goes. "Slab at the highest ground under the footprint" is an assumption of
the Phase 3 plan (`docs/ASSUMPTIONS.md`, 2026-10-16, marked cheap to change),
carried into v1; REVIEW's "the S5 rule" was loose wording. A lower slab keeps
the box bottom at the slab, so the beard keeps taking its ground plane from
it; a cut inside the lot is not a terraced area. S2 appends an ASSUMPTIONS
row that supersedes the 2026-10-16 one. If in-world cut faces (widened by the
beard, below) are judged ugly, W6's flattening tool is the existing fallback.

# Goal

Every shop door sits within 2 of the street in front of it (DS-45 says "at
most 2"; the plan also keeps it at least -2, a door below the street being the
mirror case). Houses and workshops follow DS-45's default limits (rise at most
3 as a plain flight, 4-5 with a landing or porch at the door), and a minority
of house lots take one of DS-45's two deliberate variants, which are checked
for a door reachable from the street, landings and railings instead. No window or door ends up below the ground outside it, every
door keeps a path to the street, and the world matches the plan.

# Approach and key choices

## 1. Standing level: as low as the cut cap allows

For the three street-front types (`shop`, `house`, `workshop`; towers, halls,
temples, blocks, high-rises and walls keep today's rule), with `s` = the
standing level of the entrance storey (`oy + 1` when the entrance storey is
storey 0):

- `G`, the door ground: the brief's `frontY` when it is known and within
  `BANK_LIMIT` (4) of `fr`; otherwise `fr`. `fr` is the probe's `floorY` at the
  lot's front-edge cell straight out from the main door (the main door: the
  entrance-storey exterior door facing the lot front; else the first one).
- `M`: the highest `floorY` over the **site** (below).
- `s0`: today's level (highest ground under the footprint plus the door guard).
- **Dig in:** `s = min(s0, max(G + 1, M - CUT_MAX))`, `CUT_MAX` = 4
  (`Grading.MAX_CUT_FILL`, the cut the roads already allow). So the door is
  one step above the street (rise 1) when the cut stays within 4; otherwise
  the slab sits as low as a 4-block cut allows. Rise <= 2 holds while
  `M - G <= 6` (shops: S3 keeps lots inside that); rise <= 5 while
  `M - G <= 9` (houses: with a landing at the door from rise 4).
- Never higher than today (`min(s0, ...)`), so flat and downhill lots are
  byte-identical to today, and no column is cut by more than 4.
- No cut in water: if any site column with `floorY > s` has
  `waterDepth > 0`, fall back to `s0`.

Rejected: the architect's point 3 ("bank buildings": rise 1 always, the back of
storey 0 under the earth, a back door on storey 1). With the cap at 4 and an
areaway, shops never need it (S3 keeps `M - G <= 6`) and houses up to
`M - G = 9` stay within DS-45 with a landing; settlement house lots are capped
at a whole-lot relief of 8 (`Briefs.STEEP_RELIEF`). It needs terrain in the
shell (windows, room uses and an upper door depend on the buried depth), so a
spec field and changes to the facade, interior and massing passes. DS-45's
text names the bank building as what a steep lot gets; this plan meets DS-45's
limits without it (partial dig-in plus a landing) and builds it as S8, which
7.18 BASEMENT needs (it lists SHOPLOW's bank buildings as a dependency), after
the architect's answer to A3.

## 2. The cut: site boxes, never over the shell

The **site** is: the columns under any volume (the whole plan, so arcade
walkways and the ground under jetties are included), a 1-cell ring around the
footprint (8-neighbour) and the door approach (the cells straight out from the
main door to the lot's front edge, door width), all clipped to the lot.
For each site column outside the footprint whose `floorY > s` (earth above the
standing level), the instance pass adds:

- `AIR` site boxes over `[s - 1, floorY)`, merged along x like today's
  foundation runs, **skipping every cell a shell box occupies**. Site boxes
  are rastered after shell boxes and `AIR` clears (`PieceRaster.Grid.of`,
  `VoxelGrid.of`), so an `AIR` box over the shell would erase arcade posts or
  walls. No headroom above `floorY`: the beard plane is the slab, and above it
  `beard_thin` only lowers the live ground.
- a `PAVING` cell at `s - 1` (the floor of the areaway and forecourt), again
  only where the shell has nothing. `PAVING` and `GROUND` are style roles in
  every style JSON already.

Inside the footprint the building replaces the earth itself: room parts clear
their air (`PieceRaster.Grid.of` fills room parts with air first) and the shell
boxes are solid. S5a proves no footprint cell between the slab and the old
ground is left unclaimed; if some are, S2's cut builder adds `AIR` site boxes
for exactly those cells (shell-free), so `PieceRaster` itself does not change.

The **approach fill**: when the street is above the door's approach ground
(`G >= s - 1` and an approach cell has `floorY < s`), the approach cells get
`GROUND` from `floorY` to `s - 2` and `PAVING` at `s - 1`, so the door meets a
level forecourt one step above or level with the street, instead of a flight
down into a dip and a climb back up to the street.

The ring is the architect's areaway: every wall that has earth beyond it gets
a 1-wide light well down to the floor, so **no window or door is ever below
the ground outside it**, storey 0 keeps its windows (DS-24/DS-25 hold without
interior changes) and back or wing doors open into the well, which runs at one
level to the front. The cut face beyond the ring is natural ground (W5's
accepted cut faces); a retaining wall would need a second cell (question A4).

## 3. One effective-ground rule for everyone

`core/terrain/SiteGround` (new): `TerrainProbe probe(PlacedBuilding b,
TerrainProbe raw)` returns, per column:

- footprint columns: `min(raw, oy)` (the slab is the ground);
- a column where a building `AIR` site box spans the surface (`box.y <= raw - 1`
  and `box.maxY >= raw`): the box's bottom;
- then, where `PAVING`/`GROUND` site boxes stack from that ground up: one above
  their top. (`FOUNDATION` under treads and landings stays "not ground", as
  today, so a flight cannot pass `steps-reach-ground` by standing on its own
  kerb.)

For every building that exists today the result equals the raw probe (the slab
is already at or above every footprint column; WalkOps' `AIR` ramps do not span
the surface), so S1 changes no output. Users: `InstancePassV1` (door guard,
`steps()` ground and rails), `Invariants` (each building's checks, including
`StairGround`, get the wrapped probe), the CLI `floorplan` if it reads ground,
and the viewer's `terrainTops` (TypeScript mirror of the same rule).

## 4. Houses: three outcomes, picked per lot

Deterministic from `Hash64.hash(worldSeed, "site-strategy", lot.x, lot.z)`
(never from iteration order), weighted by the slope class and style:

| lot | default | alternative (minority) |
|---|---|---|
| uphill, `M - G >= 4` | (a) dig in | (c) raised: today's `s0`, the flight broken by landings |
| downhill, the slab 4+ above the lowest footprint ground | plinth (today) | (b) posts: the downhill foundation becomes posts, the door at street level |
| otherwise | dig in (same as today on flat ground) | none |

Proposed weights (question A1): (c) 20 % of eligible lots, 40 % in jungle; (b)
25 %, 50 % in jungle and on swamp/mangrove biomes. Shops: dig in only.
Workshops: DS-45 gives them the house limits and allows the variants;
proposed: dig in only (a cart door wants level ground; question A2).

Default lots: a landing at the door from rise 4 (`TypeLimits.stoop` becomes a
minimum rise: shops 2 as today, houses and workshops 4). Variant lots: no rise
limit, but DS-45's run rule: no straight run of more than `MAX_RISERS` = 3
risers without a landing or a turn. The checks re-derive whether a lot is a
variant from the same hash and slope class (the scene carries the world seed,
the lot and the terrain), so the IR needs no marker.

## 5. Market halls: the trade is on the street

Per the architect: legs 3 (4 from width 11); only on lots at least 9 wide that
front a square or a corner, with relief <= 1; the upper room loses its
shopfront (ordinary windows, no DS-7 terrace) and is reached by a stair along
a side wall (or inside the open floor, question A6), never a front flight.
The shell reads only the spec, so the site facts reach it through a new spec
key `square` (boolean: the lot fronts a plaza or a street corner and has
relief <= 1), set by `SpecPolicyV1` from a new `Brief.square`. Like `lead`, it is
written to JSON and hashed only when true, so every existing spec hash stays.

## 6. Checks

- `door-rise` (new invariant, generator v1; DS-45's enforcement): every
  entrance-storey exterior door facing the lot front. Shops rise -2..2
  (4 off a frontage pavement, a later item, so no exemption yet); houses and
  workshops on default lots at most 5, with a landing at the door above 3;
  variant lots are exempt from the rise and get `flight-landings`. Street
  level: the scene's ground surface (`StairGround.surfaceLevels`) at the first
  column outside the lot in line with the door, else the effective ground
  there (DS-45 says `frontY`, else the door's outside cell; the scene's own
  street surface is the same street, read where the door meets it, and CLI
  `check` scenes have no streets). Stilted buildings are exempt (their trade
  floor is the open ground).
- `flight-landings` (new invariant): on variant lots, no straight run of more
  than `MAX_RISERS` (3) risers without a level tread or a turn; reported for
  every other flight.
- A **door-rise table** per type (rise buckets <= -3, -2..0, 1, 2, 3, 4, 5, 6-8,
  9+, and the share with a landing) in `check` and `city-check`, like the
  glazing table: the before/after measure.

# Steps

`[P]` = can start at once, in parallel with the other `[P]` steps.

## S0 [P] Door-rise measure and tables (report only)

- `core/check/DoorRise.java`: `measure(SceneDoc, PlacedBuilding)` → per entrance
  door {door id, type, rise, street source (`ground`/`terrain`), longest run of
  risers without a level tread on its flight, landing at door yes/no}; plus a
  `Report` that buckets by type (copy `Glazing.Report`'s shape).
- Print it in `CheckCommand` and `CityCheckCommand` (`## Door rise (DS-45)`
  section) and add the two ids as **design warnings** (the `HighriseChecks`
  pattern: listed, never failing). S2 and S6 promote them.
- Record the baseline in the log: `city-check --seeds 987654321,12345 --sites
  gates:fixtures/terrain/gate-sites.tsv --scenes auto:999` (the 9 gate
  windows; check the exact flags in `CityCheckCommand`), plus river-town and
  fly-hill-town (`--terrain` from their presets in `viewer/city-presets/`). If
  `auto:999` is too slow on the metropolis windows, say so and use the largest N
  that runs in a few minutes.
- Tests (`DoorRiseTest`): flat lot, rise 1; a hand-built building lifted 4,
  rise 5 and run 5; a scene with a road surface box 2 above the terrain in front
  of the door, rise measured from the road (mutation: ignore ground boxes and the
  test fails); a market hall is skipped.
- Files: new check class and test, the two CLI commands. No generator change.

## S1 [P] Effective ground (no output change)

- `core/terrain/SiteGround.java` (section 3). `Invariants.BuildingCheck` takes
  `SiteGround.probe(b, ground)` instead of `ground`; check that `StairGround`
  and `entrance-reachable` read it through `floorY(...)` (they do today:
  `BuildingCheck.floorY`, and `steps-reach-ground`'s call site).
- `InstancePassV1.steps()` and `rails()` build `ground[]` from the wrapped probe
  of the provisional building (outside-lot `outG` stays raw).
- Tests: `SiteGroundTest`: a hand-made `PlacedBuilding` whose footprint lies 3
  below the probe, with `AIR` + `PAVING` ring boxes (helper `CutFixture` in
  test sources; S1v and S5a reuse it, and it writes
  `planner-core/build/cut-fixture.json` for the viewer) passes every invariant;
  with the raw probe it fails `no-gap-below` and `no-floating` (that is the
  mutation check). All golden digests unchanged (`GoldenDigestV1Test`,
  `SettlementGoldenTest`): the proof that S1 is a no-op for today's buildings.
- Files: new class, `Invariants`, `InstancePassV1` (ground arrays only).

## S1v [P] Viewer: cuts show

- `viewer/src/mesh.ts` `terrainTops`: also lower columns by each building's
  footprint (`min(top, oy)`, footprint = volumes with `min[1] == 0` through the
  placement, as `InstancePassV1.footprint`) and by building `air` site boxes
  that span the surface; `paving`/`ground` site boxes are voxels already.
- Test in `mesh.test.ts`/`renderDistrict.test.ts`: a scene (inline, or the S1
  fixture JSON copied into the viewer's test fixtures) whose footprint lies
  below the terrain: no terrain top quad inside the footprint above the slab;
  a ring column is lowered to the cut. Screenshot one case with the
  `cutaway` camera and look at it: the shop room shows no terrain inside, the
  areaway is open to its floor.
- Works against the fixture alone; no Java dependency.

## S2 (after S1) Dig in: slab, cut, approach, house landing, `door-rise`

- New `core/gen/v1/SiteLevel.java`: `G`, `M`, `s0`, `s` and the cut cells
  (sections 1-2); `InstancePassV1.placeChecked` uses it for shop, house and
  workshop (`DIG_TYPES`), keeps today's path for the rest and for stilted
  shells. Constants `CUT_MAX = Grading.MAX_CUT_FILL`, `BANK_LIMIT = 4`.
- Emit the cut and approach boxes (shell-free cells only; one `VoxelGrid` of
  the shell at `oy` tells which cells are taken), then the door guard and
  `steps()` with `SiteGround`.
- `TypeLimits`: the stoop becomes a minimum rise (shops 2, keeping today's
  `ground[s0] < oy` behaviour; houses 4; others never).
- House strategy pick (section 4) with only (a) wired: the hash and the slope
  classes exist; (b) and (c) return (a) until S6/S7.
- Promote `door-rise` (shops; houses and workshops on default lots) to a
  failing invariant in `Invariant` (append last, as `window-size` was).
- Tests:
  - `DigInTest` (planner-core), shops and workshops on `plane:64:0:1:N` for N in
    3, 4, 6, 9, 12 and fronts N/E/S/W, seeds 0..15, lots from `testLots`, plus
    houses on N in 2, 3, 6: invariants clean; rise -2..2 (shops), <= 5 with a
    landing above 3 (houses, workshops); cut <= 4 at every column; **no window or door cell has
    effective ground above its bottom in the column outside it** (walk every
    exterior opening; mutation: drop the ring and this fails); every site cell
    outside the footprint with earth above `s` is cut; no `AIR` site box
    overlaps a shell box cell.
  - Regression: flat and downhill planes (`flat:64`, `plane:64:0:-1:N`) give
    byte-identical scenes to the legacy rule (keep the legacy path reachable
    from tests, e.g. a package-private flag, and compare JSON).
  - Water: a probe with `waterDepth > 0` on the uphill ring falls back to `s0`.
  - `ShopStoopTest` rewritten: on its `SLOPES` every shop rises at most 2, and
    a stoop appears only at rise 2; `StreetWalk` from the street to the door
    passes on every case. `ShopOpenGroundTest`, `RailingTest`, `LotRailTest`,
    `WideDoorStepsTest`, `V1PropertyTest` stay green.
  - Regenerate v1 goldens (shop, house, workshop should move; others must not),
    settlement goldens and fixtures (`SampleSettlementTest`, the district
    fixtures under `viewer/fixtures/settlement/`, `CityCommandTest`): see
    NOTES "golden"; never hand-merge.
- Gallery: `shop-slope-deep`, `house-slope` and a new `workshop-slope` preset
  (copy `house-slope`), before and after; open the PNGs. Expect the shop door
  one step above the lot front, the areaway visible in `back-right`/`cutaway`.
- Docs: ASSUMPTIONS row superseding 2026-10-16 ("slab at the highest ground");
  NOTES line for `SiteGround`.

## S3 [P] Lot planner: shop front rise, embankment lots

- `Briefs.fillLot`: `fr` = floorY at the lot's front-edge middle column
  (the same column `Briefs.frontY` looks out from). If `frontY` is known and
  `|frontY - fr| > BANK_LIMIT` (4), a house, workshop or shop lot becomes
  `empty:bank` (the street runs on an embankment or in a cutting along it).
- A shop lot needs `hi - frontY <= SHOP_FRONT_RISE` (6; `hi` = the lot's
  highest floorY), else re-pick with the shop excluded (`ClassTable.pick` with
  an exclusion, as MINLOT's path). Keep `SHOP_GRADE` (question J1).
- Tests: `BriefTest` cases with synthetic terrain and a one-street plan:
  street 6 above the front row → `empty:bank`; street 3 below a shop lot rising
  4 → re-picked; within limits → unchanged. A fixture test on
  `fixtures/terrain/fly-1-hill-town.json.gz` (seed 1 site
  `-1536,4800,town,418`): `dm10_31.l0`, `dm9_29.l10` and `dm9_28.l19` are
  `empty:bank` (mutation: `BANK_LIMIT` 20 and it fails). Report how many lots
  change status on the 9 gate windows and the two towns.
- Viewer: `settlement.ts` accepts any `empty:*` status already; check the plan
  legend shows the new one sensibly.
- Regenerate settlement goldens and district fixtures (conflicts with S2's
  regeneration are generated files: regenerate, never hand-merge).
- Propose (do not do) the streets item: a street that needs more than 4 of
  fill or cut along a built frontage should be regraded or rerouted (with
  6.3's junction heights).

## S4 [P] Market hall rework

- `BuildingSpec.square` (boolean; JSON key `square` and `SpecHash` entry only
  when true, like `lead`; `IrReader` optional; viewer `ir.ts` optional field);
  `Brief.square` filled in `Briefs` (front edge on a plaza, or a corner lot: the
  streets mask has a side bit) and passed by `SpecPolicyV1` as
  `square && lot relief <= 1`.
- `ShopType.groundMode`: `MARKET` only when `spec.square()` and width >= 9
  (otherwise the draw falls to arcade or enclosed; question A6 on the share).
  `legHeight`: 3, or 4 from width 11. `Stilts` accepts lift 3 for this use
  (keep `TowerType` at its own minimum 4 so tower goldens do not move).
- Upper room: no shopfront bays (`Shopfront.facade` skips the market mass);
  its entrance on a side face toward the back; the mass is 2 narrower on that
  side so the side flight and its rail fit in the envelope (`steps()` routes
  it). Upper room use `hall` until the interior designer answers (I1).
- Tests (`MarketHallTest`, `ShopOpenGroundTest` updated): no market without
  `square` or under width 9; legs 3/4; no `shopfront` layout kind on any
  market storey; the entrance is not on the front face; steps reach the ground
  and are railed; spec hash unchanged for every non-square spec (golden digests
  of all other types unchanged). Gallery `shop-flat` and `shop-medieval` with
  square briefs (CLI: add `--square` to `gen`/`batch` if needed), look at them.
- Files: `BuildingSpec`, `SpecHash`, `IrReader`, `ir.ts`, `Brief`, `Briefs`
  (small overlap with S3), `SpecPolicyV1`, `ShopType`, `ShopMassing`,
  `Shopfront`, `Stilts`.

## S5a (after S1; parallel with S2) Every buried cell is claimed

- `BuriedRasterTest` (planner-core, `raster`): with `CutFixture` and, once S2
  lands, real dug-in shops and houses from `DigInTest`'s cases, every cell
  inside the footprint's volumes from the slab + 1 up to the raw `floorY - 1`
  is emitted by `PieceRaster.building` (solid or air). If any is not, S2's cut
  builder adds shell-free `AIR` site boxes for them (coordinate with the S2
  coder), not a `PieceRaster` change (ROADS1 has uncommitted `PieceRaster`
  work).
- Mutation: remove the room-part clear in a test copy of the grid, or drop one
  ring box, and the test fails.

## S5b (after S2, and after ROADS1 merges) In the world

- `:mod:test`: `BlockMapperTest` (or the existing role-coverage test) covers
  `paving` and `ground` in every style for building pieces.
- Heavy, under the heavy lock, through `scripts/evidence.sh`:
  `scripts/client-test.sh` (B2 `DistrictOrderClientGameTest`: ledger digests
  equal in every order, claimed areaway air is air in the world or a known
  spill kind; B3 `TerrainClientGameTest`: void 0, the burial metric before and
  after in the log). Touch `gametest/order/Spill.java` only if B2 reports a new
  kind (ROADS1 also edits it).
- Screenshots: river-town `dm7_m22` street (the architect's example
  `dm7_m22.l6`, rise 5 today) and one hill-town street, in-game. The beard will
  carve a wider shelf than the planned areaway (`Beardifier`, NOTES 2026-10-26):
  say what it looks like.

## S6 (after S2) Raised houses: flights with landings

- `steps()`: the breadth-first search runs over (cell, level, risers since the
  last level tread or turn) so a flight may put a level tread between risers
  and must, or turn, after `MAX_RISERS` (3) straight risers; shortest route first, ties as today; a route may not use
  a cell twice. Landings get rails as landings (`RAIL_DROP_LANDING`).
- Wire strategy (c) in the house pick (`s = s0`); promote `flight-landings` to a
  failing invariant for variant lots (DS-45).
- Tests: `FlightLandingsTest`: house on `plane:64:0:1:2` with (c) forced, rise
  8-12, every run <= `MAX_RISERS`, all treads railed (DS-8), invariants clean;
  the strategy share over 400 uphill lots within 5 points of the weights; the
  same lot gives the same strategy whatever order lots are generated in.
  `WideDoorStepsTest`, `LotRailTest`, `RailingTest` green. Gallery `house-slope`
  with (c) forced.

## S7 (after S2 and architect answer A1) Downhill houses on posts

- Strategy (b): footprint columns whose foundation would be 4+ high get posts
  (`BEAM` columns at the corners and at most every 3 cells along the downhill
  edges, braced as `Stilts` braces) and stay open under the slab; the uphill
  part keeps its foundation.
- Exemptions, each with a test: `no-gap-below` accepts an open column when a
  post of the same building stands within 3 cells and reaches the ground;
  `Pieces.deepen` deepens posts, never fills under the open columns; B3's void
  metric treats such buildings like stilted ones (needs a marker: the posts'
  site boxes carry `Role.BEAM` under the footprint; reuse that).
- Tests: `PostedHouseTest` (invariants clean; no foundation box under an open
  column; door rise 1); a mutation removing a post fails `no-gap-below`.

## S8 (after S2 and architect answer A3; before 7.18 BASEMENT) Bank buildings

Houses (and workshops) with `M - G` 6-9 taking rise 1 by burying the back of
storey 0: spec key `bank` (buried depth at the back wall), facade bays below it
blank, storey-0 rooms with no window get a non-habitable use and a light, a
storey-1 back door onto the high ground (DS-35's second door). DS-45 describes
this for steep lots, and 7.18 BASEMENT builds on its cut and retaining
machinery, so it is not optional; it is not needed for DS-45's limits, so it
follows S2. Plan it in detail (lead-coder) once the architect answers A3.

## S9 (after S2, S3, S4, S6; S7 if done) Measure and review

- Door-rise tables on the same runs as S0's baseline: shops 100 % within
  -2..2, houses and workshops histograms before and after, status changes from
  S3.
- Galleries: `shop-slope-deep`, `house-slope`, `workshop-slope`, `shop-flat`
  (market halls) before and after; `npm run city-gallery` for river-town and
  fly-hill-town; one fly-through street frame if FLY2 has landed.
- Architect and interior-designer reviews with those pictures; one
  `docs/REVIEW.md` entry for John (shops at grade, the areaway look, the
  house mix); tick 7.17.

# Order and parallelism

- Wave 1, all at once: **S0, S1, S1v, S3, S4**. S1v works from a fixture JSON;
  S3 and S4 touch `Briefs.java` in different methods (small merge).
- Wave 2: **S2** (needs S1), **S5a** (needs S1; finishes against S2's cases).
- Wave 3: **S6** (needs S2), **S5b** (needs S2 and ROADS1 merged), **S7**
  (needs S2 and answer A1).
- Wave 4: **S9**.
- One coder each; S2 is the largest (M). S6 is M. Others S.

# Interactions with work in flight

- **ROADS1** (`claude/p7-roads1`): uncommitted edits to `PieceRaster`,
  `StairGround`, `Role`, `Shapes`, `RoadFeature`, `Spill`. This plan changes
  none of those except, if S5b finds a new spill kind, `Spill`; and S1 wraps
  the probe *before* `StairGround` sees it. S5b waits for ROADS1's merge. If
  ROADS1 changes plaza `PAVING` per style, the areaway floor follows it.
- **FLY2**: no code overlap; its frames change (buildings sit lower); re-run
  the fly-through after S2 for S9.
- **PERFSRV**: a few more site boxes per building on slopes; no budget risk
  expected; S9 notes the planner `bench` before/after.
- **7.16 slope term**: reshuffles settlements and every join golden. Whichever
  of 7.16 and S2/S3 merges second regenerates goldens and fixtures.
- **7.18 BASEMENT** (new, run-phase6): depends on SHOPLOW's floor reference, cut and retaining machinery and bank buildings (S8); its walk-outs (B2) turn the downhill plinth into a storey. `SiteGround` and the cut boxes are what it should build on; keep S2's cut builder reusable (one class, `SiteLevel`).
- **SHOPLAND (5.12)**, **STAIRGROUND**, **LOTRAIL/MINLOT**, **DOOR2**: their tests
  stay; `ShopStoopTest` changes meaning (S2). MINLOT's rail-room widening could
  relax once fewer flights exist (not here).

# Risks and open points (assumption if nobody answers)

- In-world the beard widens the cut: `beard_thin` subtracts density above the
  slab plane within a few blocks of the piece box (kernel `exp(-d²/16)`), so the
  natural face behind the areaway slopes back further than planned. Accepted by
  W5; S5b shows it. Our `PAVING` floor and walls are not affected (our blocks
  are written after the noise).
- Cut faces deeper than the surface layer show stone (S5's gotcha); at 4 blocks
  mostly dirt. Accept.
- Water tables: no cut where a site column has water (falls back to today).
- Shops lost on hills (S3 re-picks): report the count; assume acceptable.
- `GEN_VERSION` stays 2 (precedent: goldens regenerated per item).

# Design questions

For the **architect**:

- A1. The house mix: (c) raised-with-landings on uphill lots and (b) posts on
  downhill lots, against digging in and the plinth. Proposed: (c) 20 %, 40 % in
  jungle; (b) 25 %, 50 % in jungle and swamp/mangrove; both only from a 4-block
  difference. Does any style rule one out (desert, snowy)?
- A2. Workshops: DS-45 gives them the house limits and the variants. Proposed:
  dig in only, no variants (a cart door wants level ground). Agree?
- A3. Partial dig-in leaves houses with `M - G` 6-9 at rise 2-5 with a landing,
  within DS-45's limits, until S8 adds bank buildings (rise 1, back of storey 0
  underground, back door upstairs). From what difference should S8 take over?
- A4. The areaway: 1 cell, natural cut face, `PAVING` floor. A retaining wall
  in foundation material needs a second cell (lots have a 1-cell margin). Is
  the natural face fine; is `PAVING` the right floor; can it go unrailed at the
  top edge (that cell is outside the lot)?
- A5. DS-45 sets at most 3 straight risers on variant stairs. Should default
  lots at rise 4-5 also break their flight (a turn after the door landing), or
  is the door landing enough there, as DS-45's default reads?
- A6. Market hall: stair along a side wall outside the hall (proposed) or
  inside the open floor; share among eligible lots (proposed 50 %, since few
  lots qualify); legs 3/4 confirmed.
- A7. The approach fill (a level paved forecourt when the street is 1-4 above
  the lot front): right answer?

For the **interior designer**:

- I1. Market hall upper room: hall, guild room or store, and its furniture.
- I2. Storey-0 rooms whose windows face a 1-wide, up to 4-deep areaway: keep
  bedrooms there or prefer other uses?

# Questions for John

- J1. Keep `SHOP_GRADE` (shops only on gentle lots, as today), or loosen it now
  that shops can dig in? Assumed: keep it in this item.
- None of the rest needs John before work starts.
