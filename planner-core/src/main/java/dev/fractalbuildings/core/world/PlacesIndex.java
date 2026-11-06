package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellSkeleton;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Gate;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The places index (C15, {@code /fb places}): what the planner placed within a square around a point, as nested maps for
 * {@link PlaytestNote#json}. Pure planner data (region field, district tiles, skeleton gates); no world blocks are read. Every list is sorted
 * by id and every map has a fixed key order, so equal (seed, config, centre, radius) give byte-identical JSON.
 *
 * <p>Format {@code fb-places/1}: {@code settlements[]} (id, tier, centre, radius, bbox of its disc, {@code links} = ids of the settlements an
 * intercity road joins it to, {@code gates[]} = its exit gates (id, x, z, road), {@code districts[]} = the planned tiles that touch the
 * square (id, tile, bbox, lot counts by type, {@code notable[]}) ). A notable building is a built lot whose type is not a house: id, type,
 * style, lot rectangle and the facing of its front (the street side; the door is on that side, its exact block is a building detail).
 */
public final class PlacesIndex {
	private PlacesIndex() {}

	public static final String FORMAT = "fb-places/1";

	/** Building types that are not listed as notable (the bulk of every district). */
	static final java.util.Set<String> COMMON = java.util.Set.of("house", "cottage", "hut");

	public static Map<String, Object> build(WorldPlanner w, int cx, int cz, int radius) {
		int x0 = cx - radius, z0 = cz - radius, x1 = cx + radius, z1 = cz + radius;
		// settlements whose disc meets the square
		TreeMap<String, SettlementSite> sites = new TreeMap<>();
		Cell c0 = w.cellAt(cx, cz);
		int reach = Math.floorDiv(radius + w.config().maxRadius(), w.config().cellSize()) + 2;
		for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
			Optional<SettlementSite> o = w.siteOf(new Cell(c0.cx() + dx, c0.cz() + dz));
			if (o.isEmpty()) continue;
			SettlementSite s = o.get();
			int nx = Math.max(x0, Math.min(x1, s.centre().x())), nz = Math.max(z0, Math.min(z1, s.centre().z()));
			long ex = nx - s.centre().x(), ez = nz - s.centre().z();
			if (ex * ex + ez * ez <= (long) s.radius() * s.radius()) sites.put(s.id(), s);
		}
		// planned tiles that touch the square, by owner
		TreeMap<String, TreeMap<String, TileInfo>> tilesBySite = new TreeMap<>();
		DistrictGrid.TileId lo = w.grid().tileOf(x0, z0), hi = w.grid().tileOf(x1, z1);
		for (int iz = lo.iz(); iz <= hi.iz(); iz++) for (int ix = lo.ix(); ix <= hi.ix(); ix++) {
			Optional<TileInfo> t = w.tile(ix, iz);
			if (t.isEmpty()) continue;
			sites.putIfAbsent(t.get().settlementId(), t.get().site());
			tilesBySite.computeIfAbsent(t.get().settlementId(), k -> new TreeMap<>()).put(String.format("%09d,%09d", ix + 100_000_000, iz + 100_000_000), t.get());
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("format", FORMAT);
		out.put("seed", w.seed());
		out.put("centre", pt(cx, cz));
		out.put("radius", radius);
		List<Object> list = new ArrayList<>();
		for (SettlementSite s : sites.values()) list.add(settlement(w, s, tilesBySite.getOrDefault(s.id(), new TreeMap<>())));
		out.put("settlements", list);
		return out;
	}

	private static Map<String, Object> pt(int x, int z) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", x);
		m.put("z", z);
		return m;
	}

	private static Map<String, Object> bbox(int minX, int minZ, int maxX, int maxZ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("minX", minX);
		m.put("minZ", minZ);
		m.put("maxX", maxX);
		m.put("maxZ", maxZ);
		return m;
	}

	private static Map<String, Object> settlement(WorldPlanner w, SettlementSite s, TreeMap<String, TileInfo> tiles) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", s.id());
		m.put("tier", s.tier().json);
		m.put("centre", pt(s.centre().x(), s.centre().z()));
		m.put("radius", s.radius());
		m.put("bbox", bbox(s.centre().x() - s.radius(), s.centre().z() - s.radius(), s.centre().x() + s.radius(), s.centre().z() + s.radius()));
		TreeMap<String, Boolean> links = new TreeMap<>();
		for (SettlementSite n : w.linkNeighbours(s)) links.put(n.id(), true);
		m.put("links", new ArrayList<>(links.keySet()));
		TreeMap<String, Gate> gates = new TreeMap<>();
		for (Cell c : s.cells()) {
			CellSkeleton sk = w.source(s).skeleton(c);
			if (sk == null) continue;
			for (Gate g : sk.gates()) if (g.kind() == GateKind.EXIT) gates.put(g.x() + "," + g.z() + "," + g.id(), g);
		}
		List<Object> gl = new ArrayList<>();
		for (Gate g : sortedGates(gates)) {
			Map<String, Object> gm = new LinkedHashMap<>();
			gm.put("id", g.id());
			gm.put("x", g.x());
			gm.put("z", g.z());
			gm.put("road", g.road());
			gl.add(gm);
		}
		m.put("gates", gl);
		List<Object> dl = new ArrayList<>();
		for (TileInfo t : tiles.values()) dl.add(district(w, t));
		m.put("districts", dl);
		return m;
	}

	/** Gates sorted by (x, z, id) numerically (the map key sorts as text). */
	private static List<Gate> sortedGates(TreeMap<String, Gate> gates) {
		List<Gate> l = new ArrayList<>(gates.values());
		l.sort(java.util.Comparator.comparingInt(Gate::x).thenComparingInt(Gate::z).thenComparing(Gate::id));
		return l;
	}

	private static Map<String, Object> district(WorldPlanner w, TileInfo t) {
		Map<String, Object> m = new LinkedHashMap<>();
		DistrictGrid.Tile tl = t.tile();
		var plan = w.plan(t.ix(), t.iz()).plan();
		m.put("id", plan.district().id());
		m.put("tile", pt(t.ix(), t.iz()));
		m.put("bbox", bbox(tl.minX(), tl.minZ(), tl.maxX(), tl.maxZ()));
		TreeMap<String, Integer> counts = new TreeMap<>();
		TreeMap<String, LotBrief> notable = new TreeMap<>();
		for (LotBrief l : plan.lots()) {
			if (!l.builds()) continue;
			counts.merge(l.type(), 1, Integer::sum);
			if (!COMMON.contains(l.type())) notable.put(l.id(), l);
		}
		m.put("lotsByType", new LinkedHashMap<String, Object>(counts));
		List<Object> nl = new ArrayList<>();
		for (LotBrief l : notable.values()) {
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("id", l.id());
			b.put("type", l.type());
			b.put("style", l.style());
			b.put("bbox", bbox(l.x(), l.z(), l.x() + l.w(), l.z() + l.d()));
			b.put("front", l.front().name().toLowerCase(java.util.Locale.ROOT));
			nl.add(b);
		}
		m.put("notable", nl);
		return m;
	}
}
