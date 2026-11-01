package dev.fractalbuildings.mod.teleport;

/**
 * Which chunks vanilla's {@code PortalForcer.createPortal} (26.3, lines 52-153) reads for an exit at a block column, for {@link PortalHold}.
 * Pure (no Minecraft classes), so a unit test can check it against a re-run of the vanilla loops ({@code PortalSearchTest}).
 *
 * <p>The search visits {@code BlockPos.spiralAround(origin, 16, EAST, SOUTH)}: every column within 16 blocks on both axes. At a candidate
 * column, {@code canHostFrame} reads {@code width} -1..2 along the portal's positive direction and {@code offset} -1..1 across it; the frame
 * it then writes (width -1..2) updates its neighbours, one block further. So the reads stay within -18..+19 blocks along the portal axis
 * (+x for an x-axis portal, +z for a z-axis one) and -17..+17 across it.
 */
public final class PortalSearch {
	/** PortalForcer's spiral radius. */
	public static final int SPIRAL = 16;
	/** Lowest block offset read along the portal's direction: spiral, frame width -1, neighbour update -1. */
	static final int LOW_ALONG = -(SPIRAL + 2);
	/** Highest block offset read along the portal's direction: spiral, frame width 2, neighbour update +1. */
	static final int HIGH_ALONG = SPIRAL + 3;
	/** Farthest block offset read across it, both ways: spiral, frame offset 1 (or the neighbour of a frame block). */
	static final int ACROSS = SPIRAL + 1;

	private PortalSearch() {}

	/** {minChunkX, maxChunkX, minChunkZ, maxChunkZ} of the chunks read for an exit at block (x, z) through a portal on the x (or z) axis. */
	public static int[] chunks(int x, int z, boolean axisX) {
		return new int[] {
			Math.floorDiv(x + (axisX ? LOW_ALONG : -ACROSS), 16), Math.floorDiv(x + (axisX ? HIGH_ALONG : ACROSS), 16),
			Math.floorDiv(z + (axisX ? -ACROSS : LOW_ALONG), 16), Math.floorDiv(z + (axisX ? ACROSS : HIGH_ALONG), 16)};
	}
}
