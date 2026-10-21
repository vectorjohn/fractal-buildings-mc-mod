package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;

/**
 * The world-wide district tile grid (Phase 5 plan, decision 1). Tiles are the rectangles between
 * hashed lines, chunk-aligned and 8 to 12 chunks (128 to 192 blocks) wide, the same everywhere whether
 * a settlement is there or not. A pure function of (seed, position): no probes, no state.
 *
 * <p>Line {@code k} on an axis sits at chunk {@code PERIOD * k + o(k)} with {@code o(k)} in
 * {@code [-JITTER, JITTER]}. Tile index {@code i} spans lines {@code i} (inclusive) to {@code i + 1}
 * (exclusive). The period and jitter are constants so decision 1 can be changed in one place.
 */
public final class DistrictGrid {
	/** Nominal tile size in chunks. */
	public static final int PERIOD = 10;
	/** Line offset range, chunks; tiles are PERIOD - 2*JITTER .. PERIOD + 2*JITTER wide. */
	public static final int JITTER = 1;
	/** Purpose string of the line hash (part of the output). */
	static final String PURPOSE = "tile-line";

	public static final int AXIS_X = 0; // a line of constant x (runs along z)
	public static final int AXIS_Z = 1; // a line of constant z (runs along x)

	private final long seed;

	public DistrictGrid(long seed) {
		this.seed = seed;
	}

	/** Tile index pair; ix counts lines along x, iz along z. */
	public record TileId(int ix, int iz) {}

	/**
	 * A tile: block rectangle [minX, maxX) x [minZ, maxZ), chunk aligned, with the chunk at its centre
	 * (the structure start chunk, ADR 0003 W1).
	 */
	public record Tile(int ix, int iz, int minX, int minZ, int maxX, int maxZ, int originChunkX, int originChunkZ) {
		public int widthX() { return maxX - minX; }
		public int widthZ() { return maxZ - minZ; }
		public boolean contains(int x, int z) { return x >= minX && x < maxX && z >= minZ && z < maxZ; }
		public TileId id() { return new TileId(ix, iz); }
	}

	/**
	 * A border segment: the piece of line {@code line} on {@code axis} between crossing lines
	 * {@code index} and {@code index + 1} of the other axis. {@code fixed} is the block coordinate of the
	 * line, {@code from}..{@code to} the block extent along it. Both neighbouring tiles see the same
	 * (axis, line, index) key, so {@link #hash} agrees.
	 */
	public record Segment(int axis, int line, int index, int fixed, int from, int to) {
		public long hash(long seed, String purpose) {
			return Hash64.hash(seed, purpose, axis, line, index);
		}
	}

	/** Chunk coordinate of line k (same function for both axes, hashed per axis). */
	public int lineChunk(int axis, int k) {
		return PERIOD * k + Hash64.below(Hash64.hash(seed, PURPOSE, axis, k), 2 * JITTER + 1) - JITTER;
	}

	/** Block coordinate of line k. */
	public int lineBlock(int axis, int k) {
		return lineChunk(axis, k) * 16;
	}

	/** Index of the tile along one axis containing block coordinate c (O(1): lines around floorDiv). */
	public int indexOf(int axis, int c) {
		int chunk = Math.floorDiv(c, 16);
		int k = Math.floorDiv(chunk, PERIOD) + 1; // lines k-2..k are enough; start high and walk down
		while (lineChunk(axis, k) > chunk) k--;
		return k;
	}

	public TileId tileOf(int x, int z) {
		return new TileId(indexOf(AXIS_X, x), indexOf(AXIS_Z, z));
	}

	public Tile tile(int ix, int iz) {
		int x0 = lineChunk(AXIS_X, ix), x1 = lineChunk(AXIS_X, ix + 1);
		int z0 = lineChunk(AXIS_Z, iz), z1 = lineChunk(AXIS_Z, iz + 1);
		return new Tile(ix, iz, x0 * 16, z0 * 16, x1 * 16, z1 * 16, x0 + (x1 - x0) / 2, z0 + (z1 - z0) / 2);
	}

	public Tile tileAt(int x, int z) {
		TileId id = tileOf(x, z);
		return tile(id.ix(), id.iz());
	}

	/**
	 * The tile a column belongs to (D5 ownership): the tile containing it. A road centred on a line is
	 * split along the line, each half owned by its own side.
	 */
	public Tile owner(int x, int z) {
		return tileAt(x, z);
	}

	/** The four border segments: west, east (axis x lines ix, ix+1), north, south (axis z lines iz, iz+1). */
	public Segment[] segments(int ix, int iz) {
		Tile t = tile(ix, iz);
		return new Segment[] {
			new Segment(AXIS_X, ix, iz, t.minX(), t.minZ(), t.maxZ()),
			new Segment(AXIS_X, ix + 1, iz, t.maxX(), t.minZ(), t.maxZ()),
			new Segment(AXIS_Z, iz, ix, t.minZ(), t.minX(), t.maxX()),
			new Segment(AXIS_Z, iz + 1, ix, t.maxZ(), t.minX(), t.maxX()),
		};
	}
}
