# P5 DT: determinism suite (5.6)

`planner-core/src/test/.../settlement/SettlementDeterminismTest.java`. Cases are the viewer's `city-presets/*.json` with an
`at` site (resolved like the CLI's `--site`; recorded terrain paths made absolute), so presets added by D1, D2, MC2 or TR are
picked up with no edit. Per preset the reference is an in-order `CityPlanner.plan` on a fresh `SkeletonSource.Direct` (settlement,
every district, the `autoScenes(2)` scenes, and the skeleton alone). Compared byte for byte against:

- cells built each from its own fresh source in shuffled order, then `Skeletons.assemble` (two shuffles);
- a fresh source warmed in shuffled cell order, districts planned in shuffled order (two shuffles);
- `CityPlanner.plan` through a fresh source, in order;
- 8 threads, one shared source with a synchronised LRU skeleton cache of 2 cells (evicts constantly; the test fails if it never
  built more cells than the site has), districts, scenes, and the skeleton assembled from concurrently built cells;
- districts planned alone in a new source (10 sampled per preset; all with `-Dfb.det.full=true`), and the scenes alone.

Mutants (kept as tests, run on a 512-cell noise city; each must produce differences): a counter that drops the gates after 40
skeleton calls, a skeleton that loses a road when a neighbour cell was built first, roads ordered by an identity-hash `HashSet`.
A clean-source control must report none.

Verification: all 12 presets pass (default sampled 25 s on a loaded machine; full about 40 s). Real-code mutation: a static counter in `Districts.plan`
that returns empty on every 37th call failed 5 of 6 tests in the suite (the remaining one is the lint-like presets-exist check);
reverted. Lint: `DeterminismLintTest` already walks all of `src/main/java` (so core/settlement, region, probe, style are covered);
added `scanCoversThePhase5Packages` so a package move cannot silently drop them.

No determinism bug found in D0 code; no main code changed.
