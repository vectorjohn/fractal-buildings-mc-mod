# YUNG's structure mods and YUNG's API

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A family of mods by YUNGNICKYOUNG that replace vanilla
structures (Better Dungeons, Mineshafts, Strongholds, Desert Temples, Witch
Huts, Ocean Monuments, Nether Fortresses, Bridges, …) on a shared library,
[YUNG's API](https://github.com/YUNG-GANG/YUNGs-API). Maintained
(26.1.2, Fabric and NeoForge).

## Approach

Two styles coexist. (1) Most mods are **data-driven jigsaw**:
a `yungsapi:yung_jigsaw` structure type with template pools of hand-built NBT
pieces (Strongholds: 97 NBTs in pools such as `rooms`, `hallways`,
`hall_doorways`, `terminators`), dressed by many small custom **structure
processors** (ruin, ore, banner, rare-block, leg, redstone) that randomise
blocks at placement. (2) A few are **hand-coded `StructurePiece` generators**:
Better Mineshafts (tunnels, intersections, side rooms laid out in Java with
overlap checks, blocks filled box by box in `postProcess`) and the spider
dungeon.

## Procedural vs templated

Jigsaw mods: templated pieces, procedural
assembly. Mineshafts: fully procedural geometry with config-driven block
randomisers. Neither generates buildings from rules at the room/facade level.

## How it hooks into worldgen

Ordinary vanilla structures (`Structure` + `structure_set` +
placement), so `/locate`, structure bounding boxes, spawn overrides and
terrain beardifier all work. YUNG's jigsaw extends vanilla's:
- `size` (depth) up to **128** vs vanilla's 20; horizontal
  `max_distance_from_center` still capped at **128** blocks (Strongholds use
  116). No workaround beyond that: big structures are deep, not wide.
- Per-element `max_count`, `min_required_depth`/`max_possible_depth`,
  `is_priority`, `ignore_bounds`, **conditions** (biome, altitude, depth,
  rotation, random, piece-in-range), post-assembly **modifiers/actions**
  (transform a piece once the layout is known), and a **`deadend_pool`** that
  swaps a piece for a terminator when it can't grow.
- `BoxOctree` for overlap tests (faster than vanilla's voxel shapes);
  `enhanced_terrain_adaptation` with a 3-D kernel (`bury`, carved top), aquifer
  overrides, and `EnhancedRandomSpread` placement with exclusion zones.

**Determinism / chunk independence.** Inherits vanilla's model: the whole
layout is planned once from `(world seed, start chunk)` in `findGenerationPoint`
(e.g. `setLargeFeatureSeed`), then each chunk places the clipped part of each
piece. Chunk-independent by construction. Caveat: `ISafeWorldModifier`
bypasses `PalettedContainer` locks to dodge a multi-thread crash (the source
comment calls it a hack of unknown validity).

**Biome adaptation.** By separate structures per biome (Mineshafts: 13 variants,
acacia to spruce_snowy, in one weighted structure set) and by biome
conditions on pool elements; materials are not swapped by role.

## Licence

LGPL-3.0 for API and mods (repo `LICENSE`). We may study it
and *depend on* YUNG's API as a library; copying code into our (to-be-chosen)
codebase would make those parts LGPL. Treat as "read, don't copy". NBT pieces
are LGPL-covered assets too.

## What to borrow

- Ideas for a richer jigsaw if we use structures at all: depth/max-count
  constraints, dead-end fallback, post-layout modifiers, conditions.
- Processor-driven weathering (ruin, moss, rare blocks) as a cheap variety layer
  on top of our generated geometry.
- Per-biome variants as separate structures in one weighted set.
- Mineshafts show a code-defined `StructurePiece` layout works fine inside the
  structure system: the closest existing shape to "planner emits IR, adapter
  emits pieces".

## What to avoid

- The 128-block horizontal radius: a structure can't be a city. Anything larger
  must be several coordinated structures or a non-structure hook.
- Lock-bypassing world writes.
- Variety through ever more hand-built NBT pieces; that is the scaling wall
  we want to get past.

## Verdict

The reference for how far the structure/jigsaw path stretches (and its 128-block wall); study, link, don't copy.

**Sources / what I couldn't verify:** Read YUNG's API, Better Dungeons,
Mineshafts, Strongholds, Desert Temples and Witch Huts at their default
branches (`26.1.2`, Sept 2026); the jigsaw limits from the
[wiki](https://minecraft.wiki/w/Structure/JSON_format). Not checked: Better Ocean
Monuments, Nether Fortresses and Bridges (not opened; (assumed jigsaw or coded pieces like the others); in-game behaviour
not run; whether YUNG's API's jigsaw raises any vertical limit beyond vanilla.
