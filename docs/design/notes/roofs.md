# Notes: building pitched roofs

Build guidance for [DS-36](../STANDARDS.md) (smooth pitched
roofs). Not rules: the implementer may do it another way if the rule's
checks hold. Drafted by the architect, 2026-10-25 (Minecraft 26.3).

- 1:1: the top cell of every course is a stair facing up the slope; odd
  spans cap the ridge row with a bottom slab, even spans meet back to back.
- 1:2: courses alternate bottom slab and top slab.
- 2:1 (taiga, snowy): a stair over a full block of the same material on each
  course (or over an upside-down stair, for a layered look); no smoother
  shape exists for this pitch.
- Hip lines take outer-corner stairs and valleys inner-corner stairs; the
  writer sets the stair `shape` state itself (no neighbour updates).
  Overhangs may show upside-down stairs underneath as a soffit (DS-27).
- Hay thatch (savanna) has no stairs or slabs: hay for the body courses, a
  wood's stairs and slabs at eaves, ridge and verges
  ([styles/savanna.md](../styles/savanna.md)).
- Code, as of 2026-10-25: the raster gives `ROOF` cells no shape
  (`Shapes.of`: only `STAIR` cells become stairs) and `Shape` has no corner
  field; the mod's `ShapeTable` already maps blocks to their stairs and
  slab. The viewer draws cubes, so check roofs in game.
- Code, as of 2026-10-28 (DS36ROOFS): `RoofPass` emits `roof_stair_n/e/s/w`, `roof_slab` and `roof_slab_top` for the exposed top cells of pitched roofs; `Shapes.roofStair` sets the corner `shape` with Minecraft's rule from every stair the world will hold beside it (r2: interior and porch stairs and the tile's other buildings count too); a hip's ring corners alternate their facing along x and z so the eave stairs stay straight, down to the eave overhang's corner (r2); each building piece also writes the stairs one block across the chunk border beside its own (`StairSeam`, r2), so a tree's neighbour update cannot reshape a roof by chunk order; slab pitches run bottom, top, top ... per step. Not done: the 2:1 "upside-down stair" layering, hay thatch (savanna), stair gable dormers (dormer caps are slabs). Roofs can be read as an ASCII map in `RoofSmoothTest.roofMap`.
