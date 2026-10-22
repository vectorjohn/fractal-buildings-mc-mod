# p5-wallslope: a continuous wall walk on sloping ground

Follow-up to p5-wallwalk (John: "when walls connect, a mob/player should be able to walk between the segments. That might mean stairs or something"). On slopes each ring lot kept its own base level, so the walk jumped at the joins.

## What changed
- **Ring walk profile** (`WallRing.walkLevels`): the level (world y of the ground slab) of every ring lot, worked out once for the whole ring from the exact terrain (`Districts.wallLots`, so every tile holding part of the ring gets the same numbers). Each level is at least the lot's highest ground; neighbours (in clockwise order, `WallRing.clockwise`, since `pieces` lists the south and west sides against the clockwise direction) differ by at most `Brief.MAX_STEP` = 10; a gate lot or bastion (met by a doorway) is never lower than either neighbour. Least solution of those rules, by relaxation; deterministic.
- The level travels as `Brief.Walk(base, prev, next)` on `LotBrief`/`Brief` (JSON key `walk`, written only for ring lots). `SpecPolicyV1` counts it for the height cap; `InstancePassV1` sets the slab to it (foundations fill up to it).
- **Ramps** (`WalkOps.joins`, via the new `BuildingType.walkJoins` hook, added to the placed building's site boxes, so the shell stays a pure function of the spec): a curtain lot that has a higher neighbour climbs one block per column over the last k columns of its walk (fill plus a stair block on top, between the parapets), so the top of the ramp is level with the neighbour's walk or a tower doorway.
- **Doors that cannot be reached** (a tower standing high above its ground has no room for a flight down in a 12x12 lot): `InstancePassV1.sealed` walls the door up for `wall`/`curtain`/`bastion`; a curtain whose only door goes becomes solid (otherwise `rooms-reachable` fails).
- `WallType`: the stair core is redrawn (up to 8 times) when it fills the doorway cells of a tower on the lot's end; before, such a tower had no doorway onto the neighbour's walk (it showed on slopes; flat towns were lucky).
- `BastionType.maxGrade` 4 -> 3: a corner on any slope is built (before, ground steeper than 1:4 over the lot dropped it, leaving a gap). Golden `bastion.txt` regenerated.

## Verification
- `WallWalkTest` (extended): flat (5 rings, every lot at one level), noise (8 rings, max step 3), river (8 rings, max step 10), steep noise and walled cities (2 terrains x towns and cities, complete rings only, max step 10, no ring lot left empty): walking from every corner tower reaches every lot on the wall top, moves level or one up/down; the gate climb reaches the walk level; each join of different levels has a stair ramp on the lower curtain; `walkLevels` property test (300 random rings: at least the ground, step bound, towers not lower, minimal).
- Mutations: no profile propagation fails 4 tests; no ramps fails the ramp test; tower rule off fails 3.
- `:planner-core:test`, `:cli:test`, viewer `npm test`: pass. `city-check` without `--ignore`: 384 sites (flat, noise, river, seeds 1..16) 0 failing; 640 (flat, noise, seeds 1..40) 0; 960 steep and river (seeds 1..40) 3 failing, all `roads-connected`, identical on the base branch (pre-existing); T2 gate sites 0.
- Before this change: entrance-reachable and unreachable-room violations on ring lots appeared at first when only the 1 block step was allowed (raises of 8 to 33 blocks), hence the bigger step with ramps and the sealing.
- Looked at `viewer/out/city/town-walled-hills` (new preset `town-walled-hills`, seed 541 river terrain): the wall climbs the hill in steps with a green stair ramp at the joins and the tower at the low end. Two frames in `docs/review/phase5/wallwalk/slope-*.png`.

## Limits
- Ring pieces dropped because a plaza reserves their ground leave a real gap (existing; 3 of 20 steep rings).
- Levels are raised, never lowered: on steep ground a wall stands up to ~20 blocks above the ground at a corner (foundations).
- Gate lots follow the profile, not the road: on steep ground the gate floor can stand above the street.
- Ramp stair blocks carry no facing yet (the mod's role mapping decides).

## Round 2 (verifier findings)
- Merged SKYLINE (Briefs.java conflict: kept its highrise storey line and `l.walk()`); goldens: `curtain.txt` regenerated (guard door now 10+ columns from the ends).
- Gates follow their own ground: the "tower never lower than a neighbour" rule is gone. `WallRing.joined` is the rule now: curtains differ by at most 10 (the lower one climbs a ramp), a tower may stand up to 10 below a curtain (curtain ramps up to the doorway) or up to `MAX_CARVE` = 9 above it, where the curtain cuts a stair flight down through its own end (`WalkOps.ramp`, negative step in `Brief.Walk`; fill, plug wall behind it, stair blocks); two towers side by side are level. Verifier's case (seed 2, noise:9, cell -2,1, gate dm8_6.w6): gate level 98 (was 105; the baseline was 97).
- `Invariants`: roof-covers and shell-closed excuse a curtain's cut stair well.
- Gate vs street (`WallWalkTest.gatePassagesStayAtTheirOwnGroundAndNearTheStreet`): on flat/noise/river towns (100 gates) none is lifted above its own ground; on steep towns and cities 28 of 122 faces (14 gates) are, up to 14 blocks (a cliff beside the gate). The requested "within 1 block of frontY" is not reachable by this item: the passage floor is the lot's highest ground (towers included), also on the base branch (gaps up to 19 on steep, 9 on the river towns). Needs the road graded to the gate (a roads item).
- Not done: no ramp is skipped toward a plaza-dropped neighbour (the other tile's drop is not known when the levels are computed).
- `docs/IR.md`: optional lot key `walk` documented.
