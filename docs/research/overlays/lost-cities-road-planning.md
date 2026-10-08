# The Lost Cities: street and highway planning

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

How [Lost Cities](https://github.com/McJtyMods/LostCities) plans roads inside
cities (`HIERARCHICAL_GRID_V1`, `worldgen/street/`) and between them
(`INTERCITY_NETWORK_V1`, `worldgen/highway/`), both default for new worlds.
Read branch `26.2_fabric` at `7e7d102` (Oct 2026); shipped since
11.0.x (26.2). `HIGHWAY_MAJOR_CITY_PLAN.md` is a **plan, not built**. City
placement: [lost-cities-city-placement.md](lost-cities-city-placement.md);
generation: [../mods/the-lost-cities.md](../mods/the-lost-cities.md).

## Approach

**Streets: a global road field, clipped to the city mask.** Primary road
candidates are infinite lines every 8 chunks per axis (seeded offset); each is
on with chance 0.45, and every 4th candidate is forced, so blocks are 8–32
chunks. Each block (keyed by its west/north primaries) hashes a density,
picks 0–2 secondary lines per axis (hash-sorted candidates, ≥ 4 chunks apart,
≥ 3 from edges), and each sub-cell may get one tertiary dead-end spur (chance
0.4, 2–5 chunks). The planner "knows nothing about cities": a chunk is road
only if it's city, has a city neighbour, and isn't overridden by a building.
A separate bridge planner spans short water gaps along primaries.

**Highways: hub graph on planning cells.** The world is cut into 128×128-chunk
planning cells; each cell has at most one **hub**: the best city-potential
sample on a 16-chunk lattice (potential ≥ 0.2, quantised, hash tie-breaks).
Each hub ranks hubs within ±2 cells (64–320 chunks away, route ≥ 40) and
selects up to 2, one per compass sector first; an **edge exists only if both
ends select each other**; the smaller key owns the route. Routes are one or
two axis-aligned segments (L shape), choosing the bend that avoids railway
corridors, then the one crossing less city potential. A chunk asks the routes
owned by cells within ±2 whether they cover it. Highway level comes from the
two cities' levels (average by default).

## Procedural vs templated

Layout is fully procedural; road and highway pieces are authored 16×16 parts.

## How it hooks into worldgen

Inside the per-chunk `RAW_GENERATION` feature. Every query is a **bounded,
pure function** of (seed, dimension, profile, coordinates): `mix64` hashes with
per-purpose salts, `floorDiv`/`floorMod` for negatives, explicit ordering for
ties, caches that "must never affect results". City potential for remote
hubs uses `ApproximateCityPotential` (the city field plus height/biome
modifiers), never `BuildingInfo`, which avoids recursion. Two caveats:
the hub decision per cell is **persisted in world SavedData** (and the chosen
modes are), so results depend on the save as well as the seed; and potential
still uses the seed-free city-centre hash noted in the placement note.

## Licence

MIT ([LICENSE](https://github.com/McJtyMods/LostCities/blob/26.2_fabric/LICENSE)).
Study and borrowing are allowed with attribution.

## What to borrow

- **Hierarchy by nested hashed grids**: arterials as global lines (so they
  continue between neighbouring cities and across cell edges), local streets
  per block, spurs per sub-cell. Any chunk resolves its road type with O(1)
  hashes; no flood fill, no order dependence.
- **Field first, then clip to the settlement mask**: separates "where roads
  would be" from "where the city is", so cities that merge share streets.
- **Hub-per-cell graph with mutual selection** for intercity roads: bounded
  search, canonical edge owner, deterministic. Sector diversity avoids all
  links going one way.
- The plan doc's checklist (stable hash ties, quantised scores, caches never
  change results, bounded neighbourhoods) as a ready-made review checklist for
  our planner.
- Its known failures, which the plan doc lists: one-hub-per-cell misses or
  duplicates cities near cell edges; mutual selection leaves hubs
  unconnected. Our region cells own one settlement each, which avoids the
  first; a "mandatory first edge" rule avoids the second.

## What to avoid

- Axis-aligned grid and L-shaped highways only: reads as a chunk grid; we
  want curved, terrain-following roads (an L or a straight line can be a
  skeleton that we then bend).
- Persisting planning decisions in the save: we need seed + position only.
- Highway planning cost: the 11.0.2 changelog is mostly highway-planning
  performance fixes (cold hub scans, locks, thread pools). Keep our intercity
  graph coarse and cheap.

## Verdict

Strong model for deterministic road hierarchy: hashed nested grids clipped to
the city mask, plus a hub-per-cell mutual-selection graph between cities;
borrow the structure, not the rectilinear geometry.

**Sources / what I couldn't verify:** read `HierarchicalStreetPlanner`,
`StreetPlannerSettings`, `EffectiveStreetResolver`, the head of
`HierarchicalBridgePlanner`, `IntercityHighwayPlanner` (hub, selection,
route, chunk query), `ApproximateCityPotential`, `HighwayHubPersistence`,
profile defaults, `HIGHWAY_MAJOR_CITY_PLAN.md`, `changelog.txt`. Not run; no
screenshots seen; didn't read the legacy (Perlin) highway mode or how highway
chunks are rendered and levelled.
