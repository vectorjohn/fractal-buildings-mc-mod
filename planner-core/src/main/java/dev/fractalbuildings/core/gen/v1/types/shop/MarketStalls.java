package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * The trade on a market hall's open floor (7.17 S4 round 2; architect: 2 to 4 stall positions, a 3-wide walk clear on the axis, the stair foot
 * clear; interior designer {@code market-stalls-interior}: forms A canopy, B trestle, C cloth rack, each at most 3 x 2, goods per style, never a
 * workshop's job block) and its light (DS-24: a chain lantern about every 5 x 5 from the hall floor).
 *
 * <p>The stalls are shell boxes of the style's own roles (counter and canopy of {@code trim}, posts of {@code fence}), so they are in the viewer
 * and under the invariants; what lies on them is hooks the mod turns into blocks: {@code goods} (a block of the style's wares on the counter or
 * behind it), {@code light} (a lantern on the counter), {@code chest} (the trestle's stock). Stalls stand against the two long sides, 3 along
 * the side by 2 deep, with the counter row facing the walk; the rows next to the stair are left free. All coordinates are local to the raised
 * part, so the open floor is at negative y (standing level {@code 1 - lift}).
 */
final class MarketStalls {
	private MarketStalls() {}

	/** Wares of the stalls, by style family; the mod picks a block per hook (see {@code HookPlacer}). */
	static final int MAX_STALLS = 4;
	/** Stall depth across the walk and length along the side. */
	static final int DEPTH = 2, LEN = 3;
	/** Rows left free between the last stall and the back wall: the stair's row, its stringers and its approach. */
	static final int BACK_CLEAR = 5;

	/** A stall position: the column of its back and front row, its first row, and the side it faces ({@code EAST} for the left side). */
	record Slot(int back, int front, int z, Facing facing) {
		boolean covers(int x, int z) { return x >= Math.min(back, front) && x <= Math.max(back, front) && z >= this.z && z < this.z + LEN; }
	}

	/** The stall positions of a hall mass, in the order they are taken: first row left, first row right, second row left, second row right. */
	static List<Slot> slots(Mass m) {
		List<Slot> out = new ArrayList<>();
		for (int row = 0; row < 2; row++) {
			int z = m.z() + 2 + 4 * row;
			if (z + LEN - 1 > m.maxZ() - 1 - BACK_CLEAR) continue;
			out.add(new Slot(m.x() + 1, m.x() + 2, z, Facing.EAST));
			out.add(new Slot(m.maxX() - 2, m.maxX() - 3, z, Facing.WEST));
		}
		return out;
	}

	/** Form of the k-th stall: 0 canopy (A), 1 trestle (B), 2 cloth rack (C), by style family (the interior designer's table). */
	static int form(String style, int k) {
		int[] f = switch (style) {
			case "medieval" -> new int[] {0, 2, 1, 0};
			case "plains" -> new int[] {0, 1, 1, 0};
			case "taiga" -> new int[] {0, 2, 1, 0};
			case "snowy" -> new int[] {1, 2, 0, 1};
			case "desert" -> new int[] {1, 2, 0, 2};
			case "jungle" -> new int[] {0, 1, 2, 0};
			default -> new int[] {0, 1, 2, 0};
		};
		return f[k % f.length];
	}

	/** The chosen stalls: at least 2 (one a side) and at most 4, as many as the hall's depth has room for. */
	static List<Slot> chosen(BuildContext c, Mass m) {
		List<Slot> all = slots(m);
		int n = Math.min(all.size(), new Draws(c.hash("market/stalls")).range(2, MAX_STALLS));
		return all.subList(0, n);
	}

	static void place(BuildContext c, Mass m, Core k, int lift, String room) {
		int l0 = 1 - lift; // standing level of the open floor, local
		List<Slot> slots = chosen(c, m);
		String style = c.spec().style();
		for (int i = 0; i < slots.size(); i++) stall(c, slots.get(i), form(style, i), l0, lift, room);
		lights(c, m, k, slots, l0, room);
	}

	private static void box(BuildContext c, Role role, int x, int y, int z, int sx, int sy, int sz) {
		c.box(new Box(dev.fractalbuildings.core.ir.Box3.of(x, y, z, sx, sy, sz), role, Layer.SHELL, Box.NO_STOREY));
	}

	private static void stall(BuildContext c, Slot s, int form, int l0, int lift, String room) {
		Facing f = s.facing();
		int z0 = s.z(), z2 = s.z() + 2;
		// counter: the whole front row
		box(c, Role.TRIM, s.front(), l0, z0, 1, 1, LEN);
		switch (form) {
			case 0 -> { // canopy: posts at the back corners and the front's first end, a roof of trim over all 6 cells (3 clear: at L2; 5 clear: one higher)
				int tall = lift >= 6 ? 3 : 2;
				box(c, Role.FENCE, s.back(), l0, z0, 1, tall, 1);
				box(c, Role.FENCE, s.back(), l0, z2, 1, tall, 1);
				box(c, Role.FENCE, s.front(), l0 + 1, z0, 1, tall - 1, 1);
				box(c, Role.TRIM, Math.min(s.back(), s.front()), l0 + tall, z0, DEPTH, 1, LEN);
				c.hook("goods", room, s.back(), l0, z0 + 1, f);
				c.hook("goods", room, s.front(), l0 + 1, z0 + 1, f);
				c.hook("light", room, s.front(), l0 + 1, z2, f);
			}
			case 1 -> { // trestle: low counter, stock behind it
				c.hook("goods", room, s.back(), l0, z0, f);
				c.hook("goods", room, s.back(), l0, z0 + 1, f);
				c.hook("chest", room, s.back(), l0, z2, f);
				c.hook("goods", room, s.front(), l0 + 1, z0, f);
				c.hook("goods", room, s.front(), l0 + 1, z2, f);
				c.hook("light", room, s.front(), l0 + 1, z0 + 1, f);
			}
			default -> { // cloth rack: a back of trim 2 high at the ends, 1 between, wares on top of it and the counter
				box(c, Role.TRIM, s.back(), l0, z0, 1, 1, LEN);
				box(c, Role.TRIM, s.back(), l0 + 1, z0, 1, 1, 1);
				box(c, Role.TRIM, s.back(), l0 + 1, z2, 1, 1, 1);
				c.hook("goods", room, s.back(), l0 + 1, z0 + 1, f);
				c.hook("goods", room, s.front(), l0 + 1, z2, f);
				c.hook("light", room, s.front(), l0 + 1, z0, f);
			}
		}
	}

	/** Chain lanterns from the hall floor: a grid of pitch at most 5 over the walk (moved to the axis where a stall stands), one before the stair. */
	private static void lights(BuildContext c, Mass m, Core k, List<Slot> slots, int l0, String room) {
		int iw = m.sx() - 2, id = m.sz() - 2;
		int nx = (iw + 3) / 4, nz = (id + 3) / 4; // pitch about 4: the rows next to the stair lose theirs
		int axis = m.x() + m.sx() / 2;
		List<int[]> cells = new ArrayList<>();
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < nz; j++) {
				int x = m.x() + 1 + (2 * i + 1) * iw / (2 * nx), z = m.z() + 1 + (2 * j + 1) * id / (2 * nz);
				for (Slot s : slots) if (s.covers(x, z) || s.covers(x - 1, z) || s.covers(x + 1, z)) { x = x < axis ? axis - 1 : x > axis ? axis + 1 : axis; break; } // over the walk, on its own side
				if (z >= m.maxZ() - 4 && x >= k.x() - 1 && x <= k.maxX()) continue; // the stair's rows: its own light before it, below
				boolean dup = false;
				for (int[] p : cells) dup |= p[0] == x && p[1] == z;
				if (!dup) cells.add(new int[] {x, z});
			}
		}
		cells.add(new int[] {k.x() + k.sx() / 2, m.maxZ() - 4});
		for (int[] p : cells) c.hook("ceiling_light", room, p[0], l0, p[1], Facing.NORTH);
	}
}
