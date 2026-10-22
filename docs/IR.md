# IR: `fb-scene/1`

The JSON the planner writes and the viewer reads. Writer: `core/ir/IrJson.java`
(deterministic: same scene gives byte-identical text, UTF-8, trailing newline).
Structural checks: `core/ir/IrValidator.java` (invariant id `ir-valid`).
Worked example, written by the writer and compared byte for byte in
`SampleFixtureTest`: `viewer/fixtures/sample-house.json`.

## Conventions

- Integers only. 64-bit values (`seed`, `specSeed`) are decimal **strings**, since
  JavaScript numbers lose precision above 2^53.
- Keys appear in the order below. Lists of lots, buildings, storeys, volumes,
  rooms, openings, roofs, boxes, siteBoxes and violations have one element per
  line; every other object sits on one line. Readers must not rely on the layout.
- `min` is `[x, y, z]`, `size` is `[sx, sy, sz]`; a box covers `[min, min + size)`.
- Directions: `facing` is `"north"|"east"|"south"|"west"` (north = -z, east = +x).

## Schema

```
scene    { format: "fb-scene/1", genVersion: int, seed: "<int64>",
           terrain: terrain | null, lots: [lot], buildings: [building],
           violations: [violation] }
terrain  { x0, z0, nx, nz: int, floorY: [int; nx*nz] }   row-major, z outer
lot      { id, x, z, w, d: int, front: facing, airRights?, frontY?: int }   airRights, frontY: only when airRights > 0          world columns [x,x+w) x [z,z+d)
building { id, lots: [lot id], origin: [x,y,z], rotation: 0..3,
           shell: shell, siteBoxes: [box] }
shell    { spec, specHash: "<16 hex>", bounds: {min,size}, storeys: [storey],
           volumes: [volume], rooms: [room], openings: [opening],
           roofs: [roof], boxes: [box], hooks: [hook] }   hooks: genVersion >= 2 only
spec     { genVersion, specSeed: "<int64>", style, type, width, depth,
           storeys, roof, maxHeight, streets, lead? }   maxHeight, streets: genVersion >= 2 only; lead: only when > 0
storey   { index, y, height }       y = local y of the slab; stand at y + 1
volume   { id, min, size, parent: id | null }
room     { id, storey, volume, use, parts: [{min,size}] }
opening  { id, kind: door|window|stair|arch, min, size, facing, from, to }
           from/to: room id or "outside"; stair facing = direction of ascent
roof     { form, volume, ridgeAxis: "x"|"z"|null, baseY, rise, run }
hook     { id, kind, room: room id, pos: [x,y,z], facing }
box      { min, size, role, layer, storey }
           role: air|foundation|floor|wall|trim|window|door|roof|stair|beam|fence
           layer: foundation|shell|interior|roof;  storey: index or -1
violation { invariant, building: id | null, message, cells: [{min,size}] }
```

Examples (from the sample house):

```
{"id": "lot0", "x": 98, "z": -42, "w": 11, "d": 9, "front": "north"}
{"index": 0, "y": 0, "height": 4}
{"id": "v0", "min": [0, 0, 0], "size": [7, 5, 5], "parent": null}
{"id": "r0", "storey": 0, "volume": "v0", "use": "room", "parts": [{"min": [1, 1, 1], "size": [2, 3, 3]}]}
{"id": "o0", "kind": "door", "min": [1, 1, 0], "size": [1, 2, 1], "facing": "north", "from": "r0", "to": "outside"}
{"form": "gable", "volume": "v0", "ridgeAxis": "x", "baseY": 5, "rise": 1, "run": 1}
{"min": [0, 1, 0], "size": [7, 3, 1], "role": "wall", "layer": "shell", "storey": 0}
{"invariant": "in-lot", "building": "b0", "message": "...", "cells": [{"min": [0, 0, 0], "size": [1, 1, 1]}]}
```

## Meaning

