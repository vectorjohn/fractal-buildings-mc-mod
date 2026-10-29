package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Plaza;
import dev.fractalbuildings.core.settlement.Street;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What the planner says is at a world position, as an ordered map for the playtest note sidecar ({@link PlaytestNote}, FBNOTE): the
 * settlement and its tier, the district tile, the lot, the building (id, type, style, seed), the room and the street or plaza the position
 * is on. Read only (the tile may be planned, a few hundred ms the first time); call it on the server thread in the mod.
 */
public final class PlanLookup {
	private PlanLookup() {}

	/** A street counts as "under" a position within this many blocks of its centre line beyond half its width. */
	static final int STREET_SLACK = 1;

	/**
	 * @param x block x
	 * @param y block y of the player's feet (rooms are matched on it)
	 * @param z block z
	 * @return {@code {"settlement": null}} outside any settlement tile; otherwise the keys named above (absent when nothing matches)
	 */
	public static Map<String, Object> at(WorldPlanner w, int x, int y, int z) {
		Map<String, Object> out = new LinkedHashMap<>();
		DistrictGrid.Tile t = w.tileAt(x, z);
		Optional<TileInfo> info = w.tile(t.ix(), t.iz());
		if (info.isEmpty()) {
			out.put("settlement", null);
			return out;
		}
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("id", info.get().settlementId());
		s.put("tier", info.get().tier().json);
		out.put("settlement", s);
		out.put("tile", mapOf("ix", t.ix(), "iz", t.iz()));
		TilePlan plan = w.plan(t.ix(), t.iz());
		out.put("district", plan.plan().district().id());
		for (LotBrief l : plan.plan().lots()) {
			if (x >= l.x() && x < l.x() + l.w() && z >= l.z() && z < l.z() + l.d()) {
				out.put("lot", mapOf("id", l.id(), "type", l.type(), "style", l.style(), "status", l.status()));
				break;
			}
		}
		for (BuildingPiece b : w.buildings(t.ix(), t.iz())) {
			if (!b.brief().rect().contains(x, z)) continue;
			BuildingSpec spec = b.building().shell().spec();
			Map<String, Object> bm = mapOf("id", b.building().id(), "lot", b.brief().id(), "type", spec.type(), "style", spec.style(),
					"seed", spec.specSeed(), "storeys", spec.storeys());
			out.put("building", bm);
			Placement pl = b.building().placement();
			for (Room r : b.building().shell().rooms()) {
				for (Box3 part : r.parts()) {
					if (pl.toWorld(part).contains(x, y, z)) {
						out.put("room", mapOf("id", r.id(), "use", r.use(), "storey", r.storey(), "volume", r.volume()));
						break;
					}
				}
				if (out.containsKey("room")) break;
			}
			break;
		}
		Street bestStreet = null;
		int bestD = Integer.MAX_VALUE;
		for (Street st : plan.plan().streets()) {
			int d = st.nearest(x, z)[0];
			if (d <= st.width() / 2 + STREET_SLACK && d < bestD) {
				bestD = d;
				bestStreet = st;
			}
		}
		if (bestStreet != null) out.put("street", mapOf("id", bestStreet.id(), "kind", bestStreet.kind().json, "width", bestStreet.width(), "distance", bestD));
		for (Plaza p : plan.plan().plazas()) {
			if (x >= p.x() && x < p.x() + p.w() && z >= p.z() && z < p.z() + p.d()) {
				out.put("plaza", mapOf("id", p.id(), "kind", p.kind()));
				break;
			}
		}
		return out;
	}

	static Map<String, Object> mapOf(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
		return m;
	}
}
