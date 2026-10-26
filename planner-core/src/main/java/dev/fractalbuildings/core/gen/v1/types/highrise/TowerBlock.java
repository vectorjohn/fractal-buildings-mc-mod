package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;

/**
 * The core block of a high-rise in building (local) coordinates (plan docs/plans/highrises.md, sections 3
 * and 4): the stairwell (the 2-row {@link Core} plus a landing row on the hall side, and a guard row when
 * {@code sd == 4}) and a 3 x 3 lift shaft beside it, walled, with the hall in front of it. The core
 * {@code (kx, kz)} is the one of the {@code Massing}: {@code len} long along x, 2 deep.
 *
 * <p>Rows (z), with {@code g = sd - 2} rows in front of the core: the hall's rear partition at
 * {@code kz - g - 1}, the stairwell rows {@code kz - g .. kz + 1}, the back partition at {@code kz + 2}.
 * Columns (x) for an east shaft: end partition {@code kx - 1}, stairwell {@code kx .. kx + len - 1},
 * partition {@code kx + len}, shaft {@code kx + len + 1 .. kx + len + 3}, end partition {@code kx + len + 4}.
 * A west shaft mirrors it.
 *
 * <p>{@code sd} is the stairwell depth, one constant for the whole generator ({@link #SD}): 3 is an open
 * well beside the landing row, 4 adds a guard row (John's open question in the plan).
 */
public record TowerBlock(int kx, int kz, int len, boolean shaftEast, int hw, int sd) {
	/** Stairwell depth of the generator. */
	public static final int SD = 3;
	/** Shaft interior side. */
	public static final int SHAFT = 3;

	public TowerBlock {
		if (sd != 3 && sd != 4) throw new IllegalArgumentException("stairwell depth " + sd);
	}

	/** Rows in front of the core: 1 (sd 3) or 2 (sd 4). */
	public int g() { return sd - 2; }

	/** Block width in x with both end partitions: len + 6. */
	public int width() { return len + 6; }

	/** Block depth in z with the hall partition and the back partition: sd + 2. */
	public int depth() { return sd + 2; }

	/** First x of the block (its west end partition). */
	public int x0() { return shaftEast ? kx - 1 : kx - 5; }

	public int x1() { return x0() + width(); }

	public int z0() { return kz - g() - 1; }

	public int z1() { return kz + 3; }

	/** Stairwell interior: {x, z, sx, sz}. */
	public int[] stairwell() { return new int[] {kx, kz - g(), len, sd}; }

	/** Row of the guard screen ({@code sd == 4}), or -1. */
	public int guardRow() { return sd == 4 ? kz - 1 : -1; }

	/** Shaft interior: {x, z, sx, sz}, flush with the landing row. */
	public int[] shaft() { return new int[] {shaftEast ? kx + len + 1 : kx - 1 - SHAFT, kz - g(), SHAFT, SHAFT}; }

	/** First row of the hall interior; it ends before {@link #hallZ1()}. */
	public int hallZ0() { return kz - g() - 1 - hw; }

	/** Row of the partition between the hall and the block (hall interior is {@code [hallZ0, hallZ1)}). */
	public int hallZ1() { return kz - g() - 1; }

	/** Row of the partition between the hall and band B (the street side). */
	public int frontPartition() { return hallZ0() - 1; }

	/** The stair house footprint: the whole block with its walls. */
	public Mass stairHouse(String id, int from, String parent) {
		return new Mass(id, "stairhouse", x0(), z0(), width(), depth(), from, from + 1, parent, false);
	}

	/** The block of a built massing (its core and stair house), or null for the fallback box. */
	public static TowerBlock of(Massing m, int sd) {
		if (m.cores().isEmpty()) return null;
		Core k = m.cores().get(0);
		Mass sh = null;
		for (Mass a : m.masses()) if (a.kind().equals("stairhouse")) sh = a;
		if (sh == null) return null;
		return new TowerBlock(k.x(), k.z(), k.sx(), sh.x() == k.x() - 1, hallWidth(m.main()), sd);
	}

	/** Hall width: 2, or 3 when the podium interior is 30+ across both ways. */
	public static int hallWidth(Mass podium) { return podium.sx() - 2 >= 30 && podium.sz() - 2 >= 30 ? 3 : 2; }

	/** Rows of band B (street side units) in {@code tier}: from its front interior row to the hall's front partition. */
	public int bandBDepth(Mass tier) { return frontPartition() - (tier.z() + 1); }

	/**
	 * True if this block can stand in {@code tier}: band B (street side) is 3+ deep; the block's back is
	 * the tier's back wall, or 3+ interior rows lie behind it; at each end its partition is the exterior
	 * wall or 3+ interior cells lie between (room for a unit); and not against both walls when a room
	 * stands behind it.
	 */
	public boolean fits(Mass tier) {
		if (x0() < tier.x() || x1() > tier.maxX() || z0() < tier.z() + 1) return false;
		if (bandBDepth(tier) < 3) return false;
		boolean flushBack = tier.maxZ() == z1();
		if (!flushBack && tier.maxZ() < z1() + 4) return false;
		int left = x0() - tier.x(), right = tier.maxX() - x1();
		if (left != 0 && left < 4 || right != 0 && right < 4) return false;
		return flushBack || left != 0 || right != 0;
	}
}
