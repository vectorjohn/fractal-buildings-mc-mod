package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Interior rules (plan step 4.5) over every {@link TestMassings} fixture, both styles and many seeds. */
class RoomSplitTest {
	static final List<String> STYLES = List.of("plain", "medieval");
	static final int SEEDS = 20;

	/** A fixture type that records its facade plan. */
	static final class Recording implements BuildingType {
		final TestMassings.FixtureType base;
		FacadePlan plan;

		Recording(Massing m) { base = new TestMassings.FixtureType(m); }

		@Override public String id() { return base.id(); }

		@Override public TypeLimits limits() { return base.limits(); }

		@Override public Massing mass(BuildContext c) { return base.massing(); }

		@Override public Program facade() {
			return c -> {
				FacadePass.run(c);
				plan = c.facadePlan();
			};
		}
	}

	interface Each {
		void run(String name, String style, long seed, Massing m, Recording t, BuildingIr ir);
	}

	static void forEachCase(Each each) {
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String style : STYLES)
				for (long seed = 0; seed < SEEDS; seed++) {
					Recording t = new Recording(f.getValue());
					BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(seed, style), t);
					each.run(f.getKey(), style, seed, f.getValue(), t, ir);
				}
	}

	static boolean isPartition(Box b) { return b.layer() == Layer.INTERIOR && b.role() == Role.WALL; }

	static Set<String> partitionCells(BuildingIr ir, int storey) {
		Set<String> out = new TreeSet<>();
		for (Box b : ir.boxes()) if (isPartition(b) && b.storey() == storey) cells(b.box(), out);
		return out;
	}

	static void cells(Box3 b, Set<String> out) {
		for (int x = b.x(); x < b.maxX(); x++) for (int z = b.z(); z < b.maxZ(); z++) out.add(x + "," + z);
	}

	@Test
	void roomsAndPartitionsTileEachFloorExactly() {
		int[] partitioned = {0};
		forEachCase((name, style, seed, m, t, ir) -> {
			for (Volume v : ir.volumes()) {
				Box3 vb = v.box();
				for (int s = 0; s < ir.storeys().size(); s++) {
					List<Room> rooms = new ArrayList<>();
					for (Room r : ir.rooms()) if (r.volume().equals(v.id()) && r.storey() == s) rooms.add(r);
					if (rooms.isEmpty()) continue;
					Set<String> want = new TreeSet<>();
					cells(Box3.of(vb.x() + 1, 0, vb.z() + 1, vb.sx() - 2, 1, vb.sz() - 2), want);
					Set<String> got = new TreeSet<>();
					int n = 0;
					for (Room r : rooms) for (Box3 p : r.parts()) {
						Set<String> one = new TreeSet<>();
						cells(p, one);
						n += one.size();
						got.addAll(one);
					}
					Set<String> part = partitionCells(ir, s);
					part.removeIf(k -> !want.contains(k));
					if (!rooms.isEmpty() && !part.isEmpty()) partitioned[0]++;
					assertEquals(n, got.size(), name + " " + style + " " + seed + " rooms overlap on " + v.id() + " storey " + s);
					for (String k : part) assertFalse(got.contains(k), name + " " + style + " " + seed + " partition cell " + k + " is inside a room");
					got.addAll(part);
					assertEquals(want, got, name + " " + style + " " + seed + " rooms + partitions do not tile " + v.id() + " storey " + s);
				}
			}
		});
		assertTrue(partitioned[0] > 50, "partitions should appear in many floors, saw " + partitioned[0]);
	}

	@Test
	void everyRoomSideIsAtLeastRoomMinOrTheWholeInterior() {
		forEachCase((name, style, seed, m, t, ir) -> {
			int min = StyleLibrary.get(style).interior().roomMin();
			for (Room r : ir.rooms()) {
				if (r.use().equals("corridor")) continue; // corridors are 2-3 wide by design
				Box3 vb = ir.volumes().stream().filter(v -> v.id().equals(r.volume())).findFirst().orElseThrow().box();
				for (Box3 p : r.parts()) {
					assertTrue(p.sx() >= Math.min(min, vb.sx() - 2) && p.sz() >= Math.min(min, vb.sz() - 2), name + " " + style + " " + seed + " room " + r.id() + " is " + p.sx() + "x" + p.sz());
				}
			}
		});
	}

	@Test
	void noWindowBayOrEntranceInsideCellIsAPartition() {
		forEachCase((name, style, seed, m, t, ir) -> {
			for (FacadePlan.Layout l : t.plan.layouts()) {
				for (int b = 0; b < l.bays().length; b++) {
					if (!FacadePlan.WINDOW.equals(l.kinds().get(b))) continue;
					int p = l.centre(b);
					String cell = l.face().insideX(p) + "," + l.face().insideZ(p);
					assertFalse(partitionCells(ir, l.face().storey()).contains(cell), name + " " + style + " " + seed + " partition behind window at " + cell + " storey " + l.face().storey());
				}
			}
			for (FacadePlan.Entrance e : t.plan.entrances()) {
				String cell = e.face().insideX(e.pos()) + "," + e.face().insideZ(e.pos());
				assertFalse(partitionCells(ir, e.face().storey()).contains(cell), name + " " + style + " " + seed + " partition behind the entrance at " + cell);
			}
			long windows = ir.openings().stream().filter(o -> o.kind() == OpeningKind.WINDOW).count();
			long bays = t.plan.layouts().stream().flatMap(l -> l.kinds().stream()).filter(FacadePlan.WINDOW::equals).count();
			assertTrue(windows >= bays - 2 * m.cores().size() * ir.storeys().size(), name + " " + style + " " + seed + ": " + windows + " windows of " + bays + " bays");
		});
	}

	@Test
	void roomGraphAndReachabilityHoldOnEveryFixture() {
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String style : STYLES)
				for (long seed = 0; seed < SEEDS; seed++) {
					List<Violation> v = Invariants.check(TestMassings.scene(f.getValue(), seed, style, FixturePipelineTest.GROUND.get("flat")));
					if (!v.isEmpty()) bad.add(f.getKey() + " " + style + " " + seed + ": " + v.get(0).invariant() + " " + v.get(0).message());
				}
		if (!bad.isEmpty()) fail(bad.size() + " cases fail, first: " + bad.subList(0, Math.min(5, bad.size())));
	}

	static Role roleAt(BuildingIr ir, int x, int y, int z) {
		Role r = Role.AIR;
		for (Box b : ir.boxes()) if (b.box().contains(x, y, z)) r = b.role();
		return r;
	}

	@Test
	void hooksAreRoomAirOnFloorOutsideCoresAndDoorApproaches() {
		int[] total = {0};
		forEachCase((name, style, seed, m, t, ir) -> {
			for (Hook h : ir.hooks()) {
				total[0]++;
				String at = name + " " + style + " " + seed + " hook " + h.id() + " " + h.kind() + " at " + h.x() + "," + h.y() + "," + h.z();
				Room r = ir.rooms().stream().filter(q -> q.id().equals(h.room())).findFirst().orElseThrow();
				assertTrue(r.parts().stream().anyMatch(p -> p.contains(h.x(), h.y(), h.z())), at + " is not in its room");
				assertEquals(Role.AIR, roleAt(ir, h.x(), h.y(), h.z()), at + " cell not air");
				assertEquals(Role.AIR, roleAt(ir, h.x(), h.y() + 1, h.z()), at + " head not air");
				assertTrue(roleAt(ir, h.x(), h.y() - 1, h.z()).solid, at + " not on a solid floor");
				for (Core k : m.cores()) {
					assertFalse(h.x() >= k.x() - 1 && h.x() <= k.maxX() && h.z() >= k.z() - 1 && h.z() <= k.maxZ() && r.storey() >= k.from() && r.storey() < k.to(), at + " is in or next to a core");
				}
				for (Opening o : ir.openings()) {
					if (o.kind() != OpeningKind.DOOR) continue;
					Box3 d = o.box();
					assertFalse(h.y() >= d.y() && h.y() < d.maxY() && h.x() >= d.x() - 1 && h.x() <= d.maxX() && h.z() >= d.z() - 1 && h.z() <= d.maxZ(), at + " is in front of door " + o.id());
				}
			}
		});
		assertTrue(total[0] > 500, "hooks should be placed, saw " + total[0]);
	}

	@Test
	void typicalFloorsRepeatUpToAYShift() {
		for (String style : STYLES)
			for (long seed = 0; seed < 5; seed++) {
				BuildingIr ir = TestMassings.shell(TestMassings.all().get("setback"), seed, style);
				// the facade pass changes bay rhythm every band (bandSize storeys), and a plan follows the bays: floors repeat within a band
				int bs = StyleLibrary.get(style).facade().bandSize();
				for (int s = 3; s < 8; s++) {
					int ref = s - 1; // the previous storey, if it is in the same facade band
					if (FacadePass.band(TestMassings.all().get("setback").mass("shaft1"), s, bs) != FacadePass.band(TestMassings.all().get("setback").mass("shaft1"), ref, bs) || ref < 2) continue;
					assertEquals(plan(ir, ref), plan(ir, s), style + " " + seed + ": storey " + s + " partitions and doors differ from storey " + ref);
					assertEquals(hooks(ir, ref), hooks(ir, s), style + " " + seed + ": storey " + s + " hooks differ from storey " + ref);
					if (s - 2 >= 2 && FacadePass.band(TestMassings.all().get("setback").mass("shaft1"), s - 2, bs) == FacadePass.band(TestMassings.all().get("setback").mass("shaft1"), s, bs)) assertEquals(stairs(ir, s - 2), stairs(ir, s), "flights of the same parity are identical");
				}
			}
	}

	static List<String> plan(BuildingIr ir, int s) {
		int y = ir.storeys().get(s).y();
		List<String> out = new ArrayList<>();
		for (Box b : ir.boxes()) if (b.layer() == Layer.INTERIOR && b.storey() == s && (b.role() == Role.WALL || b.role() == Role.DOOR)) out.add(b.role() + " " + b.box().x() + "," + (b.box().y() - y) + "," + b.box().z() + " " + b.box().sx() + "x" + b.box().sy() + "x" + b.box().sz());
		return out;
	}

	static List<String> stairs(BuildingIr ir, int s) {
		int y = ir.storeys().get(s).y();
		List<String> out = new ArrayList<>();
		for (Box b : ir.boxes()) if (b.layer() == Layer.INTERIOR && b.storey() == s && (b.role() == Role.STAIR || b.role() == Role.AIR)) out.add(b.role() + " " + b.box().x() + "," + (b.box().y() - y) + "," + b.box().z() + " " + b.box().sx() + "x" + b.box().sy() + "x" + b.box().sz());
		return out;
	}

	static List<String> hooks(BuildingIr ir, int s) {
		int y = ir.storeys().get(s).y();
		List<String> out = new ArrayList<>();
		for (Hook h : ir.hooks()) {
			Room r = ir.rooms().stream().filter(q -> q.id().equals(h.room())).findFirst().orElseThrow();
			if (r.storey() == s) out.add(h.kind() + " " + h.x() + "," + (h.y() - y) + "," + h.z() + " " + h.facing());
		}
		return out;
	}

	@Test
	void bigFloorsGetACorridorAndSmallOnesDont() {
		Massing big = new Massing(StoreyGrid.of(1, 4, 4), List.of(Mass.ground("main0", "main", 3, 4, 24, 16, 1)), List.of());
		int withCorridor = 0;
		for (long seed = 0; seed < 10; seed++) {
			BuildingIr ir = TestMassings.shell(big, seed, "plain");
			if (ir.rooms().stream().anyMatch(r -> r.use().equals("corridor"))) withCorridor++;
			assertTrue(ir.rooms().stream().anyMatch(r -> r.use().equals("entry")));
			assertTrue(ir.rooms().size() >= 6, "a 22x14 floor should have several rooms");
		}
		assertTrue(withCorridor >= 5, "corridors on 24x16 floors: " + withCorridor + " of 10");
		BuildingIr stairs = TestMassings.shell(TestMassings.all().get("box3"), 3, "plain");
		assertTrue(stairs.rooms().stream().anyMatch(r -> r.use().equals("stair")));
		BuildingIr small = TestMassings.shell(TestMassings.all().get("box1"), 3, "plain");
		assertFalse(small.rooms().stream().anyMatch(r -> r.use().equals("corridor")));
	}

	@Test
	void sixtyStoreyTowerPlansAFewBandsAndStaysConnected() {
		StoreyGrid g = StoreyGrid.of(60, 4, 4);
		Massing tower = new Massing(g, List.of(Mass.ground("main0", "main", 4, 4, 16, 16, 60)), List.of(new Core(6, 8, 5, 2, 0, 60)));
		long t0 = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		var scene = TestMassings.scene(tower, 1, "plain", FixturePipelineTest.GROUND.get("flat"));
		long ms = (java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - t0) / 1_000_000; // thread CPU time: load-independent
		List<Violation> v = Invariants.check(scene);
		assertTrue(v.isEmpty(), v.isEmpty() ? "" : v.get(0).invariant() + " " + v.get(0).message());
		assertTrue(ms < 20_000, "generation took " + ms + " cpu ms");
		BuildingIr ir = scene.buildings().get(0).shell();
		assertEquals(plan(ir, 4), plan(ir, 6), "typical floors repeat within a facade band");
		assertEquals(plan(ir, 40), plan(ir, 42), "typical floors repeat within a facade band");
	}
}
