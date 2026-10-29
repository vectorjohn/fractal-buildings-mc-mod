# ORDERSIM S4a report (2026-10-28)

## Result: done
## Did
- mod/src/test/.../ordersim/OrderSimTest.java: SC1 (gate-temperate-city, densest 6x6, k=3) and SC5 (gate-snow-city, 4x4, k=3, freeze_top_layer) in the six fast orders: C1-C3, C5, C6 pass; C4 printed. Liveness asserted. Three ENABLED=false controls.
- OrderSimRockTest.java: pinned forest_rock case (finds the culprit call from the full pool on SC1 4x4, replays it alone, spaced); asserts today's C3 failure; `FIXED` flag for ROCKVETO to flip.
- Hooks (test-only, additive): Scenario.city / SimFeatures.hashed / pool overloads with an excluded-keys set; SimCompare.contracts also counts `Class.method/old-kind` (leaf, log, snow, vine, air, other) in SimRun.branchWrites.
- forest_rock excluded from SC1/SC5 pool (not classified as spill). Plan "S4a as built", log docs/log/2026-10-28-ordersim-s4a.md, NOTES lines. ROADMAP not ticked.
## Commits
- 1c10f931 tests and hooks; 59e09f4c docs; da373ac6 NOTES flake line; 737c6d4e merge of run-phase6 (S4b)
## Verification
- `:mod:test --tests '*OrderSim*'` -> pass (harness 4, rock 1, OrderSimTest 5). Cost on loaded host (loadavg 8-13): SC1 6 orders 4.7 s, SC5 2.3 s, controls ~4 s, rock ~3-7 s; classes 16-19 s + 7 s test time.
- `scripts/evidence.sh p6-ordersim-s4a scripts/anywhere.sh -- scripts/test-all.sh --no-client` at 59e09f4c75 (run-phase6 1a7a29d4, merged already), ran on beebox (1h11m, busy): cli 37, determinism 17, mod 80/84 + 36 game tests, viewer 142 pass; planner-core 903/912 with 1 failure: WorldPlannerOrderTest.eightThreadsTinyCachesAndAnEvictor TimeoutException (known load flake in NOTES). Identical local rerun of that class alone: pass. Evidence .claude/reports/p6-ordersim-s4a-evidence/59e09f4c75-20261028T081013Z. Later commits are NOTES only.
- heavy: none (no client layer needed; test-only change).
- flakes: WorldPlannerOrderTest.eightThreadsTinyCachesAndAnEvictor timeout on loaded beebox (recorded in NOTES).
- output looked at: reports in mod/build/ordersim/*.report (printed: SC1 differing claimed cells vs forward 45-134, SC5 34-186, all spill; SC5 reverse has 132 air->snow cells on street edges, accepted spill).
- mutation checks: controls are themselves the mutations (SpillClear/StreetAirFix/BuildingAirClear off: residue 0 -> 4 / 11 / 27); rock test shows the unknown solid cell with the rock as last writer (forward, shuffle-2, spiral C3; reverse/shuffle-1/checker C4).
## Not done / blockers
- Controls do not fail C3 as the plan said: SpillKinds.airKind accepts leaves, logs and snow in air as spill, so passes switched off only add spill. Controls assert bigger residue instead. A true check-based control would need a stricter classifier or spaced scripted trees (C4). Plan updated.
- SC5: StreetAirFix's hanging-snow branch did not fire; snow is removed by SpillClear.run/snow (131 cells), liveness asserts either.
## Design questions
- none (no generator change)
## Notes for the coordinator
- ROCKVETO coder: set OrderSimRockTest.FIXED=true when the veto is in production and in SimFeatures.place; optionally drop NO_ROCK in OrderSimTest to put rocks back into SC1/SC5.
- S4b: my edits to shared files are additive (SimCompare.contracts one extra branchWrites line + oldKind helper, Scenario/SimFeatures overloads); union merge should be clean.
- Fast-suite cost ~25 s of :mod:test on a busy host.
