# Run 2026-10-15 phase1-followups

## Scope
2026-10-15 (later) John: note SilverBullet 2.11.0 use (CLAUDE.md pointers; study https://docs.silverbullet.md/ADR); start 1.8 synthesis. John pushed INDEX reviews (3 deep-dive, 2 drop) + SilverBullet flake (cbfd76e), merged into run branch.
John, 2026-10-15: "Yes please add the spikes" (track-4 recommended Phase 2 spikes: flattening, village-exclusion mixin, procedural pool element into vanilla villages); "in parallel, update ARCHITECTURE.md to catch up with research"; question on the 8-chunk cutoff (can a city be many building starts, sizes from noise/plan?); add to Phase 1 research: generating structures outside worldgen time (Distant Horizons-style far pregeneration, plan cities on fully built chunks) as a backup option.

## Run
Branch `claude/run-phase1-followups`, worktree `.claude/worktrees/run-phase1-followups`, base d42fd9f (main). Cap 5 coding items (none expected; docs/research only).
Note: John has a stash on main (`stash@{0}`, ARCHITECTURE.md "Building IR" definition note).

## In flight

## Items
| id | item | branch | base | status | result | notes |
|---|---|---|---|---|---|---|
| F1 | ROADMAP: add Phase 2 spikes + Phase 1 item 1.9 (track 7 out-of-worldgen) | claude/assist-roadmap-followups | d42fd9f | done | b2e5e93 | assistant a40fb516fb95161b1 (f483d2a); Worldgen ADR renumbered 2.7→2.10; PROCESS.md needs sync (F5) |
| F2 | ARCHITECTURE.md catch-up with research | claude/architecture-catchup | d42fd9f | done | 617b21d | researcher a2a67685d58a2beb4 (d9c85be); John's stash still applies (checked); 4 research contradictions listed in .claude/reports/architecture-catchup.md |
| F3 | Answer: 8-chunk cutoff / city as many starts | (none) | | done | | assistant a9a1fd5cadb437fce, answered John 2026-10-15 |
| F4 | Track 7 research batch 1: out-of-worldgen generation | claude/research-postgen-1 | d42fd9f | done | f147b95 | assistant a0b8d89ab37eebd26; 4 notes; verdict backup/add-on; batch 2 suggestions in its report (pregen rate on host, setStartForStructure after the fact, DH LOD push, Millénaire, Structurize, Moonrise/Folia) |
| F5 | PROCESS.md: sync to S7–S9, ADR 2.10, track 7 | claude/assist-roadmap-followups | b2e5e93 | done | 7e771e1 | resumed F1 assistant (6b31783); also Gate 0 'passed' in PROCESS.md |
| G1 | SilverBullet conventions: CLAUDE.md pointers + study SB ADR setup | claude/assist-silverbullet | cbfd76e | done | 4f8b3be | assistant a74429cbf6b9a0c34; also fixed flake syntax error from cbfd76e (SB_SPACE_IGNORE); flake.nix changed (John to review); templates got frontmatter |
| G2 | 1.8 Landscape synthesis | claude/synthesis | cbfd76e | done (pass 1) | 8cc072d | researcher a854521194e7df064 (b35cae8, 8420e5d); [deep-dive] markers to fold in; gate decisions B1–B4, I1–I6; ADR 0001 building, 0002 integration after John answers |
| G3 | Deep-dive: split grammars + CGA (pcg/) | claude/research-deepdive-grammars | cbfd76e | done | 568ffd7 | researcher a13ed8951d96d4b42 (2559659); one combined note; synthesis changes → pass 2 |
| G4 | Deep-dive: Lithostitched (overlays/) | claude/research-deepdive-lithostitched | cbfd76e | done | 8d38014 | researcher aff80fde01a60ac10 (35686bf, baeffd3); changes for ARCHITECTURE flattening, surface-level note, synthesis I4 → fold into synthesis pass 2 |
| G5 | Synthesis pass 2: fold in G3, G4; ARCHITECTURE flattening + surface-level note fixes | claude/synthesis | 568ffd7 | done | cdbc838 | resumed researcher (010b17c); ~2,200 words |

## Gates
- Phase 0: passed 2026-10-14 (John).
- Phase 1: READY for John 2026-10-15. Package: docs/research/synthesis.md (decisions B1–B4, I1–I6 + spike-scope tweaks, each with recommended answer); background: ARCHITECTURE.md, deep-dives pcg/grammars-deep-dive.md, overlays/lithostitched-deep-dive.md, postgen/overview.md, docs/PROCESS.md. After decision: researcher drafts ADR 0001 (building) + 0002 (integration). Dependent: all Phase 2 spikes awaiting gate.


## Rules and decisions
- 2026-10-15 coordinator: started the 3 deep-dives John flagged in parallel with the synthesis; synthesis to be revised with deep-dive results afterwards.
- 2026-10-15 coordinator: cbfd76e (John, on main) broke flake.nix syntax; fixed in run branch 4f8b3be. Doc conventions: frontmatter only on research notes/ADRs per templates; # headings; relative links, no [[ ]] in body, no #anchors.
- 2026-10-15 John: merge into main even when main isn't checked out (his main checkout sits on hide-from-git-push so he can push to it). Method: under merge.lock, if main is an ancestor of the run branch, make a --no-ff merge commit with commit-tree and update-ref main <new> <old>; otherwise merge main into the run branch first. Never touch his checked-out branch.
