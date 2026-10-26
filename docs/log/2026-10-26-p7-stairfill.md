# STAIRFILL: no stair blocks under a step (2026-10-26)

John (REVIEW 6.8, `987654321-hamlet-4-interior.jpg`): the blocks under a staircase were also stairs.

Cause: not the mod. `InteriorPass.flight` fills each step column as a `Role.STAIR` box from the floor up to the step. `Shapes.stair`
(planner-core raster) then gave every cell of the column a stair shape, because each had a higher solid cell ahead (the next, taller
column). `BlockMapper` just maps the shape it is given.

Fix: in `Shapes.stair`, for an interior flight (a STAIR opening supplies the ascent), a STAIR cell with another STAIR cell directly above
it is a full block (`Shape.NONE`, the material's plain block). Only the top cell of each column stays a stair. Exterior steps (no ascent hint) unchanged. The IR is
unchanged, so the viewer (role-coloured boxes) looks the same and no goldens changed (full planner-core suite passed apart from the one
test below).

Tests: `StairRiseTest.noStairBlockUnderAStairOfTheSameFlight` (6 types x 2 styles x 6 seeds, raster level; fails with the fix removed);
`StairRiseTest.everyFlightOfEveryTypeAndStyleHasNoJump` (stair-rise invariant) still passes; `RasterOwnershipTest.doorsAndStairsFaceTheRightWay`
updated to allow a full-block support under a step.
Not run: a mod game test (the mod code is not involved), client screenshots.
