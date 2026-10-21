# Phase 5 city-check totals

`fractal-cli city-check` with **no `--ignore`** (every settlement and building rule), run 2026-10-21 on `claude/run-phase5` at
`2e67271` (IRR1 merged; TIDY's later merge `75f8171` changes only the CLI's `--site` cell cap and tests). The ms columns
are wall time on a busy machine (load 3 to 8), not a measurement; see [scale.md](scale.md) for timings.

| Run | Command | Sites | Failing | Violations |
|---|---|---|---|---|
| 9 gate windows | `city-check --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:2` | 9 (18 scenes) | 0 | 0 |
| Synthetic | `city-check --seeds 1..50 --sites random:4 --scenes auto:1` (terrain `noise:3:70:10:300`) | 200 | 0 | 0 |
| World 12345 | `city-check --seeds 12345 --terrain recorded:fixtures/terrain/world-12345-s64.json.gz --sites cells` | 152 | 0 | 0 |
| World 987654321 | same on `world-987654321-s64.json.gz` | 150 | 0 | 0 |

By tier (sites / districts / lots):

| Tier | Synthetic | World 12345 | World 987654321 |
|---|---|---|---|
| hamlet | 57 / 146 / 282 | 57 / 130 / 184 | 43 / 106 / 169 |
| village | 57 / 358 / 1,787 | 48 / 255 / 1,050 | 56 / 299 / 1,191 |
| town | 51 / 816 / 10,741 | 29 / 444 / 3,296 | 35 / 555 / 3,839 |
| city | 27 / 1,644 / 43,067 | 14 / 859 / 9,466 | 13 / 720 / 5,545 |
| metropolis | 8 / 1,265 / 32,076 | 4 / 271 / 2,562 | 3 / 626 / 5,852 |

Real terrain gives far fewer lots per district than synthetic noise (about 11 against 26 for a city): the urban mask, steep
ground, water and the "lots only on streets that reach a live road" rule (ROADS) all cut lots.

## Gate windows (report as written by the CLI)

9 sites (scenes: auto:2), 0 failing, 0 violations

| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | s1_m10 | city | 477 | 1 | 33 | 208 | 2 | 92 | 12 | 0 | 1812 |
| gate-desert-badlands | s6_m7 | city | 827 | 1 | 64 | 457 | 2 | 82 | 42 | 0 | 2055 |
| gate-snow-city | s4_0 | city | 478 | 1 | 29 | 103 | 2 | 62 | 23 | 0 | 1708 |
| gate-jungle-savanna | sm1_m4 | city | 790 | 1 | 48 | 368 | 2 | 91 | 28 | 0 | 1824 |
| gate-border-desert-jungle | s7_m8 | city | 577 | 1 | 45 | 336 | 2 | 81 | 24 | 0 | 1805 |
| gate-coast-city | sm8_m4 | city | 740 | 2 | 56 | 388 | 2 | 81 | 32 | 0 | 2045 |
| gate-metropolis-temperate | s2_m6 | metropolis | 1430 | 8 | 220 | 2355 | 2 | 93 | 88 | 0 | 3640 |
| gate-metropolis-mixed | s2_m2 | metropolis | 1126 | 4 | 114 | 1006 | 2 | 89 | 49 | 0 | 2449 |
| gate-joined-city | s8_0 | city | 717 | 2 | 61 | 514 | 2 | 89 | 38 | 0 | 2379 |

Terrain response (urban % = columns the mask kept of those inside the footprint that are dry; cut and fill in thousands of blocks):

| site | urban % | lots | empty:steep | cut k | fill k | steep roads | bridges | quays | quay lots |
|---|---|---|---|---|---|---|---|---|---|
| gate-temperate-city | 91 | 208 | 4 | 26 | 113 | 24 | 8 | 31 | 19 |
| gate-desert-badlands | 73 | 457 | 29 | 104 | 68 | 43 | 47 | 71 | 32 |
| gate-snow-city | 70 | 103 | 5 | 27 | 15 | 15 | 6 | 17 | 12 |
| gate-jungle-savanna | 83 | 368 | 14 | 100 | 70 | 49 | 49 | 55 | 10 |
| gate-border-desert-jungle | 90 | 336 | 2 | 28 | 53 | 9 | 32 | 51 | 18 |
| gate-coast-city | 96 | 388 | 8 | 44 | 77 | 39 | 35 | 69 | 21 |
| gate-metropolis-temperate | 93 | 2355 | 14 | 375 | 322 | 156 | 356 | 230 | 165 |
| gate-metropolis-mixed | 97 | 1006 | 16 | 153 | 181 | 77 | 145 | 91 | 36 |
| gate-joined-city | 92 | 514 | 27 | 132 | 69 | 37 | 91 | 75 | 32 |
