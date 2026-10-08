# Townscaper (Oskar Stålberg)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A 2020 toy town-builder by Oskar Stålberg (published by Raw Fury): the player
only adds or removes a block and picks its colour; the game decides what that
block becomes (house, arch, stair, roof, garden, bridge). Best write-up:
[Game Developer, "How Townscaper works"](https://www.gamedeveloper.com/game-platforms/how-townscaper-works-a-story-four-games-in-the-making);
grid recipe re-implemented in Boris the Brave's
[Sylves tutorial](https://boristhebrave.com/docs/sylves/1/articles/tutorials/townscaper.html).

## Approach

1. **Irregular grid:** a hexagon filled with triangles; random pairs merge into
   rhombi; everything is subdivided into quads (a leftover triangle becomes
   three quads); the mesh is relaxed. The infinite world is a hex grid of such
   chunks, each seeded by its hex coordinate, with relaxation run across chunk
   borders. Extruded upward into layers.
2. **Dual / corner modules:** the player's state is one bit (+ colour) per grid
   *vertex*; geometry is placed per *cell* from its corner bits, the
   marching-cubes / corner-Wang-tile idea. Few hand-made modules cover all
   corner configurations and deform to fit each irregular cell.
3. **WFC on top:** where several modules fit a corner configuration, a
   WaveFunctionCollapse-style solve picks variants that agree with neighbours,
   then a separate decoration pass adds detail. Contradictions are allowed to
   "fail silently" (Bad North instead restarted the whole solve).

## Procedural vs templated

Hand-made modules (hundreds; a full re-theme was said to need ~500), assembled
fully by rules from a sparse occupancy field. Every town differs in massing;
the vocabulary (roofs, windows, colours) is fixed, which is exactly why it reads
as coherent.

## How it hooks into worldgen

Not worldgen; a technique. Voxel mapping:
- **Our grid is regular**, so the irregular grid itself doesn't transfer (a
  Minecraft block can't be skewed). What transfers is the **two-level split**:
  a coarse occupancy field (which cells are "building") chosen by the planner,
  and a local rule that turns each cell's neighbourhood into geometry.
- **Corner rules are chunk-friendly:** a cell's geometry depends only on its
  own and adjacent occupancy, so any region can be rendered given a 1-cell
  halo. The grid's own chunking (seed per hex, bounded relaxation) is a worked
  example of order-independent lazy generation.
- **The WFC layer is not**: same issues as in [wfc-model-synthesis](wfc-model-synthesis.md).
  Keep it inside one building's bounded solve.

## Licence

Townscaper: proprietary, no source. Sylves (grid library, C#): MIT
([LICENSE.txt](https://github.com/BorisTheBrave/sylves/blob/main/LICENSE.txt)).
Ideas only; no patents known (not searched).

## What to borrow

- **Occupancy first, form second:** a building as a coarse 3D cell set (e.g.
  4×4×5-block cells), with roofs, overhangs, arches, stairs derived from the
  neighbourhood of each cell. A small, testable IR stage between massing and
  blocks; closed roofs become a local rule ("top face of an occupied cell with
  empty above gets a roof module").
- **Constrained player-like input:** the planner only decides occupancy and a
  colour/material role per cell; charm comes from the derivation rules. Good
  match for abstract material roles.
- **Graceful failure:** a contradiction degrades to a plain default module,
  never a hole; mirrors the fallback rule already proposed for WFC.
- Decoration as a separate, later pass (flowers, chimneys, lamps).

## What to avoid

- Irregular grids and mesh deformation: no voxel equivalent.
- Relying on the player for global layout: Townscaper has no streets, plots
  or interiors; reachability and lot boundaries must come from our planner.
- A module count in the hundreds before anything looks good; start with the
  minimal corner set over roles.

## Verdict

Borrow the occupancy-grid + neighbourhood-derived modules split as a massing →
blocks stage; skip the irregular grid; WFC only as a bounded variant picker.

**Sources / what I couldn't verify:** read the Game Developer article, Sylves
Townscaper tutorial and Boris the Brave's rhombus-tiling post. Did not watch
Stålberg's talks ("Beyond Townscaper", EPC 2018 Bad North talk) or read his
tweet threads directly; module counts and the corner-bit details are from the
article and secondary sources, not the game's code.
