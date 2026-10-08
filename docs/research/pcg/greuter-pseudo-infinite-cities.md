# Greuter et al. 2003, "pseudo infinite" cities

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Greuter, Parker, Stewart, Leach, "Real-time procedural generation of 'pseudo
infinite' cities", GRAPHITE 2003, 8 pages
([DOI](https://doi.org/10.1145/604471.604490),
[PDF mirror](https://svn.sable.mcgill.ca/sable/courses/COMP763/oldpapers/greuter-03-real-time.pdf)).
The earliest on-demand city that regenerates identically from (seed, cell)
in any order. Pointed to by the [Kelly & McCabe survey](kelly-mccabe-city-survey.md);
this note reads it as a precedent for our determinism model.

## Approach

- **Cells:** the plane is a square grid of 25 m cells; each holds buildings
  *or* street (how that is chosen isn't stated). Only cells within a 120°
  view cone and N rings of the camera are generated ("view frustum
  filling"); an LRU cache of display lists keeps recent ones (minAge 100,
  maxAge 500 frames, capacity 1000) and regenerates evicted ones on return.
- **Seeding:** one 32-bit seed per building,
  `seed = hash(x ^ hash(z ^ citySeed))` with Thomas Wang's 32-bit mix, then a
  Park–Miller LCG for all draws. **Why hash:** with raw neighbouring seeds the
  LCG's first outputs were similar, giving "recognisably similar buildings".
- **Building:** floor plans built top-down: a random polygon, then each
  iteration unions another random rotated polygon centred on a random vertex
  of the previous plan; each plan is extruded for a random height below the
  last, so towers step out as they go down (wedding-cake). Scaled to the
  cell. Textures: 10 window tiles, repeats from façade length / window width.
- **Cost:** ~2–10 ms per building (2003 P4); generation dominates, cache
  overhead negligible.

## Procedural vs templated

Fully rule-generated; varies footprint, step heights, texture. One building
type (office tower), no interaction between cells, so low variety; the paper
says so.

## How it hooks into worldgen

Not worldgen, but the same contract Minecraft imposes: any cell, any order,
same result, nothing global stored. It works because **nothing crosses a cell
border**: each cell is self-contained and the street grid is fixed. Our
region-cell plan ([ARCHITECTURE](../../ARCHITECTURE.md), "settlement region
layer") is the generalisation: hash(seed, region) → cached plan, which can
contain cross-lot structure (roads, districts); then hash(seed, lot, purpose)
→ building. [The Lost Cities](../mods/the-lost-cities.md) is the Minecraft
version of Greuter's model (chunk = cell). Their LRU cache ≈ our bounded,
thread-safe plan cache; regenerating on eviction is safe only because output
is a pure function.

## Licence

Paper only, © ACM; no code released. Wang's mix function is public domain
(widely reproduced). Ideas are free.

## What to borrow

- **Evidence for the hashing rule** (CLAUDE.md): hash (seed, x, z, purpose)
  through a strong mixer; never seed a weak PRNG with raw or XOR-combined
  coordinates. `java.util.Random` has the same weak-first-output problem as
  their LCG; use a SplitMix64-style finaliser to derive sub-seeds.
- **Purpose tags:** they nest one hash per coordinate; we also need a purpose
  argument so the footprint, palette and roof streams of one lot don't
  correlate.
- **Generate on demand, cache, regenerate freely:** the plan cache may evict
  at any time; a test that regenerates a cell after eviction and compares
  bytes is a cheap determinism check.
- **Stepped extrusion with growing footprints** (each lower stage = union of
  the upper plan and a new primitive) as a skyscraper massing rule on our
  grid: upper floors always supported, so no floating blocks by construction.

## What to avoid

- Cells that never talk to each other: no continuous streets, districts or
  skyline gradient. Keep cross-lot structure in the region plan.
- Arbitrary rotated polygons; on blocks use axis-aligned rectangles.
- Combining coordinates with XOR before hashing (x ^ z symmetry, collisions
  for swapped or equal coordinates in other schemes); mix each input in turn.

## Verdict

Confirms our determinism model (hashed per-cell seed, generate on demand,
cache is disposable) and the reason for strong hashing; its self-contained
cells are too limited for cities, which is why we add the region plan.

**Sources / what I couldn't verify:** read the full paper (McGill mirror).
Didn't run anything; timings are the paper's. How cells are assigned to
streets vs buildings is not described in the paper.
