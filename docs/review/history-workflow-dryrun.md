# History workflow: dry run

The test for [history-workflow.md](history-workflow.md): `scripts/promote.sh` run on 2026-11-02 against the real branches. Nothing was merged, pushed or rewritten; the results are three throwaway branches you can browse with `git log`:

- `claude/promote-dryrun`: what `main` would get from Phase 6 (`main..claude/run-phase6`), built with `scripts/promote.sh --dry-run --run claude/run-phase6`.
- `claude/promote-rewrite-dryrun`: what today's `main` (Phases 0-5 and the earlier Phase 6 merges) would look like rebuilt the same way, from the first commit: `scripts/promote.sh --base $(git rev-list --max-parents=0 main) --run main --branch claude/promote-rewrite-dryrun`.
- `claude/promote-publish-dryrun`: the two together, the `main` you would publish: the rewrite above with Phase 6 stacked on it (`scripts/promote.sh --run claude/run-phase6 --onto claude/promote-rewrite-dryrun --branch claude/promote-publish-dryrun`). 333 commits in all (the first commit, 84 for Phases 0-5, 248 for Phase 6), against 575 on `main` today plus 995 more after a plain `--no-ff` merge of Phase 6; its tree equals `claude/run-phase6` except that `docs/runs/` is gone (the rewrite drops the old ledgers, 7 files).

Delete them with `git branch -D claude/promote-dryrun claude/promote-rewrite-dryrun claude/promote-publish-dryrun`.

In all three, `docs/runs/` (the run ledgers) is kept as it is on the commit the new history starts from (option A1 in the proposal; so the rewrite has none), and the final tree was checked identical to the source branch everywhere else.

# Phase 6 promotion

- base: main (934626b4), run: claude/run-phase6 (05d70982), range: 934626b406fffd37d3e63b1eb43273f271da52af..05d70982711103e9b5465bec421f887de4688c4b
- branch written: claude/promote-dryrun (805b4112)
- run first-parent commits: 647 (code 210, docs 84, bookkeeping 353)
- promoted commits: 248 (empty groups dropped: 0)
- excluded paths (kept as on the commit it stacks on): docs/runs
- tree check: PASS (identical to claude/run-phase6 outside: docs/runs)

## Needs a look (32)

- a56281cd no item code in subject: 6.10 (planner half): roads between settlements, port tag, SK approaches
- 7af866f1 no item code in subject: generated block reference for design reviewers
- c809a0fa no item code in subject: two-level verification, evidence pinning, merge trains, short merge lock, design reviewers in process
- 16c06741 no item code in subject: warm persistent post-merge snapshot worktree
- b820a078 no item code in subject: John 2026-10-25 design decisions (DS-1 mass timber, DS-29, DS-23..28, savanna + mass-timber briefs, ROADMAP 7.6-7.9)
- 538398a0 no item code in subject: load-scaled viewer e2e budgets; REVIEW questions; DS-39/40 proposed
- 86be78e0 no item code in subject: John: CONFIG.md
- 49983a7b no item code in subject: 7.12: generous windows (1x2 default, 2x2 common), glazing report, window-size
- 05164b81 no item code in subject: John 2026-10-26 answers filed; REVIEW archive; DS-41..43
- 56ae97ca no item code in subject: review-check.sh guards archived REVIEW items
- ed5aa374 no item code in subject: FB_VK=intel switch, gpu-intel spike note
- df9f187c no item code in subject: door-rise baseline tool and tables
- f526aab6 no item code in subject: Intel iGPU default for client tests, lavapipe fallback
- 48369a83 no item code in subject: scripts/test-scope.sh, docs/TESTING.md policy
- 36110559 no item code in subject: review-check duplicate titles; test-scope reads current checkout
- a15d5cc1 no item code in subject: at most 2 full fast suites machine-wide
- 686cf4ea no item code in subject: John beebox remote worker (934626b4) + fixes
- 4c815d6e no item code in subject: snapshot-test own lock file (heavy.lock self-deadlock fix)
- 6cc9cf53 no item code in subject: beebox first use, remote.sh fixes
- d5f2a27d no item code in subject: polish lines, ROOMWALK note, beebox note, EULA wording
- 0ae0c46a no item code in subject: scripts/anywhere.sh late-binding test router (local or beebox, whichever frees first)
- cc346f11 no item code in subject: order-test.sh writes eula=true itself (standing approval), checks local/offline
- 4b04d2dd no item code in subject: Move the flake to nix/ (path: flake), add cached scripts/dev wrapper, root stub that points to it
- ce3f9b69 no item code in subject: Basements: John's 2026-10-29 answers in ROADMAP 7.18 (two phases), STANDARDS (lit not daylit, dense storage fill, skull niche; DS-75 proposed), spawn answer
- 4cdeaffc no item code in subject: Revert "6.25 MAINSTUB-CORE: whole-settlement path never cuts the core tile"
- 1b3f6e54 no item code in subject: client-test, server-only-test: unset CI so Loom does not nest its own xvfb-run
- 43f28d14 no item code in subject: File John's 2026-10-31 answers; prune QUESTIONS.md; stop union-merging it
- 68699407 no item code in subject: Explainer page: how the planner goes from a seed to a city, where it reads terrain, where the time goes
- 96b6e549 no item code in subject: Worker pool: gitignored .claude/workers.conf; remote.sh --worker, anywhere.sh routes across N workers
- 2a0655fb no item code in subject: perf-check self-test: accept zero knownOver rows in the real budget file
- 32629e3d no item code in subject: document the Haiku docs watcher
- 7457c1f8 no item code in subject: server-only-test finds Xvfb via xvfb-run on workers

