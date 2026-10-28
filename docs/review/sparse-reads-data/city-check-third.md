# city-check

gate-cut > 4 (EXIT gates whose street end is more than that below or above the ground; report only, fix is 7.19): 3

9 sites (scenes: auto:2), 0 failing, 0 violations

Design warnings (high-rise, door-rise beyond the planner's limit, flight-landings and proposed checks, not violations; 9 sites): core-fill 9; door-rise 1; edge-taper 10; gate-cut 3; highrise-lobby 13; road-hierarchy 5; road-sliver 26; roof-reachable 26; shaft-present 13; 

| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | s1_m10 | city | 477 | 1 | 33 | 327 | 2 | 100 | 0 | 0 | 11897 |
| gate-desert-badlands | s6_m7 | city | 827 | 1 | 64 | 735 | 2 | 100 | 0 | 0 | 9558 |
| gate-snow-city | s4_0 | city | 478 | 1 | 29 | 278 | 2 | 100 | 0 | 0 | 7016 |
| gate-jungle-savanna | sm1_m4 | city | 790 | 1 | 48 | 606 | 2 | 100 | 0 | 0 | 8746 |
| gate-border-desert-jungle | s7_m8 | city | 577 | 1 | 45 | 625 | 2 | 100 | 0 | 0 | 7866 |
| gate-coast-city | sm8_m4 | city | 740 | 2 | 56 | 713 | 2 | 100 | 0 | 0 | 9229 |
| gate-metropolis-temperate | s2_m6 | metropolis | 1430 | 8 | 220 | 3751 | 2 | 100 | 0 | 0 | 18962 |
| gate-metropolis-mixed | s2_m2 | metropolis | 1126 | 4 | 114 | 1495 | 2 | 100 | 0 | 0 | 13601 |
| gate-joined-city | s8_0 | city | 717 | 2 | 61 | 636 | 2 | 100 | 0 | 0 | 12804 |

Terrain response (urban % = columns the mask kept of those inside the footprint that are dry; cut and fill in thousands of blocks):

| site | urban % | lots | empty:steep | cut k | fill k | steep roads | bridges | quays | quay lots |
|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | 92 | 327 | 25 | 404 | 1224 | 69 | 37 | 14 | 8 |
| gate-desert-badlands | 74 | 735 | 64 | 613 | 1858 | 92 | 101 | 40 | 50 |
| gate-snow-city | 69 | 278 | 37 | 208 | 642 | 39 | 33 | 9 | 10 |
| gate-jungle-savanna | 83 | 606 | 64 | 606 | 1829 | 110 | 94 | 28 | 19 |
| gate-border-desert-jungle | 90 | 625 | 36 | 453 | 1295 | 59 | 80 | 27 | 25 |
| gate-coast-city | 97 | 713 | 40 | 531 | 1629 | 76 | 70 | 26 | 22 |
| gate-metropolis-temperate | 93 | 3751 | 163 | 3652 | 11425 | 420 | 566 | 215 | 262 |
| gate-metropolis-mixed | 97 | 1495 | 103 | 1616 | 4979 | 189 | 253 | 50 | 57 |
| gate-joined-city | 92 | 636 | 71 | 672 | 2034 | 99 | 117 | 44 | 38 |

## Fill (DS-47, report only)

Dry columns in the site disk by what covers them (%): road, built lot, plaza, unbuilt lot (`empty:*`), empty. Frontage = share of the non-road columns beside a road that are lot or plaza. Core is density 70+, middle 50-69, edge under 50.

### Band core

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 155 | 13.8 | 26.9 | 1.8 | 8.5 | 49.0 | 43.7 |
| gate-desert-badlands | 302 | 10.8 | 25.2 | 0.7 | 3.8 | 59.5 | 48.9 |
| gate-snow-city | 56 | 9.8 | 17.9 | 0.7 | 3.2 | 68.4 | 47.7 |
| gate-jungle-savanna | 231 | 13.8 | 24.9 | 0.4 | 5.2 | 55.7 | 45.2 |
| gate-border-desert-jungle | 121 | 11.7 | 31.4 | 2.0 | 4.4 | 50.6 | 64.1 |
| gate-coast-city | 253 | 13.2 | 30.3 | 1.0 | 7.2 | 48.2 | 54.5 |
| gate-metropolis-temperate | 861 | 17.2 | 34.5 | 0.6 | 5.6 | 42.2 | 53.2 |
| gate-metropolis-mixed | 866 | 13.5 | 31.7 | 0.7 | 4.9 | 49.1 | 58.0 |
| gate-joined-city | 208 | 13.1 | 16.6 | 1.6 | 6.0 | 62.8 | 33.7 |

### Band middle

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 63 | 8.3 | 22.2 | 0.0 | 2.9 | 66.7 | 55.9 |
| gate-desert-badlands | 249 | 8.4 | 13.8 | 0.0 | 5.0 | 72.8 | 36.7 |
| gate-snow-city | 101 | 4.5 | 10.2 | 0.0 | 4.6 | 80.7 | 51.0 |
| gate-jungle-savanna | 190 | 8.5 | 15.6 | 0.4 | 4.7 | 70.8 | 42.9 |
| gate-border-desert-jungle | 145 | 7.2 | 23.8 | 0.0 | 2.5 | 66.6 | 63.9 |
| gate-coast-city | 159 | 7.9 | 18.0 | 0.0 | 2.0 | 72.1 | 56.5 |
| gate-metropolis-temperate | 867 | 15.8 | 31.0 | 0.6 | 1.7 | 50.9 | 59.6 |
| gate-metropolis-mixed | 214 | 12.7 | 24.8 | 0.0 | 2.0 | 60.5 | 56.7 |
| gate-joined-city | 212 | 9.8 | 18.2 | 0.3 | 6.4 | 65.3 | 47.8 |

### Band edge

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 368 | 4.3 | 5.4 | 0.4 | 1.3 | 88.6 | 28.2 |
| gate-desert-badlands | 1049 | 2.1 | 3.5 | 0.0 | 1.5 | 92.8 | 32.1 |
| gate-snow-city | 414 | 4.2 | 8.2 | 0.0 | 2.3 | 85.4 | 43.7 |
| gate-jungle-savanna | 610 | 4.6 | 7.0 | 0.0 | 2.7 | 85.7 | 35.1 |
| gate-border-desert-jungle | 498 | 4.6 | 12.1 | 0.0 | 2.7 | 80.6 | 53.4 |
| gate-coast-city | 626 | 3.5 | 8.8 | 0.0 | 1.0 | 86.7 | 51.9 |
| gate-metropolis-temperate | 2574 | 6.4 | 10.7 | 0.1 | 1.9 | 80.9 | 45.0 |
| gate-metropolis-mixed | 799 | 5.7 | 10.2 | 0.1 | 3.1 | 80.9 | 45.1 |
| gate-joined-city | 707 | 4.1 | 7.2 | 0.1 | 2.5 | 86.2 | 37.6 |

### Ring 0-0.25

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 39 | 17.8 | 34.2 | 2.5 | 0.1 | 45.4 | 45.7 |
| gate-desert-badlands | 127 | 8.0 | 16.7 | 0.5 | 1.7 | 73.1 | 41.6 |
| gate-snow-city | 31 | 6.1 | 3.0 | 1.3 | 0.0 | 89.7 | 14.6 |
| gate-jungle-savanna | 116 | 12.4 | 18.2 | 0.6 | 3.3 | 65.5 | 37.9 |
| gate-border-desert-jungle | 54 | 12.2 | 24.0 | 2.8 | 5.7 | 55.3 | 50.5 |
| gate-coast-city | 101 | 16.3 | 27.6 | 1.4 | 10.0 | 44.6 | 42.7 |
| gate-metropolis-temperate | 344 | 16.4 | 26.8 | 0.5 | 5.9 | 50.5 | 44.9 |
| gate-metropolis-mixed | 175 | 17.0 | 25.7 | 1.3 | 5.2 | 50.8 | 48.4 |
| gate-joined-city | 65 | 16.2 | 19.7 | 3.8 | 5.0 | 55.4 | 33.0 |

### Ring 0.25-0.5

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 122 | 12.0 | 20.3 | 1.7 | 10.9 | 55.1 | 36.0 |
| gate-desert-badlands | 360 | 7.4 | 16.6 | 0.4 | 3.4 | 72.2 | 47.1 |
| gate-snow-city | 122 | 6.5 | 14.6 | 0.0 | 4.1 | 74.8 | 53.7 |
| gate-jungle-savanna | 319 | 9.3 | 17.3 | 0.1 | 4.6 | 68.7 | 43.8 |
| gate-border-desert-jungle | 169 | 7.5 | 24.8 | 0.5 | 3.1 | 64.1 | 67.5 |
| gate-coast-city | 195 | 10.8 | 29.1 | 0.6 | 4.6 | 54.9 | 59.3 |
| gate-metropolis-temperate | 995 | 14.5 | 30.1 | 0.4 | 3.8 | 51.1 | 52.9 |
| gate-metropolis-mixed | 523 | 12.6 | 32.0 | 0.5 | 5.4 | 49.5 | 57.2 |
| gate-joined-city | 255 | 11.4 | 16.9 | 0.5 | 5.7 | 65.5 | 36.7 |

### Ring 0.5-0.75

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 172 | 8.5 | 16.2 | 0.7 | 1.7 | 72.8 | 43.5 |
| gate-desert-badlands | 548 | 5.8 | 9.9 | 0.0 | 3.3 | 81.0 | 36.1 |
| gate-snow-city | 176 | 5.1 | 8.8 | 0.0 | 2.6 | 83.6 | 41.8 |
| gate-jungle-savanna | 410 | 6.9 | 11.3 | 0.2 | 4.0 | 77.6 | 39.1 |
| gate-border-desert-jungle | 248 | 7.0 | 19.2 | 0.0 | 2.9 | 70.9 | 59.6 |
| gate-coast-city | 292 | 6.5 | 14.0 | 0.0 | 1.4 | 78.1 | 52.1 |
| gate-metropolis-temperate | 1377 | 13.5 | 23.5 | 0.5 | 1.6 | 60.9 | 53.3 |
| gate-metropolis-mixed | 549 | 9.2 | 20.9 | 0.3 | 1.9 | 67.6 | 58.8 |
| gate-joined-city | 388 | 6.3 | 10.8 | 0.2 | 4.4 | 78.5 | 41.8 |

### Ring 0.75-1

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 252 | 2.4 | 3.7 | 0.0 | 1.4 | 92.5 | 32.0 |
| gate-desert-badlands | 565 | 1.2 | 2.2 | 0.0 | 1.3 | 95.3 | 32.8 |
| gate-snow-city | 242 | 3.5 | 8.2 | 0.0 | 2.6 | 85.6 | 46.1 |
| gate-jungle-savanna | 187 | 2.1 | 3.9 | 0.0 | 1.4 | 92.6 | 42.8 |
| gate-border-desert-jungle | 292 | 3.6 | 10.4 | 0.0 | 2.4 | 83.6 | 51.6 |
| gate-coast-city | 449 | 2.6 | 7.8 | 0.0 | 0.9 | 88.7 | 59.8 |
| gate-metropolis-temperate | 1587 | 4.0 | 8.0 | 0.0 | 1.9 | 86.1 | 51.1 |
| gate-metropolis-mixed | 633 | 6.9 | 13.0 | 0.0 | 3.8 | 76.3 | 48.8 |
| gate-joined-city | 418 | 3.1 | 6.3 | 0.0 | 2.1 | 88.5 | 42.6 |

### All

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 586 | 7.3 | 12.9 | 0.7 | 3.4 | 75.7 | 39.6 |
| gate-desert-badlands | 1599 | 4.7 | 9.2 | 0.1 | 2.5 | 83.4 | 40.4 |
| gate-snow-city | 571 | 4.8 | 9.5 | 0.1 | 2.8 | 82.9 | 45.6 |
| gate-jungle-savanna | 1032 | 7.4 | 12.6 | 0.2 | 3.6 | 76.2 | 40.8 |
| gate-border-desert-jungle | 764 | 6.2 | 17.4 | 0.3 | 2.9 | 73.2 | 58.5 |
| gate-coast-city | 1037 | 6.6 | 15.5 | 0.3 | 2.6 | 75.1 | 54.0 |
| gate-metropolis-temperate | 4302 | 10.5 | 19.6 | 0.3 | 2.6 | 67.1 | 51.9 |
| gate-metropolis-mixed | 1880 | 10.1 | 21.8 | 0.4 | 3.8 | 63.9 | 54.3 |
| gate-joined-city | 1127 | 6.8 | 11.0 | 0.4 | 3.8 | 77.9 | 39.0 |

### Per site

| site | tier | arterial+main % | street km | slivers | sliver area | empty gaps <=12 % | dead ends | block lots | core lot depth (median, n) | roof cover % |
|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | city | 30.1 | 8.8 | 3 | 420 | 10.7 | 40 | 0 | hall 26 (1), highrise 22 (24), house 10 (9), shop 17 (85), workshop 15 (8) | 14.3 |
| gate-desert-badlands | city | 24.1 | 16.9 | 4 | 1280 | 11.9 | 100 | 0 | hall 25 (1), highrise 23 (20), house 9 (25), shop 17 (229), workshop 15 (5) | 10.9 |
| gate-snow-city | city | 25.9 | 6.1 | 0 | 0 | 11.8 | 26 | 0 | hall 25 (1), highrise 24 (4), house 9 (9), shop 17 (19), temple 27 (1) | 7.6 |
| gate-jungle-savanna | city | 31.0 | 14.2 | 4 | 2630 | 13.4 | 82 | 0 | hall 26 (1), highrise 24 (14), house 10 (16), shop 17 (185), temple 27 (1), workshop 15 (8) | 12.8 |
| gate-border-desert-jungle | city | 19.9 | 10.4 | 0 | 0 | 4.9 | 45 | 0 | hall 26 (1), highrise 24 (17), house 10 (6), shop 17 (111), temple 26 (1), workshop 12 (5) | 12.9 |
| gate-coast-city | city | 27.7 | 14.1 | 2 | 862 | 12.6 | 80 | 0 | hall 25 (1), highrise 23 (39), house 9 (28), shop 17 (179), temple 26 (1), workshop 16 (5) | 11.5 |
| gate-metropolis-temperate | metropolis | 31.3 | 83.6 | 17 | 4470 | 9.7 | 374 | 1 | hall 25 (2), highrise 23 (212), house 10 (72), shop 17 (568), temple 27 (1), workshop 13 (17) | 13.6 |
| gate-metropolis-mixed | metropolis | 32.3 | 35.8 | 7 | 2788 | 10.9 | 192 | 0 | hall 26 (3), highrise 22 (229), house 10 (68), shop 17 (398), workshop 14 (17) | 2.9 |
| gate-joined-city | city | 38.3 | 16.8 | 4 | 1361 | 13.8 | 83 | 0 | hall 26 (1), highrise 22 (10), house 9 (15), shop 17 (100), temple 26 (1), workshop 15 (4) | 18.0 |

### Sliver pairs

Columns of sliver strips by the kinds of the two roads either side (kinds in alphabetical order), per site in the order of the table above.

| pair | gate-temperate-city | gate-desert-badlands | gate-snow-city | gate-jungle-savanna | gate-border-desert-jungle | gate-coast-city | gate-metropolis-temperate | gate-metropolis-mixed | gate-joined-city | all |
|---|---|---|---|---|---|---|---|---|---|---|
| arterial/lane | 0 | 630 | 0 | 0 | 0 | 0 | 317 | 0 | 657 | 1604 |
| arterial/main | 0 | 0 | 0 | 0 | 0 | 0 | 941 | 66 | 378 | 1385 |
| arterial/quay | 156 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 96 | 252 |
| lane/lane | 0 | 0 | 0 | 0 | 0 | 0 | 180 | 0 | 0 | 180 |
| lane/main | 0 | 0 | 0 | 0 | 0 | 407 | 530 | 170 | 0 | 1107 |
| lane/quay | 0 | 0 | 0 | 0 | 0 | 0 | 108 | 0 | 0 | 108 |
| lane/street | 261 | 0 | 0 | 0 | 0 | 0 | 772 | 347 | 0 | 1380 |
| main/main | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 10 | 0 | 25 |
| main/quay | 0 | 0 | 0 | 0 | 0 | 0 | 392 | 179 | 230 | 801 |
| main/street | 3 | 0 | 0 | 2298 | 0 | 455 | 657 | 2016 | 0 | 5429 |
| quay/quay | 0 | 42 | 0 | 0 | 0 | 0 | 170 | 0 | 0 | 212 |
| quay/street | 0 | 167 | 0 | 317 | 0 | 0 | 388 | 0 | 0 | 872 |
| street/street | 0 | 441 | 0 | 15 | 0 | 0 | 0 | 0 | 0 | 456 |

## Glazing (DS-33, report only)

Buildings in the district scenes built; glass cells over exterior wall-plane cells.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| hall | 8 | 20 % | 22 % | 15 % | 12..29 % | 228 | 0 % | 100 % | 28 % | 28 % | 25-40 % |
| highrise | 13 | 27 % | 31 % | 27 % | 20..34 % | 4969 | 0 % | 100 % | 40 % | 40 % | 35-90 % (7.13) |
| house | 71 | 19 % | 20 % | 18 % | 10..31 % | 749 | 0 % | 100 % | 4 % | 4 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |
| shop | 178 | 23 % | 22 % | 23 % | 12..33 % | 7220 | 0 % | 100 % | 32 % | 32 % | upper 25-40 %, shopfront 50-70 %, upper sill +1 |
| temple | 9 | 14 % | 16 % | 12 % | 7..19 % | 310 | 0 % | 100 % | 10 % | 10 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |
| tower | 3 | 14 % | 15 % | 13 % | 0..22 % | 98 | 0 % | 100 % | 0 % | 0 % | - |
| workshop | 19 | 14 % | 14 % | 16 % | 10..23 % | 246 | 0 % | 100 % | 4 % | 4 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |

## Door rise (DS-45, report only)

Entrance doors of the buildings in the district scenes built; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 178 | 1 | 28 | 146 | 3 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 2 % | 0 | 2 | 177 (99 %) |
| house | 71 | 1 | 11 | 56 | 2 | 0 | 0 | 1 | 0 | 0 | 1 (1 %) | 1 % | 0 | 5 | 69 (97 %) |
| workshop | 19 | 0 | 2 | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 | 19 (100 %) |
| hall | 8 | 0 | 1 | 2 | 1 | 1 | 1 | 0 | 1 | 1 | 3 (38 %) | 0 % | 0 | 30 | 5 (63 %) |
| highrise | 13 | 1 | 1 | 0 | 5 | 1 | 2 | 0 | 3 | 0 | 5 (38 %) | 15 % | 0 | 7 | 7 (54 %) |
| temple | 9 | 0 | 2 | 2 | 2 | 0 | 2 | 0 | 0 | 1 | 3 (33 %) | 0 % | 0 | 11 | 6 (67 %) |
| tower | 3 | 0 | 0 | 1 | 0 | 1 | 1 | 0 | 0 | 0 | 1 (33 %) | 0 % | 0 | 4 | 2 (67 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
