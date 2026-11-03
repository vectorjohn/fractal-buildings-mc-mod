# History on main: a proposal

Written 2026-11-02 for John, before publishing. Mark it up in place (`-- @John`). The dry run (the test) is in [history-workflow-dryrun.md](history-workflow-dryrun.md). Item codes are looked up in [GLOSSARY.md](../GLOSSARY.md).

# The problem

Phase 6 alone is 647 commits on the run branch's main line (`main..claude/run-phase6`, first parent only; 994 with everything the merges bring in). Sorted by the paths each one changes:

- **353 bookkeeping only**: run ledger, ROADMAP status and ticks, REVIEW, QUESTIONS, NOTES, ASSUMPTIONS, GLOSSARY, INDEX lines, work logs, review pictures. 187 of them start `Ledger:`.
- **84 docs only**: plans, research notes, design standards, CLAUDE.md and agent rules, your docs-feedback batches.
- **210 code or tooling**: mostly one verifier squash per item (`6.38 DOORREACH: ...`), plus tooling merges from assistant branches.

193 of the 647 are merges (52 of them your `john/docs-feedback` syncs). Until now Phase 6 would reach `main` as `git merge --no-ff claude/run-phase6`, which brings all 994 along. `main` already has 575 commits of the same mix from Phases 0-5.

# Recommendation

Keep the run branches exactly as they are: every ledger line, sync and test result is still committed there, and agents keep working the same way. But **`main` is built, not merged**. A new script, `scripts/promote.sh`, writes `main`'s next commits from the run branch:

1. It walks the run branch's main line since the last promotion and sorts each commit as code, docs or bookkeeping, by the paths it changes (not by its message).
2. Every code commit (an item squash, a tooling merge) becomes one commit on `main`. Adjacent docs commits (your batches, plans, research notes) are combined into one docs commit. Bookkeeping commits are folded into the commit before them: the `Ledger: DOORREACH merged` line, the ROADMAP tick and the work log end up inside `feat(DOORREACH)`.
3. Messages become semantic: `type(CODE): summary`, for example `perf(PARREADS): parallel terrain reads for cold settlement planning` (PARREADS: parallel terrain reads). Types: `feat`, `fix`, `perf`, `test`, `docs`, `build`, `chore` (tooling), `revert`. The body keeps the verifier's squash message (what changed, known issues, how it was verified) and a `Run-commits:` line pointing back to the run branch.
4. Each new commit's content is a snapshot of the run branch at the end of its group, so there are no cherry-picks and no conflicts. At the end it checks that the result is identical to the run branch apart from the excluded paths, and stops if not.
5. It never touches `main`: it writes a branch `promote/<run>`. Landing it is a fast-forward of `main` (verifier, under the merge lock), then `git merge main` back into the run branch. The last commit carries `Promoted-from: <sha>`, so the next promotion carries on from there. Promotions can happen at a phase end or every few days.

**Result in the dry run: Phase 6 becomes 248 commits instead of 994** (107 feat, 28 perf, 7 fix, 18 test, 47 chore, 38 docs, 2 build, 1 revert), and `git log --oneline main` reads like a changelog. Full list in the [dry run](history-workflow-dryrun.md).

# What you decide

Each option has a default, which the dry run uses.

