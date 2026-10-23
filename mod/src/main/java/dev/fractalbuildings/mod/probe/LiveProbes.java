package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionProbe;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** The four live probes of one world over one shared memo. */
public final class LiveProbes {
	/** Chunks kept by the district memo (each is 4 KB): 4096 chunks is 16 MB and covers a 1 km square. */
	public static final int DEFAULT_CHUNKS = 4096;
	/** Sparse columns kept for region (stride 64) and coarse (stride 32) reads. */
	public static final int DEFAULT_SPARSE = 1 << 17;

	private final LiveTerrainProbe exact;
	private final SparseColumns sparse;
	private final LiveBiomeProbe biome;

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs) { this(gen, rs, ChunkFill.DEFAULT); }

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs, ChunkFill fill) {
		this.exact = new LiveTerrainProbe(gen, rs, fill, DEFAULT_CHUNKS);
		this.sparse = new SparseColumns(gen, rs, DEFAULT_SPARSE);
		this.biome = new LiveBiomeProbe(gen, rs, (x, z) -> sparse.get(x, z)[0]);
	}

	public TerrainProbe exact() { return exact; }

	public LiveTerrainProbe exactLive() { return exact; }

	public BiomeProbe biome() { return biome; }

	/** Region lattice reads (any column, memoised singly; the region layer only reads multiples of {@link RegionProbe#STRIDE}). */
	public RegionProbe region() {
		return new RegionProbe() {
			@Override public int floorY(int x, int z) { return sparse.get(x, z)[1]; }
			@Override public int waterDepth(int x, int z) { int[] c = sparse.get(x, z); return c[0] - c[1]; }
			@Override public String biome(int x, int z) { return biome.biome(x, z); }
		};
	}

	/** Coarse-lattice reads at the given stride, exact values (decision 1: no screen). */
	public CoarseProbe coarse(int stride) {
		TerrainProbe t = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return sparse.get(x, z)[1]; }
			@Override public int waterDepth(int x, int z) { int[] c = sparse.get(x, z); return c[0] - c[1]; }
		};
		return CoarseProbe.of(t, biome, stride);
	}

	public SparseColumns sparse() { return sparse; }
}
