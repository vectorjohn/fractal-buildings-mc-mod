# Spike S10, planner half: roads between settlements

Branch `claude/spike-s10-planner`; spec [intercity-roads.md](../../plans/intercity-roads.md), "The spike (S10)" half 1. The
mod half is in [s10-mod.md](s10-mod.md). Spike code (`planner-core/.../spike/s10/RoadNet.java`, CLI `roads`,
`RoadNetTest`) is throwaway and not merged; this note, its numbers and the PNGs are what lands. Run on the two recorded
world dumps (24,576 blocks square, stride 64), cell size 1024, join rule on, host load average about 12 (20 CPUs).

# Question

Can any chunk answer "which inter-settlement road columns lie in me, and at what deck height" from a bounded, cached read
of the region field, byte-identical in any order and with evicting caches, cheap enough for the serial lane, and do the
roads look like roads on real terrain?

# Answer

**Yes on determinism and on cost per chunk; yes on looks at region and street scale; two design points need deciding
before 6.10** (the read radius, and where the exact-height probes are paid). Pass criteria of the plan: both determinism
checks hold (test below), at least 90 % of settlements linked (91 % and 91 % of all, 93 % and 94 % away from the window
edge), per-road-chunk cost far under 5 ms (warm: 0.0 ms; first touch of a region cell 1 ms mean on recorded probes).

# What was built

- **Settlements** = resolved cells with no `claimedBy` (joined cities are one node: centre and drawn radius), read through
  `FieldRegionView.cell`. Only cells the dump can decide are used.
- **Graph** = relative neighbourhood graph on centres (no third settlement closer to both than they are to each other),
  kept when the gap `|AB| - rA - rB <= Lgap` (1536). The test reads settlements within `|AB|` of A only, so it is local and
  symmetric (both ends find the same edge).
- **Ports** on the circle `r + 64` at the bearing to the other end, snapped to the 64 lattice; water, peak (> y 140) or inside
  another footprint + 32 gives up to 4 hashed tries (hash of seed, both cells, try).
- **Route** = `LatticePath` A* on the 64 lattice with `defaultCost` (slope 6/block of height, 40 per water node), water runs
  up to 96 blocks (bridges), other footprints + 32 forbidden, own footprints forbidden, search band = ports' box grown by
  `max(256, 0.3 |AB|)`. Then string-pull (drop lattice nodes while the 16-block-sampled chord stays within 6 blocks of height
  per sample, no new water run over 96, no footprint), centripetal Catmull-Rom, stations every 8 blocks, deck = box average
  of the surface height over +-8 stations (a bridge station keeps at least the water surface), columns rasterised within
  `width/2` of the curve (width 3 hamlet, 4 village, 5 town, 7 city to city), bucketed by chunk.
- **Chunk query** `query(chunkX, chunkZ)`: region cell of the chunk, cached list of edges whose port box meets the cell
  (`edgesNearCell`), each edge's cached route, the chunk's bucket, merged by column (lower edge key wins on overlap),
  sorted by (z, x). Every memo is a `BoundedCache` with a capacity parameter.

# Evidence

## Pictures (opened and looked at)

- [roads-12345.png](s10/roads-12345.png), [roads-987654321.png](s10/roads-987654321.png): whole world, 16 blocks per pixel.
  Circles are footprints (tier colour: yellow hamlet, orange village, red town, purple city, black metropolis), yellow lines
  roads with black edge, cyan = bridge stations, red dashed = RNG edge that did not route. **What I see:** a sparse network, not
  a mesh. Towns in a landmass form chains and small loops, roads bend round lakes and along coasts, cross narrow water as
  short cyan bridges, and stop at a ring just outside each footprint. Islands and settlements across wide sea stay alone. A
  few red dashes sit inside or beside big discs (see Weak spots). A couple of roads make a hook or a U (for example near
  (390, 470) and (1380, 1105) px on the second world) where a lake or footprint forces a detour; they read as odd.
- [roads-12345-zoom.png](s10/roads-12345-zoom.png): 4 blocks per pixel. Roads read as roads here: smooth curves, short
  straight runs where the land is flat, bridges over ponds, nothing in any footprint. Ports sit 64 blocks off the footprint,
  so each road ends in a gap that the settlement's own exit road must close.

## Numbers

