package dev.fractalbuildings.core.probe;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps probes and counts calls: exact terrain calls (floorY, waterDepth, surfaceY), biome calls and
 * coarse calls (through {@link #coarse}). Used to report and bound how many probes a district or
 * skeleton reads (ADR 0003 D7). Thread-safe; counts never affect the answers.
 */
public final class CountingProbe implements TerrainProbe, BiomeProbe {
	private final TerrainProbe terrain;
	private final BiomeProbe biome;
	private final AtomicLong exact = new AtomicLong(), biomeCalls = new AtomicLong(), coarseCalls = new AtomicLong();

	public CountingProbe(TerrainProbe terrain, BiomeProbe biome) {
		this.terrain = terrain;
		this.biome = biome;
	}

	@Override public int floorY(int x, int z) { exact.incrementAndGet(); return terrain.floorY(x, z); }

	@Override public int waterDepth(int x, int z) { exact.incrementAndGet(); return terrain.waterDepth(x, z); }

	@Override public int surfaceY(int x, int z) { exact.incrementAndGet(); return terrain.surfaceY(x, z); }

	@Override public String biome(int x, int z) { biomeCalls.incrementAndGet(); return biome.biome(x, z); }

	/** A coarse probe whose calls are counted here (as coarse calls). */
	public CoarseProbe coarse(CoarseProbe inner) {
		return new CoarseProbe() {
			@Override public int stride() { return inner.stride(); }
			@Override public int floorY(int x, int z) { coarseCalls.incrementAndGet(); return inner.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { coarseCalls.incrementAndGet(); return inner.waterDepth(x, z); }
			@Override public String biome(int x, int z) { coarseCalls.incrementAndGet(); return inner.biome(x, z); }
			@Override public boolean water(int x, int z) { coarseCalls.incrementAndGet(); return inner.water(x, z); }
		};
	}

	public long exactCalls() { return exact.get(); }
	public long biomeCalls() { return biomeCalls.get(); }
	public long coarseCalls() { return coarseCalls.get(); }

	public void reset() {
		exact.set(0);
		biomeCalls.set(0);
		coarseCalls.set(0);
	}
}
