# Computer-generated residential building layouts (Merrell, Schkufza, Koltun 2010)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Merrell, Schkufza, Koltun, ACM TOG 29(6) / SIGGRAPH Asia 2010
([PDF](https://www.cs.princeton.edu/courses/archive/spring11/cos598A/pdfs/Merrell10a.pdf)).
The standard reference for generating whole houses *from the inside out*:
interior layout first, exterior emerges from it. A lighter grid method is in
[constrained-growth-floor-plans](constrained-growth-floor-plans.md).

## Approach

1. **Architectural program** (rooms, sizes, aspect ratios, which pairs connect
   by door or open wall) sampled from a **Bayesian network** learned from 120
   hand-encoded catalogue houses; separate nets for 1-, 2-, 3-storey. Any
   subset of variables (bedrooms, floor area) can be fixed as requirements.
2. **Floor plans** by **Metropolis (simulated-annealing-like) search** over
   rectilinear room polygons: moves are *slide a wall* (split at a random point,
   move by N(0, σ²), snap to walls within 1 ft and to walls on the floor below)
   and *swap two rooms' labels*. Start: equal rectangles on a grid.
   Cost = accessibility (missing connections, entrances/patios not on the
   outside) + dimensions (log-likelihood of each room's area and aspect under
   the net) + floors (upper-floor area not supported by the floor below;
   floor 1 must fit the **lot**) + shape (non-convexity, edge count).
3. **3D from a style template:** doors near room corners, typed by room
   privacy; windows at regular spacing on exterior walls (smaller or none if a
   lower roof blocks them); stair shape from the stair room's shape (straight,
   U, L); **hipped roof by straight skeleton**, gables by projecting chosen
   roof faces. Same layout → Cottage, Italianate, Tudor, Craftsman.

## Procedural vs templated

Fully generated; the only "templates" are a learned distribution and per-style
element tables. Varies: room set, adjacency graph, plan shape, outline. Style
is a separate, swappable layer (exactly our role → palette split).

## How it hooks into worldgen

Not worldgen; per-building. For our planner:
- **Cost:** seconds to 7 minutes per house, 100 000 iterations (35 s on a
  2010 i7) for the figure. Too slow at structure-start time unless cut down
  hugely (fewer rooms, coarse block grid, fixed iteration budget).
- **Correctness is not guaranteed:** the search can stop with the accessibility
  term > 0 (a blocked staircase): ~1 in 20 two-storey runs, 1 in 5
  three-storey. We'd need a validator plus a deterministic fallback.
- **Determinism:** fine with a hashed seed and a fixed iteration count; but
  a fixed budget means output depends on the budget, and floating-point
  acceptance tests must be reproducible (`StrictMath`, no parallelism).
- **Voxel fit:** rectilinear walls snap naturally to blocks; the 1 ft snap
  becomes "1 block". Room minimums (door width, 2-block headroom) matter more
  than in feet.
- **Data:** we have no training corpus; the Bayesian net would become a
  hand-written table of room-type probabilities per building type.

## Licence

Paper only; no code released. Patents: not searched (Stanford work; treat as
unknown). Ideas are free.

## What to borrow

- **Program → plan → dress** pipeline; program as a small graph (rooms, sizes,
  door/open-wall edges) in the Building IR, so reachability is checkable on the
  graph before any geometry exists.
- **Privacy gradient** (public rooms near the entrance, private deeper) as a
  cheap rule for ordering rooms on any generator.
- **Cost terms as invariant checks:** upper floor inside the floor below
  (no floating blocks), floor 1 inside the lot, every program edge has a shared
  wall long enough for a door. Useful as validators even if we never optimise.
- **Style template** separate from layout; window downgrade under a lower roof.
- Straight-skeleton roofs for rectilinear outlines (hip, then gables by face).

## What to avoid

- Stochastic optimisation as the main generator at worldgen time: slow,
  unbounded quality, and correctness only as a soft cost.
- A learned Bayesian net: no data, and opaque to tune; hand tables suffice.
- Coupled multi-storey optimisation; the paper's own failures come from it.

## Verdict

Best model of *what* a believable house program is (graph, privacy gradient,
support/lot terms as validators); too slow and soft-constrained to be *how* we
generate. Pair its program layer with a constructive layout method.

**Sources / what I couldn't verify:** read the full paper. No code to run;
timings are the paper's. Patent status not checked. Later work in the same
line (Bao et al. 2013 "Generating and exploring good building layouts"; Wu et
al. 2018 MIQP floor plans) not read.
