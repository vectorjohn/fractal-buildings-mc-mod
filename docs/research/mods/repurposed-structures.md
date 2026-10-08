# Repurposed Structures (TelepathicGrunt)

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Adds biome-themed variants of vanilla structures: 16 village styles, 9
mansions, 18 mineshafts, 19 outposts, pyramids, witch huts, a nether and an
overworld "city", and more (119 structure JSONs, ~3,500 NBT pieces).
[Source](https://github.com/TelepathicGrunt/RepurposedStructures),
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/repurposed-structures-fabric).
Maintained: default branch `26.3` (Fabric, NeoForge), last commit Sept 2026.

## Approach

Almost everything is one data-driven structure type,
`generic_jigsaw_structure`, pointing at hand-built NBT template pools
(a village style = `town_centers`, `streets`, `houses`, `decor`,
`terminators`, `villagers`; ~80–100 NBTs each). The jigsaw assembler is
YUNG's piece-limited manager (credited in the source; see
[yungs-structure-mods.md](yungs-structure-mods.md) for the mechanism).
Mansions and monuments reuse vanilla's coded `StructurePiece` generators with
swapped blocks.

## Procedural vs templated

Templated pieces, rule-assembled layouts, then block processors (noise and
random replacement, vines, breakage, coral alive/dead). Two villages of one
style differ in street topology and house choice; houses are identical copies.

## How it hooks into worldgen

Ordinary `Structure` + `structure_set`. New over YUNG's:
- **Required-piece counts** (`rs_pieces_spawn_counts/*.json`:
  `always_spawn_this_many`, `never_spawn_more_than_this_many`,
  `minimum_distance_from_center_piece`). Implemented as **generate-and-test**:
  re-roll the whole layout up to **40 times** until counts are met, then log an
  error and keep the last attempt. The city uses this to force two tower tops.
- **Site checks before layout** in `findGenerationPoint`: every chunk within
  `valid_biome_radius_check` must be a valid biome; terrain range over
  `terrain_height_radius_check` chunks must be ≤ `allowed_terrain_height_range`
  (villages: radius 2 chunks, ≤ 22 blocks); `cannot_spawn_in_liquid`;
  `y_allowance`. Three **burying** modes after layout (`LOWEST_CORNER`,
  `AVERAGE_LAND`, `LOWEST_SIDE`) shift the whole structure.
- **Size:** `size` ≤ 30 depth; horizontal box defaults to **80** blocks from
  the centre, capped at **128** (`max_distance_from_center`), with
  `pools_that_ignore_boundaries` letting tops/bridge ends poke out. Same wall
  as YUNG's: villages are bigger than vanilla (size 10) but not towns.
- **Placement:** `advanced_random_spread` with a `super_exclusion_zone`
  (no village within 6 chunks of another set's structure). One structure set
  holds all village styles, weighted; biome tags pick which can spawn.
- **Feature suppression:** mixins cancel lakes, geodes, basalt deltas, falls,
  vines etc. when the origin is inside a structure carrying an RS tag
  (e.g. `NO_LAKES`). Cheap fix for "lake through the house".
- `PoolAdditionMergerManager`: data files that append entries to other mods'
  or vanilla pools at server start (how RS houses enter vanilla villages).

**Determinism.** Layout seeded by `setLargeFeatureSeed(seed, chunkX, chunkZ)`;
the 40-retry loop reuses that RNG, so it stays deterministic. A global
`ConcurrentHashMap` caches `getFirstFreeHeight` (cleared at 2,048 entries);
harmless for output since heights are pure functions of the seed.

**Biome adaptation.** One structure per biome style, selected by biome tag;
no role-based material swap.

## Licence

LGPL-3.0 (`LICENSE.txt`). Study freely; copying code into our codebase would
make those parts LGPL. Read, don't copy. NBT assets are LGPL too.

## What to borrow

- Site-suitability checks (biome radius, terrain range, liquid) as a cheap
  veto before planning: same as our lot-probe step.
- Constraint-like piece quotas ("at least 2 towers", "min distance from
  centre"), but satisfied by construction in our planner, not by re-rolling.
- Structure tags that suppress specific vanilla features inside our bounds.
- Pool-append data files as a compat route if we ever contribute to
  vanilla/other villages.

## What to avoid

- Generate-and-test with a silent fallback after N tries: costs time and can
  emit a structure that violates its own rule.
- Variety by a new NBT set per biome (16 village styles × ~90 pieces).
- The 80/128-block box: the "city" is a single jigsaw structure, a big
  castle, not a settlement.

## Verdict

Widest catalogue of biome variants and a good list of placement/site-check
knobs; same jigsaw size wall; LGPL-3.0, read don't copy.

**Sources / what I couldn't verify:** Read the `26.3` branch:
`GenericJigsawStructure`, `PieceLimitedJigsawManager`,
`StructurePieceCountsObj`, `GeneralUtils.getCachedFreeHeight`,
`NoLakesInStructuresMixin`, village/city structure and structure-set JSONs.
Not run in-game; didn't read `MansionPieces`/`MonumentPieces` or the
`use_bounding_box_hack` details beyond the call site.
