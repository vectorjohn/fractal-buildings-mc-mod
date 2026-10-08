Where to look next
==================

The agent team did a ton of work and I'm leaving this document here as a pointer of what was done and where to start looking.

Phase 0 2026-10-08 01:00:00
===========================

Phase 0 is finished and merged into main as eb50c75, and all six research tracks have covered their starting lists: 55 notes are on main. No agents are still running.

Phase 0: done, gate waiting on you ("builds green, and the viewer renders the box")
- The build, the viewer skeleton, the templates, the smoke path (CLI → JSON → viewer → screenshot), the mod's server and client game tests, and scripts/test-all.sh are all verified and merged.
- test-all.sh passes every layer, including the client game test on lavapipe.
- No EULA is accepted anywhere: the eula=true flag turned out to be unnecessary, so it was removed.
- To review the gate, look at viewer/out/smoke.png and mod/build/screenshots/hello-hut.png. If you'd rather see a screenshot first, Phase 0's archived text is in docs/archive/roadmap-phase-0.md.

Phase 1 research: tracks 1–6 covered
- Start at docs/research/INDEX.md; every note has a one-line verdict and status: new. That's also your early skim (1.7) whenever you have time.
- I didn't start the landscape synthesis (1.8), since it was outside what you asked. It's the natural next step before the Phase 1 gate.
- Where integration points:
  - Structure-based is the only approach with proper hooks in 26.3. A custom chunk generator or a biome overlay would fight the engine.
  - In-game probes confirmed two hard limits: blocks more than 8 chunks from a structure's start are silently dropped, and structure planning runs on a single serial lane, so city plans must be lazy and cached.
  - Biome lookups can change with query order at exact climate ties; a fixed "priming" lookup fixes it.
  - Flattening terrain under cities means wrapping three terrain slots, not one.
  - Keeping vanilla villages out needs a small mixin of our own.
- Where building generation points:
  - A grammar for the building shell (split grammar or CGA style).
  - Grid-based floor plans (constrained growth) followed by a repair pass that guarantees every room is reachable.
  - Roofs as each cell's distance to the footprint edge.
  - WFC only for small, bounded details.
  - Cities planned once per region cell and cached, with The Lost Cities-style placement that crosses biome borders.
- ARCHITECTURE.md's worldgen section is corrected to match what the source and probes showed.

Decisions for you
- The Phase 0 gate.
- Is an optional Lithostitched dependency acceptable as a compatibility path for terrain flattening and keeping structures out of cities?
- May a block colour table computed from Mojang's textures be committed? For now only the script that generates it is.
- Should façades be capped at 2 layers of depth? WesterosCraft's guides warn that heavier layering looks over-detailed.

Left in place
- claude/run-phase0-research and its worktree: fully merged into main.
- claude/research-worldgen-2 and its worktree: also merged, but kept for its untracked probe code under mod/src/*/java/probe/, for Phase 2 to re-implement. Delete it once Phase 2 has what it needs.

Files for you to review
- The Gradle build files and the wrapper.
- flake.nix: poppler-utils, plus Playwright's browsers and their environment variables.
- viewer/package.json and viewer/tsconfig.json.
- .claude/agents/*.md and the new scripts/squash-merge.sh. Verifiers now merge in one locked call, after two of them let the lock lapse mid-merge. Coders now must write their report files; a generic harness instruction had been stopping them.

The only new docs/REVIEW.md entry was the mod licence, now settled as MIT with you as author.

Next steps: approve the Phase 0 gate, skim the index, then have the synthesis written. Track 4 suggests Phase 2 spikes on terrain flattening, putting our houses into vanilla villages, and the village-exclusion mixin. Start a fresh coordinator session for that; the run log is complete.

