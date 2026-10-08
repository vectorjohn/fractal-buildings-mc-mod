# "Gaussianly Filtered" (GDMC 2025 winner): attracting paths and blurred grading

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

The 2025 GDMC winner (5.95 overall; best adaptability 5.69 and aesthetics
6.54 that year), a solo entry by Isaac Braam:
https://github.com/IsaacBraamGit/gdcm2025 (last commit 2025-07). Python on
GDPC/GDMC-HTTP, ~2.9k lines, self-described as "functional but not pretty".
No paper; the [2025 results page](https://gendesignmc.wikidot.com/wiki:2025-settlement-generation-competition)
quotes: paths from "a water like simulation"; the rest "gaussian filters,
image processing, mathematical functions, artsy patterns".

## Approach

1. **Site:** a slope map; pick the 260x260 window with least total slope.
2. **Buildings,** greedily by type (largest first, a capped count each):
   box-filtered mean slope over footprint + border, take the minimum, stop
   when above a threshold (0.8); the first building also pays a
   distance-from-centre penalty. **Orientation:** compare edge heights with
   heights 10 blocks out on each side; the door faces the steepest downhill
   side, or the centre on flat ground.
3. **Grading:** platform at mean footprint height, then a **weighted Gaussian
   blur** (sigma 1.8, radius 6) of the heightmap where the platform and a
   2-block halo carry high weight with falloff; terrain is cut or filled to
   the result, so slopes ease into the pad instead of ending in a wall.
4. **Paths ("water sim"):** A* from each door to its nearest door and one
   random door (kept only if cost <= 1.5x Manhattan). Then 30 iterations:
   rasterise all paths, Gaussian-blur them (sigma 2), subtract blurred house
   mask x4 and slope x0.2, and move each interior path point to its
   highest-valued neighbour. Paths **attract each other and merge** like
   rivulets into shared channels while sliding off houses and slopes.
5. Path heights blurred again (sigma 3), half-steps become slabs, lamp posts
   spaced >= 8 apart.

## Procedural vs templated

Templated buildings: eight hand-built houses, a quarry and a large
"collection" building, stored as block CSVs. Layout and grading procedural.

## How it hooks into worldgen

Post-hoc, whole area, global unseeded `random`; greedy placement and path
relaxation are global and order-dependent. **Transfers:** footprint
statistics, the downhill-facing rule and the weighted-blur grading are all
local to a lot plus a margin, so a region-cell planner can compute them from
height probes. Path relaxation would work inside one cached region plan
(seeded, fixed iteration count), not across independent chunks.

## Licence

**CC BY-NC 4.0** (licence text file in the repo root). Non-commercial clause
is an extra condition for our purposes; **ideas only, copy no code**.

## What to borrow

- **Weighted-blur grading** around a pad: one convolution gives a smooth
  cut/fill target with the pad held fixed. Bounded, deterministic, cheap.
- **Door faces downhill** (or the town centre when flat): one line of logic
  that makes houses read as sited, not dropped.
- **Path bundling by blurred attraction:** after a road graph is laid,
  relax edges toward each other to merge parallel spurs into shared lanes.
- Slab steps for 1-block path rises; lamp spacing by minimum distance.

## What to avoid

- Fixed 4-block solid foundation of the dominant ground block under every
  pad; 30 blocks of air cleared above each footprint.
- Greedy biggest-first placement into the single flattest window: towns
  cluster, never adapt to two valleys.
- Path relaxation without a cap on drift (paths can leave the A* corridor).

## Verdict

Best 2025 terrain fit comes from cheap local filters (blurred grading, downhill-facing doors) that port to a region planner; path bundling is a nice extra; CC BY-NC, ideas only.

**Sources / what I couldn't verify:** results page; `Water_sim.py`,
`find_buildings.py` (`get_placements`, `get_downhill_sides`), `_RunMe.py`
(`place_build`, path finishing) read. Didn't run it or look at judges'
comments; `build_map.py` (an older procedural-house path) only skimmed and
may be unused. Whether "water like" means anything beyond this attraction
loop: not confirmed beyond the code.
