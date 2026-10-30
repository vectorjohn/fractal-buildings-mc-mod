# 6.24 EMPTYCITY: settlements that resolve with nothing in them

Branch `claude/p6-emptycity` (lead-coder; HAMLETEMPTY `claude/p6-hamletempty` merged in for `settlement-empty` and its fixture).

## Question
HAMLETEMPTY found six settlements with no building on the 64-block world dumps: seed 3141592653589793238 city sm1_0 (-384,256 r704;
`/fb locate city` at spawn returns it) and hamlet s0_m6 (384,-5696); seed 1709797029209580947 villages s2_m1, s4_1 and hamlets sm2_m2,
sm1_3. Real, or an artefact of the dump?

## Diagnosis
- sm1_0 on the 64-block dump: the hub is the centre column (-384,256) itself. The dump's sample there is a surface of 64 with the four
  32-block neighbours at 78-83 (bilinear between samples 64 apart), so every A* step from the hub is over the 1-in-3 road grade and all
  five main roads come back empty. With no main road the street network has nothing to flood from the hub, `CityPlanner.plan` drops all
  six arterials (`RoadNetwork.reaches`), and the 49 tiles plan 0 lots. (`city --cell -1,0` without `--districts` shows 6 roads because
  that path does not filter by the network.) The hub column is still "buildable" (3 in 4) though no road can leave it.
- The other five have hubs and roads; their lots fail on the interpolated exact terrain (a river sample floods 64 blocks around it).
- Fine dumps (TerrainDumpTool, step 4 for the city, step 2 for the others, layered over the 64-block world dump): sm1_0 373 buildings
  (hub moves to -384,224, 5 main roads), s0_m6 5, s2_m1 24, s4_1 21, sm2_m2 2, sm1_3 3.
- Live terrain (`LiveProbes`, no server, `WorldConfig.defaults()`, road graph on): sm1_0 50 tiles 355 buildings, s0_m6 5, s2_m1 24,
  s4_1 31, sm2_m2 2, sm1_3 3. The C2 kit's in-game fly-through frame of sm1_0 (`docs/review/phase6/gate-kit/3141592653589793238/`,
  city sweep-a) shows the built city too.

So all six are dump artefacts; no planner change. The pit-hub mechanism could in principle occur on real terrain (a buildable hub column
whose every neighbour is over the road grade), but none was seen: 0 `settlement-empty` on the 9 gate windows and 150 noise sites.

## Changes
- `ProbeSpecs.coarseDumpStep` / `coarseDumpNote` (core): a recorded dump coarser than the 32-block skeleton lattice, and the note tools print.
- `city-check`: `settlement-empty` fails again on fine terrain; on a coarse dump it (and the same empty settlement's hub
  `roads-connected`) is a warning ending "(terrain step 64: likely a dump artefact, 6.24)", listed under "Empty settlements on coarse
  terrain" with the note, and a stderr note. `city` prints the note when it reports `settlement-empty` on a coarse dump.
- `SettlementEmptyTest` comment corrected (sm1_0 has a hub; nothing leaves it).

## Verification
- `EmptySettlementLiveTest` (mod unit test, about 40 s): the six settlements have buildings on live terrain; `/fb locate city` at spawn of
  3141... is sm1_0. Control: the same counts on the 64-block dump are 0 (debug run, `SettlementEmptyTest` rows).
- `CityCheckTest.anEmptySettlementFailsOnlyOnFineTerrain` (coarse sm1_0: exit 0 with the marked warning; an all-water hamlet on
  synthetic terrain: exit 1), `ProbeSpecsTest.coarseDumps`. Mutation: threshold raised to 64 -> both fail.
- `city-check` gates (9 sites) and `--seeds 1..3 --sites cells` (150): 0 failing, 0 settlement-empty.

Note: in mod unit tests biome tags are unbound (NOTES, PARREADS); that only slows biome aliasing, the fine dumps agree.
