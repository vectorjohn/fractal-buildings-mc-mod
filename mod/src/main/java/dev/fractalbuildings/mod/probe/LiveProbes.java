package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.world.WorldProbes;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** The four live probes of one world over one shared memo. */
public final class LiveProbes {
	/**
	 * Chunks kept by the district memo (each is about 2 KB): 8192 chunks is about 17 MB and covers a 1.4 km square. A metropolis'
	 * {@code warm} reads its junction field over about 4,900 chunks (regionperf, 2026-10-27); at 4096 a quarter of its fills were refills.
	 */
	public static final int DEFAULT_CHUNKS = 8192;
	/** Sparse columns kept for region (stride 64) and coarse (stride 32) reads. */
	public static final int DEFAULT_SPARSE = 1 << 17;
	/** Single columns kept for road stations (about 450 reads per route). */
	public static final int DEFAULT_ROAD_COLUMNS = 1 << 16;

	private final LiveTerrainProbe exact;
	private final SparseColumns sparse;
	/** The road graph's station reads: single exact columns (stations are 8 apart, so whole chunks would be waste), own memo so the lattice reads keep theirs. */
	private final SparseColumns roadColumns;
	/** District reads: surface from the chunk memo. */
	private final LiveBiomeProbe biome;
	/** Region and coarse lattice reads: surface from single columns, no chunk fills. */
	private final LiveBiomeProbe sparseBiome;

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs) { this(gen, rs, ChunkFill.DEFAULT); }

	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs, ChunkFill fill) { this(gen, rs, fill, DEFAULT_CHUNKS); }

	/** {@code chunks}: the district memo's capacity (tests and benches; the game uses {@link #DEFAULT_CHUNKS}). */
	public LiveProbes(NoiseBasedChunkGenerator gen, RandomState rs, ChunkFill fill, int chunks) {
		this.exact = new LiveTerrainProbe(gen, rs, fill, chunks);
		this.sparse = new SparseColumns(gen, rs, DEFAULT_SPARSE);
		this.roadColumns = new SparseColumns(gen, rs, DEFAULT_ROAD_COLUMNS);
		this.biome = new LiveBiomeProbe(gen, rs, exact::surfaceY);
		this.sparseBiome = new LiveBiomeProbe(gen, rs, sparse::surface);
	}

	/**
	 * What a world's planner reads. The mod's {@code WorldPlanners} and the perf test both build it here, so the perf rows measure the
	 * game's probes (regionperf: the perf test had read road stations through the chunk memo, which the game never does).
	 * {@code exact} is {@link #exact()} or a wrapper of it (the perf test counts its calls).
	 */
	public WorldProbes world(TerrainProbe exact) {
		return new WorldProbes(region(), coarse(32), exact, biome(), null, roads());
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

	/** Exact heights at single columns for the road graph (same definition as {@link #exact}, bit for bit). */
	public TerrainProbe roads() {
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) { return roadColumns.floor(x, z); }
			@Override public int waterDepth(int x, int z) { return roadColumns.water(x, z); }
			@Override public int surfaceY(int x, int z) { return roadColumns.surface(x, z); }
		};
	}

	/** Vanilla noise generated so far, by kind (perf rows): whole chunks filled for district reads (refills included). */
	public long chunkFills() { return exact.memo().fills(); }

	/** Single lattice columns generated (region and coarse reads). */
	public long sparseColumns() { return sparse.misses(); }

	/** Single road-station columns generated. */
	public long roadColumns() { return roadColumns.misses(); }
}
