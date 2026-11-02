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
- **Ceilings are never raised to make a run pass** (John, 2026-11-02). A row
  over its ceiling is a bug. For 0.1.0 John accepts living with such bugs: the
  row gets a `knownOver` field in `mod/perf-budget.json` and a line under
  "Known perf bugs" below. `scripts/perf.sh` then prints
  `PERF KNOWN OVER (bug, accepted for 0.1.0: ...)`, which is not a pass and
  doesn't fail the run. The run fails again if the row gets more than 25 %
  worse than the value recorded in the field.

## Where the ceilings come from

Most budgets and ceilings were set early (B8, the performance budget check,
2026-10-23) before the code they judge was measured: the budgets come from
the phase 6 plan's spike estimates, and most ceilings are 2x or 3x a budget
or a single noisy run. Only the count rows added since (chunks of terrain
generated, columns, raw plans) are set from a measured value, mostly at 1.2x
to 1.3x it. Read a timing ceiling as a guess, not a target someone derived;
the numbers stay as they are (sources: `docs/plans/phase6.md` "Budgets",
[log/2026-10-23-p6-b8-perf.md](log/2026-10-23-p6-b8-perf.md), ASSUMPTIONS rows of 2026-10-23).

| Row (what it measures) | Budget / ceiling | Basis |
|---|---|---|
| Cold district plan, median and p95 | 300 / 1,500 ms; 1,000 / 3,000 ms | guess: plan target from an early prototype (spike), ceiling about 3x one loaded run |
| First tile of a metropolis, cold | 1,000 / 4,000 ms | guess: plan target, ceiling about 3x one loaded run |
| Same, prefetched (what the lane pays) | 20 / 40 ms | guess: plan target, ceiling 2x |
| Region layer + road network, cold | 6,000 / 10,000 ms | John's tolerance (2026-10-23, a ~4 s teleport hitch is fine), set on a number that later turned out to be 8x low (JUNCTION, street heights where streets meet) |
| Region chunks of terrain generated / lattice columns / road-station columns | 5,000 / 6,200; 52,500 / 64,000; 15,000 / 18,500 | measured: about 1.25x the measured counts (4,924; 51,054; 14,587) |
| Quays of the waterfront tile, time | 3,000 / 6,000 ms | partly measured: budget about 1.3x two quiet runs (2.25 / 2.17 s), ceiling 2x the budget |
| Quays, chunks of terrain generated | 2,700 / 5,212 | measured, now stale: ceiling 2x the measured 2,606; the row measures 151 since QUAYFAST (quays traced run by run) |
| Neighbour cascade, chunks generated and raw plans | 11,891 / 14,270; 38 / 46 | measured: budget = the measured count, ceiling 1.2x |
| Building lookups inline during a flight | 5 / 10 % | guess: plan target |
| Wall time per downtown chunk vs vanilla | 3x / 4.5x | John's tolerance, set after one measured 2.85x |
| Block writes per downtown chunk | 5 / 10 ms | guess: plan estimate from a prototype (100 ns a block) |
| Worst server tick during a flight | 3,000 / 6,000 ms | measured loosely: chosen to cover the bimodal 2.8 to 4.0 s seen in B8's runs |
| p95 server tick during a flight | 50 / 100 ms | guess: no source recorded |
| Region-file growth, downtown | +100 % / +200 % | John's tolerance, set to one measured +100 % |
| Heap growth | 500 / 1,000 MB | guess: plan estimate from a prototype |
| `/locate` settlement, fresh / from spawn | 3,000 / 5,000 ms; 2,000 / 4,000 ms | measured once: about 3x the 1.0 to 1.8 s seen at B8, before later planner changes |
| `/locate` vanilla village (veto), fresh | 10,000 / 20,000 ms | guess: the plan's 10 s |
| Tile recomputes, 3 and 8 players | 0 / 5 | target 0 (measured 0); ceiling a guess |

## Known perf bugs (accepted for 0.1.0)

One line per row that is over its ceiling, or close enough to watch.
Marked `knownOver` in `mod/perf-budget.json` only where it is over now.
Values from the latest quiet run (the perf worker, 2026-11-02, commit
05142f534b, load 1.4 to 6.6).