- **Local frame**: +x along the front, +y up, +z from the front (z = 0) to the
  back; y = 0 is the ground-floor slab. Shell boxes lie inside `bounds`; site
  boxes (foundations, fill) may go below y = 0.
- **Boxes apply in order**, shell boxes then site boxes; a later box wins where
  they overlap; `air` clears.
- **Placement** (`Placement.java`): rotation r turns clockwise seen from above;
  the local front faces `north.rotate(r)`; the rotated footprint starts at
  `origin` in +x/+z. Local column (lx, lz) with bounds extent (sx, sz) goes to:
  - r0: (ox+lx, oz+lz)
  - r1: (ox+sz-1-lz, oz+lx)
  - r2: (ox+sx-1-lx, oz+sz-1-lz)
  - r3: (ox+lz, oz+sx-1-lx)

  and y is oy + ly. Facings rotate with `north.rotate(r)` (north, east, south, west order).
- **Role flags** (`Role.java`): `solid` = blocks movement and can be stood on
  (all but `air` and `door`); `closesShell` = counts as closed when checking sealed
  rooms (all but `air`).
- **specHash**: SHA-256 of the spec's canonical compact JSON (keys in the order
  above, no spaces, `specSeed` as a string), first 8 bytes as 16 lower-case hex
  digits (`SpecHash.java`). It is the stable byte form of the cache key.
- **ir-valid** checks: ids unique (lots, buildings; volumes, rooms, openings per
  building); building lots, volume parents, room volumes, opening ends and roof
  volumes resolve; room and box storeys exist; storeys are numbered from 0 and
  stack (`y` of storey i+1 = `y + height` of storey i, first `y` = 0); volumes,
  room parts, openings and shell boxes lie inside `bounds`.

## Generator v1 additions (genVersion 2, Phase 4)

Added keys only; `format` stays `fb-scene/1` (plan: [plans/phase4.md](plans/phase4.md)).
Writers emit them only when the spec's `genVersion` is 2 or more, so v0 files are
byte-for-byte unchanged; readers treat them as absent for genVersion 1.

- **spec.width / spec.depth** mean the *envelope*: the part of the lot the shell may
  use. The shell's `bounds` cover exactly `[0, width) x [0, depth)` in x and z; the
  building's volumes sit somewhere inside it.
- **spec.maxHeight**: exclusive bound on local y, chosen by the spec policy so that the
  building stays at or below world y 319 wherever the instance pass sets it down on the
  lot. **spec.streets**: bit mask of local sides facing a street (1 north = front,
  2 east, 4 south, 8 west); the front bit is always set. **spec.roof** is `"auto"`.
  **spec.lead** (Phase 5 JT, written only when above 0): blocks the envelope extends in front of the type's usual
  front edge (its front margin plus the air rights), so upper storeys can jetty over the street; local z = 0 is that
  far in front of where it would be and ground masses start at z >= lead. **lot.airRights / lot.frontY** (written only
  when airRights > 0): how far past the front edge, over the street, voxels are allowed, at world y >= frontY + 3;
  the `in-lot` invariant accepts exactly that strip (front side only) and nothing else outside the lot.
- **volume ids** are `<kind><n>` (`main0`, `wing1`, `jetty1`, `podium0`, `shaft1`,
  `crown2`, `hall0`...); a volume's box runs from its first storey's slab to its top
  slab inclusive. A volume whose `min` y is above 0 stands on its `parent` (inside it:
  a setback; around it with at most 2 blocks of overhang: a jetty).
- **roles** `beam` (posts, beams, half-timbering, columns) and `fence` (railings,
  balustrades); both solid and closed.
- **hooks**: furnishing hooks, `{id, kind, room, pos, facing}`; `pos` is a cell of room
  air standing on a floor; `kind` is a free tag (`bed`, `table`, `workstation`,
  `chest`, `hearth`, `altar`, `counter`, `light`...). The mod places furniture there
  (6.4); the planner never does. `ir-valid` checks ids are unique, `room` resolves and
  `pos` lies inside `bounds`.
