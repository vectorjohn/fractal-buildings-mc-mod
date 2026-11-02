# Workflow proposal (2026-10-27)

John asked (2026-10-27) for ways to make the run more mechanical, to run fewer tests, to see what agents are doing without an LLM, to see at a glance what needs him, and to catch stalled jobs, and later (point 6) suggested reusing a saved world instead of regenerating one for every look. This page answers each point with today's evidence, options, cost (S = an hour or two, M = half a day, L = more), what it saves and the risks, and ends with a build order and the decisions that are John's.

Written by the assistant from the run ledger ([2026-10-22-phase6](../runs/2026-10-22-phase6.md), "RESUMED 2026-10-27" and "Heavy runs"), the agent definitions, the scripts and the subagent transcripts. Nothing here is built yet; every option is a proposal.

# What exists already (not proposed again)

- [`scripts/anywhere.sh`](../../scripts/anywhere.sh) (merged 0ae0c46a today): a job waits locally in a FIFO and starts on whichever host frees first. It solved "one host idle while the other has a backlog" and "the perf worker can't drop a queued job" for jobs sent through it.
- [`scripts/test-scope.sh`](../../scripts/test-scope.sh) and [TESTING.md](../TESTING.md): which layers a change needs, from git paths.
- [`scripts/evidence.sh`](../../scripts/evidence.sh): pins commit, command, exit and logs, so a verifier can reuse a coder's heavy run.
- Merge trains and batched heavy runs: written down in `.claude/agents/verifier.md` ("merge: train", "heavy-batch"), but done by hand by an LLM verifier, so they are rarely used and each step costs context.
- Suite slots ([`scripts/suite.sh`](../../scripts/suite.sh)) already write a holder note; the heavy and merge locks don't.
- Gradle's build cache is on (`org.gradle.caching=true`). Not checked: whether test tasks actually get cache hits across worktrees.
- `scripts/reap.sh` (process cleanup) was proposed in chat earlier and never built; it is folded into point 5 below.
- [PROCESS-QUESTION-2026-10-25.md](../PROCESS-QUESTION-2026-10-25.md) answered "what is the process"; this page is about tooling, not the process description.

# The common thread

Most of today's pain comes from state that lives only in prose (the ledger, briefs, chat) or only inside one agent's head (what it is waiting on, which pid is its own). The fix is the same each time: **scripts write small state files, other scripts read them, and agents only call the scripts**. The coordinator then reads one generated board instead of re-deriving state, and John reads one generated "needs John" list.

Proposed layout (all under `<repo root>/.claude/`, not committed, shared by every worktree like the locks are):

| Path | Written by | Holds |
|---|---|---|
| `.claude/board/items.tsv` | `scripts/board.sh` | one row per item: slug, lane, agent id, branch, host, since |
| `.claude/tested.tsv` | `scripts/evidence.sh` | tree hash, layer, result, evidence folder |
| `.claude/procs/<pid>.tsv` | the test scripts | process groups our scripts started: owner label, worktree, command, start |
| `.claude/status/asks.tsv` | `scripts/ask-john.sh` | open requests for John, one per line |
| `.claude/status/board.md`, `needs-john.md`, `watchdog.log` | `scripts/status.sh`, `scripts/watchdog.sh` | generated views |

# 1. More mechanical: queues, lanes, locks

**Problem (today).** The ledger's "In flight" block is one 900-character line per item that the coordinator rewrites by hand ("r3 DONE ... → verifier ... → DONE ..."); a fresh coordinator has to re-read it all. Merges are serial LLM steps: each verifier waits for the merge lock and then for its own post-merge suite. Locks hide who holds them: HEAVY2 deadlocked for hours because `snapshot-test.sh --name heavy` took `heavy.lock` itself and `client-test.sh` then waited on it (fixed: `snapshot-heavy.lock`), and nobody could see it until John killed the session. `FB_ONLY` was in briefs before it existed, and a typo in it silently skips every test (TESTLAYERS "TO FILE").

