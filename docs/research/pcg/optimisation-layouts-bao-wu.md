# Optimisation-based layouts: Bao et al. 2013 and Wu et al. 2018

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Two follow-ups to [Merrell 2010](merrell-residential-layouts.md), both with
Peter Wonka:
- **Bao, Yan, Mitra, Wonka**, "Generating and exploring good building
  layouts", ACM TOG 32(4) / SIGGRAPH 2013
  ([project](http://vecg.cs.ucl.ac.uk/Projects/SmartGeometry/good_layout/goodLayouts_sigg13.html),
  [DOI](https://doi.org/10.1145/2461912.2461977)). **Massing** on a parcel,
  not floor plans.
- **Wu, Fan, Liu, Wonka**, "MIQP-based layout design for building
  interiors", CGF 37(2) / Eurographics 2018
  ([PDF](http://staff.ustc.edu.cn/~lgliu/Projects/2018_EG_IPLayout/Paper/IPLayout.LowRes.pdf)).
  **Floor plans** by mixed-integer quadratic programming.

## Approach

- **Bao:** a building = union of boxes (x, y, θ, l, w, h). **Hard
  constraints** (linear): every box inside the parcel, edges kept on the
  parcel line, topology kept, thickness t_min ≤ t ≤ t_max between parallel
  walls. **Soft** (energies): covered-area target, box / edge / hole /
  courtyard counts, storey-height histogram, sunlit courtyard façades,
  shadow cast on neighbours, surface/volume (heat). Simulated annealing
  (2000–5000 steps; add / remove / move / resize a box, snap within 3 % of
  the parcel, reject anything outside it) → gradient steps + QP projection
  onto the hard constraints → local "shape space" from the lowest Hessian
  eigenmodes, linked by a "portal graph" for a user to browse. Times per
  dataset (Matlab QP): tens of seconds to minutes.
- **Wu:** rooms are unions of axis-aligned rectangles; the outline is a
  bounding box minus "obstacle" rectangles. Constraints are linear with
  big-M binaries: **inside**, **no overlap** (one of left / right / front /
  back per room pair), **size range**, **aspect ratio**, **position** (the
  foyer covers the door point), **boundary** (room touches one of given
  outside edges: light, kitchen exhaust), **adjacency with contact length
  ≥ c** (room for a door). Objective: uncovered area + squared size error.
  **Hierarchy:** solve coarse, then re-solve sub-domains with uncovered area
  or bad sizes after splitting rooms in two; large buildings = regions →
  aisles widened between them → rooms per region. Gurobi, accepts
  sub-optimal solutions after a time limit (5 s for ≤ 20 rooms), relaxes
  size bounds by 10 % until feasible. Doors on adjacency edges, windows
  evenly spaced. 5 rooms 0.13 s, 15 rooms 10–18 s, 100–150 rooms 50–90 s;
  Merrell-style annealing > 2 h past ~50 rooms + constraints.

## Procedural vs templated

Both fully generated from constraint specs; Wu learns size and adjacency
ranges from 200 plans. Variety comes from SA seeds (Bao) or solver choices
among equal optima (Wu: two layouts "with the same input", mechanism not
stated).

## How it hooks into worldgen

Not worldgen. **Too slow and not deterministic enough**: seconds to minutes
per building, a commercial solver (Gurobi), and Wu's time-limited
"accept sub-optimal" makes the result depend on CPU speed and load, which
breaks our determinism rule outright. Bao's is an interactive design tool.
Both confirm the [Merrell](merrell-residential-layouts.md) verdict:
constructive generation ([Lopes](constrained-growth-floor-plans.md), split
grammars) plus validators, not optimisation.

## Licence

Papers only; Bao lists a demo/code zip on the project page (licence not
checked, not downloaded). Gurobi is commercial. Ideas are free.

## What to borrow

- **Wu's constraint list as validators** over the Building IR (cheap
  integer checks on our grid): room inside footprint; rooms don't overlap
  and cover the floor; min / max size and aspect per room type; **adjacency
  contact ≥ door width (1 block) + the door's 2-block height**; entrance
  room covers the door cell; named rooms touch an outside wall (kitchen,
  bedrooms for windows); stair cell identical on every floor.
- **Wu's ablation as a test list:** each dropped constraint produces a
  recognisable defect (cramped kitchen, corridor-only access, no path to
  rooms); name invariant failures after them in the viewer.
- **Wu's hierarchy for big buildings:** regions → aisles → rooms, the same
  shape as zones → rooms in Lopes; aisle = widen the gap between regions.
- **Bao's hard constraints as lot / massing validators:** inside parcel,
  wall thickness bounds (no 1-block slivers, no unlit deep plans), front
  edge on the street line. Soft terms (courtyard light, shadow on
  neighbours, storey histogram) can become **scores for "generate N, keep
  best"** with fixed N.
- **Size relaxation by 10 % steps until feasible** as a bounded,
  deterministic fallback for our constructive generators.

## What to avoid

- Any solver with a wall-clock time limit or non-deterministic search.
- A solver dependency at all (Gurobi licence, native code in the mod).
- Rotated boxes (Bao's θ) on a block grid.

## Verdict

Confirms optimisation is too slow and (with time limits) non-deterministic
for worldgen; borrow Wu's constraint set as IR validators and test names,
its region → aisle → room hierarchy, and Bao's parcel / thickness rules.

**Sources / what I couldn't verify:** read both full papers (Bao reduced
PDF from the project page, Wu from the author's site); supplementary
material not read. Nothing run; timings are the papers'. Bao's demo code
licence not checked.
