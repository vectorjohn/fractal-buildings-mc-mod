# ORDERSIM S4b: regressions SC2 and SC3, and the mutants

Branch `claude/p6-ordersim-s4b`. Test-only (mod/src/test/.../ordersim/); no production change.

## What was done
- `RegressionScenarios` builds the scenarios from `ScenarioFinder`'s spots on gate-temperate-city: a 3 x 3 inner window, no hashed features, one scripted feature (the first rng seed in 0..63 that places on bare terrain).
- `OrderSimRegressionTest` (7 tests, fast orders, all checks pass on the current code):
  SC2a/b lake vetoed in all 6 orders, veto counted; SC2c lake placed in all 6; SC3 on-surface, buried, cut-headroom and the new street case (below) pass with liveness.
- `OrderSimMutantTest` (12 tests, forward and reverse): all 11 mutants of the plan's table, each asserted to fail its check, with a control (no mutant, same scenario) that must pass; plus LAKES on SC2b.
- `ScenarioFinder` (S2's file, additive): `trunkSpots(fromNeighbour)`, `trunkSpotFromNeighbour`, `trunkSpotInStreet`, and `lakeUnderBox` now skips spots another building's deep foundation also meets.

## Findings that changed the plan
- **SC3 spots must be grown by the neighbour chunk.** `DarkOakTrunkPlacer` puts the 2 x 2 base at origin..origin+1, so the chunk of the tree is the chunk of the base's minimum corner. The finder's first spots had the street column as that corner: the street's own chunk grew the tree, and its pieces ran after it in every order (nothing order dependent). `trunkSpotFromNeighbour` keeps spots where the corner is the non-street column.
- **TRUNK_BASE needs the whole base on the street.** With a log left beside the dirt (base half on open ground) `SpillKinds.trunkBaseBeside` rightly accepts the dirt, so the mutant is invisible. `trunkSpotInStreet` finds a 2 x 2 base wholly on street columns across a chunk line, street cut 2+ below the ground: then the mutant leaves `dirt` in planned air and reverse fails C3 ("unknown air", 2 cells).
- **LAKES needs the lake inside the wall.** The finder's SC2a origin is 6 blocks outside the wall, the lake's rim barely reaches it and rarely carves (no failure). Moving the origin 4 blocks east (still outside the box, grid still meets it, still vetoed) gives 1 unknown solid cell in reverse (the wall is built, then the lake writes). Fragile in cell count (1), deterministic.
- **Histograms are empty on every SC3 scenario**: the passes restore everything, so there is no accepted spill left to pin (plan: `light_gray_concrete->dirt` / `air->dirt` classified spill). `restoreBase` fires (2 writes) on on-surface, cut-headroom and street.
- **Deviations from the mutant table**: SPILL_LOGS is caught by **C5** ("SpillClear.run cleared a log"), not C3; EARLY_STEP by **C1** (the structure-step check, as S3 built it), not C3.

## Not reproduced (plan's two manual checks)
- `trunkBaseBeside` returning false: SC3 still passes (no accepted spill left, as above). The classifier is not exercised by these spots on the current code.
- `claimsNear` without deep-foundation growth: SC2b still vetoed, because the building's ground piece box covers the same volume here. The deep-foundation growth is redundant on this city. Instead SC2b is shown sensitive with LAKES (53 unknown cells forward): had the veto missed, the lake damages the foundation.

## Cost
Regression class 10 s (7 tests, loadavg ~30, so a busy host); mutant class 29 s (12 tests, loadavg ~20), mostly first-use warm-up of planner and SC1 plan. One scenario in 6 orders: 1 to 4 s under that load.
