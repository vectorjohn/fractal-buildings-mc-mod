# JEB_GEN_ (PositionNotLoaded, GDMC 2023 winner): score-and-reject site selection

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

The 2023 GDMC winner (5.98 overall; adaptability 6.40, the best of that year
by over 2 points), by four students at National Taiwan Normal University.
Python on GDPC + GDMC-HTTP, Minecraft 1.19.2, ~3.3k lines. Code and an
11-page write-up are linked from the
[2023 results page](https://gendesignmc.wikidot.com/wiki:2023-settlement-generation-competition)
(Dropbox zip); the repo it names, https://github.com/NTNU-GDMC/GDMC, is
public (last push 2024-04). Shares the agent pattern with
[Mike's Angels](mikes-angels-2022-iterative-planning.md); see
[the competition note](gdmc-competition.md) for the criteria.

## Approach

Terrain is reduced to 2D maps: mean and standard-deviation height (from a
2D prefix-sum array over the no-leaves heightmap, so any rectangle's mean is
O(1)), liquid, resources (wood, stone, "artificial" blocks), and seven biome
classes. A round-based loop runs build agents (chalet, desert house, sawmill,
farm, quarry, forge, church) and a road agent against a 2x2-cell blueprint;
a "settlement level" unlocks more buildings as simulated resources accrue.
Each build agent samples free rectangles and keeps the best score. Roads
connect each new building to an existing one by A*.

## Procedural vs templated

Templated: hand-built NBT houses, three upgrade levels each, with wood
swapped for the local species and sandstone in deserts. Layout is procedural.

## How it hooks into worldgen

Post-hoc over a whole build area, global unseeded `random`, and a 465 s
wall-clock cutoff that changes the result. Not deterministic. What adapts it
to terrain is **where it refuses to build**, not how it builds:

- Site score (`agent_generator.py`): reject if water, existing structures,
  near the edge, or wrong biome tag; reject if stddev of height is above a
  threshold; reject if the fill needed below the mean height exceeds 3 blocks
  per footprint cell; otherwise score = 1/stddev + biome/resource fit.
- Road A*: steps of 2+ blocks are impassable; height change weighted x2,
  water x10; cost divided by (1 + "hot value") so reused roads become
  arteries; search pruned to an ellipse (AC + CB > 2AB discarded).

## Licence

**No licence** in the zip or on the GitHub repo (API reports none): all
rights reserved. Ideas only; copy nothing.

## What to borrow

- **Prefix-sum height and height² maps** for O(1) mean/stddev of any
  candidate lot: cheap enough to run inside a region-cell planner.
- **Hard rejects before soft scores**: a fill budget (cells x 3 blocks) and a
  stddev cap keep houses off cliffs; judges rewarded exactly this.
- **Road cost: no 2-block steps, weighted dy, water penalty, reuse discount,
  ellipse bound.** All local and bounded, so it runs per region from probes.
- Biome tags per building type (desert vs non-desert, forest for sawmills).

## What to avoid

- 2D roads raised to door height leave gaps under the road (their own
  future-work note); cobblestone fill to mean height under each house.
- Simulated resources and levels: whole-run state, no worldgen analogue.
- Wall-clock cutoffs and unseeded sampling; templates with three fixed levels.

## Verdict

Best-adaptability entry adapts by rejection: borrow prefix-sum lot stats, fill/stddev caps and the weighted A* road cost; no licence, ideas only.

**Sources / what I couldn't verify:** write-up PDF (read in full);
`agent_generator.py`, `analyze_util/basic.py`, `agent.py`, `core.py`
(blueprint), `nbt_builder.py` from the competition zip. Didn't run it. Judges'
comments not read (spreadsheet); whether the GitHub repo matches the zip not
checked.
