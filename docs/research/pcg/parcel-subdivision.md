# Procedural parcels: OBB and skeleton subdivision (Vanegas et al. 2012)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Vanegas, Kelly, Weber, Halatsch, Aliaga, Müller, "Procedural generation of
parcels in urban modeling", Computer Graphics Forum 31(2), Eurographics 2012
([PDF](https://www.cs.purdue.edu/cgvlab/papers/aliaga/eg2012.pdf),
DOI 10.1111/j.1467-8659.2012.03047.x). The block → lot step of CityEngine,
replacing [Parish–Müller](parish-muller-cities.md)'s "split longest edges,
drop lots without access", which left unassigned holes in blocks.

## Approach

Each block (a face of the road graph) is partitioned **completely** (no
leftover area) under per-block attributes: area bounds `Amin..Amax`, width
bounds `Wmin..Wmax`, split irregularity `ω`, street-access preference `ξ`.
Two styles, mixable in one block:
- **OBB (recursive):** take the parcel's minimum-area oriented bounding box;
  split across its long side at the midpoint shifted by `ω`·random; recurse
  until area/frontage are in bounds. If a child has no street access, rotate
  the split 90° with probability `ξ` (`ξ = 1` guarantees access). Split
  lines snap to nearby block vertices. **Child seeds are drawn before
  recursing**, so a local change only disturbs nearby parcels.
- **Skeleton (strips):** inset the block by `doffset` with the straight
  skeleton (see [straight-skeleton-roofs](straight-skeleton-roofs.md)); each
  skeleton face is a strip fronting one street. Merge faces on the same
  "logical street", move the triangular corner pieces to the wider (or
  longer) street's strip so lot sides meet the street square, then cut each
  strip by rays perpendicular to the street at spacing `N((Wmin+Wmax)/2, 3ω)`.
  Merge triangles and tiny parcels into the neighbour with the longest shared
  edge. A large `doffset` gives back-to-back lots; a small one leaves an
  interior courtyard (perimeter block), which may be OBB-split further.
Also: matching parcels before/after an edit by generalised barycentric
position, for interactive editing (irrelevant to us). Speed: a whole city
under 3 s; 1–10 ms per block (CityEngine, 2012 hardware).

## Procedural vs templated

Fully procedural. Per-block attributes can be fitted to real-city GIS
statistics (mean ± k·sd of area and width); the paper's figures match
observed districts well. Varies: lot sizes, depth, courtyard or not.

## How it hooks into worldgen

Not worldgen; per block, inside a region cell's city plan.
- **Locality:** each block is subdivided independently of the others, so it
  parallelises and is order-free once the street graph exists. Seed per
  block from a hash of (cell seed, block id); per child from the parent's.
- **Voxel fit:** OBB style on axis-aligned blocks is just recursive
  rectangle bisection: integer cuts, every lot a rectangle. Skeleton style
  gives trapezoids at corners; snap or accept stepped lot edges.

**Compared with Watabou's bisection** (see [watabou-city-generators](watabou-city-generators.md)):
Watabou is the OBB style without the access rule or bounds: cut across the
longest edge with jittered ratio/angle, stop at a random size, sometimes
leave a lot empty, and filter outskirts by road distance. Vanegas adds (1)
**complete partition**, (2) **access guaranteed** by the `ξ` rotate rule, (3)
**explicit area and frontage bounds**, (4) a second style, strips, which
gives the street-facing terraced rows and courtyard blocks that Watabou's
cuts can't. Watabou is simpler and enough for a medieval core; Vanegas's
access rule and bounds are cheap additions worth taking.

## Licence

Paper only. CityEngine is proprietary (Esri). The paper's skeleton step used
CGAL (GPL/commercial; see the roofs note). Small open OBB-parcelling demos
exist (e.g. github.com/stavguo/obb-parcelling), not read. Ideas free;
patents not searched.

## What to borrow

- **OBB recursive split with the access rule** as our default lot splitter:
  on axis-aligned blocks it is integer rectangle bisection, deterministic,
  bounded depth, every lot fronts a street.
- **Attributes as the district knob** (`Amin/Amax`, `Wmin/Wmax`, `ω`, `ξ`):
  same idea as Watabou's ward parameters, but with bounds we can test.
- **Strips** for street-front rows (terraces, market streets) and
  **perimeter blocks with a courtyard** (inset by lot depth) for dense cores.
- **Child seeds fixed before recursion** = hashed sub-seeds per node.
- **Merge slivers** into the neighbour with the longest shared edge.

## What to avoid

- Free-angle OBB cuts on diagonal blocks: stair-stepped lot edges. Keep the
  blocks axis-aligned where buildings go.
- The skeleton style on irregular blocks unless we already have a robust
  skeleton; corner reassignment has many special cases.

## Verdict

Best lot-subdivision reference: adopt the OBB splitter with access and size
bounds (a stricter Watabou), add skeleton strips later for terraces and
courtyard blocks.

**Sources / what I couldn't verify:** read sections 1–6 of the paper; the
appendix pseudocode (Algorithms 1–2) and the statistics section only
skimmed. Nothing run.
