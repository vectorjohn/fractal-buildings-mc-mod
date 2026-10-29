package dev.fractalbuildings.mod.probe;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.cache.Fanout;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.worldgen.Workers;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

/** PARREADS: chunks filled in parallel on vanilla noise are the chunks a serial read fills, each filled once, and a serial prefetch does nothing. */
class LiveReadsTest {
	static final long SEED = 987654321L;
	/** A 6 x 5 chunk box of the temperate gate window (river and bank). */
	static final int[] BOX = {1280, -9600, 1280 + 6 * 16 - 1, -9600 + 5 * 16 - 1};

	@Test
	void parallelFillsMatchSerialReadsAndFillEachChunkOnce() {
		var gen = TerrainDumpTool.generator();
		var rs = TerrainDumpTool.randomState(SEED);
		ThreadPoolExecutor pool = Workers.create(4, Workers.READS_PREFIX);
		try {
			LiveProbes par = new LiveProbes(gen, rs, ChunkFill.DEFAULT, 256, Fanout.over(() -> pool, 4));
			par.reads().prefetch(List.of(BOX, BOX)); // twice: the second box adds nothing
			assertEquals(30, par.chunkFills(), "each chunk of the box filled once");
			par.reads().prefetch(List.of(BOX));
			assertEquals(30, par.chunkFills(), "a filled chunk is not filled again");
			LiveProbes ser = new LiveProbes(gen, rs);
			for (int z = BOX[1]; z <= BOX[3]; z += 3) {
				for (int x = BOX[0]; x <= BOX[2]; x += 3) {
					assertEquals(ser.exact().floorY(x, z), par.exact().floorY(x, z), "floor " + x + "," + z);
					assertEquals(ser.exact().waterDepth(x, z), par.exact().waterDepth(x, z), "water " + x + "," + z);
				}
			}
			assertEquals(30, par.chunkFills(), "the reads after the prefetch are all hits");
		} finally {
			Workers.shutdown(pool);
		}
	}

	@Test
	void aSerialFanoutPrefetchesNothing() {
		LiveProbes ser = new LiveProbes(TerrainDumpTool.generator(), TerrainDumpTool.randomState(SEED));
		ser.reads().prefetch(List.of(BOX));
		assertEquals(0, ser.chunkFills(), "serial: filling ahead would only add chunks the planner may never read");
	}

	@Test
	void aPrefetchFillsAtMostHalfTheMemo() {
		ThreadPoolExecutor pool = Workers.create(2, Workers.READS_PREFIX);
		try {
			LiveProbes par = new LiveProbes(TerrainDumpTool.generator(), TerrainDumpTool.randomState(SEED), ChunkFill.DEFAULT, 16, Fanout.over(() -> pool, 2));
			par.reads().prefetch(List.of(BOX));
			assertEquals(8, par.chunkFills());
		} finally {
			Workers.shutdown(pool);
		}
	}

	@Test
	void readThreadsDefaultIsBounded() {
		int n = Workers.readThreads();
		assertTrue(n >= 0 && n <= 32, "readThreads " + n);
		if (System.getProperty("fractalbuildings.readThreads") == null) assertTrue(n >= 1 && n <= 6);
	}
}
