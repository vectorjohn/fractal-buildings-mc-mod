
Site ids use the `m` form for negative cells (`s-1_-4` became `sm1_m4` when D0 made `--cell` and `--site` agree); `--scene` is the old spelling of `--scenes`. Whole-city presets for the gate windows live in `viewer/city-presets/`.
# District fixtures

Settlements with district detail written by `fractal-cli city --districts` over recorded or synthetic terrain, for the plan view
(`viewer/e2e/districts.spec.ts`). `CityCommandTest.districtFixturesAreCurrent` byte-compares them with the writer, so they are
regenerated, never edited. Run from the repo root in the dev shell after `./gradlew :cli:installDist`
(`F=cli/build/install/fractal-cli/bin/fractal-cli`, `D=viewer/fixtures/settlement/districts`):

| Directory | Command | Shows |
|---|---|---|
| `river-town/` | `$F city --seed 12345 --terrain recorded:fixtures/terrain/w12345-river-s8.json.gz --site -900,-3500,town,300 --districts --out-dir $D/river-town` | a town on the hilly river window: lots along organic roads and ports, a bridge |
| `mixed-city/` | `$F city --seed 1 --terrain recorded:fixtures/terrain/w1-mixed-s8.json.gz --site 8500,-3300,city,450 --districts --out-dir $D/mixed-city` | a city among lakes (T1 mixed window): lots keep off water, lanes wind around it |
| `synth-city/` | `$F city --seed 1001 --terrain "noise:1:70:7:220+river:-120:24:6" --site 0,0,city,700 --cell-size 512 --districts --out-dir $D/synth-city` | a 5-cell city on gentle noise terrain with a river: arterials, plain border streets, grid lines straight across tiles, whole-block lots, highrises |

District scenes (2 MB each) are not committed. For the 3D view: add `--scene d0_0,d1_0` and an `--out-dir` outside the fixtures (for example
`viewer/out/city/synth-city`), then `FB_SCENE_CITY=$PWD/viewer/out/city/synth-city npx playwright test e2e/districts.spec.ts -g "district d"`
(screenshots in `out/district/dl-*.png`).
