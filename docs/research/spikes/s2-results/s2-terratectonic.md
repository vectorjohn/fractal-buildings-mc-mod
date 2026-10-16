# S2 results, mod set `terratectonic`

loadavg at start: 12.55 10.71 12.09 10/4241 148121

fingerprint: biomes=162 terralith_biomes=95 finalDensity.hash=1577109110 chunkSurfaceLevel.class=HolderHolder sea=63 minY=-64 height=384 biomeSource=net.minecraft.world.level.biome.MultiNoiseBiomeSource

## Cost (server thread, single thread, seed 12345, warmed up)

- getBaseHeight(OCEAN_FLOOR_WG): 0.93 ms per column (4000 random columns)
- 16-block grid over 512x512 (1024 columns): 584.05 ms
- coarse screen (chunkSurfaceLevel sampled uncached): 2.49 ms per column
- whole chunk fill+surface (no carvers, no features), for context: 32.18 ms per chunk
- loadavg after: 12.05 10.69 12.06 8/4540 148847 (sink 767922)

## Exact height (predicted getBaseHeight vs real) per seed

`B` = real first-free height after fill + surface rules; `C` = after carvers too. Land = predicted height > sea level (63); ocean = pred <= sea. cols = land columns; `=B` share equal to B; err = B - pred; `B>pred` = surface rules/fill add above prediction.

| seed | windows | land cols | =B % | =A % | =C % | p99 abs(B-pred) | max abs | B>pred cols | max B-pred | min B-pred | C<pred cols | min C-pred |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 12345 | mountain@-1600,1088 coast@192,192 river@-1856,2496 ocean@-832,-1984 land@-2368,-960 land@-2880,-704 land@1984,-2880 | 84338 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1 | mountain@2752,2880 coast@2368,-1600 river@192,960 ocean@-1472,-1728 land@64,-832 land@2240,-2880 land@576,-1088 | 84203 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2026 | mountain@-64,-3008 coast@1088,2496 river@-1728,-64 ocean@-192,2880 land@1600,1216 land@2752,704 land@-704,-704 | 92898 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 424242 | mountain@1344,1984 coast@576,-2624 river@-1984,-960 ocean@2624,-192 land@1728,-1856 land@-1728,1856 land@1600,192 | 68122 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 987654321 | mountain@-576,2880 coast@-2880,2112 river@-1728,960 ocean@-448,-2496 land@-1728,-2880 land@1856,1984 land@-1984,-2624 | 87100 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -5551212 | mountain@704,-1472 coast@-2496,1856 river@2240,-64 ocean@-1216,-2880 land@-2496,1856 land@704,-1984 land@-704,576 | 70734 | 99.88 | 100.00 | 99.88 | 0 | 3 | 0 | 0 | -3 | 82 | -3 |
| 777 | mountain@2752,1088 coast@-1344,1600 river@-3008,832 ocean@576,960 land@2752,960 land@-1728,2240 land@1344,-1728 | 90864 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 31415926 | mountain@-576,2368 coast@1216,1472 river@-192,-1984 ocean@832,704 land@2112,2240 land@-2496,832 land@-2880,2112 | 80269 | 99.77 | 100.00 | 99.77 | 0 | 4 | 0 | 0 | -4 | 183 | -4 |
| 8001 | mountain@1216,-1216 coast@64,704 river@576,-704 ocean@-1600,2880 land@-1984,192 land@2112,-192 land@-1600,448 | 64746 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -99 | mountain@-3008,-1472 coast@1600,-448 river@-1856,-2880 ocean@-1216,1600 land@1728,-2240 land@-320,1856 land@-3008,2752 | 71784 | 95.90 | 100.00 | 95.90 | 4 | 4 | 0 | 0 | -4 | 2943 | -4 |

## Ocean columns (pred <= sea level)

| seed | cols | =B % | B>pred cols | max B-pred | cols with solid above sea (ice) |
|---|---|---|---|---|---|
| 12345 | 30350 | 100.00 | 0 | 0 | 0 |
| 1 | 30485 | 94.80 | 1585 | 99 | 1585 |
| 2026 | 21790 | 100.00 | 0 | 0 | 0 |
| 424242 | 46566 | 100.00 | 0 | 0 | 0 |
| 987654321 | 27588 | 100.00 | 0 | 0 | 0 |
| -5551212 | 43954 | 99.83 | 76 | 34 | 76 |
| 777 | 23824 | 100.00 | 0 | 0 | 0 |
| 31415926 | 34419 | 94.91 | 1752 | 50 | 1752 |
| 8001 | 49942 | 98.28 | 858 | 62 | 858 |
| -99 | 42904 | 91.87 | 3462 | 76 | 3462 |

## Coarse screen (chunkSurfaceLevel at the column) minus real B, land columns

| seed | cols | mean | median | p10 | p90 | stdev | within +-4 after pooled fix | p90 abs after pooled fix |
|---|---|---|---|---|---|---|---|---|
| 12345 | 84338 | -22.63 | -14 | -71 | -10 | 22.40 | 77.19% | 58 |
| 1 | 84203 | -15.06 | -13 | -24 | -8 | 9.10 | 65.15% | 11 |
| 2026 | 92898 | -25.78 | -13 | -91 | -10 | 30.64 | 79.89% | 78 |
| 424242 | 68122 | -23.72 | -14 | -63 | -9 | 22.62 | 61.69% | 50 |
| 987654321 | 87100 | -21.49 | -14 | -59 | -9 | 21.62 | 71.61% | 46 |
| -5551212 | 70734 | -28.95 | -13 | -92 | -10 | 31.66 | 71.00% | 79 |
| 777 | 90864 | -12.51 | -12 | -17 | -8 | 4.32 | 76.84% | 6 |
| 31415926 | 80269 | -15.42 | -14 | -22 | -10 | 6.97 | 76.09% | 9 |
| 8001 | 64746 | -30.53 | -14 | -95 | -9 | 33.26 | 62.01% | 82 |
| -99 | 71784 | -26.92 | -13 | -80 | -10 | 28.05 | 67.56% | 67 |

Pooled median (coarse - B) over all seeds: **-13** (p10 -59, p90 -9, n=795058). Fix = subtract it.

## By window kind (all seeds pooled), land columns

| kind | cols | =B % | p99 abs | max abs | coarse-B median | coarse-B p10 | coarse-B p90 |
|---|---|---|---|---|---|---|---|
| coast | 108850 | 99.96 | 0 | 3 | -10 | -15 | -8 |
| land | 447140 | 99.33 | 0 | 4 | -13 | -16 | -9 |
| mountain | 163840 | 100.00 | 0 | 0 | -56 | -102 | -15 |
| river | 75228 | 99.76 | 0 | 4 | -13 | -15 | -10 |

## What surface rules changed vs the filled column (blocks at the changed top, all seeds, all columns)

- ADDED above fill: block.minecraft.packed_ice: 7127
- ADDED above fill: block.minecraft.snow_block: 606
- REMOVED (non-opaque now) top: block.minecraft.powder_snow: 3234

carver stage error (null if worked): java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.WorldGenRegion.registryAccess()" because "carverBiomeRegion" is null
