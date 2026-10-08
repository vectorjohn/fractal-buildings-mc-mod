# "The AI Settlement Generation Challenge in Minecraft: First Year Report" (survey)

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Salge, Green, Canaan, Skwarski, Fritsch, Brightmoore, Ye, Cao, Togelius;
arXiv 2103.14950 (2021), https://arxiv.org/abs/2103.14950. The organisers'
write-up of the **first (2018) GDMC round**: four entries, eight judges,
scores, how each entry worked, and the open problems. The criteria and
judging process are covered in [the competition note](gdmc-competition.md);
this note is about the approaches.

## Approach

The paper sorts the entries into two families of placement:

- **Bottom-up incremental** (1st, Skwarski, 4.38): a fixed build order
  (houses, plazas, farms); each structure is placed by scoring 5000 random
  positions on elevation, layout fit (soft rules such as "plazas want
  surrounding houses") and distance to what exists. After each placement, A*
  routes a road to the nearest neighbour; steps of 2+ blocks and hill
  "corners" are impassable, so some links are never built. Judges rated it
  the most adaptive (5.42), mainly for placing buildings on flat land.
- **Edge map** (3rd, Brightmoore): any 2-block height step counts as an edge;
  plots are regions with no edge inside them. Large London-style buildings,
  no roads.
- **Top-down partitioning** (2nd Fritsch, 4th Ye/Cao): repeated bisection
  with a road strip between the halves; plots are assigned to "builders" by
  fitness (centrality, road access), and road quality fades from stone to
  gravel to dirt towards the edge. Ye/Cao flattened the whole site (CGA-like
  add/cut grammar) and scored 0.96 on adaptability.

## Procedural vs templated

All four generate buildings by rule (parameterised grammars, modules);
adaptation is mostly categorical: dominant wood/stone, biome, crops.

## How it hooks into worldgen

Not applicable (survey). For us: **top-down partitioning** maps onto a
deterministic region-cell planner. **Bottom-up incremental** placement is
order-dependent across the whole area, so it only works inside one cached
region plan.

## Licence

arXiv non-exclusive distribution licence (all rights reserved by the
authors). Ideas only. Entry code was not reviewed here.

## What to borrow

- **Open problems to check our cities against:** buildings and doors facing
  water (Fig. 8), towns split by rivers with no bridge (Fig. 9),
  no "big-picture" adaptation (a pass suggests walls and towers, a coast
  suggests a harbour), and building variety that is not tied to districts.
- Unjumpable-step edge map as a cheap buildability mask.
- Road surface quality by distance from the centre (stone, gravel, dirt).
- Roads must avoid diagonal climbs. Encoding what the player can walk is the
  "embodied" constraint they praise.

## What to avoid

- Flattening the site (lowest adaptability in the field).
- Unbounded plot sizes (tiny and huge houses, per the judges).
- Rejecting road links outright: gaps in connectivity were visible.

## Verdict

Useful early survey: two placement families (bottom-up scoring vs top-down partition) and a checklist of failures (water, bridges, big-picture adaptation); historical, read once.

**Sources / what I couldn't verify:** full PDF via `pdftotext` (sections
4–6 read closely; the conclusion skimmed). Entry code and the Brightmoore
blog post not followed. Whether a journal version exists not checked.
