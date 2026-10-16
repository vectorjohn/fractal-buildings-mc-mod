# S2 results, mod set `terralith`

loadavg at start: 6.06 7.12 6.72 5/4011 92279

fingerprint: biomes=162 terralith_biomes=95 finalDensity.hash=1879630814 chunkSurfaceLevel.class=HolderHolder sea=63 minY=-64 height=384 biomeSource=net.minecraft.world.level.biome.MultiNoiseBiomeSource

## Cost (server thread, single thread, seed 12345, warmed up)

- getBaseHeight(OCEAN_FLOOR_WG): 0.75 ms per column (4000 random columns)
- 16-block grid over 512x512 (1024 columns): 299.61 ms
- coarse screen (chunkSurfaceLevel sampled uncached): 0.06 ms per column
- whole chunk fill+surface (no carvers, no features), for context: 24.72 ms per chunk
- loadavg after: 5.66 7.02 6.69 2/3991 92354 (sink 589561)

## Exact height (predicted getBaseHeight vs real) per seed

`B` = real first-free height after fill + surface rules; `C` = after carvers too. Land = predicted height > sea level (63); ocean = pred <= sea. cols = land columns; `=B` share equal to B; err = B - pred; `B>pred` = surface rules/fill add above prediction.

| seed | windows | land cols | =B % | =A % | =C % | p99 abs(B-pred) | max abs | B>pred cols | max B-pred | min B-pred | C<pred cols | min C-pred |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 12345 | mountain@-1856,-1984 coast@-1856,2496 river@-320,320 ocean@64,448 land@-192,1472 land@-2368,-960 land@-1088,1088 | 67758 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1 | mountain@2752,-2496 coast@448,-704 river@192,-1728 ocean@64,-832 land@-1472,-1728 land@-1216,-1728 land@-2752,-1984 | 84098 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2026 | mountain@-2752,1600 coast@1216,1088 river@2880,1600 ocean@-64,2240 land@1600,1216 land@1984,-1472 land@576,320 | 76279 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 424242 | mountain@-832,2368 coast@-1216,-3008 river@2624,-192 ocean@-192,-2624 land@-192,320 land@-2368,1216 land@-192,2624 | 68523 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 987654321 | mountain@2624,2752 coast@-1728,-2880 river@832,1472 ocean@-1472,-2752 land@-2880,2112 land@2496,2624 land@-1088,-832 | 69432 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -5551212 | mountain@-320,-2880 coast@-2496,1856 river@-1472,-320 ocean@2880,320 land@-2240,1600 land@704,-1984 land@-704,576 | 77460 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 777 | mountain@1728,-320 coast@2368,1472 river@2624,-1984 ocean@1344,-192 land@-2112,1344 land@576,960 land@-1984,-192 | 85632 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 31415926 | mountain@-960,64 coast@-2496,832 river@1984,-3008 ocean@-1472,-192 land@2112,2240 land@-2496,832 land@1728,2112 | 67867 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 8001 | mountain@2240,2112 coast@-3008,-320 river@2112,-192 ocean@-1216,-2240 land@-1600,2880 land@-1088,-832 land@-2240,832 | 81801 | 100.00 | 100.00 | 100.00 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| -99 | mountain@1600,-1472 coast@-320,1856 river@-1984,-832 ocean@832,-192 land@1728,-2240 land@1728,-832 land@-1984,448 | 74568 | 99.18 | 100.00 | 99.18 | 0 | 9 | 0 | 0 | -9 | 612 | -9 |

## Ocean columns (pred <= sea level)

| seed | cols | =B % | B>pred cols | max B-pred | cols with solid above sea (ice) |
|---|---|---|---|---|---|
| 12345 | 46930 | 99.44 | 0 | 0 | 0 |
| 1 | 30590 | 100.00 | 0 | 0 | 0 |
| 2026 | 38409 | 99.18 | 315 | 63 | 315 |
| 424242 | 46165 | 100.00 | 0 | 0 | 0 |
| 987654321 | 45256 | 100.00 | 0 | 0 | 0 |
| -5551212 | 37228 | 96.80 | 739 | 45 | 739 |
| 777 | 29056 | 99.84 | 0 | 0 | 0 |
| 31415926 | 46821 | 98.95 | 0 | 0 | 0 |
| 8001 | 32887 | 100.00 | 0 | 0 | 0 |
| -99 | 40120 | 86.06 | 5591 | 75 | 5591 |

## Coarse screen (chunkSurfaceLevel at the column) minus real B, land columns

| seed | cols | mean | median | p10 | p90 | stdev | within +-4 after pooled fix | p90 abs after pooled fix |
|---|---|---|---|---|---|---|---|---|
| 12345 | 67758 | -11.95 | -11 | -17 | -8 | 4.12 | 81.56% | 6 |
| 1 | 84098 | -35.41 | -10 | -175 | -4 | 59.53 | 49.07% | 163 |
| 2026 | 76279 | -13.22 | -12 | -22 | -7 | 6.98 | 61.89% | 12 |
| 424242 | 68523 | -11.73 | -11 | -17 | -7 | 4.14 | 76.78% | 7 |
| 987654321 | 69432 | -40.71 | -15 | -94 | -9 | 39.18 | 49.78% | 82 |
| -5551212 | 77460 | -12.42 | -12 | -19 | -8 | 5.19 | 76.05% | 8 |
| 777 | 85632 | -38.51 | -14 | -136 | -9 | 54.37 | 60.46% | 124 |
| 31415926 | 67867 | -14.61 | -14 | -23 | -8 | 7.53 | 55.59% | 12 |
| 8001 | 81801 | -11.87 | -11 | -19 | -7 | 5.30 | 70.64% | 8 |
| -99 | 74568 | -12.80 | -12 | -21 | -5 | 8.83 | 61.55% | 12 |

Pooled median (coarse - B) over all seeds: **-12** (p10 -27, p90 -7, n=753418). Fix = subtract it.

## By window kind (all seeds pooled), land columns

| kind | cols | =B % | p99 abs | max abs | coarse-B median | coarse-B p10 | coarse-B p90 |
|---|---|---|---|---|---|---|---|
| coast | 85809 | 100.00 | 0 | 0 | -12 | -17 | -8 |
| land | 409816 | 99.85 | 0 | 9 | -12 | -22 | -7 |
| mountain | 160203 | 100.00 | 0 | 0 | -12 | -154 | -5 |
| ocean | 11043 | 100.00 | 0 | 0 | -11 | -16 | -7 |
| river | 86547 | 100.00 | 0 | 0 | -13 | -21 | -8 |

## What surface rules changed vs the filled column (blocks at the changed top, all seeds, all columns)

- ADDED above fill: block.minecraft.packed_ice: 6221
- ADDED above fill: block.minecraft.snow_block: 424
- REMOVED (non-opaque now) top: block.minecraft.powder_snow: 612
- REMOVED (non-opaque now) top: block.minecraft.water: 1254

carver stage error (null if worked): java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.WorldGenRegion.registryAccess()" because "carverBiomeRegion" is null
