# Explainer page data

How `docs/review/explainer/index.html` ("Inside the City Planner") is built. The page is one self-contained HTML file
(published as a private Artifact page; SilverBullet won't render it). Everything on it about metropolis `s2_m6`
(seed 987654321, 2432,-5696, r 1430, the city of every perf row) comes from these inputs.

| Input | How it was made | What the page uses it for |
|---|---|---|
| `roads-987.json` | `fractal-cli roads --seed 987654321 --terrain recorded:fixtures/terrain/world-987654321-s64.json.gz --out roads-987.json` | region cells (reason, roll, claims), sites, 64-block terrain, intercity links, meeting points, dropped pairs |
| `s2m6/` | `fractal-cli city --seed 987654321 --terrain recorded:fixtures/terrain/gate-metropolis-temperate.json.gz --site 2432,-5696,metropolis,1430 --districts --out-dir s2m6` | skeleton (coarse mask, quarters, cells, main roads, gates), 220 district tiles, streets by kind, quays, 4,065 lots |
| `fixtures/terrain/gate-metropolis-temperate.json.gz` | committed fixture | 16-block terrain under the city |
| `reach-units.tsv` | `.claude/reports/profile-evidence/reach-7ba928e426/` (forensic, gitignored): `PerfReachTest` at 7ba928e4, live vanilla noise, `-Dfb.perf.reach=1` | every noise unit (chunk fill or column) of one cold plan, by step and purpose, at chunk resolution |
| `explainer-dump.json` | `ExplainerDumpTest.java.txt` copied into `planner-core/src/test/java/dev/fractalbuildings/core/world/ExplainerDumpTest.java` (throwaway, never merged), then `scripts/dev ./gradlew :planner-core:test --tests '*ExplainerDumpTest*' -Dfb.explainer.out=FILE` | junction pins (435), junction-field chunks, whole quay trace chunks (reference path), each of the 211 waterfront tiles' lazy quay chunks from a fresh source, centre-tile chunks; recorded terrain, counts only |

Then:

```sh
scripts/dev node scripts/explainer/build-data.mjs roads-987.json s2m6 \
  fixtures/terrain/gate-metropolis-temperate.json.gz reach-units.tsv explainer-dump.json data.json
scripts/dev node scripts/explainer/build-page.mjs data.json docs/review/explainer/index.html
# screenshots (from viewer/, after npm ci): desktop and phone, light and dark, every timeline step
cd viewer && ../scripts/dev node ../scripts/explainer/shoot.mjs ../docs/review/explainer/index.html OUTDIR steps
```

`template.html` holds the page (CSS, text, drawing code); `build-page.mjs` draws the architecture SVG and inlines the
data and `docs/review/road-refine/profile-r3_m2_2.png`. Timings on the page are copied by hand from the quiet-perf-worker
sources it cites (PERFBASE section 7 of `docs/review/regionperf-decision.md` on `claude/p6-perfbase`, HEAVY10,
PERFORMANCE.md PARREADS); nothing here measures time.
