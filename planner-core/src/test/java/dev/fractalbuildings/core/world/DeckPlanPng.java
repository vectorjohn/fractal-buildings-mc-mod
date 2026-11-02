package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.road.Curve;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.settlement.Cell;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** DECKDIAG F3 review picture (opt-in {@code -Dfb.planpng=OUT.png}): the sm11_m2 lake crop, water blue, skeleton approaches red, drawn approaches yellow with deck stations orange, ports white. */
class DeckPlanPng {
	@Test
	void sm11m2() throws Exception {
		String out = System.getProperty("fb.planpng");
		Assumptions.assumeTrue(out != null, "opt-in");
		WorldPlanner w = JoinWorld.deckdiag();
		int x0 = -10688, z0 = -1664, x1 = -10240, z1 = -1152, sc = 2;
		BufferedImage img = new BufferedImage((x1 - x0) * sc / 2, (z1 - z0) * sc / 2, BufferedImage.TYPE_INT_RGB); // 1 px per 2 blocks
		var t = w.probes().roadExact();
		for (int py = 0; py < img.getHeight(); py++) for (int px = 0; px < img.getWidth(); px++) {
			int x = x0 + px * 2, z = z0 + py * 2;
			int h = Math.max(0, Math.min(255, (t.floorY(x, z) - 55) * 6));
			img.setRGB(px, py, t.waterDepth(x, z) >= 1 ? new Color(40, 90, 200).getRGB() : new Color(70 + h / 4, 120 + h / 4, 70 + h / 4).getRGB());
		}
		Graphics2D g = img.createGraphics();
		g.setStroke(new BasicStroke(2f));
		var site = w.siteOf(new Cell(-11, -2)).orElseThrow();
		var owner = w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(-11, -2)).orElseThrow();
		for (Cell c : site.cells()) for (var r : w.source(site).skeleton(c).approaches()) {
			g.setColor(new Color(230, 40, 40));
			for (int i = 1; i < r.points().size(); i++) line(g, r.points().get(i - 1).x(), r.points().get(i - 1).z(), r.points().get(i).x(), r.points().get(i).z(), x0, z0);
		}
		g.setStroke(new BasicStroke(3f));
		for (var an : w.approachNodes(site)) {
			double[][] st = Curve.stations(new ArrayList<>(an.nodes()));
			var pr = Profile.approach(st, t, new TreeMap<>(), an.road().width(), true);
			for (int k = 1; k < st.length; k++) {
				boolean deck = false;
				for (var c : pr.crossings()) if (k >= c.first() && k <= c.last()) deck = true;
				g.setColor(deck ? new Color(255, 140, 0) : new Color(255, 230, 0));
				line(g, (int) st[k - 1][0], (int) st[k - 1][1], (int) st[k][0], (int) st[k][1], x0, z0);
			}
		}
		g.setColor(Color.WHITE);
		for (var p : w.roads().portsOf(owner)) g.fillOval((p.x() - x0) / 2 - 4, (p.z() - z0) / 2 - 4, 8, 8);
		g.setColor(Color.MAGENTA);
		g.fillRect((site.centre().x() - x0) / 2 - 4, (site.centre().z() - z0) / 2 - 4, 8, 8);
		g.dispose();
		ImageIO.write(img, "png", new File(out));
	}

	static void line(Graphics2D g, int ax, int az, int bx, int bz, int x0, int z0) { g.drawLine((ax - x0) / 2, (az - z0) / 2, (bx - x0) / 2, (bz - z0) / 2); }
}
