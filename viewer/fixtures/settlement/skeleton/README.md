# Skeleton fixtures

Cell skeletons written by `fractal-cli city` over recorded terrain, for the plan view (`viewer/e2e/skeleton.spec.ts`).
`CityCommandTest.viewerFixturesAreCurrent` byte-compares them with the writer, so they are regenerated, never edited.
Run from the repo root in the dev shell (`nix develop -c ...`) after `./gradlew :cli:installDist`:

| Directory | Command | Shows |
|---|---|---|
| `river-town/` | `city --seed 12345 --terrain recorded:fixtures/terrain/w12345-river-s8.json.gz --site -900,-3500,town,300 --out-dir viewer/fixtures/settlement/skeleton/river-town` | a town on the river window: main roads over the hilly bank, a bridge, hub market and hall |
| `joined-metropolis/` | `city --seed 12345 --terrain recorded:fixtures/terrain/world-12345-s64.json.gz --cell 6,6 --out-dir viewer/fixtures/settlement/skeleton/joined-metropolis` | a metropolis that claimed 5 cells (MC1 join rule): arterials on tile borders, 14 gates, cell gates between the cells; terrain is the coarse world dump, so heights are interpolated |
