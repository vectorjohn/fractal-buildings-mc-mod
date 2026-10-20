package dev.fractalbuildings.core.gen.v1.types.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Tower properties (plan T-tower): tall and narrow, one core through every storey, under the height cap; the
 * watchtower variants (a stilted lookout, a top-heavy tower) and unglazed window holes.
 */
class TowerTypeTest {
	static final int SEEDS = 600;
	static final int[][] ENVELOPES = {{7, 7}, {9, 9}, {11, 11}, {13, 13}};

	static BuildContext ctx(String style, long seed, int w, int d, int storeys, int maxHeight) {
		BuildingSpec sp = new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "tower", w, d, storeys, "auto", maxHeight, 1);
		return new BuildContext(sp, new TowerType(), StyleLibrary.get(style));
	}

	static Massing mass(String style, long seed, int w, int d, int storeys, int maxHeight) {
		return new TowerType().mass(ctx(style, seed, w, d, storeys, maxHeight));
	}

	@Test
	void towersAreAtLeastTwiceAsTallAsWideAndStayUnderTheCap() {
		for (String style : StyleLibrary.ids())
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS; seed++) {
					int storeys = 4 + new Draws(seed * 7 + e[0]).below(7);
					BuildContext c = ctx(style, seed, e[0], e[1], storeys, 300);
					Massing m = new TowerType().mass(c);
					boolean stilted = m.main().kind().equals(TowerType.LOOKOUT);
					int lift = stilted ? TowerType.legHeight(c, m.grid()) : 0;
					int wide = 0, top = 0;
					for (Mass a : m.masses()) {
						wide = Math.max(wide, Math.max(a.sx(), a.sz()));
						top = Math.max(top, a.topSlab(m.grid()) + lift);
					}
					String ctx = style + " " + e[0] + "x" + e[1] + " seed " + seed;
					if (stilted) assertTrue(2 * top >= 3 * wide, ctx + ": stilted height " + top + " < 1.5 x width " + wide);
					else assertTrue(top >= 2 * wide, ctx + ": height " + top + " < 2 x width " + wide);
					assertTrue(top + 2 <= 300, ctx + ": top " + top);
					if (stilted) {
						assertEquals(1, m.storeys(), ctx);
						assertTrue(m.cores().isEmpty(), ctx);
						continue;
					}
					assertEquals(storeys, m.storeys(), ctx + ": storeys kept when there is room");
					assertEquals(1, m.cores().size(), ctx);
					Core k = m.cores().get(0);
					assertTrue(k.from() == 0 && k.to() == m.storeys(), ctx + ": the core serves every storey");
				}
	}

	@Test
	void heightCapLowersTheStoreys() {
		Massing m = mass("plain", 3, 11, 11, 10, 34);
		assertTrue(m.storeys() < 10 && m.grid().y(m.storeys()) + 2 <= 34);
	}

	@Test
	void variantsAndFootprintsVary() {
		Set<String> shapes = new HashSet<>();
		Set<Integer> widths = new HashSet<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			Massing m = mass("medieval", seed, 11, 11, 8, 300);
			shapes.add(m.masses().stream().map(Mass::kind).reduce("", (a, b) -> a + "/" + b));
			widths.add(m.main().sx() * 100 + m.main().sz());
		}
		assertTrue(shapes.size() >= 3, "shapes " + shapes);
		assertTrue(widths.size() >= 6, "footprints " + widths);
	}

	// ---- watchtower variants ----

	/** A tower placed on flat ground in a lot; null terrain probe use is not needed. */
	static SceneDoc scene(long seed, String style, int lot) { return scene(seed, style, lot, "flat:64"); }

	static SceneDoc scene(long seed, String style, int lot, String terrain) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		return new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, lot, lot, Facing.NORTH), "tower", style, p.terrain(), p.biome()));
	}

	static boolean stilted(PlacedBuilding b) { return b.shell().volumes().stream().anyMatch(v -> v.id().equals("leg0")); }

	static Volume volume(BuildingIr ir, String id) {
		for (Volume v : ir.volumes()) if (v.id().equals(id)) return v;
		return null;
	}

	/** First seed below 400 whose tower matches. */
	static SceneDoc find(String style, int lot, Predicate<PlacedBuilding> p) {
		for (long seed = 0; seed < 400; seed++) {
			SceneDoc s = scene(seed, style, lot);
			if (p.test(s.buildings().get(0))) return s;
		}
		throw new AssertionError("no matching tower in " + style + " " + lot);
	}

	static boolean topHeavy(BuildingIr ir) {
		Volume room = volume(ir, "jetty1"), shaft = volume(ir, "main0");
		return room != null && room.box().sx() >= shaft.box().sx() + 2 && room.box().sz() >= shaft.box().sz() + 2;
	}

	static SceneDoc withBoxes(SceneDoc s, java.util.function.UnaryOperator<List<Box>> f) {
		PlacedBuilding b = s.buildings().get(0);
		BuildingIr sh = b.shell();
		BuildingIr shell = new BuildingIr(sh.spec(), sh.bounds(), sh.storeys(), sh.volumes(), sh.rooms(), sh.openings(), sh.roofs(), f.apply(new ArrayList<>(sh.boxes())), sh.hooks());
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), shell, b.siteBoxes());
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.violations());
	}

	static TreeSet<String> fired(SceneDoc s) { return fired(s, "flat:64"); }

	static TreeSet<String> fired(SceneDoc s, String terrain) {
		TreeSet<String> t = new TreeSet<>();
		for (Violation v : Invariants.check(s, ProbeSpecs.parse(terrain).terrain())) t.add(v.invariant());
		return t;
	}

	@Test
	void everyVariantAppearsAndPassesTheInvariants() {
		int stilted = 0, top = 0, classic = 0;
		for (long seed = 0; seed < 300; seed++) {
			SceneDoc s = scene(seed, seed % 2 == 0 ? "medieval" : "plain", 13);
			PlacedBuilding b = s.buildings().get(0);
			if (stilted(b)) stilted++;
			else if (topHeavy(b.shell())) top++;
			else classic++;
			assertEquals(new TreeSet<String>(), fired(s), "seed " + seed);
		}
		assertTrue(stilted >= 20 && top >= 15 && classic >= 100, "stilted " + stilted + " top-heavy " + top + " classic " + classic);
	}

	@Test
	void stiltedLegsStandOnTheGroundAndHoldTheRoom() {
		int n = 0;
		for (long seed = 0; seed < 300 && n < 25; seed++) {
			SceneDoc s = scene(seed, "medieval", 13);
			PlacedBuilding b = s.buildings().get(0);
			if (!stilted(b)) continue;
			n++;
			BuildingIr ir = b.shell();
			int lift = ir.storeys().get(0).height();
			assertEquals(0, ir.storeys().get(0).y());
			assertEquals(lift, ir.storeys().get(1).y(), "the room's floor is on top of the legs");
			assertTrue(ir.rooms().stream().allMatch(r -> r.storey() >= 1), "no room in the leg zone");
			VoxelGrid g = VoxelGrid.of(b);
			Volume room = volume(ir, "main0");
			assertEquals(lift, room.box().y());
			for (int i = 0; i < 4; i++) {
				Volume leg = volume(ir, "leg" + i);
				assertEquals(0, leg.box().y());
				for (int y = 0; y < lift; y++) assertEquals(Role.BEAM, g.get(leg.box().x(), y, leg.box().z()), "seed " + seed + " leg " + i + " y " + y);
				// each leg is a corner of the room above
				assertTrue(leg.box().x() == room.box().x() || leg.box().x() == room.box().maxX() - 1);
				assertTrue(leg.box().z() == room.box().z() || leg.box().z() == room.box().maxZ() - 1);
				assertEquals(Role.FLOOR, g.get(leg.box().x(), lift, leg.box().z()));
			}
			// open space under the room: the centre column is air from the ground to the floor
			int cx = room.box().x() + room.box().sx() / 2, cz = room.box().z() + room.box().sz() / 2;
			for (int y = 0; y < lift; y++) assertEquals(Role.AIR, g.get(cx, y, cz), "seed " + seed + " under the room");
			// the way up is the front steps: the door is above the leg zone, with a flight of steps down to the terrain
			Opening door = ir.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE)).findFirst().orElseThrow();
			assertEquals(lift + 1, door.box().y());
			assertTrue(b.siteBoxes().stream().anyMatch(x -> x.role() == Role.STAIR), "seed " + seed + ": front steps");
		}
		assertTrue(n >= 10, "stilted towers found: " + n);
	}


	/** The lifted door has a walkable way down on sloped and noisy ground too (front steps with foundations). */
	@Test
	void stiltedLookoutsReachTheGroundOnAnyTerrain() {
		for (String terrain : List.of("flat:64", "plane:64:1:0:4", "plane:64:0:1:3", "noise:7:64:6:32", "noise:3:70:8:24")) {
			int n = 0;
			for (long seed = 0; seed < 200; seed++) {
				SceneDoc s = scene(seed, "medieval", 13, terrain);
				if (!stilted(s.buildings().get(0))) continue;
				n++;
				assertEquals(new TreeSet<String>(), fired(s, terrain), terrain + " seed " + seed);
			}
			assertTrue(n >= 10, terrain + ": stilted towers " + n);
		}
	}

	@Test
	void aLookoutWithItsStairRemovedFailsEntranceReachable() {
		for (String terrain : List.of("flat:64")) {
			SceneDoc s = null;
			for (long seed = 0; seed < 200 && s == null; seed++) {
				SceneDoc c = scene(seed, "medieval", 13, terrain);
				if (stilted(c.buildings().get(0))) s = c;
			}
			PlacedBuilding b = s.buildings().get(0);
			PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(),
					b.siteBoxes().stream().filter(x -> x.role() != Role.STAIR && x.layer() != dev.fractalbuildings.core.ir.Layer.FOUNDATION || x.box().y() < 0).toList());
			SceneDoc bare = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.violations());
			assertTrue(fired(bare, terrain).contains("entrance-reachable"), terrain + ": " + fired(bare, terrain));
		}
	}
	@Test
	void aMissingOrShortLegStillFails() {
		SceneDoc s = find("medieval", 13, TowerTypeTest::stilted);
		assertEquals(new TreeSet<String>(), fired(s));
		BuildingIr ir = s.buildings().get(0).shell();
		Volume leg = volume(ir, "leg2");
		Predicate<Box> isLeg = b -> b.role() == Role.BEAM && b.box().x() == leg.box().x() && b.box().z() == leg.box().z() && b.box().y() == 0 && b.box().sx() == 1 && b.box().sz() == 1;
		// the leg is gone
		SceneDoc gone = withBoxes(s, l -> { l.removeIf(isLeg); return l; });
		assertTrue(fired(gone).contains("no-gap-below"), "a missing leg: " + fired(gone));
		// the leg stops short of the ground
		SceneDoc short_ = withBoxes(s, l -> {
			for (int i = 0; i < l.size(); i++) {
				Box b = l.get(i);
				if (isLeg.test(b)) l.set(i, new Box(Box3.of(b.box().x(), 1, b.box().z(), 1, b.box().sy() - 1, 1), b.role(), b.layer(), b.storey()));
			}
			return l;
		});
		assertTrue(fired(short_).contains("no-gap-below"), "a short leg: " + fired(short_));
		// no legs, no stair and no front steps: the room hangs in the air
		SceneDoc none = withBoxes(s, l -> { l.removeIf(b -> b.storey() == 0); return l; });
		PlacedBuilding pb = none.buildings().get(0);
		SceneDoc bare = new SceneDoc(none.genVersion(), none.seed(), none.terrain(), none.lots(),
				List.of(new PlacedBuilding(pb.id(), pb.lots(), pb.ox(), pb.oy(), pb.oz(), pb.rotation(), pb.shell(), List.of())), none.violations());
		assertTrue(fired(bare).contains("no-floating"), "no support: " + fired(bare));
	}

	@Test
	void topHeavyRoomOverhangsTheShaftOnAllSides() {
		int n = 0;
		for (long seed = 0; seed < 400 && n < 25; seed++) {
			PlacedBuilding b = scene(seed, "medieval", 13).buildings().get(0);
			if (!topHeavy(b.shell())) continue;
			n++;
			Volume room = volume(b.shell(), "jetty1"), shaft = volume(b.shell(), "main0");
			assertEquals("main0", room.parent());
			assertTrue(room.box().x() <= shaft.box().x() - 1 && room.box().z() <= shaft.box().z() - 1, "overhangs west and north");
			assertTrue(room.box().maxX() >= shaft.box().maxX() + 1 && room.box().maxZ() >= shaft.box().maxZ() + 1, "overhangs east and south");
			assertTrue(room.box().sx() >= shaft.box().sx() + 2);
			assertTrue(b.shell().storeys().size() >= 4);
		}
		assertTrue(n >= 10, "top-heavy towers found: " + n);
	}

	@Test
	void windowHolesHaveNoGlassAndTheShellStaysClosed() {
		int holes = 0, glazed = 0;
		for (long seed = 0; seed < 200; seed++) {
			SceneDoc s = scene(seed, "medieval", 13);
			PlacedBuilding b = s.buildings().get(0);
			VoxelGrid g = VoxelGrid.of(b);
			int air = 0, glass = 0;
			for (Opening o : b.shell().openings()) {
				if (o.kind() != OpeningKind.WINDOW) continue;
				Role r = g.get(o.box().x(), o.box().y(), o.box().z());
				if (r == Role.AIR) air++;
				else if (r == Role.WINDOW) glass++;
			}
			assertFalse(air > 0 && glass > 0, "seed " + seed + " mixes holes and glass");
			if (air > 0) {
				holes++;
				assertEquals(new TreeSet<String>(), fired(s), "seed " + seed);
			} else if (glass > 0) glazed++;
		}
		assertTrue(holes >= 20 && glazed >= 40, "holes " + holes + " glazed " + glazed);
	}

	@Test
	void anUndeclaredHoleInTheWallIsStillALeak() {
		SceneDoc s = find("medieval", 13, b -> !stilted(b) && !topHeavy(b.shell()) && b.shell().openings().stream().anyMatch(o -> o.kind() == OpeningKind.WINDOW));
		assertEquals(new TreeSet<String>(), fired(s));
		BuildingIr ir = s.buildings().get(0).shell();
		Opening w = ir.openings().stream().filter(o -> o.kind() == OpeningKind.WINDOW).findFirst().orElseThrow();
		// air one cell above the window column, in the wall, declared by no opening
		SceneDoc leak = withBoxes(s, l -> {
			l.add(new Box(Box3.of(w.box().x(), w.box().maxY(), w.box().z(), 1, 1, 1), Role.AIR, dev.fractalbuildings.core.ir.Layer.SHELL, 0));
			return l;
		});
		TreeSet<String> got = fired(leak);
		assertTrue(got.contains("shell-closed") || got.contains("roof-closed"), "an undeclared hole: " + got);
	}
}
