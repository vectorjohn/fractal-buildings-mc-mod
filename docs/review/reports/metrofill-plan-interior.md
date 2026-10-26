# METROFILL plan interior questions I1, I2 (2026-10-26)

Mode: question. Round: first.
Looked at: the METROFILL plan as of commit 553d6c60 (`docs/plans/metrofill.md`: Approach, G0, L1, Design questions), `docs/review/reports/p7-metrofill-plan.md`, STANDARDS DS-11, 12, 23, 24, 25, 32, 35, 39, `docs/plans/highrises.md` (plate templates, GRAND/BANQUET back-of-house, DS-25 exempt list, unit-depth table), `docs/plans/furniture.md` (room table), `ShopType`/`ShopMassing` (wing size 5-9 wide by 5-12 deep, up to 14 when deep). Floor plans I ran: `floorplan --lot 14x30 --style medieval --type shop`, seeds 1-4 (output in my scratchpad).

What the shops do today (seeds 1-4, 14x30): the room with the door is `shop` and every other room, rear wing included, is a generic `room` with a bed, table, chest and sometimes a workstation (seed 1: five bedrooms on the ground floor behind the shop, r4 reached only through r5's neighbour chain; seed 2: wing rooms `hall` + `room` stacked four storeys). A deeper wing would just add more ground-floor bedrooms, so I1 needs a programme for the wing, not just a mass.

# Question

I1. What rooms go in a shop's rear wing at density 70 and up, by style and tier, and do windows onto a rear yard (4 behind, 2 beside) satisfy DS-25?
I2. In a 30-deep high-rise podium, are windowless back rooms allowed, or does the podium need light courts or a step-back?

# Recommendation

**I1, ground floor of the rear wing: the shop's back of house. Upper storeys of the wing: part of the dwelling above.**

| Wing storey | Every style | Town styles (medieval, plains, taiga, snowy) | Warm styles (desert, savanna, jungle) | City and metropolis on plain / modern styles |
|---|---|---|---|---|
| Ground, front room (next to the shop) | `storage` (store room): door from the shop floor behind the counter | as left; barrels, chest nook, shelves | as left | stock room |
| Ground, back room (on the yard) | `kitchen` with the back door to the yard (DS-35) | kitchen, or a `workshop` back room for trades that make goods (baker, smith, cooper: 1 in 3; the job block there, not in the shop) | kitchen opening onto the yard as a work court | `office` (back office) or a second stock room; the dwelling's kitchen goes upstairs |
| Upper storeys | bedrooms off the landing (DS-12), the dwelling's kitchen if it is not below | as left | as left; a flat roof terrace on the wing when the style has flat roofs | one flat (or the upper flat's bedrooms) reached from the main stair |

Rules of thumb: the wing never holds the first ground-floor bedroom (a bedroom on the ground floor of a shop is reached through the shop, which the flat's people would not do). A wing 5-6 wide and up to 8 deep is one room per storey (store room, or kitchen); 9+ deep is split in depth into store room plus kitchen/workroom, each at least 3x3 and aspect at most 1.5 (DS-11). If the wing has its own stair it is the flat's stair, entered from the yard door, which gives the "separate house door" of DS-35 for free.

**DS-25 with a yard: yes.** DS-25 asks that every habitable room has a window; it does not ask that the window face a street. A window onto the building's own yard is a real window (daylight, view, and in game the sky light of an open yard). Conditions I would hold:
- At least 2 open-air cells clear in front of the window, open to the sky (the 2-block side strip meets this; 4 behind is comfortable). A window into a wall 1 block away does not count.
- Put the habitable rooms (kitchen, bedrooms) on the 4-deep rear face or the side strips; the store room may be windowless (it is not habitable; the high-rise plan already exempts `storage`).
- Windows on the wing's side faces still align storey to storey and across the wing face (DS-25 bays); the wing is a facade of its own.
- DS-24 is unchanged: every wing room gets a light source; yard daylight does not stop night spawns.

**I2: windowless back-of-house is allowed in the podium; no light courts. Step the shaft back above the podium instead.**
- **Podium storeys (lobby, open floor, banquet):** the full 30-deep plate is fine. The GRAND and BANQUET templates in `docs/plans/highrises.md` already put `office` beside the core and `storage` and `kitchen` behind it, and the DS-25 test there exempts `storage`, `kitchen`, `plant`, `entry`, `corridor` and `stairwell`. Use the depth for those, plus a service room (loading, `storage`) with a back or side door to the rear yard, which is a fair reading of DS-35's second door. Hold two conditions: every windowless room has a light source (DS-24: a lantern per room, more in rooms over ~12 across), and no habitable use (`office`, `flat`, `bedroom`, `banquet`, `lobby`) is in the windowless band. The plan's "back segment deeper than 1.5 x width becomes `storage`" rule already does this.
- **Shaft storeys (typical units):** these must not be 30 deep. At 30 deep each band is 13 or more, which is the "known issue until RACETRACK" row of the high-rise unit table (an entry strip of 4+ and units 8 deep at the facade with a dark strip behind). Keep the shaft's interior depth at about 24 at most (bands of 12 or less) by setting the shaft back from the rear of the podium, with the podium roof behind as a terrace. That is how real podium towers do it, and it shows the base-shaft split from the cross street.

# Reasons

- Period practice: the burgage plot's back house held the store, the kitchen (fire away from stock) and the workshop of a trade that made what it sold. The family lived over the shop. The counter-front, store-behind order is already in our room table ("shop storage behind the counter").
- Today's generic ground-floor bedrooms behind a shop break the adjacency rules (bedrooms reached through the shop and through each other). A deeper wing would make that worse, so the wing needs uses before L1 makes it common (4 in 5 deep shops).
- Light courts in Minecraft are 3-5 block shafts. They are dark at the bottom, read as holes from the air (against METROFILL's fill goal), cut the plate's circulation, and need their own DS-25 alignment and roof logic. Back-of-house that needs no window is cheaper and is what real deep podiums contain.

# Alternative

- I1: make the wing a separate small dwelling (a flat for the shopkeeper's family, its own door on the yard), with the main block upstairs let out. That is believable in city tiers, but it multiplies kitchens (DS-12 leans away from a second room of another kind) and leaves the shop without a store room. I prefer the back-of-house version, with a flat in the wing only on its upper storeys in city and metropolis.
- I2: a light court for podiums 3+ storeys tall whose upper podium storeys carry offices. That only matters if podiums grow past the lobby and special floors. If they do, prefer a rear step-back over a court.

# Rule?

Propose two guideline rules:
- **Shop back of house**: a shop's ground floor behind the shop floor is a store room (door from behind the counter), then a kitchen or workroom with the back door. No bedrooms on a shop's ground floor; the dwelling is upstairs and in the wing's upper storeys. Code candidate: a `ShopType` programme by role and position (the wing's ground leaves), plus a test "no `bedroom` use on storey 0 of a shop". From: I1 and today's floor plans (seeds 1-4, every seed with rooms behind the shop).
- **Window clearance** (amends DS-25's "every habitable room has a window"): a window counts only with at least 2 open-air cells clear in front of it. Rooms without a counting window may only be non-habitable (`storage`, `plant`, `corridor`, `stairwell`, `entry`, `kitchen` in high-rises), and each still gets a light source (DS-24). Code candidate: extends the high-rise window-per-room test to every type, with the clearance taken from the scene (neighbouring lots), since the yard and the neighbour are known there. From: I1, I2.
