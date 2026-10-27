package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.structure.Posts;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.ir.Box;
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
		if (market(c)) {
			marketFacade(c);
			return;
		}
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
		arcade(c, f, plan.entrance());
	}

	/**
	 * The posts of an arcade (a front jetty of 2 or more blocks): a row along the jetty's front edge, from corner to
	 * corner, about 3-4 apart and none in front of the door, carrying the upper storeys over the walkway, with a beam
	 * between them under the floor edge. The ground floor behind it keeps its shopfront.
	 */
	private static void arcade(BuildContext c, Face front, FacadePlan.Entrance door) {
		Massing m = c.massing();
		Mass jetty = null;
		for (Mass a : m.masses()) if (a.kind().equals("jetty") && a.parent() != null && a.parent().equals(m.main().id())) jetty = a;
		if (jetty == null || m.main().z() - jetty.z() < 2) return;
		// a jetty over the street (air rights) has no posts: they would stand in the street
		if (jetty.z() < c.spec().lead() - c.type().limits().front()) return;
		int h = m.grid().y(1), z = jetty.z();
		int[] xs = Posts.spaced(jetty.x(), jetty.maxX() - 1, 4, door.pos() - 1, door.pos() + door.width());
		for (int i = 0; i < xs.length; i++) Posts.column(c, "post" + i, xs[i], z, h);
		c.box(Role.BEAM, Layer.SHELL, 0, jetty.x(), h - 1, z, jetty.sx(), 1, 1);
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
		if (market(c)) { // no door in the wall: the hall is entered by the stair from the open floor
			OpeningPass.run(c, e -> null);
			return;
		}
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


	/** True for the market hall: one raised mass on posts ({@link ShopType#MARKET_KIND}). */
	static boolean market(BuildContext c) { return c.massing().main().kind().equals(ShopType.MARKET_KIND); }

	private static Core legCore(Massing m) {
		for (Core k : m.cores()) if (k.from() == Core.LEG) return k;
		throw new IllegalStateException("market hall without its stair core");
	}

	/**
	 * The market hall's facade: windows on every face of the upper room (no shopfront, DS-45: the street level is the open floor), and an entrance
	 * that is only the top of the stair: on the back face, at the stair's last column, so that no door is cut there (the openings pass cuts none).
	 */
	private static void marketFacade(BuildContext c) {
		List<FacadePlan.Layout> layouts = FacadePass.layout(c);
		Massing m = c.massing();
		Core k = legCore(m);
		Face back = null;
		for (Face f : c.faces()) if (f.mass().equals(m.main().id()) && f.storey() == 0 && f.side() == Facing.SOUTH && (back == null || f.length() > back.length())) back = f;
		if (back == null) throw new IllegalStateException("market hall has no back face");
		int pos = Math.max(back.a() + 1, Math.min(back.b() - 2, k.maxX() - 1));
		FacadePlan plan = new FacadePlan(layouts, new FacadePlan.Entrance(back, pos), List.of());
		c.setFacadePlan(plan);
		FacadePass.decorate(c, plan);
	}

	/**
	 * The market hall's interior: the open floor paved (so the stair's foot and the walker standing in front of it are on a floor), the shared pass (one
	 * room per floor, the stair core with its first flight from the open floor), a balustrade of fence along both sides of that flight's
	 * higher steps, and a rail round the opening it makes in the first floor where a walker could step into it.
	 */
	private static void marketInterior(BuildContext c) {
		Massing m = c.massing();
		Mass main = m.main();
		Core k = legCore(m);
		int lift = c.stilts().lift(), ground = -lift; // local y of the open floor, below the raised building's own frame
		c.box(Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY, main.x(), ground, main.z(), main.sx(), 1, main.sz());
		InteriorPass.run(c);
		int row = k.maxZ() - 1, first = k.x() + k.sx() - lift; // the flight is on the core's back row, its last step on the core's last column
		for (int i = 1; i <= lift - 2; i++) { // sides of the steps above the first, under the floor: a fence column as high as the step and its rail
			int h = Math.min(i + 2, lift - 1);
			for (int z : new int[] {row - 1, row + 1}) c.box(Role.RAIL, Layer.SHELL, Box.NO_STOREY, first + i, ground + 1, z, 1, h, 1);
		}
		c.box(Role.RAIL, Layer.INTERIOR, 0, first - 1, 1, row, 1, 1, 1); // the slab's end beside the opening
		if (m.storeys() == 1) c.box(Role.RAIL, Layer.INTERIOR, 0, first, 1, row - 1, lift - 1, 1, 1); // the front side of the opening, open floor of the hall
		String hall = null;
		for (var r : c.rooms()) if (r.storey() == 0) hall = r.id();
		MarketStalls.place(c, main, k, lift, hall);
	}

	/** The shared interiors, then a counter near the front of the shop room and, if the upper floor planned no bedroom, a bed and a table. */
	static void interior(BuildContext c) {
		if (market(c)) {
			marketInterior(c);
			return;
		}
		InteriorPass.run(c);
		Massing m = c.massing();
		Mass main = m.main();
		int y = m.grid().y(0) + 1;
		int door = c.facadePlan().entrance().pos();
		counter:
		for (boolean strict : new boolean[] {true, false})
			for (int z = main.z() + 2; z < main.z() + 5; z++) {
				for (int dx : new int[] {2, -2, 3, -3, 1, -1, 4, -4, 5, -5}) {
					int x = door + dx;
					if (x >= main.x() + 1 && x <= main.maxX() - 2 && hook(c, m, "counter", 0, y, x, z, Facing.NORTH, 1, strict)) break counter;
				}
			}
		if (m.storeys() < 2 || c.hooks().stream().anyMatch(h -> h.kind().equals("bed"))) return;
		Mass up = null;
		for (Mass a : m.masses()) {
			if (a.spans(1) && a.floorAt(1) && !a.kind().equals("wing") && (up == null || a.sx() * a.sz() > up.sx() * up.sz())) up = a;
		}
		if (up == null) return;
		int y1 = m.grid().y(1) + 1;
		// two cells clear of the stair core if the floor has room, else one (a 7-wide market hall)
		java.util.List<int[]> bedList = new java.util.ArrayList<>(java.util.List.of(new int[] {up.x() + 1, up.z() + 1}, new int[] {up.maxX() - 2, up.z() + 1}, new int[] {up.x() + 1, up.z() + 2}, new int[] {up.maxX() - 2, up.z() + 2}));
		for (int x = up.x() + 2; x <= up.maxX() - 3; x++) bedList.add(new int[] {x, up.z() + 1}); // any cell along the back wall if the corners are taken
		int[][] bedAt = bedList.toArray(new int[0][]);
		int[][] tableAt = {{up.maxX() - 2, up.z() + 1}, {up.x() + 1, up.z() + 1}, {up.maxX() - 2, up.z() + 2}, {up.x() + 1, up.z() + 2}};
		place:
		for (int clear = 2; clear >= 1; clear--)
			for (int[] p : bedAt) if (bedHeadFree(c, m, y1, p[0], p[1], clear) && hook(c, m, "bed", 1, y1, p[0], p[1] + 1, Facing.NORTH, clear)) break place; // head to the wall (DS-48): the hook is the foot
		// no room for it across the back wall: along it, the head in a corner against the side wall
		if (c.hooks().stream().noneMatch(h -> h.kind().equals("bed")))
		place:
		for (int clear = 2; clear >= 1; clear--)
			for (int[] e : new int[][] {{up.x() + 1, up.z() + 1, 1}, {up.maxX() - 2, up.z() + 1, -1}, {up.x() + 1, up.maxZ() - 2, 1}, {up.maxX() - 2, up.maxZ() - 2, -1}})
				if (bedHeadFree(c, m, y1, e[0], e[1], clear) && hook(c, m, "bed", 1, y1, e[0] + e[2], e[1], e[2] > 0 ? Facing.WEST : Facing.EAST, clear)) break place;
		place:
		for (int clear = 2; clear >= 1; clear--)
			for (int[] p : tableAt) if (hook(c, m, "table", 1, y1, p[0], p[1], Facing.SOUTH, clear)) break place;
	}

	/** The head cell of a bed (against the wall at (x, z)) is free standing air. */
	private static boolean bedHeadFree(BuildContext c, Massing m, int y, int x, int z, int clear) {
		for (var k : m.cores()) if (x >= k.x() - clear && x < k.maxX() + clear && z >= k.z() - clear && z < k.maxZ() + clear) return false;
		return c.role(x, y, z) == Role.AIR && c.role(x, y + 1, z) == Role.AIR;
	}

	/** True if a piece at (x, z) would leave the cell in front of an existing hook with no free neighbour (it would be walled in; DS-48). */
	private static boolean boxesIn(BuildContext c, int y, int x, int z) {
		for (var h : c.hooks()) {
			if (h.y() != y || h.kind().equals("ceiling_light") || h.kind().equals("light")) continue;
			int fx = h.x() + h.facing().dx, fz = h.z() + h.facing().dz;
			if (fx == x && fz == z) return true;
			int free = 0;
			for (Facing d : Facing.values()) {
				int nx = fx + d.dx, nz = fz + d.dz;
				if (nx == x && nz == z || nx == h.x() && nz == h.z()) continue;
				boolean taken = false;
				for (var o : c.hooks()) if (o.y() == y && !o.kind().equals("ceiling_light") && (o.x() == nx && o.z() == nz || o.kind().equals("bed") && o.x() + o.facing().dx == nx && o.z() + o.facing().dz == nz)) taken = true;
				if (!taken && c.role(nx, y, nz) == Role.AIR && c.role(nx, y + 1, nz) == Role.AIR) free++;
			}
			if (free == 0) return true;
		}
		return false;
	}

	/** A hook on a free standing cell at least {@code clear} cells from every core and from the other hooks; false if the cell is unsuitable. */
	private static boolean hook(BuildContext c, Massing m, String kind, int storey, int y, int x, int z, Facing facing, int clear) { return hook(c, m, kind, storey, y, x, z, facing, clear, true); }

	/** {@code strict}: the cell in front must also have no hook within one cell of it (a narrow shop may have to settle for the loose rule). */
	private static boolean hook(BuildContext c, Massing m, String kind, int storey, int y, int x, int z, Facing facing, int clear, boolean strict) {
		if (c.role(x, y, z) != Role.AIR || c.role(x, y + 1, z) != Role.AIR) return false;
		for (var k : m.cores()) if (x >= k.x() - clear && x < k.maxX() + clear && z >= k.z() - clear && z < k.maxZ() + clear) return false;
		for (var h : c.hooks()) if (h.y() == y && Math.abs(h.x() - x) + Math.abs(h.z() - z) < 2) return false;
		// the cell in front of a piece (where its user stands) is free and has a free neighbour of its own, so a piece cannot wall itself in (DS-48)
		int fx = x + facing.dx, fz = z + facing.dz;
		if (!kind.equals("bed") && (c.role(fx, y, fz) != Role.AIR || c.role(fx, y + 1, fz) != Role.AIR)) return false;
		if (strict && !kind.equals("bed")) for (var h : c.hooks()) if (h.y() == y && Math.max(Math.abs(h.x() - fx), Math.abs(h.z() - fz)) <= 1) return false;
		if (boxesIn(c, y, x, z)) return false;
		String room = c.roomAt(x, y, z);
		if (room == null) return false;
		c.hook(kind, room, x, y, z, facing);
		return true;
	}
}
