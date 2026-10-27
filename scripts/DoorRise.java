// Door-rise baseline for SHOPLOW (docs/plans/shoplow.md, step S0): how far each entrance door stands above the street
// in front of it, per building type, over whole settlements. Dev-time tooling, never shipped; run through
// scripts/door-rise.sh, which builds the CLI and puts its jars on the classpath.
//
// It mirrors `city-check` (plan the settlement, build the scenes of autoScenes(city, N) districts) but builds the
// scenes WITH interiors (exterior scenes drop rooms and openings, so no doors). Per door it measures what the planned
// core check `DoorRise` (S0) is specified to measure, so S9 can compare against the same numbers:
//   - entrance doors: StairGround.doors (exterior doors of the lowest storey with rooms);
//   - rise = door standing level (oy + door.box.y) minus the street level, where the street level is the scene's road,
//     paving or deck surface (max y of those ground boxes, as StairGround.surfaceLevels) at the first column outside the
//     building's lots straight out from the door, else terrain floorY there (source `ground` / `terrain`);
//   - riseFrontY = door level minus the lot brief's frontY (the architect's measure in shoplow-architect.md), when known;
//   - run = the longest run of risers (blocks of descent between level treads; turns ignored, as S0 words it) on the
//     walkable way from the door down to the ground (breadth first, StairGround's walker model, half-block steps; the
//     way ends at the nearest ground, often the lot's own ground, not the street); landing = a level move before the
//     walker is more than half a block below the door's floor (a stoop or porch at door level);
//   - the walker reads the ground through SiteGround (SHOPLOW S2: the cut and the fill are ground);
//   - kind: market (shop whose lowest room storey is above storey 0: the market hall on posts), stilted (any other type
//     like that), else the spec type.
// Usage: java -cp 'cli/build/install/fractal-cli/lib/*' scripts/DoorRise.java --scenes N --out-dir DIR SITE...
//   SITE = label:seed:terrainSpec:X,Z,TIER,RADIUS   or   gates:FILE (every row of the gate-sites table)
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.*;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.*;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class DoorRise {
	record Site(String label, long seed, String terrain, String site) {}

	record Door(String site, String district, String building, String lot, String door, String kind, String style, boolean front, int rise, String source, Integer riseFrontY, int run, boolean landing, boolean reached) {}

	public static void main(String[] args) throws Exception {
		int scenes = 999;
		Path out = null;
		List<Site> sites = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (a.equals("--scenes")) scenes = Integer.parseInt(args[++i]);
			else if (a.equals("--out-dir")) out = Path.of(args[++i]);
			else if (a.startsWith("gates:")) {
				Path tsv = Path.of(a.substring(6));
				List<String> lines = Files.readAllLines(tsv);
				for (String l : lines.subList(1, lines.size())) {
					String[] f = l.split("\t");
					if (f.length < 6) continue;
					sites.add(new Site(f[0], Long.parseLong(f[1]), "recorded:" + tsv.resolveSibling(f[0] + ".json.gz"), f[2] + "," + f[3] + "," + f[4] + "," + f[5]));
				}
			} else {
				String[] f = a.split(":", 2);
				int last = f[1].lastIndexOf(':');
				String head = f[1].substring(0, last);
				int c = head.indexOf(':');
				sites.add(new Site(f[0], Long.parseLong(head.substring(0, c)), head.substring(c + 1), f[1].substring(last + 1)));
			}
		}
		if (out == null || sites.isEmpty()) throw new IllegalArgumentException("usage: --scenes N --out-dir DIR SITE...");
		Files.createDirectories(out);
		List<Door> all = new ArrayList<>();
		List<String> siteRows = new ArrayList<>();
		Map<String, Integer> lotStatus = new TreeMap<>(); // "type status" over every planned district
		for (Site s : sites) {
			long t0 = System.nanoTime();
			ProbeSpecs.Probes p = ProbeSpecs.parse(s.terrain());
			RegionField field = new RegionField(s.seed(), 1024);
			SettlementSite site = fromSite(field, s.site());
			RegionView view = new FixedSiteView(site, field);
			CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);
			SkeletonSource.Direct src = new SkeletonSource.Direct(s.seed(), view, site, coarse);
			CityPlanner.City city = CityPlanner.plan(src, coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
			List<String> ids = CityPlanner.autoScenes(city, scenes);
			List<List<Door>> per = ids.parallelStream().map(id -> {
				DistrictScenes.Built b = CityPlanner.scene(city, id, p.terrain(), p.biome(), false);
				List<Door> ds = new ArrayList<>();
				Map<Long, Integer> surface = surfaceLevels(b.scene());
				Map<String, Integer> frontY = new TreeMap<>();
				for (LotBrief lb : b.plan().lots()) frontY.put(lb.id(), lb.frontY());
				for (PlacedBuilding pb : b.scene().buildings()) ds.addAll(measure(s.label(), id, b.scene(), pb, p.terrain(), surface, frontY));
				return ds;
			}).collect(Collectors.toList());
			int n = 0;
			for (List<Door> l : per) { all.addAll(l); n += l.size(); }
			for (var d : city.districts().values()) for (LotBrief lb : d.lots()) lotStatus.merge(lb.type() + "\t" + lb.status(), 1, Integer::sum);
			int planned = (int) city.districts().values().stream().filter(d -> d.lots().stream().anyMatch(LotBrief::builds)).count();
			siteRows.add(String.format(Locale.ROOT, "| %s | %d | %s | %s | %d of %d | %d | %.0f |", s.label(), s.seed(), site.tier().json, s.site(), ids.size(), planned, n, (System.nanoTime() - t0) / 1e9));
			System.err.printf(Locale.ROOT, "door-rise: %s: %d scenes, %d doors, %.1f s%n", s.label(), ids.size(), n, (System.nanoTime() - t0) / 1e9);
		}
		StringBuilder tsv = new StringBuilder("site\tdistrict\tbuilding\tlot\tdoor\tkind\tstyle\tfront\trise\tsource\triseFrontY\trun\tlanding\treached\n");
		for (Door d : all)
			tsv.append(String.join("\t", d.site(), d.district(), d.building(), d.lot(), d.door(), d.kind(), d.style(), d.front() ? "1" : "0", "" + d.rise(), d.source(),
					d.riseFrontY() == null ? "" : "" + d.riseFrontY(), "" + d.run(), d.landing() ? "1" : "0", d.reached() ? "1" : "0")).append('\n');
		Files.writeString(out.resolve("doors.tsv"), tsv, StandardCharsets.UTF_8);
		Files.writeString(out.resolve("tables.md"), tables(all, siteRows, lotStatus), StandardCharsets.UTF_8);
	}

	// ---- tables

	static final String[] BUCKETS = {"<= -3", "-2..0", "1", "2", "3", "4", "5", "6-8", "9+"};

	static int bucket(int r) { return r <= -3 ? 0 : r <= 0 ? 1 : r == 1 ? 2 : r == 2 ? 3 : r == 3 ? 4 : r == 4 ? 5 : r == 5 ? 6 : r <= 8 ? 7 : 8; }

	static final List<String> KINDS = List.of("shop", "market", "house", "workshop");

	static String table(List<Door> ds, java.util.function.ToIntFunction<Door> rise, java.util.function.Predicate<Door> has) {
		StringBuilder sb = new StringBuilder("| kind | doors |");
		for (String b : BUCKETS) sb.append(' ').append(b).append(" |");
		sb.append(" 4+ | landing | run 4+ | max |\n|---|---|");
		for (int i = 0; i < BUCKETS.length + 4; i++) sb.append("---|");
		sb.append('\n');
		TreeSet<String> kinds = new TreeSet<>(Comparator.comparingInt((String k) -> KINDS.indexOf(k) < 0 ? 99 : KINDS.indexOf(k)).thenComparing(k -> k));
		for (Door d : ds) kinds.add(d.kind());
		for (String k : kinds) {
			List<Door> l = ds.stream().filter(d -> d.kind().equals(k) && has.test(d)).toList();
			if (l.isEmpty()) continue;
			int[] h = new int[BUCKETS.length];
			int hi = 0, max = Integer.MIN_VALUE, land = 0, run4 = 0;
			for (Door d : l) {
				int r = rise.applyAsInt(d);
				h[bucket(r)]++;
				if (r >= 4) hi++;
				if (d.landing()) land++;
				if (d.run() >= 4) run4++;
				max = Math.max(max, r);
			}
			sb.append("| ").append(k).append(" | ").append(l.size()).append(" |");
			for (int c : h) sb.append(' ').append(c).append(" |");
			sb.append(String.format(Locale.ROOT, " %d (%d %%) | %d %% | %d | %d |%n", hi, Math.round(100.0 * hi / l.size()), Math.round(100.0 * land / l.size()), run4, max));
		}
		return sb.toString();
	}

	/** Per kind, buildings whose highest front-door rise is 4+ (the architect counted buildings, not doors). */
	static String buildings(List<Door> ds) {
		StringBuilder sb = new StringBuilder("| kind | buildings with a front door | highest front door rise 4+ | 6+ |\n|---|---|---|---|\n");
		Map<String, Map<String, Integer>> worst = new TreeMap<>(Comparator.comparingInt((String k) -> KINDS.indexOf(k) < 0 ? 99 : KINDS.indexOf(k)).thenComparing(k -> k));
		for (Door d : ds) if (d.front()) worst.computeIfAbsent(d.kind(), k -> new TreeMap<>()).merge(d.site() + "/" + d.district() + "/" + d.building(), d.rise(), Math::max);
		for (var e : worst.entrySet()) {
			long n4 = e.getValue().values().stream().filter(r -> r >= 4).count(), n6 = e.getValue().values().stream().filter(r -> r >= 6).count();
			sb.append(String.format(Locale.ROOT, "| %s | %d | %d (%d %%) | %d |%n", e.getKey(), e.getValue().size(), n4, Math.round(100.0 * n4 / e.getValue().size()), n6));
		}
		return sb.toString();
	}

	static String tables(List<Door> all, List<String> siteRows, Map<String, Integer> lotStatus) {
		StringBuilder sb = new StringBuilder();
		sb.append("## Runs\n\n| site | seed | tier | site arg | scenes (of planned) | doors | seconds |\n|---|---|---|---|---|---|---|\n");
		for (String r : siteRows) sb.append(r).append('\n');
		List<Door> front = all.stream().filter(Door::front).toList();
		sb.append("\n## All sites: front doors, rise from the street surface in front (DS-45 measure)\n\n").append(table(front, Door::rise, d -> true));
		sb.append("\n## All sites: front doors, rise from the lot's frontY (the architect's measure)\n\n").append(table(front, d -> d.riseFrontY(), d -> d.riseFrontY() != null));
		sb.append("\n## All sites: side and back entrance doors, rise from the ground straight out\n\n").append(table(all.stream().filter(d -> !d.front()).toList(), Door::rise, d -> true));
		sb.append("\n## All sites: buildings\n\n").append(buildings(all));
		sb.append("\n## All sites: lots by planned type and status (every planned district)\n\n| type | status | lots |\n|---|---|---|\n");
		for (var e : lotStatus.entrySet()) sb.append("| ").append(e.getKey().replace("\t", " | ")).append(" | ").append(e.getValue()).append(" |\n");
		long unreached = all.stream().filter(d -> !d.reached()).count();
		sb.append("\nDoors whose walker found no way down to the ground (flight run not measured): ").append(unreached).append('\n');
		for (String site : all.stream().map(Door::site).distinct().toList()) {
			List<Door> l = front.stream().filter(d -> d.site().equals(site)).toList();
			sb.append("\n## ").append(site).append(": front doors\n\n").append(table(l, Door::rise, d -> true));
		}
		return sb.toString();
	}

	// ---- measure

	static List<Door> measure(String site, String district, SceneDoc scene, PlacedBuilding b, TerrainProbe terrain, Map<Long, Integer> surface, Map<String, Integer> frontY) {
		List<Door> out = new ArrayList<>();
		BuildingIr s = b.shell();
		if (s.storeys().isEmpty()) return out;
		int lowest = Integer.MAX_VALUE;
		for (Room r : s.rooms()) lowest = Math.min(lowest, r.storey());
		if (lowest == Integer.MAX_VALUE) lowest = 0;
		Storey s0 = s.storeys().get(lowest);
		String type = s.spec().type();
		String kind = lowest > 0 ? (type.equals("shop") ? "market" : "stilted-" + type) : type;
		List<Lot> lots = new ArrayList<>();
		for (String id : b.lots()) for (Lot l : scene.lots()) if (l.id().equals(id)) lots.add(l);
		VoxelGrid g = null;
		for (Opening o : s.openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
			if (g == null) g = VoxelGrid.of(b);
			Placement pl = b.placement();
			Facing outL = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
			Facing outW = pl.toWorld(outL);
			Box3 dw = pl.toWorld(o.box());
			int wx = dw.x(), wz = dw.z(), level = pl.worldY(o.box().y());
			boolean front = !lots.isEmpty() && lots.get(0).front() == outW;
			int k = 0;
			while (k < 64 && inLots(lots, wx, wz)) { wx += outW.dx; wz += outW.dz; k++; }
			Integer sv = surface.get(key(wx, wz));
			int street = sv != null ? sv : terrain.floorY(wx, wz);
			Integer fy = null;
			Integer lf = lots.isEmpty() ? null : frontY.get(lots.get(0).id());
			if (front && lf != null && lf != Integer.MIN_VALUE) fy = level - lf;
			int[] run = run(g, o, outL, dev.fractalbuildings.core.terrain.SiteGround.probe(b, terrain), surface, lots); // the ground as the building leaves it (cut and fill)
			out.add(new Door(site, district, b.id(), lots.isEmpty() ? "" : lots.get(0).id(), o.id(), kind, s.spec().style(), front, level - street, sv != null ? "ground" : "terrain", fy, run[0], run[1] == 1, run[2] == 1));
		}
		return out;
	}

	static boolean inLots(List<Lot> lots, int x, int z) {
		for (Lot l : lots) if (l.containsColumn(x, z)) return true;
		return false;
	}

	static Map<Long, Integer> surfaceLevels(SceneDoc scene) {
		Map<Long, Integer> m = new TreeMap<>();
		for (Box b : scene.ground()) {
			if (b.role() != Role.ROAD && b.role() != Role.PAVING && b.role() != Role.DECK) continue;
			Box3 q = b.box();
			for (int z = q.z(); z < q.maxZ(); z++)
				for (int x = q.x(); x < q.maxX(); x++) m.merge(key(x, z), q.maxY(), Math::max);
		}
		return m;
	}

	static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	/** {longest straight run of risers, landing at the door 0/1, reached ground 0/1}: StairGround's walker, with parents kept. */
	static int[] run(VoxelGrid g, Opening o, Facing out, TerrainProbe terrain, Map<Long, Integer> surface, List<Lot> lots) {
		int x0 = g.minX, x1 = g.minX + g.sx - 1, z0 = g.minZ, z1 = g.minZ + g.sz - 1;
		for (Lot l : lots)
			for (int cx : new int[] {l.x(), l.x() + l.w() - 1})
				for (int cz : new int[] {l.z(), l.z() + l.d() - 1}) {
					int lx = g.placement().localX(cx, cz), lz = g.placement().localZ(cx, cz);
					x0 = Math.min(x0, lx); x1 = Math.max(x1, lx); z0 = Math.min(z0, lz); z1 = Math.max(z1, lz);
				}
		x0 -= 2; x1 += 2; z0 -= 2; z1 += 2;
		int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
		int[] level = new int[nx * nz];
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int wx = g.worldX(x, z), wz = g.worldZ(x, z);
				int lv = terrain.floorY(wx, wz);
				Integer s = surface.get(key(wx, wz));
				if (s != null) lv = s;
				level[(z - z0) * nx + (x - x0)] = lv;
				lo = Math.min(lo, lv);
				hi = Math.max(hi, lv);
			}
		int ylo = Math.min(lo, g.worldY(g.minY)) - 2, yhi = Math.max(hi, g.worldY(g.minY + g.sy)) + 3;
		int floor2 = 2 * g.worldY(o.box().y());
		Map<Long, long[]> parent = new HashMap<>(); // node -> {parent node, facing ordinal}; lookups only, order never matters
		List<int[]> queue = new ArrayList<>();
		queue.add(new int[] {o.box().x(), o.box().z(), floor2});
		long start = node(o.box().x(), o.box().z(), floor2);
		parent.put(start, new long[] {Long.MIN_VALUE, -1});
		long goal = Long.MIN_VALUE;
		for (int head = 0; head < queue.size() && goal == Long.MIN_VALUE; head++) {
			int[] c = queue.get(head);
			long cn = node(c[0], c[1], c[2]);
			for (Facing f : head == 0 ? new Facing[] {out} : Facing.values()) {
				int px = c[0] + f.dx, pz = c[1] + f.dz;
				if (px < x0 || px > x1 || pz < z0 || pz > z1) continue;
				int lv = level[(pz - z0) * nx + (px - x0)];
				for (int top2 = 2 * (ylo + 1); top2 <= 2 * yhi; top2++) {
					if (top2 - c[2] > 1 || c[2] - top2 > 2) continue;
					int kind = surfaceKind(g, px, pz, top2, lv);
					if (kind == NONE) continue;
					long k = node(px, pz, top2);
					if (parent.containsKey(k)) continue;
					parent.put(k, new long[] {cn, f.ordinal()});
					if (kind == GROUND) { goal = k; break; }
					queue.add(new int[] {px, pz, top2});
				}
				if (goal != Long.MIN_VALUE) break;
			}
		}
		if (goal == Long.MIN_VALUE) return new int[] {0, 0, 0};
		// walk back: the path as (top2, facing) from the door outward
		List<long[]> path = new ArrayList<>();
		for (long n = goal; n != start; n = parent.get(n)[0]) path.add(new long[] {top2(n), parent.get(n)[1]});
		Collections.reverse(path);
		// Walker steps are half blocks (a stair top is half a block), so a straight flight of rise R is R blocks of descent.
		// A run is the descent between level moves (a tread or landing), in whole blocks rounded up; turns do not end it.
		// A landing at the door: a level move before the walker is more than half a block below the door's floor.
		int prev = floor2, best = 0, cur = 0;
		boolean landing = false;
		for (long[] st : path) {
			int t = (int) st[0];
			if (t < prev) cur += prev - t;
			else {
				if (t == prev && t >= floor2 - 1) landing = true;
				cur = 0;
			}
			best = Math.max(best, (cur + 1) / 2);
			prev = t;
		}
		return new int[] {best, landing ? 1 : 0, 1};
	}

	static final int NONE = 0, GROUND = 1, BUILT = 2;

	static long node(int x, int z, int top2) { return (((long) top2 + 4096) << 40) ^ (((long) x + 100000) << 20) ^ ((long) z + 100000); }

	static int top2(long n) { return (int) ((n >>> 40) - 4096); }

	static int surfaceKind(VoxelGrid g, int lx, int lz, int top2, int level) {
		boolean stair = (top2 & 1) == 1;
		int y = stair ? (top2 - 1) / 2 : top2 / 2 - 1;
		boolean groundHere = !stair && y == level - 1 && !solidBuilt(g, lx, y, lz);
		boolean built = solidBuilt(g, lx, y, lz) && (stair ? g.get(lx, (y - g.placement().oy()), lz) == Role.STAIR : g.get(lx, (y - g.placement().oy()), lz) != Role.STAIR);
		if (!groundHere && !built) return NONE;
		int body = y + 1;
		for (int k = body; k < body + 2; k++) if (k < level || solidBuilt(g, lx, k, lz) || g.get(lx, k - g.placement().oy(), lz) == Role.DOOR) return NONE;
		return groundHere ? GROUND : BUILT;
	}

	static boolean solidBuilt(VoxelGrid g, int lx, int wy, int lz) { return g.get(lx, (wy - g.placement().oy()), lz).solid; }

	/** CityCommand.fromSite (package-private there). */
	static SettlementSite fromSite(RegionField field, String arg) {
		String[] f = arg.split(",", -1);
		int x = Integer.parseInt(f[0]), z = Integer.parseInt(f[1]), r = Integer.parseInt(f[3]);
		Tier tier = Tier.of(f[2]);
		Cell claimant = CellGeometry.owner(field, x, z);
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++)
			for (int dx = -8; dx <= 8; dx++) {
				Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
				long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
				if (c.equals(claimant) || ex * ex + ez * ez <= (long) r * r) cells.add(c);
			}
		cells.sort(CellGeometry::compare);
		return new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), tier, new Pt(x, z), r, claimant, cells);
	}

}
