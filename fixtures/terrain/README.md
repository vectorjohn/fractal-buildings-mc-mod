# Terrain fixtures

Recorded vanilla 26.3 terrain and biomes as `fb-region-dump/1` (`layers: []`), read by
`RecordedProbe` (plain or gzip, detected by the `1f 8b` magic) and `ProbeSpecs`
`recorded:<path>`. Columns are on a lattice (`x0 + i*step`); surface is the heightmap
convention (top of terrain or water), `water = surface - floor`, biome is the quart at the
surface Y. Total committed size is about 0.6 MB.

| File | Seed | Window (blocks) | Step | Size | Shows |
|---|---|---|---|---|---|
| `s4-12345-crop32.json` | 12345 | x -1408..576, z -4160..-2176 | 64 | 9 KB | crop of S4's map; river at (576, -4160) |
| `world-12345-s64.json.gz` | 12345 | x, z -12288..12224 (384 x 384) | 64 | 231 KB | coarse world map, about 58% land; ocean and land blobs |
| `world-987654321-s64.json.gz` | 987654321 | same | 64 | 234 KB | second coarse world map |
| `w12345-river-s8.json.gz` | 12345 | x -1408..632, z -4160..-2120 (256 x 256) | 8 | 59 KB | around (-384, -3136), the crop's city: rivers, a bay, coast to the south |
| `w1-mixed-s8.json.gz` | 1 | x 7936..9976, z -4672..-2632 (256 x 256) | 8 | 63 KB | around the land city (8960, -3648) of S4's map: savanna, temperate and jungle mix, many rivers, a lake |
| `fly-12345-coast-city.json.gz` | 12345 | x 64..2232, z -1344..824 (272 x 272) | 8 | 72 KB | B9 fly-through city `s1_m1` (1152, -256, r807): snowy coast, frozen rivers (CITYQ, `CityFillTest`) |
| `fly-1-hill-town.json.gz` | 1 | x -2240..-848, z 4096..5488 (176 x 176) | 8 | 32 KB | B9 fly-through "metropolis" `sm2_4` (-1536, 4800, r418), a town since CITYQ: steep, lakes (`CityFillTest`) |

## Regenerating, or making larger windows

```sh
nix develop -c ./gradlew :mod:test --tests '*TerrainDumpToolTest.dump' \
  -Dfb.dump=seed:x0:z0:step:nx:nz:name[,seed:...]   # writes fixtures/terrain/<name>.json.gz
```

`-Dfb.dump.out=DIR` changes the output directory (relative to `mod/`). No server needed:
the tool (`mod/src/test/java/dev/fractalbuildings/mod/TerrainDumpTool.java`, test sources
only) bootstraps vanilla worldgen and reads `ChunkGenerator.getBaseColumn`. Output is
byte-stable (1 and 4 threads give identical bytes). Timings (20 threads, quiet machine):
384 x 384 at step 64 takes 4 to 7 s; 256 x 256 at step 8 takes 1.5 to 4.5 s. Cost is about
0.03 to 0.05 ms per column per thread, so a 3072-block window at step 8 (384 x 384) is
about 5 s and about 150 to 250 KB gzipped; do not commit more than a few of those.

Do not hand-edit dumps; regenerate them. Vanilla only (no Terralith or Tectonic). The biome
list at the surface can still name `dripstone_caves` or `lush_caves` where the surface is
carved (S4 gotcha).

## Gate windows (T2)

Nine windows for the Phase 5 gate (John looks at cities in at least five biome mixes). Each is the
settlement disk plus 256 blocks on every side (step 8; step 16 where the window passes 3072). They
were picked from the join-policy region maps of the two world dumps (`fractal-cli region --seed S
--terrain recorded:world-S-s64.json.gz`: winners with `claimedBy` null, tier, radius, groups).
Machine-readable list: `gate-sites.tsv` (read by `GateTerrainTest`, which plans every site from its
dump and expects 0 violations and the cell count). Plan with
`fractal-cli city --seed S --terrain recorded:fixtures/terrain/<name>.json.gz --site X,Z,TIER,RADIUS`.
**Use `--site`, not `--cell`, on these windows:** on a fine window the join rule needs neighbour
cells beyond the window's edge, so `--cell` answers "belongs to no settlement" for all but one
(on the world dump `--cell` works for all of them, and gives the same site).

