# 7.20 METROFILL R1 architect review (2026-10-26)

Mode: review. Round: first.
Counts: 0 violations, 2 should-fix, 4 suggestions, 1 proposed rule.

Looked at:
- Plans, before (run-phase6, 15:32, pre-R1) and after (p7-metrofill-r1, 19:31, after the round-3 quay fix): `viewer/out/city/gate-metropolis-mixed/plan.png` and `viewer/out/city/gate-metropolis-temperate/plan.png` in both worktrees. I also looked at zoomed crops of them, written to the scratchpad `arch-mf/`: mixed core, mixed north ring, temperate centre.
- 3D frames: temperate `d15_m36-overview.png` (before and after), temperate `d15_m36-street.png` (after), mixed `d14_m12-overview.png` (after). They change very little: these tiles carry no demoted arterial.
- After only: `viewer/out/city/mixed-city/plan.png`, plus the git diff of fixture `mixed-city/districts/d54_m20.json` at ae622d0b, for the lost quay.
- Data: district JSON for both gate metropolises, before and after. I ran node scripts over it for arterial connected components, arterial ends and square placement (which roads touch each side of each square). I also generated `city --districts --scenes none` with both CLIs, under the scratchpad, for gate-temperate-city (987654321), gate-coast-city (12345), gate-joined-city (987654321) and d3-metropolis-flat (7).
- Not seen: in-game pictures. Floor plans don't apply to this item.

## Violations

None. DS-17: `roads-attached` and `roads-connected` pass, and every demoted line is either a through street or gone. I saw no new street stubs on the plans. DS-18: demoted arterials stay streets, so minor streets still run past them. DS-47: arterial+main slivers fell by 60-70 % on the coder's numbers, and in the plans the doubled avenues are gone.

## Should fix

- **Arterial islands.** Demoting every second line cuts some arterial pieces off the arterial network. They are mostly outer-ring pieces, which R1 correctly does not cut, but the spokes that joined them to the network are now 5-wide streets. You then get an 11-wide avenue that starts and ends at crossings with minor streets: the hierarchy runs backwards. New islands (blocks):
  - gate-metropolis-mixed: the north ring loop, 1920..2256 x -2560..-2224, 865 blocks of arterial. On the plan it is a yellow loop whose only links are grey streets (x 2064 and z -2224 used to be arterials).
  - gate-metropolis-temperate: an L at 2256..2400 x -5936..-5776, and an L on the east edge at 3040..3184 x -6080..-5776.
  - gate-temperate-city: a line at x 960, z -9600..-9424.
  - gate-joined-city: 8624..8800 at z 480, and 8800..8960 x -144..0.

  That is 6 new pieces in 4 of the 6 cities I measured (gate-coast-city and d3-metropolis-flat have none). Some islands existed before R1, but these are new. The number of places where an arterial runs straight on into a street at a 4-way crossing rose from 10 to 13 (mixed) and from 16 to 30 (temperate).

  Fix direction: after the parity pass, make arterials one connected network. Where demotion would orphan an arterial piece (ring or not), keep the shortest demoted segment that reconnects it. If no segment can reconnect it, demote the piece too. Prefer keeping the link: a ring is only worth having if the avenues reach it.

- **Crossing squares wander onto lanes.** The headline "squares 4/21 → 11/23" counts squares that the district plaza search (`Districts`, up to SHIFT_RINGS × 16 = 160 blocks) moved off their crossing because the corner graded badly. Most end up in block interiors on lanes:
  - mixed: only 4 of 11 squares touch an arterial. The hub square is a fifth, on purpose. The rest sit 20-54 blocks from the corner on lanes or streets.
  - temperate: 5 of 24 squares touch an arterial.
  - d3-metropolis-flat (flat noise terrain): 23 of 27 squares touch an arterial.

  So the problem is real terrain. A moved square also cuts the interior grid: in mixed d11_m12 the square at 1874,-1832 removed street x 1836 and two cross-street pieces, the tile went from 31 lots to 22, and it left a 1-block lane at 1899,-1811. On the plan, these squares read as random holes inside blocks, joined by bent lanes (mixed north crop: "square" inside a lane loop). The search itself predates R1, but `SQUARE_ONE_IN = 1` multiplies its use and the item relies on it for the count. Same view as my A9 answer: squares away from the arterials look random.

  Fix direction: a crossing square that cannot grade at its corner may shift only along its own arterial frontage, keeping one side on the arterial and staying within about one lot depth of the crossing. Otherwise drop it and leave the plaza share to DG2b pocket parks and hall/temple forecourts. Report the core plaza share from squares that front a street of class street or higher.

## Suggestions

- **Anchor the parity phase to the hub.** The phase is a hash, so in gate-metropolis-temperate the east-west line past the temple, square and hall (z -5776) became a 5-wide street. The downtown lost its through avenue and the arterials now pass the centre tile only at its far corner. Mains still converge on the hub, so it is not wrong, but the line beside the civic centre is the one to keep. Pick the phase so that the lines bounding the centre tile are arterials.
- **Short arterial stretches.** For example, x 2864 in temperate is arterial only from -5776 to -5456, with streets beyond both ends. Demote any arterial run shorter than about 2 tiles that ends at street crossings at both ends, or extend it to the next arterial. This overlaps the island fix above.
- **Quay end at the waterfront tile edge: acceptable.** Ending a quay where the settlement's owned waterfront ends is the right urban edge, since the land beyond is unplanned. The cost is small: one 35-block quay (8695..8745, -3053..-3037) in mixed-city d54_m20. Polish: end such a quay at its last street meeting rather than at the tile line, so it never finishes as a short dead-end stub along the water.
- **Fill.** The demoted lines leave very large blocks with little in them. Example: mixed north, the block bounded by z -2224, x 1584 and x 2256, about 340 blocks wide, with mostly open ground where the old arterial frontage was. That is L1/G0's job (DS-47), not R1's. Note it for the L1 review rather than here.

