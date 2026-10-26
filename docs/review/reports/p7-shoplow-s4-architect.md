# p7-shoplow-s4 architect review (2026-10-26)

Mode: review. Round: first.
Counts: 2 violations, 3 should-fix, 5 suggestions, 3 proposed rules.

Looked at:
- Pictures (p7-shoplow-s4 worktree, `viewer/out/gallery/`, 16 seeds each): `shop-market-medieval-front-left.png`, `-front.png`, `-back-right.png`, `-cutaway.png`; `shop-market-plain-front-left.png`, `-front.png`; `shop-market-before-front-left.png` (base CLI, old halls on seeds 5 and 14).
- Floor plans (S4 CLI, `floorplan --square`): medieval lot 13x22 seed 1 (all storeys); medieval lot 11x18 seeds 0, 4, 12 (storey table).
- IR (`viewer/gallery-data/gen/shop-market-{medieval,plain}/seed-0..15.json`, jq): storey heights, north-face window x by storey, hooks below storey 1, storey-0 boxes (seed 4).
- Base run-phase6 CLI: `batch` of medieval shops, seeds 0..7, lot 13x22, north-face windows (to see whether the stacking slip is new).
- City data (S4 worktree, `viewer/out/city/{river-town,town-walled,gate-temperate-city,mixed-city}/districts/*.json`): every square or market plaza and the lots within 16 blocks of it. `Districts.java` (`landmarksAndPlazas`, `hallLots`, `frontageLots`, `placeAlong`).
- Not seen: in-game pictures (block shapes, the stair and its rails, the real light under the floor), the gallery `top` camera, a hall inside a city scene (none exist).

## Question 1: market hall or carport?

Today it reads as **a tall town house on short stilts**, which from the square is a carport under a box. It does not yet read as a market house. Five reasons, most important first:
1. **The open floor is the lowest band of the building.** It has 2 clear on 9 of 16 seeds and 3 clear on the other 7, under a 4-clear hall and one or two 3-clear stores. At Ledbury, Ross-on-Wye and Chipping Campden the open ground storey is as tall as the room above, or taller. The trade floor is the reason the building exists, so it has to be the dominant band.
2. **There are too many storeys.** 7 of 16 seeds have 4 storeys (open floor, hall, two stores) and are 18-24 high on an 11x12 plan: a tower-house proportion. A market house is the open floor, one room and a roof.
3. **The posts read as studs.** They are 1-block frame posts at 3-block centres, the same as the timber frame above, standing straight on the paving. Nothing says "arcade": no stone pads, no brackets or arch heads, and the bay rhythm on the square face is uneven.
4. **The floor is empty and unlit.** No stall, no light (DS-24, below). In the front views the open floor is a dark slot.
5. **It is a free-standing building type placed as a frontage lot.** It is open on four sides with windows on four faces, which is right for an island in the market place. It sits on a lot line with its back to the next yard.

Items 1 and 4 are violations, items 2-4 are should-fix, and item 5 belongs to the Districts follow-up (question 2). With those fixed, the same building reads as a market house.

## Violations

- **DS-23** The open trading floor has **2 clear blocks on 9 of 16 seeds** in both presets: medieval and plain seeds 4-10, 13 and 15 have storey 0 at floor-to-floor 3, with paving at local y=0, standing at y=1 and the hall floor at y=3. The floor plan of lot 11x18 (seeds 0, 4, 12) says "clear air about 2 blocks". The **other 7 seeds have 3 clear**.
  - DS-23 says "ground floors of shops, halls and tower bases 4-5 clear", and 2-high spaces are "always wrong". A 2-clear space a player walks through is a crawlspace, and it is the main cause of the carport reading.
  - My A6 answer ("legs 3, or 4 from width 11") caused this. I gave the lift without saying it counts from the paving slab, so legs 3 became 2 clear. I withdraw that number.
  - Fix direction: give the open floor **4 clear (floor-to-floor 5) at widths 9-10 and 5 clear from width 11**, the same on every seed of a width. It sets the stair length to 5 or 6 risers, which fit along an 11-wide back edge.
- **DS-24** **No light source under the hall on 16 of 16 seeds** in both presets. No hook sits below storey 1; every `ceiling_light` and `light` is at y >= 5. The open floor is a covered space of 11x12 (99-132 cells) with block light 0 at its middle, and DS-24 names covered arcades.
  - Fix direction: hang lanterns on chains from the hall floor, about one per 5x5 bay, kept clear of the stair. Each needs 3 blocks of headroom under it, which the 4-5 clear above gives (at 2 clear a hanging lantern blocks the walk). Wall torches on the posts also work.

## Should fix

- **Massing: one room over the market, not a tower.** 7 of 16 seeds (1, 2, 5, 6, 10, 13, 14 in both presets) stack two `store` storeys over the hall, so the building is 4 storeys and 18-24 high on an 11x12 plan.
  - Why: a market house is long and low, the open floor and one civic room under a steep roof. The square's height landmark is the temple or town hall, not the market house.
  - Fix direction: **open floor (4-5 clear) + hall (4-5 clear) + roof**. The store goes in the roof space, which a medieval 1/1 gable over 11 wide already gives about 5 blocks of, or it is dropped.
  - The total height comes to about 10-11 to the eave. That is lower than the typical house beside it at the eave, and taller in presence because of the open base.
  - Which storey is the store is the interior designer's call (I1). How many storeys there are is mine.
