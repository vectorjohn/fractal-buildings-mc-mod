# JT: jetties over streets (2026-10-21)

Branch `claude/p5-jt-jetties`, plan `docs/plans/phase5.md` step JT.

**What**
- `BuildingSpec.lead` (spec JSON `lead` only when > 0): rows of envelope in front of the type's front margin. `SpecPolicyV1` sets it from `Brief.airRights` for house and shop in styles with jetties, 2+ storeys, known `frontY`, lot ground >= `frontY` (so slab bottom >= frontY + 4 >= the required 3).
- `InstancePassV1` moves the envelope origin forward by `lead`; ground masses keep the usual place (`Massing.shiftedZ`). `HouseMassing` pins the front wall to the usual front edge and draws a front overhang of margin + 1..air (75% chance); `ShopMassing` does the same for its jetty (air jetty replaces the 1-block one); arcades and market halls stay inside the lot, and arcade posts are skipped for a jetty past the lot edge.
- `Lot.airRights/frontY` (written when > 0, `GeneratorV1` stamps them from the spec); `Invariants.inLot` accepts voxels in the front strip at world y >= frontY + 3 (`Lot.allows`).
- `MassingCheck.require/check` take `frontMax` (M5): 2 by default for ShellGenerator, 3 for shops and air-rights buildings (was 3 for all).
- CLI `gen|batch|check|bench --air N`, gallery presets `"air": N`, viewer parses `lead`, `airRights`, `frontY`; docs/IR.md updated.

**Verified**
- `JettyTest` (7 tests: 640 generated scenes with 0 invariant failures, overhang only in the front strip, never below frontY + 3, never deeper than air; no brief / other style / other type / street above the ground means no lead; JSON round trip; determinism). Mutants killed: dropping the y clearance from `Lot.allows` fails 1 test; allowing the strip on every side fails 3.
- Whole `:planner-core:test`, `:cli:test`, viewer `npm test` (99), `:mod:build` incl. game tests: pass. Golden digests unchanged (no brief = old bytes), so nothing regenerated.
- `check --seeds 0..60 --types house,shop --styles medieval --air 2`: 7808 cases, 0 failing; `--air 1/2` with plain and plains also 0.
- Galleries `house-medieval-jetty` (air 1) and `shop-medieval-jetty` (air 2), 16 seeds: 0 violations; 9/16 houses and 6/16 shops reach past the lot edge (before: 0, no brief). Viewed `front-left` and `top`: overhangs show the bressumer; copies in `docs/review/phase5/jetties/`.

**Surprises**: the first sweep failed `in-lot` on shop arcade posts standing in the street (`Shopfront.arcade` treats any front jetty >= 2 as an arcade): fixed by skipping posts past the lot edge. East/west fronts need the lot's w and d swapped in tests (`SpecPolicyV1` reads w along the front).

**Known issues**: plains has `jetty 0`, so only medieval uses air rights (plan said plains too). Only the main stack's front jetties use the strip; other types (workshop, hall, block) keep inside. The viewer draws no lot or street outline, so overhang amount is judged from `top`/`front-left` views only.
