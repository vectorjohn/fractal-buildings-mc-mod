package dev.fractalbuildings.core.gen.v1.types.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.SpecHash;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 7.17 SHOPLOW S4: the market hall. Only a shop on a lot the brief marks {@code square} (one per square, level ground) and with an envelope
 * of 9 or more is one; its open floor is at street level on 3 (or from width 11, 4) high posts; its stair is inside, along the back edge,
 * up through a hole in the first floor; the room above is civic (a guild hall, then stores), never a shop or a bedroom.
 */
class MarketHallTest {
	static final String[] STYLES = {"plain", "medieval", "desert", "jungle"};
	static final String[] TERRAINS = {"flat:64", "plane:64:1:0:40", "plane:64:0:1:40"};
	/** Lot sizes {w, d}: the envelope is two narrower (side margins) and two shallower. */
	static final int[][] LOTS = {{11, 16}, {12, 20}, {13, 22}, {15, 24}, {10, 18}};

	static SceneDoc scene(String style, long seed, int w, int d, String terrain, boolean square) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		Brief brief = new Brief(1, 99, 1, 0, Brief.NO_FRONT_Y, null, square);
		return new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, w, d, Facing.NORTH), "shop", style, p.terrain(), p.biome(), brief));
	}

	static BuildingIr shell(SceneDoc s) { return s.buildings().get(0).shell(); }

	static boolean market(BuildingIr b) { return b.volumes().stream().anyMatch(v -> v.id().startsWith("leg")); }

	/** Problems with one market hall, empty if it follows S4's rules. */
	static List<String> problems(BuildingIr b) {
		List<String> p = new ArrayList<>();
		int lift = b.storeys().get(1).y();
		int w = b.spec().width();
		if (lift != (w >= 11 ? 6 : 5)) p.add("open floor " + lift + " high (" + (lift - 1) + " clear) on width " + w); // DS-23: 4 clear at 9-10, 5 clear from 11, from the paving
		// DS-45: the street level is the open floor: no room, no wall, no door in a wall, nothing but posts, paving, the stair and its balustrade
		if (b.rooms().stream().anyMatch(r -> r.storey() == 0)) p.add("a room on the open floor");
		for (Box x : b.boxes()) {
			if (x.box().y() >= lift - 1) continue;
			if (x.role() != Role.BEAM && x.role() != Role.FOUNDATION && x.role() != Role.STAIR && x.role() != Role.RAIL && x.role() != Role.TRIM && x.role() != Role.FENCE) p.add("a " + x.role() + " block below the first floor at y " + x.box().y());
		}
		if (b.volumes().stream().filter(v -> v.id().startsWith("leg")).count() < 6) p.add("fewer than 6 posts");
		// no shopfront and no door upstairs
		for (Opening o : b.openings()) {
			if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) p.add("an exterior door " + o.id());
			if (o.kind() == OpeningKind.WINDOW && o.box().sy() > 2) p.add("a shopfront-sized window " + o.id());
		}
		for (var h : b.hooks()) if (h.kind().equals("counter") || h.kind().equals("bed") || h.kind().equals("lectern")) p.add("hook " + h.kind());
		// the stair: inside, from outside (the open floor), along the back edge, up to the first floor; its first step is at the ground
		Opening up = null;
		for (Opening o : b.openings()) if (o.kind() == OpeningKind.STAIR && Opening.OUTSIDE.equals(o.from())) up = o;
		if (up == null) return append(p, "no stair from the open floor");
		if (up.box().y() != 1 || up.box().sy() != lift) p.add("stair box " + up.box() + " for legs " + lift);
		int backZ = b.volumes().stream().filter(v -> v.id().equals("main0")).mapToInt(v -> v.box().maxZ()).findFirst().orElse(-1);
		if (up.box().z() != backZ - 2) p.add("stair on row " + up.box().z() + ", not along the back edge (" + (backZ - 2) + ")");
		if (up.facing() != Facing.EAST) p.add("stair faces " + up.facing());
		// the hole in the first floor over it: air at slab level over every step but the last, and a floor under the stair's foot
		int hole = 0;
		for (Box x : b.boxes()) if (x.role() == Role.AIR && x.box().y() == lift && x.box().z() == up.box().z() && x.box().sx() == lift - 1) hole++;
		if (hole == 0) p.add("no hole in the first floor");
		// one hall storey over the open floor and nothing else (a store, if any, is in the roof space: dropped)
		if (b.rooms().size() != 1 || !b.rooms().get(0).use().equals("hall") || b.rooms().get(0).storey() != 1) p.add("rooms " + b.rooms().stream().map(r -> r.storey() + ":" + r.use()).toList());
		if (b.storeys().size() != 2) p.add(b.storeys().size() + " storeys incl. the open floor");
		p.addAll(MarketFloorRules.problems(b, lift, up));
		return p;
	}

	private static List<String> append(List<String> p, String s) {
		p.add(s);
		return p;
	}

	@Test
	void everyMarketHallFollowsItsRulesAndPassesTheInvariants() {
		int halls = 0, n = 0;
		TreeSet<Integer> legs = new TreeSet<>();
		for (String style : STYLES)
			for (String terrain : TERRAINS)
				for (int[] e : LOTS)
					for (long seed = 0; seed < 24; seed++) {
						SceneDoc s = scene(style, seed, e[0], e[1], terrain, true);
						n++;
						BuildingIr b = shell(s);
						String id = style + " " + terrain + " " + e[0] + "x" + e[1] + " seed " + seed;
						List<Violation> v = Invariants.check(s, ProbeSpecs.parse(terrain).terrain());
						assertTrue(v.isEmpty(), id + ": " + (v.isEmpty() ? "" : v.get(0).invariant() + " " + v.get(0).message()));
						if (!market(b)) continue;
						halls++;
						assertTrue(b.spec().square() && b.spec().width() >= ShopType.MARKET_MIN_WIDTH, id);
						List<String> p = problems(b);
						assertTrue(p.isEmpty(), id + ": " + p);
						legs.add(b.storeys().get(1).y());
					}
		System.out.println("market halls: " + halls + " of " + n + " square shops, legs " + legs);
		assertTrue(halls >= 0.9 * n * 0.8, "market halls " + halls + "/" + n); // the narrowest lots (envelope 8) have none
		assertEquals(new TreeSet<>(List.of(5, 6)), legs);
	}

	@Test
	void noMarketWithoutSquareOrUnderWidthNine() {
		for (String style : STYLES)
			for (int[] e : LOTS)
				for (long seed = 0; seed < 64; seed++) {
					assertFalse(market(shell(scene(style, seed, e[0], e[1], "flat:64", false))), "a market hall without a square lot: " + style + " " + e[0] + "x" + e[1] + " seed " + seed);
				}
		for (long seed = 0; seed < 64; seed++) // envelope 8 (lot 10 wide)
			assertFalse(market(shell(scene("medieval", seed, 10, 18, "flat:64", true))), "a market hall on an envelope of 8, seed " + seed);
		// ground that is not level is not a square lot, whatever the brief says
		for (long seed = 0; seed < 32; seed++) assertFalse(shell(scene("medieval", seed, 13, 22, "plane:64:1:0:6", true)).spec().square());
	}

	@Test
	void theSquareFlagChangesTheSpecHashOnlyWhenSet() {
		BuildingSpec plain = new BuildingSpec(2, 7, "medieval", "shop", 11, 16, 2, "auto", 384, 1);
		BuildingSpec sq = new BuildingSpec(2, 7, "medieval", "shop", 11, 16, 2, "auto", 384, 1, 0, true);
		assertFalse(SpecHash.canonical(plain).contains("square"));
		assertTrue(SpecHash.canonical(sq).endsWith(",\"square\":true}"), SpecHash.canonical(sq));
		assertFalse(SpecHash.of(plain).equals(SpecHash.of(sq)));
		assertEquals(plain, new BuildingSpec(2, 7, "medieval", "shop", 11, 16, 2, "auto", 384, 1, 0, false));
	}

	@Test
	void theUpperRoomsAreCivicAndLit() {
		int lit = 0, halls = 0;
		for (long seed = 0; seed < 60; seed++) {
			BuildingIr b = shell(scene("medieval", seed, 13, 22, "flat:64", true));
			if (!market(b)) continue;
			halls++;
			for (Room r : b.rooms()) {
				long lights = b.hooks().stream().filter(h -> h.room().equals(r.id()) && h.kind().equals("ceiling_light") && h.y() >= b.storeys().get(1).y()).count();
				assertTrue(lights >= 1, "room " + r.id() + " has no ceiling light, seed " + seed);
				if (r.use().equals("hall")) {
					int longSide = r.parts().stream().mapToInt(q -> Math.max(q.sx(), q.sz())).max().orElse(0);
					if (longSide >= 11) assertTrue(lights >= 2, "hall " + longSide + " long with " + lights + " chandelier(s), seed " + seed); // I1: two lights on a room 12+ long (11 inside the walls)
					assertTrue(b.hooks().stream().anyMatch(h -> h.room().equals(r.id()) && h.kind().equals("table")) && b.hooks().stream().anyMatch(h -> h.room().equals(r.id()) && h.kind().equals("hearth")), "hall without its two furniture groups, seed " + seed);
					lit++;
				}
			}
		}
		assertTrue(halls > 20 && lit == halls, halls + " halls, " + lit + " lit guild halls");
	}

	/** SHA-256 of 16 fixed market halls (square lots, plain and medieval, level ground) against {@code golden/v1/shop-market.txt}; same rule as {@code GoldenDigestV1Test}: on a mismatch the new file is written to build/golden/v1 and copied over if the change is intended. */
	@Test
	void marketHallsAreDeterministicAndMatchTheGoldenDigests() throws Exception {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			String style = i % 2 == 0 ? "plain" : "medieval";
			int[] lot = LOTS[i % 4];
			String json = dev.fractalbuildings.core.ir.IrJson.write(scene(style, 1_000_000 + i, lot[0], lot[1], "flat:64", true));
			assertEquals(json, dev.fractalbuildings.core.ir.IrJson.write(scene(style, 1_000_000 + i, lot[0], lot[1], "flat:64", true)), "same seed, same bytes");
			lines.add(i + " " + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		}
		List<String> expected;
		try (java.io.InputStream in = MarketHallTest.class.getResourceAsStream("/golden/v1/shop-market.txt")) {
			expected = in == null ? List.of() : List.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n"));
		}
		if (!expected.equals(lines)) {
			java.nio.file.Path out = java.nio.file.Path.of("build/golden/v1/shop-market.txt");
			java.nio.file.Files.createDirectories(out.getParent());
			java.nio.file.Files.writeString(out, String.join("\n", lines) + "\n");
			org.junit.jupiter.api.Assertions.fail("market hall golden digests differ; if intended copy " + out.toAbsolutePath() + " to planner-core/src/test/resources/golden/v1/shop-market.txt (never hand-merge)");
		}
	}

	@Test
	void theChecksCanFail() {
		SceneDoc s = null;
		for (long seed = 0; s == null && seed < 50; seed++) {
			SceneDoc c = scene("medieval", seed, 13, 22, "flat:64", true);
			if (market(shell(c))) s = c;
		}
		assertTrue(s != null);
		assertTrue(problems(shell(s)).isEmpty());
		PlacedBuilding pb = s.buildings().get(0);
		BuildingIr b = pb.shell();
		// mutant 1: no paving under the stair's foot: the flight has nothing to start from (stair-rise)
		List<Box> noPaving = b.boxes().stream().filter(x -> x.role() != Role.FOUNDATION).toList();
		assertTrue(fails(s, withBoxes(pb, b, noPaving), "stair-rise"), "paving removed");
		// mutant 2: a slab over the hole: the stair leads into a ceiling
		List<Box> capped = new ArrayList<>(b.boxes());
		for (Box x : b.boxes()) if (x.role() == Role.AIR && x.box().y() == b.storeys().get(1).y() && x.box().sx() > 1) capped.add(new Box(x.box(), Role.FLOOR, x.layer(), x.storey()));
		assertTrue(fails(s, withBoxes(pb, b, capped), "stair-rise") || fails(s, withBoxes(pb, b, capped), "rooms-reachable"), "hole capped");
		// mutant 3: a wall on the open floor breaks the rules of the form
		List<Box> walled = new ArrayList<>(b.boxes());
		walled.add(new Box(dev.fractalbuildings.core.ir.Box3.of(3, 1, 3, 3, 1, 1), Role.WALL, dev.fractalbuildings.core.ir.Layer.SHELL, 0));
		assertFalse(problems(new BuildingIr(b.spec(), b.bounds(), b.storeys(), b.volumes(), b.rooms(), b.openings(), b.roofs(), walled, b.hooks())).isEmpty());
	}

	private static PlacedBuilding withBoxes(PlacedBuilding pb, BuildingIr b, List<Box> boxes) {
		BuildingIr shell = new BuildingIr(b.spec(), b.bounds(), b.storeys(), b.volumes(), b.rooms(), b.openings(), b.roofs(), boxes, b.hooks());
		return new PlacedBuilding(pb.id(), pb.lots(), pb.ox(), pb.oy(), pb.oz(), pb.rotation(), shell, pb.siteBoxes());
	}

	private static boolean fails(SceneDoc s, PlacedBuilding mutant, String invariant) {
		SceneDoc m = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(mutant), List.of());
		var all = Invariants.check(m);
		return all.stream().anyMatch(v -> v.invariant().equals(invariant));
	}
}
