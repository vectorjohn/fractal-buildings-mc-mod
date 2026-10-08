# Run 2026-10-08: phase0-research

## Scope
- 2026-10-08 John: "Do Phase 0 and start the Phase 1 research tracks"
- Read as: Phase 0 items 0.1, 0.3–0.6 (0.2 done); Phase 1 tracks 1.1–1.6 run in
  batches through their starting lists; tell John at 8–10 notes (1.7 early skim).
  1.8 synthesis not in scope unless John says (tracks would need to be covered first).

## Setup
- Run branch `claude/run-phase0-research`, worktree
  `/home/agent/fractal-buildings-mc-mod/.claude/worktrees/run-phase0-research`
- Base commit 9fb9dc8 (main). Parallelism cap: 5 coding items.

## Dependency picture
- 0.1 split: 0.1a Gradle (planner-core, cli, mod) ‖ 0.1b viewer skeleton. Ready.
- 0.3 templates/index: ready (docs). Research notes use the template spec from ROADMAP 0.3 until it lands.
- 0.4 smoke path: waits 0.1a + 0.1b.
- 0.5 mod hello world + game tests: waits 0.1a.
- 0.6 test-all.sh: waits 0.1a (+0.1b); runs alongside 0.4/0.5, skips layers not yet present.
- Track 4 (worldgen internals): source reading after 0.1a (Loom present, cache warm); probe tests after 0.5.
- Tracks 1, 2, 3, 5, 6: ready now. INDEX lines need 0.3 merged.
- 0.1 checkbox ticked by coordinator once 0.1a and 0.1b are both merged.

## In flight
(item | agent | id | started | branch | base | notes). Worktrees `.claude/worktrees/<branch minus claude/>`, reports `.claude/reports/<slug>.md`.
- 1.5c terralith revisit | assistant | a9c9b401acd16f969 | 01:36 | claude/research-terralith-revisit | 1d8c943 | rewrite terralith.md from datapack under John's overview-only rule
- 1.4 worldgen-2 | researcher (resumed) | a157a165bdcab6409 | 01:42 | claude/research-worldgen-2 | 284fc15 | chunk_surface_level + wrap_noise_router, keeping villages out, instanceof NBCG + procedural pool element, probes; ARCHITECTURE primer corrections
- 1.3 pcg-4 | assistant | aa95f9497bdb6acf0 | 01:42 | claude/research-pcg-4 | 284fc15 | Greuter 2003, Bao/Wu floor plans, Nystrom rooms-and-mazes; final track 3 batch

