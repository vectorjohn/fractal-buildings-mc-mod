# History workflow: dry run

The test for [history-workflow.md](history-workflow.md): `scripts/promote.sh` run against the real branches, first on 2026-11-02 and again on 2026-11-05 after step H2 of [the workflow-history plan](../plans/workflow-history.md) (self-test cases, `scripts/promote-overrides.tsv`, see below). Nothing was merged, pushed or rewritten; the results are three throwaway branches you can browse with `git log`:

- `claude/promote-dryrun`: what `main` would get from Phase 6 (`main..claude/run-phase6`, run branch at 2db7b04c), built with `scripts/promote.sh --dry-run --replace --run claude/run-phase6`.
- `claude/promote-rewrite-dryrun`: what today's `main` (Phases 0-5 and the earlier Phase 6 merges) would look like rebuilt the same way, from the first commit: `scripts/promote.sh --replace --base $(git rev-list --max-parents=0 main) --run main --branch claude/promote-rewrite-dryrun`.
- `claude/promote-publish-dryrun`: the two together, the `main` you would publish: the rewrite above with Phase 6 stacked on it (`scripts/promote.sh --replace --run claude/run-phase6 --onto claude/promote-rewrite-dryrun --branch claude/promote-publish-dryrun`). 344 commits in all (the first commit, 84 for Phases 0-5, 259 for Phase 6), against 575 on `main` today plus 1072 more after a plain `--no-ff` merge of Phase 6; its tree equals `claude/run-phase6` except that `docs/runs/` is gone (the rewrite drops the old ledgers, 7 files).

Delete them with `git branch -D claude/promote-dryrun claude/promote-rewrite-dryrun claude/promote-publish-dryrun`.

In all three, `docs/runs/` (the run ledgers) is kept as it is on the commit the new history starts from (option A1 in the proposal; so the rewrite has none), and the final tree was checked identical to the source branch everywhere else.

# What H2 changed (2026-11-05)

- **Subjects.** `scripts/promote-overrides.tsv` gives a `type(scope): summary` subject to every commit the first dry run listed without an item code (20 on the old `main`, 32 in Phase 6) plus three Phase 6 merges since (the history proposal, W1+H1+H3, W4+W5). Where there is no item code the scope is the area (`process`, `remote`, `test`, `design`, ...); the two whole-phase squashes (Phases 0 and 4) have none. Every row could be classified from its diff and merge message; none waits on you. "Needs a look" went from 32 to 0 (Phase 6) and from 20 to 0 (rewrite). Subjects already in `type(CODE): summary` form, which the verifier writes from now on (H1), are kept as written, so the file should rarely need new rows.
- **No more reverting `main`-only work.** The first dry run's first Phase 6 commit deleted `scripts/remote.sh` and your two screenshot utilities (they were on `main` but not yet merged into the run branch at that point), and a later commit put them back. Now each promoted commit is built on top of those `main` changes (`git merge-tree`); in 62 of the 67 affected commits five files changed on both sides (CLAUDE.md, `coder.md`, `verifier.md`, `client-test.sh`, `test-all.sh`) and take the run branch's version until the run merged `main`, which is what the old history did too.
- **Safer overrides.** A row naming a commit the run reaches but does not walk (a feature-branch commit inside a merge, which would never apply) is refused; rows for commits already on `main` or on another branch are skipped and counted, so one file serves both the rewrite and the Phase 6 promotion. A commit that only edits the overrides file is bookkeeping.
- **Self-test** (`scripts/promote-selftest.sh`, temp repos, a few seconds): adds promotion after a rewrite plus the `git merge -s ours main` reconnect, docs-feedback merges, an override outside the walked range (refused), unmerged `main` changes with a conflicting file, and semantic subjects passing through.

# Phase 6 promotion

- base: main (934626b4), run: claude/run-phase6 (2db7b04c), range: 934626b406fffd37d3e63b1eb43273f271da52af..2db7b04c6ebca4fe9386f847ba8bbb6104414817
- branch written: claude/promote-dryrun (06a80217)
- run first-parent commits: 693 (code 217, docs 106, bookkeeping 370)
- promoted commits: 259 (empty groups dropped: 0)
- excluded paths (kept as on the commit it stacks on): docs/runs
- overrides: scripts/promote-overrides.tsv (35 applied; skipped: 20 already promoted or base history, 0 for other runs)
- groups built on main changes the run had not merged yet (kept, not reverted): 67
- of those, 62 took the run's side in files changed on both sides (main's lines arrive with the run's merge of main): .claude/agents/coder.md .claude/agents/verifier.md CLAUDE.md scripts/client-test.sh scripts/test-all.sh
- tree check: PASS (identical to claude/run-phase6 outside: docs/runs)

## Needs a look (0)

none

## Proposed history of the 259 commits (oldest first)

