package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class FieldRegionViewTest {
	static final RegionProbe PLAINS = RegionProbe.of((x, z) -> 64, (x, z) -> "minecraft:plains");

	static RegionPlanner planner(long seed, RegionProbe probe) {
		return new RegionPlanner(new RegionField(seed, 1024), probe);
	}

	static FieldRegionView.Resolved[][] batch(FieldRegionView v, int r) {
		FieldRegionView.Resolved[][] out = new FieldRegionView.Resolved[2 * r + 1][2 * r + 1];
		for (int cz = -r; cz <= r; cz++) for (int cx = -r; cx <= r; cx++) out[cz + r][cx + r] = v.cell(cx, cz);
		return out;
	}

	@Test
	void everyCellAloneEqualsTheBatchInAnyOrderOnAnyThreadCount() throws Exception {
		for (long seed : new long[] {12345, 987654321L}) {
			RegionProbe world = new RegionTestWorld(seed);
			var expected = batch(new FieldRegionView(planner(seed, world)), 8);
			// each cell alone on a fresh view
			for (int cz = -8; cz <= 8; cz += 4) {
				for (int cx = -8; cx <= 8; cx += 2) {
					assertEquals(expected[cz + 8][cx + 8], new FieldRegionView(planner(seed, world)).cell(cx, cz), "alone " + cx + "," + cz);
				}
			}
			// shuffled order on one view
			List<int[]> all = new ArrayList<>();
			for (int cz = -8; cz <= 8; cz++) for (int cx = -8; cx <= 8; cx++) all.add(new int[] {cx, cz});
			Collections.shuffle(all, new Random(seed));
			FieldRegionView shuffled = new FieldRegionView(planner(seed, world));
			for (int[] c : all) assertEquals(expected[c[1] + 8][c[0] + 8], shuffled.cell(c[0], c[1]));
			// 8 threads sharing one view, shuffled
			FieldRegionView shared = new FieldRegionView(planner(seed, world));
			ExecutorService ex = Executors.newFixedThreadPool(8);
			try {
				List<Future<FieldRegionView.Resolved>> fs = new ArrayList<>();
				for (int[] c : all) fs.add(ex.submit(() -> shared.cell(c[0], c[1])));
				for (int i = 0; i < all.size(); i++) assertEquals(expected[all.get(i)[1] + 8][all.get(i)[0] + 8], fs.get(i).get());
			} finally {
				ex.shutdown();
			}
		}
	}

	/** A direct, global implementation of the rule over a big grid, as an oracle for the bounded one. */
	private static final class Oracle {
		final RegionPlanner p;
		final int S = 1024;
		final TreeMap<CellKey, CellDecision> claimants = new TreeMap<>();
		final TreeMap<CellKey, List<CellKey>> claimed = new TreeMap<>();

		Oracle(RegionPlanner p, int g) {
			this.p = p;
			for (int cz = -g; cz <= g; cz++) {
				for (int cx = -g; cx <= g; cx++) {
					int drawn = Tiers.radius(p.field.seed, cx, cz, Tiers.tier(p.field.seed, cx, cz), p.maxRadius);
					if (drawn <= 0.4 * S) continue;
					CellDecision d = p.decide(cx, cz);
					if (!d.settled()) continue;
					List<CellKey> cells = new ArrayList<>();
					for (int z = -g; z <= g; z++) {
						for (int x = -g; x <= g; x++) {
							long dx = p.field.siteX(x, z) - d.centreX(), dz = p.field.siteZ(x, z) - d.centreZ();
							if (dx * dx + dz * dz <= (long) drawn * drawn) cells.add(new CellKey(x, z));
						}
					}
					if (!cells.contains(new CellKey(cx, cz))) cells.add(new CellKey(cx, cz));
					claimants.put(new CellKey(cx, cz), d);
					claimed.put(new CellKey(cx, cz), cells);
				}
			}
		}

		boolean higher(CellKey b, CellKey a) {
			int c = Long.compareUnsigned(Hash64.hash(p.field.seed, "region/priority", b.cx(), b.cz()), Hash64.hash(p.field.seed, "region/priority", a.cx(), a.cz()));
			return c != 0 ? c > 0 : (b.cx() != a.cx() ? b.cx() < a.cx() : b.cz() < a.cz());
		}

		boolean survives(CellKey a) {
			for (CellKey b : claimants.keySet()) {
				if (b.equals(a) || !higher(b, a)) continue;
				List<CellKey> x = new ArrayList<>(claimed.get(a));
				x.retainAll(claimed.get(b));
				if (!x.isEmpty()) return false;
			}
			return true;
		}

		CellKey owner(CellKey k) {
			CellKey w = null;
			for (CellKey a : claimants.keySet()) {
				if (claimed.get(a).contains(k) && survives(a)) {
					assertNull(w, "two survivors claim " + k);
					w = a;
				}
			}
			return w;
		}
	}

	@Test
	void matchesAGlobalReferenceAndSurvivorsNeverOverlap() {
		int joinedMembers = 0, owners = 0, dropped = 0;
		for (long seed : new long[] {1, 12345, 987654321L}) {
			RegionPlanner p = planner(seed, PLAINS);
			FieldRegionView v = new FieldRegionView(p);
			Oracle o = new Oracle(p, 30);
			List<CellKey> survivors = new ArrayList<>();
			for (CellKey a : o.claimants.keySet()) if (o.survives(a)) survivors.add(a);
			for (int i = 0; i < survivors.size(); i++) {
				for (int j = i + 1; j < survivors.size(); j++) {
					List<CellKey> x = new ArrayList<>(o.claimed.get(survivors.get(i)));
					x.retainAll(o.claimed.get(survivors.get(j)));
					assertTrue(x.isEmpty(), "survivors overlap " + survivors.get(i) + " " + survivors.get(j));
				}
			}
			dropped += o.claimants.size() - survivors.size();
			for (int cz = -12; cz <= 12; cz++) {
				for (int cx = -12; cx <= 12; cx++) {
					CellKey k = new CellKey(cx, cz);
					CellKey w = o.owner(k);
					FieldRegionView.Resolved r = v.cell(cx, cz);
					if (w == null) {
						assertNull(r.claimedBy());
						assertNull(r.site());
						assertEquals(p.decide(cx, cz), r.decision(), "alone " + k);
					} else if (w.equals(k)) {
						owners++;
						assertNull(r.claimedBy());
						assertEquals(r.site().owner(), k);
						assertEquals(r.decision().radiusDrawn(), r.decision().radius(), "winner takes its drawn radius");
					} else {
						joinedMembers++;
						assertEquals(w, r.claimedBy());
						assertEquals(w, r.site().owner());
						assertEquals(p.decide(cx, cz), r.decision());
					}
				}
			}
		}
		assertTrue(owners > 20 && joinedMembers > 20 && dropped > 20, "owners " + owners + ", members " + joinedMembers + ", dropped " + dropped);
	}

	@Test
	void aDroppedClaimantsCellsAreMembersOnlyOfAWinner() {
		RegionPlanner p = planner(12345, PLAINS);
		FieldRegionView v = new FieldRegionView(p);
		int checked = 0;
		for (int cz = -10; cz <= 10; cz++) {
			for (int cx = -10; cx <= 10; cx++) {
				FieldRegionView.Resolved r = v.cell(cx, cz);
				if (r.member()) {
					FieldRegionView.Resolved w = v.cell(r.claimedBy().cx(), r.claimedBy().cz());
					assertNull(w.claimedBy(), "the owner of a site is not itself a member");
					assertEquals(w.site(), r.site());
					assertTrue(w.site().cells().contains(new CellKey(cx, cz)));
					assertTrue(w.decision().settled());
					checked++;
				}
			}
		}
		assertTrue(checked > 50);
	}

	@Test
	void winnersReachTheirDrawnRadiusAcrossCells() {
		int big = 0, cross = 0;
		for (long seed : new long[] {12345, 987654321L, 5}) {
			FieldRegionView v = new FieldRegionView(planner(seed, PLAINS));
			for (var row : batch(v, 12)) {
				for (var r : row) {
					if (r.site() == null || r.claimedBy() != null) continue;
					CellDecision d = r.decision();
					assertEquals(Math.min(d.radiusDrawn(), 4096), d.radius());
					if (d.radius() > 512) big++;
					if (d.radius() > d.radiusFit()) cross++;
				}
			}
		}
		assertTrue(big >= 5, "settlements past the old clamp: " + big);
		assertTrue(cross >= 5, "settlements wider than their cell's fit: " + cross);
	}

	@Test
	void readsStayInsideTheReadRadius() {
		for (RegionProbe base : new RegionProbe[] {PLAINS, new RegionTestWorld(9)}) {
			int[] bad = {0}, reads = {0};
			double[] far = {0};
			// query cells that belong to a join (owners and members), so the rival scan in survives() runs
			FieldRegionView probeView = new FieldRegionView(planner(9, base));
			List<int[]> qs = new ArrayList<>();
			for (int cz = -12; cz <= 12 && qs.size() < 40; cz += 2) for (int cx = -12; cx <= 12 && qs.size() < 40; cx += 2) if (probeView.site(cx, cz) != null) qs.add(new int[] {cx, cz});
			assertTrue(qs.size() >= 20, "joined cells to query: " + qs.size());
			for (int[] q : qs) {
				RegionField f = new RegionField(9, 1024);
				int sx = f.siteX(q[0], q[1]), sz = f.siteZ(q[0], q[1]);
				RegionPlanner[] h = new RegionPlanner[1];
				FieldRegionView[] vh = new FieldRegionView[1];
				RegionProbe probe = new RegionProbe() {
					@Override public int floorY(int x, int z) { check(x, z); return base.floorY(x, z); }
					@Override public int waterDepth(int x, int z) { return base.waterDepth(x, z); }
					@Override public String biome(int x, int z) { check(x, z); return base.biome(x, z); }
					void check(int x, int z) {
						reads[0]++;
						int rr = vh[0].readRadius();
						double dd = Math.hypot(x - sx, z - sz);
						far[0] = Math.max(far[0], dd);
						if (dd > rr) bad[0]++;
					}
				};
				h[0] = planner(9, probe);
				vh[0] = new FieldRegionView(h[0]);
				vh[0].cell(q[0], q[1]);
			}
			assertEquals(0, bad[0], "probe reads beyond the read radius");
			assertTrue(reads[0] > 1000);
			assertTrue(far[0] > 2 * 4096, "farthest read " + far[0] + " never passed 2R: the rival scan is not exercised");
		}
	}

	@Test
	void theReadRadiusIsTight() {
		// a far probe column must matter: the farthest reads come close to the bound (it is not vacuous)
		RegionField f = new RegionField(9, 1024);
		int sx = f.siteX(0, 0), sz = f.siteZ(0, 0);
		int[] far = {0};
		RegionProbe probe = new RegionProbe() {
			@Override public int floorY(int x, int z) { far[0] = Math.max(far[0], Math.max(Math.abs(x - sx), Math.abs(z - sz))); return 64; }
			@Override public int waterDepth(int x, int z) { return 0; }
			@Override public String biome(int x, int z) { return "minecraft:plains"; }
		};
		FieldRegionView v = new FieldRegionView(planner(9, probe));
		v.cell(0, 0);
		assertTrue(far[0] > 3 * 1024, "reads reached only " + far[0]);
		assertTrue(far[0] <= v.readRadius());
	}

	@Test
	void smallMaxRadiusKeepsTheOldBehaviour() {
		// no radius above 0.4 cells: nothing claims, every cell equals its grow decision
		RegionPlanner p = planner(3, PLAINS);
		p.maxRadius = 400;
		FieldRegionView v = new FieldRegionView(p);
		for (var row : batch(v, 5)) for (var r : row) {
			assertNull(r.site());
			assertEquals(p.decide(r.decision().cx(), r.decision().cz()), r.decision());
		}
	}

	@Test
	void goldenSites() throws Exception {
		List<String> stale = new ArrayList<>();
		for (long seed : new long[] {12345L, 987654321L}) {
			FieldRegionView v = new FieldRegionView(planner(seed, new RegionTestWorld(seed)));
			StringBuilder sb = new StringBuilder();
			for (var row : batch(v, 8)) {
				for (var r : row) {
					CellDecision c = r.decision();
					sb.append(c.cx()).append(' ').append(c.cz()).append(' ').append(c.settled() ? c.tier().id : "-").append(' ')
						.append(c.centreX()).append(' ').append(c.centreZ()).append(' ').append(c.radius()).append(' ')
						.append(r.claimedBy() == null ? "." : r.claimedBy().cx() + "," + r.claimedBy().cz()).append('\n');
				}
			}
			byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			String actual = HexFormat.of().formatHex(d) + "\n" + sb;
			String expected;
			try (var in = FieldRegionViewTest.class.getResourceAsStream("/golden/region/join-" + seed + ".txt")) {
				expected = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			if (!expected.equals(actual)) {
				Path out = Path.of("build/golden/region/join-" + seed + ".txt");
				Files.createDirectories(out.getParent());
				Files.writeString(out, actual);
				stale.add(seed + " (new file " + out.toAbsolutePath() + ")");
			}
		}
		assertTrue(stale.isEmpty(), "join goldens differ: " + stale + ". If intended, copy build/golden/region/join-*.txt to src/test/resources/golden/region/.");
	}
}
