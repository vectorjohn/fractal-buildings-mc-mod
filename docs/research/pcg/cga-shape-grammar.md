# CGA shape grammar ("Procedural Modeling of Buildings")

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Müller, Wonka, Haegler, Ulmer, Van Gool, "Procedural Modeling of Buildings",
SIGGRAPH 2006 ([PDF](https://peterwonka.net/Publications/pdfs/2006.SG.Mueller.ProceduralModelingOfBuildings.final.pdf),
DOI 10.1145/1141911.1141931). Extends split grammars (see
[split-grammars.md](split-grammars.md)); it is the language inside Esri
CityEngine (commercial).

## Approach

Rules `id: pred : cond ; successor : prob` rewrite shapes, each with an oriented
box **scope**, via translate/rotate/size, `I(obj)` inserts, `Subdiv` splits with
absolute vs relative (`1r`) sizes, `Repeat` (tile as many as fit, stretch to
fill) and `Comp` (split a volume into faces, edges or vertices). Buildings go
**mass model → façades/roof → details**: assemble volumes (L/H/U/T, roofs:
gable, hip, mansard, gambrel...), `Comp("sidefaces")` turns them into 2D façade
scopes, then split into floors, tiles, windows. Context comes from **occlusion
queries** (`Shape.occ("noparent") == "none"` → window, else wall), **sightline
queries** (`Shape.visible("street")` → put the entrance there) and **snap lines**
(floor planes of the dominant volume force aligned floors on the others);
rules carry **priorities** so derivation runs coarse to fine.

## Procedural vs templated

Rule-generated structure; small hand-made terminals (Pompeii: 190 rules over
36 terminal objects for a whole city; office mass-model variety from 4 rules).
Rules are stochastic (`: 0.5` branches, `rand(a,b)`), so two lots differ in
massing, wing sizes, entrance side and bay rhythm, but share the façade logic.

## How it hooks into worldgen

Not worldgen; a per-lot derivation (axiom = lot polygon).
- **Voxel fit:** good with care. `Repeat` + relative sizes adapt to any lot
  width, which suits whole-block grids, but stretching must round to integers
  (a 7-wide wall can't hold three 2-wide windows plus equal piers). Sloped roof
  faces and arbitrary rotations don't map to blocks; restrict to axis-aligned
  scopes, 90° rotations and stair/slab roof profiles.
- **Determinism:** fine if rule selection and `rand` draw from
  `hash(seed, lot, shape path, purpose)`; the paper's random source is unspecified.
- **Chunk independence:** occlusion/snap queries look only inside one building's
  shape tree (an octree), so a building is self-contained. Queries against
  *neighbouring* buildings (shared walls, the street) need the neighbours'
  massing from the deterministic city plan, not from generated chunks.
- **Cost:** ~1 s per 50 000-polygon building in 2006 C++; a Minecraft building
  is orders of magnitude smaller.

## Licence

Paper only; CityEngine/CGA implementation is proprietary (Esri). Ideas are free.
Patents: none found in a web search; Procedural Inc./Esri may hold patents on
later CityEngine features. Not checked in a patent database (flag for the human
if we copy CGA syntax closely).

## What to borrow

- **Mass model first, then `Comp` into façades**: our IR already wants volumes
  → walls → openings. Occlusion queries ("don't put a window where a wing
  meets") are exactly the invariant we need for L/T/U footprints.
- **Snap planes** for floor alignment across wings: in voxels, a shared floor
  height grid per building does the same job more simply.
- **Relative vs absolute sizes and `Repeat`** as the core façade operators.
- **Rule priorities** (massing < façade < roof < detail) as fixed derivation
  phases, which keeps derivation order deterministic.
- `visible("street")` → entrance faces the street: needs only the lot's street
  edge from the city plan.

## What to avoid

- Writing a general string grammar language and interpreter up front; the paper
  itself notes "carelessly written rules can be very contrived". A typed Java
  rule API over our IR is cheaper to test.
- No interiors and no connectivity guarantees: CGA models shells. Room layout
  and "every room reachable" need a separate stage.
- Non-axis-aligned geometry and smooth roofs.

## Verdict

Best-fit reference for building massing and façades; adopt its staged
mass → façade → detail pipeline and occlusion/snap ideas, not its language.

**Sources / what I couldn't verify:** read the full paper. Did not use
CityEngine. Patent status unverified. Integer-rounding behaviour is my
inference for voxels, not from the paper.

**Deep-dive (2026-10-15):** [grammars-deep-dive](grammars-deep-dive.md) covers rule semantics, voxel rounding, seeding, a typed Java API sketch and later work for this and its sibling technique.
