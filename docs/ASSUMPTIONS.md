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
| 2026-10-08 | 0.1a | Mod id `fractalbuildings`; Java packages `dev.fractalbuildings.{core,cli,mod}`; Gradle group `dev.fractalbuildings` | Short, generic, no real domain owned | yes (rename + sed) |
| 2026-10-08 | 0.1a | planner-core is bundled in the mod jar via Loom `include project(':planner-core')` (jar-in-jar), not shading | Simplest; one line; core stays a separate jar so Minecraft never sees it on a compile classpath of core | yes |
| 2026-10-08 | 0.1a | Loom 1.18.3 (release) instead of the template's `1.18-SNAPSHOT`; fabric.mod.json licence MIT as placeholder | Reproducible builds; licence undecided | yes |
| 2026-10-08 | 0.4 | Smoke IR JSON is written by a hand-rolled writer in `cli` (`IrJson`), IR records (`SmokeIr`) live in planner-core; no Jackson | Fixed key order and formatting for free, no dependency, IR is tiny and provisional; revisit when the real IR (Phase 3) is large | yes |
| 2026-10-08 | 0.4 | Viewer pins `@playwright/test` to the nixpkgs playwright-driver version (1.63.0) and the e2e screenshot test lives in `viewer/e2e`, run by `npm run screenshot` | Browsers come from Nix, so versions must match | yes |
| 2026-10-08 | Phase 1 | Agents don't read Stardust Labs code or data (Terralith etc.); its licence forbids using it to configure, test, debug or augment AI systems. Notes come from public pages only | Licence text; John to decide | yes |
| 2026-10-08 | Phase 1 | Code with no licence, or MIT with extra conditions (e.g. Frightful Hobgoblin's), is treated as ideas-only: study the approach, never copy code | Conservative default; John to decide | yes |
| 2026-10-08 | Phase 1 | A block colour/lightness table derived from Mojang textures is computed at build/dev time and not committed (scripts and our palette orderings may be committed) | Mojang usage guidelines claim derivatives of assets; John to decide | yes |
| 2026-10-08 | Phase 1 | John's decision (overrides the Stardust Labs row above): agents may read Stardust Labs (Terralith etc.) and similarly restricted code/data to understand the general approach (integration points, algorithms); never copy code or data from them | John: gray area, overview only | yes |
| 2026-10-08 | Phase 1 | John confirmed: code with no licence or MIT with extra conditions is ideas-only | John | yes |
| 2026-10-08 | 0.1a | John confirmed: the mod is MIT, author John Reeves | John | yes |
| 2026-10-08 | 0.5 | Game tests live in a separate `gametest` source set with its own mod id `fractalbuildings-test` (Loom `configureTests`), not in `main` | Keeps test code out of the shipped jar; Loom's default layout | yes |
| 2026-10-08 | 0.5 r2 | Correction: no `eula = true` in `configureTests`; game tests run without it | EULA is John's to accept; verified both game test tasks pass without it | yes |
| 2026-10-16 | S3 | Spike grid: district 12x12 chunks, city cell 2x2 districts, 50 % of cells are cities, structure step `top_layer_modification`. Placeholders for the real region layer, not decisions | Fits the 8-chunk start reach with margin; the real sizes come from S4/S6 and Phase 5 | yes |
| 2026-10-16 | S6 | Stand-in generator cost modelled as thread CPU spin (1 ms house, 50/200 ms mid/tall tower), IR as greedy boxes; real generators are 5-20x dearer | Findings scale linearly; re-run with the real generator in Phase 3/4 |
