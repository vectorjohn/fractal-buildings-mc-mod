package dev.fractalbuildings.core.gen.v1.types.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Shop properties (plan T-shop): the front room spans the front, each shopfront window is >= 2 wide, a centred door, a counter, living upstairs. */
class ShopTypeTest {
	static final int SEEDS = 256;
	static final int[][] ENVELOPES = {{7, 12}, {9, 15}, {12, 22}, {10, 24}};
	static final String[] STYLES = {"plain", "medieval"};

	static BuildingIr shop(String style, long seed, int w, int d) {
		int storeys = 2 + new Draws(seed * 7919 + w).below(3);
		return ShellGeneratorV1.generate(new BuildingSpec(2, seed, style, "shop", w, d, storeys, "auto", 384, 1));
	}

	/** Problems with one shop, empty if it has the shop properties. */
	static List<String> problems(BuildingIr b) {
		List<String> p = new ArrayList<>();
		int w = b.spec().width();
		Opening door = null;
		for (Opening o : b.openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) door = o;
		if (door == null) return List.of("no entrance");
		if (Math.abs(door.box().x() - (w - 1) / 2) > 1) p.add("door at x " + door.box().x() + " is not centred in " + w);
		int wins = 0;
		for (Opening o : b.openings()) {
			if (o.kind() != OpeningKind.WINDOW || o.facing() != Facing.NORTH || o.box().z() != door.box().z() || o.box().y() > 4) continue;
			wins++;
			if (o.box().sx() < 2) p.add("shopfront window " + o.id() + " is " + o.box().sx() + " wide");
		}
		if (wins == 0) p.add("no shopfront windows");
		Hook counter = null;
		for (Hook h : b.hooks()) if (h.kind().equals("counter")) counter = h;
		if (counter == null) return append(p, "no counter hook");
		Room front = null;
		for (Room r : b.rooms()) if (r.id().equals(counter.room())) front = r;
		if (front == null || front.storey() != 0) return append(p, "counter is not in a ground room");
		Box3 part = front.parts().get(0);
		if (part.x() != 1 || part.sx() != w - 2) p.add("front room spans x " + part.x() + ".." + (part.maxX() - 1) + ", not the whole front of " + w);
		if (part.z() != door.box().z() + 1) p.add("front room does not start behind the front wall");
		if (b.storeys().get(0).height() != 5) p.add("ground storey is " + b.storeys().get(0).height() + " high");
		if (b.spec().storeys() >= 2 && b.hooks().stream().noneMatch(h -> h.kind().equals("bed"))) p.add("no bed upstairs");
		return p;
	}

	private static List<String> append(List<String> p, String s) {
		p.add(s);
		return p;
	}

	@Test
	void everyShopHasAShopfrontCounterAndLivingUpstairs() {
		for (String style : STYLES)
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS; seed++) {
					List<String> p = problems(shop(style, seed, e[0], e[1]));
					assertTrue(p.isEmpty(), style + " seed " + seed + " " + e[0] + "x" + e[1] + ": " + p);
				}
	}

	@Test
	void theChecksCanFail() {
		BuildingIr b = shop("medieval", 1, 9, 16);
		assertTrue(problems(b).isEmpty());
		// mutant: a 1-wide display window
		List<Opening> ops = new ArrayList<>(b.openings());
		for (int i = 0; i < ops.size(); i++) {
			Opening o = ops.get(i);
			if (o.kind() == OpeningKind.WINDOW && o.box().sx() >= 2) {
				ops.set(i, new Opening(o.id(), o.kind(), Box3.of(o.box().x(), o.box().y(), o.box().z(), 1, o.box().sy(), o.box().sz()), o.facing(), o.from(), o.to()));
				break;
			}
		}
		assertFalse(problems(rebuild(b, ops, b.hooks(), b.rooms())).isEmpty(), "narrow window");
		// mutant: no counter
		List<Hook> hooks = new ArrayList<>(b.hooks());
		hooks.removeIf(h -> h.kind().equals("counter"));
		assertFalse(problems(rebuild(b, b.openings(), hooks, b.rooms())).isEmpty(), "no counter");
		// mutant: a front room that stops short of the side wall
		List<Room> rooms = new ArrayList<>();
		for (Room r : b.rooms()) {
			Box3 q = r.parts().get(0);
			String cr = b.hooks().stream().filter(h -> h.kind().equals("counter")).findFirst().get().room();
			rooms.add(r.id().equals(cr) ? new Room(r.id(), r.storey(), r.volume(), List.of(Box3.of(q.x(), q.y(), q.z(), q.sx() - 1, q.sy(), q.sz())), r.use()) : r);
		}
		assertFalse(problems(rebuild(b, b.openings(), b.hooks(), rooms)).isEmpty(), "short room");
	}

	private static BuildingIr rebuild(BuildingIr b, List<Opening> ops, List<Hook> hooks, List<Room> rooms) {
		return new BuildingIr(b.spec(), b.bounds(), b.storeys(), b.volumes(), rooms, ops, b.roofs(), b.boxes(), hooks);
	}

	@Test
	void diversity() {
		int n = 0, rear = 0, jetty = 0, fullGlass = 0, deep = 0;
		java.util.Set<Integer> storeys = new java.util.TreeSet<>(), windowCounts = new java.util.TreeSet<>();
		for (long seed = 0; seed < SEEDS; seed++)
			for (int[] e : ENVELOPES) {
				BuildingIr b = shop("medieval", seed, e[0], e[1]);
				n++;
				if (b.volumes().stream().anyMatch(v -> v.id().startsWith("wing"))) rear++;
				if (b.volumes().stream().anyMatch(v -> v.id().startsWith("jetty"))) jetty++;
				if (b.openings().stream().anyMatch(o -> o.kind() == OpeningKind.WINDOW && o.box().sy() == 3)) fullGlass++;
				storeys.add(b.storeys().size());
				int fronts = 0;
				for (Opening o : b.openings()) if (o.kind() == OpeningKind.WINDOW && o.facing() == Facing.NORTH && o.box().y() < 5) fronts++;
				windowCounts.add(fronts);
				if (b.spec().depth() >= 20) deep++;
			}
		System.out.println(String.format(Locale.ROOT, "shop diversity (medieval, %d): rear wings %d, jetties %d, full glass %d, storeys %s, front window counts %s", n, rear, jetty, fullGlass, storeys, windowCounts));
		assertTrue(rear >= 0.15 * n, "rear wings " + rear + "/" + n);
		assertTrue(jetty >= 0.10 * n, "jetties " + jetty + "/" + n);
		assertTrue(fullGlass >= 0.10 * n, "full-glass fronts " + fullGlass + "/" + n);
		assertTrue(storeys.size() >= 3, "storey counts " + storeys);
		assertTrue(windowCounts.size() >= 2, "front window counts " + windowCounts);
		assertEquals(n / 2, deep);
	}
}
