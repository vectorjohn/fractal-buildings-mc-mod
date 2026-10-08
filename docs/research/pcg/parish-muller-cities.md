# Parish and Müller, "Procedural Modeling of Cities"

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Parish, Müller, SIGGRAPH 2001 ([PDF](https://cgl.ethz.ch/Downloads/Publications/Papers/2001/p_Par01.pdf),
DOI 10.1145/383259.383292). The original CityEngine: roads, blocks, lots and
buildings for a whole city from a few image maps.

## Approach

An **extended L-system** grows road segments, but the productions only emit an
*ideal successor* with unset parameters; a `globalGoals` function fills them
(highways steer toward population peaks by casting rays over a density map;
streets follow a pattern rule: grid "New York", radial "Paris", contour-following
"San Francisco", or none, blended by weight maps), and a `localConstraints`
function prunes or rotates segments out of water/parks, snaps ends to nearby
roads or crossings to close loops ("self-sensitive"), or deletes them. Blocks
(areas enclosed by roads) are recursively split along their longest roughly
parallel edges until lots are under an area threshold; lots without street
access or too small are dropped. Each lot gets one building from a stochastic
L-system (extrude and transform the lot outline, by zone type), with façade
detail done as layered-grid procedural textures.

## Procedural vs templated

Fully procedural layout; buildings are simple extrusions with texture detail
(the paper's own stated limitation: "functionality of the buildings can not be
represented"). Variety comes from the input maps and pattern blending.

## How it hooks into worldgen

Not worldgen. For us it is a candidate for the **city plan** step of a region
cell (ARCHITECTURE.md, "settlement region layer").
- **Voxel fit:** road graph and lot polygons are continuous; rasterise roads to
  blocks and prefer the grid rule (axis-aligned lots) so lots are rectangles on
  the block grid. Diagonal and curved roads alias badly at 1-block resolution.
- **Determinism:** growth is order-dependent (each segment checks the ones
  already placed). Fine if the whole city is grown in one deterministic pass with
  a priority queue, a seeded RNG and sorted spatial lookups.
- **Chunk independence:** not local. The network must be computed whole per
  region cell (cached, as ARCHITECTURE.md proposes), then each district/chunk
  reads it. Roads crossing a cell edge need agreed endpoints (e.g. gates at
  hashed points on cell borders).
- **Cost:** Manhattan street graph < 10 s, lots + 13 000 buildings ~10 min
  (2001 hardware). A Minecraft town of a few hundred lots should be milliseconds
  to low seconds, but must stay bounded since it runs at structure-start time.
- **Inputs:** the population and pattern maps become our own noise fields;
  "illegal area" becomes water/steepness from the terrain-height and biome probes.

## Licence

Paper only; CityEngine is now proprietary (Esri). No patent found in a web
search; not checked in a patent database. Ideas are free to use.

## What to borrow

- **Global goals / local constraints split**: proposal from style and density,
  then repair against terrain and existing roads. Keeps rules small and makes
  terrain adaptation one function (water, slope via probes).
- **Self-sensitive snapping** (intersect → crossing, near a crossing → extend)
  to get a net, not a tree.
- **Blended pattern rules** (grid core, organic outskirts) for coherence plus
  variety within one town.
- **Block → lot subdivision** with street-access and minimum-size filters;
  lots are exactly the input our building generator expects.
- Zone/height maps choosing building type per lot.

## What to avoid

- Implementing it literally as an L-system string rewriter; the logic is a
  priority-queue road grower (how most later reimplementations do it, unverified
  here).
- City-scale highway logic: Minecraft settlements are towns; scale the goals down.
- Buildings as extruded lots: use a grammar (see `split-grammars.md`,
  `cga-shape-grammar.md`) on each lot instead.

## Verdict

Good model for a region cell's road network and lots (goals/constraints,
snapping, block subdivision); compute per cell, never per chunk.

**Sources / what I couldn't verify:** read the full paper. No code; the claim
that later reimplementations use a priority queue is from memory and unchecked.
Rasterisation and cost estimates for Minecraft are my inference.