## Proposed history of the 248 commits (oldest first)

```
94c4fc58 feat: roads between settlements, port tag, SK approaches
a2057beb feat(SRVONLY): unmodded client can join a server running the mod
42526ab2 feat(B2): in-world chunk-order determinism, hook readback, spill classes
b41282fb feat(BEDFIX): both bed halves written by whichever chunk generates first
c1943a2b feat(B7): vanilla-structure veto, biome aliases, terrain-mod compat matrix
9c54c43e chore(client-test.sh): watchdog kills a hung client and always frees the heavy lock
49362231 docs: research: vanilla-block furniture idioms
f8863fc2 feat(JUNCTION): r3 + 6.10 C1 + SPILLDIRT: junction heights, intercity roads, B2
41d52e68 feat(STAIRS): interior flights reach the next floor, no one-block jump
0119db9d feat(PALETTE): calmer style palettes, block colour table, PaletteCalmTest
0e50e34e feat(B9): fly-through client test and scripts/flythrough.sh
d9908682 feat(TPHOLD): hold far same-dimension teleports until terrain is FULL
acdffd04 perf(B8): performance budget in CI, perf.sh + perf-budget.json
e2355c5f docs: architect + interior-designer reviewers, design standards
f019549b chore: generated block reference for design reviewers
bf09396c chore: two-level verification, evidence pinning, merge trains, short merge lock, design reviewers in process
8ebc4a2d feat(proc-floorplan): fractal-cli floorplan, text floor plans from the IR
5cd83947 chore: warm persistent post-merge snapshot worktree
8aaf2e20 feat(proc-gametest-reg): game tests register themselves (no shared list)
76e09537 build(proc-gametest-reg-2): game test scan guards and rerun on scan change
536c603f chore: john 2026-10-25 design decisions (DS-1 mass timber, DS-29, DS-23..28, savanna + mass-timber briefs, ROADMAP 7.6-7.9)
11e69a06 docs: should-fix tier, verdict line, one fix round
ce6767c6 feat(TESTISO): server game tests own their world areas; order-proof suite
75b6d6fa docs: lean DS rules, design notes
b2d83fe6 feat(RAILFIX): railings that hold (rail 1 above the surface, not standable)
b394b8d4 feat(HR-B0): high-rise design checks as warnings
7ec76e33 feat(HR-A1): style variants by height; minimal tall style
07bcf352 feat: load-scaled viewer e2e budgets; REVIEW questions; DS-39/40 proposed
be2acc5a feat(HR-B1): tower massing - programme, core block, stair house
c414166c feat(RNFIX): RoadNet.flight stores before removing the flight, re-checks after claim
639c21be feat: CONFIG.md
f8f146ba feat(LOTRAIL): set buildings back so railed flights stay in lot
6774c078 feat(HR-B2): TowerPlate templates (CORRIDOR, GRAND, BANQUET, STAIRHOUSE)
d5fc37df feat(HR-A2): A2k keep palettes for towers of 7+ storeys (DS-1)
bf56d905 docs: John's REVIEW answers
f4160a1f feat: generous windows (1x2 default, 2x2 common), glazing report, window-size
2264dfdd chore: john 2026-10-26 answers filed; REVIEW archive; DS-41..43
d69dde76 feat(HR-A2): HR-A3 mass-timber style family for forest high-rises (7-20 storeys)
ad292eca fix(DOOR2): landings never cover an earlier door's flight (doorWidth 2)
d45a96c4 feat(MINLOT): slope lots sized with room for a railed setback
4208ba58 feat(FACADE2): house 2x2 fronts, piered 2x2 shop/block uppers, tallGlass tests
63f0c34e test(FLYCAP): fly-through frames taken only once two captures agree
e6c5d1ae feat(HR-A2): HR-A2 shared tall family (brick/stone/terracotta/concrete) by style
79e13fdb feat(HR-B3a): tower floor plans, shaft, ceiling lights, piers
1b87935d feat(HR-B3b): high-rise facade bands, roof doors and roof guard
2a06318d feat(FOLIAGE): remove foliage cut loose by road and street clears
a51e423c chore: review-check.sh guards archived REVIEW items
7167edea docs(CLAUDE.md): review-check line
956159c7 feat(CITYQ): fly-through city fixes: tiers fit radius, lattice, edge taper, spill clear
3a034211 feat: FB_VK=intel switch, gpu-intel spike note
af38b9cb docs(CLAUDE.md): intel iGPU approved for client tests (John 2026-10-26); INDEX gpu-intel
3af0faa5 feat(SHOPLOW): S1v viewer shows dug-in buildings and areaways
ee4a52d1 chore: door-rise baseline tool and tables
a1de6a24 docs(CLAUDE.md): door-rise.sh line
7c4d177f chore: intel iGPU default for client tests, lavapipe fallback
65f05b7b docs(DS-47): dense cores are filled
c4dac5b6 chore: scripts/test-scope.sh, docs/TESTING.md policy
98d23a2e docs: docs/plans/metrofill.md (DS-47); drop resurrected REVIEW items
a1c14bc4 feat(SHOPLOW): S1: SiteGround, one effective-ground rule (no output change)
feac6918 feat(STAIRFILL): cells under an interior step are full blocks, not stairs
619e93e3 chore: review-check duplicate titles; test-scope reads current checkout
e24a9eaa feat(SHOPLOW): S0 core: DoorRise check (DS-45, report only)
de7dc94d chore: at most 2 full fast suites machine-wide
71f2ad35 docs: playtest design answers, 6.18 ROOMWALK, 7.22 INN, DS-48..52 proposed
8b968834 feat(SHOPLOW): S4 market hall on the square: open floor, stalls, inside stair
e4ac3b02 feat(STEPTOP): exterior steps beside a door or a lower step stay stairs
8287b2a4 feat(METROFILL): R1: arterials every 2nd tile line, squares at crossings
75a1fa3a feat: john beebox remote worker (934626b4) + fixes
ef3c17f2 feat(DEADMAIN): trim shore-spit exit bridges, dead-end main road rules
7a4d7d78 feat(SHOPLOW): S2+S3: shops, houses, workshops dig in; lot planner limits
5c6181fd chore: snapshot-test own lock file (heavy.lock self-deadlock fix)
9004bb2e chore: beebox first use, remote.sh fixes
0334eb79 feat(ROADS1): one road material per road and street, city decks (DS-30, DS-44)
8e70943d feat(ROOMWALK): furnished rooms stay walkable; beds head to the wall
f951e134 feat(METROFILL): M0: fill metrics and three report-only checks
842f9f25 feat(ROADCLEAR): no snow layers left floating over cleared roads
ae5fd96e feat(METROFILL): DX: diagonal switch, inert by default
f9e69731 feat(STEPBURY): / 6.14 TREEIN: claimed headroom over steps, trees out of building air
ce475fe4 feat(TESTLAYERS): client layers (FB_LAYER, FB_ONLY), B2 on dedicated servers
801049ae fix(ROOMWALK2): shop counter no longer walled in by a light
d5508807 perf(REGIONPERF): region perf row diagnosed, quays split out, count rows
9b81c590 feat(ORDERFAIL): no vanilla lake where our pieces are; chunk order safe again
c15c9faf feat(ROADJOIN): steps 1-4: approaches meet streets, follow the water rule
c1333f1e chore: polish lines, ROOMWALK note, beebox note, EULA wording
bf63fa8d docs: CLAUDE.md order-test EULA wording, verifier remote to-main --full
e83bc791 chore: scripts/anywhere.sh late-binding test router (local or beebox, whichever frees first)
fe2adced test(SHOPLOW): S5a BuriedRasterTest, every buried cell is claimed
c58e7ea8 chore(anywhere.sh): beebox depth default 2 (3 slots, John 2026-10-27)
c0517eec feat(GROUNDWALK): GroundWalkTest covers all road roles; dry bridge-bank cells on earth
3eda68c0 feat(ROADCLEAR2): remove hanging snow and neighbour trunks from street air
5163cd95 chore(anywhere.sh): local load ceiling 14 -> 18 (20 cores; local sat idle at load 14-20)
1fdf1342 docs: John's 2026-10-27 (door frame, inn, DS-48, DS-49)
070991e6 chore: order-test.sh writes eula=true itself (standing approval), checks local/offline
8eb60404 chore(anywhere.sh): quiet switch --local-max N [--until HH:MM] caps local suite/heavy/full jobs
0c7bb469 feat(SPILLDIRT): classify 2x2 trunk-base dirt as spill; clear trunk base in street air
28731f15 chore(ORDERMEM): order-test jobs from memory, exit 4 when incomplete; remote.sh reports OOM kills (137)
720b5c1e chore(order-test.sh): FB_ORDER_RUN_MB default 2300 (John 2026-10-27)
dcb64c97 docs(ORDERSIM): plan for engine-free chunk-order tests; ROADMAP checkbox; NOTES API facts
ca4d49c8 chore(order-test.sh): FB_ORDER_RUN_MB back to 3000 (2300 OOM-killed 3 jobs on beebox)
052bdf07 test(ORDERSIM): S1: SimBoot, SimWorld, SimLevel test infrastructure
c093ad0a docs: rule: never run gradlew --stop (it stops every agent's daemon); NOTES incident line
a6412aac feat(GALLERYROOMS): drop curtain furniture buried by a walk stair cut
03dbdf52 test(ORDERSIM): S2: SimSites and ScenarioFinder (test-only)
3b03d0f2 test(ORDERSIM): S6: LakeVetoGameTest proves the lake mixin is applied
6b8ed5f8 feat(ORDERSIM): S0: production seams R1, R2, R4-R7 for the order simulation
447e3dc5 test(WORKERSFLAKE): WorkersShutdownTest counts only its own pool's threads
2477b785 test(ORDERSIM): S3: order harness (OrderSim, SimFeatures, SimOrders, SimCompare C1-C6)
73b5bfdb feat(METROFILL): R2: parallelMain query, pair table, lot-loss guard (yields off)
9cdbbd5e feat(GUARDDOOR): curtain guard door clear of gallery stair cuts
c90e1da3 perf(TESTSPEED): planner-core suite 45 -> 13 min, heavy JUnit tag
12d1afae perf(PERFSRV): perf.sh world half on a renderer-free dedicated server
c3641e61 chore(POSTMERGEDETACH): detached post-merge tests, --cleanup, postmerge-status.sh
0736d3d5 perf(TESTSPEEDFIX): to-main shortcut runs planner-heavy, determinism counts survive the heavy run, full usage header, --planner-heavy wording
2ffb6d5b test(ORDERSIM): S4b: SC2/SC3 regressions (ORDERFAIL, SPILLDIRT) and mutants
ca7c6038 test(ORDERSIM): S4a: scenarios SC1 and SC5, liveness, controls, pinned rock case
bda838b1 test(WPOTFLAKE): progress-based hang detector for WorldPlannerOrderTest
b6be3797 feat(SPILLDIRT3): order-test classifier accepts tree-base dirt under street surface
62f3a088 feat(ROCKVETO): veto vanilla boulders (forest_rock) that could reach our claims
4b60a6af feat(ROCKVETO2): replay the boulder descent before the veto box
854bdc1e feat(TREEINORDER): BuildingAirClear removes every log in claimed building air
51cbc163 test(COLDCHUNK): diagnosis, perf-harness stack watchdog
0d8519d5 feat(FOLIAGECLUSTER): floating leafless log cluster removed in any pass order
a8b8183b chore(evidence.sh): unique evidence folder when same-label runs start in the same second
4001fe31 perf(TESTSPEED2): share planned sites and gate-window planners across tests
fdc56546 build(FORKMEM): memory-aware planner-core test fork count
37e2e2ff docs: John's notes on ROADMAP.md, STANDARDS.md
ed9ef327 feat(FLY2): fly-through review tool: wide 1920x1080 shots, rd 24/32, street-level frames
8b0f4f9e feat(VILLROAD): roads bend round vanilla villages, graded spur joins the street
6fdaff6a docs(STANDARDS): move rules approved 2026-10-28 to their sections
53f06c92 perf(PROFILE): per-phase JFR and thread counters in the perf world half, scripts/profile.sh, JfrAttrib
fe0a4f89 feat(ORDERSIM): R3+S5: RoadFeature.placeIn seam, SC4 roads order scenario
af632a8f feat(ORDERSIM): S7: order test advisory for sim-covered paths, mod-heavy step
ea14a626 chore(test-scope): village joins keep the real order test required (sim skips structure starts, S8 open)
b0cd94b5 perf(PARREADS): parallel terrain reads for cold settlement planning
7c98d13d test(HARNESSTAGS): bind real vanilla biome tags in the unit-test terrain harness
074b4c74 feat(DS36ROOFS): smooth pitched roofs in stairs and slabs, chunk-order safe
3149160d feat(DS35DOORS): more entrances (DS-35 and workshop-doors amendment)
671ebe0d docs(REVIEW): items link tracked copies in docs/review/, not .claude/reports/
c8e111d8 feat: move the flake to nix/ (path: flake), add cached scripts/dev wrapper, root stub that points to it
f0e6eab8 chore(remote.sh): state files, --attach/--attach-last/--list, keep the copy until fetched, worker cleanup
a83caf18 feat(FBNOTE): /fbnote playtest note command, sidecar JSON, gallery script
845b0012 docs: release plan for John (P6 gate checklist, increments, release train); coordinator honours backlog tags
f015c8fc chore(C2): playtest kit: scripts/playtest-kit.sh and a first run on 6ea46a67 (no frames yet)
1e0c474f docs(CLAUDE.md): playtest-kit command
45fb31c3 feat(STAIRFOOT): stair-foot invariant, high-rise roof door off the flight opening
2ffbcd1d feat(DOORBANK): no front door faces a street bank it can't climb
2b26d829 feat(PORCHSTEP): a step up to every door sill a block above the ground
5e6da685 test(FOLIAGEFAIL): foliage street headroom counts only ledger-claimed air
5ccd5686 chore(client-test): start Xvfb without LD_LIBRARY_PATH; backend guard
84c3089a docs(CLAUDE.md): xvfb GLX leak, client-test backend guard
4c0b0dab feat(DEADPORT): link rule - another exit gate, else drop, else counted dead end
aa0b931e feat(LEAFORDER): tree room check reads natural terrain in our claim region
813d22c1 test(SIMTIMEFLAKE): CPU-time instead of wall-clock asserts in fast tests
9f4d0b8b perf(DISTPERF): memoised terrain per district, route marks in step search
14f388a9 docs(STANDARDS): file open reviewer proposals under Proposed by agents (DS-55..DS-73, amendments); REVIEW reply
0ca47e55 feat(ROADLEAFORDER): FoliageClear.own judges a chunk's own trees vs plan claims
9d9e91b4 docs: research: road-refine spike (finer reads, reroute, D* Lite) and answer to John at 20.14.21
335538b3 feat(scripts/docs-sync.sh): mechanical docs-feedback sync with exit codes, plus selftest
3593c9e8 feat(MAINSTUB): exit roads with nothing beyond their gate end at the town
bbedf398 chore: basements: John's 2026-10-29 answers in ROADMAP 7.18 (two phases), STANDARDS (lit not daylit, dense storage fill, skull niche; DS-75 proposed), spawn answer
99f3e79a fix(MAINSTUB-CORE): whole-settlement path never cuts the core tile
08b0da37 revert: revert "6.25 MAINSTUB-CORE: whole-settlement path never cuts the core tile"
13c73d57 chore: client-test, server-only-test: unset CI so Loom does not nest its own xvfb-run
83268e2e fix(MAINSTUB-CORE): never cut the core tile; cut keeps core street overhang
e8995d4c chore(docs/QUESTIONS.md): one-line index of decisions open for John
53bc996d docs: John's notes on john-TODO.md
11a38b80 fix(HAMLETEMPTY): hamlet no longer left without a building
50a5584b feat(EMPTYCITY): empty settlements on coarse dumps are artefacts
62b04c19 feat(PORTALCOLD-M): nether-portal stall into cold settlements measured
dc2a28a9 docs: John's notes on styles.md
dcc888b7 chore(scripts/glossary.sh): generate docs/GLOSSARY.md (item codes and run terms) with --check
21c31468 chore(glossary): table links as SilverBullet wiki links ([[docs/x|text]]), which open in the same tab
598a2815 docs(CLAUDE.md): table-cell links are SilverBullet wiki links (John 2026-10-31, option 1)
65765410 chore(scripts): quiet-check.sh (quiet-machine rule); perf.sh tags its output quiet or NOT A MEASUREMENT
2b6710e5 docs: John's notes on PLAYTEST.md
d57b6a5c perf(COLDARRIVAL): cold-arrival perf rows (mod vs vanilla), report-only
8366600b docs(QUAYLAZY): plan (lazy per-cluster quay trace + parallel walks), probe findings in NOTES
ec9c9c0d chore: file John's 2026-10-31 answers; prune QUESTIONS.md; stop union-merging it
d74d604c perf(QUAYLAZY): 3a: QuayTrace, a lazy cluster-wise quay trace over a Bank
b4011672 perf(QUAYLAZY): 1+2: chain walker in Quays, Districts prep for the lazy trace
6cf87bce feat(DOORAIR): door steps claim street headroom
e0c3c4dd fix(SKIPROLL): decide never-settling region cells without terrain reads
ab350800 perf(QUAYLAZY): 3b: tile planning uses the shared lazy quay trace
4e8f62aa docs: John's notes on road-refine.md
ceb788cf perf(PERFBASE): perf base world is a real vanilla server (tools/vanilla-server)
38b5109a docs(CLAUDE.md): perf.sh base is a vanilla server (PERFBASE); polish line
2bc7b0de chore(perf-check): self-test fixtures judge timing rows only (SKIPROLL count ceiling broke 3 cases); --allow-absent-prefix arrival. for FB_PERF_ARRIVAL=0
7349b4aa perf(QUAYLAZY): 4: lazy quay walks and cluster grading fan out on the reads pool
2af127be chore: explainer page: how the planner goes from a seed to a city, where it reads terrain, where the time goes
85b4cb39 feat(TPHOLD15): teleport hold cap 90 s; release-on-plan built but off
fb6ac9c8 feat(STAIRJOG): entrance flights run straight, runs of 2+, jog checks
c631d88d chore(ORDERHANG): bounded order-test shutdown after the verdict
674c4f03 docs(CLAUDE.md): order-test bounded runs (ORDERHANG)
7882deb0 perf(QUAYLAZY): 5: warmer plans the destination tile, no whole-city quay trace
193bc0bd feat(PORTALCOLD): hold nether-portal trips until the exit area is ready
2978b04c test(PERFSMOKE): smoke test waits on the suite wall budget, not 120 s
277717ec docs: research: roads-first design note (local road lattice that settlements follow)
3b36a684 perf(QUAYLAZY): 6: lazy quay trace measured on quiet beebox; tile budgets set
a641ca2f docs(JFLAZY): plan: profile the slowest cold chunk; lazy junction field rejected, CASCADE planned instead
198d7185 perf(CASCADE): 1: count harness and FB_PERF_SAMPLE sampler
053edd4f feat(CASCADE): 5: warmer warms nearest settlements first, neighbour tiles, hold-only links
c2209ee7 feat(READSDEFAULT): reads pool = cpus - 1, 1 to 11
97bf38b7 feat(CASCADE): 2: bounded exit-cut walk proves "cut at or beyond m" cheaply
eef7cd35 chore(ORDERPORT): order-test picks a free port and retries on bind failure
4119122e feat(CASCADE): 5b: neighbour plans queue on tile change only, one per player
ae7aa3a2 feat(CASCADE): 3: link rule answered from one drawn approach where that proves it
85d7ecc9 feat(CASCADE): 4: link rule, approach draw and exit walk fan out on reads pool
48ea1a41 test(TESTFIX2): PortalHold flake fix, LinkShortCutTest sampled
6347d25d feat(CASCADE): 2fix: quay chain walks remember 4 prefetch boxes (p95 5.7 -> 3.6-3.9 s)
f2fcf791 feat(CASCADE): 7: quiet-beebox measurement; cascade count rows enforced
df3e6076 docs(QUAYFAST): plan: trace quay runs, not contour chains (byte-identical); answer to John's question; (B) analysis
1c959e9f feat(MAINSTEP): 3-wide step at the main door (door width + 2)
af6c5f1f feat(TPHOLD60): TeleportHold cap 90 s -> 60 s (John's decision)
a9da6767 perf(QUAYFAST): 1: run walker in Quays (runThrough, segmentsIn)
05c7f420 perf(QUAYFAST): 2: Bank.maybeOk, the cheap screen on the quay bank
9caa9bc3 perf(QUAYFAST): 0: per-sample cold district timing rows
a4187549 perf(QUAYFAST): 3: run-level QuayTrace (a tile walks only its quay runs)
1c943191 chore(QUIETHOLE): quiet-check watches the whole perf window (beebox job history, lock/CPU sampler)
964d3f86 chore(PERFROUTE): anywhere sends timing runs to an empty beebox only and fills it
f4241340 test(MAINSTEP): foliage: FoliageClientGameTest injection skips building-owned cells
f628fe58 perf(QUAYFAST): 4: screened seed and partner scans (skip cells with no maybeOk column)
8d4e7a3f feat: worker pool: gitignored .claude/workers.conf; remote.sh --worker, anywhere.sh routes across N workers
e4b52f59 chore(HEAVYSLOTS): per-worker heavy_slots; anywhere.sh sends at most that many heavy jobs to a worker, light/suite fill the rest
9e79999d perf(QUAYFAST): 5+6: count guards, cascade budget 11,891, perf-worker numbers
9cdf916e chore(WORKERHEALTH): preemptible workers; fast health check before routing/sending; waiters ride out a suspended worker; JOB LOST (75)
0209d507 perf(perf-check): knownOver rows (PERFKNOWN): over-ceiling bug accepted for 0.1.0, visible, fails if 25 % worse
a2dc177b docs: release follow-ups: tick playtest bug lists, add TPABORT and QUAYFAST 4b boxes, REGIONPERF and gate results, archive settled REVIEW items, README cold-arrival note, DS-19 interim deck proposal
4751c324 perf(QUAYFAST): 4b: CoarseTerrain memoises water/peak per lattice column
8991c2a4 perf(QUAYFAST): 4b: district p95 back under its ceiling (1,073 ms quiet); drop knownOver, PERFORMANCE known bugs
2f31a8c9 test: perf-check self-test: accept zero knownOver rows in the real budget file
bbf0b557 docs(DECKDIAG): plan: why approach decks run along water; F1-F3 for P6, DEADPORT (c) triage
85912144 feat(FBNOTEON): /fbnote on by default, notes=false to turn off
cdec5acf feat(RUNGUARD): 6.36: test runs can't silently test less than asked
3c9a20e9 chore(ANYFLAKE): anywhere self-test heavy-slots jobs wait for a release file, not a sleep
d228f339 fix(TPABORT): teleport and portal holds never release into unready chunks
57727261 feat(GLASSSTUB): pane-attached check, connector game test
14bc0b00 feat(LONGDECK): + DECKDIAG F1/F2: long approach decks for towns and up only
1aea8e08 perf(METROWARM): region warmer looks ahead by flight speed
4e8766fb feat(DECKDIAG): F3: shore-aware approach reroute; sm11_m2 approach live again
dead1675 docs: John's notes on README-player.md
cbf21abb chore: document the Haiku docs watcher
c6c300e2 feat(ROADJOIN): step 5: opt-in in-game join check
fd9d4e80 docs: John's notes on README-player.md
36b932c6 feat(COLDTICK): cat/patrol spawners skip ticketed but not-FULL chunks
bfa1e680 feat(THINDECK): no edge street half-built over water in cities
3ed23b3f chore: server-only-test finds Xvfb via xvfb-run on workers
77443c67 feat(DOORREACH): door-reaches-street check in city-check (report only)
805b4112 feat(CFGEXAMPLE): example config kept in step by a test
```