| File | Seed | Cell (cx,cz) | Site X,Z | Tier, radius | Cells | Window (x0, z0, size) / step | Biome mix (groups, share of footprint) | Covers |
|---|---|---|---|---|---|---|---|---|
| `gate-temperate-city.json.gz` | 987654321 | 1,-10 | 1280,-9600 | city, 477 | 1 | 512,-10368, 1536 / 8 | temperate 91, water 8 | (1) plains/forest city |
| `gate-desert-badlands.json.gz` | 987654321 | 6,-7 | 6720,-6784 | city, 827 | 1 | 5632,-7872, 2176 / 8 | badlands 51, desert 36, water 8 | (2) badlands + desert |
| `gate-snow-city.json.gz` | 987654321 | 4,0 | 4288,448 | city, 478 | 1 | 3520,-320, 1536 / 8 | snow 84, water 11 | (3) snowy; hilly, lakes |
| `gate-jungle-savanna.json.gz` | 987654321 | -1,-4 | -640,-3712 | city, 790 | 1 | -1728,-4800, 2176 / 8 | jungle 49, savanna 26, water 14, temperate 8 | (4) jungle and savanna on a peninsula |
| `gate-border-desert-jungle.json.gz` | 987654321 | 7,-8 | 7872,-7744 | city, 577 | 1 | 6976,-8640, 1792 / 8 | jungle 42, desert 28, badlands 6, temperate 6, water 12 | (5) straddles desert and jungle |
| `gate-coast-city.json.gz` | 12345 | -8,-4 | -7360,-3712 | city, 740 | 2 | -8384,-4736, 2048 / 8 | temperate 61, water 33, shore 6 | (6) larger than 512 square, bays and islands |
| `gate-metropolis-temperate.json.gz` | 987654321 | 2,-6 | 2432,-5696 | metropolis, 1430 | 8 | 704,-7424, 3456 / 16 | temperate 79, mountain 8, water 8, swamp 4 | (7) high-rise downtown candidate, widest city |
| `gate-metropolis-mixed.json.gz` | 987654321 | 2,-2 | 2304,-1920 | metropolis, 1126 | 4 | 896,-3328, 2816 / 8 | temperate 22, water 20, savanna 18, jungle 16, desert 14, shore 8 | (5) and (7): six groups |
| `gate-joined-city.json.gz` | 987654321 | 8,0 | 8832,192 | city, 717 | 2 | 7808,-832, 2048 / 8 | temperate 91, water 8 | (8) two-cell joined city |

Sizes: 34 to 116 KB each, about 540 KB for the nine. Seed 987654321 serves eight of the nine, so
a gate pack can also use the coarse `world-987654321-s64` map as an overview. All nine are real
join winners in the region map (no forced `--site`). Skeleton check: all plan with 0 violations
(23 to 208 districts); every one is larger than 512 blocks across (radius over 256).
Regenerate with `-Dfb.dump=` entries `seed:x0:z0:step:n:n:name` where R = (radius + 256) rounded
up to a multiple of 64, x0 = floor((x - R) / 64) * 64 (same for z0), n = 2R / step + 1. The exact
nine entries: `987654321:512:-10368:8:193:193:gate-temperate-city`, `987654321:5632:-7872:8:273:273:gate-desert-badlands`,
`987654321:3520:-320:8:193:193:gate-snow-city`, `987654321:-1728:-4800:8:273:273:gate-jungle-savanna`,
`987654321:6976:-8640:8:225:225:gate-border-desert-jungle`, `12345:-8384:-4736:8:257:257:gate-coast-city`,
`987654321:704:-7424:16:217:217:gate-metropolis-temperate`, `987654321:896:-3328:8:353:353:gate-metropolis-mixed`,
`987654321:7808:-832:8:257:257:gate-joined-city`.

What the skeletons show (plan view over each window, `fractal-cli city --site`): the badlands, jungle
and desert/jungle border cities have a compact hub with main roads reaching the edges; the
desert/jungle border runs right through the hub (desert west, jungle east). The two metropolises
show a dense core of several hundred blocks with arterials on tile borders. The snow city and the
coast city have a fragmented urban mask (steep and wet columns drop out: the known skeleton
limit), so they are the stress cases for DL streets, not showcases.
