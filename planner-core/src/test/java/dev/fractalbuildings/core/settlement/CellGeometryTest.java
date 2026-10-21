package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import java.util.List;
import org.junit.jupiter.api.Test;

class CellGeometryTest {
	private static boolean inside(List<Pt> poly, double x, double z) {
		boolean in = false;
		for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
			Pt a = poly.get(i), b = poly.get(j);
			if ((a.z() > z) != (b.z() > z) && x < (double) (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) in = !in;
		}
		return in;
	}

	@Test
	void polygonMatchesOwnership() {
		for (long seed : new long[] {1, 2, 3}) {
			RegionField f = new RegionField(seed, 1024);
			for (int c = 0; c < 6; c++) {
				Cell cell = new Cell(c - 3, 2 * c - 5);
				List<Pt> poly = CellGeometry.polygon(f, cell);
				int bad = 0, n = 0;
				for (int i = 0; i < 400; i++) {
					int x = f.siteX(cell.cx(), cell.cz()) - 1400 + Hash64.below(Hash64.hash(seed, "gx", c, i), 2800);
					int z = f.siteZ(cell.cx(), cell.cz()) - 1400 + Hash64.below(Hash64.hash(seed, "gz", c, i), 2800);
					boolean owns = CellGeometry.owns(f, cell, x, z), in = inside(poly, x, z);
					// within 2 blocks of the border the rounded polygon may disagree
					if (owns != in) { n++; if (f.edgeDistance(x, z) > 2 || owns) bad++; }
				}
				assertEquals(0, bad, "cell " + cell + " seed " + seed + ": " + n + " disagreements");
			}
		}
	}

	@Test
	void sharedEdgeIsTheSameFromBothSidesAndLiesOnTheBisector() {
		RegionField f = new RegionField(9, 1024);
		int edges = 0;
		for (int cz = -2; cz <= 2; cz++) for (int cx = -2; cx <= 2; cx++) {
			Cell a = new Cell(cx, cz);
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dz == 0) continue;
				Cell b = new Cell(cx + dx, cz + dz);
				CellGeometry.Edge e1 = CellGeometry.sharedEdge(f, a, b), e2 = CellGeometry.sharedEdge(f, b, a);
				assertEquals(e1, e2, "edge must not depend on the argument order");
				if (e1 == null) continue;
				edges++;
				for (double t : new double[] {0.1, 0.5, 0.9}) {
					double x = e1.x(t), z = e1.z(t);
					double da = Math.hypot(x - f.siteX(a.cx(), a.cz()), z - f.siteZ(a.cx(), a.cz())), db = Math.hypot(x - f.siteX(b.cx(), b.cz()), z - f.siteZ(b.cx(), b.cz()));
					assertEquals(da, db, 1e-6, "edge point is equidistant from both sites");
					// no other site is closer
					RegionField.Hit h = f.query((int) Math.round(x), (int) Math.round(z));
					boolean ab = (h.cx() == a.cx() && h.cz() == a.cz()) || (h.cx() == b.cx() && h.cz() == b.cz());
					assertTrue(ab, "edge point of " + a + "|" + b + " is owned by a third cell " + h);
				}
			}
		}
		assertTrue(edges > 30, "found " + edges + " edges");
	}

	@Test
	void nonNeighboursShareNoEdge() {
		RegionField f = new RegionField(9, 1024);
		assertNull(CellGeometry.sharedEdge(f, new Cell(0, 0), new Cell(5, 5)));
		assertNull(CellGeometry.sharedEdge(f, new Cell(0, 0), new Cell(0, 0)));
	}
}
