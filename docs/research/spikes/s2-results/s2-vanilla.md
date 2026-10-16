# S2 results, mod set `vanilla`

loadavg at start: 5.57 6.86 5.73 3/3956 78034

fingerprint: biomes=67 terralith_biomes=0 finalDensity.hash=1270868880 chunkSurfaceLevel.class=HolderHolder sea=63 minY=-64 height=384 biomeSource=net.minecraft.world.level.biome.MultiNoiseBiomeSource

## Cost (server thread, single thread, seed 12345, warmed up)

- getBaseHeight(OCEAN_FLOOR_WG): 0.32 ms per column (4000 random columns)
- 16-block grid over 512x512 (1024 columns): 136.28 ms
- coarse screen (chunkSurfaceLevel sampled uncached): 0.03 ms per column
- whole chunk fill+surface (no carvers, no features), for context: 57.90 ms per chunk
- loadavg after: 5.29 6.78 5.71 3/4011 78164 (sink 607229)

## Exact height (predicted getBaseHeight vs real) per seed

`B` = real first-free height after fill + surface rules; `C` = after carvers too. Land = predicted height > sea level (63); ocean = pred <= sea. cols = land columns; `=B` share equal to B; err = B - pred; `B>pred` = surface rules/fill add above prediction.

| seed | windows | land cols | =B % | =A % | =C % | p99 abs(B-pred) | max abs | B>pred cols | max B-pred | min B-pred | C<pred cols | min C-pred |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 12345 | mountain@-960,960 coast@-960,1472 river@-2368,-1088 ocean@1856,-1600 land@-192,1472 land@-2368,-960 land@-1856,320 | 82188 | 97.93 | 100.00 | 97.16 | 3 | 15 | 0 | 0 | -15 | 2335 | -25 |
| 1 | mountain@-2368,-2624 coast@448,-704 river@2624,-2752 ocean@1984,1728 land@-1472,-1728 land@-2752,-1984 land@960,960 | 80254 | 98.87 | 100.00 | 97.10 | 1 | 5 | 0 | 0 | -5 | 2326 | -26 |
| 2026 | mountain@1856,-1344 coast@576,320 river@2880,1600 ocean@-64,2240 land@1216,1088 land@1600,1216 land@1984,-1472 | 75971 | 98.34 | 100.00 | 96.81 | 1 | 13 | 0 | 0 | -13 | 2426 | -35 |
| 424242 | mountain@320,832 coast@-1216,-3008 river@2624,-192 ocean@-704,-2752 land@-192,320 land@-2368,1216 land@-2880,2880 | 68043 | 99.91 | 100.00 | 98.35 | 0 | 1 | 0 | 0 | -1 | 1120 | -43 |
| 987654321 | mountain@448,1088 coast@-2880,2112 river@832,1472 ocean@832,1472 land@2496,2624 land@-1088,-832 land@1856,1984 | 94475 | 98.70 | 100.00 | 97.75 | 1 | 12 | 0 | 0 | -12 | 2122 | -18 |
| -5551212 | mountain@-3008,2496 coast@-2496,1856 river@-1472,-320 ocean@-3008,-2624 land@-2240,1600 land@-704,576 land@-1216,-2880 | 86842 | 99.14 | 100.00 | 97.94 | 0 | 10 | 0 | 0 | -10 | 1786 | -15 |
| 777 | mountain@2240,-2752 coast@2368,1472 river@2624,-1984 ocean@1344,-192 land@576,960 land@-1984,-192 land@-1984,-2752 | 83126 | 99.34 | 100.00 | 98.29 | 0 | 4 | 0 | 0 | -4 | 1421 | -14 |
| 31415926 | mountain@-1344,960 coast@-2496,832 river@1216,-320 ocean@-1472,-192 land@2112,2240 land@1728,704 land@-1472,-1984 | 85474 | 100.00 | 100.00 | 98.90 | 0 | 0 | 0 | 0 | 0 | 936 | -34 |
| 8001 | mountain@2624,2240 coast@-3008,-320 river@2112,-192 ocean@-1216,-2240 land@-1600,2880 land@-1088,-832 land@-2240,832 | 75300 | 100.00 | 100.00 | 98.86 | 0 | 0 | 0 | 0 | 0 | 861 | -52 |
| -99 | mountain@-1088,-2240 coast@1600,-448 river@2240,-704 ocean@2240,2496 land@1728,-2240 land@1728,-832 land@-1984,448 | 70014 | 98.37 | 100.00 | 97.79 | 1 | 8 | 0 | 0 | -8 | 1548 | -8 |