## Items
| id | item | branch | base | status | result | notes |
|---|---|---|---|---|---|---|
| 0.1a | Gradle multi-project (planner-core, cli, mod) | claude/p0-gradle | 9fb9dc8 | done | 08c4955 (verifier a336d21fd88b3be9d COMPLETE 00:29) | coder ae5b5458ee0fb8e88 done 00:22; MC 26.3, Loader 0.19.5, FAPI 0.162.0+26.3, Loom 1.18.3, Gradle 9.7.1; mod id fractalbuildings, pkg dev.fractalbuildings.*; jar-in-jar |
| 0.1b | viewer skeleton (TS + three.js + test runner) | claude/p0-viewer | 9fb9dc8 | done | c03006f | coder acb409d3609e1c20b done 00:20 (no report file written; log used). COMPLETE; known issue: irToVoxels ordering test only varies x (polish) |
| 0.3 | templates + INDEX | claude/p0-templates | 9fb9dc8 | done | run 733cba5, main e547a03 | a07d6da76725f60fd finished 00:19; docs-only merge by coordinator |
| 0.4 | smoke path CLI→IR→viewer→Playwright | claude/p0-smoke | f692f7e | done 29fe961 (verifier ac8324a7d5a83fbec COMPLETE 01:14) | b04b016, e264e22, e81f12d | coder a12bee4714d8956af done 01:04; flake: playwright-driver.browsers; @playwright/test 1.63.0 pinned |
| 0.5 | mod hello world + server/client game test | claude/p0-mod-hello | f692f7e | done 27034cc (verifier a21e190951ddbb89f COMPLETE r2 01:45) (coder r2 done 01:29: eula removed, screenshots copied to mod/build/screenshots, IrJsonDeterminismTest; full test-all L1–L5 OK) | ec63e6e, 049e871, d878c51 | coder a563a8d2fd9c44db1 done 00:57; set eula=true in Loom configureTests → asked John; no report file |
| 0.6 | scripts/test-all.sh | claude/p0-test-all | f692f7e | done | 19af8f3 (verifier a0a7f20f0a21457ba COMPLETE 00:58) | assistant af54f0e027db1e803 done 00:51; 0.4/0.5 layer pickup untested → full run after both land |
| 1.1 | Track 1 mods, batch 1 | claude/research-mods-1 | 9fb9dc8 | done | run 1f0c13f, main 2ce5266 | a2d4310290faa412e 00:39; 4 notes; Lost Cities, YUNG's, Overhauled Village, Towns and Towers |
| 1.2 | Track 2 GDMC, batch 1 | claude/research-gdmc-1 | 9fb9dc8 | done | run 37dbd96, main 0d54582 | a09cbc9afda1ed35e 00:45; 4 notes; batch 2 running |
| 1.3 | Track 3 PCG, batch 1 | claude/research-pcg-1 | 9fb9dc8 | done | run d768e0b, main 9596589 | a5f8d29147312d00c 00:36; 4 notes; added poppler-utils to flake.nix (list for John); Parish-Müller, CGA, split grammars, WFC |
| 1.4 | Track 4 worldgen internals, batch 1 | claude/research-worldgen-1 | p0-gradle | done | run dfe7446 (cherry-picked), main da341e4 | a157a165bdcab6409 01:38; 4 notes + NOTES; all primer hypotheses hold; NEW: biome lookup order-dependent at climate ties (priming fixes); structure-based only viable option; structure pipeline, height/biome queries, jigsaw, extension points; probes after 0.5 |
| 1.5 | Track 5 overlays, batch 1 | claude/research-overlays-1 | 9fb9dc8 | done | run d768e0b, main 9596589 | ab4430f66348ff1aa 00:36; 4 notes; starting list covered; Terralith licence question for John |
| 1.6 | Track 6 aesthetics, batch 1 | claude/research-aesthetics-1 | 9fb9dc8 | done | run eafa02b, main ede9e6e | af105d6d90dd69247 00:45; 4 notes; starting list covered; batch 2 running |
| 1.5b | Track 5 overlays, batch 2 | claude/research-overlays-2 | 1f0c13f | done | run 7f675ae, main 9b2608d | a2c55c25680d19929 01:04; 3 notes; track 5 covered (list + finds). Leftover finds → track 4: Lithostitched wrap_noise_router semantics, 26.3 preliminary_surface_level |
| 1.3b | Track 3 PCG, batch 2 | claude/research-pcg-2 | 1f0c13f | done | run 13752b5, main a9d7cbf | a0512de05d1e19280 01:08; 5 notes: Townscaper, Watabou, Merrell, Lopes, MarkovJunior; INDEX lines in scratchpad pcg2.txt. Batch 3 candidates: Chen 2008 tensor streets, Vanegas 2012 parcels, Kelly-McCabe survey, Bao 2013/Wu 2018, Marson-Musse treemaps, Hahn 2006, straight skeleton roofs, Nystrom rooms-and-mazes |
| 1.1b | Track 1 mods, batch 2 | claude/research-mods-2 | 1f0c13f | done | run f216943, main dabd3bf | a6b3a2f3719029118 01:10; 4 notes; track 1 starting list covered → closed. Unfollowed finds: Moog's Missing Villages (AGPL), RC Volts (closed), StructureTutorialMod, Cristel Lib, Arilas LC port, Dungeons and Taverns |
| 1.2b | Track 2 GDMC, batch 2 | claude/research-gdmc-2 | 37dbd96 | done | run 9dabe60, main 8783b84 | afdebb4d353bd4292 01:12; 5 notes; track 2 closed. Unfollowed finds: Field Lab Gamma 2023, DustBeam 2025, Slothlab 2025, Hervé-Salge FDG 2021 metrics paper, Terrain-adaptive PCGML CoG 2024 |
| 1.6b | Track 6 aesthetics, batch 2 | claude/research-aesthetics-2 | 37dbd96 | done | run 959bdaa, main 633b78e | aa79b1225a8898087 01:22; 4 notes; track 6 closed. Unfollowed: class/wealth style axis, quoins/lintels/arches at 1-block scale, dormer catalogue, Colorflow/Blockpedia |
| 1.3c | Track 3 PCG, batch 3 | claude/research-pcg-3 | 959bdaa | done | run 284fc15, main 0fa8ea0 | a286e03f6a08b9487 01:40; 4 notes: tensor streets, parcels, straight-skeleton roofs, Kelly-McCabe |

