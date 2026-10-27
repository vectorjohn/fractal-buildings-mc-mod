# CLAUDE.md

A Fabric mod for Minecraft (Java edition) that generates varied, believable
buildings and whole cities from rules rather than stamped templates. Research
and a three.js review loop come first; worldgen integration is proven with
throwaway spikes before the architecture is locked in. The plan is in
`docs/ROADMAP.md`; the design in `docs/ARCHITECTURE.md`.

## Where things live

| What | Where | Who writes it |
|---|---|---|
| Phases, items (checkboxes), testing strategy, polish backlog | `docs/ROADMAP.md` | coders tick **their own** item; the status table is the coordinator's |
| Run ledger: what's in flight, who's on it, outcomes | `docs/runs/<date>-<slug>.md` | coordinator only |
| Work log, one file per item: what was done, how it was verified, surprises | `docs/log/<YYYY-MM-DD>-<item-slug>.md` | the coder of that item |
| Durable facts: API gotchas, test quirks, known flakes, baselines (one bullet per line) | `docs/NOTES.md` | anyone, append only |
| Cheap-to-change assumptions made without the human (one table row per line) | `docs/ASSUMPTIONS.md` | anyone, append only |
| Decisions that shape the architecture (gate outcomes) | `docs/adr/NNNN-<slug>.md` (listed by the query page `docs/adr/INDEX.md`) | researcher drafts; the human approves |
| Research one-pagers, and their index with statuses | `docs/research/<track>/<slug>.md`, `docs/research/INDEX.md` | research agents; INDEX lines by the coordinator |
| Plans for large items | `docs/plans/<slug>.md` | lead-coder (`mode: plan`) |
| Things only the human can judge (looks, "would a person build this", in-game feel) | `docs/REVIEW.md` | anyone, append only; results by the human |
| Finished phases' full text, old material | `docs/archive/` | coordinator at a phase merge |

Agents read **slices** of the big docs (`grep -n` the item, then read that
range), not whole files. Keep every doc lean: the log lives in per-item files
precisely so nobody has to read history to find the current state.

## Reading docs (SilverBullet)

John reads these docs in SilverBullet 2.11.0 (pinned in the flake; the repo
root is the space), and also on GitHub and as an EPUB, so write plain markdown
that works in all three. Full conventions: `docs/SILVERBULLET.md`.

- `#` headings only; keep the `# Title` line at the top of each file.
- Link docs with relative markdown links (`[x](pcg/split-grammars.md)`), not
  `[[wiki links]]`, and not to `#anchors`. Tasks are `- [ ]` / `- [x]`.
- Research notes and ADRs start with YAML frontmatter (`tags: research` /
  `tags: adr`, plus the keys in their `TEMPLATE.md`); other docs don't need it.
- Backtick a `#word` you don't mean as a tag. Docs are 2.x: no v1 syntax.
- ROADMAP phase sections carry an `Updated: YYYY-MM-DD` line; update it when
  you edit that section.

## Human review loop

- John reviews docs in the worktree `.claude/worktrees/docs-feedback` (branch
  `john/docs-feedback`), served by SilverBullet on `127.0.0.1:3000`
  (localhost only). It is kept at the run branch's latest docs.
- He edits there without committing and signs feedback `@John` (inline
  `-- @John` or not). Some of it is questions for agents: answer in place
  with `-- @assistant` (or your role) on your own branch, or let the
  coordinator relay the answer.
- He may also write `@John <date>: <comment>` on its own line. Reply as a
  thread: an indented list item under his line, `  - @<role> <date>: ...`,
  naming the agent role that answered (e.g. `@assistant`, `@researcher`).
- **Only the coordinator** syncs, commits or merges that worktree; other
  agents never touch it (procedure in `.claude/agents/coordinator.md`).
- John's own working branches are `john/<claude branch it came from>` (e.g.
  `john/adr-worldgen`); the coordinator merges them like `john-work`.

## Module boundaries