- *Fixed, no longer marked.* Cold district plan, p95 (`district.cold.p95.ms`: the slowest of 7 city tiles planned from scratch): 1,073 ms on the quiet perf worker (beebox, 2026-11-02, commit f5302ab3f1, load 0.2 to 2.1) against a 3,000 ms ceiling, still over the 1,000 ms budget; was 5,967 ms. Samples 12..18,-35: 581 / 494 / 695 / 1,027 / 394 / 455 / 1,073 ms. Fixed by QUAYFAST 4b (the quay screen re-read the coarse probe per column; `CoarseTerrain` now remembers water and peak per lattice column); `knownOver` removed from the row.
- *Watch, not marked.* Region layer + road network, cold (`region.cold.warm.ms`: the regional plan and roads around a metropolis, first time): 9,242 ms against a 10,000 ms ceiling (budget 6,000); 11.3 s was seen on the perf worker in MAINSTUB round 2 (polish 6.25). Cause: noise generation for the intercity road graph's terrain reads (entry "Region layer row" below).
- *Watch, not marked.* Worst server tick during an elytra flight into a city (`mod.flight.tick.max.ms`): 4,074 ms against a 6,000 ms ceiling (budget 3,000); bimodal since B8, either about 0.3 s or 2.8 to 4.5 s. Cause not known (polish line "QUAYFAST 5+6").
- *Watch, not marked.* Cold district plan, median: 711 ms against 1,500 (budget 300). `/locate` settlement in a fresh area: 3,305 ms against 5,000 (budget 3,000).

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
  Joins and respawns already get vanilla's loading screen. A dimension change
  gets it too, but only after the exit portal is found: on a first
  nether-portal trip vanilla builds the exit portal (`PortalForcer.createPortal`)
  on the server thread before the screen opens, which generates the overworld
  chunks there, so a cold city is planned during a silent server freeze
  (inferred from the 26.3 bytecode, not measured; ROADMAP 6.30 PORTALCOLD,
  measurement item PORTALCOLD-M).
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
- **Improve**: A6 (plan phase 6: confine a district's reads to its own ~150
  chunks instead of the ~430 its grading and lot probes spread over). The cost
  is per chunk filled (1.9 ms for all 256 columns), not per column, so reading
  fewer columns inside a chunk saves nothing: a 4-block lattice still touches
  every chunk, and a checkerboard was measured to save no chunks (SPARSEREADS,
  [review/sparse-reads.md](review/sparse-reads.md)). Sub-block fills were tried
  and were slower (A1F).
- **2026-11-01 CASCADE 2fix (HEAVY12 FAIL)**: on a quiet perf worker the p95 is 5,660 ms (ci 3,000) since CASCADE step 2 (bfa3f27d), 1,169 ms before. No work was added: the slowest sample, 16,-35, now pays a lazy quay chain trace (2,683 of its 2,779 chunk fills) that the old whole-road exit cut used to prepay in the unbudgeted neighbour row (`district.cold.neighbour.ms` 8,912 -> 1,615 ms on the same quiet runs; counts: neighbour plus samples 4,281 fills before, 4,145 after, `ColdRowCountTest`). So the real cold cost of a waterfront tile whose chain nobody traced is about 5.7 s, and the earlier 1.1 s was an artifact of row order. [log](log/2026-11-01-p6-cascade-2fix.md). Fix forward (995ae8c2): the chain walk remembers 4 prefetch boxes instead of 1 (its breadth-first ends alternated, so nearly every read paid a parallel round: 3,760 -> 505 rounds); the quiet perf worker p95 3,946 / 3,586 ms over 2 runs (load 1.31 / 0.01), still above ci 3,000. **Improve**: the rest is the chain trace itself (about 6,700 chunk fills on the pool, 2,779 serial): prefetch the contour corridor only, or overlap the next box with the walk (QUAYLAZY follow-up); otherwise the row needs a decision.

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

### perf.sh world half moved off the renderer (PERFSRV, 2026-10-26)

