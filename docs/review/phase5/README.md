# Phase 5 gate pack

What John looks at for the Phase 5 gate: settlements and cities planned outside Minecraft on
recorded terrain, from a hamlet to an 8-cell metropolis. Built 2026-10-21 from `claude/run-phase5`
at `2e67271` (all Phase 5 items merged; TIDY `75f8171` merged in afterwards, it changes no plan).
The gate (ROADMAP Phase 5): **cities in at least five biome mixes, one straddling a border, one
larger than 512 x 512, one with a high-rise downtown.** The questions for each city: does the
biome mix read right, does the border blend, does the downtown read as a downtown, **would a
person have built this town here?** All the questions are collected at the end and in
[REVIEW.md](../../REVIEW.md) under "Phase 5 gate".

# Contents

- `cities/<preset>/`: per city, the plan view (`plan.jpg`, the whole settlement; `plan-centre.jpg`
  for big cities whose overview is too small to draw lots), a zoomed district with its lots
  (`plan-district.jpg`), the skyline colours (`plan-height.jpg`, lots by top storey: pale yellow
  1, orange 5 to 10, red 20 to 30, violet 60), and 2 to 5 fly-through frames of the featured
  district (`fly-N.jpg`: two aerials, a street-level view along the street most lots front onto,
  the square or green, and a low skyline view for big cities). 86 JPEGs, 7.7 MB.
- [city-check.md](city-check.md): `city-check` with no rule ignored over the 9 gate windows,
  200 synthetic sites and both world maps: **0 violations**.
- [scale.md](scale.md): the scale check (5.9), re-measured by TIDY after TR, ROADS and D3.
- `region/`: region maps before and after the water bias (5.10).
- Earlier per-item pictures, still current for their question: `d3/` (skyline, avenues, walled
  city), `irregular/` (old town), `terrain/` (quays, bridges, steep town), `roads/`, `hub/`,
  `skeleton/`, `shopland/`, `circulation/`, `jetties/`, `plains-palette/`.

