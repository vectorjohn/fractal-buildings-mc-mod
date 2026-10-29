# Invariant report

Cases checked: 5000, failing: 0

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

High-rise design checks (DS-22, DS-32): 0 cases with a high-rise, 284 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

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
| flight-landings | 284 | 284 |

- first flight-landings `seed 1 hall 30x30 plain noise:7:64:6:32 north: o5: a straight run of 4 risers (hall)`

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| hall | 5000 | 28 % | 25 % | 30 % | 18..37 % | 632732 | 0 % | 100 % | 22 % | 22 % | 25-40 % |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| plain | 5000 | 28 % | 25 % | 30 % | 18..37 % | 632732 | 0 % | 100 % | 22 % | 22 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| hall | 5000 | 0 | 13 | 1981 | 133 | 290 | 400 | 614 | 1217 | 352 | 2583 (52 %) | 0 % | 284 | 20 | 2417 (48 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| hall | 4958 | 0 | 474 | 2010 | 158 | 184 | 235 | 222 | 252 | 1423 | 2132 (43 %) | 1 % | 0 | 45 | 2826 (57 %) |

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| hall | 5000 | 0 | 42 | 4958 | 0 | 0 | 3750 | 1712 | 0 | 0 |