```
0aeb7a55 feat(roads): roads between settlements, planner half (road graph, port tag, approaches, roads CLI)
d2b4d4a1 feat(SRVONLY): unmodded client can join a server running the mod
6eba4cb1 feat(B2): in-world chunk-order determinism, hook readback, spill classes
204ff023 feat(BEDFIX): both bed halves written by whichever chunk generates first
4139faef feat(B7): vanilla-structure veto, biome aliases, terrain-mod compat matrix
a8d4b7aa chore(client-test.sh): watchdog kills a hung client and always frees the heavy lock
f6808338 docs: research: vanilla-block furniture idioms
8ca44394 feat(JUNCTION): r3 + 6.10 C1 + SPILLDIRT: junction heights, intercity roads, B2
3b39c676 feat(STAIRS): interior flights reach the next floor, no one-block jump
0131c4d4 feat(PALETTE): calmer style palettes, block colour table, PaletteCalmTest
4628378d feat(B9): fly-through client test and scripts/flythrough.sh
5dd8d6d6 feat(TPHOLD): hold far same-dimension teleports until terrain is FULL
e0cde7c7 perf(B8): performance budget in CI, perf.sh + perf-budget.json
33f48c73 docs: architect + interior-designer reviewers, design standards
f4169fea chore(design): generated block reference for the design reviewers
7ab76f39 chore(process): two-level verification, evidence pinning, merge trains, short merge lock
b249adc7 feat(proc-floorplan): fractal-cli floorplan, text floor plans from the IR
75d99cfa chore(process): warm persistent post-merge snapshot worktree
ae4730ca feat(proc-gametest-reg): game tests register themselves (no shared list)
3330e682 build(proc-gametest-reg-2): game test scan guards and rerun on scan change
42fa1604 docs(design): John's 2026-10-25 design decisions (DS-1, DS-23..29, savanna and mass-timber briefs)
28039da7 docs: should-fix tier, verdict line, one fix round
f86be1bf feat(TESTISO): server game tests own their world areas; order-proof suite
f4e867b6 docs: lean DS rules, design notes
188cb2cf feat(RAILFIX): railings that hold (rail 1 above the surface, not standable)
c3d21825 feat(HR-B0): high-rise design checks as warnings
562acefc feat(HR-A1): style variants by height; minimal tall style
232a6b5a test(viewer): load-scaled e2e budgets; DS-39 and DS-40 proposed
d6dd1fcf feat(HR-B1): tower massing - programme, core block, stair house
4caf3206 feat(RNFIX): RoadNet.flight stores before removing the flight, re-checks after claim
71bd6508 chore(silverbullet): John's CONFIG.md edit
bede4eb3 feat(LOTRAIL): set buildings back so railed flights stay in lot
6c6d5183 feat(HR-B2): TowerPlate templates (CORRIDOR, GRAND, BANQUET, STAIRHOUSE)
d01cb98c feat(HR-A2): A2k keep palettes for towers of 7+ storeys (DS-1)
71f2715f docs: John's REVIEW answers
d317ca90 feat(windows): generous windows (1x2 default, 2x2 common), glazing report, window-size check
8579b75a docs(design): file John's 2026-10-26 answers, DS-41..43, REVIEW archive
33491e93 feat(HR-A2): HR-A3 mass-timber style family for forest high-rises (7-20 storeys)
d1445920 fix(DOOR2): landings never cover an earlier door's flight (doorWidth 2)
b67ff1f4 feat(MINLOT): slope lots sized with room for a railed setback
cc503c6a feat(FACADE2): house 2x2 fronts, piered 2x2 shop/block uppers, tallGlass tests
c7eb2bc3 test(FLYCAP): fly-through frames taken only once two captures agree
df527961 feat(HR-A2): HR-A2 shared tall family (brick/stone/terracotta/concrete) by style
e7862314 feat(HR-B3a): tower floor plans, shaft, ceiling lights, piers
e41d5986 feat(HR-B3b): high-rise facade bands, roof doors and roof guard
97d93a12 feat(FOLIAGE): remove foliage cut loose by road and street clears
c931955a chore(review): review-check.sh keeps archived REVIEW items from coming back
c7d963a7 docs(CLAUDE.md): review-check line
5c45f2dd feat(CITYQ): fly-through city fixes: tiers fit radius, lattice, edge taper, spill clear
c2971105 chore(test): FB_VK=intel renderer switch for client tests, Intel iGPU spike note
fb0ffed3 docs(CLAUDE.md): intel iGPU approved for client tests (John 2026-10-26); INDEX gpu-intel
b0b90681 feat(SHOPLOW): S1v viewer shows dug-in buildings and areaways
2bd6b288 chore(SHOPLOW): door-rise baseline tool and tables
ab54397d docs(CLAUDE.md): door-rise.sh line
5e125079 chore(test): Intel iGPU by default for client tests, lavapipe fallback
e0ccbe03 docs(DS-47): dense cores are filled
58048ed9 chore(test): scripts/test-scope.sh and the docs/TESTING.md policy
8cc31f1a docs: docs/plans/metrofill.md (DS-47); drop resurrected REVIEW items
3845b140 feat(SHOPLOW): S1: SiteGround, one effective-ground rule (no output change)
58f00d34 feat(STAIRFILL): cells under an interior step are full blocks, not stairs
41aa0176 fix(tooling): review-check catches duplicate titles; test-scope reads the current checkout
de87df4f feat(SHOPLOW): S0 core: DoorRise check (DS-45, report only)
efc88ca8 chore(test): at most 2 full fast suites machine-wide (suite slots)
cf092bdd docs: playtest design answers, 6.18 ROOMWALK, 7.22 INN, DS-48..52 proposed
d281b67b feat(SHOPLOW): S4 market hall on the square: open floor, stalls, inside stair
148e0f1c feat(STEPTOP): exterior steps beside a door or a lower step stay stairs
80031d5d feat(METROFILL): R1: arterials every 2nd tile line, squares at crossings
79bde1f6 chore(remote): remote worker support from main (scripts/remote.sh), with fixes
6ddcb6da feat(DEADMAIN): trim shore-spit exit bridges, dead-end main road rules
81dd6265 feat(SHOPLOW): S2+S3: shops, houses, workshops dig in; lot planner limits
48fc87f4 fix(snapshot-test): own lock file instead of heavy.lock (self-deadlock)
b6009273 fix(remote): remote.sh fixes from the first remote worker run
fa6099c2 feat(ROADS1): one road material per road and street, city decks (DS-30, DS-44)
ac5dc009 feat(ROOMWALK): furnished rooms stay walkable; beds head to the wall
16f287b4 feat(METROFILL): M0: fill metrics and three report-only checks
c466944d feat(ROADCLEAR): no snow layers left floating over cleared roads
2f2111c8 feat(METROFILL): DX: diagonal switch, inert by default
d6d2b38e feat(STEPBURY): / 6.14 TREEIN: claimed headroom over steps, trees out of building air
6ffc1562 feat(TESTLAYERS): client layers (FB_LAYER, FB_ONLY), B2 on dedicated servers
1b462fa2 fix(ROOMWALK2): shop counter no longer walled in by a light
a1425ac2 perf(REGIONPERF): region perf row diagnosed, quays split out, count rows
1759b72c feat(ORDERFAIL): no vanilla lake where our pieces are; chunk order safe again
37ac7efa feat(ROADJOIN): steps 1-4: approaches meet streets, follow the water rule
12b5efad chore(order-test): EULA wording for the standing approval; polish lines, ROOMWALK note
503bc615 docs: CLAUDE.md order-test EULA wording, verifier remote to-main --full
9cb5ea6d chore(anywhere): scripts/anywhere.sh routes test runs to whichever host frees first
da5439bf test(SHOPLOW): S5a BuriedRasterTest, every buried cell is claimed
cc2a52a0 chore(anywhere.sh): beebox depth default 2 (3 slots, John 2026-10-27)
add8d260 feat(GROUNDWALK): GroundWalkTest covers all road roles; dry bridge-bank cells on earth
055a4c5a feat(ROADCLEAR2): remove hanging snow and neighbour trunks from street air
6ec08c24 chore(anywhere.sh): local load ceiling 14 -> 18 (20 cores; local sat idle at load 14-20)
8004442f docs: John's 2026-10-27 (door frame, inn, DS-48, DS-49)
7f4a540a chore(order-test): writes eula=true itself (standing approval), local servers only
49802595 chore(anywhere.sh): quiet switch --local-max N [--until HH:MM] caps local suite/heavy/full jobs
bc728e5c feat(SPILLDIRT): classify 2x2 trunk-base dirt as spill; clear trunk base in street air
c9660fb4 chore(ORDERMEM): order-test jobs from memory, exit 4 when incomplete; remote.sh reports OOM kills (137)
a6dea31c chore(order-test.sh): FB_ORDER_RUN_MB default 2300 (John 2026-10-27)
79a613b4 docs(ORDERSIM): plan for engine-free chunk-order tests; ROADMAP checkbox; NOTES API facts
34909e61 chore(order-test.sh): FB_ORDER_RUN_MB back to 3000 (2300 OOM-killed 3 jobs on beebox)
947dcdfc test(ORDERSIM): S1: SimBoot, SimWorld, SimLevel test infrastructure
4c9e8ef7 docs: rule: never run gradlew --stop (it stops every agent's daemon); NOTES incident line
fa79e158 feat(GALLERYROOMS): drop curtain furniture buried by a walk stair cut
b339a71c test(ORDERSIM): S2: SimSites and ScenarioFinder (test-only)
86794dc2 test(ORDERSIM): S6: LakeVetoGameTest proves the lake mixin is applied
30693f8a feat(ORDERSIM): S0: production seams R1, R2, R4-R7 for the order simulation
0615bcfd test(WORKERSFLAKE): WorkersShutdownTest counts only its own pool's threads
aa300cf0 test(ORDERSIM): S3: order harness (OrderSim, SimFeatures, SimOrders, SimCompare C1-C6)
a464d60e feat(METROFILL): R2: parallelMain query, pair table, lot-loss guard (yields off)
4945a9cf feat(GUARDDOOR): curtain guard door clear of gallery stair cuts
3adbc3e8 perf(TESTSPEED): planner-core suite 45 -> 13 min, heavy JUnit tag
ce13b1e8 perf(PERFSRV): perf.sh world half on a renderer-free dedicated server
d2aaa715 chore(POSTMERGEDETACH): detached post-merge tests, --cleanup, postmerge-status.sh
cf3c2588 perf(TESTSPEEDFIX): to-main shortcut runs planner-heavy, determinism counts survive the heavy run, full usage header, --planner-heavy wording
1baf6236 test(ORDERSIM): S4b: SC2/SC3 regressions (ORDERFAIL, SPILLDIRT) and mutants
078185bb test(ORDERSIM): S4a: scenarios SC1 and SC5, liveness, controls, pinned rock case
3f05ff2c test(WPOTFLAKE): progress-based hang detector for WorldPlannerOrderTest
d224b02b feat(SPILLDIRT3): order-test classifier accepts tree-base dirt under street surface
34b009de feat(ROCKVETO): veto vanilla boulders (forest_rock) that could reach our claims
d8e9bd54 feat(ROCKVETO2): replay the boulder descent before the veto box
200472a5 feat(TREEINORDER): BuildingAirClear removes every log in claimed building air
c75b32dd test(COLDCHUNK): diagnosis, perf-harness stack watchdog
f40e3551 feat(FOLIAGECLUSTER): floating leafless log cluster removed in any pass order
d58db3ef chore(evidence.sh): unique evidence folder when same-label runs start in the same second
39629447 perf(TESTSPEED2): share planned sites and gate-window planners across tests
d66b93d9 build(FORKMEM): memory-aware planner-core test fork count
0460f121 docs: John's notes on ROADMAP.md, STANDARDS.md
16b83a27 feat(FLY2): fly-through review tool: wide 1920x1080 shots, rd 24/32, street-level frames
6a813329 feat(VILLROAD): roads bend round vanilla villages, graded spur joins the street
e955a46f docs(STANDARDS): move rules approved 2026-10-28 to their sections
438321fa perf(PROFILE): per-phase JFR and thread counters in the perf world half, scripts/profile.sh, JfrAttrib
cd4ea158 feat(ORDERSIM): R3+S5: RoadFeature.placeIn seam, SC4 roads order scenario
d015d550 feat(ORDERSIM): S7: order test advisory for sim-covered paths, mod-heavy step
05475e4d chore(test-scope): village joins keep the real order test required (sim skips structure starts, S8 open)
6bae0110 perf(PARREADS): parallel terrain reads for cold settlement planning
2c651009 test(HARNESSTAGS): bind real vanilla biome tags in the unit-test terrain harness
1ecd8ff4 feat(DS36ROOFS): smooth pitched roofs in stairs and slabs, chunk-order safe
d1f0e0e6 feat(DS35DOORS): more entrances (DS-35 and workshop-doors amendment)
a8d50442 docs(REVIEW): items link tracked copies in docs/review/, not .claude/reports/
761b13c8 build(DEVSHELL): move the flake to nix/ (path: flake), cached scripts/dev wrapper, root stub
cb5ce964 chore(remote.sh): state files, --attach/--attach-last/--list, keep the copy until fetched, worker cleanup
f529908b feat(FBNOTE): /fbnote playtest note command, sidecar JSON, gallery script
c65ee2ad docs: release plan for John (P6 gate checklist, increments, release train); coordinator honours backlog tags
65786ba1 chore(C2): playtest kit: scripts/playtest-kit.sh and a first run on 6ea46a67 (no frames yet)
f427078e docs(CLAUDE.md): playtest-kit command
a9463e24 feat(STAIRFOOT): stair-foot invariant, high-rise roof door off the flight opening
08ca6044 feat(DOORBANK): no front door faces a street bank it can't climb
a4d63fe3 feat(PORCHSTEP): a step up to every door sill a block above the ground
afb4188f test(FOLIAGEFAIL): foliage street headroom counts only ledger-claimed air
06132d34 chore(client-test): start Xvfb without LD_LIBRARY_PATH; backend guard
d80f6889 docs(CLAUDE.md): xvfb GLX leak, client-test backend guard
cf1ed3d8 feat(DEADPORT): link rule - another exit gate, else drop, else counted dead end
86de5a8c feat(LEAFORDER): tree room check reads natural terrain in our claim region
3717dd25 test(SIMTIMEFLAKE): CPU-time instead of wall-clock asserts in fast tests
a45cfce5 perf(DISTPERF): memoised terrain per district, route marks in step search
152a0e96 docs(STANDARDS): file open reviewer proposals under Proposed by agents (DS-55..DS-73, amendments); REVIEW reply
0f796a7d feat(ROADLEAFORDER): FoliageClear.own judges a chunk's own trees vs plan claims
ab234a89 docs: research: road-refine spike (finer reads, reroute, D* Lite) and answer to John at 20.14.21
ccb326fa feat(scripts/docs-sync.sh): mechanical docs-feedback sync with exit codes, plus selftest
844942c9 feat(MAINSTUB): exit roads with nothing beyond their gate end at the town
8d699d2b docs(BASEMENT): John's 2026-10-29 basement answers (DS-75 proposed); docs-sync ignores ledger edits
9277313f fix(MAINSTUB-CORE): whole-settlement path never cuts the core tile
7287a213 revert(MAINSTUB): undo MAINSTUB-CORE (whole-settlement path never cuts the core tile)
ce428e3c fix(test): client-test and server-only-test unset CI so Loom does not nest xvfb-run
764d9449 fix(MAINSTUB-CORE): never cut the core tile; cut keeps core street overhang
5930f909 chore(docs/QUESTIONS.md): one-line index of decisions open for John
a241e01e docs: John's notes on john-TODO.md
5d10b335 fix(HAMLETEMPTY): hamlet no longer left without a building
88f81bc4 feat(EMPTYCITY): empty settlements on coarse dumps are artefacts
3f0198ff feat(PORTALCOLD-M): nether-portal stall into cold settlements measured
a67d8718 docs: John's notes on styles.md
67f68c82 chore(scripts/glossary.sh): generate docs/GLOSSARY.md (item codes and run terms) with --check
3a4e2d9d chore(glossary): table links as SilverBullet wiki links ([[docs/x|text]]), which open in the same tab
1760cfdc docs(CLAUDE.md): table-cell links are SilverBullet wiki links (John 2026-10-31, option 1)
72bac739 chore(scripts): quiet-check.sh (quiet-machine rule); perf.sh tags its output quiet or NOT A MEASUREMENT
3283fe34 docs: John's notes on PLAYTEST.md
54833401 perf(COLDARRIVAL): cold-arrival perf rows (mod vs vanilla), report-only
73063a93 docs(QUAYLAZY): plan (lazy per-cluster quay trace + parallel walks), probe findings in NOTES
f6afe798 docs(design): file John's 2026-10-31 answers; prune QUESTIONS.md and stop union-merging it
c1d64300 perf(QUAYLAZY): 3a: QuayTrace, a lazy cluster-wise quay trace over a Bank
a3124604 perf(QUAYLAZY): 1+2: chain walker in Quays, Districts prep for the lazy trace
df946071 feat(DOORAIR): door steps claim street headroom
99ee9ea2 fix(SKIPROLL): decide never-settling region cells without terrain reads
7685c063 perf(QUAYLAZY): 3b: tile planning uses the shared lazy quay trace
811f8083 docs: John's notes on road-refine.md
2223e38f perf(PERFBASE): perf base world is a real vanilla server (tools/vanilla-server)
2cea7f6f docs(CLAUDE.md): perf.sh base is a vanilla server (PERFBASE); polish line
a6f8fe90 chore(perf-check): self-test fixtures judge timing rows only (SKIPROLL count ceiling broke 3 cases); --allow-absent-prefix arrival. for FB_PERF_ARRIVAL=0
c08bc23d perf(QUAYLAZY): 4: lazy quay walks and cluster grading fan out on the reads pool
2b69b81d docs(explainer): explainer page builder: seed to city, where it reads terrain, where the time goes
4640aace feat(TPHOLD15): teleport hold cap 90 s; release-on-plan built but off
126f4add feat(STAIRJOG): entrance flights run straight, runs of 2+, jog checks
edd5b405 chore(ORDERHANG): bounded order-test shutdown after the verdict
62eb062c docs(CLAUDE.md): order-test bounded runs (ORDERHANG)
e5d7b986 perf(QUAYLAZY): 5: warmer plans the destination tile, no whole-city quay trace
4012bbf9 feat(PORTALCOLD): hold nether-portal trips until the exit area is ready
50c31797 test(PERFSMOKE): smoke test waits on the suite wall budget, not 120 s
d38d9262 docs: research: roads-first design note (local road lattice that settlements follow)
7cb5a4e6 perf(QUAYLAZY): 6: lazy quay trace measured on quiet beebox; tile budgets set
e8643e69 docs(JFLAZY): plan: profile the slowest cold chunk; lazy junction field rejected, CASCADE planned instead
42158de5 perf(CASCADE): 1: count harness and FB_PERF_SAMPLE sampler
c6e45f16 feat(CASCADE): 5: warmer warms nearest settlements first, neighbour tiles, hold-only links
de3c1a1e feat(READSDEFAULT): reads pool = cpus - 1, 1 to 11
c7e3379a feat(CASCADE): 2: bounded exit-cut walk proves "cut at or beyond m" cheaply
3cf078ca chore(ORDERPORT): order-test picks a free port and retries on bind failure
2aaf5fa5 feat(CASCADE): 5b: neighbour plans queue on tile change only, one per player
27acab78 feat(CASCADE): 3: link rule answered from one drawn approach where that proves it
073779c6 feat(CASCADE): 4: link rule, approach draw and exit walk fan out on reads pool
cc08df8d test(TESTFIX2): PortalHold flake fix, LinkShortCutTest sampled
36ec79f0 feat(CASCADE): 2fix: quay chain walks remember 4 prefetch boxes (p95 5.7 -> 3.6-3.9 s)
a0c9cd80 feat(CASCADE): 7: quiet-beebox measurement; cascade count rows enforced
8df5ef00 docs(QUAYFAST): plan: trace quay runs, not contour chains (byte-identical); answer to John's question; (B) analysis
4f1c7e0b feat(MAINSTEP): 3-wide step at the main door (door width + 2)
073c78a8 feat(TPHOLD60): TeleportHold cap 90 s -> 60 s (John's decision)
7c2347c5 perf(QUAYFAST): 1: run walker in Quays (runThrough, segmentsIn)
0939937a perf(QUAYFAST): 2: Bank.maybeOk, the cheap screen on the quay bank
999ac084 perf(QUAYFAST): 0: per-sample cold district timing rows
21de7832 perf(QUAYFAST): 3: run-level QuayTrace (a tile walks only its quay runs)
57d28c88 chore(QUIETHOLE): quiet-check watches the whole perf window (beebox job history, lock/CPU sampler)
4433c19b chore(PERFROUTE): anywhere sends timing runs to an empty beebox only and fills it
633b953e test(MAINSTEP): foliage: FoliageClientGameTest injection skips building-owned cells
a7a4e558 perf(QUAYFAST): 4: screened seed and partner scans (skip cells with no maybeOk column)
8bc708a1 chore(remote): worker pool in .claude/workers.conf; remote.sh --worker, anywhere.sh routes across it
bb4f4051 chore(HEAVYSLOTS): per-worker heavy_slots; anywhere.sh sends at most that many heavy jobs to a worker, light/suite fill the rest
5ed2ddfd perf(QUAYFAST): 5+6: count guards, cascade budget 11,891, perf-worker numbers
5b15fb28 chore(WORKERHEALTH): preemptible workers; fast health check before routing/sending; waiters ride out a suspended worker; JOB LOST (75)
7e03922c perf(perf-check): knownOver rows (PERFKNOWN): over-ceiling bug accepted for 0.1.0, visible, fails if 25 % worse
da195ae6 docs: release follow-ups: tick playtest bug lists, add TPABORT and QUAYFAST 4b boxes, REGIONPERF and gate results, archive settled REVIEW items, README cold-arrival note, DS-19 interim deck proposal
daa3307d perf(QUAYFAST): 4b: CoarseTerrain memoises water/peak per lattice column
54dbf4bb perf(QUAYFAST): 4b: district p95 back under its ceiling (1,073 ms quiet); drop knownOver, PERFORMANCE known bugs
6df7c65d test(PERFKNOWN): perf-check self-test accepts a budget file with no knownOver rows
87cd2404 docs(DECKDIAG): plan: why approach decks run along water; F1-F3 for P6, DEADPORT (c) triage
7bcdc12c feat(FBNOTEON): /fbnote on by default, notes=false to turn off
14b1e44e feat(RUNGUARD): 6.36: test runs can't silently test less than asked
1353abf8 chore(ANYFLAKE): anywhere self-test heavy-slots jobs wait for a release file, not a sleep
02be3955 fix(TPABORT): teleport and portal holds never release into unready chunks
5a1f87d3 feat(GLASSSTUB): pane-attached check, connector game test
0e6f0842 feat(LONGDECK): + DECKDIAG F1/F2: long approach decks for towns and up only
8ef945a0 perf(METROWARM): region warmer looks ahead by flight speed
0c6025bc feat(DECKDIAG): F3: shore-aware approach reroute; sm11_m2 approach live again
2482fa52 docs: John's notes on README-player.md
a3686417 docs(process): document the Haiku docs watcher
40fce076 feat(ROADJOIN): step 5: opt-in in-game join check
dd457a26 docs: John's notes on README-player.md
8c82192b feat(COLDTICK): cat/patrol spawners skip ticketed but not-FULL chunks
693da471 feat(THINDECK): no edge street half-built over water in cities
e49769a0 fix(SRVONLY): server-only-test finds Xvfb through xvfb-run on workers
86323546 feat(DOORREACH): door-reaches-street check in city-check (report only)
79afd467 feat(CFGEXAMPLE): example config kept in step by a test
f76f350e chore(promote): history-on-main proposal and promote.sh dry run
dc61d0ca docs: fabric + NeoForge feasibility note
5b0d539b feat(FBNOTESEED): /fbnote records the seed for ops on a server
0b2056aa feat(ORDERSTAIR): porch/interior stairs take Minecraft's corner shape
a4c19751 feat(TILEPLANS): plan only neighbour tiles a street can reach (rows 50 to 32)
9795bf0e docs: John's notes on README-player.md, index.md
40462160 chore(process): verifier Write tool, semantic commit subjects, promotion to main (W1, H1, H3)
fa42667c chore(W13): world-query.sh, offline save reader (McaTop + FloatingSnow as one tool)
fbbd40d7 docs(CLAUDE.md): world-query line; QUESTIONS: W6p allow rules for John
263f7557 chore(process): board, ask-john and needs-john status scripts (W4, W5)
06a80217 chore(W5): board and ask-john lines in CLAUDE.md, coordinator.md, verifier.md; ledger
```