- `planner-core/`: pure Java. **Never imports `net.minecraft`** or Fabric, and
  has no Minecraft on its classpath (the build enforces this). It turns a seed,
  a lot and probes into a Building IR / city plan.
- `cli/`: runs the planner and writes IR as JSON (`gen --seed --lot --style`).
- `viewer/`: TypeScript + three.js. Reads IR JSON only; renders it, draws
  invariant failures, and produces seed galleries via Playwright.
- `mod/`: the Fabric adapter. Maps material roles to block states and IR to
  structure pieces. Keep it thin: Minecraft APIs move between releases.
- **Probes** (terrain height, biome) are interfaces in the core. Implementations:
  synthetic (unit tests), recorded dumps (viewer, CLI), live Minecraft (mod).
- Generators emit **abstract material roles** (wall, trim, roof, floor);
  palettes map roles to blocks per style or biome.
- **Determinism is a hard rule.** Everything derives from the world seed and
  position; chunks generate independently and in any order. In core code: no
  `Math.random`, no wall-clock time, no iteration over unordered collections
  (`HashMap`/`HashSet`) where order affects output, no shared mutable RNG across
  threads. Derive sub-seeds by hashing (seed, coordinates, purpose).

## Where you're running

- Host is **NixOS**. You are the unprivileged `agent` user: no sudo, not a
  trusted Nix user. You can't install system packages; `apt`/`dnf`/`npm -g`
  style installs won't work or won't stick.
- **Always work inside the flake's dev shell** for anything that builds, runs or
  tests: `nix develop -c <cmd>`, unless your shell already is one (check
  `IN_NIX_SHELL`). The only exception is pure research and writing files.
- **All tooling comes from `flake.nix`.** If a tool or library is missing, **add
  it to the flake**. That is expected and sometimes required (Playwright, a
  native library, a CLI tool); don't download binaries into `~`, don't use
  `nix profile install`, and don't work around a missing dependency. Commit
  `flake.lock` with it, and list the flake change in your report.
- Node packages for `viewer/` come from `npm install` inside the dev shell
  (project-local `node_modules/`), with the lockfile committed. Anything that
  needs native binaries (Playwright browsers) comes from the flake instead;
  see the comment in `flake.nix`.

## Network

- Internet works (GitHub, Maven repos, Mojang, Fabric, npm), including Nix's
  own fetches.
- LAN (`192.168.0.0/16` etc.) and the tailnet (`100.64.0.0/10`) are **blocked by
  a per-user firewall**. This is intentional; don't try to work around it.
  Loopback and DNS are open.
- **One exception: SSH to the worker box `beebox`**, for running builds and
  tests there through `scripts/remote.sh` (see "Parallel work"). Use it only
  for that; nothing else on the LAN or tailnet is reachable, and that's on
  purpose. It may not be set up on every host: `scripts/remote.sh --check`
  says whether it is.
- If a DNS lookup fails, report it rather than switching URLs to dodge it.

## Dev shell

`devShells.default` provides:

- **JDK 25** (Minecraft 26.x needs it), with `JAVA_HOME` set. When the target
  Minecraft version changes, check https://fabricmc.net/develop/ for the
  Minecraft/Loader/Fabric API/Loom versions and the Java version they need.
- Gradle comes from the project's `./gradlew` wrapper, not the system. Set
  `org.gradle.java.installations.auto-download=false` in `gradle.properties` so
  Gradle uses the flake's JDK.
- Node.js (with npm) for the viewer.
- The native libraries LWJGL `dlopen`s, on `LD_LIBRARY_PATH` (plus
  `/run/opengl-driver/lib` for Mesa). If the client crashes at startup with
  `UnsatisfiedLinkError` or "GLFW error 65542 / failed to load library", a
  library is missing: read the log, add it to the flake, retry.

## Display / running the game

