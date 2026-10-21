package dev.fractalbuildings.core.gen.v1.structure;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import java.util.TreeSet;

/**
 * Free-standing posts for covered open ground (a shop arcade, a workshop porch): a 1 x 1 beam column
 * from the ground slab up, which is also a 1 x 1 volume so that the instance pass founds it down to
 * the terrain ({@code no-gap-below}) and the way-up flights keep clear of it. The roof or floor they
 * carry must touch them, which is what keeps it from floating.
 */
public final class Posts {
	private Posts() {}

	/**
	 * Positions along the run {@code from..to} (both ends included): the ends and evenly spaced posts
	 * between, about {@code target} apart, none inside {@code [gap0, gap1]} (a doorway; pass gap0 > gap1
	 * for none). Ascending.
	 */
	public static int[] spaced(int from, int to, int target, int gap0, int gap1) {
		int len = to - from;
		int k = Math.max(1, Math.round((float) len / target));
		TreeSet<Integer> out = new TreeSet<>();
		out.add(from);
		out.add(to);
		for (int i = 1; i < k; i++) {
			int p = from + i * len / k;
			if (p >= gap0 && p <= gap1) p = p - gap0 <= gap1 - p ? gap0 - 1 : gap1 + 1;
			if (p > from && p < to && !(p >= gap0 && p <= gap1)) out.add(p);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/** One post at column (x, z), {@code height} high from local y 0; the volume is named {@code id}. */
	public static void column(BuildContext c, String id, int x, int z, int height) {
		c.box(Role.BEAM, Layer.SHELL, 0, x, 0, z, 1, height, 1);
		c.volume(new Volume(id, Box3.of(x, 0, z, 1, height, 1), null));
	}
}
