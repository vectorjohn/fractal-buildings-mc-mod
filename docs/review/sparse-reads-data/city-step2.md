# sparse reads: city pipeline

Reads are distinct columns / distinct 16x16 chunks asked of the real terrain, by phase: J = `warm` (street network and junction field), Q = `warmQuays`, T = the rest (all tiles' lots, ground, buildings).

| window | setup | J cols | J chunks | Q cols | Q chunks | T cols | T chunks | tiles | lots | builds | steps>1 tiles | steps>3 tiles | road cells | dy>2 | dy>4 | mean abs dy | ms J | ms Q | ms T |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| gate-border-desert-jungle | exact | 9703 | 656 | 81077 | 4643 | 255301 | 13 | 45 | 679 | 597 | 1 | 0 | 50323 | 7179 | 4207 | 1.57 | 88 | 653 | 9280 |
| gate-border-desert-jungle | checker | 14854 | 1130 | 84004 | 4169 | 126311 | 13 | 45 | 679 | 598 | 3 | 0 | 50320 | 7458 | 4144 | 1.58 | 67 | 513 | 10327 |
| gate-border-desert-jungle | third | 9927 | 1135 | 125831 | 4165 | 78401 | 13 | 45 | 690 | 606 | 5 | 0 | 50104 | 7572 | 4122 | 1.59 | 123 | 515 | 10082 |
| gate-border-desert-jungle | checker-s3 | 14946 | 1130 | 84100 | 4169 | 129056 | 13 | 45 | 679 | 598 | 3 | 0 | 50320 | 7458 | 4144 | 1.58 | 106 | 443 | 10349 |
| gate-border-desert-jungle | third-s3 | 9965 | 1135 | 126329 | 4165 | 79515 | 13 | 45 | 690 | 606 | 5 | 0 | 50104 | 7531 | 4073 | 1.58 | 54 | 477 | 9953 |
| gate-coast-city | exact | 13681 | 994 | 84254 | 5516 | 331133 | 121 | 56 | 803 | 707 | 9 | 1 | 72248 | 11755 | 4700 | 1.39 | 104 | 486 | 10732 |
| gate-coast-city | checker | 20590 | 1455 | 86813 | 5057 | 169530 | 121 | 56 | 802 | 708 | 11 | 1 | 72259 | 12009 | 4698 | 1.40 | 59 | 268 | 12626 |
| gate-coast-city | third | 13952 | 1463 | 129959 | 5062 | 119360 | 125 | 56 | 806 | 708 | 7 | 2 | 72604 | 12098 | 4698 | 1.41 | 62 | 325 | 12162 |
| gate-coast-city | checker-s3 | 20733 | 1455 | 86900 | 5057 | 173073 | 121 | 56 | 802 | 707 | 11 | 1 | 72259 | 12007 | 4696 | 1.40 | 50 | 374 | 12157 |
| gate-coast-city | third-s3 | 14047 | 1463 | 130508 | 5062 | 121146 | 125 | 56 | 806 | 706 | 7 | 2 | 72678 | 12175 | 4753 | 1.41 | 62 | 680 | 11789 |
| gate-snow-city | exact | 6130 | 445 | 52037 | 2958 | 123684 | 192 | 29 | 317 | 244 | 5 | 1 | 29398 | 5511 | 2194 | 1.51 | 98 | 227 | 3711 |
| gate-snow-city | checker | 9131 | 693 | 53548 | 2710 | 62756 | 192 | 29 | 317 | 244 | 5 | 1 | 29398 | 5579 | 2194 | 1.52 | 38 | 387 | 4818 |
| gate-snow-city | third | 6287 | 694 | 81716 | 2717 | 41069 | 226 | 29 | 315 | 241 | 5 | 1 | 29348 | 5898 | 2229 | 1.55 | 49 | 320 | 4426 |
| gate-snow-city | checker-s3 | 9259 | 693 | 53863 | 2710 | 64827 | 192 | 29 | 317 | 244 | 5 | 1 | 29398 | 5574 | 2194 | 1.52 | 42 | 181 | 4800 |
| gate-snow-city | third-s3 | 6339 | 694 | 82711 | 2717 | 42000 | 226 | 29 | 315 | 241 | 5 | 1 | 29348 | 5886 | 2228 | 1.54 | 105 | 593 | 4061 |
| gate-temperate-city | exact | 8176 | 602 | 56024 | 3090 | 182147 | 383 | 33 | 385 | 321 | 5 | 1 | 50516 | 8975 | 5497 | 1.59 | 1618 | 581 | 13498 |
| gate-temperate-city | checker | 12252 | 921 | 57791 | 2785 | 92993 | 369 | 33 | 384 | 321 | 5 | 1 | 50943 | 9135 | 5503 | 1.59 | 1653 | 547 | 14194 |
| gate-temperate-city | third | 8338 | 910 | 87154 | 2796 | 63093 | 417 | 33 | 381 | 320 | 5 | 1 | 49794 | 9081 | 5386 | 1.60 | 1632 | 653 | 13283 |
| gate-temperate-city | checker-s3 | 12351 | 921 | 57854 | 2785 | 94726 | 369 | 33 | 384 | 321 | 5 | 1 | 50796 | 9128 | 5501 | 1.59 | 1672 | 534 | 14412 |
| gate-temperate-city | third-s3 | 8385 | 910 | 87662 | 2796 | 64106 | 417 | 33 | 381 | 319 | 5 | 1 | 49794 | 9089 | 5415 | 1.61 | 1650 | 679 | 13157 |

## Difference to exact, per window

| window | setup | lots in both | same box+type | builds flipped | front y: mean abs / max / over 1 | junctions in both | level: mean abs / max / over 1 / over 3 | quay columns exact / sparse / both | quay y mean abs on both |
|---|---|---|---|---|---|---|---|---|---|
| gate-border-desert-jungle | checker | 679 | 679 | 1 | 0.02 / 1 / 0 | 52 | 0.10 / 1 / 0 / 0 | 1132 / 1132 / 1132 | 0.03 |
| gate-border-desert-jungle | third | 676 | 526 | 17 | 0.39 / 8 / 53 | 51 | 0.20 / 1 / 0 / 0 | 1132 / 1094 / 1090 | 0.08 |
| gate-border-desert-jungle | checker-s3 | 679 | 679 | 1 | 0.02 / 1 / 0 | 52 | 0.10 / 1 / 0 / 0 | 1132 / 1132 / 1132 | 0.03 |
| gate-border-desert-jungle | third-s3 | 676 | 526 | 17 | 0.39 / 8 / 53 | 51 | 0.20 / 1 / 0 / 0 | 1132 / 1094 / 1090 | 0.08 |
| gate-coast-city | checker | 802 | 777 | 4 | 0.11 / 12 / 13 | 94 | 0.07 / 1 / 0 / 0 | 1159 / 1158 / 1158 | 0.07 |
| gate-coast-city | third | 796 | 639 | 28 | 0.58 / 18 / 77 | 92 | 0.15 / 1 / 0 / 0 | 1159 / 1099 / 1080 | 0.10 |
| gate-coast-city | checker-s3 | 802 | 777 | 3 | 0.11 / 12 / 13 | 94 | 0.07 / 1 / 0 / 0 | 1159 / 1158 / 1158 | 0.07 |
| gate-coast-city | third-s3 | 798 | 659 | 26 | 0.55 / 18 / 69 | 92 | 0.15 / 1 / 0 / 0 | 1159 / 1099 / 1080 | 0.10 |
| gate-snow-city | checker | 317 | 317 | 0 | 0.01 / 1 / 0 | 30 | 0.07 / 1 / 0 / 0 | 477 / 477 / 477 | 0.01 |
| gate-snow-city | third | 314 | 269 | 5 | 0.25 / 6 / 13 | 30 | 0.23 / 1 / 0 / 0 | 477 / 485 / 473 | 0.07 |
| gate-snow-city | checker-s3 | 317 | 317 | 0 | 0.01 / 1 / 0 | 30 | 0.03 / 1 / 0 / 0 | 477 / 477 / 477 | 0.00 |
| gate-snow-city | third-s3 | 314 | 269 | 5 | 0.25 / 6 / 13 | 30 | 0.23 / 1 / 0 / 0 | 477 / 485 / 473 | 0.07 |
| gate-temperate-city | checker | 384 | 352 | 0 | 0.18 / 5 / 16 | 45 | 0.02 / 1 / 0 / 0 | 840 / 839 / 839 | 0.03 |
| gate-temperate-city | third | 374 | 245 | 18 | 0.84 / 27 / 53 | 45 | 0.02 / 1 / 0 / 0 | 840 / 836 / 819 | 0.05 |
| gate-temperate-city | checker-s3 | 384 | 377 | 0 | 0.05 / 4 / 2 | 45 | 0.02 / 1 / 0 / 0 | 840 / 839 / 839 | 0.03 |
| gate-temperate-city | third-s3 | 374 | 245 | 19 | 0.84 / 27 / 53 | 45 | 0.02 / 1 / 0 / 0 | 840 / 836 / 819 | 0.05 |

