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
| Design standards: John's numbered rules for looks and layout (`DS-n`), living and amended over time | `docs/design/STANDARDS.md` | reviewers (`architect`, `interior-designer`) propose; John accepts |
| Style briefs: what each style should look like, one file per style | `docs/design/styles/` | agents draft; John accepts |
| Block reference for the design agents (generated: ids, variants, colours) | `docs/design/blocks.md`, plus the table in the reviewer agent files | `scripts/design-blocks.sh` only; never edit by hand |
| Block judgement cells (role, style fit, notes) that `scripts/design-blocks.sh` reads | `docs/design/blocks-notes.md` | John edits by hand (agents may propose); then rerun `scripts/design-blocks.sh` |
| Research one-pagers, and their index with statuses | `docs/research/<track>/<slug>.md`, `docs/research/INDEX.md` | research agents; INDEX lines by the coordinator |
| Plans for large items | `docs/plans/<slug>.md` | lead-coder (`mode: plan`) |
| Performance issues and accepted tradeoffs | `docs/PERFORMANCE.md` | anyone, append; decisions by the human |
| Things only the human can judge (looks, "would a person build this", in-game feel) | `docs/REVIEW.md` | anyone, append only; results by the human |
| Open questions for John: one line per decision waiting for him, linking where it's discussed | `docs/QUESTIONS.md` | anyone appends; the coordinator removes answered lines |
| Finished phases' full text, old material | `docs/archive/` | coordinator at a phase merge |
| Glossary: every item code (PARREADS, TPHOLD, ...) and run term, one line each, with its ROADMAP number, link and status | `docs/GLOSSARY.md` (generated) | `scripts/glossary.sh` only; non-item codes and terms go in `docs/glossary-extra.tsv` (anyone), then rerun |

Agents read **slices** of the big docs (`grep -n` the item, then read that
range), not whole files. Keep every doc lean: the log lives in per-item files
precisely so nobody has to read history to find the current state.

ADRs record a point-in-time architecture decision and are superseded, not
edited; `docs/design/STANDARDS.md` holds living rules for how things should
look and work, which generators and reviewers check against.

## Reading docs (SilverBullet)

John reads these docs in SilverBullet 2.11.0 (pinned in the flake; the repo
root is the space), and also on GitHub and as an EPUB, so write plain markdown
that works in all three. Full conventions: `docs/SILVERBULLET.md`.

- `#` headings only; keep the `# Title` line at the top of each file.
- Link docs with relative markdown links (`[x](pcg/split-grammars.md)`), not
  `[[wiki links]]`, and not to `#anchors`; the one exception is a link inside a table cell, which is a wiki link from the repo root (`[[docs/log/x|text]]`), since SilverBullet opens table markdown links in a new tab (John 2026-10-31; `docs/SILVERBULLET.md`). Tasks are `- [ ]` / `- [x]`.
- Research notes and ADRs start with YAML frontmatter (`tags: research` /
  `tags: adr`, plus the keys in their `TEMPLATE.md`); other docs don't need it.
- Backtick a `#word` you don't mean as a tag. Docs are 2.x: no v1 syntax.
- ROADMAP phase sections carry an `Updated: YYYY-MM-DD` line; update it when
  you edit that section.
- **Gloss item codes for John.** In anything meant for John (`docs/REVIEW.md`,
  `docs/QUESTIONS.md`, `docs/review/`, report summaries he reads, messages to
  him), give an item code a gloss on first use, "MAINSTUB (main roads that end
  at a gate)", or link its ROADMAP entry. [docs/GLOSSARY.md](docs/GLOSSARY.md)
  is the lookup; a new code that isn't a ROADMAP item gets a row in
  `docs/glossary-extra.tsv`, then `scripts/glossary.sh`.

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
- A `docs/REVIEW.md` item that asks John to look at something (screenshots,
  plan maps, a report excerpt) copies the 1-3 key files into a tracked
  `docs/review/<item-slug>/` (downscaled, about 1 MB per item at most) and
  links the copies relatively. `.claude/reports/` is gitignored and John can't
  open it: name it only in plain text, as the forensic source.

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
  tests: `scripts/dev <cmd>` (from a subdirectory `../scripts/dev`; it runs `<cmd>`
  directly when `IN_NIX_SHELL` is set). The only exception is pure research and writing files.
- **The flake is `nix/flake.nix`, used only as `path:`.** `scripts/dev` caches the
  env per flake hash in `~/.cache/fractal-devshell/` with a GC root (details in its
  header). Bare fallback: `nix develop path:./nix -c <cmd>` (exactly `path:`: `.` or
  `./nix` copy the whole repo into the store). Plain `nix develop -c` fails on purpose
  (root stub). Keep only `flake.nix` and `flake.lock` in `nix/`.
- **All tooling comes from `nix/flake.nix`.** If a tool or library is missing, **add
  it to the flake**. That is expected and sometimes required (Playwright, a
  native library, a CLI tool); don't download binaries into `~`, don't use
  `nix profile install`, and don't work around a missing dependency. Commit
  `nix/flake.lock` with it, and list the flake change in your report.
- Node packages for `viewer/` come from `npm install` inside the dev shell
  (project-local `node_modules/`), with the lockfile committed. Anything that
  needs native binaries (Playwright browsers) comes from the flake instead;
  see the comment in `nix/flake.nix`.

## Network

