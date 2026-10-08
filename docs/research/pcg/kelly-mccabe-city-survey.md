# Kelly & McCabe survey of city generation (2006), and small gap-fillers

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

G. Kelly, H. McCabe, "A survey of procedural techniques for city
generation", ITB Journal 14, 2006, 16 pages
([PDF on citygen.net](https://www.citygen.net/files/Procedural_City_Generation_Survey.pdf),
[journal page](https://arrow.tudublin.ie/itbj/vol7/iss2/5/)). An early,
short survey: primitives (fractals, L-systems, Perlin noise, tiling, Worley
cells), then five city systems scored on seven criteria. Read here only for
what our notes don't already cover.

## Approach (what it covers that we don't have)

Already in our notes: Parish–Müller ([note](parish-muller-cities.md)) and
split grammars ([note](split-grammars.md)). New:
- **Greuter et al. 2003, "Undiscovered City"** (real-time, infinite city):
  a uniform road grid; **each building's seed is a hash of its grid cell
  coordinates** (plain neighbouring integers gave look-alike buildings, so
  they hash), buildings generated on demand for the view frustum and kept
  in an LRU cache. Buildings: stacked extrusions, each lower section's
  plan = the one above plus another primitive. Weak variety (one office
  type, 10 textures, one grid).
- **Lechner/Watson et al. "CityBuilder"** (NetLogo agents): road
  *extenders* reach unserved land, *connectors* add a link where network
  distance ≫ straight distance; developer agents (residential, commercial,
  industrial) buy land by their own value rules. Good grid ↔ organic
  blending, but village scale took ~15 min and is order-dependent
  simulation.
- **Sun et al. 2002, template roads:** raster / radial / mixed patterns
  grown from a centre; or roads = Voronoi edges of points sampled from a
  population map. Each step casts fixed-length rays within a "freedom
  factor" angle and takes the legal one with least elevation change.
- **Criteria:** realism, scale, variation, input, efficiency, control,
  real-time. Overlaps the GDMC rubric
  ([gdmc-competition](../gdmc/gdmc-competition.md)); "variation" and
  "input" (no hand data needed) are worth adding to our gallery reviews.

## Procedural vs templated

Survey; covers both. Greuter is fully rule-based but low variety; Sun is
pattern templates bent by terrain.

## How it hooks into worldgen

Not worldgen. **Greuter is the only surveyed system with our constraint**:
generate any part on demand from (seed, cell coordinates), in any order.
It works because its layout is a fixed grid. Our region-cell plan (one
deterministic plan per cell, cached) generalises it: hash → cell plan,
hash → building. Sun's least-elevation stepping is a cheap, local road
heuristic that suits the height probe.

## Licence

Survey text © authors / ITB; ideas only. The authors' own later tool
(CityGen, citygen.net) not checked.

## What to borrow

- **Hash (seed, coordinates, purpose) for every building seed**, never raw
  coordinates: Greuter saw visible correlation without it. Already our rule
  (CLAUDE.md); this is the evidence.
- **Least-elevation road stepping within a freedom angle** (Sun) for paths
  between settlements or outskirts lanes.
- **Network-distance vs straight-distance shortcut rule** (CityBuilder
  connectors) to add loops to a tree-like road net, run once per cell.

## What to avoid

- Agent simulation at worldgen time (slow, order-dependent).
- Single-pattern grids: the survey's main realism complaint.

## Folded-in short notes (not read in full)

- **Marson & Musse 2010**, squarified treemaps for floor plans (IJCGT,
  [DOI 10.1155/2010/624817](https://doi.org/10.1155/2010/624817); PDF
  blocked here, 403): split the footprint into zones by area with the
  squarified-treemap layout (rooms near square), then rooms within zones,
  then an A* corridor through rooms that lack access (from the Lopes
  summary and the abstract). Rectangles only, room aspect "for free";
  close to OBB bisection. **No separate note needed**: the
  [constrained-growth](constrained-growth-floor-plans.md) note is our
  interior default; treemaps are a fallback for simple rectangular houses.
- **Hahn, Bose, Whitehead 2006**, "Persistent realtime building interior
  generation" (Sandbox '06): interiors generated lazily, only the part near
  the player, consistent whichever way you walk in, with player changes
  stored separately and reapplied (abstract only). Office method: shafts,
  hallways, then rooms. **Worth its own note later** only if we ever
  generate interiors lazily (e.g. tall towers); the shafts-first rule is
  already recorded in the constrained-growth note.

## Verdict

Low value as a survey now (2006, five systems); its one useful addition is
Greuter's hashed-cell, on-demand city as precedent for our determinism
model, plus Sun's least-elevation road stepping.

**Sources / what I couldn't verify:** read the full survey. Greuter, Sun and
CityBuilder are summarised from the survey, not their papers. Marson & Musse
and Hahn: abstracts and secondary summaries only.
