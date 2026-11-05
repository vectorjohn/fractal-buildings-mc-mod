# Plan: workflow tooling and history on main

Assistant plan, 2026-11-05, branch `claude/assist-workflow-history-plan` (base `claude/run-phase6` at 3c92b33e). It combines the two proposals John approved on 2026-11-05: [workflow-proposal.md](../review/workflow-proposal.md) (his answers under "What John decides", "Other fixes" and the closing `@coordinator` line) and [history-workflow.md](../review/history-workflow.md) (closing `@coordinator` line; dry run in [history-workflow-dryrun.md](../review/history-workflow-dryrun.md)). His exact words are in commits 550ea0c4 and 9f7236d9 (`git show <sha> -- docs/review/`). Item codes: [GLOSSARY.md](../GLOSSARY.md).

# Decisions (John, 2026-11-05)

History ("sounds good, lets do it", no option changed, so every default stands):

- **A1**: run ledgers (`docs/runs/`) never reach `main`; work logs, REVIEW, NOTES and the rest go, folded into their feature commit.
- **B1**: one `main` commit per item squash. **C1**: process tooling keeps its own `chore`/`docs` commits. **D1**: keep `Run-commits:` and `Promoted-from:` trailers; run branches kept under local `archive/` tags.
- **E1**: rewrite `main` once before publishing (Phases 0-5 become 84 commits). **F**: fix subjects through `scripts/promote-overrides.tsv`; from now on the verifier writes `type(CODE): summary` itself.

Workflow (John approved the build order as proposed):

