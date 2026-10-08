# Roadmap Phase 0: Scaffolding (archived 2026-10-08)

Full text as it stood when the phase finished. Summary in [../ROADMAP.md](../ROADMAP.md).


A short phase: set up the repo so research notes and spikes have a home, and
prove the toolchain end to end before agents fan out.

Parallel: 0.1 first (it creates the build). Then 0.2–0.3 (docs), 0.4, 0.5 and
0.6 side by side. Phase 1 tracks 1, 2, 3, 5 and 6 can start at once; track 4
waits for 0.5.

- [x] **0.1 Repo layout and build.** Gradle multi-project: `planner-core/`
  (pure Java, **no Minecraft on its classpath**: enforce it), `cli/`, `mod/`
  (Fabric Loom); `viewer/` (TypeScript + three.js, npm, lockfile committed);
  `docs/research/`, `docs/adr/`. Versions from fabricmc.net for Minecraft 26.3.
  Mod id and Java package recorded in `docs/ASSUMPTIONS.md`. Fill in CLAUDE.md
  "Build and test commands". Builds green inside `nix develop`.
  - 0.1b (viewer half): `viewer/` done: Vite 8 + Vitest 5 + TS 7 strict, three.js scene (orbit controls, instanced voxels), provisional `Ir` type and unit-tested `irToVoxels` stub. `npm ci && npm test && npm run build` green. No Playwright (0.4 adds it).
  - 0.1a (Gradle half, done): `planner-core/`, `cli/`, `mod/` build green with `./gradlew build`; boundary check `:planner-core:checkNoMinecraft` proven to fail on a bad import and on a Fabric dependency. `viewer/` is 0.1b.
- [x] **0.2 CLAUDE.md**: module boundaries, environment, parallel-work rules,
  review conventions. (Done at setup; 0.1 fills in the commands.)
- [x] **0.3 Templates and index.** `docs/research/TEMPLATE.md` (one-pager: what
  it is (link), approach in three sentences, procedural vs templated, how it
  hooks into worldgen, licence, what to borrow, what to avoid, verdict);
  `docs/adr/TEMPLATE.md` (context, options, decision, consequences);
  `docs/research/INDEX.md` (one line per note: name and link, one-line verdict,
  status `new` / `reviewed` / `deep-dive` / `drop`; grouped by track).
- [x] **0.4 Smoke path.** The CLI writes a hard-coded 5×5×5 box as IR JSON; the
  viewer renders it; one Playwright screenshot proves it (add Playwright to the
  flake, see the comment in `flake.nix`).
  Done: `fractal-cli gen ... [--out FILE]` writes provisional `irVersion: 0` JSON
  (floor/wall/roof boxes); `viewer/fixtures/box5.json` is the shared fixture;
  `cd viewer && npm run screenshot` writes `viewer/out/smoke.png`.
  **Tests:** `IrJsonDeterminismTest` (byte-identical output, fixture not stale), `voxels.test.ts`
  (parses the CLI fixture), `e2e/smoke.spec.ts` (canvas colour fractions).
- [x] **0.5 Mod hello world and Minecraft test harness.** The Fabric mod builds,
  one server game test and one client game test (Xvfb + lavapipe, screenshot)
  pass. This warms the shared Loom cache and proves the environment for track 4
  and Phase 2. (Added at setup.)
  *Tests:* `:mod:runGameTest` (server, part of `:mod:build`), `:mod:runClientGameTest` (screenshot copied to `mod/build/screenshots/hello-hut.png`).
- [x] **0.6 `scripts/test-all.sh`.** Runs every layer with a short summary
  (counts, screenshot and log paths); `--no-client` skips layer 5; takes the
  heavy lock for client tests itself. (Added at setup.)

**Gate:** builds green, and the viewer renders the box.

