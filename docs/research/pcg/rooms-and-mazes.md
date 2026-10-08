# Rooms and mazes (Nystrom 2014): connectivity repair on a grid

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Bob Nystrom, "Rooms and Mazes: A Procedural Dungeon Generator", blog post,
2014 ([post](https://journal.stuffwithstuff.com/2014/12/21/rooms-and-mazes/)),
from his roguelike Hauberk
([code at the post's commit](https://github.com/munificent/hauberk/blob/db360d9efa714efb6d937c31953ef849c7394a39/lib/src/content/dungeon.dart),
[demo repo](https://github.com/munificent/rooms-and-mazes)). Builds on Jamis
Buck's and Karcero's maze-then-rooms generators. We care about its
**connect** and **uncarve** passes, not the dungeon look.

## Approach

1. Place random non-overlapping rooms (fixed number of attempts, discard on
   collision); odd sizes and positions so everything aligns to a 2-cell grid.
2. Flood-fill each remaining solid area with a maze (a randomised flood
   fill that never carves into open space). Every room and maze is a
   **region** id.
3. **Connectors:** wall cells with ≥ 2 different regions among their 4
   neighbours. **Regions are graph vertices, connectors are edges.**
4. **Spanning tree:** open a connector (door, open doorway or floor), merge
   the regions it joins, drop connectors that now join the same region, and
   open each dropped one with chance 1/`extraConnectorChance` (20 in the
   code) to make loops; also drop connectors within 1 cell of the one just
   opened (no double doors). Repeat until one region. The post grows from a
   random root room (Prim-like); the code picks any remaining connector
   (Kruskal-like).
5. **Remove dead ends:** repeatedly fill any open cell with exactly one open
   neighbour until none are left, so every corridor leads somewhere.

## Procedural vs templated

Fully procedural, a few knobs (room attempts, winding %, extra-connector
chance). Rooms are plain rectangles.

## How it hooks into worldgen

Not worldgen; a per-building pass in planner-core after interior layout.
For us, regions are rooms, hallways and **the outside** (as the root, so
the entrance is part of the tree); later, floors joined by stair cells as
vertical connectors.
- **Determinism:** list connectors in a fixed scan order (y, z, x), draw
  with a hashed sub-seed, merge with union-find keyed by region index; no
  hash-set iteration (Dart's literal maps are insertion-ordered; in Java use
  `LinkedHashMap` or arrays). Dead-end removal reaches the **same fixed point
  in any scan order** (it is leaf pruning), so it is order-safe.
- **Cost:** linear in cells × passes; trivial for a house.
- **Voxel fit:** a connector in a 1-block wall needs **different regions on
  opposite sides** (N/S or E/W), floor at the same level on both, 2 blocks
  of headroom, and not at a wall corner. Nystrom's "any 2 of 4 neighbours"
  only works because of his odd-grid alignment; we need the stricter test.

## Licence

Hauberk and the demo repo: MIT (COPYRIGHT files). Post text: © Nystrom,
ideas only. We may study the code and port it with attribution, though it
is ~60 lines and easier to rewrite.

## What to borrow

- **Reachability/repair stage** for any interior generator (Lopes growth,
  grammar, WFC): flood-fill regions → collect legal door cells → spanning
  tree from the outside / hallway root → optional extra doors at a low,
  per-style rate (houses near 0; inns, castles higher) → invariant: one
  region. Generalises the door pass in
  [Lopes](constrained-growth-floor-plans.md), which adds doors "until every
  room is reached" without loop control.
- **Prim order from the entrance** with connector choice biased to public
  rooms / hallway first gives Merrell's privacy gradient
  ([merrell](merrell-residential-layouts.md)) for free: private rooms end
  up as leaves.
- **Drop connectors near an opened one**: no two doors side by side.
- **Dead-end removal** for corridors and paths (castle passages, alleys,
  garden paths); [MarkovJunior](markovjunior.md) does the same as a rewrite
  rule ("retract dead ends") alongside other bounded grid passes.
- **Fail loudly:** if regions stay unconnected (no legal connector), report
  an invariant failure and fall back, rather than carve through walls.

## What to avoid

- The maze fill itself: twisty 1-wide corridors read as dungeon, not house
  (Nystrom himself finds them "annoyingly windy").
- Picking door cells uniformly along a shared wall: prefer the middle or
  near a corner, by style.
- The 2-cell alignment: it makes plans look artificial (his words) and
  wastes space at Minecraft scale.

## Verdict

Adopt its connect pass (regions as vertices, legal door cells as edges,
spanning tree from the entrance plus a tunable loop rate) and dead-end
removal as our interior reachability/repair stage; skip the maze generator.

**Sources / what I couldn't verify:** read the post and the Hauberk
`dungeon.dart` at the linked commit (connect, junction, dead-end code);
didn't run it. Buck's and Karcero's generators not read. The voxel
connector rules are my adaptation, untested.