- There's **no physical display**; use Xvfb or Xvnc.
- **Render with Vulkan on Mesa's software driver (lavapipe).** Minecraft's
  OpenGL path needs an sRGB GLX visual that Xvfb/Xvnc don't offer, so the game
  falls back to Vulkan. The dev shell pins the loader to lavapipe with
  `VK_DRIVER_FILES`.
- The host's NVIDIA and Intel GPUs are visible to Vulkan, but **don't use them**
  without asking. The GPU is shared with the human's desktop. If lavapipe is
  too slow for something, say so, or run it on the worker box, which renders
  on its own GPU (`scripts/remote.sh`, see "Parallel work").
- **Automated runs:** `xvfb-run -a nix develop -c ./gradlew <task>`, under the
  heavy lock, or on the worker box with `scripts/remote.sh` (see "Parallel
  work").
- **When the human wants to watch:** a VNC display on `:1`, localhost only:
  ```sh
  Xvnc :1 -localhost -geometry 1920x1080 -SecurityTypes None &
  DISPLAY=:1 nix develop -c ./gradlew :mod:runClient
  ```
  Always pass `-localhost`.
- **Desktop launch (only when the human asks):** the human logs into GNOME as
  `agent` on the host's screen and you start the client there with GPU
  rendering; the one case where the GPU is pre-approved. Check the session
  exists (`loginctl` shows an `agent` session on `seat0`, and
  `/run/user/1500/wayland-0` exists), then
  `setsid -f ./scripts/run-desktop.sh > run/desktop-client.log 2>&1 < /dev/null`.
  If there's no session, ask; don't fall back to the GPU on Xvfb/Xvnc.
- `runClient` doesn't exit on its own. Run it in the background or with a
  timeout and read `run/logs/latest.log`. Prefer client game tests.
- The dev client runs offline with a dev username. Don't ask for or store
  account credentials. No audio device: OpenAL errors are expected.
- Don't set `eula=true` for a dedicated server on the human's behalf. Ask.

## Build and test commands

*(Phase 0 fills these in as they come to exist; keep this section the single
source of truth, and update it when a command changes.)*

- Everything: `scripts/test-all.sh` (`--no-client` skips layer 5, the Minecraft
  client game tests; the script enters the dev shell and takes the heavy lock
  itself, so don't wrap it in either). Per-step logs in `build/test-all/`; ends
  with a per-layer summary, screenshot and log paths; non-zero exit on failure.
- Planner core (pure, fast): `nix develop -c ./gradlew :planner-core:test`
- Mod, incl. server game tests (`:mod:runGameTest`, wired into `check`): `nix develop -c ./gradlew :mod:build` (jar in `mod/build/libs/`, planner-core bundled as jar-in-jar); server tests alone: `nix develop -c ./gradlew :mod:runGameTest`
- Client game tests (heavy, ~20 s, exits on its own): `scripts/client-test.sh [gradle args]` (enters the dev shell and takes the heavy lock itself; runs `:mod:runClientGameTest` under `xvfb-run`; the bare `xvfb-run -a nix develop -c ./gradlew :mod:runClientGameTest` is also safe, since the task itself drops `WAYLAND_DISPLAY` and sets `SDL_VIDEODRIVER=x11`); screenshots are copied to `mod/build/screenshots/` (e.g. `hello-hut.png`; the raw ones are in `mod/build/run/clientGameTest/screenshots/`), log in `mod/build/run/clientGameTest/logs/latest.log`
- Viewer (`viewer/`: Vite + Vitest + strict TS; `npm test` type-checks then runs headless): `cd viewer && nix develop -c npm ci` once, then `nix develop -c npm test`, `nix develop -c npm run build`, `nix develop -c npm run dev` (dev server); how-to for humans (URLs, controls, env vars): `docs/VIEWER.md`; gallery: `nix develop -c npm run gallery` (one gallery per `viewer/gallery-presets/<name>.json`: generates each with the CLI batch (or, for `"kind": "dump"`, the core fixture dump) into `viewer/gallery-data/gen/<name>/`, builds into `dist-gallery/`, captures one PNG per preset x camera to `viewer/out/gallery/<name>-<camera>.png`, cameras front-left, back-right, top, front and optionally cutaway; `FB_PRESETS=a,b` limits the presets, `npm run gen -- a b` regenerates only those; `FB_PORT` picks the preview port; `npm run dev:gen` serves the same data in the dev server)
- CLI: `nix develop -c ./gradlew :cli:installDist` then `cli/build/install/fractal-cli/bin/fractal-cli <gen|batch|check|bench|diversity|region|city|city-check|sample-city> ...` (inside the dev shell; no args prints usage, bad input exits 2; generator v1 by default, `--gen v0` for Phase 3 output, `--type T` picks the building type (`check`/`bench`: `--types`), style `auto` picks it from the biome), e.g. `gen --seed 1 --lot 16x12 --style medieval --terrain plane:64:1:0:4 --check --out out.json`, `batch --seeds 0..15 --lot 16x12 --style medieval --out-dir viewer/gallery-data/gen/medieval` (seed-N.json plus index.json for the viewer), `city --seed 12345 --terrain recorded:fixtures/terrain/w12345-river-s8.json.gz --site -900,-3500,town,300 --out-dir viewer/fixtures/settlement/skeleton/river-town` (a settlement skeleton: `settlement.json` + `terrain.json.gz`; `--cell CX,CZ` instead of `--site` plans the settlement a region cell belongs to through the join rule; fixtures in `viewer/fixtures/settlement/skeleton/README.md`; `--districts` also plans every district: `districts/<id>.json` with briefs filled and lot counts in `settlement.json`, `--scenes all|none|auto:N|ID,ID` adds exterior district scenes (`--scene` is the old spelling; `--interiors` keeps interiors; `--check` prints every violation); fixtures in `viewer/fixtures/settlement/districts/README.md`), `city-check --seeds A..B [--terrain T,..] [--sites cells|random:N|gates:FILE] [--scenes auto:N] [--ignore RULE,..] [--out FILE.md]` (plans every district of each settlement, builds scenes for N of them, runs every settlement and building rule, markdown report; exit 1 on a violation not ignored; e.g. `city-check --seeds 1 --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:2`), `sample-city --out-dir viewer/fixtures/settlement/sample` (the sample settlement plan, its districts and one district scene; `--big N` writes a synthetic N x N grid for viewer performance tests, put it under `viewer/out/`; regenerate the committed fixture with it when `SampleSettlementTest` says it is stale), `check --seeds 0..999` (exit 1 on any violation), `bench --n 1000`; or `:cli:run --args="..."`; city gallery (D0): `nix develop -c npm run city` plans every `viewer/city-presets/<name>.json` (one per T2 gate window, plus the T1 river-town, mixed-city and a synthetic city, and a hamlet and a village on each of the T1 windows (D1)) into `viewer/out/city/<name>/` (`npm run city -- a b` only those), and `npm run city-gallery` also builds the viewer and captures `plan.png` plus a 3D overview and street frame of two district scenes per city next to the data (`FB_CITIES=a,b` limits it; not part of `scripts/test-all.sh`)
- Viewer screenshot (Playwright, software WebGL, writes `viewer/out/smoke.png`): `cd viewer && nix develop -c npm run screenshot`
- Whole Gradle build (all three modules + boundary check): `nix develop -c ./gradlew build`
- Boundary check alone: `nix develop -c ./gradlew :planner-core:checkNoMinecraft` (also part of `check` and run before `compileJava`)
- **Any of the above on the worker box:** `scripts/remote.sh -- <command>`, the command exactly as you'd run it from the worktree root but without a `flock .../heavy.lock` prefix (e.g. `scripts/remote.sh -- scripts/test-all.sh`, `scripts/remote.sh -- scripts/client-test.sh`, `scripts/remote.sh -- nix develop -c ./gradlew :planner-core:test`). It copies the worktree (no `.git`, nothing gitignored), queues the command, prints the last 40 lines and saves the full log to `build/remote/<job>.log`, copies results back to the same paths (`build/test-all/`, JUnit XML, `mod/build/screenshots/`, client test logs, `viewer/out/`; `--fetch PATH` for more) and exits with the command's status. Output written elsewhere doesn't come back unless you `--fetch` it. `scripts/remote.sh --check` exits 0 if the worker is reachable; `--help` for the rest.

## Parallel work (a priority)

Running work in parallel is the default in this project, not an exception.
Most of it is pure Java, TypeScript and docs that build in seconds, so several
agents can work and test at once. The rules below exist to make that safe.

- **One worktree per branch**, under `<repo root>/.claude/worktrees/<slug>`,
  each with its own `build/`, `run/` and `node_modules/`. Never work in another
  agent's worktree or in the main checkout.
- **Builds and tests run concurrently by default**: planner-core tests, viewer
  tests, CLI runs, server game tests, Playwright captures. The host has 20
  CPUs and 62 GB of RAM.
- **Heavy jobs take the heavy lock**: Minecraft client game tests, `runClient`
  under Xvfb, and timing or performance measurements. Lavapipe uses every core
  it can, so two of these at once slow each other down and make
  timing-sensitive tests flaky. Run them as
  `flock <repo root>/.claude/heavy.lock <cmd>` (it waits its turn).
  Timing numbers taken while other agents are busy are noisy: record the load
  (`cat /proc/loadavg`) next to them, and re-measure on a quiet machine before
  a number decides a gate.
- **The worker box (`beebox`) is a second heavy slot.** It runs one job at a
  time from its own queue, so it doesn't use the heavy lock, and a busy local
  heavy lock doesn't block it. When `scripts/remote.sh --check` succeeds, send
  heavy jobs and long runs there with `scripts/remote.sh -- <cmd>`: client
  game tests, `scripts/test-all.sh`, the ~10 min `:planner-core:test`,
  galleries. Run quick builds and tests locally as before; copying the
  worktree over costs a few seconds. If the check fails, use the local heavy
  lock as above.
  - **It renders on its GPU**, not lavapipe: there's no desktop on it to
    share the GPU with. `scripts/remote.sh` runs the command in a headless
    sway session and the test scripts use its Xwayland display instead of
    Xvfb (`FB_USE_DISPLAY=1`), so Minecraft gets hardware OpenGL (radeonsi;
    the log says "Using graphics device: AMD Radeon Graphics"). Screenshots can
    differ slightly from lavapipe ones. If a run fails in a way that looks
    like a GPU or display problem, retry with `scripts/remote.sh --cpu`
    (Xvfb + lavapipe, as locally) and add a line to `docs/NOTES.md`.
  - It runs one job at a time, so it's quiet, which suits measurements, but
    **timings from different hosts or renderers aren't comparable**: record
    the host (`beebox` or this one) and renderer with every number, and
    compare like with like.
  - Interactive runs (`runClient` you or the human watch, VNC, the desktop
    launch) stay local.
  - In the integration checkout, a remote run still holds the merge lock like
    a local Gradle run (`docs/NOTES.md`): it copies results back into the
    checkout's `build/` directories.
  - Its output is the tail of the log, lines cut to 300 characters; the full
    log is in `build/remote/<job>.log`. Grep that rather than printing it.
- **Merges into a shared branch take the merge lock**: hold
  `flock <repo root>/.claude/merge.lock` for the whole sequence (clean-checkout
  check, merge, commit, post-merge tests, cleanup). Two merges into the same
  checkout at once corrupt each other.
- **Merge your parent branch into yours before your final test run**, and
  resolve conflicts yourself. Any agent may merge its parent (integration)
  branch into its own branch at any time; only the verifier and coordinator
  merge into a shared branch.
- **Avoid conflicts by ownership**:
  - Tick only your own ROADMAP checkbox and write notes under it. The status
    table and phase headings belong to the coordinator.
  - Write your log entry as a new file in `docs/log/`; never edit another
    item's log.
  - `docs/NOTES.md`, `docs/ASSUMPTIONS.md`, `docs/REVIEW.md` and
    `docs/research/INDEX.md` are append-only and merge with `union` (see
    `.gitattributes`). Add entries at the end of the relevant section; don't
    reflow or edit others' lines.
  - The first Gradle build of a new Minecraft version populates the shared
    Loom cache in `~/.gradle`; let one build finish that before fanning out.
- **Never kill a process you didn't start.** Other agents, and the human's own
  game client, run on the same machine. Before you finish, stop everything you
  started.

## Working conventions

- Multi-item work runs through the agents in `.claude/agents/` (see README,
  "Long runs with agents"). The `coordinator` keeps several items in flight
  at once. **`coder` (Sonnet) does most of the implementation and `assistant`
  (Opus) most of the research, tooling, testing support and small proofs of
  concept**; `researcher` (Opus, high effort) takes research where depth
  matters (decompiled-source reading, deep-dives, the synthesis, ADR drafts)
  and is the high-effort fallback; `lead-coder` (Opus) is reserved for
  genuinely hard coding problems (plans for large or unclear items, diagnosis
  after a failed verification). The `verifier` reviews and merges.
- **Research, then spikes, then architecture.** Research notes are one page at
  most, one per project/paper/technique, using `docs/research/TEMPLATE.md`, with
  the licence recorded. Spike code is **never merged**: a spike's findings note
  (and its test, re-implemented later if it's to be kept) is what lands, and its
  lessons go into an ADR.
- **INDEX statuses steer research:** `new`, `reviewed`, `deep-dive` (spend more
  time on it), `drop` (stop). The human sets them; agents read them before
  starting a research batch.
- **Review gates.** Each phase ends with something the human looks at and
  approves before the next phase starts (ROADMAP, "Gate" under each phase).
  Work that depends on an unpassed gate waits; everything else carries on.
- **Look at what you generate.** Every generator change comes with viewer
  output (screenshots or a seed gallery) that an agent has actually looked at
  (open the PNGs with Read), compared with the previous gallery where one
  exists.
- **Completion bar: working and reviewable first, polish later.** Breaking (fix
  before an item counts as done): build or tests fail, determinism broken, the
  core imports Minecraft, an invariant fails (unreachable rooms, open roofs,
  floating blocks, leaving the lot), a crash, or the feature produces no
  usable output in the viewer or the game. Non-breaking (fix if trivial,
  otherwise record as a "*Known issues:*" note under the checkbox and in the
  ROADMAP "Polish backlog" and move on): looks a bit off, edge cases, weak
  tests for secondary behaviour, doc slips. Judgments only the human can make
  go in `docs/REVIEW.md`.
- **Decisions without the human:** pick a reasonable, cheap-to-change option,
  add a row to `docs/ASSUMPTIONS.md`, and carry on. Architecture-shaping
  decisions get an ADR and wait for the human at the gate.
- **Licences:** study other mods' code only as their licence allows, and never
  copy code from a project whose licence is incompatible. Record the licence in
  every one-pager.
- **Scope:** villager AI, quests and interiors-as-gameplay are later projects.
- Say whether you actually ran something. "Not run" is fine; a false "passes"
  is not.
- Git: commit locally in small steps on `claude/*` branches. Only push if a
  remote is configured, and then only to `claude/*` branches. Never
  force-push other branches. `main` changes only through merges.
- Never read or write outside this user's home and `/tmp`. Other users' homes
  are private by design.
- Before changing `build.gradle*`, `settings.gradle*`, `gradle/wrapper/*`,
  `gradle.properties`, `flake.nix`, `viewer/package.json` dependencies or
  `.envrc`, go ahead if it's needed, but list it in your summary. The human
  reviews those before running the code as themselves.
