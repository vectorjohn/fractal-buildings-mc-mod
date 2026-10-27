# 7.20 METROFILL M0: fill metrics (report only)

Branch `claude/p7-metrofill-m0`, base `claude/run-phase6` (R1 arterials already in). No planner change; nothing fails.

## What

- `core/check/FillStats` (planner-core): one byte raster of the site disk (road from `SettlementChecks.footprint` of every district street, built lot, unbuilt `empty:*` lot, plaza), measured over dry columns (the plan's coarse raster, like the architect's script). Zones by density band (core 70+, middle 50-69, edge) and ring; frontage (non-road columns beside a road that are lot or plaza) per zone and per road kind; street length by kind clipped to the listing tile; slivers (row and column scan of every line, gaps of 12 or less between roads with no lot or plaza, 4-connected components with a long side of 32 or more); dead ends; `block` lots; median lot depth per type in the core. `checks(Tier)` gives `core-fill`, `road-sliver`, `road-hierarchy` (nothing below a city; slivers only where the density at their middle is 70+).
- CLI: `city-check` now writes a `## Fill` section (tables per band, ring, all, and a per-site line; roof cover % when `--scenes N > 0`), lists the three checks among the design warnings, and takes `--fill-json FILE` (sorted keys). The CLI part is in `cli/FillReport` plus about 20 lines in `CityCheckCommand` (hooks), so it merges separately from ROADJOIN's changes there.
- Baseline: `docs/review/metrofill/fill-before.md` and `.json` (the 9 gate windows, `--scenes auto:0`, 6.6 s wall for all nine, post-R1).

## Tests

- `FillStatsTest` (8): two parallel streets 10 apart and 65 long, empty between: one sliver of 650; a lot between: none; 20 long: none; frontage 32 of 140 edge columns; a street listed by two tiles counts once (60); checks quiet below a city and fire on a bare arterial-only core; dead ends of a T (3); JSON stable.
- `FillStatsParityTest`: ring table of `synth-city` within 1 point of the prototype (node numbers pinned in the test).
- `CityCheckTest.cityCheckReportsFillAndItsChecksOnlyWarn`: section present, exit 0, `--fill-json` byte-stable.
- Mutation: tile clip replaced by an unbounded rect: the two-tile length test fails.

## Decisions and differences from the prototype

- Water and density come from the plan's coarse raster (`plan.coarse()`), not a separate `DensityField` argument: same field the prototype reads, no extra plumbing.
- Roads are the districts' streets only. The prototype also rasterises `settlement.roads`, which adds about 1 point of road in the core ring of synth-city and takes plaza columns; the parity test pins the prototype with that line removed.
- A plaza between two roads fills a gap (the prototype only counted lots), and every row and column is scanned (the prototype samples every 4th).
- Dead ends use distinct street geometry (a street listed by two tiles counts once), centre-line distance <= width/2 + 2.
- Only `empty:*` lots are "unbuilt"; the prototype called them "steep".
- Sliver violation lists only slivers whose middle has density 70+ (DS-47 is about the core); the stats count all of them.
