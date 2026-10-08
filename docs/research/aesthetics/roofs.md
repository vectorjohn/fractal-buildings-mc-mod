# Roofs: pitch, shapes, overhangs

**Track:** 6 Aesthetics · **Kind:** technique (builder practice) · **Date:** 2026-10-08

## What it is

How roofs are shaped and built from stairs, slabs and full blocks so they
read right at Minecraft's 1 m resolution. Sources read:

- Minecraft Wiki, [Tutorial:Roof construction guidelines](https://minecraft.wiki/w/Tutorial:Roof_construction_guidelines)
  and [Tutorial:Roof types](https://minecraft.wiki/w/Tutorial:Roof_types) (the
  most concrete source found; numbers below come from them unless noted).
- WesterosCraft, [Exteriors](https://westeroscraft.com/docs/building/fundamentals/exteriors)
  (eaves, thatch pitch, roof material by class).
- vibecraft `building-structures/styles.md` ([repo](https://github.com/amenti-labs/vibecraft)).
- Vanilla village roofs, dumped layer by layer from the structure files
  (see `vanilla-villages.md`).

## Approach in three sentences

Minecraft offers three clean pitches: 2:1 (63°, stacked full blocks), 1:1
(45°, one stair per step) and 1:2 (≈27°, alternating bottom/top slabs or
stair + slab); mixing them gives gambrel and mansard profiles. The roof is
chosen from footprint size and use (gable, hip, half-hip, saltbox, shed,
flat with parapet, gambrel, mansard, helm, monitor), extended past the wall
by an eave, and large footprints are split into several roof sections that
meet along valleys. Material and weathering follow the wall palette (ridge
lighter, valleys and eaves darker).

## Procedural vs templated

Procedural and well studied: roofs are a function of the footprint polygon
(straight skeleton / hip-roof algorithms in the PCG literature, see track 3),
the pitch and the eave depth. Stair shapes (`inner_left`, `outer_right`, …)
at hips and valleys need a corner-resolution rule, which vanilla gets from
stair auto-shaping when blocks update.

## How it hooks into worldgen

n/a: design rules. The roof height adds to the building's vertical extent,
and eaves add 1 block outside the wall footprint: the lot planner must
reserve that margin, and neighbours' eaves must not intersect.

## Licence

Wiki text CC BY-NC-SA 3.0 (ideas only); WesterosCraft: none stated; vibecraft
MIT; vanilla data: Mojang, studied only.

## What to borrow (encodable rules)

- **Pitch set.** pitch ∈ {2:1 full blocks, 1:1 stairs, 1:2 slab/stair
  alternation}; style picks a default (thatch and snowy styles ≥ 1:1,
  WesterosCraft says thatch needs ≥ 45°; desert flat; East-Asian 1:2 with
  wide eaves, vibecraft says 20–30° and 2–3 block overhang).
- **Eave.** Overhang 1 block on all eaves and gable ends by default (vanilla
  plains, savanna, taiga do exactly this); 2–3 for styles with deep eaves.
  The eave course is an upside-down-stair or slab edge, so the underside
  isn't a flat block face.
- **Roof height.** Roof rise between 1 and 2 storeys (wiki). With 1:1, rise
  = ceil(span / 2), so spans > ~12 blocks need a different roof form.
- **Span limits.** Simple gable: span ≤ 12 (wiki). Footprints > ~15×15 get
  multiple sections (cross-gables, wings, a hip + gable mix) with rise per
  section ≈ 5–7 blocks. Mansard/gambrel only on large footprints (wiki
  example 16×20).
- **Odd widths.** Prefer an odd building width under a gable or helm so the
  ridge is a single centred block (wiki); for even widths use a slab or
  double-stair ridge.
- **Ridge and gable ends.** Ridge capped with slabs/full blocks of the roof
  or trim material; gable-end walls filled with the frame/infill pattern of
  the wall below (not left open).
- **Flat roofs.** Always a border of a different block: slab or wall
  parapet (wiki); light it or block it so mobs can't spawn.
- **Hips and valleys.** Stairs face only along the two axes; corners use
  `inner`/`outer` stair shapes. Remove blocks inside the "V" where two
  roof sections join (wiki).
- **Interior.** Roof blocks should not be visible as the only ceiling layer
  from inside where a style has rafters (WesterosCraft).

## What to avoid

- Gables over spans so wide the roof becomes a tall barn; flat roofs with
  no border; roofs flush with the wall (no eave) except where the style
  calls for it (desert flat, snowy compact).
- Mixed stair orientations on one slope; roof material identical to the
  wall material.

## Checks we could test

- Closed roof (already a planned invariant): every interior column has a roof
  cell above it.
- Eave ≥ 1 for pitched styles; eave cells within the lot.
- Rise / span consistent with the declared pitch; rise ≤ 2 storeys per
  section; no single gable section with span > 12.
- Stair orientation consistency per slope; valid corner shapes at hips.

## Verdict

Adopt. Roof generation is a footprint-to-roof function with a small pitch
set, an eave parameter and a section splitter for large footprints. Track 3
(straight skeleton / CGA roof rules) supplies the algorithm.

## Could not verify

The Shockbyte and ExitLag roof guides returned 403 or were not fetched; the
YouTube roof tutorials were not watched. Whether vanilla stair corner shapes
resolve correctly when placed by worldgen without block updates is a
Phase 2 / mod-side question, not checked.