## Gates
- Phase 0: builds green; viewer renders the box. Package: `scripts/test-all.sh` all 7 layers pass (L1 planner-core 1/1, cli 5/5, viewer 6/6; L2 determinism 3/3; L3 viewer/out/smoke.png; L4 server game tests; L5 client mod/build/screenshots/hello-hut.png on lavapipe). Look at: viewer/out/smoke.png (run `cd viewer && nix develop -c npm run screenshot`), hello-hut.png, the build/flake/package files listed in the to-main merge message. Status: awaiting John (told 01:47). Merge to main: pending verifier.
- Phase 1: John picks building direction + integration approach (not reached this run).

## Rules and decisions
- 2026-10-08 00:27 coordinator: pending Polish backlog lines (add after 0.1a merge commits): 0.1b irToVoxels ordering test only varies x; 0.1b `npm run dev` never run.
- 2026-10-08 00:27 lesson: never edit tracked files in the integration checkout without first checking `git status` for an in-progress merge (I staged a ROADMAP edit into the 0.1a verifier's squash merge; reverted). Verifier seemed not to hold merge.lock across its conflict.
- 2026-10-08 00:38 coordinator: run branch now holds code, so research notes reach main via a throwaway branch off main with `git checkout <run> -- docs/research` (scratchpad merge-research.sh). Same content lands again at the phase merge; no conflict expected.
- 2026-10-08 00:40 coordinator: Stardust Labs licence (Terralith) forbids using its code to "configure, test, debug, or augment" AI systems → agents don't read Stardust Labs code/data, public pages only, until John decides (ASSUMPTIONS row).
- 2026-10-08 00:40 Polish backlog lines for 0.1a/0.1b committed (7d0c49f); REVIEW 0.1a licence item.
- 2026-10-08 00:40 1.7 early skim: 12 notes on main → told John.
- 2026-10-08 00:58 0.5 sets `eula=true` (Loom fabricApi.configureTests) for game-test runs; CLAUDE.md says ask John → merge held, asked John. Verifier checking whether tests run without it.
- 2026-10-08 01:09 second verifier lock lapse: 0.4 verifier squash staged in integration checkout while merge.lock was free. Fix verifier.md (hold one flock across merge+commit+tests) via an assistant after this phase.
- 2026-10-08 01:16 0.5 verifier a21e190951ddbb89f: COMPLETE; eula=true unnecessary (only writes eula.txt in clientGameTest run dir; both tests pass without) → removing it instead of asking John. Also :mod:build wiped client screenshot dir.
- 2026-10-08 01:26 merged assist verifier-lock (03e7628): scripts/squash-merge.sh does check+squash+commit+tests under one flock; verifier.md forbids per-command locking and resolving any conflict (reset instead); coder.md requires report file (harness's generic "don't write report files" line was the cause). Until sessions restart from main with these files, brief each verifier: "merge only via scripts/squash-merge.sh in the integration checkout, one call".
- 2026-10-08 01:35 John: (1) reading Stardust Labs/restricted code for a general overview is OK, never copy; (2) unlicensed / MIT-with-conditions = ideas only; (3) mod licence MIT, author John. Recorded in ASSUMPTIONS + REVIEW.
