package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.RoadNet.EdgeKey;
import dev.fractalbuildings.core.road.RoadNet.Route;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Roads between settlements (ADR 0004, ROADMAP 6.10) on the two recorded worlds (24,576 blocks square, stride 64): the graph
 * guarantee N1, the water invariants WR1 to WR3, claim-based reads RC, and determinism RD1 to RD4 (any order, evicting caches, a
 * mutant that depends on cache state).
 */
class RoadNetTest {
	static final long[] SEEDS = {12345, 987654321};
	private static final Path FIXTURES = Files.isDirectory(Path.of("../fixtures/terrain")) ? Path.of("../fixtures/terrain") : Path.of("fixtures/terrain");

	record World(long seed, RecordedProbe rec, RegionMap.Window w, RoadNet ref) {
		static World load(long seed) {
			try {
				RecordedProbe r = RecordedProbe.read(FIXTURES.resolve("world-" + seed + "-s64.json.gz"));
				RegionMap.Window w = new RegionMap.Window(r.x0(), r.z0(), r.nx(), r.nz());
				World bare = new World(seed, r, w, null);
				return new World(seed, r, w, bare.net(FieldRegionView.DEFAULT_CACHE, FieldRegionView.DEFAULT_CACHE));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		RoadNet net(int cap, int viewCap) {
			RegionPlanner p = new RegionPlanner(new RegionField(seed, 1024), RegionProbe.of(rec, rec));
			return new RoadNet(p.field, new FieldRegionView(p, w, viewCap), w, RegionProbe.of(rec, rec), rec, cap);
		}
	}

	private static final Map<Long, World> WORLDS = new TreeMap<>();

	static synchronized World world(long seed) {
		return WORLDS.computeIfAbsent(seed, World::load);
	}

	// ---- graph (N1, N2) ----

	@Test
	void settlementsFoundByClaimsMatchABruteForceScan() {
		for (long seed : SEEDS) {
			World w = world(seed);
			RoadNet n = w.net(8192, 8192);
			List<Settlement> all = n.allSettlements();
			assertTrue(all.size() > 100, "settlements " + all.size());
			Random rnd = new Random(seed);
			int nonEmpty = 0;
			for (int i = 0; i < 60; i++) {
				int x = w.w().x0() + 2048 + rnd.nextInt(w.w().x1() - w.w().x0() - 4096), z = w.w().z0() + 2048 + rnd.nextInt(w.w().z1() - w.w().z0() - 4096);
				int dist = i % 2 == 0 ? 300 : 1800;
				TreeSet<CellKey> want = new TreeSet<>(), got = new TreeSet<>();
				for (Settlement s : all) if (Math.sqrt((double) (s.x() - x) * (s.x() - x) + (double) (s.z() - z) * (s.z() - z)) - s.r() <= dist) want.add(s.owner());
				for (Settlement s : n.settlementsNear(x, z, dist)) got.add(s.owner());
				assertEquals(want, got, "rim within " + dist + " of " + x + "," + z);
				if (!want.isEmpty()) nonEmpty++;
			}
			assertTrue(nonEmpty > 20, "non-empty scans " + nonEmpty);
		}
	}

	@Test
	void linksAreSymmetricAndTheGraphKeepsEveryDryLandGroupConnected() {
		for (long seed : SEEDS) {
			World w = world(seed);
			RoadNet n = w.ref();
			List<Settlement> all = n.allSettlements();
			TreeMap<CellKey, Integer> idx = new TreeMap<>();
			for (int i = 0; i < all.size(); i++) idx.put(all.get(i).owner(), i);
			int[] linked = new int[all.size()], routable = new int[all.size()];
			for (int i = 0; i < all.size(); i++) { linked[i] = i; routable[i] = i; }
			int orphanRoutable = 0;
			for (Settlement a : all) {
				// symmetry: b is linked to a iff a is linked to b
				for (EdgeKey e : n.edgesOf(a)) {
					Settlement b = n.settlement(e.a().equals(a.owner()) ? e.b() : e.a()).orElseThrow();
					assertTrue(n.edgesOf(b).contains(e), "link " + e + " is missing at its other end");
					assertTrue(RoadNet.gap(a, b) <= RoadNet.LGAP);
					union(linked, idx.get(a.owner()), idx.get(b.owner()));
				}
				// every pair within the gap limit that routes (or touches)
				for (Settlement b : n.neighbours(a)) {
					assertTrue(n.neighbours(b).contains(a), "neighbour lists are not symmetric");
					if (n.routable(a, b)) union(routable, idx.get(a.owner()), idx.get(b.owner()));
				}
				if (n.edgesOf(a).isEmpty()) for (Settlement b : n.neighbours(a)) if (n.routable(a, b)) orphanRoutable++;
			}
			// N1 guarantee: an unlinked settlement has no routable neighbour (the orphan-rescue check finds nothing) ...
			assertEquals(0, orphanRoutable, seed + ": orphans");
			// ... and so every group joined by routable pairs ("dry land") is one group in the road graph
			for (int i = 0; i < all.size(); i++) {
				for (int j = i + 1; j < all.size(); j++) {
					assertEquals(find(routable, i) == find(routable, j), find(linked, i) == find(linked, j), seed + ": groups of " + all.get(i).owner() + " and " + all.get(j).owner());
				}
			}
			int links = 0;
			for (Settlement s : all) links += n.edgesOf(s).size();
			assertTrue(links / 2 > 40, seed + ": links " + links / 2);
		}
	}

	static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }

