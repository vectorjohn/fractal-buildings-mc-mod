# P6 A2: WorldPlanner, the per-world planner facade

Branch `claude/p6-a2-worldplanner`, plan step A2 of [phase6.md](../plans/phase6.md). Pure Java in `planner-core/.../core/world/`.

## What landed

- **API commit** (`836aa08`): `WorldPlanner` (bodies throwing), `WorldConfig`, `WorldProbes`, `TileInfo`, `TilePlan`, `PieceRef`,
  `BuildingPiece`, `GroundPiece`, `SettlementInfo`, and a working `FixtureWorldPlanner.sample()` (the hand-laid `SampleSettlement`
  town: 4 tiles, 40 lots). `FixtureWorldPlanner` is in `main` (not testFixtures) so the mod's tests can use it without build changes.
  `WorldPlanner` is a non-final class so the fixture can subclass it.
- **Extractions, behaviour preserving** (goldens and digests unchanged): `CityPlanner.planTile` (Districts.plan + Briefs.fill, used by
  `CityPlanner.plan` and the world planner), `DistrictScenes.generateLots` / `generateLot` (the refuse-then-house fallback) and
  `DistrictScenes.ground` (street and plaza grading); `DistrictScenes.build` is now these three plus the invariants.
- **Implementation**: one `FieldRegionView`/`FieldSiteView` per planner; one `SkeletonSource.Direct` per settlement id in a
  `BoundedCache` (cap 64) so every tile of a settlement shares skeletons and the memoised road network; `tile()` = owner from the
  tile-centre cell (else lattice vote through `Districts.owns`) and `RoadNetwork.planned`; per-tile `FlightCache`s (one future per key,
  caller runs the task if no worker started it, nothing built under the lock) for plan, buildings and ground. `prefetch` queues work on
  the supplied executor; `warm` resolves cells and builds networks; `nearest` scans cells; `evictAll` empties every memo.
- `SkeletonSource.Direct.network()` now builds once under a lock (a metropolis network is seconds; 8 threads each building it would
  multiply the cost). `BoundedCache.clear()` added.
- `WorldPlanner.forSite(...)`: a planner for one given settlement (no region field), the CLI's `--site`. The gate windows are too
  small for the region planner to decide (every tile came back empty), so the gate tests use it.

## Tests (planner-core, `core/world/`)

- `WorldPlannerParityTest`: all 9 gate windows, every planned tile: the tile set equals `CityPlanner.plan`'s districts and each plan is
  byte-equal `DistrictJson` (and landmarks); on `gate-temperate-city` buildings equal `DistrictScenes.build(...exterior=false)` byte for
  byte through `IrJson`, and the ground volume inside the tile matches.
- `WorldPlannerOrderTest`: 30 tiles of `gate-metropolis-mixed` (plan + buildings + ground): reverse, two shuffles, capacity 1/2/3 with
  each tile asked twice, 8 shuffled threads, 8 threads at capacity 1 with an evictor thread calling `evictAll` every 2 ms, prefetch on a
  4-thread pool: identical bytes. Harness mutants (an order-dependent biome probe; a probe with a shared scratch field) are detected.
- `WorldPlannerInvariantTest`: on all 9 windows every tile's buildings and ground lie inside the tile (no air strip crosses a tile
  border: the plan's "clip the strip in `Briefs.fill`" was not needed) and ground inside its chunk; every 4th tile's union passes
  `Invariants.check` with no violations.
- `WorldPlannerRegionTest` (`world-12345-s64` map, region-derived settlements): finds city `sm3_m9`, every CLI district matches and no
  extra tile is claimed; start chunks; `frequency` 0 gives no settlement; warm and capacity 1 change nothing.
- `WorldPlannerIsolationTest`: two worlds (same site id, seeds A and A+1) interleaved on 8 threads; each plan carries its own seed;
  a disabled style becomes `plain` and nothing else changes.
- `FixtureWorldPlannerTest`.
- Mutation checks: a static `sources` cache shared by all planners fails the isolation test (the first version of the test did not:
  both worlds were contaminated alike, so plans now assert their own seed); a shared counter in the building id fails the order and
  parity tests.

## Decisions and deviations

- `raster(PieceRef, Box3, CellSink)` and `CellSink` are **not** in this commit: they need A3's `Shape`/`PieceRaster`, which was not
  merged. One method over `PieceRaster` once A3 lands (B1 or the verifier).
- `plan()` throws `IllegalArgumentException` for a tile with no settlement (callers check `tile()` first); `buildings`/`ground` return
  empty lists then.
- `BuildingPiece.box` is the lot rectangle plus the shell footprint (air strip included), from the lowest foundation box to the top of
  the shell, **not clipped** (the test asserts it lies inside the tile instead); `minY` is below the slab when a foundation reaches lower.
- `WorldProbes` has a fifth component, the optional recorded `window`, with a four-argument constructor for unbounded worlds.
- `WorldConfig.styles` empty means all styles; `plain` is always allowed. `frequency` multiplies `RegionPlanner.baseChance`.
- `tile()` builds the owner's whole road network on first touch (all member cell skeletons): seconds for a metropolis. Nothing here
  hides it; B4's warmer calls `warm`. A tile owned only through the hub-tile rule with no lattice column in a settlement's cells would
  not be found (not seen in the fixtures).
- Skeleton and network builds are not single-flight across threads except `network()`; two threads missing the same cell skeleton both build it.
