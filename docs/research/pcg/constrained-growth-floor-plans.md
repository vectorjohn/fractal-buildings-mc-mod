# Constrained growth floor plans (Lopes et al. 2010), and grid room layout

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Lopes, Tutenel, Smelik, de Kraker, Bidarra, "A constrained growth method for
procedural floor plan generation", GAME-ON 2010, 6 pages
([PDF](https://publications.graphics.tudelft.nl/papers/584)). A fast,
grid-based, constructive alternative to [Merrell et al.'s optimisation](merrell-residential-layouts.md).
Its related-work section summarises the other light methods (below).

## Approach

Input: footprint rasterised to a grid (e.g. 0.5 m cells; non-rectangular
footprints mark outside cells), a room list with zone (public / private /
hallway) and **area ratio**, adjacency and connectivity constraints.
1. **Hierarchy:** split the house into zones first, then each zone into rooms
   (same algorithm per level); adjacency allowed only within a zone or to a
   parent zone.
2. **Seeds:** a weight grid (0 outside, 1 at cells far enough from walls for the
   room's expected size, high near already-placed rooms it must touch); pick a
   seed cell, zero the weights around it.
3. **Growth:** repeatedly pick a room with probability ∝ its ratio and grow it
   by the **longest free line** that keeps it rectangular, up to its max size;
   then a second pass allows **L-shapes** (U-shapes prevented); then leftover
   cells go to the neighbouring room with most contact.
4. **Doors:** declared connections first, then hallway → every adjacent public
   room, private → an adjacent public room, then a **reachability check** from
   the hallway that adds doors along adjacencies until every room is reached.
   Stairs: a stair room on floor 1 is copied to the same cells on upper floors,
   which then grow around it.
If an adjacency constraint ends up unmet, **restart**. Run N times and pick
the best by a score (fewest corners, smallest hall). < 100 ms simple, ~1 s
complex (2010 hardware).

Lighter relatives it cites: **Hahn et al. 2006** (offices: place stair/lift
shafts, split floors by hallway, subdivide rest; generated just in time from a
stored seed), **Marson & Musse 2010** (squarified treemaps by zone, then an A*
corridor through rooms needing access), **Martin 2006** (room graph grown
from the front door, then "pressure" expansion).

## Procedural vs templated

Fully generated from a short room spec per building type; specs are the
hand-written part. Varies: seed positions, growth order, shapes (rect / L).

## How it hooks into worldgen

Not worldgen; per-building, after massing. Voxel fit is **excellent**: the
algorithm *is* a grid algorithm, so a cell = 1 block (or 2×2 to leave room
for 1-block walls). Wall cells come from room boundaries; doors need a shared
boundary ≥ 1 block wide plus 2 high.
- **Determinism:** all draws (seed cell, room pick, tie-break among equal
  lines) from a hashed sub-seed; restarts and the N alternatives get the
  attempt index in their seed. Iterate rooms in list order, never a hash set.
- **Cost:** tiny for Minecraft-sized houses (a 12×10 floor is 120 cells).
- **Invariants:** reachability is **by construction** (final door pass), not
  a soft cost; footprint containment too (outside cells have weight 0).

## Licence

Paper only, no code. Patents: not searched. Ideas are free. (The
graphics.tudelft.nl host didn't resolve from here; the PDF came from
publications.graphics.tudelft.nl.)

## What to borrow

- **The default interior generator for houses**: rasterised footprint →
  zone split → seeded rect-then-L growth → door pass with a reachability sweep.
- **Zones then rooms** (public/private/hall) gives the privacy gradient cheaply.
- **Generate N, score, keep best** with N fixed and seeds derived from the
  attempt index: deterministic and bounded.
- **Stair cell copied up** through floors; upper floors grow around it.
- Hahn's **shafts first** for tall buildings (towers, apartments).

## What to avoid

- Unbounded restart loops; cap attempts and fall back to "one room per zone".
- Its random door position within a wall; prefer near corners (Merrell) or
  aligned with the entrance axis.
- Uncontrolled L-shaped rooms where function needs a rectangle (the paper's
  own garage example); add per-room aspect limits.

## Verdict

Best fit for our interiors: fast, grid-native, reachability by construction;
use it after the shell grammar, with Merrell's program ideas as the input spec.

**Sources / what I couldn't verify:** read the full Lopes paper. Hahn,
Marson & Musse and Martin are summarised from its related work, not read.
No implementation run; timings are the paper's.
