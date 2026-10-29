# HARNESSTAGS: bind biome tags in the unit-test terrain harness

Follow-up from PARREADS. `TerrainDumpTool` never bound biome tags, so `BiomeAliases.alias` threw and caught "Tags not bound" on every biome read and memoised nothing; the planner perf rows overstated the game.

## Done
- `mod/src/test/.../testing/BiomeTagBinder`: binds the real vanilla biome tags (loaded from the jar's data pack with `TagLoader.loadTagsForRegistry` over `ElementLookup.fromGetters`) on the biome holders. `MappedRegistry.bindTags` is not usable here: `VanillaRegistries.createWorldLookup()` returns an `EmptyTagLookupWrapper`, not a registry, so it calls the package-private `Holder.Reference.bindTags` by reflection (test code only). Mod tags (`fractalbuildings:no_settlements`) are not in the vanilla pack and stay empty.
- Called from `TerrainDumpTool.boot` and `ordersim/SimBoot.boot` (SimBoot already bound block/fluid tags only).
- Test `BiomeTagHarnessTest`: `Holder.is(BiomeTags...)` does not throw, gives the right answers, and `BiomeAliases` works. Control: before the binder the test failed with `IllegalStateException` (Tags not bound).

## Output unchanged
Planner perf count rows are identical before and after (probes 153,378; chunk fills 4,928; sparse columns 68,343; road columns 14,587; quay probes 597,670; district buildings 15). `ParReadsDigest` digests: see report. No golden changed.

## Perf re-baseline (`scripts/perf.sh --planner-only`, local, NOT quiet: load 35.7 before, 16.3 to 29.4 after)
| row | before (tags unbound) | after |
|---|---|---|
| region cold warm | 37.4 s | 11.1 s |
| quays | 44.8 s | 20.7 s |
| region.warmed.worker | 63.2 s | 25.1 s |
| first tile (district.cold.plan) | 3096 ms | 938 ms |
| district cold median / p95 | 1446 / 2067 ms | 512 / 842 ms |
| veto footprint first | 4372 ms | 8227 ms (load 29 at the end, noise) |
Loads differ between the runs, so the ratio is indicative only; remeasure on a quiet machine before a budget decision.
