# city-check

gate-cut > 4 (EXIT gates whose street end is more than that below or above the ground; report only, fix is 7.19): 2

9 sites (scenes: auto:2), 0 failing, 0 violations

Design warnings (high-rise, door-rise beyond the planner's limit, flight-landings and proposed checks, not violations; 9 sites): core-fill 9; door-rise 2; edge-taper 12; flight-landings 2; gate-cut 2; highrise-lobby 14; road-hierarchy 5; road-sliver 31; roof-reachable 28; shaft-present 14; 

| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | s1_m10 | city | 477 | 1 | 33 | 318 | 2 | 100 | 0 | 0 | 9140 |
| gate-desert-badlands | s6_m7 | city | 827 | 1 | 64 | 762 | 2 | 100 | 0 | 0 | 8667 |
| gate-snow-city | s4_0 | city | 478 | 1 | 29 | 283 | 2 | 100 | 0 | 0 | 6743 |
| gate-jungle-savanna | sm1_m4 | city | 790 | 1 | 48 | 599 | 2 | 100 | 0 | 0 | 8158 |
| gate-border-desert-jungle | s7_m8 | city | 577 | 1 | 45 | 600 | 2 | 100 | 0 | 0 | 8413 |
| gate-coast-city | sm8_m4 | city | 740 | 2 | 56 | 692 | 2 | 100 | 0 | 0 | 8877 |
| gate-metropolis-temperate | s2_m6 | metropolis | 1430 | 8 | 220 | 3727 | 2 | 100 | 0 | 0 | 16473 |
| gate-metropolis-mixed | s2_m2 | metropolis | 1126 | 4 | 114 | 1497 | 2 | 100 | 0 | 0 | 10872 |
| gate-joined-city | s8_0 | city | 717 | 2 | 61 | 670 | 2 | 100 | 0 | 0 | 10122 |

Terrain response (urban % = columns the mask kept of those inside the footprint that are dry; cut and fill in thousands of blocks):

| site | urban % | lots | empty:steep | cut k | fill k | steep roads | bridges | quays | quay lots |
|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | 91 | 318 | 17 | 394 | 1212 | 63 | 29 | 14 | 8 |
| gate-desert-badlands | 73 | 762 | 61 | 612 | 1856 | 79 | 101 | 36 | 49 |
| gate-snow-city | 70 | 283 | 36 | 205 | 639 | 37 | 33 | 8 | 8 |
| gate-jungle-savanna | 83 | 599 | 59 | 595 | 1816 | 102 | 90 | 28 | 19 |
| gate-border-desert-jungle | 90 | 600 | 34 | 442 | 1267 | 59 | 80 | 29 | 25 |
| gate-coast-city | 96 | 692 | 40 | 528 | 1627 | 67 | 70 | 26 | 21 |
| gate-metropolis-temperate | 93 | 3727 | 149 | 3646 | 11393 | 426 | 568 | 206 | 252 |
| gate-metropolis-mixed | 97 | 1497 | 102 | 1587 | 4918 | 188 | 262 | 43 | 54 |
| gate-joined-city | 92 | 670 | 70 | 678 | 2069 | 102 | 121 | 44 | 41 |

## Fill (DS-47, report only)

Dry columns in the site disk by what covers them (%): road, built lot, plaza, unbuilt lot (`empty:*`), empty. Frontage = share of the non-road columns beside a road that are lot or plaza. Core is density 70+, middle 50-69, edge under 50.

### Band core

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 154 | 14.0 | 27.1 | 1.6 | 8.4 | 48.8 | 44.3 |
| gate-desert-badlands | 300 | 10.9 | 25.5 | 0.7 | 3.0 | 60.0 | 49.0 |
| gate-snow-city | 56 | 10.6 | 18.2 | 0.7 | 2.7 | 67.8 | 48.2 |
| gate-jungle-savanna | 227 | 13.5 | 24.6 | 0.4 | 4.3 | 57.2 | 47.1 |
| gate-border-desert-jungle | 122 | 11.4 | 31.1 | 1.9 | 3.7 | 51.9 | 65.8 |
| gate-coast-city | 253 | 13.0 | 28.7 | 1.2 | 7.3 | 49.8 | 52.3 |
| gate-metropolis-temperate | 865 | 17.2 | 34.8 | 0.6 | 4.8 | 42.6 | 53.6 |
| gate-metropolis-mixed | 867 | 13.3 | 32.1 | 0.7 | 4.4 | 49.5 | 60.1 |
| gate-joined-city | 208 | 13.7 | 17.1 | 1.5 | 6.1 | 61.6 | 32.1 |

### Band middle

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 62 | 7.5 | 21.6 | 0.0 | 1.1 | 69.7 | 61.3 |
| gate-desert-badlands | 237 | 9.0 | 15.5 | 0.0 | 4.7 | 70.9 | 38.8 |
| gate-snow-city | 103 | 4.0 | 9.8 | 0.0 | 3.3 | 82.9 | 58.8 |
| gate-jungle-savanna | 199 | 8.3 | 15.1 | 0.4 | 4.2 | 72.0 | 43.3 |
| gate-border-desert-jungle | 146 | 6.6 | 21.6 | 0.0 | 1.9 | 69.9 | 62.5 |
| gate-coast-city | 160 | 7.6 | 18.2 | 0.0 | 2.3 | 72.0 | 59.0 |
| gate-metropolis-temperate | 880 | 15.7 | 30.0 | 0.6 | 1.9 | 51.7 | 58.7 |
| gate-metropolis-mixed | 215 | 12.5 | 24.3 | 0.0 | 2.4 | 60.8 | 56.6 |
| gate-joined-city | 213 | 10.4 | 18.8 | 0.2 | 6.5 | 64.0 | 48.4 |

### Band edge

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 373 | 4.5 | 6.1 | 0.4 | 0.8 | 88.2 | 29.4 |
| gate-desert-badlands | 1068 | 2.0 | 3.6 | 0.0 | 1.6 | 92.8 | 33.0 |
| gate-snow-city | 414 | 4.2 | 8.0 | 0.0 | 2.4 | 85.4 | 44.7 |
| gate-jungle-savanna | 612 | 4.6 | 7.1 | 0.0 | 2.5 | 85.8 | 36.2 |
| gate-border-desert-jungle | 500 | 4.5 | 12.2 | 0.0 | 2.8 | 80.5 | 53.6 |
| gate-coast-city | 631 | 3.7 | 8.6 | 0.0 | 1.0 | 86.7 | 49.8 |
| gate-metropolis-temperate | 2595 | 6.3 | 10.5 | 0.1 | 1.8 | 81.3 | 45.2 |
| gate-metropolis-mixed | 803 | 5.5 | 10.1 | 0.0 | 3.2 | 81.2 | 47.9 |
| gate-joined-city | 709 | 4.1 | 7.4 | 0.1 | 2.4 | 86.0 | 38.8 |

### Ring 0-0.25

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 39 | 18.9 | 33.2 | 2.5 | 0.1 | 45.3 | 42.9 |
| gate-desert-badlands | 127 | 7.7 | 16.8 | 0.5 | 2.0 | 73.1 | 42.9 |
| gate-snow-city | 31 | 6.4 | 2.9 | 1.2 | 0.0 | 89.5 | 14.0 |
| gate-jungle-savanna | 116 | 12.7 | 18.5 | 0.6 | 3.1 | 65.1 | 39.3 |
| gate-border-desert-jungle | 55 | 12.1 | 24.6 | 2.8 | 5.4 | 55.0 | 53.5 |
| gate-coast-city | 101 | 16.4 | 26.7 | 1.7 | 10.9 | 44.3 | 42.0 |
| gate-metropolis-temperate | 345 | 16.1 | 27.8 | 0.6 | 5.0 | 50.4 | 47.6 |
| gate-metropolis-mixed | 175 | 17.0 | 25.2 | 1.2 | 4.7 | 51.8 | 49.1 |
| gate-joined-city | 65 | 16.2 | 19.2 | 3.6 | 4.6 | 56.3 | 30.0 |

### Ring 0.25-0.5

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 122 | 11.9 | 20.5 | 1.4 | 10.6 | 55.7 | 37.1 |
| gate-desert-badlands | 362 | 7.5 | 16.7 | 0.4 | 2.7 | 72.6 | 46.7 |
| gate-snow-city | 123 | 6.6 | 15.2 | 0.0 | 2.8 | 75.3 | 57.9 |
| gate-jungle-savanna | 321 | 8.8 | 16.8 | 0.1 | 3.5 | 70.8 | 45.8 |
| gate-border-desert-jungle | 169 | 7.1 | 23.8 | 0.5 | 2.5 | 66.1 | 68.2 |
| gate-coast-city | 197 | 10.5 | 27.2 | 0.6 | 4.5 | 57.1 | 57.2 |
| gate-metropolis-temperate | 1000 | 14.6 | 29.6 | 0.4 | 3.6 | 51.8 | 51.9 |
| gate-metropolis-mixed | 525 | 12.2 | 32.4 | 0.6 | 5.1 | 49.7 | 59.4 |
| gate-joined-city | 257 | 11.8 | 17.9 | 0.4 | 5.8 | 64.1 | 37.4 |

### Ring 0.5-0.75

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 173 | 8.7 | 17.5 | 0.7 | 0.9 | 72.2 | 45.3 |
| gate-desert-badlands | 548 | 5.8 | 10.5 | 0.0 | 3.1 | 80.6 | 37.8 |
| gate-snow-city | 177 | 4.5 | 8.2 | 0.0 | 2.6 | 84.7 | 46.6 |
| gate-jungle-savanna | 413 | 6.8 | 11.3 | 0.2 | 3.8 | 77.8 | 39.4 |
| gate-border-desert-jungle | 251 | 6.7 | 18.5 | 0.0 | 2.6 | 72.2 | 59.5 |
| gate-coast-city | 295 | 6.4 | 13.9 | 0.0 | 1.4 | 78.3 | 52.4 |
| gate-metropolis-temperate | 1396 | 13.4 | 22.9 | 0.5 | 1.6 | 61.5 | 52.7 |
| gate-metropolis-mixed | 551 | 9.0 | 21.0 | 0.3 | 1.7 | 68.0 | 60.3 |
| gate-joined-city | 390 | 6.6 | 11.1 | 0.2 | 4.3 | 77.9 | 42.3 |

### Ring 0.75-1

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 254 | 2.3 | 3.7 | 0.0 | 0.9 | 93.1 | 34.0 |
| gate-desert-badlands | 568 | 1.1 | 2.2 | 0.0 | 1.4 | 95.4 | 33.4 |
| gate-snow-city | 242 | 3.8 | 8.0 | 0.0 | 2.8 | 85.3 | 44.1 |
| gate-jungle-savanna | 188 | 2.0 | 4.1 | 0.0 | 1.4 | 92.5 | 44.1 |
| gate-border-desert-jungle | 292 | 3.6 | 10.4 | 0.0 | 2.5 | 83.5 | 51.0 |
| gate-coast-city | 450 | 2.7 | 7.6 | 0.0 | 1.0 | 88.7 | 57.1 |
| gate-metropolis-temperate | 1599 | 3.8 | 7.9 | 0.0 | 1.7 | 86.5 | 52.9 |
| gate-metropolis-mixed | 635 | 6.7 | 12.9 | 0.0 | 3.9 | 76.5 | 51.6 |
| gate-joined-city | 418 | 3.1 | 6.3 | 0.0 | 2.2 | 88.4 | 43.4 |

### All

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 589 | 7.3 | 13.2 | 0.6 | 2.8 | 76.0 | 40.7 |
| gate-desert-badlands | 1605 | 4.7 | 9.4 | 0.1 | 2.3 | 83.4 | 41.3 |
| gate-snow-city | 573 | 4.8 | 9.4 | 0.1 | 2.6 | 83.2 | 47.6 |
| gate-jungle-savanna | 1038 | 7.2 | 12.5 | 0.2 | 3.2 | 76.9 | 41.9 |
| gate-border-desert-jungle | 768 | 6.0 | 17.0 | 0.3 | 2.7 | 73.9 | 58.6 |
| gate-coast-city | 1043 | 6.5 | 15.0 | 0.3 | 2.7 | 75.5 | 52.6 |
| gate-metropolis-temperate | 4340 | 10.4 | 19.3 | 0.3 | 2.4 | 67.6 | 51.9 |
| gate-metropolis-mixed | 1886 | 9.9 | 21.8 | 0.4 | 3.7 | 64.3 | 56.3 |
| gate-joined-city | 1130 | 7.0 | 11.3 | 0.4 | 3.9 | 77.4 | 39.2 |

### Per site

| site | tier | arterial+main % | street km | slivers | sliver area | empty gaps <=12 % | dead ends | block lots | core lot depth (median, n) | roof cover % |
|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | city | 30.4 | 8.7 | 5 | 942 | 11.1 | 40 | 1 | hall 26 (1), highrise 22 (23), house 10 (10), shop 18 (90), workshop 15 (7) | 13.9 |
| gate-desert-badlands | city | 24.2 | 16.9 | 6 | 2031 | 11.6 | 100 | 0 | hall 25 (1), highrise 25 (18), house 8 (27), shop 18 (236), workshop 14 (6) | 10.9 |
| gate-snow-city | city | 27.7 | 6.0 | 0 | 0 | 9.5 | 20 | 0 | hall 25 (1), highrise 24 (4), house 8 (8), shop 16 (21), temple 27 (1) | 7.6 |
| gate-jungle-savanna | city | 33.4 | 13.7 | 3 | 1879 | 12.6 | 83 | 0 | hall 26 (1), highrise 24 (12), house 10 (19), shop 17 (178), temple 27 (1), workshop 13 (5) | 13.7 |
| gate-border-desert-jungle | city | 20.5 | 10.1 | 1 | 255 | 5.0 | 45 | 0 | hall 26 (1), highrise 23 (16), house 10 (6), shop 17 (108), temple 26 (1), workshop 12 (5) | 16.9 |
| gate-coast-city | city | 27.6 | 14.1 | 3 | 1167 | 13.4 | 77 | 0 | hall 25 (1), highrise 23 (37), house 10 (25), shop 17 (167), temple 26 (1), workshop 12 (8) | 11.2 |
| gate-metropolis-temperate | metropolis | 31.5 | 83.2 | 17 | 5098 | 9.9 | 366 | 1 | hall 25 (2), highrise 23 (219), house 10 (67), shop 17 (579), temple 27 (1), workshop 13 (15) | 13.0 |
| gate-metropolis-mixed | metropolis | 32.7 | 34.9 | 7 | 2956 | 10.0 | 183 | 1 | block 77 (1), hall 26 (3), highrise 22 (229), house 10 (63), shop 17 (393), workshop 14 (20) | 2.9 |
| gate-joined-city | city | 37.6 | 17.3 | 4 | 1240 | 13.0 | 84 | 0 | hall 26 (1), highrise 24 (11), house 8 (15), shop 17 (104), temple 26 (1), workshop 15 (4) | 17.5 |

### Sliver pairs

Columns of sliver strips by the kinds of the two roads either side (kinds in alphabetical order), per site in the order of the table above.

| pair | gate-temperate-city | gate-desert-badlands | gate-snow-city | gate-jungle-savanna | gate-border-desert-jungle | gate-coast-city | gate-metropolis-temperate | gate-metropolis-mixed | gate-joined-city | all |
|---|---|---|---|---|---|---|---|---|---|---|
| arterial/lane | 320 | 937 | 0 | 0 | 0 | 0 | 505 | 0 | 412 | 2174 |
| arterial/main | 0 | 0 | 0 | 0 | 0 | 0 | 617 | 66 | 378 | 1061 |
| arterial/quay | 156 | 0 | 0 | 221 | 0 | 0 | 0 | 0 | 96 | 473 |
| lane/lane | 202 | 0 | 0 | 0 | 0 | 0 | 515 | 0 | 0 | 717 |
| lane/main | 0 | 0 | 0 | 0 | 0 | 407 | 424 | 170 | 6 | 1007 |
| lane/quay | 0 | 0 | 0 | 0 | 62 | 0 | 0 | 0 | 0 | 62 |
| lane/street | 261 | 0 | 0 | 0 | 193 | 0 | 731 | 144 | 90 | 1419 |
| main/main | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 10 | 0 | 25 |
| main/quay | 0 | 0 | 0 | 0 | 0 | 0 | 246 | 239 | 258 | 743 |
| main/street | 3 | 396 | 0 | 1633 | 0 | 735 | 1170 | 2327 | 0 | 6264 |
| quay/quay | 0 | 42 | 0 | 0 | 0 | 6 | 170 | 0 | 0 | 218 |
| quay/street | 0 | 215 | 0 | 10 | 0 | 19 | 705 | 0 | 0 | 949 |
| street/street | 0 | 441 | 0 | 15 | 0 | 0 | 0 | 0 | 0 | 456 |

## Glazing (DS-33, report only)

Buildings in the district scenes built; glass cells over exterior wall-plane cells.

| type | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |
|---|---|---|---|---|---|---|---|---|---|---|---|
| hall | 8 | 20 % | 22 % | 15 % | 12..29 % | 238 | 0 % | 100 % | 23 % | 23 % | 25-40 % |
| highrise | 14 | 28 % | 32 % | 28 % | 20..35 % | 5380 | 0 % | 100 % | 45 % | 45 % | 35-90 % (7.13) |
| house | 73 | 20 % | 21 % | 19 % | 10..38 % | 832 | 0 % | 100 % | 5 % | 5 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |
| shop | 171 | 23 % | 22 % | 24 % | 11..33 % | 7000 | 0 % | 100 % | 33 % | 33 % | upper 25-40 %, shopfront 50-70 %, upper sill +1 |
| temple | 9 | 14 % | 16 % | 12 % | 7..19 % | 310 | 0 % | 100 % | 10 % | 10 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |
| tower | 4 | 13 % | 15 % | 13 % | 0..27 % | 87 | 0 % | 100 % | 0 % | 0 % | - |
| workshop | 14 | 14 % | 14 % | 15 % | 10..23 % | 174 | 1 % | 99 % | 5 % | 5 % | 15-25 % (cottage), 2x2 15-30 % in 2-wide styles |

## Door rise (DS-45, report only)

Entrance doors of the buildings in the district scenes built; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).

