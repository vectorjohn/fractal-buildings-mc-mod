# P6 RAILFIX (6.9 defect, DS-8 amendment) architect review (2026-10-25)

*Frozen copy of the agent report `.claude/reports/p6-railfix-architect.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: review. Round: first.
Counts: 1 violation, 2 should-fix, 3 suggestions, 2 proposed rules.
Looked at: docs/review/phase6/railfix/shop-slope-deep-after2.png, house-slope-after2.png,
tower-medieval-after2.png; docs/review/phase6/railings/shop-slope-deep-after.png,
house-slope-after.png, tower-medieval-after.png (all in worktree p6-railfix). At gallery scale
the rails are a few dark pixels, so the judgement below comes from the voxel IR of the
same galleries (`viewer/gallery-data/gen/{shop-slope-deep,house-slope,tower-medieval}/seed-*.json`,
generated 18:47, after the fix commit 18:40): 40 buildings (shop 0..15, house 0..7,
tower 0..15), scanned with a throwaway node script (rasterise shell + site boxes; rail
height against the adjacent tread; posts with no same-y solid neighbour; open tread edges
with a drop of 2+; standable surfaces within 0.6 below a rail top; rails beside doors), plus
ASCII sections of house 0, house 3, shop 0, tower 0. Code read: the diff of ff97f8df
(`InstancePassV1.rails`, `Invariants.walkFloor`) and `raster/Shapes` (fence/rail
connections). Not seen: in-game shapes (no client screenshot), settlement scenes.

## What the fix gets right

- Height: every one of the 170 rail blocks in the 40 buildings sits exactly one block above the
  highest tread beside it (top 1.5 above that tread's surface). John's complaint ("only go up
  to ground level") is fixed where rails exist.
- Doors: no rail stands beside or in front of any door leaf (0 of 40); the wide-door case the
  coder found is handled.
- Walk model: rail and fence tops no longer count as floor; sensible, and nothing broke.
- Straight runs at one level join (posts connect to the kerb or the next rail); the stepped
  run up a flight joins each post to the next step's kerb, so a straight flight has no
  horizontal gap.

## Violations

- **DS-8** open sides on the lot line get no rail, shop-slope-deep 8 of 16 seeds (0, 1, 7, 8,
  10, 11, 12, 15), house-slope 4 of 8 (3, 4, 6, 7), tower-medieval 3 of 16 (1, 4, 13; 1-2 open
  cells each). Worst: house seed 3, the flight runs along the lot's front row (local z = -1,
  x -1..2) on top of the plinth, with an unguarded drop of 8 to 11 blocks on the street side;
  shop seed 0, the 8-cell stair landing along the front (z = -1, y 0) has a 3-block drop to
  the street over its whole length. Cause: `rails` skips cells outside the lot, so a flight on
  the lot edge is never railed. DS-8 asks for a rail on every open side of an exterior flight
  or landing; this is the case where falling is most likely and highest. Pre-existing (6.9
  *Known issues*: "a flight on a lot edge has no rail on the street side"), not caused by
  this fix, but the fix exists to make rails functional and half the shop seeds still have
  none where it matters. Fix direction: when a flight or landing needs a rail on a side that
  is outside the lot, move the flight one cell inward so the rail fits inside the lot (or
  build the rail as a parapet on the outer tread row); the coordinator may split this into
  its own item if it is too big for this round.

## Should fix

- **Corner gaps where two rails meet diagonally**, house-slope 4 of 8 seeds (0, 1, 2, 5: two
  gaps each, at the door landing and at the turn at the foot), tower-medieval 10 of 16 (0, 2,
  3, 5, 6, 7, 9, 11, 12, 14, at the door landing). Example house seed 0: the top tread (3, 0, 0)
  is the landing in front of the door (3, 1, 1); its rails are at (2, 1, 0) and (3, 1, -1),
  diagonal to each other, and the outside corner cell (2, *, -1) is empty down to the ground
  (10+ blocks). Neither post has an arm toward the corner, so the gap between them is 0.75
  wide, wider than the player (0.6): a player stepping out of the door can walk diagonally
  off the landing. Tower seed 0 has the same at its door landing (drop 2). This is exactly
  "never functional at keeping you from falling", at the door, where everyone stands. Fix
  direction: a rail run that turns gets a corner post in the outside corner cell (kerb up to
  the landing level, rail on it), so the two runs join.
- **Stepped flight rails can be stepped over**, house-slope 4 of 8 (0, 1, 2, 5), tower 5 of 16
  (4, 5, 7, 11, 14), shop 2 of 16 (5, 14). Beside a flight, the rail beside tread
  k tops out 1.5 above tread k but only 0.5 above tread k+1 and the kerb beside k+1, which
  are flush with it. Minecraft auto-steps 0.6, so a player going down the flight close to the
  edge steps onto the next post's arm (diagonally, past the 0.25-wide post above) and off the
  side (house seed 0: rail (5, -1, -1) from tread (4, -1, 0); drop 10+). Fix direction: beside
  a flight, a rail's top must be 1.5 above every walking surface next to it, not only its own
  tread: e.g. raise the kerb beside tread k to tread k+1's level (a stepped cheek wall) with
  the rail on that, or use the tread above's level for the whole step. Whatever is chosen,
  extend `RailingTest` with "no standable surface within 0.6 below a rail top on the open
  side".

## Suggestions

- The STANDARDS DS-8 amendment's "For implementers" paragraph still describes the old bug
  (rail at the tread's own y); after merge it should say what the code does now, and its
  "step up beside a flight one block above each step" wording is what produces the
  step-over gap above (see Proposed rules).
- A long landing built of stair blocks (shop seeds 0, 8, 10, 12: 8 stair cells at one level
  along the front) reads as a step that leads off the edge; a landing should be a slab or
  full block, stairs only where the level changes. (Boundary with the stair work; check in
  game.)
- The in-game look is unseen: dark oak fence posts stepping beside a flight with the
  connections the writer sets. Ask for one client screenshot of a house-slope flight before
  the gate.

## Proposed rules

- DS-8 amendment, wording for flights: "Beside a flight, the rail's top is at least 1.5
  above every walking surface next to it (its own tread, the tread above and any kerb or
  cheek wall); rails turning a corner meet in a corner post. No gap between rail posts is
  wider than 0.5." / why: Minecraft steps 0.6 and the player is 0.6 wide, so stepped and
  diagonal fence posts are not barriers / scope: all exterior flights, landings, terraces,
  bridges / enforcement: code, extend `RailingTest` (standable-surface and corner checks).
  From: the two should-fix items.
- Flights and landings that need a rail keep one cell inside the lot line for it. / why: the
  lot edge is where the street drop is; recurs (6.9 known issue, this review) / scope: site
  pass, all types / enforcement: code, a `RailingTest` case on a flight against the lot edge.

## For John

- Nothing blocks on you. If you look in game: walk down a hillside house's front flight
  hugging the rail, and step diagonally out of the door onto its landing; both still let you
  fall in the generated data.

Verdict: should-fix round needed

# Re-review r2 (2026-10-25, eb846806)

Looked at: docs/review/phase6/railfix/house-slope-r2.png, shop-slope-deep-r2.png,
tower-medieval-r2.png (gallery scale; rails a few pixels, no visible regressions). Re-scanned
the gallery IR (`viewer/gallery-data/gen/*`, 19:23) and regenerated the same three presets
with the worktree CLI (jar 19:20, no source newer than it; output identical to the gallery
data, e.g. house seed 3) into my scratchpad: same 40 buildings, same script.

- **Corner gaps: fixed.** 0 diagonal rail pairs without a corner post (round 1: house 4/8,
  tower 10/16). Corner posts appear where two runs meet (house 0, 1, 2, 5; towers).
- **Step-over: fixed.** 0 standable surfaces within 0.6 below a rail top (round 1: 13
  buildings). Rails beside a flight now sit one above the highest tread around them (2 above
  their own tread on a flight: a stepped cheek, reads fine).
- **DS-8 lot line: not fixed for buildings with a one-cell margin, and not only shops.**
  Still unguarded, unchanged from round 1: shop-slope-deep 8 of 16 (0, 1, 7, 8, 10, 11, 12, 15:
  9-10 cells each, 3-block drop), plus shop 13 (stoop ends, drop 2), 2, 4, 9 (1-2 cells);
  **house-slope 4 of 8 (3, 4, 6, 7)**: the flight runs along the lot's front row with
  **8-11-block drops** (house 3: treads (-1..2, *, -1), house 6: (0..3, *, -1)); tower 2 of 16
  (1, 13, 1-2 cells). The house preset puts a 12x10 building on a 14x12 lot: a one-cell margin
  on every side, exactly the "only a lot-line margin" case; the strict pass finds no route
  and falls back. So the coder's "houses fully guarded" holds for the test set, not for the
  gallery. Remaining unguarded: 15 of 40 buildings (was 17 of 40 by the same count).
- New, minor: a lone rail post with nothing to join (tower 10 at (6, 1, 0) beside the door
  landing; shop 3 at (9, 1, 0), unchanged). A single 0.25-wide fence post on a flush kerb can
  be walked round; not checked in detail. Suggestion: a one-cell rail joins the wall or a
  neighbour, or the kerb rises to make it a solid parapet.

**Block or separate item?** Separate item, opened now. The remaining case can't be fixed in
the rails pass: the rail has no in-lot cell to stand on, so it needs a site/massing change
(keep a 2-cell margin, or set the building back from the lot line, where a flight on a
slope needs a rail on its outer side, or put the stoop inside a recessed porch). This round
fixes the two other failures fully and does not make the lot-line case worse. But the
item's scope must be corrected: **houses and towers as well as shops**, and the worst cases
are the 8-11-block drops on hillside houses, not the shop stoops. Until it lands, DS-8 is
still broken in those buildings (an unfixed violation, carried by that item, not this one).
Recommended test: a `RailingTest` case on the house-slope preset (14x12 lot, plane slope 1),
seeds 3, 4, 6, 7.

Verdict: meets the standard (on the condition that the DS-8 lot-line case is opened as its own item now, scoped to houses, towers and shops)
