# Profiling a metropolis: what is ours, what is vanilla

For John, 2026-10-28 (assistant). This answers your question from today:
can one profile tell whether vanilla work runs because of us, or do we need
a vanilla run to compare against? It also answers your two follow-ups: how
far planning one metropolis reaches, and how many of our noise reads land in
chunks that vanilla computes again later. The numbers are from this machine
(i7-12700, local, under the heavy lock). Each set of numbers gives its load.
*Estimate* marks anything worked out rather than measured.

Earlier work this builds on: [PERFORMANCE.md](../PERFORMANCE.md) ("Region
layer row", "Quays front-load probes") and the
[regionperf decision brief](regionperf-decision.md), including §6 on
COLDCHUNK.

## Short answer

- **One profile is enough to answer "is this vanilla work ours?"** Each CPU
  sample carries its whole call stack. If the stack has a frame of our code
  under the vanilla method, we caused that sample. On the cold metropolis
  block this split put 78.2 of 82.6 CPU-seconds on us. The difference between
  the mod run and the vanilla run was 78.9 s, so the two methods agree to
  within 1 %.
- **A CPU comparison against vanilla is less useful than it sounds.** Both
  worlds generated the same chunks (Minecraft's own chunk events, per
  generation step, agree within 3 %). Even so, vanilla's own CPU per chunk
  differed by up to 1.8x between the two runs. So on this machine a
  difference in CPU totals smaller than about 50 % of vanilla's own share is
  noise.
- **The worst thing the player hits is a wait, not CPU.** During a flight
  into the metropolis the server froze for **104 s in one tick**. The only
  code on the server thread was vanilla's (`CatSpawner` loading a chunk next
  to the player). That chunk was waiting for our quay trace on a worker
  thread. No frame of ours is on the frozen thread's stack, and the CPU
  totals don't show it either. Only tick times and samples of waiting threads
  find it.
- **Next time:** one profile of the mod world, with stack ancestry, tick
  times and Minecraft's chunk events, is enough. Run the vanilla world only
  to compare work counts (chunks per step), tick times, save size or heap,
  not CPU seconds.

## Setup

`scripts/profile.sh` runs the existing perf world half twice: the mod world,
then the base world. The base world is the same seed 987654321 with the
settlement frequency set to 0, so it has the same terrain, the same
mock-player flight and the same chunk blocks. Each run is a dedicated server
with no renderer. Each phase gets its own JFR recording: the `profile`
settings plus the JDK 25 CPU-time sampler, which samples every busy thread,
with a stack depth of 1024. No stack was truncated. Each phase also records
exact per-thread CPU and allocation counters, GC totals, and Minecraft's own
JFR events (chunk generation per step, structure starts, tick time).
`scripts/JfrAttrib.java` reads the recordings and puts each sample in one of
three groups:

- **ours**: the top frame is our code;
- **vanilla for us**: the top frame is vanilla or JDK code, and one of our
  frames is lower on the same stack;
- **vanilla own**: no frame of ours on the stack.

The perf harness's own frames don't count as ours. The phases are:

- **flight**: 2,500 blocks at elytra speed, 1,516 ticks;
- **cold**: a 9x9 chunk block at the centre of metropolis s2_m6 (2432,-5696,
  radius 1,430), never generated before;
- **warm**: the 9x9 block next to it, after that city's caches are full.

| Run | Flight to | Load (1-min) | Notes |
|---|---|---|---|
| R1 `aaf1d475` | gate city (1280,-9600) | 1.0 to 3.6 | |
| R3 `a3ff20dc` | gate city | 2.9 to 3.3 | Same as R1, plus Minecraft's chunk events. Repeats R1 within 3 % on every row. |
| R2 `bee8b0fb` | the metropolis, ending 600 blocks inside its rim | mod 4.5 to about 18; base 22 | Another agent's suite started mid-run. Use its timings with care, but the stall it found is structural. |
| R4, R5 | metropolis (R4); gate, pinned to P-cores, base first (R5) | | Queued behind other agents' heavy jobs (load 30 to 50). See "Not done". |
| Reach `7ba928e4` | none (planner test) | 30 to 50 | `PerfReachTest`: counts only, no timings. |

The evidence (`.jfr` files, logs, rows) is in
`.claude/reports/profile-evidence/<sha>-<time>/mod/build/perf/`, and the reach
counts in `.claude/reports/profile-evidence/reach-7ba928e426/`.

