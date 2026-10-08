# Moog's structure mods (MVS, MES, MNS, … on Moog's Structure Lib)

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A family by FinnDog ("Moog"): Voyager (overworld, 130 structures), End,
Nether, Soaring, Ocean, Missing Villages, Temples, Mineshafts and more, all
content packs on one library,
[Moog's Structure Lib](https://github.com/FinnSetchell/MoogsStructureLib) (MSL).
[MVS source](https://github.com/Moog-s-Mods/MoogsVoyagerStructures),
[Modrinth](https://modrinth.com/mod/moogs-voyager-structures). Maintained
(MSL 3.4.2, Oct 2026; 1.20 to 26.3 from one Stonecutter build).

## Approach

The opposite of a settlement generator: **many small, independent
structures**, each its own `structure` + `structure_set` with its own salt
and spacing, sprinkled over the world for "life" (wells, carts, stalls, huts,
trees, ruins, a cathedral). In MVS, **125 of 130 structures have jigsaw
`size: 1`**: a single hand-built NBT from a start pool, so variety is "which
NBT from the pool" plus processors. Only campsite, cathedral, jungle tower
and a few others are multi-piece (max `size` 17, `max_distance_from_center`
35–80).

## Procedural vs templated

Templated. MSL is code; the content mods are now pure data (MVS's
`1.21-datapack` branch has no Java). Variation comes from pool choice,
rotation and block processors (`BlockStateRandomizer`, random replace,
spawner/trial-spawner/vault randomisers, armor-stand equipment, loot swaps).

## How it hooks into worldgen

Vanilla `Structure`/`structure_set`. MSL is openly a fork: the jigsaw
structure, piece-limited jigsaw manager, `BoxOctree` and processors come from
Repurposed Structures ([note](repurposed-structures.md)), and an "enhanced
terrain adaptation" beardifier (3-D Gaussian kernel, `top`/`bottom` actions,
padding, band) is a reduced port of YUNG's API
([note](yungs-structure-mods.md)); `CREDITS.txt` says so. What MSL adds:
- **Site checks used aggressively**: e.g. a house needs terrain range ≤ 3
  blocks over a 2-chunk radius and no liquid; it simply skips bumpy sites.
- **Per-set placement config surfaced in-game**: every set using MSL's
  `advanced_random_spread` gets a disable toggle and a spacing slider in the
  config screen, keyed by set id, no extra data needed. Plus
  `min_distance_from_world_origin`.
- **Replacement presets** (`replace_vanilla.json`,
  `conditional_concentric_rings`): swap a vanilla structure 1:1 or coexist at
  reduced density.
- An `AsyncLocator` for `/locate` off the server thread; an online 3-D
  preview site of every structure.

**Determinism:** inherits the vanilla/RS model (per-start-chunk seeds); 116
separate salts mean structures don't correlate with each other, which also
means **nothing clusters**: no notion of neighbours or a settlement.

**Biome adaptation:** by **separate structures per biome** (`acacia_well`,
`birch_well`, `jungle_well`, …; `dead_tree_<wood>` ×8) gated by `c:` biome
tags (58 of 130 just use `#c:is_overworld`). No role-based palette.

## Licence

MSL: LGPL-3.0. MVS repo `COPYING.md`: code/JSON **LGPL-3.0**, all `.nbt`
files **All Rights Reserved**, explicitly forbidding reuse "including AI
training datasets" (Modrinth lists MVS as MIT; the repo file is newer and
stricter; trust the repo). Other members vary on Modrinth: MES/MNS/MTR LGPL,
MSS/MMR GPL-3.0, MMV AGPL-3.0, MOS LGPL with ARR structures. Read MSL as
"read, don't copy"; don't open or reuse any NBT.

## What to borrow

- In-game per-structure-set toggle and spacing slider, generated from data:
  a cheap, loved config UX for our settlement density.
- Strict flatness veto for small buildings instead of terraforming.
- A browsable online 3-D preview of every output: same idea as our viewer
  gallery, good for the human gate.
- A small "scatter" layer (wells, carts, stalls) between settlements, if we
  want roadside life later.

## What to avoid

- One structure per biome × object: the catalogue grows multiplicatively
  (8 dead trees, 6 wells), exactly what material roles avoid.
- Uncorrelated independent sets: they can't make a town, only sprinkles.
- Copying from a library that is itself a chain of LGPL forks.

## Verdict

Scatter-of-small-templates, not settlements; useful for its placement/config
UX and site-veto habits; MSL LGPL-3.0, NBTs ARR (no AI use).

**Sources / what I couldn't verify:** Read MSL `main` (`CREDITS.txt`,
`README`, `EnhancedTerrainAdaptation`, `ConditionalConcentricRings`,
`StructureListManager` headers, file list) and MVS `1.21-datapack` (structure
and set JSONs, `COPYING.md`); Modrinth API for licences. Didn't open any
`.nbt`; didn't read MMV (villages) or MES/MNS repos; didn't run in-game;
MSL's README says some code is AI-assisted.
