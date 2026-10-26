# metro-sparse architect answer (2026-10-26)

Mode: question. Round: first.
Looked at: `docs/review/phase6/flythrough/12345-metropolis-2-overview.jpg`; `docs/review/phase6/cityq/c12345-metro-before.jpg`, `c12345-metro-after.jpg`; plans I made on `claude/run-phase6` at 5145ff5d: `viewer/out/city/gate-metropolis-mixed/plan.png`, `d14_m12-overview.png`; `viewer/out/city/gate-metropolis-temperate/plan.png`, `plan-height.png`, `d15_m36-overview.png`. Plan JSON measured with `.claude/reports/metro-sparse-architect/fill-metrics.js` and `lot-depths.js` (node, `node fill-metrics.js <city out-dir>`). I opened every file listed here.

## Question

John (REVIEW 6.8) on 12345-metropolis-2-overview: "the roads are a little wonky. There are a lot of redundant roads, and empty space. If more space was filled _between_ the roads, that would look good, but it's too sparse." Is that still true on the current branch, what causes it, what do real dense cities do, and what should the coder change?

## Recommendation

**(1) The site in that frame is no longer a metropolis.** `city --seed 12345 --cell 0,5` now gives `s0_5 town r276`. It was a join claimant that lost its join and kept the metropolis tier at radius 276. CITYQ fixed that (`c12345-metro-after.jpg`: a town, no arterials). **But John's complaint holds for every real metropolis.** I planned the two real ones (`--districts`) and measured dry land inside the disk:

| | gate-metropolis-temperate (987654321, r1430, 3704 built lots) | gate-metropolis-mixed (987654321, r1126, the fly-through `s2_m2`, 1523 lots) |
|---|---|---|
| Core, r < 0.25 R: roads / lots / plazas / **empty** | 18.7 / 23.9 / 1.4 / **51 %** | 18.6 / 25.1 / 0.9 / **52 %** |
| r 0.25-0.5 | 16.0 / 27.7 / 1.0 / **53 %** | 14.0 / 28.6 / 0.2 / **51 %** |
| r 0.5-0.75 | 14.4 / 23.2 / 0.4 / 61 % | 10.4 / 20.9 / 0.3 / 67 % |
| Outer, r > 0.75 R | 4.0 / 8.3 / 0 / 86 % | 7.0 / 13.5 / 0 / 77 % |
| Street edge with a lot behind it (core, then by ring) | **43 %**, 50, 54, 55 % | **46 %**, 55, 56, 55 % |
| Same, by road kind | street 62, arterial 57, lane 43, **main (avenues) 32, quay 24 %** | street 64, arterial 55, lane 51, main 30, quay 23 % |
| Street length (centreline) | 135 km: arterial 49, main 15, street 54, lane 7, quay 10 | 59 km: arterial 21, main 7, street 28, lane 2, quay 2 |
| Arterial + main as share of street length | **47 %** | **47 %** |
| Street to street, gaps that hold lots (median) | 66 blocks | 68 |
| Street to street, gaps with no lot | 28 % of gaps; 10 % are empty slivers 12 wide or less | 26 %; 11 % |
| Who bounds those slivers (top pairs) | main+street, arterial+main, lane+main, lane+street, quay+street | main+street, arterial+main, lane+main |
| Dead-end street ends (no other street within reach) | 395 (street 177, quay 130, lane 74) | 184 (street 128) |
| Median lot depth (core): house / shop / high-rise | 9 / 17 / 23 | 10 / 17 / 23 |
| Whole-block (`block`) lots | 3 | 2 |

So: yes, it is still sparse and over-roaded. Half the land in the core is empty, and less than half of each core street edge has a building behind it. That is the opposite of a real core, where the street edge is the fullest place in the city. Plazas and steep (`empty:steep`) lots are minor (1-5 %). Street *spacing* is not the problem: about 66 blocks between streets is a good city block. The empty land is (a) the **hollow middle of every block** and (b) **slivers and triangles** left between roads that run side by side or cross at 45 degrees. The plans show this clearly. In `gate-metropolis-temperate/plan-height.png` every block is a ring of thin lots around a dark empty centre, with diagonal white avenues that carry no lots. In `d15_m36-overview.png`, a core tile, three high-rises and five houses stand around a 60-block grass field.

