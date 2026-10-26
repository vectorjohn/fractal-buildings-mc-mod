## Result: done (mode: plan)

## Did
- Wrote `docs/plans/shoplow.md`: the 7.17 SHOPLOW plan for shops, houses and workshops (DS-45 as widened by John on 2026-10-26, with his "not all the time" refinement for houses). Added a plan link under the 7.17 checkbox in `docs/ROADMAP.md` (box not ticked) and 4 NOTES lines.
- Diagnosed the hill-town houses whose floor is 10-14 below the street (seed 1: `dm10_31.l0`, `dm9_29.l10`, `dm9_28.l19`). Ran `city --districts --scenes dm10_31,dm9_29,dm9_28` from this worktree and read the scene terrain and ground boxes. `frontY` is right. The streets stand on 12-14-block earth embankments: they start at a ridge junction (y 87) and come down at the grade limit, 1 block per 4, while the ground falls faster. The CLI already prints "needs more than 4 blocks of cut or fill". The doors are **not reachable from the street**: the fill ends in a vertical face at the lot line, and `entrance-reachable` only checks the path from the door to the lot's own ground. Plan step S3 leaves such lots empty (`empty:bank`). A streets item should fix the grading itself.
- Checked the ADR question. No amendment is needed. ADR 0003 W5 fixes the mechanism (box bottom at the slab, `beard_thin`, our own foundations, cut faces accepted) but not the floor level. "Slab at the highest ground" is a Phase 3 assumption (ASSUMPTIONS 2026-10-16, cheap to change). S2 appends a row that supersedes it.
- Checked these APIs and code paths, with file references:
  - `InstancePassV1.placeChecked`/`steps`/`rails`.
  - `Invariants`: the floorY convention; `BuildingCheck` passes `ground` to `StairGround`.
  - Raster order in `PieceRaster.Grid.of` and `VoxelGrid.of`: rooms first, then shell boxes, then site boxes, and air clears. So an AIR site box must never cover shell cells.
  - Viewer `terrainTops`.
  - `Pieces.deepen`.
  - `DistrictStructure`: the piece box bottom is the slab.
  - `Beardifier` 26.3, decompiled with `:mod:genSources`: non-pool pieces get groundLevelDelta 0; the `BEARD_THIN` kernel is `exp(-d²/16)`.
  - `ShopType`/`ShopMassing`/`Stilts` (MIN_LIFT 4).
  - `BuildingSpec`/`SpecHash`/`IrReader`/`ir.ts`: an optional key like `lead`.
  - `Briefs.frontY`/`streetMask`, `TypeLimits`, style JSONs (`paving` and `ground` roles exist).

## Commits
- 2f7dd8cd 7.17 SHOPLOW: plan (docs/plans/shoplow.md), NOTES on beard, site-box order, effective ground, hill-town embankments

## Verification
- `nix develop -c ./gradlew :mod:genSources` → sources jar used for the Beardifier reading.
- `:cli:installDist` and `fractal-cli city --seed 1 --terrain recorded:fixtures/terrain/fly-1-hill-town.json.gz --site -1536,4800,town,418 --districts --scenes dm10_31,dm9_29,dm9_28 --interiors` (scratch output only) → the embankment diagnosis above.
- heavy: none. flakes: none. No code changed, no tests run.

## Plan summary
- Standing level `s = min(s0, max(G+1, M-4))`. `G` is the street at the door (frontY when it is within 4 of the lot front, else the terrain there). `M` is the highest ground over the site (plan + 1-cell ring + door approach). `s0` is today's level.
  - Rise 1 when a cut of at most 4 allows it.
  - Shops keep rise <= 2 for `M-G <= 6` (S3 holds shop lots to that).
  - Houses keep rise <= 5 with a landing for `M-G <= 9`.
  - Flat and downhill lots are byte-identical to today.
- The cut is AIR site boxes plus a PAVING floor in the ring, approach and plan columns outside the footprint (shell cells skipped). It works as an areaway, so no window or door is ever below the ground outside it. Interior and shell are unchanged.
- One effective-ground rule (`core/terrain/SiteGround`) for the generator, the invariants and the viewer. It is a no-op for today's buildings, and S1 proves that with unchanged goldens.
- Houses: a per-lot hash picks the outcome: dig in (default), raised with landings on uphill lots (c), posts on downhill lots (b). Shops and workshops only dig in.
- Market halls:
  - Only with a new spec key `square` (plaza front or corner, relief <= 1; hashed only when true) and width >= 9.
  - Legs 3, or 4 from width 11.
  - No shopfront upstairs; a side stair instead of a front flight.
