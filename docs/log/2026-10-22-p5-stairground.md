# STAIRGROUND: front steps that end above the ground

John (REVIEW, Phase 5 gate): "I see a lot of places where the stairs out of a building don't reach the ground."

## Cause

An off-by-one between the generator's step rule and the probe convention. `TerrainProbe.floorY` is the first free block
above the ground (the surface is at `floorY`), but the instance pass (`InstancePassV1.steps`) and the old
`entrance-reachable` invariant both treated the ground as solid up to and including `floorY`. A flight therefore ended
when the terrain's first free block reached the level of the *next* step block, which leaves the last step one to two blocks above the real
ground; a door whose outside ground was one below the slab got no steps at all (door floor 2 above the ground); a step standing exactly on
the ground (h == g) was never placed. Roads and plazas were not a cause: on the gate windows the failing flights end on
terrain, not at a graded street (the check does include road/paving surfaces from the scene's ground boxes).

## Check (`steps-reach-ground`, `Invariant.STEPS_REACH_GROUND`, `core/check/StairGround.java`)

Runs in `Invariants.check` for every building (genVersion 2), so it is in `city-check`, the CLI `check` and every property test.
Model in half blocks: ground surface of a column = `floorY`, or the top of a ROAD/PAVING/DECK ground box over it; a STAIR block at y
has its top at y + 1/2; other solids at y + 1. From the cell outside each ground-storey exterior door a breadth-first walk
(rise at most half a block, drop at most one, two free blocks above, never through a door) must reach a column's ground surface.
That fails when the last step is more than half a block above the ground, or no walkable block follows it. `entrance-reachable` is kept as it was.

## Fix (`InstancePassV1.steps`)

A flight ends on ground between the next step level plus one and plus two (`h + 1 <= g <= h + 2`; at the door itself `oy <= g <= oy + 1`; higher ground is a bank the walker cannot step up), a step standing on the
ground is placed (`h >= g`, foundation only when `h > g`), and the stoop landing is used for any door floor more than one above the
ground (`ground < oy`). Flights are one step longer where they were short; no change on flat ground or when the lot has room.

## Numbers (`city-check`, steps-reach-ground violations, before the fix / after)

| Run | Before | After |
|---|---|---|
| 9 gate windows, `--scenes auto:2` (18 scenes) | 156 (9 of 9 sites failing) | 0 |
| 9 gate windows, `--scenes auto:10` | 820 | 0 |
| synthetic `--seeds 1..50 --sites random:4 --terrain noise:3:70:10:300 --scenes auto:1` (200 sites) | 1346 (177 failing) | 0 |
| synthetic steep `--seeds 1..20 --sites random:4 --terrain noise:5:70:24:120 --scenes auto:1` (80 sites) | 1084 (79 failing) | 0 |
| world 12345 `--sites cells --scenes auto:1` (152 sites) | 998 (132 failing) | 0 |

(Measured with the new check on the old generator, then the fixed generator.)

## Not covered

The check starts at doors. The wall-walk ramps between curtain sections (WALLSLOPE) are not door runs and are not checked
here (the doors of towers and gatehouses are). A lot-edge to street drop (a cut road below the lot) is not checked either: no failure of
that kind showed up in the failing cases.

## Pictures

`docs/review/phase5/stairground/` before/after pairs (viewer, `e2e/stairground.spec.ts`, `STAIRGROUND=1 FB_SG_CASES=cases.json`): house on a slope
(no steps at all before, two steps after), shop on a slope, a shopfront with no stoop (temperate gate window), a desert shop whose flight ended a block high.

## Tests

- New `StairGroundTest`: generated house, shop, workshop and temple scenes on six slopes pass; two controls (the lowest step removed, the whole flight removed) must fail.
- `entrance-reachable` now takes the top solid block as `floorY - 1` (it was `floorY`).
- Golden v1 digests regenerated (all types; GEN_VERSION unchanged). `ShopStoopTest`: the wide-landing share fell from over 90 % to 87 % of lifted shops (the flight is one step longer), threshold 0.8, and landings of 3+ cells are required up to a lift of 4 (was 5).
- Mutation: the pre-fix step rule gives the 156 / 820 / 1346 / 1084 / 998 violations in the table above.
- Run: `:planner-core:test :cli:test` pass, viewer `npm test` 129 pass, `:mod:build` (server game tests) passes, `city-check` (no `--ignore`) 0 violations on the 9 gate windows (auto:10), 200 synthetic sites, 80 steep synthetic sites and both world maps.
