# Plan: Phase 6, Minecraft integration and release

Status: proposed (2026-10-22). Author: lead-coder. Items: ROADMAP "Phase 6" 6.1–6.8 and
the mod half of 6.10. Not covered: 6.9 railings (coded separately now) and 6.10's planner
half (waits for ADR 0004).
Decisions this plan relies on: [ADR 0003](../adr/0003-worldgen.md) (W1 district starts,
W2 per-city suitability, W3 references, W4 step, W5 terrain, W7 veto, P1 two-level plan,
P2 prefetch, R1/R2 region field and joins, D1–D7 determinism), the Phase 5 plan
([phase5.md](phase5.md): district tiles, `CityPlanner`, `FieldRegionView`), the spikes
[S3](../research/spikes/s3.md), [S5](../research/spikes/s5.md),
[S6](../research/spikes/s6.md), [S8](../research/spikes/s8.md),
[S1](../research/spikes/s1.md), [S2](../research/spikes/s2.md) and
[S10 mod half](../research/spikes/s10-mod.md), and the roads plan
[intercity-roads.md](intercity-roads.md).

**Gate:** a fresh world on three seeds plays well for an hour; then a first public release.

# Decisions for John (work proceeds on the default)

Each has a default that the steps implement. Changing one later costs the steps named.

1. **Exact heights in the game, not the coarse screen.** Every Phase 5 rule was tuned
   and reviewed on recorded dumps of *exact* heights (`getBaseColumn`), including the
   region decision and the 32-block skeleton lattice. ADR 0003 W2 says suitability
   uses the coarse screen (sampled `chunk_surface_level`, 11 ms a decision, but
   off by 12 to 16 blocks on average and by 50 to 150 under Tectonic and Terralith, S2).
   Using it in the game would make the game's cities differ from the reviewed ones,
   with no way to compare them. **Default:** the game uses the dumps' exact definition
   everywhere, made affordable by a per-world column memo, a per-chunk batched probe
   for district reads, and background warming (steps A1, B4). The coarse screen is the
   fallback for the region layer only, if A1's or B8's numbers miss the budget.
   This changes W2's wording, so it needs an amendment to ADR 0003 at the gate.
   *Cost of the fallback:* A1, B4; region decisions then no longer match the viewer.
2. **Villagers.** **Default:** beds and job-site blocks in every settlement (from the
   planner's hooks), villagers spawned only in hamlets and villages (one per bed, at
   most 10 per district), none in towns and larger in this release (entity count and
   lag). Steps: B5.
3. **No monster spawns inside buildings.** **Default:** the building structures set
   `spawn_overrides` for `monster` with `"bounding_box": "piece"` and no spawns, so
   houses don't fill with zombies at night (vanilla villages don't do this; ocean
   monuments do). Streets stay as dangerous as the countryside. Steps: B1.
