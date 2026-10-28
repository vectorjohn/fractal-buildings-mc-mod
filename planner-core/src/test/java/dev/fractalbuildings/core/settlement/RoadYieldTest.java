package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The pointwise parallel-main query (METROFILL R2). The yields built on it ship off; see RoadNetwork.LATTICE_YIELD. */
class RoadYieldTest {
	private static List<int[]> line(int x0, int z0, int x1, int z1) {
		return StreetCols.columns(List.of(new Pt(x0, z0), new Pt(x1, z1)));
	}

	private static RoadNetwork.Mains mains() {
		RoadNetwork.Mains m = new RoadNetwork.Mains(0, 0, 400, 400);
		m.add(line(112, 40, 112, 300)); // a main road along z at x = 112
		return m;
	}

	@Test
	void gridLineBesideAMainIsParallel() {
		RoadNetwork.Mains m = mains();
		assertTrue(m.parallel(DistrictGrid.AXIS_X, 124, 80, 88, 24), "12 beside the main");
		assertTrue(m.parallel(DistrictGrid.AXIS_X, 112, 80, 88, 24), "on the main");
	}

	@Test
	void gridLineFarFromTheMainIsKept() {
		assertFalse(mains().parallel(DistrictGrid.AXIS_X, 176, 80, 88, 24));
	}

	@Test
	void aMainCrossingAtRightAnglesDoesNotCount() {
		RoadNetwork.Mains m = new RoadNetwork.Mains(0, 0, 400, 400);
		m.add(line(40, 120, 300, 120)); // runs along x, crosses a line of constant x
		assertFalse(m.parallel(DistrictGrid.AXIS_X, 124, 80, 200, 24));
		assertTrue(m.parallel(DistrictGrid.AXIS_Z, 130, 80, 200, 24), "a line of constant z beside it is parallel");
	}

	@Test
	void halfTheNodesDecide() {
		RoadNetwork.Mains m = new RoadNetwork.Mains(0, 0, 400, 400);
		m.add(line(112, 40, 112, 100));
		assertTrue(m.parallel(DistrictGrid.AXIS_X, 124, 40, 120, 24), "main covers 40..100 of 40..120");
		assertFalse(m.parallel(DistrictGrid.AXIS_X, 124, 40, 240, 24), "main covers under half");
	}

	@Test
	void zeroReachTurnsTheRuleOff() {
		assertFalse(new RoadNetwork(mains()).parallelMain(DistrictGrid.AXIS_X, 124, 80, 88, 0));
	}
}
