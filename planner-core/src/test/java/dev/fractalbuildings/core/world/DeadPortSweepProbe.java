package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.settlement.Cell;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Throwaway classification of link ends on the whole step-64 worlds (opt-in {@code -Dfb.deadport=1}; before and after counts of 6.22 DEADPORT). */
class DeadPortSweepProbe {
	@Test
	void sweep() {
		Assumptions.assumeTrue(System.getProperty("fb.deadport") != null, "opt-in");
		run("w12345", WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT));
		run("w987654321", new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, WorldPlanner.Caches.DEFAULT));
	}

	static void run(String label, WorldPlanner w) {
		int ports = 0, alt = 0, dead = 0, dropped = 0, unserved = 0;
		long t0 = System.nanoTime();
		for (int cz = -12; cz <= 11; cz++) for (int cx = -12; cx <= 11; cx++) {
			try {
				var so = w.roads().settlement(new CellKey(cx, cz));
				var site = w.siteOf(new Cell(cx, cz));
				if (so.isEmpty() || site.isEmpty()) continue;
				var raw = w.approachRoads(site.get(), so.get());
				var dp = w.deadEndPorts(site.get());
				var dr = w.droppedPorts(site.get());
				for (var p : w.roads().portsOf(so.get())) {
					ports++;
					var a = raw.stream().filter(q -> p.equals(q.port())).findFirst();
					String alts = a.isPresent() && a.get().road().id().matches(".*_9[0-9]$") ? " ALT " + a.get().road().id() + " from " + a.get().road().points().get(0) : "";
					if (!alts.isEmpty()) alt++;
					if (a.isEmpty()) unserved++;
					String st = dp.contains(p) ? "DEAD_END" : dr.contains(p) ? "DROPPED" : "LIVE";
					if (dp.contains(p)) dead++;
					if (dr.contains(p)) dropped++;
					if (a.isEmpty() || !alts.isEmpty() || !st.equals("LIVE"))
						System.out.println(label + " " + st + " " + site.get().id() + " " + site.get().tier() + " port " + p.x() + "," + p.z() + " other " + p.other() + " meeting " + p.meeting() + (a.isEmpty() ? " unserved" : " served") + alts);
				}
			} catch (RuntimeException e) {
				// a cell at the edge of the recorded grid (skipped alike before and after)
			}
		}
		System.out.printf("%s SUMMARY ports %d unserved %d alt %d dead-end %d dropped(port ends) %d (%.0f s)%n", label, ports, unserved, alt, dead, dropped, (System.nanoTime() - t0) / 1e9);
	}
}
