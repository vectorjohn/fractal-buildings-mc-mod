package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.EdgeStreetWaterTest;
import dev.fractalbuildings.core.settlement.RoadKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.40 THINDECK on John's playtest world (seed 1573336981619055086, metropolis s4_m2): the lattice street on the border x=4480 between tiles
 * 27,-6 (planned) and 28,-6 (not planned) ran over a lake 12 to 25 deep (z -960..-919). Tile 27 built its west two columns, nothing built
 * the east half or the centre-line piers: a thin slab in the air (playtest shot 21.01.30 at 4492,68,-950).
 */
class ThinDeckTest {
	@Test
	void noStreetIsLaidOverTheLakeAlongTheUnplannedTile() {
		WorldPlanner w = JoinWorld.pt1573();
		assertTrue(w.tile(28, -6).isEmpty(), "the tile east of the street is not planned");
		for (int[] t : new int[][] {{27, -6}, {28, -7}, {27, -7}}) {
			for (var s : w.plan(t[0], t[1]).plan().streets()) {
				assertTrue(!(s.points().get(0).x() == 4480 && s.points().get(s.points().size() - 1).x() == 4480 && s.points().get(0).z() >= -960 && s.points().get(s.points().size() - 1).z() <= -816 && s.points().get(0).z() < -900 && s.points().get(s.points().size() - 1).z() > -930 && s.kind().name().equals("STREET") && s.points().get(s.points().size() - 1).z() - s.points().get(0).z() > 60), "street " + s.id() + " in tile " + t[0] + "," + t[1]);
			}
			for (var g : w.ground(t[0], t[1])) {
				for (Box b : g.boxes()) {
					if (b.role() == Role.ROAD_CITY && b.box().x() >= 4476 && b.box().maxX() <= 4484 && b.box().z() >= -966 && b.box().maxZ() <= -935) throw new AssertionError("a street surface over the lake: " + b.box());
				}
			}
		}
	}


	/**
	 * The exact step removes it (round 2: {@code RoadNetwork.withWater}): the coarse lattice keeps the street, the network over the exact
	 * terrain drops it, since its centre line crosses the lake and no other street hangs on it. With the exact test switched off the
	 * street comes back (the coarse network has no water rule for edge streets).
	 */
	@Test
	void theExactNetworkDropsTheWetEdgeStreetTheCoarseLatticeKeeps() {
		WorldPlanner w = JoinWorld.pt1573();
		var src = w.source(w.siteOf(new Cell(4, -2)).orElseThrow());
		var seg = new DistrictGrid.Segment(DistrictGrid.AXIS_X, 28, -6, 4480, -960, -816);
		assertTrue(src.network().oneSided(seg));
		assertEquals(RoadKind.STREET, src.network().kind(seg));
		assertNull(src.network(w.probes().exact()).kind(seg));
		assertEquals(0, src.network(w.probes().exact()).inset(seg));
		EdgeStreetWaterTest.edgeStreetsAreDryOrInside(src, w.probes().exact());
	}
	@Test
	void theWholeSettlementHasNoEdgeCutStreetsAndNoUnsupportedDecks() {
		WorldPlanner w = JoinWorld.pt1573();
		var site = w.siteOf(new Cell(4, -2)).orElseThrow();
		var p = w.probes();
		var city = CityPlanner.plan(w.source(site), p.coarse(), p.exact(), p.biome(), GeneratorV1.GEN_VERSION);
		assertEquals(List.of(), SettlementChecks.streetEdgeCut(city.districts().values(), p.exact()));
		for (var dp : city.districts().values()) {
			var g = dev.fractalbuildings.core.settlement.DistrictScenes.ground(dp, p.exact(), site.tier());
			assertEquals(List.of(), g.settlement().stream().filter(v -> v.invariant().equals(SettlementChecks.STREET_DECK_UNSUPPORTED)).toList(), dp.district().id());
		}
	}
}
