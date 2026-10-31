# Release plan: Phase 6 gate and increments

Written 2026-10-29 by the assistant for John, from ROADMAP Phase 6 and 7, the
open REVIEW items, the run ledger and the two playtests
([2026-10-26](phase6/playtest-comments/REVIEW.md), build e237e451;
[2026-10-28](phase6/playtest-2026-10-28/review.md), build b0288bd0; triaged 2026-10-29).
Nothing in the ROADMAP changes until you answer the questions at the end.

Phase 6's gate is: **a fresh world on three seeds plays well for an hour; then
a first public release.** "Blocks release" below means broken or clearly bad
for a player in a fresh world. "Nicer" is not enough.

# 1. P6 release gate checklist

Status values: done, in progress, not started, needs John. The tag is the one
I suggest you give it.

## Bugs from the playtests

- [ ] **6.15 ROADJOIN, roads between settlements meet the streets.** In
  progress: steps 1-4 merged (7ec724a9). Still open: your call on long
  crossings (REVIEW "6.15 ROADJOIN: an approach road with no dry route"),
  the `dead-end-port` follow-up, and the step 5 in-game check. Blocks: a
  player who follows a road arrives at a step, a gap or a dead end.
  Tag `#backlog-p6`.
- [ ] **6.22 DEADPORT: road to nowhere over a big bridge** (2026-10-28,
  [20.02.18](phase6/playtest-2026-10-28/images/2026-10-28_20.02.18.jpg) at
  3542,116,-2186). Not started. This is the `dead-end-port` case of 6.15,
  seen in game after steps 1-4 merged. Blocks: a road leads the player into a
  ravine. Tag `#backlog-p6` (close it with 6.15). Three more roads to
  nowhere from 2026-10-28 (20.42.25, 20.43.12, tagged `#backlog`; 20.25.33,
  `#backlog-polish`) are listed under 6.22 to check for the same cause.
- [ ] **6.19 PORCHSTEP: no step up to raised doors** (2026-10-27,
  [17.46.21](phase6/playtest-2026-10-28/images/2026-10-27_17.46.21.jpg) at
  361,66,335, and [18.39.40](phase6/playtest-2026-10-28/images/2026-10-27_18.39.40.jpg):
  "generally missing stairs on porches now"). Not started; needs diagnosis
  on the current head. It looks like a regression from the 6.12 STEPTOP or
  SHOPLOW S2 work: the door sill is one full block up, with no stair. Blocks:
  it's the same one-block jump at the front door that 6.12 treated as a bug,
  and it's on most houses. Tag `#backlog-p6`. Seen again on 2026-10-28
  ([20.36.04](phase6/playtest-2026-10-28/images/2026-10-28_20.36.04.jpg) at
  3824,76,-1215: "Still missing stairs into the house"), and a stoop flight
  whose foot is a block above the road
  ([20.17.47](phase6/playtest-2026-10-28/images/2026-10-28_20.17.47.jpg) at
  3438,100,-1373, tagged `#backlog #walkability`) is filed with it as
  evidence (DS-4, likely the same cause).
- [ ] **6.20 DOORBANK: doors below an embankment street** (2026-10-28,
  [20.10.04](phase6/playtest-2026-10-28/images/2026-10-28_20.10.04.jpg) at
  3858,93,-1557: "Do they take a ladder to get up to the road?"). Not
  started. Blocks: the door can't be reached from the street. A minimal P6 fix
  is a check that no entrance faces a street wall, plus a fix that moves the
  door or leaves the lot empty (`empty:bank` exists). The real fix, streets
  near the ground, is 7.19 FALLSTREETS in P7. Tag `#backlog-p6`.
- [ ] **6.21 STAIRFOOT: an inner stair whose foot is at a drop**
  ([18.52.25](phase6/playtest-2026-10-28/images/2026-10-27_18.52.25.jpg) at
  1049,133,915). Not started. Two steps up to a door start at the edge of the
  stairwell hole, so the door can't be reached. Blocks: an unreachable door
  is broken, not a looks issue. It's probably small: the stair foot needs a
  standing cell, and `furnished-rooms-walkable` could check it. Tag
  `#backlog-p6`.
- [ ] **6.31 STAIRJOG: a high-rise entrance flight that jogs with no room to turn** (2026-10-31,
  [03.10.40](phase6/playtest-2026-10-31/2026-10-31_03.10.40.jpg),
  [03.12.04](phase6/playtest-2026-10-31/2026-10-31_03.12.04.jpg),
  [03.14.45](phase6/playtest-2026-10-31/2026-10-31_03.14.45.jpg) at
  4184,95,-1114). Not started. You tagged it `#backlog-p6`. Blocks: a main
  entrance whose flight is hard to walk. Cheap fix first: start the flight one
  cell further along so it runs straight. Added by the 2026-10-31 triage; the
  other 2026-10-31 notes went to the Backlog (unscheduled), the Polish backlog
  and hype.md, with one question in QUESTIONS.md (deep street cuts).
