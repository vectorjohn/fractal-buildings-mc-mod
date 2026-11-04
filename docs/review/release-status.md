# Release status: 0.1.0 checklist audit

Audited 2026-11-02 by the assistant, for John, from the ROADMAP, the work logs,
git history on `claude/run-phase6` (head ce129a26) and the run ledger. It checks
every item of [release-plan.md](release-plan.md) section 1; the statuses there
were corrected in place. Item codes are in [GLOSSARY.md](../GLOSSARY.md).

# Summary

**What still blocks the 0.1.0 release candidate**

1. **6.15 ROADJOIN** (roads between settlements meet the streets): steps 1-4
   merged; three follow-ups remain. LONGDECK (decks over 20 blocks only at towns
   and up) has a commit on its branch, not yet verified. DECKDIAG (why decks run
   along water, plus the dead-end case (c) of 6.22) is being diagnosed. The
   step 5 in-game check comes after both.
   - @John 2026-11-04: are these followed up on? @coordinator
     - @coordinator 2026-11-04: yes, all done. LONGDECK merged e8c5740e (with DECKDIAG steps 1-2), DECKDIAG step 3 (shore-aware approach reroute) f949e57a, and the step 5 in-game check (an opt-in client test over your spots, max step 1) 92bd6191; 6.15 is ticked and the slow-test batches HEAVY14 and HEAVY15 passed with it. Left as known issues: the second metropolis exit (3200,1792) has no road because its link was dropped, and your shots 21.35.56 and 21.36.39 still need coordinates (REVIEW item). ROADJOIN no longer blocks the release.
2. **P6 work in flight that the checklist doesn't list** (started from your
   2026-11-01 and 11-02 answers): TPABORT (holds abort after 60 s instead of
   releasing into unready chunks), 6.34 METROWARM (the region warmer looks
   further ahead on fast flights) and QUAYFAST 4b (the cold district plan fix,
   being verified). These are coding and verification, not decisions.
3. **After those merge:** a batched heavy run on the candidate (the last one,
   HEAVY13, passed at 1ea75a7d before QUAYFAST, TPHOLD60 and MAINSTEP merged),
   the gate kit regenerated with fly-through frames, your third-seed playtest,
   then C3 (cutting the release).
4. **Small open release tasks:** a README sentence on the cold-arrival wait
   (about 35 s in a big city, the hold message, abort after 60 s), and the
   server-only wording once you answer.

**Waiting only on you** ([QUESTIONS.md](../QUESTIONS.md), "Release (0.1.0)")

- Release basics: read LICENSE, CHANGELOG, player README and icon (line
  "Release basics", added today; REVIEW "A5 release basics").
