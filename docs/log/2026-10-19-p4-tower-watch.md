# 4.6 tower: watchtower variants (gate follow-up)

John at the Phase 4 gate: towers do not look like watch towers; wanted spindly legs, a distinctive larger room on top, windows that are holes.

## Done
- **Stilted lookout** (`TowerType.stilted`, `structure/Stilts`): a 5-7 wide single-storey room on four 1x1 BEAM posts (4-8 high), a top ring under the floor, optional X braces on all four faces, open space under the room, and a STAIR flight (each step on a block, so the flight is one connected zigzag) up the front wall to the door. The passes build the room in its own frame; `ShellGeneratorV1.assemble` lifts it (see plan Deviations).
- **Top-heavy tower** (`TowerType.topHeavy`): shaft of n-1 or n-2 storeys plus a jetty room of 1-2 storeys overhanging 1-2 blocks on every side (existing `MassingOps.jetty`, core through both).
- **Unglazed windows**: `facade.unglazed` style key (default off) and `OpeningPass.run(c, cutter, unglazed)`; the tower draws holes for 60 % of watchtowers and 25 % of classic towers.
- Variants drawn from `tower/variant` (18 % stilted, 18 % top-heavy), falling back to the classic shaft when the envelope is too small.

## Invariants
None weakened globally. Stilts: storey 0 is the open leg zone, the room's volumes start above it (`no-gap-below` reads volumes at storey 0 only), each leg is its own 1x1 volume that must have a solid column from the ground (missing or short leg fails `no-gap-below`), and without legs and stair the room fails `no-floating`. Window holes: `Invariants.floodOutside` treats air inside a *declared* `window` opening as closed; an undeclared gap still fails `shell-closed` (tested).

## Verified
- `TowerTypeTest` (9 tests): variants appear (300 seeds, 13x13: >= 20 stilted, >= 15 top-heavy, >= 100 classic) and pass all invariants; stilted legs are BEAM from y 0 to the floor at each corner, air under the room, stair step by step with headroom ending at the door; top-heavy room overhangs the shaft on all sides; holes buildings never mix holes and glass and keep the shell closed; leak control.
- Mutations: removing `hole[ni]` from the flood: 4 tests fail (`shell-closed`); starting the posts at y 1: `no-gap-below` in 4 tests.
- `fractal-cli check --types tower --seeds 0..199 --lots 11x11,13x13,15x15`: 67200 cases, 0 violations. Golden `tower.txt` regenerated (only tower digests changed).
- Gallery `tower-medieval` before/after (Read): seeds 4 and 11 are now stilted lookouts (small roofed room on dark legs, stair on the front), 6 and 10 top-heavy, 1, 3, 5 show window holes; the other seeds unchanged.

## Known issues
Stair has no rail and its foot has no foundations on slopes; `entrance-reachable` does not check the lifted door; dormer panes stay glass with holes; lookouts need an envelope >= 7 wide (lots >= 9 wide) and get 4-8 high legs only as the width allows.

## Round 2 (verifier: lifted door unreachable on non-flat ground)
Dropped the stilts' own stair. The site pass now builds front steps (foundation-backed) from the lowest room storey's exterior door, and `entrance-reachable` checks that storey's doors (general; storey 0 for all other types). Step headroom check widened by one cell (the ring under the floor blocked a step-down). Tests: `stiltedLookoutsReachTheGroundOnAnyTerrain` (flat, two planes, two noises), `aLookoutWithItsStairRemovedFailsEntranceReachable`; mutation (entranceStorey -> storey 0) fails 3 tests. `check --seeds 0..49` all types: 207200 cases, 0 failing; tower 0..199: 89600, 0. Golden tower.txt regenerated; gallery re-captured (seeds 4 and 11 now have a ramp to the ground) and pack PNG recopied.
