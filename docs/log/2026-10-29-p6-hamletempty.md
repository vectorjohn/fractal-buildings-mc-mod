# 6.23 HAMLETEMPTY: a hamlet with no building

Branch `claude/p6-hamletempty`. Found by the C2 gate kit: seed 3141592653589793238, hamlet s0_0 at (384, 640), r47.

## Diagnosis
`fractal-cli city --cell 0,0 --roads --districts` on the kit dump (`scripts/playtest-kit.sh` terrain dump, now `fixtures/terrain/pt3141-world-s64.json.gz`):
2 districts, 0 lots. Without `--roads` (or with `--site`) the same hamlet has 2 lots. So the cause is the road graph, not the
neighbouring city's claim or the DOORBANK `empty:bank` rule (that runs at scene time; the lots were already 0 in the plan).

`Skeletons` adds a required exit per link port and drops a hashed (here: the straight street's own) exit within 3 lattice steps of
it. For the hamlet the two link exits were diagonal from the hub; `StraightProgram.mainRoads` routed them by A* as diagonal paths,
and the street's own ends were dropped as redundant. The hamlet's lots hang on the straight main street, and its only buildable side
was the one the diagonal paths crossed: 0 lots.

## Fix
`StraightProgram.viaStreet`: a road to a non-street target runs hub -> nearer street end -> (45-degree leg, then axis leg) -> exit,
when each leg is dry and gentle (`straight`); otherwise the old A* route. The street keeps its lots (3 lots, 0 violations now).
New rule `settlement-empty` (`SettlementChecks.settlementEmpty`, run by `CityPlanner.check`, so `city-check` and `city` report it).

## Verification
- `SettlementEmptyTest` (planner-core, world package): s0_0 has >0 buildings through `WorldPlanner` with the road graph on (was 0 on the
  old code: failed with `Row[id=s0_0, tiles=2, buildings=0]`); every hamlet and village of the seed's 64-block dump has a building;
  the rule unit test.
- `city-check --seeds 1 --sites gates:fixtures/terrain/gate-sites.tsv`: 9 sites, 0 failing (0 settlement-empty).
  `city-check --seeds 1..3 --sites cells` (noise terrain): 150 sites, 0 failing.
  `city-check` on the 64-block dump of the seed: 2 settlement-empty (see below). `city-check` has no road graph, so it cannot see the
  hamlet bug itself; the world test is the guard for that.

## Findings, not fixed
On the coarse (64-block) dumps a few settlements resolve with nothing in them even without road links: seed 3141592653589793238
city sm1_0 (-384, 256, r704; `DefaultProgram.hub` finds no owned urban column, so no hub, 0 roads, 49 empty districts) and village s0_m6;
playtest-1709 villages s2_m1, s4_1, hamlet sm2_m2, sm1_3 (CLI `city --cell` agrees). They may be artefacts of the coarse dump (the game reads
exact terrain), but a settlement the region layer resolves with no urban ground is the same player-visible symptom: needs a look with
a fine dump or in game (`/fb locate city` at spawn of that seed points at sm1_0). Candidate fix: region footprint and skeleton density
disagree; resolve only sites with a hub. Overlaps DEADPORT/WorldPlanner; not touched.

`city-check` lists settlement-empty as a design warning (not failing): the existing CityCheckTest world-map tests (coarse dumps) have 5 such sites in 150 and would fail otherwise. `:cli:test` passes with that.

Tests not run: client-full, perf-planner, city gallery, order (heavy; the change only re-routes link roads of hamlets/villages).
