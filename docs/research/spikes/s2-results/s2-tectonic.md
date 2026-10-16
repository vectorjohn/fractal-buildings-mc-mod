# S2 results, mod set `tectonic`

loadavg at start: 7.71 10.61 10.76 3/3947 113843

fingerprint: biomes=67 terralith_biomes=0 finalDensity.hash=1811246447 chunkSurfaceLevel.class=HolderHolder sea=63 minY=-64 height=384 biomeSource=net.minecraft.world.level.biome.MultiNoiseBiomeSource

## Cost (server thread, single thread, seed 12345, warmed up)

- getBaseHeight(OCEAN_FLOOR_WG): 0.76 ms per column (4000 random columns)
- 16-block grid over 512x512 (1024 columns): 428.36 ms
- coarse screen (chunkSurfaceLevel sampled uncached): 1.96 ms per column
- whole chunk fill+surface (no carvers, no features), for context: 19.79 ms per chunk
- loadavg after: 6.68 10.29 10.65 3/3928 114101 (sink 768019)

## Exact height (predicted getBaseHeight vs real) per seed

`B` = real first-free height after fill + surface rules; `C` = after carvers too. Land = predicted height > sea level (63); ocean = pred <= sea. cols = land columns; `=B` share equal to B; err = B - pred; `B>pred` = surface rules/fill add above prediction.

| seed | windows | land cols | =B % | =A % | =C % | p99 abs(B-pred) | max abs | B>pred cols | max B-pred | min B-pred | C<pred cols | min C-pred |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 12345 | mountain@-1600,1088 coast@192,192 river@-1856,2496 ocean@-832,-1984 land@-2368,-960 land@-2880,-704 land@1984,-2880 | 84338 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1 | mountain@2752,2880 coast@2368,-1600 river@192,960 ocean@-1472,-1728 land@64,-832 land@2240,-2880 land@576,-1088 | 83125 | 99.35 | 100.00 | 99.35 | 0 | 4 | 0 | 0 | -4 | 544 | -4 |
| 2026 | mountain@-64,-3008 coast@1088,2496 river@-1728,-64 ocean@-192,2880 land@1600,1216 land@2752,704 land@-704,-704 | 92898 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 424242 | mountain@1344,1984 coast@576,-2624 river@-1984,-960 ocean@2624,-192 land@1728,-1856 land@-192,2624 land@1600,192 | 68122 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 987654321 | mountain@-576,2880 coast@-2880,2112 river@-1728,960 ocean@-448,-2496 land@-1728,-2880 land@1856,1984 land@-1984,-2624 | 87100 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -5551212 | mountain@704,-1472 coast@-2496,1856 river@2240,-64 ocean@-1216,-2880 land@-2496,1856 land@704,-1984 land@-704,576 | 69900 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 777 | mountain@2752,1088 coast@-1344,1600 river@-3008,832 ocean@576,960 land@2752,960 land@-1728,2240 land@1344,-1728 | 89599 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 31415926 | mountain@-576,2368 coast@1216,1472 river@-192,-1984 ocean@832,704 land@2112,2240 land@-2496,832 land@-2880,2112 | 80269 | 99.90 | 100.00 | 99.90 | 0 | 5 | 0 | 0 | -5 | 83 | -5 |
| 8001 | mountain@1216,-1216 coast@64,704 river@576,-704 ocean@-1600,2880 land@-1984,192 land@2112,-192 land@-1600,448 | 64746 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -99 | mountain@-3008,-1472 coast@1600,-448 river@-1856,-2880 ocean@-1216,1600 land@1728,-2240 land@-320,1856 land@-3008,2752 | 71778 | 96.54 | 100.00 | 96.54 | 3 | 4 | 0 | 0 | -4 | 2485 | -4 |

## Ocean columns (pred <= sea level)

| seed | cols | =B % | B>pred cols | max B-pred | cols with solid above sea (ice) |
|---|---|---|---|---|---|
| 12345 | 30350 | 100.00 | 0 | 0 | 0 |
| 1 | 31563 | 95.03 | 1570 | 97 | 1570 |
| 2026 | 21790 | 100.00 | 0 | 0 | 0 |
| 424242 | 46566 | 100.00 | 0 | 0 | 0 |
| 987654321 | 27588 | 100.00 | 0 | 0 | 0 |
| -5551212 | 44788 | 100.00 | 0 | 0 | 0 |
| 777 | 25089 | 100.00 | 0 | 0 | 0 |
| 31415926 | 34419 | 92.38 | 1969 | 50 | 1969 |
| 8001 | 49942 | 97.17 | 1415 | 62 | 1415 |
| -99 | 42910 | 98.48 | 648 | 37 | 648 |

## Coarse screen (chunkSurfaceLevel at the column) minus real B, land columns

| seed | cols | mean | median | p10 | p90 | stdev | within +-4 after pooled fix | p90 abs after pooled fix |
|---|---|---|---|---|---|---|---|---|
| 12345 | 84338 | -22.63 | -14 | -71 | -10 | 22.40 | 77.19% | 58 |
| 1 | 83125 | -14.81 | -12 | -24 | -8 | 9.19 | 65.31% | 11 |
| 2026 | 92898 | -25.78 | -13 | -91 | -10 | 30.64 | 79.89% | 78 |
| 424242 | 68122 | -23.41 | -13 | -63 | -8 | 22.78 | 59.71% | 50 |
| 987654321 | 87100 | -21.49 | -14 | -59 | -9 | 21.62 | 71.61% | 46 |
| -5551212 | 69900 | -28.90 | -13 | -92 | -10 | 31.93 | 69.19% | 79 |
| 777 | 89599 | -12.48 | -12 | -17 | -8 | 4.36 | 76.42% | 6 |
| 31415926 | 80269 | -15.42 | -14 | -22 | -10 | 6.97 | 76.07% | 9 |
| 8001 | 64746 | -30.53 | -14 | -95 | -9 | 33.26 | 62.30% | 82 |
| -99 | 71778 | -26.97 | -14 | -80 | -10 | 28.03 | 67.56% | 67 |

Pooled median (coarse - B) over all seeds: **-13** (p10 -59, p90 -9, n=791875). Fix = subtract it.

## By window kind (all seeds pooled), land columns

| kind | cols | =B % | p99 abs | max abs | coarse-B median | coarse-B p10 | coarse-B p90 |
|---|---|---|---|---|---|---|---|
| coast | 108850 | 100.00 | 0 | 0 | -10 | -15 | -8 |
| land | 446062 | 99.45 | 0 | 4 | -13 | -16 | -9 |
| mountain | 163840 | 99.66 | 0 | 4 | -56 | -102 | -15 |
| river | 73123 | 99.89 | 0 | 5 | -13 | -15 | -10 |

## What surface rules changed vs the filled column (blocks at the changed top, all seeds, all columns)

- ADDED above fill: block.minecraft.packed_ice: 5268
- ADDED above fill: block.minecraft.snow_block: 334
- REMOVED (non-opaque now) top: block.minecraft.powder_snow: 3118
- REMOVED (non-opaque now) top: block.minecraft.water: 654

carver stage error (null if worked): java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.WorldGenRegion.registryAccess()" because "carverBiomeRegion" is null
