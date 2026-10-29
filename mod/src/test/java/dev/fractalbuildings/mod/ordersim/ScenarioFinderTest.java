package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.LakeSpot;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkKind;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkSpot;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.Window;
import dev.fractalbuildings.mod.worldgen.LakeVeto;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S2. The finder is a pure function of the plan, and it finds, on the order test's settlement (gate-temperate-city), a spot for
 * each case of SC1 to SC3 with the geometry the scenario needs. Liveness here is geometric: each spot is a place where the feature
 * would reach the pass's read-the-world branch (a lake grid meeting a dug-in wall or only the deep foundation, a 2 x 2 trunk base
 * straddling a street's edge row on a chunk line). That the branch then fires is counted by the sim's own liveness check (S3).
 */
class ScenarioFinderTest {
	static SimSites gate;
	static WorldPlanner planner;
	static ScenarioFinder finder;
	static long plannerMs, plannerCpuMs;

	@BeforeAll
	static void build() {
		long t0 = System.nanoTime(), c0 = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		finder = new ScenarioFinder(planner, gate.site(), gate.probes().exact());
		finder.densestWindow(6); // warms the tiles' pieces
		plannerMs = (System.nanoTime() - t0) / 1_000_000;
		plannerCpuMs = (java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - c0) / 1_000_000; // CPU, not wall: load-independent
		System.out.println("[ordersim-s2] planner + pieces for " + gate.name() + ": " + plannerMs + " ms, " + finder.tiles().size() + " tiles");
	}

	static ScenarioFinder fresh() {
		SimSites g = SimSites.load("gate-temperate-city");
		return new ScenarioFinder(g.planner(), g.site(), g.probes().exact());
	}

	@Test
	void deterministicOnTheSamePlannerAndOnAFreshOne() {
		ScenarioFinder other = fresh();
		for (ScenarioFinder f : List.of(finder, other)) {
			assertEquals(finder.densestWindow(6), f.densestWindow(6));
			assertEquals(finder.lakeAtDugInWall(), f.lakeAtDugInWall());
			assertEquals(finder.lakeUnderBox(), f.lakeUnderBox());
			assertEquals(finder.lakeFarBelow(), f.lakeFarBelow());
			assertEquals(finder.trunkSpots(), f.trunkSpots());
		}
		assertEquals(finder.densestWindow(6), finder.densestWindow(6));
	}

	@Test
	void lakeBoxCopyMatchesTheMod() {
		for (int[] o : new int[][] {{0, 0, 0}, {-17, 64, 301}, {5, -40, -5}}) {
			Box3 mod = LakeVeto.box(new BlockPos(o[0], o[1], o[2]));
			assertEquals(mod, ScenarioFinder.lakeBox(o[0], o[1], o[2]));
		}
	}

	@Test
	void sc1WindowHoldsBuildingsStreetsAndChunkLines() {
		Window w = finder.densestWindow(6);
		System.out.println("[ordersim-s2] SC1 window " + w);
		assertEquals(6, w.n());
		assertTrue(w.buildings() >= 4, "buildings " + w.buildings());
		assertTrue(w.groundPieces() >= 6, "ground pieces " + w.groundPieces());
		assertTrue(w.chunkLineStreetColumns() > 0, "street columns on a chunk line");
		assertTrue(w.buildingAirColumns() > 0, "building air columns (BuildingAirClear's domain)");
	}

	@Test
	void sc2DugInWall() {
		LakeSpot s = finder.lakeAtDugInWall();
		System.out.println("[ordersim-s2] SC2a " + s);
		assertNotNull(s, "a dug-in building on gate-temperate-city");
		BuildingPiece b = building(s);
		Box3 grid = ScenarioFinder.lakeBox(s.x(), s.y(), s.z());
		assertTrue(grid.intersects(b.box()), "the grid meets the building's box");
		assertTrue(!b.box().contains(s.x(), s.y(), s.z()), "the origin is outside the box: only the grid reaches it");
		assertTrue(b.box().y() < gate.probes().exact().floorY(b.box().x() + b.box().sx() / 2, b.box().z() + b.box().sz() / 2) - 1, "dug in");
		assertTrue(s.vetoes());
		assertEquals(s.vetoes(), planner.claimsNear(grid));
	}