- **roof.form** strings in use (the roofs step, 4.3, owns this line): `gable`, `hip`, `pyramid`, `shed`, `mansard`, `flat`. One record per roofed mass; masses of one roof group share the form; `ridgeAxis` only for `gable`.
- **room.use** strings in use (the interiors step, 4.5, owns this line): `room`, `entry` (the room behind an entrance), `corridor`, `stair` (the room holding a core), `hall` (an open mass); a type's `RoomProgram` may add its own.
- **hook.kind** strings in use (the interiors step, 4.5, owns this line): `bed`, `table`, `chest`, `workstation`, `hearth`, `light` (default `RoomProgram`); hooks are floor cells against a wall or partition, never within one cell of a door, the entrance or a core.

## Versioning

`format` changes only when an old reader would misread a file; adding optional
keys is fine. `genVersion` changes whenever generated output changes on purpose
(it is part of every cache key).

## Stilts and window holes (tower watchtowers, genVersion 2)

No new keys. A building on stilts (tower type, stilted lookout) has **storey 0 = the open leg zone**: `y` 0, `height` = the leg height, no rooms and no walls; the room above is storey 1 and later. Its volumes start at `y` = the leg height (parent `null`), and four extra 1 x 1 volumes `leg0`..`leg3` (north-west, north-east, south-west, south-east) stand on the ground under its corners, each with a `beam` column from y 0; a `stair` flight (each step on a block) climbs along the front wall to the entrance door, whose `y` is above storey 0. An **unglazed window** is an opening of kind `window` whose cells are `air` (no `window` voxels); `shell-closed` and `roof-closed` count air inside a declared `window` opening as closed.

## District scenes (Phase 5, additive to `fb-scene/1`)

Added keys only, written only when present, so every existing file and digest is unchanged
(plan: [plans/phase5.md](plans/phase5.md)). A district scene is an ordinary `fb-scene/1` file holding every built
lot of one district tile.

- **`terrain.water`**: optional `[int; nx*nz]`, water depth above `floorY` per column (0 on land), same layout as
  `floorY`. Absent means no water. Reader: non-negative, length `nx*nz`.
- **`ground`**: optional top-level list of boxes, after `buildings` and before `violations`, one per line, same shape as
  a shell box (`{min, size, role, layer, storey}`) but in **world** coordinates, not shell-local. For road surfaces,
  plaza paving, fill, cuts (`air`), bridge decks and piers. Every ground box has `layer: "ground"` and `storey: -1`
  (`ir-valid` checks both). Boxes apply in list order; later wins. Road boxes sit at `y - 1` where `y` is the street's
  surface height (heightmap convention, the first free y).
