package dev.fractalbuildings.mod.perf;

import dev.fractalbuildings.core.cache.Fanout;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.probe.ChunkFill;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.Workers;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** The game's probes at this commit, for {@link ParReadsDigest}: PARREADS, over a reads pool as {@code Workers.start} makes it. */
final class ParReadsDigestProbes {
	private ParReadsDigestProbes() {}

	private static final ThreadPoolExecutor POOL = Workers.create(Math.max(1, Workers.readThreads()), Workers.READS_PREFIX);

	static WorldProbes game(NoiseBasedChunkGenerator gen, RandomState rs) {
		LiveProbes lp = new LiveProbes(gen, rs, ChunkFill.DEFAULT, LiveProbes.DEFAULT_CHUNKS, Fanout.over(() -> POOL, Workers.readThreads()));
		return lp.world(lp.exact());
	}
}
