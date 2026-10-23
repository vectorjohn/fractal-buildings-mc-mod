package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Role;
import java.util.List;

/** Block choice per cell: the style's palette list for the role at {@code cellHash(x, y, z) % n}, as the viewer picks colours. */
public final class Variant {
	private Variant() {}

	/** Port of the viewer's {@code cellHash} ({@code Math.imul} is plain int multiplication); unsigned 32-bit result. */
	public static long cellHash(int x, int y, int z) {
		int h = (x * 0x1b873593) ^ (y * 0x85ebca6b) ^ (z * 0xc2b2ae35);
		h = (h ^ (h >>> 16)) * 0x7feb352d;
		h = (h ^ (h >>> 15)) * 0x846ca68b;
		return (h ^ (h >>> 16)) & 0xffffffffL;
	}

	/** Block id for a role at a cell; an unknown style or a role the style lacks falls back to {@code plain}. */
	public static String pick(String style, Role role, int x, int y, int z) {
		if (role == Role.AIR) return "minecraft:air";
		List<String> list = list(style, role);
		if (list == null) list = list(StyleLibrary.FALLBACK, role);
		if (list == null || list.isEmpty()) return "minecraft:stone";
		return list.get((int) (cellHash(x, y, z) % list.size()));
	}

	private static List<String> list(String style, Role role) {
		StyleRules s;
		try {
			s = StyleLibrary.get(style);
		} catch (IllegalArgumentException e) {
			return null;
		}
		return s.palette().get(role.json());
	}
}
