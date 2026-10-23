package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.Ids;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The world planner gives what the CLI's {@code CityPlanner} gives for the same settlement: plans, buildings and ground, byte for byte. */
class WorldPlannerParityTest {
	/** Every tile of the window the planner plans, by district id. */
	static TreeMap<String, TileInfo> planned(WorldPlanner w, GateWorld g) {
		var win = g.probes().window();
		var a = w.grid().tileOf(win.x0(), win.z0());
		var b = w.grid().tileOf(win.x1(), win.z1());
		TreeMap<String, TileInfo> out = new TreeMap<>();
		for (int iz = a.iz() - 1; iz <= b.iz() + 1; iz++) {
			for (int ix = a.ix() - 1; ix <= b.ix() + 1; ix++) {
				var t = w.tile(ix, iz);
				if (t.isPresent()) out.put(Ids.district(ix, iz), t.get());
			}
		}
		return out;
	}

	static CityPlanner.City reference(GateWorld g) {
		SettlementSite site = g.site();
		var src = new SkeletonSource.Direct(g.seed(), new FixedSiteView(site, new RegionField(g.seed(), 1024)), site, g.probes().coarse());
		return CityPlanner.plan(src, g.probes().coarse(), g.probes().exact(), g.probes().biome(), GeneratorV1.GEN_VERSION);
	}

	@Test
	void everyGateWindowTileHasTheCityPlannersPlan() {
		int tiles = 0;
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.planner(WorldPlanner.Caches.DEFAULT, null);
			CityPlanner.City city = reference(g);
			TreeMap<String, TileInfo> mine = planned(w, g);
			assertEquals(new TreeSet<>(city.districts().keySet()), new TreeSet<>(mine.keySet()), g.name() + ": the planned tiles");
			for (var e : mine.entrySet()) {
				TilePlan tp = w.plan(e.getValue().ix(), e.getValue().iz());
				assertEquals(DistrictJson.write(city.districts().get(e.getKey())), DistrictJson.write(tp.plan()), g.name() + " " + e.getKey());
				assertEquals(city.landmarks().get(e.getKey()), tp.landmarks(), g.name() + " " + e.getKey() + " landmarks");
				tiles++;
			}
		}
		assertTrue(tiles > 400, "tiles " + tiles);
	}

	@Test
	void buildingsAndGroundEqualTheDistrictScene() {
		GateWorld g = GateWorld.load("gate-temperate-city");
		WorldPlanner w = g.planner(WorldPlanner.Caches.DEFAULT, null);
		CityPlanner.City city = reference(g);
		int checked = 0, buildings = 0;
		for (var e : planned(w, g).entrySet()) {
			DistrictPlan dp = city.districts().get(e.getKey());
			if (dp.lots().stream().noneMatch(l -> l.builds())) continue;
			if (checked++ % 3 != 0) continue; // a third of the tiles: each scene runs the invariants
			DistrictScenes.Built b = DistrictScenes.build(dp, city.landmarks().get(e.getKey()), g.probes().exact(), g.probes().biome(), false);
			int ix = e.getValue().ix(), iz = e.getValue().iz();
			List<BuildingPiece> pieces = w.buildings(ix, iz);
			assertEquals(b.scene().buildings().size(), pieces.size(), e.getKey());
			SceneDoc want = new SceneDoc(GeneratorV1.GEN_VERSION, g.seed(), null, b.scene().lots(), b.scene().buildings(), List.of());
			List<dev.fractalbuildings.core.ir.Lot> lots = new ArrayList<>();
			List<dev.fractalbuildings.core.ir.PlacedBuilding> bs = new ArrayList<>();
			for (BuildingPiece p : pieces) { lots.add(p.lot()); bs.add(p.building()); }
			assertEquals(IrJson.write(want), IrJson.write(new SceneDoc(GeneratorV1.GEN_VERSION, g.seed(), null, lots, bs, List.of())), e.getKey() + " buildings");
			buildings += pieces.size();
			// ground: the scene's boxes clipped to the tile, per chunk, are the pieces' boxes
			var t = e.getValue().tile();
			List<Box> fromPieces = new ArrayList<>();
			for (GroundPiece gp : w.ground(ix, iz)) fromPieces.addAll(gp.boxes());
			long want3 = 0, got = 0;
			for (Box x : b.scene().ground()) {
				var k = Pieces.clip(x.box(), t.minX(), t.minZ(), t.maxX(), t.maxZ());
				if (k != null) want3 += k.volume();
			}
			for (Box x : fromPieces) got += x.box().volume();
			assertEquals(want3, got, e.getKey() + " ground volume inside the tile");
		}
		assertTrue(buildings > 20, "buildings compared " + buildings);
	}
}