Front doors: rise from the street surface in front.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 171 | 3 | 28 | 137 | 3 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 4 % | 0 | 2 | 168 (98 %) |
| house | 73 | 1 | 15 | 54 | 1 | 0 | 1 | 1 | 0 | 0 | 2 (3 %) | 0 % | 0 | 5 | 70 (96 %) |
| workshop | 14 | 0 | 1 | 13 | 0 | 0 | 0 | 0 | 0 | 0 | 0 (0 %) | 0 % | 0 | 1 | 14 (100 %) |
| hall | 8 | 0 | 1 | 2 | 1 | 2 | 0 | 0 | 2 | 0 | 2 (25 %) | 0 % | 0 | 7 | 6 (75 %) |
| highrise | 14 | 0 | 1 | 1 | 5 | 1 | 2 | 0 | 4 | 0 | 6 (43 %) | 14 % | 2 | 7 | 8 (57 %) |
| stilted-tower | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 1 (100 %) | 0 % | 0 | 5 | 0 (0 %) |
| temple | 9 | 1 | 1 | 2 | 2 | 0 | 2 | 0 | 0 | 1 | 3 (33 %) | 0 % | 0 | 10 | 5 (56 %) |
| tower | 3 | 0 | 0 | 1 | 0 | 1 | 0 | 1 | 0 | 0 | 1 (33 %) | 0 % | 0 | 5 | 2 (67 %) |

Side and back entrance doors: rise from the ground straight out.

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | landing | run 4+ | max | within DS-45 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