- **A. What stays off `main`.** A1 (default): the run ledgers (`docs/runs/`) never reach `main`; they stay on the run branches and the archive tags. Work logs (`docs/log/`), REVIEW, NOTES and the rest go to `main`, folded into the feature they belong to. A2: ledgers go too, folded like the other bookkeeping. A3: also leave work logs off `main` (I'd keep them: they say how each item was checked).
- **B. How fine-grained.** B1 (default): one commit per item squash, so a multi-step item like CASCADE (cold-arrival speed-ups) gives one commit per step. B2: one commit per item code (all CASCADE steps in one); fewer commits, but a long gap between related steps would hide what came in between. B3: coarser still, one commit per phase plus docs; easy to read, useless for `git bisect`.
- **C. Process tooling.** 85 of the 248 are `chore` or `docs` commits, many of them about how the agents work (worker routing, test scripts, agent rules). C1 (default): keep them as their own commits; it's an honest record of an AI-run project. C2: fold the process-only ones into a single `chore: run tooling` commit per promotion.
- **D. Back-references.** `Run-commits:` and `Promoted-from:` name commits that exist only in this private repo. D1 (default): keep them and keep the run branches under local `archive/` tags, so they resolve here and are harmless elsewhere. D2: keep only `Promoted-from:` (the script needs one marker; it could also live in a local tag instead).
- **E. Rewrite `main` once before publishing** (needs your go-ahead; below). E1 (default): rebuild `main` from its first commit the same way: Phases 0-5 become 84 commits instead of 575, and with Phase 6 the published `main` is **333 commits** (branch `claude/promote-publish-dryrun`). E2: keep today's `main` as is and only promote from now on (the published history keeps 575 noisy commits). E3: publish one "initial import" commit of today's tree and promote from then on (simplest, but drops the item history).
- **F. Message fixes.** The script guessed type and scope for old commits; 32 Phase 6 commits (and 20 older ones) have no item code in their subject and get a plain `type: summary`. Default: leave them; or list fixes in `scripts/promote-overrides.tsv` (one line per commit: sha, class, message) and rerun. From now on the verifier writes the semantic subject itself.

# What changes where

- **`.claude/agents/verifier.md`**, squash message (step 5 of the merge): the first line becomes `type(CODE): summary` (type by the rules above; scope the item code, else the area such as `viewer`, `scripts`). "Phase merge into main": replace `git merge --no-ff claude/run-<slug>` with `scripts/promote.sh --run claude/run-<slug>`, check its summary (tree check PASS, the "needs a look" list), then `git -C M merge --ff-only promote/<slug>` under the merge lock, and `git merge main` back into the run branch.
- **`.claude/agents/coordinator.md`** "Merging into `main`": the same steps; docs-only branches may keep `--no-ff` merges on the run branch (the script turns them into docs commits).
- **`scripts/squash-merge.sh`**: optional check that the message's first line matches `^(feat|fix|perf|test|docs|build|chore|revert)(\(.+\))?: ` (warning first, refusal later).
- **`CLAUDE.md`**: one line under "Build and test commands" for `scripts/promote.sh` and its self-test, and one under "Working conventions": `main` gets promoted commits only, never a run-branch merge.
- **Optional, less noise at the source:** `scripts/docs-sync.sh` could squash a sync into one commit rather than a merge, and the ledger could live outside the tree. Neither is needed once `main` is promoted, so I'd leave both.

# One-time rewrite (option E1)

Nothing has been pushed (no remote is configured), so the rewrite only affects this machine. Steps, with the coordinator paused and no merges running:

1. Tag what exists: `archive/pre-publish` on today's `main`, `archive/run-phase6` on the run branch (and any earlier run branches still around). No tags exist yet.
2. `scripts/promote.sh --base <first commit> --run main --branch promote/rewrite`, then `scripts/promote.sh --run claude/run-phase6 --onto promote/rewrite --branch promote/publish` (what the dry run did). Both checks must say PASS; `git diff archive/pre-publish promote/publish --stat` may show only `docs/runs/`.
3. Move `main`: in the main checkout (clean, on `main`), `git reset --keep promote/publish`. Only `docs/runs/` changes on disk.
4. Reconnect the run branch: `git merge -s ours main` on `claude/run-phase6` (records the new `main` as merged without changing any file; a normal merge would conflict everywhere, since the only shared commit is the first one).
5. Your branches: `john/docs-feedback` follows the run branch and is unaffected. `john/adr-worldgen`, `john/perf-deep-dive` and `john/trial` start from old `main`; if any is still wanted, `git rebase --onto main archive/pre-publish john/<x>` moves it (the trees match), otherwise leave them on the archive.
6. Worktrees (22 now) are on run or feature branches, which are not rewritten; the post-merge snapshot worktree just moves to new commits as usual.

Risks: anything that recorded an old `main` sha (ledger lines, logs, REVIEW items that say "at 934626b4") still resolves here through `archive/pre-publish`, but not on GitHub unless that tag is pushed. A `git reset` on the wrong checkout loses work; step 3 is done once, by hand or by the coordinator, after `git status` is clean. Undo is `git reset --keep archive/pre-publish` on `main`.
