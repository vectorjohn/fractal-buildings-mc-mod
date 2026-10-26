# basements interior answer (2026-10-26)

Mode: question. Round: first.
Counts: 0 violations, 0 should-fix, 0 suggestions, 3 proposed rules.
Looked at: no pictures (no basements exist yet). Read `docs/design/STANDARDS.md`
(DS-12, 13, 22, 23, 24, 32), `docs/plans/furniture.md` (roles, catalogue, programs,
chimneys), `docs/FURNITURE-AUTHORING.md` (format), `docs/design/styles/README.md`,
and grepped the ROADMAP: no basement item exists; the only mention is 3.9a's note that
`no-gap-below` flags buried floors, so "basements will need a rule change".
Which buildings get one, how deep, and the outside access are the architect's call
(`basements-architect.md`, written in parallel). This file covers what goes inside.

## Question

John 2026-10-26: basements for houses (sometimes, perhaps by climate) and high-rises
(several levels: boilers and the like). What rooms go in them per building type and
era, how are they furnished and lit, where does the stair down go, how big are they?

## Recommendation

A basement is **service space, never living space**. It holds storage, drink, fuel,
plant and the dead. It doesn't count toward DS-12 (bedrooms first) and never holds a
bedroom, kitchen or living room. The one exception is the desert *sardab*, below.
Each basement room gets a **use** from a short list (`cellar`, `wine_cellar`,
`storeroom`, `undercroft`, `crypt`, `plant`, `lockers`, `laundry`, `vault`, `service`)
and a furnishing program like the rooms above.

**Shared spec, all types**

- **Height:** floor plus 3 clear (DS-23). Undercrofts, crypts and high-rise plant rooms
  get 4-5 clear, so that boilers and vaults fit.
- **Walls follow the walls above** (load-bearing): partitions in the basement sit under
  the ground-floor partitions, so its rooms are the ground plan's rooms, or merges of
  them. A cellar may cover only part of the footprint: under the kitchen or the
  stair, never under a porch.
- **Material, 60-30-10:** about 60 % the style's `stone` (walls; the floor is the
  same stone, or `packed_mud` / `coarse_dirt` in a rural root cellar, the period dirt
  floor), about 30 % main `wood` (barrels, shelves, posts, the stair) and at most 10 %
  light and metal (lanterns, chains, iron). No cloth: no carpets or beds below grade.
  Barrels have a fixed spruce colour; they read against any of our stones.
- **Fill:** dense along the walls, with a clear aisle 1 wide (2 in plant rooms, shops
  and high-rise corridors) from the stair foot to every door and every chest. Storage
  rooms look right at around 50 % cover, which is more than DS-13's 40 % (see Proposed
  rules). Until John moves the cap, keep to 40 %.
- **Light (DS-24):** basements have no daylight, so every cell's light comes from
  placed sources. Give each room one `lantern` (15) hung at y2 or on an `iron_chain`
  in taller rooms; rooms wider than about 12 cells get two. In corridors put a wall
  `torch` or lantern every 8 cells or less. Block light stays at 1 or more out to
  about 26 cells, but the closer spacing keeps corridors from looking like caves.
  Crypts can use `candle` groups (3 light per candle, 4 candles make 12) or a
  `soul_lantern` (10) for mood. They still need light 1 or more everywhere, so check
  the corners. Light blocks go on walls and ceilings, never in the aisle.
- **The stair down:** stack it **directly under the up flight**, the same stair core
  continuing down, entered through a door in the hall or kitchen. That is the period
  practice, it costs no extra plan area, and DS-5 and DS-22 already think in stacked
  cores. Small cottages and root cellars use a **hatch** instead: a `wood.trapdoor`
  in the kitchen or pantry floor over a ladder against a solid wall. Nothing may stand
  on the hatch cell or beside it. Halls and temples use a stair beside the sanctuary or
  in an aisle bay. High-rises use the stairwell and the elevator shaft, continued
  down (DS-22). Outside cellar doors and beer drops are the architect's.

## Per building type

