---
tags: research
track: pool
date: "2026-10-16"
author: researcher
licence: proprietary
---

# Vanilla precedents: stronghold rings, the template cache, processors

## What it is

Three places where Minecraft 26.3 already precomputes or caches worldgen work,
read from the Loom-decompiled sources (Mojang names). Each is a small working
model of one part of the pool idea:

- **stronghold rings** are precomputed at load on worker threads, and worldgen
  joins the result;
- the **template manager** is a lazy memo of authored buildings;
- **structure processors** are a per-instance variation pass over a shared
  template.

## Approach (how it really works)

**Stronghold rings: precompute off-thread, join on demand.**

- When `ServerLevel` is constructed, it calls
  `chunkSource.getGeneratorState().ensureStructuresGenerated()`
  (`ServerLevel`, constructor).
- `ChunkGeneratorStructureState.generateRingPositions` then works for every
  `concentric_rings` placement:
  - on the calling thread, it draws each ring position's angle and distance
    from one `RandomSource` seeded with `concentricRingsSeed`;
  - for each position it takes `random.fork()` for the biome search;
  - it submits each biome search (`findBiomeHorizontal`, radius 112) as
    `CompletableFuture.supplyAsync` on
    `Util.backgroundExecutor().forName("structureRings")`;
  - `Util.sequence` combines the results in submission order.
- `getRingPositionsFor` calls `join()` on that future. A chunk that needs the
  rings before they are done simply waits, and gets the same answer.
- Nothing is saved. The rings are recomputed on every load.

**Template manager: lazy memo.**

- `StructureTemplateManager.structureRepository` is a
  `ConcurrentHashMap<Identifier, Optional<StructureTemplate>>`, filled by
  `computeIfAbsent(id, this::tryLoad)`. It is cleared on resource reload
  (`onResourceManagerReload`).
- Each `StructureTemplate.Palette` memoises derived data: the per-`Block`
  filtered block lists (`cache`, a plain `HashMap` with `computeIfAbsent`) and
  `cachedJigsaws`.
- Jigsaw pools are the authored "pool + choose" model: weighted elements,
  picked with the start's seeded random.

**Processors: a per-instance pass over a shared template.**

- `StructureTemplate.placeInWorld` runs `processBlockInfos` on every placement.
  Village and ruin processors (`BlockRotProcessor`, `RuleProcessor`) take
  their randomness from the position:
  - `StructurePlaceSettings.getRandom(pos)` is
    `RandomSource.create(Mth.getSeed(pos))`, or `Util.getMillis()` when `pos`
    is null;
  - `RuleProcessor` calls `RandomSource.create(Mth.getSeed(pos))` directly.
- `Mth.getSeed(x, y, z)` is `@Deprecated` and hashes **coordinates only, not
  the world seed**.

## Procedural vs templated

Authored templates, chosen from weighted pools, varied per instance by
processors (mossy and cracked swaps, block rot). This is the
"pool + per-instance detail" end state of John's idea, with the pool authored
rather than generated.

## How it hooks into worldgen

Rings: at level construction, on the background executor, joined from
placement checks. Templates: loaded on first use by jigsaw placement on the
worldgen lane. Processors: inside `postProcess` for each template piece.

## Licence

Minecraft code is proprietary (Mojang EULA): study only, never copy. Every
pattern below is re-implemented from the description.

## What to borrow

- **Draw the randomness before dispatch.** Rings draw all seeds and forks on
  one thread in a fixed order, then hand each task its own fork, so the
  content can't depend on which task finishes first. For us that means
  variant i gets `H(seed, key, i)` before submission, never a shared generator.
- **Consumers join a future.** "Not ready" is a wait, never a different
  answer. This is the pure-cache contract.
- **Start at level load and don't persist.** The ring search is a biome search
  per ring position (128 in vanilla's set). Mojang recomputes it on every
  load, which is the P0 choice in [overview](overview.md).
- **The generic-vs-instance split.** The template is shared and immutable,
  and the processors add per-position variety at placement. Our equivalent is
  the IR (roles) shared per spec, with palette, weathering and foundation per
  instance.

## What to avoid

- Per-instance randomness without the world seed. `Mth.getSeed(pos)` makes a
  zombie village's mossy blocks identical at the same position in every
  world. Our per-instance passes key on H(world seed, lot, purpose).
- Wall-clock fallbacks (`Util.getMillis()` when `pos` is null).
- Unsynchronised memo maps inside shared immutable objects (the palette's
  `HashMap` cache). It is safe only while one thread touches it; whether two
  dimension lanes can race on one template is *inferred*, not checked.

## Verdict

Vanilla already does the "pure precompute, join on demand, recompute on load"
pattern for rings, and the "shared template plus seeded per-instance pass"
pattern for villages. Both carry over directly; avoid its position-only
seeds.

**Sources / what I couldn't verify:** read `ChunkGeneratorStructureState`
(`generatePositions`, `generateRingPositions`, `ensureStructuresGenerated`,
`getRingPositionsFor`), `ServerLevel` (constructor), `StructureTemplateManager`,
`StructureTemplate` (`Palette`, `processBlockInfos`), `StructurePlaceSettings`,
`BlockRotProcessor`, `RuleProcessor`, `CappedProcessor`, `Mth.getSeed` (26.3
sources jar) and `structure_set/strongholds.json` (count 128, distance 32,
spread 3). I did not time the ring precompute or check whether any later
code persists it.
