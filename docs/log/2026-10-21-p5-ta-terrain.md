# TA: terrain toolkit (Phase 5, 5.3 groundwork)

Pure Java in `planner-core/.../core/terrain/`, tests in `TerrainToolkitTest` (17 tests).

- `WaterSamples` (local stand-in for P0's `CoarseProbe`: `stride()`, `water(x, z)`; `WaterSamples.of(TerrainProbe, stride)`).
  `WaterField.of(src, x0, z0, nx, nz)`: two-pass 8-connected chamfer distance in blocks (10/14), 4-connected component labels in scan order, `isOcean` = more than 4096 samples.
- `LatticePath.find(start, goal, cost, bounds, step[, maxWaterRun, maxExpansions, reverseNeighbours])`: A* with the open set ordered by (f, g, z, x, run), parent changes only on strictly lower g, so ties never depend on neighbour order (tested with the reversed-neighbour hook). The water run (nodes x step, default 40 blocks) is part of the search state, so the limit is exact: 40 crosses, 41 fails. Costs in tenths of a block; `defaultCost` = 1/block + 6/block of height difference + 40 per water node. Expansion budget (default 200k) returns an empty list.
- `Grading.road(points, width, exact)`: centre line densified to columns; anchors every 8 columns (plus ends and water-span ends); 3-point median of ground heights; grade clamp of floor(distance/3) between anchors by repeated forward/backward passes (bridge anchors fixed); interpolation gives at most 1 per 3 blocks. Water spans become decks at max(surface)+3 with piers every 8 (at the centre line). `steep` lists column ranges where |road - ground| > 4; boxes are still emitted for them (caller reroutes). Boxes are merged runs: `ROAD`, `FILL`, `CUT`, `DECK`, `PIER` (`GroundBox.Kind`).
- `Grading.plaza(rect, exact)`: level = median of a 4-block lattice, raised so no cut exceeds 4 (fill first); empty if relief > 6, any water, or a cut > 4 remains. The `PLAZA` boxes tile the rectangle exactly.
- `LotGround.relief(lot, exact)`: 9 samples plus the front-centre height.

Mutation checks: allowing a 41-block water run, and clamping the grade at 1 per 2, each fail 4 tests.

Decisions: height level `y` follows the heightmap convention (first free block; surface block at y-1). `GroundBox` has its own `Kind` rather than `Role`, since P0 owns roles; the caller maps kinds to roles. Follow-up once P0 merges: let `CoarseProbe` implement `WaterSamples` (or adapt it), and `Rect` could become P0's box type. Not done: roads wider cells use the first claiming column's level at junction overlaps (fine for single roads; SK/DL should grade each road separately and union).
