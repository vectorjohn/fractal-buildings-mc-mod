package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.FieldSiteView;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.Tier;
import java.io.IOException;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The region layer of the planner on a recorded world map (12345, 64-block lattice): settlements come from the field's join rule, not from a given site. */
class WorldPlannerRegionTest {
	static final long SEED = 12345;
	static final WorldProbes WORLD = world();

	static WorldProbes world() {
		try {
			return GateWorld.probes(RecordedProbe.read(GateWorld.FIXTURES.resolve("world-12345-s64.json.gz")));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	static WorldPlanner planner(WorldConfig cfg, WorldPlanner.Caches caps) {
		return new WorldPlanner(SEED, cfg, WORLD, null, caps);
	}

	@Test
	void findsTheRegionPlannersCityAndPlansItLikeTheCli() {
		WorldPlanner w = planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		// the city at cell -3,-9 (CityCheckTest): its centre is the nearest settlement of city size from inside the cell
		RegionField field = new RegionField(SEED, 1024);
		Optional<SettlementInfo> n = w.nearest(field.siteX(-3, -9), field.siteZ(-3, -9), Tier.CITY, 1);
		assertTrue(n.isPresent());
		SettlementSite site = n.get().site();
		assertEquals("sm3_m9", site.id());

		RegionPlanner rp = new RegionPlanner(field, dev.fractalbuildings.core.region.RegionProbe.of(WORLD.exact(), WORLD.biome()));
		FieldRegionView fv = new FieldRegionView(rp, WORLD.window());
		// the planner draws roads by default, so the CLI side plans with the same road graph (required exits and approaches)
		dev.fractalbuildings.core.road.RoadNet net = new dev.fractalbuildings.core.road.RoadNet(field, fv, WORLD.window(), WORLD.region(), WORLD.roadExact());
		FieldSiteView view = new FieldSiteView(fv, field, net);
		CityPlanner.City city = CityPlanner.plan(new SkeletonSource.Direct(SEED, view, site, WORLD.coarse()), WORLD.coarse(), WORLD.exact(), WORLD.biome(), GeneratorV1.GEN_VERSION);
		assertFalse(city.districts().isEmpty());
		TreeMap<String, Boolean> mine = new TreeMap<>();
		for (var d : city.districts().values()) {
			var i = d.district();
			Optional<TileInfo> t = w.tile(i.ix(), i.iz());
			assertTrue(t.isPresent(), i.id());
			assertEquals(site.id(), t.get().settlementId(), i.id());
			assertEquals(DistrictJson.write(d), DistrictJson.write(w.plan(i.ix(), i.iz()).plan()), i.id());
			mine.put(i.id(), true);
		}
		// and no neighbour of the city's tiles claims to be one of its own that the CLI does not plan
		for (var d : city.districts().values()) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					int ix = d.district().ix() + dx, iz = d.district().iz() + dz;
					Optional<TileInfo> t = w.tile(ix, iz);
					if (t.isPresent() && t.get().settlementId().equals(site.id())) assertTrue(mine.containsKey(dev.fractalbuildings.core.settlement.Ids.district(ix, iz)), "extra tile " + ix + "," + iz);
				}
			}
		}
	}

	@Test
	void theRoadSwitchTurnsTheGraphAndTheApproachesOff() {
		WorldConfig d = WorldConfig.defaults();
		WorldConfig off = new WorldConfig(d.cellSize(), d.frequency(), d.maxRadius(), d.styles(), d.genVersion(), false);
		WorldPlanner on = planner(d, WorldPlanner.Caches.DEFAULT), none = planner(off, WorldPlanner.Caches.DEFAULT);
		assertTrue(on.roadsEnabled());
		assertFalse(none.roadsEnabled());
		int roadChunks = 0;
		for (var e : on.roads().allEdges()) {
			var r = on.roads().route(e);
			if (!r.ok || r.touching) continue;
			var first = r.byChunk.values().iterator().next().get(0);
			int cx = Math.floorDiv(first.x(), 16), cz = Math.floorDiv(first.z(), 16);
			assertFalse(on.roadColumns(cx, cz).isEmpty());
			assertEquals(java.util.List.of(), none.roadColumns(cx, cz));
			for (var c : on.roads().query(cx, cz)) assertTrue(on.roadColumns(cx, cz).contains(c), "the graph's column " + c);
			if (++roadChunks >= 10) break;
		}
		assertTrue(roadChunks >= 10);
		assertNull(none.districtColumns(0, 0));
	}

	@Test
	void startChunksAreTheTileCentresAndNeedNoProbes() {
		WorldPlanner w = planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		DistrictGrid g = w.grid();
		int starts = 0;
		for (int cz = -40; cz < 40; cz++) {
			for (int cx = -40; cx < 40; cx++) {
				boolean want = false;
				var t = g.tileAt(cx * 16, cz * 16);
				want = t.originChunkX() == cx && t.originChunkZ() == cz;
				assertEquals(want, w.isStartChunk(cx, cz));
				if (want) starts++;
			}
		}
		assertTrue(starts >= 36 && starts <= 100, "starts " + starts); // about (80 / 10)^2 tiles
		// every tile has exactly one start chunk, inside the tile
		for (int iz = -4; iz < 4; iz++) for (int ix = -4; ix < 4; ix++) {
			var t = g.tile(ix, iz);
			assertTrue(w.isStartChunk(t.originChunkX(), t.originChunkZ()));
			assertTrue(t.contains(t.originChunkX() * 16, t.originChunkZ() * 16));
		}
	}

	@Test
	void zeroFrequencyGivesNoSettlements() {
		WorldConfig none = new WorldConfig(1024, 0.0, dev.fractalbuildings.core.region.Tiers.DEFAULT_MAX_RADIUS, java.util.Set.of(), GeneratorV1.GEN_VERSION);
		WorldPlanner w = planner(none, WorldPlanner.Caches.DEFAULT);
		assertTrue(w.nearest(-3000, -9000, Tier.HAMLET, 3).isEmpty());
		WorldPlanner def = planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		assertTrue(def.nearest(-3000, -9000, Tier.HAMLET, 3).isPresent());
	}

	@Test
	void warmAndTinyCachesChangeNoAnswer() {
		WorldPlanner a = planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		WorldPlanner b = planner(WorldConfig.defaults(), WorldPlanner.Caches.all(1));
		RegionField field = new RegionField(SEED, 1024);
		int x = field.siteX(-3, -9), z = field.siteZ(-3, -9);
		a.warm(x, z, 600);
		var city = a.nearest(x, z, Tier.CITY, 1).orElseThrow();
		var other = b.nearest(x, z, Tier.CITY, 1).orElseThrow();
		assertEquals(city, other);
		var ca = a.tile(-3 * 1024 / 160 - 1, -9 * 1024 / 160);
		assertEquals(ca, b.tile(-3 * 1024 / 160 - 1, -9 * 1024 / 160));
		assertTrue(new Cell(-3, -9).equals(city.site().claimant()) || city.site().cells().contains(new Cell(-3, -9)));
	}
}
