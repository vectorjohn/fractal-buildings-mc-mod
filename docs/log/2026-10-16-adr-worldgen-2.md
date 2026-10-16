# ADR 0003 supersedes 0002; gate answers recorded (2026-10-16)

**Who:** researcher, branch `claude/adr-worldgen-2` off `claude/run-phase2`
(f557081, run branch merged in again at 76ff24c).

- **ADR 0003** (`docs/adr/0003-worldgen.md`) rewritten to stand alone and
  set to `accepted` (John, 2026-10-16: "I agree with the proposed
  decisions"). Checked 0002 and 0001 line by line and folded in what 0003
  didn't restate:
  - W9 (0002 I4): no required dependencies; publish
    `fractalbuildings:settlement` as a 2D density-function type, no
    Lithostitched modifiers or data. Option 6 and "revisit 6b" record the
    Lithostitched alternative.
  - W10 (0002 I5 and I1): the feature as contingency, track 7 as a backup
    promoted only if both base paths fail, overlay and custom generator
    dropped (options 1d, 1e).
  - W5: I1's road and wall segments in a district's box, I2's "no terraces",
    steep-lot rejection kept for small fill buildings only.
  - Context: track 4 limits and John's "effectively unbounded" target.
  - Practical limits: a building footprint is under 224 blocks (0001 had
    ~200 in a ~248-block box).
- **Gate answers recorded:** R2 dropped or unsuitable big cities release
  their cells (with a proposed one-round, bounded-radius rule, marked
  untested, for 5.8); W5 the S5 way kept and the cut faces accepted; W6 S7
  cut/fill acceptable; R1 note on John's water remark for Phase 5,
  undecided. Every S6 figure is tagged `[S6]`, pending the quiet-machine
  re-measure.
- **ADR 0002:** `status: superseded`, `supersededBy` 0003, one banner line.
  0003 lists 0002 under `related` (the template has no `supersedes` key).
- **S4 note:** S defined at first use.
- **ARCHITECTURE:** best-of-8 decisions, per-region priming, 14 × 14
  districts, 2.5 s at 2048² (two-level plan), the coarse-screen veto used
  only to cull, join cells with release; intro and size targets point to
  0003.
- **REVIEW:** S5 ticked; S4, S5 and S7 moved to Done; John's text unchanged.
- **Not run:** nothing (docs only; the heavy lock was busy with S6's
  re-measure).

## S6 quiet re-measure folded in

- Refreshed the 16 `[S6]` figures in 0003 from `s6.md` at 7b098fa
  (`claude/spike-s6`) and removed the tags; the Context now says once that
  S6 figures are quiet-machine, stand-in generator. Changed: 8192²
  monolithic 29.5 s; suitability 11–12 ms coarse, about 130 ms exact;
  `/locate` 0.1–0.24 s against 2.5–25 s; walk lead 7.5 s; the 1 MB slot
  spills to `.mcc` (not a hard limit). New in P2: at most 4 prefetch
  workers (8 cost 22 % and 16 cost 44 % of chunk throughput on land), a
  refinement, not a new decision. ARCHITECTURE's 34 s became 29.5 s.