## Top-down: ours, vanilla for us, vanilla own

These are CPU-seconds from the CPU-time samples (one sample is 10 ms of one
thread's CPU). The figures are R1 / R3. The exact per-thread counters agree
with the sampled totals to within 3 %.

| Phase | Wall (mod / base) | Ours | Vanilla for us | Vanilla own | Mod total | Base total |
|---|---|---|---|---|---|---|
| Cold metropolis block | 75.0 s / 2.2 s | 2.7 / 2.8 | 75.5 / 76.0 | 4.4 / 4.3 | 82.6 / 83.1 | 3.7 / 7.2 |
| Warm block next to it | 4.5 s / 1.8 s | 1.2 / 1.2 | 2.1 / 2.2 | 2.7 / 2.8 | 6.1 / 6.1 | 2.1 / 2.3 |
| Gate-city flight | 78 s / 78 s (tick-paced) | 2.7 / 2.6 | 79.6 / 81.1 | 84.3 / 85.8 | 166.6 / 169.4 | 161.1 / 162.6 |

Notes on the table:

- "Ours" (our own code running, not our calls into vanilla) is only 2 to 3 %
  of the cold block, and 20 % of the warm block (tile and building plans,
  `PieceRaster`).
- Almost all of "vanilla for us" is terrain noise that our probes ask for:
  `GradientNoise`, `Aquifer`, the density functions, and the biome `Climate`
  search.
- The base world isn't pure vanilla: our code is loaded, with frequency 0.
  In the base flight, 5.2 CPU-s are for us: the vanilla-structure veto and
  the region warmer still decide region cells, reading terrain and biomes,
  although no cell can settle at frequency 0. That is 3 % of the base
  flight. It doesn't change any conclusion, but it is a cheap win (candidate
  5 below).

**Where the "for us" time goes**, by part of our planning. Each stack is
classified by its most specific frame:

| Part | Cold block | Gate flight (R3) | Metropolis flight (R2) | Warm block |
|---|---|---|---|---|
| Quays (settlement-wide trace) | 55.1 s (66 %) | 35.2 s | 107.1 s | 0 |
| Junction field | 9.0 s | 5.8 s | 12.4 s | 0 |
| Tile and building plans | 10.1 s | 6.3 s | 23.2 s | 2.7 s (44 %) |
| Streets and skeleton (coarse lattice) | 3.8 s | 20.7 s | 23.0 s | 0.2 s |
| Intercity roads: routes and stations | 0.1 s | 7.6 s | 6.2 s | 0 |
| Region decisions (sites, vetoes, road graph) | 0 | 5.9 s | 5.3 s | 0 |
| Block placement and features | 0.7 s | 2.1 s | 3.0 s | 0.4 s |

## The 15 hottest methods

From the cold block (R3). The callers are vanilla's. The last column is the
nearest frame of ours on the stack, with its share of that method's samples.

