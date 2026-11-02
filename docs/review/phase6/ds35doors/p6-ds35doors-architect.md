# DS35DOORS architect review (2026-10-28)

Mode: review. Round: first.
Counts: 2 violations, 3 should-fix, 7 suggestions, 3 proposed rules.

Looked at (worktree `.claude/worktrees/p6-ds35doors`, HEAD 0b08b5c5; CLI built there):
- Evidence floorplans, before and after: house-3 (20x20 medieval), house-5 (12x10 plain), shop-2 (11x17 medieval), workshop-4 (20x16 medieval), workshop-7 (24x20 plain), hall-1 (30x30 plain), temple-1 (20x30 medieval), highrise-3 (24x24 tall-terracotta), highrise-5 (30x30 tall-brick).
- Evidence check reports `check-before/` and `check-after/` for house, shop, workshop, hall, temple, highrise (invariants, design warnings, Door rise and Entrances tables).
- My own floorplans (flat): house medieval 16x12 seeds 0,1,2,4,6,7,8,9; house plain 12x10 same seeds; shop medieval 9x16 same seeds; high-rise `tall` 24x24 same seeds. On slopes: house medieval 16x14 `plane:64:1:0:4` seeds 0..11; house medieval 20x20 `hill:64:20:24` seeds 0..9 (seed 7 read in full).
- My own IR (`gen`, read with jq): house 16x14 seeds 0..7 in plain, medieval, taiga, desert (door count, faces, trim around the main door); hall 30x30 seeds 0..11 in plain and medieval; workshop medieval 14x12 and 20x16 seeds 0..7; temple 20x30 s1, shop 11x17 s2, house 20x20 s3, high-rise tall-terracotta 24x24 s3 (trim, door, site boxes); the same high-rise from the run-phase6 CLI for comparison.
- Images: `docs/review/phase6/ds35doors/gallery/workshop-medieval-back-right-seed3-before-after.png`; `viewer/out/gallery/house-slope-back-right.png`, `house-plain-flat-back-right.png`, `workshop-flat-back-right.png`, `temple-flat-back-right.png`, `highrise-flat-front-left.png` (the coder's final gallery run). The cells are too small to judge a single door; placement was judged from the plans and IR.
- Not seen: any in-game screenshot (door leaf shapes, how a walled-up door bay reads in the block texture), a settlement scene with corner lots.

## Violations

- **DS-35 (last sentence, with DS-26)** In the `plain` style the secondary door is identical to the main door: both 1x2, no surround, no head, no canopy. Main-door trim boxes: 0 in 8/8 plain houses (16x14 s0..7) and 12/12 plain halls (30x30 s0..11), against 2-3 in every medieval, taiga and desert case. So on every plain building with two doors (6 of 8 houses, 12 of 12 halls; and `plain` is the fallback style for any biome no other style claims) a passer-by cannot tell the front door from the back door. DS-35 says secondary doors are smaller and plainer; since 1x2 is the smallest door, the only way to meet it is for the main door to be the marked one. Fix direction: give the main door of every style a mark the secondary door lacks: at least a head (top slab or trim lintel, DS-27) and jambs, or a canopy. That also clears the matching DS-26 gap ("main entrance marked"), which predates this item. Leave secondary doors bare.
- **DS-45 (Stair runs, every lot)** The new secondary-door flights add straight runs of 4+ risers with no level tread. `flight-landings` cases before and after: house 124 to 265, workshop 184 to 360, hall 284 to 465, temple 1344 to 1696, high-rise 659 to 959 (the "run 4+" column for side and back doors: house 143, workshop 186, hall 225, temple 412, high-rise 641). Front-door runs are unchanged, so the increase is all this item's (`check-*/{type}.md`, Door rise tables). The rule covers "no exterior flight on any lot, any building type", and the code only reports it as a warning today, but this item adds the cases. A secondary door is optional and the walled-up fallback already exists, so the fix is cheap. Fix direction: a secondary door whose flight cannot be broken every 3 risers (a landing or a turn) is moved to another face or walled up, like the doors that fail `steps-reach-ground`.

## Should fix

- **The 2-wide high-rise main door sits half a block off the facade axis**, 10 of 10 high-rises (tall 24x24 s0,1,2,4,6,7,8,9; highrise-3; highrise-5). Every front checked has an odd number of cells and is symmetric: highrise-3's front runs x=1..21 with window pairs at 3-4/18-19 and 7-8/14-15 around axis 11. Before this item the 1-wide door sat at x=11, on the axis. Now it is at x=10..11: one block of wall on its left and two on its right, on the most formal facade in the town. Fix direction: keep the door on the axis. On an odd front use 3 leaves (DS-35 allows "2-3 wide"; a row of three lobby doors is ordinary) or 1 wide with a strong surround. Use 2 leaves only where the axis falls between two blocks, or shift the window bays so they stay symmetric about the door.
- **On slopes the back door is walled up in most houses, and the walled bay is left blind.** The coder's figure is 390 of 640 houses on `hill`. In the check runs 12000 of 16000 houses fall outside the enforced level-lot case, and most settlements stand on sloping ground, so in play DS-35 is mostly unmet. The fallback itself is sound as a last resort: a door in mid-air or into earth would be worse (DS-4), and on dug-in lots the proper answer is DS-45's bank door on the upper storey (SHOPLOW S8). Two things should change now. (1) Before walling up, try the other non-front faces: the back, then each side, preferring the side whose ground is level with the floor. The coder's own suggestion, several candidate doors with the first one that reaches the ground kept, is the right shape. (2) A walled-up door bay should go back to the window it replaced, not stay a blind bay. `InstancePassV1.sealed` turns the door box into wall, so the bay rhythm loses a window and the room behind it loses one (seen in `hill:64:20:24` s7, kitchen wing south face `!~###!`). Also undo the back-door room programme when the door goes (interior designer's side, see below).
- **Verify the side and back doors with rise of -3 or less**: hall 786 of 4998 (16 %), workshop 105, temple 61, house 52 (`check-after`, "Side and back entrance doors"). That reads as the ground straight outside lying 3 or more blocks above the door sill: a door opening into a bank. The coder says such doors are walled up, so this may be a measuring artefact (ground taken beyond a cut pad). Not seen in a picture. Show one case, or wall these up too.

