# PORTALCOLD: the first nether-portal trip into a cold settlement

Roadmap item 6.30 (measurement step PORTALCOLD-M). Date 2026-10-30, branch `claude/p6-portalcold-m`.

## Question

When a player first goes through a nether portal and comes out in an unvisited (cold) settlement, how long does the overworld server thread stall before vanilla's loading screen, and does the exit portal land in or damage a building or street?

## Answer

**The stall is real and long: about 37 s into a cold town and about 64 s into a cold metropolis core, against about 12.5 s for the same jump into cold wild terrain (all on beebox, a quiet box).** The extra 24 s (town) and 52 s (metropolis) is ours. Nothing is sent to the player meanwhile, and the whole server is frozen, not only that player. A default dedicated server has `max-tick-time=60000`, so the metropolis case (63-65 s here, 60 s on the busy local host) would trip the watchdog and crash the server (the harness runs with `max-tick-time=-1`, so this is inferred, not observed).

The cause is not the portal logic. It is the number of chunks the exit forces. In every cold case `overworld.loadedChunks` went from 0 to **625 (a 25 x 25 chunk square, 400 blocks across)** inside the one `NetherPortalBlock.getPortalDestination` call (`PortalForcer` searches for a portal over a wide radius and then builds one). When the same area was already loaded (case `wild2`, around world spawn, 841 chunks loaded before) the identical call took **4-5 ms**. The server thread is parked (`Unsafe.park`, 98-99 % of 250 ms samples) while worker threads generate the chunks; 25 x 25 chunks cover about 3 x 3 settlement tiles of 144-160 blocks, so a city exit plans several tiles, which is where the extra time goes (lane counters at arrival: 4 starts (town) and 6 (metropolis) created, 33 and 40 postProcess calls, 1 % of building lookups inline).

The dimension change after the call is cheap: `teleport.call` 4-6 ms, longest tick in the 100 ticks after arrival 33-101 ms. `TeleportHold` did not take the transition over (`teleport.held=false` in every case), as the bytecode reading predicted: the stall happens before `ServerPlayer.teleport` is ever reached, so a hook at the head of `teleport` (what TPHOLD uses) is too late for option c.

Exit position: in both cities the exit portal came out on open ground next to nothing: town on podzol in a dark-oak forest 2,4 blocks from the scaled target, metropolis on the stone-brick plaza floor 5,4 from the target with nothing above it (17 x 17 x 14 block census: 285 stone bricks, dirt, no walls). No planned building box of the exit tile overlaps the 6 x 7 x 7 footprint of the portal and its platform, nor lies within 3 blocks of it. The only blocks the portal replaces are the 14 obsidian and 6 portal blocks themselves (4-6 plaza bricks in the metropolis). So in these two samples a portal built into a city damaged no building and no street beyond its own footprint. One sample per city is not a proof: vanilla picks the nearest spot with a solid 3 x 3 floor and 4 blocks of air, and in a dense core that can be a street, a roof or a courtyard; I did not test dense districts.

## Numbers

Seed 987654321 (the PERFSRV world), fresh world dir and fresh JVM per case, dedicated server (no renderer), creative mock player with view distance 8, spawn area loaded first. Host `beebox` (12 cores, quiet), loadavg at the trigger 4-6; `renderer: none`. Final run, merged with run-phase6 at `b7ab02d0` (evidence `.claude/reports/p6-portalcold-m-evidence/b7ab02d067-20261030T230411Z`, remote job log; the result JSONs are copied to `docs/research/spikes/portalcold/`).

| case | overworld exit | exit tile | `getPortalDestination` | teleport call | longest tick after | chunks loaded (before / after) |
|---|---|---|---|---|---|---|
| wild (control, cold) | 6000, 6000 | 37,37 | 12.7 s | 5 ms | 62 ms | 0 / 625 |
| wild2 (control, area already loaded) | -3000, 3000 | -19,18 | 4 ms | 4 ms | 74 ms | 841 / 841 |
| town | 1280, -9600 | 8,-60 | **36.6 s** | 5 ms | 58 ms | 0 / 625 |
| metropolis core | 2432, -5696 | 15,-36 | **65.3 s** | 6 ms | 33 ms | 0 / 625 |

