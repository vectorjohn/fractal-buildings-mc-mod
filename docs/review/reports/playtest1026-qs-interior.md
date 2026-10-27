# playtest1026-qs interior answers (2026-10-27)

Mode: question. Three questions from John's playtest 2026-10-26.
Looked at: photos `docs/review/phase6/playtest-comments/2026-10-26_20.02.15.jpg` (unwalkable room), `_21.27.38.jpg` (workshop lean-to), `_21.05.45.jpg` (dormers), `_21.29.47.jpg` (roadside hamlet); playtest `REVIEW.md`; ROADMAP 419-424 and polish lines 578-583; STANDARDS DS-5, DS-11..16, DS-24, DS-35; `docs/plans/furniture.md` ("Which room gets what", "Walkability invariant"). Code read: `InteriorPass.hookSites`, `HighriseRooms.hooks`, `HouseRooms.hooks`, `RoomProgram.DEFAULT`, `WorkshopMassing`, `WorkshopType`, `RoofPass.placeDormer`, mod `HookPlacer.bed`, `WorldHooks.headX`. CLI floor plans run at 1008ce76: `floorplan --type highrise --seed 3 --lot 24x20 --style plain` (storeys 0-2); `floorplan --type workshop --lot 20x16`, seeds 0..30 in each of medieval, plains, plain and taiga (124 plans; seed 8 medieval read in full). I could not reproduce John's exact room: his terrain dump isn't committed.

# Q1. Unwalkable rooms and furniture clearance

**Question.** What is the minimum walkway between furniture, walls and doors, and what should happen when a room is too small for its programme?

**What goes wrong today** (photo 20.02.15, a high-rise bedroom about 3x3: a bed, a chest beside the door, a block in front). The room breaks accepted **DS-13** ("never blocking a door, stair or path"), but nothing checks it: the `furnished-rooms-walkable` invariant in `plans/furniture.md` was planned for 7.5 and never built. `hookSites` puts hooks on wall-ring cells. It keeps hooks out of the 3x3 around each door and at least one cell apart from each other, but it never floods the floor. Three gaps follow:
- **The bed's second cell is never reserved.** The hook faces away from the wall and the mod puts the bed's head on the cell in front of it (`headX = x + facing.dx`). So every bed has its **foot to the wall and its head in the middle of the room**. That gets the idiom backwards (head to the wall), and in a 3-wide room it blocks the centre row. This holds for every bed, in every building type.
- **The standing `light` uses up a floor cell even when a ceiling light already hangs there** (LAMPS polish line). In a 9-cell room that cell is often the one the lane needed.
- In a room 3 wide, a hook on each long wall plus a bed head leave no lane.

**Recommendation (proposed DS-48, "Rooms are walkable", which turns DS-13's "path" into numbers):**
1. **Lane.** Clear floor (the floor below, 2 air, nothing that blocks) forms one connected region. It joins the inside cell of every door and arch, the foot and top cells of every stair, and each piece's use cell: the free long side of a bed, the front of a chest, workstation, stove and counter, the seat of a chair. The lane is **1 cell wide** in houses, bedrooms, kitchens and storage, and **2 wide** in halls, shops, lobbies, banquet rooms, taprooms and the workshop path from the cart door (agent's numbers). These cells don't block: carpets, pressure plates, open trapdoors, wall signs, hung lanterns at y2 or above.
2. **Doors.** Nothing on the door cell, on its two neighbours along the wall, or on the cell in front of it on either side (as the furniture plan says; hooks already hold this).
3. **Narrow rooms.** In a room whose short side is 3, furniture goes on one long wall and the end wall away from the door; the other long wall stays clear. In a room 4 wide, both long walls may be used.
4. **Beds.** The head goes against the wall and the bed reaches into the room. Both cells are reserved, and one long side stays free. The fix is to place the hook so it faces the wall (head on the wall cell) or to flip the mod's head offset. The head cell is checked like any other furniture cell.
5. **Smallest room per use** (interior side; matches my standing brief): bedroom 3x3 (single bed and a chest), 3x4 for a double; kitchen 3x3; storage 2x2; living 4x4; dining 3x4; studio flat 4x5 (bed, stove, table).

**When a room is too small, in this order:** (a) place the required pieces first and check the lane after each one; drop optional ones (table, bench, standing light) that break it. (b) Use a smaller piece: a single bed instead of a double, a counter instead of a table. (c) Demote the use: a bedroom that cannot hold bed, lane and use cell becomes a closet or storage room with a chest (`TowerPlate` already demotes for shape and window). DS-12 then looks for the bedroom somewhere else. (d) Only then change the plan, by merging the room or moving the partition. That is the architect's floor layout, so this step belongs to them. A required piece never goes where it breaks the lane.

**Strongest alternative:** keep the rule a guideline until 7.5's pieces arrive. I advise against that: the bug is in today's hooks, John hit it in play, and the flood fill is cheap.

**Rule?** Yes: DS-48 as above, proposed. Enforcement: code. Build the `furnished-rooms-walkable` invariant now, over today's hooks with the bed's head cell, in `check` and `city-check`, and make `hookSites` lane-aware with a flood fill after each placement. Carry the same invariant into 7.5.

**Feeds:** a new Phase 6 bug item (suggest **6.18 ROOMWALK**: invariant, lane-aware hooks, bed orientation). 7.5 Furnishings reuses the invariant. The LAMPS polish line (dropping the standing light frees the cell) helps but doesn't fix this on its own.

# Q2. Inn bias for roadside hamlets

**Question.** What room programme should the inn of a small roadside hamlet have?

