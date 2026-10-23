# Plan: multi-block furnishings

Status: design proposal, not scheduled. Author: assistant, 2026-10-23. ROADMAP item 7.5.
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
| `wood` | `planks stairs slab trapdoor fence fence_gate door pressure_plate button shelf log` | a wood family: `oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `mangrove`, `cherry`, `pale_oak`, `poplar`, `bamboo`, `crimson`, `warped` |
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

Legend used in sketches: `S` stairs, `s` slab, `T` open trapdoor (thin panel),
`t` closed trapdoor, `F` fence, `p` pressure plate, `c` carpet, `B` barrel, `K` bookshelf,
`L` lantern, `P` plant pot, `b` bed (hook), `C` chest (hook).

## Seating

**1. chair** 1x1x1. `wood.stairs` facing north (tall half at the back, seat to the
south). Variant *banner back*: a `cloth.banner` standing on the cell behind (only
against a partition with room behind it; otherwise skip). Placement: at a table side,
back away from the table; or against a wall. Rooms: kitchen, dining, living, bedroom
(desk), hall, shop, workshop.

**2. armchair** 3x1x1, against a wall or free-standing with its back to a rug.
`accent.trapdoor` open, `wood.stairs` facing north, `accent.trapdoor` open (panels on
the edges touching the seat). Rooms: living, bedroom (area 20+), hall.

```
  = = =
  T S T        arms: open trapdoors, panels against the seat
  . . .        clear in front (at least the centre cell)
```

**3. stool** 1x1x1. `wood.slab` bottom. Free-standing at a counter or workbench.
Rooms: kitchen, workshop, shop. (Replaces today's "bench" spruce slab.)

**4. sofa** 4x1x1 (2–3 seats plus arms). `accent.trapdoor` open, 2–3 x
`wood.stairs` facing north, `accent.trapdoor` open. Optional `cloth.carpet` row in
front (walkable). Rooms: living, hall.

```
  = = = = =
  T S S S T
  c c c c c    optional carpet, walkable
```

**5. corner_sofa** 3x3x1 in a room corner. Stairs along both walls, the corner stair
with `shape` `inner_left`/`inner_right` so it joins (our writes do not run neighbour
updates, so the template must carry the corner shape); trapdoor arms at both open ends.
Rooms: living (area 24+).

```
  = = = =
  = S S T
  = S . .
  = T . .
```

**6. pew** 3–5 x1x1, a row of `wood.stairs` facing away from the altar with
`wood.trapdoor` open at both ends; rows with one clear row between them, an aisle
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
Rooms: kitchen. (Smoker is the butcher's job site: villagers in hamlets may claim it.)

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
`plant.pot` or `candle` on the mantel ends when the room is 4+ high. No chimney in v1
(smoke particles rise into the ceiling; a chimney is a roof-pass item for later).
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
most about 40 % of the floor cells; rugs do not count).

| Room use | Required | Optional (by weight) |
|---|---|---|
| bedroom | bed_set (double_bed if area 20+; two bed_sets where today two beds) | wardrobe, dresser, desk + chair, rug, potted_plant, floor_lamp |
| living | sofa or armchair x2, coffee_table | fireplace, bookcase, rug, floor_lamp, potted_plant, chandelier (24+) |
| kitchen | kitchen_run | post_table or dining_table + chairs/stools, potted_plant |
| dining | dining_table + chairs | dresser, chandelier, rug |
| entry, landing | chest_nook | potted_plant, rug, pew (as a bench) |
| hall | long_table + chairs, fireplace | chandelier, bookcase, armchair, rug |
| workshop | workbench | stools, counter (wood), chest_nook, sink (as tub) |
| shop | shop_counter | chest_nook, potted_plant, shelves |
| nave, chapel | pews | chandelier |
| sanctuary | (altar hook as today) | bookcase (1 wide), candles, potted_plant |
| tower room | bed_set (single) | post_table + chair, chest_nook, bookcase |
| corridor, stair | none | potted_plant, floor_lamp (only where 3+ wide) |

Today's hook kinds map in: `bed` and `chest` stay hooks inside pieces; `table` becomes
post_table or dining_table; `hearth` becomes fireplace (living, hall) or stove
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

The viewer draws furniture cells with the block's colour at full size in v1, slabs and
carpets as half and 1/16 height, stairs as a slab plus a half-cube back, trapdoors and
pressure plates as thin boxes; good enough to judge layout and clutter. (Viewer item,
small.)

## Walkability invariant

A new invariant `furnished-rooms-walkable` in `check/Invariants`:

- **Blocking cells:** any furniture cell at y0 or y1 above the floor, except
  `carpet`, pressure plates, open trapdoors (an edge panel) and wall-attached hooks
  and buttons. Hanging lanterns at y2 or higher never block.
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
   player; world setting `furnishings`.
5. **F5 Viewer** (viewer, S): furniture cells drawn with approximate shapes; a
   `furniture` gallery preset (each piece in a 7x7 test room per style).
6. **F6 Review** (heavy, S): client screenshots of furnished rooms (house, hall, shop,
   workshop) in two styles; REVIEW entry.

F1 and F2 run in parallel; F3 and F4 need F1; F5 needs F3's IR; F6 last.

# Open questions for John

1. Is "stairs + open trapdoor arms" the chair look you want, or do you prefer sign
   arms (look nicer, but pop off if a neighbour block updates)?
2. Job blocks as furniture (smoker, cauldron, barrel, lectern) let villagers in
   hamlets claim them. Fine, or should kitchens use furnaces (no job) instead?
3. Fireplaces without chimneys in v1: acceptable, or wait for a chimney in the roof pass?
4. How full is "lived-in": the 40 % floor cap is a guess.
