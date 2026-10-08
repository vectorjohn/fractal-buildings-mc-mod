# Tensor-field street modeling (Chen et al. 2008)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Chen, Esch, Wonka, Müller, Zhang, "Interactive procedural street modeling",
ACM TOG 27(3) / SIGGRAPH 2008 ([PDF](https://web.engr.oregonstate.edu/~zhange/images/street_sig08.pdf),
DOI 10.1145/1360612.1360702). Streets are traced as the two perpendicular
families of curves of a designed 2D tensor field, instead of grown like
[Parish–Müller](parish-muller-cities.md)'s L-system.

## Approach

1. **Field:** a tensor (orientation mod 180°) on a 512² grid, bilinear
   between vertices. It is a sum of **basis fields**, each weighted by
   `exp(-d·|p - p_i|²)`: *grid* (constant direction), *radial* (circles
   around a centre), *boundary* (one grid element per segment of a
   water/park outline, so roads follow coasts), *heightfield* (minor
   direction = gradient, so one family follows contours). Optional
   Laplacian smoothing, brush strokes, and **rotation fields** (Perlin noise
   in ±90°, or ≤ 20° to make crossings non-orthogonal) for organic layouts.
2. **Major roads:** evenly spaced hyperstreamlines (Jobard–Lefer) traced by
   adaptive Runge–Kutta, **alternating major and minor** families from a
   **priority queue of seeds** (near water, degenerate points, population
   centres). A line stops at the domain edge, a degenerate point, a loop,
   max length, or when closer than `dsep` to another line; it then looks
   ahead `dlookahead` to join a line (fewer dead ends). **Density =
   `dsep` from a population map.** Graph = intersections of the two families.
3. **Minor roads:** the major graph splits the domain into regions; each
   gets its own field (can be discontinuous across major roads), traced the
   same way. Then **graph noise**: delete some segments (Halton sampling)
   for Manhattan-style dead ends and merged blocks.
Timing: no machine numbers; "about five minutes" of user time per city.

## Procedural vs templated

Procedural but **designer-driven**: the paper is an editing tool, and its
variety comes from placed design elements. For us, elements would be
placed by rules (centre → radial, river → boundary, hill → heightfield).

## How it hooks into worldgen

Not worldgen; a candidate for the street step of a settlement region cell.
- **Field is local; tracing is not.** The field at a point is a pure
  function of the elements and terrain, so it can be sampled anywhere. But
  `dsep` spacing makes each streamline depend on all earlier ones (seed
  queue order), so the network must be traced **once per region cell**
  and cached, like Parish–Müller. Never per chunk.
- **Determinism:** deterministic given a fixed seed order, fixed RK step
  rules and `StrictMath`; adaptive step control plus float comparisons
  against `dsep` are the fragile part (tiny float drift moves a whole
  street). Prefer a fixed step and integer-snapped seeds.
- **Cost:** a small town (a few 100 m across, `dsep` 10–30 blocks) is a few
  hundred streamlines; should be milliseconds, not measured.
- **Voxel fit: poor for building lots.** Curves alias at 1 block, and
  blocks become irregular quads. Fine for *paths, lanes and outskirts*
  where Minecraft paths look natural; a grid field reduces to
  axis-aligned streets anyway.
- **Region seams:** different cells' fields meet discontinuously; agree
  hashed gate points on the cell border and seed streamlines from them.

## Licence

Paper only, no code. Open reimplementation:
[ProbableTrain/MapGenerator](https://github.com/ProbableTrain/MapGenerator)
(TypeScript, **LGPL-3.0**: study fine, don't copy into the mod). Ideas free.

## What to borrow

- **Orientation field from terrain**: contour-following lanes on slopes,
  shore-following roads, from our height and biome probes. Usable even
  without streamline tracing, e.g. to orient lots and buildings per
  district or to bias Parish–Müller's growth direction.
- **Density as spacing** (`dsep` from a density map): one knob for
  dense core → sparse edge.
- **Major roads first, then per-block minor fields**, discontinuous across
  major roads: districts with their own grain.
- **Graph noise** (delete a few segments) for cheap irregularity.

## What to avoid

- Free-form curved streets inside the built-up core: lots and buildings
  must be axis-aligned on the block grid (see `watabou-city-generators.md`).
- Adaptive integration and long float chains where a tiny difference
  reorders the result.
- The interactive editing tools: we have no designer.

## Verdict

Borrow the terrain-derived orientation field and density-as-spacing for
outskirts and paths; Parish–Müller-style growth or bisection stays the core
street/lot method because curved streets fit voxel lots badly.

**Sources / what I couldn't verify:** read the full paper. MapGenerator:
licence and description only, code not read. Costs for a Minecraft town
are my estimate; nothing run.