| # | Method | Self | For us | Called from | Nearest frame of ours |
|---|---|---|---|---|---|
| 1 | `GradientNoise.permute` | 43.2 % | 98 % | `PerlinNoise.addToVolume` 45 %, `permuteToGrad` 36 % | `ChunkFill$Batched.scan` 63 %, `.window` 30 % |
| 2 | `Aquifer$NoiseBasedAquifer.computeSubstance` | 25.3 % | 99 % | `ChunkFill$Batched.scan` 99 % | `ChunkFill$Batched.scan` |
| 3 | `InterpolatedFunction$Sampler.fillCell` | 2.1 % | 99 % | `sampleWithBlockStep` | `ChunkFill$Batched.scan` |
| 4 | `PerlinNoise.addToVolume` | 2.0 % | 99 % | `NoiseStack$Perlin.addToVolume` | `.window` 50 %, `.scan` 47 % |
| 5 | `Climate$RTree$SubTree.search` | 2.0 % | 90 % | itself (recursion) | `LiveBiomeProbe.biome` 90 % |
| 6 | `UnaryFunction$SqueezeSampler.sampleVolume` | 1.7 % | 99 % | `MinSampler.sampleVolume` | `ChunkFill$Batched.scan` |
| 7 | `ChunkFill$Batched.scan` (ours) | 1.2 % | n/a | `ChunkFill$Batched.fill` | n/a |
| 8 | `DensityBuffer.set` | 1.2 % | 93 % | `fillCell` | `ChunkFill$Batched.scan` |
| 9 | `PalettedContainer.get` | 1.1 % | 2 % | itself | `FoliageClear.run`, `BuildingAirClear` |
| 10 | `RangeChoiceFunction$Sampler.sampleVolume` | 0.8 % | 95 % | `MinSampler.sampleVolume` 76 % | `ChunkFill$Batched.scan` |
| 11 | `Aquifer$NoiseBasedAquifer.getAquiferStatus` | 0.7 % | 100 % | `computeSubstance` | `ChunkFill$Batched.scan` |
| 12 | `BinaryFunction$MaxSampler.sampleVolume` | 0.6 % | 94 % | `ConstMulSampler.sampleVolume` 93 % | `ChunkFill$Batched.scan` |
| 13 | `DensitySampler$Bound.sampleValue` | 0.6 % | 100 % | `Aquifer.calculatePressure` 93 % | `ChunkFill$Batched.scan` |
| 14 | `NoiseStack$Perlin.get` | 0.6 % | 98 % | `ShiftedXzSampler.sampleVolume` 65 % | `ChunkFill$Batched.scan` 57 % |
| 15 | `SmearedPerlinNoise.addToVolume` | 0.6 % | 100 % | `NoiseStack$SmearedPerlin.addToVolume` | `.window` 55 %, `.scan` 45 % |

Further up the stack, 66 % of all cold samples sit under
`Districts$Build.settlementQuays`, called from a vanilla worker's
`generateStructureStarts` through `GroundStructure`. In the flights the list
is similar, plus `Climate$RTree` search at 9 % of all CPU (80 % for us,
through `LiveBiomeProbe.biome`) and vanilla's own surface rules, chunk
saving (`Deflater`) and lighting. The full per-phase reports are
`profile/<variant>-<phase>.md` next to each run's `.jfr` files.

## A/B totals: mod world against the base world

| Row | Mod | Base | Ratio |
|---|---|---|---|
| Cold block: wall | 75.0 s | 2.2 s | 34x |
| Cold block: first chunk | 71 s | 0.29 s | |
| Cold block: median chunk | 36 ms | 23 ms | 1.6x |
| Warm block: mean chunk | 55 ms | 22 ms | 2.5x |
| Warm block: process CPU | 10.3 s | 2.9 s | 3.5x |
| Gate flight: process CPU | 235 s | 208 s | 1.13x |
| Gate flight: server thread CPU | 5.5 s | 10.3 s | 0.5x |
| Gate flight: chunks generated (`terrain` step), R3 | 2,711 | 2,784 | 0.97x |
| Gate flight: allocation | 27.6 GB | 17.0 GB | 1.6x |
| Gate flight: GC pauses (JFR, R3) | 0.6 s, longest 118 ms | small | |
| Heap after the run | 452 MB | 324 MB | +128 MB |
| Region-file growth, both blocks | 2.27 MB | 1.73 MB | +31 % |

**Agreement with the stack split.** On the cold block the two methods agree
to within 1 % (see the short answer). On the warm block the vanilla run
leaves 4.0 s, of which ancestry puts 3.3 s on our frames. The other 0.6 s is
vanilla's own work running longer: lighting, setting blocks, saving more
blocks. That is the indirect cost a stack can't see, and here it is small.

**Where the A/B misleads.** In the flight, vanilla's own CPU was 84 s in the
mod world and 156 s in the base world, for the same chunks. R1 and R3 both
show this. The difference is spread over noise filling, saving, lighting and the
server thread; only feature placement costs the same in both. Minecraft's
own events agree: the `terrain` step took 22 ms per chunk in the mod world
and 37 ms in the base world. In the mod world's JVM,
vanilla's hottest noise method is compiled differently: `permute` is called
straight from `addToVolume`, not through `permuteToGrad`. So the likely
causes are the JIT (our probes warm up the same noise code in another way)
and this CPU's hybrid cores (8 P-cores with SMT plus 4 E-cores, so CPU time
per unit of work depends on where a thread lands). This is *not verified*:
R5, pinned to one thread per P-core with the base world first, would
separate the two causes, and it hasn't run (see "Not done"). Either way, the
plain A/B difference in the flight (5.5 CPU-s) understates our cost there
(about 82 CPU-s in our frames). The ancestry split doesn't
depend on that baseline.

