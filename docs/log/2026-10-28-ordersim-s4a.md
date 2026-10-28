# ORDERSIM S4a: SC1, SC5, liveness, controls

Added `OrderSimTest`, `OrderSimRockTest` and a pool-exclusion hook (`Scenario.city`, `SimFeatures.hashed/pool` overloads) plus per-old-block liveness keys in `SimCompare.contracts`. Test-only; no production change.

- SC1 (gate-temperate-city, densest 6 x 6, k = 3, no `forest_rock`) and SC5 (gate-snow-city, 4 x 4, k = 3, `freeze_top_layer` in the pool) pass C1 to C3, C5, C6 in the six fast orders; C4 is printed (SC1 differing cells 45 to 134 per order, SC5 34 to 186; these are spill, not asserted).
- Liveness: SpillClear leaf clear, StreetAirFix log removal, BuildingAirClear leaf clear and FoliageClear erase fire in SC1; snow is removed in SC5 (by SpillClear.run, 131 cells).
- Controls (SpillClear, StreetAirFix, BuildingAirClear `ENABLED = false`): C3 cannot fail because the classifier calls leaves/logs/snow in air spill; instead the residue in claimed air is 0 with the pass on and 4 / 11 / 27 with it off. Recorded as a deviation in the plan.
- `forest_rock` over a claimed street cell (real, ROCKVETO): kept out of SC1/SC5; `OrderSimRockTest` pins it (asserts the failure; `FIXED` flag for ROCKVETO).
- Time: the 3 new classes about 20 s on a loaded host (loadavg 8 to 13).
