package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Briefs;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.DistrictSummary;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.Skeletons;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.style.StyleField;
import dev.fractalbuildings.mod.TerrainDumpTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The A1 timing table. Not part of the normal test run: {@code -Dfb.timing=1} (run under the heavy lock, record the load).
 * Writes {@code mod/build/live-probe-timing.md}.
 */
class LiveProbeTiming {
	static final long SEED = 987654321L;
	static final int SITE_X = 1280, SITE_Z = -9600, RADIUS = 477;

	private final StringBuilder md = new StringBuilder();

	private void line(String s, Object... a) {
		String t = String.format(Locale.ROOT, s, a);
		md.append(t).append('\n');
		System.out.println("TIMING " + t);
	}

	/** Records every exact column a plan reads. */
	static final class Recorder implements TerrainProbe {
		final TerrainProbe inner;
		final TreeSet<Long> columns = new TreeSet<>();
		long calls;

		Recorder(TerrainProbe inner) { this.inner = inner; }

		private void rec(int x, int z) { calls++; columns.add(((long) x << 32) | (z & 0xffffffffL)); }

		@Override public int floorY(int x, int z) { rec(x, z); return inner.floorY(x, z); }
		@Override public int waterDepth(int x, int z) { rec(x, z); return inner.waterDepth(x, z); }
		@Override public int surfaceY(int x, int z) { rec(x, z); return inner.surfaceY(x, z); }

		int chunks() {
			TreeSet<Long> c = new TreeSet<>();
			for (long k : columns) c.add(((long) ((int) (k >> 32) >> 4) << 32) | (((int) k >> 4) & 0xffffffffL));
			return c.size();
		}
	}