- [x] **The rest of the 2026-10-28 notes.** Triaged 2026-10-29 by your
  tags; no new release blocker. 22 `#backlog` notes went to the new
  Backlog (unscheduled) section of the [ROADMAP](../ROADMAP.md) (proposed,
  decision 7), pointing at 7.3, 7.11 and 7.19 FALLSTREETS where those cover
  them; 5 `#backlog-polish` notes to the Polish backlog; 13 `#hype` shots to
  [hype.md](phase6/hype.md); your rule proposal is DS-54 (proposed). The
  agents' answers are in place in the notes. Open for you: whether the two
  floating-foliage notes (20.16.21, 20.18.20, both break the accepted DS-20)
  should be P6 bugs, and DS-54 (accept, narrow or drop).

Everything else from the 2026-10-26 playtest is done (6.12-6.14, 6.16-6.18c)
or is already in the Polish backlog.

## Release work

- [ ] **Performance on first arrival** (REVIEW "REGIONPERF"). Needs John.
  After PARREADS, the worst cold chunk is about 37 s locally (it was
  70-120 s), with loads 5-46, so not on a quiet machine. The teleport hold
  gives up after 30 s, and `/locate` on a fresh world takes 5.1 s against a
  5 s ceiling. Blocks: a player teleporting or flying into an unvisited
  city can wait half a minute or more, or land before the city is there.
  For P6, decide the budget, re-measure quietly, and say it in the
  README. Making it faster is P7. Tag `#backlog-p6` (the decision only).
- [ ] **Batched heavy run on the release candidate**: `test-all.sh --full`
  plus the order test and `perf.sh`. In progress: HEAVY5 passed at 620ca7fd,
  except perf (above); later merges, such as DS36ROOFS, DS35DOORS and
  PARREADS, need a HEAVY6. Blocks: it's the evidence that the jar works.
  Tag `#backlog-p6`.
- [ ] **C2 review pack and playtest kit** ([plan](../plans/phase6.md),
  step C2). Not started. It holds per-seed recipes, settlements near spawn,
  fly-through frames and known issues. Blocks: the gate playtest needs it.
  Tag `#backlog-p6`.
- [ ] **The gate playtest itself**: three seeds, one hour each, on the
  release candidate. Needs John. You've played two seeds on older builds.
- [ ] **Release basics** (REVIEW "A5 release basics"). Needs John to read the
  LICENSE, the CHANGELOG draft, the [player README](../release/README-player.md)
  and the placeholder icon.
