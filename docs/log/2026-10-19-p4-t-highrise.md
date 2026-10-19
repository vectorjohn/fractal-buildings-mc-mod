# 4.6 T-highrise: setback tower

Branch `claude/p4-t-highrise`. Files: `gen/v1/types/highrise/{HighriseType,HighriseMassing}.java`, one line in `Types.java`,
`golden/v1/highrise.txt`, tests `HighriseTypeTest`, `HighriseDiversityTest`, presets `highrise-{plain,medieval,flat}`.

## What it does
- `HighriseMassing`: storeys = min(spec, capacity) where capacity keeps top slab + 3 <= `maxHeight` (the policy's y 319
  bound). Podium (1-3 storeys, only when n >= 6), shaft bands (8-20 storeys; setbacks between them, 0-3 per tower),
  crown (1-3 storeys, n >= 8), each tier `MassingOps.setback` of the one below with per-side insets (a draw of 1-4 on
  about two thirds of the sides, at least one) and at least 10 across; `MassingOps.core` through all storeys (2 x 6,
  hosts = every tier). Failing draws back off to fewer setbacks, then fewer storeys, then a tiny box. No new MassingOps
  methods (`stack` stays unused: uniform insets were too tame).
- Everything after massing is the shared passes: R's flat roofs with parapets (tall or partly covered groups), I's floors.

## Verification
- `HighriseTypeTest` (tiers inside each other, top under `maxHeight`, >= 20 storeys when `maxHeight` >= 100, podium /
  band / inset / crown ranges, one core 0..n, low ceilings 6..33, shell bounds <= `maxHeight`, shell time),
  `HighriseDiversityTest` (256 seeds: distance 0.5949, 41 signatures, no repeats; floors 0.47 / 32 / <= 10 repeats,
  seed-ignored mutant fails). `V1PropertyTest` covers the type (48 cases); golden file generated.
- Mutants: `MAX_BAND` 30 fails the band test; ignoring `maxHeight` fails the tier/height test; core to n - 1 fails three.
- `check --types highrise --seeds 0..199`: 25600 cases, 0 failing. `bench --types highrise` under the heavy lock (load ~19
  from other agents): shell mean 3.9 ms, p95 13.5 ms; check p95 19 ms. Nowhere near the 100 ms flag.
- Gallery (`highrise-plain` noise, `highrise-flat` 44x44 flat, `highrise-medieval` + cutaway): 16 towers each, 0
  violations; a 16-cell page of 250-block towers renders in 3-5 s (cutaway 1.1 s). Looked at front-left, top, cutaway:
  visible podium, 2-4 steps, varied widths and heights, window and floor-line banding; cutaway shows rooms, corridors
  and the stair core. R, Fa, S merged (r2): crowns and terraces are flat with parapets, windows read as a bay rhythm, entrance steps present
  (checked on merge; see the report).
