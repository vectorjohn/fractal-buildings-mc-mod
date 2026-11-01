# CASCADE step 3: A2, short-circuit link rule

Branch `claude/p6-cascade-3`, base `claude/run-phase6`. Step 3 of [plans/jflazy.md](../plans/jflazy.md). No timings taken (step 7).

## Done

- `WorldPlanner.drawOne(site, owner, i)`: memoised (`drawnMain`) draw of the i-th skeleton approach; `approachRoads` builds its main approaches from it, so the full rule reuses it.
- `exitServedAt`: a main approach whose exit is within 2 blocks of the gate, drawn, with no port or a link not DROPPED, proves true. Otherwise the full rule runs (alternate approaches depend on all the mains).
- `served(owner, other)`: a drawn main approach to `other`'s port proves true (it is in `approachRoads`, so the full set has it). Otherwise the full `computeServed`. Skipped when `servedLinks` already holds the answer. IAE (recorded window) falls back to the full rule.
- Oracle: `-Dfb.linkrule.reference=true`, or `fullLinkRule()` in tests. `LinkShortCutTest` (pt1573, w12345, w987654321): every exit gate (1,316: 349 served, 967 not) on a fresh planner per settlement; every graph edge (234: LIVE/DROPPED/DEAD_END 175/37/22) on a fresh planner per edge; three shuffled orders on one planner, then gates; fewer drawn approaches for 2 of 6 gates of pt1573 s4_m2. Only difference found: where the full rule throws IAE (a gate whose settlement a recorded window lacks part of) the short cut can answer true; never in a real world (no window). The test allows exactly that.
- Mutation: answering true for a drawn approach whose link is DROPPED fails `everyGateAnswerEqualsTheFullRule` (pt1573 s6_m9 gate 6752,-8960).

## Counts (step 1 harness, tile 14,-36, one thread)

| | before (0977cade) | after |
|---|---|---|
| chunk fills after plan | 17,890 | 16,248 |
| after neighbours | 18,243 | 16,601 |
| raw plans (after neighbours) | 52 | 40 |
| link rule fills | 10,977 | 9,335 |
| junction field / exit cut | 4,717 / 2,113 | 4,717 / 2,113 |

A 9 % cut, less than the plan hoped. Cause: 967 of 1,316 gates are not served. A "false" answer needs every approach (a not-yet-served port can get an alternate from any gate), so those still draw all approaches. A cheaper false needs a proof that no port lacks a main approach, which itself needs all mains drawn. The rest of the link-rule cost is the far settlement's skeletons and junction field.