Repeats on beebox (same code before the merge): wild 12.3, 12.7, 13.1 s; town 36.6, 37.3, 38.1 s; metropolis 63.3, 64.1, 64.5 s. The spread is under 5 %. A local run at loadavg 16-22 (host `central`, 20 cores, loaded by other agents): town 40.3 s, metropolis 60.4 s. Wild 12.7 s includes our mod with no settlement in range (0 starts created), so it is mostly vanilla worldgen of 625 chunks on this server (about 20 ms a chunk); no vanilla-only (mod off) run was made.

Where the time is, from 250 ms samples of the server thread during the call: 98-99 % parked waiting on worker threads, under 1 % in our code on that thread. The work runs in the worker pools, so it parallelises but the server thread waits for all 625 chunks.

## What it means for the options

- **a) accept**: not defensible for the metropolis: over a minute of a frozen server with no message, and a watchdog crash on a default dedicated server. Acceptable only for the town (37 s) if John wants it, and still frozen for everyone online.
- **b) warm at the scaled exit while the player stands in the portal**: vanilla's portal wait is 80 ticks (4 s) in survival, immediate in creative, and the work is 37-65 s on a quiet 12-core box with 625 chunks to generate. A warm that only reads or plans the exit tile would not cover the 3 x 3 tiles, and the chunk generation itself (not only our planning) is most of the time (wild alone is 12.7 s). So warming alone cannot finish in the portal wait; it would only shorten the stall.
- **c) hold the transition like TPHOLD**: this is what the numbers point to, with two corrections to the brief. First, the hook has to be earlier than TPHOLD's: on `NetherPortalBlock.getPortalDestination` (or `PortalForcer`), not `ServerPlayer.teleport`, because the stall is inside the destination search. Second, the hold must not block the server thread: request the 25 x 25 exit area as chunk tickets asynchronously (and warm the planner tiles under it), return no destination while it loads (the player stays in the portal with the portal overlay, and the server keeps ticking), then let vanilla proceed when `loadedChunks` for the area is complete, when the call takes ~5 ms as in `wild2`. TPHOLD already has the ticketing and message machinery to reuse; vanilla's exit search area is wider than the 3 x 3 tiles TPHOLD warms for overworld jumps, which is the part to check.
- A smaller cut of the cost: keep the search from loading 625 chunks (a mixin narrowing `PortalForcer`'s search radius) is a vanilla behaviour change and out of scope for a measurement; mention only.

## Limits

- The portal transition is driven by calling the two things vanilla's tick path calls (`getPortalDestination`, then `ServerPlayer.teleport`), timed separately: a mock player with a dead connection is never ticked through `Entity.handlePortal`. The first harness tried that path and the player never arrived (the portal wait never completed for the unticked mock). The code that stalls is the same call.
- Player at view distance 8; a client at 12 asks for more chunks around the arrival, which happen after the portal call returns and are asynchronous.
- One exit per cold city and one seed; no vanilla-only run; no singleplayer integrated-server run (a stalled server is the same thread there; the watchdog does not apply to it).
- Exit damage check is bounding-box overlap with planned building boxes plus a block census and ASCII dump (`portal-<case>.txt`), not a diff against the portal-free world.

## Re-running

`scripts/portalcold.sh [wild wild2 town metro]` (fresh world per case; takes the heavy lock; on beebox `scripts/evidence.sh p6-portalcold-m scripts/remote.sh --fetch mod/build/portalcold -- scripts/portalcold.sh`). Harness `mod/src/gametest/java/dev/fractalbuildings/gametest/perf/PortalColdHarness.java` (run `:mod:runPortalColdServer`, not part of `check`, so the default fast suites do not run it). To make it a regression test for a fix, assert `getPortalDestination.ms` and the longest tick.