- [ ] **Phase 6 plan decisions 7 and 8** (REVIEW "Phase 6 plan: decisions
  for John"). Needs John: who publishes, where, under what name, and whether
  the source goes public; and the server-only install wording (the SRVONLY
  spike found that a client with only Fabric Loader, and not our mod, joins
  fine, so the README can say "clients don't need the mod").
- [ ] **C3 cut the release** (section 2, "Cutting a release"). Not started;
  it waits for everything above.

## Not release-blocking: move out of P6

| Item | Why it can wait | Tag (suggested; filed 2026-10-29 as) |
|---|---|---|
| Raised-road "maze", fill under junctions (28 Oct, 21.13.10, 20.07.31) | walkable, looks wrong: 7.19 FALLSTREETS | `#backlog-p7` (you: `#backlog`; Backlog (unscheduled), evidence on 7.19) |
| Bridge missing supports (20.04.35) | looks only: 7.3 Bridges | `#backlog-p7` (you: `#backlog`; Backlog (unscheduled) → 7.3) |
| Slabs on road rises, steps down into road cuts (17.47.56, 18.42.21) | walkable: 7.11 part 1 | `#backlog-p7` (you: `#backlog`; Backlog (unscheduled) → 7.11) |
| High-rise floors all alike (19.01.27) | looks: 7.13 | `#backlog-p7` (you: `#hype #ideas` on 19.02.02; hype.md, 7.13) |
| Grid of melted snow on a road (20.07.31) | cosmetic, cause unknown | `#backlog-polish` (you: `#backlog`; in the 20.07.31 Backlog line) |
| Open stairwell beside a door, balcony with no door (18.43.43, 18.45.46) | looks or safety, not broken | `#backlog-polish` (you: `#backlog` and `#hype`; Backlog (unscheduled), hype.md; balcony question to the architect) |
| Long road detour round a crossing (20.05.40) | a question, not a defect | `#backlog` (Backlog (unscheduled)) |
| BRIDGEENDS (held for your rails answer) | looks; merge when you answer | `#backlog-polish` |
| Unlit house rooms (REVIEW, DS-24) | monsters can't spawn in pieces anyway | `#backlog-polish` |
| Tunnels, lakes veto, workflow proposal, the other open REVIEW looks | none breaks play | as they are |

# 2. Proposed process

**P6 is the release increment, and its scope is fixed.** It's the checklist
above and nothing else. Phase 7 items already merged on the run branch stay
merged and ship. Partial ones, such as METROFILL R1/R2 and SHOPLOW S2-S4, ship
as they are: they are verified and do no harm. No new P7 work starts until
0.1.0 is cut, except a bug you tag `#backlog-p6`.

**P7 picks 3-5 main features from the backlog.** Candidates, with the case
for each:

- **7.19 FALLSTREETS**: it fixes the raised-road mazes and DOORBANK at the
  root. It came up in both playtests.
- **7.21 SQUARES**: framed squares with markets standing in them. Today a
  square gets only its landmark lot.
- **7.22 INN**: you've already decided on it, and it gives the road hamlets
  a purpose.
- **7.18 BASEMENT**: its plan is approved and it hasn't started.
- **7.20 METROFILL R3 / FRONTAGE**: dense cores, the second time you've
  asked for them.
- **High-rise looks (DS-34)**: frame and glass towers. There is no ROADMAP
  item yet.
- **7.11 part 1, smooth roads**: slabs on road rises, which you've asked for
  twice. Small enough for the release train instead.

Every other Phase 7 item moves to the unscheduled backlog, and P8 picks from
it the same way.

**A release train for bugs and polish runs all the time.** What qualifies:

- a real bug tagged with the current increment;
- polish lines of size S or M that add no new DS rule, ADR or planner-order
  change.

The cut is a patch release (0.1.1, 0.1.2, ...) after a batched heavy run
passes on the run branch with at least one change a player can see. At most
one cut a week, and you publish it. Almost any generator fix changes what new
chunks get, so each changelog entry says "Worldgen changed: yes/no", and a
"yes" warns that half-explored settlements can show a seam.

**New items that arrive mid-increment go to the backlog.** The coordinator
files them in the ROADMAP section their tag names and doesn't start them.
The only exceptions are a `#backlog-p6` (current increment) note that is a
real bug, and you asking for the work.

**How the tags map to the ROADMAP:**

| Tag | Goes to |
|---|---|
| `#backlog-p6` | the Phase 6 section, a checkbox in its bug list (real bugs only, otherwise the coordinator asks you) |
| `#backlog-p7` | the Phase 7 section, as an unstarted 7.x item |
| `#backlog-polish` | one line in "Polish backlog" |
| `#backlog` | a new "Backlog (unscheduled)" section (proposed; today the status table sends these to the Polish backlog or a phase item) |

After 0.1.0, `#backlog-p7` is the current increment, and so on.

**Cutting a release** means these steps:

1. Freeze: a passing batched heavy run (`scripts/snapshot-test.sh --name
   heavy`) on the candidate commit.
2. A verifier merges the run branch into `main` (`merge: to-main`). `main` is
   349+ commits behind today.
3. Bump the version in `gradle.properties` (`version=0.1.0`). `mod/build.gradle`
   appends `+<minecraft_version>`, and `fabric.mod.json` expands `${version}`,
   so that line is the only edit.
4. Write the `CHANGELOG.md` section: `0.1.0+26.3 (unreleased)` exists as a
   draft. Date it, cut it to what ships, and add the jar's SHA-256.
5. Build the jar from a clean checkout: `./gradlew :mod:build` gives
   `mod/build/libs/fractalbuildings-0.1.0+26.3.jar`, then a smoke test in a
   fresh client.
6. You tag `main` (`v0.1.0+26.3`; there are no tags yet) and upload. Agents
   prepare everything and stop there (plan step C3).

# 3. Decisions for you

1. Is the P6 checklist above the whole release scope, with nothing added?
2. Are PORCHSTEP, DOORBANK and STAIRFOOT release blockers, or do any of them
   go to P7?
3. For 6.15's long crossings: drop the approach, or allow a long deck at
   town tier and up until 7.3?
4. REGIONPERF: what cold-arrival wait is acceptable for 0.1.0 (30 s, 60 s),
   and does the `/locate` ceiling go up to 6 s?
5. Do your two playtest seeds count toward "three seeds", or do all three
   run on the release candidate?
6. Which 3-5 features make up P7?
7. Can the other Phase 7 items move to a new "Backlog (unscheduled)"
   section, where plain `#backlog` notes also go?
8. Patch releases: at most weekly after a passing heavy run, cut from the
   run branch, with you publishing. Yes?
9. Publishing (decision 7): which platform, what project name, and does the
   source go public?