- **Show the trade on the open floor** (boundary: the floor layout is mine, the contents are the interior designer's). 16 of 16 open floors are bare paving.
  - Why: an empty covered floor reads as a carport or a parking deck. The same finding was in the polish backlog after 5.12 ("market hall floor is empty (no stalls)"); this is the second time.
  - Fix direction: 2-4 stall positions on the open floor: against the posts on the long sides, plus one beside the stair. Keep clear a 3-wide walk through the middle on the axis from the square face to the back, and a 2x2 cell at the stair foot. What the stalls are made of (counter slabs, barrels, carpet awnings, a weighing beam) is the interior designer's.
- **Posts that read as an arcade.** On 16 of 16 seeds the posts are frame-sized and set at 0, 3, 6, 10 across the 11-wide square face (seed 1 plan), so the bays are uneven and asymmetric.
  - Why: from 30 blocks the open floor carries the building's identity, and on the square face it needs a calm, centred rhythm (DS-25's even bays).
  - Fix direction:
    - On the face to the square, put the posts symmetric about the centre with a centre bay on the axis: width 11 at 0, 3, 7, 10 (bays of 2, 3 and 2 clear); width 13 at 0, 4, 8, 12; width 9 at 0, 4, 8.
    - Stand each post on a 1-block stone pad in the plinth material.
    - Put an upside-down stair (DS-27) under the beam at each post head, as a bracket or arch spring. The heads are visible in game only, so they need an in-game shot.

## Suggestions

- **Window stacking (DS-25, pre-existing; tracked by ROADMAP 7.9).** The north (square) face has hall windows at x = 1, 4, 5, 8 and store windows at 1, 4, 6, 8 on seed 1, and the store above that at 2, 5, 6, 8. All 16 seeds of both presets miss the stack. The base generator's medieval shops do the same (seeds 1, 2, 5, 6), so this item did not introduce it. The market hall is the square's most-viewed facade, so it is a good first target for 7.9. The hall-only massing above removes half of it.
- **Fewer, grander hall windows.** The hall storey has 18 windows for one 90-cell room, about one every other cell on all four faces. For a civic room on the square, use pairs of 1x2 lights centred in each bay between posts (stacked over the post rhythm), or one 2x2 per bay on the square face, and fewer on the back.
- **Stone pads and a one-step plinth edge.** The paving sits straight on the terrain with the posts on it. A 1-high stone kerb round the paved floor, set flush or one slab up, marks it as a building's floor and not a yard.
- **Gable to the square, with a clock or bell.** Most seeds already put the gable to the square. Add a small bellcote or clock board (a trapdoor or banner) in the gable apex on the square face; Ross-on-Wye and many Flemish halles carry one. Later, with PLAZAS.
- **City and modern styles.** Plain on a 1:40 slope reads well: dark posts, a calm brick-coloured mass and a slate roof. The modern city styles (DS-37) will need a concrete or stone arcade version instead of timber posts. Not seen; check once halls appear in cities.

## Question 2: spec for the Districts follow-up (squares lined by ordinary lots)

**Why halls never appear.** The district planner lays lots only along street runs (`frontageLots`/`placeAlong`). A square is `PLAZA` cells, so nothing faces it except the hall or temple lot that `hallLots` puts flush against it.
- Measured in the S4 worktree's city data: town-walled's 34x46 market has one hall lot touching it and the next lot 14 away. gate-temperate-city's 23x27 square has only a hall 9 away. river-town's 31x33 market has one shop 6 away facing away from it. Only mixed-city d52 has ordinary lots near a square.
- These are the "big empty pads" of John's PLAZAS remark, and the same fault: a square nobody fronts is not a square.

The spec has two parts. Part A gives halls on every eligible square with no dependence on frontage. Part B makes squares into rooms.

