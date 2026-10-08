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
| Decisions that shape the architecture (gate outcomes) | `docs/adr/NNNN-<slug>.md` | researcher drafts; the human approves |
| Research one-pagers, and their index with statuses | `docs/research/<track>/<slug>.md`, `docs/research/INDEX.md` | research agents; INDEX lines by the coordinator |
| Plans for large items | `docs/plans/<slug>.md` | lead-coder (`mode: plan`) |
| Things only the human can judge (looks, "would a person build this", in-game feel) | `docs/REVIEW.md` | anyone, append only; results by the human |
| Finished phases' full text, old material | `docs/archive/` | coordinator at a phase merge |

Agents read **slices** of the big docs (`grep -n` the item, then read that
range), not whole files. Keep every doc lean: the log lives in per-item files
precisely so nobody has to read history to find the current state.

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
  too slow for something, say so.
- **Automated runs:** `xvfb-run -a nix develop -c ./gradlew <task>`, under the
  heavy lock (see "Parallel work").
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

- Everything: `scripts/test-all.sh` (`--no-client` skips the Minecraft client
  game tests).
- Planner core (pure, fast): `nix develop -c ./gradlew :planner-core:test`
- Mod, incl. server game tests: `nix develop -c ./gradlew :mod:build`
- Client game tests (heavy): `flock <repo root>/.claude/heavy.lock xvfb-run -a nix develop -c ./gradlew :mod:runClientGameTest`
- Viewer: `cd viewer && nix develop -c npm test`; gallery: *(Phase 3)*
- CLI: *(Phase 0)*

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
