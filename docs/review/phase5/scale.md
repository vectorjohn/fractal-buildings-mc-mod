# Phase 5 scale check (5.9)

## Now (after TR, ROADS, D3; task p5-tidy)

Same matrix and tool (`scripts/city-bench.sh`, raw output [scale-raw-now.txt](scale-raw-now.txt)), commit `a38e329` (run-phase5
at `da6d904` plus the CLI cell-cap change, which does not touch planning), heavy lock held, 1-minute loadavg 2.6 to 11.9 at the start of
each configuration (5-minute 4 to 7; other agents' Gradle runs overlapped, so +-30 % again). Columns as in the old table.

| Config | Tier, r | Cells | Districts | Median ms | p95 ms | Exact probes / district (median) | Skeleton per cell (median ms) | Whole `plan` (ms) | Alloc per plan |
|---|---|---|---|---|---|---|---|---|---|
| syn 512 | city 256 | 1 | 11 | 2.83 | 8.83 | 3375 | 2.9 | 44 | 53 MiB |
| syn 2048 | metropolis 1024 | 4 | 142 | 5.34 | 14.17 | 8910 | 5.5 | 771 | 1103 MiB |
| syn 4096 | metropolis 2048 | 4 | 506 | 5.65 | 11.88 | 9218 | 18.1 | 3771 | 5283 MiB |
| gate temperate city | city 477 | 1 | 33 | 5.52 | 18.96 | 5001 | 226 (outlier, load) | 686 | 464 MiB |
| gate desert-badlands | city 827 | 1 | 64 | 6.96 | 12.87 | 6687 | 86 | 741 | 477 MiB |
| gate snow | city 478 | 1 | 29 | 6.92 | 11.50 | 4865 | 5.6 | 217 | 193 MiB |
| gate jungle-savanna | city 790 | 1 | 48 | 7.11 | 14.75 | 8356 | 10.5 | 407 | 289 MiB |
| gate border | city 577 | 1 | 45 | 6.98 | 12.70 | 6675 | 7.7 | 412 | 358 MiB |
| gate coast | city 740 | 2 | 56 | 6.00 | 14.70 | 7138 | 6.6 | 466 | 315 MiB |
| gate joined | city 717 | 2 | 61 | 6.55 | 12.68 | 8181 | 189 (outlier) | 987 | 643 MiB |
| gate metropolis mixed | metropolis 1126 | 4 | 114 | 5.48 | 11.24 | 6506 | 6.5 | 787 | 690 MiB |
| gate metropolis temperate | metropolis 1430 | 8 | 220 | 6.40 | 13.07 | 8869 | 4.8 | 1685 | 1519 MiB |
| world dump cell 2,-6 | metropolis 1430 | 8 | 211 | 5.53 | 12.54 | 5568 | 5.0 | 1457 | 1675 MiB |

Peak RSS with scenes 470 to 1350 MiB, without 211 to 1012 MiB; the 4096^2 city still plans in `-Xmx64m` (5.3 ms median, 3.0 s).
Skeleton cache sweep unchanged in shape (capacity 4 is enough: 5.8 / 5.8 ms; capacity 1 is 26.7 / 36.9 ms, 2 is 6.7 / 9.9 ms).
Region cache capacity 4 to 8192: 5.3 to 7.5 ms. Not re-measured by hand: the scene-per-district and scenes JSON columns (the raw file has them).

**What changed against the old table.** Median district plan+brief on the recorded gate windows went from 0.7 to 2.0 ms to
5.5 to 7.1 ms (about 4 x), and median exact probes per district from 235 to 2,950 to 4,900 to 8,900, so the bar "under 1,000" is now
missed on **every** gate city (it was missed on 3 of 9 plus synthetic before). The 1.5 x bar (2048^2 against 512^2) still holds on the gate windows
(gate metropolis 1430: 6.40 ms
against gate city 477: 5.52 ms, 1.16 x) but not on synthetic noise (5.34 / 2.83 = 1.9 x with scenes in the JVM, 5.53 / 2.11 = 2.6 x without,
because the 512^2 city is only 11 districts of mostly small ones; read the gate rows). Skeleton per cell (2 to 11 ms) and memory are unchanged.
Whole-city plan time rose 3 to 5 x on the gate cities (77 to 145 ms before, 217 to 987 ms now) because it has 20 to 60 % more districts and each one is dearer.

**Which merge.** Measured one gate city (`gate-temperate-city`, `--scenes 0`, 6 timed reps, heavy lock, loadavg 3.4 to 7.5) at three commits
of `run-phase5`:

| Commit | Districts | Lots | Median ms | Exact probes / district (median) |
|---|---|---|---|---|
| `7b0214c` SC merged (baseline) | 27 | 194 | 1.91 | 493 |
| `e64024c` TR (terrain response) | 28 | 240 | 6.35 | 5,636 |
| `94cb769` ROADS | 33 | 282 | 8.00 | 5,501 |
| `da6d904` D3 (full bench above, with scenes) | 33 | n/a | 5.52 | 5,001 |

**TR (`e64024c`) is the step**: roughly 11 x the probes and 3.3 x the time in one commit; ROADS and D3 add nothing. The likely cause is TR's
fuller hill mask (urban ground up to 3 in 4, was 1 in 2): `Districts.plan` memoises `floorY` per column of the district's built area, and a mask
that admits more of the hillside makes the built area (and lots: 194 to 240) much bigger. Not isolated by toggling the mask alone (cheap
to do for whoever tunes it: the constant is in the TR log). Live, 5,000 exact probes per district is 0.75 to 1.9 s per district with
`getFirstFreeHeight` (about 0.1 s with the sampled surface level), so 6.5/6.7 must lean on the coarse height grid.

## Before TR, ROADS and D3 (old table, SC's measurement at `9bf001f`)

Single-threaded, outside the game, `fractal-cli city-bench` (all steps in
[the log](../../log/2026-10-21-p5-sc-scale.md)). Matrix and sweeps: `scripts/city-bench.sh`; raw output
of this run: [scale-raw.txt](scale-raw.txt). Measured at branch commit `9bf001f` (run-phase5 merged at `9fbee73`),
heavy lock held, loadavg 1-minute value between 3.7 and 10.7 at the start of each configuration (the 5/10/15 minute
averages were 9 to 10 and 13 to 15: other agents were still busy, so absolute times carry about +-30 %).
Each row: 2 to 15 warm-up repetitions discarded, then 5 to 25 timed repetitions (every district of every repetition is a sample).
"Synthetic" is `noise:7:64:20:200` terrain (a costly function); "gate" rows plan the T2 recorded windows with `--site`;
"world" is `--cell 2,-6` on the coarse world dump (the 8-cell metropolis).

## Per district (plan + brief, ms) and probes

| Config | Tier, r | Cells | Districts | Median ms | p95 ms | ms per lot (mean) | Exact probes / district (median) | Skeleton per cell (median ms) | Region decision, cold (ms) |
|---|---|---|---|---|---|---|---|---|---|
| syn 512 | city 256 | 1 | 9 | 3.09 | 5.33 | 0.17 | 5926 | 1.7 | 1.6 |
| syn 2048 | metropolis 1024 | 4 | 131 | 5.52 | 13.29 | 0.24 | 9474 | 5.9 | 2.3 |
| syn 4096 (cell size 2048) | metropolis 2048 | 4 | 484 | 5.59 | 10.97 | 0.22 | 11703 | 19.9 | 1.0 |
| gate temperate city | city 477 | 1 | 27 | 1.36 | 4.93 | 0.29 | 493 | 7.2 | 0.7 |
| gate desert-badlands | city 827 | 1 | 54 | 0.70 | 4.10 | 0.25 | 235 | 10.6 | 0.8 |
| gate snow | city 478 | 1 | 23 | 1.19 | 3.70 | 0.33 | 283 | 4.5 | 0.7 |
| gate jungle-savanna | city 790 | 1 | 42 | 1.48 | 5.29 | 0.29 | 1562 | 8.1 | 0.7 |
| gate border | city 577 | 1 | 40 | 1.25 | 5.01 | 0.21 | 896 | 7.7 | 0.8 |
| gate coast | city 740 | 2 | 48 | 1.13 | 5.60 | 0.28 | 540 | 5.7 | 0.7 |
| gate joined | city 717 | 2 | 52 | 2.01 | 4.30 | 0.30 | 1982 | 7.0 | 0.9 |
| gate metropolis mixed | metropolis 1126 | 4 | 107 | 1.78 | 6.35 | 0.25 | 1983 | 6.1 | 0.9 |
| gate metropolis temperate | metropolis 1430 | 8 | 208 | 1.67 | 6.38 | 0.21 | 2534 | 4.6 | 0.7 |
| world dump cell 2,-6 | metropolis 1430 | 8 | 202 | 1.92 | 6.59 | 0.20 | 2952 | 4.7 | 2.8 |

Whole `CityPlanner.plan` (skeletons, every district, whole-city rules), same runs: 35 ms (syn 512), 887 ms (syn 2048),
2.8 s (syn 4096), 77 to 145 ms for the 1-cell gate cities, 368 ms and 653 ms for the two gate metropolises, 689 ms (world 2,-6).
Without scenes (separate JVMs, `--scenes 0`): syn 512 / 2048 / 4096 median 4.45 / 4.91 / 5.45 ms per district, metropolis gate 1.84 ms.

## Memory

| Config | Allocated by one `CityPlanner.plan` | per district | Peak RSS, with 4 scenes | Peak RSS, no scenes | Settlement JSON | District JSON (all) | Scene JSON (avg) |
|---|---|---|---|---|---|---|---|
| syn 512 | 41 MiB | 4.7 MiB | 696 MiB | 203 MiB | 13 KiB | 50 KiB | 1.4 MiB |
| syn 2048 | 874 MiB | 6.8 MiB | 1082 MiB | 610 MiB | 149 KiB | 972 KiB | 3.7 MiB |
| syn 4096 | 4397 MiB | 9.1 MiB | 1233 MiB | 1071 MiB | 566 KiB | 3.5 MiB | 3.1 MiB |
| gate metropolis temperate | 844 MiB | 4.1 MiB | 659 MiB | 628 MiB | 288 KiB | 895 KiB | 1.6 MiB |

Allocation is churn (garbage), not retention: the heap retained by a finished plan is under 5 MiB even for 484 districts
(under the 1 MiB noise of a forced GC for most), and **the 4096^2 city (484 districts, 11,160 lots) plans in a 64 MiB heap**
(`-Xmx64m`; 128, 256 and 512 MiB give the same 2.5 to 2.9 s). Peak RSS in the table is the default 16 GiB-max heap growing lazily, not a need.

## Bounded caches (MC2 `BoundedCache`), no scenes

| Skeleton cache capacity (`SkeletonSource.Direct`, default 64) | syn 2048 (4 cells): median ms / total plan ms / allocated | world 2,-6 (8 cells): median ms / total plan ms / allocated |
|---|---|---|
| 1 | 14.7 / 1967 / 4484 MiB | 16.5 / 3460 / 5598 MiB |
| 2 | 5.7 / 1063 / 1933 MiB | 5.9 / 2075 / 2740 MiB |
| 4 | 4.7 / 647 / 874 MiB | 2.6 / 771 / 1020 MiB |
| 64 | 5.3 / 728 / 874 MiB | 1.9 / 721 / 908 MiB |

| Region cache capacity (`FieldRegionView`, default 8192 per memo), world 2,-6 | median ms per district | total plan ms | region decision (cold) |
|---|---|---|---|
| 4 | 1.83 | 696 | 3.4 ms |
| 64 | 2.02 | 736 | 2.3 ms |
| 1024 | 1.81 | 677 | 2.4 ms |
| 8192 | 1.98 | 729 | 2.4 ms |

A district reads the skeletons of its own cell and the neighbours it touches, so a skeleton cache of 4 is already enough to
hold the working set of a district; capacity 1 or 2 rebuilds skeletons per district (total plan time 2.7 x to 4.8 x at 1, 1.5 x to 2.9 x at 2,
allocation 2 x to 6 x). Region caches matter only while resolving the first cell: the cold decision does not change with capacity 4 to 8192.
Capacity changes speed only; byte-identity with evicting caches is `MultiCellCityTest`'s job.

## Against the bars (plan 5.9 / step SC)

- Median district plan time at 2048^2 and multi-cell within 1.5 x of 512^2: **met on the recorded gate terrain** (city 477: 1.36 ms;
  metropolis 1430, 8 cells: 1.67 ms, 1.2 x; world 2,-6: 1.92 ms, 1.4 x). On synthetic noise 512 -> 2048 -> 4096 the ratio was
  1.8 x with scenes in the JVM and 1.1 x / 1.2 x in the no-scenes runs, so it depends on JIT state and district mix; per lot the
  cost is flat (0.17 / 0.24 / 0.22 ms). A district costs about what is in it (lots, probed columns), not how big the city is.
- Skeleton per cell under 50 ms: **met** (1.7 to 10.6 ms at 1024 cell size; 20 to 32 ms at a 2048 cell size).
- Exact probes per district plan under 1,000: **met in 6 of 9 gate cities (235 to 896 median)**; **not met** for the jungle city (1562),
  both metropolises (about 2000 to 2500), the joined city (1982), the world dump (2952) and every synthetic-noise run (5,900 to 11,700).
  p95 is several times the median. Mean (not median) is 1.7k to 4.4k on gates. Most of it is `Districts.plan` memoising `floorY` and `waterDepth`
  per column of the district's built area; scene probes (the building generator's, off the lane) are 70,000 to 990,000 per scene
  (median 100,000 to 200,000), i.e. each scene costs 15 to 66 ms planning-side.

Live translation with S6's figures (exact height 0.15 to 0.38 ms through `getFirstFreeHeight`; 6 to 28 us through the sampled
`chunk_surface_level`; coarse probe about 17 us; biome 4 us): a gate-city district of ~500 exact probes costs 75 to 190 ms live (3 to 14 ms with the
sampled surface level); a metropolis district of ~2,500 costs 0.4 to 1.0 s (15 to 70 ms with the sampled level). Coarse and biome probes
(median ~600 and 0 to 200 per district) add ~10 to 15 ms. A scene of 100,000 exact probes would be 15 to 38 s with `getFirstFreeHeight`,
so scenes must not probe exactly per column live: that is a finding for 6.5 and 6.7 (use the sampled surface level or a coarse
height grid per lot).