**(2) Causes, most important first.**

1. **Lots are as shallow as their type allows, not as deep as the block** (`Districts.placeAlong`/`dims`). The lot depth is the type's test-lot depth (the weighting favours the first candidates) plus 0-4 blocks. In a 66-deep block, two rows of 9-23-deep lots leave a 20-46-block strip in the middle. `MAX_DEPTH = 44` is a ceiling that is never used.
2. **Gaps along the frontage are added on purpose.** One lot in four is followed by a 2-4-block gap (`lot-gap`), and a failed try skips 4 blocks. That suits a village. In a core it breaks the street wall.
3. **Diagonal avenues get no frontage lots.** `placeAlong` only runs on axis-aligned runs; diagonals get only "cut lanes". So 15 km of avenue are 30 % built, and they cut the grid blocks into triangles and slivers (main+street is the top sliver pair). This is backlog line 5.7 D3 ("diagonal avenue frontage is empty"), still open.
4. **Too many road layers stacked on top of each other.** In the core every tile border (~160) is an 11-wide arterial (`Arterials.probability`: 1 at density 80). On top of that come grid lines every 62-100, 3-4 avenues, skeleton main roads (arterial+main slivers: a main road running beside an arterial), quays set back from the water with lanes to them, and ports. Arterial plus main is 47 % of all street length. Real cities have about 15-25 % (US urban functional classes: local streets about two-thirds or more of the length). Each extra layer adds roads without adding frontage.
5. **Quays are drawn as streets with nothing on their water side**, and lots seldom face them (24 %). Many end blind (130 ends). They are the "wonky" curves in the plan.
6. **Perimeter blocks almost never happen**: 5 `block` lots in two metropolises. The one type that fills a block whole is effectively off (backlog 5.7 "city blocks stay near absent").
7. Minor: houses make up a large share of r 0.25-0.5 (297 + 90). That is the DS-18 taper working, but detached 10x10 houses on 10-wide plots read as gaps from the air. `BuiltEdge` (DS-18) only caps the class, not the lot count, so it is **not** a cause of emptiness. Water: the temperate disk is about 32 % water on the coarse raster, which is real, and not counted above.

**(3) What real dense cities do** (metric, then × ~1.25 for player scale):