- Server-only wording, plan decision 8 (line "Server-only wording", added today).
- Release scope, Q1 (line "Release scope").
- Polish triage: whether its proposed P6 items join the release (line "Polish
  triage"). Its item 5, the perf ceilings, is already settled by your
  2026-11-02 rule that ceilings are never raised.
- History cleanup before `main`: your details (release-plan "Publishing"; no
  QUESTIONS line, since you said you'd come back to it).
- Not blocking 0.1.0: P7 features (Q6) and the Backlog section (Q7).

**Looks forgotten** (waiting on nothing, no one assigned)

- DS-19 (no causeways or long bridges) still says 20 blocks at most. Your Q3
  answer ("allow the deck") needs an interim exception written into it, and
  nobody has been asked to draft it.
- The README sentence on the cold-arrival wait has no owner.
- REVIEW "REGIONPERF" has an empty result line, though you decided it on
  2026-11-02. REVIEW "Phase 6 gate" still asks whether your two seeds count,
  which you answered in Q5.
- Two ROADMAP parent boxes, "Playtest 2026-10-28 bug list" and "Playtest
  2026-10-31 bug list", have every child ticked but are still unticked.

# Items

| Item | True status | What remains | Blocker or next step |
|---|---|---|---|
| 6.15 ROADJOIN (roads between settlements meet the streets) | Partly done: steps 1-4 merged 7ec724a9 (2026-10-27); follow-ups in progress since 2026-11-02 | LONGDECK merge, DECKDIAG diagnosis and fix (with 6.22 case (c)), step 5 in-game check; DS-19 interim exception text | LONGDECK coder, DECKDIAG lead-coder; [[docs/plans/roadjoin|plan]] |
| 6.22 DEADPORT (road to nowhere over a big bridge) | Done: a59e65c7 (2026-10-29), steps (a) and (b) | Case (c), the counted dead ends, moved to DECKDIAG | none for this box |
| Three more roads to nowhere (20.42.25, 20.43.12, 20.25.33) | Done in the planner by 6.25 MAINSTUB (main roads that end at a gate), 9a6c5d45 | Not checked in game | your next playtest |
| 6.19 PORCHSTEP (no step up to raised doors) | Done: 46a0c194 (2026-10-29); 6.32 DOORAIR 22d78854 and 6.33 MAINSTEP 38d9773e followed | none | not a blocker (Q2) |
| 6.20 DOORBANK (doors below an embankment street) | Done: 117aeb45 (2026-10-29), the minimal fix | the root fix is 7.19 FALLSTREETS (P7) | not a blocker (Q2) |
| 6.21 STAIRFOOT (inner stair foot at a drop) | Done: 2c06e7af (2026-10-29) | none | not a blocker (Q2) |
| 6.31 STAIRJOG (entrance flight that jogs) | Done: 45a96b8c (2026-10-31) | your in-game look at 4184,95,-1114 | optional |
| Rest of the 2026-10-28 notes | Done: triaged 2026-10-29; both open questions decided 2026-10-31 (foliage notes to backlog, DS-54 accepted narrowed) | none | none |
| Performance on first arrival (REGIONPERF) | Decided 2026-11-02: about 35 s cold arrival is fine for 0.1.0; holds abort after 60 s; ceilings never raised (PERFKNOWN 766bf8fe) | TPABORT merge; README sentence; REVIEW result line | TPABORT coder; README unassigned |
| Batched heavy run on the release candidate | Waiting for the candidate: HEAVY13 passed at 1ea75a7d (2026-11-01) | one batch after the in-flight P6 work merges; none running now | in-flight P6 items |
| C2 review pack and playtest kit | Mostly done: 76707ba8, 50873017 (2026-10-29); seeds 5bfb8a16 | regenerate with `scripts/playtest-kit.sh --fly` on the candidate | the candidate; [[docs/review/phase6/gate-kit/README|gate kit]] |
| Gate playtest (three seeds) | Waiting for the candidate, then you; your two played seeds count (Q5) | the third, fresh seed on the candidate | the candidate |
| Release basics (A5) | Waiting on you since 2026-10-22: files merged a94cda2f | your read; REVIEW result | [[docs/QUESTIONS|QUESTIONS]] "Release basics" |
| Plan decisions 7 and 8 | 7 decided (Q9, 2026-11-01); 8 waiting on you | server-only wording; README still says "untested" | [[docs/QUESTIONS|QUESTIONS]] "Server-only wording" |
| Publishing | Decided (Q9): Modrinth and CurseForge, "Fractal Buildings", source public | history cleanup details | you, before `main` |
| C3 cut the release | Not started | everything above | all rows above |
| TPABORT (holds abort, not release, after 60 s) | In progress: 3 commits on its branch | verify and merge | not on the checklist; no ROADMAP box |
| 6.34 METROWARM (warmer looks ahead on fast flights) | In progress: 2 commits on its branch (adds the ROADMAP box) | verify and merge | not on the checklist |
| QUAYFAST 4b (cold district plan p95 fix) | In progress: verifying | merge; remove the knownOver mark if under 3 s | not on the checklist; no ROADMAP box |

Held branches waiting on your answers (not release blockers, QUESTIONS lines
exist): DSROADS (lane joins) and BRIDGEENDS (rails).
