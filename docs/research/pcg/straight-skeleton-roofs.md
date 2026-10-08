# Straight-skeleton roofs, and their voxel equivalent

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Aichholzer, Aurenhammer, Alberts, Gärtner, "A novel type of skeleton for
polygons", J.UCS 1(12), 1995 ([paper page](https://www.jucs.org/jucs_1_12/a_novel_type_of.html)).
The straight skeleton partitions a polygon by shrinking it, every edge moving
inward at the same speed; lifting each point by the time the front passed
it gives "a canonical way of constructing a polygonal roof" (the paper): a
hip roof over any footprint. This note owns the algorithm; the look
(pitches, eaves, roof types) is in [aesthetics/roofs](../aesthetics/roofs.md)
and the track 6 roof-vocabulary note.

## Approach

- **Wavefront:** offset all edges inward; **edge events** (an edge shrinks
  to zero) and **split events** (a reflex vertex hits an opposite edge,
  splitting the front) change topology; the traced vertex paths are the
  skeleton arcs (hips, valleys, ridges). Unlike the medial axis it has only
  straight arcs. Practical algorithm: Felkel–Obdržálek 1998, a priority
  queue of events; numerically fragile (simultaneous events, parallel
  edges), which is why implementations are hard.
- **Weighted skeleton** (each edge its own speed = its own pitch; speed 0
  or a vertical plane = gable end): Kelly & Wonka, "Interactive architectural
  modeling with procedural extrusions" (TOG 2011); used by Merrell 2010 for
  hips with gables chosen per face (see [merrell](merrell-residential-layouts.md)).
- **Voxel equivalent (our proposal, checked with a 20-line script):** for a
  rasterised footprint with axis-aligned walls, the mitred inward offset by
  `d` equals erosion by a `(2d+1)²` square, so the **hip-roof height of a
  cell is its chessboard (L∞) distance to the outside**: a two-pass integer
  distance transform. On an L footprint it gives the correct hips, ridges and
  the diagonal valley at the reflex corner, with no events, no floats.
  Height / 2 gives a 1:2 slab pitch; × 2 a 2:1 full-block pitch.
- **Gables and cross-gables:** split the footprint into rectangular **wings**
  (the massing grammar has them anyway), give each a ridge axis and height
  `1 + distance to its long sides`, and take the **max** over wings. Clip a
  wing where it joins another, or its ridge runs through the other wing's
  roof (seen in the script); with clipping, the max produces the valleys.

## Procedural vs templated

Fully procedural: roof = f(footprint, pitch per edge or wing).

## How it hooks into worldgen

Per building, after massing. **Deterministic and chunk-local** if the
building is planned whole (structure start) and its blocks emitted per
chunk from the plan. The integer distance transform has no float or order
issues; a polygon skeleton library would need care (see below).

**Guaranteeing closed roofs.** With the heightmap form, the roof is one
height per footprint column, so it can't have holes. Two checks remain:
(1) every footprint cell is covered by some wing (else height 0 = hole);
(2) any step > 1 between neighbouring cells is a vertical face (gable end,
wing joint) that must be filled with wall material down to the lower
surface. The L∞ transform and each single wing are 1-Lipschitz, so (2) only
fires at wing joints and gable ends. Both are cheap invariants for the IR.
Dormers, overhangs and eaves are additions on top, outside the heightmap.

## Licence

| Implementation | Language | Licence | Use |
|---|---|---|---|
| CGAL `Straight_skeleton_2` | C++ | GPL-3.0 or commercial | study only |
| [twak/campskeleton](https://github.com/twak/campskeleton) (Kelly, weighted, Felkel + robustness fixes) | Java | Apache-2.0 (deps: javax.vecmath, JAMA, twak utils; not checked) | could depend on |
| [twak/siteplan](https://github.com/twak/siteplan) (procedural extrusions) | Java | Apache-2.0 | study |
| [kendzi-math](https://github.com/kendzi/kendzi-math) `kendzi-straight-skeleton` (JOSM Kendzi3D roofs) | Java | BSD-3-Clause | could depend on |
| [StrandedKitty/straight-skeleton](https://github.com/StrandedKitty/straight-skeleton) | TS (CGAL via Wasm) | repo says MIT, but bundles GPL CGAL code | treat as GPL |
| Botffy/polyskel | Python | LGPL-3.0 | study |

Paper ideas are free.

## What to borrow

- **Chessboard distance transform** for hip roofs on any rectilinear
  footprint; per-wing axis distance + max for gables and cross-gables.
  Roof stair facing and `inner`/`outer` shapes follow from the height
  gradient and its diagonal neighbours.
- **Coverage + step-fill invariants** for "no open roofs".
- **Skeleton strips** reused for lots (see [parcel-subdivision](parcel-subdivision.md)).
- A polygon skeleton library only if we ever allow diagonal walls;
  campskeleton or kendzi are licence-compatible.

## What to avoid

- Running a float polygon skeleton per building when walls are already on
  the block grid: more code, fragile events, aliasing on rasterisation.
- Hip roofs over very long or large footprints: rise grows with the short
  span; cap height (flatten to a ridge plateau or a flat top) per style.
- Even wing widths without a ridge rule: the transform gives a flat 2-wide
  ridge (style decides slab ridge or double stair; see the roofs note).

## Verdict

Use the voxel form (L∞ distance transform; per-wing max for gables) as our
roof algorithm: closed by construction and checkable; keep campskeleton
(Apache-2.0) in reserve for non-rectilinear footprints.

**Sources / what I couldn't verify:** paper abstract only (full text not
read); edge/split events and Felkel from background knowledge. Library
licences from GitHub metadata and READMEs; their code and robustness not
tested; campskeleton's dependency licences not checked. The voxel claims
were checked only by a throwaway node script on one L footprint, not a proof.
