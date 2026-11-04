# 6.42 TILEPLANS fix: raw tile plans 50 to 32

Coder, 2026-11-04, branch `claude/p6-tileplans`. Follows [the diagnosis](2026-11-02-p6-tileplans.md).

- Stack tally of the 50 raw plans (temporary trace): 30 first demanded by `drawOne`, 9 by `alternateApproach` candidates, 11 by plan/trim. All came from `streetLevel`'s 3x3 tile read, not from the prefetch alone (the prefetch change alone left 50).
- Fix: `streetLevel` skips a neighbouring tile when the column is farther than `STREET_SLACK + extra + plazaExtra` (`Districts.MARGIN` 8 + street reach 6 + 34 plaza slack) from the tile's rectangle (streets are kept within `MARGIN` of the tile, `Districts`). `drawApproach` prefetches the exit station's tiles first and the rest only when the exit meets a street; the prefetch uses the same filter (`prefetchAround`).
- Verification: PerfCascadeTest (`:mod:test -Dfb.perf.cascade=1 --tests '*PerfCascadeTest*'`): raw plans after neighbours 50 to 32 (s2_m2 21 to 14, s1_m8 5 to 3, s2_m6 24 to 15); chunk fills 13315 to 11326 (fewer tiles planned). Road output: temporarily made `streetLevel` compute the unfiltered result as well and throw on any difference; full `:planner-core:test :mod:test` passed with it (no mismatch), then removed it.
- Ceiling 46 untouched.
