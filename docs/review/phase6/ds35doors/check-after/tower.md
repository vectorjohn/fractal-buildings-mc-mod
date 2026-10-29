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

High-rise design checks (DS-22, DS-32): 0 cases with a high-rise, 1036 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

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
| flight-landings | 1036 | 1036 |

- first flight-landings `seed 0 tower 9x9 plain hill:64:20:24 south: o3: a straight run of 4 risers (tower)`

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| tower | 16000 | 21 % | 19 % | 21 % | 5..30 % | 767293 | 0 % | 100 % | 4 % | 4 % | - |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medieval | 8000 | 20 % | 20 % | 20 % | 5..29 % | 381957 | 0 % | 100 % | 5 % | 5 % |  |
| plain | 8000 | 21 % | 18 % | 22 % | 5..30 % | 385336 | 0 % | 100 % | 3 % | 3 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| stilted-tower | 3072 | 0 | 0 | 0 | 0 | 0 | 184 | 738 | 1624 | 526 | 3072 (100 %) | 0 % | 552 | 17 | 0 (0 %) |
| tower | 12928 | 0 | 622 | 4931 | 2722 | 2036 | 775 | 214 | 264 | 1364 | 2617 (20 %) | 0 % | 484 | 14 | 10311 (80 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
