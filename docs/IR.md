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
lot      { id, x, z, w, d: int, front: facing }          world columns [x,x+w) x [z,z+d)
building { id, lots: [lot id], origin: [x,y,z], rotation: 0..3,
           shell: shell, siteBoxes: [box] }
shell    { spec, specHash: "<16 hex>", bounds: {min,size}, storeys: [storey],
           volumes: [volume], rooms: [room], openings: [opening],
           roofs: [roof], boxes: [box] }
spec     { genVersion, specSeed: "<int64>", style, type, width, depth,
           storeys, roof }
storey   { index, y, height }       y = local y of the slab; stand at y + 1
volume   { id, min, size, parent: id | null }
room     { id, storey, volume, use, parts: [{min,size}] }
opening  { id, kind: door|window|stair, min, size, facing, from, to }
           from/to: room id or "outside"; stair facing = direction of ascent
roof     { form, volume, ridgeAxis: "x"|"z"|null, baseY, rise, run }
box      { min, size, role, layer, storey }
           role: air|foundation|floor|wall|trim|window|door|roof|stair
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

## Versioning

`format` changes only when an old reader would misread a file; adding optional
keys is fine. `genVersion` changes whenever generated output changes on purpose
(it is part of every cache key).
