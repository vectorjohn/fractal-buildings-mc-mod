# COLDCHUNK: what the 70-120 s cold chunk is doing

Question: one of 81 chunks in the perf cold metropolis block takes 70-120 s (`mod.cold.chunk.max.ms`), the rest ~35 ms.

Method: `PerfSupport.generate` got a daemon watchdog that, once a chunk exceeds 4 s, dumps the stacks of busy Worker/Server threads every 5 s, and prints `[COLDCHUNK] chunk#i (cx,cz) took N ms`. Run: `scripts/perf.sh --world-mod-only` (local, load 4-6 at start, FB_LANE_LOG=1), commit 3b-watchdog on 620ca7fd+.

Answer (`mod/build/perf/world-mod.log`, this run: chunk max 108,277 ms, cold block start max 738 ms):
- It is chunk #0, the first chunk asked (148,-360). Chunk #6 took 1.1 s, all others about 70 ms median.
- Yes, synchronous: the server thread waits in `ServerChunkCache.getChunk` for a worker-run start, whose first step is `Districts.plan` for the tile. All 21 dumps show exactly one busy worker (Worker-Main-7) inside that plan: the stack is `Build.run -> quays -> settlementQuays -> Quays.contour / quayOk -> wt / waterLevel -> LiveTerrainProbe.waterDepth -> ColumnMemo -> ChunkFill.Batched.scan -> Aquifer.computeSubstance / PerlinNoise` (noise generation dominates the stalls).
- Not lock contention: no other worker was busy during the stall, and the counters show 1% inline lookups, start creation max 738 ms.
- Not the region layer: that is `region.cold.warm.ms` (~30 s), a different row, paid before the block in the perf run. The stall is the settlement-wide quay trace (`region.cold.quays.ms` 56-70 s; PERFORMANCE.md "Quays front-load probes on a waterfront settlement's first tile"; METROFILL R1 r3 made the first waterfront tile pay for the whole city's quays).
- The cold ratio (~33) is that one outlier; without it the cold block is ~1.0x the base.

Fix: not small. It is single threaded, ~600k exact reads. Options for John's REGIONPERF decision: (1) trace quays lazily from the asking tile (the PERFORMANCE.md "Improve" idea); (2) warm quays on the worker pool ahead of the first start (`warmQuays` exists; prefetch from the TPHOLD/region warmer) so the first chunk finds it done, and make the perf cold block call it like the game's hold does; (3) split the trace by water body and fill chunk columns in parallel on the pool; (4) coarser water test for the contour (the 4-lattice `wt`/12-block `waterLevel` points are the cost). No code fix made.
