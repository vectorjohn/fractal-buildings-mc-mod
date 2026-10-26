# 7.13 high-rise plan: interior questions (2026-10-25)

*Frozen copy of the agent report `.claude/reports/highrise-plan-interior.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: question. Plan: `docs/plans/highrises.md` (Design questions, interior-designer 1-4).
Read: the plan (whole), `docs/design/notes/highrises.md`, STANDARDS DS-10..16, 22..25, 32,
`docs/plans/furniture.md` ("Which room gets what", "Walkability invariant"),
`InteriorPass.hookSites`, `RoomProgram.DEFAULT`, `HouseRooms`, mod `HookPlacer`,
`FloorPlan.SOFT_ASPECT` (2.0) and `RoomShapeTest` (< 8 % of rooms over aspect 2).
Not run: the installed CLI predates `floorplan`, so band depths below are computed from the plan's
geometry (podium interior about lot - 4; band depth D = (interior - hall) / 2 - 1), not measured:
lot 20 -> D ~6, lot 28 -> D ~10, lot 36 -> D ~13, lot 44 -> RACETRACK territory.

# What today's hooks can and can't do

Each hook is one block on a **random wall cell** of the room (not within 1 of a door or the core,
hooks at least 2 apart). In game: `table` = crafting table, `hearth` = furnace, `counter` = barrel
(lid up), `bench` = spruce bottom slab, `chest` = loot chest (high-rise uses the house table),
`light` = lantern, hung only if a ceiling is within 4 above (so on the floor in 5-clear rooms, and
hung half the time otherwise), `workstation` = lectern/barrel/loom for a high-rise, `bed` = bed.
They can't make sets (chairs at a table), face the entrance, or sit away from walls. So before 7.5 a
room reads by *what kinds of block it has and how many*, nothing more; keep the lists honest and
keep them small.

# Q1. Lobby back-of-house

**Recommendation.** Band A beside the core block, per side: the side nearer the street entrance's
x is `office` (manager / concierge), the other side `storage` (porter's store, mail room). Behind
the block: `storage`. Each room min 3x3; if a side segment is deeper than 1.5x its width, cut it in
depth: the front part (touching the lobby, door onto it) keeps the use, the back part is `storage`
with a door from the front room (back-of-house may be reached through back-of-house). Hooks:
office table, bench, chest, light; storage chest x2, counter, light.
**Reason.** A period apartment or office lobby has a desk, a manager's office and a porter's store,
not more offices; two `office` rooms beside a lobby read as a mistake.
**Alternative.** Both `office` (the plan's assumption): fine for an office tower, odd for flats.

# Q2. Banquet back band

**Recommendation.** Band A: the side nearer the stairwell door's far end is `kitchen` (servery),
the other side `storage` (cloakroom and store); behind the block `storage` (pantry) with its door
toward the kitchen side when the split allows. Kitchen min 3x4, aspect <= 1.5 by the same depth
cut as Q1 (back part `storage`). No head table or stage before 7.5: random wall hooks can't build
one, and a lone crafting table "head table" reads as nothing. 7.5 adds them (rows below).
**Reason.** Banquets are served from a kitchen next door; the other side needs a non-public room,
and a cloakroom/store is what a hall has.
**Alternative.** Both sides kitchen (two kitchens on a floor is a DS-12-style programme smell).

# Q3. Unit sizes and counts

Bands deeper than ~7 can't be split side by side into square rooms (a 4-wide bedroom 10 deep is
aspect 2.5, breaks DS-11). So units get a **private entry strip** on the hall side:

| Band depth D | Entry strip E (use `entry`, door from the hall) | Facade room depth F |
|---|---|---|
| <= 7 | none (hall door opens into the main room) | D |
| 8-10 | 2 | D - 3 |
| 11-12 | 3 | D - 4 |
| 13+ | D - 9 (known issue until RACETRACK) | 8 |

Facade rooms sit side by side, each touching the facade; each opens off the entry strip (or off
the main room when there is no strip). Every facade room's width >= max(min, ceil(F / 1.5)), never
below ceil(F / 2).

- **Apartment** (main room use `flat`: living with an open kitchenette, DS-10): studio = one `flat`
  room, unit 4-8 wide (typical 6); 1-bed = `flat` >= 4 (typ 5-6) + bedroom >= 4 (3x4 min,
  double bed), unit >= 9 wide (typ 10-11); 2-bed = flat >= 5 + 2 bedrooms >= 4, unit >= 14
  (typ 14-16). Draw a target width 9-11 per building; a segment narrower than 9 gets a studio,
  one under 4 wide becomes a `storage` closet off the hall.
- **Office**: one room `office`, width >= max(6, ceil(D / 1.5)), typical 8-12 (draw once).
- **Suites** (SUITES floor): 2-bed units, target 14-18, 2-4 per floor, each >= 60 cells; merge
  leftovers into the neighbour rather than adding a studio.
- **Back room behind the core**: a bedroom (apartments) or `office`, door from the neighbouring
  unit's **`flat`** room, never from its bedroom (bedrooms not reached through bedrooms), so the
  band-A side unit puts its flat room on the core side.
- **Count**: per band segment of length L, units = floor((L + 1) / (W + 1)). Roughly (computed):
  lot 20 plate 2-3 units/floor; lot 28 4-5 (band B 2 one-beds, band A a studio each side + back
  room); lot 36 5-7. Offices about the same count at W 10.

# Q4. Hook lists until 7.5

Lights first, because the plan's lists leave dark floor (DS-24): a hung lantern at y2 lights floor
cells up to 12 blocks away horizontally (11 at y3), so a 26x10 lobby with 2 lights has dark ends,
and an 8x8 room with one hung wall lantern has a dark far corner.

- **`ceiling_light` gets its own site rule** (B3a): not wall cells but an interior grid of pitch
  <= 10 in x and z, half a pitch from the walls; door exclusion waived (it hangs, never blocks).
  Hung with an `iron_chain` so the lantern sits at y2 (3 clear) or y3 (5 clear): chain length =
  clear - 3. Every room with w + d >= 15 gets the grid (at least one at its centre).
- Lists (n = room count of wall hooks; cap by candidates as today):

| Use | Hooks |
|---|---|
| lobby | counter x2, bench x4, table, light x2, ceiling_light grid (no chest, no workstation) |
| banquet | table x6, bench x6, counter x2 (buffet), ceiling_light grid |
| open | table x4, bench x4, chest, ceiling_light grid |
| office | table + bench per 12 cells (1-4 each), chest, light (+ grid if w + d >= 15) |
| flat | hearth (stove), counter, table, bench x2, chest, light |
| bedroom | bed, chest, light |
| entry | chest, light |
| kitchen | hearth, counter x2, chest, light |
| storage | chest x2, counter, light |
| corridor | ceiling_light every 8 cells along its length (min 1) |
| stairwell, stair house | ceiling_light at the landing door cell only (landing row stays empty) |

No `workstation` anywhere in a high-rise (the high-rise set is lectern/barrel/loom: job blocks as
decoration). Suites use the flat/bedroom lists. This reads as "lobby / banquet / office / flat"
only by counts; that is the honest ceiling until 7.5.

**For 7.5's "Which room gets what"** (proposed rows): lobby: reception desk (shop_counter) on the
wall facing the entrance + 2 seating groups, optional plants, rug, chandelier (5 clear); banquet:
dining_table groups on a grid + head long_table at an end wall, optional stage (slab platform 1
high, 3 x 7), chandelier; open: long_tables + chairs; office: desk + chair per 12 cells, bookcase,
chest_nook; flat: short kitchen_run (stove, sink, counter: DS-14), post_table + 2 chairs, sofa;
storage: chest_nook, barrels, shelves; corridor, stairwell: none (plants only where 3+ wide).

# Plan items against DS rules

- **DS-11** (rooms lean square): "living + bedroom from 8 wide, + 2 bedrooms from 12", side by side,
  on bands ~10-13 deep gives rooms of aspect 2.5-4. Fix: the entry strip and widths in Q3.
- **DS-24** (no dark rooms): `ceiling_light x2` in grand rooms, wall-only hook sites, and
  `HookPlacer`'s hang search (4 up) in 5-clear rooms leave dark floor. Fix: Q4's grid rule. Also
  the shaft pit (3x3 floor, dark, sealed): make its floor bottom slabs (no spawning) or light it
  (overlaps the architect's question 1).
- **DS-15** (fireplaces have chimneys): `living: hearth` becomes a fireplace under 7.5, which then
  needs a flue up through every storey. Use the use `flat` for the unit's main room and map its
  hearth to a stove, not a fireplace.
- **DS-25** (every habitable room has a window): the plan's B2 tests don't assert it; inner cuts and
  back rooms could miss every window cell. Add a test: every `flat`/`bedroom`/`office`/`lobby`/
  `banquet`/`open` leaf touches a window cell (`entry`, `storage`, `kitchen` exempt). B2's "every
  leaf touches the boundary" test needs `entry` added to its exemptions.
- **DS-14** (kitchen job blocks): `hearth` = furnace, no sink; same gap as houses today, closed by
  7.5's kitchen_run. Not new; noted.
- DS-22: on banquet/open floors the stairwell opens into the grand room; I read that as allowed
  ("lobby or open floor"). DS-32 notes' "columns about every 6" on open floors is not in the plan
  (architect's call, suggestion only).

# For John

Nothing blocking. One taste call if he wants it: apartments get a windowless private hallway on
the corridor side (as real deep-plan flats do); the alternative is fewer, wider units.
