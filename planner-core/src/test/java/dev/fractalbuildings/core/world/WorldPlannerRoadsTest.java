package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.RoadPort;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The road layer of the planner meets the settlements (ADR 0004 N5, plan C1): the approach from each required exit to its port is drawn
 * by the road layer, is one connected strip, and joins the graph's road at the port at the same height.
 */
class WorldPlannerRoadsTest {
	/** Cells with links in the recorded world 12345 (see LinkExitsTest). */
	static final int[][] CELLS = {{3, 7}, {-11, -3}, {10, 1}, {2, 1}, {4, 0}};

	private static boolean connected(List<Col> cols) {
		TreeSet<Long> all = new TreeSet<>();
		for (Col c : cols) all.add(((long) c.z() << 32) ^ (c.x() & 0xffffffffL));
		if (all.isEmpty()) return false;
		TreeSet<Long> seen = new TreeSet<>();
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[] {cols.get(0).x(), cols.get(0).z()});
		seen.add(((long) cols.get(0).z() << 32) ^ (cols.get(0).x() & 0xffffffffL));
		while (!q.isEmpty()) {
			int[] p = q.poll();
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
				long k = ((long) (p[1] + dz) << 32) ^ ((p[0] + dx) & 0xffffffffL);
				if (all.contains(k) && seen.add(k)) q.add(new int[] {p[0] + dx, p[1] + dz});
			}
		}
		return seen.size() == all.size();
	}

	/** One drawn approach is one connected strip that reaches its port and, at a graph road's port, meets that road within a step; true for a graph road, false for a meeting point. */
	static boolean checkJoinsItsRoad(WorldPlanner w, WorldPlanner.Approach a) {
		List<Col> cols = a.columns();
		assertFalse(cols.isEmpty(), a.road().id());
		assertTrue(connected(cols), "approach " + a.road().id() + " is one connected strip");
		RoadPort p = a.port();
		assertTrue(p != null, "approach " + a.road().id() + " ends at a port");
		// the strip reaches the port
		Col near = null;
		double best = 1e18;
		for (Col col : cols) {
			double d = Math.hypot(col.x() + 0.5 - p.x(), col.z() + 0.5 - p.z());
			if (d < best) { best = d; near = col; }
		}
		assertTrue(best <= p.width(), "approach " + a.road().id() + " ends " + best + " from its port");
		if (p.meeting()) return false;
		// and the graph's road starts there: a column of it within the width, at the same height (+-1)
		int cx = Math.floorDiv(p.x(), 16), cz = Math.floorDiv(p.z(), 16);
		Col road = null;
		double rbest = 1e18;
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (Col col : w.roads().query(cx + dx, cz + dz)) {
			double d = Math.hypot(col.x() + 0.5 - p.x(), col.z() + 0.5 - p.z());
			if (d < rbest) { rbest = d; road = col; }
		}
		assertTrue(road != null && rbest <= p.width(), "the road starts " + rbest + " from the port of " + a.road().id());
		assertTrue(Math.abs(road.y() - near.y()) <= 1 + a.portExcess(), "step of " + (road.y() - near.y()) + " at the port of " + a.road().id());
		return true;
	}

	@Test
	void everyApproachIsOneStripThatJoinsItsRoadAtTheSameHeight() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		RoadNet net = w.roads();
		int approaches = 0, joined = 0, meeting = 0;
		for (int[] c : CELLS) {
			RoadNet.Settlement s = net.settlement(new CellKey(c[0], c[1])).orElseThrow();
			SettlementSite site = w.siteOf(new Cell(c[0], c[1])).orElseThrow();
			for (WorldPlanner.Approach a : w.drawnApproaches(site, s)) {
				approaches++;
				if (checkJoinsItsRoad(w, a)) joined++;
				else meeting++;
			}
		}
		System.out.println("approaches: " + approaches + ", joined to a road at the port: " + joined + ", meeting points: " + meeting);
		assertTrue(approaches >= 5 && joined >= 3, "approaches " + approaches + " joined " + joined);
	}

	@Test
	void theColumnsAroundAChunkAreTheChunksOwnAndItsNeighbours() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		RoadNet net = w.roads();
		int checked = 0;
		for (var e : net.allEdges()) {
			var r = net.route(e);
			if (!r.ok || r.touching) continue;
			Col c = r.byChunk.values().iterator().next().get(0);
			int cx = Math.floorDiv(c.x(), 16), cz = Math.floorDiv(c.z(), 16);
			List<List<Col>> around = w.roadColumnsAround(cx, cz);
			assertEquals(9, around.size());
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
				List<Col> mine = w.roadColumns(cx + dx, cz + dz);
				List<Col> got = around.get((dz + 1) * 3 + dx + 1);
				for (Col k : net.query(cx + dx, cz + dz, e2 -> w.linkState(e2) != WorldPlanner.LinkState.DROPPED)) assertTrue(got.contains(k)); // 6.22: a dropped link is not drawn
				for (Col k : got) assertEquals(Math.floorDiv(k.x(), 16), cx + dx);
				if (dx == 0 && dz == 0) assertEquals(mine, got);
			}
			if (++checked >= 12) break;
		}
		assertEquals(12, checked);
	}

	/** No road column lands in a district's footprint (D5): every ground box, building box and lot column of the tiles an approach crosses is masked, and the surface plan draws none of them. */
	@Test
	void districtFootprintsInTheChunksOfAnApproachAreMaskedAndNeverDrawn() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		RoadNet net = w.roads();
		int chunks = 0, maskedColumns = 0, roadColumnsInMask = 0;
		for (int[] c : CELLS) {
			RoadNet.Settlement s = net.settlement(new CellKey(c[0], c[1])).orElseThrow();
			SettlementSite site = w.siteOf(new Cell(c[0], c[1])).orElseThrow();
			java.util.TreeSet<Long> seen = new java.util.TreeSet<>();
			for (WorldPlanner.Approach a : w.drawnApproaches(site, s)) {
				for (Col col : a.columns()) {
					int cx = Math.floorDiv(col.x(), 16), cz = Math.floorDiv(col.z(), 16);
					if (!seen.add(((long) cz << 32) ^ (cx & 0xffffffffL))) continue;
					boolean[] mask = w.districtColumns(cx, cz);
					var id = w.grid().tileOf(cx * 16 + 8, cz * 16 + 8);
					boolean planned = w.tile(id.ix(), id.iz()).isPresent();
					if (!planned) continue;
					chunks++;
					// everything the tile's pieces own in this chunk is masked
					int x0 = cx * 16, z0 = cz * 16;
					java.util.function.BiConsumer<int[], String> must = (r, what) -> {
						for (int z = Math.max(r[1], z0); z < Math.min(r[3], z0 + 16); z++) for (int x = Math.max(r[0], x0); x < Math.min(r[2], x0 + 16); x++) {
							assertTrue(mask != null && mask[(z - z0) * 16 + (x - x0)], what + " column " + x + "," + z + " is not in the district mask of chunk " + cx + "," + cz);
						}
					};
					for (var g : w.ground(id.ix(), id.iz())) if (g.chunkX() == cx && g.chunkZ() == cz) for (var b : g.boxes()) must.accept(new int[] {b.box().x(), b.box().z(), b.box().maxX(), b.box().maxZ()}, "ground");
					for (var b : w.buildings(id.ix(), id.iz())) must.accept(new int[] {b.box().x(), b.box().z(), b.box().maxX(), b.box().maxZ()}, "building");
					for (var l : w.plan(id.ix(), id.iz()).plan().lots()) must.accept(new int[] {l.x(), l.z(), l.x() + l.w(), l.z() + l.d()}, "lot");
					if (mask == null) continue;
					// and the surface plan draws nothing there, on flat ground and on a slope
					for (int slope = 0; slope < 2; slope++) {
						final int sl = slope;
						var plan = dev.fractalbuildings.core.road.RoadSurface.plan(cx, cz, w.roadColumnsAround(cx, cz), mask, new dev.fractalbuildings.core.road.RoadSurface.Ground() {
							@Override public int topY(int x, int z) { return 70 + sl * ((x + z) % 7); }
							@Override public boolean wet(int x, int z) { return false; }
						});
						for (int i = 0; i < 256; i++) {
							if (!mask[i]) continue;
							maskedColumns++;
							if (plan.kind[i] != dev.fractalbuildings.core.road.RoadSurface.NONE) roadColumnsInMask++;
						}
					}
				}
			}
		}
		System.out.println("district mask: " + chunks + " planned chunks under approaches, " + maskedColumns + " masked columns, " + roadColumnsInMask + " drawn");
		assertTrue(chunks >= 4, "chunks " + chunks);
		assertTrue(maskedColumns > 0, "the test must see masked columns");
		assertEquals(0, roadColumnsInMask);
	}
}
