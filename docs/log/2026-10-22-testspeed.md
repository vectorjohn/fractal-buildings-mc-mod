# TESTSPEED: faster planner-core tests

`planner-core/build.gradle`: `maxParallelForks = min(6, cpus/3)` (= 6 here), `maxHeapSize = 1g`.
Gradle distributes test classes over fork JVMs; each class stays in one JVM, so the in-JVM
determinism comparisons are unchanged.

Audit: no test uses static mutable state across classes (CityProgramTest and V0PropertyTest cache
in their own class statics only). Fixed output paths (`build/districts`, `build/raster`,
`build/golden/region`, ...) each have a single writing class and use createDirectories, so no collision.

Result: 603 tests before and after, ~10 min serial -> about 4.3 min (cold, `--rerun-tasks`, incl. compile).
The floor is the single longest class (SettlementDeterminismTest ~138 s, DistrictsTest ~118 s).
Tried JUnit method-level concurrency on those two: no gain under load 15 (other agents), reverted.
No quickTest task added. Three consecutive runs after merging run-phase6 (606 tests, 0 failures each, load 8-15 from other agents): 352 s, ~340 s, 337 s wall incl. compile.
No quickTest task added. Three consecutive full runs after merging run-phase6 (606 tests, 0 failures each, loadavg 8-15 from other agents): 352 s, ~340 s, 337 s wall, incl. compile.
