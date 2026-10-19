package dev.fractalbuildings.core.gen.v1.model;

import dev.fractalbuildings.core.ir.Box3;
import java.util.Objects;

/**
 * One massing volume: a rectangular footprint [x, x + sx) x [z, z + sz) (local frame, walls
 * included) over grid storeys [from, to). It becomes an IR {@code Volume} whose box runs from the
 * slab of {@code from} up to and including the top slab at {@code grid.y(to)}.
 *
 * <ul>
 *   <li>{@code id} is the IR volume id; by convention kind plus a number ("main0", "wing1").
 *   <li>{@code kind}: "main", "wing", "tower", "podium", "shaft", "crown", "jetty", "setback",
 *       "annex", "hall"... Free tag; passes may key on it.
 *   <li>{@code parent}: the mass this one stands on (its {@code to} equals this {@code from}), or
 *       null for a ground mass ({@code from == 0}).
 *   <li>{@code open}: one tall space over all its storeys (no intermediate slabs: halls, naves,
 *       tower stair halls). It has one room and floors only at {@code from}.
 * </ul>
 */
public record Mass(String id, String kind, int x, int z, int sx, int sz, int from, int to, String parent, boolean open) {
	public static final int MIN_SIDE = 3;

	public Mass {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		if (sx < MIN_SIDE || sz < MIN_SIDE) throw new IllegalArgumentException("mass " + id + " footprint " + sx + "x" + sz);
		if (from < 0 || to <= from) throw new IllegalArgumentException("mass " + id + " storeys [" + from + ", " + to + ")");
	}

	public static Mass ground(String id, String kind, int x, int z, int sx, int sz, int storeys) {
		return new Mass(id, kind, x, z, sx, sz, 0, storeys, null, false);
	}

	public int maxX() { return x + sx; }

	public int maxZ() { return z + sz; }

	public boolean ground() { return from == 0; }

	/** Contains footprint column (cx, cz), walls included. */
	public boolean contains(int cx, int cz) { return cx >= x && cx < maxX() && cz >= z && cz < maxZ(); }

	/** Column (cx, cz) lies strictly inside the walls. */
	public boolean interior(int cx, int cz) { return cx > x && cx < maxX() - 1 && cz > z && cz < maxZ() - 1; }

	/** Mass occupies storey s. */
	public boolean spans(int s) { return s >= from && s < to; }

	/** Storey s has a floor slab in this mass (every spanned storey, or only {@code from} if open). */
	public boolean floorAt(int s) { return open ? s == from : spans(s); }

	/** The IR volume box: from the slab of {@code from} to the top slab at {@code grid.y(to)} inclusive. */
	public Box3 box(StoreyGrid g) { return Box3.of(x, g.y(from), z, sx, g.y(to) - g.y(from) + 1, sz); }

	/** Local y of the top slab. */
	public int topSlab(StoreyGrid g) { return g.y(to); }
}
