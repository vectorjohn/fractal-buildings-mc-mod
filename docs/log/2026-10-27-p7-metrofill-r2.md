# 7.20 METROFILL R2: parallel roads yield

Plan step R2 of `docs/plans/metrofill.md`. Branch `claude/p7-metrofill-r2`.

## What changed

- `RoadNetwork`: `build` keeps the skeleton **main** roads in a direction-split raster (`Mains`: runs along x / along z; diagonal runs are in neither), and `parallelMain(axis, pos, from, to, reach)` answers "do at least half the 8-block nodes of this stretch have a main running the same way within `reach` across it". Crossing mains do not count. Pointwise (raster lookup), so tile-border agreement does not depend on the asking tile.
- **Lattice (on)**: `RoadNetwork.eligible` leaves out a plain border street with a parallel main within `LATTICE_YIELD` = 16 over at least half its length (city and metropolis). Connectivity holds (mains are live roots).
- **Grid lines (off)**: `StreetRules.gridSideEdgeOk` carries the same query, per block side, but `GRID_YIELD` = 0. At 24 it saved no sliver (28 -> 29 in the sweep) and made `DistrictsTest.invariantsHoldOverManySyntheticSites` fail (see Latent bug).
- **Arterials (off)**: `ARTERIAL_YIELD` = 0. At 16 the sliver count did not move and gate-joined-city got an `arterials-connected` warning. `arterial/main` slivers (995 columns) are 12-30 apart, outside the reach. Plan question A3 stays open.
- **Lanes (off)**: `Districts.runsBeside` (a frontage or quay lane running more than `LANE_PARALLEL` = 16 blocks within 12 of another street, axis-parallel, lanes within 16 of a tile border exempt) is implemented and wired into the lane callers, but ships at `LANE_PARALLEL` = 0: it saved 2 slivers and triggered the same latent bug.
- `FillStats.sliverPairs`, a `### Sliver pairs` table in `city-check` (the plan's "pair table": sliver columns by the kinds of the two roads either side) and `sliverPairs` in `--fill-json`.
- `GroundWalkTest.junctionStepsDoNotGetWorse`: exact ratchet 107 -> 110 tiles with a junction step over 1 (of 670); dropping streets beside mains moves a few junctions. Steps over 3 unchanged (11).
- District fixtures regenerated (mixed-city, synth-city; river-town unchanged).

## Latent bug found (not fixed here)

With grid yield 24 or lanes 16 on, `DistrictsTest` case 8 fails `tile-border-agree` between tiles dm11_1 and dm10_1 (z 182): a skeleton square (p0, 27x25) sits 5 blocks inside tile A's east border and hides main-road columns in A (`reserved`), while tile B, which does not know the square, draws the road. A street that used to occupy that spot kept the square out of it. Fix would be keeping squares out of the 8-block margin or not clipping roads by them there. Noted in NOTES.

## Numbers (9 gate windows, `city-check --scenes auto:0`; `docs/review/metrofill/fill-r2-before.md` vs `fill-r2.md`)

- road-sliver warnings (core zone) 28 -> 25; all slivers 44 -> 39; sliver area 15050 -> 11572 columns; `main/street` pair 6272 -> 2945 columns (the plan's target pair); empty gaps <= 12 % mean 10.6 -> 10.1.
- Core lot share (mean of the 9) 26.4 -> 25.8 %, core road 13.0 -> 12.8 %, frontage 50.4 -> 50.5 %: no lots lost (-0.6 point, shorter streets).
- road-hierarchy warnings 5 -> 6 (coast-city 27.6 -> 30.7 %): dropped streets shorten the denominator; report-only until F, R3 thins more.
- Sweep (slivers, core warnings): grid 16/24/30 alone 29; lattice 12: 29, 16..32: 26; arterial 16: +1 `arterials-connected`; lanes 26 -> 24.
- city-check 0 violations. Remaining pairs: arterial/lane 1857, lane/street 1675, main/street 2945, arterial/main 995 (R3, L1).

## Verification

- `RoadYieldTest`: Mains cases (beside, on, far, crossing main, half of nodes, zero reach off; literal 24) and, on the gate cities, no present plain street within 16 of a parallel main. Mutation: `LATTICE_YIELD` 0 -> the gate test fails.
- Tile-border agreement and determinism: `DistrictsTest`, `CityProgramTest` (per-side grid), `GroundWalkTest` and the CLI fixture tests pass; see the report for the exact runs.
- City gallery plan.png before/after (gate-metropolis-mixed, gate-coast-city): small differences only.

# Round 2 (architect VIOLATION DS-47, verifier INCOMPLETE)

The lattice yield at 16 dropped a border street with its lot rows and its branch streets; the 9-site means hid it. Round 2:

- New guard: `FillStats.districtLots` / `lotLosses` and `city-check --fill-baseline FILE` (a `--fill-json` of the baseline): the `## Lot-loss guard` table lists every district whose built lot count changed and flags a drop in density 50+ as LOSS (exit 1). `FillLotLossTest`. With the yield at 16 it exits 1 with 12 LOSS rows over coast-city, jungle-savanna and metropolis-mixed (`docs/review/metrofill/fill-r2-yield16.md`, `.json`).
- Cascade test in `RoadNetwork.build`: a yielding segment is dropped only if no other segment loses its link to the hub. It removed the emptied tile in coast-city (dm46_m22 28 -> 0, dm46_m21 15 -> 0 went away) but 12 districts still lost lots (the lot row that faced the street: d13_m13 30 -> 26, dm4_m25 22 -> 9). Re-rooting branch streets and re-fronting lots on the main road is a generator change (Districts), not built.
- Decision (the architect's fallback): `LATTICE_YIELD` = 0, byte-neutral (fill-r2.json equals the baseline, 0 districts changed, 0 losses; district fixtures and GroundWalk ratchet back to base values). `parallelMain`, `Mains`, the cascade test, the pair table and the guard stay for R3/L1. The earlier "no lots lost" claim was wrong: it was a 9-site mean.
- Sweep with the guard (all vs baseline): reach 6..15: nothing changes at all (0 districts); 16: 12 losses. The effect is a step at 16.
- The architect's two true duplicates (coast-city NE, metropolis-mixed NW) are only removed together with the harmful ones at 16; a real fix needs the hand-over (rules 1-3 in the review).
