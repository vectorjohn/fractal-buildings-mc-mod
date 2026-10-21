# House program: one kitchen and one living room per house (coder)

Branch `claude/p5-house-program` (base `claude/run-phase5` at CIRC2 a7ad5a4 plus later merges). Follow-up to 5.11/CIRC2:
CIRC2's verifier saw 65-73 % of houses without a kitchen because each mass (main block, wings) was given its rooms alone.

## What changed

- `RoomProgram.refineBuilding(List<Slot>, Where, Draws)` (default no-op) and `RoomProgram.Slot` (mass, first storey of the band,
  `span` = storeys sharing the band, role, area, mutable `use`, `next()` = rooms joined by a door of the same plan).
- `InteriorPass.run` is now two-phase: every band is first drafted (plan, per-mass uses, `refine`), then `program(...)` calls the
  type's `refineBuilding` once for the whole building, then archways and hooks are built from the final uses (they depend on the
  uses: `openPlan`). Other types ignore the hook; only the house golden digests moved.
- `HouseRooms.refineBuilding`: a house with >= 2 habitable rooms (bedroom/living/kitchen, counting storeys) gets exactly one
  ground-floor kitchen, chosen by score (a repeated type or spare room, area >= 9, beside the living room or the entry room);
  then, when the ground floor has >= 2 such rooms, one living room (the entry's if it is one, else the room next to the
  kitchen). Extra kitchens/livings in plain rooms become bedrooms, except 15 % (`REPEAT_PERCENT`) repeats, and a second kitchen
  only with >= 5 ground rooms (`BIG_HOUSE_ROOMS`). A one-storey house still keeps a bedroom. Roles ENTRY/STAIR/THROUGH are never
  converted to bedrooms (the way into a wing stays a passage).
- Decisions: a one-room hut stays a single bedroom ("hall house", CIRC2 rule). A ground floor of one habitable room under
  bedrooms is a kitchen-hall (hearth, table, chest) that doubles as the living room; a ground floor of only entry and halls puts
  the kitchen in the entry room. Cross-mass adjacency is not used when scoring (only same-floor-plan doors).

## Numbers (1500 houses per style, same lots/seeds; `-Dfb.houseprogram=1500`, `HouseProgramTest.report`)

Before (CIRC2 state) -> after:

| style | no kitchen (2+ habitable rooms) | no kitchen (3+ rooms) | bedrooms/house | rooms > 2:1 | rooms behind >= 2 rooms | 2 kitchens | 2+ living on ground |
|---|---|---|---|---|---|---|---|
| plain | 57.3 -> 0.0 % | 41.5 -> 1.3 % | 1.53 -> 1.40 | 7.1 -> 7.1 % | 0.09 -> 0.09 % | 2.7 -> 0.1 % | 6.3 -> 4.5 % |
| medieval | 53.5 -> 0.0 % | 39.2 -> 1.4 % | 1.81 -> 1.67 | 5.8 -> 5.8 % | 0.31 -> 0.31 % | 4.0 -> 0.1 % | 8.1 -> 5.3 % |
| plains | 57.5 -> 0.0 % | 42.4 -> 1.4 % | 1.55 -> 1.41 | 7.7 -> 7.7 % | 0.11 -> 0.11 % | 2.8 -> 0.1 % | 6.3 -> 4.5 % |
| desert | 47.9 -> 0.0 % | 35.7 -> 2.6 % | 1.82 -> 1.68 | 3.1 -> 3.1 % | 0.26 -> 0.26 % | 5.7 -> 0.3 % | 9.3 -> 6.5 % |
| snowy | 50.2 -> 0.0 % | 36.5 -> 3.8 % | 1.76 -> 1.65 | 3.6 -> 3.6 % | 0.16 -> 0.16 % | 3.2 -> 0.0 % | 6.3 -> 3.5 % |

The "3+ rooms" residue counts stair and entry rooms: a hut with one bedroom above its entry and stair has 3 rooms but only one habitable room, so it stays a hall house.
Bedrooms fell about 0.14 per house: the kitchen and living room now take rooms that were bedrooms.
Houses with 3+ habitable rooms and >= 2 ground rooms and no living room: 0 %.

## Verification

`:planner-core:test :cli:test` pass (V1PropertyTest, determinism, CirculationTest, SampleSettlementTest, CityCommandTest unchanged).
Only `golden/v1/house.txt` regenerated (GEN_VERSION stays 2). New `HouseProgramTest` (kitchen on the ground floor, <= 2 kitchens, second kitchen
< 10 %, living room present); mutation: `refineBuilding` returning at the top -> `HouseProgramTest` fails ("has no kitchen").
`HouseTypeTest.groundFloorIsForLiving` accepts a kitchen-hall; `RoomShapeTest` bedroom floor 1.5 -> 1.3. Gallery
`house-medieval/plains/desert`: 0 violations. Plans for seed 7 and 9: `docs/review/phase5/circulation/house-medieval-plans.md`
("After house program"): seed 7 now has a kitchen between two living rooms in the front row.