	static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

	@Test
	void touchingPairsShareAMeetingPointAndCountAsRouted() {
		for (long seed : SEEDS) {
			RoadNet n = world(seed).ref();
			int touching = 0;
			for (EdgeKey e : n.allEdges()) {
				Settlement a = n.settlement(e.a()).orElseThrow(), b = n.settlement(e.b()).orElseThrow();
				Route r = n.route(e);
				assertEquals(RoadNet.gap(a, b) < RoadNet.TOUCH, r.touching, "touching " + e);
				if (!r.touching) continue;
				touching++;
				assertTrue(r.ok && r.columns == 0 && r.byChunk.isEmpty(), "a meeting point has no road of its own");
				int[] m = RoadNet.meeting(a, b);
				assertEquals(m[0], r.portA[0]);
				assertEquals(m[1], r.portB[1]);
				// the point lies between the rims, on the line between the centres
				double da = Math.hypot(m[0] - a.x(), m[1] - a.z()), db = Math.hypot(m[0] - b.x(), m[1] - b.z());
				assertEquals(Math.hypot(a.x() - b.x(), a.z() - b.z()), da + db, 2.0);
			}
			assertTrue(touching > 3, seed + ": touching pairs " + touching);
		}
	}

	// ---- water and footprints (WR1 to WR3) ----

	@Test
	void everyRouteIsDryApartFromShortSingleSpanCrossings() {
		for (long seed : SEEDS) {
			World w = world(seed);
			RoadNet n = w.ref();
			List<Settlement> all = n.allSettlements();
			int routes = 0, crossings = 0, wetColumns = 0, columns = 0;
			for (EdgeKey e : n.allEdges()) {
				Route r = n.route(e);
				if (r.touching) continue;
				routes++;
				Profile.Result p = r.profile;
				assertTrue(r.ok && p.ok());
				for (Profile.Crossing c : p.crossings()) {
					crossings++;
					assertTrue(c.length() <= Profile.MAX_CROSSING, e + " crossing of " + c.length());
					// one span: bank to bank is at most the run plus the two station gaps (no causeway), flat, no piers
					assertTrue((c.to() - c.from()) * Curve.STATION <= Profile.MAX_CROSSING + 2 * Curve.STATION, "span " + c);
					for (int k = c.first(); k <= c.last(); k++) assertEquals(c.deck(), p.deck()[k], "flat deck");
				}
				// every wet station lies inside a crossing span; crossings are at least 32 blocks apart
				for (int k = 0; k < p.wet().length; k++) if (p.wet()[k]) assertTrue(p.span()[k], e + ": wet station " + k + " outside a crossing");
				for (int i = 1; i < p.crossings().size(); i++) {
					assertTrue((p.crossings().get(i).first() - p.crossings().get(i - 1).last()) * Curve.STATION >= Profile.MIN_CROSSING_GAP, "crossings too close");
				}
				// grade: outside the pinned spans the deck moves at most MAX_STEP per station
				for (int k = 1; k < p.deck().length; k++) {
					if (p.span()[k] && p.span()[k - 1]) continue;
					assertTrue(Math.abs(p.deck()[k] - p.deck()[k - 1]) <= Profile.MAX_STEP + 1, e + ": deck step at " + k);
				}
				// no column in a third settlement's footprint, none in the band's outside
				for (var en : r.byChunk.entrySet()) {
					for (Col c : en.getValue()) {
						columns++;
						for (Settlement s : all) {
							if (s.owner().equals(e.a()) || s.owner().equals(e.b())) continue;
							long dx = c.x() - s.x(), dz = c.z() - s.z();
							assertTrue(dx * dx + dz * dz >= (long) s.r() * s.r(), "road " + e + " runs through " + s.owner());
						}
						assertTrue(c.x() >= r.bandBox[0] - 32 && c.x() <= r.bandBox[2] + 32 && c.z() >= r.bandBox[1] - 32 && c.z() <= r.bandBox[3] + 32, "outside the band");
						// WR3 on the recorded terrain: a wet column off a crossing span would need fill or an unplanned span
						if (!c.crossing() && w.rec().waterDepth(c.x(), c.z()) >= 1) wetColumns++;
					}
				}
			}
			assertTrue(routes > 40, seed + ": routes " + routes);
			assertTrue(crossings > 5, seed + ": crossings " + crossings + " (the dumps are pond-heavy)");
			// the centre line is dry at every station; a few shore columns under the road's width, or a pond narrower than the 8-block spacing, are wet: WR3 gives them deck blocks, never fill
			assertTrue(wetColumns <= columns / 100, seed + ": " + wetColumns + " wet road columns off a crossing of " + columns);
			System.out.println("roads " + seed + ": routes " + routes + ", crossings " + crossings + ", columns " + columns + ", wet columns off a span " + wetColumns);
		}
	}

