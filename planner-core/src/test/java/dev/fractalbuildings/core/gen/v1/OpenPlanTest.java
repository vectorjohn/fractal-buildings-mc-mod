package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Room;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 gate (open plan): kitchens open onto living areas in modern styles and stay walled off
 * mostly in medieval ones; bedrooms keep doors. Print the per-style table with {@code -Dfb.openplan=N}.
 */
class OpenPlanTest {
	/** {kitchen links, open kitchen links, living-entry-hall links, open ones, bedroom links, open bedroom links, wide open links}. */
	static long[] share(String style, int n) {
		long[] r = new long[7];
		for (BuildingIr b : CirculationTest.houses(style, n)) {
			Map<String, String> use = new TreeMap<>();
			for (Room x : b.rooms()) use.put(x.id(), x.use());
			for (Opening o : b.openings()) {
				if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
				String a = use.get(o.from()), c = use.get(o.to());
				if (a == null || c == null) continue;
				boolean open = o.kind() == OpeningKind.ARCH, pub = a.equals("living") || a.equals("entry") || a.equals("hall") || c.equals("living") || c.equals("entry") || c.equals("hall");
				if ((a.equals("kitchen") || c.equals("kitchen")) && pub) {
					r[0]++;
					if (open) r[1]++;
					if (open && o.box().sx() * o.box().sz() >= 2) r[6]++;
				} else if ((a.equals("living") || a.equals("entry") || a.equals("hall")) && (c.equals("living") || c.equals("entry") || c.equals("hall"))) {
					r[2]++;
					if (open) r[3]++;
				}
				if (a.equals("bedroom") || c.equals("bedroom")) {
					r[4]++;
					if (open) r[5]++;
				}
			}
		}
		return r;
	}

	@Test
	void kitchenIsOpenToTheLivingAreaInModernStylesAndMostlyWalledOffInMedieval() {
		// 1500 houses per style, before (every public pair 40 %, 1-3 wide) / after: see docs/log/2026-10-21-p5-openplan.md
		long[] plain = share("plain", 400), medieval = share("medieval", 400);
		double p = plain[1] * 1.0 / plain[0], m = medieval[1] * 1.0 / medieval[0];
		assertTrue(p >= 0.6, "plain kitchen links open: " + p);
		assertTrue(m <= 0.35, "medieval kitchen links open: " + m);
		assertTrue(plain[6] * 2 >= plain[1], "most open plain kitchen links are 2 wide or more: " + plain[6] + " of " + plain[1]);
		assertTrue(plain[5] == 0 && medieval[5] == 0, "bedrooms keep doors");
	}

	@Test
	void report() {
		int n = Integer.getInteger("fb.openplan", 0);
		if (n == 0) return;
		for (String style : List.of("plain", "medieval", "plains", "desert", "snowy", "taiga", "jungle")) {
			long[] r = share(style, n);
			System.out.printf("OPEN %s kitchen-links=%d open=%.1f%% wide=%.1f%% public-pairs=%d open=%.1f%% bedroom-links=%d open=%d%n", style, r[0], 100.0 * r[1] / r[0], 100.0 * r[6] / r[0], r[2], 100.0 * r[3] / r[2], r[4], r[5]);
		}
	}
}