## What the player actually feels

- **Flying into the gate city (R1, R3): smooth.**
  - Tick p95: mod 7 to 9 ms; base 12 to 13 ms.
  - Worst tick: mod 475 to 560 ms, at 2,308 blocks flown; base 131 to
    136 ms.
  - Ticks over 50 ms: mod 3 to 4; base 5 to 7.
  - The quays and plans run on workers and finish while the server keeps
    ticking.
- **Flying into the metropolis (R2): one 104 s server freeze.**
  - The freeze came at 1,978 blocks, about 880 blocks inside the rim.
  - Loaded chunks around the player had already dropped from 1,225 to 836,
    because generation was backing up.
  - Then vanilla's `CatSpawner` (on the server thread) read a block next to
    the player. Its chunk wasn't generated, so the server thread waited in
    `ServerChunkCache.getChunk` (1,730 of about 1,750 server-thread samples in that
    window).
  - That chunk's structure start was waiting on the settlement-wide quay
    trace. One `Worker-Main` thread spent 70.6 CPU-s in
    `settlementQuays` during the freeze, and our pool spent another 5.5 s
    there.
  - In a real game every player on the server would freeze for that time.
    The run was under load from another agent (up to 18), so the length may
    be inflated; R4 repeats it on a quiet machine.
  - The cause is structural: the quay trace runs inside a vanilla structure
    start, and any synchronous chunk read near the player waits for it.
- **Teleporting or arriving cold (the cold block): the first chunk takes 71
  s.** That is the same quay trace. In the harness it blocks the server
  thread; in the game TPHOLD holds the player for up to 30 s.
- **Already-planned city chunks: 2.5x a vanilla chunk** (55 ms against
  22 ms), well within the budget of 3x.
- **GC is not a problem.** The longest pause was 118 ms, and pauses total
  about 0.6 s per phase.

## Your follow-up 1: how far planning one cold metropolis reaches

`PerfReachTest` builds a fresh planner over the game's own probes on the
same metropolis. It runs the steps in the game's order: `warm` (radius 2,048
blocks), then `warmQuays`, then the centre tile. It counts every probe read
by purpose (from its stack) and every newly computed piece of noise (a
16x16 chunk fill or a single column), with its position. Afterwards it reads
the planner's caches. The counts match the perf budget's count rows (4,924
chunk fills, 68,343 lattice columns, 14,587 road columns), so the test sees
the same work the game does.

What `warm` resolves:

- **Region cells: 444 decided** (131 settled). They reach out to **17 cells
  (17 km)** from the metropolis cell. Only 25 of them are the 5x5 cells
  around the point (rings 0 to 2: 1, 8 and 16 cells). The rest are decided
  for the road graph.
- **Settlements: 106 found by the road graph** (318 cells asked whether they
  hold one). Of these, **4 get their street network and junction field
  built**: the metropolis plus every settlement in the 5x5 cells, which are
  villages s1_m8 (2.2 km away) and s1_m4 (2.9 km) and town s3_m8 (2.5 km).
- **Intercity links: 79 routes built** (31 succeed), with 54 settlements as
  endpoints and 6,221 stations. Only **5 routes touch the metropolis**. The
  endpoints are 1 hop from it: 5, 2 hops: 9, 3: 8, 4: 8, 5: 11, 6: 8, and
  4 are not connected to it.
  - So the answer is **not neighbours only, and not neighbours of
    neighbours: up to 6 hops.** Deciding which edges cross the 25 cells needs
    every settlement within about 3.9 km of each cell. Each of those needs
    its own neighbours and the witnesses whose links must route (ADR 0004 N1,
    RC), and that chain keeps going outwards.

What each part costs in noise. A chunk is one 16x16 fill (in-game about
2.8 ms of CPU each, from the cold block). A column is one exact column
(about 0.27 ms, from regionperf). The CPU column is an *estimate* from those
two rates.

