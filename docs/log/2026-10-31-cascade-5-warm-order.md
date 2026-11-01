# CASCADE step 5: the warmer warms the cascade first (A4)

Step 5 of [the JFLAZY plan](../plans/jflazy.md). Changes only the order in which pure caches fill; no answer, golden or byte of output.

- `WorldPlanner.warm` visits found settlements by (rim distance from the point, id) (`warmOrder`, `StrictMath`), not by id.
- New `WorldPlanner.linkNeighbours(site)` (settlements joined by a road-graph link) and `warm(x, z, r, quays, destinationOnly)`: with the flag only the nearest settlement and its link neighbours are warmed (their cells' links, junction fields, quays).
- `RegionWarmer.warmAt` after `plan(ix, iz)` submits the 8 neighbour tiles' `plan` as one fire-and-forget worker task (counted in `LaneStats.warmBegin/warm` so the perf settle sees it). It never waits on it, so the worker pool cannot deadlock on it, and the destination plan always comes first. Skipped under a hold.
- `warmPoint` (the TPHOLD path) does the restricted pass, completes the hold's future, then runs the full pass and the neighbours after release. TeleportHold's 90 s cap and `releaseOnPlan` are untouched. PortalHold does not call the warmer (it waits for nothing), so it is unchanged.
- Tests: `WarmOrderTest` (distance order with ties by id; mutation: sign of the radius term fails it; destination-only then full warm equals cold answer).
- No timings taken (step 7 measures).
