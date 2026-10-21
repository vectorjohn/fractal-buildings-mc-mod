package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.TerrainProbe;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/** Evidence tool: top-down picture of district plans (terrain shade, water, streets by kind, plazas, lots by type, tile borders). */
final class DistrictPng {
	private DistrictPng() {}

	static int typeColour(String t) {
		return switch (t) {
			case "house" -> 0xd9a066;
			case "shop" -> 0xe0d050;
			case "workshop" -> 0xb06030;
			case "tower" -> 0xc060c0;
			case "block" -> 0xd04040;
			case "highrise" -> 0x6060e0;
			case "hall" -> 0x40c0c0;
			case "temple" -> 0xf0f0f0;
			default -> 0x909090;
		};
	}

	static void write(List<DistrictPlan> plans, TerrainProbe terrain, File out, int scale) throws IOException {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (DistrictPlan p : plans) {
			Rect r = p.district().rect();
			x0 = Math.min(x0, r.x()); z0 = Math.min(z0, r.z()); x1 = Math.max(x1, r.x() + r.w()); z1 = Math.max(z1, r.z() + r.d());
		}
		int w = x1 - x0, h = z1 - z0;
		BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		int[][] col = new int[w][h];
		for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
			int wx = x0 + x, wz = z0 + z, g = 40 + Math.min(120, Math.max(0, terrain.floorY(wx, wz) - 40) * 3);
			col[x][z] = terrain.waterDepth(wx, wz) > 0 ? 0x2060c0 : (g << 8) | (g / 3 << 16) | (g / 3);
		}
		for (DistrictPlan p : plans) {
			for (Street s : p.streets()) {
				int c = switch (s.kind()) { case ARTERIAL -> 0xffffff; case MAIN -> 0xe8d090; case STREET -> 0xc0a070; case LANE -> 0x8a7050; default -> 0x80644a; };
				for (Rect r : dev.fractalbuildings.core.check.SettlementChecks.footprint(s)) fill(col, r, c, x0, z0, w, h);
			}
			for (Plaza pz : p.plazas()) fill(col, new Rect(pz.x(), pz.z(), pz.w(), pz.d()), 0xb0b0b0, x0, z0, w, h);
			for (LotBrief l : p.lots()) {
				fill(col, l.rect(), typeColour(l.type()), x0, z0, w, h);
				// front edge marker
				Rect e = switch (l.front()) {
					case NORTH -> new Rect(l.x(), l.z(), l.w(), 1);
					case SOUTH -> new Rect(l.x(), l.z() + l.d() - 1, l.w(), 1);
					case WEST -> new Rect(l.x(), l.z(), 1, l.d());
					default -> new Rect(l.x() + l.w() - 1, l.z(), 1, l.d());
				};
				fill(col, e, 0x202020, x0, z0, w, h);
			}
		}
		for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) for (int sz = 0; sz < scale; sz++) for (int sx = 0; sx < scale; sx++) img.setRGB(x * scale + sx, z * scale + sz, col[x][z]);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(255, 0, 255, 140));
		for (DistrictPlan p : plans) {
			Rect r = p.district().rect();
			g.drawRect((r.x() - x0) * scale, (r.z() - z0) * scale, r.w() * scale, r.d() * scale);
		}
		g.dispose();
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
	}

	private static void fill(int[][] col, Rect r, int c, int x0, int z0, int w, int h) {
		for (int z = Math.max(r.z(), z0); z < Math.min(r.z() + r.d(), z0 + h); z++) for (int x = Math.max(r.x(), x0); x < Math.min(r.x() + r.w(), x0 + w); x++) col[x - x0][z - z0] = c;
	}
}
