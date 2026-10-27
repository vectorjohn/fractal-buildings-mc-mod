# ORDERFAIL: order test failed at s1_m10 (lava lakes in dug-in buildings)

Lead-coder diagnosis and fix, 2026-10-27, branch `claude/p6-orderfail` (from `claude/p6-testlayers`).

## Symptom

`scripts/order-test.sh` at e7e1e6cf: clean-centre, forward, shuffle1, threads and threads_evict reported the same 52 `unknown` solid cells
at the gate city s1_m10. The other orders passed. The ledger digest was the same in every order, so the planner was order independent.
The world was not.

## Cause

- The server log's cell lines (`[B2] unknown solid cell`) put them all around 1307,66,-9435: building blocks (cobblestone, stone bricks,
  mossy cobblestone) turned into lava with cave air above, and planks and panes turned into cave air. That is the inside of a vanilla
  lava lake: `LakeFeature.place` (26.3 bytecode) moves its origin by (-8, -4, -8), fills a 16 x 8 x 16 grid with fluid below its level
  and `AIR` (cave air) above, and puts a stone barrier on the shell. It replaces anything outside `#features_cannot_replace` and has no
  structure check (CITYQ r7 had already seen the barrier).
- Order dependence: when a lake's origin chunk is decorated after the neighbour chunk that holds our piece, the lake overwrites the
  piece. When it is decorated before, our piece overwrites the lake, and the lake's shape test also reads our blocks. So it is a real
  difference in the world, not a classifier gap.
- Why now: HEAVY2 bisected it on beebox. 6e0eb94c (DEADMAIN) passes and 48791604 fails with the same 52 cells, so the trigger is 499b82ee,
  7.17 SHOPLOW S2+S3 dig-in. The dug-in walls now reach down into the lake's grid. Before that the lake was in the ground under the
  city, where it was harmless.
- A gap found on the way: `BuildingPiece.box` does not cover the deep foundation that `Pieces.deepen` adds when rastering
  (`FOUNDATION_BELOW` = 12 blocks under the lowest planned foundation). The new raster cross-check test caught it: cobblestone at y 61
  under a box starting at y 72. `claimsNear` grows the box by that depth. The box itself was left alone, because `TerrainStats` volumes
  and `DistrictStructure` read it.

## Fix

- Core: `WorldPlanner.claimsNear(Box3)` answers whether a piece of ours could own a cell in a box, or read the world there. It is true
  when the box meets a building piece's box (plus the deep foundation) or a ground piece's box of a planned tile. It is also true when
  the box lies within `RoadSurface.CUT_REACH` + 1 of a road column and reaches above `deck - 24`. It is pure.
- Mod: `LakeFeatureMixin` (HEAD of `LakeFeature.place`) asks `LakeVeto.vetoes`. A lake whose grid, grown by one block, meets our claims
  is not placed. This depends only on the lake's position, so it gives the same answer in every chunk order. Lakes far below a city or
  a road are still placed. The mixin is overworld only, and on a failure it places the lake (vanilla) and counts in
  `WorldPlanners.FAILURES`.
- Mutant `lakes` (`Mutations.LAKES`) turns the veto off. `order-test.sh` runs it as `mutant-lakes`, and it must be caught by "not a
  known vanilla spill". Every order run reports `lake veto: N lakes not placed`.
- `RoadsClientGameTest` gets a cell-level diff (`CELLDIFF`, and "cells that differ ... road N, district M in K chunks") for the ROADCLEAR
  data point below.

## Tests

- `WorldPlannerClaimsNearTest` (planner-core, about 6 s): every building and ground piece corner is claimed. In the boxes it calls free,
  no piece of the tile rasters a cell (this is the test that found the foundation gap). The deep underground under a district is free.
  Roads claim their deck, their reach and down to `ROAD_DEPTH`, but not below it. The answers are the same from a cold planner asked in
  reverse order and after `evictAll`.
- Order test results are in the report `.claude/reports/p6-orderfail.md`.

## ROADCLEAR snowy-site data point (seed 1709797029209580947, 4500,1700)

This is not the lake mechanism, and the world was not order dependent here. `RoadsClientGameTest`'s digest covered the whole
`PieceLedger` recording. The new `CELLDIFF` lines (beebox job 22, commit 71ad7262) showed what differed between asc and
shuffled-evicting: 7548 district cells, 0 road cells, in chunks 10..13,14..16 (around 175,55,256). Those cells are present in one run
and absent in the other. That is a settlement near the world spawn, 4300 blocks from the corridor. The client loads the spawn area at
the same time as the test, so its district cells land in the recording in some runs and not in others. That is timing in the test
harness, not order dependence: road columns and road cells were equal in every order. Fix: the digest (and the diff) covers only the
corridor chunks and their 8 neighbours, which every order decorates, and it logs how many recorded cells it left out.
