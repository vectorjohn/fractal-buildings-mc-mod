# Invariant report

Cases checked: 16000, failing: 0

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
| entrance-count | 0 |

## Design warnings (not violations)

High-rise design checks (DS-22, DS-32): 0 cases with a high-rise, 988 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

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
| door-rise | 988 | 988 |
| flight-landings | 114 | 114 |

- first door-rise `seed 1 house 20x20 medieval hill:64:20:24 south: o1: rise 7 from the street on a lot beyond the planner's limit (a house door sits at most 5 above it)`
- first flight-landings `seed 4 house 31x17 medieval hill:64:20:24 south: o4: a straight run of 4 risers (house)`

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| house | 16000 | 21 % | 21 % | 21 % | 11..36 % | 235378 | 0 % | 100 % | 13 % | 13 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medieval | 8000 | 21 % | 23 % | 19 % | 11..36 % | 123624 | 1 % | 99 % | 13 % | 13 % |  |
| plain | 8000 | 21 % | 18 % | 24 % | 13..28 % | 111754 | 0 % | 100 % | 13 % | 13 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| house | 16000 | 0 | 829 | 12109 | 1390 | 137 | 212 | 381 | 648 | 294 | 1535 (10 %) | 7 % | 114 | 16 | 14758 (92 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| house | 7473 | 0 | 1538 | 3026 | 775 | 507 | 414 | 264 | 332 | 617 | 1627 (22 %) | 33 % | 0 | 19 | 6047 (81 %) |

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| house | 16000 | 0 | 8527 | 7473 | 0 | 0 | 12000 | 12268 | 0 | 0 |
