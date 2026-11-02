# 6.42 TILEPLANS: why the CASCADE raw-plans row went 38 to 50

Coder, 2026-11-02, branch `claude/p6-tileplans`. Diagnosis only; no code change, the ceiling (46) is untouched.

- Row: `cascade.neighbours.rawPlans`, from `CascadeCount` (planner only, deterministic; `-Dfb.perf.cascade=1 --tests '*PerfCascadeTest*'`, ~2-4 min, no quiet host needed).
- Bisect (same count in repeated runs): `e8c5740e^` 38, `e8c5740e` (LONGDECK) 50, `e778c8e3` and `aed627b5` 50 (no code differs from e8c5740e), `f949e57a` (F3) 50, run-phase6 head 200b53eb 50. Culprit is LONGDECK; F3 and later change nothing.
- Per settlement (after neighbours): s2_m2 9 to 21, s1_m8 5, s2_m6 24 unchanged. Chunk fills identical (12674 / 13297), so the cost is only in plans.
- Where the plans come from (stack sampling of `rawPlan`): about 70% `drawApproach` (prefetch of the tiles around the first 24 stations, line 1243, and `streetLevel`, 685/1254), from `drawOne` and from `alternateApproach` (WorldPlanner ~1200). Not `onLots` (disabling it changes nothing).
- Why: LONGDECK lets approaches that used to be dropped as too-long decks route and draw (k2_1_0 now reroutes as a 503-block detour; more alternate-gate candidates pass `routeNodes`). Each candidate that reaches `drawApproach` raw-plans the 3x3 tiles around its first 24 stations that belong to the settlement; s2_m2 is the big settlement there.
- Options (not done, John's call): (1) accept as `knownOver` at 50; (2) in `drawApproach`, prefetch/plan tiles only for the stations actually read (stop at the first station with no street) and skip `alternateApproach` candidates by cheap wet/length test before drawing; both change which plans exist, not the roads, but need the corpus tests re-run.
