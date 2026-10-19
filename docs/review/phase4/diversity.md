# Phase 4 diversity report (generator v1)

Produced for the gate pack (4.10, 2026-10-19) with one `fractal-cli diversity --type T --seeds 0..255
--lot L --style medieval` per type (terrain `flat:64`, front north; 256 buildings, 32,640 pairs each).
Houses also in `plains` and `plain`, and the Phase 3 v0 baseline (`--gen v0`, plain, 20x20) for scale.

| type | lot | mean Jaccard distance | near-dup rate (< 0.02) | voxel-set repeats | distinct signatures | storeys H | ridgeAxis H | roomsPerStorey H | doorEighths H | windows H |
|---|---|---|---|---|---|---|---|---|---|---|
| house (medieval) | 20x20 | 0.7978 | 0.0000 | 0 | 74 | 1.5583 | 1.3960 | 5.1979 | 2.7053 | 5.4223 |
| house (plains) | 20x20 | 0.7956 | 0.0000 | 0 | 51 | 1.5583 | 1.5228 | 4.3903 | 2.3327 | 5.3755 |
| house (plain) | 20x20 | 0.8030 | 0.0000 | 1 | 50 | 1.5583 | 1.0709 | 4.3053 | 2.3352 | 5.3511 |
| shop | 11x17 | 0.4807 | 0.0006 | 1 | 81 | 1.5583 | 0.8527 | 5.4017 | 0.0000 | 5.1783 |
| workshop | 16x14 | 0.6115 | 0.0003 | 5 | 4 | 0.9978 | 1.2717 | 0.9487 | 2.1127 | 3.4759 |
| tower | 13x13 | 0.7223 | 0.0003 | 0 | 79 | 2.7567 | 0.8523 | 4.5890 | 2.4665 | 6.0369 |
| temple | 25x38 | 0.7529 | 0.0000 | 0 | 57 | 0.9978 | 1.0385 | 4.0333 | 0.0000 | 6.1307 |
| wall | 32x9 | 0.4188 | 0.0008 | 8 | 6 | 0.9978 | 0.0000 | 2.4388 | 1.4323 | 4.8125 |
| highrise | 36x36 | 0.6724 | 0.0000 | 0 | 256 | 5.2572 | 0.0000 | 8.0000 | 2.9630 | 7.8408 |
| hall | 56x44 | 0.8402 | 0.0000 | 0 | 82 | 1.5583 | 1.5724 | 4.7115 | 2.5319 | 6.7395 |
| block | 64x56 | 0.6162 | 0.0000 | 0 | 255 | 1.9901 | 0.8896 | 7.9922 | 0.9004 | 7.2337 |
| v0 house (Phase 3) | 20x20 | 0.6884 | 0.0033 | 64 | 182 | 1.5466 | 0.7922 | 2.8448 | 2.7391 | 4.7196 |

H = entropy in bits over the 256 buildings. Repeats by spec hash are 0 everywhere.

**Reading it.**

- **Width and depth entropy are 0 for every v1 type** (v0: 3.4 and 2.8 bits), so they are left out:
  in v1 the spec's width and depth are the lot's envelope, and the shape varies inside it (massing
  plans, wings, setbacks). The "signature" (width x depth x storeys / rooms per storey) therefore
  counts less than in v0 even where the buildings differ more; the Jaccard distance is the better
  measure. Houses went from 0.69 (v0) to 0.80, with 0 voxel-set repeats (v0: 64).
- **Most varied:** hall (0.84; four sub-kinds), house, temple, tower. High-rise and city block have a
  distinct signature for nearly every seed (many storeys and units).
- **Least varied: wall (0.42) and shop (0.48).** Both are narrow, mostly fixed programs: a wall is
  gatehouse + curtains with a fixed passage, a shop is a centred door between display windows
  (doorEighths 0 for shops by design; temples too, door on the axis). Wall has 8 exact voxel repeats
  and 6 signatures.
- **Workshop: only 4 signatures and 5 voxel repeats** at 16x14 (hall + wing in few arrangements;
  0.61 mean distance). Worth a look in the gallery: do the 16 seeds look like the same building?

Regression floors live in the type tests (the `*TypeTest` variety assertions and `HighriseDiversityTest`).