## Proposed rules

- **Arterials form one network.** In city and metropolis, every arterial piece reaches the settlement's main arterial network (or its ring) without stepping down to a lower class on the way. An arterial does not start or end at a crossing with a lower-class road unless it ends at the settlement edge or at a gate.
  - Why: an avenue that starts and stops between minor streets inverts the street hierarchy, so a player can't read it.
  - Scope: city and metropolis.
  - Enforcement: code, a new check `arterials-connected` (count arterial components, allowing those at the edge).
  - From: the first should-fix above. It is not a recurrence: ROADS2 (5.1) only dropped unlinkable arterials.

## For John

- Nothing blocks the item. Just the proposed rule: whether arterials must form one connected network.

Verdict: should-fix round needed

# Re-review after round 4 (2026-10-26, branch HEAD 377f676a)

Mode: review. Round: re-review after round 4 (the single should-fix round).
Counts: 0 violations, 0 should-fix open, 2 suggestions (polish backlog), 1 proposed rule (unchanged).

Looked at:
- Plans, regenerated at 20:58: `viewer/out/city/gate-metropolis-mixed/plan.png` and `viewer/out/city/gate-metropolis-temperate/plan.png` in the p7-metrofill-r1 worktree.
- Zoomed crops of the temperate plan, around the two new links: scratchpad `arch-mf/r4-temp-link.png` and `arch-mf/r4-temp-east.png`.
- The same node scripts as in the first review, rerun on the district JSON of both gate metropolises: arterial components, arterial ends, and square sides with their gap to the crossing.
- Not seen: whether the squares are graded flush with the arterial. That needs a 3D or in-game frame of a crossing square; the street frames in the gallery don't show one.

## Should-fix items from round 1

- **Arterial islands: fixed.** Each gate metropolis now has 1 arterial component. Before round 4, mixed had 3 and temperate had 6.
  - Mixed: the north ring loop joins the network again through x 2256 down to z -1744. In the plan the yellow network is continuous.
  - Temperate: the west L reaches the network through a 2-segment link, one tile west along z -5776 and then south down x 2256 to z -5616. The east L reaches it through x 2864.
  - The coder reports `arterials-connected` = 0 on both worlds and on the gates. I did not rerun city-check.
- **Squares on lanes: fixed.** Every crossing square now touches an arterial:
  - mixed: 10 of 10 (the hub square, d14_m12, faces mains and lanes by design);
  - temperate: 16 of 16. The 2 that don't touch are the hub square (d15_m36, on mains) and one 16x16 square inside d13_m34, which the coder says is an old-town or cell square (it was there before R1 too).

  The slides are modest: gaps of 6-45 blocks from the corner, along the arterial. d11_m12 now keeps its interior street; the square sits at 1899,-1803 against the arterial.

## Answers to the coder's questions

- **(a) A link on a line parallel to another arterial: acceptable.** The tile lattice keeps such pairs at least one tile apart (about 130-190 blocks; the temperate west link runs about 145 blocks from x 1584). That is a normal spacing for avenues and nowhere near the DS-47 sliver distance of 16. A 2-segment L that steps around steep or wet ground reads as an avenue following the land. One preference for later polish: when a straight continuation of the orphaned piece passes the ground tests, take it over an L of the same length.
- **(b) Dropping 7 of 25 crossing squares on temperate: acceptable.** A square that cannot meet its arterial is worse than no square, because it reads as a random hole in a block. The cost is the core plaza share, now 0.6 % on temperate against a 5-10 % target. That share has to come from DG2b pocket parks and from hall and temple forecourts. If those still leave the core under 5 %, the next step is the A9 fallback from my plan answer: squares at arterial × main crossings.
- **(c) Hub parity: anchor it, but as polish, not in this round.** On each axis, keep the tile line nearest the hub centre. If both lines are equally far, keep the one on the side that holds the hub square. Take the phase per settlement from that line rather than world-wide; the hub is pointwise for every tile of the settlement, so tiles still agree.
  - On temperate this flips both axes: keep z -5776, the avenue past the temple, square and hall, and x 2400, instead of today's far corner (z -5616, x 2544).
  - Watch joined settlements: two neighbouring settlements with different phases must not put arterials one tile apart where they meet.
  - If that gets complicated, leave it. The mains already converge on the hub, so the street hierarchy still reads.

## Polish backlog (no longer holds the verdict)

- **Arterials still run straight on into a street at 4-way crossings in places:** 10 on mixed, 22 on temperate, for example x 2864 at z -5776 and z -5456. One short arterial still ends at nothing: temperate 3184,-6080, which predates R1. Either demote an arterial run that ends at street crossings at both ends, or extend it to the next arterial.
- **Hub-anchored parity**, as in (c).

Verdict: meets the standard
