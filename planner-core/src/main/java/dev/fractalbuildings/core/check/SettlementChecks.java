package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Plaza;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.settlement.Street;
import java.util.ArrayList;
import java.util.List;

/**
 * Settlement invariants (docs/plans/phase5.md "Settlement invariants"), kept apart from {@link Invariants} so
 * parallel steps do not conflict. Each rule is a method returning violations (empty = holds) with a stable id; steps
 * add their rules here. Violation {@code building} is null; the message names the lots.
 */
public final class SettlementChecks {
	private SettlementChecks() {}

	public static final String LOT_IN_TILE = "lot-in-tile";
	public static final String LOTS_DISJOINT = "lots-disjoint";

	/** Every rule that needs only a district plan and the landmarks inside it. */
	public static List<Violation> district(DistrictPlan d, List<Landmark> landmarks) {
		List<Violation> out = new ArrayList<>(lotInTile(d));
		out.addAll(lotsDisjoint(d, landmarks));
		return out;
	}

	/** {@code lot-in-tile}: every lot lies inside its district tile. */
	public static List<Violation> lotInTile(DistrictPlan d) {
		List<Violation> out = new ArrayList<>();
		Rect tile = d.district().rect();
		for (LotBrief l : d.lots()) {
			if (!tile.containsRect(l.x(), l.z(), l.w(), l.d())) {
				out.add(v(LOT_IN_TILE, "lot " + l.id() + " lies outside tile " + d.district().id(), l.x(), l.z(), l.w(), l.d()));
			}
		}
		return out;
	}

	/** {@code lots-disjoint}: lots overlap no other lot, street, plaza or landmark. */
	public static List<Violation> lotsDisjoint(DistrictPlan d, List<Landmark> landmarks) {
		List<Violation> out = new ArrayList<>();
		List<LotBrief> lots = d.lots();
		for (int i = 0; i < lots.size(); i++) {
			LotBrief a = lots.get(i);
			for (int j = i + 1; j < lots.size(); j++) {
				LotBrief b = lots.get(j);
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), b.x(), b.z(), b.w(), b.d())) {
					out.add(v(LOTS_DISJOINT, "lots " + a.id() + " and " + b.id() + " overlap", a.x(), a.z(), a.w(), a.d()));
				}
			}
			for (Street s : d.streets()) {
				for (Rect r : footprint(s)) {
					if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), r.x(), r.z(), r.w(), r.d())) {
						out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps street " + s.id(), a.x(), a.z(), a.w(), a.d()));
						break;
					}
				}
			}
			for (Plaza p : d.plazas()) {
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), p.x(), p.z(), p.w(), p.d())) {
					out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps plaza " + p.id(), a.x(), a.z(), a.w(), a.d()));
				}
			}
			for (Landmark m : landmarks) {
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), m.x(), m.z(), m.w(), m.d())) {
					out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps landmark " + m.id(), a.x(), a.z(), a.w(), a.d()));
				}
			}
		}
		return out;
	}

	/**
	 * The columns a street covers, as rectangles: for each point, a {@code width} square centred on it (columns
	 * {@code p - width/2 .. p - width/2 + width - 1}), merged per axis-aligned segment; diagonal segments contribute
	 * one square per step.
	 */
	static List<Rect> footprint(Street s) {
		List<Rect> out = new ArrayList<>();
		int w = s.width(), h = w / 2;
		List<Pt> ps = s.points();
		for (int i = 1; i < ps.size(); i++) {
			Pt a = ps.get(i - 1), b = ps.get(i);
			if (a.x() == b.x() || a.z() == b.z()) {
				int x0 = Math.min(a.x(), b.x()) - h, z0 = Math.min(a.z(), b.z()) - h;
				out.add(new Rect(x0, z0, Math.abs(b.x() - a.x()) + w, Math.abs(b.z() - a.z()) + w));
			} else {
				int n = Math.abs(b.x() - a.x()), sx = Integer.signum(b.x() - a.x()), sz = Integer.signum(b.z() - a.z());
				for (int k = 0; k <= n; k++) out.add(new Rect(a.x() + sx * k - h, a.z() + sz * k - h, w, w));
			}
		}
		return out;
	}

	private static Violation v(String id, String msg, int x, int z, int w, int d) {
		return new Violation(id, null, msg, List.of(new Box3(x, 0, z, w, 1, d)));
	}
}
