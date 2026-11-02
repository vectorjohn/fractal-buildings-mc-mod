# DECKDIAG: why approach roads lay long decks along water, and what P6 does about it

Updated: 2026-11-02. Lead-coder diagnosis and plan for John's two questions of 2026-11-02 (run ledger: DECKDIAG).
Related: 6.15 ROADJOIN (roads between settlements don't meet the settlement's streets; "Long crossings decided"
note), 6.22 DEADPORT (roads that end at a port no approach joins), LONGDECK (long decks for towns and up only,
`claude/p6-longdeck`, in flight), [ADR 0004](../adr/0004-roads-between-settlements.md) (roads between settlements),
DS-19 in [STANDARDS](../design/STANDARDS.md) (water is crossed only by one span of at most 20 blocks), and the
architect's draft DS-19 interim amendment (not yet accepted).

Words used below:

- *Link* (graph road): the road between two settlements, from one *port* to the other.
- *Port*: where a link starts, just outside a settlement's edge.
- *Approach*: the short road from a settlement's exit gate to its port.
- *Deck*: a flat, raised road surface over water (a plain bridge slab).

# 1. The answer to John, in plain words

**Q2: "why do we get bridges along the water, don't we use path finding to point the roads towards their
destination? How does that happen?"**

- The roads between settlements (links) are path-found and checked carefully. They never get a deck longer than
  20 blocks: a longer one is rerouted, or the link is dropped. **Every long deck is on an approach**, the
  50-300 block stretch from a town's gate to the port where the link starts.
- The approach is path-found too, and it does point at its destination. In the clearest "along the water" case
  found (hamlet sm11_m2, below), the gate and the port both sit near the same lake shore, so the straight line
  between them follows the shore. The path finder has no reason to leave it, for three reasons:
  1. **It cannot see water between its grid points.** It plans on a 32-block grid and checks for water only at
     the grid points. It treats water less than 2 deep as dry land. A step from one dry point to the next can
     run beside the water, or jump over a river. It also adds **no extra cost for being next to water**. ADR 0004
     (WR1) says the search should, and the link router does (it adds a penalty near wet points); the approach
     router never got it. Its costs favour flat ground strongly: 1 block of climb costs as much as 6 blocks of
     road, and a slope steeper than 1 in 3 is not allowed. Flat ground is usually the strip next to a river or
     lake, so the road hugs the shore.
  2. **The deck code calls a road "wet" when it merely passes near water.** A point on the road counts as wet if
     any water lies within 8 blocks ahead or behind, or 1 block beyond the road's edge. Wet patches less than 32
     blocks apart are merged into one. The whole merged stretch then becomes one flat deck, 1-2 blocks above the
     water, including the dry ground in between. So a road that runs beside a shore, with dry ground under its
     centre, turns into a long flat "bridge" along the shore.
  3. **The fix-up step accepts any deck up to 100 blocks.** When it finds a run over 20 blocks it tries 3
     reroutes. Each one only bans the 4 grid points round the middle of the run and searches again with the
     same costs, which still can't see the water. It then keeps the best try even if that is still a 21-100
     block deck. It never tries a route a little further from the shore.
- **The "along" test is crude.** It counts a run as "along" when the water ends within 8 blocks on one side at
  more than half its points. Your 21.31.49 crossing is tagged "along" because its eastern half crosses a marsh
  of islets and puddles, so every point there has a shore nearby. That is why nothing could use the test to
  drop roads yet.
- Your four guesses: (a) water costs less than steep banks: not exactly. Water at a grid point is forbidden, but
  the flat ground next to water is the cheapest there is. (b) lattice resolution: yes, that is cause 1.
  (c) the deck flattening a shallow-angle crossing: yes, cause 2. 21.31.49 has 59 blocks of flat deck for a
  33-block river channel. (d) "along" mis-measuring an oblique crossing: yes, at 21.31.49.

An approach running beside a lake (hamlet sm11_m2 on your playtest seed, at -10496,-1440). North is up, one
character is 3 blocks. `~` is water, `.` is land, `=` is the flat deck, digits are the road's 8-block stations
(station 14 at the top, at the port).

