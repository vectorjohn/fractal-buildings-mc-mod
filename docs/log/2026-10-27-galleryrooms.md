# 2026-10-27 GALLERYROOMS

Problem: `city-gallery` failed `furnished-rooms-walkable` ("r0 (gallery)") in town-walled dm6_m24, dm6_m25 and town-walled-hills d7_m11.

Diagnosis (city seed 2, site -797,-3872 town 300, `--interiors`, voxel dump of the room): the gallery is a 34x3 lane, not too narrow, and the rule is right. The chest (x=28) stood inside solid blocks. `InstancePassV1` adds `WalkOps.joins` after the shell is furnished; where a neighbouring tower doorway is lower than the walk (k < 0) it fills the gallery across with a stair ramp over `-k` columns and a closing wall one column further. The chest's use cell was inside that wall, so it was cut off. Nothing else in the room was wrong (door at x=19 reaches the whole west side).

Fix: `BuildingType.walkClear(shell, walk)` (default identity); `CurtainType` overrides it with `WalkOps.withoutCutHooks`, which drops hooks at x <= -prev (west) or x >= w-1+next (east) when that step is negative. Called in `InstancePassV1` beside `walkJoins`. A dropped chest is cosmetic ("a chest now and then"); the gallery keeps its lanterns elsewhere. No ASSUMPTIONS row (the rule is unchanged).

Verification: new `RoomWalkTest.aCurtainGalleryHasNoFurnitureInsideItsStairCuts` (80 seeds, 36x9 lot, Walk(70,-9,-9)); with the fix line commented out it fails at seed 1, with it it passes. `city` on the three scenes: 0 violations (dm6_m24 floorplan shows the chest gone, light kept). `city-check --seeds 1..3 --sites at:-797,-3872,town,300 --scenes auto:20`: 0 failing. Goldens: none known to change (hooks only on walled-town curtains with a lower neighbour); full suite result in the report.
