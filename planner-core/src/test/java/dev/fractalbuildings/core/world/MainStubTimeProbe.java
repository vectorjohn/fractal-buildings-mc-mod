package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.Cell;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MainStubTimeProbe {
	@Test
	void time() {
		Assumptions.assumeTrue(System.getProperty("fb.timeprobe") != null, "opt-in");
		WorldPlanner w = JoinWorld.pt1573();
		var site = w.siteOf(new Cell(4, -2)).orElseThrow();
		var t = w.grid().tileAt(site.centre().x(), site.centre().z());
		w.source(site).skeleton(site.cells().get(0));
		w.source(site).network();
		long t0 = System.nanoTime();
		w.tile(t.ix(), t.iz());
		long t1 = System.nanoTime();
		w.rawPlan(t.ix(), t.iz());
		long t2 = System.nanoTime();
		w.plan(t.ix(), t.iz());
		long t3 = System.nanoTime();
		w.buildings(t.ix(), t.iz());
		w.ground(t.ix(), t.iz());
		long t4 = System.nanoTime();
		System.out.println("CORE " + dev.fractalbuildings.core.settlement.ExitTrim.inCore(w.source(site), w.rawPlan(t.ix(), t.iz()).plan().district().rect()) + " " + w.rawPlan(t.ix(), t.iz()).plan().district().rect() + " centre " + site.centre() + " tile " + t.minX() + "," + t.minZ());
		System.out.printf("TIMES tile %d rawPlan %d plan(trim) %d build+ground %d ms%n", (t1 - t0) / 1000000, (t2 - t1) / 1000000, (t3 - t2) / 1000000, (t4 - t3) / 1000000);
	}
}
