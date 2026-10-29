package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.road.RoadNet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 6.15 ROADJOIN look (opt-in, {@code FB_ROADJOIN_PNG=outdir}): a top view of the approaches of one settlement near a point. Water blue, ground grey by
 * height, approach columns red (deck over water orange), district street/deck/plaza surfaces green, district-owned (masked) columns yellow.
 * {@code FB_ROADJOIN_PNG_AT="w|pt,cx,cz,x,z,r"}, e.g. {@code pt,3,1,3616,1440,80} (pt: playtest fixtures, w: world 12345).
 */
@EnabledIfEnvironmentVariable(named = "FB_ROADJOIN_PNG", matches = ".+")
class ApproachPicTest {
	@Test
	void pic() throws Exception {
		File dir = new File(System.getenv("FB_ROADJOIN_PNG"));
		dir.mkdirs();
		for (String spec : System.getenv().getOrDefault("FB_ROADJOIN_PNG_AT", "pt,3,1,3616,1440,80;w,2,1,2560,1984,200").split(";")) {
			String[] f = spec.split(",");
			WorldPlanner w = f[0].equals("pt") ? JoinWorld.playtest() : WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
			int cx = Integer.parseInt(f[1]), cz = Integer.parseInt(f[2]), x0 = Integer.parseInt(f[3]), z0 = Integer.parseInt(f[4]), r = Integer.parseInt(f[5]);
			TerrainProbe t = w.probes().exact();
			var so = w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(cx, cz)).orElseThrow();
			var site = w.siteOf(new dev.fractalbuildings.core.settlement.Cell(cx, cz)).orElseThrow();
			int scale = r > 100 ? 3 : 6, n = 2 * r;
			BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
			for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
				int wx = x0 - r + x, wz = z0 - r + z;
				int y = t.surfaceY(wx, wz);
				int g = Math.max(40, Math.min(220, 100 + (y - t.surfaceY(x0, z0)) * 4));
				int rgb = t.waterDepth(wx, wz) > 0 ? 0x2a5fb4 : g << 16 | g << 8 | g;
				for (int i = 0; i < scale; i++) for (int j = 0; j < scale; j++) img.setRGB(x * scale + i, z * scale + j, rgb);
			}
			TreeSet<Long> chunks = new TreeSet<>();
			var apps = w.drawnApproaches(site, so);
			for (var a : apps) for (RoadNet.Col k : a.columns()) chunks.add(JoinWorld.key(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16)));
			TreeMap<Long, int[]> d = JoinWorld.districtTops(w, chunks);
			for (var en : d.entrySet()) paint(img, (int) (en.getKey() & 0xffffffffL), (int) (en.getKey() >> 32), x0, z0, r, scale, 0x30c030);
			for (var a : apps) for (RoadNet.Col k : a.columns()) {
				boolean[] m = w.districtColumns(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16));
				boolean masked = m != null && m[(k.z() & 15) * 16 + (k.x() & 15)];
				paint(img, k.x(), k.z(), x0, z0, r, scale, masked ? 0xe0e030 : k.crossing() ? 0xff9020 : 0xe02020);
			}
			javax.imageio.ImageIO.write(img, "png", new File(dir, "approach-" + x0 + "-" + z0 + ".png"));
		}
	}

	static void paint(BufferedImage img, int wx, int wz, int x0, int z0, int r, int scale, int rgb) {
		int x = wx - (x0 - r), z = wz - (z0 - r);
		if (x < 0 || z < 0 || x >= 2 * r || z >= 2 * r) return;
		for (int i = 0; i < scale; i++) for (int j = 0; j < scale; j++) img.setRGB(x * scale + i, z * scale + j, rgb);
	}
}
