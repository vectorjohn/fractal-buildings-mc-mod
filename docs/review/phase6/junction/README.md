# Junction heights: before and after (JUNCTION)

Round 1 listed the worst junctions on the gate windows; round 2 fixed them in the planner (see [the log](../../../log/2026-10-23-p6-junction.md)).
Pictures: the viewer's 3D district scene, fly camera from the south-east, before (`claude/run-phase6`) and after.

| Picture | Where | Before | After |
|---|---|---|---|
| `temperate-a-before.png`, `temperate-a-after.png` | gate-temperate-city 958,-9426 (d5_m60): arterial `a06_m9600` ends on border street `b06_m9424` | the arterial stops 20 blocks above the street it meets (82 and 60), a sheer wall of fill | the arterial ramps down at 1 in 3 and meets the street flat |
| `temperate-corner-before.png`, `temperate-corner-after.png` | gate-temperate-city 1104,-9600 (d7_m60): four arterial segments at one corner | 83, 80, 83 and 97: a tower of road | one flat crossing |
| `badlands-before.png`, `badlands-after.png` | gate-desert-badlands 6876,-6724 (d42_m43): three arterials at a corner | 106, 85 and 81 | one level, cut into the slope (a deep cut: the corner sits between the streets' grounds) |

In game: seed 987654321, `/tp 990 100 -9394` (temperate) or `/tp 6905 125 -6695` (badlands), spectator.
