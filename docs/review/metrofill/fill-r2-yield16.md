# city-check

gate-cut > 4 (EXIT gates whose street end is more than that below or above the ground; report only, fix is 7.19): 2

9 sites (scenes: auto:0), 0 failing, 0 violations

Design warnings (high-rise, door-rise beyond the planner's limit, flight-landings and proposed checks, not violations; 9 sites): core-fill 9; edge-taper 13; gate-cut 2; road-hierarchy 5; road-sliver 25; 

| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | s1_m10 | city | 477 | 1 | 33 | 313 | 0 | 100 | 0 | 0 | 4531 |
| gate-desert-badlands | s6_m7 | city | 827 | 1 | 64 | 763 | 0 | 100 | 0 | 0 | 5928 |
| gate-snow-city | s4_0 | city | 478 | 1 | 29 | 285 | 0 | 100 | 0 | 0 | 4260 |
| gate-jungle-savanna | sm1_m4 | city | 790 | 1 | 48 | 588 | 0 | 100 | 0 | 0 | 5667 |
| gate-border-desert-jungle | s7_m8 | city | 577 | 1 | 45 | 604 | 0 | 100 | 0 | 0 | 4852 |
| gate-coast-city | sm8_m4 | city | 740 | 2 | 56 | 686 | 0 | 100 | 0 | 0 | 5654 |
| gate-metropolis-temperate | s2_m6 | metropolis | 1430 | 8 | 220 | 3740 | 0 | 100 | 0 | 0 | 12232 |
| gate-metropolis-mixed | s2_m2 | metropolis | 1126 | 4 | 114 | 1479 | 0 | 100 | 0 | 0 | 7649 |
| gate-joined-city | s8_0 | city | 717 | 2 | 61 | 656 | 0 | 100 | 0 | 0 | 7780 |

Terrain response (urban % = columns the mask kept of those inside the footprint that are dry; cut and fill in thousands of blocks):

| site | urban % | lots | empty:steep | cut k | fill k | steep roads | bridges | quays | quay lots |
|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | 91 | 313 | 15 | 395 | 1204 | 63 | 29 | 14 | 8 |
| gate-desert-badlands | 73 | 763 | 61 | 611 | 1852 | 79 | 102 | 36 | 48 |
| gate-snow-city | 70 | 285 | 35 | 205 | 639 | 37 | 33 | 8 | 8 |
| gate-jungle-savanna | 83 | 588 | 53 | 572 | 1750 | 91 | 86 | 25 | 18 |
| gate-border-desert-jungle | 90 | 604 | 31 | 443 | 1266 | 61 | 80 | 29 | 25 |
| gate-coast-city | 96 | 686 | 39 | 517 | 1588 | 61 | 66 | 27 | 23 |
| gate-metropolis-temperate | 93 | 3740 | 150 | 3637 | 11359 | 399 | 574 | 203 | 255 |
| gate-metropolis-mixed | 97 | 1479 | 96 | 1559 | 4817 | 194 | 251 | 42 | 54 |
| gate-joined-city | 92 | 656 | 69 | 682 | 2060 | 102 | 121 | 44 | 40 |

## Fill (DS-47, report only)

Dry columns in the site disk by what covers them (%): road, built lot, plaza, unbuilt lot (`empty:*`), empty. Frontage = share of the non-road columns beside a road that are lot or plaza. Core is density 70+, middle 50-69, edge under 50.

### Band core

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 154 | 13.8 | 27.0 | 1.6 | 8.3 | 49.2 | 45.3 |
| gate-desert-badlands | 300 | 10.8 | 25.4 | 0.7 | 2.9 | 60.1 | 49.1 |
| gate-snow-city | 56 | 10.6 | 18.2 | 0.7 | 2.7 | 67.8 | 48.2 |
| gate-jungle-savanna | 227 | 12.7 | 21.9 | 0.4 | 3.9 | 61.1 | 47.9 |
| gate-border-desert-jungle | 122 | 11.4 | 31.1 | 1.9 | 3.7 | 51.9 | 65.8 |
| gate-coast-city | 253 | 12.8 | 28.0 | 1.0 | 7.0 | 51.2 | 52.2 |
| gate-metropolis-temperate | 865 | 17.0 | 34.4 | 0.5 | 5.4 | 42.7 | 54.0 |
| gate-metropolis-mixed | 867 | 13.2 | 31.7 | 0.7 | 4.3 | 50.1 | 60.0 |
| gate-joined-city | 208 | 13.7 | 16.8 | 1.5 | 6.6 | 61.4 | 31.8 |

### Band middle

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 62 | 7.5 | 21.6 | 0.0 | 1.1 | 69.7 | 61.3 |
| gate-desert-badlands | 237 | 8.9 | 15.4 | 0.0 | 4.7 | 70.9 | 38.9 |
| gate-snow-city | 103 | 4.0 | 9.8 | 0.0 | 3.3 | 82.9 | 58.8 |
| gate-jungle-savanna | 199 | 8.1 | 15.6 | 0.4 | 3.7 | 72.1 | 45.8 |
| gate-border-desert-jungle | 146 | 6.6 | 21.6 | 0.0 | 1.9 | 69.9 | 62.3 |
| gate-coast-city | 160 | 7.6 | 17.5 | 0.0 | 2.3 | 72.6 | 57.1 |
| gate-metropolis-temperate | 880 | 15.7 | 30.1 | 0.6 | 1.7 | 51.8 | 59.2 |
| gate-metropolis-mixed | 215 | 11.8 | 23.1 | 0.0 | 2.2 | 62.8 | 56.1 |
| gate-joined-city | 213 | 10.4 | 18.3 | 0.2 | 7.1 | 64.0 | 46.4 |

### Band edge

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 373 | 4.5 | 6.1 | 0.4 | 0.8 | 88.2 | 29.4 |
| gate-desert-badlands | 1068 | 2.0 | 3.6 | 0.0 | 1.6 | 92.8 | 33.0 |
| gate-snow-city | 414 | 4.2 | 8.1 | 0.0 | 2.4 | 85.4 | 45.0 |
| gate-jungle-savanna | 612 | 4.5 | 7.1 | 0.0 | 2.5 | 85.9 | 36.1 |
| gate-border-desert-jungle | 500 | 4.5 | 12.2 | 0.0 | 2.7 | 80.5 | 53.7 |
| gate-coast-city | 631 | 3.7 | 8.5 | 0.0 | 1.0 | 86.8 | 49.7 |
| gate-metropolis-temperate | 2595 | 6.3 | 10.6 | 0.1 | 1.8 | 81.3 | 45.3 |
| gate-metropolis-mixed | 803 | 5.5 | 10.3 | 0.0 | 3.1 | 81.1 | 48.7 |
| gate-joined-city | 709 | 4.1 | 7.3 | 0.1 | 2.6 | 86.0 | 38.4 |

### Ring 0-0.25

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 39 | 18.3 | 33.5 | 2.5 | 0.1 | 45.6 | 44.6 |
| gate-desert-badlands | 127 | 7.7 | 16.9 | 0.5 | 1.9 | 73.1 | 43.1 |
| gate-snow-city | 31 | 6.4 | 2.9 | 1.2 | 0.0 | 89.5 | 14.0 |
| gate-jungle-savanna | 116 | 11.6 | 16.4 | 0.6 | 2.4 | 69.0 | 40.5 |
| gate-border-desert-jungle | 55 | 12.1 | 24.6 | 2.8 | 5.4 | 55.0 | 53.5 |
| gate-coast-city | 101 | 15.7 | 24.6 | 1.4 | 10.0 | 48.3 | 41.1 |
| gate-metropolis-temperate | 345 | 16.0 | 27.5 | 0.6 | 5.3 | 50.6 | 47.4 |
| gate-metropolis-mixed | 175 | 16.5 | 23.8 | 1.2 | 4.5 | 54.1 | 49.7 |
| gate-joined-city | 65 | 16.5 | 18.6 | 3.6 | 5.0 | 56.2 | 28.5 |

### Ring 0.25-0.5

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 122 | 11.8 | 20.4 | 1.4 | 10.4 | 56.1 | 37.5 |
| gate-desert-badlands | 362 | 7.5 | 16.6 | 0.4 | 2.7 | 72.7 | 46.7 |
| gate-snow-city | 123 | 6.6 | 15.2 | 0.0 | 2.8 | 75.3 | 57.9 |
| gate-jungle-savanna | 321 | 8.5 | 15.8 | 0.1 | 3.3 | 72.3 | 47.0 |
| gate-border-desert-jungle | 169 | 7.1 | 23.8 | 0.5 | 2.5 | 66.1 | 68.2 |
| gate-coast-city | 197 | 10.7 | 27.4 | 0.6 | 4.5 | 56.8 | 56.5 |
| gate-metropolis-temperate | 1000 | 14.4 | 29.4 | 0.4 | 3.8 | 52.0 | 52.6 |
| gate-metropolis-mixed | 525 | 12.2 | 32.2 | 0.6 | 5.1 | 49.9 | 59.0 |
| gate-joined-city | 257 | 11.8 | 17.6 | 0.4 | 6.3 | 63.9 | 36.8 |

### Ring 0.5-0.75

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 173 | 8.7 | 17.5 | 0.7 | 0.9 | 72.2 | 45.3 |
| gate-desert-badlands | 548 | 5.8 | 10.5 | 0.0 | 3.1 | 80.6 | 38.0 |
| gate-snow-city | 177 | 4.5 | 8.2 | 0.0 | 2.6 | 84.7 | 46.6 |
| gate-jungle-savanna | 413 | 6.8 | 11.4 | 0.2 | 3.7 | 77.9 | 39.6 |
| gate-border-desert-jungle | 251 | 6.7 | 18.5 | 0.0 | 2.5 | 72.3 | 59.2 |
| gate-coast-city | 295 | 6.2 | 13.2 | 0.0 | 1.5 | 79.0 | 51.1 |
| gate-metropolis-temperate | 1396 | 13.4 | 22.9 | 0.5 | 1.6 | 61.5 | 52.7 |
| gate-metropolis-mixed | 551 | 8.8 | 20.5 | 0.3 | 1.6 | 68.9 | 60.2 |
| gate-joined-city | 390 | 6.6 | 10.7 | 0.2 | 4.7 | 77.9 | 40.7 |

### Ring 0.75-1

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 254 | 2.3 | 3.7 | 0.0 | 0.9 | 93.1 | 34.0 |
| gate-desert-badlands | 568 | 1.1 | 2.1 | 0.0 | 1.4 | 95.4 | 32.7 |
| gate-snow-city | 242 | 3.8 | 8.1 | 0.0 | 2.7 | 85.3 | 44.7 |
| gate-jungle-savanna | 188 | 2.0 | 4.1 | 0.0 | 1.4 | 92.5 | 44.1 |
| gate-border-desert-jungle | 292 | 3.6 | 10.3 | 0.0 | 2.5 | 83.6 | 51.6 |
| gate-coast-city | 450 | 2.7 | 7.7 | 0.0 | 0.9 | 88.7 | 57.9 |
| gate-metropolis-temperate | 1599 | 3.8 | 8.0 | 0.0 | 1.7 | 86.4 | 52.9 |
| gate-metropolis-mixed | 635 | 6.6 | 13.3 | 0.0 | 3.7 | 76.4 | 52.3 |
| gate-joined-city | 418 | 3.1 | 6.4 | 0.0 | 2.2 | 88.3 | 44.2 |

### All

| site | land k | road | lot | plaza | unbuilt | empty | frontage |
|---|---|---|---|---|---|---|---|
| gate-temperate-city | 589 | 7.2 | 13.2 | 0.6 | 2.8 | 76.1 | 41.1 |
| gate-desert-badlands | 1605 | 4.7 | 9.4 | 0.1 | 2.3 | 83.5 | 41.4 |
| gate-snow-city | 573 | 4.8 | 9.4 | 0.1 | 2.6 | 83.2 | 47.8 |
| gate-jungle-savanna | 1038 | 7.0 | 12.0 | 0.2 | 3.0 | 77.8 | 42.6 |
| gate-border-desert-jungle | 768 | 6.0 | 17.0 | 0.3 | 2.7 | 74.0 | 58.6 |
| gate-coast-city | 1043 | 6.5 | 14.6 | 0.3 | 2.6 | 76.0 | 52.2 |
| gate-metropolis-temperate | 4340 | 10.3 | 19.3 | 0.3 | 2.5 | 67.6 | 52.1 |
| gate-metropolis-mixed | 1886 | 9.7 | 21.6 | 0.4 | 3.6 | 64.8 | 56.4 |
| gate-joined-city | 1130 | 7.0 | 11.1 | 0.4 | 4.2 | 77.3 | 38.4 |

### Per site

| site | tier | arterial+main % | street km | slivers | sliver area | empty gaps <=12 % | dead ends | block lots | core lot depth (median, n) |
|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | city | 30.9 | 8.6 | 4 | 740 | 10.5 | 38 | 1 | hall 26 (1), highrise 22 (23), house 10 (10), shop 18 (85), workshop 15 (7) |
| gate-desert-badlands | city | 24.3 | 16.9 | 6 | 2031 | 11.6 | 99 | 0 | hall 25 (1), highrise 25 (18), house 9 (29), shop 18 (234), workshop 14 (6) |
| gate-snow-city | city | 27.7 | 6.0 | 0 | 0 | 9.5 | 20 | 0 | hall 25 (1), highrise 24 (4), house 8 (8), shop 16 (21), temple 27 (1) |
| gate-jungle-savanna | city | 35.1 | 13.0 | 2 | 954 | 10.0 | 77 | 0 | hall 26 (1), highrise 23 (12), house 10 (19), shop 17 (161), temple 27 (1), workshop 13 (4) |
| gate-border-desert-jungle | city | 20.4 | 10.1 | 1 | 255 | 5.1 | 45 | 0 | hall 26 (1), highrise 23 (16), house 10 (6), shop 17 (108), temple 26 (1), workshop 12 (5) |
| gate-coast-city | city | 28.3 | 13.9 | 2 | 712 | 12.9 | 77 | 0 | hall 25 (1), highrise 23 (35), house 10 (25), shop 17 (164), temple 26 (1), workshop 12 (7) |
| gate-metropolis-temperate | metropolis | 31.7 | 82.7 | 16 | 4718 | 9.7 | 372 | 1 | hall 25 (2), highrise 23 (213), house 10 (73), shop 17 (566), temple 27 (1), workshop 13 (15) |
| gate-metropolis-mixed | metropolis | 33.4 | 34.2 | 5 | 1171 | 9.0 | 183 | 1 | block 77 (1), hall 26 (3), highrise 22 (222), house 10 (63), shop 17 (391), workshop 14 (20) |
| gate-joined-city | city | 37.6 | 17.3 | 4 | 1296 | 13.0 | 82 | 0 | hall 26 (1), highrise 24 (10), house 8 (14), shop 17 (101), temple 26 (1), workshop 15 (4) |

### Sliver pairs

Columns of sliver strips by the kinds of the two roads either side (kinds in alphabetical order), per site in the order of the table above.

| pair | gate-temperate-city | gate-desert-badlands | gate-snow-city | gate-jungle-savanna | gate-border-desert-jungle | gate-coast-city | gate-metropolis-temperate | gate-metropolis-mixed | gate-joined-city | all |
|---|---|---|---|---|---|---|---|---|---|---|
| arterial/lane | 320 | 937 | 0 | 0 | 0 | 0 | 188 | 0 | 412 | 1857 |
| arterial/main | 0 | 0 | 0 | 0 | 0 | 0 | 617 | 0 | 378 | 995 |
| arterial/quay | 156 | 0 | 0 | 221 | 0 | 0 | 0 | 0 | 96 | 473 |
| lane/lane | 0 | 0 | 0 | 0 | 0 | 0 | 190 | 0 | 0 | 190 |
| lane/main | 0 | 0 | 0 | 0 | 0 | 407 | 430 | 170 | 6 | 1013 |
| lane/quay | 0 | 0 | 0 | 0 | 62 | 0 | 0 | 0 | 0 | 62 |
| lane/street | 261 | 0 | 0 | 0 | 193 | 0 | 987 | 144 | 90 | 1675 |
| main/main | 0 | 0 | 0 | 0 | 0 | 0 | 15 | 10 | 0 | 25 |
| main/quay | 0 | 0 | 0 | 0 | 0 | 0 | 246 | 179 | 314 | 739 |
| main/street | 3 | 396 | 0 | 708 | 0 | 280 | 1170 | 668 | 0 | 3225 |
| quay/quay | 0 | 42 | 0 | 0 | 0 | 6 | 170 | 0 | 0 | 218 |
| quay/street | 0 | 215 | 0 | 10 | 0 | 19 | 705 | 0 | 0 | 949 |
| street/street | 0 | 441 | 0 | 15 | 0 | 0 | 0 | 0 | 0 | 456 |

## Lot-loss guard (DS-47, vs `--fill-baseline`)

Districts whose built lot count changed. A drop in density 50 and up is a LOSS (exit 1).

| site | district | density | before | after | |
|---|---|---|---|---|---|
| gate-jungle-savanna | dm3_m23 | 76 | 7 | 10 |  |
| gate-jungle-savanna | dm3_m24 | 74 | 14 | 13 | LOSS |
| gate-jungle-savanna | dm4_m25 | 83 | 22 | 9 | LOSS |
| gate-jungle-savanna | dm5_m25 | 85 | 23 | 22 | LOSS |
| gate-coast-city | dm44_m26 | 54 | 22 | 16 | LOSS |
| gate-coast-city | dm46_m23 | 85 | 19 | 16 | LOSS |
| gate-coast-city | dm47_m23 | 85 | 8 | 5 | LOSS |
| gate-metropolis-mixed | d10_m16 | 56 | 16 | 11 | LOSS |
| gate-metropolis-mixed | d10_m17 | 35 | 37 | 36 |  |
| gate-metropolis-mixed | d11_m16 | 68 | 52 | 50 | LOSS |
| gate-metropolis-mixed | d11_m17 | 54 | 53 | 47 | LOSS |
| gate-metropolis-mixed | d12_m17 | 63 | 48 | 44 | LOSS |
| gate-metropolis-mixed | d13_m13 | 100 | 30 | 26 | LOSS |
| gate-metropolis-mixed | d13_m17 | 74 | 16 | 10 | LOSS |

12 loss(es).
