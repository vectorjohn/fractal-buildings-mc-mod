# 3.5 / V1: viewer scene format and instanced mesh

- `ir.ts`: fb-scene/1 types plus `parseScene` (integers, enums, lot and building references, terrain length; errors read `fb-scene: buildings[0].shell.boxes[3].role: ...`). The old irVersion-0 smoke parser is gone.
- `voxels.ts`: `sceneVoxels` (shell then site boxes, air clears, placement per Placement.java via `placeColumn`, sorted building/y/z/x), `terrainVoxels`, `sceneBounds`.
- `palettes.ts`: `ROLE_COLOURS`, `STYLE_PALETTES` (plain, medieval), magenta for unknown.
- `render.ts`: `buildSceneObject(scene, {colour, style})` returns a Group with child groups terrain/foundation/shell/interior/roof/violations (violations empty, for V3); `frameCamera`. One InstancedMesh per non-empty layer; its `userData.voxels` holds the voxel list (for picking/V2).
- `main.ts`: `?ir=`, `?fixture=`, `?colour=palette`, `?style=`, a role/palette select; camera looks at the front (-z side).
- Tests: Vitest 17 pass (parse errors with paths, 196 voxels with per-role counts foundation 14, floor 70, wall 69, roof 35, door 4, window 4, four rotations of a 5x3 footprint, terrain). e2e smoke: voxels 196, roof reddish / walls beige fractions pass.
- Look: `viewer/out/smoke.png` shows the house on a green terrain slab, grey foundation, blue floor slabs, beige walls, orange door, cyan window, stepped red gable roof.
- Removed the box5 viewer test (Java CLI test still reads `fixtures/box5.json`; left in place).
