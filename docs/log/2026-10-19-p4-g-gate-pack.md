# 4.10 G: Phase 4 gate pack

Built `docs/review/phase4/` from `claude/run-phase4` at fc06cb3 (all of 4.2-4.9 merged). Docs
and gallery presets only; no production code.

**Done**

- Gallery: `npm run gallery` for all 46 presets (41 existing + 5 new style-sheet presets
  `block-plains`, `temple-desert`, `temple-jungle`, `hall-taiga`, `hall-snowy`): 214 Playwright
  tests passed, every scene 0 violations. Committed a reviewable subset (coordinator: at most ~8 MB; the full set was ~35 MB): 18 PNGs in `gallery/<type>/` (front-left of the main preset per type, cutaways for house, workshop, temple, hall, block, and four extras) and 10 in `gallery/styles/` (house + one large type per new style); 7.6 MB in all. The full set (all presets x cameras) is regenerated with `npm run gallery` into `viewer/out/gallery/`. ASSUMPTIONS row added.
- Invariants: `check --types T` per type, 168,000 cases, 0 failing on all eleven invariants
  (`invariants.md`).
- Diversity: `diversity --type T --seeds 0..255` on one lot per type, medieval; houses also
  plains and plain; v0 for scale (`diversity.md`).
- Timing: `bench --types T` under the heavy lock on a quiet machine (no other agents; loadavg
  2.40-2.67), one pass over each type's whole matrix; NOTES Baselines line and the README table.
- README (what to look at per type, 15 questions), REVIEW: a "4.10 Phase 4 gate" entry listing
  every question with the entry that first asked it, plus new entries for house, shop, facades,
  interiors and the four new styles (the other types already had entries; not duplicated).
- ROADMAP: Phase 4 known issues from the run ledger and item notes copied into the Polish backlog
  (one line per item); "Added in Phase 4" testing-infrastructure note; 4.10 ticked.
- Fixes: plan T-block Deviation said `maxGrade` 12, the code has 16; correction line appended to
  the T-temple log (its orientation code was replaced by `TypeLimits.oriented` in round 3).

**Seen in the pictures** (README has the per-type detail): medieval blocks and halls read best;
the high-rise setbacks read; walls and towers read like houses (windows and timber framing on
every storey); shops look like narrow town houses with big ground-floor windows; plains, desert,
taiga and snowy colour mixes are speckled at gallery scale.

**Numbers worth keeping:** v1 house about 0.13 ms to generate (v0 0.011 ms); the slowest
shells are the city block (p95 32 ms) and high-rise (p95 16 ms); the slowest check is the hall
(p95 34 ms, 200x200 lots included). Width/depth entropy is 0 for every v1 type because the spec's
size is the lot envelope, so the "signature" count understates v1 variety.

**Not done / notes:** no per-type REVIEW entry was rewritten (append-only); the 4.6 tower entry
has no checkbox, so the gate entry asks for its answer.
