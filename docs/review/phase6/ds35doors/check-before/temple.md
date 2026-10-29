# Invariant report

Cases checked: 16000, failing: 4000

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
| entrance-count | 4000 |

## Design warnings (not violations)

High-rise design checks (DS-22, DS-32): 0 cases with a high-rise, 1344 cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).

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
| flight-landings | 1344 | 1344 |

- first flight-landings `seed 0 temple 19x31 medieval noise:7:64:6:32 north: o2: a straight run of 4 risers (temple)`

First failing cases:

- `seed 0 temple 15x25 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 15x25 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 15x25 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 15x25 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 19x31 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 19x31 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 19x31 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 19x31 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 25x38 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 25x38 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 25x38 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 25x38 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 31x45 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 31x45 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 31x45 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 temple 31x45 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 1 temple 15x25 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 1 temple 15x25 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 1 temple 15x25 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 1 temple 15x25 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| temple | 16000 | 16 % | 19 % | 15 % | 8..29 % | 841384 | 0 % | 100 % | 13 % | 13 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medieval | 8000 | 17 % | 23 % | 15 % | 9..29 % | 421528 | 0 % | 100 % | 16 % | 16 % |  |
| plain | 8000 | 15 % | 17 % | 15 % | 8..24 % | 419856 | 0 % | 100 % | 10 % | 10 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| temple | 16000 | 0 | 960 | 5038 | 1878 | 2788 | 1092 | 228 | 2016 | 2000 | 5336 (33 %) | 0 % | 1344 | 19 | 10664 (67 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| temple | 16000 | 0 | 16000 | 0 | 0 | 4000 | 12000 | 0 | 0 | 0 |
