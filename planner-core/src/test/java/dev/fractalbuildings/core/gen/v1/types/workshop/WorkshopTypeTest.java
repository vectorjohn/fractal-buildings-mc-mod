package dev.fractalbuildings.core.gen.v1.types.workshop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Workshop properties (plan T-workshop): a tall open mass with a 2-wide door, holding >= 2 workstation hooks, and a living wing. */
class WorkshopTypeTest {
	static final int SEEDS = 256;
	static final int[][] ENVELOPES = {{12, 11}, {14, 11}, {18, 13}, {22, 17}};
	static final String[] STYLES = {"plain", "medieval"};

	static BuildingIr workshop(String style, long seed, int w, int d) {
		int storeys = 1 + new Draws(seed * 7919 + w).below(2);
		return ShellGeneratorV1.generate(new BuildingSpec(2, seed, style, "workshop", w, d, storeys, "auto", 384, 1));
	}

	/** Problems with one workshop, empty if it has the workshop properties. */
	static List<String> problems(BuildingIr b) { return problems(b, true); }

	static List<String> problems(BuildingIr b, boolean wing) {
		List<String> p = new ArrayList<>();
		Volume hall = b.volumes().get(0);
		if (!hall.id().equals("main0")) p.add("first volume is " + hall.id());
		if (hall.box().sy() < 6) p.add("hall is only " + hall.box().sy() + " tall"); // 5 high storey + top slab
		Opening door = null;
		for (Opening o : b.openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to()) && o.facing() == dev.fractalbuildings.core.ir.Facing.NORTH && (door == null || o.box().sx() > door.box().sx())) door = o; // the main entrance faces the front (DS35DOORS adds secondary doors on other faces)
		if (door == null) return append(p, "no entrance");
		if (door.box().sx() != 2) p.add("cart door is " + door.box().sx() + " wide");
		if (door.box().z() != hall.box().z()) p.add("door is not on the hall's front");
		int centre = hall.box().x() + hall.box().sx() / 2;
		if (Math.abs(door.box().x() + 1 - centre) > 1) p.add("door at x " + door.box().x() + " is off the hall's centre " + centre);
		Room open = null;
		for (Room r : b.rooms()) if (r.use().equals("workshop")) open = r;
		if (open == null) return append(p, "no workshop room");
		if (!open.volume().equals("main0")) p.add("workshop room is in " + open.volume());
		if (b.rooms().stream().filter(r -> r.volume().equals("main0")).count() != 1) p.add("hall is split into several rooms");
		long stations = 0;
		for (Hook h : b.hooks()) if (h.kind().equals("workstation") && h.room().equals(open.id())) stations++;
		if (stations < 2) p.add("only " + stations + " workstation hooks in the hall");
		if (wing && b.volumes().size() < 2) p.add("no living wing");
		return p;
	}

	private static List<String> append(List<String> p, String s) {
		p.add(s);
		return p;
	}

	@Test
	void everyWorkshopHasAWideDoorAndWorkstationsInAnOpenHall() {
		for (String style : STYLES)
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS; seed++) {
					List<String> p = problems(workshop(style, seed, e[0], e[1]));
					assertTrue(p.isEmpty(), style + " seed " + seed + " " + e[0] + "x" + e[1] + ": " + p);
				}
	}

	@Test
	void theChecksCanFail() {
		BuildingIr b = workshop("medieval", 1, 18, 13);
		assertTrue(problems(b).isEmpty(), problems(b).toString());
		// mutant: a 1-wide door
		List<Opening> ops = new ArrayList<>();
		for (Opening o : b.openings()) {
			boolean ext = o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to());
			ops.add(ext ? new Opening(o.id(), o.kind(), Box3.of(o.box().x(), o.box().y(), o.box().z(), 1, o.box().sy(), o.box().sz()), o.facing(), o.from(), o.to()) : o);
		}
		assertFalse(problems(rebuild(b, ops, b.hooks())).isEmpty(), "narrow door");
		// mutant: no workstations
		List<Hook> hooks = new ArrayList<>(b.hooks());
		hooks.removeIf(h -> h.kind().equals("workstation"));
		assertFalse(problems(rebuild(b, b.openings(), hooks)).isEmpty(), "no workstation hooks");
	}

	private static BuildingIr rebuild(BuildingIr b, List<Opening> ops, List<Hook> hooks) {
		return new BuildingIr(b.spec(), b.bounds(), b.storeys(), b.volumes(), b.rooms(), ops, b.roofs(), b.boxes(), hooks);
	}

	@Test
	void theSmallestLotStillGetsTheWorkHall() {
		for (String style : STYLES)
			for (long seed = 0; seed < SEEDS; seed++) {
				List<String> p = problems(workshop(style, seed, 10, 9), false);
				assertTrue(p.isEmpty(), style + " seed " + seed + " 10x9: " + p);
			}
	}

	@Test
	void diversity() {
		int n = 0, behind = 0, beside = 0, leanTo = 0, twoStorey = 0;
		Set<Integer> hallHeights = new TreeSet<>(), stations = new TreeSet<>();
		for (long seed = 0; seed < SEEDS; seed++)
			for (int[] e : ENVELOPES) {
				BuildingIr b = workshop("medieval", seed, e[0], e[1]);
				n++;
				Volume hall = b.volumes().get(0);
				for (Volume v : b.volumes()) {
					if (v.id().startsWith("wing")) {
						if (v.box().x() > hall.box().x() - 1 && v.box().maxX() < hall.box().maxX() + 1) behind++;
						else beside++;
					}
					if (v.id().startsWith("lean")) leanTo++;
				}
				if (b.storeys().size() >= 2) twoStorey++;
				hallHeights.add(b.storeys().get(0).height());
				stations.add((int) b.hooks().stream().filter(h -> h.kind().equals("workstation")).count());
			}
		System.out.println(String.format(Locale.ROOT, "workshop diversity (medieval, %d): wing behind %d, wing beside %d, lean-to %d, two storeys %d, hall heights %s, workstations %s",
				n, behind, beside, leanTo, twoStorey, hallHeights, stations));
		assertTrue(behind >= 0.2 * n, "wings behind " + behind + "/" + n);
		assertTrue(beside >= 0.2 * n, "wings beside " + beside + "/" + n);
		assertTrue(leanTo >= 0.06 * n, "lean-tos " + leanTo + "/" + n);
		assertTrue(twoStorey >= 0.1 * n, "two storeys " + twoStorey + "/" + n);
		assertTrue(hallHeights.size() >= 2, "hall heights " + hallHeights);
		assertTrue(stations.size() >= 2, "workstation counts " + stations);
	}
}
