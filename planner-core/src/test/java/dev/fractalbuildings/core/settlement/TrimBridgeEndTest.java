package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 6.16 DEADMAIN: an exit road whose last bridge runs to its final point stops at the land before the bridge. */
class TrimBridgeEndTest {
	private static TierProgram.Route route(List<int[]> bridges, int... xz) {
		List<Pt> pts = new java.util.ArrayList<>();
		for (int i = 0; i < xz.length; i += 2) pts.add(new Pt(xz[i], xz[i + 1]));
		return new TierProgram.Route(pts.get(pts.size() - 1), pts, bridges);
	}

	@Test
	void aBridgeToTheLastPointIsCutAtItsFirstLandPoint() {
		var r = route(List.of(new int[] {0, 1}, new int[] {2, 4}), 0, 0, 32, 0, 64, 0, 96, 0, 128, 0);
		var t = Skeletons.trimBridgeEnd(r);
		assertEquals(List.of(new Pt(0, 0), new Pt(32, 0), new Pt(64, 0)), t.points());
		assertEquals(new Pt(64, 0), t.target());
		assertEquals(1, t.bridges().size(), "only the earlier bridge stays");
		assertEquals(0, t.bridges().get(0)[0]);
	}

	@Test
	void aBridgeStartingAtTheFirstPointIsKept() {
		var r = route(List.of(new int[] {0, 3}), 0, 0, 32, 0, 64, 0, 96, 0);
		assertSame(r, Skeletons.trimBridgeEnd(r), "trimming would leave no road and drop the exit");
	}

	@Test
	void aRoadWhoseBridgesEndBeforeItsLastPointIsKept() {
		var r = route(List.of(new int[] {1, 2}), 0, 0, 32, 0, 64, 0, 96, 0);
		assertSame(r, Skeletons.trimBridgeEnd(r));
	}
}
