package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SampleScenes;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class InvariantsTest {
	/** Applies edits to the single sample building. */
	static SceneDoc mutate(UnaryOperator<List<Box>> boxes, UnaryOperator<List<Opening>> openings, UnaryOperator<List<Box>> site,
			int dox, int doy) {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		BuildingIr sh = b.shell();
		BuildingIr shell = new BuildingIr(sh.spec(), sh.bounds(), sh.storeys(), sh.volumes(), sh.rooms(),
				openings.apply(new ArrayList<>(sh.openings())), sh.roofs(), boxes.apply(new ArrayList<>(sh.boxes())));
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox() + dox, b.oy() + doy, b.oz(), b.rotation(), shell,
				site.apply(new ArrayList<>(b.siteBoxes())));
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.violations());
	}

	static SceneDoc mutateBoxes(UnaryOperator<List<Box>> boxes) {
		return mutate(boxes, o -> o, s -> s, 0, 0);
	}

	static TreeSet<String> ids(List<Violation> vs) {
		TreeSet<String> t = new TreeSet<>();
		for (Violation v : vs) t.add(v.invariant());
		return t;
	}

	static void assertFires(SceneDoc s, String... expected) {
		TreeSet<String> got = ids(Invariants.check(s));
		for (String e : expected) assertTrue(got.contains(e), "expected " + e + " in " + got);
	}

	@Test
	void samplePassesEverything() {
		assertEquals(List.of(), Invariants.check(SampleScenes.house()));
	}

	@Test
	void missingRidgeLeavesRoofOpenButRoomsSealed() {
		SceneDoc s = mutateBoxes(b -> { b.remove(15); return b; });
		TreeSet<String> got = ids(Invariants.check(s));
		assertEquals(new TreeSet<>(List.of("roof-covers", "roof-closed")), got);
	}

	@Test
	void missingNorthWallOpensShell() {
		SceneDoc s = mutateBoxes(b -> { b.remove(1); return b; });
		assertFires(s, "shell-closed");
	}

	@Test
	void missingInteriorDoorWithOpeningBreaksGraphAndWalk() {
		SceneDoc s = mutate(b -> { b.remove(17); return b; }, o -> { o.remove(1); return o; }, x -> x, 0, 0);
		assertFires(s, "room-graph", "rooms-reachable");
	}

	@Test
	void wallInsteadOfInteriorDoorBreaksOnlyTheWalk() {
		SceneDoc s = mutateBoxes(b -> {
			Box d = b.get(17);
			b.set(17, new Box(d.box(), Role.WALL, d.layer(), d.storey()));
			return b;
		});
		assertEquals(new TreeSet<>(List.of("rooms-reachable")), ids(Invariants.check(s)));
	}

	@Test
	void shiftedOutOfLot() {
		assertFires(mutate(b -> b, o -> o, x -> x, 6, 0), "in-lot");
	}

	@Test
	void outOfWorldHeight() {
		assertFires(mutate(b -> b, o -> o, x -> x, 0, 400), "in-world", "no-floating");
	}

	@Test
	void floatingSiteBlock() {
		SceneDoc s = mutate(b -> b, o -> o, x -> {
			x.add(new Box(Box3.of(3, 12, 2, 1, 1, 1), Role.WALL, Layer.INTERIOR, Box.NO_STOREY));
			return x;
		}, 0, 0);
		TreeSet<String> got = ids(Invariants.check(s));
		assertEquals(new TreeSet<>(List.of("no-floating")), got);
		Violation v = Invariants.check(s).get(0);
		assertEquals(List.of(Box3.of(103, 76, -38, 1, 1, 1)), v.cells());
	}

	@Test
	void missingFoundationLeavesGap() {
		SceneDoc s = mutate(b -> b, o -> o, x -> { x.clear(); return x; }, 0, 0);
		assertEquals(new TreeSet<>(List.of("no-gap-below")), ids(Invariants.check(s)));
	}

	@Test
	void probeOverridesScenePatch() {
		TerrainProbe flat = (x, z) -> 64;
		// foundation at 63 is now below the ground and the front columns' lowest voxel is 63 != 64
		assertTrue(ids(Invariants.check(SampleScenes.house(), flat)).contains("no-gap-below"));
	}

	@Test
	void cellsAreCappedAndCounted() {
		SceneDoc s = mutate(b -> b, o -> o, x -> x, 6, 0);
		Violation v = Invariants.check(s).stream().filter(w -> w.invariant().equals("in-lot")).findFirst().orElseThrow();
		assertEquals(64, v.cells().size());
		assertTrue(v.message().contains("first 64 listed"), v.message());
	}

	@Test
	void outputIsDeterministicAndOrdered() {
		SceneDoc s = mutate(b -> { b.remove(15); return b; }, o -> o, x -> x, 6, 0);
		assertEquals(Invariants.check(s), Invariants.check(s));
		List<Violation> vs = Invariants.check(s);
		int last = -1;
		for (Violation v : vs) {
			int i = idx(v.invariant());
			assertTrue(i >= last, "order " + vs);
			last = i;
		}
	}

	private static int idx(String id) {
		for (Invariant i : Invariant.values()) if (i.id.equals(id)) return i.ordinal();
		throw new AssertionError(id);
	}

	@Test
	void rotatedSamplePasses() {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		for (int rot = 1; rot <= 3; rot++) {
			PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), rot, b.shell(), b.siteBoxes());
			var p = nb.placement();
			// the foundation sits on the low row: ground is 63 where local z < 2
			TerrainProbe t = (x, z) -> p.localZ(x, z) >= 0 && p.localZ(x, z) < 2 && p.localX(x, z) >= 0 && p.localX(x, z) < 7 ? 63 : 64;
			SceneDoc rs = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), List.of());
			assertEquals(List.of(), Invariants.check(rs, t), "rotation " + rot);
		}
	}

	/** A slab with a 2-high platform at x 3..4; with a 1-high step block at x 2 the platform top is reachable. */
	static SceneDoc platformScene(boolean withStep) {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		List<Box> boxes = new ArrayList<>();
		boxes.add(new Box(Box3.of(0, 0, 0, 5, 1, 3), Role.FLOOR, Layer.SHELL, 0));
		boxes.add(new Box(Box3.of(3, 1, 0, 2, 2, 3), Role.WALL, Layer.SHELL, 0));
		if (withStep) boxes.add(new Box(Box3.of(2, 1, 1, 1, 1, 1), Role.STAIR, Layer.INTERIOR, 0));
		boxes.add(new Box(Box3.of(0, 1, 1, 1, 2, 1), Role.DOOR, Layer.SHELL, 0));
		var sh = b.shell();
		BuildingIr shell = new BuildingIr(sh.spec(), Box3.of(0, 0, 0, 5, 6, 3), sh.storeys(), sh.volumes(),
				List.of(new dev.fractalbuildings.core.ir.Room("r0", 0, "v0", List.of(Box3.of(3, 3, 0, 2, 1, 3)), "room")),
				List.of(new Opening("o0", dev.fractalbuildings.core.ir.OpeningKind.DOOR, Box3.of(0, 1, 1, 1, 2, 1),
						dev.fractalbuildings.core.ir.Facing.WEST, "r0", Opening.OUTSIDE)),
				List.of(), boxes);
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), 0, shell, List.of());
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), List.of());
	}

	@Test
	void walkClimbsOneBlockSteps() {
		assertFalse(ids(Invariants.check(platformScene(true))).contains("rooms-reachable"));
		assertTrue(ids(Invariants.check(platformScene(false))).contains("rooms-reachable"));
	}

	@Test
	void speed() {
		SceneDoc s = SampleScenes.house();
		long t0 = System.nanoTime();
		for (int i = 0; i < 10_000; i++) Invariants.check(s);
		double ms = (System.nanoTime() - t0) / 1e6;
		System.out.println("10,000 sample checks: " + ms + " ms");
		assertTrue(ms < 20_000);
	}

	@Test
	void reportSummarises() {
		InvariantReport r = new InvariantReport();
		r.add("sample", Invariants.check(SampleScenes.house()));
		r.add("broken", Invariants.check(mutate(b -> b, o -> o, x -> x, 6, 0)));
		assertEquals(2, r.cases());
		assertEquals(1, r.failingCases());
		assertTrue(r.count(Invariant.IN_LOT) == 1);
		assertTrue(r.toMarkdown().contains("`broken` in-lot"), r.toMarkdown());
	}
}