| Part | Noise computed | Probe calls | CPU (est.) | Inside the city |
|---|---|---|---|---|
| The metropolis's own and the 24 nearby cells' region decisions | 8,453 columns | 67,200 | 2.3 s | 9 % |
| Region decisions for the road graph (419 more cells) | 44,666 columns | 353,579 | 12.1 s | 0 % |
| Link routes: lattice nodes | 6,243 columns | 32,466 | 1.7 s | 0 % |
| Link routes: stations | 14,587 columns | 30,768 | 3.9 s | 0 % |
| Streets and skeleton, 4 settlements (coarse lattice) | 8,825 columns | 1.03M | 2.4 s | 61 % |
| Junction fields, 4 settlements | 4,924 chunks + 156 columns | 153k exact, 287k coarse | 13.8 s | 96 % |
| Quays (metropolis and town s3_m8) | **19,854 chunks** + 435 columns | 598k exact, 975k coarse | **55.6 s** | 83 % |
| Centre tile plan | 54 chunks | 91k | 0.2 s | 100 % |
| **All** | 24,832 chunks + 83,365 columns | | **about 92 s** | |

The quays are new as a chunk count. Their 598k reads fill 19,854 chunks,
which is **65 % of the city's roughly 25,100 chunks**. Together with the
junction field, our probes fill about 84 % of the city's chunks before a
player arrives. The neighbour settlements cost little: they are small, and
together they account for 3 to 4 % of the junction fills. The road graph is
cheap per read, but it reaches very far.

## Your follow-up 2: reads that vanilla computes again

Our reads come before the chunks exist, so nothing can be shared in either
direction. What matters is how many of our reads land in chunks that vanilla
will generate anyway.

- **In the city** (66 % of the noise CPU, *estimate*): 16,405 quay chunks,
  4,731 junction chunks and 5,343 street columns. Vanilla computes their noise
  again **only when a player gets that close**. If a player explores the
  whole city, all of it is computed twice. A single flight across it
  generates a corridor about 35 chunks wide: 10.5 % of the quay chunks and
  11.2 % of the junction chunks fall inside the corridor of the metropolis
  flight (*estimate* from the path geometry; R4 measures it).
- **Outside the city** (34 %): all the road-graph region decisions, the
  route nodes and stations, and about 3,400 quay and junction chunks past
  the rim. These chunks get generated only if someone travels there, and
  most of them are kilometres away.
- **Measured, R3** (gate flight plus the two metropolis blocks): only 454 of
  108,197 units (0.4 %) were in chunks that run generated. The 54 chunks of
  the centre tile were all regenerated; almost nothing else was.

So the duplicated work is mostly city chunks that a visitor eventually
generates. The far reads are pure overhead until someone travels there.

## Win candidates, ranked by expected saving

| # | Change | Expected saving (from the profile) | Risk or trade-off |
|---|---|---|---|
| 1 | **Take the quay trace out of the chunk pipeline**: never run it inside a structure start on a vanilla worker. Run it ahead on our pool (RegionWarmer or TPHOLD), and have a start that would wait on it wait with a bound instead. | Removes the 104 s server freeze and the 71 s first chunk. CPU unchanged. | The output must not depend on timing: a start can't place a stub because the trace was late, or chunks differ by order. Needs a design that keeps determinism, such as making chunks near an unfinished settlement wait in the pipeline instead of on the server thread. M. |
| 2 | **Lazy quay trace** (regionperf option E): follow only the contour chains that reach the asking tile, plus the earlier runs that overlap them. | Up to about 55 s per waterfront metropolis cold, 35 s on the gate-city flight, 107 s on the metropolis flight. About 60 % of all our CPU in the measured phases. | Must keep `tile-border-agree`. M. Partly also a fix for candidate 1, because the first tile waits much less. |
| 3 | **Parallel quay trace** (option C), per water body or per contour chain. | Wall time divided by about 3 to 4 on the pool. CPU unchanged. | Competes with chunk generation for cores. S to M. |
| 4 | **Fewer far region decisions for the road graph**: cap the witness and neighbour chain, or settle edges near the point first and the far ones lazily. Decide the dice roll before reading any terrain (option D). | Up to 12 s of 92 s (*estimate*): 444 cells to 17 km against 25 nearby. Option D is also the 5.2 CPU-s the base world spends at frequency 0. | Changing the reach changes which links exist (ADR 0004 RC), so links near the edge could differ between chunks. Option D doesn't change output if the join rule never reads those cells' terrain (to check). S to M. |
| 5 | **Read the junction field from street columns, not whole chunks** (option F). | Up to about 10 of 14 s (*estimate*: 153k reads over 4,924 chunks is 31 columns per chunk). | Single columns cost about 25x a chunk's per-column share, so this only pays when a chunk has fewer than about 10 reads. Needs a per-chunk read count first. M. |
| 6 | **Memo for biome lookups on the coarse and region lattices** (`Climate$RTree` search). | Up to 9 % of flight CPU (about 10 to 15 s per flight). | regionperf dropped a lattice biome memo at under 4 % of the warm alone. The flight profile shows more: the street skeleton's coarse reads go through the same search. S. |
| 7 | **Save the decisions, junction fields and quays with the world** (option H). | About 92 s per cold metropolis after every restart. | A file format and invalidation on `genVersion`. M. |
| 8 | **Warm-ahead earlier for fast travel**: start RegionWarmer at a larger lead, scaled by speed. | Hides the cold cost behind the flight at elytra speed. 34 to 68 s of lead (B8) is less than the 75 to 107 s of work. | More speculative work for directions not taken. S. |
| 9 | **Warm chunks: building rasters** (`PieceRaster$Grid.union` is 11 % self of the warm block), `FoliageClear`, `HookPlacer`. | About 2 s of 4.5 s per 81 already-planned chunks. | Low priority; already within budget. S. |

