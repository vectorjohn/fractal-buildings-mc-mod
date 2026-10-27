# SHOPLOW S0 core check against the baseline

The core `DoorRise` check (`core/check/DoorRise.java`, printed as `## Door rise (DS-45, report only)` by `check` and
`city-check`) against [baseline.md](baseline.md), measured 2026-10-26 on the S0 core branch (load average about 12).
Runs: `city-check --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:999`, plus `--seeds 12345 --terrain
recorded:fixtures/terrain/w12345-river-s8.json.gz --sites at:-900,-3500,town,300` and `--seeds 1 --terrain
recorded:fixtures/terrain/fly-1-hill-town.json.gz --sites at:-1536,4800,town,418`, each `--scenes auto:999 --door-rise`; the three
front-door tables summed by kind.

Result: **every kind matches the baseline's first table exactly** (doors, the nine rise buckets, 4+ count, run 4+, max):

| kind | doors | <= -3 | -2..0 | 1 | 2 | 3 | 4 | 5 | 6-8 | 9+ | 4+ | run 4+ | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| shop | 2253 | 59 | 326 | 605 | 602 | 372 | 195 | 59 | 24 | 11 | 289 | 64 | 15 |
| market | 567 | 1 | 10 | 2 | 14 | 18 | 55 | 129 | 305 | 33 | 522 | 554 | 16 |
| house | 4600 | 138 | 794 | 1293 | 959 | 559 | 319 | 205 | 252 | 81 | 857 | 235 | 22 |
| workshop | 1186 | 34 | 197 | 289 | 233 | 150 | 106 | 75 | 83 | 19 | 283 | 115 | 14 |
| block | 37 | 0 | 1 | 3 | 1 | 3 | 9 | 9 | 7 | 4 | 29 | 28 | 11 |
| hall | 18 | 1 | 0 | 3 | 5 | 2 | 1 | 0 | 5 | 1 | 7 | 7 | 15 |
| highrise | 722 | 18 | 55 | 100 | 141 | 104 | 76 | 61 | 117 | 50 | 304 | 230 | 20 |
| stilted-tower | 27 | 1 | 0 | 0 | 1 | 0 | 2 | 6 | 11 | 6 | 25 | 26 | 19 |
| temple | 9 | 1 | 0 | 2 | 2 | 1 | 1 | 0 | 0 | 2 | 3 | 2 | 10 |
| tower | 160 | 9 | 22 | 34 | 28 | 25 | 15 | 9 | 13 | 5 | 42 | 13 | 12 |

The new "within DS-45" column is a little stricter than the baseline headline, which counted rise only: it also fails a
door whose measured way has a straight run over 3 risers. Shops 1531 (baseline 1533), houses 3585 (3605), workshops 930
(945). Market halls count 1 (baseline 26): the baseline gave them the shop limit on rise alone; with the run rule only 1
passes.