- **What**: the world rows (`/locate`, flight ticks, inline share, chunk cost vs
  vanilla, postProcess, region growth, heap) now come from a dedicated server
  with no client (`:mod:runPerfServer`, `PerfServerHarness`; a mock player
  flies the same path, so chunk loading, tracking and the teleport hold are the
  same code as a client's player), run twice (variant mod, variant base) and
  merged by `scripts/perf-merge.mjs`; same rows, budgets and `summary.md`.
  Server settings: seed 987654321, view distance 4, simulation distance 12
  (what the integrated server used at render distance 4). The old form is
  `scripts/perf.sh --client` (`PerfClientGameTest`).
- **Needs**: a dedicated server needs an accepted EULA; the standing approval in
  CLAUDE.md covers `mod/build/run/perfServer/` (local, 127.0.0.1, online-mode=false),
  so `perf.sh` writes `eula.txt` itself when it is missing (PERFSRV r9).
- **Still client-side**: nothing today; a frame-time row (GPU) would be.

### Quays front-load probes on a waterfront settlement's first tile

- **What**: METROFILL R1 (r3) made a settlement's quay runs a settlement-wide value (traced over every waterfront tile it plans and graded once, cached per source and probe like the junction field), because grading a run through each tile's own window made neighbouring tiles cut it differently (`tile-border-agree`). The first waterfront tile asked pays for the whole settlement's quays.
- **Measured** (recorded probe calls, planner-core test, load ~10 to 20, 2026-10-26): hub tile of gate-desert-badlands (city r827) 371k calls (budget in `HubSquareGateTest` was 220k, now 450k); whole settlement 1.88M -> 1.35M calls (gate-desert-badlands, 64 tiles), 3.39M -> 2.86M (s7_m3 metropolis on world 12345, 120 tiles); wall time unchanged (1.5 s / 1.8 s).
- **Budget**: none of its own; the cold-tile budget above.
- **Decision**: open.
- **Improve**: trace lazily from the asking tile: follow only the contour chains that reach it, plus the earlier runs (canonical order) that overlap them.
- **QUAYLAZY (2026-11-01, done, steps 1 to 6)**: the lazy trace is in. A tile traces only the quays that reach it, and the mod's region warmer plans the destination tile instead of tracing the whole waterfront (`-Dfractalbuildings.quayTrace=whole` restores the old warm). Output is byte-identical. Quiet perf worker, same code with and without the whole trace: one waterfront tile of the metropolis 2.2 s and 2,606 chunk fills (new rows `region.cold.quays.tile.ms` budget 3 s / ceiling 6 s, `.chunkFills` budget 2,700 / ceiling 5,212) against 20 s for the whole trace; the warmer on a worker 29 s to 10 s; cold arrival 72 s to 53 s; TeleportHold 66 s to 47 s. **The slowest cold chunk did not change (34.7 s both ways)**: it is the junction field and region layer a structure start waits for, not the quays. The worst elytra-flight tick is bimodal (5.0, 5.0, 0.3, 0.8 s over four runs; budget 3 s). Table, ablations (reads pool, walk-ahead, scan box, fork cost) and the hold-cap numbers: [regionperf-decision.md](review/regionperf-decision.md) "After QUAYLAZY".
- **Open, needs John**: a cold metropolis arrival is still 12x vanilla (53 s against 4.4 s) and holds 47 s; the remaining cost is settlement-wide (junction field, region layer 10 s), so the next lever is a lazy junction field. The hold cap cannot go to 15 s until the slowest cold chunk is under about 12 s.

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

### Region layer row: JUNCTION r3 and intercity roads made it about 8x dearer, not a per-probe slowdown (regionperf, 2026-10-27)

- **What**: `perf.sh` row "Region layer + road network, cold, metropolis" (`WorldPlanner.warm` on the metropolis at 2432,-5696, budget 6 s, ceiling 10 s) is 45 s on the perf worker (701a96d6, load 1.5). The 4.7 s of 2026-10-23 was measured on B8's branch **before** it merged 9911ff15 (JUNCTION r3 + 6.10 C1 intercity roads; B8 log: "JUNCTION: not merged when measured"), and the row was not re-measured after that merge, so e9b92460's own number was never 4.7 s.
- **Bisect** (same bench at each point, one host, alternating): 9911ff15^1 5.6 / 6.6 s with 0 exact probes and 19,302 lattice columns; 9911ff15 62 s; e9b92460 52 / 66 s with 169,522 exact probes; 701a96d6 57 s. Nothing between e9b92460 and 701a96d6 changed the probes (`mod/.../probe` untouched) or the cost per column. The verifier's "0.03 ms vs 0.25 ms per probe" compared 169k probes with a run that had none.
- **Where the time goes** (701a96d6, game probes, JFR and counters): vanilla noise generation, about 95 % of the row (`GradientNoise.permute` 52 %, aquifer 18 %). It is now 68,343 lattice columns (region decisions: the intercity road graph decides about 5x the region reads, 297,870 against 52,869), 14,587 road-station columns (C1 routes) and 4,924 chunk fills for the junction field and street rules (JUNCTION, 153k exact reads). With `intercityRoads` off the same warm is 36 s at load 30 (half the row); the junction field is most of the rest.
- **Fixed here**: (1) the perf test built its probes by hand and read road stations through the chunk memo (each station a 16x16 chunk fill), which the game never does; `LiveProbes.world(exact)` now builds the probes for both `WorldPlanners` and the perf test. (2) The district chunk memo is 8,192 chunks (was 4,096): the junction field's 4,924 chunks had 6,327 fills, a quarter of them refills (3 to 8 % of the row; about 9 MB more heap at most). (3) Count rows in `mod/perf-budget.json` for the noise `warm` generates (`region.cold.warm.chunkFills`, `.sparseColumns`, `.roadColumns`, deterministic, always enforced), so a change that adds work fails at once, whatever the load. A lattice biome memo was tried and dropped (climate sampling is under 4 %).
- **perf worker** (83c7a808, load 2.4 to 1.2): 45,240 ms before these fixes (701a96d6) to **36,360 ms** after (-20 %); quays 69 s; the worker path (warm + quays, as RegionWarmer) 103 s. Evidence `.claude/reports/p6-regionperf-evidence/83c7a808f1-20261027T161611Z/`.
- **Open, needs John**: the row is still 3.6x its ceiling. The budget (6 s, ceiling 10 s, John 2026-10-23) and the accepted "teleport stalls about 4 s" tradeoff were both decided on the pre-JUNCTION number; a cold teleport into an unvisited metropolis now waits for this whole warm. Options: raise the row to what JUNCTION + C1 cost (and re-decide the teleport tradeoff; TPHOLD already holds far teleports until the terrain is ready), or make the work cheaper: decide fewer region cells for the intercity graph (most of the lattice columns), read the junction field from fewer chunks, or the W2 coarse screen (plan phase 6). The quay trace (`warmQuays`, 598k exact reads, 71 s on the perf worker) is paid by the first waterfront tile and is not in this row.

### Metropolis profile: 104 s server freeze on an elytra flight into a cold waterfront metropolis (PROFILE, 2026-10-28)

- **What**: JFR profile of the perf world half, mod world against the base world, split by stack ancestry into ours, vanilla for us and vanilla own; full write-up [profile-metropolis.md](review/profile-metropolis.md). Flying into metropolis s2_m6, the server thread froze for one 104 s tick: vanilla's `CatSpawner` read a chunk next to the player whose structure start was waiting on the settlement-wide quay trace (running on a vanilla worker inside `generateStructureStarts`). On the cold block, 78 of 83 CPU-s are vanilla noise our probes ask for; quays are 66 % of it.
- **Measured** (local i7-12700, heavy lock; R1/R3 load 1 to 3.6, R2 load up to 18 from another agent's suite; evidence `.claude/reports/profile-evidence/`): cold block 75 s wall (base 2.2 s), first chunk 71 s; warm chunk 55 ms against 22 ms; gate-city flight smooth (tick p95 7 to 9 ms, worst 475 to 560 ms); metropolis flight worst tick 103.6 s (R2, not yet repeated on a quiet machine). One cold metropolis plan reads 24,832 noise chunks (quays 19,854, which is 65 % of the city's chunks) and 83,365 single columns, and decides 444 region cells out to 17 km.
- **Budget**: none for a freeze; the flight row (worst tick 3 s, ceiling 6 s) measures the gate-city flight only.
- **Decision**: open.
- **Improve**: ranked candidates in the write-up; first, take the quay trace out of the chunk pipeline and make it lazy.

### Parallel terrain reads: region row 3x, quays 3.5x faster, same output (PARREADS, 2026-10-28)

- **What**: the exact-terrain reads of a cold settlement (vanilla noise, pure in seed and position) now fill the chunk memo on a reads pool (`fractalbuildings-reads-N`, a third of the CPUs, 1 to 6) ahead of the planner's own single-threaded reads: the quay trace row by row of waterfront tiles, the junction field, region resolution and road links in parallel, the junction fields beside the road links; the warmer runs each quay trace right after its junction field (`warm(..., true)`). Caller-runs, so a call from a worldgen thread or a pool thread never waits for a helper that has not started.
- **Measured** (re-measured 2026-10-31, QUIETPERF): on the perf worker (12 CPUs) with nothing else running or queued, checked every minute and after each run. Before is 5209d507 (`perf.sh --planner-only`, 1 run). After is run-branch 64d76a52, which has PARREADS and the items merged since (3 runs: 2 full, 1 planner-only). The region count rows are nearly equal (chunk fills 4,924 before, 4,930 after; sparse 68,343 and road 14,587 both), so the two do the same work. Load before the planner step: 1.5 before; 4.3 / 1.9 / 1.4 after (run 1 started right after its own build). Evidence: `.claude/reports/quietperf-bee-before-evidence/5209d50703-20261031T073338Z/` and `quietperf-bee-r{1,2,3}-evidence/`.

  | Row (the perf worker, quiet) | Before | After (runs 1 / 2 / 3) | Speed-up |
  |---|---|---|---|
  | Region row `region.cold.warm.ms` | 36,392 ms | 14,838 / 11,640 / 11,686 ms | 3.1x (2.5x against run 1) |
  | Quay trace `region.cold.quays.ms` | 69,504 ms | 21,534 / 19,942 / 20,810 ms | 3.3x |
  | Warmer path `region.warmed.worker.ms` | 103,328 ms | 30,582 / 30,987 / 31,931 ms | 3.3x |

  The cold-chunk row (`mod.cold.chunk.max.ms`) wasn't measured before (planner-only run). After, it is 53.3 / 53.9 s, against 0.33 s in the vanilla base world on the same host. That is still the quay trace in a structure start: the perf cold block runs no warmer first (COLDCHUNK). Output: 11 live digests equal before and after (`ParReadsDigest`, from the original PARREADS run). *Not valid (busy host):* the original local numbers (loads 4.8 to 46 and 9 to 32; region 30.5 to 10.0 s, quays 57.4 to 16.2 s, warmer 195 to 24 s, cold chunk 93 to 37 s). The direction holds, but those numbers are not results.
- **Budget**: the region row (6 s, ceiling 10 s) is over its ceiling on the perf worker (11.6 to 14.8 s); not re-decided (the old budgets came from local runs).
- **Decision**: open (the region row's budget, and the teleport tradeoff, still wait on John; REGIONPERF brief).
- **Improve**: bind empty biome tags in `TerrainDumpTool` (the planner rows pay a "Tags not bound" exception per biome read that the game never pays, NOTES); take the quay trace out of the chunk pipeline (profile candidate 1) or make it lazy (candidate 2).
- @John 2026-10-30: @coordinator For future reference, if you didn’t measure performance on a quiet machine **you did not measure**. Don’t report invalid results like this please. Do it again. Unless it shows such a drastic improvement that you get a speedup despite being busy.
  - @assistant 2026-10-31: Done. The rule is now in CLAUDE.md, "Parallel work" (heavy lock bullet): a timing from a busy host is not a measurement and is not reported. Quiet means no other job or agent work on the host, not a load threshold. Measurements go to the perf worker, sent when nothing else is running or queued. `scripts/quiet-check.sh` checks this, and `scripts/perf.sh` records it in `mod/build/perf/quiet.txt` and ends with `perf: NOT A MEASUREMENT` when the host was busy. The agent files and NOTES.md (QUIETPERF) carry it too. PARREADS was re-measured that way above: about 3x on all three rows, before and after on the same quiet perf worker. The old local numbers are marked not valid.
- 2026-10-31 PERFBASE: earlier "mod / vanilla" ratios (regionperf-decision.md section 7, COLDARRIVAL, HEAVY10) used a base world that was the mod with settlement frequency 0, not vanilla (its village `/locate` was 5.26 s with region reads, 0.25 s after SKIPROLL). Since PERFBASE the base half of `scripts/perf.sh` is `tools/vanilla-server` (no fractalbuildings at all); corrected ratios are in that section. Do not compare numbers taken before with numbers after.
- 2026-11-01 B1 (CASCADE step 6, the reads pool size, PARREADS's helper threads): on a quiet perf worker (12 CPUs, dedicated server, no renderer, 2 runs each at c468dd41, loadavg 1.4 to 5.9 from the series' own work), 8 or 11 reads threads instead of today's 4 cut the slowest cold chunk from 34.9 to 30.5-30.9 s and the cold arrival from 52.3-52.8 to 46.5-47.9 s; the worst flight tick stays two-mode (its 4-5 s mode came 1 of 2 runs at 4, 2 of 2 at 8, 0 of 2 at 11). Details: [regionperf-decision.md](review/regionperf-decision.md) section 7, "Reads pool size". Decision on the default: open.

### The cascade a cold chunk waits for: link rule and exit cut (CASCADE, 2026-11-01)

- **What**: the slowest cold chunk of a metropolis waited about 24 s of its 35 s for a neighbouring tile's plan, which runs the exit trim (the exit-road cut walk and the link rule that asks whether a gate's road link is served by both settlements). CASCADE (steps 2 to 5, with READSDEFAULT's bigger reads pool) made the cut walk bounded, answered the link rule from one drawn approach where that proves it, fanned the rest out on the reads pool, and made the region warmer start it first. Output is byte-identical. Plan [jflazy.md](plans/jflazy.md); numbers [regionperf-decision.md](review/regionperf-decision.md) "After CASCADE".
- **Measured** (the world rows predate CASCADE2FIX; the perf worker, 12 CPUs, dedicated server, no renderer; two `scripts/perf.sh` runs at 4de791c5, each started with nothing else running or queued, `perf: quiet host`, 1-min loadavg just before the world half 2.75 / 3.17, at its end 5.98 / 6.77): slowest cold chunk **24.5 / 24.7 s** (30.6 s with the 11-thread pool alone, 34.9 s at the 4-thread c468dd41; vanilla 0.2 s); cold arrival 39.2 / 39.0 s (47.1 s, 52.5 s; vanilla 3.5 / 4.5 s); TeleportHold held 33.2 / 32.8 s (42.0, 46.9 s); flight tick max 0.91 / 0.92 s, p95 10.0 / 10.8 ms (the 4 to 5 s mode did not appear); region warm 9.2 / 9.3 s (ceiling 10 s). Remaining slowest chunk, from a sampler run: 14 of 25 s waiting for the link rule's neighbour tile plans, now on all cores; the exit-cut walk is gone.
- **Budget**: `cascade.neighbours.chunkFills` (16,460, ci 19,752) and `cascade.neighbours.rawPlans` (38, ci 46) are count rows, always enforced (1.2 x the value at 98f07884, deterministic; was 18,243 and 52). The world rows have no budget (COLDARRIVAL, report-only; John sets them). Planner rows: "Cold district plan, p95" fails (5.7 s, ceiling 3 s) since CASCADE 2; fix in CASCADE 2fix, then the count rows are checked again.
- **Planner row after CASCADE 2fix** (the quiet perf worker, 72917c94): cold district plan p95 3.67 s against ceiling 3 s (FAIL, median 668 ms), was 5.7 s before 2fix and about 1.15 s before CASCADE 2. Known issue; John decides a QUAYLAZY follow-up or a different ceiling. Count rows unchanged (16,460 / 38).
- **Decision**: open (TeleportHold cap, QUESTIONS; the arrival is still 9 to 11x vanilla).
- **Improve**: the remaining 14 s is the far settlements' tile plans and quays for the link check (plan DQ1 and DQ2 change output); saving warmed data with the world (regionperf option 6) is the other lever.

### The quay trace by runs and the cheap screen (QUAYFAST, 2026-11-02)

- **What**: the quay trace walks each waterfront run once and a cheap screen skips cells that cannot hold a quay (steps 0 to 4, byte-identical output).
- **Measured** (two full `scripts/perf.sh` runs, the perf worker (12 CPUs), commit 05142f53, `perf: quiet host`, loadavg before 1.77 / 1.42): the district window needs 1,066 chunk fills (was 4,145) and 5 walk rounds (was 648); CPU 29.5 / 28.4 s (was 49 to 53); the owned quay tile 0.95 s and 151 fills (was 2.2 s, 2,606); cold arrival 35.4 / 34.9 s (HEAVY13 42.3, CASCADE 7 39); hold 28.7 / 28.6 s. **Cold district p95 5.96 / 5.97 s against the 3 s ceiling: FAIL, worse than before (3.7 s)**: tile 18,-35 alone is 5.96 s, dominated by the screen's uncached biome reads (`QuayTrace.maybeOf` to `LiveBiomeProbe.biome`).
- **Budget**: `cascade.neighbours.chunkFills` lowered to 11,891 (ci 14,270). `region.cold.quays.tile.chunkFills` (2,700) is now 151; proposal: budget 160, ci 320.
- **Decision**: open; details in `docs/review/regionperf-decision.md` section 7, "After QUAYFAST".
- **Improve**: cache or batch the screen's water and biome reads per coarse cell (the p95 is one tile); the flight tick two-mode (4 to 5 s) is back.
