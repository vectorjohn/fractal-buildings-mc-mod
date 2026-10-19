package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.v1.types.house.HouseType;
import dev.fractalbuildings.core.gen.v1.types.tower.TowerType;
import dev.fractalbuildings.core.gen.v1.types.wall.WallType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The registered building types. Each 4.6 step adds one {@code r.add(...)} line (and its import);
 * when two steps' lines meet in a merge, keep both. Iteration order is by id, whatever the line order.
 */
public final class Types {
	private Types() {}

	private static final List<BuildingType> ALL;

	static {
		List<BuildingType> r = new ArrayList<>();
		r.add(new HouseType());
		r.add(new TowerType());
		r.add(new WallType());
		r.sort(Comparator.comparing(BuildingType::id));
		for (int i = 1; i < r.size(); i++) {
			if (r.get(i).id().equals(r.get(i - 1).id())) throw new IllegalStateException("duplicate type " + r.get(i).id());
		}
		ALL = List.copyOf(r);
	}

	/** All types, sorted by id. */
	public static List<BuildingType> all() { return ALL; }

	public static List<String> ids() { return ALL.stream().map(BuildingType::id).toList(); }

	public static BuildingType get(String id) {
		for (BuildingType t : ALL) if (t.id().equals(id)) return t;
		throw new IllegalArgumentException("unknown building type '" + id + "', expected one of " + ids());
	}
}
