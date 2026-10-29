# Invariant report

Cases checked: 16000, failing: 11000

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
| entrance-count | 11000 |

First failing cases:

- `seed 0 shop 9x14 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 medieval plane:64:1:0:24 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 medieval plane:64:1:0:24 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 medieval noise:7:64:1:48 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 medieval noise:7:64:1:48 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain plane:64:1:0:24 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain plane:64:1:0:24 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain noise:7:64:1:48 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 9x14 plain noise:7:64:1:48 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval plane:64:1:0:24 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval plane:64:1:0:24 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval noise:7:64:1:48 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 medieval noise:7:64:1:48 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 plain flat:64 north` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)
- `seed 0 shop 11x17 plain flat:64 south` entrance-count: 1 exterior door(s) on a building of 2+ storeys or 80+ cells (needs 2)

## Glazing (DS-33, report only)

Glass cells over exterior wall-plane cells, ground to top ceiling.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 16000 | 24 % | 23 % | 24 % | 17..32 % | 587352 | 0 % | 100 % | 41 % | 41 % | upper 25-40 %, shopfront 50-70 %, upper sill +1 |

| style | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medieval | 8000 | 22 % | 25 % | 20 % | 17..27 % | 326040 | 0 % | 100 % | 22 % | 22 % |  |
| plain | 8000 | 26 % | 20 % | 30 % | 18..32 % | 261312 | 0 % | 100 % | 64 % | 64 % |  |

## Door rise (DS-45, report only)

Entrance doors; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 16000 | 0 | 0 | 16000 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 | 16000 (100 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Entrances (DS-35, invariant entrance-count)

Exterior ground-floor doors per building; the last column counts buildings breaking the rule (2+ doors from 2 storeys or 80 cells, none into a bedroom, high-rises on two faces with a 2-wide main door, a workshop wing and lean-to each with a door).

| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the count rules are not enforced (a slope, or ground that allows no back door) | with a bed | lean-to stores | stores with a light |
|---|---|---|---|---|---|---|---|---|---|---|
| shop | 16000 | 0 | 16000 | 0 | 0 | 11000 | 5000 | 16000 | 0 | 0 |
