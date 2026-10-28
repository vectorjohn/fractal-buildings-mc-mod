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
