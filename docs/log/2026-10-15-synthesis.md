# 1.8 Landscape synthesis (2026-10-15)

**Who:** researcher, branch `claude/synthesis` (base `claude/run-phase1-followups` at cbfd76e).

**What was done:** wrote `docs/research/synthesis.md`, about two pages. It
compares the building approaches (grammar, WFC, graph + templates) and the
integration approaches (structures, per-chunk feature, biome overlay, custom
generator, post-generation), recommends a direction for each, maps the
follow-up checks onto S1–S9, and lists the gate decisions (B1–B4, I1–I6),
each with a recommended answer. ROADMAP 1.8 is ticked.

**Inputs:**

- INDEX statuses as John set them, including his comments on the
  moogs-structures and recurrent-complex notes.
- ARCHITECTURE.md as caught up on 2026-10-15.
- The catch-up report's list of contradictions
  (`.claude/reports/architecture-catchup.md`). Each one is resolved or framed
  in the "Where the notes disagreed" section.

**Notes read:**

- In full: split-grammars, cga-shape-grammar, wfc-model-synthesis,
  townscaper, markovjunior, constrained-growth, recurrent-complex,
  towns-and-towers, the-lost-cities, repurposed-structures,
  structure-pipeline, extension-points, jigsaw, surface-level-and-flattening,
  custom-generator-and-pool-element, keeping-structures-out, lithostitched,
  postgen/overview.
- In part: the approach section of post-generation-placement; the approach
  and borrow sections of tectonic; the borrow section of
  lost-cities-city-placement.
- Verdict lines only: every other note.

**Verification:** every relative link in the synthesis resolves (checked with
a shell loop). Nothing was run. All numbers are copied from the cited notes
or NOTES.md.

**Surprises and new points:** these came from reasoning over the notes, not
from new evidence, and are marked *inferred* in the doc:

- District starts on a fixed sub-grid, not starts per building, because the
  placement test must be cheap from position.
- Saving pieces as references makes the world sensitive to config or version
  changes partway through, which can leave seams (an S6 question).
- A flattening mask driven only by the region field removes the
  plan/mask/`getBaseHeight` cycle.
- S1 and S2 are mostly answered by the track 4 probes.