- **roles** `road`, `paving`, `ground` (fill earth), `deck` (bridge decks and piers, TR 5.3: planks, sandstone in the desert): solid and closed. Every style's `palette` and `colours` carry them
  (`road`: gravel/path gradient, `paving`: stone bricks, also a quay street's surface, `ground`: dirt, `deck`: planks; desert uses sandstone and sand).
- **layer** `ground`.
- `lots` and `buildings` list every built lot of the district; building ids are `b0`..`bN` in lot order.

## Settlement plans (Phase 5): `fb-settlement/1` and `fb-district/1`

Settlement plans do not carry voxels, so they get their own formats beside `fb-scene/1` (plan decision 4).
Code: `core/settlement/` (`SettlementPlan`, `DistrictPlan` and the records they hold; `SettlementJson`,
`DistrictJson`). Same conventions as above: integers only, 64-bit seeds as decimal strings, keys in the order shown,
list elements one per line, trailing newline, strict readers (errors start with the JSON path `$.lots[3].front`,
unknown keys ignored). `planVersion` is 1 for all of Phase 5; a reader rejects any other. Sample files, written by
`fractal-cli sample-city` and byte-compared in `SampleSettlementTest`: `viewer/fixtures/settlement/sample/`.

Points are world `[x, z]` columns. A road's `points` are its centre line; every segment is axis-aligned or at 45
degrees. A road of width `w` covers the columns `p - w/2 .. p - w/2 + w - 1` around each point of the line.
**Paths** (`detail`, `scene`) are relative to the directory of `settlement.json`.

```
settlement { format: "fb-settlement/1", planVersion: 1, genVersion, seed: "<int64>",
  site:      { id, tier, centre: [x, z], radius, claimant: [cx, cz], cells: [[cx, cz]] },
  bounds:    { x, z, w, d },
  coarse:    { x0, z0, step, nx, nz,               row-major, z outer, lattice columns x0 + i*step
               floorY: [int], water: [int],        water depth above floorY
               biome: [int], biomes: [string],     index into biomes
               mask: [int],     0 wild, 1 urban, 2 water, 3 steep, 4 open (park, green)
               density: [int],  0..100
               quarter: [int] },  index into quarters, -1 none
  quarters:  [{ id, kind }],    id = position; kind: downtown|old-town|market|residential|workshop|waterfront|park|outskirts
  cells:     [{ cx, cz, site: [x, z], polygon: [[x, z]] }],
  districts: [{ id, ix, iz, x, z, w, d, origin: [chunkX, chunkZ], quarter, density,
                styles: [{ style, weight }], lots, detail: "<path>" | null }],
  roads:     [{ id, kind, width, points: [[x, z]], bridges: [[i, j]] }],   kind: arterial|main|street|quay|lane|path (a `quay` is the waterfront promenade, width 4, TR 5.3);
                                                                           bridges: inclusive point-index ranges over water
  gates:     [{ id, kind, x, z, cells: [[cx, cz], [cx, cz]] | null, road: id | null }],   kind: cell|wall|exit
  landmarks: [{ id, kind, x, z, w, d, district }],   kind: square|market|green|park|temple|hall|tower
  walls:     [{ id, points: [[x, z]], closed: bool }],
  violations: [violation] }                           violation as in fb-scene/1

district { format: "fb-district/1", planVersion: 1, genVersion, seed: "<int64>",
  district: { id, ix, iz, x, z, w, d, quarter, density },
  streets:  [{ id, kind, width, points: [[x, z]], y: [int] }],   y per point: road surface (first free y)
  plazas:   [{ id, kind, x, z, w, d, y }],
  lots:     [{ id, x, z, w, d, front, type, style, storeys: [min, max], streets, airRights, frontY,
               density, status, walk? }],       front: north|east|south|west; status: "build" | "empty:<reason>"
               walk?: [base, prev, next] on town-wall (ring) lots only: base = world y of the lot's ground slab in the ring's walk profile (the generator uses it instead of the
               highest ground under the footprint), prev/next = work at the lot's anticlockwise/clockwise end: >0 a stair ramp up that many blocks to a higher
               neighbour, <0 a stair cut down through a curtain end to a lower tower doorway, 0 none (|n| <= 10; optional key, written only when present)
  scene:    "<path to the fb-scene file>" | null,
  violations: [violation] }
```

- **Ids**: districts `d<ix>_<iz>`, a negative index written with `m` (`d3_m2`); the id must match `ix`, `iz`.
  Ids are unique within each list.
- **Tiles** are chunk aligned: `x`, `z`, `w`, `d` multiples of 16, `origin` = `[x/16, z/16]`. The district reader
  rejects a lot that lies outside its tile (invariant `lot-in-tile` checks the same on an in-memory plan).
- **`streets` in a lot** is the mask of its sides facing a street in the **lot's local frame** (1 front, 2 east of the
  front, 4 back, 8 west of the front, as `BuildingSpec.streets`); the generator forces the front bit. `frontY` is the
  street surface y in front of the front edge centre, or `-2147483648` (`Brief.NO_FRONT_Y`) if unknown. `airRights` is how far the building may overhang the street (0 to 2; JT).
