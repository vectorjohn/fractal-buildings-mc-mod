package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.InstancePassV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * STAIRGROUND: the {@code steps-reach-ground} invariant. Generated buildings on slopes pass it (every type that has a door), and
 * it fails for a flight cut one step short and for a door left at the top of the wall.
 */
class StairGroundTest {
	private static final String ID = Invariant.STEPS_REACH_GROUND.id;
	private static final String[] SLOPES = {"plane:70:1:0:6", "plane:70:0:1:6", "plane:70:1:1:12", "plane:70:-1:0:6", "plane:70:0:-1:8", "flat:70"};

	private static List<Violation> mine(List<Violation> all) {
		List<Violation> v = new ArrayList<>();
		for (Violation x : all) if (x.invariant().equals(ID)) v.add(x);
		return v;
	}

	private static GenRequest request(String type, long seed, String terrain) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		return new GenRequest(seed, new Lot("lot0", 0, 0, 18, 18, Facing.NORTH), type, "medieval", p.terrain(), p.biome());
	}

	@Test
	void generatedBuildingsOnSlopesReachTheGround() {
		int checked = 0;
		for (String type : new String[] {"house", "shop", "workshop", "temple"})
			for (String slope : SLOPES)
				for (long seed = 1; seed <= 6; seed++) {
					GenRequest rq = request(type, seed, slope);
					SceneDoc s;
					try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; } // lot too small or too steep for the type
					assertEquals(List.of(), mine(Invariants.check(s, rq.terrain())), type + " " + slope + " seed " + seed);
					checked++;
				}
		assertTrue(checked > 60, "only " + checked + " scenes generated");
	}

	/** Types whose doors still stand above the ground on a steep slope (houses, shops and workshops dig in to the street since SHOPLOW S2, so their flights are rare). */
	private static final String[] FLIGHT_TYPES = {"temple", "tower", "hall"};

	/** Control: take a building whose flight has at least two steps and drop the step nearest the ground; the check must fail. */
	@Test
	void aFlightCutShortFails() {
		int failed = 0, tried = 0;
		for (long seed = 1; seed <= 18; seed++) {
			GenRequest rq = request(FLIGHT_TYPES[(int) (seed % FLIGHT_TYPES.length)], seed, "plane:70:1:1:12");
			SceneDoc s;
			try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
			PlacedBuilding b = s.buildings().get(0);
			Box lowest = null;
			for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && (lowest == null || x.box().y() < lowest.box().y())) lowest = x;
			if (lowest == null) continue;
			tried++;
			List<Box> site = new ArrayList<>(b.siteBoxes());
			site.remove(lowest);
			PlacedBuilding cut = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
			SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(cut), s.ground(), List.of());
			if (!mine(Invariants.check(c, rq.terrain())).isEmpty()) failed++;
		}
		assertTrue(tried >= 3, "tried " + tried);
		assertEquals(tried, failed, "every building with its lowest step removed must fail steps-reach-ground");
	}

	/** Control: no steps at all in front of a door two blocks above the ground fails. */
	@Test
	void aDoorWithoutStepsFails() {
		GenRequest rq = null;
		SceneDoc s = null;
		PlacedBuilding b = null;
		for (long seed = 1; seed <= 12 && b == null; seed++) {
			rq = request(FLIGHT_TYPES[(int) (seed % FLIGHT_TYPES.length)], seed, "plane:70:1:0:6");
			try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
			if (s.buildings().get(0).siteBoxes().stream().anyMatch(x -> x.role() == Role.STAIR)) b = s.buildings().get(0);
		}
		assertTrue(b != null, "no building with a flight");
		boolean[][] fp = InstancePassV1.footprint(b.shell());
		List<Box> site = new ArrayList<>(b.siteBoxes());
		site.removeIf(x -> x.box().x() < 0 || x.box().z() < 0 || x.box().x() >= fp.length || x.box().z() >= fp[0].length || !fp[x.box().x()][x.box().z()]); // the whole flight, steps and their foundations
		PlacedBuilding bare = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
		SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(bare), s.ground(), List.of());
		assertFalse(mine(Invariants.check(c, rq.terrain())).isEmpty());
	}
}
