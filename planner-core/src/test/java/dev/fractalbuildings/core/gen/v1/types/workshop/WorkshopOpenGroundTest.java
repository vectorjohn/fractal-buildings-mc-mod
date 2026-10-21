package dev.fractalbuildings.core.gen.v1.types.workshop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** 5.12: workshops sometimes have a covered work yard: a canopy on posts in front of the hall. */
class WorkshopOpenGroundTest {
	static boolean porch(BuildingIr b) { return b.volumes().stream().anyMatch(v -> v.id().startsWith("post")); }

	/** Problems with the porch of one workshop (none if it has no porch). */
	static List<String> problems(BuildingIr b) {
		List<String> p = new ArrayList<>();
		if (!porch(b)) return p;
		Volume hall = b.volumes().get(0);
		int a = hall.box().z();
		if (a < 2 || a > 3) p.add("porch depth " + a);
		Opening door = null;
		for (Opening o : b.openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) door = o;
		if (door == null) return List.of("no entrance");
		List<Volume> posts = b.volumes().stream().filter(v -> v.id().startsWith("post")).toList();
		if (posts.size() < 2) p.add(posts.size() + " posts");
		for (Volume v : posts) {
			if (v.box().z() != 0) p.add(v.id() + " is not on the front edge");
			if (v.box().x() >= door.box().x() - 1 && v.box().x() <= door.box().maxX()) p.add(v.id() + " stands in front of the cart door");
		}
		for (int z = 0; z < a; z++) {
			final int zz = z;
			boolean covered = b.boxes().stream().anyMatch(x -> x.role() == Role.ROOF && x.box().z() == zz && x.box().sx() == hall.box().sx());
			if (!covered) p.add("no canopy over row " + z);
		}
		return p;
	}

	@Test
	void porchesFollowTheirRulesAndAreAFairShare() {
		int n = 0, porches = 0;
		for (String style : WorkshopTypeTest.STYLES)
			for (int[] e : WorkshopTypeTest.ENVELOPES)
				for (long seed = 0; seed < 512; seed++) {
					BuildingIr b = WorkshopTypeTest.workshop(style, seed, e[0], e[1]);
					List<String> p = problems(b);
					assertTrue(p.isEmpty(), style + " seed " + seed + " " + e[0] + "x" + e[1] + ": " + p);
					n++;
					if (porch(b)) porches++;
				}
		System.out.println(String.format(Locale.ROOT, "workshop porches: %d of %d", porches, n));
		assertTrue(porches >= 0.2 * n && porches <= 0.6 * n, "porches " + porches + "/" + n);
	}

	@Test
	void theChecksCanFail() {
		BuildingIr b = null;
		for (long seed = 0; b == null && seed < 200; seed++) {
			BuildingIr c = WorkshopTypeTest.workshop("medieval", seed, 18, 13);
			if (porch(c)) b = c;
		}
		assertTrue(b != null && problems(b).isEmpty());
		List<Box> boxes = new ArrayList<>(b.boxes());
		final int hallW = b.volumes().get(0).box().sx();
		boxes.removeIf(x -> x.role() == Role.ROOF && x.box().z() == 0 && x.box().sx() == hallW);
		assertTrue(!problems(new BuildingIr(b.spec(), b.bounds(), b.storeys(), b.volumes(), b.rooms(), b.openings(), b.roofs(), boxes, b.hooks())).isEmpty());
	}
}