```
.............~~~~~~~~~~~~~~~~~~~
..........4.~~~~~~~~~~~~~~~~~~~~     the road (4 = station 14, at the port)
..........=.~~~~~~~~~~~~~~~~~~~~     runs 2-5 blocks off the lake's west shore;
..........3~.~~~~~~~~~~~~~~~~~~~     its centre is dry at 9 of 11 stations,
..........=.~~~~~~~~~~~~~~~~~~~~     but water within the road's edge+1 or
..........2.~~~~~~~~~~~~~~~~~~~~     8 blocks ahead makes every station "wet":
.........==..~~~~~~~~~~~~~~~~~~~     80 blocks of flat deck at y 82 over
.........1..~.~~~~~~~~~~~~~~~~~~     ground at 70-80
..........0..~~~~~~~~~~~~~~~~~~~
...........9=~~~~~~~~~~~~~~~~~~~
.............~8=~~~~~~~~~~~~~~~~
...............~=6.~~~~~~~~~~~~~
................~.~5=..~.~.~~~~~
....................=4..~......~
.....................3..........     (station 3, the dry start of the run)
```

The same tool on your approved crossing (21.31.49, metropolis s1_2's approach k2_1_0 at 3201,70,1683): the gate
is at station 0 on the west bank. The route jumps the river on one diagonal 45-block grid step, then crosses a
marshy islet strip. All of it, dry stations 5 and 8 included, is one 59-block deck.

```
.....~~~~~~~~~...........~................
.....~~~~~~~~~....~~......................
.....~~~~~~~~~......=6==7=8==9--0.........   east bank: marsh, islets, puddles
...~~~~~~~~~~~~~..=5=.~~~.................
...~~~~~~~~~~~~~.==....~..................
...~~~~~~~~~~~~~=4.....~..................
...~~~~~~~~~~~~==~.....~..................
...~~~~~~~~~~~=3~~~....~..................   river channel, about 30 wide,
....~~~~~~~~~~=~~~~~......................   crossed at 45 degrees
.......~~~~~~2~~~~~~...~..................
........~~~~=~~~~~~~..~~..................
.........~~1=~~~~~~~~.~~..................
.........~==~~~~~~~~~~~~..................
.........~0~~~~~~~~~~~~~..................   station 0: the gate, on the bank
```

**Q3 (DEADPORT case c, a road that ends at a port with no approach) in one line:** none of these dead ends was
waiting on "allow the deck". Decks of 21-100 blocks were already built at every tier. The dead ends come from
other causes (section 5). The P6 steps below fix the one real case I could check on true terrain (sm11_m2, the
lake road above). The rest is P7.

# 2. Cases checked

The world dumps at 64-block steps (`world-*-s64`, `pt*-world-s64`) smear water: between samples the depth is
interpolated, so every lake grows a fringe of 1-deep "water" tens of blocks wide. On those dumps, 105 kept long
decks and 30 kept dead ends show up across four worlds. **Most of them are not in the game.** I re-planned six
places on fresh 4-block dumps of the playtest world 1709797029209580947 (layered over the s64 map as
`JoinWorld.probes` does; the skeleton then reads exact lattice columns, as in game). Two of the s64 long decks
disappeared outright (village s4_1, hamlet s8_4). Two of three s64 dead ends checked were not dead ends (towns
sm4_m6, s8_10). Never count long decks, "along" runs or dead ends on an s64 dump; use dumps at step 8 or finer.

Real-terrain cases (deck length is the `Profile` crossing length; "centre wet" is blocks of water under the road's
centre line over the deck's length):

| Case | Where | Deck | Centre wet | What is really happening | Old `alongBank` | Proposed test (F2) |
|---|---|---|---|---|---|---|
| metropolis s1_2 k2_1_0 (John's 21.31.49) | 3200,1696 to 3258,1663 | 59 | 43 of 64; longest run 33; river 31 wide | A real crossing. The skeleton's diagonal grid step jumps a 30-wide river at 45 degrees (the corner grid points are in the river; the link router forbids that cut, the approach router doesn't). Then a marsh of islets, merged into the same deck (gap under 32). | along (wrong) | across |
| hamlet sm11_m2 km11_m2_0 | -10462,-1400 to -10496,-1472 | 80 | 18 of 56; longest 11; only 1-deep puddles | **Along**: the exit-to-port line follows a lake shore 2-5 blocks out. The centre line is dry at 9 of 11 stations. It is also refused for a deck 7 below a perched pond (wetLow), so the port is a real **dead end**. | along | along |
| town sm4_m6 km4_m6_0 | -4256,-5696 to -4325,-5695 | 80 | 58 of 88; longest 41; neck 26 | A V round a lake's narrow neck. The grid point at the neck is in 1-2 deep water, so it counts as "dry". The legs hug the shore on both sides, and the deck covers both legs. A crossing, but 4 times longer than the neck. | along | across |
| town s8_10 k8_10_0 | 8672,10304 to 8709,10262 | 44 | 41 of 48; river 34 | A clean diagonal jump across a river. | along | across |
| town s3_9 k3_9_0 | 3296,9604 to 3264,9472 | 24 and 40 | 20 of 48; 29 of 48 | A 14-corner zig-zag skeleton route along a creek, two decks. | along | along (both) |

Probe code, dumps and raw output are in `.claude/reports/p6-deckdiag-probes/` (gitignored; forensic source):
`DeckDiagProbe.java` (world package test), `ProfileVariants.java` (road package test), `dd-*.json.gz`,
`prototype-shore-footprint.diff`.

# 3. The mechanism, with references

All line numbers are in `claude/run-phase6` at 61705eea.

- **Approach router (skeleton):** `LinkExits.approach`, `planner-core/.../settlement/LinkExits.java:82-115`.
  - Water is forbidden only at nodes (`:90`), via `CoarseTerrain.water` = `CoarseProbe.water` = depth >= 2 or a
    water biome (`probe/CoarseProbe.java:21`), on the 32-block lattice (`mod/.../LiveProbes.java:57`).
  - Diagonals may cut a wet corner.
  - Cost is `(10|14) * step + 60 * dy` (`:95`), and a grade over 1 in 3 is forbidden (`:94`). There is no
    near-water term.
  - Compare the link router `RoadNet` (64 lattice): a node is wet at depth >= 1 (`road/RoadNet.java:398`), a
    diagonal cutting a wet corner is forbidden (`:555`), and a node next to a wet node costs +200 (`:558`,
    `nearWet` `:420`). That penalty is ADR 0004 WR1's "the lattice search also charges extra for nodes next to
    wet nodes", and approaches are meant to follow the water rule too (N5).
- **Approach reroute:** `WorldPlanner.routeNodes` (`world/WorldPlanner.java:770-804`).
  - It reroutes only round a crossing over 20.
  - It bans the 4 lattice nodes round that crossing's middle station and calls `rerouteApproach` (`:1242`), the
    same blind cost as the skeleton (`:1251`, `:1260`).
  - It tries at most `RoadNet.MAX_REROUTES` = 3 times.
  - It keeps the attempt with the lowest `longRuns * 1000 + wetFailures` (`:783`), a count, not a length. So a
    100-block deck ties with a 21-block one.
  - It returns null (drops the approach) only when every attempt has a run over 100 (`:803`).
- **Wet test and deck:** `Profile.approach` (`road/Profile.java:186-282`).
  - A station is wet if water lies at any of 3 lines (the centre and `hw + 1` either side, `:206`), within
    +-8 blocks along the road (`:204`).
  - Groups closer than 32 merge (`:234`, ADR WR2).
  - The length is the arc span of all wet samples in the group, merged dry stations included.
  - Every station of the group gets one flat deck at max(higher bank, water + 1 or 2) (`:256`, `:271`), with
    `span` set one station beyond each end.
  - That is wider than ADR 0004 WR1 (a station is wet when *its* depth is at least 1). It was widened so that a
    stream between stations, or a road edge over a bank, gets a deck instead of failing `wetLowColumns`
    (`WorldPlanner.java:1233`).
- **Along test:** `Profile.alongBank` (`:109`): at more than half of the run's stations, water ends within
  `ALONG_NEAR` = 8 blocks on one side. Every station counts, including bank and puddle stations. It is used for
  `alongRuns` only (`:250`).

# 4. Proposed fixes, sized

Evidence comes from throwaway prototypes on the six real-terrain places (17 approaches) and on the w12345 s64
world (150 approaches; stress only, see section 2).

| Fix | What | Size | Risk | Evidence |
|---|---|---|---|---|
| **F1 footprint wet test** | A station is wet only when water lies under the road's own footprint (offsets -floor(hw)..+floor(hw), every block) within +-4 along (half a station, so stations tile the road). WR3 still decks every wet column under the road, so a stream between stations is still covered. | S | Low-medium: every approach with water near it changes its deck, and district plans change through `approachLines` only where a reroute changes. The `wetLowColumns` refusals did not rise in the prototype. | Real terrain: deck columns over dry land 1212 to 668 (-45 %), total deck columns 2007 to 1463, refusals 0 to 0, long decks unchanged (6). sm11_m2's run 80 to 40, k2_1_0 still 59. |
| **F2 along test, and drop along runs** | Replace `alongBank` with: at the run's stations with **centre depth >= 2** (real water, not a puddle or a fringe), at most half have water ending within 8 on one side, and at least half the centre line over the run is water. A run with no such station is along. An along run over 20 counts like a long run (score 1000, dropped when no attempt avoids it). That is John's "a run along water is never built (reroute or drop)". | S-M | Medium: more approaches dropped, so more links DROPPED or DEAD_END. Must land with F3, which removes the cases it would drop wrongly. | Real terrain: k2_1_0 across, sm11_m2 along, sm4_m6 across, s8_10 across, s3_9 along. On the s64 lake road w12345 k2_1_2 (John's road-0 case), all three runs are along. Without F2, F1 lets k2_1_2 back in (runs 63, 81, 29, all under 100). |
| **F3 shore-aware reroute attempt** | In `routeNodes`, when the best attempt still has a run over 20, try once more with `rerouteApproach(..., shore = true)`: wet := `coarse.water` or depth >= 1, no diagonal across a wet corner, +200 for a node next to a wet node (the link router's rule). Keep it when its score is lower. The score counts along runs too, with ties going to the shorter total of runs over 20. | M | Medium: approach routes change where they had long decks, so lots near them move. Order-independent (pure reads of lattice columns). | sm11_m2: the route moves one lattice column west (110 to 157 blocks long). Its long deck goes (0 runs over 20, 17 deck columns), and its DEADPORT dead end becomes LIVE (prototype F1+F3 in `WorldPlanner`, `deadFine` probe). k2_1_0, s8_10 and sm4_m6: no dry route (k2_1_0, s8_10) or a worse one (sm4_m6 88 vs 79), so the current route is kept. |
| F4 same costs in the skeleton router | `LinkExits.approach` gets F3's costs, so the first attempt is already shore-aware. | M | High churn: every skeleton approach near water changes, as do district plans, gate tests and fixtures. | Not prototyped. **P7**, with 7.3. |
| F5 crossing-aware routing | Let the approach lattice cross water on purpose (`LatticePath` water runs, cost per wet node, no corner cutting). The A* then picks the narrowest crossing, square to the bank, instead of an accidental jump. sm4_m6's neck could be about 20 instead of 80. Needs an exact-read edge check too, since 32-block nodes are too coarse for necks. | L | High. | One prototype (bridge cost 6 steps, one wet node) made k2_1_0 45 instead of 59 but s8_10 65 instead of 44. The 32 lattice is too coarse; a finer search is needed. **P7, 7.3 Bridges.** |

Recommended for P6: **F1 + F2 + F3 together** (one coder, or two in sequence, section 7). Alone, F1 brings back
along runs F2 then has to drop. F2 alone drops approaches F3 could have saved. F3 alone does nothing at
sm11_m2: its shore route still scores 1 under today's wide wet test, and wins only with F1.

## The architect's DS-19 draft, test (3)

The draft says a deck is a crossing when, 6 blocks from each end excluded, at most a quarter of it lies within
3 blocks of a shore. **On real terrain it calls John's approved k2_1_0 "along": 41 % of it is within 3 blocks of
a shore**, because of the islet marsh east of the channel. Measured with the same probe:

| Deck | Within 3 of a shore |
|---|---|
| k2_1_0 | 41 % |
| sm4_m6 | 55 % |
| s3_9 | 55-58 % |
| s8_10 | 19-25 % |
| sm11_m2 | 76 % (98 % with F1) |

No threshold separates k2_1_0 (approved crossing) from sm4_m6 and s3_9 cleanly, and on s64 dumps the fringe makes
it meaningless. F2's test passes k2_1_0 and fails sm11_m2, as the draft requires. Proposed wording for the
checkable rule: "apart from puddles and fringes (water under 2 deep), the deck's wet part has water on both sides
of the road for at least half its length, and water lies under at least half its centre line". The reviewer's
eye test in the draft ("ends on opposite banks, heads away from the bank it left") stands. Listed under Design
questions.

# 5. DEADPORT case (c) triage

What (c) is: a link kept although one end has no approach, because dropping it would leave the other settlement
with no link at all (`WorldPlanner.linkState`, DEAD_END).

**The long-deck decision unlocks none of them.** `routeNodes` already keeps 21-100 decks at every tier. LONGDECK
narrows that, so it can only add dead ends at hamlets and villages. Census on the four s64 worlds
(`DeckDiagProbe.worlds`; 30 dead ends; causes by where the approach was lost):

| Cause | Count (s64) | What it is | Fixable in P6? |
|---|---|---|---|
| Drawn approach refused (`wetLowColumns` > 0) | 19 | A road column on water at or below its surface: the street pin at the gate is below a stream or perched pond on a slope (`Profile.approach` keeps the street when the gap is over 2), or a deck pinned down to the port, like sm11_m2's deck 7 below a pond at y78. | Partly: where the cause is an along route (sm11_m2), **yes, by F1 + F3** (verified). Where it is a stream on a slope beside the gate, no: that needs a stepped or raised gate design. **P7** (it belongs with 7.19 FALLSTREETS, the street grading at gates, and 7.3). |
| Every attempt has a run over 100 | 6 | A long lake or river between gate and port. | Some may shrink under F1's narrower wet test; re-measure after F1 to F3. Runs that really are over 100 stay forbidden by the decision. **P7** (7.3). |
| No skeleton approach (no dry lattice path or no exit) | 5 | The skeleton router can't cross water at all, except by an accidental jump. | No: needs F4/F5 (crossing in the skeleton router). **P7** (7.3). |

On real terrain, of the three s64 dead ends I could re-plan: sm4_m6 and s8_10 are **not** dead ends (s64
artefacts), and sm11_m2 is real and is fixed by F1 + F3. So the true (c) count is unknown and probably well below
the s64 figure. **Recommendation:** in P6, do F1 to F3 and then run the census on fine terrain (step 4). Everything
left goes to P7 under 7.3 Bridges, plus 7.19 for the gate-slope refusals.

**LONGDECK interaction (for the coordinator):** LONGDECK passes `maxRun = 20` for hamlets and villages
(`claude/p6-longdeck`, uncommitted diff in `Profile.approach` and `routeNodes`). Today's wide wet test makes
phantom long runs (sm11_m2's 80 is 9/11 dry centre line). So LONGDECK alone will drop hamlet and village
approaches over decks that aren't there, and turn their links into DROPPED or DEAD_END. Land F1 with or before
LONGDECK, or re-count after both. Both branches edit the same lines (`Profile.approach` signature and the
`longRuns` line; the `Profile.approach` calls at `WorldPlanner.java:782` and `:1229`). Steps 1-3 should start from
the run branch after LONGDECK merges.

# 6. What stays as is

- The link router, `Profile.build` and the 20-block rule for links (no long decks there).
- The 100 cap, the flat deck form, `airUnder` and the deck material (DS-30).
- ADR 0004 WR2's 32-block merge for *counting* runs. F1 changes only which stations are wet. If the merged-island
  deck (k2_1_0's east half) is to become ground road between short decks, that is a design question (below), not
  this plan.

# 7. Steps

Base: `claude/run-phase6` after LONGDECK merges (both touch `Profile.approach` and `routeNodes`). Steps 1 and 3
can run in parallel, in separate worktrees: step 1 touches only `Profile` and its tests, step 3 only
`WorldPlanner`. Step 2 needs step 1 (same method). Step 4 needs all three. One coder doing 1 then 2 while another
does 3 is the fastest split; one coder doing 1 to 4 in sequence is also fine (about M+M in all).

Shared fixtures, for every step:

- **k2_1_0** is already committed: `JoinWorld.playtest()` (`pt1709-join-s2` over `pt1709-world-s64`), settlement
  cell (1,2), approach id `k2_1_0`, crossing from station 0 (3200,1696) to station 9 (3258,1663).
- **sm11_m2:** commit a new crop. Hamlet cell (-11,-2), centre about (-10464,-1376), approach `km11_m2_0`, port
  (-10496,-1472).

```sh
scripts/dev ./gradlew :mod:test --tests '*TerrainDumpToolTest.dump' \
  -Dfb.dump=1709797029209580947:-10624:-1600:2:161:161:pt1709-lake-s2 -Dfb.dump.out=../fixtures/terrain
```

  Add a row to `fixtures/terrain/README.md`, and a `JoinWorld.lake()` that layers it, `pt1709-join-s2` and
  `pt1709-world-s64` (finest first). Check that the planner reproduces the probe's numbers on it (the probes used a
  step-4 crop `dd-sm10_m2`; if the skeleton differs on the new crop, widen it to x -10688..-10240,
  z -1664..-1152). The other crops in `.claude/reports/p6-deckdiag-probes/` are for manual checks; don't commit
  them all (fixture budget).

## Step 1: footprint wet test (F1), S, parallel with step 3

- Code:
  - `Profile.approach` (`road/Profile.java:192-219`): station k is wet when some column with along-offset
    s in [-STATION/2, STATION/2) and lateral offset o in [-floor(hw), floor(hw)] (step 1 block) has
    `waterDepth >= 1`. Today it is s in [-8, 8] and o in {-(hw+1), 0, hw+1}.
  - Keep the end clamping (no s < 0 at k = 0, no s > 0 at k = n-1). Keep `water`/`firstArc`/`lastArc` from the
    same samples.
  - Update the javadoc (`:182-185`, `:211-212` comment) and Profile's class comment if it mentions the reach.
- Tests (`ApproachWaterTest`, planner-core):
  1. `besideTheShoreIsNotWet`: synthetic probe, water (depth 4) at z >= 3 for all x, dry elsewhere; width-3 road
     along z = 0 for 160 blocks. Expect no crossings, `wetFailures 0`. It fails on today's code: the side line at
     `hw + 1` = 2.5 rounds (`Profile.round`, floor(v + 0.5)) to z = 3, which is wet, so today the whole road is one
     160-block run. With F1 the footprint is z in [-1, 1], which is dry.
  2. `streamBetweenStationsIsStillDecked`: a 3-wide stream crossing the road between two stations (e.g. x 12..14).
     Expect one crossing with `span` true on both neighbouring stations, and the raster columns over the stream
     `crossing() == true` and above water.
  3. `k2_1_0KeepsItsCrossing`: on `JoinWorld.playtest()`, the approach k2_1_0 has exactly one crossing over 20,
     length 50-65.
  4. Existing `ApproachWaterTest` cases still pass (shore exit, wide river, steep pins).
- Corpus checks: `ApproachWaterTest.corpusHasNoDeckAtWaterLevel` stays 0 unexplained. Run `ApproachJoinTest`,
  `DeadPortTest`, `DeadPortPlaytestTest`, `ApproachCacheTest`. If one changes, inspect the case (map or top view)
  before re-baselining, and record each changed name in the log. `lakeRoadIsNotBuilt` (k2_1_2) **will fail on F1
  alone** (the prototype drew k2_1_2). That is expected until step 2: mark it in the log and don't loosen it.
- Mutation check: restore the 3-line offsets and confirm test 1 fails.

## Step 2: the along test and the along drop (F2), S-M, after step 1

- Code:
  - `Profile.alongBank` becomes:
    - over stations a..b, take only those whose own column has `waterDepth >= 2` ("deep");
    - `near` = deep stations where water ends within `ALONG_NEAR` (8) on the left or right (exact reads, as now,
      `SIDE_CAP` 40);
    - centre share = blocks of water under the centre line between `from` and `to`, sampled every block, over
      that span;
    - along := deep == 0 or near * 2 > deep or share * 2 < span.
  - Keep it pure and exact-read only.
  - In `routeNodes` (`WorldPlanner.java:783`): score `(longRuns + alongRuns) * 1000 + wetFailures`. Track
    `bestLong` as longRuns + alongRuns, so a route whose best attempt still runs along water over 20 returns null.
    The approach is not drawn, the DEADPORT rule decides the link, and step a (another exit gate) is still tried,
    because `alternateApproach` goes through `routeNodes`.
  - Ties: keep the existing order (first attempt wins).
  - In `drawApproach`, `Approach.alongRuns` stays as a count.
  - Update the `Profile.ApproachResult` javadoc. Say in the step's log that this closes gap 2 of the 6.15
    "Long crossings decided" note. The coder ticks nothing unless the coordinator makes a ROADMAP item.
- Tests:
  1. `k2_1_0IsACrossing`: `JoinWorld.playtest()`, approach k2_1_0, `alongRuns == 0`. It fails on today's
     `alongBank` (gives 1).
  2. `lakeShoreRunIsAlong`: `JoinWorld.lake()`, the sm11_m2 approach on its **skeleton** nodes
     (`source(site).skeleton(cell).approaches()`, id `km11_m2_0`, drawn with `Profile.approach`), `alongRuns == 1`.
  3. Synthetic: today's `longRunAlongABankIsCountedSeparately` keeps both asserts (along 1, across 0); add a
     shallow-marsh crossing (river 30 wide, depth 6, then a 20-block strip of depth-1 puddles) → across.
  4. `lakeRoadIsNotBuilt` (w12345 k2_1_2) passes again.
  5. Mutation: the old `alongBank` fails test 1. Dropping the "deep" filter fails the marsh case.
- Risk to record: approaches dropped for along runs. Print the count per corpus world in the log, before and after.

## Step 3: shore-aware reroute attempt (F3), M, parallel with step 1

- Code (`WorldPlanner`):
  - Add `boolean shore` to `rerouteApproach` (keep the old signature delegating with false). With shore:
    - wet := `coarse.water(x,z) || coarse.waterDepth(x,z) >= 1`;
    - a wet node is forbidden, as now;
    - a diagonal step whose either corner node is wet is forbidden;
    - +200 when any of the 8 neighbours of the new node is wet (the `RoadNet.nearWet` rule, ADR 0004 WR1).
  - Lattice reads only: the coarse probe at the 32 stride.
  - In `routeNodes`, after the attempt loop, when the best attempt has `longRuns + alongRuns > 0` (step 2's
    score; until step 2 merges, `wetFailures > 0`):
    - run one shore reroute from the first corner to the last of the **original** corners, with no banned nodes
      and the same `exits` and `urban`;
    - profile it the same way and take it when its score is strictly lower, or equal with a smaller sum of
      crossing lengths over 20.
  - The prototype diff is `.claude/reports/p6-deckdiag-probes/prototype-shore-footprint.diff` (the `shore`
    parameter and the extra attempt). It also has a `-Dfb.deckdiag.fp` switch in `Profile`, which is step 1's
    change: don't copy the switches or the `System.out` lines.
- Tests:
  1. `shoreRerouteLeavesTheLake`: on `JoinWorld.lake()`, the drawn sm11_m2 approach `km11_m2_0` has no crossing
     over 20, and `deadEndPorts(site)` is empty. It fails without step 1 (shore score ties at 1) and without
     step 3. So until both are merged, put it under step 4, or write it against step 1's branch merged in.
  2. A synthetic one that needs no step 1: a flat plane with a lake strip whose shore is 2 lattice columns from
     the straight exit-port line, wet nodes depth >= 1 on the lattice. The plain reroute keeps the shore line.
     The shore reroute moves at least 32 blocks away, and its profile has no crossing. Drive it through
     `routeNodes` via a small package-private seam if needed (e.g. a static `rerouteFor(...)` taking a
     `CoarseProbe`); don't make `routeNodes` public.
  3. Order independence: `DeadPortTest.theRuleDoesNotDependOnWhatIsAskedFirst` and the
     `ApproachCacheTest` / thread tests pass. The new attempt reads only lattice columns and exact stations, so it
     shouldn't need changes.
- Mutation: drop the +200 and confirm test 2 fails.

## Step 4: re-baseline, census and review, M, after steps 1-3

- Corpus tests that name outcomes will move.
  - In the prototype (F1 + F3, no F2) these failed: `ApproachWaterTest.lakeRoadIsNotBuilt`,
    `DeadPortTest.aDroppedLinkDrawsNothing` (a corpus link now LIVE), `anotherExitGateJoinsThePort` (k5_0 now
    served by its own approach, `k5_0_0`, not `k5_0_90`) and `theCorpusDeadEndsAreDropped` (w12345 s2_1 port
    2496,2176 now joined).
  - For each one, look at the case: is the new outcome right (a dry route found, a phantom deck gone)? Then pick a
    new case that still exercises the mechanism (DeadPortTest needs some dropped link and some other-gate rescue;
    find them with `DeadPortSweepProbe`). Never weaken an assert to pass.
- Census, fine terrain only:
  - Extend `DeadPortSweepProbe` or add an opt-in probe that, for the playtest worlds' settlements with a crop
    (pt1709 join, lake, pt1573 metro) and the gate windows, prints approaches with a deck over 20 (tier,
    length, along/across) and dead ends (cause as in section 5).
  - Before and after numbers go in the log.
  - Report the count of long decks per tier, as the architect's draft asks ("Counts in reviews").
- Look: top views of k2_1_0, sm11_m2 and one other changed approach before and after. Use the viewer
  `region.html` with a `roads` dump, or the CLI `roads --terrain recorded:...` and a scene. Open the PNGs.
  REVIEW item "DECKDIAG: approach roads off the shore" with 2-3 images copied to `docs/review/deckdiag/`.
- Layers: `scripts/test-scope.sh claude/run-phase6` will ask for planner-core, the server game tests, and
  probably the order test and the full client layer (road columns change in game). Run them as it prints;
  heavy ones go through `scripts/evidence.sh deckdiag ...`.
- NOTES lines to append (durable facts from this diagnosis):
  - Approach routers (`LinkExits.approach`, `WorldPlanner.rerouteApproach`) lacked ADR 0004 WR1's near-water
    cost and the link router's corner rule until DECKDIAG.
  - Counts of long decks, along runs or dead ends on s64 dumps are mostly interpolation artefacts; use step 8 or
    finer.
  - `TerrainDumpTool` crops of 1024x1024 at step 4 take a few seconds (`-Dfb.dump.out=../build/x`).

# 8. Risks and open questions

- **More drops (F2).** John said along runs are never built. If the census shows towns losing their only link,
  that is the rule working; report it, don't relax F2. Assumption if nobody answers: drop, as decided.
- **k2_1_0's merged deck over the islets** stays one 59-block deck (WR2's 32-block merge). If John wants ground
  road on the islets between short decks, that is a WR2 change (ADR) for 7.3. Assumption: unchanged in P6.
- **sm4_m6's V** stays an 80-block deck where a crossing of about 20 exists at the neck: the 32-block lattice can't
  find it (F5, P7). Assumption: accepted as interim, counted in the census.
- **District churn:** a rerouted approach changes `approachLines`, so the lots next to it move. That already happens
  with today's reroutes; galleries and gate tests may shift where approaches changed.
- **Cost:** F3 adds at most one lattice search per approach that already failed (bounded window, 200k
  expansions). F2 adds about 2 x 8 x deep-stations exact reads per long run. Neither is measured; not measured on
  a quiet host.

# 9. Design questions (for the architect; the coordinator relays)

1. The DS-19 draft test (3), "a quarter within 3 blocks of a shore", fails John's approved k2_1_0 (41 %). Would the
   architect take F2's test instead: at the stations with water at least 2 deep, the water reaches past 8 blocks
   on both sides at least half the time, and at least half the deck's centre line is over water? Or another number
   that passes k2_1_0 (41 %) and fails sm11_m2 (76 %, 98 % with F1)? sm4_m6 (55 %) and s3_9 (55-58 %) fall between
   those two.
2. Should an approach's deck over a marsh of islets (k2_1_0's east half) stay one deck, or become ground road on
   the islets between decks of at most 20? Today WR2's 32-block merge forces one deck.
3. sm4_m6: is a crossing deck 4 times longer than the narrowest crossing acceptable as interim (it is a town, and
   "across" by F2), or should such an approach be dropped until 7.3 finds the neck?

**Decided (John 2026-11-02, "take the architect's suggestions"):** question 3 is answered as the architect suggested.
sm4_m6's 80-block deck over the 26-block neck stays as an interim until 7.3 Bridges. The census counts it as "deck
over 2x the narrowest crossing", and the DECKDIAG review pack carries a top view of it. The DS-19 amendment (one
interim crossing deck at towns and up) is accepted in [STANDARDS](../design/STANDARDS.md).

  - @assistant 2026-11-02: filed John's acceptance. Nothing in the steps changes; step 4's census adds the "deck over
    2x the narrowest crossing" count and the sm4_m6 top view.
