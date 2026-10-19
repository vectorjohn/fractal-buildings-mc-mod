package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The shop's three programs, each the shared pass plus the shop's additions. Facade: the main
 * mass's ground front is replaced by a shopfront (display windows 2-3 wide, 1-wide piers, a door at
 * the centre) under a trim fascia. The front inside row is reserved
 * (pier bays count), so the interior pass cannot cut the front room across. Openings: the display windows (the shared pass skips the shopfront kind).
 */
final class Shopfront {
	private Shopfront() {}

	static void facade(BuildContext c) {
		List<FacadePlan.Layout> layouts = FacadePass.layout(c);
		Massing m = c.massing();
		List<FacadePlan.Entrance> others = new ArrayList<>();
		for (int e = 1; e < m.entrances().size(); e++) others.add(FacadePass.entrance(c, m, layouts, m.entrances().get(e), e));
		Face f = frontFace(c);
		if (f == null) throw new IllegalStateException("shop has no ground front");
		int first = f.a() + 1, last = f.b() - 2; // cells between the corner/junction posts
		int len = last - first + 1;
		int dw = len >= 9 && c.draws("shop/door").chance(1, 2) ? 2 : 1; // a double door when there is room for a window each side
		int door = first + (len - dw) / 2;
		Draws r = c.draws("shop/bays");
		List<Integer> bays = new ArrayList<>();
		List<String> kinds = new ArrayList<>();
		region(door - first, r, true, bays, kinds);
		bays.add(1);
		kinds.add(FacadePlan.DOOR);
		if (dw == 2) { // the second door cell is a custom kind so the interior pass reserves its inside cell too
			bays.add(1);
			kinds.add(ShopType.PIER);
		}
		region(last - (door + dw - 1), r, false, bays, kinds);
		FacadePlan.Layout shop = new FacadePlan.Layout(f, first, bays.stream().mapToInt(Integer::intValue).toArray(), kinds);
		int at = -1;
		for (int i = 0; i < layouts.size(); i++) if (layouts.get(i).face().equals(f)) at = i;
		if (at >= 0) layouts.set(at, shop);
		else layouts.add(shop);
		// the fascia: a trim band along the top row of the ground wall, between the posts
		c.box(Role.TRIM, Layer.SHELL, 0, f.x(first), f.y0() + f.height() - 1, f.z(first), alongX(f) ? len : 1, 1, alongX(f) ? 1 : len);
		FacadePlan plan = new FacadePlan(layouts, new FacadePlan.Entrance(f, door, dw), others);
		c.setFacadePlan(plan);
		FacadePass.decorate(c, plan);
	}

