# p6-dsroads (DSROADS) architect review (2026-10-28)

Mode: review. Round: first.
Counts: 1 violation, 1 should-fix, 4 suggestions, 1 proposed rule.

Looked at (plan views I rendered from the branch's own `WorldPlanner.approachRoads` columns, 81x81 blocks around each exit, 8-block grid; approach coloured by width (pale yellow 3, light orange 4, orange 5, red 7, purple 9), district streets grey (light 3, mid 5, dark 7+), lots brown, exit blue; scratch program, nothing in the repo changed):
- playtest world 1709797029209580947 (`pt1709-world-s64`), metropolis cell 1,2: `pt_1_2_0.png` (k2_1_0, link 3), `pt_1_2_1.png` (k2_1_1, link 4)
- world 12345 (`world-12345-s64`): `w_7_-3_0.png` (metropolis k7_m2_0, link 3), `w_2_1_1.png` (city k2_1_1, link 3), `w_-9_3_0.png` (city km9_3_0, link 4), `w_-3_-4_0.png` (city km3_m5_0, link 4), `w_-3_-4_1.png` (city km3_m5_1, link 5)
- Counted (not drawn): mouth width against the width of the street at the exit for every approach of 10 of the 16 city and metropolis sites of world 12345 (16 approaches; the run stopped at s8_4 on a recorded-grid edge).
- `city-check --seeds 12345 --sites cells --terrain recorded:fixtures/terrain/world-12345-s64.json.gz --roads` (my run: 240 joins, 2 over: s6_6 k6_5_0 and sm9_3 km9_3_1).
- Code read: `RoadRanks.taper`, `RoadNet.raster` (width per station), `WorldPlanner.approachRoads`, `Districts.markApproaches`.
- Not seen: in-game shots of a tapered approach (none taken: the coder's client run has no approach screenshot); the w987654321 lane case; the s6_6 approaches (my render printed nothing for it). DS-50: not looked at as pictures; the check reports 0 roofed by construction, which I accept.

## Violations

- **DS-52** "the link meets a junction, never a lane or path", and now also the width drop: 3 of 485 joins (w12345 s6_6 k6_5_0 at 7072,5952, sm9_3 km9_3_1 at -9472,3712; one on w987654321) land on a 3-wide district lane. The change made these worse: before, a 3-5 link met a 3 lane (drop 0-2); now the approach is drawn 7 or 9 wide at the exit, so a metropolis road 9 wide butts into a 3-wide lane (drop 6, the check's own line). Fix direction, two parts: (a) now, in this item: start the taper from the width of the street the exit actually lands on (see the should-fix); that removes the width drop on all three. (b) The rank part (the exit should land on a main street or at a junction) is district planning; if it is not done here it needs John's or the coordinator's say-so to carry it as a known issue on 6.15 ROADJOIN / DEADMAIN (see For John).

## Should fix

- **Approaches swell at the gate wider than the street they join (a bulb), on 7 of 16 city approaches counted on world 12345.** The taper starts from the tier's nominal main-street width (`TierPrograms.mainWidth`: 7 city, 9 metropolis), but many cities' actual exit street is 5 wide (kind MAIN, width 5: km3_m9_0/1/2, km3_m5_0/1, km9_3_0, k2_3_1). So the road reads 4 -> 5 -> 7 for about 12 blocks, then snaps back to 5 at the town: a lay-by blob in front of every gate, the opposite of a road widening into a street. Clearest in `w_-3_-4_0.png` (cell -3,-4, exit -2080,-4384: a 5-wide street, a 7-wide red pad, then the 4-wide link) and `w_-3_-4_1.png` (exit -2272,-3872: a 5-wide link that was already the street's width now swells to 7 at the end of a 5 street; this one got worse with no gain). `w_-9_3_0.png` the same (-9280,3232). Where the exit street really is 7 or 9 (`w_2_1_1`, `w_7_-3_0`, `pt_1_2_0/1`) the taper reads right. Fix direction: the gate width is the width of the street at the exit (what `RoadRanks.streetWidth` already measures), never more than it; the tier constant only as a fallback when no street is found. The mouth then always equals the street it continues, and the funnel only ever narrows away from town. The lot-free corridor (`Districts.markApproaches`) should follow the same number.

## Suggestions

- **Keep the 2-per-8 rate** (1 block per side every 8-block station; 9 to 3 over 24 blocks plus the first 8). At player scale it reads as a gentle flare you notice walking in, not a funnel; 1 per side every 4 (the rule's minimum) reads as stair-steps from the road. A 1:8 per side edge is also about what a real lane-drop taper looks like at our 1.25-1.5x scale. Symmetric on straight runs (the raster is centred); with an even link (4) the last step is one block on one side, which is fine.
- **Stepped edges grow single-cell teeth on curves and diagonals** (`pt_1_2_0.png` near the red/orange boundary, `w_7_-3_0.png` the lone red cell under the orange piece, `w_2_1_1.png`): each column takes the width of its nearest segment, so at a bend the two widths interleave. Assign the step by distance along the curve (arc length from the exit) rather than nearest segment, so each step is one clean line across the road.
- **The wide first piece overhangs the street's end** by 2-4 blocks to the side and beyond (`w_7_-3_0.png`, the purple pad past the dark street's end; `pt_1_2_1.png`, purple wider than the diagonal street's end on the east). Clip the first piece to the street's own width at the join, or start it a half-width back from the street's end.
- **Mark the gate**: the width change from street to road is where DS-52 says the material changes (DS-30). A pair of posts or lamps at the mouth (city: lamp posts; town: stone pillars) would make the hand-over legible and give the flare a reason. When DS-31 kerbs are built, the kerb should end at the gate and return round the corner; the taper stays unkerbed. I could not check in game where the material actually changes on the tapered piece (not seen).

## Proposed rules

- **DS-52 clarification: the gate width is the street's, not the tier's.** An approach is drawn, at the exit, exactly as wide as the street it joins, and from there only narrows (or stays) toward the link; it never widens and then narrows again. / Why: the tier's nominal main-street width and the street actually at the exit differ in about half the cities on world 12345, and a road that swells in front of the town reads as a mistake. / Scope: approaches of every tier. / Enforcement: code, add to `RoadRanks`: mouth width == exit street width, and width non-increasing from exit to port. From: the should-fix above (first occurrence).

## For John

- The 3 joins (of 485) where a road arrives on a 3-wide lane: fixing the width part is in reach on this item; making the exit land on a main street or junction is district planning. Is it acceptable to close DSROADS with that rank part carried as a known issue on 6.15 ROADJOIN, or should this item hold until it is done? (Where to look: w12345 7072,5952 and -9472,3712.)

Verdict: should-fix round needed

# r2 re-review (2026-10-28)

Mode: review. Round: re-review after r2 (commits 9485251e, 7459cab5). Scope: the DS-52 violation and the swell should-fix only.
Counts: 1 violation (rank part only, with John), 0 should-fix, 2 suggestions carried, 0 new proposed rules.
Looked at:
- My scratch render, re-run on the r2 classes (built 16:12, after 7459cab5): `png2/w_-3_-4_0.png`, `png2/w_-3_-4_1.png`, `png2/w_7_-3_0.png`, `png2/pt_1_2_0.png`.
- The coder's map `p6-dsroads-r2-maps/after--9_3_0.png`.
- Mouth width against the exit street, recounted on the same 16 w12345 approaches as r1.
- My own `city-check --seeds 12345 --sites cells --roads` run on w12345: 223 joins, 0 with a width step over 2.

## Violations

- **DS-52 width part: fixed.** No join has a width drop over 2 any more; my w12345 run agrees with the coder's (223 joins, 0 over).
- **DS-52 "never a lane": still unfixed** at the 3 lane exits (w12345 7072,5952 and -9472,3712; one on w987654321). The width at these joins now passes; the rank does not, and that is district planning. It is with John in REVIEW ("DSROADS: road links that end on a 3-wide lane"). My recommendation is unchanged: carry it as a known issue on 6.15 ROADJOIN.

## Should fix

- **Bulb at the gate: fixed.** Every mouth now matches the street it joins:
  - cell -3,-4 exit 0 goes 5 (street) -> 5 -> 4 (link), with no pad (`w_-3_-4_0.png`);
  - exit 1, a 5-wide link onto a 5-wide street, stays a constant 5 (`w_-3_-4_1.png`);
  - -9,3 goes 5 -> 4 (`after--9_3_0.png`);
  - streets that really are 7 or 9 wide keep the full taper (w12345 7,-3, which is unchanged, and pt 1,2).

  Recount on the same 16 approaches: 13 mouths equal their street, 0 swells. The other 3 (km4_m5_0, km4_m4_0, k2_3_0) are 7-wide links landing on a 5-wide street. They are within DS-52's drop of 2 and were already like that before this item, so they are not a finding here.

## Suggestions (carried, polish backlog)

- **Teeth on bends.** Single-cell teeth where the width steps on a bend are still visible in my renders: `pt_1_2_0.png` at the purple/red and red/orange steps, and `w_7_-3_0.png` with a lone red cell under the orange piece. These look the same as in r1, so the arc-length change did not visibly alter them on these two approaches. Minor.
- **Unchanged from r1:** first-piece clipping past the street's end (not done, as the coder says) and the gate markers.

Verdict: needs John
