# "Mike's Angels" GDMC 2022 winner: decentralised iterative planning

- **Links:** paper: van der Staaij, Prins, Prins, Poelsma, Smit,
  Müller-Brockhausen, Preuss, "Believable Minecraft Settlements by Means of
  Decentralised Iterative Planning", IEEE CoG 2023,
  https://arxiv.org/abs/2309.10871. Code: the 2022 submission zip ("Medieval
  City Generator", Python on GDMC-HTTP + an early GDPC), linked from
  https://github.com/avdstaaij/terrain-adaptive-pcgml-in-minecraft
  (`data-gen/generators/setup-generators.sh`, a Google Drive download).
- **Licence:** paper: arXiv non-exclusive distribution licence. Submission
  code: **no licence file** (all rights reserved by default). Read for ideas
  only; copy nothing.

## What it is

The 2022 GDMC winner (every judge ranked it first; record narrative 6.83 and
aesthetics 7.08 out of 10). A walled medieval town with roads, houses, shops,
farms, a church, watchtower, docks and decorations, plus a generated chronicle.

## Approach in three sentences

Terrain is first reduced to 2D feature maps over the build area: height (trees
removed and recorded), slope, water and existing structures. A **blueprint
simulation** then runs ~60 "years": each step, ~37 independent agents (road
splitter/extender/connector, plot agents, a wall agent, farm, church,
watchtower, tree and decoration agents) each sample ~200 random candidate
spots satisfying hard constraints, score them with a weighted fitness, and
commit the best if it beats a threshold, writing into shared maps (road, plot,
wall) that the other agents read next step. Only after the blueprint is done
are blocks placed: buildings from 5-block **cells** of prebuilt wall/roof
models arranged by a small per-building heightmap, procedural farms grown by
dilation along contours, and roads/walls by weighted A*.

## Procedural vs templated

Hybrid. Layout is fully procedural; buildings assemble small hand-made
modules (wall segments, roofs) procedurally, with wood type swapped to the
locally dominant species.

## How it hooks into worldgen

Post-hoc and global: all 256x256 terrain is read first, agents iterate over
the whole area, Python `random` is unseeded/global (not deterministic), and
houses sink a solid stone foundation column down to ground under every
footprint cell. For chunked worldgen, the *loop* transfers only if run on a
whole region before chunks are populated (a region-level planner over
probes, e.g. one city per region, then chunks slice the plan). Per-chunk
incremental agents would not work.

## What to borrow

- **Plot fitness** (from `plot_agent.evaluate_location`): penalise mean slope
  and water in the footprint, penalise difference between plot mean height and
  the road height at the door, small bonus for area. Plots are cut
  perpendicular to a road from a door point and grown to max depth, so every
  building faces a street by construction. Cheap and directly portable.
- **Time as a planning axis:** each plot records `year_built`; growth order
  gives a history the chronicle and styling (older core, newer edge) can use.
- **Specialised placement agents sharing occupancy maps**, each with its own
  constraints (watchtower on high ground, docks on water edge, farms on flat
  land outside the wall) — an easy, extensible pattern for our city planner.
- Wall routed along the convex hull of the town with terrain-weighted A*,
  so it follows contours.
- Local material: pick the dominant nearby tree species for wood roles.

## What to avoid

Solid foundation pillars under each house (judges called them "floating
foundation platforms"); 2D-only blueprints (no overhangs or terraces);
buildings that ignore their neighbours; hard-coding plot sizes to one house
module; global unseeded RNG.

## Verdict

**Strong reference for city layout.** Adopt the plot-from-road-frontage and
slope/water/height fitness ideas; consider the iterative-agents loop for the
region-level planner (Phase 5-ish), seeded per region.

## Couldn't verify

Didn't run it. Agent counts, scores and runtimes are from the paper as read
via a summarising fetch, spot-checked against `main.py` (agent list matches in
kind). Didn't read building/roof code in depth.