- Internet works (GitHub, Maven repos, Mojang, Fabric, npm), including Nix's
  own fetches.
- LAN (`192.168.0.0/16` etc.) and the tailnet (`100.64.0.0/10`) are **blocked by
  a per-user firewall**. This is intentional; don't try to work around it.
  Loopback and DNS are open.
- **One exception: SSH to the workers listed in the worker pool file**
  (`.claude/workers.conf`, see "Parallel work"), for running builds and tests
  there through `scripts/remote.sh` and `scripts/anywhere.sh`. Use it only for
  that; nothing else on the LAN or tailnet is reachable, and that's on
  purpose. There may be no workers on a host (no file): then everything runs
  locally. `scripts/remote.sh --workers` lists them and `--check` says whether
  one is reachable.
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
- **Render with Vulkan.** Minecraft's OpenGL path needs an sRGB GLX visual
  that Xvfb/Xvnc don't offer, so the game falls back to Vulkan (but an Xvfb
  started with the dev shell's `LD_LIBRARY_PATH` does offer one, and the game
  then takes OpenGL on llvmpipe; the client scripts start Xvfb with it unset,
  `docs/NOTES.md`). The dev shell
  pins the loader to Mesa's software driver (lavapipe) with `VK_DRIVER_FILES`;
  the headless client scripts switch it to the Intel iGPU (below).
- **Intel iGPU is the default for automated client runs** (John 2026-10-26;
  UHD 770, idle, the desktop runs on NVIDIA): `scripts/client-test.sh` (and so
  `test-all.sh`, `flythrough.sh`, `perf.sh`, the snapshot/heavy batches) and
  the vanilla client of `scripts/server-only-test.sh` pick the renderer with
  `scripts/vk-select.sh`: Mesa ANV with the sw present path under Xvfb, still
  under the heavy lock. If the Intel device isn't usable they log a `FALLBACK`
  line and use lavapipe; `FB_VK=lavapipe` forces lavapipe. Findings:
  `docs/research/spikes/gpu-intel.md`.
- **NVIDIA stays off limits** without asking (it's shared with the human's
  desktop); the scripts refuse `FB_VK=nvidia` and only ever list one ICD.
  Other ad-hoc runs (a bare `xvfb-run ... ./gradlew`, Xvnc) stay on the dev
  shell's lavapipe unless you source `scripts/vk-select.sh`. If the Intel
  iGPU or lavapipe is too slow for something, say so, or run it on a worker
  (the perf worker renders on its own GPU; `scripts/remote.sh`, see "Parallel work").
