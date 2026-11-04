# ORDERSTAIR (6.43): a porch stair changed shape by chunk order

Lead-coder diagnosis and fix, 2026-11-04, branch `claude/p6-orderstair`.

## Symptom

HEAVY14 (run-phase6 580ba622): the order test run `forward` failed with one unknown solid cell, `stone_brick_stairs->stone_brick_stairs` at
1222,67,-9441 (chunk-local z 15). A rerun of `forward,reverse` passed. The verifier suspected the DS36ROOFS r2 class (a roof stair losing its
corner across a chunk border), at a spot `StairSeam` does not cover.

## Diagnosis

- The sim's recorded terrain gives a different plan at that spot, so I added a probe to the order harness: `-Dfb.order.probe=x,y,z` (passed
  through `JAVA_TOOL_OPTIONS`) logs every planned cell in a 5 x 3 x 5 box round the cell, with piece, role, raster shape, wanted state and
  world state. Each unknown solid cell now also logs its full want/have states, and the first three get the same box.
- One `--only forward` run with the probe (passed, as most do) showed the plan: building piece `BUILDING 7,-60 #12`, a porch flight. The
  cell is a `STAIR` facing north, rastered `shape=straight`. In front of it, at 1222,67,-9442, is the landing stair, facing east (the flight
  turns to the door at 1223,68,-9442). West of it is polished andesite. The cell to the south, across the chunk border, is air.
- Minecraft's `StairBlock.getStairsShape` (26.3, lines 121-153; NOTES DS36ROOFS r2) looks at the stair in front. That stair is turned across,
  and `canTakeShape` passes because the west cell is not a stair, so Minecraft gives the cell `outer_right`. The raster's state was not
  Minecraft's, so any `updateShape` on it changes it. A tree in the chunk to the south ends with `StructureTemplate.updateShapeAtEdge`, which
  runs `updateShape` on the blocks beside its leaves. A leaf at 1222,67,-9440 (street air, which a clear removes later) does that, but only
  when that chunk is decorated after the stair's chunk. Hence a rare, order-dependent change.
- The cause is not a gap in `StairSeam`. Roof stairs already followed Minecraft's corner rule (DS36ROOFS r2, `StairSeamTest`). Interior and
  porch `STAIR` cells did not: `Shapes.stair` always returned a straight stair. Over a third of the tiles of 3 gate windows, 3 of 4152 such
  stairs were wrong, all at landing turns.

## Fix

`Shapes.of`: a `STAIR` cell that is a stair block (it has a facing) gets the corner from the same rule as roof stairs (`Shapes.roofStair`,
now documented for both). It reads `RoleView.stairAt`, which already counts every stair the world holds (roof, interior and porch stairs,
other buildings of the tile). `StairSeam` already writes `STAIR` cells across chunk borders (`Pieces.stair`), so a corner whose source is in
the next chunk is covered as well. A stair's facing never depends on its corner, so `stairAt` does not recurse.

## Verification

- `StairSeamTest.everyInteriorAndPorchStairHasMinecraftsShapeOverTheWholeTile` (new): before the fix 3 wrong (gate-temperate-city
  1015,119,-9614 and two in gate-jungle-savanna); after it 4152 stairs, 3 corners, 0 wrong. The roof stair counts did not change (28631, 1538
  corners, 0 wrong).
- `PorchStairCornerSimTest` (new, order sim with the real `updateShapeAtEdge` run from the neighbour chunk at its decoration): before the fix
  4 of the fast orders (reverse, shuffle-1, spiral, checker) end with `outer_right` against the raster's `straight`. After it every order
  keeps the raster's `outer_right`.
- The fast suite and the order test results are in the report (`.claude/reports/p6-orderstair.md`).

## Can it show up elsewhere?

Only buildings emit `STAIR` cells (ground pieces emit none). Every stair the planner writes must already hold Minecraft's shape for the final
world. Both kinds now do, and the seam covers chunk borders. Panes, bars and fences that a vanilla update re-connects are already classified
as known `[shape]` spills by the order test (70 `glass_pane` cells in the failing run). That is an earlier, accepted decision, unchanged here.
