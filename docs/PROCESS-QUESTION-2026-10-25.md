# Process overview: what exists (answer to John, 2026-10-25)

John asked: is there a short, high-level description of the development process
for a human, showing where we are and where to suggest things? And is the same
process written down in several diverging places? Answer from `@assistant`.

# Short answer

Yes: [PROCESS.md](PROCESS.md) is close to what you want, but it's stale. It
was written on 2026-10-14 as a one-page map: a mermaid diagram of phases,
tracks and gates, a "Where John gives input" table, and a second diagram of how
one item gets done. It hasn't been touched since 2026-10-15 (Phase 1), so:

- the green "you are here" node still sits on 1.7 (we're in Phase 6);
- the item loop has none of the 2026-10-25 changes: no `architect` /
  `interior-designer`, no violations / should-fix / suggestions / proposed
  rules, no `merge: hold`, quick/full levels, merge trains or batched heavy
  runs;
- the input table doesn't mention [design/STANDARDS.md](design/STANDARDS.md),
  inline `@John` comments in this worktree, or the ledger's "Proposed rules";
- about half of it (the phases 0–5 diagram and its dependency notes) is
  history now, and it's the verbose part.

So I'd adapt and trim PROCESS.md rather than start a new page. README and
ROADMAP already link to it as "the one-page diagram".

# Where the process is described today

Each place below describes part of the process, and most of them restate it:

| Place | What it covers | State |
|---|---|---|
| [PROCESS.md](PROCESS.md) | phases, gates, your inputs, item loop (diagrams) | stale since Phase 1 |
| README, "Long runs with agents" | agent table, how to start the coordinator, branch diagram, where things are written | says "six agents" (there are eight); no design review |
| CLAUDE.md, "Where things live" and "Working conventions" | the doc table; one paragraph per agent role | current; written for agents |
| [design/README.md](design/README.md) | the two reviewers, the finding tiers, verdicts | current; the best short text on design review |
| [design/STANDARDS.md](design/STANDARDS.md) intro | violations and suggestions | **already out of date**: no should-fix tier |
| [REVIEW.md](REVIEW.md) header | how you answer review items | current |
| ROADMAP intro and "Testing strategy" | principles, gates, test layers | current |
| `.claude/agents/coordinator.md` | the full process: briefs, levels, routing verdicts, trains, heavy batches, gates | current; the real source, about 480 lines |
| `.claude/agents/verifier.md`, `coder.md`, `architect.md`, `interior-designer.md` | each role's side of the same loop | current |
| [john-TODO.md](john-TODO.md) | a "where to look" note from Phase 0 | stale since 2026-10-08 |

The divergence you're worried about has already happened twice: the
STANDARDS.md intro and the README agent table both missed changes that the
agent files got.

# Recommendation

1. **One human page: PROCESS.md, rewritten to about one screen.** It's the
   only place that explains the process to you. It describes things at the
   level of who does what and where you come in, and doesn't restate
   mechanics (briefs, flags, lock files).
2. **Everything else links to it instead of restating it.**
   - The README "Long runs with agents" section shrinks to how to start or
     resume the coordinator, plus a link.
   - The STANDARDS.md intro shrinks to "how reviewers use these rules: see
     design/README.md", which removes the stale tier list.
   - design/README.md stays as the detail page for design review.
   - The agent files remain the executable detail. They're instructions for
     agents, so some overlap with PROCESS.md is fine. PROCESS.md says
     "mechanics: coordinator.md" and never repeats them.
3. **Keep "where we are now" out of the page, so it can't go stale.** Point
   to the run ledger's status line and the ROADMAP status table instead of
   a "you are here" node.
4. **Archive what's history.** The phases 0–5 diagram moves to
   `docs/archive/`, and john-TODO.md is archived or deleted (your call; it's
   your file).
5. **One upkeep rule** for agents in CLAUDE.md: a change to the process
   updates PROCESS.md in the same commit.

# Sketch of the new PROCESS.md (headings, one line each)

- **The shape**: research, then spikes, then architecture; phases end in
  gates you approve; work runs in parallel. Three sentences.
- **Who does what**: one line per agent (coordinator, coder, lead-coder,
  assistant, researcher, verifier, architect, interior-designer).
- **How one item gets done**: a small diagram. Brief, then coder, then
  verifier with the reviewers alongside. Reviewer findings are violations
  (block), should-fix (one round, then the Polish backlog), suggestions (the
  Polish backlog) or proposed rules (sent to you). Then merge into the run
  branch, heavy tests in batches, and a phase merge into main at the gate.
- **Where we are**: links to the current ledger in `docs/runs/` and the
  ROADMAP status table.
- **Where you come in**: a table. Gates (the review package);
  [REVIEW.md](REVIEW.md) `Result:` lines (looks and feel);
  [STANDARDS.md](design/STANDARDS.md) (add or edit rules, accept proposed
  ones); inline `@John` comments in this SilverBullet worktree (questions and
  feedback on any doc); [ASSUMPTIONS.md](ASSUMPTIONS.md) (override agent
  choices); research/INDEX statuses; the ROADMAP Polish backlog (reprioritise);
  telling the coordinator directly (scope, pause, resume).
- **Where things are written**: one line each for the ledger, log, NOTES,
  ADRs, reviews in `.claude/reports/` and galleries. Link CLAUDE.md for the
  full table.

I haven't written the new page. If you want it (or a different split), reply
inline here and an agent will make the change on its own branch.
