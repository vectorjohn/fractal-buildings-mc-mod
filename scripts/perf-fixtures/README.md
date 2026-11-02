# Perf fixtures

Recorded `mod/build/perf/*.json` from real `scripts/perf.sh` runs, used by
`scripts/perf-check.test.mjs` (`node scripts/perf-check.test.mjs`).

- `mainstub-87869d24d3/`: planner half of the MAINSTUB verification run
  (the perf worker, load 15.2): first tile of a metropolis 59.8 s against a 4 s
  ceiling, which passed as `skip(load)` before the `loadSkipGuard` check.
- `leaforder-ac2f7fc037/`: a loaded full run (planner + world) whose
  skip(load) rows are within their guard (first tile 5.3 s, 1.3x).
