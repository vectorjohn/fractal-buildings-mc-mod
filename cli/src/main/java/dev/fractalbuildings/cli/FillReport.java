package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.check.FillStats;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.json.JsonReader;
import dev.fractalbuildings.core.json.JsonWriter;
import dev.fractalbuildings.core.settlement.CoarseRaster;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.SortedMap;

/** The {@code ## Fill} section of {@code city-check} and its {@code --fill-json} file (METROFILL M0, DS-47; report only). Kept apart from {@link CityCheckCommand}. */
final class FillReport {
	private FillReport() {}

	/** One site's numbers: the stats (null when planning failed) and the roof cover of its district scenes. */
	record Site(String label, String tier, FillStats fill, long roofCols, long roofDry) {}

	/**
	 * Roof cover of one district scene: {covered, dry}: columns under any building voxel (shell only, no site boxes) of the dry columns of the
	 * tile, which is what reads from the air; a yard counts as lot in the main table, not as roof.
	 */
	static long[] roofCover(DistrictScenes.Built b, CoarseRaster coarse) {
		var rect = b.plan().district().rect();
		boolean[] cov = new boolean[rect.w() * rect.d()];
		for (PlacedBuilding pb : b.scene().buildings()) {
			PlacedBuilding shellOnly = new PlacedBuilding(pb.id(), pb.lots(), pb.ox(), pb.oy(), pb.oz(), pb.rotation(), pb.shell(), List.of());
			VoxelGrid.of(shellOnly).forEach((x, y, z, role) -> {
				int wx = pb.placement().worldX(x, z) - rect.x(), wz = pb.placement().worldZ(x, z) - rect.z();
				if (wx >= 0 && wz >= 0 && wx < rect.w() && wz < rect.d()) cov[wz * rect.w() + wx] = true;
			});
		}
		int[] water = coarse.water();
		long covered = 0, dry = 0;
		for (int j = 0; j < rect.d(); j++) {
			for (int i = 0; i < rect.w(); i++) {
				int ci = Math.floorDiv(rect.x() + i - coarse.x0(), coarse.step()), cj = Math.floorDiv(rect.z() + j - coarse.z0(), coarse.step());
				if (ci < 0 || cj < 0 || ci >= coarse.nx() || cj >= coarse.nz() || water[cj * coarse.nx() + ci] > 0) continue;
				dry++;
				if (cov[j * rect.w() + i]) covered++;
			}
		}
		return new long[] {covered, dry};
	}

