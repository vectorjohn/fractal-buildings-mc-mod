# Notes: building roads

Build guidance for [DS-30](../STANDARDS.md) (one road material) and
[DS-31](../STANDARDS.md) (smooth roads, kerbed city streets). Not rules:
the implementer may do it another way if the rules' checks still hold.
Drafted by the architect, 2026-10-25; materials per tier and biome are in
[styles/README.md](../styles/README.md), "Road surfaces".

# Half steps

- Simplest: keep the integer height profile and put a bottom slab on every
  cell of the cross-section just before each 1-block rise (h, h, h+0.5,
  h+1). The grade limits (streets 1 in 3, `Grading.GRADE_RUN`; roads
  between settlements 1 in 4, `Profile.MAX_STEP`, as of 2026-10-25) keep
  this within slabs, so carriageways never need stairs.
- Stairs (the road material's, facing up the slope) only on footpaths and
  steps steeper than 1 in 2.
- Junctions are level; the lesser road eases to them in half steps.
- Bottom slabs are never waterlogged, and sit on solid blocks (gravel and
  sand fall).

# Kerbs

- Keep the carriageway exactly 0.5 below the sidewalk beside it: a bottom
  slab where the sidewalk is a full block, a full block where the sidewalk
  is a slab, so the kerb holds on slopes. A 0.5 kerb is walkable, so no
  ramps.
- Two kerbed streets: the junction is carriageway, the sidewalks turn the
  corner. A lane, path or plaza meets the street at sidewalk level, across
  the sidewalk; the kerb is the step down.
- Doors and stoops (DS-4) land on the sidewalk, never the carriageway.
- Bridges carry carriageway, sidewalks and rails (DS-8), the carriageway in
  the road's own material in cities (DS-44); a deck that cannot
  ends the kerb at the abutment, with the carriageway raised to sidewalk
  level.
