# MAINSTEP foliage regression (fix-forward)

FoliageClientGameTest failed with "StreetAirFix left 3 of 9 injected snow layers over air" from 6.33 MAINSTEP (38d9773e) on; the crash stopped the later client tests.

**Cause: the test, not StreetAirFix.** The injection control samples every 37th claimed-air street column. MAINSTEP adds claimed-air step cells, which shifted the sample (seed 31337: 9 snow injections instead of 8). Diagnostic run: the 3 layers left sat at y = frontY + 4 in the air-rights strip of a lot (d2_m4.l1, l7, l8), not in a lot column. `StreetAirFix` leaves a cell a building may own (`Lot.allows`: lot column, or air-rights strip at least `AIR_CLEARANCE` = 3 above the street) on purpose, so those injections were invalid. 7/9 had passed the old 3-in-4 bar before only by luck of the sample.

**Fix** (`FoliageClientGameTest.inject`): skip a column where a building may own the snow cell or the trunk cell (`lotMayOwn`, the same `Lot.allows` rule). With those gone the bar is tightened from 3-in-4 to all injected snow layers removed. Result on seed 31337: snow 6/6, trunks 6/6, 6 bases restored. No planner or mod main code changed, goldens untouched.