	@Test
	void sc2UnderBoxIsOnlyTheDeepFoundation() {
		LakeSpot s = finder.lakeUnderBox();
		System.out.println("[ordersim-s2] SC2b " + s);
		assertNotNull(s);
		BuildingPiece b = building(s);
		Box3 grid = ScenarioFinder.lakeBox(s.x(), s.y(), s.z());
		assertFalse(grid.intersects(b.box()), "the plain box is not met");
		Box3 deep = new Box3(b.box().x(), b.box().y() - ScenarioFinder.FOUNDATION_BELOW, b.box().z(), b.box().sx(), b.box().sy() + ScenarioFinder.FOUNDATION_BELOW, b.box().sz());
		assertTrue(grid.intersects(deep), "the deep foundation is");
		assertTrue(planner.claimsNear(grid), "claimsNear sees the deep foundation");
		assertTrue(s.vetoes());
	}

	@Test
	void sc2FarBelowIsNotVetoed() {
		LakeSpot s = finder.lakeFarBelow();
		System.out.println("[ordersim-s2] SC2c " + s);
		assertNotNull(s);
		assertFalse(planner.claimsNear(ScenarioFinder.lakeBox(s.x(), s.y(), s.z())));
		assertFalse(s.vetoes());
		BuildingPiece b = building(s);
		assertTrue(s.y() < b.box().y() - ScenarioFinder.FOUNDATION_BELOW, "below the deep foundation");
	}

	@Test
	void sc3TrunkSpotsStraddleAStreetEdgeOnAChunkLine() {
		List<TrunkSpot> spots = finder.trunkSpots();
		for (TrunkSpot s : spots) System.out.println("[ordersim-s2] SC3 " + s);
		assertFalse(spots.isEmpty(), "at least one trunk spot");
				for (TrunkSpot s : spots) {
			assertTrue(s.streetChunkX() != s.outChunkX() || s.streetChunkZ() != s.outChunkZ(), s.kind() + ": the pair crosses a chunk line");
			assertEquals(1, Math.abs(s.streetX() - s.outX()) + Math.abs(s.streetZ() - s.outZ()), "adjacent columns");
			var cols = finder.columns(s.streetChunkX() - 1, s.streetChunkZ() - 1, 3);
			var street = cols.get(s.streetX(), s.streetZ());
			assertNotNull(street);
			assertTrue(street.street, "the street column carries a surface");
			assertEquals(s.surfaceY(), street.surfaceY);
			var out = cols.get(s.outX(), s.outZ());
			assertTrue(out == null || !(out.street || out.buildingAir || out.buildingSolid), "the other column is open ground");
			switch (s.kind()) {
				case BURIED -> assertTrue(s.surfaceY() > gate.probes().exact().floorY(s.streetX(), s.streetZ()) + 1, "raised street");
				case ON_SURFACE -> assertTrue(Math.abs(s.surfaceY() - gate.probes().exact().floorY(s.streetX(), s.streetZ())) <= 1, "at the natural level");
				case CUT_HEADROOM -> {
					assertTrue(s.cut() >= 2, "cut " + s.cut());
					assertTrue(s.airTopY() >= s.surfaceY(), "cleared headroom above the surface");
				}
			}
		}
	}

	@Test
	void sc3EveryKindIsFoundOrAbsenceIsNamed() {
		// The plan decides which kinds exist on this terrain. The case that matters for SPILLDIRT (3rd hit, restoreBase air) must exist.
		for (TrunkKind k : TrunkKind.values()) System.out.println("[ordersim-s2] SC3 " + k + ": " + (finder.trunkSpot(k) != null ? "found" : "NONE on gate-temperate-city"));
		assertNotNull(finder.trunkSpot(TrunkKind.ON_SURFACE), "ON_SURFACE");
		assertNotNull(finder.trunkSpot(TrunkKind.BURIED), "BURIED");
		assertNotNull(finder.trunkSpot(TrunkKind.CUT_HEADROOM), "CUT_HEADROOM");
	}

	@Test
	void plannerBuildTimeIsSeconds() {
		assertTrue(plannerCpuMs < 60_000, "planner + pieces took " + plannerCpuMs + " cpu ms, " + plannerMs + " wall ms");
	}

	private static BuildingPiece building(LakeSpot s) {
		return planner.buildings(s.building().ix(), s.building().iz()).stream().filter(b -> b.ref().equals(s.building())).findFirst().orElseThrow();
	}
}
