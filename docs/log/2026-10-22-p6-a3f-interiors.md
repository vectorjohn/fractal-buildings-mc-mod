# P6-A3F: interiors clear terrain, RAIL shape, full-interior ownership test

Follow-up to A3 (cell raster) from its verifier's findings.

- `PieceRaster.Grid.of` fills every room part (`BuildingIr.rooms()`, world-transformed) with air (-1) before the boxes, and the grid hull grows to include them. A building therefore owns its interior air and ground yields to it. Volumes are not used (roof space and stilt zones stay untouched). Boxes overwrite the air, so solid furniture or stairs inside a room win.
- `Shapes.of`: `RAIL` shares the `FENCE` connect mask.
- `RasterOwnershipTest` now runs on scenes with full interiors (`exterior=false`, used by all its tests) and the ownership test also on exterior-only scenes. It asserts every room-part cell is emitted and building-owned.
- New `ShapesTest.railConnectsLikeAFence` and `roomPartsAreBuildingAirThatGroundYieldsTo` (ground box over a room yields; neighbours outside stay ground).

Numbers (6 gate districts): exterior-only 544,643 cells, 87 air clears; with interiors 1,001,784 cells, 382,153 building air cells, of which 380,154 are room-part cells counted by the independent check. Test terrain has flat ground boxes that do not enter rooms, so "ground cells yielded" stays 0 there; the unit test covers yielding.

Verification: the shuffled-chunk determinism test and doors/stairs facing test pass on full interiors (6,456 door cells, 3,590 stair shapes). Mutation: removing the room fill fails `ShapesTest.roomPartsAre...` and `everyCellHasOneOwnerWithInteriors`. Looked at `planner-core/build/raster/district-0.png` (top-down ignores air, so unchanged and sane).