## Suggestions

- **A shop's back door should land in a stair lobby, not the sales floor**: 7 of 8 shops (9x16 s0,2,4,6,7,8,9; shop-2) open the back door into the shop room. Only s1 has a separate stair room with the back door in it, which is the right pattern. DS-35's enforcement text accepts the shop floor, so this is not a violation. But "a separate house door" means one that reaches the stair without crossing the shop, so the dwelling can be entered when the shop is shut. The back door already lands near the stair foot in most seeds, so one partition with a door to the shop gives a back hall. Don't add a second street door beside a narrow shopfront: the frontage is too valuable and it breaks the shopfront. Use one only on fronts of 11+ where the house door can have its own bay at one end (the Georgian and Victorian shop-house).
- **The high-rise side door is always on the east face (10/10), tight against the lobby's back partition** (z=9 or 10 of a lobby z=1..10). Pick the face that fronts a side street, lane or the larger open side (this is also the start of DS-35's corner rule), and centre the door on the lobby depth so it lines up with the lobby's cross axis.
- **High-rise main door is unmarked** (no trim, canopy or step in the IR; the same before this item, so a DS-26 gap rather than this item's). The 2-high door sits in a 5-high ground storey whose windows run to +5, so its head does not line up with theirs. A transom of glass above the leaves (to the window head) plus a slab canopy would mark it and align the heads.
- **Halls: one side door per long side on large halls** (hall block over ~20 long). One door is acceptable now. The side door always goes on the east (24/24 halls); choose the side that faces a street or the square where one exists.
- **Temples: the transept-end side door is right** (temple-1, east transept, centred on the gable): the medieval north or south transept portal. For temples without transepts, put the side door in the nave's side wall in the bay nearest the front (the parish "south door"), not mid-nave. One side door is enough. Doors on both transept ends only when the plan is formal and symmetric.
- **Secondary door framing: no surround is right** (answers the coder's DS-26 question). Don't make them lower: 2 high is the minimum walkable door. In timber-frame and stone styles a plain head (a top slab or the frame beam carried over the leaf, DS-27) is fine and reads as built. No jambs or canopy.
- **One-storey house loses its only bedroom** (house-3, 20x20 s3: before entry, hall, hall, bedroom; after entry, hall, hall, kitchen, with three hearths in four rooms). Room programme is the interior designer's call, but a house with no bed is a layout finding. When the back-door room is the only bedroom, put the door on another room's wall instead.

## Answers to the coder's questions (architect)

- Shop house door: see the first suggestion. Keep the back door, give it a stair lobby, and use a street-side house door only on wide fronts.
- Secondary door type: 1 wide, 2 high, no surround is enough. Make the main door the marked one (Violation 1).
- Workshop door faces: the lean-to door goes on the face away from the street, preferring the back (yard) over the side. The living-wing door goes from the kitchen on the back or yard-side face, never on the street face. When both are possible, choose the face that opens onto the same yard as the hall's back, so the three doors share one yard. workshop-7 (both doors south into the yard behind the hall) is the model; workshop-4 is fine.
- Halls and temples: one side door now. See the suggestions for a second door on large halls and for temples without transepts.
- High-rise: one secondary door meets DS-35 until the 12-20 block frontage rule is built. Skipping the 2-wide main door on the smallest lots is fine where the front is under about 12 wide, with a 1-wide door with a surround and canopy instead. Wherever a door is wide it must sit on the axis (Should fix 1).
- Slope fallback (the coordinator's question): acceptable as a last resort, not as the main behaviour; see Should fix 2.
- Not reviewed: corner buildings and the door every 12-20 blocks of frontage. They are not built, and the brief scopes them out; the coordinator should make them a follow-up item, since they remain open parts of an accepted rule.

## Proposed rules

- **Secondary doors near the ground**: a back or side door rises at most 3 as a plain flight, or 4-5 with a landing (the DS-45 house default). Beyond that it moves to another non-front face; failing that it is walled back to a window, and its room keeps its normal programme. Why: today's side-door flights reach 9-19 risers (house 584, temple 2388, high-rise 977 at 9+), which is not a yard door. Scope: all types with secondary doors. Enforcement: code (extend `door-rise` to secondary doors). From: Violation 2 and the Door rise tables.
- **"Smaller and plainer" means the main door is the marked one**: 1x2 is the smallest door, so in every style the main entrance carries a mark the secondary doors lack (a surround or head, a canopy, or more width), and no secondary door is wider or more marked than the main. Scope: all styles. Enforcement: code candidate (trim or canopy around the main door; no secondary door wider than it). From: Violation 1.
- **Doors on the axis**: on a symmetric front the main door is centred. A door's width matches the parity of the front: 2 leaves on an even front, 1 or 3 on an odd one, or the bays are shifted to stay symmetric about the door. Scope: formal fronts (high-rises, halls, temples, symmetric styles). Enforcement: code candidate (door centre equals facade axis on fronts flagged symmetric). From: Should fix 1.

## For John

- Nothing blocks on you. Do you want DS-35 met on sloping lots now (the other-face search in Should fix 2), or is level-lot-only acceptable until the bank buildings (S8) arrive? I recommend now: most real lots slope.

Verdict: should-fix round needed

# DS35DOORS architect re-review, round 2 (2026-10-28)

Mode: review. Round: re-review after r2 (only round 1's violations and should-fix items, plus anything the fix broke).
Counts: 0 violations, 0 should-fix (1 unfixed item moved to Suggestions), 3 suggestions, 0 new proposed rules.
Looked at (worktree `.claude/worktrees/p6-ds35doors`, HEAD b2ba6b87; CLI rebuilt there): the coder's report `p6-ds35doors-r2.md`; `docs/review/phase6/ds35doors/check-after/{house,workshop,hall,temple,highrise,shop}.md` (Door rise tables, `flight-landings`); IR from `gen` for house plain and medieval 16x14 s0..7 and hall plain 30x30 s0..7 (trim at every exterior door); floorplan front rows of 32 high-rises: `tall` 24x24 s0..7, `tall-brick` 30x30 s0..7, `tall` 25x25 s0..7, `tall-terracotta` 24x24 s0..7; floorplans of house medieval 20x20 on `hill:64:20:24` s0..15 (s7 read in full). No images this round.

## Round 1 items

- **Violation DS-35/DS-26 (plain main door like the back door): fixed.** In all 24 IR cases the main door has 3 trim boxes and every secondary door has 0. The three boxes are a jamb on each side (2 high) and a 3-wide head: plain house s1 door x=7 with jambs x=6 and 8 and a head over x=6..8; plain hall s0 likewise. 6 of 7 two-door plain houses, all 8 plain halls and 7 of 7 two-door medieval houses show the difference.
- **Violation DS-45 stair runs: fixed.** In the side- and back-door table, "run 4+" is 0 for house, workshop, hall, temple and high-rise (before: 143 / 186 / 225 / 412 / 641). `flight-landings` is back to or below the base figures: house 114, workshop 182, hall 284, temple 1339, high-rise 520.
- **Should-fix, wide high-rise door off the axis: not fixed (moved to Suggestions).** Judged from the drawn ground-floor front row (window pairs mirror about the axis): the door sits on the axis in only 9 of 32 high-rises.
  - Centred: `tall` 24x24 s2 and s7, `tall-terracotta` 24x24 s2 and s7, `tall-brick` 30x30 s2, s4 and s7, `tall` 25x25 s2 and s7.
  - Still a 2-wide door on an odd front, half a block off: for example `tall` 24x24 s0, `##~~##~~#%%##~~##~~##` with x=0..20 and axis 10, door at 9..10. The same holds for s1, s3, s4, s5 and s6, for the same seeds in `tall-terracotta` (including highrise-3, unchanged), and for `tall-brick` s0, s1, s3, s5 and s6.
  - New this round: a 1-wide door on an even front, half a block off the other way. `tall` 25x25 s0, `##~~##~~##%###~~##~~##` with x=0..21 and axis 10.5, door at 10. The same holds for s1 and s3..s6.
  - The parity test looks at something other than the drawn front wall. The footprint is reported as 22 while the front row is 21 cells with an outside column, so it probably counts the footprint or the volume width. Fix direction is as in round 1: take the parity of the front wall that is actually drawn, between its corner cells.
- **Should-fix, slope fallback: fixed.** Another face is tried before a door is walled up: hill s2, s5 and s12 have an east door. 4 of 16 hill houses are walled up (s7, s8, s9, s13). The walled bay gets its window back (s7: `sealed-o7` at x=3 on the kitchen wing's south face, `!~#~#!`). Whether DS-35 must hold on slopes is now John's REVIEW question, so it no longer blocks this item.
- **Should-fix, rise of -3 or less: fixed.** The side- and back-door tables show 0 for every type.

## Suggestions (polish backlog)

- **High-rise main door on the axis** (the unfixed should-fix above, 23 of 32 cases off). Take the parity of the drawn front wall. On odd fronts use 1 leaf, or 3. On even fronts use 2.
- **Very long side-door flights, made worse by the other-face retry.** Runs are now at most 3 risers, but the climb itself grew. Hall side doors with a rise of 9 or more went from 638 to 1423 (of 4958), and the tallest went from 20 to 45. Temples went from 2388 to 2476 and houses from 584 to 617 (`check-after`). A 45-riser switchback to a hall side door obeys DS-45 as worded but is not a side door anyone would build. Round 1's proposed rule (secondary doors rise at most 3, or 5 with a landing, else another face, else wall up) would cap it. I repeat it for John alongside the slope question.
- **The restored window is 1 high**: s7's `sealed-o7` sits at sill +1, head +2, while its neighbours run to head +3, so the heads on that face don't line up. Give the restored window the head and width of the window bay it replaced.

## For John

- With the slope question in REVIEW, please also decide round 1's proposed rule on secondary-door rise. Without it, the face retry turns some side doors into stair towers: hall side doors up to 45 risers.

Verdict: meets the standard

# DS35DOORS architect re-review, round 3 (2026-10-29)

Mode: review. Round: re-review after r3 (round 2's open polish item, the slope behaviour John decided, the coder's two questions, and anything the fix broke).
Counts: 0 violations, 0 should-fix, 3 suggestions, 0 new proposed rules.
Looked at (worktree `.claude/worktrees/p6-ds35doors`, HEAD ee3b610a; CLI rebuilt there):
- the coder's report `p6-ds35doors-r3.md`; `floorplans-r3/after/house-hill-s3.txt`;
- my own `check --seeds 0..59` runs on `hill:64:20:24` and `noise:7:64:6:32`: house (medieval and plain, 960 cases), hall (plain, 600), temple (medieval, 480). From each I read the failing count, the Door rise tables and `flight-landings`;
- IR from `gen` of house medieval 20x20 on `hill:64:20:24` seeds 0..11: doors and site boxes. For s4 I traced every stair, rail and paving box of the back flight;
- floorplan front rows of 32 high-rises (`tall` 24x24 s0..7, `tall-brick` 30x30 s0..7, `tall` 25x25 s0..7, `tall-terracotta` 24x24 s0..7).

No images: the gallery PNGs in `viewer/out/gallery/` are dated 2026-10-28 20:50, before r3, and the r3 gallery ran on the perf worker. Not seen in pictures or in game: how the slope flights look.

## Checks

- **DS-45 on the new slope flights: kept.**
  - In my runs every side and back door has "run 4+" = 0 and "<= -3" = 0, for house (515 doors), hall (598) and temple (477). `entrance-count` fails 0 times, and the check reports 0 failing cases overall.
  - `flight-landings` counts only main-door flights. In my runs it shows house 0, hall 54 and temple 111, all on main doors, outside this item.
  - Doors per house: 11 of 12 hill houses (s0..11) have two doors. Only s0 has one, and it is a one-storey house.
- **The flights read as built (judged from the IR).** Hill s4's back door (south, x=9) goes down like this:
  - three risers;
  - a level landing at z=10;
  - two more risers;
  - a quarter turn west onto a level cell at the foot.
  
  Rails run on both sides all the way, and there are no diagonal gaps in the run. That is how a hillside house's garden stair is built. In the other seeds the doors move to the face that works (s7, s8: west), which is what John asked for.
- **Correction to the brief: not every flight is "at most 3 risers".** Only the first retry phase prefers that; the later phases accept a longer flight, broken by landings and turns. Side and back doors rising 9 or more: house 139 of 515 (27 %), hall 135 of 598, temple 192 of 477; the highest rise is 19. All of them keep DS-45's 3-riser runs, so this is not a violation. On steep lots a long garden stair with landings is believable. It stays on the polish backlog (round 2's long-climbs line, which now covers houses and temples too). Round 1's proposed rise cap would trade these stairs for walled-up doors, so it is John's call against his "must hold on slopes".
- **High-rise main door on the axis: fixed (round 2's polish item).** All 32 fronts are symmetric about the door:
  - 2 leaves on even fronts, e.g. `tall` 24x24 s2, `##~~##~~##%%##~~##~~##`;
  - 3 leaves on odd fronts, e.g. s0, `##~~##~~#%%%#~~##~~##` (front x=0..20, door 9..11, axis 10).
  - In every case each leaf group has a one-block solid jamb, so DS-51 holds.

## Answers to the coder's questions

- **3-leaf main door on odd high-rise fronts: yes, acceptable.**
  - A row of three lobby doors is ordinary on an office or apartment tower, and DS-35 allows 2-3 wide.
  - Set the hinges so the outer leaves swing from the jambs and the middle one pairs with one of them.
  - The strongest alternative is one leaf with glazed sidelights in the 3-wide opening (glass, pane or tinted, between door and jamb). It reads more domestic, so it suits small apartment towers better than office towers. Keep 3 leaves as the default.
- **High-rise second door only into the lobby hall rows (191 of 1000 noise high-rises keep one door): acceptable for this item, as the ground-blocked exemption John asked for. Worth improving.**
  - The truest second door of a tall building is the **fire-stair exit**: the stair core opening straight outdoors on another face, which every real high-rise has (DS-22's single core makes this natural).
  - Letting the second door open from the stair core, or from a service corridor, when no lobby row works should clear most of the 191.
  - Suggestion below; not blocking.

## Suggestions (polish backlog)

- **High-rise fire-exit door:** when no lobby row can take the second door, open it from the stair core or a corridor onto another face (191 of 1000 on noise, 113 of 1000 on hill, per the coder's numbers).
- **Long secondary climbs** (carried over, now measured on houses and temples too): side and back doors rising 9 or more on 27-40 % of slope cases, up to 19. Prefer the lowest-rise face when several work, before taking the first that passes.
- **Restored window 1 high** (carried over from round 2; not re-checked this round).

## For John

- Nothing blocks. The trade-off to decide is long garden stairs with landings (now about a quarter to two fifths of side doors on steep lots) against walled-up second doors. I lean to keeping the stairs and preferring the lowest face.

Verdict: meets the standard