1. Merge queue: yes, a script may fast-forward the run branch after the train's suite passes; train size 4 (proposed, not contested).
2. Golden and fixture regeneration after merge, guarded to the generated files the branch touched: yes.
3. Heavy layers only in batches, **every 4 merges** (not "or 6 h"); per-item `client-full` only when the brief asks.
4. Test ledger reuse on an identical tree: yes (heavy batches and to-main always rerun).
5. Reaping: yes, report-only first, then automatic kills of registered orphans past budget; `reap.sh --mine` allowed for agents.
6. Allow rules in `.claude/settings.json` for the merge and reap scripts: yes.
7. Where John looks: **SilverBullet** for now ("play it by ear"; a real dashboard page later if SB doesn't fit).
8. Perf worker: yes to `job kill` on queued jobs, yes to counting it as 3 slots.
9. Haiku digest: **no**, the generated board only.
10. Reference worlds: yes, but as a **world cache**, not a few fixed seeds: when testing hits a new seed or place, that seed and area are generated once and reused by later checks. Any playtest seed is fine. Stale worlds are allowed; whether one is too stale is the agent's judgement for what it tests.
- Other fix "verifier can't write report files": "agreed, lets allow that" (add `Write` to `verifier.md`).

# Already done since 2026-10-27

| Row | What | State |
|---|---|---|
| 1 | Verifier `Write`; `FB_*` validation; `gradlew --stop` ban | **partly**: validation done (RUNGUARD: `client-test.sh` exits 2 on a bad `FB_LAYER`/`FB_ONLY`/`FB_VK`, `remote.sh` forwards `FB_*`); ban done (CLAUDE.md, coder, verifier, assistant); `Write` not done |
| 2 | Lock notes, self-deadlock check | **partly**: suite slots write notes; HEAVY2 fixed by `snapshot-heavy.lock`; no `lib/locks.sh`, heavy/merge locks silent |
| 3 | Test ledger (`tested.tsv`) | not done |
| 4 | `ask-user.sh` / `needs-user.sh` | **partly**: `docs/QUESTIONS.md` is a hand-kept "needs John" list; no script, no denial scan |
| 5 | `board.sh` lanes | not done (ledger "In flight" prose) |
| 6 | `status.sh` | not done (pieces: `anywhere.sh --status`, `postmerge-status.sh`, `remote.sh --list`) |
| 7 | `bg.sh`, no synchronous long waits | **partly**: post-merge runs detached with result files (`postmerge-run.sh`), `remote.sh` state files and `--attach`; no general helper or rule |
| 8 | Watchdog, process registry | **partly**: per-run watchdogs (`client-test.sh`, `order-run-bounded.sh`); nothing host-wide |
| 9 | `:planner-core:regenGoldens` | not done |
| 10 | `merge-queue.sh` | not done (trains and heavy batches exist as LLM verifier modes; `squash-merge.sh` detaches post-merge) |
| 11 | `reap.sh` | not done |
| 12 | SB status page (Haiku dropped) | not done |
| 13 | `world-query.sh` | not done (`McaTop.java`, `FloatingSnow.java` separate) |
| 14 | Reference worlds | not done (`playtest-kit.sh` writes per-seed `/tp` lists, a seed for the index) |
| 15 | `/fb places` | not done (`/fb info`, `/fb locate` exist) |
| P8 | Perf worker 3 slots / queued `job kill` | **partly**: the pool file says `slots = 3` and `anywhere.sh` uses it; queued `job kill` is a change to `job` on the perf worker, outside this repo |
| hist | `promote.sh` + self-test, `--onto`, overrides support, `docs/runs` excluded by default | **done** (3da41ac0, 5c263589); no overrides file, agent files and CLAUDE.md still say `merge --no-ff` |

# Steps

Each step is one agent. Assistant unless marked coder. Steps in the same group run in parallel; a group starts when the steps it needs have merged. Agent-file changes take effect only in sessions started after they reach the main checkout.

**Group 1 (start now, all parallel)**

- **W1** (row 1 rest, S): `Write` in `verifier.md` tools; coordinator brief template: "check a variable with `--help` before naming it"; coordinator heavy-batch rule "every 3-4 merges" becomes "every 4 merges", per-item `client-full` only when the brief asks (decision 3). Files: `.claude/agents/verifier.md` (frontmatter), `coordinator.md`.
- **W2** (row 2, S): `scripts/lib/locks.sh` (suite-slot code generalised) for heavy, merge and snapshot locks: holder note, waiter prints holder every 5 min, refuse when the holder is the waiter's ancestor; `scripts/locks.sh` lists all. Self-test.
- **W3** (row 3, S): test ledger in `evidence.sh`: `.claude/tested.tsv`, `scripts/tested.sh`, `ALREADY PASSED`, `FB_RETEST=1` (set by heavy batches and to-main). CLAUDE.md line.
- **W4** (row 4, S): `ask-user.sh` and `needs-user.sh` writing `.claude/status/needs-user.md`; it lists open `docs/QUESTIONS.md` lines too, so QUESTIONS stays the committed record and the script adds the live items (asks, classifier denials, build-file changes, REVIEW counts).
- **W5** (row 5, S): `board.sh` lanes, history, merge counter for decision 3 ("heavy batch due" at 4); `coordinator.md` uses it for "where is item X", the ledger keeps decisions only.
- **W13** (row 13, S): `world-query.sh` from McaTop and FloatingSnow.
- **H1** (history, S): semantic subjects. `verifier.md` step 5: first line `type(CODE): summary` (types feat, fix, perf, test, docs, build, chore, revert; scope the item code, else the area); `squash-merge.sh` warns when the first line doesn't match `^(feat|fix|perf|test|docs|build|chore|revert)(\(.+\))?: ` (warning now, refusal after a week of clean merges). Touches `verifier.md` like W1, in another section.
- **H2** (history, S): `promote.sh` hardening: self-test cases for (a) promoting after a rewrite plus a `git merge -s ours main` reconnect, (b) a docs-feedback merge on the run branch, (c) an override naming a commit outside the walked range (refuse); `scripts/promote-overrides.tsv` with subjects for the 32 Phase 6 and 20 older commits the dry run left without an item code.
- **H3** (history, S, after H1): agent files and CLAUDE.md. `verifier.md` "Phase merge into main" and `coordinator.md` "Merging into `main`": `scripts/promote.sh --run claude/run-<slug>`, check tree check PASS and the "Needs a look" list, `git update-ref refs/heads/main promote/<slug> <old main>` under the merge lock (or `merge --ff-only` where `main` is checked out), then `git merge -s ours main` into the run branch (safe: the tree check proved the content equal outside `docs/runs/`). The docs-only shortcut to `main` goes through `promote.sh` too. CLAUDE.md: a "Build and test commands" line for `promote.sh` and its self-test; under "Working conventions": `main` gets promoted commits only, never a run-branch merge, and run branches are never pushed (they carry the ledgers and old history).
- **W6p** (decision 6, S): `.claude/settings.json` allow rules, starting with `scripts/squash-merge.sh` and `scripts/promote.sh` (one rule first, to see whether allow rules beat the auto-mode classifier); W10 and W11 add theirs. John reviews the file (CLAUDE.md: config changes are listed for him).
- **C9** (row 9, **coder**, S): `:planner-core:regenGoldens` Gradle task plus the fixture regen command (build file change: listed for John).

**Group 2 (after W2, W3, W4, W5)**

- **W6** (row 6, M): `status.sh` (agents from transcripts, machines, lanes) writing `.claude/status/board.md`.
- **W7** (row 7, S): `bg.sh` result files; "no synchronous long waits" rule in coder, verifier, assistant files.
- **W8** (row 8, M): `watchdog.sh` report-only on a systemd user timer; process registry in test scripts and `remote.sh`.

**Group 3**

- **W10** (row 10, M; after W3, W5, C9, H1; lead-coder review): `merge-queue.sh`, trains of 4, regen guard, one fast suite, ff-only, bisect; takes the verifier's semantic message file as is. Runs beside manual merges for a day before it replaces them.
- **W11** (row 11, M; after W8): `reap.sh --mine` / `--orphans`, report-only first, kills after a clean week.
- **W12** (row 12, S; after W6): copy `board.md` and `needs-user.md` into a git-ignored `STATUS.md` in the docs-feedback space each minute, plus a `coordinator.md` line. No Haiku digest.

**Group 4 (world cache; independent of the merge queue, can start once W13 is in)**

- **W14** (row 14, M, redesigned by decision 10): `scripts/worlds.sh get --seed S --at X,Z [--radius R]` returns a cached save covering that area, generating it (`server-only-test.sh` machinery, pregenerated by `forceload`) only when none exists; cache in `~/.cache/fractal-worlds/` keyed by seed, area and commit, with an index of what each save covers and an LRU cap of ~2 GB per host; `worlds.sh look` copies the save, loads the stored jar, takes the frames with the commit and "N generator commits behind" stamped on. Seeds to start: the fly-through's 987654321, 12345 and 1, plus John's playtest seed 1709797029209580947. The reviewer and verifier files say: staleness is a judgement call, stated in the verdict; generation, order, perf and gate evidence always need a fresh world.
- **C15** (row 15, **coder**, S; after W14): `/fb places` dump for the index.
- **P8** (John, outside the repo): allow `job kill` on queued jobs in the perf worker's `job` wrapper (he said yes; the change lives on that machine). The separate QUESTIONS line on exclusive perf jobs stays open.

**Group 5 (last, in this order)**

- **E1: one-time rewrite of `main`. Needs John's explicit go at execution time**, although the plan is approved: it is the one step that is hard to undo once published. Run with the coordinator paused, no merge or post-merge running, H2 merged. `main` is checked out in no worktree today (the main checkout is on `john/trial`), so the move is a ref update with an old-value check:

  ```sh
  R=/home/agent/fractal-buildings-mc-mod; cd $R
  old=$(git rev-parse main)
  git tag -a archive/pre-publish main -m "main before the one-time rewrite"
  git tag -a archive/run-phase6 claude/run-phase6 -m "run branch at the rewrite"
  git tag -a archive/run-phase5 claude/run-phase5 -m "run branch at the rewrite"
  scripts/promote.sh --base $(git rev-list --max-parents=0 main) --run main \
      --branch promote/rewrite --summary .claude/reports/promote-rewrite.md
  #   check: "tree check: PASS"; John reads `git log --oneline promote/rewrite`
  git diff --stat archive/pre-publish promote/rewrite   # only docs/runs/
  flock $R/.claude/merge.lock git update-ref refs/heads/main promote/rewrite $old
  flock $R/.claude/merge.lock git -C $R/.claude/worktrees/run-phase6 \
      merge -s ours main -m "Reconnect to rewritten main (no file changes)"
  ```

  The last line records the new `main` as merged into the run branch without changing a file; without it `promote.sh` refuses (base not an ancestor). John's `john/adr-worldgen`, `john/perf-deep-dive` and `john/trial` stay on old history; `git rebase --onto main archive/pre-publish john/<x>` moves one if he wants it. **Undo**: `git update-ref refs/heads/main archive/pre-publish^{} promote/rewrite` (and `git reset --keep HEAD^` in the run worktree to drop the reconnect merge, if nothing came after it). Nothing is pushed by this step; once `main` is pushed, undo would need a force-push.

- **E2: Phase 6 to `main` through `promote.sh`** (the `merge: to-main` verifier, after E1 and the Phase 6 gate; order: rewrite `main`, promote the run branch, fast-forward): the to-main test run as today, then `scripts/promote.sh --run claude/run-phase6` (writes `promote/run-phase6`, tree check PASS, Needs-a-look list read), `git update-ref refs/heads/main promote/run-phase6 <main>` under the merge lock, `git merge -s ours main` into the run branch. Publishing (a remote, pushing `main` and maybe the archive tags) is a separate decision for John.

Order against the build order: groups 1-4 follow John's rows 1-15 (W-numbers match rows); H1-H3 sit in group 1 because the merge queue (W10) must already write semantic messages and E2 needs the new to-main steps. Nothing here changes `planner-core/`, `cli/`, `viewer/` or `mod/` except C9 and C15.

# State file formats (W2, W3; for the board and status pages)

Written 2026-11-05 with W2 and W3 (branch `claude/assist-wfhist-c`). W5 (`board.sh`) and W6 (`status.sh`) read these files; they are not committed and live in `<main root>/.claude/`, shared by every worktree.

- **Test ledger** `.claude/tested.tsv` (W3; writer `scripts/evidence.sh`, reader `scripts/tested.sh`; the authoritative description is the header of `scripts/lib/tested.sh`). Tab separated, one header line `time tree layer exit host renderer commit label evidence command`, then one row per evidence run, appended when it ends, never edited. `tree` is the clean worktree's `HEAD^{tree}` or `dirty`; `layer` is a key such as `fast`, `fast+client-quick`, `fast+client-full+order`, `client-quick`, `client-only:A,B`, `order`, `gradle:<args>`, `perf` or `cmd:<command>`; `exit` is the exit code or `reused` (an `ALREADY PASSED` skip); `host` is `local` or the worker's pool name; `label` is the evidence label, normally the item slug. A reader takes the newest non-`reused` row per (tree, layer); exit 0 is a pass. The board can show "last test per item" by `label`, and "already passed" for a branch head with `scripts/tested.sh <layer> <rev>`.
- **Lock notes** (W2; writer `scripts/lib/locks.sh`, reader `scripts/locks.sh list`): beside each lock file `<lock>.holder` (lines `pid=`, `pgid=`, `label=`, `worktree=`, `cmd=`, `since=` epoch seconds) while it is held, and one `<lock>.wait.<pid>` (`since=`, `label=`) per waiter. A note whose pid is gone or whose lock is free is stale; `scripts/locks.sh list` checks that and also shows plain `flock` holders and waiters from `/proc/locks`. Suite slots keep their one-line note inside `suite.lock.N`, as before. Prefer calling `scripts/locks.sh list` over reading the files.
