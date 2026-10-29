package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MainStubProbe {
	@Test
	void road() {
		String prop = System.getProperty("fb.mainstubroad");
		Assumptions.assumeTrue(prop != null, "opt-in");
		String[] wc = System.getProperty("fb.mainstubworld", "pt,4,-2").split(",");
		WorldPlanner w = wc[0].equals("pt") ? JoinWorld.pt1573() : wc[0].equals("w987") ? new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, WorldPlanner.Caches.DEFAULT) : WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		SettlementSite site = w.siteOf(new Cell(Integer.parseInt(wc[1]), Integer.parseInt(wc[2]))).orElseThrow();
		var src = w.source(site);
		for (Cell c : site.cells()) for (Road r : src.skeleton(c).roads()) if (r.id().equals(prop)) {
			System.out.println("ROAD " + r.id() + " w" + r.width() + " pts " + r.points().stream().map(p -> p.x() + "," + p.z()).toList() + " bridges " + r.bridges().stream().map(java.util.Arrays::toString).toList());
			var cols = ExitTrim.columns(r);
			int n = cols.size();
			for (int i = n - 1; i >= Math.max(0, n - 400); i -= 12) {
				int x = cols.get(i)[0], z = cols.get(i)[1];
				var t = w.grid().tileAt(x, z);
				var ti = w.tile(t.ix(), t.iz());
				StringBuilder sb = new StringBuilder("col " + i + " " + x + "," + z + " tile " + t.ix() + "," + t.iz() + " planned " + ti.isPresent());
				if (ti.isPresent()) {
					var p = w.rawPlan(t.ix(), t.iz()).plan();
					for (var l : p.lots()) if (x >= l.x() - 12 && x < l.x() + l.w() + 12 && z >= l.z() - 12 && z < l.z() + l.d() + 12) sb.append(" [").append(l.id()).append(" ").append(l.type()).append(" ").append(l.status()).append(" front ").append(l.front()).append(" ").append(l.w()).append("x").append(l.d()).append("]");
					for (var s : p.streets()) if (!s.id().startsWith(r.id()) && s.nearest(x, z)[0] <= 8) sb.append(" ST ").append(s.id()).append(" d").append(s.nearest(x, z)[0]).append(" w").append(s.width());
					for (var s : p.plazas()) if (x >= s.x() - 12 && x < s.x() + s.w() + 12 && z >= s.z() - 12 && z < s.z() + s.d() + 12) sb.append(" PLAZA ").append(s.id());
				}
				System.out.println(sb);
			}
		}
	}
}
