# SHOPLOW baseline: door rise before the change

Step S0 of [the SHOPLOW plan](../../../plans/shoplow.md), measured 2026-10-26 on `claude/run-phase6` at 8d0dbaf8
(generator v1, before any SHOPLOW step). S9 reruns the same command and compares.

- Command: `scripts/door-rise.sh --out-dir build/door-rise` (all district scenes, i.e. `auto:999`, built **with
  interiors**, of the 9 gate windows in `fixtures/terrain/gate-sites.tsv`, river-town (seed 12345) and fly-hill-town
  (seed 1, from their `viewer/city-presets/`)). About 25 s on 20 cores; output deterministic (two runs byte-identical).
- Every door: [baseline-doors.tsv.gz](baseline-doors.tsv.gz) (one row per entrance door: site, district, building,
  lot, door, kind, style, front, rise, source, riseFrontY, run, landing, reached), for a per-door diff in S9.
- Tool, not production code: `scripts/DoorRise.java` measures what the plan specifies for the core `DoorRise` check
  (S0), so the coder's `## Door rise (DS-45)` table in `check`/`city-check` should reproduce these numbers; if it
  does not, the definitions below say where they differ. The core check, its tests and the CLI sections are still to
  be written (a coder item: S0 asks for `core/check/DoorRise.java`, `DoorRiseTest` and the two CLI commands).

Definitions (from the plan's section 6 and step S0):

- **Entrance door**: an exterior door of the lowest storey with rooms (`StairGround.doors`). Every building measured
  has exactly one, facing the lot front, except the 5 `block` buildings (37 front and 106 side doors).
- **Rise**: the door's standing level (`oy + door.box.y`) minus the street level at the first column outside the
  building's lots straight out from the door: the scene's road, paving or deck surface there (as
  `StairGround.surfaceLevels`), else terrain `floorY` (only 3 of 9,579 front doors had no street surface). Rise 1 =
  one step up. The second table uses the lot brief's `frontY` instead (the architect's measure); the two agree within
  a few doors per bucket.
- **Kind**: the spec type, except `market` = a shop whose lowest room storey is above storey 0 (the market hall on
  posts) and `stilted-<type>` for any other type like that.
