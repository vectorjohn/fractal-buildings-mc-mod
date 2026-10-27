package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.DoorChecks;
import dev.fractalbuildings.core.check.DoorRise;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DS-45 "Stair runs" (SHOPLOW S2): no exterior flight has more than 3 risers in a straight run where the lot has room for landings; the search falls back to the old shortest flight where it has not. */
class FlightRunsTest {
	/** {doors measured, doors with a run over 3} for types that still climb (towers, temples and halls keep the old slab rule). */
	private static int[] count(String[] types, String[] slopes) {
		int doors = 0, over = 0;
		for (String type : types)
			for (String slope : slopes)
				for (long seed = 1; seed <= 24; seed++) {
					ProbeSpecs.Probes p = ProbeSpecs.parse(slope);
					GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, 18, 18, Facing.NORTH), type, "medieval", p.terrain(), p.biome());
					SceneDoc s;
					try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
					assertEquals(List.of(), Invariants.check(s, rq.terrain()), type + " " + slope + " seed " + seed);
					for (DoorRise.Door d : DoorChecks.doors(s, s.buildings().get(0), rq.terrain())) {
						if (!d.reached() || d.rise() < 2) continue;
						doors++;
						if (d.run() > DoorChecks.MAX_RISERS) over++;
					}
				}
		return new int[] {doors, over};
	}

	@Test
	void flightsBreakAtLandingsWhereTheLotAllows() {
		String[] types = {"temple", "tower", "hall"};
		String[] slopes = {"plane:70:1:1:12", "plane:70:1:0:6", "plane:70:0:1:6", "plane:70:0:-1:8", "plane:70:0:1:12"};
		int[] now = count(types, slopes);
		SiteLevel.LEGACY.set(true);
		int[] old;
		try { old = count(types, slopes); } finally { SiteLevel.LEGACY.set(false); }
		System.out.println("flight runs: " + now[1] + " of " + now[0] + " doors with a straight run over 3 risers (old search " + old[1] + " of " + old[0] + ")");
		assertTrue(old[1] >= 15, "the control: the old search leaves long runs on these slopes (" + old[1] + ")");
		assertTrue(now[1] * 4 <= old[1], "the stair-run search removes most long runs: " + now[1] + " against " + old[1]);
	}
}