**Recommendation.** A hamlet that a road between settlements runs through gets **one inn** (bias, not certainty; the architect sets the share and the lot, which should be at the hub on the through road with a yard behind). The inn is a two-storey building, house-sized to large, about 10x8 to 14x12. The programme, period wayside inn:
- **Storey 0. The taproom** (the common room): the largest room, 5x6 to 8x8, entered straight from the road door. It is the focal point: a fireplace with a chimney (DS-15) on the long wall, a bar counter (`shop_counter`) with barrels behind it across a short wall, and one or two long tables with benches (`long_table` + pew/stool). It has two furniture groups (fire and table) and a 2-wide lane from the door to the bar, the stair and the kitchen. **Kitchen** behind the taproom, opening onto it (DS-10 per style; medieval through a door): a `kitchen_run` with smoker and sink (DS-14), and the back door to the yard (DS-35). Optional: a **store** (barrels, chests) off the kitchen, or a keg cellar where 7.18 B1 gives one (its "tavern" case).
- **Storey 1. A landing or corridor** with **3-5 guest rooms** off it, each 3x3 to 3x4: one bed (head to the wall), a chest, a candle or lantern. No room is reached through another (as DS-32's hall-access rule says for flats). Add **one innkeeper's room** with a double bed where the plate allows. This is DS-12's "bedrooms first" taken to its natural end.
- **Light:** a lantern in every room (DS-24). In the taproom, hanging lanterns at y2+; a chandelier only if the room is 4+ high or 24+ cells.
- **Materials:** a cottage register, rough planks and logs per the style. One cloth colour family for beds and rugs across all the guest rooms, so they read as one house.
- Villagers will claim the guest beds. That is fine, and it makes the inn feel lived in.

This also answers John's "1 bed in each tiny house": the hamlet's beds go to the inn. Plain cottages keep 1-2 beds. A cottage bedroom of 3x4 or more could take a double bed once 7.5 brings `double_bed`.

**Strongest alternative:** no new type; make the largest hamlet house a "house with a taproom" (the front room becomes a taproom and the upper bedrooms multiply). It is cheaper, but it reads as a house: no bar, and no stable yard on the exterior.

**Rule?** Not as a DS rule. It is a building type and a settlement bias. If John likes it, the settlement half could become a rule ("hamlets and villages on a road between settlements have an inn"), owned by the architect.

**Feeds:** a new ROADMAP item for an `inn` type. It sits with 7.5 (pieces: shop_counter, long_table, pews, barrel_rack from the basements answer) and 7.18 B1 (keg cellar). The architect's shoplow-s4 line ("inns or taverns when that type exists") also waits on it. The settlement bias is the architect's.

# Q3. Workshop back door, and dormer stairs from inside

**Workshop.** Across the 124 CLI plans (lot 20x16, seeds 0..30, four styles), 9 seeds have a lean-to, and in every one of them it has a door from the work hall. What it lacks is any **door to the outside**: the cart door is the building's only exterior opening (seed 8 medieval: o2 is the only door out). That fits John's "back door to the lean-to". His in-game building may be a different case: I could not reproduce it without his dump. The lean-to's **programme is wrong** too. It uses `RoomProgram.DEFAULT` "room", so it gets a bed and a table (seed 8: bed h7, table h8), and the living wing's "room" gets bed, table, chest **and a workstation**. Recommendation:
- **Lean-to = store, woodshed or wash-house:** chests and barrels, a counter, a sink used as a tub, a light. No bed. It gets a plain person door to the yard on its back or side face, so the hall gets a second way out (proposed DS-35: a cart door and a person door). The door from the hall stays, kept clear (DS-48 lane).
- **Living wing = a small house:** kitchen (with DS-14 job blocks) and bedroom through `HouseRooms`, not DEFAULT, and no workstation in it. Its back door goes out from the kitchen (DS-35), reached from the hall door.
- **Boundary:** which face gets the door and how it is framed is the architect's call (floor layout and exterior). The uses and contents are mine.
- **Feeds:** the WORKSHOP lean-to door polish line (line 581). Widen it to "lean-to exterior door plus store programme; living wing on the house programme". S.

**Dormer stairs.** There is no interior angle today. Dormers are **sealed** boxes (`RoofPass.placeDormer`: the one cell of air inside is closed by the window, cheeks, cap and slope). No storey or room lies in the roof, and no stair arrives under it. So John's change (stair blocks for the dormer cap and cheeks, photo 21.05.45) is purely the architect's. My only condition is that the cap still seals that cell (`roof-closed`), so no light or mob leak opens into the storey below.

If habitable attics come later (a garret bedroom under the roof), these interior rules apply:
- The stair arrives under the ridge, where there are 2 clear blocks over the top step and its landing (DS-5). It never arrives under a dormer: a dormer gives only about 2 blocks of headroom at the window.
- The lane runs down the ridge line.
- A bed goes with its head to the knee wall, beside a dormer, not in its mouth.
- A dormer opening counts as floor only where it has 2 blocks of headroom.
- Lighting follows DS-24 (daylight doesn't count).

**Feeds:** the DORMER stairs polish line (line 582) for the exterior. Attic rooms would be a new item, not yet proposed.

# Proposed rules

- **DS-48 Rooms are walkable** (proposed). The rule: Q1 points 1-4. Why: John, 20.02.15 "not really walkable". It puts numbers on DS-13. Scope: all rooms. Enforcement: code, the invariant `furnished-rooms-walkable` over hooks now, then over 7.5 pieces. Source: this answer. Recurs: the same invariant is in `plans/furniture.md` and the market-stalls review; it was never built.
- **DS-35 amendment** (proposed rule): a workshop's lean-to and living wing each get their own exterior person door. Enforcement: code candidate `entrance-count`.

# For John

- Q2: do you want a real `inn` type (taproom, bar and guest rooms; my recommendation), or only a bigger hamlet house with a taproom front room?
