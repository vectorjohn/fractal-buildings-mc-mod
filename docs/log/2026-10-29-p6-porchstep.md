# 2026-10-29 p6-porchstep (6.19 PORCHSTEP)

**Cause.** The slab block stands on the ground, so the sill (feet) of every ground-floor door is one block over the ground in front, also on flat land. `InstancePassV1.steps` ended the search at depth 0 when the ground in front was within `[oy, oy+1]` (a walker may drop a block, STAIRGROUND), so it placed no step for rise 1; the walk models (`StairGround`, `RasterEntranceWalkTest` with its one-block jump rise) never asked for one. `SiteLevel` (forecourt) also left the door cell plain when the approach was one row deep (lot edge = door row). John's shots (17.46.21, 18.39.40, 20.36.04/35) are exactly this. Not one commit's regression; found by counting, below.

**Measure.** `RasterEntranceWalkTest` (gate windows 0 and 2, 6 districts, exterior + interior scenes) now also counts doors where no reachable standing surface of the outside cell is within half a block of the sill: 190 of 351 doors before, 0 after. The walk also limits rises onto anything a building placed (landing, forecourt, first step off a street) to a half step (stair after stair stays a flight): 5 unreachable doors before and after.

**Fix.** `InstancePassV1.steps`: outer loop `stepLo = oy + 1` then `oy`; the plain drop (old behaviour) only where no route with a step exists, so lots with no room keep their invariants. `SiteLevel`: step block on the door cell for approachRows 1 too.

**Invariant.** `entrance-reachable` (`Invariants`) also accepts a step standing on the ground itself as the end of the walk: a door in the lot edge row has no plain ground cell left once its front cell is a step (lot edge: 4 cells of the city windows).

**Tests.** `PorchStepTest` (new; flat:70 + two slopes; fails with `stepLo = oy` only: mutation checked). Updated for the new meaning: `InstancePassV1Test` (flat land has 1-4 steps, was 0; "steps missing" control uses flights of 2+ steps), `StairGroundTest.aDoorWithoutStepsFails` (2+ stair boxes), `ShopStoopTest` (a stoop is 2+ landing cells; the one front step is not one). Goldens regenerated (every building with an entrance door changes).

**Known issue.** 5 side doors of metropolis blocks and 1 highrise: the flight ends with a full-block drop to a cut street (20.17.47 family); `steps` only knows the lot's ground. `KNOWN_FOOT_JUMPS = 5` ratchet. CLI `check --seeds 0..249` (house, shop, workshop x 6 styles, default terrains/lots/fronts): 288000 cases, 0 failing.

**Round 2.** Double doors step under every leaf; the wide-door flight search is kept (the 1-up leaf gets its own step) instead of dropping to the plain drop. Known issues: PorchStepTest's Manhattan-1 escape does not guard double doors; highrise-3 setback shifted one block in the golden regeneration.
