# Roof forms and massing vocabulary

**Track:** aesthetics ·
**Date:** 2026-10-08 · **Author:** assistant

## What it is

Which roof forms and massing moves a generator should support first, and a
grid-friendly way to compute roofs for irregular (L/T/U) footprints. Builds on
[roofs](roofs.md) (pitches, eaves, span limits), which this note doesn't repeat.
Sources:

- Minecraft Wiki [Tutorial:Roof types](https://minecraft.wiki/w/Tutorial:Roof_types)
  (`Tutorial:Curved roofs` now redirects here) and
  [Tutorial:Architectural terms](https://minecraft.wiki/w/Tutorial:Architectural_terms)
  (a short glossary: buttress, corbel, dormer, monitor, mullion, transom).
- Barequet, Eppstein, Goodrich, Vaxman, [Straight skeletons of
  three-dimensional polyhedra](https://arxiv.org/abs/0805.0022) (2008): for
  orthogonal inputs the shrinking front at time t is "the set of points at L∞
  distance exactly t from the boundary".
- Tom Kelly's weighted straight skeleton in Java,
  [campskeleton](https://github.com/twak/campskeleton) (Apache-2.0), used by
  Kelly & Wonka's procedural extrusions (gables = edges with vertical weight).
- Track 3 for the grammar side: [CGA](../pcg/cga-shape-grammar.md),
  [split grammars](../pcg/split-grammars.md). Track 3 has no straight-skeleton
  note yet.

## Approach

**Roof = profile(distance).** For an orthogonal footprint on the block grid,
the 1:1 hip roof is height h(x,z) = d∞(x,z), the chessboard distance from the
cell to outside the footprint (eave included). By the Barequet et al. result
this *is* the straight-skeleton roof for orthogonal polygons, so L/T/U/courtyard
hips with correct ridges, hips and valleys come from a distance transform, with
no polygon code. Variants are changes to that formula:

- pitch: h = p(d) with p(d) = 2d (2:1), d (1:1), d/2 (1:2, half steps → slabs);
- gambrel / mansard / bell-cast: piecewise p (steep then shallow; or a
  shallow first step for a bell-cast flare);
- gable: measure d only to the eave edges, not the gable-end edges (the
  weighted-skeleton trick); half-hip / Dutch gable: switch from gable to hip
  above / below a chosen height (wiki);
- shed / skillion: d to one edge only;
- flat: h = const, plus a parapet ring.

**Composite massing = max of parts.** Build each mass (main block, wing,
lean-to, tower) with its own roof heightfield; the combined roof is the
cell-wise max, which produces valleys where roofs meet (the wiki's "remove the
blocks inside the V"). Stairs/slabs then come from the heightfield: integer
step → stair facing down-gradient; half step → slab; corner cells take
`inner`/`outer` shapes from neighbour heights.

## Procedural vs templated

Fully rule-generated. Footprint, pitch profile, eave and per-edge gable flags
are the inputs; everything else is computed.

## How it hooks into worldgen

n/a (core geometry). Heightfields are pure functions of the footprint, so
deterministic and chunk-independent once the building is planned.

## Licence

Wiki text CC BY-NC-SA 3.0 (ideas only). Paper: arXiv, ideas. campskeleton:
Apache-2.0, could be used as a dependency in the core if we ever need
non-orthogonal footprints (not needed for a grid-first plan).

## What to borrow (encodable rules): support order

1. **Gable** (odd widths preferred) and **hip** (incl. L/T/U via d∞): the
   bulk of real houses; plus **shed/lean-to** for annexes, porches, outshuts.
2. **Flat + parapet** (desert, dense urban) and **pyramidal/helm** for towers
   (helm: odd-width square, stairs facing two directions only, wiki).
3. **Cross-gable / wings** by the max rule; **half-hip / Dutch gable**;
   **dormers** (gable, shed; wall dormers on thatch) on roofs with rise ≥ 4.
4. Later: **gambrel / mansard** (large footprints, wiki ~16×20),
   **saltbox**, **monitor**, conical towers. Skip curved/barrel roofs: the
   wiki says curves are "hard to model well at normal Minecraft scales".
- **Massing hierarchy.** One primary mass; wings narrower and with a ridge
  ≥ 1 lower; lean-tos only on long sides, ≤ half the main depth. Thatch: one
  uninterrupted roof, no cross-gables or monitors, wall dormers only (wiki).
- **Glossary** for IR role names: plinth, jetty, buttress, corbel, dormer,
  monitor, mullion, transom, lintel, sill, eave, ridge, hip, valley, gable end.

## What to avoid

- A polygon straight-skeleton library for grid footprints (overkill; the
  distance transform is exact for orthogonal shapes).
- Roof variants on tiny buildings: half-hips and Dutch gables are unreadable
  below ~9 wide (wiki notes they need larger-than-village buildings).
- Wings at the same ridge height as the main block (reads as one blob).

## Checks we could test

- Roof heightfield equals p(d∞) on single-mass hips (unit test on L/T/U).
- Composite roof ≥ every part's roof; every valley cell is a stair/slab, never
  an open gap; closed-roof invariant holds.
- Each roof slope: one stair facing per straight run; corners have valid
  `inner`/`outer` shapes.
- Wing ridge < main ridge; thatch roofs have exactly one section.

## Verdict

Adopt: roofs as pitch-profile(L∞ distance) heightfields, composites by max; ship gable, hip (incl. L/T/U), shed, flat and helm first, cross-gables and dormers next, gambrel/mansard later, curves never.

**Sources / what I couldn't verify:** The d∞ = straight skeleton equivalence
is quoted from Barequet et al. (stated for orthogonal polyhedra, the 2D case
following as a special case); I didn't implement it or test degenerate
footprints (e.g. two parallel edges meeting at the same time). The gable-by-
excluding-edges trick is my grid adaptation of weighted skeletons, untested on
L shapes. The old "Curved roofs" tutorial content is gone (redirect).
