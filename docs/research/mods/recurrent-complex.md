# Recurrent Complex (Ivorius)

**Track:** mods · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A Forge structure framework plus ~200 bundled structures (ruins, castles,
dungeons, mazes, villages, trees), all authored and tuned through a large
in-game GUI. [Source](https://github.com/Ivorforce/RecurrentComplex),
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/recurrent-complex).
Last real feature work 2017 (722 commits that year, 2 in 2018); now 1.12.2
only, with a burst of security fixes in Aug 2026. A closed-source
continuation, **Recurrent Complex Volts** (custom licence), ports it to
1.20.1/1.21.1/26.x.

## Approach

A structure (`.rcst`: block data + `structure.json`) carries **generation
types** (natural, static, maze component, list, vanilla-village, sapling),
**transformers** and **variables**. Placement plans a structure in one chunk,
records it (UUID, bounding box, seed, transform) in per-world saved data, and
fills the rest of its box **chunk by chunk** as neighbours are decorated.
Multi-piece buildings come from **world scripts** (marker blocks inside a
structure that spawn another structure, a list pick, or a maze).

## Procedural vs templated

Templates, but with the richest rule layer of any mod in this track:
- **Transformers**, two-phase (`prepareInstanceData` once, saved as NBT;
  `transform`/`skipGeneration` per block or per chunk): Natural (blend a
  foundation into terrain with noisy expansion), Ruins (decay with
  `minDecay`/`maxDecay`/chaos/density, erosion, gravity, vines, cobwebs),
  Replace (weighted block swap), Pillar (supports to the ground), Negative
  space, Ensure blocks, Village-specific, Property.
- **Variables**: named booleans (chance + biome/dimension condition) set by
  the parent and **inherited by child structures**, so "this castle is ruined"
  propagates to all its pieces; transformers are gated on them.
- **Maze generation**: room-grid components with typed exits, connected by a
  **bounded-backtracking** search (`reverses`) under rules: reachability
  between named exits, prevent-connection, AABB limit, blocked connectors.
  The maze core runs headless (no Minecraft) with tests and a benchmark.
- **Placer**: picks Y by scoring candidate heights with weighted factors
  (height limits, block-match ratio, surface rays), not a single heightmap.
- **Inventory generators** (`.rcig`): weighted item components for loot;
  also a poem generator for books.

## How it hooks into worldgen

1.12 chunk **decoration** (populate) events, not structure starts. Natural
selection uses biome/dimension **expressions** with weights, spawn-distance
rules and per-category frequency. **Determinism is partial**: per-structure
seeds derive from the decorate RNG (`chunkX*341873128712 + chunkZ*…+seed`),
but `limitations` and "blocking" entries are resolved against the saved-data
world state, so outcomes depend on generation order; the placer reads live
terrain. No fixed size cap (boxes are rasterised to chunks and completed
lazily, including chunks that already existed).

## Licence

Repo: **MIT** (`LICENSE`, 2022); the maze core files carry **Apache-2.0**
headers. Both permissive: we may study and reuse with attribution. Bundled
`.rcst` structures: not checked, assume not reusable. Volts: custom licence,
no public source; public pages only.

## What to borrow

- Two-phase transformer contract: plan once into serialisable instance data,
  then apply per chunk. Same split as our IR → adapter.
- Inherited boolean variables (ruined, wealthy, snowy) driving whole
  compounds; maps onto our style/condition parameters.
- Maze-of-rooms with reachability rules and bounded backtracking, kept pure
  and unit-tested: a template for interior/compound layout in `planner-core`.
- Score-based vertical placement over several factors.

## What to avoid

- Order-dependent rules (world-state limits, blocking registry): breaks our
  determinism rule.
- Generating during decoration with lazily completed boxes and saved data:
  fragile, and tied to a 1.12 hook that no longer exists.
- Why it faded (inferred, not stated by the author): GUI-heavy authoring on
  Forge 1.12 internals made every port a rewrite; 1.13's flattening and the
  1.16–1.18 data-driven structure system replaced its hooks; a single author.

## Verdict

The best prior art for rule-driven *variation* (transformers, inherited
variables, rule-constrained mazes); dead hooks, order-dependent; MIT, borrow
ideas and even code.

**Sources / what I couldn't verify:** Read `master` (1.4.8.8):
`Transformer`, `TransformerRuins`/`Natural` fields, `GenericVariableDomain`,
`WorldGenStructures`, `StructureGenerator`, `WorldStructureGenerationData`,
`GenericPlacer`, maze `ReachabilityStrategy`/`MazeComponentConnector`
headers; GitHub wiki (2 pages; the Fandom wiki not read); git history
counts; Volts via the Modrinth API. Not run. No author statement on why
development stopped found.
