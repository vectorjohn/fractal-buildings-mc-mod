# Notes: durable facts

Facts that later work depends on and that aren't obvious from the code:
Minecraft 26.x / Fabric / three.js API gotchas, test-harness quirks, known
flakes, baseline numbers. **One bullet per line** (this file is union-merged
across parallel branches; see CLAUDE.md "Parallel work"). Add to the end of the
right section, with the item id and date. Fix or remove a line when it stops
being true.

## Minecraft and Fabric APIs
- 0.1a (2026-10-08): Target MC 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18.3 (plugin id `net.fabricmc.fabric-loom`, unobfuscated: no mappings, no `remapJar`), Gradle 9.7.1, JDK 25; all pinned in `gradle.properties`. MC identifiers are `net.minecraft.resources.Identifier` (not ResourceLocation).

## Planner core
- 0.1a (2026-10-08): `:planner-core:checkNoMinecraft` fails the build if any source mentions net.minecraft/net.fabricmc/com.mojang or any config declares/resolves such a dependency; it runs before `compileJava` and in `check`.

## Viewer and Playwright

## Environment and tooling

## Tests and known flakes

## Baselines (timings, sizes)
- Viewer: `npm` inside `nix develop` run from `viewer/` works (flake found by searching upward; the "does not contain a flake.nix" line is just info). `npm test` includes the tsc type-check.