4. **Vanilla villages.** **Default:** kept everywhere except inside our settlements
   plus a margin (W7, S8's veto). Steps: B7.
5. **Config is pinned per world.** **Default:** `config/fractalbuildings.json` gives
   the defaults for *new* worlds; the values are copied into the world's saved data at
   creation and win from then on, so editing the file never puts seams into an
   existing world. `/fb config` prints the world's values. Steps: B6.
6. **Mod updates mid-world.** A new generator version changes what not-yet-generated
   chunks get, so a city half-built before an update can show a seam (ADR 0003
   Consequences). **Default:** accepted and said in the README; pieces save their
   generator version and the log warns on a mismatch; no old-generator support.
7. **Release.** **Default:** Modrinth first (CurseForge later), version
   `0.1.0+26.3`, release channel `alpha`, MIT, Fabric API required, John publishes
   from his account. We prepare the jar, the page text, the screenshots and the
   changelog (step C3). **Publishing, the project name on the page, and whether the
   source repository goes public are John's calls.**
8. **Server-only install.** Our registries (structure type, piece type, placement
   type, feature type) are not synced to clients in vanilla, so a vanilla client can
   probably join a server running the mod (*inferred*, not tested: we have no vanilla
   client in the dev shell and a dedicated server needs the EULA, which is John's).
   **Default:** the README says "install on the server or in singleplayer; clients
   joining a server may not need it (untested)" until John tries it.
9. **Terralith and Tectonic in tests.** The Stardust Labs licence forbids using their
   work to test or augment AI systems (ASSUMPTIONS, Phase 1). S2 and S7 already ran
   their jars as black boxes in game tests. **Default:** 6.6 does the same (run the
   jars, never read their code or data). If John objects, 6.6 tests Lithostitched and
   TerraBlender only and the README lists Terralith and Tectonic as untested.
10. **Scope cuts for the first release.** **Default:** deferred to after the release:
    per-wing ground levels for big buildings on slopes (6.3's planner half; ASSUMPTIONS
    4.1 row), density-function flattening (W6), the published
    `fractalbuildings:settlement` density function (W9), and 6.10 unless ADR 0004 and
    the planner half are done before C3 starts (the ROADMAP already allows it to move
    to Phase 7).

# Goal

Turn the proven planner into worldgen: a fresh world grows hamlets to metropolises in
any chunk order, with buildings you can walk into, beds and loot, `/locate` support,
acceptable chunk-generation cost, and a jar John can publish. Phase 5 left the core
able to plan any settlement from probes; the mod today only proves it can place a
3×3 hut.

# What exists and what is missing

**Exists (on `claude/run-phase6` at 8995637):**

- `mod/`: `FractalBuildingsMod` (logs the core version), `HelloHut`, one server and one
  client game test (`HelloGameTest`, `HelloClientGameTest`), and the mod JUnit layer
  (`mod/src/test`, `TerrainDumpTool`: bootstraps vanilla worldgen without a server and
  reads `getBaseColumn`). planner-core is bundled jar-in-jar. Nothing registers a
  structure, placement, piece or feature.
- **Palettes are already block ids**, in the core: each style JSON
  (`planner-core/src/main/resources/styles/*.json`) maps every `Role` (foundation,
  floor, wall, trim, beam, fence, window, door, roof, stair, road, paving, ground,
  deck) to a list of `minecraft:` ids; the viewer picks a list entry with
  `cellHash(x, y, z) % n` (`viewer/src/styles.ts` `styleColour`). So "district roles
  not mapped in the mod" means only that no mod code reads them yet.
- **The core plans everything the mod needs**, as pure functions of probes:
  `RegionPlanner` + `FieldRegionView` (cells, join rule), `FieldSiteView`,
  `SkeletonSource.Direct` (skeletons, road network), `DistrictGrid` (world-wide tiles,
  8 to 12 chunks, start chunk at the centre: ADR 0003's 14 × 14 limit holds),
  `Districts.plan` + `Briefs.fill` (streets, plazas, lots with briefs),
  `GeneratorV1.generate(GenRequest)` (one placed building per lot, with foundations and
  front steps), `Grading.road` / `Grading.plaza` (ground boxes), `CityPlanner` (the
  whole pipeline for the CLI). Memos are `BoundedCache`s (thread-safe, may compute a
  value twice on a race) and `CachedIrSource` (one future per key).
- Spike branches with working 26.3 code to copy shapes from (never merged, read them
  for API use): `claude/spike-s3` (placement subclass, structure, reference pieces,
  ledger, order test), `claude/spike-s5` (beard variants, site metrics), `claude/spike-s6`
  (prefetch caches, lane counters, movement simulation, save-size sweep),
  `claude/spike-s8` (veto mixins), `claude/spike-s9` (`Structure.generate` in a server
  game test), `claude/spike-s10-mod` (per-chunk feature, claim ledger).

**Missing (this plan):** live probes; a per-world, thread-safe planner façade;
turning a placed building into blocks (roles to block states with shapes: doors,
panes, fences, stairs, logs); registration (placement, structures, pieces, sets,
tags); terrain behaviour in a real world; prefetch and region warming; hooks to beds,
workstations, chests and lights; config; the veto and compat; performance numbers and
their CI check; fly-through screenshots; the review pack; the release files (there is
no `LICENSE` file; the README is the agents' README).

**Phase 5 issues that land here** (ROADMAP Phase 5, "Open known issues"):

- *The probe bar* (5.9: 4.9k to 8.9k exact probes per district against a bar of
  1,000). In the game a probe is 0.15 to 0.38 ms (S6), so a cold district plan is 0.7
  to 3.4 s on the serial lane if every probe is a `getBaseHeight`. Answered by A1
  (cheaper exact probes), B4 (prefetch and warming) and, only if those miss the budget,
  the conditional planner step A6. The bar becomes a *time* budget (below).
- *Roles `deck`, `paving`, `road`, `ground` not mapped*: A3/A4 map every role.
- *`SkeletonSource` and `FieldRegionView` per world and thread-safe*: A2.

# How a city larger than one start gets into the world

This is ADR 0003 W1, P1, R1, R2 and D5 made concrete; nothing here reopens them.

- **Units.** The region field (1024-block hashed Voronoi cells, best of 8 centres,
  joins) decides where settlements are and how big; a settlement spans any number of
  cells. The world is cut into **district tiles** by `DistrictGrid` (hashed lines every
  10 ± 1 chunks, the same in every world area). **One tile = one structure start**,
  at the tile's centre chunk. A city is simply all the tiles its settlement owns
  (`Districts.owns`): there is no city-sized object in the world at all, so there is no
  city size limit from the structure system.
- **Placement** (`DistrictPlacement extends RandomSpreadStructurePlacement`, spacing 10
  = `DistrictGrid.PERIOD`): `isPlacementChunk` is "is this the centre chunk of its tile"
  (pure geometry, no probes, as S3's 3–36 ns test); `getPotentialStructureChunk(seed,
  x, z)` returns the centre chunk of tile `(floorDiv(x, 10), floorDiv(z, 10))`. Checked:
  sector chunk `10k + c` always lies in tile `k` for the middle `c`, because line `k`
  is at `10k + o`, `o ∈ [-1, 1]`; so `/locate`'s sector walk visits every tile once.
- **The region layer in the mod** is one `WorldPlanner` per world (A2), created from
  the world seed, the pinned config and live probes, and found from any thread through
  `WorldPlanners.of(GenerationContext)` (keyed by the level's `RandomState`, a weak map).
  `findGenerationPoint` asks it "which settlement owns this tile, and what tier"; a
  tile no settlement owns returns `Optional.empty()` at the cost of a cached cell
  lookup. That also serves `/locate` (`StructureCheck` calls `findGenerationPoint`
  without the placement, so it must repeat the centre-chunk test, S3).
- **Suitability is per city, never per start** (W2, S3's "rejected district leaves a
  hole"): the structures' `biomes` is every overworld biome (`#minecraft:is_overworld`,
  oceans included), and the planner alone decides. Biome preferences for packs go
  through tags the live biome probe reads (6.2, B1).
- **Two structure kinds per tile, because of the beard.** `beard_thin` takes its ground
  plane from each piece box's `minY` and ignores `maxY` (checked:
  `Beardifier.sampleValueUnchecked`, `BEARD_THIN -> dyToGround`, ground =
  `box.minY() + groundLevelDelta`, kernel ±12; decompiled 26.3
  `net/minecraft/world/level/levelgen/Beardifier.java` lines 140–176). One terrain
  adaptation applies to every piece of a start, and a sloping street piece would have
  the terrain around it flattened to its lowest point. So:
  - **building structures** (`terrain_adaptation: beard_thin`), one rigid piece per
    building, box = the lot (plus its air-rights strip), box `minY` = the building's
    slab level `oy` (S5, W5). One structure per tier (`fractalbuildings:hamlet` …
    `metropolis`, same type, a `tier` field) in **one** structure set: a multi-entry
    set tries its entries in a seeded order until one generates and skips the chunk if
    any entry already has a start (checked: `ChunkGenerator.createStructures`, lines
    ~500–565), and exactly one tier matches a tile, so the result is deterministic.
    This gives `/locate structure fractalbuildings:city` and the tag
    `#fractalbuildings:settlement` for free.
  - a **ground structure** `fractalbuildings:district_ground` (`terrain_adaptation:
    none`, its own structure set with the same placement), pieces for the tile's
    streets, plazas, quays, bridges, cut and fill (the scene's `ground` boxes), clipped
    per chunk-sized piece.
  - **Ownership** (D5): the core resolves the scene into disjoint cell sets, one per
    piece (A3), so the two structures never write the same block and their order in
    the decoration step does not matter. Every piece clips to its tile and to
    `chunkBB`; tiles partition the plane.
- **Pieces save references and their brief, never IR** (W3): tile `(ix, iz)`, lot
  index, slab level, generator version and the lot brief (about 40–60 B), so a start
  reloaded from disk rebuilds its buildings without re-planning the district.
- **Per-chunk features versus pieces.** A feature (S10) has no reach limit and no
  start, and would also avoid the beard question for streets. It is kept for the
  intercity roads (6.10, C1), where nothing needs `/locate`, boxes or saving. Districts
  stay pieces: `/locate`, the beard for buildings, saved starts, spawn overrides and
  bounding boxes for the veto all come with them, and W1/W5 decided it. A tile's
  ground could move to a feature later without touching the core (the cell sets are
  the same).
- **The serial lane** (S6): start creation and `postProcess` run on one lane per
  dimension. Start creation needs the region decision, the settlement's skeletons, the
  district plan and every building's slab level; `postProcess` needs the buildings.
  Region and skeletons are warmed in the background around players (B4), a start
  submits its own buildings and its neighbour tiles' plans to at most 4 workers (P2),
  and the lane joins a future or computes inline (D6). Teleporting into a metropolis
  has no lead and pays the cold cost (accepted; measured in B8).

# Budgets (what "acceptable cost" means)

Proposed numbers for 6.7, from ADR 0003's measured limits; quiet machine, vanilla
terrain, loadavg recorded. B8 asserts them leniently in CI and records the real values.

| What | Budget | Source |
|---|---|---|
| Start creation on the lane, cold (plan + briefs + slab levels), city tile | median ≤ 300 ms, p95 ≤ 1 s | S6 first-touch lead: 0.9 s at elytra |
| Same, warm (neighbour prefetched) | median ≤ 20 ms | S6 two-level 60–79 ms for the first start |
| Region resolve, cold, on the lane | ≤ 1 s once per area; 0 when warmed | MC1 note: 17k–28k lattice columns |
| Building lookups that run inline at elytra speed | ≤ 5 % | P2 pool rule |
| Wall time per chunk in a city at sprint speed | ≤ 1.5 × the same area without the mod | S6 (cold downtown 54 ms vs 26) |
| Block writes per downtown chunk | ≤ 5 ms | S6: 100 ns a block |
| Start NBT per district | ≤ 64 KB | W3, S6: 13 B a reference |
| Region-file growth of a city area | ≤ +10 % | S6: +0.9 to +9 % |
| Heap growth after flying 4 km through a metropolis | ≤ 500 MB, flat after | S6: 140 MB per 64 downtowns |
| `/locate structure #fractalbuildings:settlement` from spawn | ≤ 10 s cold | S6: 2.5–25 s exact |

# Core API this plan adds (planner-core, pure Java)

Owned by A2 and A3. Everything below is a pure function of (seed, config, probes) with
memos that only memoise (D6).

```java
package dev.fractalbuildings.core.world;

/** Settlement planning for one world: thread-safe, bounded, order-independent. */
public final class WorldPlanner {
  public WorldPlanner(long seed, WorldConfig config, WorldProbes probes, Executor workers);
  public DistrictGrid grid();
  public boolean isStartChunk(int chunkX, int chunkZ);          // no probes
  public Optional<TileInfo> tile(int ix, int iz);              // owner settlement, tier; region resolve
  public TilePlan plan(int ix, int iz);                        // one future per tile; Districts.plan + Briefs.fill
  public List<BuildingPiece> buildings(int ix, int iz);        // lot index, brief, box (minY = slab level)
  public List<GroundPiece> ground(int ix, int iz);             // chunk-sized ground pieces of the tile
  public void raster(PieceRef piece, Box3 clip, CellSink out); // owned cells only, with shapes
  public void prefetch(int ix, int iz);                        // buildings of this tile, plans of its neighbours
  public void warm(int blockX, int blockZ, int radius);        // region + skeletons around a point
  public Optional<SettlementInfo> nearest(int x, int z, Tier min, int maxCells); // /fb locate
}
public record WorldConfig(int cellSize, double frequency, int maxRadius, Set<String> styles, int genVersion) {}
public record WorldProbes(RegionProbe region, CoarseProbe coarse, TerrainProbe exact, BiomeProbe biome) {}
public interface CellSink { void cell(int x, int y, int z, Role role, String blockId, Shape shape); }
```

`Shape` (A3) is a small record: `facing` (nullable), `half` (lower/upper for doors,
bottom/top for stairs and slabs), `hinge`, `axis` (x/y/z for logs and beams), and a
4-bit `connect` mask (N, E, S, W) for panes, fences and walls. `Shape.NONE` for plain
blocks. The mod maps (`blockId`, `Shape`) to a `BlockState` (A4).

# Steps

Sizes: S (a day for a coder), M, L. **Heavy** = needs the heavy lock for a client game
test or a timing. Waves are a guide; each step names exactly what it waits for.

## Wave A: start at once, side by side (no client tests)

### A1. Live probes and their cost (6.5/6.7 groundwork) — size M, heavy lock for timings only

Files: `mod/src/main/java/dev/fractalbuildings/mod/probe/` (`LiveTerrainProbe`,
`LiveBiomeProbe`, `LiveRegionProbe`, `LiveCoarseProbe`, `ColumnMemo`),
`mod/src/test/.../probe/LiveProbeTest.java`, a mixin config
`fractalbuildings.mixins.json` (A1 creates it; B7 adds to it).

- Heights use the dump definition exactly (`TerrainDumpTool.surfaceAndFloor`:
  surface = first non-air from the top + 1, floor = first non-air non-fluid + 1, from
  `ChunkGenerator.getBaseColumn`), so `floorY` = floor, `waterDepth` = surface − floor.
  Move that function to main code (`LiveTerrainProbe.column`) and make the dump tool
  call it, so the two can never drift.
- Biomes: `MultiNoiseBiomeSource.getNoiseBiome` after the fixed priming lookup (D3,
  S1, the dump tool's `PRIME`), at the surface quart, returned as exactly the string
  `RecordedProbe.biome` gives for the same column of a dump (the dump writes vanilla
  ids bare; check how `RecordedProbe` fills `biomeNames`). TerraBlender's per-region
  anchor is B7's.
- `ColumnMemo`: a per-world `BoundedCache<Long, int[]>` keyed by chunk, holding the 16 ×
  16 floor and water values of a chunk; a district read touches each chunk once. Pure:
  eviction changes speed only.
- Measure, in `:mod:test` (bootstrapped generator, no server), on seed 987654321 over
  the `gate-temperate-city` window, three ways to fill one chunk of the memo:
  (a) 256 × `getBaseColumn`;
  (b) **batched**: one `NoiseChunk` over a 16 × H × 16 `DensityVolume`
  (`new NoiseChunk(randomState, null, settings, fluidPicker, Blender.empty(), volume)`
  then `cachingSamplers().get(finalDensity).sampleVolume(volume)` and the aquifer per
  block, as `NoiseBasedChunkGenerator.iterateNoiseColumn` does for one column, lines
  164–205; the constructor is public; `globalFluidPicker` is a private field, so add an
  `@Accessor`/`@Invoker` mixin for it or for `createFluidPicker`);
  (c) **windowed**: the batched volume restricted to a cell-aligned y window around the
  sampled `chunk_surface_level`, falling back to (b) when the window does not bracket
  the surface.
  Also time the sampled surface level alone (the coarse screen) and one region-lattice
  column (64-stride `getBaseColumn`, no batching possible).
- **Pass:** (b) and (c) equal (a) on 100 % of columns in the window (any mismatch
  means they are not exact; drop that method). Parity: `LiveTerrainProbe` and
  `LiveBiomeProbe` reproduce `fixtures/terrain/world-12345-s64.json.gz` (a 64 × 64
  sub-grid is enough) and `w12345-river-s8.json.gz` exactly (100 % of columns; this is
  what makes the game's region decisions equal the viewer's).
- **Evidence:** the parity test; a timing table per method (ms per chunk, µs per
  column, loadavg) in the log and one NOTES line; a recommendation for B4/B8: which
  method `ColumnMemo` uses. Mutation check: drop the priming lookup → the biome parity
  test fails on some tie column (S1 found 2–15 per 16k).
- If even the best method makes a city tile's plan cost more than about 300 ms cold,
  say so in the report: the coordinator then schedules A6.

### A2. `WorldPlanner`: the per-world planner façade — size L

Files: `planner-core/src/main/java/dev/fractalbuildings/core/world/` (new package),
tests in `planner-core/src/test/.../world/`.

**First commit within the first hour:** the public API above (`WorldPlanner`,
`WorldConfig`, `WorldProbes`, `TileInfo`, `TilePlan`, `BuildingPiece`, `GroundPiece`,
`PieceRef`, `CellSink`) with bodies throwing `UnsupportedOperationException`, plus
`FixtureWorldPlanner` (test helper in `planner-core/src/testFixtures` or a `test`
class B1 can copy) that serves one tile from a committed district fixture
(`viewer/fixtures/settlement/districts/`). B1 and B5 start against it.

- One `FieldRegionView` per planner (built from `WorldConfig`); one
  `SkeletonSource.Direct` per settlement id in a `BoundedCache<String, SkeletonSource>`
  (capacity 64), so every tile of a settlement shares its skeletons and memoised
  `network()` (NOTES ROADS: a source serving many tiles must memoise it). Tile ownership
  through `Districts.owns`.
- `plan(ix, iz)`: one `CompletableFuture` per tile in a bounded map (the
  `CachedIrSource` pattern: no build inside `computeIfAbsent`, the caller runs the task
  if no worker has started it). Runs `Districts.plan`, then `Briefs.fill` with the
  settlement's `QuarterField::at`, exactly as `CityPlanner.plan` does (call the same
  code, don't copy it: extract a `CityPlanner.planTile(...)` if needed).
- `buildings(ix, iz)`: per build lot, `GeneratorV1.generate(GenRequest(seed, lot,
  type, style, exact, biome, brief))` with the fallback rule of `DistrictScenes.build`
  (refused type → 1–2 storey house, refused again → empty), shared, not copied (extract
  `DistrictScenes.generateLot`). Each `BuildingPiece` carries the lot index, the brief,
  the box (lot rect plus air strip, clipped to the tile; `minY` = `PlacedBuilding.oy`,
  `maxY` = top of the shell) and the `PlacedBuilding` (cached per tile, evictable; a
  piece can rebuild it from its brief alone).
- `ground(ix, iz)`: the ground boxes of every street and plaza of the tile
  (`Grading.road` / `Grading.plaza` as `DistrictScenes.build` does; extract
  `DistrictScenes.ground(plan, exact)`), clipped to the tile, grouped into one piece
  per chunk that holds any.
- **No `TerrainPatch` and no invariant run in the game path** (both read the whole tile
  plus margin: 43k columns). Tests run the invariants instead (below).
- `WorldConfig.styles`: a disabled style is replaced by `plain` in `Briefs.fill`'s
  style pick (B6 adds the option; A2 threads it through and tests it).
- `prefetch`, `warm` and `nearest` as in the API; the executor is supplied (tests pass
  a direct executor or a 4-thread pool).

**Tests (all on recorded dumps, no Minecraft):**
- `WorldPlannerParityTest`: for `gate-temperate-city` (`RecordedProbe` on the gate
  window) every tile's `plan` equals the `fb-district/1` text `CityPlanner` gives for
  the same site, and the buildings equal the scene's buildings (`DistrictScenes.build`
  with `exterior=false`), byte for byte through `IrJson`.
- `WorldPlannerOrderTest` (DT-style): plan + buildings + ground of 30 tiles of
  `gate-metropolis-mixed` in forward, reverse, shuffled and 8-thread orders, with cache
  capacity 1 to 3 and an evictor thread; identical bytes. Mutants (kept as switches, as
  S3's `S3_MUTATE`): a shared counter in the brief seed, an unsynchronised `TreeMap`
  skeleton memo shared by threads.
- `WorldPlannerInvariantTest`: the union of a tile's buildings and ground passes
  `Invariants.check` (as the scene would) on 9 gate windows (sampled tiles).
- No building box outside its tile (catches air rights crossing a tile border; if it
  fires, clip the air strip in `Briefs.fill` and record a deviation).
- `SkeletonSource`/`FieldRegionView` per world: two `WorldPlanner`s with different
  seeds on one thread pool never share a cache entry (a test that interleaves them).

### A3. Cell raster, ownership and block shapes — size M

Files: `planner-core/src/main/java/dev/fractalbuildings/core/raster/` (`PieceRaster`,
`Shape`, `Shapes`, `Variant`), tests beside them. Needs only existing types
(`PlacedBuilding`, `Box`, `Opening`, `SceneDoc`), so it runs beside A2.

- `PieceRaster.building(PlacedBuilding b, Box3 clip, CellSink out)`: apply the shell's
  boxes, then its site boxes, in list order into a grid over `clip` grown by 1 block on
  each side (`AIR` clears, a later box wins: `Box` javadoc), transform with
  `Placement`, emit only cells inside `clip`. The 1-block border gives each cell its
  neighbours across a chunk border without reading the world (connections must never
  depend on what a neighbour chunk has placed: that is order-dependent).
- `PieceRaster.ground(List<Box> boxes, Box3 clip, CellSink out)`, same rules.
- **Ownership:** match the viewer's composition. Read `viewer/src/voxels.ts`
  (`sceneVoxels`) for which wins where a ground box and a building overlap, write the
  rule in `docs/IR.md`, and implement it as "a ground cell is not emitted where a
  building of the same tile has a cell". Test: for 3 gate tiles, the union of all
  piece rasters (buildings + ground) equals the scene's voxels (the same cells and
  roles as the viewer would draw), and no cell is emitted twice.
- **Variant:** `Variant.pick(style, role, x, y, z)` = the palette list entry at
  `cellHash(x, y, z) % n`, a Java port of the viewer's `cellHash`. Test with a fixture
  of 50 (x, y, z, hash) rows generated from the TS function (add a tiny vitest that
  writes it, or hand-copy from a node one-liner) so the game picks the same block the
  viewer shows.
- **Shapes** (`Shapes.of(grid, x, y, z)`):
  - `DOOR`: lower and upper half by the opening's 2-high box; `facing` = the opening's
    `facing` in world terms (`Placement.toWorld(Facing)`); hinge left for single doors,
    left/right pairs for 2-wide doors.
  - `WINDOW` with a pane block, and `FENCE`: `connect` = neighbours that are solid
    roles, `WINDOW` or `FENCE`.
  - `BEAM` and `TRIM` with a log block: `axis` = the long axis of the box that made the
    cell (vertical posts y; rails and bressumers x or z).
  - `STAIR`: a stair block facing the adjacent cell one step higher (the next step of
    a flight, or the door or floor it climbs to); `half` bottom. Interior flights:
    `Opening` kind stair carries the ascent `facing`. A step with no higher neighbour
    stays a full block. (The style palettes say `oak_planks` for `stair`: A4 maps a
    stair-capable cell to the material's stair block, e.g. `oak_planks` →
    `oak_stairs`, through a table; no palette change.)
  - everything else `Shape.NONE`.
- Tests: per shape a small hand-built `BuildingIr` with the expected `Shape`; a door
  in every rotation; a pane row crossing a clip border gets the same `connect` from
  either side (the order-independence case); a flight of front steps all facing up
  the flight. Mutation: compute `connect` from the clip only (no border) → the
  cross-border test fails.

### A4. Block mapping (6.1 palette half) — size S–M

Files: `mod/src/main/java/dev/fractalbuildings/mod/block/` (`BlockMapper`,
`ShapeTable`), `mod/src/test/.../block/BlockMapperTest.java` (bootstrapped registries,
as the dump tool). Needs A3's `Shape` record; until A3 commits it, write against the
record as specified above (same field names) and rebase.

- `BlockMapper.state(String blockId, Shape shape)`: `BuiltInRegistries.BLOCK` lookup by
  `Identifier`, then properties: `DoorBlock.HALF`/`FACING`/`HINGE`, `CrossCollisionBlock`
  N/E/S/W for panes and fences (and `WallBlock`'s side properties for walls),
  `RotatedPillarBlock.AXIS` for logs, `StairBlock.FACING`/`HALF`, slab `TYPE`. Cache
  per (id, shape) in a concurrent map (the result is a pure function).
- `ShapeTable`: full block → stair and slab variant (`oak_planks` → `oak_stairs`,
  `oak_slab`; `cobblestone` → `cobblestone_stairs`, ...); covers every block id any
  style uses for `stair` and `roof` (and the lintel slabs John asked for in 5.11, used
  by a later shape pass). Unknown → keep the full block, log once.
- Unknown block id (a typo, a block removed in a later version) → `minecraft:stone`
  with one warning per id, never a crash.
- **Tests:** every id in every style palette (iterate `styles/index.txt`) resolves to
  a non-air block; every (id, shape) the shapes can produce for that role yields a state
  whose properties match (`state.getValue(DoorBlock.HALF)` etc.); `ShapeTable` covers
  every `stair` and `roof` id. Mutation: break one palette id in a temp style → the
  test names it.

### A5. Release basics — size S (assistant or coder; docs and build metadata)

Files: `LICENSE` (MIT, "Copyright (c) 2026 John Reeves", as fabric.mod.json and
ASSUMPTIONS 0.1a), `mod/build.gradle` (put `LICENSE` in the jar; version
`${version}+${minecraft_version}` for the jar name), `gradle.properties` if needed,
`mod/src/main/resources/fabric.mod.json` (`contact`, `icon`, `suggests` nothing),
`mod/src/main/resources/assets/fractalbuildings/icon.png` (placeholder 128 × 128,
replaced by C3 from a screenshot), `CHANGELOG.md`, and a player README draft at
`docs/release/README-player.md` (what it does, install, compat, known issues, how to
report; C3 moves it to `README.md` and the agent README to `docs/AGENTS.md`).

- **Evidence:** `nix develop -c ./gradlew :mod:build`, then `jar tf` on the jar shows
  `LICENSE`, `fabric.mod.json` with the expanded version, the nested planner-core jar
  under `META-INF/jars/`, the icon; the jar name is `fractalbuildings-0.1.0+26.3.jar`.
- Build-file changes are listed in the report (CLAUDE.md).

### A6. (Conditional) Cut district probes — size M, only if A1 or B8 says so

Planner change in `Districts`/`Briefs`/`Grading`: read heights on a 4-block lattice
with bilinear interpolation for street grading and lot relief, exact only at building
footprints. Changes every district fixture, golden and the gate windows' plans:
regenerate them, run `city-check` on the 9 gate windows (0 new violations), and put
before/after plan views in the log. Not scheduled unless A1's best method misses the
start-creation budget.

## Wave B: after Wave A (each names its inputs)

### B1. Registration, placement, structures, pieces (6.1 pieces half, 6.2) — size L

Needs: A2's API commit (and `FixtureWorldPlanner`), A3, A4. Files:
`mod/src/main/java/dev/fractalbuildings/mod/worldgen/` (`DistrictPlacement`,
`DistrictStructure`, `GroundStructure`, `BuildingPiece`, `GroundPiece`,
`WorldPlanners`, `ModWorldgen.register()`), data under
`mod/src/main/resources/data/fractalbuildings/` (`worldgen/structure/{hamlet,village,
town,city,metropolis,district_ground}.json`, `worldgen/structure_set/{districts,
district_ground}.json`, `tags/worldgen/structure/settlement.json`,
`tags/worldgen/biome/no_settlements.json`), server game tests in
`mod/src/gametest/java/dev/fractalbuildings/gametest/worldgen/` (register them in the
gametest `fabric.mod.json`, or they silently don't run).

- Registration as S3 did it (`Registry.register` into `BuiltInRegistries.STRUCTURE_TYPE`,
  `STRUCTURE_PIECE` with a `StructurePieceType.ContextlessType`, `STRUCTURE_PLACEMENT`
  with a `MapCodec`). Placement JSON: `{"type": "fractalbuildings:districts", "salt":
  …, "spacing": 10, "separation": 1}` (the codec requires spacing > separation:
  `RandomSpreadStructurePlacement` lines 15–26).
- Structure JSON: `"biomes": "#minecraft:is_overworld"`, `"step":
  "top_layer_modification"` (W4), building tiers `"terrain_adaptation": "beard_thin"`,
  ground `"none"`, building tiers `"spawn_overrides": {"monster": {"bounding_box":
  "piece", "spawns": []}}` (decision 3; codec checked:
  `StructureSpawnOverride` fields `bounding_box` (`piece`/`full`) and `spawns`).
- `WorldPlanners.of(GenerationContext)`: a weak map from `RandomState` to
  `WorldPlanner`, created on first use from `ctx.seed()`, the world's pinned config
  (B6; until then `WorldConfig.defaults()`), live probes from
  `ctx.chunkGenerator()`/`ctx.randomState()`/`ctx.heightAccessor()`, and the shared
  executor (B4; until then a direct executor). A test hook
  `WorldPlanners.overrideForTests(Function<GenerationContext, WorldPlanner>)`.
- `DistrictStructure.findGenerationPoint`: repeat the centre-chunk test, ask
  `tile(ix, iz)`, return empty unless the tile's tier is this structure's; the stub's
  builder adds one `BuildingPiece` per `buildings(ix, iz)` entry (box from the core).
  `GroundStructure` likewise with `ground(ix, iz)`.
- `BuildingPiece.postProcess`: ignore the `random` argument (D1); `raster` the piece
  clipped to `chunkBB` ∩ tile, `BlockMapper.state`, `level.setBlock(pos, state, 2)`.
  Save: `IntArray` (tile ix, iz, lot index, slab y, gen version) + the brief's fields;
  load rebuilds from the brief through `WorldPlanner`.
- A tiny ledger hook (static, off by default, as S3's `S3Ledger`): records (pos, state
  id) per write and the claimed air volume (S10's claim semantics), for B2 and the tests
  here.
- **Server game tests (flat world, cheap, no heavy lock):** the game-test world is
  flat, seed 0, structures off (NOTES), so generate by hand as S9 did
  (`holder.value().generate(holder, level.dimension(), level.registryAccess(), gen,
  gen.getBiomeSource(), randomState, ...)`) for a tile of a flat-plains settlement
  (flat terrain settles: MC1's flat-plains runs), then `StructureStart.placeInChunk`
  per chunk in ascending, reversed and shuffled order: identical ledger digests; a
  start saved with `createTag` and loaded with `loadStaticStart` places the same
  blocks; every piece's blocks lie inside its tile. A test with
  `overrideForTests` returning `FixtureWorldPlanner` (hilly fixture terrain, blocks
  float over the flat world but the digest doesn't care). Mutants: mix the `random`
  argument into a variant pick → digests differ between orders; clip to `chunkBB` only
  (not the tile) → the "inside its tile" test fails.
- Data checks in the same run: the structure, set and tag JSON load (the world loads;
  a malformed codec field fails the server start), `/locate structure
  fractalbuildings:village` returns a result or a clean "not found" (structures are off
  in the test world, so assert it doesn't throw).

### B2. In-world determinism, T1 (6.1/6.2 evidence) — size M, **heavy**

Needs: B1. A client game test `DistrictOrderClientGameTest` re-implementing S3's test
properly (S3 "How to re-implement"): fresh normal worlds (`setUseConsistentSettings(false)`,
seed 987654321), the tiles of `gate-temperate-city` (site 1280, −9600; take the tile
list from `WorldPlanner` in the test), chunks generated in forward, reverse, two
shuffles, spiral, 8-thread shuffled, and 8-thread with an evictor clearing every
`WorldPlanner` cache every 2 ms.

- **Digest (D7):** SHA-256 of the sorted ledger of what our pieces own (writes plus
  claimed air), per chunk and whole; identical in every order. Plus the **reference
  raster**: the core's `raster` of every piece computed in the test JVM with the same
  live probes (no world reads) equals the ledger; and world blocks equal the raster for
  ≥ 99.9 % of solid cells (vanilla trees still get into our air, D5).
- Read back: every door is a door, every bed (after B5) a bed.
- Mutants kept as switches (S3): a shared counter in the variant pick; a ground piece
  box one block too large (blocks written twice); step `surface_structures` instead
  of `top_layer_modification` (S3: trees replace solid blocks).
- Record lane counters (B4's, when merged) and timings in the log, not as asserts.

### B3. Terrain in a real world (6.3) — size M, **heavy**

Needs: B1 (B2 first in the lock queue). A client game test measuring S5's metrics on
real settlements of the three review seeds (987654321, 12345, 1): per building, void
under the floor (must be 0), wall exposure and burial at the base ring; per ground
piece, no road, quay or bridge block above air (floating), and road surfaces walkable
(at most one block step between neighbouring road columns, the 5.2 tile-border case
included). Two screenshots per site (one sloping city edge, one shore).

- Expected trouble, fix in the core if it shows: fill boxes that end above terrain the
  beard lowered (a gap under a road next to a building: extend fill boxes down to
  `floorY − 12` when the column is within 12 blocks of a building box, a pure rule);
  jetties over a street where the ground piece's cut clears up to `frontY + 4`
  (`Lot.AIR_CLEARANCE` is 3, so the slab bottom at `frontY + 4` must survive: a test).
- **Evidence:** the metrics table in the log, screenshots looked at, REVIEW entry for
  uphill cut faces (S5: 5–12 blocks) and bare stone on deep cuts.
- Not in this step: per-wing ground levels (decision 10).

### B4. Prefetch, warming and lane counters (P2) — size M

Needs: A1, A2, B1. Files: `mod/.../worldgen/Workers.java`,
`mod/.../worldgen/RegionWarmer.java`, `mod/.../worldgen/LaneStats.java`;
core: `WorldPlanner.prefetch`/`warm` bodies if A2 left them thin.

- One shared executor per server: at most 4 daemon threads named
  `fractalbuildings-worker-N`, low priority (S6: 4 workers cost 3 % of chunk
  throughput, 8 cost 22 %). Shut down on `ServerLifecycleEvents.SERVER_STOPPING`.
- At start creation, `prefetch(ix, iz)`: this tile's buildings (for its
  `postProcess`), and the plans of its 8 neighbour tiles owned by the same settlement.
- `RegionWarmer`: on `ServerTickEvents.END_SERVER_TICK`, every 20 ticks, for each
  player, `warm(x, z, 2 cells)` in the workers: resolves region cells and builds the
  skeletons of every settlement found (a metropolis spans up to about 50 cells: its
  skeletons are 0.15–0.4 s each with exact 32-stride probes, so warming matters most
  there). Pure caches only, so warming changes timing, never output.
- `LaneStats` behind `-Dfb.lane=1`: start-creation and `postProcess` time per tile,
  inline versus done-ahead building lookups (S6's DONE / INFLIGHT / INLINE),
  peak concurrent `postProcess`. A `/fb lane` command prints them.
- **Tests:** core: `WorldPlannerOrderTest` with prefetch on a 4-thread pool and an
  evictor gives the same bytes (A2's test, one more configuration); server game test:
  after `prefetch`, `buildings()` reports a cache hit; shutdown leaves no thread named
  `fractalbuildings-worker` (a test that lists threads).

### B5. Gameplay hooks and commands (6.4) — size M

Needs: B1 (A3 for the raster). Files: `mod/.../gameplay/` (`HookPlacer`,
`LootTables`, `Villagers`, `FbCommand`), loot tables under
`data/fractalbuildings/loot_table/chests/<type>.json`, server game tests.

- Hooks (`Hook` in the IR: bed, table, workstation, chest, hearth, altar, counter,
  light) become blocks in `BuildingPiece.postProcess`, after the shell cells and only in
  the chunk that owns the hook cell: bed (two blocks, foot at the hook, head in
  `facing`; skip if the head cell is not air in the shell), workstation by the
  building type and room use (workshop: smithing/stonecutter/fletching by a hash;
  shop: barrel or lectern; temple: brewing stand; house: crafting table), chest with
  `RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(hash(seed,
  x, y, z, "loot")), pos, key)` (checked: used by `ShipwreckPieces` and
  `EndCityPieces`), lights (lantern on the floor or hung from a ceiling cell). Hook
  blocks enter the ledger like any other block.
- Villagers (decision 2): in hamlets and villages, one `EntityType.VILLAGER` per bed at
  the bed, at most 10 per district; created with `EntitySpawnReason.STRUCTURE` and
  added through the `WorldGenLevel` (*inferred* to work as vanilla templates' entities
  do; the step checks it). Entities are not part of the D7 digest.
- `/fb` command (`CommandRegistrationCallback`): `/fb info` (settlement id, tier,
  district tile, lot, building type and style at the player), `/fb locate [tier]`
  (nearest settlement centre through `WorldPlanner.nearest`, clickable teleport like
  `/locate`), `/fb lane` (B4), `/fb config` (B6). `/locate structure
  #fractalbuildings:settlement` and `/locate structure fractalbuildings:city` come from
  B1's per-tier structures; `/locate` finds the nearest *district*, not the centre
  (S3), which is why `/fb locate` exists.
- Room for a villager AI mod later: a small public API class
  `dev.fractalbuildings.mod.api.Settlements` (`at(ServerLevel, BlockPos)` → settlement,
  tile, lot, building type, and the hooks of that building in world coordinates). No
  behaviour; documented in the README.
- **Server game tests** (flat world, as B1): a house tile places at least one bed whose
  POI the `PoiManager` reports after the chunk loads; a chest has our loot table and the
  same loot seed in two runs; `/fb info` at a lot names its type.

### B6. Config (6.5) — size S–M

Needs: A2 (`WorldConfig`). Can start beside B1. Files: `mod/.../config/`
(`FbConfig`, `WorldConfigData extends SavedData`), `config/fractalbuildings.json`
written with defaults on first run.

- Options: `frequency` (multiplies the region planner's base chance), `maxRadius`
  (default `Tiers.DEFAULT_MAX_RADIUS` = 4096; above 4096, a log warning that cities over
  8192 across take hours of chunk generation, ADR 0003 "Practical size limits"),
  `styles` (enabled styles; a disabled one falls back to `plain`), `villagers`
  (on/off), `vetoVanillaVillages` (on/off, B7), `cellSize` (expert; 1024).
- Pinning (decision 5): on `ServerWorldEvents.LOAD` of the overworld, read the world's
  `fractalbuildings` saved data; if absent, copy the file's values into it. The
  `WorldPlanner` reads only the saved values. *Check in this step* that LOAD fires
  before the spawn chunks generate (if not, read the saved data lazily in
  `WorldPlanners.of` from the server's overworld data storage).
- **Tests:** core: a disabled style never appears in any brief of 3 gate windows;
  `frequency` 0 gives no settlement in a 16 × 16-cell window. Server game test: a world
  created with `maxRadius` 2048 keeps it after the file changes to 4096 (save, change,
  reload in the test).

### B7. Veto and compatibility (6.6) — size L, **heavy** for the mod matrix

Needs: B1. Files: `mod/.../compat/` (`CityVeto`, `BiomeAliases`,
`TerraBlenderPriming`), mixins added to `fractalbuildings.mixins.json`,
`data/fractalbuildings/tags/worldgen/structure/excluded_from_cities.json`
(villages, pillager outposts, other surface structures).

- **Veto (W7, S8):** the two `@Inject(HEAD, cancellable)` mixins of S8
  (`tryGenerateStructure`, `StructureCheck.canCreateStructure`) with one predicate:
  the structure is in `#fractalbuildings:excluded_from_cities` and its start chunk lies
  within a settlement's footprint plus a margin of at least its reach (villages 80,
  jigsaw 128 + 12). The footprint test comes from `WorldPlanner` (the cell's settlement
  and its radius from the region view: no district planning). Re-check the private
  targets with `javap -p` (W7).
- **T3 test** (client, heavy): a vanilla village whose start lies just outside a
  settlement's margin still generates and `/locate structure minecraft:village_plains`
  finds it; one inside does not generate and `/locate` does not report it (S8's
  agreement check).
- **Biomes from other mods:** `BiomeAliases` maps a non-vanilla biome to a vanilla
  stand-in by convention tags (`c:is_desert` → `minecraft:desert`, `c:is_jungle`,
  `c:is_savanna`, `c:is_badlands`, `c:is_snowy`, `c:is_taiga`, `c:is_ocean`,
  `c:is_river`, `c:is_mountain`; otherwise `minecraft:plains`), applied in
  `LiveBiomeProbe` so the core's biome classes and style lists work unchanged. Plus
  `#fractalbuildings:no_settlements` (B1's tag): those biomes alias to a class with
  multiplier 0. Unit test over a fake tag lookup.
- **TerraBlender:** with more than one region, prime with a per-region anchor (S1:
  `IExtendedParameterList.getUniqueness` region index, anchor picked from 16
  candidates, fixed per world). Detect TerraBlender with
  `FabricLoader.isModLoaded("terrablender")`; reflection only, no compile dependency.
- **Mod matrix** (client, heavy, one run per set, S2's `-Ps2ModSet` mechanism and
  jars): vanilla, Lithostitched, Terralith + Lithostitched, Tectonic, Terralith +
  Tectonic (decision 9), TerraBlender + 2 regions. Per set: a world on seed 987654321
  generates the tiles around the first settlement `/fb locate` finds, B2's digest is
  equal in two orders, no crash, start-creation time logged (S2: exact probes are 2 to
  3 times dearer under these mods).
- **Evidence:** the matrix table (set, settlement found, digest equal, ms per start)
  in the log; README compat section text for C3.

### B8. Performance budget in CI (6.7) — size M, **heavy**

Needs: B4 (B2 for the harness). A client game test `PerfClientGameTest` plus
`scripts/perf.sh` and `mod/perf-budget.json` (the budget table above).

- Movement as S6 did it (spectator moved each server tick at 33 blocks/s elytra and
  5.6 sprint), on a fixed path into the `gate-metropolis-temperate` centre (seed
  987654321, site 2432, −5696) and through `gate-temperate-city`; a teleport case
  (`/tp` into the metropolis centre, cold).
- Measure: lane counters (B4), wall ms per chunk with the mod against the same path
  with the structures disabled by a test flag (same world seed), inline share, start
  creation cold and warm, region resolve, `/locate` time, start NBT sizes
  (`createTag`), region-file bytes of the city area against the no-city run, heap after
  a forced GC at the end.
- **CI check:** `scripts/test-all.sh` layer 5 runs it with the budget's lenient form
  (fail only above 2 × budget, so a loaded machine doesn't flake) and prints the
  numbers; a quiet-machine run (loadavg under 2) records the real values in
  `docs/review/phase6/perf.md`. Thresholds and the measured values go to NOTES.
- If a budget fails on a quiet machine: the report names which (start creation →
  A6; inline share → more workers or W10's pool rule; region resolve → the coarse-screen
  fallback of decision 1). Don't loosen a budget without the coordinator.

### B9. Fly-through screenshots (6.8) — size S–M, **heavy**

Needs: B2, B3 (B5 for beds and lights in view). A client game test
`FlyThroughClientGameTest` with a fixed camera path per seed: three settlements per
seed (from `/fb locate` results, fixed in the test once chosen), 6 frames each
(approach from 200 blocks, above the hub, a street at eye height, an interior through
a door, a shore or slope, the edge), render distance 12, chunks generated server-side
first then `hasRenderedAllSections()` polled (S5's recipe). PNGs to
`mod/build/screenshots/flythrough/<seed>-<site>-<n>.png`.

- Coarse automated check per frame: fraction of pixels that are sky below 0.9 and
  fraction of "our" blocks in view (from the ledger and the camera frustum) above 0 for
  the city frames; never pixel-exact.
- Agents open every PNG (Read) and describe what they see in the log.

## Wave C: after Wave B

### C1. Intercity roads, mod half (6.10) — size M, **heavy**; waits for ADR 0004

Needs: ADR 0004 accepted, A4, B1 (ownership). Re-implement S10's feature properly
(s10-mod.md "Re-implementing it properly"): a per-chunk `Feature`
(`codec()`, `place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`, `MapCodec`
in `BuiltInRegistries.FEATURE_TYPE`, `worldgen/feature` + `placed_feature` with empty
`placement`, `BiomeModifications.addFeature(foundInOverworld(), TOP_LAYER_MODIFICATION,
key)`), reading a core `RoadQuery` (the planner half's per-chunk query; until it lands,
a fixture polyline as S10's). Columns owned by a settlement tile are skipped (the
settlement's own streets meet the road at its exits). Ground found by scanning down
from `WORLD_SURFACE_WG` past leaves, logs, replaceables and fluids; never
`MOTION_BLOCKING`. Claim-semantics ledger, 4 orders identical, the leaf-ground mutant
kept; road blocks from the style palette of the biome (`road`, `deck` for bridges).
Fix S10's sheer cuts with the shoulder rule the ADR settles. If ADR 0004 is late, this
step moves to Phase 7 and the release goes without it (decision 10).

### C2. Review pack and playtest kit (gate) — size M, **heavy** for captures

Needs: B2–B9 (C1 if it landed). Files: `docs/review/phase6/README.md`, screenshots
under `docs/review/phase6/` (JPEG, 960 wide, as the S5/S7 notes do, keep the pack under
about 5 MB), `docs/REVIEW.md` entry "Phase 6 gate".

- **Per seed** (987654321, 12345, 1): the world-creation recipe (singleplayer,
  default world type, seed, structures on), a table of settlements near spawn
  (`/fb locate` for each tier: id, tier, centre, distance, biome mix; one over
  512 × 512 and one straddling a biome border), the B9 fly-through frames, a plan view
  of the same settlement from the viewer (`npm run city` on the matching dump, so John
  can compare game and viewer), and the timing and size numbers from B8.
- **Playtest checklist for John** (one hour per seed, his call how): spawn, `/fb locate
  village`, walk in, enter buildings, sleep in a bed, open chests, trade if villagers
  spawned, elytra over a city (lag?), `/locate structure #fractalbuildings:settlement`,
  night in a city (spawns), and what to write in REVIEW. Plus the desktop-launch steps
  from CLAUDE.md (John logs in to GNOME as `agent`; an agent runs
  `scripts/run-desktop.sh`), and how to pass the seed (the client's world-creation
  screen; no command-line seed).
- **Known issues** listed plainly (cut faces, teleport hitch, seams after updates,
  Phase 5 backlog items visible in game).

### C3. First public release (after John passes the playtest) — size S–M

Needs: C2 and John's go. Files: `README.md` (player README from A5, with screenshots;
the agent README moves to `docs/AGENTS.md` and CLAUDE.md's link follows),
`CHANGELOG.md` (0.1.0), `docs/release/modrinth.md` (page text: summary, features,
compatibility, known issues, licence, links; gallery list from C2), icon from a C2
frame, `gradle.properties` version.

- **Evidence:** `scripts/test-all.sh` green on the release commit; the jar built from
  a clean checkout; `jar tf` as A5; the jar loads in a fresh dev client with no other
  mod than Fabric API (client test smoke); the SHA-256 of the jar in the changelog.
- **Flag, don't do:** uploading to Modrinth or CurseForge, creating a git tag on
  `main`, pushing anything public. The report ends with "ready for John to publish" and
  the exact artefact path. (Optional, if John wants it: the Minotaur Gradle plugin for
  Modrinth uploads, configured but never run by an agent.)

## Optional, after the release unless John asks sooner

- **O1 Roof and lintel shapes:** a core shape pass for roof edges as stairs and wide
  openings' lintels as top slabs, arches as upside-down stairs (John, REVIEW 5.11
  OPENPLAN). A3's `Shape` and A4's `ShapeTable` already carry what it needs.
- **O2 Per-wing ground levels** (6.3 planner half; ASSUMPTIONS 4.1): one slab and one
  rigid piece per wing on slopes.
- **O3 `fractalbuildings:settlement` density function** (W9) and flattening (W6).

# Test infrastructure this plan adds

- `FixtureWorldPlanner` (A2): a one-tile planner from committed fixtures, for mod tests
  that must not depend on live terrain.
- `WorldPlanners.overrideForTests` (B1): inject a planner into the game.
- The ledger hook with claim semantics and the D7 digest helper (B1, used by B2, B7,
  C1), in `mod/src/main/.../worldgen/Ledger.java` (off unless a test turns it on).
- Live-probe parity against `fixtures/terrain/` (A1), so any later Minecraft bump shows
  at once whether the game still matches the reviewed dumps.
- Movement simulation and lane counters (B4, B8), the perf budget file, `scripts/perf.sh`.
- The three review seeds with fixed settlement coordinates (B9, C2): 987654321 (the
  gate windows' seed), 12345 (`gate-coast-city`, the river window), 1 (the mixed
  window).
- Add each to ROADMAP "Testing strategy" when it lands (the step's coder).

# Parallelism and file ownership

| Step | Can start | Runs beside | Owns (others don't edit) | Heavy |
|---|---|---|---|---|
| A1 | now | A2–A5 | `mod/.../probe/`, mixins json (creates) | timings only |
| A2 | now | A1, A3–A5 | `core/world/`, `CityPlanner`/`DistrictScenes` extractions | no |
| A3 | now | A1, A2, A4, A5 | `core/raster/`, `docs/IR.md` ownership rule | no |
| A4 | now (A3's `Shape` as specified) | A1–A3, A5 | `mod/.../block/` | no |
| A5 | now | everything | `LICENSE`, `CHANGELOG.md`, `docs/release/`, `mod/build.gradle` jar bits, `fabric.mod.json` metadata | no |
| A6 | only if A1/B8 say so | B1–B7 | `Districts`, `Briefs`, `Grading`, fixtures, goldens | no |
| B1 | A2 API commit + A3 + A4 | B6 | `mod/.../worldgen/`, `data/fractalbuildings/worldgen`, structure tags | no |
| B2 | B1 | B4–B6 (not other heavy) | `gametest/.../order/` | **yes** |
| B3 | B1, after B2 in the lock queue | B4–B6 | `gametest/.../terrain/`, core fill-depth fix | **yes** |
| B4 | A1, A2, B1 | B2, B3, B5, B6 | `Workers`, `RegionWarmer`, `LaneStats` | no |
| B5 | B1 | B2–B4, B6, B7 | `mod/.../gameplay/`, loot tables, `/fb` | no |
| B6 | A2 | B1–B5 | `mod/.../config/` | no |
| B7 | B1 | B4–B6 | `mod/.../compat/`, mixin entries, veto tag | **yes** (matrix, T3) |
| B8 | B4 (+B2 harness) | B5, B6 | perf test, `scripts/perf.sh`, `perf-budget.json` | **yes** |
| B9 | B2, B3 (+B5) | B6 | fly-through test | **yes** |
| C1 | ADR 0004, A4, B1 | B-steps | `mod/.../roads/` | **yes** |
| C2 | B2–B9 | C1 | `docs/review/phase6/` | captures |
| C3 | C2 + John | — | `README.md`, release files | smoke only |

- **Ready to run in parallel first: A1, A2, A3, A4, A5** (five coders; none needs a
  client test; A1 takes the heavy lock briefly for its timing table only).
- **Client-heavy queue** (one lock, about 1–10 minutes per run, more for the B7 matrix):
  B2 → B3 → B7 → B8 → B9 → C1 → C2. Keep at most two heavy steps in flight; the
  others develop and run their server tests while they wait.
- Shared files: `FractalBuildingsMod.onInitialize` (each step adds one
  `XInit.register()` line, in wave order), the gametest `fabric.mod.json` entrypoint
  lists, `fractalbuildings.mixins.json` (A1 creates, B7 appends). Conflicts there are
  one-line merges; resolve them by keeping both lines.

# Risks and open questions (with the assumption if nobody answers)

- **Exact probes too slow even batched** (A1). *Assumption:* the batched or windowed
  probe gets a city tile's plan under 300 ms; else A6, and decision 1's fallback for the
  region layer.
- **First touch of a metropolis needs every member skeleton** (a district's lots read
  the whole settlement's road network, NOTES ROADS): up to about 50 cells × 1,024
  exact 32-stride probes, 5 to 20 s cold. *Assumption:* the region warmer (B4) hides it
  for anyone travelling; a teleport into a metropolis can hitch for several seconds
  (measured in B8, written in the README). If B8 shows it on ordinary travel: build the
  network from the cells within reach of the tile first (a planner change, a new step).
- **The cold region resolve reads 17k–28k lattice columns** (MC1 note). *Assumption:*
  under 1 s with the memo and warming; else decision 1's fallback for the region layer.
- **Air rights crossing a tile border** (a jetty over a border street would be written
  by the wrong start). *Assumption:* rare; A2's test finds it and `Briefs.fill` clips.
- **Beard versus ground fill** (B3's floating-road case). *Assumption:* a pure
  fill-depth rule fixes it.
- **Ownership order between the two structures**. *Assumption:* none needed, because
  A3 makes their cells disjoint; B2's "written twice" mutant proves the test catches it.
- **Server-side only** (decision 8) and **EULA** for a dedicated-server test: John's.
- **Entities at worldgen** (B5 villagers) are inferred to work like vanilla template
  entities; if not, villagers are left out of the release and noted.
- **Memory**: real IR is larger than S6's stand-in. *Assumption:* the bounded caches
  (64 skeleton sources, about 64 tile plans, building IR per tile evicted after its
  `postProcess` finishes) keep heap growth under the budget; B8 measures.
- **Version churn**: every Minecraft bump re-runs A1's parity test, B7's `javap -p`
  check of the veto targets, and B2 (the S3 trees-on-roofs finding depends on step order).
- **Spawn far from any settlement**: *assumption:* vanilla spawn is kept; `/fb locate`
  and the review pack's coordinates cover the playtest.