Each commit's body keeps the item squash's own message (bullets, known issues, how it was verified), then lists the run commits it stands for, the count of folded bookkeeping commits and a `Run-commits:` trailer; the last one carries `Promoted-from:`. Example: `git log -1 --format=%B <sha>` on `feat(DOORREACH)`.

# Rewrite of main (Phases 0-5)

- base: 9fb9dc8b1e11f9e1eef73a66180eef9f295cac0d (9fb9dc8b), run: main (934626b4), range: 9fb9dc8b1e11f9e1eef73a66180eef9f295cac0d..934626b406fffd37d3e63b1eb43273f271da52af
- branch written: claude/promote-rewrite-dryrun (d8477d45)
- run first-parent commits: 158 (code 68, docs 47, bookkeeping 43)
- promoted commits: 84 (empty groups dropped: 0)
- excluded paths (kept as on the commit it stacks on): docs/runs
- tree check: PASS (identical to main outside: docs/runs)

## Needs a look (20)

- eb50c754 no item code in subject: Research GDMC: GDMC-HTTP and GDPC tooling note
- cbfd76eb no item code in subject: Added some research notes to research INDEX.
- 284af7e6 no item code in subject: Phase 1 complete, awaiting gate
- beb73b20 no item code in subject: Client game tests: unset WAYLAND_DISPLAY, force SDL x11
- 7af85040 no item code in subject: Phase 2 gate passed; ADR 0003 accepted (supersedes 0002) with quiet-machine S6 numbers; SilverBullet ignore list
- 2d70bc74 no item code in subject: John feedback: docs/REVIEW.md
- d8b2ce6e no item code in subject: Ledger: Phase 3 run closed
- e4d14dbd no item code in subject: John feedback: docs/review/phase4/README.md
- 7ee555d9 no item code in subject: 5.12: open ground storeys for shops (arcade, market hall), workshop porch
- 2c3cd867 no item code in subject: 5.11: interior circulation - hub door graph, passages, archways
- 55412f34 no item code in subject: p5 T2: nine real-terrain gate windows with site table
- 316dfb2d no item code in subject: CONFIG.md ADR tag; SB_SPACE_IGNORE hides source/data
- f3209968 no item code in subject: Phase 5 gate review pack
- fe7c51e5 no item code in subject: John feedback: docs/REVIEW.md (roads, subways, landmarks)
- b83f0ee0 no item code in subject: 6.9: rail role beside exterior flights, landings and lookout stairs
- 55899225 no item code in subject: Viewer e2e: free preview port per run; scratchpad log-name note
- 40abde5d no item code in subject: Untrack mod/logs files re-added by B1
- 850f7886 no item code in subject: 6.3: terrain in a real world: street earth/headroom, deep foundations
- 4a994be3 no item code in subject: Added a couple utilities for gameplay galleries
- 934626b4 no item code in subject: Changes to use beebox as a remote worker