- **Run**: the most blocks of descent between level treads on the walkable way from the door down to the nearest
  ground (StairGround's walker; turns do not end a run, as S0 words it). The way ends at the lot's own ground, which
  can be higher than the street, so run can be less than rise. **Landing**: a level step at door height before the
  way goes down (a stoop or porch).

# Headline

Front doors, all 11 sites (DS-45 limits: shops -2..2; houses and workshops at most 3, or 4-5 with a landing):

| kind | doors | within DS-45 | rise 4+ | rise 6+ | below the street (<= -3) | 4-5 without a landing |
|---|---|---|---|---|---|---|
| shop (enclosed and arcade) | 2253 | 1533 (68 %) | 289 (13 %) | 35 | 59 | - |
| market hall | 567 | 26 (5 %) | 522 (92 %) | 338 | 1 | - |
| shops together | 2820 | 1559 (55 %) | 811 (29 %) | 373 | 60 | - |
| house | 4600 | 3605 (78 %) | 857 (19 %) | 333 | 138 | 524 |
| workshop | 1186 | 945 (80 %) | 283 (24 %) | 102 | 34 | 105 |

- Shops: the misses are rise 3 (372, 17 %), 4-5 (254) and 6+ (35); 59 sit 3+ below the street in front.
- Market halls: rise 5-8 is the norm (434 of 567), up to 16; their flights run 4+ blocks without a tread in 554.
- Houses never get a landing today (0 %); every house door at rise 4-5 (524) misses the "landing above 3" half of
  DS-45, and 333 are at 6-22.
- River-town and fly-hill-town reproduce the architect's counts in
  [shoplow-architect.md](../../reports/shoplow-architect.md): river-town 7 of 24 shops at 4-5 plus both market halls
  at 6, houses 23 of 123 at 4+ (13 at 6-8); fly-hill-town's 5 houses with the street far above the door are its 5
  houses at <= -3. Workshops: 9 of 39 at 4+ in river-town (the architect counted 11).
- Lots left empty today (S3 changes these): house 384, workshop 229, highrise 167 lots `empty:steep`; no shop lot is
  empty (2820 build).

# Runs

| site | seed | tier | site arg | scenes (of planned) | doors | seconds |
|---|---|---|---|---|---|---|
| gate-temperate-city | 987654321 | city | 1280,-9600,city,477 | 26 of 26 | 336 | 2 |
| gate-desert-badlands | 987654321 | city | 6720,-6784,city,827 | 46 of 46 | 759 | 2 |
| gate-snow-city | 987654321 | city | 4288,448,city,478 | 21 of 21 | 298 | 1 |
| gate-jungle-savanna | 987654321 | city | -640,-3712,city,790 | 42 of 42 | 588 | 1 |
| gate-border-desert-jungle | 987654321 | city | 7872,-7744,city,577 | 38 of 38 | 609 | 1 |
| gate-coast-city | 12345 | city | -7360,-3712,city,740 | 44 of 44 | 707 | 1 |
| gate-metropolis-temperate | 987654321 | metropolis | 2432,-5696,metropolis,1430 | 171 of 171 | 3788 | 5 |
| gate-metropolis-mixed | 987654321 | metropolis | 2304,-1920,metropolis,1126 | 86 of 86 | 1552 | 2 |
| gate-joined-city | 987654321 | city | 8832,192,city,717 | 43 of 43 | 729 | 1 |
| river-town | 12345 | town | -900,-3500,town,300 | 12 of 12 | 195 | 0 |
| fly-hill-town | 1 | town | -1536,4800,town,418 | 11 of 11 | 124 | 0 |

# All sites: front doors, rise from the street surface in front (DS-45 measure)

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 2253 | 59 | 326 | 605 | 602 | 372 | 195 | 59 | 24 | 11 | 289 (13 %) | 53 % | 64 | 15 |
| market | 567 | 1 | 10 | 2 | 14 | 18 | 55 | 129 | 305 | 33 | 522 (92 %) | 73 % | 554 | 16 |
| house | 4600 | 138 | 794 | 1293 | 959 | 559 | 319 | 205 | 252 | 81 | 857 (19 %) | 0 % | 235 | 22 |
| workshop | 1186 | 34 | 197 | 289 | 233 | 150 | 106 | 75 | 83 | 19 | 283 (24 %) | 37 % | 115 | 14 |
| block | 37 | 0 | 1 | 3 | 1 | 3 | 9 | 9 | 7 | 4 | 29 (78 %) | 0 % | 28 | 11 |
| hall | 18 | 1 | 0 | 3 | 5 | 2 | 1 | 0 | 5 | 1 | 7 (39 %) | 0 % | 7 | 15 |
| highrise | 722 | 18 | 55 | 100 | 141 | 104 | 76 | 61 | 117 | 50 | 304 (42 %) | 27 % | 230 | 20 |
| stilted-tower | 27 | 1 | 0 | 0 | 1 | 0 | 2 | 6 | 11 | 6 | 25 (93 %) | 0 % | 26 | 19 |
| temple | 9 | 1 | 0 | 2 | 2 | 1 | 1 | 0 | 0 | 2 | 3 (33 %) | 0 % | 2 | 10 |
| tower | 160 | 9 | 22 | 34 | 28 | 25 | 15 | 9 | 13 | 5 | 42 (26 %) | 0 % | 13 | 12 |

# All sites: front doors, rise from the lot's frontY (the architect's measure)

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 2253 | 60 | 325 | 606 | 597 | 378 | 192 | 59 | 25 | 11 | 287 (13 %) | 53 % | 64 | 15 |
| market | 567 | 1 | 10 | 2 | 15 | 18 | 54 | 130 | 304 | 33 | 521 (92 %) | 73 % | 554 | 16 |
| house | 4600 | 145 | 811 | 1257 | 969 | 561 | 316 | 211 | 246 | 84 | 857 (19 %) | 0 % | 235 | 22 |
| workshop | 1186 | 35 | 199 | 278 | 244 | 152 | 97 | 82 | 79 | 20 | 278 (23 %) | 37 % | 115 | 15 |
| block | 37 | 0 | 0 | 5 | 0 | 0 | 8 | 16 | 8 | 0 | 32 (86 %) | 0 % | 28 | 8 |
| hall | 16 | 1 | 0 | 1 | 6 | 1 | 1 | 0 | 5 | 1 | 7 (44 %) | 0 % | 7 | 15 |
| highrise | 722 | 17 | 58 | 95 | 145 | 99 | 82 | 60 | 117 | 49 | 308 (43 %) | 27 % | 230 | 20 |
| stilted-tower | 27 | 1 | 0 | 0 | 1 | 0 | 2 | 6 | 10 | 7 | 25 (93 %) | 0 % | 26 | 20 |
| temple | 9 | 1 | 0 | 2 | 2 | 1 | 1 | 0 | 0 | 2 | 3 (33 %) | 0 % | 2 | 10 |
| tower | 160 | 9 | 22 | 33 | 31 | 25 | 13 | 8 | 14 | 5 | 40 (25 %) | 0 % | 13 | 12 |

# All sites: side and back entrance doors, rise from the ground straight out

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| block | 106 | 0 | 5 | 5 | 11 | 14 | 21 | 9 | 32 | 9 | 71 (67 %) | 0 % | 65 | 12 |

# All sites: buildings

| kind | buildings with a front door | highest front door rise 4+ | 6+ |
|---|---|---|---|
| shop | 2253 | 289 (13 %) | 35 |
| market | 567 | 522 (92 %) | 338 |
| house | 4600 | 857 (19 %) | 333 |
| workshop | 1186 | 283 (24 %) | 102 |
| block | 5 | 4 (80 %) | 3 |
| hall | 18 | 7 (39 %) | 6 |
| highrise | 722 | 304 (42 %) | 167 |
| stilted-tower | 27 | 25 (93 %) | 17 |
| temple | 9 | 3 (33 %) | 2 |
| tower | 160 | 42 (26 %) | 18 |

# All sites: lots by planned type and status (every planned district)

| type | status | lots |
|---|---|---|
| block | build | 5 |
| hall | build | 18 |
| highrise | build | 722 |
| highrise | empty:steep | 167 |
| house | build | 4600 |
| house | empty:steep | 384 |
| shop | build | 2820 |
| temple | build | 9 |
| tower | build | 187 |
| workshop | build | 1186 |
| workshop | empty:steep | 229 |

Doors whose walker found no way down to the ground (flight run not measured): 0

# gate-temperate-city: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 76 | 1 | 14 | 15 | 31 | 8 | 6 | 1 | 0 | 0 | 7 (9 %) | 55 % | 1 | 5 |
| market | 22 | 0 | 1 | 0 | 0 | 0 | 5 | 3 | 12 | 1 | 21 (95 %) | 82 % | 22 | 9 |
| house | 137 | 8 | 13 | 44 | 37 | 10 | 5 | 4 | 11 | 5 | 25 (18 %) | 0 % | 8 | 13 |
| workshop | 40 | 1 | 6 | 13 | 9 | 4 | 2 | 1 | 3 | 1 | 7 (18 %) | 40 % | 2 | 14 |
| block | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | 4 | 8 (100 %) | 0 % | 8 | 11 |
| hall | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 8 |
| highrise | 27 | 3 | 1 | 6 | 6 | 3 | 2 | 3 | 3 | 0 | 8 (30 %) | 33 % | 6 | 7 |
| stilted-tower | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 8 |
| temple | 1 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 3 |
| tower | 5 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 2 | 2 (40 %) | 0 % | 0 | 12 |

# gate-desert-badlands: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 239 | 4 | 29 | 63 | 66 | 40 | 22 | 6 | 5 | 4 | 37 (15 %) | 60 % | 5 | 11 |
| market | 55 | 1 | 1 | 0 | 3 | 2 | 6 | 7 | 34 | 1 | 48 (87 %) | 78 % | 52 | 9 |
| house | 363 | 11 | 64 | 81 | 78 | 47 | 30 | 13 | 25 | 14 | 82 (23 %) | 0 % | 19 | 19 |
| workshop | 62 | 3 | 10 | 16 | 10 | 7 | 7 | 3 | 4 | 2 | 16 (26 %) | 42 % | 6 | 10 |
| hall | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 2 |
| highrise | 28 | 2 | 5 | 4 | 3 | 3 | 2 | 4 | 4 | 1 | 11 (39 %) | 21 % | 12 | 12 |
| stilted-tower | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 8 |
| temple | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 |
| tower | 9 | 0 | 1 | 0 | 1 | 3 | 1 | 2 | 1 | 0 | 4 (44 %) | 0 % | 1 | 7 |

# gate-snow-city: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 45 | 1 | 3 | 19 | 14 | 3 | 4 | 1 | 0 | 0 | 5 (11 %) | 47 % | 3 | 5 |
| market | 11 | 0 | 0 | 0 | 0 | 0 | 2 | 4 | 3 | 2 | 11 (100 %) | 73 % | 11 | 9 |
| house | 202 | 6 | 34 | 49 | 42 | 30 | 12 | 13 | 16 | 0 | 41 (20 %) | 0 % | 14 | 8 |
| workshop | 22 | 0 | 6 | 4 | 4 | 3 | 0 | 2 | 3 | 0 | 5 (23 %) | 45 % | 3 | 7 |
| hall | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 |
| highrise | 6 | 0 | 1 | 2 | 0 | 0 | 2 | 0 | 0 | 1 | 3 (50 %) | 67 % | 2 | 18 |
| stilted-tower | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 0 | 0 | 2 (100 %) | 0 % | 2 | 5 |
| temple | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 (100 %) | 0 % | 1 | 10 |
| tower | 8 | 0 | 2 | 1 | 2 | 1 | 2 | 0 | 0 | 0 | 2 (25 %) | 0 % | 0 | 4 |

# gate-jungle-savanna: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 159 | 8 | 20 | 36 | 40 | 31 | 13 | 6 | 4 | 1 | 24 (15 %) | 52 % | 4 | 10 |
| market | 40 | 0 | 1 | 0 | 2 | 3 | 2 | 7 | 20 | 5 | 34 (85 %) | 65 % | 39 | 12 |
| house | 286 | 15 | 57 | 68 | 54 | 31 | 15 | 12 | 21 | 13 | 61 (21 %) | 0 % | 21 | 21 |
| workshop | 71 | 2 | 12 | 15 | 16 | 7 | 8 | 5 | 5 | 1 | 19 (27 %) | 38 % | 4 | 10 |
| hall | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 |
| highrise | 21 | 3 | 2 | 1 | 3 | 2 | 2 | 3 | 2 | 3 | 10 (48 %) | 43 % | 4 | 13 |
| stilted-tower | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 1 | -10 |
| temple | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 (100 %) | 0 % | 1 | 10 |
| tower | 8 | 0 | 1 | 4 | 0 | 2 | 0 | 0 | 1 | 0 | 1 (13 %) | 0 % | 0 | 6 |

# gate-border-desert-jungle: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 151 | 5 | 24 | 46 | 39 | 25 | 9 | 1 | 0 | 2 | 12 (8 %) | 53 % | 3 | 15 |
| market | 48 | 0 | 0 | 1 | 0 | 0 | 9 | 11 | 26 | 1 | 47 (98 %) | 83 % | 47 | 9 |
| house | 283 | 7 | 61 | 79 | 62 | 41 | 21 | 5 | 6 | 1 | 33 (12 %) | 0 % | 11 | 13 |
| workshop | 81 | 0 | 16 | 19 | 18 | 17 | 5 | 3 | 2 | 1 | 11 (14 %) | 27 % | 5 | 11 |
| hall | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 7 |
| highrise | 23 | 0 | 1 | 7 | 5 | 2 | 0 | 4 | 3 | 1 | 8 (35 %) | 30 % | 6 | 20 |
| stilted-tower | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | 1 | 4 (100 %) | 0 % | 4 | 12 |
| temple | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 1 (100 %) | 0 % | 0 | 4 |
| tower | 17 | 1 | 2 | 2 | 6 | 6 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 1 | 3 |

# gate-coast-city: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 162 | 5 | 14 | 44 | 47 | 26 | 16 | 6 | 3 | 1 | 26 (16 %) | 45 % | 7 | 9 |
| market | 48 | 0 | 2 | 0 | 1 | 1 | 5 | 11 | 26 | 2 | 44 (92 %) | 69 % | 47 | 9 |
| house | 350 | 9 | 42 | 109 | 72 | 44 | 26 | 22 | 21 | 5 | 74 (21 %) | 0 % | 20 | 11 |
| workshop | 93 | 1 | 14 | 18 | 19 | 10 | 13 | 9 | 7 | 2 | 31 (33 %) | 48 % | 12 | 14 |
| hall | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 |
| highrise | 40 | 0 | 4 | 0 | 11 | 6 | 7 | 3 | 9 | 0 | 19 (48 %) | 20 % | 14 | 8 |
| stilted-tower | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 0 | 2 (100 %) | 0 % | 2 | 7 |
| temple | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 |
| tower | 10 | 0 | 2 | 4 | 2 | 2 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 1 | 3 |

# gate-metropolis-temperate: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 884 | 21 | 135 | 242 | 224 | 148 | 82 | 23 | 9 | 0 | 114 (13 %) | 53 % | 26 | 8 |
| market | 211 | 0 | 3 | 0 | 3 | 8 | 19 | 53 | 111 | 14 | 197 (93 %) | 72 % | 207 | 16 |
| house | 1751 | 38 | 312 | 522 | 383 | 203 | 117 | 84 | 79 | 13 | 293 (17 %) | 0 % | 75 | 13 |
| workshop | 494 | 12 | 87 | 119 | 97 | 66 | 41 | 29 | 38 | 5 | 113 (23 %) | 35 % | 51 | 11 |
| block | 24 | 0 | 1 | 3 | 1 | 2 | 8 | 8 | 1 | 0 | 17 (71 %) | 0 % | 15 | 7 |
| hall | 7 | 0 | 0 | 0 | 2 | 2 | 1 | 0 | 2 | 0 | 3 (43 %) | 0 % | 3 | 8 |
| highrise | 288 | 8 | 27 | 40 | 62 | 44 | 30 | 20 | 44 | 13 | 107 (37 %) | 28 % | 86 | 13 |
| stilted-tower | 10 | 0 | 0 | 0 | 1 | 0 | 1 | 2 | 4 | 2 | 9 (90 %) | 0 % | 9 | 10 |
| temple | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | -4 |
| tower | 55 | 3 | 9 | 9 | 9 | 4 | 7 | 5 | 7 | 2 | 21 (38 %) | 0 % | 5 | 11 |

# gate-metropolis-mixed: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 344 | 8 | 53 | 88 | 87 | 66 | 30 | 7 | 2 | 3 | 42 (12 %) | 51 % | 9 | 13 |
| market | 85 | 0 | 0 | 0 | 3 | 2 | 4 | 25 | 46 | 5 | 80 (94 %) | 73 % | 84 | 9 |
| house | 651 | 28 | 114 | 169 | 123 | 86 | 53 | 32 | 33 | 13 | 131 (20 %) | 0 % | 34 | 22 |
| workshop | 158 | 9 | 26 | 30 | 33 | 19 | 20 | 11 | 8 | 2 | 41 (26 %) | 41 % | 14 | 12 |
| block | 5 | 0 | 0 | 0 | 0 | 1 | 1 | 1 | 2 | 0 | 4 (80 %) | 0 % | 5 | 6 |
| hall | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 8 |
| highrise | 263 | 0 | 11 | 38 | 46 | 42 | 29 | 22 | 47 | 28 | 126 (48 %) | 24 % | 90 | 17 |
| stilted-tower | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 | 2 (100 %) | 0 % | 2 | 13 |
| temple | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 2 |
| tower | 17 | 3 | 2 | 4 | 1 | 2 | 1 | 0 | 4 | 0 | 5 (29 %) | 0 % | 3 | 7 |

# gate-joined-city: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 166 | 6 | 32 | 45 | 44 | 24 | 8 | 6 | 1 | 0 | 15 (9 %) | 55 % | 2 | 7 |
| market | 42 | 0 | 2 | 1 | 1 | 2 | 3 | 7 | 24 | 2 | 36 (86 %) | 67 % | 40 | 11 |
| house | 382 | 7 | 61 | 109 | 68 | 47 | 30 | 17 | 26 | 17 | 90 (24 %) | 0 % | 23 | 20 |
| workshop | 89 | 1 | 8 | 31 | 10 | 10 | 7 | 10 | 8 | 4 | 29 (33 %) | 35 % | 11 | 14 |
| hall | 2 | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 2 |
| highrise | 26 | 2 | 3 | 2 | 5 | 2 | 2 | 2 | 5 | 3 | 12 (46 %) | 27 % | 10 | 13 |
| stilted-tower | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 (100 %) | 0 % | 1 | 6 |
| temple | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 2 |
| tower | 20 | 0 | 3 | 4 | 6 | 2 | 3 | 1 | 0 | 1 | 5 (25 %) | 0 % | 2 | 9 |

# river-town: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 22 | 0 | 2 | 5 | 7 | 1 | 5 | 2 | 0 | 0 | 7 (32 %) | 59 % | 4 | 5 |
| market | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 2 (100 %) | 100 % | 2 | 6 |
| house | 123 | 4 | 27 | 28 | 26 | 15 | 7 | 3 | 13 | 0 | 23 (19 %) | 0 % | 9 | 8 |
| workshop | 39 | 0 | 7 | 12 | 7 | 4 | 2 | 2 | 4 | 1 | 9 (23 %) | 33 % | 5 | 9 |
| hall | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 (100 %) | 0 % | 1 | 15 |
| stilted-tower | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 2 (100 %) | 0 % | 2 | 19 |
| tower | 6 | 0 | 0 | 2 | 0 | 2 | 1 | 1 | 0 | 0 | 2 (33 %) | 0 % | 0 | 5 |

# fly-hill-town: front doors

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 5 | 0 | 0 | 2 | 3 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 60 % | 0 | 2 |
| market | 3 | 0 | 0 | 0 | 1 | 0 | 0 | 1 | 1 | 0 | 2 (67 %) | 33 % | 3 | 6 |
| house | 72 | 5 | 9 | 35 | 14 | 5 | 3 | 0 | 1 | 0 | 4 (6 %) | 0 % | 1 | 6 |
| workshop | 37 | 5 | 5 | 12 | 10 | 3 | 1 | 0 | 1 | 0 | 2 (5 %) | 41 % | 2 | 6 |
| hall | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 2 |
| stilted-tower | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 (100 %) | 0 % | 1 | 5 |
| tower | 5 | 2 | 0 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 3 |
