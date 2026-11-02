# 6.35 GLASSSTUB: lone glass pane (claude/p6-glassstub)

Base: claude/run-phase6 (dc7cddd0).

## What was done

- New invariant `pane-attached` (DS-51, `Invariant.PANE_ATTACHED`, `check/PaneAttached.java`). A pane is a WINDOW cell. It fails when it is
  `lone` (no pane or full block on its four horizontal sides; air, doors, fences, stairs and slabs do not count), at an outer `corner` (joins only along an L)
  or `capped` (open air above: a pane at a wall top). Generator v2 scenes only, like `window-size`.
- Two views: `PaneAttached.check(VoxelGrid)` (one building alone, used by the unit tests) and `PaneAttached.checkScene(scene)`, which is what
  `Invariants.check` runs: the cells the world ends up with (`PieceRaster.building` of every building in list order, then the ground), so an earlier
  building's cell or air clear that strips the walls from beside a later building's pane is seen.
- STANDARDS DS-51 enforcement line updated.

## Cause: not found

The cause of 19.45.26 is not reproduced. Counts of failing panes, before (check run with the rule, no generator change) and after (same, nothing changed):
0 and 0 in every run below, so no generator fix was made.

| run | scope | failing panes |
|---|---|---|
| `check --seeds 0..9 --types` all 11 types, default lots/styles/terrains/fronts | ~148k cases (bastion group 102k, highrise 14k, block 14k, hall 18k, others incl.) | 0 (per-building rule; the corner/capped rules too) |
| `city-check --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:2` | 9 gate sites | 0 |
| pt1709 world (the 6.13 spot, 1266,2411), metropolis r900, `--scenes auto:400` | 68 scenes | 0 (raster view too) |
| desert-badlands and border-desert-jungle gates, `--scenes auto:150` | 2 sites | 0 (raster view too) |

(The first "before" run had only the lone rule; the strict rules and the raster view were added after, and every later run is 0 too.)
Ideas not found in the IR: the `sealed` secondary-door window (wall below, pane above) is always between wall cells; Shopfront and Hall arcade panes have wall at their ends.
What is left is world side: the photo is from before 6.13 STEPBURY/6.14 TREEIN, and a pane block with all four side properties false renders as a thin post
(the photo's sliver); `Shapes.connect` reads the piece's own grid, so a neighbour another building or feature changed afterwards is not seen. Next step: `/fbnote` at the spot in game and read the block states.

## Verification

- `PaneAttachedTest` (6 cases: sample house passes, free pane lone, pane on a wall top capped, pane at an outer corner, a pane run with wall at both ends passes, the invariant fires on a scene). Mutation: the control cases pass and the three bad cases fail only through their rule.
- The run counts above (CLI, quiet enough: host load 30-100 during runs, timings not reported).

## r2: mod side (server game test)

- `WorldgenGameTest.checkConnectors` reads back every pane, iron bars, fence and wall of a placed start and compares its state with `Block.updateFromNeighbourShapes` (what Minecraft would give it from its world neighbours). Hard failures: an arm to nothing, or a post that should join. Called from the flat-plains and fixture town tests and the new `desertCityConnectorsMatchNeighbourShapes` (flat desert, first seed below 60 with a city near the origin).
- Result: no post, no dangling arm. fixture town 565 panes 0 reshaped; plains town 3708 panes, 2 differ only by a missing arm toward a crafting table (furniture placed against a window from inside; vanilla would connect, ours does not; harmless, soft count logged); desert city 3403 panes 0 reshaped. No fences, bars or walls in these towns, so those kinds were not exercised (the test covers them when they occur).
- So the mod places pane states correctly; the 19.45.26 pane is not a state bug in these towns. Not reproduced; in-game `/fbnote` at 1266,2411 remains the step.
