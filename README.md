# fractal-buildings

A Fabric mod for Minecraft (Java edition) that generates varied, believable
buildings and cities from rules rather than stamped templates. Work in progress:
see [docs/ROADMAP.md](docs/ROADMAP.md) for the plan and status,
[docs/PROCESS.md](docs/PROCESS.md) for a one-page diagram of the phases,
tracks, gates and where you give input, and
[CLAUDE.md](CLAUDE.md) for how the repo is organised.

Development happens inside the Nix dev shell: `nix develop` (or
`nix develop -c <cmd>`).

## Long runs with agents

`.claude/agents/` defines six agents for working through the roadmap without
filling one context window, with several items in flight at once:

| Agent | Model | Job |
|---|---|---|
| **coordinator** | Opus 5.5, high effort | Reads the roadmap, starts every ready item in parallel (soft cap ~5 coding items), has each result verified, keeps the run ledger, handles gates, merges docs and finished phases into `main` |
| **coder** | Sonnet 5.5, medium | **Most of the code.** One item, plan step or spike per run, in its own branch and worktree; tests it, looks at its output, writes a log file, reports |
| **assistant** | Opus 5.5, medium | **Most of the non-code work**: breadth research tracks and one-pagers, test tooling, galleries, small throwaway proofs of concept, doc edits, and your one-off requests during a run |
| **researcher** | Opus 5.5, **high** effort | Research where depth matters: decompiled-source reading (track 4), deep-dives, the landscape synthesis, ADR drafts. Also the high-effort fallback (the coordinator can start it on another model) |
| **lead-coder** | Opus 5.5, medium | Hard coding problems only: plans for large or unclear items, diagnosis after a failed verification, tough merge conflicts |
| **verifier** | Opus 5.5, medium | Reads the report, diff and tests, runs the suite, compares galleries before and after, spot-checks a mutation, and squash-merges (the stamp of approval). Writes no code |

### Starting the coordinator

Subagents can't start subagents, so the coordinator has to be the session you
launch, from the main checkout:

```sh
claude --agent coordinator                                  # interactive
claude --agent coordinator "Do Phase 0 and start Phase 1 research in docs/ROADMAP.md"
```

To continue an interrupted run, start the coordinator again and point it at the
ledger: "continue the run in `docs/runs/<file>.md`". Restarting at each phase
boundary keeps the coordinator's context small.

### Workflow

```
main  ──────────────●──────────●─────────────────────●──   (phase merges; docs-only merges any time)
                   /          /                     /
claude/run-<slug> ●──●──●──●─●──●──●──●──●──●──●──●       coordinator's branch + worktree
                    \  \  \      \  \  \
claude/<item>        ●  ●  ●      ●  ●  ●                  several items at once, one worktree each
```

- The **ledger** `docs/runs/<date>-<slug>.md` records the scope, what's in
  flight (agent ids), each item's status and commits, gates and your
  decisions. It's how a run survives interruption.
- Each item's coder writes `docs/log/<date>-<item>.md`; durable facts go to
  `docs/NOTES.md`; assumptions made without you to `docs/ASSUMPTIONS.md`;
  things only you can judge to `docs/REVIEW.md`; decisions to `docs/adr/`.
- Research notes land in `docs/research/<track>/` with a line in
  `docs/research/INDEX.md`. Mark a note `deep-dive` or `drop` there (commit on
  `main`, or tell the coordinator) to steer its track.
- Spike code is never merged; only its findings note is.
- Shared resources: `.claude/heavy.lock` serialises Minecraft client tests and
  timing runs; `.claude/merge.lock` serialises merges. Everything else runs in
  parallel.

To watch a run, read the ledger in the coordinator's worktree
(`.claude/worktrees/run-<slug>/docs/runs/`) or `git log --graph --all`.
`scripts/agent-usage.sh <agent-id>` summarises an agent's time and tokens.

## For other mods: `Settlements`

`dev.fractalbuildings.mod.api.Settlements.at(ServerLevel, BlockPos)` answers what the planner says is at a block column: the settlement id and tier, the district tile, the lot, the building type and style, and the building's furnishing hooks (bed, chest, workstation, ...) in world coordinates. It is read-only and has no behaviour of its own (a villager-AI mod can build on it). Beds, job sites and chests are real blocks; villagers are ordinary vanilla villagers placed at beds in hamlets and villages (at most 10 per district tile).
