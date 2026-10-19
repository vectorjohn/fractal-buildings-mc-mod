package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps.Align;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each massing op over 10,000 drawn envelopes and parameters: null, or a valid massing that keeps the op's promise. */
class MassingOpsTest {
	static final int RUNS = 10_000;

	@Test
	void wingKeepsItsStrip() {
		Draws r = new Draws(1);
		int made = 0;
		for (int i = 0; i < RUNS; i++) {
			int ew = r.range(10, 40), ed = r.range(10, 30);
			Mass main = MassingOps.main(ew, ed, r.range(5, 20), r.range(5, 14), r.range(0, 20), r.chance(1, 2), 2, r);
			if (main == null) continue;
			int side = new int[] {MassingOps.E, MassingOps.S, MassingOps.W}[r.below(3)];
			Mass w = MassingOps.wing("wing1", main, side, Align.values()[r.below(3)], r.range(3, 16), r.range(3, 16), r.range(1, 3), ew, ed);
			if (w == null) continue;
			made++;
			int[] st = Massing.strip(main, w);
			assertNotNull(st, "wing shares a strip " + main + " " + w);
			assertTrue(Math.max(st[2], st[3]) >= 3, "strip >= 3");
			assertTrue(Math.max(st[2], st[3]) <= (side == MassingOps.S ? main.sx() : main.sz()) - 2, "wing narrower than its side");
			assertTrue(w.x() >= 0 && w.z() >= 0 && w.maxX() <= ew && w.maxZ() <= ed);
			int top = Math.max(2, w.to());
			Massing m = new Massing(StoreyGrid.of(top, 4, 4), List.of(main, w), List.of());
			// the pair alone has no core guarantee; only the geometry rules M2, M5, M6 are the op's promise
			for (String p : MassingCheck.check(m, ew, ed, 384)) assertTrue(p.startsWith("M7") || p.startsWith("M8") || p.startsWith("M9"), p);
		}
		assertTrue(made > RUNS / 20, "wings do get made: " + made);
	}

	@Test
	void jettyOverhangsWithinTheStyleMax() {
		Draws r = new Draws(2);
		int made = 0;
		for (int i = 0; i < RUNS; i++) {
			int ew = r.range(8, 30), ed = r.range(8, 30);
			Mass p = Mass.ground("main0", "main", r.range(0, 6), r.range(0, 6), r.range(5, 16), r.range(5, 12), 1);
			int max = r.range(1, 3);
			int[] over = {r.range(0, 3), r.range(0, 3), r.range(0, 3), r.range(0, 3)};
			Mass j = MassingOps.jetty("jetty1", p, over, r.range(2, 3), max, ew, ed);
			if (j == null) continue;
			made++;
			int total = 0;
			int[] got = {p.z() - j.z(), j.maxX() - p.maxX(), j.maxZ() - p.maxZ(), p.x() - j.x()};
			for (int k = 0; k < 4; k++) {
				assertEquals(over[k], got[k]);
				assertTrue(got[k] >= 0 && got[k] <= Math.min(max, MassingCheck.JETTY_MAX), "overhang " + got[k] + " max " + max);
				total += got[k];
			}
			assertTrue(total >= 1, "at least one side overhangs");
			assertEquals(p.id(), j.parent());
			assertEquals(p.to(), j.from());
			Massing m = new Massing(StoreyGrid.of(j.to(), 4, 4), List.of(p, j), List.of());
			for (String s : MassingCheck.check(m, ew, ed, 384)) assertTrue(s.startsWith("M3") || s.startsWith("M7"), s);
		}
		assertTrue(made > RUNS / 20, "jetties do get made: " + made);
	}

	@Test
	void setbackChildIsInsideItsParent() {
		Draws r = new Draws(3);
		int made = 0;
		for (int i = 0; i < RUNS; i++) {
			Mass p = Mass.ground("main0", "main", r.range(0, 6), r.range(0, 6), r.range(5, 16), r.range(5, 12), 1);
			int[] in = {r.range(0, 3), r.range(0, 3), r.range(0, 3), r.range(0, 3)};
			Mass s = MassingOps.setback("setback1", p, in, 2);
			if (s == null) continue;
			made++;
			assertTrue(s.x() >= p.x() && s.z() >= p.z() && s.maxX() <= p.maxX() && s.maxZ() <= p.maxZ());
			assertTrue(s.sx() < p.sx() || s.sz() < p.sz(), "strictly smaller");
			assertTrue(s.sx() >= MassingOps.MIN_ROOM && s.sz() >= MassingOps.MIN_ROOM);
			Massing m = new Massing(StoreyGrid.of(2, 4, 4), List.of(p, s), List.of());
			for (String x : MassingCheck.check(m, 40, 40, 384)) assertTrue(x.startsWith("M7") || x.startsWith("M8"), x);
		}
		assertTrue(made > RUNS / 10, "setbacks do get made: " + made);
	}

	@Test
	void stackShrinksBandByBand() {
		Draws r = new Draws(4);
		int made = 0;
		for (int i = 0; i < RUNS; i++) {
			int a = r.range(1, 3), b = r.range(2, 8), c = r.range(1, 3);
			Mass pod = Mass.ground("podium0", "podium", 2, 2, r.range(8, 24), r.range(8, 24), a);
			List<Mass> st = MassingOps.stack(pod, new int[] {a, b, c}, new int[] {r.range(0, 3), r.range(0, 3)});
			if (st == null) continue;
			made++;
			assertEquals(a + b + c, st.get(st.size() - 1).to());
			for (int k = 1; k < st.size(); k++) {
				Mass m = st.get(k), p = st.get(k - 1);
				assertEquals(p.id(), m.parent());
				assertEquals(p.to(), m.from());
				assertTrue(m.x() > p.x() && m.maxX() < p.maxX() && m.z() > p.z() && m.maxZ() < p.maxZ(), "strictly inside");
			}
		}
		assertTrue(made > RUNS / 4, "stacks do get made: " + made);
	}

	@Test
	void coresFitInsideEveryHost() {
		Draws r = new Draws(5);
		int made = 0;
		for (int i = 0; i < RUNS; i++) {
			int n = r.range(2, 5);
			StoreyGrid g = StoreyGrid.of(n, r.range(4, 6), 4);
			Mass a = Mass.ground("main0", "main", 2, 2, r.range(6, 18), r.range(6, 14), 1);
			List<Mass> hosts = new ArrayList<>(List.of(a));
			Mass b = MassingOps.setback("setback1", a, new int[] {r.range(0, 2), r.range(0, 2), r.range(0, 2), r.range(0, 2)}, n);
			if (b != null) hosts.add(b);
			else hosts.set(0, Mass.ground("main0", "main", a.x(), a.z(), a.sx(), a.sz(), n));
			Core k = MassingOps.core(g, hosts, List.of(), 0, n, r);
			if (k == null) continue;
			made++;
			Massing m = new Massing(g, hosts, List.of(k));
			assertEquals(List.of(), MassingCheck.check(m, 40, 40, 384), "core " + k);
			assertTrue(k.z() > a.z() + 1, "off the front inside row");
		}
		assertTrue(made > RUNS / 4, "cores do get made: " + made);
	}
}