	/** The stats of every site keyed by label (sorted), one JSON object, for before/after diffs. */
	static String json(List<Site> sites) {
		TreeMap<String, String> m = new TreeMap<>();
		for (Site s : sites) if (s.fill() != null) m.put(s.label(), s.fill().toJson());
		StringBuilder sb = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, String> e : m.entrySet()) sb.append("  ").append(JsonWriter.quote(e.getKey())).append(": ").append(e.getValue()).append(++i < m.size() ? ",\n" : "\n");
		return sb.append("}\n").toString();
	}

	/**
	 * The lot-loss guard (DS-47): per site, every district whose built lot count differs from the baseline run ({@code --fill-baseline}, a
	 * {@code --fill-json} file), with a LOSS flag on drops in density 50+. Returns the markdown and the number of losses.
	 */
	static Object[] lotDiff(List<Site> sites, String baselineJson) {
		@SuppressWarnings("unchecked") Map<String, Object> base = (Map<String, Object>) JsonReader.parse(baselineJson);
		StringBuilder sb = new StringBuilder("\n## Lot-loss guard (DS-47, vs `--fill-baseline`)\n\nDistricts whose built lot count changed. A drop in density 50 and up is a LOSS (exit 1).\n\n| site | district | density | before | after | |\n|---|---|---|---|---|---|\n");
		int losses = 0, rows = 0;
		for (Site s : sites) {
			if (s.fill() == null || !base.containsKey(s.label())) continue;
			SortedMap<String, int[]> before = FillStats.parseDistrictLots(base.get(s.label())), after = s.fill().districtLots;
			java.util.TreeSet<String> ids = new java.util.TreeSet<>(before.keySet());
			ids.addAll(after.keySet());
			for (String id : ids) {
				int[] b = before.get(id), a = after.get(id);
				if (b == null || a == null || a[0] == b[0]) continue;
				boolean loss = a[0] < b[0] && Math.max(a[1], b[1]) >= FillStats.LOSS_DENSITY;
				if (loss) losses++;
				rows++;
				sb.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d | %s |%n", s.label(), id, Math.max(a[1], b[1]), b[0], a[0], loss ? "LOSS" : ""));
			}
		}
		if (rows == 0) sb.append("| - | no district changed | | | | |\n");
		sb.append(String.format(Locale.ROOT, "%n%d loss(es).%n", losses));
		return new Object[] {sb.toString(), losses};
	}

	static void section(StringBuilder sb, List<Site> sites, int scenes) {
		sb.append("\n## Fill (DS-47, report only)\n\nDry columns in the site disk by what covers them (%): road, built lot, plaza, unbuilt lot (`empty:*`), empty. Frontage = share of the non-road columns beside a road that are lot or plaza. Core is density 70+, middle 50-69, edge under 50.\n");
		for (String z : FillStats.BAND_NAMES) zoneTable(sb, sites, "Band " + z, z, true);
		for (String z : FillStats.RING_NAMES) zoneTable(sb, sites, "Ring " + z, z, false);
		sb.append("\n### All\n\n").append(HEAD);
		for (Site s : sites) if (s.fill() != null) zoneRow(sb, s.label(), s.fill().all);
		sb.append("\n### Per site\n\n| site | tier | arterial+main % | street km | slivers | sliver area | empty gaps <=12 % | dead ends | block lots | core lot depth (median, n) |").append(scenes > 0 ? " roof cover % |" : "")
				.append("\n|---|---|---|---|---|---|---|---|---|---|").append(scenes > 0 ? "---|" : "").append('\n');
		for (Site s : sites) {
			FillStats f = s.fill();
			if (f == null) continue;
			StringBuilder d = new StringBuilder();
			f.coreLotDepth.forEach((t, v) -> d.append(t).append(' ').append(v).append(" (").append(f.coreLotCount.get(t)).append("), "));
			sb.append(String.format(Locale.ROOT, "| %s | %s | %.1f | %.1f | %d | %d | %.1f | %d | %d | %s |", s.label(), s.tier(), f.arterialMainPct(), f.streetLength() / 1000, f.slivers.size(), f.sliverArea,
					f.emptyShortGapPct(), f.deadEnds, f.blockLots, d.length() > 2 ? d.substring(0, d.length() - 2) : "-"));
			if (scenes > 0) sb.append(s.roofDry() == 0 ? " - |" : String.format(Locale.ROOT, " %.1f |", 100.0 * s.roofCols() / s.roofDry()));
			sb.append('\n');
		}
		java.util.TreeMap<String, long[]> pairs = new java.util.TreeMap<>();
		int pi = 0;
		for (Site s : sites) {
			if (s.fill() == null) continue;
			final int col = pi++;
			s.fill().sliverPairs.forEach((k, v) -> pairs.computeIfAbsent(k, q -> new long[sites.size()])[col] += v);
		}
		final int ncols = pi;
		sb.append("\n### Sliver pairs\n\nColumns of sliver strips by the kinds of the two roads either side (kinds in alphabetical order), per site in the order of the table above.\n\n| pair |");
		for (Site s : sites) if (s.fill() != null) sb.append(' ').append(s.label()).append(" |");
		sb.append(" all |\n|---|");
		for (Site s : sites) if (s.fill() != null) sb.append("---|");
		sb.append("---|\n");
		pairs.forEach((k, v) -> {
			long sum = 0;
			for (long x : v) sum += x;
			sb.append("| ").append(k).append(" |");
			for (int i = 0; i < ncols; i++) sb.append(' ').append(v[i]).append(" |");
			sb.append(' ').append(sum).append(" |\n");
		});
	}

	private static final String HEAD = "| site | land k | road | lot | plaza | unbuilt | empty | frontage |\n|---|---|---|---|---|---|---|---|\n";

	private static void zoneTable(StringBuilder sb, List<Site> sites, String title, String zone, boolean band) {
		sb.append("\n### ").append(title).append("\n\n").append(HEAD);
		for (Site s : sites) {
			if (s.fill() == null) continue;
			FillStats.Zone z = (band ? s.fill().bands : s.fill().rings).get(zone);
			if (z != null && z.land() > 0) zoneRow(sb, s.label(), z);
		}
	}

	private static void zoneRow(StringBuilder sb, String label, FillStats.Zone z) {
		sb.append(String.format(Locale.ROOT, "| %s | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |%n", label, z.land() / 1000.0, z.roadPct(), z.lotPct(), z.plazaPct(), z.unbuiltPct(), z.emptyPct(), z.frontagePct()));
	}
}