	static SettlementSite site(RegionField field) {
		Cell claimant = CellGeometry.owner(field, SITE_X, SITE_Z);
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) {
			for (int dx = -8; dx <= 8; dx++) {
				Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
				long ex = field.siteX(c.cx(), c.cz()) - SITE_X, ez = field.siteZ(c.cx(), c.cz()) - SITE_Z;
				if (c.equals(claimant) || ex * ex + ez * ez <= (long) RADIUS * RADIUS) cells.add(c);
			}
		}
		cells.sort(CellGeometry::compare);
		return new SettlementSite(dev.fractalbuildings.core.settlement.Ids.site(claimant.cx(), claimant.cz()), Tier.CITY, new Pt(SITE_X, SITE_Z), RADIUS, claimant, cells);
	}

	private double ms(long t0) { return (System.nanoTime() - t0) / 1e6; }

	@Test
	@EnabledIfSystemProperty(named = "fb.timing", matches = ".+")
	void timing() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(SEED);
		line("load before: %s; cpus %d", Files.readString(Path.of("/proc/loadavg")).trim(), Runtime.getRuntime().availableProcessors());

		// A: cost of filling one chunk, single thread, 40 chunks around the site, after a warm-up
		int cx0 = (SITE_X >> 4) - 4, cz0 = (SITE_Z >> 4) - 3;
		List<int[]> chunks = new ArrayList<>();
		for (int j = 0; j < 5; j++) for (int i = 0; i < 8; i++) chunks.add(new int[] {cx0 + i, cz0 + j});
		for (int i = 0; i < 8; i++) { ChunkFill.COLUMNS.fill(gen, rs, cx0 + i, cz0 - 1); ChunkFill.BATCHED.fill(gen, rs, cx0 + i, cz0 - 1); ChunkFill.WINDOWED.fill(gen, rs, cx0 + i, cz0 - 1); }
		line("");
		line("| method (one chunk = 256 columns, 1 thread) | ms per chunk | ms per column | vs (a) |");
		line("|---|---|---|---|");
		double base = 0;
		String[] names = {"(a) 256 x getBaseColumn", "(b) batched volume", "(c) windowed volume", "(a) again (drift check)"};
		ChunkFill[] fills = {ChunkFill.COLUMNS, ChunkFill.BATCHED, ChunkFill.WINDOWED, ChunkFill.COLUMNS};
		ChunkFill.Batched.FALLBACKS.set(0);
		for (int m = 0; m < 4; m++) {
			long t0 = System.nanoTime();
			for (int[] c : chunks) fills[m].fill(gen, rs, c[0], c[1]);
			double per = ms(t0) / chunks.size();
			if (m == 0) base = per;
			line("| %s | %.1f | %.3f | %.2fx |", names[m], per, per / 256, per / base);
		}
		line("windowed fallbacks to (b): %d of %d chunks (plus the warm-up's)", ChunkFill.Batched.FALLBACKS.get(), chunks.size() + 8);
		long t0 = System.nanoTime();
		int n = 0;
		for (int[] c : chunks) for (int k = 0; k < 16; k++) { gen.getBaseHeight(c[0] * 16 + k, c[1] * 16 + k, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, LiveTerrainProbe.H, rs); n++; }
		line("single getBaseHeight (WORLD_SURFACE_WG): %.3f ms per call (%d calls); getBaseColumn alone is (a)'s column cost", ms(t0) / n, n);

		// B: what a district plan reads (recorded fixture terrain, every exact call recorded)
		ProbeSpecs.Probes rec = ProbeSpecs.parse("recorded:../fixtures/terrain/gate-temperate-city.json.gz");
		RegionField field = new RegionField(SEED, 1024);
		SettlementSite site = site(field);
		var view = new FixedSiteView(site, field);
		CoarseProbe coarse = CoarseProbe.of(rec.terrain(), rec.biome(), 32);
		var src = new SkeletonSource.Direct(SEED, view, site, coarse);
		var plan = Skeletons.plan(SEED, view, site, coarse, GeneratorV1.GEN_VERSION);
		List<DistrictSummary> ds = plan.districts();
		StyleField styles = new StyleField(rec.biome(), SEED);
		line("");
		line("gate-temperate-city: %d districts; reads of one district plan (Districts.plan + Briefs.fill):", ds.size());
		List<DistrictSummary> owned = new ArrayList<>();
		long sumCalls = 0, sumCols = 0, sumChunks = 0;
		int counted = 0;
		for (DistrictSummary d : ds) {
			Recorder r = new Recorder(rec.terrain());
			var det = Districts.plan(src, d.ix(), d.iz(), r);
			if (det.isEmpty()) continue;
			Briefs.fill(det.get().plan(), src.quarters()::at, styles, r);
			owned.add(d);
			sumCalls += r.calls; sumCols += r.columns.size(); sumChunks += r.chunks();
			counted++;
		}
		line("| districts planned | exact calls (mean) | distinct columns (mean) | distinct chunks (mean) | columns per chunk |");
		line("|---|---|---|---|---|");
		line("| %d | %d | %d | %d | %.1f |", counted, sumCalls / counted, sumCols / counted, sumChunks / counted, (double) sumCols / sumChunks);

		// C: the real thing, cold memo, live terrain: wall time of one district plan per fill method
		line("");
		line("Cold district plan on live terrain (fresh memo each; skeleton already planned; 1 thread):");
		line("| district | probe | ms | chunk fills | sparse columns |");
		line("|---|---|---|---|---|");
		int[] pick = {0, owned.size() / 4, owned.size() / 2, 3 * owned.size() / 4, owned.size() - 1};
		String[] labels = {"direct columns (no memo)", "(a) memo, columns", "(b) memo, batched", "(c) memo, windowed"};
		double[] total = new double[labels.length];
		for (int p : pick) {
			DistrictSummary d = owned.get(p);
			for (int m = 0; m < labels.length; m++) {
				var live = new LiveProbes(gen, rs, m == 2 ? ChunkFill.BATCHED : m == 3 ? ChunkFill.WINDOWED : ChunkFill.COLUMNS);
				TerrainProbe exact = m == 0 ? new TerrainProbe() {
					@Override public int floorY(int x, int z) { return live.sparse().get(x, z)[1]; }
					@Override public int waterDepth(int x, int z) { int[] c = live.sparse().get(x, z); return c[0] - c[1]; }
				} : live.exact();
				long t = System.nanoTime();
				var det = Districts.plan(src, d.ix(), d.iz(), exact);
				Briefs.fill(det.get().plan(), src.quarters()::at, styles, exact);
				double e = ms(t);
				total[m] += e;
				line("| %s | %s | %.0f | %d | %d |", d.id(), labels[m], e, live.exactLive().memo().fills(), live.sparse().misses());
			}
		}
		for (int m = 0; m < labels.length; m++) line("mean cold district, %s: %.0f ms", labels[m], total[m] / pick.length);

		// D: skeleton on live coarse reads (stride 32, exact definition), cold
		line("");
		var live = new LiveProbes(gen, rs, ChunkFill.COLUMNS);
		CoarseProbe lc = live.coarse(32);
		var lsrc = new SkeletonSource.Direct(SEED, view, site, lc);
		t0 = System.nanoTime();
		var lplan = Skeletons.plan(SEED, view, site, lc, GeneratorV1.GEN_VERSION);
		line("Skeleton of the %d-cell city on live coarse reads, cold: %.0f ms, %d sparse columns read (%d districts)", site.cells().size(), ms(t0), live.sparse().misses(), lplan.districts().size());
		line("load after: %s", Files.readString(Path.of("/proc/loadavg")).trim());
		Files.writeString(Path.of("build/live-probe-timing.md"), md.toString());
	}
}
