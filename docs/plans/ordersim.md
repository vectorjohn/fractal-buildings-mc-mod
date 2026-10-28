# Plan: ORDERSIM, engine-free chunk-order tests

Lead-coder plan, 2026-10-27, branch `claude/p6-ordersim-plan` (base `claude/run-phase6` at 6b162cdc).
Item: ROADMAP Phase 6, "ORDERSIM" (John 2026-10-27). Background: [TESTING.md](../TESTING.md) ("Where order
independence is already proven", "What needs the engine"), the logs of
[ORDERFAIL](../log/2026-10-27-orderfail.md), [SPILLDIRT](../log/2026-10-27-p6-spilldirt.md) and
[B2](../log/2026-10-22-p6-b2-order.md).

# Goal

Fast plain-JUnit tests in `:mod:test`, with no server and no client, that show the mod's side of worldgen
does not depend on chunk order. Then `scripts/order-test.sh` (dedicated servers, about 10 min, 17 runs) can drop
from "required" to "consider" for most worldgen changes. It keeps running on heavy batches, to-main, Minecraft
bumps, mixin, structure-data and threading changes, and new block kinds.

The core already proves the planner side (`WorldPlannerOrderTest`, `RasterOwnershipTest`, `SettlementDeterminismTest`).
The gap is the mod path: `TilePiece`'s write and the passes that **read the world** (`SpillClear`,
`BuildingAirClear`, `StreetAirFix`, `FoliageClear`, `HookPlacer`, `RoadFeature`'s ground scan), and how they meet
vanilla features that a neighbouring chunk runs later and that write across the chunk border.

# The framing: what holds and what changes

- **Holds.** Vanilla is not order independent at chunk borders: S3 found 455 to 575 of 576 whole-chunk hashes
  differ between orders. Both real failures this week were a vanilla feature writing into our cells. Keep the real
  test as a backstop.
- **Change 1: real vanilla features, not fakes.** A probe (below) ran the real `minecraft:dark_oak`, `lake_lava`
  and `forest_rock` features from `VanillaRegistries.createWorldLookup()` against a proxy `WorldGenLevel` in plain
  JUnit. The dark oak left exactly the 4 `setDirtAt` dirt cells that caused SPILLDIRT. The lava lake wrote lava, cave
  air and its stone barrier. A hand-written fake only shows what we already know a feature does. The real one also
  shows what we don't know yet: BEDFIX was a `TreeFeature` calling `StructureTemplate.updateShapeAtEdge` on our bed,
  and the probe hit that same call path. Synthetic writers are a fallback only.
- **Change 2: compare claimed cells, not whole grids.** Real features interact with each other across borders
  (one tree blocks another), so whole-grid equality fails on vanilla alone. The sim uses the order test's own
  criteria on the cells our pieces claim:
  - the ledger equals the core's reference raster;
  - the world equals the raster, except what the shared spill classifier accepts;
  - the claimed cells are the same across orders.

  Unclaimed cells get a report-only diff, with the writer that changed each cell. They are compared strictly only
  in scripted scenarios whose features are spaced so they cannot touch each other.
- **Change 3: model post-processing.** `WorldGenRegion.setBlock` marks a cell for post-processing unless flag 16
  is set. `LevelChunk.postProcessGeneration` later reshapes marked cells with `Block.updateFromNeighbourShapes`, at
  promotion, and promotion also happens in chunk order. Our writes use 2|16. Vanilla's do not. So the sim records
  the marks and replays them when a chunk is promoted. That covers the BEDFIX class of bug and the `flag2` mutant.
- **Change 4: locality as write contracts.** The sim attributes every `setBlock` to the code that made it, and
  checks it against a contract for that pass (table below). For example, `SpillClear` may only write air, only into
  planned street air outside its own chunk, and never over a log. Each pass branch that reads the world is listed,
  and each gets a scenario that runs it in both relative orders.
- **VillageRoads join and the road's yield to foreign structures (MF2)** read vanilla structure starts from a
  `WorldGenRegion`, so the sim cannot cover them until a starts seam exists (step S8, after VILLROAD merges).
  `foreignStructureTops` already returns null for a level that is not a `WorldGenRegion`, so the sim's road
  feature runs without the yield.
- **The lake veto decision** is covered (the sim calls `LakeVeto` before every lake). The **mixin wiring** is not,
  because plain JUnit does not apply mixins. A new server game test covers it (S6).

# Verified API facts (Minecraft 26.3, Loom's `minecraft-merged-deobf-26.3.jar`)

Probes ran in this worktree as `:mod:test --tests` (3 throwaway test classes, deleted, not committed). Loadavg was
about 2.

- `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` works in `:mod:test` (BlockMapperTest and
  TerrainDumpTool already do this). It took **2.6 s**.
- **Block tags are empty after Bootstrap**: `OAK_LOG.is(BlockTags.LOGS)` was false. Our passes, and `Spill`, are
  built on `BlockTags.LOGS`/`LEAVES`/`ICE`, so without tags every check is silently wrong. Binding them takes 0.1 s:
  ```java
  try (var rm = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
    Map tags = TagLoader.loadTagsForRegistry(rm, (ResourceKey) Registries.BLOCK, (TagLoader.ElementLookup) TagLoader.ElementLookup.fromFrozenRegistry(BuiltInRegistries.BLOCK));
    BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult(Registries.BLOCK, tags)).apply();   // 300 tags; LOGS, LEAVES, DIRT true after
  }
  ```
  (`MappedRegistry.bindTags` calls `validateWrite` and fails on the frozen registry, so use `prepareTagReload`.)
  Do the same for `Registries.FLUID` if `FluidTags.LAVA` is needed (`SpillKinds.lakeBarrier`).
- `WorldGenLevel` is an interface (`javap`: it extends `ServerLevelAccessor`), so `java.lang.reflect.Proxy` can
  implement it. The handler also receives default methods. Answer those it doesn't model with
  `InvocationHandler.invokeDefault(proxy, m, args)`. Make any abstract method it doesn't model throw
  `UnsupportedOperationException(m)`, so a missing method is loud and names itself.
- `VanillaRegistries.createWorldLookup()` takes 0.2 s. Its `Registries.FEATURE` holds the configured features
  (in 26.3 `Feature` is an interface with `place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`, and
  `ConfiguredFeature` no longer exists). Keys: `TreeFeatures.DARK_OAK/OAK/FANCY_OAK/BIRCH/SPRUCE/MEGA_SPRUCE/PALE_OAK`,
  `MiscOverworldFeatures.LAKE_LAVA/FOREST_ROCK/DISK_SAND/DISK_GRAVEL/DISK_CLAY/FREEZE_TOP_LAYER/ICE_SPIKE`,
  `OreFeatures.ORE_DIRT/ORE_GRAVEL`, `TreeFeatures.HUGE_RED_MUSHROOM`. A `null` generator was fine for all three tried.
- Methods the real features called on the level: `getBlockState`, `setBlock/3`, `isStateAtPosition`,
  `isFluidAtPosition`, `getRandom` (tree decorators, `updateShapeAtEdge`), `scheduleTick/3` (return null),
  `getBlockTicks`/`getFluidTicks` (`BlackholeTickAccess.emptyLevelList()`), `getChunk/1` (the lake's
  `Feature.markAboveForPostProcessing` needs a real `ChunkAccess`), `getMinY`, `getHeight/0`, `getMaxY`,
  `setBlockAndUpdate` (a default).
- **Real `ProtoChunk`s work as the sim's storage**, so `FoliageClear`'s section reads (`level.getChunk(cx, cz).getSections()`)
  run unchanged. The `PalettedContainerFactory` is built by hand (its `create(RegistryAccess)` needs a frozen biome
  registry, which we don't have):
  ```java
  Holder<Biome> plains = reg.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
  IdMapper<Holder<Biome>> ids = new IdMapper<>(); ids.add(plains);
  var pcf = new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState(), null, Strategy.createForBiomes(ids), plains, null);
  var chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), pcf, null);
  ```
  Fill the terrain while the chunk is EMPTY, then call `chunk.setPersistedStatus(ChunkStatus.FEATURES)`. After that,
  `WORLD_SURFACE_WG` stays frozen while `WORLD_SURFACE`/`MOTION_BLOCKING` follow writes (probe: WG 65, WS 71 after a
  write at y 70). That is the real pipeline's behaviour, which `RoadFeature`'s ground scan relies on.
  `ChunkAccess.getHeight(type, x, z)` is the top block. `Level.getHeight` is that + 1, so the proxy adds 1.
- `ChunkGenerator.applyBiomeDecoration` (javap -c): for each `GenerationStep.Decoration` step in order, it first
  places the **structure starts** of that step (`StructureManager.startsForStructure ... placeInChunk`, bytecode
  ~300-350), then that step's **features** (`FeaturePlacer`). So in a chunk, our pieces (top_layer_modification)
  run after every vanilla feature of earlier steps and before the top-layer features, and our road feature is one
  of those.
- `WorldGenRegion.setBlock(BlockPos, BlockState, int, int)` (javap -c): `ensureCanWrite` (`isWithinWriteZone`:
  |chunk - centre| <= writeRadius); `chunk.setBlockState(pos, state, flags)`; block entity creation
  (`EntityBlock.newBlockEntity`, or NBT for a proto chunk); then `if ((flags & 16) == 0)` it calls
  `markPosForPostProcessing(state.getPostProcessPos(this, pos))`.
- `LevelChunk.postProcessGeneration(ServerLevel)` (javap -c): for each marked pos, it ticks the fluid if any, then
  `Block.updateFromNeighbourShapes(state, level, pos)` and `level.setBlock(pos, new, 20)`.
- 26.3 has no NOISE, SURFACE or CARVERS status. `ChunkStatus` is EMPTY, STRUCTURE_STARTS, STRUCTURE_REFERENCES,
  BIOMES, TERRAIN, FEATURES, INITIALIZE_LIGHT, LIGHT, SPAWN, FULL. Terrain (with carving) is complete in every
  chunk a FEATURES step can touch (*inferred*: FEATURES needs its neighbours at TERRAIN). So the sim builds all
  terrain up front, and carvers are not an order axis.
- Cost on the gate-temperate-city recorded planner, 8 x 8 chunks, with our raster write plus `SpillClear`,
  `BuildingAirClear`, `StreetAirFix` and `FoliageClear` per chunk, on a quiet host:
  - planner cold: 0.8 s;
  - terrain fill: 0.12 to 0.16 s;
  - our passes: 0.5 s on the first round (raster cold), then **20 to 30 ms per order**;
  - `WorldPlanners.FAILURES` 0.

  The sim is well inside a seconds budget.

# Design

## Where it lives

`mod/src/test/java/dev/fractalbuildings/mod/ordersim/`, plain JUnit 5 in `:mod:test`. That runs in `:mod:build`
and so in the fast layer. It uses the real `BlockState`s, `ProtoChunk`s, tags, features and our real pass code. No
abstraction over `BlockState`: it's not needed, and an abstraction would test a copy rather than the code that
ships. Fixtures come from `../fixtures/terrain/` (the cwd of `:mod:test` is `mod/`).

| Class | Job |
|---|---|
| `SimBoot` | once per JVM (synchronized): Bootstrap, block and fluid tags, the world lookup, biome holders by id, the `PalettedContainerFactory` (with every biome the scenarios use in the `IdMapper`) |
| `SimWorld` | the `ProtoChunk`s of a window, terrain from a `TerrainProbe` + `BiomeProbe`, post-process marks, `promote(cx, cz)`, cell provenance, the write log |
| `SimLevel` | the proxy `WorldGenLevel` bound to one decorating chunk: read and write zones, the method table, the attribution of each write |
| `SimSites` | a `gate-sites.tsv` row as `WorldProbes` + `SettlementSite` + `WorldPlanner.forSite` (a copy of the 40 lines of core's test-only `GateWorld`; see Rejected) |
| `SimFeatures` | the feature pool, scripted feature calls, hashed per-chunk calls, the lake veto in front of every lake |
| `SimOrders` | the chunk orders |
| `OrderSim` | runs one scenario in one order (the decoration model below) and returns a `SimRun` |
| `SimCompare`, `SimReport` | the checks and the report |

## Decoration model (one chunk C, in the order's turn)

1. Steps `LAKES` .. `VEGETAL_DECORATION`: run C's feature calls of that step. Each call's level is a `SimLevel`
   centred on C: reads and writes only in C's 3 x 3 chunks (the generation region our passes are built for; anything further is a C1 violation). A lake call first asks
   `LakeVeto.vetoes(planner, origin)`. If it vetoes, the lake is skipped and the veto is counted.
2. Step `TOP_LAYER_MODIFICATION`, structures first (verified order):
   - for each of our pieces whose box meets C in x/z (built by the same code as `DistrictStructure`/`GroundStructure`,
     seam R2), call `TilePiece.write(level, planner, piece, chunkBB, chunkPos, random)` (seam R1). That runs the
     raster write, then `SpillClear` + `FoliageClear` for ground pieces, and `HookPlacer` for buildings, with
     villagers off;
   - `random` is a `RandomSource` seeded from (order index, C), so the `random` mutant shows up.
3. Then that step's features: `freeze_top_layer` (if the scenario has snow), then `RoadFeature.placeIn(level, planner, C)`
   (seam R3: `SpillClear` for chunks with no ground piece, `roadLeaves`, `BuildingAirClear`, `StreetAirFix`, the road
   draw). *Assumed*: Fabric's `BiomeModifications.addFeature` appends, so our road feature runs after
   `freeze_top_layer`. That is not verified (open question 2).
4. Promotion: once C and all 8 of its neighbours have decorated, promote C. That replays its post-process marks with
   `Block.updateFromNeighbourShapes` and writes with flags 20, like `postProcessGeneration`. Fluid ticks are not run.
   *Inferred*: a chunk reaches LIGHT/FULL only after its neighbours' FEATURES, so this is the only legal schedule.

The window is (n + 2) x (n + 2) decorated chunks. Cells are compared only in the inner n x n, where all 9 chunks
that can write a cell have decorated. That is the same idea as the order test's radius.

## Features

- **Scripted calls** `(step, chunk, featureKey, origin, rngSeed)` put a known feature at a known spot: a lake whose
  grid meets a dug-in building, or a dark oak whose 2 x 2 base straddles a street's edge row on a chunk line. A
  `ScenarioFinder` computes the spots from the plan (pieces, ground boxes, chunk lines), never from hard-coded
  coordinates, so they follow the generator as it changes. It tries rng seeds 0..63, against the terrain-only world,
  until the feature places, and keeps the first that does.
- **Hashed calls**: k calls per chunk, drawn from a pool by `Hash64.hash(seed, cx, cz, "ordersim", i)`. Origin x/z
  is in the chunk; y comes from the live heightmap the vanilla placement uses (trees `OCEAN_FLOOR`, rocks and
  mushrooms `MOTION_BLOCKING`, approximately). The pool: oak, fancy_oak, dark_oak, birch, spruce, mega_spruce,
  forest_rock, disk_sand, disk_gravel, ore_dirt, huge_red_mushroom, lake_lava, plus freeze_top_layer in snowy
  scenarios. Each feature's step comes from the biome JSON in the jar (`data/minecraft/worldgen/biome/<b>.json`,
  `features[step]`).
- The feature's `RandomSource` comes from (seed, origin, key), so one call does the same thing in every order unless
  the world it reads differs. That is exactly vanilla's own order dependence.
- `getBiome(pos)` gives the probe's biome holder, so `freeze_top_layer` sees snowy biomes. `getBrightness` is 0.

## Seams in production code (no behaviour change)

| Seam | Change | Risk |
|---|---|---|
| R1 `TilePiece.write` | Move the body of `postProcess` after the planner lookup and the gen-version warning into `public static void write(WorldGenLevel, WorldPlanner, TilePiece, BoundingBox chunkBB, ChunkPos, RandomSource)`. `postProcess` calls it. | Low: a pure move. `OrderGameTest` and `GameplayGameTest` (fast) still drive `postProcess`. |
| R2 piece lists | `DistrictStructure.buildingPieces(planner, tile)` and `GroundStructure.groundPieces(planner, tile)` return the `TilePiece`s the generation stubs now build inline. The stubs call them. | Low: a pure move, inside the existing try/catch. |
| R3 `RoadFeature.placeIn` | `public static boolean placeIn(WorldGenLevel, WorldPlanner, ChunkPos)` holds everything in `place` after the dimension and planner checks. | Low. It **conflicts with VILLROAD**, which edits `draw()` in the same file: land it after VILLROAD merges, or keep the diff to the lines of `place()` only. |
| R4 `LakeVeto.vetoes(WorldPlanner, BlockPos)` | The pure decision (the `LAKES` mutant check plus `claimsNear(box(origin))` plus the counters). The level overload checks the dimension, looks up the planner and delegates. | Low. |
| R5 per-thread ledger | `PieceLedger.startLocal()`: a recording seen only by the calling thread, so orders can run on parallel threads. `record()` stays `Recording r = active; if (r == null) return;` with a sentinel for "per thread", so production pays nothing new. | Low. |
| R6 `Spill` to main | Move `gametest/order/Spill` to `mod/.../worldgen/SpillKinds` (public), with `BlockGetter` in place of `Level` in `trunkBaseBeside`/`lakeBarrier`. `OrderServerHarness` imports it. The order test and the sim must classify alike. | Low: test logic in main, like `Mutations` and `PieceLedger`. |
| R7 two mutants | `Mutations.TRUNK_BASE`: `StreetAirFix.restoreBase` returns when the plan gives air (the pre-SPILLDIRT2 code). `Mutations.SPILL_LOGS`: `SpillClear.run` clears logs and cells under logs (the pre-10-23 behaviour). Read once per `run()` into a local, not per cell. | Low. |

`SpillClear.run`, `roadLeaves`, `BuildingAirClear.run`, `StreetAirFix.run`, `FoliageClear.run` and `HookPlacer.place`
already take `(WorldGenLevel, WorldPlanner, ...)`, so they need no change.

## Orders

| Order | What it covers |
|---|---|
| `forward` (row-major), `reverse` | Together they put every adjacent pair both ways round. |
| `shuffle-1..4` (`SplittableRandom(seed)`) | Interleavings of three or more chunks. |
| `spiral` (from the centre) | Like the order test's spiral. |
| `checker` (even parity first) | Every chunk has neighbours both before and after it. |

The fast set is forward, reverse, shuffle-1, shuffle-2, spiral and checker. `-Dfb.ordersim.shuffles=N` adds more for
a soak. Orders run on a small pool (one `SimWorld` per order, the planner shared: it is thread safe and its answers
don't depend on order, as core proves). The mutant tests run their orders one after another, because
`Mutations.mode` is global. The sim has no `threads` or `threads_evict` order: Minecraft's own scheduling stays with
the order test.

## Checks per run (in `SimCompare`)

| Check | Rule | What it mirrors |
|---|---|---|
| C1 | No exception escaped; the `WorldPlanners.FAILURES` delta is 0; no read or write outside its zone. | The order test's failure count; vanilla's region check. |
| C2 | Ledger (`startLocal`): the district cells equal the reference raster (`planner.raster` of every piece over the inner window: missing/extra/wrong 0, duplicates 0). The road cells' digest is the same in every order. | The order test's ledger checks. |
| C3 | World against raster in the claimed cells: every mismatch goes through `SpillKinds` (`solidKind`/`airKind`, then `lakeBarrier`, then `trunkBaseBeside`, in the order test's order). Any `unknown` fails. The histogram is printed. | "not a known vanilla spill" |
| C4 | Across orders: each claimed cell's final state against forward's. Report-only at first, with provenance: the last writer of the cell in each order. It is strict (fails) in scenarios marked `spaced` (scripted features that can't touch each other). | New. |
| C5 | Write contracts (below), checked in forward and reverse. | New: locality. |
| C6 | Idempotence: after the order finishes, run every chunk's top-layer pieces and passes again. No claimed cell may change. | New. |

**Write contracts (C5).** The proxy finds the writer with `StackWalker`: the first frame in
`dev.fractalbuildings.mod.*`. That costs about 1 µs per write, so it is only on in forward and reverse. Every one of
our writes must carry flag 16 (`UPDATE_KNOWN_SHAPE`).

| Writer frame | Allowed write |
|---|---|
| `TilePiece.write` (raster loop) | x/z inside the decorating chunk; the state the reference raster gives |
| `SpillClear.run` | air, into ground-piece `AIR` cells (not `overridden`, not `lotMayOwn`) outside the decorating chunk; the old block is not a log |
| `SpillClear.roadLeaves` | air over a natural leaf, in road headroom |
| `BuildingAirClear.run` | air over a natural leaf, vine or tree log, in a building's planned air |
| `StreetAirFix.run` / `restoreBase` | air over a log or a snow layer in planned air; or the planned state (air included) over a dirt-family block |
| `FoliageClear` | air over foliage (log, natural leaf, vine, cocoa, snow layer, bee nest) |
| `HookPlacer.place` | a hook block at a hook position of the piece |
| `RoadFeature` (draw) | x/z inside the decorating chunk |

**Branches that read the world** (the only ways order can reach our cells). Each needs a scenario where it fires in
both relative orders. The sim counts how often each one fires, and a scenario fails its liveness check if a branch it
is meant to cover never fires:
- `SpillClear`: skips air, logs and cells under a log (left to `StreetAirFix`); skips persistent leaves; clears natural leaves out to 12.
- `StreetAirFix`: removes logs in planned air; `restoreBase` puts back a planned block, or air when the plan gives air; removes a hanging snow layer.
- `BuildingAirClear`: tree clusters in a building's air.
- `FoliageClear`: support from the 3 x 3 chunks.
- `TilePiece`: `old != state` (decides whether to write) and `cuts.note(old)` (feeds `FoliageClear`).
- `HookPlacer`: "the cell is air and the cell below is not".
- `RoadFeature`: the ground scan from the frozen WG heightmap past leaves, logs, replaceables and fluids.

## Scenarios

| Id | World | Window | Features | Covers |
|---|---|---|---|---|
| SC1 city | gate-temperate-city (the order test's settlement), centre of `s1_m10` | 6 x 6 inner | hashed, k = 3 | a broad sweep: streets on chunk lines, dug-in buildings, trees in street and building air |
| SC2 lake (ORDERFAIL) | as SC1 | 3 x 3 round the spot | scripted `lake_lava`: (a) grid meets dug-in walls; (b) origin under a building's box but inside the 12-block deep foundation; (c) far below, not vetoed (control) | lake veto, `claimsNear` deep foundation |
| SC3 trunk (SPILLDIRT) | as SC1 | 3 x 3 per spot | scripted `dark_oak` on a street edge row at a chunk line: (a) base under road concrete, log blocked (first hit, buried: classified spill); (b) trunk on the street surface, base planned air (third hit: `restoreBase` air branch); (c) base at the top of the headroom of a cut into a hillside (the 10-23 `air->dirt` case, classified by `treeAbove`) | `StreetAirFix`, `SpillKinds.trunkBaseBeside`/`treeAbove` |
| SC4 roads | the region planner over `w12345-river-s8` (`new WorldPlanner(seed, defaults, probes, null)`) where a road column meets district columns | 4 x 4 | hashed trees, a scripted crown over the road headroom | `RoadFeature.placeIn`, `roadLeaves`, ground scan |
| SC5 snow | gate-snow-city | 4 x 4 | `freeze_top_layer`, spruce | `StreetAirFix` snow, the ROADCLEAR hanging snow |

Planner build time (SC4 builds a `RoadNet`) is measured in S5. If a scenario costs more than 5 s it gets
`@Tag("slow")`, which `:mod:test` excludes unless `-Pfb.ordersimSlow` is set, and it says so in the log.

## Report

Per run, one line in the style of `OrderServerHarness`: scenario, order, cells, reference cells, ledger digest,
missing/extra/wrong, duplicates, solid bad / unknown, air bad / unknown, the spill histogram, the cross-order diff
count, contract violations, vetoed lakes and ms. Then the first 10 bad cells, each with its 6 neighbours and the
last writer of the cell in each order. It goes to stdout and to `mod/build/ordersim/<scenario>.report`, which
verifiers read.

## What the sim cannot cover (these keep the real order test)

- **Mixins applying at all** (`LakeFeatureMixin`, `ChunkGeneratorMixin`, `StructureCheckMixin`): plain JUnit has no
  mixin. S6 adds a fast server game test for the lake veto. The others are compat, not order.
- **Minecraft's chunk pipeline**: start placement and references, piece bounding boxes, worker threads,
  `RegionWarmer`/`Workers`, cache eviction under real threads, save and reload.
- **Vanilla's real decoration list**: which features, how many, placement modifiers. The sim uses a chosen pool at
  hashed spots, so a feature added in a Minecraft bump is unseen.
- **Fluids flowing, falling blocks, scheduled ticks** (dropped), light. That is why the `content` rows for new
  block kinds keep `order`.
- **Block entities**: only partly (no loot check). Villagers are not spawned.
- **Structure starts**: the VILLROAD join and the MF2 yield, until S8.
- **The live probes and config** (`WorldPlanners.of`): `LiveProbeTest` covers probe parity. Other worldgen mods:
  the compat matrix.

## test-scope.tsv and TESTING.md (step S7, after the mutation evidence is in)

- The sim is part of `fast`, so it needs no new layer. Add a row
  `path mod/src/test/java/dev/fractalbuildings/mod/ordersim/*  order-sim  fast`, and a row for `@mod/worldgen/SpillKinds.java`
  (`fast,order`: the order test uses it too).
- **`order` becomes `order?`** on these rows. The reason to write: "the order sim (fast) runs this code in 6 orders
  with vanilla features; run order if the change touches the pipeline, threads, structure data or a new block kind".
  - `@mod/worldgen/SpillClear.java`, `FoliageClear.java`, and new rows for `StreetAirFix.java`, `BuildingAirClear.java`,
    `TilePiece.java` and `LakeVeto.java` (above the `@mod/worldgen/*` catch-all);
  - `@core/settlement/*`, `@core/terrain/*`, `@core/world/*` (WorldPlanner: the core order tests plus the sim;
    caches and threads keep their own rows), `@core/probe/*`;
  - `@mod/roads/*` gets `order?` added (it had none).
- **`order` stays required** on these rows:
  - build files, `gradle.properties` and the flake (Minecraft and Fabric versions);
  - `scripts/order-test.sh` and `@gt/order/*`;
  - `Mutations.java`, `PieceLedger.java`, `RegionWarmer.java`, `Workers.java`, `@core/cache/*`, `@core/rand/*`;
  - every `@mod/mixin/*` row;
  - `mod/src/main/resources/data/*/worldgen/*` (steps, placement);
  - `@mod/block/*` (write flags and shapes are only partly modelled);
  - the `content` rows;
  - the `@mod/worldgen/*` catch-all, for files without their own row (structures, placement, `WorldPlanners`).
- Heavy batches and to-main run every layer, as now. TESTING.md: a section "Engine-free order sim (ORDERSIM)" under
  "Where order independence is already proven", and the list above in "What needs the engine". ROADMAP testing
  table: row 4b says when it is required now.

## Rejected alternatives

- **An abstract `BlockState`/cell interface with the passes ported to it.** It would test a port, not the shipped
  code, and real tags, shapes and features would be out of reach. Bootstrap costs 2.6 s once.
- **Fake features only.** They encode what we already know. The real ones run (probe), and are what failed twice.
  Kept as a fallback for a kind no real feature in the pool produces cheaply.
- **A seam in `FoliageClear` for its section reads.** Not needed: real `ProtoChunk`s work as storage.
- **Whole-grid comparison.** Vanilla-on-vanilla order effects make it fail on its own (S3). It is used only in
  `spaced` scenarios.
- **Moving core's `GateWorld` to `java-test-fixtures`.** That touches a core test class many parallel items use, for
  40 lines. Copy it; if a third user appears, move it then.
- **Running the sim inside a server game test.** Mixins would apply and the world would be real, but it costs a
  server start, and the server's chunk system fights our hand-driven order. The order test already does this.

# Steps

Parallel groups: **S0, S1, S2 and S6 can start at once**. S3 needs S0 to S2. **S4a, S4b and S5 run in parallel**
after S3. S7 comes after S4 and S5. S8 comes after VILLROAD merges.

## S0. Production seams R1 to R7 (coder, small)

Files:
- `TilePiece.java`, `DistrictStructure.java`, `GroundStructure.java`, `LakeVeto.java` (+ the mixin keeps calling
  the level overload);
- `PieceLedger.java`, `Mutations.java`, `StreetAirFix.java`, `SpillClear.java`;
- move `gametest/order/Spill.java` to `mod/.../worldgen/SpillKinds.java`, and update `OrderServerHarness`.

For R3 (`RoadFeature.placeIn`), check whether VILLROAD has merged. If not, change only `place()`.

**Done (2026-10-27):** R1, R2, R4, R5, R6, R7 landed in S0. **R3 was left out** (VILLROAD is editing `RoadFeature`); it moves to S8 / the first step after VILLROAD merges. R2 names: `DistrictStructure.buildingPieces(planner, DistrictGrid.Tile)` and `GroundStructure.groundPieces(planner, DistrictGrid.Tile)` return `List<TilePiece>`; R1 is `TilePiece.write(level, planner, piece, chunkBB, chunkPos, random)`; R4 is `LakeVeto.vetoes(WorldPlanner, BlockPos)`; R5 is `PieceLedger.startLocal()`; R6 is `SpillKinds` (public methods, `BlockGetter`); R7 is `Mutations.TRUNK_BASE` ("trunk-base") and `Mutations.SPILL_LOGS` ("spill-logs"). Tests: `PieceLedgerLocalTest`, `LakeVetoTest` in `mod/src/test`.

Tests:
- the existing fast suite (`OrderGameTest`, `GameplayGameTest` and the server game tests drive `postProcess` and
  `place`);
- a new `PieceLedgerLocalTest`: two threads with `startLocal` each see only their own cells, the global `start()`
  still works, and no recording means no cost (no allocation);
- a new `LakeVetoTest` on a `WorldPlanner.forSite` planner of the gate-temperate-city dump (about 1 s; `FixtureWorldPlanner` fails on methods it does not override): a box on a building is vetoed, a box 100 below is not, and
  `Mutations.LAKES` turns the veto off.

Scope: `@mod/worldgen/*` means the order test is required for this step, until S7 lands. Run it: it is the proof
that the seams changed nothing.

## S1. `SimBoot`, `SimWorld`, `SimLevel` (coder; parallel with S0 and S2, needs no seam)

Build on the snippets in "Verified API facts". `SimLevel`'s method table, in a `switch` on `name/argc`:
- block state, fluid state, `setBlock/3`, `setBlock/4`;
- `isStateAtPosition`, `isFluidAtPosition`;
- `getChunk/1..4` (the `ProtoChunk`; throw outside the read zone);
- `getHeight/3` (+1), `getHeightmapPos`;
- `getMinY`, `getHeight/0`, `getSeed`;
- `getRandom` (seeded per call site), `getBiome`;
- `scheduleTick`, block and fluid ticks (no-ops);
- `getBrightness`, `getBlockEntity`;
- `ensureCanWrite`, `setCurrentlyGenerating`;
- `hashCode`, `equals`, `toString`.

Defaults go to `invokeDefault`. Anything else throws.

`setBlock` follows `WorldGenRegion.setBlock`:
1. check the write zone. Outside it the write is dropped, as vanilla drops it; from our code that is a C1 violation, from a vanilla feature it is only logged;
2. `chunk.setBlockState`;
3. create the block entity for an `EntityBlock`;
4. without flag 16, mark `state.getPostProcessPos(level, pos)`;
5. log `(cell, old, new, flags, writer)`, with the writer from the current context (`feature:<key>@origin` or
   `ours:<chunk>`) and, when the audit is on, the `StackWalker` frame.

Then `promote(cx, cz)`.

Tests (`SimWorldTest`):
- tags bound: `OAK_LOG` is in `LOGS`;
- the terrain matches the probe: the top solid block is `floorY - 1` and water fills to `surfaceY`;
- real `dark_oak` places, with 4 dirt cells under a 2 x 2 trunk;
- real `lake_lava` places lava and cave air;
- `freeze_top_layer` puts snow in a snowy biome holder;
- a write 2 chunks out is refused and logged;
- an oak fence written with flags 2 next to a later stone is reshaped at promotion, and with flags 2|16 it is not;
- `FoliageClear` removes a floating leaf blob, with no planner needed;
- an unmodelled method throws with its name.

## S2. `SimSites`, `ScenarioFinder` (coder; parallel with S0 and S1, core API only)

`SimSites.load(name)` copies `GateWorld`'s rows into `WorldProbes` and the site. `planner()` is `WorldPlanner.forSite`.

The finder works on the plan only:
- windows with the most building and ground pieces near the site centre (SC1);
- spots for SC2 (a), (b) and (c) (box arithmetic with `LakeVeto.box`; it can copy the formula until S0 lands);
- spots for SC3 (a), (b) and (c): street `AIR` boxes whose edge row lies on a chunk line, with the planned surface
  block beside them, and the cut height for (c).

Test (`ScenarioFinderTest`):
- the finder is deterministic (same output twice, and on a fresh planner);
- each case finds at least one spot on gate-temperate-city;
- the spots have the stated properties (asserted from the raster);
- planner build time is logged.

## S3. `OrderSim`, `SimFeatures`, `SimOrders`, `SimCompare`, `SimReport` (coder or lead-coder; needs S0 to S2)

The decoration model, feature placement, orders and checks C1 to C6 as above, plus the report.

Test (`OrderSimHarnessTest`, on SC1 with a 2 x 2 inner window):
- forward run twice gives identical results (the sim itself is deterministic);
- every check passes on the current code;
- the run under 6 orders takes less than 3 s, with the time and loadavg printed;
- one hand-made world change after a run (stone written into a claimed air cell) is caught by C3, so the comparator
  can fail.

## S4a. Scenarios SC1 and SC5, liveness, controls (coder; after S3)

`OrderSimTest`: SC1 and SC5 in the fast order set must pass C1 to C3, C5 and C6. C4 is reported.

Liveness: SC1 fires `SpillClear`'s leaf clear, `StreetAirFix`'s log removal, `BuildingAirClear` and `FoliageClear`
at least once each. SC5 removes snow at least once.

Controls, asserting that the named check fails:
- `SpillClear.ENABLED = false`: C3 (`unknown` in street air), or a liveness fail if SC1 has no rock or leaf in the street;
- `StreetAirFix.ENABLED = false`: C3;
- `BuildingAirClear.ENABLED = false`: C3 (leaves in rooms).

## S4b. Regressions SC2 and SC3, and the mutants (coder; after S3, parallel with S4a)

`OrderSimRegressionTest`:
- SC2 (a) and (b): 0 lakes placed, the veto counted, every check passes. (c): the lake is placed in every order.
- SC3 (a) and (c): pass, with the spill histogram showing `light_gray_concrete->dirt` or `air->dirt` classified
  `spill` in at least one order (pinning the cases).
- SC3 (b): passes, and the write log shows `restoreBase` writing air over dirt at least once.

`OrderSimMutantTest`: each mutant, on its scenario in forward and reverse only, must fail the named check (the test
asserts the failure):

| Mutant | Scenario | Must fail |
|---|---|---|
| `LAKES` | SC2a | C3 `unknown` (lava or cave air in a building) |
| `TRUNK_BASE` | SC3b | C3 `unknown` air (dirt on the street surface) |
| `SPILL_LOGS` | SC3a/b | C3 `unknown` solid (dirt under a removed trunk) |
| `RANDOM` | SC1 | C2 digest differs |
| `COUNTER` | SC1 | C2 ledger against raster |
| `NO_CHUNK_CLIP` | SC1 | C5 `TilePiece` write outside its chunk |
| `SKIP_AIR` | SC1 | C3 claimed air |
| `WORLD_STONE` | SC1 | C3 |
| `EARLY_STEP` | SC1 | C3 (the sim places our structures in `SURFACE_STRUCTURES` under this mutant) |
| `FLAG2` | SC1 | C5 (missing flag 16) |
| `TWICE` | SC1 | C2 duplicates |

Run these two checks once by hand and record them in the log, not committed:
- `claimsNear` without the deep-foundation growth: SC2b must fail C3;
- `SpillKinds.trunkBaseBeside` returning false: SC3a must fail C3, which shows the classifier is the one the order
  test uses.

## S5. SC4 roads (coder; after S3, parallel with S4)

The region planner over `w12345-river-s8`. The finder finds a chunk where `roadColumnsAround` and `districtColumns`
both hold columns. Measure the planner time; if a scenario costs over 5 s, tag it `slow` (see Scenarios).

Test: C1 to C3, C5 and C6 pass. The road digest is the same across orders. Liveness: `roadLeaves` removes a leaf, and
the ground scan passes a leaf or a log at least once.

## S6. `LakeVetoGameTest` (coder; parallel with everything)

A server game test (`@gt/compat` or `@gt/order`; fast, in `:mod:build`). It takes a flat test world with a planned
settlement (the `OrderGameTest` towns) and places the registry's real `lake_lava` feature through
`Feature.place(level, generator, random, origin)`:
- at an origin whose `LakeVeto.box` meets a building: it must return false, `LakeVeto.VETOED` goes up, and the
  building's cells are unchanged;
- at a far-off origin: the veto is not counted.

This is the one check that the mixin is applied, short of the order test's `mutant-lakes`.

## S7. Calibration, docs and test-scope (coder, after S4 and S5)

1. Run `scripts/order-test.sh` and the sim on the same commit. Both must pass. Record both in the log.
2. Then make the tsv and TESTING.md changes above, plus the ROADMAP testing-table note and a NOTES line on the
   sim's run time.
3. `scripts/test-scope.sh --check-table` must stay clean.

## S8. Structure starts seam (after VILLROAD merges; coder)

`VillageRoads.adjust` and `foreignStructureTops` read starts through a small interface. Production reads
`WorldGenRegion`. The sim supplies a fixed list of fake village pieces (bounding boxes), which are known before any
feature runs in every order.

Scenario SC6: a road beside a village. The checks are as for SC4, plus the `VillageRoads.OFF` control.

# Risks and open questions (with the assumption made)

1. **The sim passes when the real game would fail**, because a vanilla behaviour isn't modelled. That is why the
   order test keeps the rows listed and every heavy batch and to-main run. A miss found there becomes a new sim
   scenario, or a corrected tsv row. *Assumed:* that is acceptable, and John asked for exactly this trade.
2. **Order of `freeze_top_layer` against our road feature** in the top-layer step is not verified. *Assumed:* ours
   runs after it (Fabric appends). S3 can check it in a server game test by reading the step's feature list from
   `BiomeGenerationSettings.features()` of a plains biome. If it is wrong, change the sim's order.
3. **Promotion schedule**: inferred, not read from `ChunkPyramid` (its requirements are in lambdas). *Assumed:*
   promotion follows the decoration of all 8 neighbours.
4. **Features in the hashed pool interact with each other**, so C4 is noisy outside `spaced` scenarios. *Assumed:*
   C4 stays report-only there. Revisit once we've seen it on real runs.
5. **Proxy method coverage** grows when a feature calls something new. Unmodelled methods throw by name, so this is
   loud, not silent.
6. **C5's `StackWalker` cost**: about 1 µs per write. It runs only in forward and reverse.
7. **The seams touch files that parallel items edit**: VILLROAD (`RoadFeature`) and BRIDGEENDS (`Grading`, not
   touched here). S0 should land early and small.
8. **Dirt left in claimed air beside a tree (SC3a, SC3c) is accepted as spill**, under ADR 0003 D5 and the order
   test's classifier. The sim makes these cells visible (C4 provenance). Whether to fix them is a design question,
   not this item's.

# Design questions

- (architect) SPILLDIRT's first and 10-23 cases are kept as accepted spill: buried dirt under road concrete, and dirt
  at the top of a street cut's headroom under the hillside's grass. Should the street pass restore the planned block
  there too (`StreetAirFix` already does it for removed trunks)? Default: no; it stays accepted spill.

# S3 as built (2026-10-28)

Deviations from the plan above (all in `mod/src/test/java/dev/fractalbuildings/mod/ordersim/`):

- **Post-process replay stays small.** In 26.3 `getPostProcessPos` is null for all but mushrooms, soul sand and magma, so `SimWorld.promote` replays only those; promotion runs once a chunk and its 8 neighbours have decorated. No bigger replay machine was built.
- **Road passes without R3.** `RoadFeature.placeIn` does not exist yet, so `OrderSim.roadStep` repeats the lines of `RoadFeature.place` before the draw (`SpillClear` where no ground piece, `roadLeaves`, `BuildingAirClear`, `StreetAirFix`). The road draw is not run. When R3 lands (S5/S8), replace `roadStep` by `placeIn` so the two cannot drift.
- **Feature steps are fixed in `SimFeatures`**, not read from the biome JSON. The hashed pool is a stress mix, not biome-accurate (it includes `forest_rock` and `dark_oak` in a temperate city).
- **The structures' step is asked of the real `DistrictStructure.step()`** (built with the JSON's settings), so `early-step` moves it; the run also records an error (C1) when it is not `TOP_LAYER_MODIFICATION`, like the order test's `stepOk`. A 2 x 2 window with k = 3 features did not show `early-step` in C3 on its own.
- **C6 is a fixpoint check**: two more sweeps of the top-layer step (pieces, then road step) over every decorated chunk; the second must change no claimed cell. "Run again, nothing changes" is false after the first sweep, because re-running `TilePiece.write` repairs spill.
- **C5 rules** are in `SimCompare.violation`: flag 16 on every write of ours, a `TilePiece` write inside the decorating chunk, air-only writes for the clears with their old-block and planned-air conditions, `restoreBase` writes exactly the planned block over dirt-family blocks. Writers are found with `StackWalker` in forward and reverse only.
- **Test hooks:** `Scenario.Extra` (test-only work in a chunk's top-layer step, used for the planted order dependence) and `OrderSim.run(.., tamper)` (changes the world before the checks read it).
- **Cost** (loadavg 22 to 28, so not quiet): SC1 2 x 2 window, 6 orders in parallel on 4 threads, 2.2 to 2.9 s; SC1 6 x 6 window (k = 3): 4.1 s parallel, 8.5 s one after another; plan (reference raster) 0.1 to 0.3 s.
