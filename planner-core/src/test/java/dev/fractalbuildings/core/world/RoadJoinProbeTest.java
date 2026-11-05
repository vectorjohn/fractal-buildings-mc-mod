package dev.fractalbuildings.core.world;

import static dev.fractalbuildings.core.world.JoinWorld.districtTops;
import static dev.fractalbuildings.core.world.JoinWorld.key;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 6.15 ROADJOIN diagnosis probe (lead-coder 2026-10-27; a tool, not a regression test, so it only runs when {@code FB_ROADJOIN} is
 * set). Plans the user's playtest world 1709797029209580947 on the committed fixtures ({@code FB_ROADJOIN=1}), or on dumps in a directory
 * ({@code FB_ROADJOIN=DIR}, files {@code pt-hamlet-s2, pt-metro-s8, pt-world-s64}, finest first, any missing skipped), and prints:
 * <ul>
 *   <li>{@code probe}: every approach of the settlements of {@code FB_ROADJOIN_CELLS} ("3,1;1,2", or "all" for the cells -1..4 x -1..5)
 *       measured by {@link JoinWorld#joins}; with {@code FB_ROADJOIN_GRID=1} a height map around each exit;
 *   <li>{@code at}: {@code FB_ROADJOIN_AT="x,z[,r];.."} a height map of every road column, district surface and water around each point;
 *   <li>{@code map}: {@code FB_ROADJOIN_MAP="x,z,r,step;.."} a coarse map, the skeleton roads, gates and approaches, and the tile there;
 *   <li>{@code crossings}: {@code FB_ROADJOIN_CROSS=1} every graph-road crossing's deck against the stations' ground.
 * </ul>
 * Run: {@code FB_ROADJOIN=1 FB_ROADJOIN_CELLS=all scripts/dev ./gradlew :planner-core:test --rerun --tests '*RoadJoinProbeTest.probe' -i}
 * (env vars are not task inputs: keep {@code --rerun}).
 */
@EnabledIfEnvironmentVariable(named = "FB_ROADJOIN", matches = ".+")
class RoadJoinProbeTest {
	static final long SEED = JoinWorld.PT_SEED;
	static TerrainProbe T;

	static WorldPlanner planner() {
		String env = System.getenv("FB_ROADJOIN");
		WorldPlanner w;
		Path dir = Path.of(env);
		if (env.equals("w12345")) w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT); // world 12345 at 64 blocks (WorldPlannerRoadsTest)
		else if (Files.isDirectory(dir)) {
			List<String> names = new java.util.ArrayList<>();
			for (String n : new String[] {"pt-hamlet-s2", "pt-metro-s8", "pt-world-s64"}) if (Files.exists(dir.resolve(n + ".json.gz"))) names.add(n);
			w = new WorldPlanner(SEED, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(dir, names.toArray(new String[0]))), null, WorldPlanner.Caches.DEFAULT);
		} else w = JoinWorld.playtest();
		T = w.probes().exact();
		return w;
	}

	@Test
	void probe() {
		WorldPlanner w = planner();
		RoadNet net = w.roads();
		String focus = System.getenv().getOrDefault("FB_ROADJOIN_CELLS", System.getenv("FB_ROADJOIN").equals("w12345") ? "3,7;-11,-3;10,1;2,1;4,0" : "3,1;1,2");
		List<int[]> cells = new java.util.ArrayList<>();
		if (focus.equals("all")) {
			for (int cz = -1; cz <= 5; cz++) for (int cx = -1; cx <= 4; cx++) cells.add(new int[] {cx, cz});
		} else for (String c : focus.split(";")) { String[] f = c.split(","); cells.add(new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1])}); }
		java.util.TreeSet<String> done = new java.util.TreeSet<>();
		for (int[] c : cells) {
			var so = net.settlement(new CellKey(c[0], c[1]));
			if (so.isEmpty()) continue;
			SettlementSite site = w.siteOf(new Cell(c[0], c[1])).orElse(null);
			if (site == null || !done.add(site.id())) continue;
			System.out.println("== " + site.id() + " " + site.tier() + " r" + site.radius() + " at " + site.centre() + " ports " + net.portsOf(so.get()));
			for (JoinWorld.Join j : JoinWorld.joins(w, c[0], c[1])) {
				System.out.println("  " + j);
				if (System.getenv("FB_ROADJOIN_GRID") != null) {
					for (WorldPlanner.Approach a : w.drawnApproaches(site, so.get())) {
						if (!a.road().id().equals(j.id())) continue;
						TreeMap<Long, Col> drawn = new TreeMap<>();
						java.util.TreeSet<Long> chunks = new java.util.TreeSet<>();
						for (Col k : a.columns()) {
							chunks.add(key(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16)));
							boolean[] m = w.districtColumns(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16));
							if (m == null || !m[(k.z() & 15) * 16 + (k.x() & 15)]) drawn.put(key(k.x(), k.z()), k);
						}
						grid(w, j.exit().x(), j.exit().z(), 20, drawn, districtTops(w, chunks));
					}
				}
			}
		}
	}

	/** A map around (x, z): per column, A/S/B/P + top y mod 100, or the ground top mod 100 in lower case dots. */
	static void grid(WorldPlanner w, int cx, int cz, int r, TreeMap<Long, Col> drawn, TreeMap<Long, int[]> d) {
		System.out.println("    grid around " + cx + "," + cz + " (rows z, cols x from " + (cx - r) + "; A approach, S street, B deck, P plaza, . ground; value = top y)");
		for (int z = cz - r; z <= cz + r; z++) {
			StringBuilder sb = new StringBuilder(String.format("    %5d ", z));
			for (int x = cx - r; x <= cx + r; x++) {
				Col k = drawn.get(key(x, z));
				int[] t = d.get(key(x, z));
				if (k != null) sb.append('A').append(String.format("%02d", (k.y() - 1) % 100));
				else if (t != null) sb.append((char) t[0]).append(String.format("%02d", t[1] % 100));
				else sb.append('.').append(String.format("%02d", (T.floorY(x, z) - 1) % 100));
			}
			System.out.println(sb);
		}
	}

	/** FB_ROADJOIN_AT="x,z[,r];..": a map of every road column (R graph road, A approach; X a crossing deck), district surfaces and water around each point. */
	@Test
	void at() {
		String spec = System.getenv("FB_ROADJOIN_AT");
		if (spec == null) return;
		WorldPlanner w = planner();
		RoadNet net = w.roads();
		for (String one : spec.split(";")) {
			String[] f = one.split(",");
			int cx = Integer.parseInt(f[0]), cz = Integer.parseInt(f[1]), r = f.length > 2 ? Integer.parseInt(f[2]) : 20;
			TreeMap<Long, Col> all = new TreeMap<>(), graph = new TreeMap<>();
			java.util.TreeSet<Long> chunks = new java.util.TreeSet<>();
			for (int z = Math.floorDiv(cz - r, 16); z <= Math.floorDiv(cz + r, 16); z++) for (int x = Math.floorDiv(cx - r, 16); x <= Math.floorDiv(cx + r, 16); x++) {
				chunks.add(key(x, z));
				for (Col k : w.roadColumns(x, z)) all.put(key(k.x(), k.z()), k);
				for (Col k : net.query(x, z)) graph.put(key(k.x(), k.z()), k);
			}
			TreeMap<Long, int[]> d = districtTops(w, chunks);
			System.out.println("== at " + cx + "," + cz + " settlements near: " + net.settlementsNear(cx, cz, 200));
			for (var e : net.edgesNearCell(new CellKey(Math.floorDiv(cx, 1024), Math.floorDiv(cz, 1024)))) {
				var rt = net.route(e);
				for (var cr : rt.profile.crossings()) {
					double[] s0 = rt.stations[cr.from()], s1 = rt.stations[cr.to()];
					if (Math.hypot(s0[0] - cx, s0[1] - cz) < 200 || Math.hypot(s1[0] - cx, s1[1] - cz) < 200)
						System.out.printf("   route %s crossing stations %d..%d (%.0f,%.0f)-(%.0f,%.0f) len %d deck %d (deck surface y; bank decks %d / %d, raw ground there %d / %d)%n", e, cr.from(), cr.to(), s0[0], s0[1], s1[0], s1[1], cr.length(), cr.deck(), rt.profile.deck()[cr.from()], rt.profile.deck()[cr.to()], T.surfaceY((int) s0[0], (int) s0[1]), T.surfaceY((int) s1[0], (int) s1[1]));
				}
			}
			System.out.println("    (R graph road, A approach, X crossing deck, S street, B district deck, P plaza, ~ water, . ground; value = top y mod 100)");
			for (int z = cz - r; z <= cz + r; z++) {
				StringBuilder sb = new StringBuilder(String.format("    %5d ", z));
				for (int x = cx - r; x <= cx + r; x++) {
					Col k = all.get(key(x, z));
					int[] t = d.get(key(x, z));
					boolean[] m = w.districtColumns(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
					boolean masked = m != null && m[(z & 15) * 16 + (x & 15)];
					if (t != null) sb.append((char) t[0]).append(String.format("%02d", t[1] % 100));
					else if (k != null && !masked) sb.append(k.crossing() ? 'X' : graph.containsKey(key(x, z)) ? 'R' : 'A').append(String.format("%02d", (k.y() - 1) % 100));
					else if (T.waterDepth(x, z) > 0) sb.append('~').append(String.format("%02d", (T.surfaceY(x, z) - 1) % 100));
					else sb.append(masked ? ',' : '.').append(String.format("%02d", (T.floorY(x, z) - 1) % 100));
				}
				System.out.println(sb);
			}
		}
	}

	/** FB_ROADJOIN_MAP="x,z,r,step;..": one char per sampled column (S street, B deck, P plaza, A approach, R graph road, ~ water, , district-masked, . ground, # building lot), and the skeleton roads and gates near. */
	@Test
	void map() {
		String spec = System.getenv("FB_ROADJOIN_MAP");
		if (spec == null) return;
		WorldPlanner w = planner();
		RoadNet net = w.roads();
		for (String one : spec.split(";")) {
			String[] f = one.split(",");
			int cx = Integer.parseInt(f[0]), cz = Integer.parseInt(f[1]), r = Integer.parseInt(f[2]), st = Integer.parseInt(f[3]);
			TreeMap<Long, Col> all = new TreeMap<>(), graph = new TreeMap<>();
			java.util.TreeSet<Long> chunks = new java.util.TreeSet<>();
			for (int z = Math.floorDiv(cz - r, 16); z <= Math.floorDiv(cz + r, 16); z++) for (int x = Math.floorDiv(cx - r, 16); x <= Math.floorDiv(cx + r, 16); x++) {
				chunks.add(key(x, z));
				for (Col k : w.roadColumns(x, z)) all.put(key(k.x(), k.z()), k);
				for (Col k : net.query(x, z)) graph.put(key(k.x(), k.z()), k);
			}
			TreeMap<Long, int[]> d = districtTops(w, chunks);
			System.out.println("== map " + one);
			Cell cell = dev.fractalbuildings.core.settlement.CellGeometry.owner(new dev.fractalbuildings.core.region.RegionField(SEED, 1024), cx, cz);
			var site = w.siteOf(cell);
			if (site.isPresent()) {
				var sk = w.source(site.get()).skeleton(cell);
				for (var rd : sk.roads()) { var pts = rd.points(); var e = pts.get(pts.size() - 1); if (Math.abs(e.x() - cx) <= r && Math.abs(e.z() - cz) <= r) System.out.println("   skeleton road " + rd.id() + " " + rd.kind() + " w" + rd.width() + " " + pts + " bridges " + rd.bridges()); }
				for (var g : sk.gates()) if (Math.abs(g.x() - cx) <= r && Math.abs(g.z() - cz) <= r) System.out.println("   gate " + g);
				for (var a : sk.approaches()) System.out.println("   approach " + a.id() + " " + a.points());
				var tid = w.grid().tileOf(cx, cz);
				System.out.println("   tile " + tid + " planned " + w.tile(tid.ix(), tid.iz()).isPresent());
				{ var tt = w.grid().tile(tid.ix(), tid.iz()); var oc = dev.fractalbuildings.core.settlement.CellGeometry.owner(new dev.fractalbuildings.core.region.RegionField(SEED, 1024), (tt.minX() + tt.maxX()) / 2, (tt.minZ() + tt.maxZ()) / 2); System.out.println("   tile box " + tt.minX() + "," + tt.minZ() + ".." + tt.maxX() + "," + tt.maxZ() + " centre cell " + oc + " site of it " + w.siteOf(oc).map(SettlementSite::id) + " owns(metro src) " + dev.fractalbuildings.core.settlement.Districts.owns(w.source(site.get()), tid.ix(), tid.iz()) + " network planned " + w.source(site.get()).network().planned(tid.ix(), tid.iz())); }
				if (w.tile(tid.ix(), tid.iz()).isPresent()) { var pl = w.plan(tid.ix(), tid.iz()); System.out.println("   notes " + pl.notes()); for (var s2 : pl.plan().streets()) System.out.println("   street " + s2.id() + " " + s2.kind() + " pts " + s2.points().size() + " first " + s2.points().get(0) + " last " + s2.points().get(s2.points().size() - 1)); }
			}
			for (int z = cz - r; z <= cz + r; z += st) {
				StringBuilder sb = new StringBuilder(String.format("    %5d ", z));
				for (int x = cx - r; x <= cx + r; x += st) {
					Col k = all.get(key(x, z));
					int[] t = d.get(key(x, z));
					boolean[] m = w.districtColumns(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
					boolean masked = m != null && m[(z & 15) * 16 + (x & 15)];
					char c;
					if (x == cx && z == cz) c = '@';
					else if (t != null) c = (char) t[0];
					else if (k != null && !masked) c = graph.containsKey(key(x, z)) ? 'R' : 'A';
					else if (masked) c = ',';
					else if (T.waterDepth(x, z) > 0) c = '~';
					else c = '.';
					sb.append(c);
				}
				System.out.println(sb);
			}
		}
	}

	/** Every graph-road crossing in the fine window: deck vs the natural ground of the stations around it (a deck far above the banks needs fill: a berm). */
	@Test
	void crossings() {
		if (System.getenv("FB_ROADJOIN_CROSS") == null) return;
		WorldPlanner w = planner();
		RoadNet net = w.roads();
		java.util.TreeSet<RoadNet.EdgeKey> edges = new java.util.TreeSet<>();
		for (int cz = -1; cz <= 5; cz++) for (int cx = -1; cx <= 4; cx++) net.settlement(new CellKey(cx, cz)).ifPresent(s -> edges.addAll(net.edgesOf(s)));
		for (var e : edges) {
			var rt = net.route(e);
			System.out.println("== " + e + " ok " + rt.ok + " touching " + rt.touching + " ports " + java.util.Arrays.toString(rt.portA) + " " + java.util.Arrays.toString(rt.portB) + " len " + Math.round(rt.length) + " " + rt.failure);
			if (!rt.ok || rt.touching) continue;
			int[] dk = rt.profile.deck();
			System.out.println("   end decks " + dk[0] + " / " + dk[dk.length - 1] + " ground " + T.surfaceY(rt.portA[0], rt.portA[1]) + " / " + T.surfaceY(rt.portB[0], rt.portB[1]));
			for (var cr : rt.profile.crossings()) {
				StringBuilder sb = new StringBuilder();
				for (int k = Math.max(0, cr.from() - 4); k <= Math.min(dk.length - 1, cr.to() + 4); k++) {
					int x = (int) Math.floor(rt.stations[k][0] + 0.5), z = (int) Math.floor(rt.stations[k][1] + 0.5);
					sb.append(' ').append(dk[k]).append('/').append(T.floorY(x, z)).append(T.waterDepth(x, z) > 0 ? "w" : "");
				}
				double[] s0 = rt.stations[cr.from()];
				System.out.printf("   crossing at %.0f,%.0f len %d deck %d | deck/ground per station:%s%n", s0[0], s0[1], cr.length(), cr.deck(), sb);
			}
		}
	}
}
