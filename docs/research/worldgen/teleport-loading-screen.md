---
tags: research
track: worldgen
date: "2026-10-23"
author: assistant
licence: proprietary
---

# Hiding the teleport hitch behind a loading screen

## What it is

Can the mod hide the ~4 s cold-city stall ([PERFORMANCE.md](../../PERFORMANCE.md))
behind a screen, the way a portal shows "Loading terrain…"? Read from
Loom-decompiled Minecraft 26.3 (`LevelLoadingScreen`, `LevelLoadTracker`,
`ClientPacketListener.handleRespawn`, `ServerPlayer.teleport`, `PlayerList`,
`PrepareSpawnTask`, `ServerChunkCache`) and the Fabric API 0.162 module jars.

## Approach (how vanilla does it)

- The client opens `LevelLoadingScreen` in exactly two places: on login
  (`handleLogin`) and on **every** `ClientboundRespawnPacket`
  (`handleRespawn` → `startWaitingForNewLevel`), whether or not the dimension
  changed. The reason picks the background: nether/end portal art, else the
  plain one. A same-dimension respawn after death uses it too.
- The screen closes when `LevelLoadTracker` is ready: the server's
  `LEVEL_CHUNKS_LOAD_START` game event arrived, **and** the section the
  camera is in has been compiled on the client (or a 30 s timeout). The
  server sends that event right away (`PlayerList.sendLevelInfo`), so what
  really holds the screen up is the player's own chunk arriving, and a
  chunk is only sent once it is generated. A cold chunk under the player is
  therefore hidden.
- Join: `PrepareSpawnTask` (configuration phase) adds a `PLAYER_SPAWN`
  ticket (radius 3) with `addTicketAndLoadWithRadius` and lets the player in
  only when those chunks are FULL; the client shows its loading screen.
- So **joins and respawns into a cold city are already hidden by
  vanilla**. A dimension change only partly (correction 2026-10-30,
  inferred from the 26.3 bytecode, not measured): before the teleport,
  `NetherPortalBlock.getExitPortal` runs on the server thread, and on a
  first trip with no portal in range `PortalForcer.createPortal` reads
  `getHeight` and `getBlockState` around the exit, which generates those
  overworld chunks then and there. A cold city is therefore planned as a
  silent server freeze while the player still stands in the portal; the
  loading screen opens only afterwards. RegionWarmer warms overworld
  players only, so nothing plans ahead from the nether. To be measured
  (ROADMAP 6.30 PORTALCOLD, measurement item PORTALCOLD-M).
- A same-dimension teleport is not hidden at all:
  `ServerPlayer.teleport` sends just a position packet when the level is
  unchanged; the client stays in the world and sees void until chunks come.

## How it hooks into worldgen

Three ways for `/tp` (and `/fb locate` jumps, `/spreadplayers`):

1. **Hold the teleport (server-only).** Mixin at the head of
   `ServerPlayer.teleport(TeleportTransition)`: for a same-level, long jump
   (beyond view distance) whose target is not warm, cancel it, start the
   RegionWarmer on the target, add a mod ticket with
   `ServerChunkCache.addTicketAndLoadWithRadius` (own `TicketType`,
   registered in `BuiltInRegistries.TICKET_TYPE`; must load and not expire
   while unloaded), show an action-bar "Preparing destination…", and replay
   the teleport on the server thread when the future completes or after a
   timeout (~15 s). The player waits ~4 s where they stand and arrives in
   generated terrain. Works with unmodded clients.
2. **Same-dimension respawn packet (server-only).** Send
   `ClientboundRespawnPacket` for the current level (keep flags 3) plus what
   `PlayerList.respawn` resends (position, abilities, effects, inventory,
   experience, level info incl. `LEVEL_CHUNKS_LOAD_START`). Unmodded
   clients then show the real "Loading terrain" screen. But the client
   rebuilds its `LocalPlayer`; anything not resent is lost (vehicles,
   elytra state, other mods' client state). Fragile across versions.
3. **Client screen (mod on the client).** Server sends a custom payload
   (Fabric `PayloadTypeRegistry` + `ServerPlayNetworking`, only if
   `ServerPlayNetworking.canSend`); the client opens a screen (our own, or
   `LevelLoadingScreen` with its own tracker) until a "ready" payload. Pair
   with (1): the hold happens anyway, the screen just covers it.

Fabric API has **no teleport event**: `ServerEntityLevelChangeEvents` fires
after a dimension change, `ServerPlayerEvents` covers respawn/copy; neither
can delay a same-level teleport, so (1) needs a mixin.

## Licence

Minecraft is proprietary (Mojang EULA): read decompiled for API study only;
no code copied. Fabric API is Apache-2.0.

## What to borrow

- `addTicketAndLoadWithRadius` + a future, as `PrepareSpawnTask` does: the
  supported way to wait for FULL chunks without blocking the server thread.
- Vanilla's own rule for "ready": the player's chunk, not a radius.

## What to avoid

- Blocking the server thread while warming (the watchdog is 60 s and other
  players stall).
- Option 2 as the default: a fake respawn changes client state we don't own.
- Holding short teleports (ender pearls, chorus fruit, `/tp` a few blocks):
  only far jumps into unwarmed regions.

## Verdict

Recommend (1), hold the teleport server-side until the target is warm: keeps
server-only installs working, size **S to M** (one mixin, a ticket type, a
public `RegionWarmer` "warm this point" future, an action-bar message,
timeout, a server game test that `/tp`s into a cold city and checks the
destination chunk is FULL before the move; ~200 to 300 lines). Add (3) later
as an optional client nicety (S, ~100 lines + client entrypoint); skip (2).

**Sources / what I couldn't verify:** read the 26.3 decompile only; nothing
was run. Not checked: what an unmodded client renders during a vanilla
same-dimension `/tp` into ungenerated chunks (whether it freezes the player);
that option 2 actually works without a dimension change in practice (the
client code path accepts it; death respawn uses it); whether other mods
(Essentials-style `/home`, `/rtp`) call `ServerPlayer.teleport` and would be
held too (they likely would, which is probably desirable).