- Manhattan: blocks 60 × 240 m between streets, lots 30 m deep (half the block), 100 % street wall; streets 18 m, avenues 30 m every ~250 m. Barcelona's Eixample: 113 m blocks, 20 m streets, perimeter blocks with a courtyard, no gap in the frontage. Paris and older European cores: 80-150 m blocks, irregular.
- In dense cores, streets are about 25-35 % of land, built parcels 55-70 %, squares and parks 5-10 %, empty under 5 %. Inner residential areas: streets 20-25 %, parcels 50-60 %. Suburban edge: streets 12-18 %, parcels 30-45 %. Gardens sit *inside* the parcels, not left over between roads.
- Hierarchy: arterials every 0.8-1.6 km in US grids (half a mile to a mile), every 400-800 m in European cities. Collectors every ~400 m; local streets fill the rest. Diagonals (Washington, Barcelona's Diagonal, Buenos Aires) are few, and where they cross the grid the corner lots are cut to the diagonal so the frontage stays continuous.

**Targets for us (dry land in the disk, measured as in the table):**

| Zone | Roads | Built lots | Plazas/parks | Empty | Frontage built | Arterial spacing |
|---|---|---|---|---|---|---|
| Core (density ≥ 70, about r < 0.35 R) | 22-30 % | 55-65 % | 5-10 % | ≤ 10 % | ≥ 85 % | every 2nd tile border (~320) |
| Middle | 18-24 % | 40-55 % | ≤ 5 % | ≤ 25 % | ≥ 70 % | every 2nd-3rd border |
| Edge (outer quarter) | 10-16 % | 25-40 % | — | free (fields, wild) | ≥ 50 % on streets that exist | a few radials only |

Also: arterial + main ≤ 25 % of street length; empty slivers (≤ 12 between two roads with no lot) ≤ 2 % of gaps; dead ends in the core ≤ 1 per 10 000 m² of built-up land, except at water and the built edge.

**(4) Coder spec (in order of payoff; each step measurable alone):**

1. **Fill the block depth in dense areas.** For lots at density ≥ 50 (city and metropolis), set the lot depth to the free depth up to the block's centre line: half the street-to-street gap, capped at `MAX_DEPTH`. The building stays the type's size at the front, and the rear of the lot is a yard, a rear wing or a service court. Above density 70, an L or rear extension or a courtyard building should take the depth (ask the interior-designer and the IR owner whether a rear court is a site box or a wing). No hollow strip wider than 8 may be left between two back-to-back lot rows. The leftover middle becomes the rear lots' yards.
2. **No frontage gaps in the core.** `lot-gap` applies only below density 50 (fade it in from 50 down to 30). A lot that fails to fit widens its neighbour rather than leaving a hole, as `dims` already does for the last strip.
3. **Frontage on diagonals.** Lay lots along 45-degree runs with the front on the stepped edge, or, cheaper, cut the triangular corner lots of the grid blocks to the diagonal (a "flatiron" lot facing the avenue). Any triangle under ~16 deep becomes a plaza, green or pocket park (dressed later by PLAZAS), never bare grass.
4. **Thin the road layers.**
   - (a) Arterials every 2nd tile border in the core, fewer outside. Plain border streets keep the lattice.
   - (b) Drop a skeleton main road or lattice street that runs parallel to another road within 16 blocks (lot depth) with no lot between them. Keep the higher-class road.
   - (c) A quay only where lots face it or it is the waterfront promenade of a waterfront quarter. Otherwise the lots on the street behind back onto the water.
   - (d) Ports and lanes only where they serve lots.
5. **Make perimeter blocks real.** Allow `block` lots (from density 70) on any closed block 40 to 100 across, not only where all four sides are border or grid streets. Also make the class table pick them often in the core, up to about a third of core blocks, mixed with frontage rows.
6. Keep DS-18: the outer ring stays loose. But loose means houses with gardens inside their lots and streets that end at fields. It does not mean streets with nothing on them.

**Measure in `city-check`:** add a `fill` section to the report (per settlement: the table above by ring, from the plan alone, no scenes). Start with report-only numbers. Make them checks once the targets are met on the 9 gate windows:

- `core-fill`: empty ≤ 15 % and frontage ≥ 80 % for density ≥ 70.
- `road-sliver`: no road-bounded empty strip ≤ 12 wide and ≥ 32 long.
- `road-hierarchy`: arterial + main ≤ 30 % of street length in a city or metropolis.

The scripts beside this report are a working prototype (rasterises roads, lots and plazas; rings; gap scan; frontage; dead ends).

## Reasons

A city reads from the air (John's camera) by the ratio of roof to ground. The street wall is what makes a street feel urban at player height. Thin lots ringing empty blocks fail both reads. Extra parallel and diagonal roads add paving without adding buildings, so the city looks both over-roaded and empty, which is exactly what John said. The block size is already right, so the fix belongs in lot packing and road selection, not in the lattice.

## Alternative

Shrink the grid pitch (e.g. 40-60) so shallow lots meet back to back. That is cheaper, but it makes the over-roading worse (roads go from about 19 % to 30 %+ of land, mostly 5-wide local streets) and doubles street grading and junction work. It is better only for an old-town quarter, where small blocks are the character (IRR1 already does that). Second alternative: rear service lanes (alleys) down the middle of deep blocks, as in Chicago. Good for a later "grid quarter" variant, but they add roads, so do it after step 1.

## Rule?

Yes. This is the second time it has come up (backlog 5.7 D3: empty avenue frontage, city blocks near absent; now REVIEW 6.8). Proposed **DS-n "Dense cores are filled"**:

- **Rule:** in city and metropolis areas of density 70 and up, buildings line at least 80 % of every street edge, block interiors are filled by lots (yards, rear wings, courtyards) rather than left over, and no two roads run within one lot depth (16) of each other with nothing between. Outer areas taper (DS-18) but keep gardens inside lots.
- **Scope:** city, metropolis.
- **Enforcement:** code, `core-fill`, `road-sliver`, and `road-hierarchy` in `city-check`.
- Wording and numbers are mine (agent's numbers) and need John's acceptance.