- **Automated runs:** `xvfb-run -a scripts/dev ./gradlew <task>`, under the
  heavy lock, or on a worker with `scripts/remote.sh` (see "Parallel
  work").
- **When the human wants to watch:** a VNC display on `:1`, localhost only:
  ```sh
  Xvnc :1 -localhost -geometry 1920x1080 -SecurityTypes None &
  DISPLAY=:1 scripts/dev ./gradlew :mod:runClient
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
- **EULA: standing approval (John, 2026-10-22, reaffirmed 2026-10-26: "I approve that eula=true request and all future ones").** Agents may write `eula=true` for any local **test** server this project runs (generated run dirs under `mod/build/run/`, e.g. `serverOnly/`, `perfServer/`, or a worktree's test dir), bound to 127.0.0.1 with `online-mode=false`, without asking. Not for a server reachable from outside this machine and not outside this project's test dirs.

## Build and test commands

*(Phase 0 fills these in as they come to exist; keep this section the single
source of truth, and update it when a command changes.)*

- Everything: `scripts/test-all.sh` (`--no-client` skips layer 5, the Minecraft
  client game tests; `--full` runs the full client layer and the district-order test, default is the quick client layer; the script enters the dev shell and takes the heavy lock
  itself, so don't wrap it in either). Per-step logs in `build/test-all/`; ends
  with a per-layer summary, screenshot and log paths; non-zero exit on failure.
- Planner core (pure, fast): `scripts/dev ./gradlew :planner-core:test` (the full run under a suite slot: `scripts/suite.sh scripts/dev ./gradlew :planner-core:test`; see "Parallel work"); tests tagged `@Tag("heavy")` (minutes each: the metropolis all-caches-at-1 case of `ApproachCacheTest`, all city presets of `SettlementDeterminismTest`) are excluded by default: `-PfbTests=heavy` runs only them, `-PfbTests=all` everything; `scripts/test-all.sh --planner-heavy` runs them as the `planner-heavy` step (about 80 min; not part of `--full`; the to-main run adds it: `--full --planner-heavy --perf`)
- Mod, incl. server game tests (`:mod:runGameTest`, wired into `check`): `scripts/dev ./gradlew :mod:build` (jar in `mod/build/libs/`, planner-core bundled as jar-in-jar); server tests alone: `scripts/dev ./gradlew :mod:runGameTest`
- District-order test (B2; dedicated servers, no renderer, not heavy, ~10 min): `scripts/order-test.sh [--jobs N] [--only label,..]` (one fresh world per run label in parallel; each run picks a free port from `FB_ORDER_PORT`+idx up and retries on a bind failure with a `PORT RETRY` line (ORDERPORT); default `--jobs` from memory (cgroup `memory.max`, else MemAvailable, `FB_ORDER_RUN_MB` per run, max 6; an explicit `--jobs` over budget warns), 2 in a perf worker job (it reads the job's own memory cap, so a worker with a smaller cap gets fewer); outputs in `build/order-test/` with `STATUS`; exit 0 all PASS, 1 a FAIL verdict, 2 bad args or build failed, **4 = ORDER TEST DID NOT COMPLETE** (a run got no verdict: killed, OOM, timeout; not a test result, rerun) ; each run goes through `scripts/order-run-bounded.sh`: `FB_ORDER_TIMEOUT` (default 3000 s) bounds a run with no verdict, and a server still alive `FB_ORDER_GRACE` s (default 120) after its verdict logs `SHUTDOWN SLOW`, saves `build/order-test/<label>.hang-jstack.<pid>` and has only its own process group killed, still counted by its verdict; a worker job killed over its memory cap makes `scripts/remote.sh` print `JOB OOM-KILLED` and exit 137; copies `mod/build/run/order/eula.txt` (worktree, else main checkout) into each run dir and, when there is none, writes `eula=true` itself under the standing EULA approval above (local 127.0.0.1, online-mode=false servers, which it checks), so `scripts/remote.sh` copies work; pin with `scripts/evidence.sh`)
- Client test layers: `FB_LAYER=quick` (default of `scripts/client-test.sh` and `test-all.sh`: Hello, District B1, Gameplay B5, Terrain B3, TeleportHold) or `FB_LAYER=full` (adds Roads, Foliage, PortalHold, Compat; `test-all.sh --full`, heavy batches and the to-main run use it plus the order test); `FB_ONLY=<TestClass>` runs one client test (e.g. `FB_ONLY=RoadsClientGameTest`); fly-through and perf stay opt-in
- Client game tests (heavy, exits on its own): `scripts/client-test.sh [gradle args]` (enters the dev shell and takes the heavy lock itself; runs `:mod:runClientGameTest` under `xvfb-run` on the Intel iGPU, lavapipe fallback; the bare `xvfb-run -a scripts/dev ./gradlew :mod:runClientGameTest` is also safe (on lavapipe), since the task itself drops `WAYLAND_DISPLAY` and sets `SDL_VIDEODRIVER=x11`); screenshots are copied to `mod/build/screenshots/` (e.g. `hello-hut.png`; the raw ones are in `mod/build/run/clientGameTest/screenshots/`), log in `mod/build/run/clientGameTest/logs/latest.log`
- Client test renderer: `FB_VK=intel` (default: the Intel iGPU, with an automatic lavapipe fallback and a `FALLBACK` line in `build/client-test/gradle.log`) or `FB_VK=lavapipe`; nvidia refused
- Client test watchdog (prefer the script over the bare command): `scripts/client-test.sh` runs Gradle `--no-daemon` in its own process group, kills that group if the client is still running 60 s after a crash marker ("Minecraft has crashed", "#@!@# Game crashed!", a test-thread exception; exit 1) or after `FB_CLIENT_TIMEOUT` seconds (default 3600; exit 124), and always releases the heavy lock; it also checks the client's "Using graphics backend/device" lines against the selected renderer (`fb_vk_backend_check` in `scripts/vk-select.sh`): a mismatch logs `client-test: BACKEND MISMATCH` and turns a pass into exit 3 (`FB_BACKEND_CHECK=warn` only warns, `=off` skips); Gradle output in `build/client-test/gradle.log`; `scripts/test-all.sh` uses it
- Viewer (`viewer/`: Vite + Vitest + strict TS; `npm test` type-checks then runs headless): `cd viewer && ../scripts/dev npm ci` once, then `../scripts/dev npm test`, `../scripts/dev npm run build`, `../scripts/dev npm run dev` (dev server); how-to for humans (URLs, controls, env vars): `docs/VIEWER.md`; gallery: `../scripts/dev npm run gallery` (one gallery per `viewer/gallery-presets/<name>.json`: generates each with the CLI batch (or, for `"kind": "dump"`, the core fixture dump) into `viewer/gallery-data/gen/<name>/`, builds into `dist-gallery/`, captures one PNG per preset x camera to `viewer/out/gallery/<name>-<camera>.png`, cameras front-left, back-right, top, front and optionally cutaway; `FB_PRESETS=a,b` limits the presets, `npm run gen -- a b` regenerates only those; `FB_PORT` picks the preview port; `npm run dev:gen` serves the same data in the dev server)
- CLI: `scripts/dev ./gradlew :cli:installDist` then `cli/build/install/fractal-cli/bin/fractal-cli <gen|floorplan|batch|check|bench|diversity|region|roads|city|city-check|sample-city> ...` (inside the dev shell; no args prints usage, bad input exits 2; generator v1 by default, `--gen v0` for Phase 3 output, `--type T` picks the building type (`check`/`bench`: `--types`), style `auto` picks it from the biome), e.g. `gen --seed 1 --lot 16x12 --style medieval --terrain plane:64:1:0:4 --check --out out.json`, `batch --seeds 0..15 --lot 16x12 --style medieval --out-dir viewer/gallery-data/gen/medieval` (seed-N.json plus index.json for the viewer), `floorplan --seed 5 --lot 20x16 --style medieval --type house [--wide]` or `floorplan --in scene.json [--building ID]` (a building's storeys as ASCII grids with legend, room table, openings, furniture and circulation, for text-reading reviewers; `Floorplan` in `docs/VIEWER.md`), `city --seed 12345 --terrain recorded:fixtures/terrain/w12345-river-s8.json.gz --site -900,-3500,town,300 --out-dir viewer/fixtures/settlement/skeleton/river-town` (a settlement skeleton: `settlement.json` + `terrain.json.gz`; `--cell CX,CZ` instead of `--site` plans the settlement a region cell belongs to through the join rule; fixtures in `viewer/fixtures/settlement/skeleton/README.md`; `--districts` also plans every district: `districts/<id>.json` with briefs filled and lot counts in `settlement.json`, `--scenes all|none|auto:N|ID,ID` adds exterior district scenes (`--scene` is the old spelling; `--interiors` keeps interiors; `--check` prints every violation); fixtures in `viewer/fixtures/settlement/districts/README.md`), `roads --seed S --terrain recorded:FILE [--out FILE]` (the road graph and every route of ADR 0004 over a recorded world: a region dump with a `roads` layer for `region.html`, and a report on stdout: linked share, edge lengths, crossings, per-route cost; `city --cell ... --roads` plans a settlement with required exits and approaches toward its links), `city-check --seeds A..B [--terrain T,..] [--sites cells|random:N|at:X,Z,TIER,RADIUS|gates:FILE] [--scenes auto:N] [--door-rise] [--ignore RULE,..] [--out FILE.md] [--fill-json FILE]` (plans every district of each settlement, builds scenes for N of them, runs every settlement and building rule, markdown report; a `## Fill` section (DS-47 metrics, METROFILL M0; warnings `core-fill`, `road-sliver`, `road-hierarchy`) is always written and `--fill-json` saves its numbers for before/after diffs; `--door-rise` also builds them with interiors and adds the `## Door rise (DS-45)` table, about 2.5x slower; exit 1 on a violation not ignored; e.g. `city-check --seeds 1 --sites gates:fixtures/terrain/gate-sites.tsv --scenes auto:2`), `sample-city --out-dir viewer/fixtures/settlement/sample` (the sample settlement plan, its districts and one district scene; `--big N` writes a synthetic N x N grid for viewer performance tests, put it under `viewer/out/`; regenerate the committed fixture with it when `SampleSettlementTest` says it is stale), `check --seeds 0..999` (exit 1 on any violation), `bench --n 1000`; or `:cli:run --args="..."`; city gallery (D0): `scripts/dev npm run city` plans every `viewer/city-presets/<name>.json` (one per T2 gate window, plus the T1 river-town, mixed-city and a synthetic city, and a hamlet and a village on each of the T1 windows (D1)) into `viewer/out/city/<name>/` (`npm run city -- a b` only those), and `npm run city-gallery` also builds the viewer and captures `plan.png` plus a 3D overview and street frame of two district scenes per city next to the data (`FB_CITIES=a,b` limits it; not part of `scripts/test-all.sh`)
- Server-only install test (heavy; takes the heavy lock itself): `scripts/server-only-test.sh [--seed N]` runs a dedicated server WITH the mod (`:mod:runServerOnly`, dir `mod/build/run/serverOnly/`, 127.0.0.1:25599, online-mode=false) and an UNMODDED client (`tools/vanilla-client`: Minecraft + Fabric Loader only, a standalone Gradle build) under Xvfb; teleports it to a settlement, screenshots, checks both logs; output in `build/server-only/` (`result.txt`, `screenshot*.png`, logs); findings in `docs/research/spikes/server-only.md`
- Gate playtest kit (C2): `scripts/playtest-kit.sh [--fly] [--seeds A,B,random] [--radius R] [--fly-from DIR]` writes `docs/review/phase6/gate-kit/` (per-seed settlement lists with `/tp` lines, road links, route, `map.svg`; `--fly` adds fly-through frames via `anywhere.sh`, ~1 h on the perf worker; ~40 s without); rerun with `--fly` on the release candidate; self-test `scripts/dev node scripts/playtest-kit.test.mjs`
- Playtest notes (FBNOTE, debug; off by default, always on in a dev run): in game `/fbnote <comment>` (client command; enable with `-Dfractalbuildings.debugNotes=true` or `debugNotes=true` in `config/fractalbuildings-client.properties`) writes `screenshots/<name>.png` plus `<name>.json` (position, seed, biome, targeted block, planner settlement/lot/building/room in singleplayer); `scripts/dev scripts/playtest-gallery.mjs <screenshots-dir> [--date D] [--out DIR] [--notes-only]` turns them into `docs/review/playtest-<date>/REVIEW.md` plus JPEGs; how-to `docs/PLAYTEST.md`; client test `FB_ONLY=FbNoteClientGameTest scripts/client-test.sh`
- Viewer screenshot (Playwright, software WebGL, writes `viewer/out/smoke.png`): `cd viewer && ../scripts/dev npm run screenshot`
- Test scope (which layers a change needs; git reads only, seconds): `scripts/test-scope.sh [<base branch>|A..B] [--explain] [--dirty]` (`<base>`: merge-base(base, HEAD)..HEAD of the checkout you run it in; `A..B`: what B changed since A, from any checkout) prints the required and advisory layers with their commands; mapping in `scripts/test-scope.tsv` (`--check-table` finds unmapped files); policy in `docs/TESTING.md`
- Performance budget (6.7, heavy, ~10 min): `scripts/perf.sh` (`--planner-only`, `--check-only`, `--world-mod-only` for a diagnostic mod-world run with per-start `[LANE-START]` lines; the world half runs on a renderer-free dedicated server, the mod world (`:mod:runPerfServer`) and a vanilla base world (`./gradlew -p tools/vanilla-server runPerfBase`: Minecraft + Fabric Loader + Fabric API and the harness only, run dir `mod/build/run/perfServerBase`; the run fails if its log mentions fractalbuildings), merged by `scripts/perf-merge.mjs`; writes `eula.txt` (`eula=true`) in both run dirs itself, under the standing EULA approval; `--client` runs the old in-client `PerfClientGameTest` form on lavapipe); budgets in `mod/perf-budget.json` (a ceiling is never raised to make a run pass: exceeding one is a bug, and a row gets `knownOver` (prints `PERF KNOWN OVER`, not a pass, does not fail unless 25 % worse than its recorded value) only with John's acceptance, given for 0.1.0 by his rule of 2026-11-02; provenance and the list in `docs/PERFORMANCE.md`); table in `mod/build/perf/summary.md`; a timing row skipped for load (`skip(load)`, loadavg >= 8) prints a `PERF SKIPPED (load)` line and is not a pass, and one with `loadSkipGuard` (first tile, region cold, /locate fresh) above that factor x its ceiling exits 5 (`PERF SKIPPED ROW FAR OVER CEILING`; `PERF_SKIP_FAR=F` overrides; self-test `node scripts/perf-check.test.mjs`); `scripts/test-all.sh --perf` runs it too
- Evidence and snapshots: `scripts/evidence.sh <label> <command...>` (runs it in the current worktree; pins commit, command, exit, loadavg and the logs/screenshots it wrote under `.claude/reports/<label>-evidence/`); `scripts/snapshot-test.sh <checkout> <rev> <label> '<cmd>' ['<cmd>' ...]` (runs each command through evidence.sh on a persistent, warm detached worktree `.claude/worktrees/<name>` (default `postmerge`; `--name heavy` for batched heavy runs) moved to `<rev>` under its own lock `.claude/snapshot-<name>.lock` (`.claude/postmerge.lock` for the default; never one of the machine-wide locks, and a name whose worktree is on a branch is refused), keeping ignored build output and `node_modules` (deleted when `viewer/package-lock.json` changes); `--cold` uses a fresh `.claude/worktrees/postmerge-<sha>` and removes it; for post-merge and batched heavy runs on a shared branch); `scripts/squash-merge.sh [--cleanup] [--wait] ...` (verifier's merge; usage in its header; `--cleanup` removes the feature worktree and branch right after the commit; post-merge tests run detached by default and it exits at once with a `POSTMERGE PENDING` line, `--wait` keeps them in the foreground); `scripts/postmerge-status.sh [slug|sha]` (PASS / FAIL / RUNNING / KILLED / DIED from the result files in `.claude/postmerge/`, exit 0 / 1 / 3; every start and outcome is also a line in `.claude/postmerge.log`); tests: `scripts/postmerge-selftest.sh`
- Door rise baseline (DS-45, SHOPLOW S0): `scripts/door-rise.sh [--scenes N] [--out-dir D]` measures every entrance door's rise on the 9 gate windows + river-town + hill-town (builds the CLI, ~25 s); tables in `docs/review/phase7/shoplow/baseline.md`
- REVIEW guard: `scripts/review-check.sh [--source worktree|index|<rev>] [--base <rev>]` exits 1 when REVIEW.md brings back an item or heading already in REVIEW-ARCHIVE.md (the union merge can resurrect them) or a second top-level item with the same bold title (a stale unticked copy; with `--base`, only one new against it); `scripts/squash-merge.sh` runs it (exit 7)
- Docs-feedback sync (coordinator): `scripts/docs-sync.sh [--run-branch B] [--checkout DIR]` (defaults: the checkout it runs in and its branch) commits John's edits in `.claude/worktrees/docs-feedback`, merges them into the run branch (merge lock, review-check, aborted on conflict), fast-forwards docs-feedback, restarts SilverBullet if down and reports new post-merge FAIL/KILLED/DIED; exit 0 with one line `docs-sync: nothing new`, 10 feedback merged (sha, files, stat), 11 `main` has commits the run branch lacks, 12 a new post-merge failure, 1 error or conflict (left clean); self-test `scripts/docs-sync-selftest.sh`
- Design block reference: `scripts/design-blocks.sh` regenerates `docs/design/blocks.md` and the table between `<!-- BLOCKS:BEGIN -->` and `<!-- BLOCKS:END -->` in `.claude/agents/*.md` from Minecraft's own `blocks.json` report and textures plus `docs/design/blocks-notes.md` (needs Loom's cached jars for the `minecraft_version` in `gradle.properties`; any mod build fetches them; enters the dev shell itself); `--check` writes nothing and exits 1 if stale (2 if the jars are missing); rerun after a Minecraft version bump or an edit to `blocks-notes.md`; `scripts/test-all.sh` runs the check
- Glossary: `scripts/glossary.sh` regenerates `docs/GLOSSARY.md` (one row per item code or run term) from the bold ROADMAP and archived item names, plans named after a code, work-log headings, `6.x` Polish backlog lines and the hand-kept `docs/glossary-extra.tsv` (CODE, meaning, link relative to `docs/`, status); sorted, deterministic, under a second; `--check` writes nothing and exits 1 if stale, `--stdout` prints it; `scripts/test-all.sh` runs the check and reports `stale` without failing
- Whole Gradle build (all three modules + boundary check): `scripts/dev ./gradlew build`
- Boundary check alone: `scripts/dev ./gradlew :planner-core:checkNoMinecraft` (also part of `check` and run before `compileJava`)
- **Any of the above on a worker:** `scripts/remote.sh [--worker W] -- <command>` (W: a worker name or role from the worker pool file; default the perf worker), the command exactly as you'd run it from the worktree root but without a `flock .../heavy.lock` prefix (e.g. `scripts/remote.sh -- scripts/test-all.sh`, `scripts/remote.sh -- scripts/client-test.sh`, `scripts/remote.sh -- scripts/dev ./gradlew :planner-core:test`). It copies the worktree (no `.git`, nothing gitignored), queues the command, prints the last 40 lines and saves the full log to `build/remote/<job>.log`, copies results back to the same paths (`build/test-all/`, JUnit XML, `mod/build/screenshots/`, client test logs, `viewer/out/`; `--fetch PATH` for more) and exits with the command's status (137, with a `JOB OOM-KILLED` line, when the job went over the worker's per-job memory cap, its `memory` in the pool file). Output written elsewhere doesn't come back unless you `--fetch` it. Each job has a state file `build/remote/<job>.state` (id, command, fetch paths, commit, status) and its copy on the worker is kept until the results are back, so a job whose waiter died (SIGKILL, or SIGHUP when a tool call or session ends; SIGINT/SIGTERM still cancel it) runs on: `scripts/remote.sh --list` shows this worktree's unfetched jobs, `--attach <job>` / `--attach-last` waits if needed, fetches, writes the log and exits with the job's status, and `scripts/evidence.sh <label> scripts/remote.sh --attach <job>` pins evidence for it; copies older than 3 days (`FB_REMOTE_KEEP_DAYS`) are deleted on the next call. `scripts/remote.sh --check` exits 0 if the worker answers (fast check, 255 if not), `--workers` lists the pool; a job lost to a preempted worker exits 75 (`JOB LOST`, rerun it; "Workers may be preempted" below); a worker that doesn't answer gets nothing sent (255); with no pool file it exits 2 and says where the file goes; `--help` for the rest.
- **Any host, whichever frees first (preferred for full suites and client runs):** `scripts/anywhere.sh -- <command>` (same command form as `remote.sh`; e.g. `scripts/evidence.sh <slug> scripts/anywhere.sh -- scripts/test-all.sh --no-client`) waits here in a FIFO queue (`.claude/anywhere/`), not in a worker's queue, and starts the command locally when what it needs is free (a suite slot, the heavy lock, 1-min load < 18) or through `scripts/remote.sh` on the first worker of the pool with a free slot (fewer jobs running or queued than its `slots`; `--depth N` overrides, so a job may run beside others; at most its `heavy_slots` of them heavy (client runs, full test-all, the order test; default all slots), counted from anywhere's own claims, while light and suite jobs fill the rest; a timing run (perf.sh or profile.sh, also under evidence.sh, or `test-all.sh --perf`) goes only to a non-preemptible worker with the perf role and only when it is empty, prefers it, fills all its slots while it runs, and reserves it while it waits for it (PERFROUTE); use `--only perf` for a measurement, since a free local heavy lock still takes it; perf.sh judges the window either way), whichever comes first; logs one `anywhere: -> HOST (why)` line (also in the evidence); `--prefer`/`--only` take `local`, a worker name, a role or a comma list (the old `--only <ssh host>` still works, resolved through the pool file), `--fetch`, `--cpu`, `--wait S`; `--status` shows the queue, this host and every worker; a worker that doesn't answer the fast probe (suspended, asleep, down) is skipped; a `JOB LOST` (75) from remote.sh is passed through, never rerun or re-routed by itself; `--dry-run` says where it would go now; FB_VK, interactive runs and a subdirectory cwd stay local; tests: `scripts/anywhere-selftest.sh`

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
  **A timing taken on a busy machine is not a measurement** (John
  2026-10-30): don't report it as a result, in a log, brief, review or
  PERFORMANCE entry; measure again on a quiet host. **Quiet means no other
  job or agent work on the host** for the whole timed run (not a loadavg
  threshold: the measured job's own threads raise the load, 4 to 6 on the
  perf worker under a perf run). **Measure on the perf worker** (the worker
  with the `perf` role in the pool file: the benchmark machine all reported
  timings come from), sent when its queue (`scripts/anywhere.sh --status`)
  shows nothing else running or queued (`scripts/anywhere.sh --only perf`
  does this). It runs several jobs at once, so there quiet means **its queue
  empty and no job running besides ours for the whole timed window** (one
  that starts and ends inside it counts).
  This host
  is not quiet (John runs his own Minecraft client here, and other agents
  work all day): don't wait for local quiet. `scripts/quiet-check.sh`
  checks it (no suite slot, heavy lock or `scripts/anywhere.sh` job held by
  anyone else, no other job on the worker it runs in, processes that aren't
  the run's own under half a core; exit 0 quiet, 1 busy; `--worker perf`
  asks the perf worker from here); `--window-start DIR` / `--window-end DIR`
  judge a whole window (the worker's job history plus a lock and CPU sampler
  every 10 s), and
  `scripts/perf.sh` watches its whole run that way, writes it to
  `mod/build/perf/quiet.txt` and ends with `perf: quiet host` or `perf: NOT
  A MEASUREMENT (busy host: <what overlapped>)` (self-test
  `scripts/quiet-check-selftest.sh`). Record host, renderer and the loadavg sampled just before
  the run next to every number, and compare like with like (mod against the
  vanilla base on the same host; never the perf worker against local). The one
  exception: a speed-up so large it shows despite the load may be reported,
  labelled "busy host, speed-up only", never as the new number.
- **Full fast suites take a suite slot**: at most `FB_SUITE_SLOTS` (default 2)
  full fast test suites run at once machine-wide, since several overlapping
  planner-core / mod test runs (each forking and multi-threaded) thrash the
  20 cores. `scripts/test-all.sh` takes a slot for its fast layers itself
  (and so do post-merge runs, which run it); run full `:planner-core:test`,
  `:mod:build`, `:mod:runGameTest` or `build` as `scripts/suite.sh <cmd>` (e.g.
  `scripts/suite.sh scripts/dev ./gradlew :planner-core:test`). It prints
  "waiting for suite slot" with the holders and waits. Single-class `--tests X`
  runs, CLI runs and heavy jobs (heavy lock) don't need it. Locks:
  `<repo root>/.claude/suite.lock.1..N`, freed when the holder exits or is killed.
- **Workers are more heavy hosts.** The worker pool is a gitignored file,
  `<repo root>/.claude/workers.conf` in the main checkout (format in
  `scripts/lib/workers.sh`; start from `scripts/workers.conf.example`), one
  entry per machine: a symbolic name, its ssh host, roles, job slots, how many of them may be heavy, memory
  cap per job, renderer and specs. The docs never name a host; they say
  **the perf worker** (the worker with the `perf` role: the benchmark
  machine, where quiet measurements are taken) and **a worker** (any enabled
  entry, e.g. one with roles `heavy suite`). `scripts/remote.sh --workers`
  prints the pool. **No file means no workers**: everything runs on this
  host, and only commands that name a worker fail (exit 2, pointing to the
  example). Each worker runs `slots` jobs at once from its own queue
  (task-spooler behind a `job` command, `[run=N/S]` in `job status`), so it
  doesn't use the heavy lock, and a busy local heavy lock doesn't block it.
  When `scripts/remote.sh --check` succeeds, send heavy jobs and long runs
  there with `scripts/remote.sh [--worker W] -- <cmd>`: client game tests,
  `scripts/test-all.sh`, the ~10 min `:planner-core:test`, galleries. Run
  quick builds and tests locally as before; copying the worktree over costs
  a few seconds. If the check fails, use the local heavy lock as above.
  - **Prefer `scripts/anywhere.sh -- <cmd>` for full suites and client
    runs** over picking a host yourself: it holds the job locally and starts
    it on whichever host frees first (the local suite slot or heavy lock, or
    a worker with a free slot), so every machine stays busy and nothing gets
    stuck in a worker's queue, which can't drop a queued job. Run it in the background
    (it can wait a long time); stop it with SIGTERM, never SIGKILL.
  - **Quiet switch:** `scripts/anywhere.sh --local-max N [--until HH:MM]` caps this host at N anywhere-routed suite/heavy/full jobs at once (also counting suite slots and the heavy lock held outside anywhere; N=0: all of them go to workers), `--local-max off` lifts it, `--status` shows it; light and local-only jobs (FB_VK, snapshot-test.sh, squash-merge.sh) are not held back, and it only gates jobs started through anywhere.sh, so while it is on, send full suites and client runs through it.
  - **A worker with a GPU renders on it**, not lavapipe: there's no desktop
    on it to share the GPU with. `scripts/remote.sh` runs the command in a
    headless sway session and the test scripts use its Xwayland display
    instead of Xvfb (`FB_USE_DISPLAY=1`), so Minecraft gets hardware OpenGL
    (the perf worker's GPU is in the pool file's `renderer` line, and the log
    names it: "Using graphics device: ..."). A worker without a GPU (no
    `/dev/dri`) falls back to Xvfb + lavapipe by itself. Screenshots can
    differ slightly from lavapipe ones. If a run fails in a way that looks
    like a GPU or display problem, retry with `scripts/remote.sh --cpu`
    (Xvfb + lavapipe, as locally) and add a line to `docs/NOTES.md`.
  - A worker is **not quiet by nature**: up to `slots` jobs share it (each
    under its memory cap, CPUWeight 50), so a measurement there counts only when nothing else ran
    beside it for the whole window (`scripts/perf.sh` checks; another job
    can start right after yours is sent). And
    **timings from different hosts or renderers aren't comparable**: record
    the host (the worker's name from the pool file, or this one) and renderer with every number, and
    compare like with like.
  - **Workers may be preempted.** They are not dedicated servers: a worker
    marked `preemptible = yes` in the pool file (a gaming PC, or a VM paused
    when a game starts) can be suspended, have its memory shrunk or reboot at
    any time, usually without warning; even an always-up one may be moved and
    be briefly down. The scripts cope: `scripts/anywhere.sh` probes each
    worker quickly (ssh ConnectTimeout 5 + `job status`, a "down" answer
    cached 60 s) and skips one that doesn't answer (`--status` shows it);
    `scripts/remote.sh` won't send to one that doesn't answer (exit 255,
    "does not answer"); a waiter rides out an unreachable worker with backoff
    for up to 6 h (`FB_REMOTE_UNREACHABLE_MAX`), since the job stays queued or
    paused there and resumes with it; `--attach` does the same. A job already
    sent is never re-routed automatically.
  - **JOB LOST (exit 75) is a rerun, not a result.** `scripts/remote.sh`
    prints `JOB LOST (worker preempted)` and exits 75 when the job vanished
    (the queue forgot it and it left no exit code) or failed after the worker
    rebooted, lost memory, was suspended (the runner logs wall-clock jumps), or
    was killed while the worker was unreachable; `scripts/anywhere.sh` passes
    75 through and says so. Like the order test's exit 4, it is not a test
    failure and not a flake: don't report it as either, don't record it in
    NOTES as a flake; rerun the command (through `scripts/anywhere.sh`, which
    routes it afresh) and mention the rerun in your report. An OOM kill with
    no sign of preemption stays `JOB OOM-KILLED` (137). A passing job that
    saw a suspend prints `WORKER SUSPENDED`: its timings are not
    measurements.
  - **Measurements need a non-preemptible, quiet perf worker.** The perf role
    is ignored on a preemptible worker, so timing runs never go there; the
    quiet rule above still applies on the perf worker.
  - Interactive runs (`runClient` you or the human watch, VNC, the desktop
    launch) stay local.
  - In the integration checkout, a remote run still holds the merge lock like
    a local Gradle run (`docs/NOTES.md`): it copies results back into the
    checkout's `build/` directories.
  - Its output is the tail of the log, lines cut to 300 characters; the full
    log is in `build/remote/<job>.log`. Grep that rather than printing it.
  - **A few slots for everyone**, so jobs can still wait in a worker's queue
    for a long time (26 min on the perf worker's first busy morning, when it
    had one slot). Look at `scripts/anywhere.sh --status` before sending, run anything long detached (`setsid nohup scripts/remote.sh
    -- ... > log 2>&1 &`) so a tool timeout can't kill the wait (if it dies
    anyway, `scripts/remote.sh --list` and `--attach <job>` get the results
    back), and watch a
    running job with `ssh <host> 'timeout 5 job log <ID>'` (the host from
    `scripts/remote.sh --workers`). Timings and the shortest client run are
    in `docs/NOTES.md` ("perf worker").
- **Merges into a shared branch take the merge lock**: hold
  `flock <repo root>/.claude/merge.lock` for the merge itself (clean-checkout
  check, merge, commit) in one process, then release it. Two merges into the
  same checkout at once corrupt each other. Post-merge tests run **outside**
  that checkout, in the warm snapshot worktree `.claude/worktrees/postmerge`
  moved to the new commit under its own lock (`scripts/snapshot-test.sh`,
  which `scripts/squash-merge.sh` starts detached, so a tool time limit
  can't kill it; `scripts/postmerge-status.sh` reports it); any
  Gradle run inside a shared checkout still needs the merge lock.
- **Heavy runs leave pinned evidence**: run a heavy layer you'll report on
  (client game tests, `scripts/perf.sh`, `scripts/flythrough.sh`) through
  `scripts/evidence.sh <item-slug> <command>`. It records the commit, command,
  result and load, and copies the logs and screenshots to
  `.claude/reports/<item-slug>-evidence/<sha>-<time>/`, so a verifier can check
  them instead of re-running.
- **Flakes are first-class**: a test that fails and then passes on an identical
  rerun is a finding, not noise (a `JOB LOST`, exit 75, is neither: the worker
  was preempted; just rerun). Record it in `docs/NOTES.md` ("Tests and known
  flakes": test, symptom, commit, how often) and in your report; the
  coordinator makes it an item. Never rerun until green and say nothing.
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
- **Never run `./gradlew --stop` (or `gradle --stop`), and never kill a
  `GradleDaemon`.** `--stop` is not scoped to your worktree: it stops every
  Gradle daemon of the `agent` user, which fails other agents' running builds
  and kills other projects' servers (it happened twice, `docs/NOTES.md`).
  Daemons are shared between worktrees and expire after 3 h idle; remove a
  worktree without stopping anything. In a throwaway worktree (bisect,
  mutation check) pass `--no-daemon` if you want nothing left behind.

## Working conventions

- Multi-item work runs through the agents in `.claude/agents/` (see README,
  "Long runs with agents"). The `coordinator` keeps several items in flight
  at once. **`coder` (Sonnet) does most of the implementation and `assistant`
  (Opus) most of the research, tooling, testing support and small proofs of
  concept**; `researcher` (Opus, high effort) takes research where depth
  matters (decompiled-source reading, deep-dives, the synthesis, ADR drafts)
  and is the high-effort fallback; `lead-coder` (Opus) is reserved for
  genuinely hard coding problems (plans for large or unclear items, diagnosis
  after a failed verification). The `verifier` reviews and merges;
  `architect` (exteriors, massing, floor layout, streets) and
  `interior-designer` (rooms, furniture) are read-only taste reviewers (Opus) that check generator
  changes against `docs/design/STANDARDS.md` and answer John's "does this look
  right" questions.
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
  exists. Generator work follows the `DS-n` rules in
  `docs/design/STANDARDS.md` and the style's brief in `docs/design/styles/`.
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
  `gradle.properties`, `nix/flake.nix`, `viewer/package.json` dependencies or
  `.envrc`, go ahead if it's needed, but list it in your summary. The human
  reviews those before running the code as themselves.
