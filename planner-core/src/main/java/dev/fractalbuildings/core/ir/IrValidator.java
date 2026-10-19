package dev.fractalbuildings.core.ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structural checks of a {@link SceneDoc}: unique ids, resolving references, boxes inside bounds, contiguous storeys. */
public final class IrValidator {
	public static final String INVARIANT = "ir-valid";

	private IrValidator() {}

	public static List<Violation> validate(SceneDoc d) {
		List<Violation> out = new ArrayList<>();
		Set<String> lotIds = unique(out, null, "lot", d.lots().stream().map(Lot::id).toList());
		unique(out, null, "building", d.buildings().stream().map(PlacedBuilding::id).toList());
		for (PlacedBuilding b : d.buildings()) {
			for (String l : b.lots()) if (!lotIds.contains(l)) bad(out, b.id(), "building references unknown lot " + l, null);
			building(out, b);
		}
		return out;
	}

	private static void building(List<Violation> out, PlacedBuilding pb) {
		String id = pb.id();
		BuildingIr s = pb.shell();
		Set<String> volumes = unique(out, id, "volume", s.volumes().stream().map(Volume::id).toList());
		Set<String> rooms = unique(out, id, "room", s.rooms().stream().map(Room::id).toList());
		unique(out, id, "opening", s.openings().stream().map(Opening::id).toList());

		int expectY = 0;
		for (int i = 0; i < s.storeys().size(); i++) {
			Storey st = s.storeys().get(i);
			if (st.index() != i) bad(out, id, "storey " + i + " has index " + st.index(), null);
			if (i == 0 ? st.y() != 0 : st.y() != expectY) {
				bad(out, id, "storey " + i + " starts at y=" + st.y() + ", expected " + expectY, null);
			}
			expectY = st.y() + st.height();
		}
		if (s.storeys().isEmpty()) bad(out, id, "building has no storeys", null);

		for (Volume v : s.volumes()) {
			if (v.parent() != null && !volumes.contains(v.parent())) bad(out, id, "volume " + v.id() + " has unknown parent " + v.parent(), v.box());
			if (!s.bounds().contains(v.box())) bad(out, id, "volume " + v.id() + " outside bounds", v.box());
		}
		for (Room r : s.rooms()) {
			if (!volumes.contains(r.volume())) bad(out, id, "room " + r.id() + " has unknown volume " + r.volume(), r.parts().get(0));
			if (r.storey() < 0 || r.storey() >= s.storeys().size()) bad(out, id, "room " + r.id() + " has unknown storey " + r.storey(), r.parts().get(0));
			for (Box3 p : r.parts()) if (!s.bounds().contains(p)) bad(out, id, "room " + r.id() + " part outside bounds", p);
		}
		for (Opening o : s.openings()) {
			for (String end : new String[] {o.from(), o.to()}) {
				if (!end.equals(Opening.OUTSIDE) && !rooms.contains(end)) bad(out, id, "opening " + o.id() + " references unknown room " + end, o.box());
			}
			if (!s.bounds().contains(o.box())) bad(out, id, "opening " + o.id() + " outside bounds", o.box());
		}
		for (Roof r : s.roofs()) {
			if (!volumes.contains(r.volume())) bad(out, id, "roof references unknown volume " + r.volume(), null);
		}
		for (Box b : s.boxes()) {
			if (!s.bounds().contains(b.box())) bad(out, id, "shell box (" + b.role().json() + ") outside bounds", b.box());
			if (b.storey() >= s.storeys().size()) bad(out, id, "box has unknown storey " + b.storey(), b.box());
		}
		unique(out, id, "hook", s.hooks().stream().map(Hook::id).toList());
		for (Hook h : s.hooks()) {
			Box3 cell = Box3.of(h.x(), h.y(), h.z(), 1, 1, 1);
			if (!rooms.contains(h.room())) bad(out, id, "hook " + h.id() + " references unknown room " + h.room(), cell);
			if (!s.bounds().contains(cell)) bad(out, id, "hook " + h.id() + " outside bounds", cell);
		}
		for (Box b : pb.siteBoxes()) {
			if (b.storey() >= s.storeys().size()) bad(out, id, "site box has unknown storey " + b.storey(), b.box());
		}
	}

	private static Set<String> unique(List<Violation> out, String building, String what, List<String> ids) {
		Set<String> seen = new HashSet<>(); // det-ok: membership only; never iterated
		for (String i : ids) if (!seen.add(i)) bad(out, building, "duplicate " + what + " id " + i, null);
		return seen;
	}

	private static void bad(List<Violation> out, String building, String msg, Box3 cell) {
		out.add(new Violation(INVARIANT, building, msg, cell == null ? List.of() : List.of(cell)));
	}
}