**A. The market house stands in the market place (island lot).**
- On every `market` plaza, and on 60 % of `square` plazas (A6's share, still one per square), place **one island lot inside the plaza**, marked `square=true`.
  - Lot 13x17 to 15x21; the hall is 11-13 wide, the rest is clear paving.
  - Long axis parallel to the plaza's long axis, centred across its width.
  - Set toward the end away from the hall or temple, so the axis from the main street mouth to the landmark's door stays open.
  - At least 8 clear paving on every side, and at least 12 in front of a hall or temple door.
  - Only on plazas with both sides at least 28 (agent's number: 13 + 2 x 8 rounded up). Smaller squares get a well or market cross instead (PLAZAS).
- This is how the English precedents do it (Ledbury, Ross-on-Wye, Chipping Campden, Dunster Yarn Market): the market house stands in the market place or the widened street, open all round. That is why it has no front and no back.
  - The plaza is already graded level (relief <= 1 by construction) and reachable (`plazaOk`).
  - The open floor gives access from every side (DS-6, DS-26: the square is its street).
- Keep S4's frontage rule (a square-facing shop with front >= 11) as the fallback where no island fits.
- Never in hamlets and villages: their greens get no hall (A6 stands).

**B. Every square is framed by building fronts.**
- **Which lots.** Each edge of a square not taken by the hall or temple lot or by a street mouth becomes a frontage run, as if it were a street: lots face the square, with their front edge on the plaza edge and setback 0.
  - Types: **at least 60 % shops** (or in city and metropolis, at density 70+, mixed shop-with-flats blocks, DS-37), and inns or taverns when that type exists. Houses at most 25 %. **No workshops**: cart yards go to back streets.
  - A corner where two square sides meet gets a corner lot facing both sides (a door on each face, DS-35 proposed).
- **How many and how wide.**
  - Frontages **7-11 blocks** (narrow burgage fronts: 5-8 m real x 1.25-1.5), the depth the type's minimum or more (DS-47's rear-yard caps in cities).
  - Lot count per side = the free length / 9, rounded down; at least 1 per side over 12 long.
  - A typical 30x32 town square with four corner mouths of 5 and a 26-wide hall gets about **8 ordinary lots**; a 34x46 market about 12.
  - **No hashed lot gap** along a square (the 1-in-4 gap in `placeAlong` is off); only a mouth or a lane breaks the row.
- **Coverage target** (checkable): building fronts along **>= 70 % of the square's perimeter in towns, >= 80 % in city and metropolis**. Street mouths and the landmark lot make up the rest. No side wholly open unless it faces water (a quay) or a wall.
  - In cities DS-47's "80 % of every street edge" should count square edges as street edges, so this is the same number.
- **Mouths.** At most one street entry per side, preferably at a corner and off the landmark's axis, so views into the square close on a building front (Sitte). A street passing the square runs along one side, never diagonally across it.
- **Heights.** Square fronts take their district's class with one storey of bias up (squares are the best addresses). In towns they stay below the hall or temple's eave, so the landmark leads; in cities (high-rises allowed by DS-18 at the core) this cap does not apply.
- **Check.** A `city-check` line per square: the frontage share (target above), the ordinary-lot count, and whether it has an island hall. Report the shares on the 9 gate sites and river-town, town-walled and mixed-city before and after.

**Where it goes.** One Districts item, scoped **town and up**.
- It shares code with METROFILL: `frontageLots` and `placeAlong`, the lot-gap rule, and DS-47's street-edge count.
- So fold part B into METROFILL as a step "square frontage" **only if METROFILL's scope is widened to towns**. METROFILL is density 70+ only, and most squares with a market are in towns. Otherwise it is a sibling item that lands right after METROFILL's lot changes, plus part A (small).
- METROFILL's A9 forecourt squares for halls and temples follow the same frontage rule.
- PLAZAS (dressing: well, benches, lamps, paving wear) stays separate.

## Proposed rules

- **Open ground floors under a building are at least 4 clear** (the trading floor of a market house, an arcade, a loggia, a covered passage under a building, a stilted house's undercroft where people walk), and they are lit (DS-24).
  - Why: S4's 2- and 3-clear undercrofts read as crawlspaces. DS-23's "market halls 6-10" reads as the enclosed market-hall volume and does not cover this case.
  - Scope: all types with a walkable open ground storey.
  - Enforcement: code candidate (the storey-0 clear height of open storeys; a light hook in any covered walkable cell).
  - From: DS-23 finding above. The DS-23 text could instead be clarified in place: "open arcades and market-house undercrofts 4-5 clear; enclosed market halls 6-10".
- **Every square is framed** (the part B coverage number, 70 % town and 80 % city of the square's perimeter in building fronts, with at most one mouth per side).
  - Why: squares are rooms; unfronted squares are the "empty pads".
  - Scope: town and up.
  - Enforcement: code (`city-check` per square).
  - From: question 2; recurs with John's PLAZAS remark (REVIEW 6.8) and METROFILL A9.
- **Covered public floors show their use** (a market floor has stalls, an arcade has shopfronts or benches; never a bare covered slab).
  - Why: an empty covered floor reads as a carport.
  - Scope: market halls, arcades, loggias.
  - Enforcement: guideline, and a code candidate (furniture hooks on the open storey).
  - From: the stall should-fix; recurs from the 5.12 polish line "market hall floor is empty (no stalls)".
- Clarification for `flight-landings` (DS-45 stair runs): the market-hall flight is inside the building's footprint under its own floor, so it is an interior stair (DS-5), not an exterior flight. The 3-riser run limit should not count it once the undercroft is 4-5 high. Not a new rule: a note for whoever writes the check.

## For John

- Nothing blocks on you. The proposed rules above are yours to accept: "open ground floors at least 4 clear" (or the DS-23 clarification) and "every square is framed" (the 70/80 % numbers).

Verdict: should-fix round needed
