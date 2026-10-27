package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Gate;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import dev.fractalbuildings.core.settlement.Street;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

/**
 * The {@code gate-cut} metric (6.15 ROADJOIN, architect 2026-10-27): how far an EXIT gate's street surface lies below (cut) or above (fill)
 * the natural ground at the gate. Over {@link #LIMIT} blocks the street end breaks the cap on its own (DS-46 extended to gates); the fix
 * is district grading (7.19 FALLSTREETS), so this only counts and reports. Pure: gates, street levels and the exact probe.
 */
public final class GateCut {
	private GateCut() {}

	/** Largest cut or fill at a gate that is still a plain sunken lane or bank (terraced to 4, ADR 0004 TC). */
	public static final int LIMIT = 4;

	/** @param delta ground surface minus street level: positive is a cut, negative a fill */
	public record Row(String gate, int x, int z, int street, int ground, int delta) {
		public boolean over() { return Math.abs(delta) > LIMIT; }
		@Override public String toString() { return gate + " at " + x + "," + z + (delta >= 0 ? " cut " : " fill ") + Math.abs(delta) + " (street " + street + ", ground " + ground + ")"; }
	}

	/** Street level at a column from the first street whose centre line passes within {@code width / 2 + 1 + extra}, nearest first. */
	static OptionalInt level(Collection<DistrictPlan> districts, int x, int z, int extra) {
		int bestD = Integer.MAX_VALUE, bestY = 0;
		for (DistrictPlan d : districts) {
			for (Street s : d.streets()) {
				int[] hit = s.nearest(x, z);
				if (hit[0] <= s.width() / 2 + 1 + extra && hit[0] < bestD) { bestD = hit[0]; bestY = hit[1]; }
			}
		}
		return bestD == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(bestY);
	}

	/** One row per EXIT gate that has a street at it. */
	public static List<Row> measure(SettlementPlan plan, Collection<DistrictPlan> districts, TerrainProbe exact) {
		List<Row> out = new ArrayList<>();
		for (Gate g : plan.gates()) {
			if (g.kind() != GateKind.EXIT) continue;
			OptionalInt lv = level(districts, g.x(), g.z(), 6);
			if (lv.isEmpty()) continue;
			int ground = exact.surfaceY(g.x(), g.z());
			out.add(new Row(g.id(), g.x(), g.z(), lv.getAsInt(), ground, ground - lv.getAsInt()));
		}
		return out;
	}
}
