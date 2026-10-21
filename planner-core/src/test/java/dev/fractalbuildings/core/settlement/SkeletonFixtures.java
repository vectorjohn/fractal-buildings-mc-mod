package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.region.RegionField;
import java.util.ArrayList;
import java.util.List;

/** Synthetic sites and probes for the skeleton tests. */
final class SkeletonFixtures {
	private SkeletonFixtures() {}

	static final int CELL = 1024;

	static CoarseProbe probe(String spec) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(spec);
		return CoarseProbe.of(p.terrain(), p.biome(), 32);
	}

	/** A site centred on the site of cell (cx, cz); its cells are the {@code maxCells} nearest whose sites lie within the radius. */
	static SettlementSite site(RegionField f, int cx, int cz, Tier tier, int radius, int maxCells) {
		Pt centre = new Pt(f.siteX(cx, cz), f.siteZ(cx, cz));
		return site(f, cx, cz, centre, tier, radius, maxCells);
	}

	static SettlementSite site(RegionField f, int cx, int cz, Pt centre, Tier tier, int radius, int maxCells) {
		List<Cell> near = new ArrayList<>();
		for (int dz = -4; dz <= 4; dz++) {
			for (int dx = -4; dx <= 4; dx++) {
				long ex = f.siteX(cx + dx, cz + dz) - centre.x(), ez = f.siteZ(cx + dx, cz + dz) - centre.z();
				if (ex * ex + ez * ez <= (long) radius * radius || (dx == 0 && dz == 0)) near.add(new Cell(cx + dx, cz + dz));
			}
		}
		near.sort((a, b) -> {
			long da = d2(f, a, centre), db = d2(f, b, centre);
			return da != db ? Long.compare(da, db) : CellGeometry.compare(a, b);
		});
		List<Cell> cells = new ArrayList<>(near.subList(0, Math.min(maxCells, near.size())));
		if (!cells.contains(new Cell(cx, cz))) cells.add(new Cell(cx, cz));
		cells.sort(CellGeometry::compare);
		return new SettlementSite("s" + cx + "_" + cz, tier, centre, radius, new Cell(cx, cz), cells);
	}

	private static long d2(RegionField f, Cell c, Pt p) {
		long dx = f.siteX(c.cx(), c.cz()) - p.x(), dz = f.siteZ(c.cx(), c.cz()) - p.z();
		return dx * dx + dz * dz;
	}
}
