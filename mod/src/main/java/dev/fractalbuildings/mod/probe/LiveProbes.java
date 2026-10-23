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
	/** District reads: surface from the chunk memo. */
	private final LiveBiomeProbe biome;
	/** Region and coarse lattice reads: surface from single columns, no chunk fills. */
	private final LiveBiomeProbe sparseBiome;

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs) { this(gen, rs, ChunkFill.DEFAULT); }

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs, ChunkFill fill) {
		this.exact = new LiveTerrainProbe(gen, rs, fill, DEFAULT_CHUNKS);
		this.sparse = new SparseColumns(gen, rs, DEFAULT_SPARSE);
		this.biome = new LiveBiomeProbe(gen, rs, exact::surfaceY);
		this.sparseBiome = new LiveBiomeProbe(gen, rs, sparse::surface);
	}

	public TerrainProbe exact() { return exact; }

	public LiveTerrainProbe exactLive() { return exact; }

	public BiomeProbe biome() { return biome; }

	/** Region lattice reads (any column, memoised singly; the region layer only reads multiples of {@link RegionProbe#STRIDE}). */
	public RegionProbe region() {
		return new RegionProbe() {
			@Override public int floorY(int x, int z) { return sparse.floor(x, z); }
			@Override public int waterDepth(int x, int z) { return sparse.water(x, z); }
			@Override public String biome(int x, int z) { return sparseBiome.biome(x, z); }
		};
	}

	/** Coarse-lattice reads at the given stride, exact values (decision 1: no screen). */
	public CoarseProbe coarse(int stride) {
		TerrainProbe t = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return sparse.floor(x, z); }
			@Override public int waterDepth(int x, int z) { return sparse.water(x, z); }
		};
		return CoarseProbe.of(t, sparseBiome, stride);
	}

	public SparseColumns sparse() { return sparse; }
}
