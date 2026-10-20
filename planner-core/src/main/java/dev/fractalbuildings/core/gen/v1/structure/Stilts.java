package dev.fractalbuildings.core.gen.v1.structure;

import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import java.util.ArrayList;
import java.util.List;

/**
 * A building raised on stilts: the passes build the raised part (a room, its roof, its door) in its
 * own frame with its floor at local y 0, and assembly lifts everything by {@code lift} and adds, under
 * it, four corner posts of the raised part's footprint, and their bracing. The way up is the site pass's front steps: a flight of foundation-backed steps from the door down to the terrain ({@code InstancePassV1}), as for any entrance.
 *
 * <p>Why the invariants need no exemption: storey 0 of the assembled building is the open leg
 * zone (y 0 to lift), the raised part's storeys follow from index 1, and its volumes start above
 * storey 0, which {@code no-gap-below} does not look at. The legs are four 1 x 1 volumes of their own,
 * each with a solid column from the ground, so a leg that is missing or short fails
 * {@code no-gap-below}; the room is connected to the ground through the legs, so
 * without them it fails {@code no-floating}.
 *
 *
 * @param lift height of the leg zone: the raised part's floor slab is at y {@code lift}
 * @param braced cross braces in every face between the posts (the top ring under the floor is always there)
 */
public record Stilts(int lift, boolean braced) {
	public static final int MIN_LIFT = 4;

	public Stilts {
		if (lift < MIN_LIFT) throw new IllegalArgumentException("lift " + lift);
	}

	/** The four leg volumes (ids leg0..leg3: north-west, north-east, south-west, south-east). */
	public List<Volume> volumes(Mass part) {
		List<Volume> out = new ArrayList<>();
		int i = 0;
		for (int[] p : posts(part)) out.add(new Volume("leg" + i++, Box3.of(p[0], 0, p[1], 1, lift, 1), null));
		return out;
	}

	private static int[][] posts(Mass m) {
		return new int[][] {{m.x(), m.z()}, {m.maxX() - 1, m.z()}, {m.x(), m.maxZ() - 1}, {m.maxX() - 1, m.maxZ() - 1}};
	}

	/**
	 * Posts and bracing, in the assembled frame (the raised part's floor slab at y {@code lift}).
	 */
	public List<Box> boxes(Mass part) {
		List<Box> out = new ArrayList<>();
		for (int[] p : posts(part)) add(out, Role.BEAM, p[0], 0, p[1], 1, lift, 1);
		// the ring under the floor edge, between the posts
		int top = lift - 1;
		add(out, Role.BEAM, part.x() + 1, top, part.z(), part.sx() - 2, 1, 1);
		add(out, Role.BEAM, part.x() + 1, top, part.maxZ() - 1, part.sx() - 2, 1, 1);
		add(out, Role.BEAM, part.x(), top, part.z() + 1, 1, 1, part.sz() - 2);
		add(out, Role.BEAM, part.maxX() - 1, top, part.z() + 1, 1, 1, part.sz() - 2);
		if (braced) {
			for (boolean rising : new boolean[] {true, false}) {
				brace(out, part.x() + 1, part.sx() - 2, part.z(), true, rising);
				brace(out, part.x() + 1, part.sx() - 2, part.maxZ() - 1, true, rising);
				brace(out, part.z() + 1, part.sz() - 2, part.x(), false, rising);
				brace(out, part.z() + 1, part.sz() - 2, part.maxX() - 1, false, rising);
			}
		}
		return out;
	}

	/** One diagonal in a face between two posts: a 4-connected staircase of beam cells from y 1 to lift - 3 (or back). */
	private void brace(List<Box> out, int from, int n, int fixed, boolean alongX, boolean rising) {
		int lo = 1, hi = lift - 3;
		int prev = lo;
		for (int i = 0; i < n; i++) {
			int pos = rising ? i : n - 1 - i;
			int y = lo + (n == 1 ? 0 : i * (hi - lo) / (n - 1));
			int y0 = Math.min(prev, y), y1 = Math.max(prev, y);
			prev = y;
			if (alongX) add(out, Role.BEAM, from + pos, y0, fixed, 1, y1 - y0 + 1, 1);
			else add(out, Role.BEAM, fixed, y0, from + pos, 1, y1 - y0 + 1, 1);
		}
	}


	private static void add(List<Box> out, Role role, int x, int y, int z, int sx, int sy, int sz) {
		out.add(new Box(Box3.of(x, y, z, sx, sy, sz), role, Layer.SHELL, 0));
	}
}
