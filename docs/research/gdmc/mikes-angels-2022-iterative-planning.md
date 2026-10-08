# "Mike's Angels" GDMC 2022 winner: decentralised iterative planning

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

The 2022 GDMC winner (every judge ranked it first; record narrative 6.83 and
aesthetics 7.08 of 10): a walled medieval town with roads, houses, shops,
farms, church, watchtower, docks and a generated chronicle. Paper: van der
Staaij et al., "Believable Minecraft Settlements by Means of Decentralised
Iterative Planning", IEEE CoG 2023, https://arxiv.org/abs/2309.10871. Code:
the submission zip ("Medieval City Generator", Python on GDMC-HTTP), linked
from https://github.com/avdstaaij/terrain-adaptive-pcgml-in-minecraft
(`data-gen/generators/setup-generators.sh`, a Google Drive download).

## Approach

Terrain is reduced to 2D maps over the build area: height (trees removed and
recorded), slope, water, existing structures. A **blueprint simulation** runs
~60 "years": each step ~37 independent agents (road splitter/extender/
connector, plot agents, wall, farms, church, watchtower, trees, decorations)
sample ~200 candidate spots meeting hard constraints, score them with a
weighted fitness and commit the best above a threshold into shared maps
(road, plot, wall) that the others read next step. Only then are blocks
placed: buildings from 5-block cells of prebuilt wall/roof models, farms grown
by dilation along contours, roads and the wall by weighted A*.

## Procedural vs templated

Hybrid: layout fully procedural; buildings assemble small hand-made modules
procedurally, with wood swapped to the locally dominant species.

## How it hooks into worldgen

Post-hoc and global: all 256x256 terrain read first, agents iterate over the
whole area, Python `random` is global and unseeded (non-deterministic), and
houses sink a solid stone foundation down to ground under every footprint
cell. The loop would transfer only as a **region-level planner** run over
probes before any chunk is populated (one town per region; chunks then slice
the plan). Incremental per-chunk agents would not work.

## Licence

Paper: arXiv non-exclusive distribution licence. Submission code: **no licence
file** (all rights reserved). Study for ideas only; copy nothing.

## What to borrow

- **Plot fitness** (`plot_agent.evaluate_location`): penalise mean slope and
  water in the footprint and the difference between plot mean height and road
  height at the door; small bonus for area. Plots are cut perpendicular to a
  road from a door point and grown to max depth, so every building faces a
  street by construction. Cheap and portable.
- **Time as a planning axis:** each plot records `year_built`; growth order
  gives history for the chronicle and for styling (older core, newer edge).
- **Specialised placement agents sharing occupancy maps**, each with its own
  constraints (watchtower on high ground, docks on the water edge, farms on
  flat land outside the wall): an extensible pattern for the city planner.
- Town wall along the convex hull with terrain-weighted A*, following contours.
- Local material: dominant nearby tree species for wood roles.

## What to avoid

- Solid foundation pillars under houses (judges: "floating foundation platforms").
- 2D-only blueprints (no overhangs, terraces); buildings that ignore neighbours.
- Plot sizes hard-coded to one house module; global unseeded RNG.

## Verdict

Strong reference for city layout: adopt road-frontage plots and slope/water/height fitness; consider seeded agents for the region planner.

**Sources / what I couldn't verify:** Paper read through a summarising fetch
(agent counts, scores, runtimes ~134 s for 256x256 on a laptop CPU are from
it); spot-checked against `main.py` (agent list matches in kind),
`agents/plot_agent.py`, `agents/agent_simulator.py`,
`structures/foundation.py`, `structures/house.py` (head). Didn't run it or
read roof/building code in depth.
