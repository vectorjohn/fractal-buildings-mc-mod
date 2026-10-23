package dev.fractalbuildings.mod.worldgen;

/**
 * Test switches that break the pieces on purpose, so the order and ownership tests can show they fail (plan B1 / B2 "mutants kept as
 * switches", as S3 did). Never set outside tests; the default is off and costs one volatile read per piece.
 */
public final class Mutations {
	/** Mix the {@code postProcess} random into the written blocks: digests then depend on the chunk order. */
	public static final String RANDOM = "random";
	/** Write the piece's cells outside the chunk being generated too. */
	public static final String NO_CHUNK_CLIP = "no-chunk-clip";

	/** Write with flag 2 alone, so the world runs neighbour shape updates (fences, panes, walls, doors, stairs re-shape in write order). */
	public static final String FLAG2 = "flag2";
	/** Skip every air write: interiors that started solid stay solid. */
	public static final String SKIP_AIR = "skip-air";
	/** Hidden global state: every 7th solid block written is stone bricks, picked by a counter shared by all pieces. */
	public static final String COUNTER = "counter";
	/** Each ground cell is claimed twice (a stand-in for two boxes owning one cell; the raster's ownership rules make a real overlap impossible). */
	public static final String TWICE = "twice";
	/** The structures run in {@code surface_structures}, before vegetation, so vanilla trees grow into them (S3). */
	public static final String EARLY_STEP = "early-step";

	/** The world gets stone in some solid cells while the ledger records the correct state, so only a world-versus-raster check can see it. */
	public static final String WORLD_STONE = "world-stone";

	public static volatile String mode;
	/** The shared counter of {@link #COUNTER}. */
	static final java.util.concurrent.atomic.AtomicInteger COUNTER_VALUE = new java.util.concurrent.atomic.AtomicInteger();

	private Mutations() {}

	static boolean is(String m) { return m.equals(mode); }
}
