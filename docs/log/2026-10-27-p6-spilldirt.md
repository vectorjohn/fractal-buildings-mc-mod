# p6-spilldirt: the concrete->dirt [unknown] order-test flake

Flake: `shuffle2` once reported 2 `light_gray_concrete->dirt [unknown]` cells at 1287/1288,65,-9504 (concrete above and around, podzol/dirt below), 1 of 17 runs at 47ebbc0f (before ROADCLEAR2).

Finding (classifier, not the generator): the cells are the south row of a chunk (z -9504 = chunk edge) next to the chunk to the north; two adjacent cells are the near edge of a 2x2 dark oak base. `DarkOakTrunkPlacer` calls `setDirtAt` under all four base cells regardless of whether the trunk cell is free; the log is placed only where free. A tree of the neighbour chunk that decorates after our write therefore leaves dirt under our concrete (log blocked by it); in orders where our write comes later it covers the dirt. That is the same vanilla spill ADR 0003 D5 lists; the pre-existing `treeAbove` rule only knew a log directly above. Buried dirt under road concrete is invisible; ROADCLEAR2's TRUNKDIRT does not apply (no log in our air). The ledger-based gate stays order independent. I could not reproduce the exact flake on demand (1 in 17 on a loaded host); the mechanism is reasoned from the tree placer plus the earlier p6-spill-dirt trace (setDirtAt flags 19), and by a second hit of the same family below.

Second hit: the first run of the new code failed `spiral` with 1 `air->dirt [unknown]` at 1228,72,-9552 (dirt, air above and below, dirt to N and W): the same 2x2 base cell whose log was blocked/removed, in claimed air.
Third: spiral again, 1263,68,-9584, an isolated dirt block on the street surface (air around, cobbled_deepslate below): a REAL leftover. `StreetAirFix.restoreBase` returned when the plan gives air in the base cell, so a trunk base on the street surface stayed after its log was removed. Fixed: a base cell the plan gives as air becomes air.

Fix (tests only, `mod/src/gametest/.../order/`): `Spill.trunkBaseBeside(have, level, x, y, z)`: have is dirt/podzol/rooted/coarse dirt/grass and one of the 8 neighbouring columns has a log at y+1 or y+2. Applied after `solidKind`/`airKind` return unknown, for solid and air cells. Not widened otherwise: an isolated dirt cell with no log near it still fails.

Verification: see .claude/reports/p6-spilldirt.md (order-test full runs, evidence folders).
