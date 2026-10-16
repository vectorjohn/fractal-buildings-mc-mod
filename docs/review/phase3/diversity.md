# Phase 3 diversity report (generator v0)

Regenerated for the gate pack (S9, 2026-10-16) with the CLI:
`fractal-cli diversity --seeds 0..255 --lot 20x20` (style `plain`, terrain `flat:64`, front north;
v0 styles differ only in palette, so `medieval` gives the same numbers). Identical to the
`V0DiversityTest` baseline from 3.11.

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

Regression floors in `V0DiversityTest`: distance >= 0.55, near-dup rate <= 0.01, signatures >= 145,
voxel-set repeats <= 80. A "seed ignored" run (always seed 0) fails them.

For scale: 10,000 seeds on a 31x17 lot (pairwise metrics over the first 256) give 434 distinct
signatures, storeys entropy 1.5365 bits (log2 3 = 1.585: storeys are 1-3), and 9,203 voxel-set
repeats: v0 has a small shape space, which Phase 4 is meant to widen.
