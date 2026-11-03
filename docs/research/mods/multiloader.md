---
tags: research
track: mods
date: "2026-11-03"
author: assistant
licence: various (see Licence)
---

# Multiloader: Fabric and NeoForge from one codebase

## What it is

A feasibility note for John's question (2026-11-03): what would it take for Fractal Buildings to run on NeoForge as well as Fabric? Our target is Minecraft 26.3 (`gradle.properties`: Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18.3). Desk research only: nothing was built or run.

## Approach (the options, for 26.3)

| Option | What it is | Supports 26.3? | Licence | What it costs us |
|---|---|---|---|---|
| **MultiLoader template** ([jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)) | `common` (compiled against vanilla) + `fabric` + `neoforge` subprojects, each with its own build plugin (Loom; ModDevGradle) | yes: it has a `26.3` branch | CC0-1.0 | a build split plus a small adapter per loader; no runtime dependency for players |
| **Architectury** (API + Loom) | the same split, plus an API that wraps events, registries and networking for both loaders | yes: API 22.x lists 26.3 for Fabric and NeoForge ([docs](https://docs.architectury.dev/api/porting/port-26.2-26.3)) | API LGPL-3.0, Loom MIT (from memory, not rechecked) | players must also install Architectury API, which we don't need, because our loader surface is small |
| **Stonecutter** ([plugin](https://plugins.gradle.org/plugin/dev.kikugie.stonecutter)) | a preprocessor for building **several Minecraft versions** from one tree; Stonecraft adds loaders on top of Architectury | yes (it doesn't depend on the Minecraft version) | not checked | solves a different problem (several MC versions); only worth it if we ever back-port |
| **Sinytra Connector** + Forgified Fabric API ([repo](https://github.com/Sinytra/Connector)) | a NeoForge mod that loads Fabric jars unchanged | **no**: the README lists 26.2.x and 26.1.x as supported; 26.3 isn't listed | MIT | nothing on our side; players install Connector and FFAPI, and we would only test it |
| NeoForge itself | the loader we'd target | 26.3.0.x builds are on the NeoForged maven (93 to 101 seen); 26.1.2 is the release line, so 26.3 is still beta | LGPL-2.1 | — |

The good news for 26.x: Minecraft is no longer obfuscated, and our build already compiles against Mojang names with plain `implementation` (no `modImplementation`, no remapping). So a `common` module compiled against vanilla needs no mapping layer, which used to be the hard part of multiloader builds. (That 26.x dropped obfuscation is my reading of our build files, not checked against Mojang's notes.)

## Our loader surface (inventory)

`planner-core/` and `cli/` have no loader code: `:planner-core:checkNoMinecraft` runs before `compileJava` and fails the build if Minecraft (and so Fabric) is on its classpath. They carry over as they are. Only `mod/` matters:

| Area | What we use now | NeoForge equivalent | Effort |
|---|---|---|---|
| Entrypoints | `fabric.mod.json`: `main` FractalBuildingsMod, `client` FbNoteClient | `neoforge.mods.toml` + `@Mod` class, client dist entry | easy |
| Fabric imports in main code | **14 of about 70 files** (6,968 lines): lifecycle events (ServerLifecycle, ServerLevel, ServerTick: 10 imports), commands (3), client commands (3), ClientTick, `FabricLoader` (6 calls: config dir, game dir, `isModLoaded`, dev env, mod container), `BiomeModifications` (1) | NeoForge event bus (`ServerStartingEvent`, `LevelEvent.Load`, `ServerTickEvent`, `RegisterCommandsEvent`, `RegisterClientCommandsEvent`), `FMLPaths`, `ModList` | easy |
| Road feature into every overworld biome | `BiomeModifications.addFeature` (RoadFeature) | a data-driven biome modifier JSON (`neoforge:add_features`) | easy |
| Registration | 5 direct `Registry.register` calls on `BuiltInRegistries` (2 structure types, 2 piece types, 1 structure placement) in ModWorldgen | NeoForge freezes those registries: `DeferredRegister` or `RegisterEvent` | medium (small, but load order changes) |
| Data | structures, structure sets, tags, placed feature, loot tables: all vanilla JSON under `data/` | the same files work | none |
| Mixins | 11, **all on vanilla classes** (ChunkGenerator x2, WorldGenRegion, PlacedFeature, three features, StructureCheck, ServerPlayer teleport, Entity portal, Cat/Patrol spawners), `defaultRequire: 1` | NeoForge loads Mixin too, but it patches vanilla, possibly some of these very methods (worldgen, teleport); an injector that no longer matches fails at startup | medium, risky (not checked) |
| Networking | none | — | none |
| Compat | TerraBlender priming, Lithostitched, Tectonic (test mods are fetched as `-fabric-26.3` jars) | NeoForge builds of the same mods exist for older versions; not checked for 26.3 | medium |
| Bundling | planner-core as jar-in-jar (`include project(':planner-core')`) | ModDevGradle `jarJar` | easy |
| Server game tests | Fabric gametest API v1 (`fabric-gametest` entrypoint), 13 files | vanilla's data-driven GameTest framework plus NeoForge test registration; our Gradle scan fills the Fabric entrypoint | hard |
| Client game tests | Fabric client gametest API (14 files, 42 imports): Hello, District, Gameplay, Terrain, holds, Roads, Foliage, Compat, FbNote, fly-through, perf | **no NeoForge equivalent** that I know of | hard |
| Harness runs | `loom.runs` for the order test and perf server (`fb.testHook`), `runServerOnly`, `runPerfServer`; 6 scripts call Loom task names (test-all, client-test, perf, server-only-test, suite, anywhere) | ModDevGradle run configs; scripts take a loader switch | medium |
| Vanilla tools | `tools/vanilla-client` and `tools/vanilla-server` run Fabric Loader without our mod | a NeoForge base for like-for-like perf; the vanilla client can stay as it is | medium |

## What would have to change

- **Easy (S, about 1 to 2 coder days):** move the 14 Fabric-touching files behind a small `Platform` interface (events, commands, config dir, `isModLoaded`); everything else (about 55 files of worldgen, probes, blocks, gameplay) moves to `common` unchanged. A `neoforge.mods.toml`, a biome-modifier JSON, `jarJar` for planner-core.
- **Medium (M, about 3 to 5 days):** the Gradle split into `common` / `fabric` / `neoforge` (the MultiLoader template's 26.3 branch as the model; it touches every build file, so John reviews it), registration through `RegisterEvent`, checking all 11 mixins against NeoForge's patched classes, a NeoForge server smoke test (boot, `/locate`, one district generated, log clean), the compat mods' NeoForge jars, and a loader switch in the scripts.
- **Hard (L, 2 weeks or more):** test parity. About 9,000 lines of game tests are written against Fabric's server and client gametest APIs, and the order, perf and fly-through harnesses run through Loom runs. Porting the client layer means writing our own client test driver on NeoForge. The cheap alternative: **Fabric stays the platform we test everything on**, and NeoForge gets a smoke layer (server boot plus a few server game tests ported to the vanilla framework, and one screenshot from a scripted client), which brings this group down to M.
- **Release:** two jars (`fractalbuildings-fabric-x.jar`, `-neoforge-x.jar`). Modrinth and CurseForge both take one version with files for several loaders (a publish plugin such as mod-publish-plugin can upload both; not checked for 26.3). It doubles the bug-report surface and the release checks.

**Overall:** **M** (about 1.5 to 2 weeks) for a NeoForge jar that is smoke-tested, with Fabric still the full test platform. **L** (a month or more) for equal testing on both loaders.

## Licence

Template CC0-1.0; Connector MIT; Architectury API LGPL-3.0 (from memory); NeoForge LGPL-2.1. We'd study the template and copy its build layout only; no code from any mod.

## What to borrow

- The MultiLoader template's layout: `common` compiled against vanilla, thin loader projects, no runtime dependency.
- A `Platform` service interface (ServiceLoader) for the few calls that differ between loaders.

## What to avoid

- Architectury API as a dependency players must install, just to wrap 14 files.
- Porting the client test layer before anyone asks for NeoForge parity.
- Counting on Connector: it doesn't cover 26.3 yet, and our mixins into vanilla worldgen are exactly the kind of thing NeoForge's patches can break under it.

## Verdict

**Default: ship 0.1.0 on Fabric only.** Make the code easy to port at little cost now (keep new Fabric calls inside the adapter files), and plan a MultiLoader split (template layout, Fabric stays the test platform, NeoForge gets a smoke layer) as a Phase 7+ item of size M. Once Connector supports our Minecraft version, a one-off "does it load under Connector" check is a cheap interim option, and we would say it is unsupported.

**Sources / what I couldn't verify:** our repo (`mod/build.gradle`, `fabric.mod.json`, `fractalbuildings.mixins.json`, greps of `mod/src`); [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (26.3 branch, CC0; I didn't read its build files, so I don't know which plugin versions it uses); [Architectury docs](https://docs.architectury.dev/api/porting/port-26.2-26.3) (a search summary, not read in full); [Connector README](https://github.com/Sinytra/Connector) (26.2/26.1 supported); [NeoForged maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) (26.3.0.93 to 101). Not checked: whether NeoForge patches the exact methods our mixins target, NeoForge builds of TerraBlender, Lithostitched and Tectonic for 26.3, Stonecutter's licence, the Architectury licences, and what NeoForge offers for client-side game tests. The effort estimates are mine, not measured.
