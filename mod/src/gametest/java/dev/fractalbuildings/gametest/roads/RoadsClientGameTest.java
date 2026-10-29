package dev.fractalbuildings.gametest.roads;

import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.RoadNet.EdgeKey;
import dev.fractalbuildings.core.road.RoadNet.Route;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.roads.RoadFeature;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * T2 road test (ADR 0004, plan C1): intercity roads in real noise worlds (the server game-test world is flat). A fixed seed, the world's own
 * planner picks a few routes near the origin away from every settlement's district reach; one fresh world per order generates the corridor
 * chunks in that order and the ledger of what the road feature claimed is digested (claim semantics: blocks placed plus the air above
 * them). All orders must give one digest. The world is then read back (the deck is what the ledger says, no deck over water outside a
 * crossing, no column inside a settlement) and the last world takes the review screenshots. The S10 mutant (ground from MOTION_BLOCKING)
 * must change the digest, so the comparison can fail.
 *
 * <p>Env: {@code FB_ROADS_SEED} (default 987654321), {@code FB_ROADS=0} skips the test, {@code FB_ROADS_SHOTS=0} skips screenshots.
 */
// gametest-order: 6
public class RoadsClientGameTest implements FabricClientGameTest {
	static final StringBuilder OUT = new StringBuilder();
	/** Failed checks: they are collected so the run still takes its screenshots, then reported together. */
	static final List<String> PROBLEMS = new ArrayList<>();
	/** Where routes are picked (FB_ROADS_CENTER "x,z", default the origin): a snowy spot of a seed shows the floating-snow check (ROADCLEAR). */
	static final int[] CENTER = center();

	static int[] center() {
		String s = System.getenv("FB_ROADS_CENTER");
		if (s == null) return new int[] {0, 0};
		String[] p = s.split(",");
		return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
	}

	static void problem(String s) {
		PROBLEMS.add(s);
		say("PROBLEM " + s);
	}

	static void say(String s) {
		System.out.println("ROADS " + s);
		OUT.append(s).append('\n');
	}

	static String loadavg() {
		try { return Files.readString(Path.of("/proc/loadavg")).trim(); } catch (Exception e) { return "?"; }
	}

	/** A chosen route: its key, the corridor of chunks to generate and what to look at. */
	record Target(String name, EdgeKey key, double[][] stations, int[] deck, List<int[]> crossings, long fingerprint) {
		/** The identity of the route: arrays inside a record compare by reference, so compare these. */
		String id() { return name + " " + key + " " + fingerprint; }
	}

	record Picked(List<Target> targets, List<ChunkPos> corridor) {}

	static long fingerprint(Route r) {
		long h = 1125899906842597L;
		for (int d : r.profile.deck()) h = h * 31 + d;
		for (double[] s : r.stations) h = h * 31 + Double.hashCode(s[0]) * 17 + Double.hashCode(s[1]);
		return h;
	}

