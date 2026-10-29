package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.style.StyleField;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MainStubDetachProbe {
	@Test
	void probe() {
		Assumptions.assumeTrue(System.getProperty("fb.detach") != null, "opt-in");
		var p = CityProgramTest.cities().get(2);
		var s = p.setup();
		SkeletonSource src = s.source();
		StyleField styles = new StyleField(s.p().biome(), s.seed());
		java.util.Map<String, DistrictPlan> raw = new java.util.TreeMap<>();
		for (var d : p.city().plan().districts()) CityPlanner.planTile(src, d.ix(), d.iz(), styles, s.p().terrain()).ifPresent(t -> raw.put(d.id(), t.plan()));
		var cuts = ExitTrim.cuts(src, (ix, iz) -> raw.get(Ids.district(ix, iz)));
		for (var c : cuts.values()) System.out.println("CUT " + c.road() + " index " + c.index() + " of " + c.cols().size() + " end " + c.end());
		for (var v : p.plan().violations()) System.out.println("VIOL " + v.invariant() + " " + v.message());
		int x0 = 3092 - 40, x1 = 3118 + 40, z0 = 1164 - 40, z1 = 1174 + 40;
		for (var dp : p.city().districts().values()) for (var st : dp.streets()) {
			boolean near = st.points().stream().anyMatch(q -> q.x() >= x0 && q.x() <= x1 && q.z() >= z0 && q.z() <= z1);
			if (near) System.out.println("STREET " + dp.district().id() + " " + st.id() + " " + st.kind() + " w" + st.width() + " " + st.points().stream().map(q -> q.x() + "," + q.z()).toList());
		}
		for (var dp : raw.values()) for (var st : dp.streets()) {
			boolean near = st.points().stream().anyMatch(q -> q.x() >= x0 && q.x() <= x1 && q.z() >= z0 && q.z() <= z1) && cuts.containsKey(ExitTrim.baseIdOf(st.id()));
			if (near) System.out.println("RAWCUT " + dp.district().id() + " " + st.id() + " " + st.points().stream().map(q -> q.x() + "," + q.z()).toList());
		}
	}
}
