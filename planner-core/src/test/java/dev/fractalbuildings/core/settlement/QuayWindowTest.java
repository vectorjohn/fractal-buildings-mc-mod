package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.RegionField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * JUNCTION / METROFILL R1: a quay run is graded, and cut where it cannot meet a street, the same way by every tile that sees it,
 * whatever that tile's window. The case: metropolis s7_m3 on the recorded world 12345 (the CLI's
 * {@code --site 7360,-2496,metropolis,1340}), where a quay at x 7676 crosses the border street at z -2240 between d47_m15 and
 * d47_m14. When each tile traced and graded the quay through its own window, d47_m15 saw the run from z -2388 and d47_m14 from
 * z -2304; the profiles differed, and only d47_m14 cut the quay at the street ({@code tile-border-agree}).
 */
class QuayWindowTest {
	static final long SEED = 12345;
	static final Path FIXTURES = Files.isDirectory(Path.of("../fixtures/terrain")) ? Path.of("../fixtures/terrain") : Path.of("fixtures/terrain");

	/** The settlement the CLI's {@code --site x,z,tier,radius} makes (as {@code CityCommand.fromSite}). */
	static SettlementSite site(RegionField field, int x, int z, Tier tier, int radius) {
		Cell claimant = CellGeometry.owner(field, x, z);
		List<Cell> cs = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) {
			for (int dx = -8; dx <= 8; dx++) {
				Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
				long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
				if (c.equals(claimant) || ex * ex + ez * ez <= (long) radius * radius) cs.add(c);
			}
		}
		cs.sort(CellGeometry::compare);
		return new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), tier, new Pt(x, z), radius, claimant, cs);
	}

	/** The runs of a list that come within {@code band} blocks of the line {@code axis == at} (x for axis 0, z for 1). */
	static TreeSet<String> near(List<String> runs, int axis, int at, int band) {
		TreeSet<String> out = new TreeSet<>();
		for (String r : runs) {
			for (String col : r.split(" ")) {
				String[] xz = col.substring(0, col.indexOf(':')).split(",");
				if (Math.abs(Integer.parseInt(xz[axis]) - at) <= band) { out.add(r); break; }
			}
		}
		return out;
	}

	@Test
	void neighbouringTilesGradeTheQuaysAcrossTheirBorderAlike() throws IOException {
		RecordedProbe r = RecordedProbe.read(FIXTURES.resolve("world-12345-s64.json.gz"));
		RegionField field = new RegionField(SEED, 1024);
		SettlementSite site = site(field, 7360, -2496, Tier.METROPOLIS, 1340);
		CoarseProbe coarse = CoarseProbe.of(r, r, 32);
		// {ix, iz} of two tiles and the border line between them: {axis, coordinate}
		int[][][] pairs = {{{47, -15}, {47, -14}, {1, -2240}}, {{47, -14}, {48, -14}, {0, 7696}}};
		int compared = 0;
		for (int[][] p : pairs) {
			// a fresh source per tile, as two worker threads or two game sessions would have: nothing is shared but the inputs
			List<String> a = Districts.quayRuns(new SkeletonSource.Direct(SEED, new FixedSiteView(site, field), site, coarse), p[0][0], p[0][1], r);
			List<String> b = Districts.quayRuns(new SkeletonSource.Direct(SEED, new FixedSiteView(site, field), site, coarse), p[1][0], p[1][1], r);
			TreeSet<String> na = near(a, p[2][0], p[2][1], Districts.MARGIN), nb = near(b, p[2][0], p[2][1], Districts.MARGIN);
			assertEquals(String.join("\n", na), String.join("\n", nb), "quays across the border of " + p[0][0] + "," + p[0][1] + " and " + p[1][0] + "," + p[1][1]);
			compared += na.size();
		}
		assertFalse(compared == 0, "the case has a quay across a border");
	}
}
