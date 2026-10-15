# Notes: durable facts

Facts that later work depends on and that aren't obvious from the code:
Minecraft 26.x / Fabric / three.js API gotchas, test-harness quirks, known
flakes, baseline numbers. **One bullet per line** (this file is union-merged
across parallel branches; see CLAUDE.md "Parallel work"). Add to the end of the
right section, with the item id and date. Fix or remove a line when it stops
being true.

## Minecraft and Fabric APIs
- 0.1a (2026-10-08): Target MC 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18.3 (plugin id `net.fabricmc.fabric-loom`, unobfuscated: no mappings, no `remapJar`), Gradle 9.7.1, JDK 25; all pinned in `gradle.properties`. MC identifiers are `net.minecraft.resources.Identifier` (not ResourceLocation).
- 1.4 (2026-10-08): Decompiled MC sources: `nix develop -c ./gradlew :mod:genSources` (about 20 s with a warm cache); the jar lands in the worktree's `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/*-sources.jar`, not in `~/.gradle`.
- 1.4 (2026-10-08): 26.3 chunk statuses: empty, structure_starts, structure_references, biomes, terrain (noise+surface+carvers merged), features, initialize_light, light, spawn, full (`ChunkPyramid.GENERATION_PYRAMID`).
- 1.4 (2026-10-08): Structure blocks more than 8 chunks from the start chunk are silently never placed (`createReferences` scans ±8 against the start's bounding box, +12 if terrain_adaptation≠none); only jigsaw validates this (horizontal ≤128, ≤116 with adaptation).
- 1.4 (2026-10-08): One `StructureStart` per `Structure` per chunk (`ChunkAccess.structureStarts` is a Map keyed by Structure).
- 1.4 (2026-10-08): `BiomeSource.getNoiseBiome` is gone in 26.x: use `biomeSource.createResolver(climateSampler).getNoiseBiome(qx,qy,qz)` or `createResolverForChunk(...)` for batched volumes.
- 1.4 (2026-10-08): Biome lookups are order-dependent at exact climate ties (`Climate.RTree` thread-local `lastResult`, about 0.05% of cells); for deterministic results, run a fixed non-tie priming lookup before each query (probe: 0 diffs across orders and threads).
- 1.4 (2026-10-08): `ChunkGenerator.getBaseHeight(x,z,type,heightAccessor,randomState)` is pure and thread-safe; it costs about 0.2 ms per column on vanilla overworld (full-column density scan), includes aquifer water, and excludes beardifier, carvers and features.
- 1.4 (2026-10-08): The `postProcess` random for structure pieces is seeded by the structure's index in the registry within its generation step, so other mods' structures change it; derive piece randomness from our own hash.
- 1.4 (2026-10-08): `/locate` only handles `RandomSpreadStructurePlacement` and `ConcentricRingsStructurePlacement` (instanceof); a custom placement must subclass one of them to stay locatable.
- 1.4 (2026-10-08): `NoiseBasedChunkGenerator` is final; Fabric API has no overworld biome injection (only `NetherBiomes`/`TheEndBiomes`) and no structure API; surface rules are now `material_rule` / `material_condition`.
- 1.4 (2026-10-08): Measured in game: a structure piece reaching 20 chunks east of its start chunk placed blocks only in chunks dx 0–8 (silent clip confirmed).
- 1.4 (2026-10-08): Measured in game: structure-start piece building runs with max concurrency 1 per dimension (serial `worldgen` lane, hopping across Worker-Main threads); a 20 ms build per start adds ~20 ms wall time each.
- 1.4 (2026-10-08): `getBaseHeight(OCEAN_FLOOR_WG)` matches real generated ground in 98.2% of land columns (p99 |err| 2; seed 12345, 576 chunks); `preliminary_surface_level` is ~12–15 blocks below ground (p90 |err| 17) but 50× cheaper (0.0085 ms/col).
- 1.4 (2026-10-08): Any generator that isn't `NoiseBasedChunkGenerator` gets a level `RandomState` built from `NoiseRouterData.none()` (`ChunkMap.<init>`), which breaks `/locate`, biome lookups and structure climate sampling unless mixed in.
- 1.4 (2026-10-08): Overworld surface rules only run where `blockY >= floor(preliminary_surface_level) + surfaceDepth - 8` (`above_preliminary_surface`); a terrain cut deeper than that is bare stone unless `preliminary_surface_level` is wrapped too.
- 1.4 (2026-10-08): Client game test with a normal world: `context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed("..."))`; run server code with `world.getServer().computeOnServer(server -> ...)`; `level.getChunk(x,z)` there generates to FULL (576 chunks ≈ 13 s under lavapipe client).
- 5.x deep-dive (2026-10-15): In 26.3 `preliminary_surface_level` is not a noise-router slot; it is a registry DF read only by the `chunk_surface_level` entry (router slot, drives material rules) and `Aquifer.Config.surface_level`. Wrap those two slots plus `final_density`, not the shared entry, or the coarse height screen gets wrapped too.
- 5.x deep-dive (2026-10-15): 26.3 `RandomState.getSampler(df)` / `samplersWithContext(ctx).sampleValue(df, x, y, z)` compiles any `DensityFunction` on demand (locked compile, `ConcurrentHashMap` cache keyed by the function), so a captured pre-wrap `final_density` can still be sampled; `getBaseHeight` itself always reads `settings.noiseRouter().finalDensity()`.
- 5.x deep-dive (2026-10-15): Lithostitched (2.0.6, 26.3) wraps the router by replacing it inside the shared `NoiseGeneratorSettings` in `ChunkMap.<init>`, so `getBaseHeight` and structures see wrapped terrain; `wrap_aquifers` exists only from 2.0.6, and its `JigsawStructureMixin` reroutes every jigsaw structure through its own assembler.

## Planner core
- 0.1a (2026-10-08): `:planner-core:checkNoMinecraft` fails the build if any source mentions net.minecraft/net.fabricmc/com.mojang or any config declares/resolves such a dependency; it runs before `compileJava` and in `check`.

## Viewer and Playwright
- Playwright pin (0.4): browsers come from nixpkgs `playwright-driver.browsers` (`PLAYWRIGHT_BROWSERS_PATH` in the flake); npm `@playwright/test` is pinned **exactly** (`--save-exact`) to `pkgs.playwright-driver.version` of the *locked* nixpkgs, currently 1.63.0 (check with `nix eval --raw --impure --expr "(import (builtins.getFlake (toString ./.)).inputs.nixpkgs {}).playwright-driver.version"`; `nix eval nixpkgs#...` uses the registry and can differ). Bump both together.
- Headless Chromium WebGL (0.4): works via `--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader`, **but only with `DISPLAY` unset**: a set-but-dead `DISPLAY` makes ANGLE try xcb and WebGL silently fails ("A WebGL context could not be created"). `playwright.config.ts` strips `DISPLAY` from the browser env.
- Viewer smoke screenshot: `cd viewer && nix develop -c npm run screenshot` (type-check, vite build, `vite preview`, Playwright) writes `viewer/out/smoke.png` (gitignored); ~1.5 s after the build. The viewer sets `window.__viewerReady` when the IR is drawn; `?ir=<url>` loads another IR file.
- IR smoke fixture `viewer/fixtures/box5.json` is generated by the CLI; `IrJsonDeterminismTest.viewerFixtureMatchesCliOutput` (Java) fails when it is stale, `voxels.test.ts` (TS) parses it. Regenerate: `./gradlew :cli:run --args="gen --seed 1 --lot 20x20 --style medieval --out $PWD/viewer/fixtures/box5.json"`.

## Environment and tooling

## Tests and known flakes

## Baselines (timings, sizes)
- Viewer: `npm` inside `nix develop` run from `viewer/` works (flake found by searching upward; the "does not contain a flake.nix" line is just info). `npm test` includes the tsc type-check.
- Game tests (26.3, Fabric API 0.162): Loom `fabricApi { configureTests { createSourceSet = true; modId = ...; enableGameTests/enableClientGameTests = true } }` creates a `gametest` source set (own `fabric.mod.json` with `fabric-gametest` / `fabric-client-gametest` entrypoints; entrypoint class needs a no-arg constructor, tests are `@GameTest public void x(GameTestHelper)`) and tasks `:mod:runGameTest` (server) and `:mod:runClientGameTest`. `check` depends on `runGameTest`, so `:mod:build` runs server tests. Run dirs: `mod/build/run/{gameTest,clientGameTest}/`.
- Client game test API: `context.worldBuilder().adjustSettings(c -> c.setSeed("..")).create()` gives a flat world by default (grass y=-61, air from -60); `world.getConnection().waitForChunksRender()`, `world.getServer().runCommand/runOnServer`, `context.takeScreenshot(name)` writes `mod/build/run/clientGameTest/screenshots/0000_<name>.png` (854x480). The test exits on its own.
- Server game test counts "2 tests" for our one `@GameTest` (the framework's own extra); a broken placement fails with "Expected block ... got ..." and a red build (mutation-checked).
- Baseline (0.5): `:mod:runClientGameTest` ~20 s wall (Xvfb + lavapipe, warm Loom cache), loadavg 5.9-7.0 at the time; `:mod:runGameTest` ~9 s; full `./gradlew build` ~10 s warm.
- Correction (0.5 r2): the second "game test" in the server run is vanilla's built-in `minecraft:always_pass` test instance, not ours. `eula = true` in `configureTests` is not needed for `runGameTest`/`runClientGameTest` (removed; we don't accept the EULA for John). Client screenshots are also copied to `mod/build/screenshots/<name>.png` by a `doLast` on `runClientGameTest`.
