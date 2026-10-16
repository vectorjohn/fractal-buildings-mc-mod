# 3.9a Voxel grid and invariant checks (S4)

Done on `claude/p3-s4-invariants`.

- `core/voxel/VoxelGrid`: dense local-frame grid over shell bounds plus all boxes (so it reaches y = -1 for the foundation), role ordinals as bytes, shell then site boxes, `air` clears, `(y, z, x)` iteration, world mapping via `Placement`.
- `core/check/Invariants.check(SceneDoc[, TerrainProbe])`: `ir-valid` (from S1's `IrValidator`, listed first) then per building the nine geometric invariants of the plan table, in enum order, cells in local (y, z, x) order, max 64 cells per violation with the total in the message. Null probe means the scene's patch, columns outside it have floorY -64. `InvariantReport` accumulates cases and renders markdown.
- One outside-air flood (non-air roles all closed, grid plus one-cell margin) serves `shell-closed` and `roof-closed`. The walk for `rooms-reachable` follows the plan (dy in -1..1, 2-high clearance on both step up and step down).

## Verification

- `nix develop -c ./gradlew :planner-core:check` passes.
- Tests: `InvariantsTest` (sample clean; each listed mutant fires its invariant; ridge-removal fires exactly roof-covers + roof-closed; wall-instead-of-door fires only rooms-reachable; floating site block and missing foundation fire only their own id; rotations 1-3 pass; cap/ordering/determinism; step-climbing walk; report), `VoxelGridTest` (196 and per-role counts), `ViolationsFixtureTest` (writes via S1's `IrJson`; fails if the fixture is stale).
- Mutation checks: disabling vertical moves in the walk fails `walkClimbsOneBlockSteps`; disabling the outside test in `roof-closed` fails the ridge test.
- Speed: 10,000 sample checks about 0.3 s (3.0e-5 s each).

## Notes

- `no-gap-below` uses "lowest solid voxel != floorY" so buried floors also flag (a basement would need a rule change). Base volumes are those with box.y <= storey 0's y.
- Room voxels more than one cell outside the grid are ignored by `shell-closed` (that is `ir-valid`'s job).
- Known gap kept from the plan: doors are not checked to be reachable from the terrain in front.
- Build change: `planner-core/build.gradle` test task also lists the violations fixture as an input.
