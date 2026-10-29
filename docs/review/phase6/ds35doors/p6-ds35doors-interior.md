# DS35DOORS interior review (2026-10-28)

Mode: review. Round: first.
Counts: 1 violation, 2 should-fix, 6 suggestions, 2 proposed rules.

Looked at:
- Images: `docs/review/phase6/ds35doors/gallery/workshop-medieval-back-right-seed3-before-after.png`, `viewer/out/gallery/workshop-medieval-cutaway.png`, `viewer/out/gallery/highrise-cut-lobby-cutaway.png` (worktree p6-ds35doors). At gallery scale none of them shows a door or a hook, so all findings below come from plans and IR.
- Coder floor plans, before and after: workshop-4 and workshop-7, shop-2, temple-1, hall-1, highrise-3 (`docs/review/phase6/ds35doors/floorplans-*`).
- My floor plans at HEAD 0b08b5c5: workshop 20x16 medieval seeds 6 and 16; house 16x14 medieval s20; house 20x16 medieval s8; highrise 30x30 plain s3.
- IR from a scripted survey. After: 0b08b5c5. Before: base 77abb369, built from `git archive` in my scratchpad. Batches:
  - house, shop and workshop, seeds 0..29, lots 16x14, 20x16 and 24x20, medieval and plain;
  - temple 20x30 and hall 30x30, seeds 0..29, medieval and plain;
  - highrise 24x24 and 30x30, seeds 0..19, plain;
  - base: house and workshop, medieval, the same three lots.
- For each building the script recorded the room behind every exterior door, the hooks on that door's inside cell, its two side neighbours and the cell beyond, the rooms and hooks of each mass, and bed counts.
- Code read: `WorkshopType` ROOMS, `HouseRooms`, `InteriorPass.hookSites`, mod `HookPlacer`.
- Not seen: any in-game interior. No client run exists for this item.

## Violations
- **DS-24: the new lean-to store has no light.**
  - Where: 0 of 17 stores in the workshop survey have a light hook (20x16 and 24x20, both styles), e.g. workshop 24x20 plain s7 r2 and 20x16 medieval s6 r2.
  - Cause: `WorkshopType` asks for `chest, counter, chest, light` cut to `1 + area/6` pieces, so the light only arrives at 18+ cells. Stores are 9-16 cells.
  - Why it breaks the rule: the room is new in this item and its programme was written here. DS-24 (accepted) wants a light source in every room. The brief and the WORKSHOP polish line both list "a light".
  - Fix direction: put the light first, or always add it, ideally as a hung lantern so it costs no floor cell.
  - Also: the wing kitchen has no light either (`hearth` becomes an unlit furnace). That is true of every `HouseRooms` kitchen and bedroom and predates this item; see "For John".

## Should fix
- **The back door costs the dwelling its only bed. Workshops now have almost no beds.**
  - Where:
    - Workshop medieval, 3 lots x 30 seeds: a bed in 69 of 90 before, 9 of 90 after. Plain matches: 13 of 180 workshops over both styles have a bed.
    - The coder's own showcase plans: workshop-4 r1 lost bed h6; workshop-7 lost both beds. The wing is one `kitchen` (hearth, chest, table), the lean-to a store, and the building has nowhere to sleep.
    - The same in houses: 20x16 medieval s3 was entry, hall with hearth, and a 5x6 bedroom in the wing. Now the wing is a kitchen and the house has no bed. That is about 1 in 30 houses, also on 24x20.
  - Cause: on a one-room wing, `backUse` and `refineBuilding` turn the bedroom into the kitchen and nothing puts the bed back. The amendment asks for "a kitchen and a bedroom". A craftsman's house with no bed is the first thing a player notices; it also starves villages of villager beds.
  - Fix direction:
    - Wing of 2+ rooms or 2 storeys: the kitchen takes the back door and the bedroom goes in the other room or upstairs. The upper `storage` rooms over wing stairs (e.g. 20x16 s16 r2, 8x3, empty) should be bedrooms where a bed fits.
    - One-room wing of 12+ cells: a kitchen-hall with a bed in the far corner (the cottage "hall", the studio `needsBed` path), not a bedroom. The door still opens into a kitchen.
    - Houses: when the back door lands in the only bedroom and another ground room already has a hearth (hall or living), move the door to that room or the entry. Don't swap the uses.
    - Answers the coder's question "loses its bedroom, acceptable?": no.
