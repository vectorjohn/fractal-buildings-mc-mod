package dev.fractalbuildings.core.settlement;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import javax.imageio.ImageIO;

/** Evidence tool (D3): the skyline along a transect, the highest lot top in each bin across a band, as bars on a ground line. */
final class SkylinePng {
	private SkylinePng() {}

	static final int BIN = 8, PX_PER_STOREY = 5;

	/**
	 * Maximum top storeys per {@value #BIN}-block bin along x (or z when {@code alongX} is false) through the centre, over lots within
	 * {@code band} blocks of the centre line; bins span {@code radius} each side of the centre.
	 */
	static int[] profile(Collection<DistrictPlan> plans, Pt centre, int radius, boolean alongX, int band) {
		int n = 2 * radius / BIN;
		int[] max = new int[n];
		for (DistrictPlan p : plans) {
			for (LotBrief l : p.lots()) {
				if (!l.builds()) continue;
				int across0 = alongX ? l.z() : l.x(), acrossLen = alongX ? l.d() : l.w(), cAcross = alongX ? centre.z() : centre.x();
				if (across0 + acrossLen < cAcross - band || across0 > cAcross + band) continue;
				int a0 = (alongX ? l.x() : l.z()) - (alongX ? centre.x() : centre.z()) + radius, a1 = a0 + (alongX ? l.w() : l.d());
				for (int b = Math.max(0, a0 / BIN); b <= Math.min(n - 1, (a1 - 1) / BIN); b++) max[b] = Math.max(max[b], l.maxStoreys());
			}
		}
		return max;
	}

	static void write(int[][] profiles, String[] labels, File out) throws IOException {
		int n = profiles[0].length, rowH = 60 * PX_PER_STOREY + 24;
		BufferedImage img = new BufferedImage(n * 2, rowH * profiles.length, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(24, 26, 32));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		for (int k = 0; k < profiles.length; k++) {
			int ground = (k + 1) * rowH - 12;
			g.setColor(new Color(60, 64, 72));
			for (int s = 10; s <= 60; s += 10) g.drawLine(0, ground - s * PX_PER_STOREY, img.getWidth(), ground - s * PX_PER_STOREY);
			for (int b = 0; b < n; b++) {
				int s = profiles[k][b];
				g.setColor(s >= 20 ? new Color(120, 120, 235) : s >= 10 ? new Color(220, 140, 80) : new Color(225, 210, 120));
				g.fillRect(b * 2, ground - s * PX_PER_STOREY, 2, s * PX_PER_STOREY);
			}
			g.setColor(Color.WHITE);
			g.drawLine(0, ground, img.getWidth(), ground);
			g.drawLine(img.getWidth() / 2, ground - 60 * PX_PER_STOREY, img.getWidth() / 2, ground + 6);
			g.drawString(labels[k] + "  (storeys: grid lines every 10; centre marked; bars 16 px = 8 blocks wide)", 6, ground - 60 * PX_PER_STOREY + 12);
		}
		g.dispose();
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
	}
}
