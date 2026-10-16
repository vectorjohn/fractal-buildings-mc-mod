# Phase 3 diversity report (generator v0)

256 seeds (0..255), 20x20 flat lot at y 64, `plains` biome, front facing north. Produced by
`V0DiversityTest` (same numbers for `plain` and `medieval`: v0 styles differ in palette only).
S9 regenerates this with the CLI `diversity` command once it exists.

| metric | value |
|---|---|
| buildings | 256 |
| pairs compared | 32640 |
| mean pairwise Jaccard distance | 0.6884 |
| near-duplicate pair rate (< 0.02) | 0.0033 |
| repeats by spec hash | 0 |
| repeats by voxel-set hash | 64 |
| distinct (w, d, storeys, rooms) signatures | 182 |

| descriptor | entropy (bits) |
|---|---|
| width | 3.4299 |
| depth | 2.7810 |
| storeys | 1.5466 |
| ridgeAxis | 0.7922 |
| roomsPerStorey | 2.8448 |
| doorEighths | 2.7391 |
| windows | 4.7196 |

Regression floors in the test: distance >= 0.55, near-dup rate <= 0.01, signatures >= 145,
voxel-set repeats <= 80. A "seed ignored" run (always seed 0) fails them.
