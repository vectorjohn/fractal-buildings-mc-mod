# 3.9b: property tests over seeds (S8)

`gen/v0/V0PropertyTest` runs GeneratorV0 through `Invariants.check(scene, terrain)`.

- `drawnCasesPassEveryInvariant`: 2000 seeds (override with `-Dfb.propertySeeds=N`); each case draws lot size {7x7, 12x10, 20x20, 31x17}, front, style, and terrain {flat, plane-x, plane-z, plane-xz, hill, bumpy noise, steep-x, steep-z (1:1 slope), recorded S4 crop at 4 spots} from `Hash64.hash(seed, "case")`. All terrain is kept in y -50..250 (planes are anchored to the lot centre height) so `in-world` is never tripped by the test terrain.
- `fullCrossProductOverFiftySeeds`: 50 seeds x 4 sizes x 4 fronts x 9 terrains x 2 styles = 14400 scenes.
- `mutationsAreCaught`: 3 seeds; removing roof boxes, stairs, doors, or lifting the building 3 blocks each makes `Invariants.check` non-empty.
- Failures print seed, terrain, lot, style and the violation ids/messages plus a `cli gen` replay hint (lot position/terrain are not CLI flags, so use the printed lot).

Result: 0 violations over 16,400 scenes. Runtime about 1.3 s total (load ~ see report), well under the 60 s budget.

Also replaced the test-only `TestVoxels` in `ShellGeneratorV0Test` with `VoxelGrid` and `Invariants` (the walk test now checks the shell placed on flat terrain with no violations); `TestVoxels.java` deleted.
