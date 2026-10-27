package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** STEPBURY (6.13): the air over every step is claimed (an AIR site box), so the world's terrain or a tree cannot close a flight. */
class StepHeadroomTest {
	private static boolean covered(List<Box> site, int x, int y, int z) {
		for (Box b : site) {
			Box3 q = b.box();
			if (b.role() == Role.AIR && q.contains(x, y, z)) return true;
		}
		return false;
	}

	/** Counts, for a building, the step cells whose head room is neither claimed nor the shell's. */
	static int unclaimed(PlacedBuilding b, int[] steps) {
		VoxelGrid bare = VoxelGrid.of(new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), List.of()));
		int bad = 0;
		for (Box s : b.siteBoxes()) {
			if (s.role() != Role.STAIR) continue;
			Box3 q = s.box();
			steps[0]++;
			for (int dy = 1; dy <= InstancePassV1.HEADROOM; dy++) {
				boolean shell = bare.inGrid(q.x(), q.y() + dy, q.z()) && bare.get(q.x(), q.y() + dy, q.z()) != Role.AIR;
				if (!shell && !covered(b.siteBoxes(), q.x(), q.y() + dy, q.z())) bad++;
			}
		}
		return bad;
	}

	@Test
	void everyStepHasClaimedHeadroomOnSlopes() {
		List<String> bad = java.util.Collections.synchronizedList(new ArrayList<>());
		int[] steps = {0};
		for (String terrain : List.of("plane:78:1:1:16", "plane:64:1:0:4", "plane:70:1:2:6")) {
			for (BuildingType t : Types.all()) {
				if (List.of("wall", "curtain").contains(t.id())) continue; // wall-walk stairs climb inside the wall band
				int lots = t.limits().testLots().size();
				IntStream.range(0, lots * 8).forEach(i -> {
					V1Case c0 = V1Case.draw(t, i % lots, i / lots, List.of("medieval", "plain"));
					V1Case c = new V1Case(c0.seed(), c0.type(), c0.style(), c0.lotW(), c0.lotD(), c0.atX(), c0.atZ(), c0.front(), terrain);
					GenRequest rq = c.request();
					try {
						for (PlacedBuilding b : new GeneratorV1().generate(rq).buildings()) {
							int[] s = {0};
							int n = unclaimed(b, s);
							synchronized (steps) { steps[0] += s[0]; }
							if (n > 0) { bad.add(n + " unclaimed cells :: " + c.replay()); }
						}
					} catch (IllegalArgumentException e) {
						if (!V1PropertyTest.isPolicyRejection(e, t)) bad.add(e + " :: " + c.replay());
					}
				});
			}
		}
		assertTrue(steps[0] > 50, "the test saw only " + steps[0] + " steps");
		assertEquals(List.of(), bad.stream().sorted().limit(10).toList(), bad.size() + " failures");
	}
}