Dropped, as you pointed out: reusing vanilla's heightmaps from generated
chunks. Our reads happen before those chunks exist. Also not worth pursuing:
GC (pauses are small), the chunk-fill method (the windowed fill is already
about 11 µs per column, far cheaper than single columns), and a coarser
water test (it would trade quay accuracy for at most the quay share, and
candidate 2 gets the same saving without that trade).

## Is one profile enough next time?

**Yes, for "is this vanilla work ours".** JFR's default stack depth of 64
frames is enough for that question: our nearest frame was always within the
top 64. But it cuts 82 % of the cold-block stacks, and that loses which part
of our planning asked (quays or junction field, for example). This study
used 1,024, and no stack was truncated. On the cold block a single profile gave the same answer as the
A/B difference to within 1 %.

**The A/B pair is still worth keeping for three things:**

1. indirect costs with no frame of ours: lighting, saving and entity ticking
   because of our blocks. These were 0.6 s of 4 s on warm chunks;
2. tick times and chunk-arrival times, against a reference;
3. counts: chunks per generation step, region-file size and heap.

**Don't compare CPU seconds between two runs on this machine.** The same
chunks cost vanilla 1.8x more CPU in one run than in the other. Compare work
counts instead, or pin both runs to the same cores (`FB_PROFILE_CPUS`,
untested here; see R5).

**Profile waits as well as CPU.** The worst problem was a 104 s wait. Only
tick times plus samples of waiting threads find it.
`scripts/profile.sh` records both, and `jdk.NativeMethodSample` and
`jdk.ThreadPark` are in the recordings.

## Not done

- **R4** (the metropolis flight on a quiet machine, with chunk events) and
  **R5** (pinned to P-cores, base world first) didn't run. Other agents'
  heavy jobs kept the load at 30 to 50. Commands:
  - R4: `FB_PERF_TARGET=metro scripts/evidence.sh profile scripts/profile.sh`
  - R5: `FB_PERF_VARIANTS="base mod" FB_PROFILE_CPUS=0,2,4,6,8,10,12,14 scripts/evidence.sh profile scripts/profile.sh`
  - Overlap: `scripts/profile-overlap.sh .claude/reports/profile-evidence/reach-7ba928e426/reach-units.tsv <R4 dir>/mod/build/perf/jfr`
- **Not checked:** whether the freeze also happens with a real client
  (`CatSpawner` runs the same way, so very likely), and how long it lasts on
  the perf worker.

## Tools

- `scripts/profile.sh` runs both worlds with per-phase JFR. It takes
  `FB_PERF_TARGET=metro`, `FB_PERF_VARIANTS` and `FB_PROFILE_CPUS`.
- `scripts/JfrAttrib.java` reads one recording and produces tables of ours
  against vanilla, by thread, part and pipeline step, plus hot methods,
  allocation, GC, Minecraft's chunk events, and `--chunks-out`.
- `scripts/profile-overlap.sh` joins our noise reads with the chunks a run
  generated.
- `PerfProfile` (game-test perf harness) and `PerfReachTest` (mod test,
  `-Dfb.perf.reach=1`) are measurement code only.
- `scripts/perf.sh --world-only` runs both world halves without the planner
  run.
