## Result: done (plan)
## Did
- Wrote docs/plans/metrofill.md (ROADMAP 7.20 METROFILL); plan link added under the 7.20 item.
- Reproduced the architect's numbers on gate-metropolis-mixed (core 51.8 % empty, frontage 45.7 %, arterial+main 47 %, 1 block lot; plan 18.7 s).
- Diagnosed why whole-block lots are rare (temporary instrumentation in a scratch build, not committed): of 501 grid-interval candidate blocks, 485 fail ringIsStreet (118 with no ring street, ~200 missing one or two sides, 53 missing under 20 %); only 5 reach the class-table draw. So the class table is not the cause; W1 fixes candidate finding and ring tolerance.
- Checked the code facts the steps depend on: shop massing grows its main block to the whole envelope depth and high-rise podiums take nearly the whole envelope (deep lots need a depth cap); relief is measured over the whole lot in SpecPolicyV1/Briefs; LatticePath is 8-connected; arterial vs border street comes from RoadNetwork.kind + Arterials.is; quays and lattice streets have to stay pointwise.
## Commits
- 553d6c60 7.20 METROFILL: lead-coder plan (docs/plans/metrofill.md)
## Verification
- city --districts on gate-metropolis-mixed + node fill-metrics.js -> same numbers as the architect (run).
- blockLots rejection counts (instrumented scratch build, reverted) -> listed above.
- No tests run (plan only). heavy: none. flakes: none.
## Not done / blockers
- none (plan only)
## Design questions
- Architect A1-A9 and interior-designer I1-I2: see the plan, "Design questions".
## Notes for the coordinator
- Steps: M0, G0, DX, R1, R2, R3, W1, L1, DG1, DG2a, DG2b, Y1, DX2, F. Wave 1 [P]: M0, DX, R1 (+G0 once SHOPLOW S4 merges: it adds LotBrief.square and edits ShopMassing/Briefs). Wave 2 after M0: L1 (after G0), W1, R2, R3, DG2a, all in parallel; merge the Districts.java steps one at a time and regenerate generated fixtures on conflicts. Wave 3: DG1 (after L1), Y1 (after G0, L1, architect A2), DG2b (after DG1, DG2a). Wave 4: DX2 (diagonal comparison for John), F (checks become violations, goldens, perf, galleries, reviews).
- Deviation from the architect: R2 lets an arterial segment yield to a parallel skeleton main road (not the reverse), because cutting a main road splits a route that carries gates and region links; asked as A3, with a fallback (R2b).
- The three checks land as report-only warnings in M0; F promotes them once the 9 gates meet them.
- Diagonal removal is a program flag (PlanOptions / TierProgram.diagonals), inert by default, CLI --diagonals on|off; no globals.
- Fill rules apply only to city and metropolis (towns etc. unchanged; river-town fixture is the control).
- AvenueDistrictsTest asserts cut lanes exist; DG1 changes that test on purpose.
- Questions for John: J1 diagonals (after DX2; on until then), J2 do yards count as filled or should roof cover also be targeted.
