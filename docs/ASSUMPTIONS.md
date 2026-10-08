# Assumptions

Cheap-to-change decisions made without John, so work could keep going. John
reads these and overrides any he disagrees with. Architecture-shaping decisions
are ADRs (`docs/adr/`), not rows here.

**One row per line, appended at the end** (union-merged across parallel
branches). When John overrides one, add a new row saying so rather than
editing the old one.

| Date | Item | Assumption | Why | Cheap to change? |
|---|---|---|---|---|
| 2026-10-07 | setup | The planner core is Java (not Kotlin or TypeScript) | One codebase with the mod; well represented in training data; roadmap open question | until Phase 3 starts |
| 2026-10-07 | setup | Research tracks 1, 2, 3, 5, 6 run as `assistant`, track 4 as `lead-coder` | John asked for coder/assistant to do most work and lead-coder only for hard problems; source reading is the hard one | yes (coordinator.md) |
| 2026-10-07 | setup | John's call: new `researcher` agent (Opus, high effort) takes track 4, deep-dives, the landscape synthesis and ADR drafts; `assistant` keeps tracks 1, 2, 3, 5, 6 | Effort barely matters for web gathering, but source reading and project-steering judgment repay depth; also a high-effort fallback for model overrides | yes (coordinator.md) |
