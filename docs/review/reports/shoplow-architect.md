# SHOPLOW architect answer (2026-10-26)

Mode: question. Round: first.
Looked at: docs/review/phase5/shopland/shop-slope-front-left.png (seeds 0-15), docs/review/phase5/stairground/shop-slope-after.jpg, shop-stoop-after.jpg, docs/review/phase6/lotrail/shop-slope-deep-lotrail.png (seeds 0-15), docs/review/phase6/flythrough/987654321-city-3-street.jpg (no shops in view). Code: `InstancePassV1.placeChecked` (floor level), `ShopType` (SHOP_GRADE, market-hall legs), `SpecPolicyV1`, logs for SHOPLAND, STAIRGROUND and MINLOT. Measured (CLI rebuilt from this worktree, scratch output only): shop `batch` seeds 0..39 on 9x14 and 11x17 lots, flat and 1:6 to 1:12 slopes, fronts north and south (480 shops); `city --districts --scenes all` for river-town (seed 12345, 26 shops, 123 houses) and fly-hill-town (seed 1, 8 shops, 72 houses). "Rise" below = the floor you stand on minus the street surface in front of the lot (`frontY`): rise 1 = one step up.

## Question

John: why do shops sit 5 blocks above the street, and can they sit lower, nearer the street, or have a walkway along them? (1) the cause; (2) what real streets on slopes do, and which to use per tier and style; (3) numbers: the most a shop door may rise, and when to change strategy; (4) what applies to houses too.

## (1) Why shops end up that high

There are two causes, and both are rules in the code, not bugs.

- **The floor goes on the highest ground under the footprint.** `InstancePassV1.placeChecked` sets the slab at the *maximum* `floorY` under the footprint and fills everything lower with foundation. The building never cuts into the slope. When a lot rises away from its street (the usual case on the uphill side of a street), the shop floor matches the back corner: rise = lot relief + 1. `SHOP_GRADE` (relief x 12 <= w + d + 12) still lets relief 3 through on a 9x18 lot and 4 on a 16x29 lot, so 4-5 is what you get. River-town: **7 of 24 enclosed shops have rise 4-5**, and every one of them is a lot rising 3-4 from a street that sits level with the lot's front row (e.g. `dm7_m22.l6` 16x29: ground 67..71, street 67, slab 71, rise 5). Grading is not the cause: the street sits 0-2 *above* the front row in every case.
- **Market halls stand on 3-5 block legs.** That is the "7-wide shops 5 above the street" in the STAIRGROUND note. The market-hall variant (about 1 shop in 5: 8 of 40 seeds on each test lot) puts the shop room on storey 1, above an open floor on posts 3-4 high (cap `(width+1)/2`, max 5). Rise is **5 on flat ground** for a 7-wide building, and 6-7 on a 1:9 to 1:6 slope. In the settlements: 2 of 26 shops in river-town and 2 of 8 in hill-town (rise 5-6). This variant shows in shop-slope-front-left and shop-slope-deep-lotrail seeds 5 and 14: a long bare flight up to a glazed front.

Together: **11 of 34 settlement shops (32 %) have rise 4 or more.** Enclosed shops on flat or gently sloping lots sit at rise 1-2 (the gallery batches: 0-2 on every slope up to 1:6).

## (2) What real streets do, and what we should use

