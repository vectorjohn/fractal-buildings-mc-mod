---
tags: research
track: pool
date: "2026-10-16"
author: researcher
licence: MIT
---

# The Lost Cities 26.x: off-thread planning and a signed persistent cache

## What it is

The newest Lost Cities code (branch `26.2_fabric`, commit `7e7d102`,
2026-10-04, [source](https://github.com/McJtyMods/LostCities)) adds an
inter-city highway planner (`INTERCITY_NETWORK_V1`, targeted at 7.5.0). It
runs its planning on a worker pool behind futures, and it **persists its most
expensive decision in the world save as a performance cache under a
signature**. It is the closest working precedent for "precompute off the lane,
stay deterministic, optionally persist". Building generation itself is
covered in [the-lost-cities](../mods/the-lost-cities.md).

## Approach (how it really works)

- **The rules, stated in its plan** (`HIGHWAY_MAJOR_CITY_PLAN.md`, "Constraints
  to preserve"):
  - results are deterministic for seed, dimension, profile and coordinates;
  - they are independent of chunk generation and query order;
  - "caches may improve performance but must never affect results";
  - bounded caches "whose eviction cannot alter results";
  - "measure cold-cache and warm-cache behavior".
- **Async planning.** `HighwayPlanningService` holds five `BoundedFutureCache`s
  (hubs, candidates, selections, routes, prepare) over a `ForkJoinPool` sized
  `max(2, cores / 2)`. `BoundedFutureCache.computeIfAbsent` puts a placeholder
  future first, so only one task runs per key. On failure it removes the entry,
  and it trims completed entries above a size cap. Dependencies chain with
  `thenCompose` and `thenApplyAsync`. Worldgen calls
  `getHighwayInfo(x, z)`, which **joins** `prepare(cell)` and then reads the
  planner (`HighwayPlanningService.getHighwayInfo`). Results that come from
  several cells are sorted before use (`getNearbyHubs`,
  `getAcceptedConnections`).
- **Signed persistent cache.** `LostCityHighwayData` (a `SavedData`) stores
  each planning cell's zero-or-one hub decision. `createCacheSignature` is an
  FNV-style hash over:
  - `FORMAT_VERSION` and `HUB_ALGORITHM_VERSION`;
  - the seed;
  - the planner settings;
  - the city chance, radius, Perlin and level profile fields;
  - `HEIGHT_SAMPLE_SIZE`;
  - the world-style id.

  On a mismatch, the dimension's entries are dropped
  ("invalidates only that dimension's old performance cache; it never changes
  a calculated result"). `IntercityHighwayPlanner.loadOrCalculateHub` reads
  the persisted value first, otherwise computes and stores it.
- **Version pinning, separately.** `LostCityWorldGenData` records each
  world's street and highway *mode*. A world without it is treated as
  `LEGACY`, and only a newly created world may pick a new mode. Algorithm
  changes therefore never reach old worlds through the cache: they go through
  an explicit, persisted mode.
- **Per-chunk memo with a stage flag.** `ChunkPlanner` keeps a `TimedCache` of
  `ChunkPlan`s, with double-checked locks per stage. Entries expire on a
  wall-clock TTL, which is harmless because results are pure.
  `Staged(value, cacheable)` caches a result only when its inputs were fully
  known.

## Procedural vs templated

Highways are procedural; buildings are templated parts. Nothing
building-level is pooled. Assets are compiled once per palette
(`CompiledPalette`) and memoised.

## How it hooks into worldgen

A per-chunk `Feature`. The planning service is per dimension
(`DefaultDimensionInfo`), and feature code joins its futures on the worldgen
thread.

## Licence

MIT ([LICENSE](https://github.com/McJtyMods/LostCities/blob/1.20/LICENSE)).
We may study and reuse it with attribution. Nothing is copied here.

## What to borrow

- A placeholder-first future cache (one computation per key, shared by
  workers and the lane), joined by consumers.
- A persisted cache **under a signature of every input**, plus a separate
  persisted *mode* for algorithm changes, rather than letting the cache pin
  old behaviour by accident.
- Its constraint list, nearly word for word, as our cache rules. Also its
  insistence on measuring cold-cache and warm-cache performance separately
  (S6).

## What to avoid

- **Signatures that miss inputs.** Hub viability reads terrain height and
  biome (`applyHighwayCityConstraints`), but the signature doesn't cover
  terrain or biome mods or data packs (*read*). Add Terralith to an old world
  and persisted hubs reflect the old terrain while new ones reflect the new.
  The cache then *does* change results (*inferred*). Generic pool entries read
  no terrain, so this is easier for us to get right.
- **Memoising a decision made from generated chunks.**
  - `StructureAvoidance` reads structure references from the chunks present
    in the `WorldGenRegion`.
  - For multibuildings, `resolveFootprint` skips missing chunks ("fail-open")
    and memoises the first answer in a static `ConcurrentHashMap`. The cached
    decision therefore reflects whichever chunks existed when it was first
    asked (*read*), so it can depend on generation order (*inferred*).
  - Our plan must never read generated chunks. The S8 veto runs at placement
    from pure functions.
- Static, process-wide caches (`MULTIBUILDING_DECISIONS`) that outlive a world
  unless something clears them.

## Verdict

A strong precedent for our cache contract: futures joined by worldgen, a
worker pool at half the cores, bounded pure caches, a signed persisted cache
and explicit per-world version modes. It also shows the two ways such a
cache quietly becomes a source of truth: an incomplete signature, and inputs
read from generated chunks.

**Sources / what I couldn't verify:** read `HighwayPlanningService`,
`BoundedFutureCache`, `IntercityHighwayPlanner` (cache and hub parts),
`LostCityHighwayData`, `LostCityWorldGenData`, `ChunkPlanner`, `ChunkPlan`,
`Staged`, `TimedCache`, `StructureAvoidance`, `BuildingInfo` (avoidance
parts), `DefaultDimensionInfo`, `HIGHWAY_MAJOR_CITY_PLAN.md`,
`plan_performance.md`. I did not run it, and I don't know whether 7.5.0 has
shipped. The terrain-mod and order effects are inferred from the code, not
observed.
