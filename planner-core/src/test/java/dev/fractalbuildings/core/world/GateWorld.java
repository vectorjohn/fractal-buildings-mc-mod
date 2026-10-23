package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.Ids;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A gate window (a row of {@code fixtures/terrain/gate-sites.tsv}) as {@link WorldProbes} on its recorded dump. */
record GateWorld(String name, long seed, int x, int z, String tier, int radius, int cells, RecordedProbe dump, WorldProbes probes) {
	static final Path FIXTURES = Files.isDirectory(Path.of("../fixtures/terrain")) ? Path.of("../fixtures/terrain") : Path.of("fixtures/terrain");

	static GateWorld load(String name) {
		for (GateWorld g : all()) if (g.name().equals(name)) return g;
		throw new IllegalArgumentException(name);
	}

	static List<GateWorld> all() {
		try {
			List<GateWorld> out = new ArrayList<>();
			for (String line : Files.readAllLines(FIXTURES.resolve("gate-sites.tsv")).stream().skip(1).toList()) {
				if (line.isBlank()) continue;
				String[] f = line.split("\t");
				RecordedProbe r = RecordedProbe.read(FIXTURES.resolve(f[0] + ".json.gz"));
				out.add(new GateWorld(f[0], Long.parseLong(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4], Integer.parseInt(f[5]), Integer.parseInt(f[6]), r, probes(r)));
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The settlement the CLI's {@code --site x,z,tier,radius} makes: the cells whose sites lie within the radius, plus the claimant. */
	SettlementSite site() {
		RegionField field = new RegionField(seed, 1024);
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
		return new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), Tier.of(tier), new Pt(x, z), radius, claimant, cs);
	}

	WorldPlanner planner(WorldPlanner.Caches caps, java.util.concurrent.Executor workers) {
		return WorldPlanner.forSite(seed, WorldConfig.defaults(), probes, workers, caps, site());
	}

	/** The probes of a dump, with the region window the way the CLI computes it. */
	static WorldProbes probes(RecordedProbe r) {
		int s = RegionProbe.STRIDE, x0 = Math.floorDiv(r.x0() + s - 1, s) * s, z0 = Math.floorDiv(r.z0() + s - 1, s) * s;
		int x1 = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), s) * s, z1 = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), s) * s;
		RegionMap.Window w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		return new WorldProbes(RegionProbe.of(r, r), CoarseProbe.of(r, r, 32), r, r, w);
	}
}
