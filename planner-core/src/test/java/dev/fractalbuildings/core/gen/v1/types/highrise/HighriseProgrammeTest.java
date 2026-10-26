package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.HighriseChecks;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * B3a: the floor programme wired into the interior pass (plate templates, uses, hooks, shaft, lights), over real V1
 * output: 32 seeds on each of the four lot sizes.
 */
class HighriseProgrammeTest {
	static final int[] LOTS = {20, 28, 36, 44};
	static final int SEEDS = 32;
	/** Uses a high-rise room may have (the generic planner's "room", "stair", "hall" are not among them). */
	static final Set<String> USES = Set.of("lobby", "corridor", "open", "banquet", "flat", "bedroom", "office", "entry", "kitchen", "storage", "stairwell", "plant");

	static SceneDoc gen(int lot, long seed) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		return new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, lot, lot, Facing.NORTH), "highrise", seed % 2 == 0 ? "medieval" : "plain", p.terrain(), p.biome()));
	}

	static List<SceneDoc> cases(int lot) {
		List<SceneDoc> out = new ArrayList<>();
		for (long seed = 1; seed <= SEEDS; seed++) out.add(gen(lot, seed));
		return out;
	}

	static BuildingIr ir(SceneDoc s) { return s.buildings().get(0).shell(); }

	/** Every design check is clean (B3b closed floor-repeat, roof-reachable and the stair house's doors). */
	@Test
	void designChecksPass() {
		List<String> left = new ArrayList<>();
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot))
				for (Violation v : HighriseChecks.check(s)) {
					left.add("lot " + lot + " " + v.invariant() + ": " + v.message());
				}
		assertEquals(List.of(), left, "design warnings");
	}

	/** Typical floors repeat: consecutive storeys of equal height and equal rooms have the same partitions, doors and shaft doorway. */
	@Test
	void interiorRepeats() {
		int pairs = 0;
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				for (Storey a : b.storeys()) {
					Storey n = b.storeys().stream().filter(q -> q.index() == a.index() + 1).findFirst().orElse(null);
					if (n == null || n.height() != a.height() || !uses(b, a.index()).equals(uses(b, n.index())) || !windows(b, a).equals(windows(b, n))) continue; // same windows: the facade bands (B3b) decide where partitions may end
					pairs++;
					assertEquals(interior(b, a), interior(b, n), "lot " + lot + " storeys " + a.index() + " and " + n.index());
				}
			}
		assertTrue(pairs > 500, "pairs " + pairs);
	}

	/** Exterior window openings (x, z, height above the slab) of a storey. */
	static Set<String> windows(BuildingIr b, Storey st) {
		Set<String> out = new TreeSet<>();
		for (Opening o : b.openings())
			if (o.kind() == OpeningKind.WINDOW && o.box().y() > st.y() && o.box().y() < st.y() + st.height()) out.add(o.box().x() + "," + (o.box().y() - st.y()) + "," + o.box().z() + "," + o.box().sy());
		return out;
	}

	static Map<String, Integer> uses(BuildingIr b, int storey) {
		Map<String, Integer> m = new TreeMap<>();
		for (Room r : b.rooms()) if (r.storey() == storey) m.merge(r.volume() + "/" + r.use(), 1, Integer::sum);
		return m;
	}

	/** Walls, doors and glass boxes of the interior layer, y relative to the storey's slab. */
	static Set<String> interior(BuildingIr b, Storey st) {
		Set<String> out = new TreeSet<>();
		for (Box x : b.boxes()) {
			if (x.layer() != Layer.INTERIOR || x.storey() != st.index() || !(x.role() == Role.WALL || x.role() == Role.DOOR || x.role() == Role.WINDOW)) continue;
			out.add(x.role() + " " + x.box().x() + "," + (x.box().y() - st.y()) + "," + x.box().z() + " " + x.box().sx() + "x" + x.box().sy() + "x" + x.box().sz());
		}
		return out;
	}

	@Test
	void everyRoomHasATowerUseAndNoWorkstationStands() {
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				for (Room r : b.rooms()) assertTrue(USES.contains(r.use()), "lot " + lot + ": room " + r.id() + " use " + r.use());
				for (Hook h : b.hooks()) assertTrue(!h.kind().equals("workstation"), "workstation hook " + h.id());
			}
	}

	/** Lobby on storey 0, the programme's special floors and a stair house with stairwell and machine room are all there over the sample. */
	@Test
	void programmeShowsUpInTheRooms() {
		Set<String> seen = new TreeSet<>();
		int stairhouses = 0, towers = 0;
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				towers++;
				int top = b.storeys().size() - 1;
				Set<String> topUses = new TreeSet<>();
				for (Room r : b.rooms()) {
					if (r.storey() == 0 && r.use().equals("lobby")) seen.add("lobby");
					if (r.use().equals("banquet")) seen.add("banquet");
					if (r.use().equals("open")) seen.add("open");
					if (r.use().equals("kitchen")) seen.add("kitchen");
					if (r.storey() == top && b.storeys().size() > 1) topUses.add(r.use());
				}
				if (topUses.equals(Set.of("stairwell", "plant"))) stairhouses++;
			}
		assertTrue(seen.containsAll(Set.of("lobby", "banquet", "open", "kitchen")), "seen " + seen);
		assertEquals(towers, stairhouses, "towers whose top storey is the stair house with a machine room");
	}

	/** The glass doorway of the shaft: one 1x2 window-role box per habitable storey in the hall-side wall; no door and no facade window uses it. */
	@Test
	void shaftHasADoorwayPerStoreyAndNoFacadeWindowOpening() {
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				int glass = 0;
				for (Box x : b.boxes()) if (x.layer() == Layer.INTERIOR && x.role() == Role.WINDOW) { glass++; assertEquals(1, x.box().sx() * x.box().sz()); assertEquals(2, x.box().sy()); }
				assertEquals(b.storeys().size() - 1, glass, "lot " + lot);
				for (Opening o : b.openings()) if (o.kind() == OpeningKind.WINDOW) assertTrue(o.to().equals(Opening.OUTSIDE) || o.from().equals(Opening.OUTSIDE), "window " + o.id() + " joins two rooms");
			}
	}

	/** The plates fit: no floor falls back to the generic planner on 128 towers (the piers keep partitions off windows). */
	@Test
	void noFloorFallsBackToTheGenericPlanner() {
		int[] st0 = TowerFloors.STATS.get().clone();
		for (int lot : LOTS) cases(lot);
		int[] st = TowerFloors.STATS.get();
		int planned = st[0] - st0[0], fell = st[1] - st0[1];
		System.out.println("TowerPlate fallback rate over " + LOTS.length * SEEDS + " towers: " + fell + " of " + planned + " floor plans");
		assertTrue(planned > 600, "planned " + planned); // typical floors of a tier share one facade layout since B3b, so the interior pass plans each only once (1155 before, 943 after)
		assertEquals(0, fell, "floors that fell back to the guillotine planner");
	}

	/** Piers: the stair house has no windows, and no window of a tier meets the end of a block wall. */

	/** Direct piers check: no exterior window has an interior partition ending on the cell just inside it. */
	@Test
	void noWindowMeetsTheEndOfAPartition() {
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				List<Box> walls = new ArrayList<>();
				for (Box x : b.boxes()) if (x.layer() == Layer.INTERIOR && x.role() == Role.WALL) walls.add(x);
				for (Opening o : b.openings()) {
					if (o.kind() != OpeningKind.WINDOW || !(o.to().equals(Opening.OUTSIDE) || o.from().equals(Opening.OUTSIDE))) continue;
					int ix = o.box().x() - o.facing().dx, iz = o.box().z() - o.facing().dz;
					for (Box w : walls)
						assertTrue(!w.box().contains(ix, o.box().y(), iz), "lot " + lot + ": window " + o.id() + " at " + o.box() + " meets a partition at " + ix + "," + iz);
				}
			}
	}
	@Test
	void stairHouseIsBlankAndPiersHoldTheBlockWalls() {
		for (int lot : LOTS)
			for (SceneDoc s : cases(lot)) {
				BuildingIr b = ir(s);
				int top = b.storeys().size() - 1;
				for (Opening o : b.openings()) {
					if (o.kind() != OpeningKind.WINDOW) continue;
					Room r = b.rooms().stream().filter(q -> q.id().equals(o.from()) || q.id().equals(o.to())).findFirst().orElse(null);
					if (r != null) assertTrue(r.storey() != top, "lot " + lot + ": window on the stair house " + o.id());
				}
			}
	}
}