## Ocean columns (pred <= sea level)

| seed | cols | =B % | B>pred cols | max B-pred | cols with solid above sea (ice) |
|---|---|---|---|---|---|
| 12345 | 32500 | 95.19 | 1564 | 57 | 1564 |
| 1 | 34434 | 100.00 | 0 | 0 | 0 |
| 2026 | 38717 | 100.00 | 0 | 0 | 0 |
| 424242 | 46645 | 100.00 | 0 | 0 | 0 |
| 987654321 | 20213 | 100.00 | 0 | 0 | 0 |
| -5551212 | 27846 | 100.00 | 0 | 0 | 0 |
| 777 | 31562 | 100.00 | 0 | 0 | 0 |
| 31415926 | 29214 | 100.00 | 0 | 0 | 0 |
| 8001 | 39388 | 99.61 | 0 | 0 | 0 |
| -99 | 44674 | 88.50 | 5137 | 60 | 5137 |

## Coarse screen (chunkSurfaceLevel at the column) minus real B, land columns

| seed | cols | mean | median | p10 | p90 | stdev | within +-4 after pooled fix | p90 abs after pooled fix |
|---|---|---|---|---|---|---|---|---|
| 12345 | 82188 | -13.32 | -13 | -17 | -9 | 5.14 | 77.46% | 6 |
| 1 | 80254 | -16.24 | -15 | -24 | -10 | 5.85 | 61.60% | 10 |
| 2026 | 75971 | -12.91 | -12 | -18 | -8 | 5.27 | 68.13% | 7 |
| 424242 | 68043 | -14.15 | -13 | -19 | -9 | 5.53 | 72.47% | 8 |
| 987654321 | 94475 | -13.85 | -13 | -19 | -8 | 7.55 | 70.85% | 7 |
| -5551212 | 86842 | -16.71 | -16 | -24 | -10 | 6.45 | 67.48% | 10 |
| 777 | 83126 | -17.17 | -16 | -26 | -10 | 6.61 | 57.68% | 12 |
| 31415926 | 85474 | -12.67 | -12 | -17 | -8 | 4.52 | 72.47% | 6 |
| 8001 | 75300 | -27.64 | -16 | -74 | -11 | 33.81 | 61.39% | 60 |
| -99 | 70014 | -15.72 | -15 | -23 | -9 | 5.73 | 58.93% | 10 |

Pooled median (coarse - B) over all seeds: **-14** (p10 -23, p90 -9, n=801687). Fix = subtract it.

## By window kind (all seeds pooled), land columns

| kind | cols | =B % | p99 abs | max abs | coarse-B median | coarse-B p10 | coarse-B p90 |
|---|---|---|---|---|---|---|---|
| coast | 98664 | 100.00 | 0 | 0 | -13 | -19 | -8 |
| land | 419592 | 99.55 | 0 | 13 | -14 | -20 | -9 |
| mountain | 159714 | 97.36 | 1 | 15 | -18 | -35 | -8 |
| ocean | 23875 | 97.88 | 1 | 12 | -13 | -18 | -8 |
| river | 99842 | 99.05 | 0 | 12 | -13 | -18 | -9 |

## What surface rules changed vs the filled column (blocks at the changed top, all seeds, all columns)

- ADDED above fill: block.minecraft.packed_ice: 6446
- ADDED above fill: block.minecraft.snow_block: 255
- REMOVED (non-opaque now) top: block.minecraft.powder_snow: 7583
- REMOVED (non-opaque now) top: block.minecraft.water: 622

carver stage error (null if worked): null
