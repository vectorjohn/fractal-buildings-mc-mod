# 6.15 ROADJOIN steps 1-2 architect review (2026-10-27)

Mode: review. Round: first.
Counts: 1 violation, 2 should-fix, 4 suggestions, 1 proposed rule.

Looked at:
- Pictures opened:
  - the coder's `ApproachPicTest` top views in `.claude/worktrees/p6-roadjoin-s1/build/rj/`: `approach-3616-1440.png` (playtest hamlet s3_1, g0) and `approach-2560-1984.png` (w12345 city s2_1, k2_1_2);
  - two top views I made of the metropolis s1_2 approach k2_1_0 (John's 21.31.49 spot) in my scratchpad (`rj/approach-3201-1683.png` r90 and `rj/approach-3200-1700.png` r180);
  - John's playtest photos `docs/review/phase6/playtest-comments/2026-10-26_21.26.27.jpg` and `21.31.49.jpg`.
- Height grids from `RoadJoinProbeTest`:
  - `probe` with grids for playtest s3_1 and s1_2;
  - `probe` for w12345 cells 10,1; 4,0; 2,1; 3,7; -11,-3 (18 approaches in all);
  - `crossings`;
  - `at` around 10527,1726 and 4868,707.
- These were run at worktree HEAD `145bcc6e` (r3: "street pin at water level is lifted to the deck floor"). They are single-class test runs in the coder's worktree with no source edits. One run collided with another Gradle run in that worktree and failed. I reran it. No verifier report existed when I wrote this.
- Docs read: STANDARDS DS-17/19/30/31/44/46/50/52, ADR 0004 N5 and WR1-WR4, `docs/plans/roadjoin.md` steps 1-2, the r1 and r2 reports and the log.
- Not seen: any in-game picture of the new joins (step 5), the far (port-side) ends of the crossing decks, how the decks look over the water, and whether the cuts are open to the sky (DS-50).

## Violations

- **DS-19: approach crossings far over 20 blocks are still built, as one flat deck.**
  - Where: 3 of 18 approaches (the `wetFailures` count).
    - **Playtest metropolis s1_2, approach k2_1_0.** This is John's 21.31.49 spot. The approach leaves the plaza at 3200,1696 and runs about 65 blocks NE to about 3258,1662 as one orange deck at y 68 across the river and its islets. See `rj/approach-3201-1683.png`.
    - **w12345 city s2_1, approach k2_1_2.** The deck runs about 190 blocks south *along* a lake from the gate at 2560,1984. See `build/rj/approach-2560-1984.png`. That is John's original road-0 case, "a road straight down the middle of a river", in its plainest form.
    - **Playtest k2_1_1.**
  - Why it breaks the rule: DS-19 (accepted) and ADR 0004 WR2 allow one span of at most 20 blocks. Anything longer must reroute, or the edge is dropped. Plan step 2 kept "the attempt with the fewest wet blocks" as a fallback. That is a choice the plan made, not one the rule allows. The plan's own test bar (`wetFailures == 0`, every crossing ≤ 20) is not met.
  - What did improve: the k2_1_0 deck now sits level with the district's bridge stub and street (B68/S69 at 3201-3204,1692-1700). It is no longer flooded at the water's level. The "don't meet up" half of 21.31.49 is fixed in the plan data.
  - Fix direction:
    - (a) Look harder for a dry route: widen the reroute box, and try the approach from another exit on the rim on the port's side. In s1_2 and s2_1 the gate sits on a waterfront, facing the water.
    - (b) What happens to whatever still fails is a choice between DS-19 and DS-17. See "For John".
    - Either way, a run *along* water (k2_1_2) must never be built.

## Should fix

- **The gate still has a step on flat ground: w12345 city s4_0, approach k4_0_2.**
  - Where: the exit at 4864,704. The street ends at S67 (4867,707). The approach's first column, next to it, is A71, and the ground all round is 67.
  - From there the approach climbs at 1 in 4 to 75 by 4887,719 on an embankment 4-8 high, while the ground beside it stays at 67-68.
  - Why it matters: this is exactly John's 21.26.27 complaint (a step where the link meets the street), on flat land, at a city gate. The coder allowlisted it, which I don't accept. The street pin is meant to win at the exit; here it doesn't.
  - Fix direction:
    - At the exit station the pin must hold: the surfaces meet within 0.5.
    - Where the port is far higher, the climb belongs where the ground climbs. Near the gate, the approach should follow the ground within 2 (DS-46's numbers), not run on an embankment.

- **An approach passes another gate on a shelf 9 blocks above it: w12345 hamlet s10_1.**
  - Where: approach k10_1_0 (`maxStep 9`, "top 120 vs S 111"). At 10525-10533, 1724-1727 it runs at 118-120 directly against the end of the street (S110-111) and the k10_1_1 approach (A111-112), which leave the k10_1_1 gate at 10528,1728.
  - Seen from the street, a road crosses the top of the gate on a 9-block cliff. A cart on k10_1_0 has no way down to the hamlet it serves except via its own exit 35 blocks away.
  - Why it matters: road surfaces that touch with a big step are the same failure as a step at a join, only at the side. It also shows that the routing lets one approach wrap round its settlement past another gate.
  - Fix direction: an approach should not touch a street or another approach except at its own exit. Where it must pass near one, it either meets it at grade (a junction) or keeps at least 2 blocks of clear ground between them.
  - Also: w12345 s2_1 k2_1_0 has a step of 5 where it touches a street at 3376,916, far from its exit. I didn't inspect it; check it with the same test.

## Suggestions

- **The approach decks still carry the route's material** (the later step after ROADS1). The metropolis k2_1_0 deck must be `cobbled_deepslate` (DS-44, accepted). As drawn it is a 65-block wooden boardwalk out of a metropolis, which would stand out even if DS-19 allowed it. Track this so step 2's decks don't ship in planks at city tier.
- **No check yet on the deck's far ends.** I could not see whether the k2_1_0 deck meets the east bank level or ends in a berm (21.35.56). `limit()` keeps the profile at 1 in 4, but terracing in `RoadSurface` could still raise a berm. Extend the join measurement to both ends of every crossing (|deck - approach or ground| ≤ 1). In game, step 5.
- **Gate-cut values are large** (report only, 7.19): w12345 k2_1_2 -16, k10_1_1 +7, playtest metropolis k2_1_0 -6. The metropolis plaza sits at 72 on ground at 62. These breach DS-46's cap of 4 at the street's own end. The metric does its job. Make sure 7.19 FALLSTREETS takes these named gates as its acceptance cases.
- **The hamlet s3_1 g0 join is good.** The approach falls 69 → 65 at one block per 4 columns into the 4-deep street cut, and meets S64-65 with a 1-block difference, which slabs can take. That is the sunken-lane case in my Q1 answer. The one thing left is the bank on the street's high side (ground 66-69 beside S63) when it is drawn in game. Check that it is terraced, not a raw face (DS-46 facing, DS-50).

## Proposed rules

- **Long wet runs: "crossing" and "along" are different.**
  - Rule: a wet run that the road crosses (it enters and leaves on opposite banks, roughly across the water) and that is longer than 20 waits for ROADMAP 7.3 Bridges. A road that runs *along* water (a wet run whose length is more than about twice the water's width at that point, agent's number) is never built: reroute, or drop.
  - Why: it separates John's road-0 complaint (k2_1_2) from a town that simply sits across a river (k2_1_0).
  - Scope: approaches and graph routes.
  - Enforcement: code. Classify wet runs in `Profile` and report both counts.
  - From: the DS-19 violation above. Earlier occurrences: ADR 0004 road-0, REVIEW S10.

## For John

- **What should happen to an approach that has no dry route and no crossing of 20 or less?**
  - Today it becomes a long flat deck. That breaks DS-19. Where to look: `rj/approach-3201-1683.png`, your 21.31.49 spot, a metropolis gate on the river.
  - Option 1: drop that approach and the link on that side. This keeps DS-19, but the link then ends at a port short of the town (DS-17 and your 21.19.18 complaint).
  - Option 2: allow a long *crossing* deck at the gates of a town or larger, in the road's own material, until 7.3 Bridges, but never a run *along* water.
  - I recommend option 2 for crossings, after a real search for another exit. For runs along water, drop the approach.

Verdict: needs John

# Re-review after round 3 (2026-10-27, branch HEAD 40d2e2cf)

Round: re-review after r3. Scope: the two should-fix items, the along-water part of the DS-19 violation, and anything the fix broke.

Counts: 1 violation still open (DS-19, the crossings of 21-100 blocks, waiting for John), 0 old should-fix items left, 1 should-fix introduced by the fix.

Looked at:
- `build/rj/approach-2560-1984.png` (regenerated in r3) and the r3 report.
- `RoadJoinProbeTest` at 40d2e2cf, run by me:
  - `probe` for w12345 cells 10,1, 4,0 and 2,1;
  - `at` around 4868,707 and 10527,1726.
- I did not reopen `approach-3616-1440.png`; r3 says it is unchanged.

## Earlier findings

- **Should-fix k4_0_2, the step at the city gate: fixed.**
  - The street ends at S67 (4867,707), and the approach starts at A67 beside it.
  - It then follows the ground at 67-69 out to 4887,721; there is no embankment over flat land any more.
  - `exitStep 0`, `maxStep 0`.
  - The step has moved to the port, where the approach meets the graph road at 3 or more. That is the right side for it, since the lower-ranked piece takes up the difference. It stays a counted known issue for the graph-road side. It belongs with the "joins are level" proposal, not with this gate.
- **Should-fix s10_1, the 9-block shelf: fixed.**
  - Around 10512-10541, 1712-1740 there is no longer any approach column above the k10_1_1 gate.
  - k10_1_0 now leaves northwards: `maxStep 0`, `exitStep 0`.
  - k10_1_1 still has a 2-step contact. Its gate street sits 7 deep in a cut (`gateCut 7`, S110-111 in ground at 117-126). That is the gate-cut case already sent to 7.19.
- **w12345 s2_1 k2_1_0, step 5 at 3376,916:** I accept the diagnosis. The approach is routed through the city's street grid (51 contacts), so this is cause D. Leave it to step 4 and the `LinkExits.approach` cost. It was never a should-fix item.
- **DS-19, the along-water part: fixed for k2_1_2.**
  - The 190-block lake deck is gone: the picture shows water and ground only.
  - The test `lakeRoadIsNotBuilt` holds it.
  - I accept 100 blocks as the interim cut between "crossing" and "along" (see Suggestions for a gap it leaves).
- **DS-19, crossings of 21-100 blocks** (playtest k2_1_0 about 65, k2_1_1): unchanged and counted, still waiting for John. The violation stands until he answers.

## Should fix (introduced by the fix)

- **k10_1_0's reroute now puts road on water.**
  - w12345 hamlet s10_1, k10_1_0: `wetLow 10`, against 0 before r3. Ten drawn approach columns lie on water outside a working crossing. These are the "2 failed crossings on a sloped stream" in the r3 report.
  - Why it matters:
    - ADR 0004 WR2/WR4 make "no road column on water outside a crossing" an invariant.
    - The plan's own test bar is `wetLow == 0`.
    - A track laid at the water's level is the flooded look of 21.31.49.
  - The new route away from the other gate is right, but it must not trade a cliff for a ford.
  - Fix direction: the keep-off-other-exits reroute must still pass the dry check. Prefer a route that crosses the stream where it is flat, or one of the other attempts. Do not keep the attempt with wet columns; or deck the stream at water+1 the way the street pin lift does.

## Answer: the k2_1_2 graph route that now ends at its port

- A road that stops in the open, short of a city, is not acceptable for long. It is John's 21.19.18 complaint ("a road stops well short of a tiny town") and it goes against what DS-17 is for ("nothing floats").
- In order of preference:
  - (a) Give that link another exit on the dry side. s2_1 is a city, so it can have a gate facing the lake's end.
  - (b) Otherwise drop the link too. That is what WR2 says ("reroute, then drop the edge"). The city keeps its other two links.
  - (c) Keep the dead end only if dropping the edge would leave the far settlement with no link at all. Then it is a John case, together with the long crossings.
- For this item, I accept the dead end as an interim state under two conditions:
  - it is counted (6.16 DEADMAIN or a `dead-end-port` count in `city-check`);
  - a ROADMAP line records (a) or (b) as the fix.
- The graph reads decisions before the skeletons are planned (N5). So dropping an edge because its approach failed is a change to how the planner is ordered, and the coordinator should place it, not this item.

## Suggestions

- The 100-block cut still lets a run of 21-100 blocks *along* a river bank be built. Count such runs separately from runs that cross the water (the proposed rule above), so John's answer on long crossings does not cover them by accident.

Verdict: needs John

# Final review: steps 1-4 on claude/p6-roadjoin-s3 (2026-10-27, ce334be8)

Round: steps 1 and 2 re-checked after r4; steps 3 and 4 seen for the first time.

Counts: 1 violation still open (DS-19, crossings of 21-100 blocks, waiting for John), 1 should-fix (new, from step 4), 2 suggestions, 1 proposed rule (an extension of one I proposed earlier).

Looked at:
- The verifier's top views in `.claude/reports/p6-roadjoin-verify-png/`: `approach-3616-1440.png`, `-3200-1690.png`, `-3264-1856.png`, `-4640-5728.png` and `-4864-704.png`.
- City plans in `.claude/worktrees/p6-roadjoin-s3/viewer/out/city/`: `hamlet-river`, `village-mixed`, `gate-metropolis-mixed` and `mc2-joined-city` (`plan.png` each).
- Two top views I made at ce334be8, in my scratchpad: `rj4/approach-3300-970.png` (w12345 s2_1 k2_1_0) and `rj4/approach-2130-1056.png` (w12345 s2_1 k2_1_1).
- `RoadJoinProbeTest` at ce334be8: `probe` for w12345 cells 4,0, 10,1, 2,1, 3,7 and -11,-3, and `at` around 4224,318.
- The r4 report and the s3-r2 report, and the ROADMAP `dead-end-port` line (6.15).
- The city plans do not draw approaches; they only show where the gates sit.

## Earlier findings, confirmed

- **wetLow:** fixed by dropping the approach. Every approach in my probe shows `wetLow 0`. s10_1 k10_1_0 is gone, so its port at 10560,1600 is a counted dead end.
- **Dead ends:** acceptable for now. The ROADMAP line records (a) another exit, (b) drop the link, (c) John if dropping would orphan the far settlement, as I asked.
- **Port step:** the step at the port is now only the part the grade limit cannot reach. Right.
- **k4_0_2:** still good. `exitStep 1`, `maxStep 1` (68 against 67), which slabs can take.
- **The along-water count (`alongRuns`):** done. Playtest k2_1_0 and k2_1_1 are counted as running along a bank. That keeps those two separate when John answers on long crossings, so he is not approving roads that run along a river by accident.
- **Step 3 (exits only in planned tiles): good.**
  - The metropolis exit now sits at the end of the avenue (`approach-3264-1856.png`: the approach leaves the end of the wide green avenue southward).
  - The playtest s64 town (`approach-4640-5728.png`) now leaves from a junction and carries straight on along the diagonal street, as DS-52 asks. In round 1 this had an 8-block gap.
  - Hamlet g0 (`approach-3616-1440.png`) is unchanged and joined at both ends.
  - In the city plans the gates sit at street ends or junctions. The village's two gates are at the two ends of its main street.
- **Playtest metropolis k2_1_0 (`approach-3200-1690.png`):** unchanged. The roughly 65-block deck leaves the plaza and is level with it. It is the open DS-19 case.

## Violations (open, waiting for John)

- **DS-19:** crossings of 21-100 blocks are still built: playtest k2_1_0 (about 65) and k2_1_1, and now also w12345 s2_1 k2_1_1 (about 35 blocks of deck along the lake shore west of its new exit at 2144,1056).
  - These are counted, and the ones along a bank are flagged.
  - The question for John is unchanged, so this holds the item at "needs John", not at a fix round.

## Should fix (step 4)

- **Approaches that cross streets on the way out of town must cross at grade.**
  - **w12345 city s2_1 k2_1_0** (`rj4/approach-3300-970.png`):
    - The exit sits at the end of a diagonal street (3232,1024). From there the reserved corridor cuts diagonally NE across the city's grid. It crosses three streets on the way: at about 3240, about 3290 (the E-W street at z~950) and 3373,916.
    - At 3373,916 the approach is at 95 and the street at 100: a 5-block cliff where a road crosses a street.
  - **w12345 s2_1 k2_1_1** (`rj4/approach-2130-1056.png`): the approach deck runs at 68 past the end of the N-S street at 2110,1055 (plaza P65), a 3-block lip.
  - Why it matters:
    - Step 4 allows streets to cross an approach, so every crossing is a junction, and a junction with a 3-5 block step is the "roads don't meet up" complaint in another place.
    - A diagonal cut through a city grid is also the "road back across town" layout my Q2 answer called a routing error (`URBAN_PENALTY` has not stopped this one).
  - Fix direction:
    - Where an approach crosses or touches a street that is not its exit, the two surfaces meet within 1 (slabs), or the approach routes round.
    - In a city, prefer leaving by the street that already heads toward the port, so the approach starts at the rim, not inside the grid.
    - Count these steps in `ApproachJoinTest`. Today they pass because only the exit is checked against 2.

## Suggestions

- **w12345 s4_0 k4_0_1, the verifier's note.**
  - At the gate (4224,320) the approach is a deck ramping 70→72, and the street is at 69-70.
  - The 2-step at 4222,316 is a *side* lip: the inside of the deck's curve overhangs the corner of the street at 71 against 69. Along travel, the steps are 1 per column or less.
  - DS-31 (accepted) only governs along travel, so this is not a violation.
  - Against my proposed "Joins are level" (0.5) it fails. Trim the deck's inner corner down to the street's level, or start the ramp 2 columns out. Polish backlog.
- **The ±2 "joins" bar in the tests** should become ≤1 at any contact (exit or crossing) once the should-fix above lands. ≤0.5 should wait until `RoadSurface` slabs are confirmed in game (step 5).

## Proposed rules

- **Extend "Joins are level" (proposed in `roadjoin-qs-architect.md`) to every place road surfaces touch.** That covers exits, crossings, and an approach running past a street end. The step there is at most 1 block (0.5 once slabs are drawn), or the surfaces are kept 2 blocks apart.
  - Recurs: the s10_1 9-block shelf (round 1), w12345 k2_1_0 at 3373,916 and k2_1_1 at 2110,1055 (this round), and polish 6.3 (junction steps).
  - Enforcement: code. Make `JoinWorld`'s `maxStep` over all contacts ≤ 1.

## For John

- Unchanged: what to do with approach crossings of 21-100 blocks. They are counted, the ones along a bank are flagged separately, and my recommendation is in the round 1 "For John" above.

Verdict: needs John

# Re-review after the step-4 should-fix round (2026-10-27, claude/p6-roadjoin-s3 0c517087)

Round: re-review after s3-r3. Scope: the step-4 should-fix item ("approaches cross streets at grade") and anything the fix broke.

Counts: 1 violation still open (DS-19, crossings of 21-100 blocks, waiting for John), 0 should-fix, 3 suggestions (moved to the polish backlog).

Looked at:
- `.claude/pics-r3/approach-3300-970.png` and `approach-2130-1056.png`.
- The s3-r3 report.
- `RoadJoinProbeTest` at 0c517087, run by me: `probe` for w12345 cell 2,1, and `at` around 3373,916 and 2110,1053.

## The should-fix item

- **Fixed: approaches now meet the streets they cross within 1 block.**
  - **w12345 k2_1_0 at 3373,916:** the approach is at 99-100 on both sides of the street, which is at 99-100. `maxStep 1`, `farBad 0`; the 5-block cliff is gone. The two other crossings on this corridor (in the picture at about 3240 and about 3290) are within 1 as well, according to `farBad 0` over the corpus.
  - **w12345 k2_1_1 at 2110,1055:** the deck (X67) used to sit 3 above the plaza (P65); the gap is now 2 on two columns beside the lake. The deck then falls 67→66→65 to the southwest and meets the ground.
  - Over the whole corpus: `farBad 0` (29 columns before), and the test asserts it.
- **Nothing the fix broke** that I could see in these two places. Exit steps stay at most 1.

## Suggestions (unfixed after the one round, so to the polish backlog)

- **The k2_1_0 corridor still cuts diagonally across the city grid** (`approach-3300-970.png`). It no longer has steps at its crossings, but it still makes an irregular cut across the blocks. A city's road out should leave by the street that already heads toward the port, so the approach starts at the rim. This needs the route-preference cost in `LinkExits.approach`.
- **A 2-block lip beside the plaza at w12345 k2_1_1** (2110,1055-1056; deck 67, plaza 65), counted as `farWet 2`.
  - The deck must stay at water+1 here.
  - The cleaner answer is to keep the deck 2 blocks clear of the plaza edge, or to drop the plaza corner to meet the deck.
- **The deck corner over the street at w12345 k4_0_1** (4222,316; 71 against 69). This is unchanged from the last round: a side lip, not a step along the road.

## For John

- Unchanged: what to do with approach crossings of 21-100 blocks. They are counted, the ones along a bank are flagged separately, and my recommendation is in the round 1 "For John" above.

Verdict: needs John
