# Performance ledger

Known performance issues and the tradeoffs we accepted, kept for a later
performance phase. For now the bar is loose (John, 2026-10-23): walking and
flying with cache warming must feel smooth; everything else is recorded here
rather than fixed.

How to use it:

- One `###` entry per issue, under "Accepted tradeoffs" or "Open issues": **What**, **Measured** (number, load average, and
  where it came from), **Budget**, **Decision** (the human's, with a date, or
  `open`), **Improve** (the idea for later).
- Anyone appends entries or adds a newer measurement to an entry (keep the old
  one with its date). Only the human sets a decision.
- Numbers taken while other agents were busy are noisy: always give the load.
- The automated check is `scripts/perf.sh` with budgets and CI ceilings in
  `mod/perf-budget.json` (6.7). The phase 6 measurement table is
  `docs/review/phase6/perf/perf.md` (arrives with B8's merge).

## Accepted tradeoffs

### Teleport into an unvisited city stalls about 4 s

- **What**: the region layer and road network for a metropolis are computed
  cold the first time anything needs them. Travel never pays it (RegionWarmer
  runs it 34 to 68 s ahead at elytra speed), but a same-dimension teleport into
  an unvisited city does: one chunk waits for it, and the player sees
  ungenerated terrain meanwhile.
- **Measured**: region + roads cold 4,763 ms (4,162 to 5,600), load 1.74 to
  3.9; in game one chunk took 4,043 ms. Source: B8 (`p6-b8-perf`, 2026-10-23).
- **Budget**: 1,000 ms (missed 4 to 5x).
- **Decision**: accepted, John 2026-10-23 ("the 4 second hitch is ok for
  teleporting"). He would like it hidden behind a screen if the engine allows.
- **Improve**: hold the teleport on the server until the destination is
  warm, or show a loading screen; see
  [teleport-loading-screen.md](research/worldgen/teleport-loading-screen.md).
  Joins, respawns and dimension changes already get vanilla's loading screen.
  Deeper fix: the W2 coarse-screen fallback (plan phase 6) to make the region
  layer cheaper; `warm()` on join and respawn.

### Downtown saves are 60 to 100 % bigger

- **What**: region files grow with the blocks a built-up chunk holds.
- **Measured**: +62 % to +102 % region-file bytes for 162 downtown chunks (6 to
  10 KB per chunk), world half at load 0.85 to 3.9. Start NBT not measured on
  its own; a whole metropolis not extrapolated. Source: B8.
- **Budget**: +10 % (S6's whole-area figure; downtown is the worst case).
- **Decision**: accepted, John 2026-10-23 ("I expect cities take up more
  space, this is acceptable").
- **Improve**: measure the start NBT (piece refs only since B1); measure a
  whole city, not only downtown.

### Server tick spike on arriving at a city

- **What**: the worst server tick of an elytra flight is at the moment the city
  is reached, when the lane joins or computes the first starts.
- **Measured**: 337 ms (vanilla world start 96 to 313 ms); once 3,990 ms at
  load 22. p95 tick 13 ms (vanilla 17). Source: B8.
- **Budget**: 250 ms (invented in B8), CI ceiling 10 s.
- **Decision**: accepted for now, John 2026-10-23 ("the slow tick is ok for
  now").
- **Improve**: find what the server thread waits on at arrival (joined
  in-flight plans, start creation) and move it to workers or warm earlier.

### A downtown chunk costs about 3x a vanilla chunk

- **What**: whole chunk wall time (terrain, features, lighting and ours) in a
  downtown. Our own code is about 11 of the 46 ms extra (start 9 ms,
  postProcess 2 ms); the rest is block updates, heightmaps and light of a
  built-up chunk.
- **Measured**: 3.2x warmed (67 vs 21 ms mean), 2.8x on a cold teleport block;
  postProcess 1.1 ms cold, 2.0 ms warmed (budget 5 ms). Source: B8.
- **Budget**: was 1.5x (S6 saw 2.1x); now 3x.
- **Decision**: accepted, John 2026-10-23 (3x budget: "the increased chunk cost
  at 3x budget sounds good").
- **Improve**: fewer block updates when writing pieces (flags, section-level
  writes), lighting cost of dense interiors.

## Open issues

### Cold district plan misses its budget

- **What**: planning one city tile from cold (skeleton warm).
- **Measured**: median 409 ms (380 to 472), p95 638 ms, load 1.74 (B8); first
  tile of a metropolis 1,291 ms with 93,170 exact probe columns, before JUNCTION
  merged. Earlier: A1 813 ms at load 12 to 20; A1F ~900 ms at load 8, of which
  probe fills were 820 ms (431 chunks at 1.9 ms). Prefetched, the lane pays
  0.04 ms.
- **Budget**: median 300 ms (missed 1.4x), p95 1,000 ms (met).
- **Decision**: open. A6 not needed for now (A1, then B8: only teleports see
  the miss; run ledger 2026-10-23).
- **Improve**: A6 (read heights on a 4-block lattice, exact only under
  buildings: ~430 to ~150 chunks per district); sub-block fills were tried and
  were slower (A1F).

### JUNCTION front-loads probes on a metropolis's first tile

- **What**: shared junction heights need the network's junctions graded before
  the first tile, so the first tile reads far more columns.
- **Measured**: first-tile probes 42k to ~170k (JUNCTION verifier); the lead
  coder saw ~133k probes and ~7.5 s live. Not yet measured by `perf.sh` after
  the merge (B8 measured 93,170 before it).
- **Budget**: first tile 1,000 ms.
- **Decision**: open (re-run `scripts/perf.sh` once JUNCTION is merged).
- **Improve**: grade junctions per tile neighbourhood instead of the whole
  network; warm the junction field with the region layer.

### Tile prefetch only starts near the player

- **What**: RegionWarmer looks 2 tiles around the player plus a 5 s lead;
  prefetch only once a tile is under the player or the lead point. Teleports
  are not covered; a fast elytra turn can join a plan in flight.
- **Measured**: inline share 0.66 to 0.78 % of building lookups over a 2.5 km
  flight (B8); neighbour buildings once 64 ms on the lane (B4, budget 20 ms).
- **Budget**: inline share 5 %; lane 20 ms per prefetched tile.
- **Decision**: open; within budget for straight flight.
- **Improve**: prefetch along the look direction and on turns; warm the target
  of `/tp`, `/fb locate` and respawn.

### Cache retention and memory ceilings

- **What**: FlightCache and BoundedCache keep entries for a grace time (120 s,
  hard limit x4), with caps sized from the heap (64 / 128 tiles).
- **Measured**: ~0.9 MiB per tile; SkeletonSource ~0.74 MiB each (256 cap up
  to ~190 MiB, rebuild 31 ms); at a 512 MiB heap worst-case retention is about
  a third of the heap; 3 and 8 players in different cities: 0 recomputes,
  +31 / +69 MB. More than 128 simultaneous tiles thrash again. Sources: CACHE
  (2026-10-22), B8.
- **Budget**: heap +500 MB (met: +51 to +60 MB); 0 recomputes.
- **Decision**: open.
- **Improve**: release on a timer, not on the next lookup; cap SkeletonSource by
  memory; test non-zero grace values; an in-game many-player test.

### Vanilla-structure veto is cold on the server thread

- **What**: the veto that keeps vanilla villages out of our footprints scans
  region cells; cold it runs on the server thread for `/locate`, explorer maps
  and trades, and on the worldgen lane during generation. Its reach (~6 km) is
  wider than needed.
- **Measured**: ~100 ms per village candidate, worst ~3 s on the lane (B7);
  footprint query 169 cells, 7.5 s first cold call, 58 ms mean over a 12 km
  walk; `/locate #minecraft:village` in a fresh area 6.7 s at load 5 to 22,
  0.4 ms repeated (B8).
- **Budget**: `/locate` 10 s; watchdog 60 s.
- **Decision**: open (within budget).
- **Improve**: tier-bounded scan, narrower reach, screen with the coarse
  lattice, pre-resolve cells in the warmer.

### Intercity roads: cold cells cost 1 to 2 s

- **What**: the first road chunk in a road cell computes the cell's routes
  (single flight, once per cell).
- **Measured**: cold cells 1.3 to 2.2 s (load 2 to 7); warm road chunks 0.3 to
  0.5 ms; mean over all 587 feature calls 16.6 ms. Source: C1 r2
  (2026-10-23).
- **Budget**: none set.
- **Decision**: open; RegionWarmer hides it in play.
- **Improve**: warm road cells with the region layer; add a `perf.sh` row.

### Bed checks in metropolis chunks

- **What**: chunks with beds check both halves against the plan raster (B5,
  BEDFIX).
- **Measured**: ~10.8 ms per metropolis chunk with beds (B5 verifier); B8's
  postProcess figure (1.1 to 2.0 ms mean) includes them.
- **Budget**: postProcess 5 ms per chunk (met on average).
- **Decision**: open.
- **Improve**: cache the bed raster per tile.

### Test wall time

- **What**: how long the suites take, which limits how often agents run them.
- **Measured**: planner-core ~10 min serially (603 tests, 10m19s at load 3 to
  7), ~340 s with parallel forks (TESTSPEED, loaded); floor
  SettlementDeterminismTest 138 s. Client game tests ~20 s in Phase 0, now
  minutes; the client chunk-order test ~24 min (BEDFIX); `perf.sh` 10 to 15
  min, 30 to 60+ min wall behind the heavy-lock queue (B8).
- **Budget**: none.
- **Decision**: open.
- **Improve**: split SettlementDeterminismTest; a faster post-merge set; run
  the long client order test only nightly or before merges to `main`.

### Prefetch grades the neighbour tiles' ground (CITYQ r2)

- **What**: `WorldPlanner.prefetch` now also computes the ground (street grading) of the eight neighbour tiles on the workers, because
  `SpillClear` re-writes street air up to 4 blocks into neighbour chunks and must not grade a neighbour on the worldgen lane. It costs
  extra worker time only where the neighbour tile never generates (the edge of explored land); elsewhere the work moves earlier.
- **Measured**: not measured.
- **Budget**: none set.
- **Decision**: open.
- **Improve**: grade only the border strip a neighbour needs, or prefetch only the four edge neighbours.

### SpillClear runs from RoadFeature in chunks beside a settlement (CITYQ r5)

- **What**: `RoadFeature.place` runs in every overworld chunk. For a chunk with a settlement within `DISTRICT_REACH` (`WorldPlanner.districtsNear`), it checks `SpillClear.hasGround`. When the chunk holds no ground piece of ours, it runs `SpillClear.run`, which reads the planned street air of neighbouring tiles' ground within 4 blocks of the chunk.
  - With intercity roads off, `districtsNear` is always true (no road net to ask), so every overworld chunk pays the `hasGround` tile lookups.
  - It can also compute a neighbour tile's plan and ground on the generation thread, when the prefetch has not.
- **Measured**: not measured. B8 (`scripts/perf.sh`) should show any cost in chunk time.
- **Budget**: none set.
- **Decision**: open.
- **Improve**: with roads off, answer `districtsNear` from the region view (settlement sites near the chunk) instead of `true`. Run the pass only for chunks that touch a tile border of a planned tile.

### SpillClear also removes late leaves (CITYQ r7)

- **What**: `SpillClear.run` now reads the street air of neighbour ground pieces up to 12 blocks out (was 4), removing only natural leaves past 4; and `SpillClear.roadLeaves` runs from `RoadFeature` in every overworld chunk with roads on, reading the 4 headroom cells of every road column in the 8 neighbour chunks (plus `districtColumns` of a neighbour that has road columns). Both remove a standing neighbour tree's crown grown after the street or road was cleared.
- **Measured**: not measured; the street window has about 2.8 times the columns of the old one, each air cell read once (plus the existing `overridden` scan). B2's 7 orders took 18-82 s each at load about 2, in line with r3.
- **Budget**: none set.
- **Decision**: open.
- **Improve**: skip box columns that cannot hold a leaf; cache the neighbours' `districtColumns`.

### In-game tests render on the CPU (lavapipe)

- **What**: every client test, `scripts/perf.sh`'s world half and the
  fly-through render with Mesa's software Vulkan (lavapipe) under Xvfb. No
  `perf.sh` row measures rendering (no FPS or frame time): all rows are
  server-side or planner numbers. The planner half (`PerfPlannerTest`: cold
  plans, region layer, recomputes) runs without a client at all. The world half
  (locate, flight ticks, chunk ratio, postProcess, region growth, heap) runs in
  the integrated server of a client at render distance 4, so its timing rows
  share the 20 cores with lavapipe and with other agents; counts, bytes and
  heap are not affected. What we cannot measure today: client frame time and
  stutter when a city's chunks arrive.
- **Measured**: render distance is set by the tests, not capped: 6 in the
  worldgen/terrain client tests, 4 in `PerfClientGameTest`, 10 in the
  fly-through (`--rd`), 8 in `server-only-test.sh`; vanilla allows 32. The
  fly-through clamps its cameras to it (approach at most `rd*16 - 16` blocks,
  overview offset at most `rd*8`). Chunks to generate and mesh grow as
  (2rd+1)^2: 441 at 10, 4,225 at 32 (9.6x); at the NOTES rate of about 13 s
  per 576 chunks that is about 95 s of generation per new camera site before
  lavapipe draws anything. Not measured at 32. Screenshots are 854x480 by
  default. Host GPUs (2026-10-26, read-only query): RTX 3060 12 GB (John's
  desktop uses it, about 2.6 GB in use), Intel UHD 770 iGPU; both render nodes
  are world-accessible.
- **Budget**: none.
- **Decision**: open (John: GPU for tests, and which).
- **Improve**: move the world half's server-side rows to a dedicated server or
  server game test (no renderer in the loop); fly-through at a higher render
  distance with clouds off and larger shots; GPU options in the assistant's
  2026-10-26 answer (gpu-perf).

### Quays front-load probes on a waterfront settlement's first tile

- **What**: METROFILL R1 (r3) made a settlement's quay runs a settlement-wide value (traced over every waterfront tile it plans and graded once, cached per source and probe like the junction field), because grading a run through each tile's own window made neighbouring tiles cut it differently (`tile-border-agree`). The first waterfront tile asked pays for the whole settlement's quays.
- **Measured** (recorded probe calls, planner-core test, load ~10 to 20, 2026-10-26): hub tile of gate-desert-badlands (city r827) 371k calls (budget in `HubSquareGateTest` was 220k, now 450k); whole settlement 1.88M -> 1.35M calls (gate-desert-badlands, 64 tiles), 3.39M -> 2.86M (s7_m3 metropolis on world 12345, 120 tiles); wall time unchanged (1.5 s / 1.8 s).
- **Budget**: none of its own; the cold-tile budget above.
- **Decision**: open.
- **Improve**: trace lazily from the asking tile: follow only the contour chains that reach it, plus the earlier runs (canonical order) that overlap them.

### Idea: deferred interior decoration pass (John 2026-10-26)

- **What**: not a measured problem yet; an option to keep in reserve in case
  furnishing (7.5) and "lived-in" detail make worldgen too slow. John:
  "What about the possibility of doing our own 'decoration pass' that can
  happen on its own thread outside of normal worldgen. Maybe interiors only.
  By definition, the interiors are almost all invisible to a player for quite
  some time. We could be updating interiors async. Even updating them as
  players near (similar to chunk loading). It might prove totally
  unnecessary. [...] sometimes 'detail' is cheap, as noise looks detailed.
  But realistic interiors might not be noise. Alternatively, we might
  procedurally generate floors with details and stamp them for multiple
  stories."
- **Measured**: no interior row exists yet. Today interiors are a small part
  of the cost: our whole postProcess is 1.1 ms cold and 2.0 ms warmed per
  downtown chunk (budget 5 ms), hooks included (`HookPlacer`, about 2 to 4
  blocks per room, plus the ~10.8 ms bed checks in bed chunks, see "Bed checks
  in metropolis chunks"). Most of a downtown chunk's 46 ms extra is vanilla's
  block updates, heightmaps and light, not our planning (B8). 7.5 (plan
  [plans/furniture.md](plans/furniture.md)) raises the count to multi-block
  pieces up to a 40 % floor cap, with signs, cauldrons, smokers and other
  block entities: that is where cost would grow, mostly in block and light
  updates, not in planning.
- **Budget**: none yet. Suggested trigger: add an "interior writes" row to
  `scripts/perf.sh` with 7.5 (time in `HookPlacer` and the furniture writer
  per chunk); consider the deferred pass only if that row passes about 5 ms
  mean per downtown chunk, or the 3x chunk-cost budget above is missed
  because of furniture.
- **Decision**: open (an idea, not scheduled).
- **Improve**, feasibility read (assistant, 2026-10-26; not prototyped):
  - *How it would work.* Worldgen writes the shell and leaves a marker: a
    per-chunk attachment (or a list in the structure start) saying "interior
    pending for these buildings". A server-tick service, like RegionWarmer
    and the B4 worker lane, finds pending chunks near players, plans the
    interior on a worker thread (pure core code, safe off-thread), and then
    writes the blocks on the server thread under a per-tick budget (say 2 ms),
    then clears the marker. Only the block writes must be on the server
    thread; the planning can be async.
  - *Hard parts.* (1) Writes only into FULL, loaded chunks, through the level,
    so block entities, light and neighbour updates are right; that makes each
    write dearer than the proto-chunk write worldgen does now, so it moves
    cost, it does not remove it. (2) Determinism: content from seed and
    position only, so only *when* it happens varies; it must not read the
    world state at write time except to skip cells a player changed. (3) The
    "decorated" flag must be saved with the chunk, written in the same tick
    as the last block, so a crash or reload neither skips nor doubles a room;
    a building split across chunks needs per-chunk flags. (4) Players who get
    there first see a bare room pop into a furnished one, so trigger it at a
    radius beyond view (a ring outside render distance would need chunk
    tickets to load the chunk, which costs memory); and never overwrite a
    block a player placed or broke. (5) Villagers: job blocks (smoker,
    cauldron, lectern) placed late change which villager claims what; place
    job blocks in worldgen and defer only decoration. (6) Server-only
    installs keep working: everything is server side and vanilla blocks, no
    client mod.
  - *Cheaper alternatives first.* Stamp one planned floor across repeated
    storeys: high-rises already repeat one typical floor per band
    (`TowerProgram`, the `floor-repeat` check, 7.13), so furnishing can plan
    once per band and copy with a y shift; cache planned interiors per
    building (a building's plan is already cached per tile). Write cheaper:
    section-level writes and fewer update flags (see "A downtown chunk costs
    about 3x"). These cut planning and write cost without the save and
    timing problems above.
  - *When it would be worth it.* Only if the interior row above stays over
    budget after stamping and cheaper writes, measured on a quiet machine.
