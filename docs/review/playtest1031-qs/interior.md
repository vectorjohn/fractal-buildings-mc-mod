# playtest1031-shopbeds interior answer (2026-10-31)

## Verdict block
Answer: a two-storey village shop's flat sleeps one household: 2-3 beds (one double where a 3x4 room fits, plus 1-2 singles), never more than 4 in any village shop. The rest of the floor is a kitchen-living room by the stair and a stock room, and no bedroom is reached through another bedroom.
What's built is a programme gap, not a code bug. `ShopType.PROGRAM` sends every room except the shop floor to `RoomProgram.DEFAULT` "room", which gets bed, table, chest and workstation. So every room upstairs is a bedroom, and no shop has a kitchen or living room. 16 of 16 CLI shops show it: 4-7 beds on two-storey 20x14 lots, 6-15 on 3-4 storeys.
Priority: polish, not a P6 blocker. Nothing is unwalkable or dark, and John liked the look. It is small (S) and belongs with DS-67 (shop back of house, proposed): do both as one shop programme item in P7.
Merge: n/a
Evidence: playtest shot 2026-10-31_02.42.59.jpg; CLI floorplans of 16 shops (plains, seeds 1-8, lots 12x16 and 20x14, at 0ae0888d); ShopType.java, RoomProgram.java, HouseRooms.java
For John: accept proposed rule "A shop sleeps one household" (below), or keep the many-bed flat as a deliberate variant?

Mode: question. Round: first.
Looked at: `docs/review/phase6/playtest-2026-10-31/2026-10-31_02.42.59.jpg` (opened) and its REVIEW.md entry. CLI `floorplan --type shop --style plains` for seeds 1-8 on lots 12x16 and 20x14 (16 plans, written to my scratchpad). I read in full seed 5 20x14 (4 storeys), seed 7 20x14 storey 1 (2 storeys) and seed 3 12x16 storey 1 (2 storeys). Code: `gen/v1/types/shop/ShopType.java` (PROGRAM), `interior/RoomProgram.java` (DEFAULT), `types/house/HouseRooms.java` (refine). STANDARDS: DS-11, DS-12, DS-35, DS-60, DS-64, DS-67. Earlier answers: `metrofill-plan-interior.md` (I1), `shoplow-plan-interior.md`, ROADMAP 7.22 INN. I could not rebuild John's own building: the village is outside the committed terrain dump (see the triage reply).

## Question
How many beds belong in a two-storey village shop's upstairs flat (DS-60, "every dwelling sleeps someone": the shopkeeper's household), and what should the rest of that floor be? Is what's built a bug, a programme rule to change, or fine?

## What's built
- `ShopType.PROGRAM.use` gives the ENTRY room `shop`. Every other room falls to `RoomProgram.DEFAULT`, which calls it `room`. DEFAULT hooks for `room` are bed, table, chest and workstation, up to 1 + area/10 of them. A shop has no `refine` step, so nothing like the house's "kitchen, then living room, then bedrooms" pass (`HouseRooms.refine`, DS-12) ever runs for it.
- Result over 16 shops: 0 kitchens and 0 living rooms. Every upstairs room is a bedroom. In the two storeys of John's case:
  - 12x16 lots (seeds 3, 4, 7, 8): 2 beds. The count is right, but the 48-cell landing round the stair holds only a light. It is the obvious living-kitchen and it is empty (seed 3, storey 1, r4).
  - 20x14 lots (seeds 3, 4, 7, 8): 4-7 beds. Seed 7, storey 1 has six bedrooms of 9-18 cells. r5 is reached only through r4 or r7, and r8 only through r10. r9 is a through-room with a bed: bedrooms reached through bedrooms.
- 3-4 storey shops copy the same plan onto each floor: 6-15 beds. Seed 6 20x14 has 15 beds. Seed 5 20x14 has 12 beds, and its three upper floors are identical, each with a bed and a workstation in r2/r7/r12.
- 5 of 16 shops have workstation hooks in bedrooms (3-7 each): job blocks used as decoration in sleeping rooms.
- The screenshot matches: a dim upstairs passage between doors, a bed, and more doors off it. This is the "odd layout, lots of beds" John saw.

## Recommendation
**The flat is one household** (shopkeeper, spouse, 1-3 children or an apprentice). Programme the shop's upper storeys like a small house, with a bed cap:

