# 4.5 Interiors (coder, branch claude/p4-i-interiors)

Plan step I in [../plans/phase4.md](../plans/phase4.md).

## What was done

- `interior/FloorPlan` (pure geometry): a mass's interior is cut by recursive guillotine
  splits (style `roomArea`, `roomMin`) into rooms with one-cell partitions. Cut lines never
  pass through *reserved* cells: window inside cells (so partitions meet outer walls at piers),
  the entrance's inside cell, the cell either side of a strip door, and a ring around each core
  (so the stair room keeps a walkable margin and the stair contract is untouched). Doors: a
  random spanning tree over neighbouring rooms (corridor edges first), plus loops
  (`loopPercent`); a few retries, then one room, if the doors can't connect.
- Corridor: a floor with `area / roomArea >= corridorAbove` gets a 2-3 wide lane along its long
  axis between two partition lines, if free lines exist.
- `InteriorPass` plans per (mass, band): storeys with equal height and equal constraints
  (window inside cells, entrance, strip doors, cores) share one plan, replicated with a y shift.
  Room ids stay mass, storey, room order. Flights, strip doors and the entrance contract are as
  before; `flight` is unchanged.
- `RoomProgram` (use tags and hook kinds), `BuildingType.rooms()` (default method, appended).
  Uses: `entry`, `corridor`, `stair`, `hall`, `room`. Hooks sit on floor cells against a wall or
  partition, never within one cell of a door, the entrance, a strip door or a core.
- `InteriorStyle`: `roomArea`, `corridorAbove`, `loopPercent` (defaults keep old output: no
  split). plain/medieval set `roomMin` 3 (was 2), `roomArea` 30/24, corridor 6/8, loops 20/30.
- Golden: `house.txt` regenerated (interior output changed; other types unchanged).
  `FixturePipelineTest` counts relaxed (door counts and room counts now include partitions).

## Verification

- `:planner-core:test :cli:test` pass (after merging claude/run-phase4). `check --seeds 0..999`: 0
  violations in every invariant (room-graph, rooms-reachable included). `bench`: shell 0.085 ms mean.
- `RoomSplitTest` (8 tests, fixtures x plain/medieval x 20 seeds): tiling, room side >= roomMin,
  no partition behind a window or entrance, hooks (room air, solid floor, not near a core or door),
  typical floors on `setback` (storeys 3-7 identical up to y; flights by parity), corridor on 24x16,
  60-storey tower connected and < 20 s.
- Mutants: dropping a partition door -> RoomSplitTest/V1PropertyTest fail on room-graph (234 and
  535 cases); removing the hook door-approach rule -> "hook ... is in front of door".
- Looked at: playwright top-down cutaways (`storeys=0`, layers foundation/shell/interior) of
  courtyard, setback, L, box3 and two houses (24x20 lots, medieval): partitions meet the outer
  wall between windows, doors (orange) in partitions, stair block (green) inside the stair room,
  courtyard wings split into 2-3 rooms each, podium of `setback` has 9 rooms. Camera zoom in the
  headless viewer is poor, so the images are small; no PNGs committed.

## Notes

- Cores in the middle of small masses (setback shaft 10x10) leave no legal cut with roomMin 3: stays
  one big stair room. Massing (M) should keep cores against a wall.
- Reserved cells block cuts strongly: window-bearing faces rarely allow two aligned lines 3-4 apart,
  hence lane width 2 or 3.

## r2: merge with M (a43b71c)

Merged claude/run-phase4; golden house.txt regenerated (not hand-merged). `:planner-core:test :cli:test` pass, `check --seeds 0..999` 0 violations (rooms-reachable 0). On M's houses (8 medieval 24x20 seeds) the one-room issue still shows on some multi-storey ones: they have only entry + stair rooms (seeds 1, 6, 7 of 8), others get 3-6 rooms; cores are mostly against walls now but the small mass leaves too little space for a cut.
