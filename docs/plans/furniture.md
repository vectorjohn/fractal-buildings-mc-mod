# Plan: multi-block furnishings

Status: design proposal, not scheduled. Author: assistant, 2026-10-23. ROADMAP item 7.5.
Open questions answered by John on 2026-10-23 (see the end): sign arms, job blocks in
kitchens, fireplaces get chimneys (step F7), floor cap 40 % to try.
Asked by John on 2026-10-23, after playing: "The rooms look sparse and un-lived in."
He wants a first catalogue of chairs, tables, sofas and kitchen furniture, and a way to
hand-craft his own alternatives ([FURNITURE-AUTHORING.md](../FURNITURE-AUTHORING.md)).
Not a first-release item: worked on when the release has nothing else to do.
Research: [minecraft-furniture.md](../research/aesthetics/minecraft-furniture.md).

# What exists today

- The interior pass (`InteriorPass.hookSites`) puts single-cell **hooks** in rooms: a
  floor cell against a wall or partition, never within one cell (diagonals too) of a
  door, the entrance, a strip door or a stair core. Each room use asks its
  `RoomProgram` for hook kinds (house bedroom: bed, chest, bed, table; kitchen: hearth,
  table, chest; workshop: workstation x2, hearth, chest; nave: benches and lights).
- The mod (`gameplay/HookPlacer`, plan B5) turns each hook into **one block** in
  `postProcess`: bed (2 cells), chest with loot, a job block, crafting table ("table"),
  furnace ("hearth"), barrel ("counter"), spruce slab ("bench"), lantern ("light").
  `WorldHooks` is the pure half (world positions, villager beds, seeds).
- The raster (A3, `PieceRaster`) emits each piece's cells with a block id and a `Shape`
  (facing, half, hinge, axis, connect). The mod's `BlockMapper` (A4) turns
  (id, shape) into a block state. A ground cell is not emitted where a building of the
  same tile has a cell (ownership).
- Rooms are small: style `roomMin` 3, `roomArea` 24 (about 4 x 6), and **3 air cells
  high** in most styles (storey height 4); halls and naves are taller.

So the rooms hold 2–4 single blocks against the walls. That is the sparseness.

# Goals and limits

- 25 pieces (below), grouped into a handful of **sets** (dining set, kitchen run, bed
  with nightstands, sitting area), placed by room use.
- One template serves every style: templates name **furniture roles** (wood, accent,
  stone, cloth, plant), each style maps them to a wood family, a stone family, colours.
- John can add or replace pieces without writing Java (authoring doc).
- Determinism unchanged: everything from (seed, building, room), no world reads.
- Rooms stay walkable: furniture never blocks a door, an archway, a stair, or the path
  between them (a new invariant).
- The viewer shows furniture as the game will (cells and colours; shapes approximate).
- Out of scope: entities (item frames, paintings, armour stands), redstone, block
  entities with contents other than chest loot (shelves stay empty in v1), mods.

# Furniture roles and forms

A template cell names a **role and a form**, or a literal block:

