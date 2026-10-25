# proc-floorplan: `fractal-cli floorplan`

Process improvement (John, 2026-10-25), no ROADMAP item. Design-review agents (architect, interior-designer) read text, so a building's layout
needs a text form.

## What

- `core/ir/FloorPlanText` (pure, planner-core, no Minecraft): IR to text. Per storey a header (index, slab y, standing y, floor-to-floor
  height, clear air, ceiling y), a character grid (1 char per block, `--wide` 2), a legend, a room table (label, id, use, area in blocks,
  volume, connections with opening id and direction, `@storeyN` for other floors), openings (door facing, window sill/head above the floor
  surface, stairs "goes UP from rA to rB" / "arrives from below"), furniture hooks (letter per kind, assigned alphabetically, `v` and `O`
  reserved), then a circulation section (entrances, rooms unreachable from outside by BFS) and a roof height map (base 36 top block y).
- `cli`: `floorplan` takes `--in FILE [--building ID]` or the `gen` flags. A scene with several buildings and no `--building` prints an index
  and exits 2. Doc: docs/VIEWER.md "Floorplan"; CLAUDE.md CLI line.
- Grid comes from rasterising the shell boxes (`VoxelGrid`, site boxes left out) at the standing layer plus the slab layer, so stairwell holes
  and walls are what the voxels say; openings override the glyph on their columns. Exterior wall = a wall cell with a 4-neighbour that is
  neither wall nor room.

## Verified

- `FloorPlanTextTest` (8, hand-built two-storey IR: glyphs, stairwell + landing, room table, sill/head, unreachable rooms, letters,
  wide/ASCII, determinism), `FloorplanCommandTest` (golden text for seed 3 medieval house, seed 5 three-storey house, seed 9 wide tower;
  `--in` equals generated; exit 2 cases). Golden rewrite: `FB_UPDATE_GOLDEN=1`.
- Mutation: door glyph `%` changed to `/` fails 1 unit and 3 golden tests.
- Looked at house (1 and 3 storeys), tower, and a district-scene building (exterior-only scene: no rooms, prints a note).

## Not in the IR (not shown)

Door hinge side / open direction, ladders (none generated), furniture size, exterior steps (site boxes), room function beyond the `use` tag.
Follow-up: district scenes are exterior-only unless `city --interiors`; plans of those buildings need that flag.