**House** (style decides which, and the architect's climate rule decides if):

| Style / era | Basement | Contents |
|---|---|---|
| taiga, snowy, plains | root cellar, about 3x3 to 4x5, under the kitchen, hatch or stair | barrels in 2-high rows, `<wood>_shelf` on the walls, `hay_block`, `pumpkin`/`melon` as stored produce, a chest nook, one lantern; dirt or cobble floor |
| medieval town house | **undercroft**: the whole footprint, vaulted, stone; a merchant's store | barrel stacks, chests, a `long_table` as a trestle, stone pillars (`stone.wall` or full blocks) every 3-4 cells in rooms wider than 5 |
| plain (modern) and town houses | cellar plus **furnace room** | `furnace` (not a job block) with a `stone.wall` or `iron_bars` flue up to the chimney column (DS-15 stacks can start here), a `coal_block` bin behind a `wood.slab` front, a workbench, a laundry corner (`water_cauldron`, `barrel`) |
| any style, rare (about 1 in 5 of the cellars that exist) | **wine cellar** | `barrel_rack` (below) along both long walls with a 1-wide aisle; `candle` on a slab table |
| desert | **sardab** (Persian/Mesopotamian cool summer room) or a **cistern** | sardab: the only living basement, with low seating (stairs), a rug and a lantern; cistern: a stone rim around water |
| jungle, savanna, mangrove, swamp | none (wet ground, stilts) | n/a |

**Shop and tavern:** a **storeroom** under the shop floor, reached by a stair behind the
counter or in the back storeroom (shop storage stays behind the counter on both levels).
Contents: chest nooks and barrel stacks, shelves. A tavern (not a building type yet)
gets a **keg cellar**: barrels on their sides on a stillage of `wood.slab` along the
walls (the `barrel_rack`), plus a `long_table` for the cellarman. Leave out the
`brewing_stand`: it is a cleric job block, and wort is brewed upstairs anyway.

**Hall:** an undercroft the size of the hall, columns on a 3-4 grid, used as storage (barrels,
chests, a few `long_table`s). It is formal and symmetric: pillar rows match the hall's bays.

**Temple:** a **crypt** under the sanctuary or chancel only (not the whole nave). It is
symmetric: a central aisle 2 wide with `tomb` pieces (below) in bays on both sides and a small
altar (a `stone.slab` with candles) at the far end. A single `skeleton_skull` in a
wall niche is period-correct (ossuaries) and needs no entity; that is John's taste
call. `bone_block` ossuary walls are too macabre for every temple.
Light: candles on the tombs and the altar, and a soul lantern on a chain.

**Workshop:** a cellar only for trades that stock goods (cooper, brewer, smith's coal):
a storeroom of barrels and `coal_block`. It is reached from the work hall, not the
living wing.

**Tower:** none, or a single storeroom or dungeon cell under a defensive tower, and only
if the architect wants one. An `iron_bars` cell door and a chest give a dungeon cell.

**High-rise** (the most interesting case, as John says):

| Level | Rooms |
|---|---|
| B1 (every high-rise with a basement) | **service corridor** 2 wide from the stairwell; **plant room** 5x5 or bigger, 4-5 clear: `boiler` pieces, pipes, a coal or fuel bunker; **laundry** (`water_cauldron` row, barrels, a smooth-stone counter); **storage lockers** (`locker_cage` pairs along a 2-wide aisle) |
| B2 (towers of about 12+ storeys, the same threshold as the special floors in DS-32) | more lockers; **water tank** room (a 3x3x3 `copper_block`/`cut_copper` tank, ladder, pipes); in office towers a **vault** (`iron_block` walls, an `iron_door`, chests), the classic 1920s bank-tower basement |
| B3 (rare, tallest only) | **archive** (shelves, chiseled bookshelves, lectern-free), or a plain deep storeroom |

High-rise basements use concrete or the style's dressed stone walls, a `smooth_stone`
or polished floor, and light from flush `sea_lantern`/froglights in the ceiling or
`copper_bulb` (set `lit=true`; it keeps that state without redstone because we write without
updates). Pipes: waxed `lightning_rod` (thin copper, has an axis, so it runs
horizontally and vertically), `iron_chain` hangers, `waxed_copper_grate` vents, a
`lever` on the boiler front as a valve wheel (it needs a supporting block). Avoid
`iron_bars` as pipes: they need explicit side connections, and the writer sets no
neighbour updates. If used, the generator must set `east`/`west` etc. itself.
Lockers are an exception: their bars should look like cages, and the generator
sets their connections.

**Parking:** skip it. No vanilla idiom reads as a car park. An empty concrete floor
with `yellow_concrete` stripes and columns is cheap, but it is an empty room people
will call a bug, and the ramp is a large plan and street cost (the architect's call).
Revisit only if a vehicle mod enters scope.

## New pieces (text format of FURNITURE-AUTHORING.md)

**barrel_rack** 3-6 x1x2 against a wall: `barrel` facing south (on its side, lid out)
at y0 and y1, on a `wood.slab` top stillage optional at y0 in 4-high rooms. Rooms:
wine_cellar, cellar, storeroom, tavern keg cellar.
```
  = = = =        side view:  B B B B   y1
  B B B B                    B B B B   y0
  . . . .        aisle
```

**boiler** 2x2x3 (plant room, 4+ clear): `blast_furnace` pair at y0 facing the
aisle (fires; armorer job block, accepted like DS-14's smoker), `copper_block` x4 at
y1-y2 (the drum), a `lightning_rod` riser from the drum to the ceiling, `lever` on the
drum front at y1 (valve).
```
  = = =
  D D r      D drum (copper, y1-y2), blast furnaces under it at y0, r rod riser
  O O .      O blast furnace fronts
  . . .      2-wide aisle
```

**locker_cage** 2x2x2 per locker, in rows: `iron_bars` front and sides (connections
set), an `iron_door` (or `wood.door`) in the front, a `chest` (hook) and a `barrel`
inside. Rooms: lockers.

**tomb** 1x2x1 in a crypt bay: `stone.block` (polished or chiseled) x2, a `candle`
(lit) on the head cell; optional effigy: `stone.slab` top. Rooms: crypt.

**root_shelf** 1-3 x1x2: `wood.shelf` at y1 over `barrel`/`hay_block`/`pumpkin` at
y0. Rooms: cellar.

## Reasons

- **Period:** cellars and undercrofts were storage and trade space (medieval
  undercrofts held goods, as in Chester and Winchester). Root cellars are a cold-climate
  answer to winter. Coal cellars and boiler rooms are 19th-20th century town houses
  and towers. Crypts sit under the chancel, not the nave. Desert houses dug down for
  coolness (the sardab). Nobody slept in a cellar who could avoid it.
- **Minecraft:** a basement has no windows, so it is dark by default and a mob
  spawner (DS-24). Planning every room's light is the main design cost. Barrels, shelves,
  furnaces and copper give vanilla a rich storage vocabulary, while cloth and beds
  don't belong. The stair under the stair keeps DS-5 and DS-22 cores straight and
  costs no extra floor.
- **Standards:** storage-only basements leave DS-12 and the house programmes
  untouched. Load-bearing walls keep DS-11 shapes, because the plan is the one above.

## Alternative

Finished basements (a rec room or a guest bedroom, as in the modern American basement)
for plain/modern houses and high-rise amenity floors (a gym or a bar on B1). That is
real postwar practice, but it puts living rooms where daylight never reaches, and
DS-12 would have to say whether they count. I would hold it for later, maybe as
a high-rise B1 "club room" like the special floors in DS-32.

## Rule?

Three proposed rules:

- **Basements are service space** / basement rooms take only service uses (storage,
  cellar, plant, lockers, laundry, crypt, vault), never bedrooms, kitchens or living
  rooms (desert sardab excepted), and don't count for DS-12 / why: period practice,
  no daylight / scope: all types with basements / enforcement: code (room-use check).
- **Basements are lit throughout** / an extension of DS-24: one lantern per room
  (two when wider than 12 cells), corridor lights every 8 cells or less, the light hook
  required in every basement room / why: no daylight and no windows, so the spawn
  risk is certain / scope: all basements / enforcement: code (DS-24's candidate light
  check covers it once built).
- **Storage rooms may fill to about 50 %** (an amendment to DS-13, which caps fill at
  40 %) / cellars, storerooms and keg cellars look right packed, as long as the
  1-wide aisle to every chest and door holds / scope: storage uses / enforcement:
  code (`COVER_CAP` per use). This needs John's word, since DS-13 is accepted.

For John: decide whether a skull niche belongs in crypts, and whether finished
basements (rec room or club room) are wanted at all.