	// ---- reads (RC) ----

	/** HT: heights come from exact reads at the stations, once per road; a warm chunk query reads nothing. */
	@Test
	void exactHeightsAreReadAtStationsOnceAndAWarmChunkReadsNothing() {
		World w = world(12345);
		java.util.concurrent.atomic.AtomicLong exactReads = new java.util.concurrent.atomic.AtomicLong(), coarseReads = new java.util.concurrent.atomic.AtomicLong();
		dev.fractalbuildings.core.probe.TerrainProbe exact = new dev.fractalbuildings.core.probe.TerrainProbe() {
			@Override public int floorY(int x, int z) { exactReads.incrementAndGet(); return w.rec().floorY(x, z); }
			@Override public int waterDepth(int x, int z) { exactReads.incrementAndGet(); return w.rec().waterDepth(x, z); }
		};
		RegionProbe base = RegionProbe.of(w.rec(), w.rec());
		RegionProbe coarse = new RegionProbe() {
			@Override public int floorY(int x, int z) { coarseReads.incrementAndGet(); return base.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { coarseReads.incrementAndGet(); return base.waterDepth(x, z); }
			@Override public String biome(int x, int z) { coarseReads.incrementAndGet(); return base.biome(x, z); }
		};
		RegionPlanner p = new RegionPlanner(new RegionField(12345, 1024), RegionProbe.of(w.rec(), w.rec()));
		RoadNet n = new RoadNet(p.field, new FieldRegionView(p, w.w()), w.w(), coarse, exact);
		List<int[]> chunks = sampleChunks(w.ref());
		for (int[] c : chunks.subList(0, 20)) n.query(c[0], c[1]);
		assertTrue(exactReads.get() > 0 && coarseReads.get() > 0);
		long e0 = exactReads.get(), c0 = coarseReads.get();
		for (int[] c : chunks.subList(0, 20)) n.query(c[0], c[1]); // warm: the feature makes no probes
		assertEquals(e0, exactReads.get());
		assertEquals(c0, coarseReads.get());
		// per road: two reads per station and at most 14 more per crossing end, plus those of rerouted attempts; the final profile is within that
		int roads = 0;
		for (RoadNet.EdgeKey e : n.allEdges()) {
			Route r = n.route(e);
			if (r.touching) continue;
			roads++;
			assertTrue(r.profile.reads() <= 2L * r.stations.length + 14L * (r.profile.crossings().size() + 1), "reads of " + e);
			assertTrue(r.exactReads >= r.profile.reads() && r.exactReads <= (RoadNet.MAX_REROUTES + 1) * (2L * r.stations.length * 2 + 60), "all attempts of " + e);
		}
		assertTrue(roads > 40);
	}


	@Test
	void aChunkReadsBoundedNeighbourhoodThroughClaims() {
		for (long seed : SEEDS) {
			World w = world(seed);
			List<Settlement> all = w.ref().allSettlements();
			Settlement big = all.get(0);
			for (Settlement s : all) if (s.r() > big.r()) big = s;
			assertTrue(big.r() > 800, "the largest settlement is a city: r " + big.r());
			int bound = w.ref().chunkReadBound();
			int[][] spots = {{big.x() + big.r() + 200, big.z()}, {big.x(), big.z() - big.r() - 200}, {big.x() - big.r() / 2, big.z()}};
			for (int[] p : spots) {
				RoadNet cold = w.net(8192, 8192);
				cold.startLog(p[0], p[1]);
				cold.query(Math.floorDiv(p[0], 16), Math.floorDiv(p[1], 16));
				double[] log = cold.stopLog();
				// the chunk's own scan stays inside the corridor bound, whatever the size of the settlements around it
				assertTrue(log[1] <= bound, seed + ": the chunk scan reached " + log[1] + " > " + bound);
				assertTrue(log[2] > 10 && log[2] < 1500, seed + ": cells touched " + log[2]);
				System.out.printf("read cap %d: chunk at %d,%d next to r%d: first scan %.0f blocks (bound %d), all scans %.0f, %.0f cells%n", seed, p[0], p[1], big.r(), log[1], bound, log[0], log[2]);
			}
		}
	}

	// ---- determinism (RD1 to RD4) ----

	/** Chunks with road, beside it and far from it (empty answers must be stable too). */
	static List<int[]> sampleChunks(RoadNet ref) {
		List<int[]> out = new ArrayList<>();
		int k = 0;
		for (EdgeKey e : ref.allEdges()) {
			Route r = ref.route(e);
			if (r.touching || k++ % 6 != 0) continue;
			int i = 0, step = Math.max(1, r.byChunk.size() / 3);
			for (long key : r.byChunk.keySet()) {
				if (i++ % step != 0) continue;
				Col c = r.byChunk.get(key).get(0);
				out.add(new int[] {Math.floorDiv(c.x(), 16), Math.floorDiv(c.z(), 16)});
			}
		}
		Random rnd = new Random(3);
		int roadChunks = out.size();
		for (int i = 0; i < 15; i++) {
			int[] b = out.get(rnd.nextInt(roadChunks));
			out.add(new int[] {b[0] + rnd.nextInt(5) - 2, b[1] + rnd.nextInt(5) - 2});
		}
		return out;
	}

	static TreeMap<String, List<Col>> run(RoadNet n, List<int[]> chunks) {
		TreeMap<String, List<Col>> out = new TreeMap<>();
		for (int[] c : chunks) out.put(c[0] + "," + c[1], n.query(c[0], c[1]));
		return out;
	}

	@Test
	void chunkQueriesAreOrderAndCacheIndependent() {
		for (long seed : SEEDS) {
			World w = world(seed);
			List<int[]> chunks = sampleChunks(w.ref());
			assertTrue(chunks.size() > 40, "sample " + chunks.size());
			TreeMap<String, List<Col>> ref = run(w.net(8192, 8192), chunks);
			int nonEmpty = 0;
			for (var l : ref.values()) if (!l.isEmpty()) nonEmpty++;
			assertTrue(nonEmpty > 30, "road chunks " + nonEmpty);
			for (int s = 0; s < 3; s++) { // shuffled orders on fresh nets
				List<int[]> shuf = new ArrayList<>(chunks);
				Collections.shuffle(shuf, new Random(s));
				assertEquals(ref, run(w.net(8192, 8192), shuf), "shuffle " + s);
			}
			// capacity 1 on every road cache (settlements, links, ports, routes, nodes, cell lists), region view at full size
			List<int[]> shuf = new ArrayList<>(chunks.subList(0, 12));
			Collections.shuffle(shuf, new Random(9));
			for (var e : run(w.net(1, 8192), shuf).entrySet()) assertEquals(ref.get(e.getKey()), e.getValue(), "cap 1 chunk " + e.getKey());
			// capacity 1 everywhere, the region view's memos included (slow: a few chunks)
			for (var e : run(w.net(1, 1), new ArrayList<>(chunks.subList(0, 2))).entrySet()) assertEquals(ref.get(e.getKey()), e.getValue(), "cap 1/1 chunk " + e.getKey());
			// where two roads overlap the lower edge key wins, and every answer is sorted by (z, x)
			for (var l : ref.values()) {
				for (int i = 1; i < l.size(); i++) assertTrue(l.get(i - 1).z() < l.get(i).z() || (l.get(i - 1).z() == l.get(i).z() && l.get(i - 1).x() < l.get(i).x()));
			}
		}
	}

	/** Control: the comparison can fail. A mutant that makes a deck depend on how many routes the net has built (cache state) is caught. */
	@Test
	void aCacheStateMutantIsCaught() {
		World w = world(12345);
		List<int[]> chunks = sampleChunks(w.ref());
		TreeMap<String, List<Col>> ref = run(w.net(8192, 8192), chunks);
		RoadNet.MUTANT_CACHE_STATE = true;
		try {
			TreeMap<String, List<Col>> a = run(w.net(8192, 8192), chunks);
			List<int[]> shuf = new ArrayList<>(chunks);
			Collections.shuffle(shuf, new Random(1));
			TreeMap<String, List<Col>> b = run(w.net(8192, 8192), shuf);
			assertFalse(a.equals(b) && b.equals(ref), "the shuffle and reference comparison must fail under the mutant");
		} finally {
			RoadNet.MUTANT_CACHE_STATE = false;
		}
		assertEquals(ref, run(w.net(8192, 8192), chunks));
		assertNotEquals(ref, run(world(987654321).net(8192, 8192), chunks), "another world gives other roads");
	}
}