	/**
	 * Fills {@code width} cells beside the door with 2-3 wide display windows separated by 1-wide
	 * piers; leftover width widens windows (up to 3), then becomes a pier next to the door.
	 */
	static void region(int width, Draws r, boolean doorOnRight, List<Integer> bays, List<String> kinds) {
		if (width <= 0) return;
		if (width < 2) {
			bays.add(width);
			kinds.add(ShopType.PIER);
			return;
		}
		int n = Math.max(1, (width + 1) / 3 - r.below(2)); // windows of 2 with 1-wide piers between
		int[] w = new int[n];
		Arrays.fill(w, 2);
		int spare = width - (2 * n + n - 1);
		for (int i = 0; spare > 0 && i < 2 * n; i++) {
			if (w[i % n] < 3) {
				w[i % n]++;
				spare--;
			}
		}
		if (!doorOnRight && spare > 0) {
			bays.add(spare);
			kinds.add(ShopType.PIER);
		}
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				bays.add(1);
				kinds.add(ShopType.PIER);
			}
			bays.add(w[i]);
			kinds.add(ShopType.SHOPFRONT);
		}
		if (doorOnRight && spare > 0) {
			bays.add(spare);
			kinds.add(ShopType.PIER);
		}
	}

	private static boolean alongX(Face f) { return f.side() == Facing.NORTH || f.side() == Facing.SOUTH; }

	/** The main mass's north wall run on the ground storey (the longest, if jetties split it). */
	static Face frontFace(BuildContext c) {
		String main = c.massing().main().id();
		Face best = null;
		for (Face f : c.faces()) {
			if (f.mass().equals(main) && f.storey() == 0 && f.side() == Facing.NORTH && (best == null || f.length() > best.length())) best = f;
		}
		return best;
	}

	/** Display window sill above the ground standing level: 1 (a stall board, 2 high) or 0 (full glass, 3 high). */
	static int sill(BuildContext c) { return new Draws(c.hash("shop/sill")).chance(1, 4) ? 0 : 1; }

	static void openings(BuildContext c) {
		OpeningPass.run(c);
		int sill = sill(c), h = 3 - sill;
		for (FacadePlan.Layout l : c.facadePlan().layouts()) {
			Face f = l.face();
			for (int b = 0; b < l.bays().length; b++) {
				if (!ShopType.SHOPFRONT.equals(l.kinds().get(b))) continue;
				int p0 = l.bayStart(b), w = l.bays()[b], y = f.y0() + sill;
				String room = roomBehind(c, f, p0, w, y, h);
				if (room == null) continue;
				Box3 box = Box3.of(f.x(p0), y, f.z(p0), alongX(f) ? w : 1, h, alongX(f) ? 1 : w);
				c.box(Role.WINDOW, Layer.SHELL, f.storey(), box.x(), box.y(), box.z(), box.sx(), box.sy(), box.sz());
				c.opening(OpeningKind.WINDOW, box, f.side(), room, Opening.OUTSIDE);
			}
		}
	}

	/** The one room behind a window span if every cell is wall with room air inside and open air outside, else null. */
	private static String roomBehind(BuildContext c, Face f, int p0, int w, int y, int h) {
		String room = null;
		for (int p = p0; p < p0 + w; p++)
			for (int k = 0; k < h; k++) {
				if (c.role(f.x(p), y + k, f.z(p)) != Role.WALL && c.role(f.x(p), y + k, f.z(p)) != Role.TRIM) return null;
				if (c.role(f.insideX(p), y + k, f.insideZ(p)) != Role.AIR || c.role(f.outsideX(p), y + k, f.outsideZ(p)) != Role.AIR) return null;
				String r = c.roomAt(f.insideX(p), y + k, f.insideZ(p));
				if (r == null || room != null && !room.equals(r)) return null;
				room = r;
			}
		return room;
	}


	/** The shared interiors, then a counter near the front of the shop room and, if the upper floor planned no bedroom, a bed and a table. */
	static void interior(BuildContext c) {
		InteriorPass.run(c);
		Massing m = c.massing();
		Mass main = m.main();
		int y = m.grid().y(0) + 1;
		int door = c.facadePlan().entrance().pos();
		counter:
		for (int z = main.z() + 2; z < main.z() + 5; z++) {
			for (int dx : new int[] {2, -2, 3, -3, 1, -1, 4, -4, 5, -5}) {
				int x = door + dx;
				if (x >= main.x() + 1 && x <= main.maxX() - 2 && hook(c, m, "counter", 0, y, x, z, Facing.NORTH, 1)) break counter;
			}
		}
		if (m.storeys() < 2 || c.hooks().stream().anyMatch(h -> h.kind().equals("bed"))) return;
		Mass up = null;
		for (Mass a : m.masses()) {
			if (a.spans(1) && a.floorAt(1) && !a.kind().equals("wing") && (up == null || a.sx() * a.sz() > up.sx() * up.sz())) up = a;
		}
		if (up == null) return;
		int y1 = m.grid().y(1) + 1;
		for (int[] p : new int[][] {{up.x() + 1, up.z() + 1}, {up.maxX() - 2, up.z() + 1}, {up.x() + 1, up.z() + 2}, {up.maxX() - 2, up.z() + 2}}) {
			if (hook(c, m, "bed", 1, y1, p[0], p[1], Facing.SOUTH, 2)) break;
		}
		for (int[] p : new int[][] {{up.maxX() - 2, up.z() + 1}, {up.x() + 1, up.z() + 1}, {up.maxX() - 2, up.z() + 2}, {up.x() + 1, up.z() + 2}}) {
			if (hook(c, m, "table", 1, y1, p[0], p[1], Facing.SOUTH, 2)) break;
		}
	}

	/** A hook on a free standing cell at least {@code clear} cells from every core and from the other hooks; false if the cell is unsuitable. */
	private static boolean hook(BuildContext c, Massing m, String kind, int storey, int y, int x, int z, Facing facing, int clear) {
		if (c.role(x, y, z) != Role.AIR || c.role(x, y + 1, z) != Role.AIR) return false;
		for (var k : m.cores()) if (x >= k.x() - clear && x < k.maxX() + clear && z >= k.z() - clear && z < k.maxZ() + clear) return false;
		for (var h : c.hooks()) if (h.y() == y && Math.abs(h.x() - x) + Math.abs(h.z() - z) < 2) return false;
		String room = c.roomAt(x, y, z);
		if (room == null) return false;
		c.hook(kind, room, x, y, z, facing);
		return true;
	}
}
