# p5-hub-square: hub square on real terrain

Bug from DL's verifier: on 6 of the 9 gate windows the hub square was dropped by `Grading.plaza` (relief over 6 or any water), so the hall and temple got no lot either.

Cause: SK places the hub on the coarse probe; exact terrain disagrees (lake edges, hills). The old fix-up tried 48 blocks of shifts only.

## What changed (`Districts.landmarksAndPlazas`, `hallLot`)
- Plaza search: shifts on the 16-lattice, ring 4 (64 blocks) then ring 10 (160), inside the tile with margin; at each reach full size then shrunk (4 blocks a side per step, 3 steps, min 16). First round wants 22 dry blocks all round (room for hall and temple), second takes any grading spot. Candidates are screened with the memoised exact `dry()` before `Grading.plaza`, and only clash with other open squares (hall and temple are re-placed anyway).
- `hallLot`: tries all four sides of the square and slides along each (4-block steps, always overlapping the side) instead of one fixed spot; checks wetness exactly (`rectFreeExactWet`) not by the conservative 7x7 flag.
- Plaza-grade rule unchanged (relief <= 6, dry). Rejected: terracing (new ground-box shapes, scene work) and exact screening in SK (needs exact probes in the skeleton).

## Verification
- `fractal-cli city --districts` on the nine gate windows: hub square on 9/9 (was 3/9), 0 violations everywhere. Hall and temple lots: all but temperate-city temple and metropolis-mixed hall and temple (the tile is mostly lake; no dry ground with room inside it).
- `HubSquareGateTest` (new): every gate window's hub tile has a square, 0 violations, hall and temple as listed, probe calls < 150k (7k-73k; before 7k-38k). It fails on the old code (no square on 6 windows).
- Plan views (Playwright, `FB_CITY_DIR`) of gate-snow-city and gate-coast-city: square with hall and temple beside it. Snow: 16x24 square, 90 blocks from the original spot.
- `:planner-core:test`, `:cli:test` pass after regenerating the river-town and mixed-city district fixtures (square or hall moved).
*Known issues:* squares can end up 50-110 blocks off the district's centre and small (16x24); the hub then is not at the road junction SK drew (roads still reach it by the district street planner). Worth a look in the viewer by John.

## Round 2 (after merging D0 and D1)
D0's whole-city `roads-connected` check flagged the moved squares: the skeleton roads ended at the old hub, and the hall lots on the square blocked the roads' ends. Changes: `Districts.plazaLanes` joins each moved square to the nearest column of each nearby street run (up to 3 within 64 blocks; lattice path, else an L either way); hall and temple lots are placed after those lanes when a square moved (`hallLots`), with relief <= 3 (a hall on steeper ground failed `entrance-reachable` at coast-city); a no-lot landmark whose spot a lot now covers is dropped; non-hub plazas (market) only search 64 blocks; `CityPlanner` takes the moved hub square's centre as the hub. Plan views in `docs/review/phase5/hub/` (snow, coast; the outlined hub box is SK's original spot, the square and lanes are drawn at their new place).
Result: `city --districts` has 0 violations on the 3 fixtures; `city-check` over the gate windows and the world map has nothing beyond the known rules. Cost: hall and temple lots are missing more often (hall: temperate, desert, border, metropolis-mixed; temple: 12 of 13 listed in `HubSquareGateTest.NO_FACING`).
