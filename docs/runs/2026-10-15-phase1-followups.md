# Run 2026-10-15 phase1-followups

## Scope
John, 2026-10-15: "Yes please add the spikes" (track-4 recommended Phase 2 spikes: flattening, village-exclusion mixin, procedural pool element into vanilla villages); "in parallel, update ARCHITECTURE.md to catch up with research"; question on the 8-chunk cutoff (can a city be many building starts, sizes from noise/plan?); add to Phase 1 research: generating structures outside worldgen time (Distant Horizons-style far pregeneration, plan cities on fully built chunks) as a backup option.

## Run
Branch `claude/run-phase1-followups`, worktree `.claude/worktrees/run-phase1-followups`, base d42fd9f (main). Cap 5 coding items (none expected; docs/research only).
Note: John has a stash on main (`stash@{0}`, ARCHITECTURE.md "Building IR" definition note).

## In flight
- F2 researcher a2a67685d58a2beb4, started 2026-10-15, claude/architecture-catchup, base d42fd9f, report .claude/reports/architecture-catchup.md
- F4 assistant a0b8d89ab37eebd26, started 2026-10-15, claude/research-postgen-1, base d42fd9f
- F5 assistant a40fb516fb95161b1 (resumed), claude/assist-roadmap-followups

## Items
| id | item | branch | base | status | result | notes |
|---|---|---|---|---|---|---|
| F1 | ROADMAP: add Phase 2 spikes + Phase 1 item 1.9 (track 7 out-of-worldgen) | claude/assist-roadmap-followups | d42fd9f | done | b2e5e93 | assistant a40fb516fb95161b1 (f483d2a); Worldgen ADR renumbered 2.7→2.10; PROCESS.md needs sync (F5) |
| F2 | ARCHITECTURE.md catch-up with research | claude/architecture-catchup | d42fd9f | running | | researcher |
| F3 | Answer: 8-chunk cutoff / city as many starts | (none) | | done | | assistant a9a1fd5cadb437fce, answered John 2026-10-15 |
| F4 | Track 7 research batch 1: out-of-worldgen generation | claude/research-postgen-1 | d42fd9f | running | | assistant |
| F5 | PROCESS.md: sync to S7–S9, ADR 2.10, track 7 | claude/assist-roadmap-followups | b2e5e93 | running | | resumed F1 assistant |

## Gates
- Phase 0: passed 2026-10-14 (John).
- Phase 1: not reached (1.7 skim with John, 1.8 synthesis not started).

## Rules and decisions
