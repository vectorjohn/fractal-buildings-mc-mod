package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.Grading;
import dev.fractalbuildings.core.terrain.GroundBox;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.ArrayList;
import java.util.List;

/**
 * Terrain-response numbers of a planned city (TR, 5.3): how much of the footprint the skeleton's urban mask kept, how many lots
 * stayed empty for steepness, and the earth moved by road and plaza grading. Pure function of the city and the exact probe.
 *
 * @param urban lattice columns the mask took (urban and open); {@code steep} those inside the footprint it refused for slope or peak
 * @param emptySteep lots with status {@code empty:steep}
 * @param cutBlocks air carved by roads and plazas; {@code fillBlocks} earth added
 * @param steepRoads street pieces whose grading still needs more than {@link Grading#MAX_CUT_FILL} on some stretch
 * @param bridges street pieces with a deck over water, {@code quays} quay street pieces, {@code quayLots} lots that build and front one
 */
public record TerrainStats(int urban, int steep, int lots, int emptySteep, long cutBlocks, long fillBlocks, int steepRoads, int bridges, int quays, int quayLots) {
	public int urbanPct() { return urban + steep == 0 ? 100 : (int) (100L * urban / (urban + steep)); }

	public static TerrainStats of(CityPlanner.City city, TerrainProbe exact) {
		int urban = 0, steep = 0;
		int[] mask = city.plan().coarse().mask();
		for (int m : mask) {
			if (m == CoarseRaster.URBAN || m == CoarseRaster.OPEN) urban++;
			else if (m == CoarseRaster.STEEP) steep++;
		}
		int lots = 0, emptySteep = 0, steepRoads = 0, bridges = 0, quays = 0, quayLots = 0;
		long cut = 0, fill = 0;
		for (DistrictPlan d : city.districts().values()) {
			List<Rect> quayRects = new ArrayList<>();
			for (Street s : d.streets()) if (s.kind() == RoadKind.QUAY) quayRects.addAll(SettlementChecks.footprint(s));
			for (LotBrief l : d.lots()) {
				if (l.builds()) lots++; if (l.builds() && frontsAny(l, quayRects)) quayLots++;
				else if (l.status().equals("empty:steep")) emptySteep++;
			}
			for (Street s : d.streets()) {
				List<LatticePath.Pt> pts = new ArrayList<>();
				for (Pt p : s.points()) pts.add(new LatticePath.Pt(p.x(), p.z()));
				Grading.RoadGrade g = Grading.road(pts, s.y(), s.width(), exact); // the plan's heights, so both tiles at a border build the same road
				if (!g.ok()) steepRoads++;
				boolean deck = false;
				for (boolean b : g.profile().bridge()) deck |= b;
				if (deck) bridges++;
				if (s.kind() == RoadKind.QUAY) quays++;
				for (GroundBox b : g.boxes()) {
					long v = (long) b.box().sx() * b.box().sy() * b.box().sz();
					if (b.kind() == GroundBox.Kind.CUT) cut += v;
					else if (b.kind() == GroundBox.Kind.FILL) fill += v;
				}
			}
		}
		return new TerrainStats(urban, steep, lots, emptySteep, cut, fill, steepRoads, bridges, quays, quayLots);
	}

	/** The column just outside the middle of the lot's front edge lies on one of the rectangles. */
	static boolean frontsAny(LotBrief l, List<Rect> rects) {
		int mx = l.x() + (l.w() - 1) / 2, mz = l.z() + (l.d() - 1) / 2;
		int x = switch (l.front()) { case WEST -> l.x() - 1; case EAST -> l.x() + l.w(); default -> mx; };
		int z = switch (l.front()) { case NORTH -> l.z() - 1; case SOUTH -> l.z() + l.d(); default -> mz; };
		for (Rect r : rects) if (r.contains(x, z)) return true;
		return false;
	}
}
