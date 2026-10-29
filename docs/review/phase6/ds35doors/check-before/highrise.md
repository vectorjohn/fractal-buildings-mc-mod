# Invariant report

Cases checked: 4000, failing: 1000

| invariant | violations |
|---|---|
| ir-valid | 0 |
| in-lot | 0 |
| in-world | 0 |
| no-floating | 0 |
| no-gap-below | 0 |
| shell-closed | 0 |
| roof-covers | 0 |
| roof-closed | 0 |
| room-graph | 0 |
| rooms-reachable | 0 |
| entrance-reachable | 0 |
| steps-reach-ground | 0 |
| stair-rise | 0 |
| window-size | 0 |
| furnished-rooms-walkable | 0 |
| door-rise | 0 |
| entrance-count | 1000 |

## Design warnings (not violations)

High-rise design checks (DS-22, DS-32): 4000 cases with a high-rise, 659 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

| check | cases failing | warnings |
|---|---|---|
| highrise-lobby | 0 | 0 |
| floor-repeat | 0 | 0 |
| hall-access | 0 | 0 |
| stairwell-enclosed | 0 | 0 |
| shaft-present | 0 | 0 |
| room-window | 0 | 0 |
| room-light | 0 | 0 |
| roof-reachable | 0 | 0 |
| door-rise | 0 | 0 |
| flight-landings | 659 | 659 |

- first flight-landings `seed 0 highrise 20x20 plain noise:7:64:6:32 north: o61: a straight run of 4 risers (highrise)`

First failing cases:

- `seed 0 highrise 20x20 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 0 highrise 28x28 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 0 highrise 36x36 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 0 highrise 44x44 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 1 highrise 20x20 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 1 highrise 28x28 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 1 highrise 36x36 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 1 highrise 44x44 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 2 highrise 20x20 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 2 highrise 28x28 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 2 highrise 36x36 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 2 highrise 44x44 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 3 highrise 20x20 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 3 highrise 28x28 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 3 highrise 36x36 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 3 highrise 44x44 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 4 highrise 20x20 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 1 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 4 highrise 28x28 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 4 highrise 36x36 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)
- `seed 4 highrise 44x44 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2); high-rise has 1 door face(s), widest door 2 (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| highrise | 4000 | 34 % | 35 % | 34 % | 17..46 % | 3018074 | 0 % | 100 % | 60 % | 60 % | 35-90 % (7.13) |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| plain | 4000 | 34 % | 35 % | 34 % | 17..46 % | 3018074 | 0 % | 100 % | 60 % | 60 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| highrise | 4000 | 0 | 0 | 1000 | 0 | 250 | 268 | 482 | 1250 | 750 | 2750 (69 %) | 45 % | 659 | 18 | 1524 (38 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| highrise | 4000 | 0 | 4000 | 0 | 0 | 1000 | 3000 | 2715 | 0 | 0 |
