# 6.25 MAINSTUB follow-up: core tile on the whole-settlement path

Branch `claude/p6-mainstub-core`. The world's lazy plan never cuts the core tile (`ExitTrim.inCore`); `CityPlanner.plan` (CLI, viewer) and `planTileTrimmed` did, so pt1573 s6_m7 tile 44,-43 clamped r6_m7_2 at the tile's last column (7199,-6815) where the world keeps it to 7208.

- Fix: `CityPlanner.plan` and `planTileTrimmed` skip the core tile (new `ExitTrim.inCore(src, ix, iz)`), matching the lazy path.
- Test: `MainStubTest.theLazyCutEqualsTheWholeSettlementCut` now walks every settlement of pt1573 that has a cut, plans it with `CityPlanner.plan` and compares each tile with `WorldPlanner.plan` (JSON). On 9a6c5d45 it fails at `s6_m7 tile 44,-43`; with the fix it passes.
- Docs: ROADMAP 6.25 note refreshed (round-3 rule; 20.02.18's bridge is cut); NOTES line on the perf row "Region layer + road network, cold" (~11.3 s on beebox over its 10 s ceiling, base and head alike).
