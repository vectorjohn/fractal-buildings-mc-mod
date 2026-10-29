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

High-rise design checks (DS-22, DS-32): 0 cases with a high-rise, 2260 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

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
| door-rise | 2260 | 2260 |
| flight-landings | 182 | 182 |

- first door-rise `seed 0 workshop 12x12 medieval hill:64:20:24 south: o0: rise 4 from the street on a lot beyond the planner's limit (a workshop door sits at most 2 above it)`
- first flight-landings `seed 3 workshop 16x14 medieval hill:64:20:24 south: o1: a straight run of 4 risers (workshop)`

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| workshop | 16000 | 14 % | 14 % | 16 % | 9..24 % | 198091 | 1 % | 99 % | 11 % | 11 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medieval | 8000 | 16 % | 16 % | 14 % | 9..24 % | 98774 | 2 % | 98 % | 13 % | 13 % |  |
| plain | 8000 | 13 % | 13 % | 18 % | 9..19 % | 99317 | 1 % | 99 % | 8 % | 8 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| workshop | 16000 | 0 | 912 | 12648 | 180 | 100 | 598 | 156 | 938 | 468 | 2160 (14 %) | 13 % | 182 | 14 | 14594 (91 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| workshop | 9795 | 0 | 1474 | 3988 | 759 | 960 | 996 | 251 | 446 | 921 | 2614 (27 %) | 31 % | 0 | 17 | 7587 (77 %) |

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| workshop | 16000 | 0 | 6245 | 9715 | 40 | 0 | 12000 | 8623 | 1616 | 1616 |
