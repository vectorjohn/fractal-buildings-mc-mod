# 6.30 PORTALCOLD: hold the nether-portal trip (option c)

2026-10-31, `claude/p6-portalcold` (lead-coder). John: "lets do c. P6 bug. A hard crash is a problem", then "I want to wait the minimum, even if that means the player sees loading chunks. I just want to avoid the possible server crash."

## What changed

- `PortalHold` (mod `teleport/`) plus `EntityPortalHoldMixin`: a `@WrapOperation` on `PortalProcessor.processPortalTeleportation` inside `Entity.handlePortal`, before vanilla sets the portal cooldown and calls `getPortalDestination`. A deferred trip changes no portal state, so vanilla asks again next tick while the entity stays in the portal.
- What is waited for (only what vanilla's call would otherwise load on the server thread, 26.3 decompiled):
  1. the chunks of `PoiManager.ensureLoadedAndValid` (17 x 17 for an overworld exit, 3 x 3 for a nether one) at status EMPTY (disk read or empty proto, no generation), then vanilla's own POI search;
  2. an existing portal in range: its chunks (and those `getLargestRectangleAround` reads) at FULL, normally a disk load;
  3. no portal: the chunks `PortalForcer.createPortal` reads (`PortalSearch.chunks`: 3 x 3 or 3 x 4 chunks) plus one ring, at INITIALIZE_LIGHT. That is all a FULL chunk depends on except LIGHT, SPAWN and FULL, which vanilla's call then runs (58-76 ms on the perf worker).
- Why one status short of FULL: `Level.getHeight` returns the minimum height for a chunk without a FULL-level ticket (`hasChunk`), and `createPortal` reads the first column of each cold chunk that way. A FULL ticket would make the held world "warm" and could move the exit. At ticket level 34 the exit is the unheld one (checked: identical in all three cases).
- Asynchronous request: our own ticket type `fractalbuildings:portal_hold` per chunk at the status's level (refcounted, overlapping requests share it), then `GenerationChunkHolder.scheduleChunkGenerationTask` once the distance manager has applied it; done = all futures done. A ticket alone generates nothing below FULL level (`docs/NOTES.md`).
- Players see TPHOLD's action-bar text "Generating terrain…" while held (`TeleportHold.message()` is now package-private, the only TeleportHold change). Every entity is held; spectators and the end portal are not; a request nobody stands in for 100 ticks is dropped; after 120 s (`DEFAULT_MAX_HOLD_MILLIS`) the trip goes anyway ([ASSUMPTIONS](../ASSUMPTIONS.md)). Survival players start the request when they step in, so vanilla's 4 s portal wait overlaps it. No planner warming, no view-distance or arrival chunks.
- Harness: `PortalColdHarness` cases with a `-held` suffix (`scripts/portalcold.sh metro-held`) drive `PortalHold.gate` each tick before the timed vanilla call, and log hold length, longest tick, release cost; a watcher logs the server thread's stack in any tick over 1.5 s. `scripts/portalcold.sh` takes `FB_HEAVY_LOCK` for functional runs.

## Verification

- Perf worker, quiet (loadavg 2-7 at the trigger), commit 6824675f, evidence `.claude/reports/p6-portalcold-evidence/6824675fc0-20261031T195335Z` and `71868d1fdb-20261031T200256Z`:

| case | unheld: one tick | held: wait | longest tick while held | release (vanilla call + teleport) | exit portal, unheld = held |
|---|---|---|---|---|---|
| metropolis core | 46.2 s | 46.3, 46.0, 46.1 s | 30, 19, 22 ms | 76+5, 68+5, 67+5 ms | 2437,67,-5692 both |
| town | 39.9 s | 41.0 s | 32 ms | 58+5 ms | 1282,67,-9596 both |
| wild | 12.0 s | 12.4 s | 21 ms | 40+4 ms | 6016,64,6002 both |

  The unheld metropolis is 46 s now (65 s at PORTALCOLD-M on the older tree): code merged since made it faster; held and unheld compare like for like here. The wait equals the unheld stall: the work is the same, it just runs while the server ticks.
- Second freeze found and fixed (`PortalHold.afterTrip`): the first client-test runs saw one 8.6-15 s tick right after a held trip. Stack (client test watcher): `Player.travel` -> `Entity.getOnPos` -> `getBlockState` -> sync `getChunk`. A player keeps its `mainSupportingBlockPos` from the old dimension (vanilla 26.3), and in the same tick `getOnPos` reads that block in the new level: the nether coordinates in the overworld, a cold chunk. The second wrap in `EntityPortalHoldMixin` clears the field after a portal trip that changed the player's level. After the fix the client test's longest server tick was 110 ms (the perf worker). Vanilla has the same stale read on command dimension changes (`execute in ... run tp`); the client test's own trip into the nether shows a 2-4 s tick from it. Not touched here.
- One unexplained long tick: in the first held harness metropolis run, one tick of 6.35 s about 8 s after arrival, not in 5 later held runs nor the unheld ones. The harness's mock player is not ticked, so it is not the supporting-block read. The harness now logs the stack of any tick over 1.5 s; open finding in `docs/NOTES.md`.
- `PortalSearchTest` (mod unit): `PortalSearch.chunks` equals the chunk box of a re-run of vanilla's spiral, frame-check and frame-write loops at every block offset and both axes; mutant `HIGH_ALONG = SPIRAL + 2` fails it.
- `PortalHoldGameTest` (server, fast suite): a NoAI pig in a portal with a cold nether exit is still in the overworld with a request open 5 ticks in, then arrives by a new exit portal; one moved out of the portal stays and its request is dropped. Mutant `enabled = false`: the first test fails ("went after 3 ticks").
- `PortalHoldClientGameTest` (client, full layer): survival player into the cold metropolis core through a real portal; must be held, arrive standing and unhurt, no server tick over 3 s. Perf worker (GPU, shared with another job) at 055849c2: held 77 s, longest tick 110 ms, arrived on the plaza at full health (screenshot `portal-hold-arrived.png`); evidence `.claude/reports/p6-portalcold-evidence/055849c27f-20261031T204649Z`. Before the supporting-block fix the same test failed with a 15.2 s tick (limit then 10 s).

## Round 2 (verifier INCOMPLETE)

- Flake fixed: `leavingThePortalDropsTheRequest` needed 113 server ticks in 120 s of wall time and failed once in the 40-test batch (53 ticks in 150 s). It now opens its request with a 5-tick idle limit (`PortalHold.testIdleTicks`, kept per request so the hook cannot leak into later requests), waits for the request to be dropped instead of counting ticks, and has `TestArea.WAIT_MILLIS` (600 s). Passed 3 runs at loadavg 20-25 and one run with the JVM pinned to one CPU.
- Exit guard: `coldExitHoldsThenGoes` now requires the hold to have prepared the createPortal chunks at `initialize_light` (portals=0). The verifier's mutant `PREPARED = FULL` fails it ("must prepare createPortal's chunks at initialize_light, not FULL").
