# Jigsaw assembly and terrain adaptation in 26.3

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

Vanilla's template-assembly structure (`JigsawStructure`, `JigsawPlacement`),
and the terrain reshaping (`Beardifier`) that any structure can use. Source:
decompiled 26.3, plus the [Jigsaw structure](https://minecraft.wiki/w/Jigsaw_structure)
wiki page, which agrees on every number below.

## Approach

- **Config** (`JigsawStructure.CODEC`):
  - `start_pool`, optional `start_jigsaw_name`, and `size` (depth 0–20).
  - `start_height` (a `HeightProvider`), plus optional
    `project_start_to_heightmap`.
  - `max_distance_from_center`: horizontal 1–128, optional separate
    vertical.
  - `pool_aliases`, `dimension_padding`, `liquid_settings`,
    `use_expansion_hack`.
  - `verifyRange` rejects horizontal distance + 12 > 128 when
    `terrain_adaptation` ≠ none, so 116 is the ceiling with adaptation.
  - Vanilla villages use 80, size 6, `beard_thin`; the ancient city uses 116,
    size 7, `beard_box`.
- **Start**: pick a random element of the start pool and a random rotation.
  Optionally anchor on a named jigsaw. Projected starts sit at
  `getFirstFreeHeight(centre)`. The free space is an AABB of ±max distance
  minus the start box, as a `VoxelShape`.
- **Growth** (`JigsawPlacement.Placer.tryPlacingChildren`): for each jigsaw
  of a piece (shuffled, then sorted by `selection_priority`):
  - Resolve its pool through the aliases.
  - Try the pool's templates in a weighted shuffle (weights 1–150), then the
    fallback pool. Past max depth, only the fallback is tried.
  - Try each of 4 shuffled rotations and each matching target jigsaw.
    `canAttach` needs opposite fronts, matching tops unless `rollable`, and
    name equal to target.
  - Accept the **first** candidate whose box (deflated 0.25) fits the
    remaining free shape, and carve it out.
- **Search order**: no backtracking, no global objective, no constraint
  beyond "fits in free space". The queue is a `SequencedPriorityIterator`:
  highest `placement_priority` first, FIFO within a priority, so roughly
  breadth-first. Interior children use their parent's own box as their free
  space.
- **Heights**: `rigid` pieces keep their relative Y. `terrain_matching`
  pieces sit at `getFirstFreeHeight(WORLD_SURFACE_WG)` of the connector at
  assembly time. At placement, `GravityProcessor` re-reads the **live**
  heightmap (`WORLD_SURFACE` in a `WorldGenRegion`).
- **Custom content**: `StructurePoolElement` is abstract and registered
  (`STRUCTURE_POOL_ELEMENT`). Its contract is `getSize`,
  `getShuffledJigsawBlocks`, `getBoundingBox`, `place` and `getType`. A
  procedural element, whose blocks come from our IR rather than NBT, is
  possible in principle.

## Terrain adaptation (`Beardifier`, verified)

- Built per chunk at the `terrain` step from referenced starts whose
  structure has adaptation ≠ none. It contributes density inside a 24-block
  kernel (radius 12) around each **rigid** piece box, plus every jigsaw
  junction.
- **Non-jigsaw pieces count as rigid**, with ground at `box.minY`. So our own
  custom pieces get adaptation by setting `terrain_adaptation` and making each
  piece's box bottom its foundation level.
- Modes:
  - `beard_thin`: fill below and carve above the ground plane, × 0.8.
  - `beard_box`: the same, but carving only above `box.maxY`.
  - `bury`: a sphere of radius 6.
  - `encapsulate`: the box at half scale.
- In 26.x it is a `DensitySampler` (`sampleVolume` over a `DensityVolume`),
  part of the new compiled density pipeline. It doesn't affect
  `getBaseHeight`, which is pre-beard.
- Cost: O(pieces within 12 blocks) per density sample. Fine for a few
  buildings per chunk; watch it with hundreds of small pieces.

## Licence

Minecraft code is proprietary (Mojang EULA): study only.

## What to borrow

- `terrain_adaptation: beard_thin` with one rigid piece per building, box
  bottom at floor level: free hill grading of up to about 12 blocks around
  each lot.
- Connector-and-pool vocabulary as a **data shape** for authored part
  libraries (doors, stair joints) if the hybrid generator needs it.
- Priority queues (`placement_priority`, `selection_priority`) as a cheap way
  to grow streets before houses.

## What to avoid

- Jigsaw as the city layout engine:
  - It is greedy and blind: no road network, no lots, no global constraints.
  - It caps out at 116–128 blocks, depth 20, one start.
  - Overlaps are rejected but gaps and dead ends are not.
  - Variety comes from template count (NBT authoring), which is what this
    project exists to avoid.
- `terrain_matching` paths: they re-read the live heightmap at placement and
  so inherit cross-chunk order effects. Compute path heights at plan time.
- Expecting the beard to fix big slopes. A 24-block kernel and × 0.8 strength
  leave visible cuts on steep hills (S5 will show).

## Hypotheses from ARCHITECTURE.md

- "Pieces stay within 128 blocks (116 with adaptation)": **confirmed**
  (`MAX_TOTAL_STRUCTURE_RANGE = 128`, `verifyRange`).
- "Terrain adaptation exists: beard_thin, beard_box, bury, encapsulate":
  **confirmed**. Refinement: it also applies to custom non-jigsaw pieces, as
  rigid boxes.

## Probe tests worth writing after 0.5

- S5: one custom rigid piece with `beard_thin` on a 20-block slope. Screenshot
  it, and measure the residual step at the box edge.
- A jigsaw structure with `max_distance_from_center: 120` and `beard_thin`:
  confirm the data pack fails to load (`verifyRange`).

## Verdict

Use the beardifier and keep jigsaw's connector idea for small part libraries.
Don't use jigsaw assembly for city or building layout: it is greedy, has no
backtracking, and is capped at 128 blocks.

**Sources / what I couldn't verify:** read `JigsawStructure`,
`JigsawPlacement`, `StructureTemplatePool`, `StructurePoolElement`,
`SinglePoolElement`, `JigsawBlock.canAttach`, `SequencedPriorityIterator`,
`PoolElementStructurePiece`, `GravityProcessor`, `Beardifier` and
`TerrainAdjustment` (26.3), and the vanilla `structure/*.json`. Nothing was
run. Beard strength on real slopes and the cost with many pieces are
inferred.
