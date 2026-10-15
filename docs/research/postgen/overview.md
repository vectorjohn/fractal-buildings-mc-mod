# Out-of-worldgen generation: overview (backup option)

**Track:** postgen · **Date:** 2026-10-15 · **Author:** assistant

## What it is

John's idea: like Distant Horizons, make chunks exist far from the player, then
plan and build cities on the finished terrain, free of worldgen's threading
and 8-chunk limits. This note combines [distant-horizons.md](distant-horizons.md),
[pregenerators.md](pregenerators.md) and
[post-generation-placement.md](post-generation-placement.md). It is the GDMC
setting (settlement generators on existing worlds, see `gdmc/`), run live.

## Approach: a pregenerate-then-plan pipeline

1. **Sites** are chosen deterministically from (seed, region) as on the main
   path, using noise probes only, so they exist before any chunk does.
2. **Trigger**: when a player comes within `R_trigger` of a site whose
   footprint (+ margin) isn't generated, queue its chunks. Load each with a
   loading-only ticket to `FULL` (`addTicketAndLoadWithRadius`, public in
   26.3), throttled, a bounded number in flight, task saved to disk.
3. **Snapshot** heightmaps/biomes/water of the footprint into the core's
   probe interfaces; the planner runs **off-thread, unbounded**: no size,
   piece or thread limits.
4. **Build**: hold tickets on the chunks no player can see, write blocks in
   batches on the server thread (heightmaps, light, saving come with
   `LevelChunk.setBlockState`), release. Persist "city N built" in
   `SavedData` so a restart resumes rather than repeats.

## Budget: when the player outruns it

Vanilla gives ~40 chunks/s in total (C2ME ~450). A 64×64-chunk city region is
~4,100 chunks, ~100 s plus planning and writing. During that time a sprinter
covers ~560 blocks (35 chunks), an elytra flyer ~3 km (200 chunks).
Walking players are fine with `R_trigger` ≈ view distance + 40 chunks;
elytra is not. Their own exploration also eats the same 40 chunks/s.
When the player gets there first: (a) build anyway, so the city pops in
in view (many block packets, looks wrong); (b) wait until no player sees the
area (could be never in multiplayer); (c) fall back to the structure path
for that site. (c) is the only one that never fails.

## Determinism, multiplayer, server

- The plan is deterministic only if its input is: vanilla terrain is, but
  feature placement can vary with generation order (C2ME says so of vanilla), and
  players can edit terrain before the plan runs. Plan on noise-level data
  (heights, biomes, water) and treat trees as clearable, or a city's layout
  depends on who arrived first.
- Server-side only; vanilla clients work. Many players in different places
  mean many sites in flight against one global budget, and pregeneration
  costs TPS; servers usually pregenerate before players join.
- Distant Horizons users would see pre-city LODs until the chunks re-save
  (unchecked whether DH re-reads them).

## Interaction with the structure-based main path

- Not a replacement: worldgen-time structures are free in throughput, never
  pop in and work at any player speed.
- Best fit is a **hybrid**: the structure path builds what fits in a start's
  ±8 chunks (houses, districts); post-gen adds city-scale extras that cross
  that limit (long walls, roads joining districts, landmarks), or pregenerates
  a server's planned capital before opening it.
- Post-gen has no `terrain_adaptation`; flattening must be written as blocks
  (cut-and-fill), as GDMC entries do.
- Pregeneration could also feed the main path: if a region exists before
  worldgen reaches it (it doesn't, by definition), so this stays one-way.

## Licence

Notes cite LGPL-3.0 (Distant Horizons), GPL-3.0 (Chunky, WorldEdit,
Structurize), MIT (C2ME, Recurrent Complex). Ideas only from GPL code.

## Verdict

Viable as a **backup or add-on**, not the main path: correct and limit-free
for sites known ahead and reached slowly (server admin pregeneration, a
spawn capital, `/fractal build-city` command), but throughput-bound at ~40
chunks/s and visibly late for fast players. Keep it as the escape hatch if
the 8-chunk limit blocks city-scale features.

**Sources / what I couldn't verify:** the three notes above. Rates are from
C2ME's benchmark, not measured here. Player speeds are typical figures (sprint
5.6 b/s, rocket elytra ~30 b/s), not checked in 26.3.

## Suggested batch 2

- Spike: measure pregeneration rate on this host (lavapipe-free dedicated
  server or server game test) for a 32×32 and 64×64 region, under the heavy lock.
- Decompiled 26.3: `ChunkAccess.setStartForStructure` post hoc (does `/locate`
  work), `Block.UPDATE_*` flags for bulk writes, light across unloaded edges.
- Distant Horizons API: can a mod push LOD data for a planned city
  (`IDhApiWorldGenerator` / terrain data repo)?
- Millénaire: villages placed into existing terrain (licence first).
- Structurize/MineColonies: incremental blueprint placement, undo, entities.
- Paper/Moonrise chunk system and Folia (region threading) for server owners.
- Recurrent Complex "complement" ported idea: plan stored per region, written
  per chunk as chunks generate, made order-independent.
