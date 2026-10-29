package dev.fractalbuildings.core.gen.v1.types.hall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.metrics.Diversity;
import dev.fractalbuildings.core.metrics.DiversityReport;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Hall / civic properties (plan step T-hall): sub-kind mix, open spans, footprints, big-lot cost. */
class HallTypeTest {
	static final int SEEDS = 256;
	static final HallType TYPE = new HallType();
	static final int[][] ENVELOPES = {{26, 26}, {52, 40}, {86, 60}, {116, 76}};

	static BuildingSpec spec(long seed, String style, int w, int d, int storeys, int maxHeight) {
		return new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "hall", w, d, storeys, "auto", maxHeight, 1);
	}

	static Massing mass(BuildingSpec sp) {
		Massing m = TYPE.mass(new BuildContext(sp, TYPE, StyleLibrary.get(sp.style())));
		List<String> p = MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight());
		assertTrue(p.isEmpty(), sp + ": " + p);
		return m;
	}

	static Massing mass(long seed, int w, int d) { return mass(spec(seed, "medieval", w, d, 1 + (int) (seed % 3), 200)); }

	@Test
	void everySubKindAppearsOnRoomyLotsAndRarelyFallsBack() {
		Map<HallMassing.Kind, Integer> n = new EnumMap<>(HallMassing.Kind.class);
		int plain = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			HallMassing.Kind k = HallMassing.kindOf(mass(seed, 116, 76));
			if (k == null) plain++;
			else n.merge(k, 1, Integer::sum);
		}
		System.out.println("hall kinds on 116x76: " + n + " plain " + plain);
		for (HallMassing.Kind k : HallMassing.Kind.values()) assertTrue(n.getOrDefault(k, 0) >= SEEDS / 10, k + " " + n);
		assertEquals(0, plain, "plain fallback on a roomy lot");
	}

	@Test
	void smallestLotsStillGetRealHalls() {
		int plain = 0, cathedrals = 0;
		for (long seed = 0; seed < SEEDS; seed++) {
			HallMassing.Kind k = HallMassing.kindOf(mass(seed, 26, 26));
			if (k == null) plain++;
			if (k == HallMassing.Kind.CATHEDRAL) cathedrals++;
		}
		assertEquals(0, cathedrals, "a cathedral needs a bigger lot");
		assertTrue(plain <= SEEDS / 20, "plain fallback " + plain + " of " + SEEDS);
	}

	@Test
	void navesAndHallsSpanAtLeast15AndFootprintsAtLeast20() {
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				Massing m = mass(seed, e[0], e[1]);
				boolean span = false;
				int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = 0, z1 = 0;
				for (Mass a : m.masses()) {
					x0 = Math.min(x0, a.x());
					z0 = Math.min(z0, a.z());
					x1 = Math.max(x1, a.maxX());
					z1 = Math.max(z1, a.maxZ());
					if (a.kind().equals("nave") || a.kind().equals("hall")) {
						assertTrue(a.open(), a.id() + " is one tall space");
						assertTrue(Math.min(a.sx(), a.sz()) >= 15, a.id() + " " + a.sx() + "x" + a.sz() + " seed " + seed);
						span = true;
					}
				}
				HallMassing.Kind k = HallMassing.kindOf(m);
				if (k == HallMassing.Kind.MARKET || k == HallMassing.Kind.TOWN || k == HallMassing.Kind.CATHEDRAL) assertTrue(span, k + " has a nave or hall");
				assertTrue(x1 - x0 >= 20 && z1 - z0 >= 20, "footprint " + (x1 - x0) + "x" + (z1 - z0) + " seed " + seed);
			}
	}

	@Test
	void everyPlanHasEntranceOnTheGroundAndFitsLowCeilings() {
		// maxHeight just above one storey still gives a valid massing (fewer storeys or the plain hall)
		for (long seed = 0; seed < 100; seed++) {
			for (int h : new int[] {12, 20, 40}) {
				Massing m = mass(spec(seed, "plain", 52, 40, 3, h));
				assertTrue(m.main().ground());
			}
		}
	}

	@Test
	void sameSeedSameMassing() {
		for (long seed = 0; seed < 50; seed++) assertEquals(mass(seed, 86, 60), mass(seed, 86, 60));
	}

	/** Records the cost of the biggest lot (plan: shell and check time for 200x200). */
	@Test
	void twoHundredSquareLotCosts() throws Exception {
		List<Long> shell = new ArrayList<>(), check = new ArrayList<>();
		int cells = 0;
		for (long seed = 0; seed < 12; seed++) {
			Lot lot = new Lot("lot0", 0, 0, 200, 200, Facing.NORTH);
			ProbeSpecs.Probes p = ProbeSpecs.parse("noise:" + seed + ":100:4:16");
			GenRequest rq = new GenRequest(seed, lot, "hall", seed % 2 == 0 ? "medieval" : "plain", p.terrain(), p.biome());
			long t0 = System.nanoTime();
			SceneDoc s = new GeneratorV1().generate(rq);
			long t1 = System.nanoTime();
			List<Violation> v = Invariants.check(s, rq.terrain());
			long t2 = System.nanoTime();
			assertEquals(List.of(), v, "seed " + seed);
			shell.add((t1 - t0) / 1_000_000);
			check.add((t2 - t1) / 1_000_000);
			BuildingIr ir = s.buildings().get(0).shell();
			cells = Math.max(cells, ir.bounds().sx() * ir.bounds().sy() * ir.bounds().sz());
		}
		long[] a = shell.stream().mapToLong(Long::longValue).sorted().toArray(), b = check.stream().mapToLong(Long::longValue).sorted().toArray();
		System.out.println(String.format(Locale.ROOT, "hall 200x200: generate ms %s, check ms %s, max cells %d", Arrays.toString(a), Arrays.toString(b), cells));
		assertNotNull(a);
	}

	/** Halls are furnished: the open hall rooms carry stalls and lights, scaling with the floor. */
	@Test
	void hallRoomsAreFurnished() {
		int halls = 0, furnished = 0;
		for (long seed = 0; seed < 40; seed++) {
			Lot lot = new Lot("lot0", 0, 0, 70, 60, Facing.NORTH);
			ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
			GenRequest rq = new GenRequest(seed, lot, "hall", "plain", p.terrain(), p.biome());
			BuildingIr ir = new GeneratorV1().generate(rq).buildings().get(0).shell();
			for (var room : ir.rooms()) {
				if (!room.use().equals("hall")) continue;
				halls++;
				long tables = ir.hooks().stream().filter(h -> h.room().equals(room.id()) && h.kind().equals("table")).count();
				if (tables >= 1) furnished++;
			}
		}
		assertTrue(halls >= 40, "hall rooms " + halls);
		assertTrue(furnished >= halls * 0.9, furnished + " of " + halls + " hall rooms have a table");
	}

	/** Cathedrals open their nave to the aisles and the transept with arches (air boxes in the shared walls). */
	@Test
	void cathedralNavesOpenIntoAislesWithArches() {
		int cathedrals = 0;
		for (long seed = 0; seed < 60; seed++) {
			Lot lot = new Lot("lot0", 0, 0, 90, 70, Facing.NORTH);
			ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
			GenRequest rq = new GenRequest(seed, lot, "hall", "plain", p.terrain(), p.biome());
			BuildingIr ir = new GeneratorV1().generate(rq).buildings().get(0).shell();
			if (ir.volumes().stream().noneMatch(v -> v.id().equals("nave0"))) continue;
			cathedrals++;
			long arches = ir.boxes().stream().filter(b -> b.role() == dev.fractalbuildings.core.ir.Role.AIR
					&& b.layer() == dev.fractalbuildings.core.ir.Layer.INTERIOR && b.box().sy() >= 3 && (b.box().sx() == 3 || b.box().sz() == 3)).count();
			assertTrue(arches >= 6, "seed " + seed + " arches " + arches);
		}
		assertTrue(cathedrals >= 5, "cathedrals " + cathedrals);
	}

	/**
	 * Diversity over 256 seeds (medieval, flat 64). Measured: 30x30 distance 0.7588 / 77 signatures,
	 * 90x64 distance 0.8829 / 89 signatures, no near-duplicates, no voxel repeats; floors are ~80 % of those.
	 */
	@Test
	void diversityFloors() {
		assertFloors(diversity(30, 30, 256, true), 0.60, 61);
		assertFloors(diversity(90, 64, 256, true), 0.70, 70);
	}

	/** Mutant: the seed is ignored; the floors must catch it. */
	@Test
	void diversityFloorsFailWhenTheSeedIsIgnored() {
		DiversityReport r = diversity(90, 64, 256, false);
		org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> assertFloors(r, 0.70, 70));
	}

	static DiversityReport diversity(int w, int d, int n, boolean useSeed) {
		List<PlacedBuilding> l = new ArrayList<>();
		for (long s = 0; s < n; s++) {
			ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
			GenRequest rq = new GenRequest(useSeed ? s : 0, new Lot("lot0", 0, 0, w, d, Facing.NORTH), "hall", "medieval", p.terrain(), p.biome());
			l.add(new GeneratorV1().generate(rq).buildings().get(0));
		}
		return Diversity.of(l);
	}

	static void assertFloors(DiversityReport r, double dist, int sigs) {
		assertTrue(r.meanJaccardDistance() >= dist, "mean distance " + r.meanJaccardDistance());
		assertTrue(r.nearDuplicateRate() <= 0.01, "near-duplicates " + r.nearDuplicateRate());
		assertTrue(r.distinctSignatures() >= sigs, "signatures " + r.distinctSignatures());
		assertTrue(r.duplicatesByVoxelHash() <= 2, "voxel repeats " + r.duplicatesByVoxelHash());
	}

	/** Roofs over wide spans stay low (HallRoofs.MAX_RISE), also on the 200x200 lot, and close (the invariants). */
	@Test
	void roofsOverWideSpansStayLow() {
		int worst = 0;
		for (long seed = 0; seed < 60; seed++) {
			int[] lot = seed % 3 == 0 ? new int[] {200, 200} : new int[] {100, 70};
			ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
			GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, lot[0], lot[1], Facing.NORTH), "hall", seed % 2 == 0 ? "plain" : "medieval", p.terrain(), p.biome());
			SceneDoc s = new GeneratorV1().generate(rq);
			assertEquals(List.of(), Invariants.check(s, rq.terrain()), "seed " + seed);
			BuildingIr ir = s.buildings().get(0).shell();
			for (var v : ir.volumes()) {
				if (ir.volumes().stream().anyMatch(o -> v.id().equals(o.parent()))) continue; // something stands on it
				var vb = v.box();
				for (var b : ir.boxes()) {
					var q = b.box();
					if (!b.role().isRoof() || q.x() < vb.x() || q.z() < vb.z() || q.maxX() > vb.maxX() || q.maxZ() > vb.maxZ()) continue;
					boolean shared = ir.volumes().stream().anyMatch(o -> o != v && o.box().x() <= q.x() && q.x() < o.box().maxX() && o.box().z() <= q.z() && q.z() < o.box().maxZ());
					if (!shared) worst = Math.max(worst, q.maxY() - vb.maxY()); // columns of another volume (a wall strip) may carry its roof
				}
			}
		}
		System.out.println("hall roofs: highest roof block above its volume's top slab: " + worst);
		assertTrue(worst <= 10, "roof rises " + worst); // 7 by design, a few blocks of slack for parapets and dormers
	}

	/** Market halls get glazed arches (3-wide window boxes from the floor) along their ground storey. */
	@Test
	void marketHallsHaveArcadeArches() {
		int markets = 0;
		for (long seed = 0; seed < 60; seed++) {
			Lot lot = new Lot("lot0", 0, 0, 70, 60, Facing.NORTH);
			ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
			GenRequest rq = new GenRequest(seed, lot, "hall", "plain", p.terrain(), p.biome());
			BuildingIr ir = new GeneratorV1().generate(rq).buildings().get(0).shell();
			if (ir.volumes().stream().noneMatch(v -> v.id().equals("annex2"))) continue;
			markets++;
			long arches = ir.openings().stream().filter(o -> o.kind() == dev.fractalbuildings.core.ir.OpeningKind.WINDOW
					&& o.box().sy() == 3 && (o.box().sx() == 3 || o.box().sz() == 3)).count();
			assertTrue(arches >= 4, "seed " + seed + " arches " + arches);
		}
		assertTrue(markets >= 5, "market halls " + markets);
	}
}
