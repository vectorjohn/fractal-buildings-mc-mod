# Plan: Phase 5, settlements and cities

Status: planned (2026-10-21). Author: lead-coder, 2026-10-21. Items: ROADMAP "Phase 5"
5.1–5.10 (5.11 and 5.12 are building-level items coded in parallel; see "Interaction
with 5.11 and 5.12").
Decisions this plan relies on: [ADR 0003](../adr/0003-worldgen.md) (W1 district starts,
W5 terrain, W6 flattening as a tool, P1 two-level plan, R1 region field, R2 join cells,
D1–D7 determinism), [ADR 0001](../adr/0001-building-direction.md) (buildings of every
size, footprints up to about 200 inside one district), the Phase 4 plan
([phase4.md](phase4.md): generator v1, golden digests, `TypeLimits`, galleries),
spikes [S4](../research/spikes/s4.md) (region field, dump format) and
[S6](../research/spikes/s6.md) (skeleton and district costs).

# Decisions for John (work proceeds on the recommended default)

These shape the architecture or the look of every city. Each has a default that the
steps below implement; changing one later costs the steps named.

1. **District tiles are a fixed, world-wide partition, not drawn per city.**
   Districts (the structure-start unit, ADR 0003 W1) are the rectangles between
   world-wide hashed lines, 8 to 12 chunks apart, chunk-aligned, the same in every
   world area whether a city is there or not. City character (downtown, market,
   residential, waterfront...) comes from **quarters**, a separate terrain-aware
   field that does not move tile borders. *Why:* W1's placement test stays a pure
   geometric test with no probes, every chunk finds its district in O(1), joined
   cities and neighbouring cells need no coordination, and buildings never cross a
   tile border by construction. *Rejected:* district borders grown per city along
   ridges and water (ARCHITECTURE's hypothesis): needs the skeleton (coarse probes)
   inside the placement test and makes borders depend on which cell drew them.
   *Cost:* a big lot must fit inside one tile (at most about 180 blocks across after
   arterial half-widths; the hall's 200 x 200 test lot does not fit, its 120 x 80
   does). Steps hit if changed: DG, SK, DL.
   - @John 2026-10-21: #question @lead-coder does this mean that, e.g. the city block from the last phase, if you wanted to make a grid of them, they would have to basically snap to a grid of 200 blocks? I don't see how that will work, maybe that “block” building doesn't make sense. Or it can happen in isolation, but have infill around it of other buildings, and there isn't really a way to tile large structures. For Hall that seems fine because you wouldn't see multiple city blocks filled up with halls. 
3. **Roads: a hybrid of a hashed arterial grid and organic main roads.** In cities,
   arterials run on tile borders (so they are straight, axis-aligned and continue
   across cells for free); main roads in every tier are paths found on the coarse
   lattice (slope- and water-aware A*) from the settlement's hub to gates and exits,
   so villages and towns are organic and cities are part grid, part organic.
   *Rejected:* all-organic (cross-cell joins need gates for every road, no free
   continuity) and all-grid (every town looks like Manhattan). Steps hit: SK, D3.
   - @John 2026-10-21: #question @lead-coder how much continuity are we getting for free here? It sounds like it's possible to do cross cell joins, right? Because the organic ones cross. Or do the side roads stay in their cell? Or, are you saying organic roads only in settlements/villages?
5. **"No size cap" means a configured maximum radius, not a design limit.** The join
   rule (R2) must read a bounded neighbourhood (about 3 x the largest radius), so the
   radius distribution's tail is clipped at `maxRadius`, a config value
   (default **4096**, cities up to about 8192², matching ADR 0003's "soft warning
   above 8192²"). 6.5 exposes it. Steps hit: R1, MC1.
6. **Settlement plans get their own formats beside `fb-scene/1`.** The 2026-10-16
   assumption was "one document format for every scale". A plan view of a 4096²
   city cannot carry voxel shells, so the plan is `fb-settlement/1` (skeleton,
   districts, roads) plus one `fb-district/1` per district (streets, lots, briefs);
   a district's 3D view stays `fb-scene/1` with two additive keys. Steps hit: P0,
   V1, V2.
7. **Jetties over streets** (John said yes, Phase 4): a building may overhang a
   street by at most 1 block (2 on streets at least 7 wide), from storey 1 up, with
   at least 3 blocks of clearance over the street surface, never over an arterial's
   carriageway centre or a lane narrower than 4. Step JT.

Cheap-to-change decisions are made in the text below and listed as rows in
`docs/ASSUMPTIONS.md` (2026-10-21, "P5 plan").

# Goal

Compose generator v1 buildings into hamlets, villages, towns, cities and
metropolises, still entirely outside Minecraft, on real terrain and biome dumps. A
city is planned in two levels (ADR 0003 P1): a cheap **skeleton** per region cell
(coarse screen, roads, quarters, density, gates), then **district detail** built
lazily per district tile (streets, lots, briefs, exact heights, buildings). The
viewer shows a 2D plan view that stays usable for cities thousands of blocks wide and
a 3D fly-through of any district. Gate: John reviews cities in at least five biome
mixes, one straddling a border, one larger than 512², one with a high-rise downtown.

# Approach and key decisions

## The units, from large to small

```
world seed
 └─ region cell (Voronoi, 1024, R1)  ── CellDecision: settled?, tier, centre, radius   [R1, R2, MC1]
     └─ settlement site (one claimant cell + member cells it joined)                    [MC1]
         └─ cell skeleton (per member cell; coarse 32-lattice)                            [SK]
              mask, density, quarters, hub, gates, main roads, arterial flags, landmarks
 district tile (world-wide hashed grid, 8–12 chunks; independent of cells)               [DG]
     └─ district plan  = f(seed, tile, skeletons of the cells overlapping the tile)        [DL, DB]
          streets, plazas, lots with briefs  → fb-district/1
          └─ buildings: GeneratorV1 per lot → fb-scene/1 (+ ground boxes)                  [DB]
```

- **Pointwise or contracted.** Everything a district reads from outside its tile is
  either a pointwise pure function of (seed, position) (density, quarters, arterial
  flags, style weights, district tiles) or a per-cell result with an explicit
  border contract (main roads end exactly at hashed gates on shared cell borders).
  That is what makes districts reproducible in any order (5.6) and cities join across
  cells without either cell reading the other's plan (5.8).
- **Coarse versus exact probes** (ADR 0003 D4, W2). The skeleton reads only a
  `CoarseProbe` on the 32-block lattice and must stay correct when its heights are
  off by up to 20 (a test wraps it with a hashed ±20 error); it never sets a floor
  height. District detail reads exact heights, sparsely: roads and plazas on an
  8-block lattice, lots at corners, centre and front midpoints. Buildings read every
  column of their own lot (that is generator v1, off the lane in game, P2).
- **Lots** come from two layouts: **grid blocks** (a tile's urban part is cut into
  rectangular street blocks by hashed street lines, blocks into lots by frontage
  packing, whole blocks become `block` lots in dense quarters) and **street
  frontage** (lots packed along both sides of every organic road). Lots are
  axis-aligned rectangles (the IR's `Lot`), front facing the street. Rejected:
  polygon parcels (no IR, no generator support, Minecraft is axis-aligned anyway).
  - @John 2026-10-21: #ideas @lead-coder can you think of any ways a city can be mostly grid based with exceptions? Real cities often have an odd diagonal road cutting through in some places. Similarly, can you envision any way the architecture as it stands now could support non-grid based cities? It's Minecraft so it's always rectangles but it could be different size rectangles that don't line up. Even grids are a modern American more planned city thing, but old cities are a lot less regular. Just want to understand what is possible.
- **Type and height by density** (5.7): a pointwise density 0–100 (distance to the
  centre over the radius, a bonus near arterials, hashed low-frequency noise, capped
  by tier) and the quarter pick the type and storey range through one table,
  `ClassTable`; the brief passes the storey range to generator v1.
- **Style by biome, blended** (5.4): style weights at a point are the share of each
  style's biomes in a 48-block disk; each lot draws its style from those weights
  with its own hash, so a border becomes a band of mixed buildings about 100 blocks
  wide. Unclaimed biomes map to the nearest style through a table.
- **Terrain** (5.3): the skeleton keeps water, peaks and slopes steeper than 1 in 2
  out of the urban mask; roads follow the ground within a grade limit (1 rise per 3
  blocks), cut and fill up to 4 blocks, and bridge water up to 40 blocks; plazas are
  levelled (fill first, cut at most 4); lots on slopes take only types whose
  `maxGrade` accepts them; optional fill houses on ground too steep are left empty
  (ADR 0003 W5: reject only small fill buildings; landmarks move instead). No terraced
  city areas (W5). Cuts and fills are `ground` boxes in the district scene; in game
  they become W6 flattening or piece blocks (Phase 6).
- **Water bias** (5.10) is a term in the region field's best-of-8 candidate score for
  towns (half weight), cities and metropolises (full weight); hamlets and villages are
  unchanged byte for byte.
- **Versioning.** Plans carry `planVersion` (1 for all of Phase 5, like GEN_VERSION 2
  in Phase 4: nothing is cached outside a process yet; golden digests catch changes).
  Settlement golden digests hash the plan JSON (`fb-settlement/1`, `fb-district/1`),
  **never building IR**, so 5.11, 5.12 and JT changing buildings never churn them.

## Tier ladder (5.1)

The region field draws the tier by weight, then the radius from the tier's band
(log-uniform; the metropolis band is a Pareto tail, alpha 1.5, clipped at
`maxRadius`). Bands overlap so the ladder has no hard steps. A tier adds structure;
none caps size except the top tier's configured `maxRadius`.

| Tier | Weight | Radius | Density cap | Adds |
|---|---|---|---|---|
| hamlet | 0.25 | 40–90 | 30 | 3–8 buildings on one track; no square |
| village | 0.30 | 80–220 | 45 | a main street (a cross street on 50 %), a green or square at the hub with a temple or hall, lanes |
| town | 0.25 | 200–500 | 65 | radial main roads, secondary streets, market square with a market hall, quarters, wall ring on 40 % with gates |
| city | 0.14 | 450–1100 | 85 | arterials on tile borders, grid blocks in dense quarters, several squares, an old-town wall on 60 %, `block` buildings, sparse high-rises at the centre |
| metropolis | 0.06 | 1000–`maxRadius` (Pareto) | 100 | a downtown of high-rises (20–60 storeys), parks, a second ring of arterials |

Weights and bands are constants in `core/region/Tiers.java` (cheap to change).
Before MC1 lands, radii are clamped to fit the cell (S4's grow policy); after it,
big settlements join cells.

## ClassTable (P0 commits this; D3 tunes it)

Pointwise density `d` at the lot centre and the quarter kind pick a type by weight
(hashed per lot); storeys come from the range shown, intersected with the type's
limits.

| d | Types by weight (storeys) |
|---|---|
| < 25 | house 70 (1–2), workshop 20 (1–2), tower 5 (4–6), shop 5 (2) |
| 25–49 | house 50 (1–3), shop 25 (2–3), workshop 20 (1–2), tower 5 (4–8) |
| 50–69 | shop 40 (2–4), house 25 (2–3), block 15 (2–4), workshop 10 (2) |
| 70–84 | block 45 (3–5), shop 25 (3–4), highrise 15 (20–30), hall 0 (landmarks only) |
| ≥ 85 | highrise 60 (20 + (d − 85) x 40 / 15, ± 20 %), block 35 (4–5), shop 5 (4) |

Quarter overrides: `workshop` quarter doubles workshop weight; `waterfront` swaps
block for shop and caps storeys at 4; `park` and `green` build nothing. Halls,
temples and towers as landmarks are placed by the skeleton, not this table. A type
whose `maxGrade` refuses the lot's relief is skipped and the next weight is drawn.
Highrise needs at least 20 storeys (its `TypeLimits`); D3 may add mid-rise (10–19).

# Data formats

Conventions as `docs/IR.md`: integers only, 64-bit values as decimal strings, keys in
the order shown, list elements one per line, deterministic writer, trailing newline.
`points` are world `[x, z]` columns; a road's `points` are its centre line, every
segment axis-aligned or 45° (A* moves on the lattice). The full text goes into
`docs/IR.md` (new sections) in step P0.

## `fb-settlement/1` (the skeleton, plan view overview)

```
settlement { format: "fb-settlement/1", planVersion: 1, genVersion: int, seed: "<int64>",
  site:      { id, tier, centre: [x, z], radius, claimant: [cx, cz], cells: [[cx, cz]] },
  bounds:    { x, z, w, d },                                 plan area, world columns
  coarse:    { x0, z0, step, nx, nz,                          row-major, z outer
               floorY: [int], water: [int], biome: [int], biomes: [string],
               mask: [int],        0 wild, 1 urban, 2 water, 3 steep, 4 open (park, green)
               density: [int],     0..100
               quarter: [int] },   index into quarters, -1 none
  quarters:  [{ id, kind }],       kind: downtown|old-town|market|residential|workshop|waterfront|park|outskirts
  cells:     [{ cx, cz, site: [x, z], polygon: [[x, z]] }],
  districts: [{ id, ix, iz, x, z, w, d, origin: [chunkX, chunkZ], quarter, density,
                styles: [{ style, weight }], lots, detail: "<relative path>" | null }],
  roads:     [{ id, kind, width, points: [[x, z]], bridges: [[i, j]] }],
               kind: arterial|main|street|lane|path; bridges: point index ranges on water
  gates:     [{ id, kind: cell|wall|exit, x, z, cells: [[cx, cz], [cx, cz]] | null, road: id | null }],
  landmarks: [{ id, kind, x, z, w, d, district }],   kind: square|market|green|park|temple|hall|tower
  walls:     [{ id, points: [[x, z]], closed: bool }],
  violations: [violation] }
```

Ids: districts `d<ix>_<iz>` (negative as `m`: `d3_m2`), roads `r<cx>_<cz>_<n>` per
cell skeleton plus `a<axis><line>_<from>` for arterial segments, gates `g<n>` sorted by
(x, z), landmarks `l<n>`.

## `fb-district/1` (one per district, loaded on demand)

```
district { format: "fb-district/1", planVersion: 1, genVersion: int, seed: "<int64>",
  district: { id, ix, iz, x, z, w, d, quarter, density },
  streets:  [{ id, kind, width, points: [[x, z]], y: [int] }],    y per point: road surface
                                                                    (heightmap convention, first free y)
  plazas:   [{ id, kind, x, z, w, d, y }],
  lots:     [{ id, x, z, w, d, front, type, style, storeys: [min, max], streets: mask,
               airRights, frontY, density, status }],   status: "build" | "empty:<reason>"
  scene:    "<relative path to fb-scene>" | null,
  violations: [violation] }
```

`streets` holds the parts of skeleton roads inside the tile (clipped, same ids) and
the tile's own streets and lanes. `frontY` is the street surface y in front of the
lot's front edge centre. `airRights` is 0 until JT.

## `fb-scene/1` additions (district scenes)

Additive, written only when present, so every existing file and digest is unchanged:

- `terrain.water`: optional `[int; nx*nz]` water depth above `floorY` (0 on land).
- top-level `ground: [box]` after `buildings`: world-frame boxes (not shell-local)
  for road surfaces, plaza paving, fill, cuts (`air`), bridge decks and piers;
  `layer` is the new `ground`, `storey` -1.
- roles `road`, `paving`, `ground` (fill earth): solid and closed (`Role.java`,
  viewer `ROLES`, every style's `palette` and `colours`).
- `lots` and `buildings` hold every built lot of the district.

## Region maps

The region command writes S4's `fb-region-dump/1` with `layers` filled (S4 "Dump
format" lists the per-cell keys), plus the per-cell keys `tier`, `waterDist` (5.10),
`claimedBy` (MC1). Terrain dumps are the same format with `layers: []`; files may be
gzipped (`.json.gz`; readers detect the `1f 8b` magic, not the name).

# Core packages and APIs (planner-core, pure Java)

- `core/probe/CoarseProbe` (P0): `int stride()`, `floorY(x, z)`, `waterDepth(x, z)`,
  `boolean water(x, z)` (depth >= 2 or a water biome), `String biome(x, z)`; throws on
  off-lattice columns; `CoarseProbe.of(TerrainProbe, BiomeProbe, stride)`.
  `core/probe/BiomeIds` (P0): `isWater`, `isPeak`, `path` for namespaced ids (rules as
  S4's `BiomeClass.isWater/isPeak`, `docs/research/spikes/s4.md`; code on branch
  `claude/spike-s4`, `planner-core/src/main/java/dev/fractalbuildings/core/region/`).
- `core/region/` (R1): `RegionField`, `Tiers`, `CellDecision`, `RegionPlanner`,
  `BiomeClass`, `RegionMap` (writer); MC1 adds `JoinRule` and `SettlementSites`.
- `core/settlement/` (P0 model and JSON; SK, DL, DB fill it): `SettlementSite`,
  `RegionView` (cell → site or none; `FixedSiteView` for CLI `--site` and tests,
  `FieldRegionView` after R1), `DistrictGrid` (DG), `CellSkeleton` + `Skeletons` (SK),
  `DensityField`, `QuarterField` (SK), `ClassTable` (P0), `DistrictPlan` + `Districts`
  (DL), `DistrictScenes` (DB), `SettlementJson`, `DistrictJson`, `SampleSettlement`
  (P0), `SkeletonSource` (direct and cached, same pattern as `core/cache/CachedIrSource`).
- `core/terrain/` (TA): `WaterField`, `LatticePath` (A*), `Grading` (road and plaza
  profiles, cut/fill/bridge boxes), `LotGround` (sparse relief).
- `core/style/StyleField` (ST).
- `core/check/SettlementChecks` (P0 creates, steps add rules): a separate file so
  `Invariants.java` (touched by 5.11, 5.12, JT) does not conflict.
- `core/gen/GenRequest` gains an optional `Brief` (P0); no behaviour change without one.

Existing APIs the steps use (checked 2026-10-21 on `claude/run-phase5` 9c5aab7):
`Hash64.hash(seed, purpose, keys...)`, `Hash64.below/range`, `Draws(seed)`;
`JsonWriter.object()/compactObject()/num/text/str64/raw/ints/array`, `JsonReader.parse`;
`IrJson.write/read`; `GeneratorV1.generate(GenRequest)` → `SceneDoc` (one lot);
`SpecPolicyV1.spec` (throws `IllegalArgumentException` for lots too small or, for
`maxGrade` > 3 types, too steep); `TypeLimits` (`minLotWidth/minLotDepth`, `maxGrade`,
`testLots`); `Types.get/ids`; `StyleLibrary.get/forBiome/ids` (`AUTO`, `FALLBACK
"plain"`); `RecordedProbe.read/parse` (implements `TerrainProbe` and `BiomeProbe`,
integer-bilinear heights, nearest biome); `TerrainPatch.sample`; `Invariants.check(scene,
terrain)`. The determinism lint bans `HashMap`, `HashSet`, `Math.sin|cos|tan|pow|exp|log|
atan2|cbrt|hypot`, `nanoTime`, `currentTimeMillis`, `new Random(` in `src/main` (use
`StrictMath`, `TreeMap`, `LinkedHashMap`; timing lives in the CLI).

# Settlement invariants (`SettlementChecks`)

Each rule lands with a test that a deliberately broken plan fails it. Ids:

| Id | Rule | Added by |
|---|---|---|
| `lot-in-tile` | every lot lies inside its district tile | P0 |
| `lots-disjoint` | lots overlap no other lot, street, plaza or landmark | P0 |
| `lot-frontage` | the cells just outside a lot's front edge are street, road or plaza along at least 3 columns | DL |
| `lot-dry` | no lot column has water depth > 0 | DL |
| `lot-grade` | the lot's relief is within its type's `maxGrade` rule (the spec policy never throws) | DL |
| `road-grade` | adjacent road points along a street differ by at most 1 in y, and by at most 1 over any 3 blocks | TA, DB |
| `roads-connected` | district (DL): every street component of a tile touches a skeleton road or the tile border where a neighbour's street continues; city (D0): every street cell reaches the hub through streets and bridges | DL, D0 |
| `tile-border-agree` | road cells on both sides of every shared tile border match | D0 |
| `cell-gate-agree` | every main road leaving a cell ends on a gate that the neighbour's skeleton also has | SK, MC2 |
| `skeleton-in-cell` | a cell skeleton's roads stay inside its polygon (gates exactly on its border) | SK |
| building invariants | `Invariants.check` on every building of a district scene, 0 failing | DB |

# Test infrastructure

- **Terrain dumps** (T1, T2): `fixtures/terrain/` gains gzipped `fb-region-dump/1`
  files: coarse world maps (step 64) for seeds 12345 and 987654321, and fine windows
  (step 8, up to about 3072²) around chosen settlements; `fixtures/terrain/README.md`
  lists each (seed, window, step, what it shows). Budget about 4 MB in total.
- **Synthetic probes** for unit tests: `SyntheticTerrain`/`SyntheticBiomes` (exist),
  plus `ProbeSpecs` additions in P0: `biomes:split:<x>:<a>:<b>` (biome a west of x,
  b east), `river:<x0>:<width>:<depth>` (a north-south river over any terrain spec,
  composable with `+`). The ±20 `BiasedCoarseProbe` (SK, test sources).
- **`SampleSettlement`** (P0): a hand-made 2 x 2-tile town on synthetic terrain (river,
  plains/desert split) written to `viewer/fixtures/settlement/sample/`
  (`settlement.json`, four `districts/*.json`, one `scenes/*.json`); a fixture test
  byte-compares it. `SampleSettlement.big(sizeBlocks)` generates a synthetic
  plan with lots for viewer performance tests (CLI `sample-city`, output gitignored).
- **CLI** (`fractal-cli`): `region` (R1), `sample-city` (P0), `city` (SK → DL → DB → D0),
  `city-check` and `city-bench` (D0, D7). All write under `--out-dir`.
- **City presets** (D0): `viewer/city-presets/<name>.json`
  (`{seed, terrain, site: {cell: [cx, cz]} | {at: [x, z], tier, radius}, districts:
  "all" | "auto:N" | [ids], scenes: [ids], camera: {plan: {...}, stops: [[x, y, z, yaw, pitch]]}}`),
  read by `scripts/gen-city.mjs` and `e2e/city.spec.ts`; `npm run city-gallery`
  (not part of `scripts/test-all.sh`, like `npm run gallery`).
- **Goldens**: `planner-core/src/test/resources/golden/settlement/<case>.txt`
  (SHA-256 of `fb-settlement/1` and every `fb-district/1` for fixed (seed, fixture,
  site) cases) and `golden/region/<seed>.txt` (region decisions). Generated, never
  hand-merged (same rule as `golden/v1`).
- **Counting probe** (P0): `core/probe/CountingProbe` wraps a probe and counts calls
  (coarse, exact, biome) with `AtomicLong`s; D7 reports probe counts per district.

# Steps

Each step is one coder (Sonnet, `mode: implement`). "Needs" is what must be merged
into the run branch first. Every step: merge the run branch before its final test
run; run `:planner-core:test :cli:test` (plus `npm test` for viewer changes, plus
`:mod:test` for T1/T2); log in `docs/log/`; tick only the ROADMAP item it owns (listed
per step; others add a note); regenerate goldens it changes and say so. Evidence that
is a picture is opened with Read and described in the log.

## Wave A: start at once, side by side (P0 first if only one slot)

### P0. Settlement IR, fixtures, brief (foundation) — size M

Needs: nothing. Blocks: SK, DL, DB, V1, V2, JT. Owns: `core/settlement/` model and JSON,
`core/probe/CoarseProbe|BiomeIds|CountingProbe`, `GenRequest`/`SpecPolicyV1` brief,
`Role.java`, viewer `ir.ts` roles/layers, every style file's three new palette lines.

1. Records in `core/settlement/` for every object in "Data formats" (`SettlementPlan`,
   `SitePart`, `CoarseRaster`, `Quarter`, `CellOutline`, `DistrictSummary`, `Road`,
   `Gate`, `Landmark`, `Wall`, `DistrictPlan`, `Street`, `Plaza`, `LotBrief`), with
   validation in constructors (ids, list sizes, tile containment of lots).
   `SettlementJson.write/read`, `DistrictJson.write/read` (writer byte-stable, reader
   strict like `IrReader`). `SettlementSite` and `Tier` enum (hamlet..metropolis).
2. `fb-scene/1` additions: `terrain.water`, `ground` boxes, layer `ground`, roles
   `road`, `paving`, `ground` (Java enum, `IrJson`, `IrReader`, `IrValidator`: ground
   boxes need no building; viewer `ir.ts` parses them; palettes and colours in all
   seven style files: road = gravel/path gradient, paving = stone bricks, ground =
   dirt; desert uses sandstone/sand; snowy packed ice is wrong, use spruce path or
   gravel).
3. `GenRequest.brief` (`Brief(int minStoreys, int maxStoreys, int streets, int
   airRights, int frontY)`, `Brief.NO_FRONT_Y = Integer.MIN_VALUE`); old constructors
   pass `null`. `SpecPolicyV1`: with a brief, storeys are drawn from the brief's
   range intersected with the type's (if empty, the type's bound nearest the brief);
   streets = brief.streets with the front bit forced (unless the type has
   `allStreets`); `airRights`/`frontY` are carried for JT/5.12 and ignored otherwise.
4. `CoarseProbe`, `BiomeIds`, `CountingProbe`; `ProbeSpecs` `biomes:split` and
   `river` (see Test infrastructure).
5. `ClassTable` (the table above) with `pick(seed, lotX, lotZ, density, quarter,
   relief, lotW, lotD)` returning (type, storeys range) or none; `SettlementChecks`
   with `lot-in-tile`, `lots-disjoint`.
6. `SampleSettlement.sample()` and `.big(size)`; CLI `sample-city --out-dir D [--big
   N]`; fixture under `viewer/fixtures/settlement/sample/`; `SampleSettlementTest`
   byte-compares (regenerate command in its message and NOTES).
7. Docs: `docs/IR.md` new sections "Settlement plans" and "District scenes";
   `CLAUDE.md` CLI line for `sample-city`.

Tests: JSON round trip for every record (write → read → write equal bytes); strict
reader rejects a wrong format, a missing key, a lot outside its tile; golden v0/v1
digests unchanged (no brief); a brief `[3, 3]` on a house gives 3 storeys, `[30, 40]`
on a house gives 3 (nearest bound), streets mask honoured in the spec; `ClassTable`
weights sum and distribution over 10,000 lots within 2 % of the table;
`lots-disjoint` fails on a hand-broken plan. `StyleLibraryTest` covers the new roles.

### R1. Region field in the core, tiers, `region` command (5.1, 5.10 groundwork) — size M

Needs: nothing (uses `CoarseProbe` from P0 if merged, else its own copy to be replaced
at merge; prefer waiting a few hours for P0). Owns `core/region/`, CLI `RegionCommand`.

Port S4's `RegionField`, `RegionPlanner`, `CellDecision`, `BiomeClass` from
`claude/spike-s4` with these changes: `Hash64` with string purposes (`region/site-x`,
`region/site-z`, `region/roll`, `region/tier`, `region/radius`, `region/cand`)
instead of `Mix64`; **integer site coordinates** (`cx*S + S/2 + floor((u-0.5)*jitter*S)`)
and integer squared distances in `query`; exact `edgeDistance` with `StrictMath.sqrt`;
candidates by `StrictMath.cos/sin`; namespaced biome ids; `BiomeClass.multiplier/group`
from a resource table `region/biome-classes.json` (S4's values); probes through
`CoarseProbe` (stride 64). Tiers from `Tiers` (table above; `maxRadius` field,
default 4096); `Policy.GROW` (S4 clamp) only, JOIN comes with MC1. CLI: `fractal-cli
region --seed N --terrain recorded:FILE [--cell-size 1024] [--out FILE]` writes the
region dump with layers.

Tests: S4's assertions on `fixtures/terrain/s4-12345-crop32.json` and synthetic
terrain now, and on T1's coarse dumps once merged (add then, or leave to R2): no city
centre on water or peak; centre at least 0.15 S from the cell edge; decisions in
shuffled order equal; 1 vs 8 threads equal; seeds differing only above bit 32 give
different fields (mutant: cast to `int` fails it); tier frequencies over 20,000 cells
within 2 % of the weights; radius bands respected; `golden/region/<seed>.txt`.

### T1. Terrain dump tool and first dumps — size M

Needs: nothing. Owns: `mod/src/test/` (new), `mod/build.gradle` test deps (**build
change, call out**), `fixtures/terrain/`, gzip reading in `RecordedProbe`.

1. `mod/build.gradle`: the JUnit lines from the S4 spike (`testImplementation
   platform("org.junit:junit-bom:${junit_version}")`, `junit-jupiter`,
   `junit-platform-launcher`, `tasks.named('test') { useJUnitPlatform(); maxHeapSize
   = '2g' }`). Verified 2026-10-21 with `javap` on
   `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/`:
   `ChunkGenerator.getBaseColumn(int, int, LevelHeightAccessor, RandomState)` and
   `VanillaRegistries.createWorldLookup()` exist.
2. `mod/src/test/java/dev/fractalbuildings/mod/TerrainDumpTool.java`: a JUnit test
   that runs only with `-Dfb.dump=<spec>` (skipped otherwise), bootstrapping vanilla
   worldgen without a server exactly as `RegionMapSpikeTest.boot()` on
   `claude/spike-s4` (`SharedConstants.tryDetectVersion()`, `Bootstrap.bootStrap()`,
   `NoiseBasedChunkGenerator` from the overworld noise settings and the overworld
   multi-noise preset), sampling with `RegionSampling.fill` from the same branch
   (`getBaseColumn` for surface and floor; biome at the surface quart with the fixed
   priming target). Spec: `seed:x0:z0:step:nx:nz:name[,…]`; writes
   `fixtures/terrain/<name>.json.gz` (`fb-region-dump/1`, `layers: []`, `mc: "26.3"`).
   Mod code lives in test sources only (no shipped classes). `:mod:test` needs a
   `-Dfb.*` forward like planner-core's.
3. `RecordedProbe.read` accepts gzip (magic bytes); `ProbeSpecs` `recorded:` too.
4. Dumps: `world-12345-s64` and `world-987654321-s64` (centre 0,0; 384 x 384 at step
   64, as S4), and two fine windows for early work: `w12345-river-s8` (2048² at step 8
   around (-384, -3136), the crop's city; has a river) and `w1-mixed-s8` (2048² at
   step 8 around a land city in a biome mix that T1 picks from S4's maps). Record
   timings and sizes in `fixtures/terrain/README.md`.

Tests: a dump of a 16 x 16 window is byte-identical on 1 and 4 threads; the new
crop of seed 12345 reproduces `s4-12345-crop32.json`'s surface and water exactly at
the known point (576, -4160) (NOTES); `RecordedProbe` reads a gzipped file equal to
its plain twin.

### ST. Biome style field (5.4) — size S

Needs: nothing. Owns `core/style/StyleField`, `planner-core/src/main/resources/styles/
biome-fallback.json`. Ticks **5.4** after DB wires it (DB adds a note; ST ticks if DB
merged first, otherwise DB ticks).

`StyleField(BiomeProbe, long seed)`: `weights(x, z)` = count of style votes over
biome samples on a 16-block lattice within a 48-block disk around (x, z) (about 29
samples; biome → style by `StyleLibrary`'s biome lists, then `biome-fallback.json`
for unclaimed biomes: savanna and windswept savanna → desert; dark forest, pale
garden, windswept hills/forest/gravelly hills, stony shore → taiga; cherry grove,
beach → plains; swamp, mangrove swamp → jungle; stony peaks → snowy; water biomes
cast no vote; anything else → plain). Returned as a sorted list of (style, weight).
`pick(lotX, lotZ)` = hashed weighted draw (`Hash64.hash(seed, "lot-style", x, z)`).
`dominant(tile)` = mode over the tile's 16-lattice samples (ties by style id).

Tests: on `biomes:split` (desert west of x = 0, plains east), the desert share of
picks is > 95 % at x < -64, < 5 % at x > 64 and between 30 % and 70 % at |x| < 16;
picks are a pure function (shuffled order equal); a biome claimed by no style and
not in the fallback gives `plain`; on a fine dump (T1) report the style mix.

### TA. Terrain toolkit (5.3 groundwork) — size M

Needs: nothing for the pure parts; P0 merged before the box output (roles). Owns
`core/terrain/`.

- `WaterField.of(CoarseProbe, x0, z0, nx, nz)`: lattice distance to the nearest water
  sample (8-connected chamfer, in blocks), plus water component labels (ocean vs
  river/lake by component size > 4096 samples at stride 32).
- `LatticePath.find(start, goal, cost, bounds)`: A* on a lattice (8 neighbours), costs
  from a function; ties broken by (f, g, z, x) so the path is unique; returns the
  lattice points. Default cost: 1 per block + 6 x |Δy| per block + 40 per water sample
  (a bridge) with water runs longer than 40 blocks forbidden.
- `Grading.road(points, width, exact)`: road surface y per point from exact heights
  sampled every 8 blocks along the centre line, smoothed (3-point median then
  clamped so the grade is at most 1 per 3 blocks), cut/fill up to 4 blocks; deeper
  is reported (`steep` segments) for the caller to reroute or make stairs; bridges
  over water spans (deck at max(water surface) + 3, piers every 8). Output:
  per-point y and `ground` boxes (road, ground fill, air cut, bridge deck with
  `trim`, piers with `wall`).
- `Grading.plaza(rect, exact)`: level at the median exact height of a 4-block lattice,
  fill first, cut at most 4; refuses (returns empty) if relief > 6.
- `LotGround.relief(lot, exact)`: max − min of 9 sparse samples (corners, mid-edges,
  centre) and the front-centre height.

Tests: A* finds the straight line on flat ground and goes round a hill when the
detour is cheaper; never crosses a 41-block water run, does cross a 40-block one;
equal-cost ties give the same path whatever the neighbour order; road grading on a
plane of slope 1/2 gives grade ≤ 1/3 with cuts and fills ≤ 4 and flags the excess;
every `ground` box from a graded road lies within the road width; plaza on a 1/8
slope is level and its boxes cover the rectangle exactly.

### DG. District grid (5.2, 5.6 groundwork) — size S

Needs: nothing. Owns `core/settlement/DistrictGrid`.

Lines along x (constant x) and along z: line k sits at chunk `10k + o(k)`,
`o(k) = Hash64.below(hash(seed, "tile-line", axis, k), 3) - 1`, so tiles are 8–12
chunks (128–192 blocks). `tileOf(x, z)` → (ix, iz) with ix the line to the west
(O(1): check lines k-1..k+1 around `floorDiv(chunkX, 10)`); `tile(ix, iz)` → world
rectangle; `originChunk` = the chunk at the tile centre (ADR 0003 W1); `owner(x, z)`
for D5 ownership (a column belongs to the tile containing it; a road centred on a
line is split along it). `segments(ix, iz)`: the four border segments with stable
keys (axis, line, index of the crossing line), so a segment's hash is the same seen
from either tile.

Tests: every column of a 4096² window belongs to exactly one tile; tiles are 128–192
wide and chunk-aligned; the origin chunk is within 6 chunks of every tile chunk (a
start reaches ±8, leaving 2 for roads and the beard); seeds differing above bit 32
differ; shuffled `tileOf` calls agree.

## Wave B: after P0 (and the named wave-A steps)

### SK. Cell skeleton (5.2 skeleton half, 5.6, 5.8 gates) — size L

Needs: P0, DG, TA. Owns `Skeletons`, `CellSkeleton`, `DensityField`, `QuarterField`,
`TierProgram` (interface + `DefaultProgram`), `RegionView`/`FixedSiteView`, CLI `city`
(skeleton only). Critical path.

`Skeletons.build(seed, RegionView view, CellKey cell, CoarseProbe probe32,
DistrictGrid grid)` → `CellSkeleton`, a pure function of those inputs only:

1. **Cell polygon**: the Voronoi cell of `cell` from the region field's sites
   (Sutherland–Hodgman clip of the 3S square by bisectors with the 24 neighbours;
   canonical vertex order; doubles, `StrictMath`, rounded to ints for JSON).
   `FixedSiteView(site, cellSize, jitter)` uses its own `RegionField` geometry so
   SK does not need R1 (R1's `RegionField` replaces it at merge; same formulas).
2. **Coarse raster** over the polygon's bounding box ∩ the site disk (+64): floorY,
   water, biome, then the **mask**: urban if inside the deformed disk (radius x (1 +
   0.15 x hashed angular noise, 8 harmonics)), not water, `floorY` < 140, and no
   4-neighbour differs by more than 16 (1 in 2 over 32); then keep only urban
   components reachable from the hub by `LatticePath` with bridges ≤ 40.
3. **Hub**: the site centre if inside this cell, else the urban sample nearest the
   cell's site. **Density** and **quarters**: `DensityField.at(site, x, z)` and
   `QuarterField.at(...)` are pointwise (see "Approach"; quarters: waterfront within
   48 of water for town+, downtown where density ≥ 80 in a metropolis, otherwise a
   world-wide hashed Voronoi of quarter seeds on a 320-block grid with kinds drawn by
   tier weights, residential 60, workshop 15, market 15 within 200 of the hub, park
   5, outskirts where density < 25).
4. **Gates** on every polygon edge shared with another member cell of the same site:
   the point at t = 0.25 + 0.5 u along the edge (u from `hash(seed, "cell-gate",
   min cell, max cell)`), snapped to the 32-lattice; if it is water or steep, try 4
   more hashed t; none suitable → no gate. **Exits**: for single-cell towns and up,
   2–4 hashed directions to the urban edge.
5. **Main roads**: `LatticePath` from the hub to each gate and exit, restricted to
   the polygon; width by tier (village 4, town 5, city 7). **Arterials** (tier city
   and up): a tile-border segment is arterial when both its end samples are urban
   and `hash(seed, "arterial", segment key) < p(density at its midpoint)` (p = 1 at
   density ≥ 80, 0.5 at 50, 0 below 30); pointwise, so both sides agree.
6. **Landmarks** (via `TierProgram`, default for all tiers): the hub square (size by
   tier: green 16–28, square 24–40, market 32–48) inside one tile, moved to the
   nearest tile that fits if the hub tile cannot; a temple or hall lot facing it.
7. **Districts**: tiles whose centre sample lies in this cell and that hold at least
   one urban sample; each with quarter (mode), density (max), style weights left
   empty (DL fills them).

`TierProgram` has hooks `hub`, `exits`, `mainRoads`, `landmarks`, `walls` (no-op),
`streetLayout` (grid or frontage, used by DL), with `DefaultProgram` used by every
tier until D1–D3 add one class per tier. CLI: `fractal-cli city --seed N --terrain
recorded:FILE (--cell CX,CZ | --site X,Z,TIER,RADIUS) --out-dir D` writes
`settlement.json` (all member cells merged) and `terrain.json.gz` (the window crop for
the plan view background).

Tests: `skeleton-in-cell` and `cell-gate-agree` over 200 synthetic sites spanning
2–6 cells; building cells in shuffled order and with a fresh source gives identical
JSON; the ±20 `BiasedCoarseProbe` changes no gate count by more than one and keeps
every invariant; a main road never crosses water wider than 40; on the river window
(T1) the town straddles the river with at least one bridge; density is 0 outside
the mask and ≤ the tier cap; `golden/settlement/` cases for 3 sites. Evidence: the
plan view (V1) or, before V1 merges, a PNG from a tiny Java raster dump; open it.

### V1. Plan view (5.5 part 1) — size M

Needs: P0. Owns `viewer/plan.html`, `src/plan*.ts`, `src/settlement.ts`,
`e2e/plan.spec.ts`. Ticks 5.5 together with V2 (the later of the two ticks).

- `settlement.ts`: strict parsers for `fb-settlement/1` and `fb-district/1` (pattern
  of `ir.ts`), gzip-aware fetch (magic bytes; `DecompressionStream('gzip')`).
- `plan.html` + `plan.ts`: a 2D canvas over the plan: layers (terrain hillshade from
  `terrain.json.gz` if present, else from the coarse raster; water; biome tint;
  urban mask; density heat; quarters; cell outlines; tile grid; roads by kind and
  width with bridges; gates; landmarks; walls; lots coloured by type, outlined by
  style; violations in red). Pan (drag), zoom (wheel, 1/16 to 8 px per block), hover
  info (district, lot brief). Lots load lazily per visible district only at zoom ≥
  1/2 px per block; the overview draws district density instead. Click a district →
  link to `index.html?ir=<scene>&camera=fly` when it has a scene. URL state `?city=
  &layers=&x=&z=&zoom=`. `window.__planReady` (district files loaded, draw count).
- Sources: `?city=<dir>` (relative to the served root: `gallery-data/city/<name>/`
  under `FB_GALLERY=1`, or the bundled fixture `fixtures/settlement/sample/`).

Tests: Vitest for both parsers (fixture round trip, strict failures), the lattice to
canvas transform, the LOD rule; Playwright `plan.spec.ts`: the sample fixture
renders with road and lot colours present (pixel colour fractions above a floor),
`__planReady`, no page errors; a `sample-city --big 4096` plan (generated in the
test setup through the CLI install) renders its overview in under 3 s and a
zoom-in pan frame in under 100 ms in Chromium (record the load; coarse bar).

### V2. District 3D view and fly-through (5.5 part 2) — size M

Needs: P0. Owns `src/render.ts` additions, `src/mesh*.ts` (new), `src/fly.ts`,
`e2e/district.spec.ts`. Coordinates with no other viewer step (V1 owns new files).

- Exposed-face meshing: one merged `BufferGeometry` per layer with only faces between
  a solid voxel and a non-solid neighbour (vertex colours), used when a scene has
  more than 200,000 voxels or `?mesh=faces`; the instanced path stays for small
  scenes (existing tests unchanged). `?exterior=1` drops the interior layer.
- Terrain as a heightfield of column tops with side faces, cut by `air` ground boxes;
  water as a flat translucent surface from `terrain.water`; `ground` boxes as a
  layer (toggle in the panel).
- `?camera=fly`: `FlyControls` (WASD + mouse; three r186 ships
  `examples/jsm/controls/FlyControls.js`, checked in `viewer/node_modules` 2026-10-21);
  `?stops=x,y,z,yaw,pitch;…` places the camera at each stop in turn for screenshots
  (`window.__viewerReady.stop`).

Tests: meshing face counts for known shapes (one voxel 6, two touching 10, a closed
3³ cube 54, interior voxel adds none); terrain cut by an air box lowers that column;
Playwright: the sample district scene renders with `mesh=faces`, frames at 3 stops,
ground and water present (colour fractions); a district of the T1 river window at
`?exterior=1` loads in under 20 s under SwiftShader (record the voxel count and
load; this is the budget D0's gallery relies on).

### R2. Water bias (5.10) — size S

Needs: R1, T1, TA. Owns the score term in `RegionPlanner`; ticks **5.10**.

In `RegionPlanner.decide`, for each candidate: `dw` = distance from the candidate to
the nearest water sample (`WaterField` on the 64-lattice footprint plus a ring to
768 blocks), `w = clamp(1 - dw/768, 0, 1)`, score' = score x (1 + B x w) with B = 0.6
for city and metropolis, 0.3 for town, 0 for hamlet and village. For B > 0 tiers the
footprint water cap rises from 0.15 to 0.40 (the disk may hold coast; the urban mask
keeps buildings dry), still with a dry centre and a land share ≥ 0.5. Add `waterDist`
to the dump. Constants in `Tiers`.

Tests and evidence: on the two world dumps, hamlet and village decisions are
byte-identical before and after (compare the golden region files: only
town/city/metropolis rows change); 0 centres in water; city and metropolis count
changes by at most 15 %; the share of city and metropolis centres within 256 blocks
of water rises by at least 50 % relative (report before → after); coarse probe
calls per decision at most 1.5 x before (counting probe). Region maps before/after
(V0's page or a Java PNG) committed to `docs/review/phase5/region/`, opened and
described.

### MC1. Join rule (5.8, region half) — size M

Needs: R1. Owns `core/region/JoinRule`, `SettlementSites`, `FieldRegionView`.

ADR 0003 R2's one-round rule: a claimant is a cell whose decision is suitable and
whose radius exceeds 0.4 S; it claims every cell whose site lies within its radius;
it survives if no suitable claimant with an overlapping claim set has a higher
priority (`hash(seed, "region/priority", cx, cz)`, ties by (cx, cz)). Survivors' claimed
cells are members; a dropped claimant's cells (and its own) decide alone with the
radius clamped to the cell (GROW). Reads only cells within 3 x `maxRadius` + S;
suitability for large tiers only, cached per cell (pure-cache contract, D6).
`FieldRegionView.site(cell)` returns the site the cell belongs to, if any.

Tests: every cell's result computed alone (fresh caches, shuffled order, 1 vs 8
threads) equals the batch; no two surviving claim sets overlap; a dropped claimant's
cells are not members of anything unless another winner claims them; the read
radius is never exceeded (a probe that throws outside it); claimed cells from the two
world dumps listed in the region dump (`claimedBy`).

### V0. Region map page — size S

Needs: R1. Owns `viewer/region.html`, `src/regionmap.ts`, `e2e/region.spec.ts`.
Port of the S4 spike page (`claude/spike-s4` `viewer/src/regionmap.ts`): biome
colours, water, cell outlines, city disks by tier, rejected reasons, water distance
heat, `claimedBy` links; gzip-aware. Playwright screenshot per world dump into
`viewer/out/region/`.

### DB. Briefs and district scenes (5.4 wiring, 5.7 mapping, 5.3 grading) — size M

Needs: P0, ST, TA. Develops against P0's sample district files; integration with DL
is D0's. Owns `DistrictScenes`, `Briefs`.

`Briefs.fill(DistrictPlan, StyleField, exact)`: per lot, style from `StyleField.pick`,
storeys from the lot's `ClassTable` range, `streets` mask from which lot sides touch
streets, `frontY` from the street's graded y at the front centre; `status` stays
`build` unless the lot is an optional fill lot (type house/workshop, not a landmark)
whose relief exceeds 8, which becomes `empty:steep`. `DistrictScenes.build(plan,
probes)`: GeneratorV1 per `build` lot with its `Brief` (catch nothing: a throwing
spec is a bug, fixed or reported), all buildings in one `SceneDoc` with a terrain
patch over the tile + 8 (with `water`), `ground` boxes from `Grading.road` for every
street and `Grading.plaza` for every plaza; `Invariants.check` on the scene plus
`SettlementChecks`. Option `exterior` drops interior boxes, rooms and hooks.

Tests: the sample district builds with 0 building violations and 0 settlement
violations; style picks on the split sample follow the biome side (> 80 % per side);
storeys within the brief range for every building; same plan twice gives
byte-identical scene JSON; mutant: a brief storey range ignored by the spec policy
fails a test. Ticks **5.4** if ST merged earlier (else ST ticks).

## Wave C

### DL. District layout: streets, blocks, lots (5.2 detail half) — size L

Needs: SK. Owns `Districts`, `DistrictPlan` building, `StreetLayout` (grid and
frontage), the DL rules in `SettlementChecks`; extends CLI `city` with
`districts/*.json`. Ticks **5.2**. Critical path.

`Districts.plan(seed, tileKey, SkeletonSource skeletons, TerrainProbe exact,
BiomeProbe biome)`: reads the skeletons of every cell whose polygon meets the tile
(cells found by a field query on the tile's 32-lattice), and only them.

1. Clip skeleton roads, landmarks and walls to the tile; arterial segments on its
   borders (half on each side, D5).
2. Plazas from landmarks (`Grading.plaza`; refused → landmark moves to the next
   tile-local spot on the 16-lattice by distance, else dropped with a reason).
3. Streets by `TierProgram.streetLayout`: **grid** (city quarters with density ≥ 50):
   hashed street lines across the tile's urban part every 44–90 blocks per axis,
   width 4–5, kept only where `Grading.road` succeeds; **frontage** (everything else):
   lanes (width 3) branching from main roads every 40–70 blocks, up to 60 long,
   `LatticePath` at stride 8 with the slope cost.
4. Lots: in grid blocks 52–100 square with density ≥ 70, one `block` lot per street
   block (all sides streets); otherwise frontage packing: walk every street edge
   (both sides, in a fixed order: streets by id, then along the polyline), place
   rectangles whose width and depth come from the type `ClassTable` picks
   (`TypeLimits.minLotWidth/minLotDepth` up to the type's largest test lot), front
   to the street, skipping overlaps with roads, plazas, lots, water and the tile
   border; relief by `LotGround` gates the type (`maxGrade`).
5. Write `fb-district/1` with streets (graded y), plazas, lots (briefs blank: DB).

Tests: all settlement invariants owned by DL, 0 failing over 500 synthetic sites x
their tiles; district plans computed in shuffled order and from a fresh source equal;
lot count per hectare by density band reported (and asserted monotone within
10 %); `golden/settlement` cases updated (regenerate). Evidence: plan views of the
sample and the two T1 windows, opened and described.

### T2. Gate terrain (fixture selection) — size S

Needs: R2 (sites shift with the water bias), T1. Owns `fixtures/terrain/` additions
and README rows, `TerrainDumpTool` finder.

A finder (JUnit, `-Dfb.find`) runs `RegionPlanner` (with JOIN if MC1 merged) over the
two world dumps and lists settled cells with tier, groups (S4's `groups`),
`straddles()`, water share. Pick and dump (step 8; step 16 above 3072²) windows
for: (1) temperate town or city (plains/forest), (2) desert or badlands, (3) taiga or
snowy, (4) jungle or savanna, (5) a settlement straddling two groups, (6) a city
larger than 512² on a river or coast, (7) a metropolis or the largest city for the
downtown (if none qualifies, a `--site` forced metropolis on the flattest temperate
land of a dump, recorded as forced), (8) a two-cell joined city if MC1 merged. Each
window is the settlement disk + 256. Commit under the size budget; README lists them.

### JT. Jetties over streets — size M

Needs: P0 and **5.12 merged** (both touch the spec policy, `InstancePassV1` front
steps, shop massing). Owns the air-rights changes in `SpecPolicyV1`,
`InstancePassV1`, `HouseMassing`/`MassingOps` jetty rules, `Invariants` `in-lot`.

`Brief.airRights` = a (0–2) on street sides only (DL/DB set it per decision 5; P0's
fixture lots get it). The envelope grows by a at the front for masses whose first
storey is ≥ 1 (and whose bottom will sit ≥ 3 above `frontY`); ground-storey masses keep
the old envelope. `in-lot` allows cells outside the lot only inside the declared
air-rights strip on the front side, at world y ≥ frontY + 3. Jetty programs may use
the strip (medieval and plains styles; others keep jetties inside). Golden v1 files
unchanged when `airRights` is 0 (no brief): show it.

Tests: a house with `airRights` 1 on a street lot jetties past the lot edge in some
seeds, never below frontY + 3, never on a side without a street; `in-lot` still fails
a jetty past a non-street side (mutant); `check --types house,shop` 0 failing.
Evidence: a district gallery frame of a medieval street with jetties, before/after.

## Wave D: after DL and DB

### D0. City pipeline end to end, city-check, presets — size M

Needs: DL, DB. Owns CLI `city` completion (`--scenes all|none|ID,..|auto:N`,
`--exterior`, `--check`), `city-check`, `city-presets/`, `scripts/gen-city.mjs`,
`e2e/city.spec.ts`, `npm run city-gallery`, `tile-border-agree` and city-level
`roads-connected`, `SkeletonSource` cache (bounded LRU, one future per key, D6),
CLAUDE.md lines.

`city-check --seeds A..B --terrain T,.. [--sites cells|random:N]`: plans every
district of each settlement, builds scenes for `auto:N` of them, runs all checks;
markdown report like `check`. Presets for the two T1 windows and the sample.

Tests: `city-check` over the T1 windows and 200 synthetic sites: 0 violations;
`tile-border-agree` mutant (shift one district's street) fails; gallery spec writes
plan PNG plus fly frames per preset (open them).

### D1. Hamlet and village programs (5.1) — size M

Needs: D0. Owns `tiers/HamletProgram`, `tiers/VillageProgram` (+ tests, presets).
Hamlet: one track (path width 2–3) through the hub along the gentlest direction, 3–8
lots by frontage, no square, houses and a workshop. Village: a main street (width 4)
through the hub across the radius, a cross street on 50 %, a green or square at the
hub with a temple (or hall on 30 %) facing it, lanes. Tests: building counts per tier
(hamlet 3–8, village 10–60) over 200 sites; every village has its landmark facing the
square; presets per tier on the T1/T2 windows.

### D2. Town program, walls and gates (5.1, 5.2 gates) — size M

Needs: D0. Owns `tiers/TownProgram`, `WallRing` (used by D3 for cities). Town: 3–5
radial main roads, a market square with a market hall (hall type, `market` kind
draw), secondary streets by frontage. Walls on 40 % of towns (60 % of cities' old
towns, D3 calls it): a closed rectilinear ring at 0.5–0.7 x radius following the
urban mask, cut into `wall`-type lots (24–64 long, 7–11 deep) clockwise; a gate where
every main road crosses, as a `wall` lot whose gate passage sits on the road; ring
corners are tower lots. Tests: the ring is closed except at gates; every main road
crossing the ring has a gate; wall lots obey every invariant; preset.

### D3. City and metropolis programs, density and skyline (5.1, 5.7) — size L

Needs: D0 (and D2 for walls; call `WallRing` after D2 merges, else leave a TODO in
the log). Owns `tiers/CityProgram`, `tiers/MetropolisProgram`, `ClassTable` tuning;
ticks **5.7**. City: arterial hierarchy (every border arterial in downtown, half
elsewhere), squares at 1 in 4 arterial crossings, grid blocks with `block` lots,
sparse high-rises where d ≥ 85, old-town walls on 60 %. Metropolis: a downtown core
(d ≥ 85 within 0.25 radius) of high-rises with storeys rising towards the centre,
parks (quarter `park`) 1 per 8 downtown tiles, an outer arterial ring. Optional
mid-rise: if `HighriseType` builds 10–19 storeys with 0 invariant failures over its
property matrix, add a `briefMinStoreys` 10 to its limits and use it at 70 ≤ d < 85.
Tests: mean storeys by density band rise monotonically; tallest building within
0.3 radius of the centre in 90 % of metropolises; no highrise below d 70; skyline
profile PNG (max height along a transect) in the log.

### MC2. Cities across cells (5.8) — size M

Needs: D0, MC1. Owns `FieldRegionView` wiring in CLI `city --cell`, multi-cell
checks, T2 window (8). Ticks **5.8**. The `city` command builds every member cell's
skeleton for a joined site; `cell-gate-agree` and `tile-border-agree` across cell
borders; a joined city on a T2 window and a synthetic 3 x 3-cell metropolis.
Tests: skeletons of the member cells built one at a time (fresh caches, each alone)
equal the batch; every gate has a road on both sides; districts straddling a cell
border plan identically whichever neighbour was built first.

### DT. Determinism suite (5.6) — size S

Needs: D0. Owns `SettlementDeterminismTest`; ticks **5.6**. For 3 presets: plan every
district (a) in order, (b) shuffled with a fresh `SkeletonSource`, (c) on 8 threads
with a cache of capacity 2 evicting constantly, (d) each district alone in a new
source; all JSON byte-identical (plans and scenes). Mutants kept as tests: a hidden
counter in `Districts`, a skeleton that reads a neighbour's plan, a `HashMap` order
dependence (all must fail). Lint covers `core/settlement`, `core/region`,
`core/terrain`, `core/style`.

### TR. Terrain response (5.3) — size M

Needs: D0. Owns quays and bridge tuning, `waterfront` quarter rules; ticks **5.3**.
Waterfront: a promenade street (width 4) along the coast or river bank inside the
waterfront quarter (graded, at water surface + 1 to + 3), lots facing the water;
bridges where main roads and arterials cross water ≤ 40 (decks, piers, ramps graded);
steep segments that `Grading` flags become stairs (`stair` role boxes, road kind
`path`) on lanes, reroutes on main roads. Evidence: river and coast presets (plan +
fly frames) and a steep-town preset; numbers: share of lots `empty:steep`, cut and
fill volumes per district, bridges per city.

### SC. Scale check (5.9) — size M, **heavy lock for timings**

Needs: D0, MC2. Owns `city-bench`; ticks **5.9**. Measures outside the game, on a
quiet machine (record `loadavg`): per district, skeleton time per cell, district plan
time, brief time, scene (generator v1) time, exact/coarse/biome probe counts, heap
retained per plan (`Runtime` before/after with a forced GC, or JFR if available),
JSON sizes; at 512², 2048², a synthetic 4096² and the multi-cell city; recorded
probes and synthetic noise terrain. **Bar:** median district plan time at 2048² and
multi-cell within 1.5 x of 512²; skeleton per cell under 50 ms; exact probes per
district plan under 1,000 (report scene probes separately: they are the building
generator's, off the lane per P2). Translate probe counts to live cost with S6's
per-probe figures; numbers to NOTES and `docs/review/phase5/scale.md`.

## Wave E

### G. Phase 5 review pack — size M

Needs: everything above (5.11, 5.12 merged if done). Owns
`docs/review/phase5/README.md`, PNG subset (≤ 8 MB), REVIEW entries.

City presets and a page per city: (1) temperate, (2) desert, (3) taiga/snowy, (4)
jungle/savanna, (5) a border straddler, (6) a city larger than 512² on water, (7) the
high-rise downtown, (8) a joined multi-cell city, plus one hamlet, one village, one
walled town. Per city: the plan view (overview and a zoomed district with lots), 3–6
fly-through frames of one chosen district (main street, square, skyline), the style
mix and building counts by type. Region maps before/after the water bias. Numbers:
`city-check` totals, the scale table, determinism results. README tells John what to
look at and asks the gate questions (does each biome mix read right; does the border
blend; does the downtown read as a downtown; would a person have built this town
here). How to open the interactive views: `npm run city-gallery` then the plan URLs.

# Parallelism and file ownership

| Step | Needs (merged) | Can run beside | Size |
|---|---|---|---|
| P0 | – | R1, T1, ST, TA, DG | M |
| R1 | (P0 preferred) | all of wave A | M |
| T1 | – | all of wave A | M |
| ST | – | all of wave A | S |
| TA | (P0 for boxes) | all of wave A | M |
| DG | – | all of wave A | S |
| SK | P0, DG, TA | V1, V2, R2, MC1, V0, DB | L |
| V1 | P0 | SK, V2, R2, MC1, V0, DB | M |
| V2 | P0 | SK, V1, R2, MC1, V0, DB | M |
| R2 | R1, T1, TA | SK, V1, V2, MC1, DB | S |
| MC1 | R1 | SK, V1, V2, R2, DB | M |
| V0 | R1 | anything | S |
| DB | P0, ST, TA | SK, V1, V2, R2, MC1, DL | M |
| DL | SK | DB, T2, JT | L |
| T2 | R2, T1 (MC1 for window 8) | DL, DB, JT | S |
| JT | P0, 5.12 | DL, T2, wave D | M |
| D0 | DL, DB | – | M |
| D1, D2, D3 | D0 (D3: D2 for walls) | each other, MC2, DT, TR | M, M, L |
| MC2 | D0, MC1 | D1–D3, DT, TR | M |
| DT | D0 | D1–D3, MC2, TR | S |
| TR | D0 | D1–D3, MC2, DT | M |
| SC | D0, MC2 | D1–D3, DT, TR (timings under the heavy lock) | M |
| G | all | – | M |

Critical path: P0 → SK → DL → D0 → D3 → G (DG and TA must land before SK; both are
small). With five coders: wave A runs P0, R1, T1, TA, DG (ST in the first free slot);
wave B starts SK as soon as P0, DG and TA merge, with V1, V2, DB, R2, MC1 beside it.

Shared files and who edits them:

- `GenRequest`, `SpecPolicyV1`: P0 (brief), then JT (air rights). 5.12 may touch the
  spec policy too: on a conflict keep both sides' logic.
- `Role.java`, viewer `ir.ts` `ROLES`/`LAYERS`, style files' palette/colours lines:
  P0 only.
- `core/settlement/*` records and JSON: P0; later steps add fields only through a
  Deviation line here and in `docs/IR.md`.
- `SettlementChecks`: each step adds its own rule methods at the end.
- `Invariants.java`: JT only (`in-lot`); 5.11/5.12 may also edit it.
- `ClassTable`: P0, then D3 only.
- `Tiers`: R1, then R2 (water constants) and MC1 (priority); one block each.
- CLI `CityCommand`: SK → DL → D0 → MC2 in sequence; `RegionCommand`: R1 → R2 → MC1.
- `viewer/**`: V0, V1, V2 own their new files; `render.ts` is V2's; D0 adds
  `city-presets/`, `scripts/gen-city.mjs`, `e2e/city.spec.ts`, the npm script.
- `fixtures/terrain/`: T1, T2 (README rows appended).
- Goldens (`golden/settlement`, `golden/region`, `golden/v1`): generated, never
  hand-merged.
- ROADMAP: owners tick 5.2 DL, 5.3 TR, 5.4 ST/DB, 5.5 later of V1/V2, 5.6 DT, 5.7 D3,
  5.8 MC2, 5.9 SC, 5.10 R2; 5.1 is ticked by the coordinator when D1–D3 have merged.

# Interaction with 5.11 and 5.12

- Neither blocks any step here. Both change building output and `golden/v1`; the
  settlement goldens hash plans only, so they do not churn.
- 5.12 (open ground storeys, shopfronts at street level) benefits from
  `Brief.frontY` (the street surface in front of the lot), which P0 adds and DB
  fills. Tell the 5.12 coder it exists once P0 merges; until then it can only use
  the lot's own ground.
- JT waits for 5.12 (same files: spec policy, front steps, shop massing).
- If 5.11/5.12 merge before G, the review pack's district frames show them; G says so.

# Risks and open questions

Each with the assumption made if nobody answers.

- **Exact probe cost in game.** Buildings read every lot column (spec height cap,
  foundations, steps). District plans are kept sparse (target under 1,000 exact
  probes), but scenes will be tens of thousands per district. P2 moves IR off the
  lane; SC reports both counts. Assumed acceptable for Phase 5; Phase 6 (6.7) may
  need the coarse-plus-margin height cap from the Phase 4 risks.
- **Recorded terrain is smoother than the game.** Fine dumps are on an 8-block
  lattice, interpolated; small cliffs and narrow streams vanish. Assumed fine for
  planning; Phase 6 in-game tests catch the rest.
- **Coarse screen bias in game** (+16 mean, ADR 0003 D4) is modelled only by the ±20
  test probe. Assumed: the skeleton never sets heights, so bias only moves mask
  edges.
- **Big lots vs tile size.** The largest lot is about 180 blocks (tile minus
  arterials); `hall-big` (200 x 200) cannot occur in a city. Assumed fine (decision 1).
- **3D district size in the browser.** A downtown district may be 1–2 M voxels and
  30–60 MB of JSON. V2 meshes exposed faces and offers `?exterior=1`; D0's `--exterior`
  shrinks the JSON. If still too slow, the review pack uses fewer districts.
- **Mid-rise gap.** `highrise` starts at 20 storeys; 6–19-storey buildings exist only
  if D3's optional step works. Assumed acceptable for the gate.
- **Tier weights and radius bands** are guesses tuned to S4's cell counts; R1 reports
  the resulting settlement counts per window; John may retune at the gate.
- **Fixture size.** Gzipped dumps total about 4 MB; if the coordinator objects,
  commit only the windows the review pack uses.

# Deviations

(Steps add one line each when they change a contract above.)
