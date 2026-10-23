package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.settlement.Tier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The port tag (ADR 0004 N6): the region field's water search tags settled towns and larger near large water; no village bias, no tier nudge. */
class PortTagTest {
	@Test
	void taggedSettlementsAreSettledBiasedTiersNearWaterOnBothWorlds() throws IOException {
		for (long seed : new long[] {12345, 987654321}) {
			RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/world-" + seed + "-s64.json.gz"));
			RegionPlanner p = new RegionPlanner(new RegionField(seed, 1024), RegionProbe.of(r, r));
			int x1 = r.x0() + (r.nx() - 1) * r.step(), z1 = r.z0() + (r.nz() - 1) * r.step();
			List<CellDecision> cells = p.decideWindow(r.x0(), r.z0(), x1, z1);
			int tagged = 0, settledBiased = 0;
			for (CellDecision d : cells) {
				if (d.settled() && d.tier().atLeast(Tier.TOWN)) settledBiased++;
				if (!d.port()) continue;
				tagged++;
				assertTrue(d.settled(), "only settled cells are tagged");
				assertTrue(d.tier().atLeast(Tier.TOWN), "no village or hamlet is tagged: " + d.tier());
				assertTrue(d.waterDist() >= 0 && d.waterDist() <= Tiers.PORT_REACH, "water at " + d.waterDist());
				assertEquals(d, p.decide(d.cx(), d.cz()), "the decision (tag included) is a pure function");
			}
			assertTrue(tagged >= 10, seed + ": tagged " + tagged);
			assertTrue(tagged < settledBiased, seed + ": not every town and city is a port: " + tagged + " of " + settledBiased);
			System.out.println("port tag " + seed + ": " + tagged + " of " + settledBiased + " settled towns, cities and metropolises");
			// the tag changes nothing else: with the water bias off no cell is tagged, and everything else the decision holds is as before
			p.waterBias = false;
			for (CellDecision d : cells) assertFalse(p.decide(d.cx(), d.cz()).port(), "bias off, no tags");
		}
	}

	/** A coast at x = 3000 (ocean to the east): towns near the shore are ports, a town far inland is not, a lone pond does not make a port. */
	@Test
	void largeWaterNearTheCentreTagsAndAPondDoesNot() {
		RegionProbe coast = new RegionProbe() {
			@Override public int floorY(int x, int z) { return x >= 3000 ? 40 : 70; }
			@Override public int waterDepth(int x, int z) { return x >= 3000 ? 30 : 0; }
			@Override public String biome(int x, int z) { return x >= 3000 ? "minecraft:ocean" : "minecraft:plains"; }
		};
		RegionProbe pond = new RegionProbe() { // one 192-block pond sample per 2000 blocks at most, never two neighbours
			@Override public int floorY(int x, int z) { return 70; }
			@Override public int waterDepth(int x, int z) { return Math.floorMod(x, 2112) == 0 && Math.floorMod(z, 2112) == 0 ? 5 : 0; }
			@Override public String biome(int x, int z) { return "minecraft:plains"; }
		};
		int near = 0, far = 0, pondTags = 0;
		for (long seed = 1; seed <= 12; seed++) {
			RegionPlanner p = new RegionPlanner(new RegionField(seed, 1024), coast);
			for (int cz = -2; cz <= 2; cz++) {
				for (int cx = -4; cx <= 3; cx++) {
					CellDecision d = p.decide(cx, cz);
					if (!d.port()) continue;
					assertTrue(d.centreX() + Tiers.PORT_REACH + 64 >= 3000 - 192, "a port is near the coast: " + d.centreX());
					near++;
				}
			}
			RegionPlanner q = new RegionPlanner(new RegionField(seed, 1024), pond);
			for (int cz = -3; cz <= 3; cz++) for (int cx = -3; cx <= 3; cx++) if (q.decide(cx, cz).port()) pondTags++;
			for (int cx = -4; cx <= -2; cx++) assertFalse(p.decide(cx, 0).port(), "inland cell " + cx);
			far++;
		}
		assertTrue(near >= 3, "ports on the coast: " + near);
		assertEquals(0, pondTags, "a single pond sample is not large water");
		assertTrue(far > 0);
	}
}
