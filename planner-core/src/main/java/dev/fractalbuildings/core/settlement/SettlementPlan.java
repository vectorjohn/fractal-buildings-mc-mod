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
		List<Wall> walls, List<Violation> violations) {
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
		for (int i = 0; i < quarters.size(); i++) if (quarters.get(i).id() != i) throw new IllegalArgumentException("quarter " + i + " has id " + quarters.get(i).id());
		for (int q : coarse.quarter()) if (q < -1 || q >= quarters.size()) throw new IllegalArgumentException("coarse quarter index " + q);
		Set<String> dIds = unique("district", districts.stream().map(DistrictSummary::id).toList());
		Set<String> rIds = unique("road", roads.stream().map(Road::id).toList());
		unique("gate", gates.stream().map(Gate::id).toList());
		unique("landmark", landmarks.stream().map(Landmark::id).toList());
		unique("wall", walls.stream().map(Wall::id).toList());
		for (DistrictSummary d : districts) {
			if (d.quarter() < -1 || d.quarter() >= quarters.size()) throw new IllegalArgumentException("district " + d.id() + " quarter " + d.quarter());
		}
		for (Landmark l : landmarks) if (!dIds.contains(l.district())) throw new IllegalArgumentException("landmark " + l.id() + " names unknown district " + l.district());
		for (Gate g : gates) if (g.road() != null && !rIds.contains(g.road())) throw new IllegalArgumentException("gate " + g.id() + " names unknown road " + g.road());
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

	/** A copy with the given violations (for checkers that annotate a plan). */
	public SettlementPlan withViolations(List<Violation> v) {
		return new SettlementPlan(genVersion, seed, site, bounds, coarse, quarters, cells, districts, roads, gates, landmarks, walls, new ArrayList<>(v));
	}
}