	/** FB_ROADS_VILLAGE=1: the test finds its own site, a route that passes a vanilla village (VILLROAD). */
	static final boolean VILLAGE_MODE = "1".equals(System.getenv("FB_ROADS_VILLAGE"));
	static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> VILLAGE_TAG =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, net.minecraft.resources.Identifier.withDefaultNamespace("village"));

	/**
	 * Village mode: walks the planned routes around the centre in key order, asks the game's structure locate for the nearest village
	 * every few stations, and takes the first route one passes within {@code NEAR} blocks of; the corridor is that route's stations within
	 * {@code WINDOW} blocks of the village. Deterministic for a seed (the routes and the locate both are).
	 */
	static Picked pickVillage(ServerLevel level, WorldPlanner planner) {
		final double NEAR = 90, WINDOW = 200;
		RoadNet net = planner.roads();
		TreeMap<EdgeKey, Route> routes = new TreeMap<>();
		for (Settlement s : net.settlementsNear(CENTER[0], CENTER[1], 5000)) {
			for (EdgeKey e : net.edgesOf(s)) {
				Route r = net.route(e);
				if (r.ok && !r.touching && r.length >= 300 && planner.linkState(e) != WorldPlanner.LinkState.DROPPED) routes.put(e, r); // 6.22: a dropped link draws nothing
			}
		}
		say("village mode: " + routes.size() + " routes around " + CENTER[0] + "," + CENTER[1]);
		int asked = 0;
		for (Route r : routes.values()) {
			for (int i = 0; i < r.stations.length; i += 5) {
				double[] st = r.stations[i];
				double da = Math.hypot(st[0] - r.a.x(), st[1] - r.a.z()) - r.a.r(), db = Math.hypot(st[0] - r.b.x(), st[1] - r.b.z()) - r.b.r();
				if (Math.min(da, db) < 330) continue;
				asked++;
				BlockPos v = level.findNearestMapStructure(VILLAGE_TAG, new BlockPos((int) st[0], 64, (int) st[1]), 6, false);
				if (v == null || Math.hypot(v.getX() - st[0], v.getZ() - st[1]) > NEAR) continue;
				Set<ChunkPos> corridor = new LinkedHashSet<>();
				for (double[] s : r.stations) {
					if (Math.hypot(v.getX() - s[0], v.getZ() - s[1]) > WINDOW) continue;
					int x = (int) Math.floor(s[0]), z = (int) Math.floor(s[1]);
					for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) corridor.add(new ChunkPos((x >> 4) + ox, (z >> 4) + oz));
				}
				say("village mode: village at " + v.getX() + "," + v.getZ() + " beside route " + r.key + " (station " + i + " of " + r.stations.length + ", " + asked + " locates), corridor "
					+ corridor.size() + " chunks");
				List<int[]> crossings = new ArrayList<>();
				for (var c : r.profile.crossings()) crossings.add(new int[] {c.first(), c.last(), c.from(), c.to(), c.length(), c.deck()});
				List<ChunkPos> out = new ArrayList<>(corridor);
				out.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
				return new Picked(List.of(new Target("village", r.key, r.stations, r.profile.deck(), crossings, fingerprint(r))), out);
			}
		}
		throw new IllegalStateException("village mode: no route passes a village within " + NEAR + " blocks (" + asked + " locates, " + routes.size() + " routes)");
	}

	/** Routes near the origin, outside every district reach; chosen by the world's own planner (the same in every fresh world of the seed). */
	static Picked pick(WorldPlanner planner) {
		RoadNet net = planner.roads();
		TreeMap<EdgeKey, Route> routes = new TreeMap<>();
		for (Settlement s : net.settlementsNear(CENTER[0], CENTER[1], 2600)) {
			for (EdgeKey e : net.edgesOf(s)) {
				Route r = net.route(e);
				if (r.ok && !r.touching && planner.linkState(e) != WorldPlanner.LinkState.DROPPED) routes.put(e, r); // 6.22: a dropped link draws nothing
			}
		}
		say("routes near the origin: " + routes.size());
		List<Target> targets = new ArrayList<>();
		Route bridge = null, relief = null, plain = null;
		double bestRelief = -1;
		for (Route r : routes.values()) {
			if (r.length < 350 || r.length > 1400) continue;
			if (!r.profile.crossings().isEmpty() && (bridge == null || r.length < bridge.length)) bridge = r;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int d : r.profile.deck()) { lo = Math.min(lo, d); hi = Math.max(hi, d); }
			if (r.profile.crossings().isEmpty() && hi - lo > bestRelief) { bestRelief = hi - lo; relief = r; }
			if (r.profile.crossings().isEmpty() && (plain == null || Math.abs(r.length - 700) < Math.abs(plain.length - 700))) plain = r;
		}
		Set<ChunkPos> corridor = new LinkedHashSet<>();
		for (Route r : new Route[] {plain, relief, bridge}) {
			if (r == null) continue;
			String name = r == bridge ? "bridge" : r == relief ? "relief" : "plain";
			if (targets.stream().anyMatch(t -> t.key().equals(r.key))) continue;
			List<int[]> crossings = new ArrayList<>();
			for (var c : r.profile.crossings()) crossings.add(new int[] {c.first(), c.last(), c.from(), c.to(), c.length(), c.deck()});
			targets.add(new Target(name, r.key, r.stations, r.profile.deck(), crossings, fingerprint(r)));
			int kept = 0;
			for (double[] st : r.stations) {
				int x = (int) Math.floor(st[0]), z = (int) Math.floor(st[1]);
				double da = Math.hypot(x - r.a.x(), z - r.a.z()) - r.a.r(), db = Math.hypot(x - r.b.x(), z - r.b.z()) - r.b.r();
				if (Math.min(da, db) < 330) continue; // districts of the end settlements reach about 64 + 160 beyond the rim
				kept++;
				for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) corridor.add(new ChunkPos((x >> 4) + ox, (z >> 4) + oz));
			}
			say(name + " " + r.key + " length " + (int) r.length + " stations " + r.stations.length + " (" + kept + " in the corridor) crossings " + crossings.size()
				+ " ports " + Arrays.toString(r.portA) + " " + Arrays.toString(r.portB));
		}
		List<ChunkPos> out = new ArrayList<>(corridor);
		out.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
		return new Picked(targets, out);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("RoadsClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		if ("0".equals(System.getenv("FB_ROADS"))) return;
		String seed = System.getenv().getOrDefault("FB_ROADS_SEED", "987654321");
		boolean shots = !"0".equals(System.getenv("FB_ROADS_SHOTS"));
		List<String> orders = VILLAGE_MODE ? List.of("asc", "desc", "shuffled", "shuffled-evicting", "nojoin", "spuryield", "spurshort", "ungraded", "mutant-asc", "mutant-shuffled") : List.of("asc", "desc", "shuffled", "shuffled-evicting", "mutant-asc", "mutant-shuffled"); // the nojoin, spuryield, spurshort and ungraded controls need a village: only in village mode
		Picked first = null;
		Map<String, String> digests = new TreeMap<>();
		Map<String, Map<Long, String>> columns = new TreeMap<>();
		Map<String, Map<Long, Integer>> cellsByOrder = new TreeMap<>(); // ORDERFAIL: the whole recording per order, to say which cells a differing digest comes from
		Map<String, java.util.Set<Long>> roadsByOrder = new TreeMap<>();
		for (int oi = 0; oi < orders.size(); oi++) {
			String order = orders.get(oi);
			boolean mutant = order.startsWith("mutant");
			boolean evict = order.endsWith("evicting");
			boolean takeShots = shots && oi == 3;
			final boolean firstOrder = oi == 0;
			if (mutant && System.getenv("FB_ROADS_MUTANT") != null && "0".equals(System.getenv("FB_ROADS_MUTANT"))) continue;
			RoadFeature.MUTANT_GROUND = mutant;
			dev.fractalbuildings.mod.roads.VillageRoads.OFF = order.equals("nojoin"); // the control: the join switched off
			RoadFeature.MUTANT_SPUR_YIELD = order.equals("spuryield"); // the control: the spur yields to the street box as in r6 (stops at its edge)
			dev.fractalbuildings.mod.roads.VillageRoads.MUTANT_SPUR_SHORT = order.equals("spurshort"); // the control: a bare centre line 2 cells short of the street
			dev.fractalbuildings.mod.roads.VillageRoads.UNGRADED = order.equals("ungraded"); // the control: the spur on the ground as in r7 (2-block steps on this seed)
			RoadFeature.LEGACY_CLEAR = "1".equals(System.getenv("FB_ROADS_LEGACY"));
			RoadFeature.CHUNKS.set(0);
			RoadFeature.BLOCKS.set(0);
			RoadFeature.NANOS.set(0);
			RoadFeature.SLOWEST.set(0);
			RoadFeature.CALLS.set(0);
			RoadFeature.CALL_NANOS.set(0);
			RoadFeature.STATS.clear();
			RoadFeature.COLUMNS.clear();
			RoadFeature.RECORD_STATS = true;
			AtomicReference<Picked> picked = new AtomicReference<>();
			try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
				String load = loadavg();
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow(() -> new IllegalStateException("no planner"));
					if (!planner.roadsEnabled()) throw new IllegalStateException("roads are off in a default world");
					long t0 = System.nanoTime();
					Picked p0 = VILLAGE_MODE ? pickVillage(level, planner) : pick(planner);
					VillageChecks.Villages vil = VillageChecks.find(level, p0.corridor());
					Picked p = new Picked(p0.targets(), vil.corridor());
					say(order + " villages near the corridor: " + vil.found().size() + ", corridor with their windows " + p.corridor().size() + " chunks");
					say(order + " picked in " + (System.nanoTime() - t0) / 1_000_000 + " ms, corridor " + p.corridor().size() + " chunks, loadavg " + load);
					picked.set(p);
					if (p.targets().size() < (VILLAGE_MODE ? 1 : 2) || p.corridor().size() < (VILLAGE_MODE ? 9 : 40)) throw new IllegalStateException("too few routes near the origin: " + p.targets().size());
					List<ChunkPos> seq = new ArrayList<>(p.corridor());
					if (order.contains("desc")) Collections.reverse(seq);
					else if (order.contains("shuffled")) Collections.shuffle(seq, new Random(order.hashCode()));
					WorldPlanners.FAILURES.set(0);
					String digest;
					try (PieceLedger.Recording rec = PieceLedger.start()) {
						long g0 = System.nanoTime();
						int n = 0;
						for (ChunkPos c : seq) {
							level.getChunkSource().getChunk(c.x(), c.z(), ChunkStatus.FULL, true);
							if (evict && ++n % 8 == 0) planner.evictAll(); // the road caches go cold in the middle of the run
						}
						say(order + " generated " + seq.size() + " chunks in " + (System.nanoTime() - g0) / 1_000_000 + " ms");
						// ORDERFAIL: only the cells of the corridor and the ring its chunks decorate. The client's own chunk loading round the spawn
						// runs at the same time and puts a nearby settlement's district cells into the recording in some runs and not others
						// (seen at 175,55,256 in shuffled-evicting, 7548 cells in chunks 10..13,14..16, far from the corridor)
						java.util.TreeMap<Long, Integer> mine = corridorCells(rec.states(), p.corridor());
						if (mine.size() != rec.size()) say(order + ": " + (rec.size() - mine.size()) + " recorded cells outside the corridor and its ring are left out of the digest");
						digest = PieceLedger.digest(mine) + " (" + mine.size() + " cells)";
						cellsByOrder.put(order, mine);
						roadsByOrder.put(order, new java.util.TreeSet<>(rec.roadCells()));
						if (!rec.duplicates().isEmpty()) problem("cells claimed twice: " + rec.duplicates().size());
						if (firstOrder) readBack(level, planner, p, vil, rec.states());
						if (!mutant) {
							List<String> lines = new ArrayList<>();
							List<String> probs = VillageChecks.check(level, planner, vil, lines, VILLAGE_MODE);
							lines.forEach(l -> say(order + " VILLAGE " + l));
							if (order.equals("nojoin")) { if (probs.isEmpty()) problem("the village check passed with the join switched off, it cannot fail"); else say("nojoin (the control) fails the village check as it must: " + probs.get(0)); }
							else if (order.equals("spuryield")) { if (probs.isEmpty()) problem("the village check passed with the spur stopping at the street box, the strict end check cannot fail"); else say("spuryield (the control) fails the village check as it must: " + probs.get(0)); }
							else if (order.equals("spurshort") || order.equals("ungraded")) { if (probs.isEmpty()) problem("the village check passed for the " + order + " control, it cannot fail"); else say(order + " (the control) fails the village check as it must: " + probs.get(0)); }
							else for (String s : probs) problem(order + ": " + s);
							if (List.of("asc", "desc", "shuffled", "shuffled-evicting").contains(order)) villageView(planner, vil, order.equals("asc"));
							if (order.equals("asc")) for (var vf : vil.found()) { if (dev.fractalbuildings.mod.roads.VillageRoads.resultOf(planner, vf).isEmpty()) continue; VillageChecks.planImage(planner, vf, "road-village-plan", lines); lines.forEach(l -> { if (l.startsWith("plan view")) say(l); }); break; }
						}
					}
					if (WorldPlanners.FAILURES.get() != 0) problem("the road feature swallowed exceptions, see the log");
					digests.put(order, digest);
					columns.put(order, new TreeMap<>(RoadFeature.COLUMNS));
					long chunks = RoadFeature.CHUNKS.get(); dev.fractalbuildings.mod.roads.VillageRoads.OFF = false; RoadFeature.MUTANT_SPUR_YIELD = false; dev.fractalbuildings.mod.roads.VillageRoads.MUTANT_SPUR_SHORT = false; dev.fractalbuildings.mod.roads.VillageRoads.UNGRADED = false;
					say(String.format("%s digest %s; road chunks %d, blocks %d, feature mean %.2f ms, slowest %.1f ms (load %s)", order, digest, chunks,
						RoadFeature.BLOCKS.get(), chunks == 0 ? 0 : RoadFeature.NANOS.get() / 1e6 / chunks, RoadFeature.SLOWEST.get() / 1e6, loadavg()));
					say(String.format("%s feature calls %d (all chunks): mean %.3f ms", order, RoadFeature.CALLS.get(), RoadFeature.CALLS.get() == 0 ? 0 : RoadFeature.CALL_NANOS.get() / 1e6 / RoadFeature.CALLS.get()));
				});
				if (first == null) first = picked.get();
				else if (!first.targets().stream().map(Target::id).toList().equals(picked.get().targets().stream().map(Target::id).toList())) problem("a fresh world of the same seed chose other routes");
				if (takeShots) screenshots(context, world, first);
			} finally {
				RoadFeature.MUTANT_GROUND = false;
				RoadFeature.LEGACY_CLEAR = false;
				RoadFeature.RECORD_STATS = false;
			}
		}
		say("ADR TC: columns with a face over 3 blocks beside a drawn neighbour (last order): " + faceShare());
		say("foreign structures seen near the roads: " + RoadFeature.FOREIGN_SEEN);
		say("DIGESTS " + digests);
		try {
			Path out = Path.of("roads-results.txt");
			Files.writeString(out, OUT.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		String ref = digests.get("asc");
		if (digests.containsKey("nojoin") && digests.get("nojoin").equals(ref)) problem("the road is the same with the village join off: the join changed nothing");
		for (String o : List.of("desc", "shuffled", "shuffled-evicting")) diff("asc", o, columns.get("asc"), columns.get(o));
		for (String o : List.of("desc", "shuffled", "shuffled-evicting")) if (cellsByOrder.containsKey(o)) cellDiff("asc", o, cellsByOrder.get("asc"), cellsByOrder.get(o), roadsByOrder.get("asc"), roadsByOrder.get(o));
		for (String o : List.of("desc", "shuffled", "shuffled-evicting")) {
			if (!ref.equals(digests.get(o))) problem("road digest differs in order " + o + ": " + digests);
		}
		if (digests.containsKey("mutant-asc") && digests.get("mutant-asc").equals(digests.get("mutant-shuffled")) && digests.get("mutant-asc").equals(ref)) {
			problem("the leaf-ground mutant was not caught: " + digests);
		}
		try {
			Files.writeString(Path.of("roads-results.txt"), OUT.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (!PROBLEMS.isEmpty()) throw new AssertionError("road test: " + PROBLEMS);
	}


	/** Cameras for a joined village (VILLROAD): {x, z, dirX, dirZ} each. */
	static volatile double[] VILLAGE_VIEW;
	/** The south edge of the village's pieces: the overview stands off beyond it. */
	static volatile double[] VILLAGE_OVERVIEW;
	static volatile double[] BEND_VIEW;
	static volatile double[] SPUR_VIEW;

	static void villageView(WorldPlanner planner, VillageChecks.Villages vil, boolean set) {
		if (!set) return;
		for (var f : vil.found()) {
			var r = dev.fractalbuildings.mod.roads.VillageRoads.resultOf(planner, f);
			if (r.removed().isEmpty() || r.spur().isEmpty()) continue;
			var v = f.village();
			double sx = 0, sz = 0;
			int maxZ = Integer.MIN_VALUE;
			for (var b : v.pieces()) { sx += (b.minX() + b.maxX()) / 2.0; sz += (b.minZ() + b.maxZ()) / 2.0; maxZ = Math.max(maxZ, b.maxZ()); }
			VILLAGE_VIEW = new double[] {sx / v.pieces().size(), sz / v.pieces().size()};
			VILLAGE_OVERVIEW = new double[] {sx / v.pieces().size(), maxZ};
			if (!r.added().isEmpty()) { // along the bend: from 30 blocks back on the line it leaves, toward the line it rejoins
				var a = r.added().get(0);
				var z = r.added().get(r.added().size() - 1);
				BEND_VIEW = new double[] {a.x(), a.z(), z.x() - a.x(), z.z() - a.z()};
			}
			if (!r.ends().isEmpty()) { // the spur mouth: from the road side toward the street end
				int[] m = r.spur().get(0), e = r.ends().get(0);
				SPUR_VIEW = new double[] {m[0], m[1], e[0] - m[0], e[1] - m[1]};
			}
			return;
		}
	}

	static String faceShare() {
		long face = 0, all = 0;
		for (int[] v : RoadFeature.STATS.values()) { face += v[5]; all += v[6]; }
		return face + " of " + all + String.format(" (%.1f %%)", all == 0 ? 0 : 100.0 * face / all);
	}

	/**
	 * ORDERFAIL: where two orders' recordings differ, cell by cell: road cells (the road feature's) or district cells (TilePiece), by chunk.
	 * The road digest covers every recorded cell, so a district piece placed in one order and not in another changes it although every
	 * road column is equal.
	 */
	/** The cells of the corridor chunks and their 8 neighbours (the chunks the corridor's generation decorates, in every order). */
	static java.util.TreeMap<Long, Integer> corridorCells(Map<Long, Integer> all, List<ChunkPos> corridor) {
		java.util.TreeSet<Long> chunks = new java.util.TreeSet<>();
		for (ChunkPos c : corridor) for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) chunks.add(((long) (c.x() + dx) << 32) ^ ((c.z() + dz) & 0xffffffffL));
		java.util.TreeMap<Long, Integer> out = new java.util.TreeMap<>();
		for (var e : all.entrySet()) {
			long k = e.getKey();
			if (chunks.contains(((long) Math.floorDiv(PieceLedger.x(k), 16) << 32) ^ (Math.floorDiv(PieceLedger.z(k), 16) & 0xffffffffL))) out.put(k, e.getValue());
		}
		return out;
	}

	static void cellDiff(String a, String b, Map<Long, Integer> ca, Map<Long, Integer> cb, java.util.Set<Long> ra, java.util.Set<Long> rb) {
		TreeMap<String, int[]> chunks = new TreeMap<>();
		int road = 0, district = 0, n = 0;
		java.util.TreeSet<Long> keys = new java.util.TreeSet<>(ca.keySet());
		keys.addAll(cb.keySet());
		for (long k : keys) {
			Integer va = ca.get(k), vb = cb.get(k);
			if (java.util.Objects.equals(va, vb)) continue;
			boolean isRoad = ra.contains(k) || rb.contains(k);
			if (isRoad) road++; else district++;
			chunks.computeIfAbsent(Math.floorDiv(PieceLedger.x(k), 16) + "," + Math.floorDiv(PieceLedger.z(k), 16), c -> new int[2])[isRoad ? 0 : 1]++;
			if (n++ < 15) say("CELLDIFF " + a + " vs " + b + " at " + PieceLedger.x(k) + "," + PieceLedger.y(k) + "," + PieceLedger.z(k) + (isRoad ? " road" : " district") + ": " + state(va) + " vs " + state(vb));
		}
		StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (var e : chunks.entrySet()) if (shown++ < 20) sb.append(' ').append(e.getKey()).append(" road ").append(e.getValue()[0]).append(" district ").append(e.getValue()[1]).append(';');
		say("cells that differ between " + a + " and " + b + ": " + (road + district) + " (road " + road + ", district " + district + ") in " + chunks.size() + " chunks:" + sb);
	}

	private static String state(Integer id) {
		return id == null ? "absent" : String.valueOf(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(id));
	}

	/** Says where two orders drew a column differently (the first 25), to find the cause of a digest that differs. */
	static void diff(String a, String b, Map<Long, String> ca, Map<Long, String> cb) {
		int n = 0, total = 0;
		for (var e : ca.entrySet()) {
			String other = cb.get(e.getKey());
			if (e.getValue().equals(other)) continue;
			total++;
			if (n++ < 25) say("DIFF " + a + " vs " + b + " at " + PieceLedger.x(e.getKey()) + "," + PieceLedger.z(e.getKey()) + ": [" + e.getValue() + "] vs [" + other + "]");
		}
		for (Long k : cb.keySet()) if (!ca.containsKey(k)) { total++; if (n++ < 25) say("DIFF " + a + " vs " + b + " only in " + b + ": " + PieceLedger.x(k) + "," + PieceLedger.z(k) + " [" + cb.get(k) + "]"); }
		say("columns that differ between " + a + " and " + b + ": " + total + " of " + ca.size());
	}

	/** The world against the plan: deck blocks as the ledger says, nothing over water but crossings, nothing inside a settlement. */
	static void readBack(ServerLevel level, WorldPlanner planner, Picked p, VillageChecks.Villages vil, Map<Long, Integer> ledger) {
		RoadNet net = planner.roads();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int cols = 0, deckOk = 0, overWater = 0, overWaterNoSpan = 0, inSettlement = 0, headBlocked = 0, solidUnder = 0, onLedger = 0;
		int step = 0;
		TreeMap<String, Integer> blockers = new TreeMap<>();
		List<String> missing = new ArrayList<>();
		for (ChunkPos c : p.corridor()) {
			for (Col col : VillageChecks.columns(planner, vil, c)) {
				cols++;
				int y = col.y() - 1;
				pos.set(col.x(), y, col.z());
				BlockState s = level.getBlockState(pos);
				Integer want = ledger.get(PieceLedger.pack(col.x(), y, col.z()));
				if (want == null && missing.size() < 5) missing.add(col.x() + "," + col.z());
				if (want != null) {
					onLedger++;
					if (want == Block.BLOCK_STATE_REGISTRY.getId(s)) deckOk++;
				}
				if (!s.isAir() && s.getFluidState().isEmpty()) solidUnder++;
				if (!level.getFluidState(pos.set(col.x(), y - 1, col.z())).isEmpty()) {
					overWater++;
					if (!col.crossing()) overWaterNoSpan++;
				}
				for (int h = 1; h <= 3; h++) {
					BlockState above = level.getBlockState(pos.set(col.x(), y + h, col.z()));
					if (!above.isAir()) {
						blockers.merge(above.getBlock().getDescriptionId(), 1, Integer::sum);
						if (!(above.is(BlockTags.LEAVES) || above.is(BlockTags.LOGS) || above.canBeReplaced() || above.getCollisionShape(level, pos).isEmpty())) headBlocked++;
						break;
					}
				}
				if ((step++ & 3) == 0 && !net.settlementsNear(col.x(), col.z(), 0).isEmpty()) inSettlement++;
			}
		}
		say(String.format("read back: %d road columns, %d on the ledger and %d equal to it, %d with a solid deck block, %d over water (%d outside a crossing), "
			+ "%d with a block in the 3 above, %d inside a settlement", cols, onLedger, deckOk, solidUnder, overWater, overWaterNoSpan, headBlocked, inSettlement));
		say("blocks in the 3 above the deck: " + blockers);
		// ROADCLEAR: a snow layer with air under it in a corridor chunk (nothing natural makes one; the clear of a road did, 2113 times in the playtest world)
		int floating = 0;
		List<String> floatAt = new ArrayList<>();
		for (ChunkPos c : p.corridor()) {
			for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
				int x = c.getMinBlockX() + dx, z = c.getMinBlockZ() + dz;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int y = top; y > top - 40 && y > level.getMinY() + 1; y--) {
					if (level.getBlockState(pos.set(x, y, z)).is(Blocks.SNOW) && level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
						floating++;
						if (floatAt.size() < 5) floatAt.add(x + "," + y + "," + z);
					}
				}
			}
		}
		say("floating snow layers in the corridor chunks: " + floating + " " + floatAt);
		if (floating > 0 && !RoadFeature.LEGACY_CLEAR) problem("snow layers floating over the roads: " + floating + " " + floatAt);
		if (cols < 800) problem("only " + cols + " road columns in the corridor");
		int unclaimed = cols - onLedger;
		if (unclaimed > 0) say("road columns the feature did not draw (a foreign structure or a district has them): " + unclaimed + ", e.g. " + missing + "; foreign structures seen: " + RoadFeature.FOREIGN_SEEN);
		if (unclaimed * 50 > cols || (unclaimed > 0 && RoadFeature.FOREIGN_SEEN.isEmpty())) problem("road columns the feature did not claim: " + unclaimed);
		if (deckOk != onLedger) problem("deck blocks that differ from the ledger: " + (onLedger - deckOk));
		if (solidUnder < onLedger) problem("deck blocks that are air or fluid: " + (onLedger - solidUnder));
		if (overWaterNoSpan * 50 > cols) problem("too many road columns over water outside a crossing: " + overWaterNoSpan + " of " + cols);
		if (inSettlement != 0) problem("road columns inside a settlement: " + inSettlement);
		if (headBlocked * 100 > cols) problem("something other than growth (leaves, logs, plants) in the 3 blocks above " + headBlocked + " of " + cols + " road columns");
	}

	static void screenshots(ClientGameTestContext context, TestSingleplayerContext world, Picked p) {
		List<double[]> views = new ArrayList<>(); // {x, z, dirx, dirz, name index}
		List<String> names = new ArrayList<>();
		// terrace: the chunk with the most terrace columns outside forests and foreign structures (trees hide the look); bridge: the
		// middle of the first crossing; country: a stretch of the plain route with no foreign structure beside it
		List<Map.Entry<Long, int[]>> byTerraces = new ArrayList<>(RoadFeature.STATS.entrySet());
		byTerraces.sort((x, y) -> x.getValue()[2] != y.getValue()[2] ? Integer.compare(y.getValue()[2], x.getValue()[2]) : Long.compare(x.getKey(), y.getKey()));
		long bestKey = byTerraces.isEmpty() ? 0 : byTerraces.get(0).getKey();
		int bestTerraces = byTerraces.isEmpty() ? -1 : byTerraces.get(0).getValue()[2];
		boolean[] found = {false};
		for (int i = 0; i < Math.min(40, byTerraces.size()) && !found[0]; i++) {
			long key = byTerraces.get(i).getKey();
			if (byTerraces.get(i).getValue()[2] < 30 || byTerraces.get(i).getValue()[4] != 0) continue;
			String[] biome = new String[1];
			world.getServer().runOnServer(server -> biome[0] = server.overworld().getBiome(new BlockPos(((int) key) * 16 + 8, 80, ((int) (key >> 32)) * 16 + 8)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
			String b = biome[0];
			if (b.contains("forest") || b.contains("taiga") || b.contains("jungle") || b.contains("swamp") || b.contains("grove")) continue;
			bestKey = key;
			bestTerraces = byTerraces.get(i).getValue()[2];
			found[0] = true;
			say("terrace chunk " + (int) key + "," + (int) (key >> 32) + " in " + b);
		}
		say("terrace columns in the chosen chunk: " + bestTerraces + " at chunk " + (int) bestKey + "," + (int) (bestKey >> 32) + (found[0] ? "" : " (every candidate was forest or beside a structure)"));
		for (Target t : p.targets()) {
			if (t.name().equals("bridge") && !t.crossings().isEmpty()) {
				int[] c = t.crossings().get(0);
				int k = (c[0] + c[1]) / 2;
				double[] a = t.stations()[Math.max(0, k - 1)], b = t.stations()[Math.min(t.stations().length - 1, k + 1)];
				views.add(new double[] {t.stations()[k][0], t.stations()[k][1], b[0] - a[0], b[1] - a[1]});
				names.add("bridge");
			}
		}
		for (Target t : p.targets()) {
			int bestK = -1;
			double bestD = 1e18;
			for (int k = 1; k < t.stations().length - 1; k++) {
				double dx = ((int) bestKey) * 16 + 8 - t.stations()[k][0], dz = (bestKey >> 32) * 16 + 8 - t.stations()[k][1];
				if (dx * dx + dz * dz < bestD) { bestD = dx * dx + dz * dz; bestK = k; }
			}
			if (bestD < 24 * 24) {
				double[] a = t.stations()[bestK - 1], b = t.stations()[bestK + 1];
				views.add(new double[] {t.stations()[bestK][0], t.stations()[bestK][1], b[0] - a[0], b[1] - a[1]});
				names.add("terrace");
				break;
			}
		}
		Target plain = p.targets().stream().filter(t -> t.name().equals("plain")).findFirst().orElse(p.targets().get(0));
		int mk = plain.stations().length / 2;
		for (int off = 0; off < plain.stations().length / 2 - 4; off++) { // the stretch nearest the middle with no foreign structure in its chunk
			boolean ok = true;
			for (int dk = -1; dk <= 1; dk++) {
				double[] st = plain.stations()[mk + dk + (off % 2 == 0 ? off / 2 : -(off + 1) / 2)];
				int[] sv = RoadFeature.STATS.get((((long) Math.floorDiv((int) st[1], 16)) << 32) | (Math.floorDiv((int) st[0], 16) & 0xffffffffL));
				if (sv == null || sv[4] != 0) ok = false;
			}
			if (ok) { mk = mk + (off % 2 == 0 ? off / 2 : -(off + 1) / 2); break; }
		}
		views.add(new double[] {plain.stations()[mk][0], plain.stations()[mk][1], plain.stations()[mk + 1][0] - plain.stations()[mk - 1][0], plain.stations()[mk + 1][1] - plain.stations()[mk - 1][1]});
		names.add("country");
		context.runOnClient(client -> client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
		world.getServer().runCommand("gamemode spectator @p");
		double[] vo = VILLAGE_OVERVIEW;
		if (vo != null) villageShot(context, world, "road-village", vo[0], vo[1] + 40, 0, -1, 50, 40); // the whole village from the south, outside it
		double[] bv = BEND_VIEW, sv = SPUR_VIEW;
		if (bv != null) { // along the road through the bend, 22 up, from 30 blocks back
			double l = Math.hypot(bv[2], bv[3]);
			villageShot(context, world, "road-village-bend", bv[0] - bv[2] / l * 30, bv[1] - bv[3] / l * 30, bv[2] / l, bv[3] / l, 22, 30);
		}
		if (sv != null) { // at the spur mouth, looking toward the street end, 22 up
			double l = Math.hypot(sv[2], sv[3]);
			villageShot(context, world, "road-village-spur", sv[0] - sv[2] / l * 14, sv[1] - sv[3] / l * 14, sv[2] / l, sv[3] / l, 22, 35);
		}
		for (int i = 0; i < views.size(); i++) {
			double[] v = views.get(i);
			double len = Math.hypot(v[2], v[3]);
			double dx = v[2] / len, dz = v[3] / len;
			// two shots each: along the road from behind, and from the side and above
			for (int s = 0; s < 2; s++) {
				double back = s == 0 ? 30 : 4, side = s == 0 ? 0 : 26;
				double cx = v[0] - dx * back - dz * side, cz = v[1] - dz * back + dx * side;
				int[] y = new int[1];
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					level.getChunkSource().getChunk((int) Math.floor(cx) >> 4, (int) Math.floor(cz) >> 4, ChunkStatus.FULL, true);
					y[0] = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(cx), (int) Math.floor(cz));
				});
				float yaw = (float) Math.toDegrees(Math.atan2(s == 0 ? -dx : -(v[0] - cx), s == 0 ? dz : (v[1] - cz)));
				world.getServer().runCommand(String.format("tp @p %.1f %d %.1f %.1f %d", cx, y[0] + (s == 0 ? 22 : 30), cz, yaw, s == 0 ? 30 : 42));
				// a far teleport is held until the terrain there is FULL (TPHOLD): wait until the client player stands at the camera, or the shot shows "Generating terrain..."
				final double[] want = {cx, y[0] + (s == 0 ? 22 : 30), cz};
				for (int w = 0; w < 120; w++) {
					context.waitTicks(20);
					java.util.concurrent.atomic.AtomicBoolean there = new java.util.concurrent.atomic.AtomicBoolean();
					context.runOnClient(client -> there.set(client.player != null && client.player.distanceToSqr(want[0], want[1], want[2]) < 9));
					if (there.get()) break;
				}
				try {
					world.getConnection().waitForChunksRender();
				} catch (AssertionError e) {
					say("chunks did not finish rendering in time; taking the screenshot anyway");
				}
				context.waitTicks(20);
				context.takeScreenshot("road-" + names.get(i) + "-" + (s == 0 ? "along" : "side"));
				say("screenshot road-" + names.get(i) + "-" + (s == 0 ? "along" : "side") + " at " + (int) v[0] + "," + (int) v[1]);
			}
		}
	}

	/** One village shot: the camera at (cx, cz), {@code up} above the ground, looking along (dx, dz) pitched down {@code pitch} degrees. */
	static void villageShot(ClientGameTestContext context, TestSingleplayerContext world, String name, double cx, double cz, double dx, double dz, int up, int pitch) {
		int[] y = new int[1];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunkSource().getChunk((int) Math.floor(cx) >> 4, (int) Math.floor(cz) >> 4, ChunkStatus.FULL, true);
			y[0] = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(cx), (int) Math.floor(cz));
		});
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		world.getServer().runCommand(String.format("tp @p %.1f %d %.1f %.1f %d", cx, y[0] + up, cz, yaw, pitch));
		final double[] want = {cx, y[0] + up, cz};
		for (int w = 0; w < 120; w++) {
			context.waitTicks(20);
			java.util.concurrent.atomic.AtomicBoolean there = new java.util.concurrent.atomic.AtomicBoolean();
			context.runOnClient(client -> there.set(client.player != null && client.player.distanceToSqr(want[0], want[1], want[2]) < 9));
			if (there.get()) break;
		}
		try { world.getConnection().waitForChunksRender(); } catch (AssertionError e) { say("chunks did not finish rendering in time; taking the screenshot anyway"); }
		context.waitTicks(20);
		context.takeScreenshot(name);
		say("screenshot " + name + " at " + (int) cx + "," + (int) cz + " looking " + String.format("%.2f,%.2f", dx, dz));
	}
}
