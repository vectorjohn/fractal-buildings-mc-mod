package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The skeleton of a settlement, JSON format {@code fb-settlement/1} (docs/IR.md "Settlement plans"). Ids are
 * unique within each list; references (landmark district, gate road, district quarter) must resolve. Shapes are
 * checked here; geometric rules (lots inside tiles, disjoint lots) are {@code SettlementChecks}.
 */
public record SettlementPlan(int genVersion, long seed, SettlementSite site, Rect bounds, CoarseRaster coarse, List<Quarter> quarters,
		List<CellOutline> cells, List<DistrictSummary> districts, List<Road> roads, List<Gate> gates, List<Landmark> landmarks,
		List<Wall> walls, List<Violation> violations, List<Road> approaches) {
	public static final String FORMAT = "fb-settlement/1";
	public static final int PLAN_VERSION = 1;

	public SettlementPlan {
		Objects.requireNonNull(site);
		Objects.requireNonNull(bounds);
		Objects.requireNonNull(coarse);
		quarters = List.copyOf(quarters);
		cells = List.copyOf(cells);
		districts = List.copyOf(districts);
		roads = List.copyOf(roads);
		gates = List.copyOf(gates);
		landmarks = List.copyOf(landmarks);
		walls = List.copyOf(walls);
		violations = List.copyOf(violations);
		approaches = List.copyOf(approaches);
		for (int i = 0; i < quarters.size(); i++) if (quarters.get(i).id() != i) throw new IllegalArgumentException("quarter " + i + " has id " + quarters.get(i).id());
		for (int q : coarse.quarter()) if (q < -1 || q >= quarters.size()) throw new IllegalArgumentException("coarse quarter index " + q);
		Set<String> dIds = unique("district", districts.stream().map(DistrictSummary::id).toList());
		Set<String> rIds = unique("road", roads.stream().map(Road::id).toList());
		unique("gate", gates.stream().map(Gate::id).toList());
		unique("landmark", landmarks.stream().map(Landmark::id).toList());
		unique("wall", walls.stream().map(Wall::id).toList());
		unique("approach", approaches.stream().map(Road::id).toList());
		for (DistrictSummary d : districts) {
			if (d.quarter() < -1 || d.quarter() >= quarters.size()) throw new IllegalArgumentException("district " + d.id() + " quarter " + d.quarter());
		}
		for (Landmark l : landmarks) if (!dIds.contains(l.district())) throw new IllegalArgumentException("landmark " + l.id() + " names unknown district " + l.district());
		for (Gate g : gates) if (g.road() != null && !rIds.contains(g.road())) throw new IllegalArgumentException("gate " + g.id() + " names unknown road " + g.road());
	}

	/** A plan without road links (no approaches). */
	public SettlementPlan(int genVersion, long seed, SettlementSite site, Rect bounds, CoarseRaster coarse, List<Quarter> quarters,
			List<CellOutline> cells, List<DistrictSummary> districts, List<Road> roads, List<Gate> gates, List<Landmark> landmarks,
			List<Wall> walls, List<Violation> violations) {
		this(genVersion, seed, site, bounds, coarse, quarters, cells, districts, roads, gates, landmarks, walls, violations, List.of());
	}

	static Set<String> unique(String what, List<String> ids) {
		Set<String> seen = new TreeSet<>();
		for (String i : ids) if (!seen.add(i)) throw new IllegalArgumentException("duplicate " + what + " id " + i);
		return seen;
	}

	public DistrictSummary district(String id) {
		for (DistrictSummary d : districts) if (d.id().equals(id)) return d;
		throw new IllegalArgumentException("no district " + id);
	}

	/** A copy with the given district list (detail paths, lot counts and style weights filled in by the district step). */
	public SettlementPlan withDistricts(List<DistrictSummary> d) {
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, d, roads, gates, landmarks, walls, violations, approaches);
	}


	/** A copy with the given violations (for checkers that annotate a plan). */
	/** The same plan with other landmarks (CityPlanner puts them where district detail placed them). */
	public SettlementPlan withLandmarks(List<Landmark> l) {
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, districts, roads, gates, new ArrayList<>(l), walls, violations, approaches);
	}

	/** The same plan with other roads (CityPlanner leaves out the arterials the street lattice does not draw). */
	public SettlementPlan withRoads(List<Road> r) {
		Set<String> ids = new TreeSet<>();
		for (Road x : r) ids.add(x.id());
		List<Gate> g = new ArrayList<>();
		for (Gate x : gates) g.add(x.road() == null || ids.contains(x.road()) ? x : new Gate(x.id(), x.kind(), x.x(), x.z(), x.cells(), null));
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, districts, new ArrayList<>(r), g, landmarks, walls, violations, approaches);
	}

	/** The plan with the cut exit roads shortened and their gates gone (an EXIT gate) or left without a road (a CELL gate), 6.25 MAINSTUB. */
	public SettlementPlan withTrimmedExits(java.util.Map<String, ExitTrim.Cut> cuts) {
		List<Road> r = new ArrayList<>();
		for (Road x : roads) r.add(cuts.containsKey(x.id()) ? ExitTrim.shorten(x, cuts.get(x.id())) : x);
		List<Gate> g = new ArrayList<>();
		for (Gate x : gates) {
			if (x.road() == null || !cuts.containsKey(x.road())) { g.add(x); continue; }
			if (x.kind() == GateKind.WALL) g.add(x); // the road still runs through the wall (ExitTrim keeps it past the gate)
			else if (x.kind() != GateKind.EXIT) g.add(new Gate(x.id(), x.kind(), x.x(), x.z(), x.cells(), null));
		}
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, districts, r, g, landmarks, walls, violations, approaches);
	}

	public SettlementPlan withViolations(List<Violation> v) {
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, districts, roads, gates, landmarks, walls, new ArrayList<>(v), approaches);
	}
}