- New checks:
  - `door-rise`: shops and workshops -2..2, measured against the scene's street surface or the terrain in front.
  - `flight-landings`: at most 5 risers in a row.
  - A door-rise table in `check` and `city-check`.
- Steps:
  - S0 measure (report only)
  - S1 SiteGround
  - S1v viewer
  - S2 dig in, the largest step
  - S3 lot planner (shop front rise, embankment lots)
  - S4 market hall
  - S5a every buried cell is claimed (core raster)
  - S5b in-world, heavy, after ROADS1
  - S6 landings and raised houses
  - S7 downhill posts
  - S8 bank buildings (optional)
  - S9 measure and review
- Parallel: wave 1 is S0, S1, S1v, S3, S4. Wave 2 is S2 and S5a. Wave 3 is S6, S5b and S7. Wave 4 is S9.

## Not done / blockers
- none (plan only).

## Design questions
- Architect:
  - A1: house mix weights. Proposed: raised 20 % (40 % jungle), posts 25 % (50 % jungle and swamp), both only from a 4-block difference.
  - A2: workshops: shop limits or house limits?
  - A3: partial dig-in plus a landing instead of bank buildings for `M-G` 6-9.
  - A4: areaway: natural cut face, PAVING floor, no rail at the top edge.
  - A5: most risers between landings (5).
  - A6: market hall stair along the side or inside the open floor; share among eligible lots (50 %).
  - A7: level forecourt fill when the street is 1-4 above the lot front.
- Interior designer:
  - I1: market hall upper room use and furniture.
  - I2: bedrooms behind a 1-wide areaway?

## Notes for the coordinator
- **Deviation from the architect's spec:** the architect's point 3 (bank buildings: rise 1, back of storey 0 underground, back door upstairs) is deferred to optional S8. With the cap at 4 plus an areaway, shops never need it, and houses stay within DS-45 with a landing. Bank buildings would need terrain in the shell (a spec field plus facade, interior and massing changes). John's wish list included "back door on the upper floor": it is in S8, pending A3.
- **Point 5 (lot planner):** I chose to keep `SHOP_GRADE` and add a front-rise limit (6). Question J1 to John: loosen `SHOP_GRADE` now that shops can dig in? Assumed: keep it.
- **ROADS1:** it has uncommitted edits in `PieceRaster`, `StairGround` and `Spill`. The plan avoids `PieceRaster` (cuts are site boxes) and wraps the probe before `StairGround` sees it. S5b (heavy, in-world) waits for ROADS1's merge and touches `Spill` only if B2 shows a new spill kind.
- **7.16 vs S2/S3:** both regenerate settlement goldens and fixtures. Whichever merges second regenerates; never hand-merge.
- **New item to file:** street grading that leaves more than 4 blocks of fill or cut along built frontage (the hill-town embankments; pairs with polish backlog 6.3 junctions).
- The architect's report lives in `.claude/reports/` (outside git). The plan cites it; you may want it moved to `docs/review/reports/` as with earlier reviews.
- `docs/REVIEW.md` line 66 calls the highest-ground floor "the S5 rule". It was a Phase 3 assumption; the plan says so, and I did not edit REVIEW.

## Round 2 (coordinator: merge run-phase6, follow accepted DS-45)
- bdeeaa59 merge: ROADMAP keeps run-phase6's 7.17 text and its new 7.18, plus only the plan link under 7.17.
- Plan updated to the accepted DS-45 text:
  - houses and workshops on default lots fail `door-rise` above 5, and need a landing above 3;
  - variant lots skip the rise check and get `flight-landings` instead, with at most 3 straight risers (was 5); the checks re-derive the variant from the hash, so no IR marker;
  - workshops get house limits per DS-45 (A2 reworded);
  - S8 bank buildings is no longer optional: DS-45 names it and 7.18 BASEMENT depends on it (after S2 and A3);
  - A3 and A5 reworded;
  - the architect report link now points to docs/review/reports/shoplow-architect.md;
  - added 7.18 to the interactions section.
- `scripts/review-check.sh --source worktree` → exit 0.
