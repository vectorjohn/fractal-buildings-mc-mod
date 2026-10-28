package dev.fractalbuildings.gametest.roads;

import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.VillageJoin;
import dev.fractalbuildings.core.road.VillageJoin.Box;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.roads.RoadFeature;
import dev.fractalbuildings.mod.roads.VillageRoads;
import dev.fractalbuildings.mod.roads.VillageRoads.Found;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * VILLROAD in a live world: finds the vanilla villages near the road corridor (from the structure starts, as the feature does), adds the
 * chunks the joins can touch to the corridor, and checks the drawn road ({@link RoadFeature#COLUMNS}, road and deck kinds) against them:
 * no road column inside a piece box, the road stays as connected as the planner's line in each village's window, the spur is drawn from
 * the road to the village's street.
 */
final class VillageChecks {
	private VillageChecks() {}

	/** Villages meeting the corridor, and the corridor with every chunk their joins can touch. */
	record Villages(List<Found> found, List<ChunkPos> corridor) {}

	static Villages find(ServerLevel level, List<ChunkPos> corridor) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Map<VillageRoads.Key, Found> all = new LinkedHashMap<>();
		Set<Long> seen = new HashSet<>();
		for (ChunkPos c : corridor) {
			for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
				if (!seen.add(((long) (c.z() + dz) << 32) ^ ((c.x() + dx) & 0xffffffffL))) continue;
				for (Found f : VillageRoads.scan((x, z) -> level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true), registry, level, c.x() + dx, c.z() + dz, 0)) all.putIfAbsent(f.key(), f);
			}
		}
		Set<ChunkPos> base = new HashSet<>(corridor);
		List<Found> near = new ArrayList<>();
		Set<ChunkPos> extra = new java.util.TreeSet<>((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
		for (Found f : all.values()) {
			Box inf = VillageJoin.influence(f.village());
			boolean meets = false;
			for (ChunkPos c : corridor) if (inf.meets(new Box(c.getMinBlockX(), c.getMinBlockZ(), c.getMinBlockX() + 15, c.getMinBlockZ() + 15))) { meets = true; break; }
			if (!meets) continue;
			near.add(f);
			for (int cz = inf.minZ() >> 4; cz <= inf.maxZ() >> 4; cz++) for (int cx = inf.minX() >> 4; cx <= inf.maxX() >> 4; cx++) if (!base.contains(new ChunkPos(cx, cz))) extra.add(new ChunkPos(cx, cz));
		}
		List<ChunkPos> out = new ArrayList<>(corridor);
		out.addAll(extra);
		return new Villages(near, out);
	}

	/** The columns of a chunk the way the feature sees them: the planner's with every village's bend applied. */
	static List<Col> columns(WorldPlanner planner, Villages v, ChunkPos c) {
		return VillageRoads.adjusted(v.found(), planner, c, planner.roadColumnsAround(c.x(), c.z())).around().get(4);
	}

	static Set<Long> drawn() {
		Set<Long> s = new HashSet<>();
		for (var e : RoadFeature.COLUMNS.entrySet()) if (e.getValue().startsWith("kind 1 ") || e.getValue().startsWith("kind 2 ")) s.add(VillageJoin.key(PieceLedger.x(e.getKey()), PieceLedger.z(e.getKey())));
		return s;
	}

	private static int components(Set<Long> cells) {
		Set<Long> left = new HashSet<>(cells);
		int n = 0;
		while (!left.isEmpty()) {
			long s = left.iterator().next();
			left.remove(s);
			ArrayDeque<Long> q = new ArrayDeque<>(List.of(s));
			n++;
			while (!q.isEmpty()) {
				long k = q.poll();
				int x = (int) k, z = (int) (k >> 32);
				for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
					long nk = VillageJoin.key(x + dx, z + dz);
					if (left.remove(nk)) q.add(nk);
				}
			}
		}
		return n;
	}

	/** Whether the village's own street block (a vanilla path block, {@link VillageRoads#PATH_BLOCKS}) tops column (x, z) within 1 of height y, with nothing solid on it. */
	static boolean vanillaPathAt(ServerLevel level, int x, int z, int y) {
		for (int dy = -1; dy <= 1; dy++) {
			var pos = new net.minecraft.core.BlockPos(x, y + dy, z);
			var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
			if (VillageRoads.PATH_BLOCKS.contains(id) && !level.getBlockState(pos.above()).isSolid()) return true;
		}
		return false;
	}

	static int surf(long packedXz) {
		String s = RoadFeature.COLUMNS.get(PieceLedger.pack((int) packedXz, 0, (int) (packedXz >> 32)));
		return s == null ? Integer.MIN_VALUE : Integer.parseInt(s.split(" ")[3]);
	}

	/**
	 * The strict spur-end check (VILLROAD r7): the spur's end is drawn, joins the road through drawn cells (4-connected, no gap), and a
	 * drawn spur cell at the end (the end or a band cell beside it) has a 4-neighbour that is the village's own path block: a column the
	 * feature did not draw ({@link RoadFeature#COLUMNS}), topped by a vanilla path block within 1 of the spur's height. Null when it
	 * holds, else the reason.
	 */
	static String spurLink(ServerLevel level, Box inf, Set<Long> drawn, Set<Long> spurCells, int[] end) {
		long ek = VillageJoin.key(end[0], end[1]);
		if (!drawn.contains(ek)) return "the spur end " + end[0] + "," + end[1] + " was not drawn";
		ArrayDeque<Long> q = new ArrayDeque<>(List.of(ek));
		Set<Long> seen = new HashSet<>(q);
		boolean road = false;
		while (!q.isEmpty() && !road) {
			long k = q.poll();
			int x = (int) k, z = (int) (k >> 32);
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				long nk = VillageJoin.key(x + d[0], z + d[1]);
				if (!drawn.contains(nk) || !inf.contains(x + d[0], z + d[1]) || !seen.add(nk)) continue;
				if (!spurCells.contains(nk)) road = true;
				q.add(nk);
			}
		}
		if (!road) return "the spur end " + end[0] + "," + end[1] + " is not joined to the road through drawn cells (a gap)";
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
			long sk = VillageJoin.key(end[0] + dx, end[1] + dz);
			if (!spurCells.contains(sk) || !drawn.contains(sk)) continue;
			int y = surf(sk);
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int vx = end[0] + dx + d[0], vz = end[1] + dz + d[1];
				if (RoadFeature.COLUMNS.containsKey(PieceLedger.pack(vx, 0, vz))) continue; // ours: not the village's street
				if (vanillaPathAt(level, vx, vz, y)) return null;
			}
		}
		return "no vanilla street block (a path block we did not draw, within 1 of the spur's height) beside the spur end " + end[0] + "," + end[1];
	}

	/** {steps over 1, largest step, pairs} between 4-neighbouring drawn spur cells (the surface heights the feature recorded). */
	static int[] spurSteps(VillageJoin.Result r) {
		Map<Long, Integer> y = new java.util.TreeMap<>();
		for (int[] c : r.spur()) {
			String s = RoadFeature.COLUMNS.get(PieceLedger.pack(c[0], 0, c[1]));
			if (s == null || !s.startsWith("kind 1 ")) continue;
			y.put(VillageJoin.key(c[0], c[1]), Integer.parseInt(s.split(" ")[3]));
		}
		int over = 0, max = 0, pairs = 0;
		for (var e : y.entrySet()) {
			int x = (int) (long) e.getKey(), z = (int) (e.getKey() >> 32);
			for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
				Integer n = y.get(VillageJoin.key(x + d[0], z + d[1]));
				if (n == null) continue;
				pairs++;
				int st = Math.abs(n - e.getValue());
				if (st > 1) over++;
				max = Math.max(max, st);
			}
		}
		return new int[] {over, max, pairs};
	}

	/**
	 * A plan view of one village's join as a PNG (4 px a block): piece boxes grey, street boxes ochre, the planner's own line blue,
	 * columns the join removed red, columns it added green, what the feature drew over them as a black dot, the spur band magenta
	 * (its end cells white, the path cell each meets cyan), the streets' path cells dark brown and the margin ring round the pieces as a pale outline. Written to {@code screenshots/<name>.png}.
	 */
	static void planImage(WorldPlanner planner, Found f, String name, List<String> say) {
		try {
			VillageJoin.Village village = f.village();
			VillageJoin.Result r = VillageRoads.resultOf(planner, f);
			Box inf = VillageJoin.influence(village);
			final int S = 4;
			int w = inf.maxX() - inf.minX() + 1, h = inf.maxZ() - inf.minZ() + 1;
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * S, h * S, java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = img.createGraphics();
			g.setColor(new java.awt.Color(0xf2f0e6));
			g.fillRect(0, 0, w * S, h * S);
			for (Box b : village.pieces()) {
				Box m = b.grow(VillageJoin.MARGIN);
				g.setColor(new java.awt.Color(0xe3dcc8));
				g.fillRect((m.minX() - inf.minX()) * S, (m.minZ() - inf.minZ()) * S, (m.maxX() - m.minX() + 1) * S, (m.maxZ() - m.minZ() + 1) * S);
			}
			for (Box b : village.pieces()) cell(g, inf, S, b, new java.awt.Color(0x8a8a8a));
			for (Box b : village.streets()) cell(g, inf, S, b, new java.awt.Color(0xc9a14a));
			for (int[] p : village.paths()) dot(g, inf, S, p[0], p[1], new java.awt.Color(0x7a4a1a)); // the streets' own path blocks (templates)
			Set<Long> removed = r.removed(), added = new HashSet<>();
			for (Col c : r.added()) added.add(VillageJoin.key(c.x(), c.z()));
			for (int cz = inf.minZ() >> 4; cz <= inf.maxZ() >> 4; cz++) for (int cx = inf.minX() >> 4; cx <= inf.maxX() >> 4; cx++)
				for (Col c : planner.roadColumns(cx, cz)) if (inf.contains(c.x(), c.z())) dot(g, inf, S, c.x(), c.z(), removed.contains(VillageJoin.key(c.x(), c.z())) ? new java.awt.Color(0xdd2222) : new java.awt.Color(0x2a5fd0));
			for (Col c : r.added()) dot(g, inf, S, c.x(), c.z(), new java.awt.Color(0x1a9a3a));
			for (int[] c : r.spur()) dot(g, inf, S, c[0], c[1], new java.awt.Color(0xc02ab8));
			for (int[] e : r.ends()) dot(g, inf, S, e[0], e[1], java.awt.Color.WHITE);
			for (int[] m : r.meets()) if (m != null) dot(g, inf, S, m[0], m[1], new java.awt.Color(0x20d0e0));
			Set<Long> drawn = drawn();
			g.setColor(java.awt.Color.BLACK);
			for (long k : drawn) {
				int x = (int) k, z = (int) (k >> 32);
				if (inf.contains(x, z)) g.fillRect((x - inf.minX()) * S + 1, (z - inf.minZ()) * S + 1, 2, 2);
			}
			g.dispose();
			Path out = Path.of("screenshots", name + ".png");
			Files.createDirectories(out.getParent());
			javax.imageio.ImageIO.write(img, "png", out.toFile());
			say.add("plan view " + out + " (" + w * S + "x" + h * S + ", north up): grey pieces, ochre street boxes, dark brown street path cells, blue planned line, red removed, green added, magenta spur, white spur ends, cyan the path cell each end meets, black dots drawn");
		} catch (Exception e) {
			say.add("plan view not written: " + e);
		}
	}

	private static void cell(java.awt.Graphics2D g, Box inf, int s, Box b, java.awt.Color c) {
		g.setColor(c);
		g.fillRect((b.minX() - inf.minX()) * s, (b.minZ() - inf.minZ()) * s, (b.maxX() - b.minX() + 1) * s, (b.maxZ() - b.minZ() + 1) * s);
	}

	private static void dot(java.awt.Graphics2D g, Box inf, int s, int x, int z, java.awt.Color c) {
		g.setColor(c);
		g.fillRect((x - inf.minX()) * s, (z - inf.minZ()) * s, s, s);
	}

	/** Problems (empty when all hold) and a summary line. */
	static List<String> check(ServerLevel level, WorldPlanner planner, Villages v, List<String> say, boolean required) {
		List<String> problems = new ArrayList<>();
		Set<Long> drawn = drawn();
		int bent = 0, spurred = 0, spurCells = 0, spurDrawn = 0, inside = 0, linked = 0;
		Set<Long> allSpur = new HashSet<>(); // spur cells may lie in a street box's margin (they cross it to the path); road columns may not
		for (Found f : v.found()) for (int[] c : VillageRoads.resultOf(planner, f).spur()) allSpur.add(VillageJoin.key(c[0], c[1]));
		for (Found f : v.found()) {
			VillageJoin.Village village = f.village();
			Box inf = VillageJoin.influence(village);
			VillageJoin.Result r = VillageRoads.resultOf(planner, f);
			int in = 0;
			for (long k : drawn) {
				int x = (int) k, z = (int) (k >> 32);
				for (Box b : village.pieces()) if (b.contains(x, z) && (!allSpur.contains(k) || !village.streets().contains(b))) in++;
			}
			inside += in;
			if (in > 0) problems.add(f.key() + ": " + in + " road columns inside a piece box (spur cells: inside a non-street piece)");
			if (!village.streets().isEmpty()) { // the path cells read from the templates are where the world has the village's path blocks (placement and rotation right)
				int onPath = 0, seenPath = 0, topPath = 0, heightSame = 0, heightNear = 0;
				for (int[] p : village.paths()) {
					if (RoadFeature.COLUMNS.containsKey(PieceLedger.pack(p[0], 0, p[1])) || !level.hasChunk(p[0] >> 4, p[1] >> 4)) continue;
					seenPath++;
					int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]);
					if (vanillaPathAt(level, p[0], p[1], h - 1)) onPath++;
					if (f.heights() != null && VillageRoads.PATH_BLOCKS.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new net.minecraft.core.BlockPos(p[0], h - 1, p[1])).getBlock()).toString())) {
						topPath++; // the grading's heights (VillageGround: noise + the village's beard) against the placed path block
						int t = f.heights().top(p[0], p[1]);
						if (t == h - 1) heightSame++;
						if (Math.abs(t - (h - 1)) <= 1) heightNear++;
					}
				}
				say.add(f.key() + ": street path cells from the templates " + village.paths().size() + ", in loaded chunks " + seenPath + ", of them topped by a path block " + onPath + "; the grading's ground heights equal the placed path block's y at " + heightSame + " of " + topPath + " (within 1: " + heightNear + ")");
				if (village.paths().isEmpty()) problems.add(f.key() + ": no street path cells were read from the templates");
				else if (seenPath > 0 && onPath * 10 < seenPath * 6) problems.add(f.key() + ": only " + onPath + " of " + seenPath + " template path cells have a path block in the world (placement or rotation wrong)");
			}
			if (r.isEmpty()) continue;
			if (!r.removed().isEmpty()) bent++;
			if (!r.spur().isEmpty()) spurred++;
			// connectivity in the window: no more pieces than the planner's own line has
			Set<Long> orig = new HashSet<>(), mine = new HashSet<>();
			for (int cz = inf.minZ() >> 4; cz <= inf.maxZ() >> 4; cz++) for (int cx = inf.minX() >> 4; cx <= inf.maxX() >> 4; cx++)
				for (Col c : planner.roadColumns(cx, cz)) if (inf.contains(c.x(), c.z())) orig.add(VillageJoin.key(c.x(), c.z()));
			for (long k : drawn) {
				int x = (int) k, z = (int) (k >> 32);
				if (inf.contains(x, z)) mine.add(k);
			}
			int a = components(orig), b = components(mine);
			say.add(f.key() + ": influence " + inf + ", planned columns " + orig.size() + " in " + a + " piece(s), drawn " + mine.size() + " in " + b + ", removed " + r.removed().size() + ", added " + r.added().size() + ", spur " + r.spur().size());
			if (b > a) problems.add(f.key() + ": the road is in " + b + " pieces in the village window, the planner's line in " + a);
			if (!r.spur().isEmpty()) {
				Set<Long> mineSpur = new HashSet<>();
				for (int[] c : r.spur()) { // every cell of the band: none is in a non-street piece or on the village's path (VillageJoin), so all are drawn
					spurCells++;
					mineSpur.add(VillageJoin.key(c[0], c[1]));
					if (drawn.contains(VillageJoin.key(c[0], c[1]))) spurDrawn++;
				}
				for (int i = 0; i < r.ends().size(); i++) { // the strict end check: drawn, no gap to the road, beside a vanilla path block at its height
					int[] e = r.ends().get(i), m = r.meets().get(i);
					if (m == null) { problems.add(f.key() + ": the spur at " + e[0] + "," + e[1] + " was aimed at a street box, not at the village's path cells (no template paths read)"); continue; }
					String why = spurLink(level, inf, drawn, mineSpur, e);
					if (why != null) problems.add(f.key() + ": " + why);
					else linked++;
					say.add(f.key() + ": spur end " + e[0] + "," + e[1] + " meets path cell " + m[0] + "," + m[1] + (why == null ? ": linked to a vanilla street block" : ": " + why));
				}
				int[] steps = spurSteps(r);
				say.add(f.key() + ": spur steps over 1 block: " + steps[0] + " of " + steps[2] + " neighbouring pairs, largest " + steps[1]);
				if (r.steep() > 0) problems.add(f.key() + ": " + r.steep() + " spur(s) too steep to grade in steps of 1 (DS-31)");
				if (steps[1] > 1) problems.add(f.key() + ": the spur has a step of " + steps[1] + " blocks");
			}
		}
		say.add("villages near the corridor " + v.found().size() + ", bent " + bent + ", spurred " + spurred + ", spur cells drawn " + spurDrawn + " of " + spurCells + ", spur ends linked to a vanilla street " + linked + ", road columns inside a piece box " + inside);
		if (v.found().isEmpty()) { if (required) problems.add("no village near the roads: the test world has nothing to join"); else say.add("no village near the corridor in this world: the village checks have nothing to check"); }
		else if (bent + spurred == 0) { if (required) problems.add("no village was joined"); else say.add("villages near the corridor, none needed a join"); }
		else if (spurred == 0 && required) problems.add("no village got a spur (the road was bent, but nothing joins it to a street)");
		if (spurCells > 0 && spurDrawn * 10 < spurCells * 8) problems.add("only " + spurDrawn + " of " + spurCells + " spur cells were drawn");
		return problems;
	}
}