- **A new door empties the small room behind it.**
  - Where:
    - 3x3 house kitchens with the inner door and the back door on one axis: before (hearth, chest), after (nothing).
      - 20x16 medieval s8, s24 and s26 (s8: wing kitchen r1, doors o0 and o2 both at x=12, windows at both side cells);
      - 16x14 medieval s8 and s24;
      - 24x20 medieval s8, s24 and s26.
      - About 2-3 of 30 houses per lot.
    - House 16x14 medieval s20: living r0 (6x6, with stair) now has a front, a back and a kitchen door and lost its hearth: empty.
    - Workshop wing `kitchen(8x3)[]`, a stair passage with no stove, 4-6 of 30 per lot (e.g. 20x16 s16 r1). It was an unfurnished `stair` room before. Now it is labelled the kitchen, and it is the building's only kitchen.
    - Temple 3x3 transept chapels with the side door lose their only bench: 4 of 30 medieval, 3 of 30 plain (e.g. temple-1 r2: bench h6 gone; nave door and outside door face each other).
  - Why it matters: a kitchen with no stove or a chapel with no seat reads as a corridor with a label. DS-13 says "rooms look lived-in".
  - Fix direction:
    - The room behind a secondary door must keep its required piece (kitchen hearth, chapel bench, living hearth).
    - Try the door on another wall cell of the same face: a corner cell, not the one in line with the inner door. Or try another room on that face.
    - Where neither works, a 3x3 room is too small to be both a passage and a kitchen. Then put the door in a bigger room or the hall.
    - Which wall cell takes the door is the architect's call (boundary). The rule that the room must still hold its stove is mine.