## Proposed history (oldest first)

```
a2e138d9 docs: templates and research index
e4d09cf8 feat: research GDMC: GDMC-HTTP and GDPC tooling note
87b6a2ae docs: terralith note revised
53c6d4d6 feat: added some research notes to research INDEX.
6c11db0e feat: phase 1 complete, awaiting gate
0633114b docs: john-work notes, Phase 1 gate passed
3d79bc60 build: client game tests: unset WAYLAND_DISPLAY, force SDL x11
591623fb feat: phase 2 gate passed; ADR 0003 accepted (supersedes 0002) with quiet-machine S6 numbers; SilverBullet ignore list
3cbba5fa docs: docs-feedback review loop documented; ROADMAP Updated: lines
a580445b feat(P3P): phase 3 plan, IR v1 types, probes, Hash64 and shared fixtures
cdc4858a feat: john feedback: docs/REVIEW.md
875147a2 feat: ledger: Phase 3 run closed
4c3b2382 docs: archive Phase 4 ROADMAP text; plan status done
72e38e20 feat: john feedback: docs/review/phase4/README.md
1c3d74d8 docs: phase 4 gate passed (John 2026-10-21); feedback items 5.11, 5.12; review loop checks main
77b925fd feat(p5-dg): world-wide district tile grid (DistrictGrid)
d3dbab20 feat(p5-t1): server-less vanilla terrain dump tool and first dumps
bef2f93b feat(ST): biome style field (StyleField)
ba9ef07a feat: open ground storeys for shops (arcade, market hall), workshop porch
cc553a62 feat(TA): terrain toolkit (water field, lattice A*, road/plaza grading)
4c448352 feat(R1): region field, settlement tiers and `fractal-cli region` in the core
ce2dd0c7 feat(V0): region map page (region.html) over region and terrain dumps
754155bb feat(P0): settlement IR (fb-settlement/1, fb-district/1), brief, fixture
815ec514 feat(V1): 2D plan view of settlement plans (viewer/plan.html)
ddb91d13 feat(MC1): join rule - big settlements claim neighbouring region cells
69e7a6e7 feat(p5-fix-block16): front steps may merge into an earlier flight
e42d17a6 feat(DB): lot briefs and district scenes with graded roads and plazas
de2c7b5d feat: interior circulation - hub door graph, passages, archways
19653aff feat(V2): district 3D view with terrain, water, roads and fly-through
edf04d06 docs: John's questions on Phase 5 plan decisions
a7683225 feat(SK): cell skeletons, fb-settlement/1 plans and fractal-cli city
ba857d52 docs: answers to John on city layout
cacc8874 feat(JT): jetties over streets (air rights, in-lot strip, house/shop)
33a0aa5d feat(R2): water bias for town, city and metropolis sites
5d2e1198 feat: p5 T2: nine real-terrain gate windows with site table
6d5db5d0 feat(SHOPLAND): shopfront landing at door level, flight down to the street
74758af6 feat(DL): district layout - streets, ports, grid blocks, lots, briefs
e7307d26 docs: answers to John, 5.13, 6.9, IRR1
b320fd56 feat(p5-plains-palette): calmer plains palette (similar-value blocks)
f9d6c20b feat(D0): city pipeline end to end, city-check, whole-city rules, presets
fdfb407c feat(CIRC2): squarer rooms, bedrooms before repeated room types
add93354 test(DT): settlement determinism suite over the city presets (5.6)
74105c77 docs: John's SB config request
16d1259d feat(MC2): cities across cells, solo sites, bounded pure caches
408a0574 feat(D1): hamlet and village tier programs, tier selector, presets
c1bbcbee feat: CONFIG.md ADR tag; SB_SPACE_IGNORE hides source/data
ff6279f9 feat(p5-house-program): one ground-floor kitchen and one living room per house
fb1ea100 feat(p5-hub-square): hub square searched on exact terrain, joined by lanes
5d4024bf feat(D2): town program, wall ring with gates and wall/tower lots
76499e2a feat(SC): city-bench scale numbers; probe bar not met
61b3a59d feat(TR): terrain response - quays, deck role, fuller hill mask, arterial grade
dc3c49b2 feat(ROADS): whole-city roads connected and graded once; KNOWN emptied
e54bc583 feat(D3): city and metropolis programs, density and skyline (5.7)
e2531a5a feat(IRR1): old-town subdivision and diagonal-avenue cut lanes
1ad4fbeb feat(p5-tidy): drop test tolerances, 64-cell sites, scale re-run
85ba2e09 feat: phase 5 gate review pack
85e7490d feat(p5-viewer-palette): per-building palettes and style ground tint in 3D
d1140377 docs: archive Phase 5 text
e8fd7e7c feat: john feedback: docs/REVIEW.md (roads, subways, landmarks)
23ff88bb docs: docs: S10, 6.10, Phase 7 transit and Create items, lintel backlog line; replies to John's 2026-10-22 notes
660877b2 feat(STAIRGROUND): exterior steps reach the ground; steps-reach-ground check
f2de9c90 docs(plan): docs/plans/phase6.md, NOTES API facts, ASSUMPTIONS defaults
a041caaa feat: rail role beside exterior flights, landings and lookout stairs
ad3d5456 feat(A5): release basics (LICENSE, jar version, icon, changelog, player README)
d61b33d9 feat(A3): cell raster, one owner per cell, block shapes (core/raster)
eb4a1de5 feat(A1): live probes with the dumps' exact heights, chunk memo, cost table
6309dd91 feat: viewer e2e: free preview port per run; scratchpad log-name note
c781d0f8 feat(A2): WorldPlanner, the per-world thread-safe planner facade
098704e8 feat(A4): block mapping, palette + Shape to Minecraft block states
731e0751 feat(B6): / 6.5: config file, per-world pinned settings, /fb config
25812de3 feat(A3F): building rooms own interior air; RAIL connects
b1646e34 feat(A1F): live biome from chunk memo, solid-above check, probe fixes
af788727 docs: notes, verifier: every Gradle run in the integration checkout holds the merge lock
52e15a68 feat(B1): district structures, placement and pieces in worldgen
0e1f2ac6 feat: untrack mod/logs files re-added by B1
55bb118b build(TESTSPEED): fork planner-core tests across JVMs
6fcdb6b0 feat(B4): worker pool, region warmer, prefetch at start, lane counters
26b7e766 feat(B5): beds, chests, job sites, villagers; /fb info and locate
fae97186 perf(CACHE): keep in-flight and recently used tiles past the cache caps
ee3c1ac6 docs(ADR): 0003: amend W2, P1, D4 for exact heights (John, 2026-10-22, Phase 6 decision 1)
f652eea6 feat: terrain in a real world: street earth/headroom, deep foundations
54e2f07b docs: John's John: answers to ADR 0004 questions 1-4
34f9b32e chore: added a couple utilities for gameplay galleries
d8477d45 feat: changes to use beebox as a remote worker
```
