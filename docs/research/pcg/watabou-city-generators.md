# Watabou's Medieval Fantasy City Generator (and Village / Neighbourhood)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Browser map generators by watabou: [Medieval Fantasy City Generator](https://watabou.itch.io/medieval-fantasy-city-generator)
(MFCG; an older version's Haxe source is [TownGeneratorOS](https://github.com/watabou/TownGeneratorOS),
~4.4k lines, last push 2021), plus closed-source siblings
([Village Generator](https://watabou.itch.io/village-generator), Neighbourhood,
Toy Town). 2D plans of plausible pre-modern towns; widely used for tabletop maps.

## Approach

Read in source (`building/Model.hx`, `wards/*.hx`):
1. **Patches:** ~8·N points on a jittered spiral → Voronoi; the inner cells are
   Lloyd-relaxed so the centre is regular; the N cells nearest the centre are
   the city. Short edges (< 8 units) are collapsed to clean junctions.
2. **Walls, gates, citadel:** a curtain wall around the inner patches, gates at
   some wall vertices; a citadel patch must be compact or the whole build throws
   and **retries** with the next RNG state.
3. **Streets:** A* from each gate to the plaza/centre over the graph of patch
   *edges* (wall vertices blocked); outside roads likewise. Streets therefore
   run along ward borders, never through them.
4. **Wards:** a fixed, lightly shuffled list (mostly Craftsmen, plus Merchant,
   Cathedral, Market, Slum, Patriciate, Military, Park...). Each ward type has a
   `rateLocation` score (Merchant: near the plaza; others random) and claims
   the best free patch. Outside the wall: farms and sparse buildings.
5. **Buildings:** each ward's patch is inset by street width (main 2, regular
   1, alley 0.6) to a block, then **recursively bisected** across its longest
   edge (ratio and angle jittered by `gridChaos`, stop at `minSq`·random size
   factor, small chance of an empty lot). Every ward type is just different
   parameters (`minSq`, `gridChaos`, `sizeChaos`, `emptyProb`). Outskirt lots
   are thinned by distance to roads.

Village Generator (devlog only): roads "based on Parish–Müller's approach",
a density field, a distorted circle as the square at the densest point.
Neighbourhood: one landmark building per district (some none, spaced apart)
"to break the monotony of regular small buildings".

## Procedural vs templated

Fully rule-generated, no templates. Varies: patch shapes, ward assignment,
walls/plaza/citadel on/off, lot sizes. Constant: the radial, centre-heavy
medieval structure (one plaza, gates → centre).

## How it hooks into worldgen

Not worldgen; a whole-city 2D plan from (size, seed).
- **Fits our region cell:** run once per settlement cell, cache, as for
  [Parish–Müller](parish-muller-cities.md). The plan is order-dependent (global
  RNG, wards claim patches in sequence), so it must never run per chunk.
- **Determinism caveats in the code:** one global LCG (`Random.hx`) consumed
  across all stages, so any change upstream reshuffles everything downstream;
  retry-on-exception loops; `Map<Point,…>` iteration in `Topology`. Our port
  would use per-stage hashed sub-seeds and sorted collections.
- **Voxel fit:** good at block scale: polygons rasterise to lots; bisection
  with axis-aligned cuts (its `createOrthoBuilding`) suits Minecraft better
  than free angles.

## Licence

TownGeneratorOS: **GPL-3.0** ([LICENSE](https://github.com/watabou/TownGeneratorOS/blob/master/LICENSE)).
Study only; don't copy code into the mod unless the mod is GPL-compatible.
Later generators: closed source, no licence. Ideas are free.

## What to borrow

- **Ward = parameter set** over one lot-subdivision routine: cheap
  variety-with-coherence (district character from 4 numbers). Maps to our
  district → style/palette selection.
- **Streets on cell borders**, lots by insetting cells by street width: lots
  can't overlap streets by construction (helps "stays in lot").
- **Location scores per district type** (merchant near the plaza, slums at the
  edge, gate wards at gates) with a fixed priority list.
- **Landmarks:** one large, distinct building per district, spaced apart.

## What to avoid

- A single global RNG stream and retry loops; derive sub-seeds per stage.
- Free-angle cuts and irregular polygons when lots become Minecraft buildings:
  snap to axes or accept stair-stepped walls.
- Only one city archetype (radial walled town); Minecraft terrain and biomes
  need others (linear villages, hill towns).

## Verdict

Strong, small reference for district-typed city plans (Voronoi wards, border
streets, recursive lot bisection); port the ideas per region cell, not the
GPL code.

**Sources / what I couldn't verify:** read Model.hx, Ward.hx, CommonWard and
ward subclasses, Topology.hx, Random.hx in the repo; Village devlog and a
ProFantasy forum post on Neighbourhood. Didn't run it. Current MFCG and
Village/Toy Town algorithms are unpublished (Patreon posts not read).