| | seed 12345 | seed 987654321 |
|---|---|---|
| settlements (cells with a settled owner) | 152 (57 hamlet, 48 village, 29 town, 14 city, 4 metro) | 150 (43 / 56 / 35 / 13 / 3) |
| RNG edges within the gap limit | 140 | 140 |
| routed / unroutable | 129 / 11 | 129 / 11 |
| unroutable, routable if the 96-block water cap is lifted | 5 | 6 |
| settlements linked | 139 of 152 = 91.4 % | 136 of 150 = 90.7 % |
| linked, 119 / 129 settlements at least 2048 from the window edge | 93.3 % | 93.8 % |
| unlinked: no RNG edge within `Lgap` / edges all failed | 9 / 4 | 11 / 3 |
| routes crossing another footprint (route curve vs disc, not +32) | **0** | **0** |
| routes with at least one bridge station | 95 of 129 | 100 of 129 |
| edge length, centre to centre, per 500 blocks | 0-500: 0, 500: 43, 1000: 67, 1500: 24, 2000: 5, 2500: 1 | 0: 1, 500: 42, 1000: 70, 1500: 20, 2000: 5, 2500: 2 |
| route length p50 / p95 / max (blocks) | 777 / 1554 / 2259 | 766 / 1631 / 2356 |
| deck grade (max per route) p50 / p95 / max | 0.3 / 0.5 / 0.8 | 0.3 / 0.7 / 1.2 |
| road columns in the world | 364,452 | 368,862 |
| edge time, recorded probes (mean / p95 / max ms) | 1.5 / 3.6 / 21 (A* alone 0.2 / 1.0 / 3.5) | 1.3 / 2.7 / 21 (A* 0.2 / 0.6 / 3.5) |
| coarse probe calls per edge (mean / p95 / max) | 2,693 / 8,296 / 14,484 | 2,738 / 7,684 / 14,668 |
| exact-height probes per routed edge (mean / p95 / max) | 645 / 1,834 / 4,432 | 619 / 1,904 / 3,624 |
| all road chunks (columns) | 10,197 | 10,080 |
| first query of a region cell (cell list and routes), 173 / 169 cells, mean / p95 / max ms | 1.0 / 3.5 / 14.8 | 0.9 / 2.7 / 13.6 |
| every later road chunk, warm | 0.0 ms (p95 0.0, max 0.1) | 0.0 ms (max 0.1) |
| cold chunk (fresh net and fresh `FieldRegionView`), 12 chunks, mean / max ms | 27.7 / 54.6 | 29.1 / 44.1 |
| read radius: farthest cell site scanned from the chunk (12 cold chunks) | 14.5 to 15.5 km | 16.5 to 18.1 km |

Reading the numbers:

- **Linked share.** All "land" settlements: the 9 to 11 unlinked with no edge are real islands or settlements more than 1536
  blocks (gap) from any neighbour across sea; no edge is the intended outcome. The edge-failing ones (3 to 4) all have a
  water gap over 96 or a blocked port (see below). Window-edge effects (neighbours outside the dump are unknown) cost the
  other 2 to 3 points. The 90 % bar is met but with 1 to 4 points of slack, so it depends on `Lgap` and the world's sea share (the dumps
  are about 58 % land); a land-heavy world would score higher.
- **Cost, with live probes.** On recorded probes an edge costs about 1.5 ms. The probe counts decide the live cost: 2.7k coarse
  calls x 17 us = about 46 ms mean (p95 140 ms, max 250 ms) per edge, and 645 exact probes x 6 to 28 us (sampled surface) = 4
  to 18 ms, or x 150 to 380 us (`getFirstFreeHeight`) = 100 to 250 ms. The coarse part is the same kind of cost the region
  decisions already pay (11 ms per cell). Per cell: 0.24 edges per cell on these worlds (140 edges / 576 cells), so
  about 12 ms of coarse probing per region cell, once, then cached.
- **Per chunk.** Once the cell's edge list and the edges' routes are cached, a chunk is a map lookup and a merge of at most
  a few hundred columns: 0.0 ms. The cost is all in the first touch of an edge (routing, shaping, rasterising), which the
  serial lane pays in one lump for the whole edge.
- **Read radius.** The scan reaches 14.5 to 18 km from the chunk. That is the sum of: cell reach for an edge band
  (`1.3 (Lgap + 2 rmax)`), then `edgesOf` for those settlements (`Lgap + rA + rmax`). It is driven by `rmax`, the largest
  settlement radius the world can produce (1,340 and 1,769 observed here; the live maximum is 4,096, which would make it
  about 3 times larger). The region view's own `readRadius()` is 13.6 km already, so the road read is the same order as the
  join rule's. All of it is memoised per region cell (`BoundedCache`), not per chunk.

# Determinism (test `RoadNetTest`)