1. **First upper storey, in order:**
   - **Kitchen-living room** (a kitchen-hall: table and chairs, stove, hearth). It goes in the room the stair arrives in, or the largest room next to the landing. It is the flat's focal room. If DS-67 later puts the kitchen in the ground-floor back room, this room becomes the **living room** (parlour) instead.
   - **Main bedroom**: the largest remaining room that opens off the landing or the kitchen-living room. A double bed where 3x4 fits, nightstand, chest.
   - **Second bedroom** (children or apprentice), 3x3+, single bed and chest.
   - **Rooms left over:** a **stock room** (chests and barrels; a closet of 25 cells or less, as in DS-64) or a second living room or study. Never another bedroom beyond the cap.
2. **Bed cap:** 2 beds in a flat of 1-2 bedrooms; at most 3 on a two-storey village shop; at most 4 in any village shop; 5 in a town shop of 3+ storeys (agent's numbers). In a 3+ storey shop, storey 2 holds the rest of the bedrooms up to the cap. Storey 3 and above are an attic store (DS-64 limits do not apply to an attic store), or one lodger's or servant's room.
3. **Adjacency:** a bedroom opens off the landing, the hall or the living room, never only through another bedroom. A room that is a through-room on the plan gets the living or stock use. Today's plans need no new partitions for this: the uses just follow position, as `HouseRooms.refine` already does.
4. **No workstations** in a shop's flat. The trade's job block belongs to the shop floor or the back workroom (DS-67).

Seed 7 20x14 storey 1 shows how this works out. r9 (18 cells, through-room off the stair) becomes the kitchen-living room. r10 (18) becomes the main bedroom with a double bed. r7 (9) is the second bedroom. r4, r5 and r8 (9-12 cells) become a stock room, a store and a study. That is 2 beds instead of 6.

## Reasons
- **Period practice:** a medieval or early-modern shop house was one family over the shop, with stock in the back and the garret. Lodgers lived in towns, not village shops. Five or more beds over a village shop reads as an inn, and 7.22 INN gives inns their own building type and programme. Keeping shops at household size keeps the inn distinct, which is why John decided on a real inn type.
- **Standards:** DS-60 only asks for at least one bed per dwelling, so a cap does not conflict with it. DS-12 ("bedrooms first") is about houses, and even it puts one kitchen and one living room before extra bedrooms; the shop flat has neither. DS-67 already removes ground-floor bedrooms from shops; this rule is the upstairs half of the same programme.
- **Minecraft:** every bed is a villager home point. 15 beds in one shop draws villagers into odd clumps and inflates village beds without houses to match. A kitchen-living room also gives the flat a light source and a focal point (DS-24, DS-13) where today there is a bare landing.
- **It is cheap:** reuse the house programme's refine with a bed cap and a "not through a bedroom" pass. Geometry is unchanged, so reachability is unchanged.

## Alternative
Leave it, and treat the many-bed flat as rare charm ("an eccentric architect", in John's words). If so, cap it: no more than 1 in 10 shops gets a lodging-house floor, and only on 3+ storeys in town tiers, with a corridor so no bedroom is reached through another. I don't recommend this for villages: it competes with the inn and leaves every shop without a kitchen.

## Rule?
Yes. The theme recurs: generic `room` bedrooms behind the shop were raised in the METROFILL I1 answer (2026-10-26, which became DS-67), and the same DEFAULT-programme problem in workshop lean-tos was fixed in DS35DOORS. Proposed:

- **DS-n A shop sleeps one household.** Rule: a shop's upper storeys are one flat. A kitchen-living room comes first, by the stair, unless the ground floor has the kitchen, in which case a living room comes first. Then up to 2-3 bedrooms, and stock rooms after that. Beds: at most 3 in a two-storey shop, 4 in a village shop, 5 in a town shop of 3+ storeys. Every bedroom opens off a landing, hall or living room, never only through another bedroom. No workstations in the flat. Why: today every upstairs room is a bedroom (16 of 16 CLI shops, up to 15 beds; playtest 2026-10-31 02.42.59), so shops read as inns and have no kitchen. Scope: type `shop` (with DS-67 for the ground floor; the inn has its own programme in 7.22). Enforcement: code. A shop `refine` reusing `HouseRooms`. A test: one kitchen or living room per shop flat, bed count within the cap, no bedroom whose only doors lead to bedrooms.

For the coordinator: a polish line or P7 item "SHOPFLAT", paired with DS-67. It might be worth a QUESTIONS.md line under Rules if John wants to decide whether the eccentric version stays.
