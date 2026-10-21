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
