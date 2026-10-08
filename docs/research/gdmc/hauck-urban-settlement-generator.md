# UrbanSettlementGenerator (Hauck and Aranha, GDMC 2019): partition, rank by steepness, MST roads

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A 2019 GDMC entry from the University of Tsukuba (Eduardo Hauck, Claus
Aranha) that builds a modern town: apartment blocks in the centre, houses on
the outskirts, all joined by roads. https://github.com/ehauckdo/UrbanSettlementGenerator
(last commit 2019-10; an MCEdit filter in Python 2, so it is dead tooling).
The repo includes a 2-page write-up (`MinecraftSettlementGenerator.pdf`).
Its placing in 2019 isn't checked here.

## Approach

1. Heightmap of the first solid block. A central rectangle (a percentage of
   the area) becomes downtown; the rest is split into four neighbourhoods.
2. Each of the 5 sections gets **binary or quadtree space partitioning**
   (picked at random) 100 times, and every leaf goes into one pool of
   candidate lots. Lots with water or lava on their perimeter, or below a
   minimum size, are dropped.
3. **Steepness** = sum over the lot of |h - modal height|; the pool is sorted
   by it. Lots are taken flattest first, skipping any that overlaps a lot
   already taken, until each section has its quota.
4. Each lot is flattened to its modal height (grass), then a house or
   apartment block is built. The door faces the map centre.
5. **Roads:** an MST over lot entrances (Manhattan distance), each edge
   routed by A* with step cost 1 + dh², widened to 3 blocks, and filled
   down to the ground where the road crosses air.

## Procedural vs templated

Rule-generated buildings, but hard-coded (one house recipe, one apartment
recipe with 4–10 floors, fixed furniture). Little variety between outputs.

## How it hooks into worldgen

An MCEdit filter over one selected box. Uses numpy `RandomState` per named
stream (default seed 10, others unseeded). The parts are local and bounded:
partition-then-rank works per region cell from height and water probes, and
MST + A* is fine inside one cached town plan. The over-sampling (100
partitions per section, then greedy non-overlap) costs CPU, but it is
deterministic if seeded.

## Licence

**MIT** (Copyright 2019 Eduardo Hauck, Claus Aranha;
[LICENSE.txt](https://github.com/ehauckdo/UrbanSettlementGenerator/blob/master/LICENSE.txt)).
We may study and reuse the code with attribution. Python 2/MCEdit makes it a
reference for ideas, not a source to port.

## What to borrow

- **Over-sample partitions, then pick flattest non-overlapping lots:** a
  simple way to get terrain-adaptive lots without a clever partitioner.
- **Modal-height steepness** (not the mean) as the lot cost and the pad
  height: a lot with one boulder still scores as flat.
- Road step cost **1 + dh²**, so roads go around a hill instead of over it.
- Downtown/outskirts sections that switch the building type (density
  gradient by distance from the centre).

## What to avoid

- Flattening every lot to its modal height with grass, so cliffs appear at
  lot edges; roads filled down to the ground under air (pillars).
- Doors always facing the map centre, ignoring roads and slope.
- An MST alone gives a tree with no loops, so travel between leaves is long
  and indirect.

## Verdict

Small, MIT, easy to read: borrow the over-sample-then-rank-by-steepness lot picking and the 1 + dh² road cost; dated tooling, weak buildings.

**Sources / what I couldn't verify:** README, write-up PDF (read in full),
`LICENSE.txt`, `Algorithms/SpacePartitioning.py`, `Helper/RNG.py`,
`Helper/utilityFunctions.py` (steepness check). Didn't run it (needs MCEdit
on Python 2). 2019 rank and judges' comments not found on the wiki page.