# Rewrite of main (Phases 0-5)

- base: 9fb9dc8b1e11f9e1eef73a66180eef9f295cac0d (9fb9dc8b), run: main (934626b4), range: 9fb9dc8b1e11f9e1eef73a66180eef9f295cac0d..934626b406fffd37d3e63b1eb43273f271da52af
- branch written: claude/promote-rewrite-dryrun (f1c188c8)
- run first-parent commits: 158 (code 68, docs 47, bookkeeping 43)
- promoted commits: 84 (empty groups dropped: 0)
- excluded paths (kept as on the commit it stacks on): docs/runs
- overrides: scripts/promote-overrides.tsv (20 applied; skipped: 0 already promoted or base history, 35 for other runs)
- groups built on 9fb9dc8b1e11f9e1eef73a66180eef9f295cac0d changes the run had not merged yet (kept, not reverted): 0
- tree check: PASS (identical to main outside: docs/runs)

## Needs a look (0)

none

## Proposed history of the 84 commits (oldest first)

```
a2e138d9 docs: templates and research index
48ad7168 feat: Phase 0 scaffolding: Gradle modules, boundary check, CLI, viewer, mod, test-all
d88b3204 docs: terralith note revised
96197fca chore(silverbullet): SilverBullet plugs (Silversearch, Mermaid), research EPUB script, INDEX notes
27c59f20 docs(research): Phase 1 complete: deep-dives, landscape synthesis, SilverBullet conventions
db3e2794 docs: john-work notes, Phase 1 gate passed
c0afb47b docs(worldgen): Phase 2 worldgen spikes S1-S8 and ADR 0003 draft; client tests force SDL x11
68f0ee24 docs(ADR): Phase 2 gate passed: ADR 0003 accepted (supersedes 0002) with quiet-machine S6 numbers
d16a7875 docs: docs-feedback review loop documented; ROADMAP Updated: lines
57d632fb feat(P3P): phase 3 plan, IR v1 types, probes, Hash64 and shared fixtures
0f6d4e08 feat(viewer): Phase 3 gate follow-ups: tilted gallery top view, jettied wings and setbacks
7670fa8f feat: Phase 4 building generator depth: v1 pipeline, roofs, facades, interiors, 9 types, 5 styles
8e101a94 docs: archive Phase 4 ROADMAP text; plan status done
4bb38b9e feat(tower): Phase 4 gate follow-ups: stilted watchtowers, viewer guide, review conventions
dfbd340f docs: phase 4 gate passed (John 2026-10-21); feedback items 5.11, 5.12; review loop checks main
f21bb195 feat(p5-dg): world-wide district tile grid (DistrictGrid)
a7ac10d7 feat(p5-t1): server-less vanilla terrain dump tool and first dumps
46cdf3b3 feat(ST): biome style field (StyleField)
09a84772 feat(shop): open ground storeys for shops (arcade, market hall), workshop porch
e353eea9 feat(TA): terrain toolkit (water field, lattice A*, road/plaza grading)
a9de0f8b feat(R1): region field, settlement tiers and `fractal-cli region` in the core
fcb0bc0a feat(V0): region map page (region.html) over region and terrain dumps
bf8866b3 feat(P0): settlement IR (fb-settlement/1, fb-district/1), brief, fixture
9fa26d24 feat(V1): 2D plan view of settlement plans (viewer/plan.html)
a63ae968 feat(MC1): join rule - big settlements claim neighbouring region cells
2060706d feat(p5-fix-block16): front steps may merge into an earlier flight
4fc74707 feat(DB): lot briefs and district scenes with graded roads and plazas
1abe3f7b feat(interior): interior circulation: hub door graph, passages, archways
80b6e68b feat(V2): district 3D view with terrain, water, roads and fly-through
5362911e docs: John's questions on Phase 5 plan decisions
e8e3a687 feat(SK): cell skeletons, fb-settlement/1 plans and fractal-cli city
f0f7888c docs: answers to John on city layout
9c2f76e6 feat(JT): jetties over streets (air rights, in-lot strip, house/shop)
47a1ac24 feat(R2): water bias for town, city and metropolis sites
c838a5a7 test(T2): nine real-terrain gate windows with a site table
f08934c5 feat(SHOPLAND): shopfront landing at door level, flight down to the street
3dc2946e feat(DL): district layout - streets, ports, grid blocks, lots, briefs
b251206a docs: answers to John, 5.13, 6.9, IRR1
69f1abfd feat(p5-plains-palette): calmer plains palette (similar-value blocks)
1a852de1 feat(D0): city pipeline end to end, city-check, whole-city rules, presets
7a8f974e feat(CIRC2): squarer rooms, bedrooms before repeated room types
62149a4a test(DT): settlement determinism suite over the city presets (5.6)
2e519baf docs: John's SB config request
31366d86 feat(MC2): cities across cells, solo sites, bounded pure caches
46deb05a feat(D1): hamlet and village tier programs, tier selector, presets
9f742427 chore(silverbullet): CONFIG.md ADR tag; SB_SPACE_IGNORE hides source and data
bacfa8d5 feat(p5-house-program): one ground-floor kitchen and one living room per house
de42c488 feat(p5-hub-square): hub square searched on exact terrain, joined by lanes
4b239c96 feat(D2): town program, wall ring with gates and wall/tower lots
b254cfe7 feat(SC): city-bench scale numbers; probe bar not met
167e6543 feat(TR): terrain response - quays, deck role, fuller hill mask, arterial grade
107bec3f feat(ROADS): whole-city roads connected and graded once; KNOWN emptied
4e39dfba feat(D3): city and metropolis programs, density and skyline (5.7)
400b773d feat(IRR1): old-town subdivision and diagonal-avenue cut lanes
7e8616b2 feat(p5-tidy): drop test tolerances, 64-cell sites, scale re-run
f6f3913b chore(viewer): Phase 5 gate review pack (capture spec, featured scenes)
e6aac4ff feat(p5-viewer-palette): per-building palettes and style ground tint in 3D
654deb2c docs: archive Phase 5 text
6ced61aa feat(settlement): Phase 5 gate follow-ups: ROADS2, OPENPLAN, WALLWALK, SKYLINE, ROADS3
ab822c0a docs: docs: S10, 6.10, Phase 7 transit and Create items, lintel backlog line; replies to John's 2026-10-22 notes
cc22844f feat(STAIRGROUND): exterior steps reach the ground; steps-reach-ground check
82a307e9 docs(plan): docs/plans/phase6.md, NOTES API facts, ASSUMPTIONS defaults
db985a0f feat(rail): rail role beside exterior flights, landings and lookout stairs
87975027 feat(A5): release basics (LICENSE, jar version, icon, changelog, player README)
fdb9adb1 feat(A3): cell raster, one owner per cell, block shapes (core/raster)
8ff46404 feat(A1): live probes with the dumps' exact heights, chunk memo, cost table
0d6a8786 test(viewer): e2e takes a free preview port per run
b5930dd9 feat(A2): WorldPlanner, the per-world thread-safe planner facade
5259b85d feat(A4): block mapping, palette + Shape to Minecraft block states
cc60211e feat(B6): / 6.5: config file, per-world pinned settings, /fb config
8b414a94 feat(A3F): building rooms own interior air; RAIL connects
cfaa936b feat(A1F): live biome from chunk memo, solid-above check, probe fixes
3aec9bb5 docs: notes, verifier: every Gradle run in the integration checkout holds the merge lock
3762c097 feat(B1): district structures, placement and pieces in worldgen
3f4c6345 chore(repo): untrack mod/logs files re-added by B1
427ef069 build(TESTSPEED): fork planner-core tests across JVMs
6fe371f2 feat(B4): worker pool, region warmer, prefetch at start, lane counters
9822a7b0 feat(B5): beds, chests, job sites, villagers; /fb info and locate
8d25f30c perf(CACHE): keep in-flight and recently used tiles past the cache caps
1fff0564 docs(ADR): 0003: amend W2, P1, D4 for exact heights (John, 2026-10-22, Phase 6 decision 1)
e089935d feat(terrain): terrain in a real world: street earth and headroom, deep foundations
c172f4ad docs: John's answers to ADR 0004 questions 1-4
1a28322f chore(scripts): OCR and scaling utilities for gameplay screenshot galleries
f1c188c8 feat(remote): scripts/remote.sh runs builds and tests on a remote worker
```
