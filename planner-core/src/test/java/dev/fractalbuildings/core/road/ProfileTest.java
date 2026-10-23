package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dry check and deck of one road (ADR 0004 WR1 to WR3, HT) over synthetic terrain: rivers of chosen width across a straight road. */
class ProfileTest {
	/** A road along +x at z = 0 from x = 0, stations every 8 blocks, {@code n} stations. */
	static double[][] line(int n) {
		double[][] st = new double[n][];
		for (int k = 0; k < n; k++) st[k] = new double[] {k * 8.0, 0};
		return st;
	}

	/** Flat land at y 70 with water over the given x ranges [from, to) (depth 3, floor 67). */
	static TerrainProbe rivers(int... ranges) {
		return new TerrainProbe() {
			boolean wet(int x) {
				for (int i = 0; i < ranges.length; i += 2) if (x >= ranges[i] && x < ranges[i + 1]) return true;
				return false;
			}
			@Override public int floorY(int x, int z) { return wet(x) ? 67 : 70; }
			@Override public int waterDepth(int x, int z) { return wet(x) ? 3 : 0; }
		};
	}

	@Test
	void dryRoadHasNoCrossingAndAFlatDeck() {
		Profile.Result r = Profile.build(line(40), rivers());
		assertTrue(r.ok());
		assertTrue(r.crossings().isEmpty());
		for (int y : r.deck()) assertEquals(70, y);
		for (boolean s : r.span()) assertFalse(s);
	}

	@Test
	void aStreamIsOneFlatSpanMeasuredExactly() {
		// water over x in [100, 112): 12 blocks wide
		Profile.Result r = Profile.build(line(40), rivers(100, 112));
		assertTrue(r.ok(), r.failure());
		assertEquals(1, r.crossings().size());
		Profile.Crossing c = r.crossings().get(0);
		assertEquals(12, c.length());
		assertTrue(c.length() <= Profile.MAX_CROSSING);
		assertTrue(r.wet()[c.first()] && r.wet()[c.last()]);
		assertFalse(r.wet()[c.from()] || r.wet()[c.to()]);
		// flat deck over the span at the banks' height (the water surface is 70, so the mean there is 70)
		for (int k = c.first(); k <= c.last(); k++) assertEquals(c.deck(), r.deck()[k]);
		for (int k = c.from(); k <= c.to(); k++) assertTrue(r.span()[k]);
	}

	@Test
	void theLongestAllowedRunPassesAndOneBlockMoreFails() {
		assertTrue(Profile.build(line(40), rivers(100, 120)).ok()); // 20 wide
		Profile.Result r = Profile.build(line(40), rivers(100, 121)); // 21 wide
		assertFalse(r.ok());
		assertTrue(r.failure().startsWith("wet run"), r.failure());
		assertTrue(r.failStation() >= 10 && r.failStation() <= 16, "station to steer round: " + r.failStation());
	}

	@Test
	void twoStreamsCloserThanThirtyTwoCountAsOneRun() {
		// two 8-wide streams with 16 dry blocks between: one run of 32 > 20
		assertFalse(Profile.build(line(40), rivers(100, 108, 124, 132)).ok());
		// 40 dry blocks between: two separate crossings
		Profile.Result r = Profile.build(line(40), rivers(100, 108, 148, 156));
		assertTrue(r.ok(), r.failure());
		assertEquals(2, r.crossings().size());
	}

	@Test
	void wetGroundAtEitherEndFailsTheRoad() {
		Profile.Result a = Profile.build(line(40), rivers(-8, 12));
		assertFalse(a.ok());
		assertEquals("wet start", a.failure());
		Profile.Result b = Profile.build(line(40), rivers(300, 400));
		assertFalse(b.ok());
		assertEquals("wet end", b.failure());
	}

	@Test
	void deckFollowsTheGroundWithinTheGradeLimit() {
		// a steady rise of 1 block in 2: far steeper than the limit of 2 blocks per 8
		TerrainProbe ramp = (x, z) -> 60 + x / 2;
		Profile.Result r = Profile.build(line(40), ramp);
		assertTrue(r.ok());
		for (int k = 1; k < r.deck().length; k++) assertTrue(Math.abs(r.deck()[k] - r.deck()[k - 1]) <= Profile.MAX_STEP, "step at " + k);
		// and a bump: the deck is smoothed below the peak
		TerrainProbe bump = (x, z) -> x >= 150 && x < 160 ? 90 : 70;
		Profile.Result b = Profile.build(line(40), bump);
		int peak = 0;
		for (int y : b.deck()) peak = Math.max(peak, y);
		assertTrue(peak < 80, "peak " + peak);
	}

	@Test
	void readsStayNearOnePerStation() {
		TerrainProbe counted = rivers(100, 112);
		long[] n = {0};
		TerrainProbe p = new TerrainProbe() {
			@Override public int floorY(int x, int z) { n[0]++; return counted.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { n[0]++; return counted.waterDepth(x, z); }
		};
		Profile.Result r = Profile.build(line(40), p);
		assertEquals(n[0], r.reads());
		// two reads per station (water, floor) plus at most 2 x 7 along the banks of each crossing
		assertTrue(n[0] <= 2 * 40 + 14, "reads " + n[0]);
	}

	@Test
	void theCrossingDeckIsTheHigherBank() {
		// west bank at 70, east bank at 76; water between
		TerrainProbe t = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return x < 100 ? 70 : x < 112 ? 67 : 76; }
			@Override public int waterDepth(int x, int z) { return x >= 100 && x < 112 ? 9 : 0; }
		};
		Profile.Result r = Profile.build(line(40), t);
		assertTrue(r.ok(), r.failure());
		Profile.Crossing c = r.crossings().get(0);
		List<Integer> banks = new ArrayList<>(List.of(r.deck()[c.from()], r.deck()[c.to()]));
		assertTrue(c.deck() >= Math.max(banks.get(0), banks.get(1)) - Profile.MAX_STEP * 2, "flat " + c.deck() + " banks " + banks);
		for (int k = c.first(); k <= c.last(); k++) assertEquals(c.deck(), r.deck()[k]);
	}
}