**How to read the pictures.** Plan views: yellow roads are arterials, white main roads and
avenues, grey streets and lanes, blue quays; lots are filled by type (orange house, light blue
shop, purple workshop, dark blue high-rise, pink hall, pale yellow temple, red city block) and
labelled squares are landmarks. The overview of a big city draws each district tile by density
instead of lots (below 0.5 px per block). The 3D frames colour each building in its own style's palette (jungle moss roofs, desert sandstone, plains red tiles, ...), and the ground (terrain and roads) takes the tint of the nearest building's style: green for plains and jungle (darker), sand for desert, white for snowy, dark green for taiga. Scenes carry no biome, so the terrain tint is a stand-in, not the real biome edge; the plan view and the counts below give the real mix. (Re-captured after the viewer fix `p5-viewer-palette`; earlier frames painted a whole district in the first building's palette.)

**Interactive.** `cd viewer && nix develop -c npm run city -- <preset>` (or no names for all),
then `nix develop -c npm run dev` and open
`http://localhost:5173/plan.html?city=/@fs/<repo>/viewer/out/city/<preset>/&terrain=/@fs/<repo>/viewer/out/city/<preset>/terrain.json.gz`
(drag, wheel, click a district for its 3D link; `&layers=%2Bheight,-terrain` for the skyline).
`npm run city-gallery` captures the standard plan and two district frames per preset
(docs/VIEWER.md, "City gallery"). **Regenerating this pack:**
`npm run city -- <names>`, `node scripts/gate-pack-scenes.mjs` (adds each featured district's
scene, list in `viewer/e2e/gate-pack-featured.json`), then
`npx vite build && FB_PORT=<port> GATE_PACK=1 npx playwright test e2e/gate-pack.spec.ts`
(about 5 minutes; PNGs in `viewer/out/gate-pack/<preset>/`; the committed JPEGs are a
re-encoded subset: `node scripts/png-to-jpg.mjs src.png dst.jpg ...`, quality 0.82; `FB_CITIES=a,b` limits the capture).

# The cities

Built lots by type for the whole settlement (empty lots left out: 0 to 36 per city, steep or no
fit). Every gate city has a hub square with one hall and one temple (metropolises add halls on
squares at arterial crossings). Agent notes are what the agent saw, not a verdict.

| City | Tier, radius | Cells | Districts | Lots | House | Shop | Workshop | Tower | Hall | Temple | Block | High-rise | Wall | Top storeys | Styles (lots) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| temperate | city 477 | 1 | 33 | 208 | 70 | 79 | 17 | 2 | 1 | 1 | 0 | 38 | 0 | 22 | taiga 143, plains 50, plain 15 |
| desert / badlands | city 827 | 1 | 64 | 457 | 165 | 231 | 32 | 0 | 1 | 1 | 0 | 27 | 0 | 22 | desert 411, plains 44, plain 2 |
| snow | city 478 | 1 | 29 | 103 | 56 | 32 | 6 | 3 | 1 | 1 | 0 | 4 | 0 | 17 | snowy 98, taiga 5 |
| jungle / savanna | city 790 | 1 | 48 | 368 | 129 | 169 | 24 | 2 | 1 | 1 | 0 | 42 | 0 | 22 | desert 198, jungle 121, plains 28, taiga 20, snowy 1 |
| border desert / jungle | city 577 | 1 | 45 | 336 | 114 | 166 | 26 | 3 | 1 | 1 | 0 | 25 | 0 | 22 | jungle 230, desert 102, plains 3, plain 1 |
| coast (> 512², water) | city 740 | 2 | 56 | 388 | 159 | 147 | 25 | 5 | 1 | 1 | 0 | 50 | 0 | 22 | plains 354, plain 20, taiga 13, desert 1 |
| metropolis temperate (downtown) | metropolis 1430 | 8 | 220 | 2355 | 683 | 882 | 171 | 18 | 7 | 1 | 2 | 591 | 0 | 60 | plains 1205, jungle 817, desert 194, taiga 102, plain 37 |
| metropolis mixed | metropolis 1126 | 4 | 114 | 1006 | 323 | 334 | 80 | 9 | 1 | 1 | 1 | 257 | 0 | 60 | desert 431, jungle 312, plains 263 |
| joined (2 cells) | city 717 | 2 | 61 | 514 | 208 | 186 | 49 | 18 | 2 | 1 | 0 | 50 | 0 | 22 | plains 421, plain 90, taiga 3 |
| hamlet | hamlet 60 | 1 | 2 | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 3 | plains 3 |
| village | village 180 | 1 | 7 | 10 | 9 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 3 | plains 10 |
| walled town | town 300 | 1 | 17 | 199 | 118 | 17 | 28 | 10 | 1 | 0 | 0 | 0 | 25 | 8 | plains 199 |

The first nine are the T2 gate windows (`fixtures/terrain/gate-sites.tsv`, presets `gate-*`);
the hamlet and village are `hamlet-mixed`, `village-mixed` (seed 1, the T1 mixed window), the
town `town-walled` (seed 2, coarse world dump; the gate windows have no walled town).

## 1. Temperate city (`gate-temperate-city`, seed 987654321)

[plan](cities/gate-temperate-city/plan.jpg), [district](cities/gate-temperate-city/plan-district.jpg),
[height](cities/gate-temperate-city/plan-height.jpg); featured district d7_m60 (28 lots: 20
shops, 8 high-rises, all taiga): [aerial](cities/gate-temperate-city/fly-1.jpg),
[aerial 2](cities/gate-temperate-city/fly-2.jpg), [street](cities/gate-temperate-city/fly-3.jpg),
[green](cities/gate-temperate-city/fly-4.jpg), [skyline](cities/gate-temperate-city/fly-5.jpg).
Seen: a compact grid core west of the diagonal avenue, a ring of mid-rise and 20-storey
towers round a hill that stays empty; the avenue runs through a cutting with no lots on it; the
green is a raised platform. Most lots are taiga (dark spruce) although the window is called
temperate: the per-lot style vote finds taiga biomes under most of the city.

## 2. Desert and badlands (`gate-desert-badlands`)

[plan (density overview)](cities/gate-desert-badlands/plan.jpg),
[centre with lots](cities/gate-desert-badlands/plan-centre.jpg),
[district](cities/gate-desert-badlands/plan-district.jpg); featured hub district d41_m43 (23
lots: 21 shops, hall, temple, all desert): [aerial](cities/gate-desert-badlands/fly-1.jpg),
[aerial 2](cities/gate-desert-badlands/fly-2.jpg), [street](cities/gate-desert-badlands/fly-3.jpg),
[square](cities/gate-desert-badlands/fly-4.jpg), [skyline](cities/gate-desert-badlands/fly-5.jpg).
Seen: flat-roofed sandstone boxes round a lake in the hub tile, terraced streets on the slope;
the walls are heavily speckled (sandstone with orange and yellow terracotta); the most rugged
window (urban 73 %, 29 lots empty for steepness). Biggest single-cell city (827).

## 3. Snow (`gate-snow-city`)

[plan](cities/gate-snow-city/plan.jpg), [district](cities/gate-snow-city/plan-district.jpg),
[height](cities/gate-snow-city/plan-height.jpg); featured district d26_1 (28 lots: 14 shops,
11 houses, snowy): [aerial](cities/gate-snow-city/fly-1.jpg),
[aerial 2](cities/gate-snow-city/fly-2.jpg), [street](cities/gate-snow-city/fly-3.jpg),
[skyline](cities/gate-snow-city/fly-4.jpg).
Seen: the thinnest city (103 lots): a ribbon along the winding main road between lakes, with
arterial stubs that carry no lots (ROADS: arterial blocks with no main-road link stay empty,
125 -> 98 then 103 lots); the featured block is a dense little grid of steep snowy roofs by a
lake. Does this read as a city, or as a town strung along a road?

## 4. Jungle and savanna (`gate-jungle-savanna`)

[plan](cities/gate-jungle-savanna/plan.jpg), [district](cities/gate-jungle-savanna/plan-district.jpg),
[height](cities/gate-jungle-savanna/plan-height.jpg); featured hub district dm5_m24 (19 lots: hall,
temple, 17 shops; jungle 14, plains 5): [aerial](cities/gate-jungle-savanna/fly-1.jpg),
[aerial 2](cities/gate-jungle-savanna/fly-2.jpg), [street](cities/gate-jungle-savanna/fly-3.jpg),
[square](cities/gate-jungle-savanna/fly-4.jpg), [skyline](cities/gate-jungle-savanna/fly-5.jpg).
Seen: a city on the savanna (yellow) side of a jungle; **savanna maps to the desert style**
(`styles/biome-fallback.json`), so desert is the largest style here (198 lots) beside jungle
(121). The 3D frames are drawn in plains colours (the first building's style, see above).

## 5. Border straddler, desert and jungle (`gate-border-desert-jungle`)

[plan](cities/gate-border-desert-jungle/plan.jpg),
[district](cities/gate-border-desert-jungle/plan-district.jpg),
[height](cities/gate-border-desert-jungle/plan-height.jpg); featured hub district d49_m49 (20
lots, all jungle): [aerial](cities/gate-border-desert-jungle/fly-1.jpg),
[street](cities/gate-border-desert-jungle/fly-3.jpg), [square](cities/gate-border-desert-jungle/fly-4.jpg),
[skyline](cities/gate-border-desert-jungle/fly-5.jpg).
Seen: jungle 230 and desert 102 lots; the blend is per lot (a hashed vote over 48 blocks, ST),
so across the border lots of the two styles mix for a few tiles. The plan outlines lots by style
but the difference is hard to see at overview scale; the 3D view now draws each building in its own style
(re-captured after p5-viewer-palette). Green bamboo roofs on the jungle houses read as jungle.

## 6. Larger than 512², on water (`gate-coast-city`, seed 12345)

[plan](cities/gate-coast-city/plan.jpg), [district](cities/gate-coast-city/plan-district.jpg),
[height](cities/gate-coast-city/plan-height.jpg); featured district dm47_m24 next to the hub (26
lots: 14 shops, 10 high-rises, hall, temple): [aerial](cities/gate-coast-city/fly-1.jpg),
[aerial 2](cities/gate-coast-city/fly-2.jpg), [street](cities/gate-coast-city/fly-3.jpg),
[skyline](cities/gate-coast-city/fly-5.jpg).
Seen: 1536 x 1344 blocks over two cells on a lake-and-coast window; the core sits on a neck of
land between two bodies of water, with quays (blue) along the shore and plank bridges; a
cluster of 22-storey towers by the hub (a city, not a metropolis, still gets 20-storey
mid-rise at density 85); main roads wander out along the shore.

## 7. High-rise downtown (`gate-metropolis-temperate`, 8 cells)

[plan](cities/gate-metropolis-temperate/plan.jpg),
[centre](cities/gate-metropolis-temperate/plan-centre.jpg),
[height](cities/gate-metropolis-temperate/plan-height.jpg),
[district](cities/gate-metropolis-temperate/plan-district.jpg); featured downtown district
d16_m36 (37 lots: 27 high-rises up to 60 storeys, 10 shops):
[aerial](cities/gate-metropolis-temperate/fly-1.jpg), [aerial 2](cities/gate-metropolis-temperate/fly-2.jpg),
[street](cities/gate-metropolis-temperate/fly-3.jpg), [square](cities/gate-metropolis-temperate/fly-4.jpg),
[skyline](cities/gate-metropolis-temperate/fly-5.jpg).
Seen: the largest plan (3264 x 3264, 2355 lots): violet 60-storey core round the hub, red
and orange outward, diagonal avenues, 21 squares and 7 halls (squares at arterial crossings). The
downtown district is a solid block of 60-storey towers of nearly one height (the plateau
D3 noted), with street canyons; only 2 city blocks in the whole metropolis (shops take the
core's low lots). Also see `d3/` for the flat synthetic metropolis and the transects.

## 8. Joined multi-cell city (`gate-joined-city`, 2 cells)

[plan (density overview)](cities/gate-joined-city/plan.jpg), [centre](cities/gate-joined-city/plan-centre.jpg),
[district](cities/gate-joined-city/plan-district.jpg); featured district d55_m1 (34 lots: 21
shops, 8 houses, hall): [aerial](cities/gate-joined-city/fly-1.jpg),
[aerial 2](cities/gate-joined-city/fly-2.jpg), [street](cities/gate-joined-city/fly-3.jpg),
[square](cities/gate-joined-city/fly-4.jpg), [skyline](cities/gate-joined-city/fly-5.jpg).
Seen: two cells planned separately meet at hashed gates and read as one city (no seam stood
out to the agent in the plan); the featured district has a square with a hall, terraced houses on a
lake shore, two mid-rise towers. `gate-metropolis-mixed` (4 cells, [plan](cities/gate-metropolis-mixed/plan.jpg),
[height](cities/gate-metropolis-mixed/plan-height.jpg), [centre](cities/gate-metropolis-mixed/plan-centre.jpg),
[district](cities/gate-metropolis-mixed/plan-district.jpg),
[aerial](cities/gate-metropolis-mixed/fly-1.jpg), [aerial 2](cities/gate-metropolis-mixed/fly-2.jpg),
[street](cities/gate-metropolis-mixed/fly-3.jpg), [skyline](cities/gate-metropolis-mixed/fly-5.jpg))
is the other joined case: a lake in the middle, the core on the land round it, desert 431 /
jungle 312 / plains 263 lots.

## 9. Hamlet, village, walled town

- **Hamlet** (`hamlet-mixed`): [plan](cities/hamlet-mixed/plan.jpg),
  [district](cities/hamlet-mixed/plan-district.jpg), [aerial](cities/hamlet-mixed/fly-1.jpg),
  [aerial 2](cities/hamlet-mixed/fly-2.jpg). Three houses on a short track by a river, no
  square. Seen: reads as a farmstead group; very small.
- **Village** (`village-mixed`): [plan](cities/village-mixed/plan.jpg),
  [district](cities/village-mixed/plan-district.jpg), [aerial](cities/village-mixed/fly-1.jpg),
  [aerial 2](cities/village-mixed/fly-2.jpg), [street](cities/village-mixed/fly-3.jpg),
  [green](cities/village-mixed/fly-4.jpg). Nine houses and a hall on one straight street
  ending at a river, a paved green beside the hall. Seen: tidy but rigid, one street only (no
  cross street on this seed); the "villages too gridded" question.
- **Walled town** (`town-walled`): [plan](cities/town-walled/plan.jpg),
  [district](cities/town-walled/plan-district.jpg), [aerial](cities/town-walled/fly-1.jpg),
  [aerial 2](cities/town-walled/fly-2.jpg), [street to the wall](cities/town-walled/fly-3.jpg),
  [market](cities/town-walled/fly-4.jpg). A rectangular ring (25 wall lots, 10 tower lots)
  with gates where the three radial main roads cross, market square and hall inside, houses
  along the roads and outside the wall. Seen: the street view ends at a crenellated wall;
  every wall lot is a full gatehouse-style building (the "walls are only gatehouses" question).

# Region maps: water bias (5.10)

Same window, before and after R2's distance-to-water term: seed 987654321
[before](region/before-987654321.png) / [after](region/after-987654321.png), seed 12345
[before](region/before-12345.png) / [after](region/after-12345.png) /
[after with water heat](region/after-heat-12345.png). Cities and metropolises (red and violet
disks) shift towards coasts and lakes (31 % -> 46 % of big sites within 64 blocks of water;
count +9 %). Made at R2's merge (`39b0526`); since then only MC2's bounded caches touched the region code
and the committed region dumps (`viewer/fixtures/region/`) are unchanged, so the maps should still hold (not re-rendered). John approved this on 2026-10-21 (REVIEW "5.10 R2").

# Numbers

- **City checks** ([city-check.md](city-check.md)): no rule ignored; 9 gate windows (18 scenes),
  200 synthetic sites and 302 settled cells of the two world maps: 0 failing, 0 violations.
- **Scale** ([scale.md](scale.md), 5.9, still unticked): per-district plan time is flat with city
  size (median 5.5 to 7.1 ms per district from the 477-radius city to the 8-cell metropolis on
  the gate windows), skeleton 2 to 11 ms per cell, a 4096² city plans in a 64 MiB heap. **Not
  met:** the bar "under 1,000 exact height probes per district" (now 4,900 to 8,900 median on
  the gate cities; the step came with TR's fuller hill mask); carried to 6.5 and 6.7, which must
  lean on the coarse height grid in the game.
- **Determinism** (5.6): `SettlementDeterminismTest` in full mode (`-Dfb.det.full=true`) plans
  all 24 city presets with a fixed site, in order, with shuffled cells and districts, on 8
  threads over an evicting 2-cell skeleton cache, and each district alone, and compares the
  settlement, district and scene bytes: **6/6 tests pass** (186 s), including the three mutant
  sources it must catch; `DeterminismLintTest` 3/3. Run 2026-10-21 by the agent at `2e67271`. The
  two `--cell` presets (`mc2-*`) are covered by `MultiCellCityTest` instead.

# What changed this phase

- **Region and sites**: region field with tiers hamlet to metropolis (R1), water bias (R2),
  cells joined into one city by a join rule (MC1), cities across cells meeting at hashed gates
  (MC2).
- **City skeleton and districts**: per-cell skeleton (urban mask, density, quarters, hub, A*
  main roads, arterials on hashed lines, district tiles; SK), lazy per-district streets, lots
  and briefs on exact terrain (DL, DB), whole-city road network graded once per road (ROADS),
  hub squares moved or shrunk to fit real ground (HUB).
- **Tier programs**: hamlet and village (D1), town with wall ring and gates (D2), city and
  metropolis with density-driven skyline, mid-rise band, diagonal avenues, squares at crossings,
  parks and an outer ring (D3), old-town subdivision with T junctions and avenue cut lanes
  (IRR1, 5.13).
- **Terrain**: quays, plank bridge decks (role `deck`), a fuller hill mask, arterials kept off
  steep ground (TR).
- **Styles**: per-lot biome style with a blended border (ST); plains palette calmer (PLAINS).
- **Buildings** (Phase 4 gate feedback): hall-based circulation and archways (5.11), squarer
  rooms and bedrooms first (CIRC2), a kitchen and living room in every house (HOUSEPROG), shop
  arcades, market halls and workshop porches (5.12), shopfront landings (SHOPLAND), jetties over
  the street (JT).
- **Tools**: plan view, district 3D fly-through, region map (V0 to V2), recorded terrain dumps
  and gate windows (T1, T2), `fractal-cli city`, `city-check`, `city-bench`, city presets and
  gallery (D0), determinism suite (DT), scale bench (SC).
- **Build and flake changes this phase** (for John to review before running as himself):
  `mod/build.gradle` (JUnit platform and deps for the T1 dump tool, 2 GB test heap, `fb.*`
  property forwarding), `cli/build.gradle` (fixtures as test inputs), `viewer/vite.config.ts`
  (`region.html` and `plan.html` inputs), `viewer/package.json` scripts `city` and
  `city-gallery` (no new dependencies), `flake.nix` `SB_SPACE_IGNORE` (SilverBullet ignores
  source and data files).

# Questions for John

Each also has its own REVIEW entry where one exists (named in brackets).

1. **The gate.** For each city above: does the biome mix read right, does the border blend
   (city 5), does the downtown read as a downtown (city 7), would a person have built this town
   here? Is the pack enough to pass the gate, as with Phase 4 ("move on without being
   perfect")?
2. **Villages too gridded?** A ruler-straight main street with houses lined up both sides, a
   cross street on half (village above; [D1 hamlet and village]).
3. **Walls.** Every wall lot is a full gatehouse (a gate building every 24 to 64 blocks, not
   only at roads), and rings are rare on real terrain: none of the 9 gate cities has one, the
   walled town is a hand-picked preset. Want a plain curtain-wall segment and more walls?
   [D2 town walls]
4. **Downtown.** The metropolis core plateaus at 60 storeys over about 0.3 of the radius (city
   7 reads as one slab of equal towers), and the core has almost no city blocks (2 of 2355
   lots). More height variety, fewer towers, more blocks? [Downtown skyline and avenues]
5. **Old town round the hub in every city.** IRR1's old town now sits round the hub of every
   city (a metropolis keeps its downtown; a city's mid-rise centre becomes old town). Wanted,
   or only in some cities? [IRR1 (5.13)]
6. **Avenues fronted by cut lanes.** Lots along a diagonal avenue face short cut lanes, not the
   avenue; wedges under 16 blocks stay empty. OK? [IRR1 (5.13)]
7. **Empty arterial blocks.** The snow city lost lots after ROADS (arterial blocks with no link to
   a main road stay empty; city 3 reads as a ribbon). Acceptable? [Whole-city roads]
8. **Moved hub squares.** On rough ground the hub square moves up to 160 blocks or shrinks to 16
   blocks; hall and temple sometimes a street away from it. [Whole-city roads]
9. **Quays and bridges.** Do the stone promenades along water and the plank bridges read as a
   waterfront (city 6; `terrain/`)? [Waterfront quays and bridges]
10. **Bedrooms per house after the kitchen rule.** Every house now has a kitchen and a living
    room, at about 0.14 fewer bedrooms per house (1.7 per medieval house). [House kitchen and
    living room; CIRC2]
11. **Seed 9's living room** is still exactly 2:1 (the room-aspect limit allows it). Fine?
    [CIRC2]
12. **Savanna as desert.** Savanna biomes take the desert style (city 4). Keep, or give savanna
    its own style later?
13. **Plains palette**: approved 2026-10-21 ("much better"); nothing to answer.
14. **5.9 probe bar not met**: per-district exact probes are 5 to 9 times the plan's 1,000 bar
    (time per district is flat). Accept as carried to 6.5 / 6.7 and close Phase 5 with 5.9
    unticked?
15. **Build and flake changes** listed under "What changed": OK to keep?
16. Still open from Phase 4: `medieval` is a style no biome picks (assumption: opt-in style). Keep?

Viewer limits noticed while building the pack (not questions; candidates for the backlog):
the district 3D view paints every building in the first building's palette; 3D terrain is
green in every biome; the gate-pack square camera sometimes lands inside a building.
