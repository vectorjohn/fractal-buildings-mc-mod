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

	public static volatile String mode;

	private Mutations() {}

	static boolean is(String m) { return m.equals(mode); }
}