**Option 1a: kanban lanes as a file (S).** `scripts/board.sh` with `add SLUG`, `move SLUG LANE [agent=ID branch=B host=H note=...]`, `list [LANE]`, `show SLUG`. Lanes: `ready`, `coding`, `verify`, `merge-queue`, `merged`, `heavy-batch`, `awaiting-john`, `done`, `blocked`. Each move appends a timestamped line to `.claude/board/history.tsv`. The coordinator calls `board.sh move` instead of writing status prose; the ledger keeps decisions, findings and John's words only. *Saves:* the coordinator's biggest re-read, and gives the status board and watchdog their item data. *Risk:* two sources of truth during the switch; make the board the only one for "where is item X" and say so in `coordinator.md`.

**Option 1b: a mechanical merge queue (M; the biggest single win).** The verifier stops merging. On COMPLETE it runs `scripts/merge-queue.sh add claude/<slug> <message file>` and finishes (no lock wait, no post-merge wait inside an agent). One runner, `scripts/merge-queue.sh run`, started detached (`setsid nohup`) by the coordinator, loops:

1. take every queued branch (up to 4), in order;
2. in the warm snapshot worktree (`snapshot-test.sh`'s `postmerge`), starting at the run branch head, squash each branch onto a detached HEAD, with the checks `squash-merge.sh` does today (duplicates, `review-check.sh`);
3. regenerate goldens and fixtures (option 2c) and commit them on top;
4. run the fast suite once (`test-all.sh --no-client` through `anywhere.sh`), recording into the test ledger (option 2a);
5. green: take the merge lock for a second and `git merge --ff-only` the run branch checkout to the tested commit; red: bisect over the train's commits with the failing command, drop the culprit, retest the rest, and move the culprit to lane `blocked` with the log path;
6. write one line per item to `.claude/board/history.tsv` and one notification file the coordinator reads.

The run branch then only ever moves to a commit whose tests passed, and a conflict (exit 3) is a lane move with the file list, not a verifier round. *Saves:* one fast suite per train instead of per item (today 4-6 merges a day, each ~20-40 min of suite time plus waits); the ~1 h suite-slot waits that killed verifiers' post-merge runs; most merge-lock waiting. *Risks:* a bad bisect when two items only fail together (then both go to `blocked`); a long-lived runner process, so it needs a pidfile, `merge-queue.sh stop`, and the watchdog (point 5). `squash-merge.sh` stays for manual and to-main merges.

**Option 1c: lock hygiene (S).** One small library (`scripts/lib/locks.sh`, the suite-slot code generalised) used for heavy, merge and snapshot locks: (1) every holder writes a note (pid, pgid, label, worktree, command, since) into the lock file, as suite slots already do; (2) waiters print the holder and its age every 5 min instead of silence; (3) before waiting, refuse if the holder's pid is an ancestor of the waiter (the HEAVY2 self-deadlock, caught in general rather than for one name); (4) `scripts/locks.sh` lists every lock, holder and waiter. Plus: test scripts validate their `FB_*` variables and exit 2 on unknown values (`FB_ONLY`, `FB_LAYER`, `FB_VK`). *Saves:* deadlocks found in seconds; no silent skip. *Risk:* low; the notes are advisory, the kernel lock is still the lock.

# 2. Running fewer tests

**Problem (today).** Coder and verifier often run the same suite on the same tree. Per-item full client suites (~40 min each under one heavy lock) queued five deep ("HEAVY QUEUE after hold"); the batched heavy run exists but per-item heavy runs keep being briefed. Post-merge suites ran per merge and several never finished ("wrapper killed at 30 min", "wrapper time limit while waiting for suite slot; next merge covers"), so "covered by the next merge" became normal. Every generator change regenerates all v1 goldens (`highrise.txt` changed 11 times since 2026-10-24, `shop.txt` 8), so nearly every merge conflicts in goldens or settlement fixtures (S2+S3: 13-file conflict; STEPBURY, HR-B1 and LOTRAIL each had a golden conflict round), and each conflict means another coder round and another full suite.

**Option 2a: a test ledger keyed by tree hash (S).** `evidence.sh` appends `tree-hash  layer  exit  host  evidence-dir` to `.claude/tested.tsv` (the layer from the command: fast, client-quick, client-full, order, perf, gallery). `scripts/tested.sh <layer> [<rev>]` answers "has this exact tree passed this layer?"; `evidence.sh` itself prints `ALREADY PASSED <dir>` and exits 0 when it has, unless `FB_RETEST=1`. Keyed by **tree**, not commit: a verifier's run on the coder's head, or a merge whose result tree equals an already-tested tree, is a hit. A finer key later: hash only the subtrees a layer reads (from `test-scope.tsv`), so a docs-only merge doesn't rerun planner-core. *Saves:* every duplicate coder/verifier run (most items today). *Risks:* a flaky test that passed once is not rerun; the batched heavy run and to-main always pass `FB_RETEST=1`. Host and renderer are recorded, so a perf number is never "reused" across hosts.

**Option 2b: heavy layers only in batches (S, a rule change plus a counter).** Per item: the fast layers, `client-quick` only when `test-scope.sh` says required, and targeted single tests (`FB_ONLY`); `client-full`, order, perf and the fly-through only in the heavy batch. `board.sh` counts merges since the last batch; when it reaches N (say 4) or 6 h, the status board shows "heavy batch due" and the coordinator starts one verifier (or the merge-queue runner starts the batch itself). Items whose risk is in-world (`core/world`, `mod/.../worldgen`) wait in lane `heavy-batch` before they count as done. *Saves:* most client hours (today roughly one full client run per world-touching item plus retries). *Risk:* a world bug is found later and bisected over several commits; the bisect already exists in `verifier.md`.

**Option 2c: goldens and fixtures regenerate after the merge, mechanically (M; needs a small coder item).** Generated files stop being merged by hand:

1. `.gitattributes`: `planner-core/src/test/resources/golden/** merge=ours-generated` and the same for the generated viewer fixtures (`viewer/fixtures/settlement/**`), with a merge driver (a two-line script in `scripts/`, registered once with `git config merge.ours-generated.driver`) that keeps the run branch's side and never conflicts.
2. A Gradle task `:planner-core:regenGoldens` (and a CLI step for the settlement fixtures, which `sample-city`/`city` already write) that rewrites every generated file. This is a build-file change, so it is a **coder** item (S): the tests already write `build/golden/**` on a mismatch, so the task is mostly a copy.
3. The merge-queue runner (1b) runs the regen after squashing each item and commits it as part of that item's merge. The guard against hiding a real change: the regen may change only generated files of the kinds the branch itself changed (if the branch never touched `golden/v1/house.txt`, a changed house digest after regen fails the train and goes to `blocked`).

*Saves:* the golden/fixture conflict round on nearly every generator merge. *Risk:* an unintended digest change rides along when the branch did touch that file; the verifier still reads the branch's own golden diff as today.

**Option 2d: no agent waits synchronously on a long lock (S, rule plus helper).** Anything that can wait more than ~10 min (suite slot, heavy lock, the perf worker) runs detached with a result file: `scripts/bg.sh <label> <cmd>` = `setsid nohup evidence.sh <label> anywhere.sh -- <cmd>`, writing `.claude/status/jobs/<label>.done` with the exit code. Agents poll the file (or the coordinator's notification arrives once, at the end). This is what killed post-merge runs today: the wait sat inside a tool call with a time limit.

# 3. Seeing what the agents are doing (no LLM)

**Problem (today).** John can't see what each agent is doing; neither can a fresh coordinator without the ledger. Everything needed is already on disk, just not in one place:

- each subagent's transcript, `~/.claude/projects/<project>/<session>/subagents/agent-<id>.jsonl`, with `agent-<id>.meta.json` (`agentType`, `description`); every line has a timestamp and `cwd`, tool calls carry the Bash `description`, and a `SubagentHandback` call marks the end (checked: 130 transcripts in this session's folder);
- lock files (suite-slot notes), the anywhere queue (`.claude/anywhere/`), `ssh <perf worker> job status`, `/proc/loadavg`;
- worktrees and branches (`git worktree list`), evidence folders and reports (`.claude/reports/`).

**Option 3a: `scripts/status.sh` (M).** A shell + `jq` script (jq is installed) that prints, and writes to `.claude/status/board.md`:

- **Agents**: one row per agent active in the last 2 h: id, type, description, worktree/branch (from `cwd`), lane and item (from the board), last activity age, the last Bash description, and `WAITING <lock>` / `RUNNING <job>` when its last call is a background command; finished agents drop out after an hour.
- **Machines**: load, heavy lock holder and age, suite slots, anywhere queue (`anywhere.sh --status`), the perf worker jobs (its `job help` now says 3 slots at a time and a 2 h job limit; `anywhere.sh` assumes one slot with `--depth 0`, worth revisiting).
- **Items by lane**, from `board.sh`.
- **Recent**: the last 10 merges, evidence folders and failures.

John runs `watch -n 60 scripts/status.sh` in a terminal, or reads the file. *Saves:* "what is going on" questions in chat and the coordinator's ledger re-reads. *Risks:* the transcript format is Claude Code's, not ours, and may change between versions; the script should degrade to "unknown" rather than fail.

**Option 3b: show it in SilverBullet (S, after 3a).** Copy `board.md` and `needs-john.md` every minute into an untracked, git-ignored file in the docs-feedback space (for example `STATUS.md`, listed in `.gitignore`). That worktree is the coordinator's, so this needs John's OK and a line in `coordinator.md`.

**Option 3c: a Haiku digest (S, optional).** Every 30 min the coordinator's cron (or a timer) has a Haiku agent read `board.md`, `needs-john.md` and the ledger's last diff and write five lines ("3 coding, 1 in merge queue, heavy batch due, 2 asks for John"). Only worth it if the raw board is too dense; the board itself needs no LLM.

# 4. What needs John, at a glance

**Problem (today).** Requests for John are scattered: the order-test EULA file blocked TESTLAYERS for hours (now a standing approval), the squash-merge of S4 and of HR-B1 was denied by the permission classifier ("Modify Shared Resources"), killing the orphan client pid 75050 was denied ("Interfere With Workloads") until John OK'd it, stale the perf worker jobs 8, 10, 12 and 13 needed a manual cleanup, and REVIEW.md has 33 open items. All of it reached John through chat. The transcripts show 8 classifier denials in this session alone, with the reason in the text.

**Option 4a: `scripts/ask-john.sh` and a generated list (S).** `scripts/ask-john.sh [--blocking] [--cmd '<command John can paste>'] "<one line>"` appends a row (id, time, agent, item, text, command) to `.claude/status/asks.tsv`; `ask-john.sh --done ID [answer]` closes it. Agents and scripts use it instead of a chat line (client-test.sh when a EULA file is missing, remote.sh when the perf worker is unreachable, the watchdog for anything it won't kill). `scripts/needs-john.sh` then writes `.claude/status/needs-john.md`, blocking items first:

1. open asks (blocking first), with the command to paste;
2. **permission denials**: grep the transcripts for "denied by the Claude Code auto mode classifier" since the last check, with agent, time, reason and the denied command (so a denied merge shows up without anyone relaying it);
3. orphans and stale jobs the watchdog found but won't touch, with a ready kill command;
4. files only John reviews: build files, `flake.*`, `gradle.properties` changed on the run branch since the last merge to `main`;
5. REVIEW.md: count of open items, and the five newest titles;
6. unmerged `john/*` branches and uncommitted edits in docs-feedback (counts only).

*Saves:* "John has to be told in chat". *Risk:* the list goes stale if agents keep using chat; `coder.md`, `verifier.md` and `coordinator.md` say "ask with ask-john.sh".

**Option 4b: fewer things that need John (S, John's call).** Allow rules in `.claude/settings.json` for the narrow scripts that keep being denied (`scripts/squash-merge.sh`, `scripts/merge-queue.sh`, `scripts/reap.sh --mine`), so a denial means something unusual. Not checked: whether allow rules override the auto-mode classifier in this Claude Code version; test with one rule first.

# 5. Stalled jobs

**Problem (today).** The HEAVY2 deadlock held the heavy lock for hours and earlier "2-h limit" kills may have been the same thing. An orphan client JVM (pid 75050) ran at ~150 % CPU with no lock after its test failed. Wrappers died at 30 min while waiting. Perf worker jobs 8, 10, 12 and 13 sat in its queue after the agents that sent them were gone, and their copies stayed (NOTES "the perf worker job copies"). One agent killed another's process and ran `gradlew --stop`, which stops **every** Gradle daemon of the `agent` user in every worktree, including other agents' builds and post-merge runs (it killed METROFILL's post-merge). Agents can't kill their own stale jobs because a bare `kill` of a pid looks to the classifier like interfering with someone else's workload. There are 19 worktrees and 33 local branches, several merged long ago.

**Option 5a: `scripts/watchdog.sh`, report only (M).** One pass, run every 5 min by a systemd user timer (`systemctl --user` works here; with `Linger=no` it runs only while the agent user has a session, which is the case during a run) or by the merge-queue runner's loop. It writes state changes only (not repeats) to `.claude/status/watchdog.log` and the needs-john list:

- **lock stalls**: heavy lock held longer than the command's budget (client full 90 min, quick 40, perf 20) with no growth in its log; suite slot held 10 min by a process using no CPU;
- **deadlocks**: a waiter whose lock's holder is its own ancestor, or a holder that is itself waiting on a lock (from the lock notes, 1c);
- **orphans**: `KnotClient`/`KnotServer`/Gradle worker JVMs whose process group's leader (one of our scripts) is gone, or whose worktree no longer exists;
- **stale the perf worker jobs**: jobs whose submitting `remote.sh` pid is gone (needs `remote.sh` to record job id and pid in `.claude/procs/`);
- **quiet agents**: a transcript with no new line in 30 min that hasn't handed back (unless its last call is a known long wait);
- **stale worktrees and branches**: branch merged into the run branch (or its squash content is: `git cherry`) and no process has its cwd there for a day.

**Option 5b: a process registry and `scripts/reap.sh` with safe rules (M, after 5a).** Our test scripts already run their work in their own process group (`client-test.sh`). They record it in `.claude/procs/<pgid>.tsv` (owner label, worktree, command, start, expected budget). `reap.sh` then:

- `reap.sh --mine <label>`: kills only process groups registered under that label (SIGTERM, then SIGKILL after 30 s), cancels its perf worker jobs, removes the entries. That is a narrow, named action an agent can be allowed to run on its own jobs.
- `reap.sh --orphans`: kills only registered groups whose leader is dead and whose budget is exceeded, and logs each kill. Anything not in the registry (John's own client, another user's process, an unregistered daemon) is never touched; it goes to needs-john with a command instead.
- never `gradlew --stop`: CLAUDE.md and `coder.md` forbid it, and test scripts run Gradle `--no-daemon` (client-test.sh does already) so there is nothing to stop.

**Option 5c: the perf worker clean-up (S).** `job kill` refuses queued jobs today; `job` lives on the perf worker, so allowing a queued kill is John's change there (or ours, if he wants). Until then `anywhere.sh` keeps jobs out of the perf worker's queue, and the watchdog lists abandoned jobs and stale `~/jobs/<name>` copies.

**Option 5d: fewer "still waiting" notifications (S).** The coordinator gets a notification for each background command or monitor an agent hands back. With 2d (result files) and 1b (the queue), agents end instead of handing back "still waiting", and the coordinator reads `board.md` when it wants a picture, so its context only sees finished work.

# 6. A reference world instead of regenerating (John's idea)

John (2026-10-27): "We also could consider that we don't *always* need to regenerate the world, depending on the use case. If we find an issue we could have some relatively recent world that an agent could fire up and just jump to some already generated areas to look at buildings of various types."

**What agents do today.** Every in-game look generates a fresh world:

- Every client game test (`FlyThroughClientGameTest`, `GameplayClientGameTest`, `RoadsClientGameTest`, `StepBuryClientGameTest`, `FoliageClientGameTest`, `TerrainClientGameTest`, `PerfClientGameTest`, ...) calls `context.worldBuilder()...create()`: a new world per run, generated chunk by chunk while the camera waits. `scripts/flythrough.sh` takes about 25 minutes for 90 frames (NOTES, B9), and fill is server-bound at ~44 chunks/s fresh (NOTES, gpu-intel). `scripts/server-only-test.sh` deletes `mod/build/run/serverOnly/world` before each run.
- The `architect` and `interior-designer` reviewers judge viewer galleries, `fractal-cli floorplan` and the in-game shots that client tests and fly-throughs left in `mod/build/screenshots/` and `docs/review/phase6/`. They may not run clients, so for a new in-game view they "ask the coordinator for a screenshot", which means a client test or fly-through run under the heavy lock (queued up to five deep today) that regenerates everything just to get a few frames.
- **Saved worlds are already being read, ad hoc.** ROADJOIN and ROADCLEAR (2026-10-27) diagnosed John's playtest world (seed 1709797029209580947, build e237e451, `~/kaiju-survival-server/run/world`, 605 MB, 71 852 chunks) without running the game: `scripts/McaTop.java` prints a top-block map of a coordinate box straight from the region files, and `scripts/FloatingSnow.java` scanned every generated chunk for snow over air. The ROADJOIN plan also reads `mod/build/run/serverOnly/world` that way after a server-only run. Nobody yet loads a saved world into a client to look around.

So the habit John asks about exists only for offline block reads; looking is always "regenerate, then look".

**Option 6a: a reference-world cache (M).** A saved world per (commit, seed), regenerated in batch and kept for agents to look at:

- **Where**: `~/.cache/fractal-worlds/<seed>-<short sha>/` on each host (outside every worktree, so `git clean` and worktree removal never touch it), holding `world/` (the save), `mod.jar` (the exact jar that generated it), `reference.json` (commit, seed, generation hash (below), radius, mod config, Minecraft version, date, host) and `places.tsv`. A `current-<seed>` symlink points at the newest. Keep the last two per seed.
- **Seeds**: the three review seeds the fly-through already uses (987654321, 12345, 1), plus John's playtest seed when he wants it.
- **Generation (`scripts/refworld-gen.sh --seed S [--radius R]`)**: the `server-only-test.sh` machinery (modded dedicated server, console on a FIFO, 127.0.0.1 only) with no client: `worldborder set` to the radius, then pregenerate by `forceload add` in blocks of at most 256 chunks through the console (vanilla only; no pregenerator mod needed; Chunky is the fallback, run as a black-box test-only jar like `fetch-test-mods.sh`'s), then `save-all flush` and stop. Run by the heavy batch (2b), on the perf worker or locally, after the batch's tests pass, so a reference world only ever comes from a tested commit.
- **The index (`places.tsv`)**: one row per interesting place: kind, label, x, y, z, yaw, pitch, plus the site id and tier. Kinds: settlement centre per tier and style, a sample building per building type (house, shop, highrise, ...), bridge, quay, gate, road join, plaza, slope street. Sources, cheapest first: the fly-through's `manifest.tsv` (it already finds sites and frames per tier) and `/fb locate <tier>` through the console; then building types from the planner itself, either `fractal-cli city --districts` on a terrain dump of the same world (NOTES warns live and recorded site choice differed once, so check each row against the save with McaTop), or, better, a small mod-side `/fb places` dump command (a **coder** item, S) that writes what the live planner placed. Camera presets come from the same rows (the fly-through's ring and street views).
- **Looking (`scripts/refworld-look.sh --world current-12345 --places 'kind=quay,tier=metropolis' | --at X,Y,Z,YAW,PITCH ...`)**: copy the save to a scratch dir (the cached copy is never opened for writing), start the modded server on the stored `mod.jar` with the world border at the pregenerated radius, join with the vanilla client (`tools/vanilla-client`, as `server-only-test.sh` does), `tp` to each preset in spectator mode, wait until two captures agree (the fly-through's `stableShot` rule), `import -window root`, then stop. Every frame gets the stamp burnt in with ImageMagick (already in the flake): `refworld 12345 @ <sha> (N generator commits behind HEAD) x,y,z`, and the same in the output `manifest.tsv` and the file name. Still a client, so it takes the heavy lock (or goes to the perf worker through `anywhere.sh`), but for minutes, not the 25-40 of a fly-through or suite.
- **Reading offline (`scripts/world-query.sh`)**: McaTop and FloatingSnow promoted to one tool with subcommands (`top BOX`, `block X Y Z`, `column X Z`, `find BLOCK BOX`, `count BLOCK BOX`, `scan <check>`), on a reference world or any save (John's included). No lock, no game, seconds. Checks like "is there floating snow on any road" or "what is at the coordinate John reported" become one command; reviewers who may not run clients may run this.

**When a reference world is valid.** `reference.json` stores a **generation hash**: the tree hash of the paths that can change what is generated (`planner-core/src/main`, `mod/src/main`, the mod's `resources/data`, `gradle.properties`; the list kept next to `test-scope.tsv`). `refworld-look.sh` prints, and stamps, one of:

- **exact**: no change to those paths between the world's commit and HEAD (`git diff --quiet <sha> HEAD -- <paths>`). The world is what HEAD would generate. Good for anything that only looks: architect and interior reviews, John's questions, docs pictures, bug reproduction at a coordinate.
- **stale, N commits behind**: those paths changed. Still good enough for looks reviews of things the change didn't touch ("how does a metropolis quay look" after a shop interior change), for orientation, and for "was it already like this before the change" comparisons, where the stamp is the point. The reviewer says in its verdict that it used a stale world.
- **fresh world required**, never a reference world: determinism and generation-order tests (`order-test.sh`), anything that verifies a generation change (the item's own evidence has to come from its own commit), performance and timing (generation cost is what they measure), the server-only install test, the compat matrix, and gate evidence.

**Cost.** Build: `refworld-gen.sh` (M, mostly reuse of `server-only-test.sh`), `refworld-look.sh` (M, same machinery plus presets and stamping), `world-query.sh` (S, the code exists), the index from manifests and `/fb locate` (S), `/fb places` (coder, S). About two days of assistant work plus one small coder item. Run: at John's world's density (605 MB / 71 852 chunks, ~8.6 KB a chunk) a radius of 1536 blocks (192 x 192 = ~37 000 chunks) is ~320 MB a seed, ~2 GB for three seeds kept twice. Generation time is **not measured**: at the ~44 chunks/s fresh fill rate seen through a client it would be ~14 min a seed; server-side `forceload` on 20 cores may be faster or slower. One refresh per heavy batch, so a few per day at most.

**Savings.** A screenshot request from a reviewer or John becomes a ~5-minute look (estimate, **not measured**: server and client start ~1-2 min, then frames load from disk instead of generating) instead of a client test or fly-through run that regenerates the world (25-40 min plus the heavy queue). Bug reproduction at a coordinate starts from a known save that every agent can cite ("refworld 12345 @ abc123, x=..., z=..."), as ROADJOIN and ROADCLEAR did with John's world, without first regenerating to reach the spot. Offline checks over a whole world (FloatingSnow-style scans over 70 000 chunks) need no client at all. It also takes reviewer-only screenshot runs out of the heavy queue, which is the queue that held items up today.

**Risks.**

- **A stale world misleading a review** (the main one): the commit and "N commits behind" are burnt into every frame and written to every manifest; the reviewer and verifier templates say a verdict on a generator change needs an exact world or the item's own run.
- **Chunks generated by a different build**: walking past the pregenerated edge would generate new chunks with whatever jar is loaded and leave a seam. The look script loads the stored jar, keeps the world border at the pregenerated radius, and keeps presets inside it minus the render distance.
- **Loading changes the save** (entities move, block ticks run, a newer Minecraft upgrades the chunks): the cached copy is never opened, only a scratch copy; after a Minecraft version bump the cache is thrown away.
- **Index drift**: `places.tsv` rows are checked against the save (McaTop top block at each row) when the world is built; a row that finds no building is dropped and logged.
- **Disk and transfer**: ~2 GB per host; whether `remote.sh`'s channel to the perf worker suits a ~1 GB copy is not checked, so generating on each host may be simpler than copying.

# Other fixes found on the way

- **Verifiers can't write report files**: `verifier.md`'s frontmatter is `tools: Read, Grep, Glob, Bash, EnterWorktree, ExitWorktree`, with no `Write`, so a verifier has to write its report through a Bash heredoc, which the classifier may refuse. Add `Write` to its tools, or add `scripts/report.sh <slug>` that writes stdin to `.claude/reports/<slug>.md`. S, assistant. (Agent definitions only take effect in sessions started after they reach the checkout the session runs in.)
- **Briefs naming things that don't exist yet** (`FB_ONLY`): the coordinator's brief template should say "check with `--help` before using a variable", and 1c makes a wrong variable fail loudly.
- **Scratchpad shared between agents** (ledger 2026-10-22, "testall.log clobbered"): `bg.sh` and evidence write under the worktree, never the scratchpad.

# Build order

| # | What | Cost | Who | Depends on |
|---|---|---|---|---|
| 1 | Verifier `Write` tool; `FB_*` validation; ban `gradlew --stop` in CLAUDE.md and coder.md | S | assistant | |
| 2 | Lock notes and self-deadlock check (`scripts/lib/locks.sh`, `scripts/locks.sh`) | S | assistant | |
| 3 | Test ledger in `evidence.sh` (`tested.tsv`, `ALREADY PASSED`, `FB_RETEST`) | S | assistant | |
| 4 | `ask-john.sh` + `needs-john.sh` (asks, denials, build files, REVIEW counts) | S | assistant | |
| 5 | `board.sh` lanes + coordinator.md switch to it | S | assistant | |
| 6 | `status.sh` (agents from transcripts, machines, lanes) | M | assistant | 2, 5 |
| 7 | `bg.sh` result files; rule "no synchronous long waits" in agent defs | S | assistant | 3 |
| 8 | `watchdog.sh` report-only + systemd user timer; process registry in the test scripts and `remote.sh` | M | assistant | 2, 4 |
| 9 | `:planner-core:regenGoldens` task + fixture regen command | S | coder | |
| 10 | `merge-queue.sh` (train in snapshot, regen guard, one suite, ff-only, bisect) | M | assistant (lead-coder review) | 3, 5, 9 |
| 11 | `reap.sh --mine/--orphans` | M | assistant | 8 |
| 12 | SilverBullet status page; Haiku digest | S | assistant | 6 |
| 13 | `world-query.sh` (McaTop and FloatingSnow as one offline tool) | S | assistant | |
| 14 | Reference worlds: `refworld-gen.sh`, `places.tsv` from manifests and `/fb locate`, `refworld-look.sh` with stamped frames; refresh in the heavy batch | M | assistant | 2b's heavy batch (for the refresh) |
| 15 | `/fb places` dump command for the index (building types by coordinate) | S | coder | 14 |

Items 1-5 are a day's work for one assistant and change nothing about how items are coded; they pay off at once. Item 13 can go in at any time (it is already used, just scattered); 14 pays off as soon as reviewers ask for in-game views, and does not depend on the merge queue. Item 10 is the one that cuts test time and golden conflicts the most, but it changes how merges happen, so it should go in after the ledger and board make it observable, and run alongside manual merges for a day before it replaces them.

# What John decides

1. **Merge queue**: may a script fast-forward the run branch after the train's suite passes (the verifier still verifies each item; the script only merges and tests)? Train size (proposed 4).
2. **Golden regeneration**: OK to regenerate goldens and fixtures after merge, guarded to the generated files the branch itself touched?
3. **Heavy layers only in batches**: per-item `client-full` dropped unless the brief asks for it; batch every 4 merges or 6 h?
4. **Test ledger reuse**: OK to skip a layer that already passed on the identical tree (always rerun in heavy batches and to-main)?
5. **Reaping**: report-only first (proposed), then automatic kills of registered orphans past their budget? Allow `reap.sh --mine` for agents?
6. **Permissions**: add allow rules for the merge and reap scripts in `.claude/settings.json`?
7. **Where you look**: terminal (`watch scripts/status.sh`), a file, or a page in SilverBullet (needs a git-ignored file in docs-feedback)?
8. **The perf worker**: allow `job kill` on queued jobs, and does `anywhere.sh` count the perf worker as 3 slots now that `job help` says 3 at a time?
9. **Haiku digest**: wanted, or the generated board only?
10. **Reference worlds**: build them (which seeds; add your playtest seed?), and is a "stale, N commits behind" world acceptable for looks reviews of things the change didn't touch, or only an exact one? Disk budget (~2 GB per host proposed)?
