package dev.fractalbuildings.core.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writer for the {@code fb-region-dump/1} region map: the probe's lattice (biome palette, surface, water)
 * plus one {@code layers} entry per field with its cell decisions and sites. Terrain-only dumps are the
 * same format with {@code layers: []}. Byte-stable: fixed number formats, ordered lists only.
 */
public final class RegionMap {
	private RegionMap() {}

	public static final String FORMAT = "fb-region-dump/1";

	/** The lattice window: nx x nz columns from (x0, z0) at {@link RegionProbe#STRIDE}. */
	public record Window(int x0, int z0, int nx, int nz) {
		public Window {
			if (Math.floorMod(x0, RegionProbe.STRIDE) != 0 || Math.floorMod(z0, RegionProbe.STRIDE) != 0 || nx <= 0 || nz <= 0) {
				throw new IllegalArgumentException("window must sit on the " + RegionProbe.STRIDE + "-block lattice: " + this);
			}
		}

		public int x1() { return x0 + (nx - 1) * RegionProbe.STRIDE; }
		public int z1() { return z0 + (nz - 1) * RegionProbe.STRIDE; }
	}

	/** {@code claimedBy} is parallel to {@code cells}: the owner cell that claimed it, or null. */
	public record Layer(RegionPlanner planner, String policy, List<CellDecision> cells, List<CellKey> claimedBy) {}

	/** Grow policy: every cell decides alone, radius clamped to half a cell. */
	public static Layer growLayer(RegionPlanner planner, Window w) {
		List<CellDecision> cells = planner.decideWindow(w.x0(), w.z0(), w.x1(), w.z1());
		return new Layer(planner, "grow", cells, java.util.Collections.nCopies(cells.size(), null));
	}

	/** Join policy (MC1): big settlements claim cells; see {@link FieldRegionView}. */
	public static Layer layer(RegionPlanner planner, Window w) {
		List<CellDecision> cells = new ArrayList<>();
		List<CellKey> claimedBy = new ArrayList<>();
		for (FieldRegionView.Resolved r : new FieldRegionView(planner, w).resolveWindow(w.x0(), w.z0(), w.x1(), w.z1())) {
			cells.add(r.decision());
			claimedBy.add(r.claimedBy());
		}
		return new Layer(planner, "join", cells, claimedBy);
	}

	private static String f(double d) {
		return String.format(Locale.ROOT, "%.3f", d);
	}

	private static void ints(StringBuilder sb, List<Integer> a) {
		sb.append('[');
		for (int i = 0; i < a.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append(a.get(i));
		}
		sb.append(']');
	}

	public static String toJson(long seed, String mc, RegionProbe probe, Window w, List<Layer> layers) {
		List<String> palette = new ArrayList<>();
		List<Integer> biome = new ArrayList<>(), surface = new ArrayList<>(), water = new ArrayList<>();
		for (int j = 0; j < w.nz(); j++) {
			for (int i = 0; i < w.nx(); i++) {
				int x = w.x0() + i * RegionProbe.STRIDE, z = w.z0() + j * RegionProbe.STRIDE;
				String b = BiomeClass.path(probe.biome(x, z));
				int k = palette.indexOf(b);
				if (k < 0) {
					palette.add(b);
					k = palette.size() - 1;
				}
				biome.add(k);
				int depth = probe.waterDepth(x, z);
				surface.add(probe.floorY(x, z) + depth);
				water.add(depth);
			}
		}
		StringBuilder sb = new StringBuilder(1 << 20);
		sb.append("{\"format\":\"").append(FORMAT).append("\",\"seed\":").append(seed).append(",\"mc\":\"").append(mc).append("\",");
		sb.append("\"grid\":{\"x0\":").append(w.x0()).append(",\"z0\":").append(w.z0()).append(",\"step\":").append(RegionProbe.STRIDE)
			.append(",\"nx\":").append(w.nx()).append(",\"nz\":").append(w.nz()).append("},\"palette\":[");
		for (int i = 0; i < palette.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append('"').append(palette.get(i)).append('"');
		}
		sb.append("],\"biome\":");
		ints(sb, biome);
		sb.append(",\"surface\":");
		ints(sb, surface);
		sb.append(",\"water\":");
		ints(sb, water);
		sb.append(",\"layers\":[");
		for (int li = 0; li < layers.size(); li++) {
			Layer l = layers.get(li);
			RegionField fld = l.planner().field;
			if (li > 0) sb.append(',');
			sb.append("{\"cellSize\":").append(fld.cellSize).append(",\"jitter\":").append(f(fld.jitter))
				.append(",\"mode\":\"BEST_OF_K\",\"policy\":\"").append(l.policy()).append("\",\"cells\":[");
			for (int i = 0; i < l.cells().size(); i++) {
				CellDecision c = l.cells().get(i);
				if (i > 0) sb.append(',');
				sb.append("{\"cx\":").append(c.cx()).append(",\"cz\":").append(c.cz())
					.append(",\"siteX\":").append(c.siteX()).append(",\"siteZ\":").append(c.siteZ())
					.append(",\"city\":").append(c.settled()).append(",\"reason\":\"").append(c.reason())
					.append("\",\"tier\":\"").append(c.tier().json).append("\",\"centreX\":").append(c.centreX())
					.append(",\"centreZ\":").append(c.centreZ()).append(",\"radius\":").append(c.radius())
					.append(",\"radiusDrawn\":").append(c.radiusDrawn())
					.append(",\"radiusFit\":").append(c.radiusFit()).append(",\"edgeDist\":").append(c.edgeDist())
					.append(",\"roll\":").append(f(c.roll())).append(",\"chance\":").append(f(c.chance()))
					.append(",\"suitability\":").append(f(c.suitability())).append(",\"siteSuitability\":").append(f(c.siteSuitability()))
					.append(",\"candidate\":").append(c.candidate()).append(",\"water\":").append(f(c.waterFrac()))
					.append(",\"peak\":").append(f(c.peakFrac())).append(",\"waterDist\":").append(Math.round(c.waterDist())).append(",\"groups\":{");
				boolean first = true;
				for (var e : c.groups().entrySet()) {
					if (!first) sb.append(',');
					first = false;
					sb.append('"').append(e.getKey()).append("\":").append(f(e.getValue()));
				}
				sb.append('}');
				CellKey by = l.claimedBy().get(i);
				if (by != null) sb.append(",\"claimedBy\":[").append(by.cx()).append(',').append(by.cz()).append(']');
				sb.append('}');
			}
			sb.append("],\"sites\":[");
			int S = fld.cellSize;
			boolean firstSite = true;
			for (int cz = Math.floorDiv(w.z0(), S) - 1; cz <= Math.floorDiv(w.z1(), S) + 1; cz++) {
				for (int cx = Math.floorDiv(w.x0(), S) - 1; cx <= Math.floorDiv(w.x1(), S) + 1; cx++) {
					if (!firstSite) sb.append(',');
					firstSite = false;
					sb.append('[').append(cx).append(',').append(cz).append(',').append(fld.siteX(cx, cz)).append(',').append(fld.siteZ(cx, cz)).append(']');
				}
			}
			sb.append("]}");
		}
		sb.append("]}\n");
		return sb.toString();
	}
}