| Role | Forms | What a style maps it to |
|---|---|---|
| `wood` | `planks stairs slab trapdoor fence fence_gate door pressure_plate button shelf log wall_sign` | a wood family: `oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `mangrove`, `cherry`, `pale_oak`, `poplar`, `bamboo`, `crimson`, `warped` |
| `accent` | same as `wood` | a second wood family (usually darker, for frames, arms, backs) |
| `stone` | `block stairs slab wall` | a stone family: `cobblestone`, `stone_brick`, `smooth_sandstone`, `mud_brick`, `polished_andesite`... (a table maps family + form to an id: `stone_bricks`, `stone_brick_stairs`) |
| `cloth` | `wool carpet banner` | a list of dye colours; one colour per room, a second for borders |
| `plant` | `pot` | a list of `potted_*` blocks per style |
| literal | any block id | itself: `smoker`, `water_cauldron`, `bookshelf`, `chiseled_bookshelf`, `lantern`, `candle`, `barrel`, `iron_chain`, `campfire`... |

Proposed style mapping (a new `furnishing` section in each `styles/*.json`; the
`extends` chain gives `plain`'s values to a style that lacks one):

| Style | wood | accent | stone | cloth | plants |
|---|---|---|---|---|---|
| plain | oak | spruce | cobblestone | white, light_gray | poppy, dandelion, fern |
| plains | oak | spruce | cobblestone | yellow, white, light_blue | dandelion, oxeye_daisy, cornflower |
| medieval | spruce | dark_oak | stone_brick | red, brown, green | fern, red_tulip, azalea_bush |
| taiga | spruce | dark_oak | cobblestone | brown, red, gray | fern, spruce_sapling |
| snowy | spruce | birch | stone_brick | light_blue, white, blue | spruce_sapling |
| desert | birch | jungle | smooth_sandstone | orange, yellow, red | cactus, dead_bush |
| jungle | jungle | bamboo | mossy_cobblestone | green, lime, brown | bamboo, fern, jungle_sapling |

All these ids exist in 26.3 (checked against the client jar's block list).

# The catalogue

Sketch conventions: **top view**, north up; the piece is drawn with its back to a wall
`=` on the north and its front to the south (the room). `.` is free floor that must
stay clear (the "use" cell where you stand or sit), `_` is the next layer up seen from
above. Footprint is W (along the wall) x D (out from the wall) x H. Block states are in
the piece's own frame (front = south) and rotate with it.

Legend used in sketches: `S` stairs, `s` slab, `A` wall sign (arm), `T` open trapdoor (thin panel),
`t` closed trapdoor, `F` fence, `p` pressure plate, `c` carpet, `B` barrel, `K` bookshelf,
`L` lantern, `P` plant pot, `b` bed (hook), `C` chest (hook).

## Seating

**Arms are wall signs** (John 2026-10-23): an `accent.wall_sign` in the cell beside
the seat, attached to the seat stair (west arm `facing` west, east arm `facing` east).
Every piece below that had open-trapdoor arms uses sign arms; the trapdoor version stays
in the catalogue as variant `arms: trapdoor` (weight 0 unless a style or John asks).
Signs pop off when their support goes and a neighbour update runs; how F4 avoids that is
under "Block states (A4)".

**1. chair** 1x1x1. `wood.stairs` facing north (tall half at the back, seat to the
south). Variant *banner back*: a `cloth.banner` standing on the cell behind (only
against a partition with room behind it; otherwise skip). Placement: at a table side,
back away from the table; or against a wall. Rooms: kitchen, dining, living, bedroom
(desk), hall, shop, workshop.

**2. armchair** 3x1x1, against a wall or free-standing with its back to a rug.
`accent.wall_sign`, `wood.stairs` facing north, `accent.wall_sign` (both attached to
the stair). Rooms: living, bedroom (area 20+), hall.

```
  = = =
  A S A        arms: wall signs attached to the seat stair
  . . .        clear in front (at least the centre cell)
```

**3. stool** 1x1x1. `wood.slab` bottom. Free-standing at a counter or workbench.
Rooms: kitchen, workshop, shop. (Replaces today's "bench" spruce slab.)

**4. sofa** 4x1x1 (2–3 seats plus arms). `accent.wall_sign`, 2–3 x
`wood.stairs` facing north, `accent.wall_sign`. Optional `cloth.carpet` row in
front (walkable). Rooms: living, hall.

```
  = = = = =
  A S S S A
  c c c c c    optional carpet, walkable
```

**5. corner_sofa** 3x3x1 in a room corner. Stairs along both walls, the corner stair
with `shape` `inner_left`/`inner_right` so it joins (our writes do not run neighbour
updates, so the template must carry the corner shape); sign arms at both open ends.
Rooms: living (area 24+).

```
  = = = =
  = S S A
  = S . .
  = A . .
```

**6. pew** 3–5 x1x1, a row of `wood.stairs` facing away from the altar with
`wood.wall_sign` arms at both ends; rows with one clear row between them, an aisle
of at least 1 cell (temples: 2). Rooms: nave, chapel; hall (as a bench against a wall).

## Tables

**7. post_table** 1x1x2 (reads as 1 high). `wood.fence` at y0, `wood.pressure_plate`
at y1 resting on the fence top (the village-house table). Placement: centre or wall,
with chairs on 1–3 sides. Rooms: kitchen, bedroom, tower room, shop.

**8. coffee_table** 1x1 or 2x1, `wood.slab` bottom (or `accent.trapdoor` closed, top
half, for a thin top). In front of a sofa or armchair, 1 cell gap. Rooms: living.

**9. dining_table** 2x1x1, two `wood.stairs` upside-down (`half` top) facing each other
(the wiki's dining table), or `wood.slab` top x2 in a lighter style. Free-standing with
1-cell clearance; chairs on the long sides and ends. Rooms: kitchen (area 16+), dining,
living (area 24+), hall.

```
  . S . .        S chairs (facing the table)
  S u u S        u upside-down stairs, tops flush
  . S . .
```

**10. long_table** 3–6 x 1 or x 2, a row of `wood.slab` top (a thick top at table
height, open underneath), the two ends `wood.stairs` upside-down facing inward for a
carved look. Chairs along both sides. Rooms: hall, tavern (later), large dining.

**11. desk** 2x1x1 against a wall: `wood.slab` top + `wood.stairs` upside-down facing
the wall (a desk with a drawer front), `candle` (lit) or `lantern` on the cell above
one end, a chair in front. Rooms: bedroom, study (later), shop back room, tower room.

```
  = =
  s U    U upside-down stair; candle above s
  . S    chair
```

## Kitchen

**12. counter** a run of 1–5 cells along a wall: each cell `stone.stairs`
upside-down facing the wall (worktop with a toe-kick), or `wood.planks` with an
`accent.trapdoor` open on its front edge in the cell in front (a cupboard door; thin,
walkable past). Above the run on the wall: optional `wood.shelf` facing south at y1
every second cell. Rooms: kitchen, shop (wood only).

**13. stove** 1x1x1, `smoker` facing south; with a 3-high room, `stone.slab` top or
`stone.stairs` upside-down at y2 above it as a hood. Inside a counter run or at its end.
Rooms: kitchen. (Smoker is the butcher's job site: villagers in hamlets may claim it.
John 2026-10-23: fine, kitchens keep the job blocks.)

**14. sink** 1x1x1, `water_cauldron` (level 3) in the counter run, `tripwire_hook`
on the wall above facing south (the tap). Needs a wall behind (the hook attaches to it).
Rooms: kitchen; workshop (as a quench tub, no tap). (Cauldron = leatherworker job site.)

**Set: kitchen_run** 3–6 x1: counter cells with one stove and one sink, sink nearer a
window if any, stove not under a window; may turn a corner (L) with an inner
`stone.stairs` corner shape. A `post_table` or `dining_table` with stools or chairs in
the rest of the room.

```
  = = = = =
  U O ~ U U      U counter, O smoker, ~ cauldron (tap on the wall above)
  . . . . .      clear strip in front, the whole length
```

## Bedroom and storage

The bed itself stays a **hook** (HookPlacer places it, with its colour, villager and
chunk-edge rules); bed pieces carry the hook and add what goes around it.

**15. nightstand** 1x1x2, `wood.slab` top (or `barrel` facing up) at y0, `candle`
(1–2, lit) or `lantern` at y1. Beside a bed head, against the same wall.

**Set: bed_set** 2–3 x2: bed (hook, head to the wall), nightstand on one side or
both, `cloth.carpet` beside the bed (walkable).

```
  = = =
  n b n      n nightstand, b bed head
  c b c      b bed foot (the hook cell), c carpet
  . . .
```

**Set: double_bed** 4x2: two beds side by side (two hooks, same colour), nightstands
at both ends. Bedrooms of area 20+ that today get two beds.

**16. dresser** 2x1x1, two `barrel` facing south (drawers), a `plant.pot` or
`candle` on top (y1). Against a wall. Rooms: bedroom, living.

**17. wardrobe** 1x1x2, `accent.planks` x2 stacked with `wood.trapdoor` open on the
room-side edge at y0 and y1 (two doors, thin). Rooms: bedroom.

**18. bookcase** 1–3 x1 x2, `bookshelf` or `chiseled_bookshelf` (random slots filled,
from the cell hash) at y0 and y1; `wood.slab` top at y2 when the room is taller than 3.
Rooms: living, hall, study, sanctuary (1 wide), tower room.

**19. chest_nook** 1–2 x1x1, the chest hook plus a `barrel` beside it, or two chests
(hooks). Rooms: entry, landing, storage, shop.

## Light and decor

**20. floor_lamp** 1x1x2, `accent.fence` + `lantern` on top. Corner or beside an
armchair or sofa. Rooms: living, bedroom, hall. (The light hook can become this.)

**21. chandelier** ceiling, 1x1: `lantern` hanging (`hanging=true`) under the ceiling
in a 3-high room (y2, above head height); in taller rooms `iron_chain` cells down to
2 cells above the floor, then the lantern; 3x3 rooms of area 30+ may hang 2.
Placement: over a free floor cell or a table, never over a door path cell lower than y2.
Rooms: hall, nave, living (area 24+).

**22. rug** 2x2 to 4x3, `cloth.carpet` in the room colour with a border of the second
colour when 3x3 or larger. Walkable; under a dining table or in front of a sofa or
bed (carpet under a piece is placed first, the piece replaces the cell).
Rooms: living, bedroom, hall, entry.

**23. potted_plant** 1x1, a `plant.pot` on the floor in a corner, or on a counter,
dresser, nightstand or windowsill cell (y1). Rooms: all living rooms, shop, entry.

**24. fireplace** 3x1x3 against an exterior wall: `stone.block` sides at y0–y1,
`campfire` (lit) in the middle at y0, `stone.slab` top (mantel) across y2, a
`plant.pot` or `candle` on the mantel ends when the room is 4+ high. A chimney rises
from the mantel through the storeys above and the roof (John 2026-10-23; design under
"Chimneys", step F7). Until F7 lands, fireplaces have no chimney.
Replaces today's "hearth" furnace in living rooms and halls; kitchens keep the stove.

```
  = = =        side view (from the room):   s s s   mantel slab at y2
  # f #                                     # f #   # stone, f campfire
  . . .                                     # . #
```

## Work and trade

**25. workbench** 3x1x1 against a wall: `crafting_table`, `wood.stairs` upside-down
facing the wall (bench top), a job block (`smithing_table`, `stonecutter`,
`grindstone`, `fletching_table`: today's workshop choice, from the hash); `stool` in
front of the bench top. Rooms: workshop. The workstation hook moves into the piece.

**Set: shop_counter** 3–5 x1 across the shop floor (not against a wall), parallel to
the street front, leaving a 1-cell gap at one end and a 2-deep strip behind it for the
shopkeeper: `wood.stairs` upside-down facing the customer side, a `barrel` and a
`lectern` or `wood.shelf` in the run, shelves on the back wall. Rooms: shop.

# Which room gets what

Each room use gets a **furnishing program**: required sets first, then optional pieces
by weight while they fit and the room stays under its coverage cap (furniture blocks at
most 40 % of the floor cells; rugs do not count). John 2026-10-23: try 40 %; F6's
screenshots decide whether it moves (one constant, `FurnishPass.COVER_CAP`).

| Room use | Required | Optional (by weight) |
|---|---|---|
| bedroom | bed_set (double_bed if area 20+; two bed_sets where today two beds) | wardrobe, dresser, desk + chair, rug, potted_plant, floor_lamp |
| living | sofa or armchair x2, coffee_table | fireplace, bookcase, rug, floor_lamp, potted_plant, chandelier (24+) |
| kitchen | kitchen_run | post_table or dining_table + chairs/stools, potted_plant |
| dining | dining_table + chairs | dresser, chandelier, rug |
| entry, landing | chest_nook | potted_plant, rug, pew (as a bench) |
| hall | long_table + chairs, fireplace | chandelier, bookcase, armchair, rug |
| workshop | workbench | fireplace (with F7 its chimney; today's workshop hearth), stools, counter (wood), chest_nook, sink (as tub) |
| shop | shop_counter | chest_nook, potted_plant, shelves |
| nave, chapel | pews | chandelier |
| sanctuary | (altar hook as today) | bookcase (1 wide), candles, potted_plant |
| tower room | bed_set (single) | post_table + chair, chest_nook, bookcase |
| corridor, stair | none | potted_plant, floor_lamp (only where 3+ wide) |

Today's hook kinds map in: `bed` and `chest` stay hooks inside pieces; `table` becomes
post_table or dining_table; `hearth` becomes fireplace (living, hall, workshop) or stove
(kitchen); `workstation` sits inside workbench; `bench` becomes pew or stool; `light`
becomes floor_lamp or chandelier. When no piece fits a room, the old single-block hook
is the fallback, so no room ends up emptier than today.

# How it fits the planner

## Data

- `planner-core/src/main/resources/furniture/<id>.json` (format `fb-furniture/1`,
  written by the converter or by hand; layout in the authoring doc) and
  `furniture/index.txt` (one id per line, union-merged like `styles/index.txt`).
  `FurnitureLibrary` loads them as `StyleLibrary` does.
- A template: size, layers of cells (role.form or literal id, plus state properties in
  the piece frame), marker cells (needs a wall behind, clear floor, hook), and
  metadata (rooms, placement kind, weight, min room area, styles allowed).

## Placement (core, interior pass)

- After `hookSites` decides a room's use, a new `FurnishPass` places the room's sets
  and pieces. Candidates are enumerated in a fixed order (x, z, then rotation 0–3) and
  filtered by fit: every piece cell on room air over the room's floor; wall markers
  over wall or partition cells; clear cells free; nothing in the door 3x3
  neighbourhood, the entrance, strip doors or stair cores (today's hook rule); no
  overlap with placed pieces except rugs. One candidate is drawn with
  `c.draws("interior/furnish", idx, s, l)`, as hooks are drawn now.
- After each piece, the walkability check below runs; a piece that breaks it is
  removed and the next candidate tried (bounded: at most 8 tries per piece). Cost is a
  few hundred cell tests per room; negligible next to the floor plan.
- Storey bands: rooms shared by a band of storeys share the plan, but furniture is
  drawn per storey (the draw names the storey), so stacked flats differ.

## IR

- A new shell key `furnishings`: `{id, piece, room, pos, facing, variant}` per placed
  piece, written only when `genVersion >= 3` (bump `GEN_VERSION`; v1 and v2 bytes and
  golden digests unchanged, as 4.1 did for hooks). Hook markers inside a piece add
  normal `hooks` entries (so `HookPlacer`, loot and villagers work unchanged).
- Room uses are already in the IR; nothing else changes.

## Raster and ownership (A3)

- `PieceRaster.building` expands furnishings after the shell boxes and site boxes:
  each furniture cell resolves its role.form through the style (`Variant` for the style
  at the building), rotates its state by the `Placement`, and is emitted **only if the
  cell is AIR in the building's own raster** (furniture never replaces a wall, floor or
  door). Ownership is unchanged: furniture cells are building cells, so a ground box
  never overwrites them, and the 1-cell border already gives neighbours across chunk
  borders.
- A furniture cell may cross a chunk border like any other cell (a sofa spanning two
  chunks is two halves written by two chunks, each from the same raster). Only beds
  keep their "both halves in one chunk or skip" rule, because HookPlacer places them.
- The cells enter the ledger and the B2 reference raster automatically; the B2
  determinism tests cover them with no change.

## Block states (A4)

`Shape` today carries facing, half, hinge, axis, connect. Furniture needs more:
trapdoor `open`, slab `type` top/bottom, stair `shape` (inner/outer corners), lantern
`hanging`, candle `candles` and `lit`, cauldron `level`, chiseled bookshelf slots, bed
`part`. Proposal: `Shape` gets one more field, `props`, a sorted
`key=value,key=value` string of extra state properties, already rotated to world
terms; `BlockMapper` applies each by name through the block's `StateDefinition`
(unknown property or value: log once, keep the default). Rotation lives in the core
(`FurnitureRotate`): `facing` and `axis` rotate, the `north/east/south/west` side
properties of fences, walls and panes rotate, `rotation` (0–15, standing banners and
signs) adds 4 per quarter turn; `shape`, `half`, `hinge`, `type`, `open` are
rotation-invariant. No mirroring: `Placement` only rotates.

**Sign arms (F4).** A wall sign survives only while the block it hangs on is there, and
the check runs on a neighbour update. Rules: (1) a template sign is valid only when its
support cell (opposite its `facing`) is a block of the same piece; F1's loader rejects
anything else, so the support is always in the same raster. (2) The raster emits a
piece's cells in two passes, supports first, then attached blocks (wall signs, wall
hooks, buttons, hanging lanterns), so within one chunk the support is written before the
sign. (3) Signs are written with the B1 flags (`UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, no
neighbour updates), so a sign written before its support at a chunk border (the other
chunk generates later) is not dropped; once both exist, later updates find the support.
(4) Blank signs: no text, a sign block entity with defaults; the waxed flag set so
players do not open the edit screen when they sit down (to verify in F4). The F4 mod
test places every seat piece in a live `ServerLevel`, then pokes a neighbour of each arm
(`updateNeighborsAt`) and checks the signs are still there. Residual risk: a player who
breaks the seat stair drops the arms, as in vanilla.

The viewer draws furniture cells with the block's colour at full size in v1, slabs and
carpets as half and 1/16 height, stairs as a slab plus a half-cube back, trapdoors and
pressure plates as thin boxes; good enough to judge layout and clutter. (Viewer item,
small.)

## Walkability invariant

A new invariant `furnished-rooms-walkable` in `check/Invariants`:

- **Blocking cells:** any furniture cell at y0 or y1 above the floor, except
  `carpet`, pressure plates, open trapdoors (an edge panel), wall signs (no collision)
  and wall-attached hooks and buttons. A chimney column cell (F7) blocks. Hanging lanterns at y2 or higher never block.
- **Rule:** in every room, the free floor cells (floor below, two cells of headroom, not
  blocking) form one 4-connected region containing: the inside cell of every door and
  archway, the foot and top cells of every stair or core opening, and every piece's
  clear (use) cells. Also: no furniture cell on a door cell, its two neighbours along
  the wall, or the cell in front of it on either side.
- A failure is a breaking invariant (like unreachable rooms), drawn in the viewer
  like the others. Test: a hand-built room where a sofa closes the only path → the
  invariant fails; the pass on 200 seeds of every type → 0 failures.

## Determinism

- Same rules as hooks: draws named by (building index, storey, leaf, purpose); no
  `HashMap` iteration; catalogue loaded in `index.txt` order, pieces sorted by id.
- Block variety inside a piece (chiseled bookshelf slots, candle count, plant choice)
  from `Variant.cellHash` of the world cell, as palettes pick blocks today.
- **Existing worlds:** furniture changes buildings. A world created before 7.5 must
  not get furniture in its newly generated chunks only (half-furnished houses at old
  chunk borders). A world setting `furnishings: true` pinned at creation (B6's
  `WorldConfigData`), default true for new worlds and false when the saved data
  predates it; the catalogue version is pinned with it, so John's later pieces show up
  in new worlds only.

# Chimneys

John 2026-10-23: "I think we should do chimneys. I know it's scope creep but it makes a
house/workshop look more correct." New scope, kept out of F3 as step F7 because it
touches the shell and the roof, not only room interiors.

- **Where.** Only above a fireplace: living rooms, halls and workshops (the workshop
  hearth becomes a fireplace, so workshops get a stack too). The **flue column** is the fireplace's
  centre cell, which backs onto an exterior wall; the stack is 1x1 inside the wall line,
  so it never leaves the lot or crosses into a street. External gable-end stacks (outside
  the footprint) are a later variant.
- **Through the storeys.** From the mantel (y2 of the fireplace's storey) the column
  goes up as `stone.block`, replacing floor cells and room air in every storey above.
  FurnishPass decides the fireplace on the lowest storey first and **reserves** the
  column in the rooms above, so their furniture and hooks avoid it and the walkability
  check counts it as blocking. A fireplace candidate whose column would hit a door, its
  3x3 neighbourhood, a stair core or opening, a corridor, or a dormer is
  rejected (the fireplace tries its next candidate, then drops to a fireplace without
  chimney).
- **Through the roof.** The roof heightfield is known (`RoofPass` runs before the
  interior pass; `RoofField` per group). The column replaces the roof voxels in its
  column and rises to `roof height at the column + 2`, and at least to the ridge + 1
  when within 2 cells of the ridge (the real-world rule of thumb, simplified). Flat
  roofs: parapet + 1. No change is needed on the neighbouring roof cells: the stack is
  solid, so the roof stays closed (the open-roofs invariant sees a solid column). A
  stack over the overhang or a gable wall cell is impossible: the flue is inside the
  wall line.
- **Top.** A lit `campfire` on the top cell, giving rising smoke above the roof (the
  hearth campfire below is unchanged; the column is solid, not a hollow flue, because a
  1x1 house cannot spare a 3x3 flue). Rain does not put campfires out in Java edition,
  as far as we know (not verified in 26.3: F7's client test checks it). Campfires do not
  spread fire. Optional cap variant per style: a `stone.slab` on two `stone.wall`
  posts over the campfire.
- **Who owns it.** FurnishPass picks the fireplace and writes a shell entry
  `chimneys: {id, room, x, z, fromY, toY}` (genVersion 3, with the furnishings). A new
  small `ChimneyPass` after the interior pass computes `toY` from the roof field and
  emits the column as shell boxes with a new role `chimney` (the palette maps it to the
  style's furnishing stone; the viewer gets a role colour). The raster gives `chimney`
  precedence over floor and roof cells, like openings over walls. Touches:
  `RoofPass`/`RoofField` (read-only query of the height at a column), the shell box
  roles, `PieceRaster` precedence, `BlockMapper` palette, viewer `ROLES`.
- **Invariants.** Floating blocks: the stack stands on the fireplace. Leaving the lot:
  impossible (inside the wall line). New check in the F7 tests: every chimney's top is
  above the roof at its column, and no chimney cell sits on a door, stair or opening.

# Designing in the viewer vs in game

John asked whether furniture can be designed in the viewer, since that is partly what
it is for. Today the viewer draws the IR: every cell is a **plain cube** coloured by
role or style palette (`viewer/src/render.ts`, `mesh.ts`). Stairs, doors, trapdoors,
signs, fences and rails are cubes; the IR boxes carry no facing or block state at all,
and hooks are small marker cubes. So:

- **Works well in the viewer:** placement, layout and room fit: which pieces a room
  gets, where they stand, clutter and free floor, walkability failures (drawn in red),
  differences between seeds and styles, the gallery across every building type. This is
  where generated rooms are previewed and reviewed (F5's `furniture` preset).
- **Needs shapes or the game:** the look of partial-block furniture: sign or trapdoor
  arms, stair seats, slab tables, fence legs, lantern on a post. As cubes these read as
  lumps.
- **Optional step F8, viewer block shapes:** draw stairs (straight and corner shapes),
  slabs, trapdoors (open and closed), fences and walls (post plus arms to neighbours),
  signs (wall and standing), doors and carpets with their real shapes, so furniture and
  the shell's rails and stairs can be judged in the viewer. Cost: M. Each shape is a
  small three.js geometry with rotations (moderate work per shape, about 8 shapes); the
  face-mesh path (`mesh.ts`, used above 200k voxels) must treat partial blocks as not
  hiding their neighbours' faces; and the viewer needs per-cell shape data: furniture
  cells have it from their templates, but shell stairs, rails and fences only get their
  shapes in `PieceRaster` (Java), so the CLI would export them or the IR would gain them
  (an IR change, S–M more). F5's "approximate shapes" for furniture cells is the cheap
  half of this; F8 makes them faithful and extends them to the shell.
- **Authoring stays in game** (structure block, [FURNITURE-AUTHORING.md](../FURNITURE-AUTHORING.md)):
  a person building with the real blocks is fastest and sees the real look. An editor in
  the viewer is still not planned.

# Steps (for the coordinator to size and schedule)

1. **F1 Catalogue data and roles** (core, S–M): `fb-furniture/1` reader,
   `FurnitureLibrary`, `furnishing` section in style JSON, role.form resolution table
   (wood family x form, stone family x form, cloth colour x form), the 25 pieces as
   JSON (hand-written in the layered format), tests: every piece resolves in every
   style to ids in a fixture of the 26.3 block list.
2. **F2 Converter** (cli, S–M): `fractal-cli furniture import <file.nbt>...` (a small
   gzip + NBT reader in the CLI, no Minecraft classes), `furniture check`,
   `furniture preview` (writes a test-room scene for the viewer). Tests with 3 small
   `.nbt` fixtures saved by a structure block.
3. **F3 Placement and invariant** (core, M–L): `FurnishPass`, programs per room use,
   fallback to old hooks, the walkability invariant, `furnishings` IR at genVersion 3,
   golden digests regenerated, galleries before/after for house, shop, workshop, hall,
   temple, tower.
4. **F4 Raster and states** (core + mod, M): furniture cells in `PieceRaster`,
   `Shape.props`, rotation, `BlockMapper` support; mod test: every piece in every
   style and rotation yields the expected states (incl. the open-trapdoor panel side);
   `/fb furniture show <id> [style]` dev command stamping a piece in 4 rotations at the
   player; world setting `furnishings`; sign arms: supports first, signs with the B1
   flags, the neighbour-update survival test (see "Block states").
5. **F5 Viewer** (viewer, S): furniture cells drawn with approximate shapes; a
   `furniture` gallery preset (each piece in a 7x7 test room per style).
6. **F6 Review** (heavy, S): client screenshots of furnished rooms (house, hall, shop,
   workshop) in two styles; REVIEW entry (includes whether 40 % coverage feels lived-in).
7. **F7 Chimneys** (core + mod + viewer, M): the "Chimneys" section: column reservation
   in FurnishPass, `chimneys` shell entry, `ChimneyPass`, role `chimney`, raster
   precedence over floors and roof, campfire top; touches the roof and shell
   generators. Tests: column clear of doors and stairs on 200 seeds per type, top above
   the roof, golden digests; galleries of houses, workshops and halls with chimneys;
   one client screenshot (smoke, rain).
8. **F8 Viewer block shapes** (viewer, M, optional; plus S–M if the shell's shapes go
   into the IR): the "Designing in the viewer vs in game" section.

F1 and F2 run in parallel; F3 and F4 need F1; F5 needs F3's IR; F7 needs F3 and F4;
F8 needs F5 and can run any time after; F6 after F5 (and after F7 if it is in).

# Open questions for John (answered 2026-10-23)

1. Is "stairs + open trapdoor arms" the chair look you want, or do you prefer sign
   arms (look nicer, but pop off if a neighbour block updates)?
   **John 2026-10-23: "Signs".** Sign arms everywhere; F4 handles the pop-off risk
   ("Block states").
2. Job blocks as furniture (smoker, cauldron, barrel, lectern) let villagers in
   hamlets claim them. Fine, or should kitchens use furnaces (no job) instead?
   **John 2026-10-23: "yes sounds awesome".** Kitchens keep smoker and cauldron.
3. Fireplaces without chimneys in v1: acceptable, or wait for a chimney in the roof pass?
   **John 2026-10-23: "I think we should do chimneys. I know it's scope creep but it
   makes a house/workshop look more correct."** New step F7 ("Chimneys").
4. How full is "lived-in": the 40 % floor cap is a guess.
   **John 2026-10-23: "Sounds good to try."** 40 %, revisited at F6.