- **Skeleton output** (SK, `Skeletons.plan`, CLI `city`): `quarters` always lists all eight kinds in the order shown above
  (`id` = position), so merged cells need no renumbering. The coarse lattice is 32 blocks; `mask` and `density` are 0/0
  outside the urban part, `quarter` is -1 there. `districts` hold the tiles whose centre lies in a member cell and that
  contain an urban column, `quarter` the mode and `density` the maximum over the tile, `styles` empty, `lots` 0, `detail`
  null. Roads: `r<cx>_<cz>_<n>` per cell (gate routes first, then exits; `main`, or `path` for a hamlet), arterials
  `a<axis><line>_<from>` (axis 0 is a line of constant x; one road per tile-border segment, the parts of the cells it crosses
  laid end to end, so two consecutive points may be 1 block apart at a cell border). Gates `g<n>` sorted by (x, z): `cell`
  gates (listed by both cells, `cells` = the pair in (cz, cx) order, `road` = the lower cell's road if it has one), `exit`
  gates at the end of an exit road. A gate column is the 32-lattice column nearest the shared border, which may belong to
  either neighbour. Landmarks `l<n>`: hub square (`green` village, `market` town, `square` city and up) with a hall or
  temple facing it, all inside one tile. `fractal-cli city` also writes `terrain.json.gz`, the plan raster as a terrain-only
  `fb-region-dump/1` (step 32) for the plan view background. Fixtures: `viewer/fixtures/settlement/skeleton/`.
- **District detail** (DL, `Districts.plan`, CLI `city --districts`): `streets` are the skeleton's main roads and paths, the
  tile-border arterials and plain border streets (in a town, city or metropolis the *street lattice* of `RoadNetwork`, ROADS2: every tile-border segment that
  is an arterial candidate or has 2 of its 3 sample points urban, is dry enough and not steeper than 1 in 3, and that a chain of such segments links to a
  live road is drawn, an arterial where the rule picked one and a 5-wide street otherwise; an arterial no chain links is not drawn), the grid lines shared
  along a row or column of tiles (they stop short of a border that has no street), hashed port lanes (not in lattice tiers) and
  frontage lanes, each clipped to the tile plus 8 blocks (so two tiles at a border list the same streets; a lot never lies outside the
  tile). Ids: the road's own (`r..`, a later piece of the same road gets `.1`...), `a<axis><line>_<from>` arterials, `b<axis><line>_<from>`
  border streets, `sx<x>_<z>` / `sz<z>_<x>` grid lines, `t<n>` ports, `n<n>` lanes, `quay<x>_<z>` waterfront promenades (kind `quay`, width 4: the marching-squares contour of "water within 8 blocks" on the 8-lattice, kept in the waterfront quarter, on a bank at most 4 above the water, runs of 40+ columns; `k<n>` lanes link them to the nearest street); sorted by id. `points` are the vertices plus both columns of every change of height (ROADS), so floor
  interpolation of `y` between consecutive points (`Grading.road(points, ys, width, exact)`, `Briefs.frontY`) gives every column's height
  exactly; a road two tiles share (skeleton road, arterial or border segment, grid run, quay run) is graded once as a whole, so both tiles
  store the same heights. A tile may be road-only (outskirts, density 0, no lots): one a main road crosses on its way to an urban tile. `lots`: `airRights` is the jetty reach over the front street (0 on a front under 4 wide or a plaza, 2
  from width 7, else 1), `frontY` the street surface at the front centre, `style` `auto` until the brief filler (`Briefs.fill`) runs.
  Hall and temple landmarks of the skeleton are lots (`hall`, `temple`) flush against the hub square, or (no room there) on the nearest
  street of the hub tile that reaches the hub; `CityPlanner` moves the settlement's landmarks to those lots and to the (possibly moved) square. Rules added to `SettlementChecks`:
  `lot-frontage`, `lot-dry`, `lot-grade`, `roads-connected` (per tile), `tile-border-agree` (two plans).
- **City pipeline** (D0, `CityPlanner`, CLI `city --districts [--scenes all|none|auto:N|ID,ID] [--interiors] [--check]` and `city-check`): one
  shared source builds every member cell once, then each owned tile is planned and briefed (`Briefs.fill` takes the quarter at each lot's
  centre, not its district's); `--scenes` builds exterior scenes (`scenes/<id>.json`, the district file's `scene` is set) for all, none, `N` chosen
  ones (the hub tile, then one per quarter kind, in a hash order) or named districts. The settlement's `violations` also hold the two
  whole-city rules: **`tile-border-agree`** for every pair of neighbouring tiles, and **`roads-connected`** (city part): the street and plaza columns of
  all tiles form 4-connected pieces; every piece that holds the front of a lot that builds must be the piece of the hub (bridges are street
  points). A piece that holds no lot front is a *stray*, counted by `city-check`; since ROADS2 it is also a violation, **`roads-attached`**: no street or plaza
  piece of the city may be cut off from the hub (strays are 0 on every window and site checked). To keep lots off fragments, `Districts` gives no lots to
  streets whose piece within the tile and its margin holds no live road. The site id is `s<cx>_<cz>` of the claimant cell with negative parts written `m` (`Ids.site`), for `--cell` and `--site` alike.
- **Brief** (`GenRequest.brief`, optional, `core/gen/Brief`): `{minStoreys, maxStoreys, streets, airRights, frontY, walk?}` (`walk` = `Brief.Walk(base, prev, next)`, ring lots only, see `fb-district/1` lots)
  from a lot of the plan. `SpecPolicyV1` draws storeys from the brief's range intersected with the type's (if they do
  not meet, the type's bound nearest the brief) and sets `spec.streets` from the brief (front forced, unless the type
  has `allStreets`). With `airRights` > 0, a known `frontY`, a style with jetties (medieval), a house or shop of 2+
  storeys and ground no lower than `frontY` across the lot, it sets `spec.lead`: the upper storeys of the main stack may
  jetty over the street (front overhang 1 to `airRights` blocks past the lot edge; the front wall stays at the usual
  front edge, so the strip over the street starts at storey 1, at least 4 blocks above the street). Without a brief
  nothing changes.
- **Checks** (`core/check/SettlementChecks`, separate from `Invariants`): `lot-in-tile`, `lots-disjoint` (lots overlap no
  other lot, street footprint, plaza or landmark). Later steps add rules there.
- **Town walls (D2, `settlement/tiers/WallRing`)**: a settlement's `walls` entry is a closed, clockwise, axis-aligned rectangle (4 corners, NW first). Its lots are `wall` lots (24 to 64 long, 7 to 11 deep, front outward) and 12 x 12 `tower` lots at the corners, ids `<district>.w<n>` (n = position along the ring, the same in every tile). A gate is a `kind: wall` gate on the ring line at the column where a main road crosses; its lot is a `wall` lot exactly 23 long, whose passage is the 5 columns round the gate column, and the road runs straight and perpendicular for 14 blocks either side. `lots-disjoint` lets a street cross a gate lot inside that passage only; ring lots are exempt from `lot-frontage` (they face the field), and are never swapped for another type. No lot straddles a tile line: the ring's coordinates keep 30 blocks from them.

# Archways (5.11)

Additive, no new keys: `opening.kind` may be `arch`, a doorless opening between two rooms of one storey (open plan or an archway), 1 to 3 cells wide along the partition and 2 high. Its cells are `air` carved from the partition (no `door` voxels), with a `trim` lintel above it when the span is 2 or 3 wide and the storey has room. It joins the room graph like a door (`rooms-reachable`, `room-graph`). `room.use` adds `landing` (house upper storeys). Consumers that only know `door` ignore it harmlessly except that they must accept the kind when parsing.
