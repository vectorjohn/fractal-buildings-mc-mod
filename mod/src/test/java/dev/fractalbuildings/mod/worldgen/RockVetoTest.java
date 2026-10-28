package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Ids;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The pure boulder veto (ROCKVETO) on a one-site planner of the gate-temperate-city dump: a boulder over a building is vetoed, one far below is not, the ROCKS mutant turns it off. */
class RockVetoTest {
	static WorldPlanner planner;
	static BlockPos onBuilding;

	@BeforeAll
	static void load() throws Exception {
		// gate-sites.tsv: gate-temperate-city, seed 987654321, site 1280,-9600, city, radius 477, one cell
		long seed = 987654321L;
		int x = 1280, z = -9600, radius = 477;
		RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/gate-temperate-city.json.gz"));
		int s = RegionProbe.STRIDE, x0 = Math.floorDiv(r.x0() + s - 1, s) * s, z0 = Math.floorDiv(r.z0() + s - 1, s) * s;
		int x1 = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), s) * s, z1 = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), s) * s;
		RegionMap.Window w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		WorldProbes probes = new WorldProbes(RegionProbe.of(r, r), CoarseProbe.of(r, r, 32), r, r, w);
		Cell claimant = CellGeometry.owner(new RegionField(seed, 1024), x, z);
		SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), Tier.CITY, new Pt(x, z), radius, claimant, List.of(claimant));
		planner = WorldPlanner.forSite(seed, WorldConfig.defaults(), probes, null, WorldPlanner.Caches.DEFAULT, site);
		DistrictGrid.TileId id = planner.grid().tileOf(x, z);
		for (int dz = -3; dz <= 3 && onBuilding == null; dz++) for (int dx = -3; dx <= 3 && onBuilding == null; dx++) {
			if (planner.tile(id.ix() + dx, id.iz() + dz).isEmpty()) continue;
			for (BuildingPiece b : planner.buildings(id.ix() + dx, id.iz() + dz)) {
				Box3 bx = b.box();
				onBuilding = new BlockPos((bx.x() + bx.maxX()) / 2, (bx.y() + bx.maxY()) / 2, (bx.z() + bx.maxZ()) / 2);
				break;
			}
		}
		assertNotNull(onBuilding, "no building found near the site");
	}

	@AfterEach
	void reset() { Mutations.mode = null; }

	@Test
	void rockOnABuildingIsVetoedAndCounted() {
		long before = RockVeto.VETOED.get();
		assertTrue(RockVeto.vetoes(planner, onBuilding));
		assertEquals(before + 1, RockVeto.VETOED.get());
	}

	@Test
	void rockFarBelowIsNotVetoed() {
		long before = RockVeto.VETOED.get();
		assertFalse(RockVeto.vetoes(planner, onBuilding.below(100)));
		assertEquals(before, RockVeto.VETOED.get());
	}

	@Test
	void rockFarAwayIsNotVetoed() {
		assertFalse(RockVeto.vetoes(planner, new BlockPos(onBuilding.getX() + 20_000, onBuilding.getY(), onBuilding.getZ())));
	}

	@Test
	void rocksMutantTurnsTheVetoOff() {
		Mutations.mode = Mutations.ROCKS;
		assertFalse(RockVeto.vetoes(planner, onBuilding));
	}
}
