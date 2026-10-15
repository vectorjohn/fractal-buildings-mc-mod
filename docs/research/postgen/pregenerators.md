# Chunk pregenerators (Chunky, and C2ME as the accelerator)

**Track:** postgen · **Date:** 2026-10-15 · **Author:** assistant

## What it is

[Chunky](https://github.com/pop4959/Chunky) generates real, saved chunks over
a chosen shape (square, circle, …) via `/chunky start`. Fabric, NeoForge,
Forge, Paper, Folia, Sponge; maintained (HEAD 2026-10-14, already on 26.x
names such as `ChunkPos.pack()`). [C2ME](https://modrinth.com/mod/c2me-fabric)
is the usual companion that parallelises vanilla chunk generation.

## Approach

- **Drives the real server, no own generator** (`fabric/.../FabricWorld`):
  on the server thread, `addTicketWithRadius(CHUNKY, pos, 0)` with
  `CHUNKY = new TicketType(0, FLAG_LOADING)` (loading, not simulation: no
  entity or block ticking), run the distance manager, then
  `getChunkFutureMainThread(x, z, ChunkStatus.FULL, …)` (via a mixin
  invoker). On completion remove the ticket and mark "chunk system
  housekeeping" so the chunk unloads and saves. Same pattern as Distant
  Horizons' `INTERNAL_SERVER` mode.
- Skips chunks already `full` by scanning the region file's `Status` field
  (`chunkScanner().scanChunk`), without loading them.
- Concurrency: a `Semaphore(50)` of in-flight chunk requests
  (`chunky.maxWorkingCount`). The real limit is vanilla's chunk system.
- Tasks persist and resume after restart; progress events report chunks/s.
- **Developer API** (`ChunkyAPI`: `startTask`, `onGenerationComplete`,
  `version`). The wiki shows it obtained through Bukkit's ServicesManager; how
  a Fabric mod gets it is not documented (unchecked).
- **26.3 has a public equivalent**: `ServerChunkCache.addTicketAndLoadWithRadius
  (TicketType, ChunkPos, radius)` adds the ticket, runs the distance manager and
  returns a future for `FULL` over the radius (read in decompiled 26.3). Our
  mod would not need Chunky or mixin invokers to pregenerate a region.

## Rates

C2ME's published benchmark (Chunky, overworld, tmpfs, Xeon 8558P at 3.2 GHz;
the page says MC 1.21.10, a search snippet said 26.2: unclear), chunks/s:

| threads | vanilla | Paper | C2ME |
|---|---|---|---|
| 1 | 13 | 13 | 29 |
| 4 | 30 | 51 | 115 |
| 8 | 39 | 94 | 229 |
| 16 | 42 | 142 | 459 |

**Vanilla plateaus around 40 chunks/s**: more cores barely help. So:
a 64×64-chunk city region (1 km square, 4096 chunks) is ~100 s on an otherwise
idle vanilla server, and that throughput is shared with players exploring.
C2ME makes it ~10 s, but we can't require it.

## Procedural vs templated

Not applicable (tool).

## How it hooks into worldgen

Outside worldgen: it requests chunks like a player would, so every mod's
worldgen (including our structure-based buildings) runs normally. The chunks
are saved, so this is a one-off cost per area.

## Licence

**GPL-3.0** ([LICENSE](https://github.com/pop4959/Chunky/blob/master/LICENSE)).
Study only; don't copy code into our (non-GPL) mod. C2ME: MIT.

## What to borrow

- Loading-only ticket → `FULL` → remove ticket, with a bounded number in
  flight, and a resumable task saved to disk.
- Skip already-generated chunks by reading region-file status, not by loading.
- A public "region is generated" check is cheap; a mod can make "area fully
  generated" a precondition and wait for it.

## What to avoid

- Assuming parallelism: vanilla is serial-ish (track 4 found
  `structure_starts` runs with concurrency 1). Budget for ~40 chunks/s total.
- `tickingLoadDuration` > 0 (simulation tickets): lets fluids settle but costs
  heavily.
- Pregenerating while players are online costs TPS; Chunky's own advice is to
  pregenerate before players join.

## Verdict

A mod can pregenerate a region ahead of the player with public 26.3 APIs and
no dependency; the cost is ~40 chunks/s in vanilla, so it only works for
bounded areas planned well before the player arrives.

**Sources / what I couldn't verify:** read Chunky HEAD (`FabricWorld`,
`ServerChunkCacheMixin`, `GenerationTask`), wiki (Pregeneration, FAQ,
System Properties, Developer API), C2ME Modrinth page, decompiled 26.3
`ServerChunkCache`. Didn't measure rates on this host (would need the heavy
lock); benchmark MC version unclear.
