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
  `docs/review/phase6/perf/perf.md`.

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
- **Decision**: open. B8 recommends against A6 now (only teleports see it);
  coordinator deferred A6 until B8 (2026-10-22).
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