| Strategy | Precedent | When it applies |
|---|---|---|
| **Cut in: ground floor at street level, back dug into the hill** | Every hill town: Bath, Edinburgh's Old Town, Italian hill towns, Lisbon. Earth stands against the back wall; on a steep lot the upper floor has a back door onto the garden ("bank houses") | Lot rises away from the street. **The default.** |
| Stepped row | Gold Hill (Shaftesbury), Clovelly, Steep Hill (Lincoln), San Francisco: each house steps down with the street, one floor per building | Street runs up the slope. We already get this, since each building takes its own door level. |
| Lower ground floor | Edinburgh closes, Genoa, Bristol's Christmas Steps: shop at street level, store or rooms below opening to the low yard behind | Lot falls away from the street. Today's max rule already puts the floor at street level there; the plinth could become a storey later. |
| Split level | Suburban and Victorian hillside houses: front half at the street, back half half a storey (2 blocks) up | Houses; rarely shops (goods move on one level). Later. |
| **Raised shared pavement** | "High pavements" in English hill towns (Totnes Butterwalk, Nottingham's High Pavement, Bath): one railed walk along the uphill side of a street that crosses a slope, with steps at the ends; every shop opens off it | A row of 2+ shops on a cross-slope street whose door level stands 2-3 above the carriageway. **This is John's "walkway along them".** |
| Arcade or raised arcade | Bern's Lauben, Thun's Obere Hauptgasse (a walk over street-level cellar shops), Bologna, Chester Rows, the Southeast Asian shophouse "five-foot way" | Medieval town and city main streets, desert, tropical; the generator already has arcade shops |

Recommendation by tier and style:

- **Hamlets and villages:** cut in, nothing else. There are few shops, and a one-step stoop is enough.
- **Towns and cities, all pitched-roof styles (medieval, plains, taiga, snowy):** cut in by default. Then the raised shared pavement on cross-slope main streets (a district-scene feature, a later item). Then the arcade where the style already has it.
- **Desert and Mediterranean:** cut in plus stepped streets. A raised arcade or terrace reads naturally under flat roofs.
- **Jungle and tropical:** houses may stand on stilts (raised floors are the style). Shops stay at street level behind a five-foot-way arcade, rise 1-2.
- **Modern city and metropolis:** the lobby or shop at street level on the main street. The podium takes up the slope with a stepped plinth and a second entrance on the uphill street (DS-35).
- **Market halls, every style:** a 4-5 rise is the real form (Ledbury, Thaxted, Chipping Campden, Ross-on-Wye market houses), but **the trade is the open floor at street level**. The upper room is a hall or store reached by a stair *inside* the arcade or along a side wall, never a glazed shopfront at the top of a front flight. They stand free on squares, roughly 9-12 wide. A 7-wide hall squeezed into a terrace row reads as a house on stilts.

## (3) Numbers

Rise is counted from the street or pavement the door faces to the floor you stand on (rise 1 = one step = today's slab on level ground).

- **Shop door: target rise 1, at most 2.** Real shop thresholds are 0-2 steps, because goods and customers cross them. Rise 2 is two stair blocks inside the front margin, needs no landing, and DS-7 does not apply to it.
- **Rise 3-4 is allowed only off a shared raised pavement or arcade** that runs the whole frontage at door level, railed on the street side (DS-8). A single shop with its own terrace (SHOPLAND) is the fallback at rise 3-4, not the plan.
- **Rise 5 or more: never on a shopfront.** Change strategy (cut in) or the lot's type.
- **Strategy by uphill relief** r = the highest ground under the footprint minus the ground at the door (frontY where known):
  - r <= 1: floor at door ground (as today).
  - 2 <= r <= 4: cut in. Floor at door ground; excavate the footprint to floor level and retain the earth against the walls. 4 = `Grading.MAX_CUT_FILL`, the cut the roads already allow.
  - 5 <= r <= 8: cut in at the front; the earth stands against the back of storey 0 (retained by the building). Storey 1 gets a back door at its own level onto the high ground (a bank building; it is DS-35's second door for free).
  - r > 8: no shop on this lot. The lot planner picks another type, as MINLOT does.
- **Market-hall legs:** 3 high (4 only from 11 wide); only on lots 9+ wide that front a plaza or corner; ground relief under the hall <= 1. Posts stand on level paving.

## (4) Houses too

The same max-ground rule lifts houses. River-town: **23 of 123 houses have rise 4+ and 13 have rise 6-8** (e.g. `dm8_29.l10` in hill-town: ground 75..81, slab 81, street 76, rise 6). The tolerance for houses is higher; Georgian and brownstone stoops are 3-8 steps.

- Rise up to 3: plain flight.
- Rise 4-5: flight with a landing or porch at the door.
- Rise above 5, or r >= 5: cut in like shops, the door at street level, the storey above opening onto the back garden.

Same cut cap (4). The same rule should cover workshops (11 of 39 at rise 4+, up to 9), which need a cart door at yard level even more. Towers on legs (rise 9-20) are a separate question, not looked at here.

Separate finding, unverified (seen only in the numbers, no picture): in hill-town, 5 houses have `frontY` **10-15 above** their floor (`dm10_31.l0`, `dm9_28.l19`, `dm9_29.l10`). Either the street is on an embankment far above the lot, or `frontY` is sampled from the wrong place. Either way those doors probably can't be reached from their street (DS-6 street-to-door is untested). Worth a look by whoever takes this item.

## Rule?

Yes, a proposed rule:

**DS-x Doors sit near the street.** A shop's main door rises at most 2 above the street or pavement it faces, or 4 when it opens off a continuous raised pavement or arcade along the frontage. A house's main door rises at most 5, with a landing above 3. On a lot rising from the street, the building cuts into the slope instead of lifting to the high ground. Scope: shops, houses and workshops in all tiers. Enforcement: code candidate `door-rise`, measured as entrance-storey floor minus the street surface in front of the door (frontY, else the terrain at the door's outside cell). Source: John, REVIEW STAIRGROUND thread, 2026-10-26.

It amends DS-7: a shop that meets this needs no DS-7 terrace.

## Recommended spec (for the coder brief)

1. **Floor reference** (`InstancePassV1.placeChecked`). For types with a street front (shop, house, workshop; not walls, towers or halls for now), set the entrance-storey slab from the **ground at the main door**: `frontY` when the brief has one, else `floorY` at the door's outside cell. Do not use the maximum over the footprint. Keep today's "door would be buried" guard. Allowed: slab = door ground (rise 1), or +1 (rise 2) where the cut would otherwise exceed the cap.
2. **Cut.** Where the ground under the footprint is above the slab, write the excavation as air ground boxes (the role road cuts already use), down to the slab, inside the footprint plus a 1-cell areaway along the uphill walls where the lot has room. Retaining faces are foundation or plinth material. Cut depth is at most 4 at any column. No window or door below the outside ground on an uphill wall: drop the opening or turn it blind. Mod check: the building piece must clear the cut cells (they are terrain today).
3. **Bank buildings.** Where the ground behind exceeds the slab by >= the storey-0 height (5 for shops, 4 for houses), the cut stops at that back wall. Storey 1 gets a back door at its level onto the uphill ground, with a path (DS-6) and no flight.
4. **Falling lots** (ground below the slab): unchanged (plinth and foundation). A lower-ground storey with a back door is a later item.
5. **Lot planner:** a shop or workshop lot needs r <= 8 measured from the front (relief across the depth from the street row). Otherwise pick another type (the MINLOT path: `ClassTable.pick` with exclusions). Replace or keep `SHOP_GRADE` as the coder finds simplest, but measure from the front, not over w + d.
6. **Market halls:** legs 3 (4 at width >= 11); only on lots >= 9 wide fronting a plaza or with a corner; skip the variant on relief > 1. The upper room loses the shopfront: ordinary windows, no DS-7 terrace. Its stair runs inside the open floor or along a side or back wall. The upper room's use (hall, store, guild room) is the interior-designer's call.
7. **Later item, not this round:** a shared raised pavement along runs of 2+ adjacent shops on cross-slope streets (district scene; rise 2-4; railed; steps at each end and at most every 16 blocks).
8. **Tests:** a `door-rise` check (shops <= 2 off a plain street; houses <= 5) over `ShopStoopTest`'s terrains and `city-check` on the gate windows. Report the rise histogram before and after for shops, houses and workshops; my baseline is in this file. Pictures: shop-slope-deep and house-slope galleries before and after, plus one street frame of river-town `dm7_m22`.