`chunkQueriesAreOrderAndCacheIndependent` (both worlds, about 20 s): about 50 to 60 chunks (3 per sampled route, plus
neighbours and empty ones) answered by a reference net, then by fresh nets in 3 shuffled orders (identical lists of
`(x, z, deckY, bridge, width)`), by a net with capacity 1 on every road cache (settlements, edge lists, routes, cell lists;
25 chunks shuffled), and by a net with capacity 1 on the road caches and on the region view's memos (2 chunks; the join rule
recomputes hundreds of cells per call). All equal. `networkInvariants` checks 0 footprint crossings, all columns inside the
band box, at least 90 % of the non-edge settlements linked. `controlDifferentGapGivesDifferentAnswers` shows the comparison
can fail. Mutation: adding `routes.misses() % 2` to the deck height (a cache-state dependence) makes the shuffle comparison
fail at once, then reverted.

# What it means for the design

1. **Plan (a) works**, and the per-chunk path is trivial once routes are cached. The route cache is the thing that matters:
   with capacity 1 the answers stay right but every chunk reroutes its edges (1.5 ms here, 50 to 250 ms live), so the cache
   must hold at least the edges of the cells around the generating chunks (a few hundred entries is plenty: 140 edges per world).
2. **Pay the exact probes where the plan says**, in the chunk, not per edge. This spike computes the whole profile at route
   time (645 exact probes per edge, of which about 200 are stations and the rest string-pull samples). Live with
   `getFirstFreeHeight` that is 100 to 250 ms on the first chunk of an edge. Either use the sampled surface level (4 to 18 ms), or
   follow the plan's fixed-window profile (stations computed per chunk from the chunk's own reach, +-8 stations of smoothing),
   and do the string-pull on the coarse lattice only (drop the 16-block chord heights: use the coarse surface). The spike
   did not implement the per-chunk profile; it is a pure function of the curve, so it is the same numbers by construction
   but should get its own test.
3. **The read radius needs a cap.** It scales with `rmax`. Cap the radius a settlement may have for road purposes (for example
   the road reaches the footprint circle clamped to 1536), or put the graph in a coarser structure keyed by region cell that
   stores only ids, so edge existence is read from a bounded neighbourhood independent of rmax. As built, reading 15 to 50 km
   around a chunk is only workable because it is a per-cell cache.
4. **Touching footprints fail** (not verified edge by edge; the red dashes inside discs on the PNGs suggest it): RNG edges between touching or overlapping discs (a small settlement inside a city
   ring) have both ports inside the other disc's forbidden zone and fail; that is correct (no road needed) and should be
   dropped from the graph by a rule (centres closer than `rA + rB + 128`: merge, no edge). One degenerate edge in world 2 has
   identical ports (route length 0).
5. **Bridges are everywhere** (74 and 78 % of routes have at least one wet station). The dumps are very pond-heavy (depth 2 or more at
   64-block sampling), so the 40-block bridge limit and a bridge piece design matter more than the plan assumes. Short bridges
   only; a 96-block cap rescued 5 to 6 of the 11 failed edges, so wider water is the main cause of unroutable edges.

# Weak spots (not fixed, spike quality)

- A few roads hook or U-turn; probably string-pull being conservative at the 6-block step limit, or detours round a ring. Tuning
  work for 6.10, plus an angle penalty for approaches.
- Ports always sit 64 blocks out; the town's exit must connect. No SK exit logic exists in the spike.
- Fine heights (`w12345-river-s8`) were not used (not checked whether a route crosses that 2 km window); heights come from the
  stride-64 dump interpolated bilinearly (`RecordedProbe`), so deck heights and grades are smoother than real terrain.
  Re-measure grades and bridge share on live terrain in the mod half.
- Window-edge settlements (decidable but with unseen neighbours) are included; their edges can differ from the unbounded world.
- Column rasterisation takes the nearest segment; overlaps between two edges at a shared junction are resolved by edge key and
  not blended.

# How to re-implement properly

Keep the graph, ports and route stages as written (each pure, cached per key). Replace the whole-edge profile with a per-chunk
profile from stations in the chunk's reach. Cap `rmax` for roads. Test as `RoadNetTest` does: reference net, shuffled orders,
capacity 1 on every memo, and a mutation that makes an answer depend on cache state. Re-run the CLI report
(`fractal-cli roads --seed S --terrain recorded:fixtures/terrain/world-S-s64.json.gz --png OUT [--zoom X,Z,SIZE --zoom-png OUT]`
on the spike branch) for the numbers above.
