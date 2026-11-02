# Polish backlog triage

Written 2026-11-01 by the assistant for John, from the "Polish backlog" section of the [ROADMAP](../ROADMAP.md) at commit a282a9b0 (181 lines). The Polish backlog is where verifiers and reviewers put known issues that don't break anything; nothing schedules from it. Each line got one of four buckets:

- **P6**: a player would hit it in 0.1.0 (a visible bug, a freeze, a building you can't get into), or it could make the release test run lie. Proposed only; nothing was added to the Phase 6 checklist.
- **P7**: it belongs to a planned Phase 7 item, named by its ROADMAP number.
- **Keep**: low priority, no schedule (mostly test gaps, tooling and small looks issues).
- **Drop**: already fixed or no longer true; removed from the ROADMAP, with the fix listed at the end of this page.

Checks were cheap (grep, git log, the docs); nothing was run, and I didn't look at screenshots.

# Summary

| Bucket | Lines |
|---|---|
| P6 (proposed) | 9 lines, grouped into 5 items |
| P7 | 38 (7.10 roads in one material: 8; 7.5 furnishings: 7; 7.11 smooth roads: 5; 7.19 FALLSTREETS (streets on falling ground stay near the ground): 5; 7.24 HRENTRY (high-rise entrances): 3; 7.17 SHOPLOW (doors near the street), 7.20 METROFILL (filled city cores), 7.21 SQUARES (framed squares and markets): 2 each; 7.3 bridges, 7.16 slope in the region screen, 7.23 SHOPFLAT (shop room programme), 7.26 BEARDFIT (entrance flights fitted to the final ground): 1 each) |
| Keep | 118 |
| Drop | 16 |

In the ROADMAP: the Drop lines are removed, P7 lines end in `(P7: 7.x)`, P6 lines end in "(proposed P6, see polish-triage)", and nothing else was reworded.

# Proposed P6 items

1. **Lone glass pane on a wall top** (GLASSSTUB, from your 2026-10-26 playtest, 19.45.26): a glass pane floating above the wall near the desert high-rises. A player sees a floating block, which our completion bar counts as breaking. Not diagnosed; the architect suspects a window cell above a setback or parapet. Fix: diagnose, then a `pane-attached` check. Small.
2. **Test runs that pass while testing the wrong thing** (TESTLAYERS (client test layers) and the worker's environment): a misspelt `FB_ONLY` test name skips every client test and the run still exits 0, and `scripts/remote.sh` drops environment variables, so `FB_LAYER=full` set before it silently runs the quick layer on the perf worker. Players never see it, but either one can make the release heavy run green without testing what it says. Fix: reject unknown test names with exit 2, and forward `FB_*` variables to the worker. Small, scripts and test code only.
3. **Multi-second server freezes on cold arrival, cause unknown** (COLDARRIVAL (cold-arrival perf rows) and 6.30 PORTALCOLD (portal trips into an unvisited city)): a 9-11 s server tick while arriving at a cold metropolis on the perf worker, and one 6.35 s tick after a portal arrival (1 run in 6). A player sees the world stop. The harness now logs the server thread's stack for long ticks, so the next occurrence says where. Fix: one diagnosis pass on the perf worker. This overlaps your open REGIONPERF (cold-arrival budget) question, so you may prefer to fold it in there.
4. **Doors with no path from the street** (from your Phase 5 review "Waterfront quays and bridges"): back-row shops behind a waterfront with no visible path or door toward the road. Today's checks stop at the lot edge, so nothing tells us how often a building is cut off. Fix: add a `door-reaches-street` check to `city-check`, measure, and fix if the count isn't near zero. Small to medium; I'm not sure how common it is now, after DS35DOORS (more entrances) and 6.20 DOORBANK (no doors facing a street wall).
5. **Perf rows that fail their ceiling on the perf worker** (decision only): the region row (11.3-11.6 s against 10 s, failing on the vanilla base too) and the cold district plan p95 (3.67 s against 3 s). The release heavy run includes `perf.sh`, so these fail it until you pick a ceiling or a fix. This isn't new work: it's your two open lines in [QUESTIONS](../QUESTIONS.md) ("REGIONPERF budget" and "Cold district plan ceiling").

- @John 2026-11-02: I approve all these. However, if (4.) causes us to drop buildings or something, then no, don’t deal with 4 and we can push it to P7. For 5 I think we have a new plan going forward, perf items can be marked known but we don’t want to forget about them.

# Not sure

- **Thin deck into open water** (playtest 2026-10-28, 21.01.30): filed as P7 7.3 bridges, but nobody has diagnosed it. If it's a crossing deck that ends in the water (a road to nowhere), it's the 6.22 DEADPORT (road to nowhere over a bridge) kind and could be P6.
  - @John 2026-11-02: look into it. See if it’s DEADPORT.
- **PARREADS** (parallel terrain reads) line dropped: the unbound tags and the busy-host timings are fixed. Part (1), a structure start tracing quays on a worldgen thread, is much smaller since QUAYLAZY (lazy quay trace) but not zero. PERFORMANCE.md tracks what remains.
  - @John 2026-11-02: not sure. What is even the question here? I think this line can go away.
- **SWITCHBACK** (more generous exterior flights) dropped as done by 6.31 STAIRJOG (straight entrance flights). The architect's landing sizes and headroom weren't checked one by one; what's left is in the 6.31 line (P7 7.24).
  - @John 2026-11-02: if there is more work, it’s P7.
- **Plains palette** (your Phase 4 REVIEW request) dropped as done by PALETTE: plains walls are now birch planks with one stripped-birch accent. It's worth a look in game if you still see speckle.
  - @John 2026-11-02: drop this as done. I’ll re-raise if needed.
- **6.15 ROADJOIN** (roads between settlements meet the streets) is still open in Phase 6. Its three backlog lines (route preference and two lips on one seed) are filed as Keep, not as part of closing 6.15.
  - @John 2026-11-02: check if they’re done. If not, P7.

# All lines

Order as in the ROADMAP. "Fix" names the commit or item for drops.

| Line | Bucket | Reason | Fix |
|---|---|---|---|
| 6.6 vanilla-structure veto cold cost | Keep | a one-off cost per fresh area, tracked in PERFORMANCE.md with the other cold-arrival costs |  |
| 6.3 street junction heights (JUNCTION rounds) | Keep | mostly fixed by JUNCTION rounds 1-3; what remains is plaza edges against streets, a separate small item |  |
| 0.1b viewer voxel ordering test | Keep | test gap in the viewer; no player effect |  |
| 0.1b `npm run dev` never run | Drop | the dev server has been in daily use since Phase 3 | [[docs/VIEWER|VIEWER.md]] documents it |
| 0.1a boundary check matches comments | Keep | a nuisance for coders only |  |
| 0.1a unused HashMap import in the CLI | Drop | the import is gone (grep finds no HashMap in `cli/.../Main.java`) | code as of a282a9b0 |
| 0.6 test-all layer detection untested | Drop | the layers landed long ago and test-all runs after every merge | Phase 0 items 0.4/0.5 |
| 0.6 test-all deadlocks under a held heavy lock | Keep | documented in `--help`; agents only |  |
| 0.4 `smokeBox` ignores the seed | Drop | `Planner.smokeBox` no longer exists | code as of a282a9b0 |
| 0.5 client layer "no count in log" | Keep | cosmetic summary line |  |
| 0.5 stale client screenshots | Keep | can mislead a reviewer, not a player; still true (`mod/build.gradle` copies, never clears) |  |
| 3 SampleScenes in the main jar | Keep | a few KB in the jar; harmless |  |
| 3.2 probe test gaps | Keep | test gaps |  |
| 3.1 IrValidator storey checks | Keep | test gap; other invariants catch bad storeys |  |
| 3.5 viewer scene test | Keep | viewer test gap |  |
| 3.9a invariant mutant gaps | Keep | test gaps; the basement note goes with 7.18 when it starts |  |
| 3.3 no front steps, no height cap | Drop | front steps exist since 4.8 and were fixed by 6.12 STEPTOP and 6.19 PORCHSTEP; `Invariants.WORLD_MAX_Y` caps height | 4.8, 6.12, 6.19; f651c3bf |
| 3.6 viewer seed box empty in fixture mode | Keep | viewer cosmetic |  |
| 3.8 thin line in the top view | Keep | viewer rendering artefact |  |
| 3.8 gallery noise terrain repeats | Keep | review tooling |  |
| 3.8 flat-shaded roofs in the top view | Keep | viewer cosmetic |  |
| 3.4 `check` exit-1 path untested | Keep | test gap |  |
| 3.4 property case replay | Keep | tooling convenience |  |
| 4.3 roof form limits | Keep | looks; more roof forms are a later style item |  |
| 4.1 plan text on golden digests | Keep | doc slip in an old plan |  |
| 4.2 massing limits | Keep | looks; `MassingOps.stack` is now used by towers, the rest still true |  |
| 4.3 roof distance cost, parapet gap | Keep | no measured cost; one-cell parapet gap is cosmetic |  |
| 4.4 facade details | Keep | looks; 7.12 changed windows, braces still missing |  |
| 4.5 furnishing hook under a window | Keep | looks; 7.5 replaces the furnishing pass |  |
| 4.8 step test gaps | Keep | test gaps |  |
| 4.9 viewer render test gaps | Keep | viewer test gaps |  |
| 4.6 house: more single-storey houses | Keep | looks |  |
| 4.6 tower details | Keep | looks |  |
| 4.6 workshop details | Keep | looks; yard props could join 7.5 |  |
| 4.6 shop: no awning or sign | Keep | looks |  |
| 4.6 temple windows repeat per storey | Keep | looks; 7.12 left temples below the glazing range |  |
| 4.6 wall type limits | Keep | looks; the dead test loop is still there |  |
| 4.6 high-rise tiers and crown | Keep | looks; not covered by 7.13 (floor plan, stairs, roof access) |  |
| 4.6 hall limits | Keep | looks |  |
| 4.6 city block limits | Keep | looks; 7.20 METROFILL may revisit whole-block lots |  |
| 4.7 plains wall gradient noisy | Drop | plains walls are now birch planks with one stripped-birch accent | bb083c9b PALETTE |
| 4.7 desert speckle, test whitelists | Keep | the yellow terracotta is gone (PALETTE); the two test whitelists remain |  |
| 4.7 taiga/snowy dormers and gradient | Keep | looks; see also DS36ROOFS speckle line |  |
| 4.7 jungle verandas | Keep | looks; biome forms are unscheduled |  |
| 4 duplicate lines in NOTES/ASSUMPTIONS | Keep | doc slip; those files are append-only, so it stays until someone tidies with care |  |
| 4.7 medieval style by name only | Keep | open question since the Phase 4 gate; harmless in game |  |
| FLK viewer stale rebuild state | Keep | viewer edge case; still true |  |
| 4.7 plains palette smoother (John) | Drop | done: plains walls are near one colour now | bb083c9b PALETTE |
| 5 biome-specific building forms | Keep | a feature idea with no item; candidate for the P7 choice (release-plan Q6) |  |
| Balconies | Keep | a feature idea (DS-28); no item yet |  |
| 5.1 D1 rugged-ground green | Keep | 1 in 400 villages; looks |  |
| 5.1 D2 town rings | Keep | looks |  |
| 5.1 ROADS2 street lattice frame | Keep | looks; 7.20 METROFILL measures it |  |
| 5.1 ROADS3 steep ramps to gates | Keep | looks; 7.19 FALLSTREETS may cover part |  |
| 5.2 DL skeleton limits | Keep | looks |  |
| 5.2 DL r2 port stubs, earthwork | Keep | looks |  |
| 5.3 TR no stairs in steep lanes | P7 7.19 | 7.19 FALLSTREETS adds stepped lanes |  |
| 5.7 D3 diagonal avenues empty | P7 7.20 | 7.20 METROFILL fills cores, incl. diagonal frontage |  |
| 5.8 MC2 CLI `--site` notes | Keep | tooling |  |
| 5.9 SC skeleton cache bars | Keep | perf targets superseded by the 6.7 budget |  |
| 5.10 R2 water share unmeasurable | Keep | measurement gap |  |
| 5.11 CIRC shops chain through a back room | P7 7.23 | 7.23 SHOPFLAT gives shops a room programme |  |
| 5.11 OPENPLAN known limits | Keep | looks; no dining room use yet |  |
| 5.11 OPENPLAN lintels and arches | Keep | looks; the palette now has slab and stair shapes, so it is buildable |  |
| 5.12 market hall empty | P7 7.21 | 7.21 SQUARES puts market houses in squares (stalls with 7.5) |  |
| 6.7 teleport hitch (TPHOLD) | Drop | done: TPHOLD (hold far teleports until the destination is generated), cap now 60 s | [[docs/log/2026-10-23-p6-tphold|TPHOLD]], [[docs/log/2026-11-01-p6-tphold60|TPHOLD60]] 0dffaccf |
| 6.8 CITYQ region screen | P7 7.16 | 7.16 adds the slope term to the region screen |  |
| 6.x TESTISO server test hygiene | Keep | test hygiene; a timed-out test can disturb the next, but does not hide a failure |  |
| 6.x FOLIAGE city woodland | Keep | John's decision (keep trees) holds; thinning is optional |  |
| 5.1 D1 hamlet streets straight (DS-21) | Keep | looks; no item; candidate for the P7 choice |  |
| 5.3 TR doors with no path from the street | P6 | a building whose door no street or lane reaches; nothing checks it at settlement level; measure first |  |
| 6.x PLAZAS bare paved squares | P7 7.21 | 7.21 SQUARES (with 7.5 for benches and lamps) |  |
| 7.20 diagonal avenues: keep or remove | P7 7.20 | John decides after 7.20 METROFILL |  |
| 6.x LAMPS two lights per room | Keep | looks; small |  |
| 6.x DOORGLASS shop door frame (DS-51) | Keep | looks; DS-51 is accepted but not built (main doors have jambs, secondary doors don't) |  |
| 6.x GLASSSTUB lone glass pane on a wall top | P6 | a floating block, which the completion bar counts as breaking; not diagnosed |  |
| 6.x WORKSHOP lean-to doors | Drop | done by DS35DOORS (more entrances): store with a yard door | [[docs/log/2026-10-28-ds35doors|DS35DOORS]] 1636aa9c |
| 6.x DORMER roofs in stairs | Keep | looks; DS36ROOFS did the main roofs, not dormers |  |
| 6.x SWITCHBACK generous flights | Drop | done by 6.31 STAIRJOG: straight runs first, runs of 2+, `flight-runs`/`flight-turns` checks; leftovers are in the 6.31 line | [[docs/log/2026-10-31-p6-stairjog|STAIRJOG]] 45a96b8c |
| 6.x PARTITIONS pale interior walls (DS-49) | Keep | looks; needs a `partition` role |  |
| 6.12 STEPTOP unit cases | Keep | test gap; scene-level test covers it |  |
| 6.12 STEPTOP landing look | Keep | looks; with the architect |  |
| 7.10 ROADS1 desert surfaces | P7 7.10 | road materials |  |
| 7.10 ROADS1 asphalt | P7 7.10 | road materials (John asked in REVIEW) |  |
| 7.10 ROADS1 quay layout | P7 7.10 | quay and street layout at the water |  |
| 7.10 ROADS1 snow on decks | P7 7.10 | treat decks like streets |  |
| 7.10 ROADS1 raw cut on a track | P7 7.11 | grading of cuts is 7.11 (and 7.19) |  |
| 7.10 ROADS1 kerbs | P7 7.11 | 7.11 part 2 already |  |
| 7.10 ROADS1 city bridge piers | P7 7.10 | DS-44 enforcement in 7.10; 7.3 may rebuild bridges |  |
| 7.10 ROADS1 jungle wear | P7 7.10 | wear variants of DS-30 |  |
| 7.10 ROADS1 client test exemption by tier | Keep | test precision |  |
| 7.10 ROADS1 plaza surface role | P7 7.10 | plaza paving per style; see 7.21 |  |
| 7.10 ROADS1 doc slip | P7 7.10 | fix when 7.10 is ticked |  |
| 6.18 ROOMWALK bed heads under windows | P7 7.5 | furniture placement |  |
| 6.18 ROOMWALK beds across 3-wide rooms | P7 7.5 | furniture placement |  |
| 6.18 ROOMWALK keep the last bed | P7 7.5 | furniture placement; dwellings without a bed |  |
| 6.18 ROOMWALK bastion walk room | P7 7.5 | furniture |  |
| 6.18 ROOMWALK wide halls | P7 7.5 | do before 7.5 furnishes halls |  |
| 6.18 ROOMWALK test gaps | Keep | test gaps |  |
| 6.16 DEADMAIN water criteria differ | Keep | consistency; no seen effect |  |
| 6.16 DEADMAIN shore gates exempt | Keep | 3 seen; road ends at a cell border on a shore; looks |  |
| 7.17 SHOPLOW frontY vs door rise | P7 7.17 | tighten in S9 |  |
| 7.17 SHOPLOW stoop test checks little | P7 7.17 | test fixture for SHOPLOW |  |
| 6.15 ROADJOIN route preference | Keep | looks; the road still meets the street |  |
| 6.15 ROADJOIN shore lip | Keep | one 2-block lip on one seed; walkable round it |  |
| 6.15 ROADJOIN deck corner lip | Keep | one side lip; within the exit bar |  |
| 6.15 ROADJOIN test label mismatch | Keep | still mismatched (prints `exitStep>1`, counts `> 2`) |  |
| 6.15 ROADJOIN CLI lacks approach corridor | Keep | review tooling |  |
| 6.15 ROADJOIN mid-street exit count | Keep | measurement |  |
| TESTLAYERS `FB_ONLY` typo runs nothing | P6 | a misspelt test name skips every client test and exits 0: a green run that tested nothing |  |
| TESTLAYERS verifier to-main lacks `--full` | Drop | verifier.md now runs `test-all.sh --full --planner-heavy` on the worker | 481866c5 TESTSPEED |
| TESTLAYERS CLAUDE.md order-test line | Drop | CLAUDE.md now says order-test writes `eula=true` itself | a69f6461 |
| REGIONPERF warmQuays not asserted | Keep | test gap; QUAYLAZY reworked the warmer since |  |
| ORDERFAIL lake veto has no upper bound | Keep | a few vanilla lakes vetoed needlessly; looks |  |
| SPILLDIRT air-base branch untested | Keep | test gap |  |
| SPILLDIRT spill test too lenient | Keep | test precision |  |
| SPILLDIRT perf not run | Drop | perf.sh has run on later commits, quietly on the perf worker | 1ea75a7d CASCADE 7, be34d4eb QUAYFAST 5+6 |
| BRIDGEENDS fill count, no in-game shot | Keep | measurement and evidence gap |  |
| BRIDGEENDS 4-high earth faces | Keep | looks; 15 cells in 4 of 12 scenes |  |
| ORDERSIM S0 test gaps | Keep | test gaps |  |
| ORDERSIM S3 test gaps | Keep | test gaps; copy drift risk |  |
| ROCKVETO doc and audit gaps | Keep | doc slips; audit of other features |  |
| TREEINORDER test gaps | Keep | test gaps |  |
| VILLROAD spur offset | Keep | looks |  |
| VILLROAD spur steps every cell | P7 7.11 | DS-31 smooth roads |  |
| DSROADS approach teeth, gate posts | P7 7.11 | gate markers and kerb ends are 7.11 part 2; teeth are small |  |
| DS35DOORS long side-door climbs | Keep | John 2026-10-28: keep long garden stairs |  |
| DS36ROOFS snowy roof speckle | Keep | looks |  |
| DS35DOORS lean-to and room programmes | P7 7.5 | furniture pieces and room programmes |  |
| DS35DOORS high-rise second door | P7 7.24 | 7.24 HRENTRY: more high-rise doors |  |
| DS35DOORS workshop wing programmes | P7 7.5 | room programmes |  |
| FBNOTE quick layer, gallery links | Keep | test time and doc rendering |  |
| FOLIAGECLUSTER rare remnant | Keep | rare, order dependent remnant at cliffs |  |
| ORDERSIM S4a test gaps | Keep | test gaps |  |
| FLY2 street frames without frontage | Keep | review tooling (gate kit `--fly`) |  |
| FLY2 low-fill frames | Keep | review tooling |  |
| FLY2 bridge detection | Keep | review tooling |  |
| FLY2 content findings | P7 7.19 | raw cuts are 7.19; steps and kerbs 7.11; long crossings 7.3 |  |
| PARREADS quay trace, unbound tags, busy timings | Drop | tags bound by HARNESSTAGS; quiet re-measure done; QUAYLAZY took the whole-city quay trace out of chunk starts | [[docs/log/2026-10-29-harnesstags|HARNESSTAGS]], e4447087, [[docs/log/2026-10-31-p6-quaylazy-5|QUAYLAZY 5]] fb2b608e |
| Playtest 20.25.33 road to nowhere | Drop | one of John's four shots that 6.25 MAINSTUB fixed | [[docs/log/2026-10-29-p6-mainstub|MAINSTUB]] 9a6c5d45 |
| Playtest 20.33.42 road could follow the river | Keep | looks |  |
| Playtest 20.37.46 full step at a road crest | P7 7.11 | slabs on road rises |  |
| Playtest 20.52.10 tree before a door | Keep | John: "I could live with it" |  |
| Playtest 21.01.30 thin deck into open water | P7 7.3 | not diagnosed; real docks are 7.4, decks 7.3 (unsure, see summary) |  |
| 6.20 DOORBANK podium before emptying a lot | P7 7.19 | the root fix is streets near the ground |  |
| 6.20 DOORBANK podium faces, tower landmarks | P7 7.24 | 7.24 HRENTRY has podiums |  |
| 6.20 DOORBANK rails and greens on emptied lots | P7 7.19 | with FALLSTREETS |  |
| 6.20 DOORBANK mutant needs all sites | Keep | test gap |  |
| 6.19 PORCHSTEP step material, door facing | Keep | looks |  |
| 6.19 PORCHSTEP drops at side doors, shop in a pit | P7 7.19 | street cuts below doors; 7.17 for the shop |  |
| 6.19 PORCHSTEP partial steps on wide doors | Keep | 1 % of wide doors; walkable |  |
| 6.19 PORCHSTEP CLI accepts 1-block jump | Keep | test gap; raster walk test guards it |  |
| 6.23 HAMLETEMPTY sm4_m1 link short of street | Keep | one seed; looks |  |
| 6.23 HAMLETEMPTY detours double back | Keep | waits for proposed DS-57 |  |
| 6.23 HAMLETEMPTY thin hamlet, CLI gaps | Keep | looks and tooling |  |
| 6.22 DEADPORT report marks | Keep | tooling |  |
| 6.25 MAINSTUB turning head, corridor | Keep | looks |  |
| 6.25 MAINSTUB city-check count | Keep | tooling |  |
| 6.29 ROADLEAFORDER camera | Keep | test coverage |  |
| 6.27 DISTPERF re-measure | Drop | re-measured on the quiet perf worker; the open ceiling question is in QUESTIONS | 1ea75a7d CASCADE 7 |
| 6.28 LEAFORDER stale wording | Keep | doc slip; still worded "counts ... as free" |  |
| 6.25 MAINSTUB region row over ceiling on the perf worker | P6 | fails the release heavy run's perf step; decide with REGIONPERF (already in QUESTIONS) |  |
| COLDARRIVAL 9-11 s server tick | P6 | a 9-11 s freeze on cold metropolis arrival, cause unknown |  |
| 6.x TPHOLD15 test gaps | Keep | test gaps |  |
| 6.30 PORTALCOLD test gaps | Keep | test gaps; the 6.35 s tick is carried by the next PORTALCOLD line |  |
| 6.33 MAINSTEP side cells as landings | Keep | looks; fewer wide steps |  |
| 6.x QUAYLAZY flight tick, region row | P6 | region row over its ceiling: same decision as REGIONPERF; flight tick under its ceiling |  |
| 6.x CASCADE 5 test gaps | Keep | test gaps |  |
| 6.x ORDERPORT fixed test ports | Keep | only collides when two such runs share a host |  |
| 6.x CASCADE 5b counter leak | Keep | possible flake, not seen; watch |  |
| 6.x CASCADE 3 test gaps | Keep | test gaps |  |
| 6.x CASCADE 4 notes | Keep | speed only |  |
| 6.x CASCADE2FIX cold district p95 over ceiling | P6 | fails the release heavy run's perf step; ceiling question already in QUESTIONS |  |
| 6.x remote.sh drops env variables | P6 | `FB_LAYER=full` or `FB_ONLY` set before the worker call are lost: the run tests something else and can pass |  |
| 6.x evidence.sh inside anywhere.sh | Keep | agents only; the documented order works |  |
| 6.x QUAYFAST 5+6 test gaps | Keep | test gaps; budget tidy-up |  |
| 6.31 STAIRJOG fallback flights | P7 7.24 | mostly superseded by 7.24 HRENTRY |  |
| 6.32 DOORAIR make the count an assertion | Keep | test hardening, small |  |
| 6.30 PORTALCOLD 6.35 s tick, mobs, `execute in` | P6 | unexplained 6.35 s freeze after a portal arrival (1 in 6); diagnose with COLDARRIVAL |  |
| PERFBASE labels and duplication | Keep | doc and code tidy-up |  |
| Playtest 02.44.32 flight foot in the grass | P7 7.26 | 7.26 BEARDFIT is this exact note |  |

# Removed from the ROADMAP (Drop)

- 0.1b `npm run dev` never run: the dev server has been in use since Phase 3; [VIEWER.md](../VIEWER.md) documents it.
- 0.1a unused `HashMap` import in `cli/.../Main.java`: gone (grep at a282a9b0).
- 0.6 test-all layer detection untested until 0.4/0.5 land: they landed, and test-all runs after every merge.
- 0.4 `Planner.smokeBox` ignores the seed: the method no longer exists.
- 3.3 no front steps, no world-height cap: front steps since 4.8, fixed further by 6.12 STEPTOP (flights end in a step, not a jump) and 6.19 PORCHSTEP (step up to raised doors); `Invariants.WORLD_MAX_Y` = 319 (f651c3bf).
- 4.7 plains wall gradient noisy, and your plains palette request: bb083c9b PALETTE.
- 6.7 teleport hitch: done as TPHOLD (hold far teleports until the destination is generated), [log](../log/2026-10-23-p6-tphold.md); cap 60 s in 0dffaccf ([log](../log/2026-11-01-p6-tphold60.md)).
- 6.x WORKSHOP lean-to: done by DS35DOORS, 1636aa9c ([log](../log/2026-10-28-ds35doors.md)).
- 6.x SWITCHBACK: done by 6.31 STAIRJOG, 45a96b8c ([log](../log/2026-10-31-p6-stairjog.md)).
- TESTLAYERS verifier to-main command lacks `--full`: fixed in 481866c5 (TESTSPEED).
- TESTLAYERS CLAUDE.md order-test line out of date: fixed in a69f6461.
- SPILLDIRT perf not run: `perf.sh` ran quietly on later commits (1ea75a7d CASCADE 7, be34d4eb QUAYFAST 5+6).
- PARREADS quay trace, unbound tags, busy timings: HARNESSTAGS ([log](../log/2026-10-29-harnesstags.md)), quiet re-measure e4447087, QUAYLAZY 5 fb2b608e ([log](../log/2026-10-31-p6-quaylazy-5.md)).
- Playtest 2026-10-28 20.25.33 road to nowhere: fixed by 6.25 MAINSTUB (main roads that end at a gate), 9a6c5d45 ([log](../log/2026-10-29-p6-mainstub.md)).
- 6.27 DISTPERF re-measure on a quiet host: done in 1ea75a7d (CASCADE 7); the ceiling question is open in QUESTIONS.
