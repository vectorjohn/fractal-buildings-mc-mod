# 6.38 DOORREACH: `door-reaches-street`

**What.** `DoorReach` (planner-core `check/`), run by `city-check` on every exterior district scene it builds. From a building's lowest ground-floor door it walks 4-neighbour columns to a road ground box (`ROAD*` roles) with steps of at most 1 block, never through a building block in the two cells a walker occupies (so the open leg zone of a stilted building is passable), a rail, open water, or a column of another building's lot (street columns excepted). A walk that leaves the scene's terrain patch is counted separately (`edge`, "the street may be in the next district") and is not a failure; the strict form (`check(scene, true)`) is for test scenes. A failure's message says the smallest step limit (2..6) at which a walk exists.

**Report only.** Failures go to the design warnings (`## Door reach (DOORREACH 6.38, report only)` lists them; the summary line `door-reaches-street (6.38 DOORREACH): N buildings checked ...` is always written), not to the exit code, because of the recorded-world result below.

**Results** (city-check, `--scenes auto:6` gate windows, `auto:8` recorded worlds, `auto:300` hill town; before = none, no check existed):
- 9 gate windows: 961 buildings, 0 failures. Walled hill town (seed 541, `noise:5:75:25:200+river:-120:24:6`, town r410): 648 buildings, 0 failures.
- Recorded 64-block world dumps (world-12345, world-987654321, pt1573, pt3141; w1-mixed-s8): about 15,300 buildings, 26 failures (0.17 %): 20 need a 2-block step, 2 need 3, 1 needs 4, 1 needs 5, 2 none up to 6. The ones inspected (world-12345 s3_m6 d23_m40/b2) are a street laid 2 blocks above the lot's terrain with no grading between: a street-grading item (7.11), not a missing door step. No building is dropped or changed.
- Unresolved edge walks: 4 / 11 / 5 / 0 on those dumps.

**The hill-town tower (d8_m12/b6).** The assistant's throwaway proxy blocked every wall/beam column with a box at local y 0-6 and so counted the tower's open leg zone (a tower on four legs, entrance floor 5 up) as wall; the flight ends on the ground under the body, which is open on all sides (beams at y 3, 3 clear). With blocks tested against the walker's two cells above the column's surface the tower reaches the street with 1-block steps: the generator was right, the proxy wrong. No generator change, goldens unchanged (nothing in the generator touched). Viewed: `.claude/reports/p6-doorreach-tower.png` (viewer, flight and legs).

**Tests.** `DoorReachTest` (flat street reached by house/shop/workshop over 12 seeds; control: street 3 above the lot gives exactly one violation saying "a walk exists with steps of 3"; no terrain patch = not checked). `CityCheckTest`: gate windows report has no `## Door reach` section, and the new hill-town test (648 buildings, none listed).

**Making it hard later.** Once streets are graded to their lots (7.11), flip the `warnings.add` in `CityCheckCommand` to `problems.add`.
