package dev.fractalbuildings.core.settlement;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Evidence tool: draws a settlement plan (mask, cells, tiles, roads, gates, landmarks) to a PNG at {@code scale} blocks per pixel. */
final class SkeletonPng {
	private SkeletonPng() {}

	static void write(SettlementPlan p, File out, int scale) throws IOException {
		Rect b = p.bounds();
		int w = b.w() / scale, h = b.d() / scale;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		CoarseRaster c = p.coarse();
		int[] mask = c.mask(), dens = c.density(), water = c.water(), quarter = c.quarter();
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int i = Math.floorDiv(px * scale, c.step()), j = Math.floorDiv(py * scale, c.step());
				int k = c.index(Math.min(i, c.nx() - 1), Math.min(j, c.nz() - 1));
				int rgb;
				switch (mask[k]) {
					case CoarseRaster.URBAN -> { int v = 120 + dens[k] * 130 / 100; rgb = new Color(v, v - 20, 60 + (quarter[k] * 20) % 120).getRGB(); }
					case CoarseRaster.WATER -> rgb = 0x2a5d9f;
					case CoarseRaster.STEEP -> rgb = 0x6b5b4b;
					case CoarseRaster.OPEN -> rgb = 0x4f9a4f;
					default -> rgb = water[k] > 0 ? 0x1f4a80 : 0x3b4a3b;
				}
				img.setRGB(px, py, rgb);
			}
		}
		Graphics2D g = img.createGraphics();
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(255, 255, 255, 90));
		for (DistrictSummary d : p.districts()) g.drawRect((d.x() - b.x()) / scale, (d.z() - b.z()) / scale, d.w() / scale, d.d() / scale);
		g.setColor(Color.MAGENTA);
		for (CellOutline o : p.cells()) {
			int n = o.polygon().size();
			for (int i = 0; i < n; i++) {
				Pt a = o.polygon().get(i), e = o.polygon().get((i + 1) % n);
				g.drawLine((a.x() - b.x()) / scale, (a.z() - b.z()) / scale, (e.x() - b.x()) / scale, (e.z() - b.z()) / scale);
			}
		}
		for (Road r : p.roads()) {
			g.setColor(r.kind() == RoadKind.ARTERIAL ? Color.ORANGE : Color.WHITE);
			g.setStroke(new BasicStroke(r.kind() == RoadKind.ARTERIAL ? 3f : 2f));
			for (int i = 1; i < r.points().size(); i++) {
				Pt a = r.points().get(i - 1), e = r.points().get(i);
				boolean bridge = false;
				for (int[] br : r.bridges()) if (i - 1 >= br[0] && i <= br[1]) bridge = true;
				g.setColor(bridge ? Color.CYAN : r.kind() == RoadKind.ARTERIAL ? Color.ORANGE : Color.WHITE);
				g.drawLine((a.x() - b.x()) / scale, (a.z() - b.z()) / scale, (e.x() - b.x()) / scale, (e.z() - b.z()) / scale);
			}
		}
		g.setStroke(new BasicStroke(1f));
		for (Gate gt : p.gates()) {
			g.setColor(gt.kind() == GateKind.CELL ? Color.YELLOW : Color.RED);
			g.fillOval((gt.x() - b.x()) / scale - 4, (gt.z() - b.z()) / scale - 4, 8, 8);
		}
		g.setColor(Color.GREEN);
		for (Landmark l : p.landmarks()) g.drawRect((l.x() - b.x()) / scale, (l.z() - b.z()) / scale, Math.max(2, l.w() / scale), Math.max(2, l.d() / scale));
		g.setColor(Color.WHITE);
		g.drawOval((p.site().centre().x() - b.x()) / scale - 3, (p.site().centre().z() - b.z()) / scale - 3, 6, 6);
		g.dispose();
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
	}
}