## Suggestions
1. **Store contents.**
   - The lean-tos hold 1-2 pieces, e.g. 20x16 s6, s8 and s11: one chest in 12 cells.
   - Wanted set, in order (answers the coder's question): light, chest, counter (barrel), then the sink used as a tub (a cauldron) once the catalogue has a `sink` hook, then a second chest or barrels.
   - At least 2 pieces from 9 cells.
2. **Back doors into living rooms.**
   - About 4-5 of 30 houses per lot, e.g. 20x16 medieval s2: wing living 6x6 while the 3x6 kitchen sits next to it.
   - DS-35 lists hall, kitchen, lobby and shop floor. Most of these cases are the front room acting as the house's hall (front and back door in one room, the cross-passage plan). That is fine and period-correct.
   - When a separate kitchen shares the back face, prefer it. `best()` already gives +30. Check why it loses here.
3. **The shop's "house door" enters the shop floor in most small shops.**
   - Shop floor 25-29 of 30 on 16x14; 7-11 of 30 on 20x16 and 24x20 (e.g. shop-2).
   - For medieval shop-houses, passing through the shop is period-correct. For plain and later styles, the family's door should reach the stair without crossing the shop (DS-35 "a separate house door").
   - Partitioning a stair passage is floor layout, so this is the architect's call (boundary). The room contents are fine: the inside cell is clear, and the stair foot is 2 cells away.
4. **High-rise second door.** Interior verdict: good.
   - 24x24: 20 of 20 open into the lobby's back row, beside the office door, with no furniture near.
   - 30x30: 10 of 20 open into the `open` annex, which joins the lobby through a 3-wide arch (s3: r1 5x9, door o279, nearest table 3 cells away). It reads as a side entrance to the lobby.
   - Prefer the lobby's own wall when the annex is under 6 wide: a narrow annex makes a tenant's office double as a passage.
   - 2-wide main doors: inside cells clear in every plan, nearest bench or table 2+ cells away.
5. **Rear passage use (the coder's question).**
   - `hall` hooks (table, hearth) suit the big rear halls of 20x16 and 24x20 shops.
   - A 3x3 or 3x4 back passage should take `entry` hooks (chest, light) instead: hall 3x3 and 3x4 cases appear in hall-medieval and plain.
6. **The "job blocks" in the wing kitchen are only a name today.**
   - `hearth` is placed as a furnace and `table` as a crafting table; there is no smoker or cauldron (DS-14, enforcement planned).
   - Not this item's to fix, but the ROADMAP line and the "Done" note on the WORKSHOP line should not claim job blocks.
   - Also good, worth recording: temple side doors into a 3x7 or 3x8 sanctuary are the priest's door, period-correct, and the altar is never beside them.

Checked and clean:
- No secondary door opens into a bedroom on any storey: 0 in the whole survey, all types.
- No hook sits on any secondary door's inside cell, its side neighbours or the cell straight beyond: 0 of all secondary doors.
- Hall side doors (hall-1 o6, centre of the long wall) open on a clear 2-wide lane.

## Proposed rules
- **Every dwelling sleeps someone.**
  - Rule: a house, a workshop's living wing and a shop's dwelling each have at least one bed. Where the programme has no bedroom, a kitchen-hall takes a bed in its far corner.
  - Why: a home with no bed reads as wrong, and villages need beds.
  - Scope: house, workshop, shop. Enforcement: code (count in `check`).
  - From: the bed should-fix above. Also, before this item, 5-7 of 30 medieval and 10 of 30 plain houses already had no bed (base 77abb369 survey), and John's playtest note "1 bed in each tiny house" (21.29.47) sits nearby.
- **A door never empties a room.**
  - Rule: adding a door, arch or stair keeps the room's required pieces (kitchen stove, bedroom bed, chapel seat, living hearth). Otherwise the opening moves, or the use moves to a room that fits.
  - Why: rooms that are a label with no furniture.
  - Scope: all types. Enforcement: code (required-piece check after `HookLayout`).
  - From: the empty-room should-fix above. Related: the wall-walk door that stripped a bed in the p6-roomwalk interior review.

## For John
- DS-24 is accepted, but the house programme gives no light hook to kitchens, living rooms or bedrooms. Furnaces don't glow, so most house rooms only get light through their doors.
  - It predates this item; the p6-roomwalk interior review counted the unlit rooms too.
  - Should it become its own item: a hung lantern in every room, now, rather than waiting for 7.5?

Verdict: should-fix round needed

# r2: re-review after round 2 (2026-10-28)

Mode: review. Round: re-review after round 2, limited to the DS-24 violation, the two should-fix items, and anything the fix broke.
Counts: 1 violation (new, introduced by the fix), 0 should-fix, 3 suggestions moved to polish, 0 new proposed rules.

Looked at:
- Branch HEAD b2ba6b87, CLI rebuilt there. Same survey as round 1: workshop and house, seeds 0..29, lots 16x14, 20x16 and 24x20, medieval and plain; temple 20x30, both styles. Compared with my round-1 run and the base 77abb369.
- Floor plans: workshop 20x16 medieval seed 25 (run by me); `docs/review/phase6/ds35doors/floorplans-after/workshop-7.txt` (r2 version).
- No new images: these items show only in plans and IR.

## Checked items
- **DS-24 lean-to light: fixed.**
  - 17 of 17 lean-to stores in the survey lead with a `ceiling_light`, e.g. workshop-7 r2 h10.
  - Stores now hold light, chest, counter and chest.
- **Should-fix 1, the only bed lost to a back door: fixed.**
  - Workshops with a bed, medieval 16x14 / 20x16 / 24x20: 23 / 23 / 21 of 30. Base: 24 / 24 / 21. Round 1: 0 / 4 / 5.
  - Medieval houses with a bed: 25 / 24 / 25 of 30, the same as base.
  - House 20x16 seed 3 keeps its bedroom.
  - One-room wings are now a bedroom, or a kitchen-hall with a bed, e.g. 20x16 seed 25 r1 (4x3: hearth, bed, lane clear), workshop-7 r1 (4x5: hearth, bed, chest, table).
  - 0 secondary doors into a bedroom. 0 hooks on or beside any secondary door's inside cell.
  - Kitchen-hall with a bed in the far corner for one-room wings: accepted (the coder's question).
- **Should-fix 2, rooms emptied by a door: mostly fixed.**
  - Fixed:
    - The 3x3 kitchens of houses 20x16 seeds 8, 24 and 26 and 16x14 seeds 8 and 24 are furnished again.
    - No 3x3 chapel takes a side door now: the temple side doors that open into a chapel all go into 3x5 chapels, and no chapel is empty.
  - Left:
    - House 16x14 medieval seed 20: the 6x6 living room with stair, front and back door is still empty (base had a hearth).
    - Workshop wing "kitchens" that are a stair passage with no stove: 4 / 4 / 6 of 30 per lot (e.g. 20x16 seed 16, 8x3). They were unfurnished `stair` rooms before this item, so this is a label, not a loss.
  - Both move to the polish backlog; see Suggestions.

## Violations (r2)
- **DS-35 amendment: no lean-to has a yard door any more, and many living wings have no door of their own.**
  - The fix broke it: `ExtraDoors.roomy` gives no door to a mass under 20 interior cells, and `EntranceCount` now only demands a door on a wing or lean-to of 20+ cells.
  - Lean-tos with an exterior door: 17 of 17 in round 1, **0 of 17** now (20x16 and 24x20, both styles), e.g. workshop-7 r2 now connects only to the hall.
  - Wings with their own door: 90 of 90 in round 1, **54 of 90** now (16x14: 10 of 30; 20x16: 20 of 30; 24x20: 24 of 30). The other 36 get a person door on the hall instead.
  - Why it breaks the rule: the amendment (approved by John 2026-10-28) gives the lean-to its own yard door. That door was John's playtest ask ("back door to the lean-to") and the core of the WORKSHOP polish line. The 20-cell limit was meant to protect a stove and a bed, and a store has neither.
  - Fix direction:
    - Lean-to: exempt stores from `roomy`. A 3x3 or 3x4 store takes a door on a short wall away from the hall door, with the light hung and chest, counter and chest on the other walls (DS-48 lane). Restore the lean-to clause in `entrance-count` for every size.
    - Living wing too small for a kitchen-hall (a lone bedroom of 9-16 cells): a door from the bedroom is ruled out by DS-35, so a hall person door is the right compromise. But that departs from the amendment's text, so it is John's call (below).

## Suggestions (r2, polish backlog)
- House 16x14 medieval seed 20: the living room is empty with front, back and kitchen doors. Retry the back door on another cell or room after the hook layout (the coder's deferred door-position retry).
- Workshop stair-passage wings labelled `kitchen` with no stove (4-6 of 30 per lot): label them `stair`, or fit the hearth in the hall-side corner. Once a kitchen-hall exists, don't call a room with no stove a kitchen.
- Houses with no bedroom at all (about 12 %, existing before this item): keep my round-1 proposed rule "every dwelling sleeps someone" for a later item. Adding a bed must respect the DS-48 lane, so it needs the smaller-piece step (single bed in a corner, table dropped), not a forced bed.

## For John
- The DS-35 amendment says the living wing's door comes "from its kitchen".
  - When the wing is only big enough for one bedroom (under about 20 cells), the generator now puts the person door on the hall instead. A bedroom must not take an outside door.
  - That is 36 of 90 workshops in my sample.
  - Recommendation: amend the rule to "a living wing too small for a kitchen gets no door; the hall gets a person door instead".
  - Your call, since it changes the approved text. Not the lean-to: its door must come back.

Verdict: should-fix round needed

# r3: re-review after round 3 (2026-10-28)

Mode: review. Round: re-review after round 3, limited to the r2 violation (lean-to and wing doors) plus anything round 3 broke in my earlier items.
Counts: 0 violations, 0 should-fix, 2 suggestions, 0 new proposed rules.

Looked at:
- Branch HEAD ee3b610a, CLI rebuilt there.
- Survey: workshop and house, seeds 0..29, lots 16x14, 20x16 and 24x20, medieval and plain, flat. Workshop 20x16 and 24x20 medieval also on `plane:64:1:0:4`.
- Script as in rounds 1-2: the room behind every exterior door; hooks on the door's inside cell, its side cells and the cell beyond; wing and lean-to rooms and hooks; beds.
- Floor plan: workshop 24x20 medieval seed 7, storey 0 (run by me).

## The r2 violation: fixed
- **Lean-to yard doors are back.**
  - Flat: 17 of 17 lean-tos have their own outside door (20x16: 8 of 8; 24x20: 9 of 9; both styles). r2 had 0 of 17.
  - Example: workshop 24x20 seed 7 r2 (4x4 store), door from the hall at o1 and yard door at o4 on the south face. Inside: hung light, chest and counter, lane clear.
  - On the 1-in-4 slope: 13 of 17. The other four are on ground where no railed flight of up to 3 risers fits (the per-lean-to `blocked` exemption). DS-4 and DS-45 come first there; see Suggestions.
- **Living wings:** the door counts are as in r2 (flat: 54 of 90 wings with their own door). The wings without one are the lone-bedroom wings, whose door is on the hall. That is John's REVIEW question, not a violation I hold.

## My earlier items: nothing broken
- **Beds.**
  - Workshops with a bed (16x14 / 20x16 / 24x20, medieval): 23 / 23 / 21 of 30, the same as r2 and the base. 21-23 on the slope.
  - Medieval houses with a bed: 25 / 24 / 25 of 30, as base.
  - 0 secondary doors into a bedroom. The new with/without-doors bed test guards this.
- **DS-24.** Every store leads with `ceiling_light`, on flat and on the slope.
- **Doors clear (DS-48).** No standing piece is on or beside any secondary door's inside cell, nor on the cell beyond it.
  - The only hooks there are 9 lean-to `ceiling_light`s hung over the yard door's inside cell or beside it, e.g. 20x16 seed 6 o3 and 24x20 seed 7 o4.
  - A lantern hung at y2 or above does not block (DS-48), and lean-tos are 5+ high. Fine.

## Coder's question
- **Is one chest acceptable in a lean-to that loses its second chest to the yard door?** Yes.
  - Stores in the sample:
    - 3x3 to 4x3: light and chest.
    - 4x4: light, chest and counter.
    - 3x5: light, chest, counter and chest.
  - A small woodshed with one chest and a barrel counter reads right. A lit, walkable store with its door is worth more than the second chest.
  - What still makes it read as a store or wash-house is the tub (a cauldron) and stacked barrels, when the catalogue has them (suggestion 1).

## Suggestions (polish backlog)
1. **Store contents.** Add the wash-tub cauldron and a barrel stack when 7.5 pieces exist. Barrels can stand in a corner beside the chest without touching the lane. This carries over from round 1, suggestion 1.
2. **Lean-tos on slopes with no yard door** (4 of 17 on `plane:64:1:0:4`). Once the survey can't place a plain flight, a door one block above the ground with a single step is still better than none. That is a DS-45 and architect question, not an interior one.

Earlier polish lines still stand (house 16x14 seed 20 empty living room; stair-passage wing "kitchens"; houses with no bedroom).

Verdict: meets the standard
