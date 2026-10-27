package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * METROFILL R1 r4 (architect: squares drifting onto lanes): a square at an arterial crossing whose corner does not grade may only slide
 * along one of its arterials, about a lot depth, keeping a side on it; otherwise it is dropped. Over the nine gate windows (real terrain,
 * where squares do move) every crossing square that moved still touches a drawn arterial.
 */
class CrossingSquareTest {
	static final Pattern MOVED = Pattern.compile("^(\\S+): plaza square moved from (-?\\d+),(-?\\d+) (\\d+)x(\\d+) to (-?\\d+),(-?\\d+) (\\d+)x(\\d+)(.*)$");

	@Test
	void aCrossingSquareThatMovesKeepsToItsArterial() throws Exception {
		int slid = 0, crossingMoves = 0;
		for (CityRoadsTest.Planned p : CityRoadsTest.gates()) {
			if (!p.src().site().tier().atLeast(Tier.CITY)) continue;
			int in = p.src().density().arterials().width() / 2 + 1;
			for (String note : p.city().notes()) {
				Matcher m = MOVED.matcher(note);
				if (!m.matches()) continue;
				DistrictPlan dp = p.city().districts().get(m.group(1));
				assertNotNull(dp, note);
				Rect t = dp.district().rect(), from = new Rect(i(m, 2), i(m, 3), i(m, 4), i(m, 5)), to = new Rect(i(m, 6), i(m, 7), i(m, 8), i(m, 9));
				boolean crossing = (from.x() == t.x() + in || from.x() + from.w() == t.x() + t.w() - in + 1) && (from.z() == t.z() + in || from.z() + from.d() == t.z() + t.d() - in + 1);
				if (!crossing) continue;
				crossingMoves++;
				assertTrue(m.group(10).contains("along its arterial"), p.name() + " " + note);
				assertTrue(Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) <= Districts.CROSS_SLIDE + 8 * 3 * 2, p.name() + " moved too far: " + note);
				boolean touch = false;
				for (Street s : dp.streets()) {
					if (s.kind() != RoadKind.ARTERIAL) continue;
					for (Rect r : SettlementChecks.footprint(s)) if (Rect.overlap(to.x() - 1, to.z() - 1, to.w() + 2, to.d() + 2, r.x(), r.z(), r.w(), r.d())) touch = true;
				}
				assertTrue(touch, p.name() + ": the square no longer touches an arterial: " + note);
				slid++;
			}
		}
		assertTrue(slid >= 5, slid + " crossing squares slid along their arterial (" + crossingMoves + " moved)");
	}

	static int i(Matcher m, int g) { return Integer.parseInt(m.group(g)); }
}
