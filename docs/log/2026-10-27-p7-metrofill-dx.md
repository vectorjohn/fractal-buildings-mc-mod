# METROFILL DX: diagonal switch, inert by default (2026-10-27)

Plan: [metrofill.md](../plans/metrofill.md) step DX. John (2026-10-26): fill up to the diagonals first; removing them is a later switch he decides (DX2). This step adds the switch only; the default is today's behaviour.

## What
- `PlanOptions(boolean diagonals)`, `PlanOptions.DEFAULT` (on); `TierPrograms.of(Tier, PlanOptions)` (`of(Tier)` delegates). Off gives separate `CityProgram(false)` / `MetropolisProgram(false)` instances; other tiers ignore it.
- `TierProgram.diagonals()` (default true). `CityProgram` off: `directions` uses axis directions only (a metropolis offers 4 avenues, not 8), `octilinear` offers a straight run or an x-then-z / z-then-x two-leg route. `DefaultProgram.route` and the four `LatticePath.find` calls in `Districts` (quay, port, plaza, frontage lanes) pass `diagonals()`.
- `LatticePath.find(..., boolean diagonal)`: 4 neighbours and the Manhattan heuristic when false; the old signature is the diagonal mode.
- CLI `--diagonals on|off` on `city` and `city-check` (`CityCommand.planOptions`). `sample-city` builds no skeleton (fixed sample), so no flag there. Viewer city preset key `"diagonals": "off"` (omitted from the CLI args when on, so default args are unchanged); `docs/VIEWER.md` updated.
- Region roads (ADR 0004), `LinkExits` and `RoadNet` untouched (they keep their own `find` calls).

## Verified
- Inert: default `city --districts` output byte-identical (diff -r) against the run-phase6 base (a95bf617) on gate-temperate-city (35 files), d3-city-avenues (57) and gate-metropolis-mixed (the whole tree). No golden or fixture was touched.
- `DiagonalSwitchTest`: default options return the same program instances; off, 30 synthetic city/metropolis sites have no diagonal road step and no skeleton violations (control: with on, at least 5 have one); off districts of 5 sites have no diagonal street, no violations, and the same JSON in forward and reverse tile order. `LatticePathAxisTest`: 4-neighbour path has only axis steps and Manhattan length. Vitest: `diagonals` preset key.
- Mutations: forcing 8 neighbours in `LatticePath`, or passing `true` in the `Districts` calls, fails `DiagonalSwitchTest`/`LatticePathAxisTest`.
- Off on gate-metropolis-mixed: `city --diagonals off --check` gives 0 violations (9.5 s). plan.png on/off in `.claude/reports/p7-metrofill-dx-evidence/`.

## Look
Off: the eight-avenue star becomes the four axis avenues, and the diagonal skeleton mains toward the links (A* fallback) become 4-neighbour staircases, which look jagged (top-left of the plan). For DX2: consider an L-route (two long legs) instead of a staircase for off mode.
