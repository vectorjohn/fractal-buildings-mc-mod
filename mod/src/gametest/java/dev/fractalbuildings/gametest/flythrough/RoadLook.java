package dev.fractalbuildings.gametest.flythrough;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Street;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.gametest.flythrough.FlyThroughClientGameTest.Frame;
import dev.fractalbuildings.gametest.flythrough.FlyThroughClientGameTest.Prepared;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * ROADS1 r2: frames of what the roads are made of, for the architect, in real worlds (opt-in: {@code FB_FLY=only FB_FLY_ROADS=1}, see
 * {@code scripts/flythrough.sh --roads}). Per seed it walks the settlements near the origin, nearest first, and for each kind of surface
 * not yet seen takes one eye-level frame: a town street by block (stone bricks, smooth sandstone), a village or hamlet track by block
 * (dirt path, gravel, mud bricks), a city junction (arterial against street), a city bridge deck and a village bridge, a quay. Frames are
 * named by what the world holds under the camera, never by what the plan says. The first world also gets a built strip comparing three
 * candidates for city asphalt next to the light grey street.
 */
final class RoadLook {
	private RoadLook() {}

	/** Surfaces already framed in an earlier seed's world (the run is single-threaded). */
	static final Set<String> FOUND = new TreeSet<>();

	static boolean enabled() { return System.getenv("FB_FLY_ROADS") != null; }

	private static final int MAX_SITES = 60;

	private static Block blk(String path) { return BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:" + path)); }

	private static String name(Block b) { return BuiltInRegistries.BLOCK.getKey(b).getPath(); }

	/** Block name under a surface cell, a slab or stairs read as their full block. */
	private static String surfaceName(ServerLevel level, int x, int y, int z) {
		level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
		String n = name(level.getBlockState(new BlockPos(x, y, z)).getBlock());
		return n.endsWith("_slab") ? n.substring(0, n.length() - 5) : n.endsWith("_stairs") ? n.substring(0, n.length() - 7) : n;
	}

	static List<Prepared> prepare(ServerLevel level, String seed, int cells, int rd, Set<String> found) {
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
		WorldPlanner planner = WorldPlanners.of(level, gen).orElseThrow(() -> new IllegalStateException("no planner in a normal world"));
		int cs = planner.config().cellSize();
		TreeMap<String, SettlementSite> all = new TreeMap<>();
		for (int j = -cells; j <= cells; j++) for (int i = -cells; i <= cells; i++)
			planner.nearest(i * cs + cs / 2, j * cs + cs / 2, Tier.HAMLET, 0).ifPresent(s -> all.put(s.site().id(), s.site()));
		List<SettlementSite> sites = new ArrayList<>(all.values());
		sites.sort(java.util.Comparator.comparingDouble((SettlementSite s) -> Math.hypot(s.centre().x(), s.centre().z())).thenComparing(SettlementSite::id));
		List<Prepared> out = new ArrayList<>();
		int n = 0;
		for (SettlementSite site : sites) {
			if (n++ >= MAX_SITES) break;
			List<Frame> fr = new ArrayList<>();
			List<String> notes = new ArrayList<>();
			try {
				frames(level, planner, site, found, fr, notes);
			} catch (RuntimeException e) {
				FractalBuildingsMod.LOGGER.warn("[B9] roads: {} {}: {}", seed, site.id(), e.toString());
			}
			if (!fr.isEmpty()) out.add(new Prepared(seed, site, fr, notes));
		}
		if (found.add("asphalt")) {
			out.add(asphalt(level));
		}
		return out;
	}

	private static void frames(ServerLevel level, WorldPlanner planner, SettlementSite site, Set<String> found, List<Frame> fr, List<String> notes) {
		DistrictGrid.Tile ct = planner.grid().tileAt(site.centre().x(), site.centre().z());
		Tier tier = site.tier();
		Box town = null, track = null, main = null, city = null, piers = null, deck = null; // the longest box of each class
		Street quay = null;
		for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
			int ix = ct.ix() + dx, iz = ct.iz() + dz;
			if (planner.tile(ix, iz).isEmpty()) continue;
			for (Street s : planner.plan(ix, iz).plan().streets()) if (s.kind() == RoadKind.QUAY && quay == null && s.points().size() >= 2) quay = s;
			for (GroundPiece g : planner.ground(ix, iz)) for (Box b : g.boxes()) {
				int len = Math.max(b.box().sx(), b.box().sz()), wid = Math.min(b.box().sx(), b.box().sz());
				Role r = b.role();
				if (r == Role.ROAD_TOWN && wid >= 2 && len >= 10 && (town == null || len > Math.max(town.box().sx(), town.box().sz()))) town = b;
				if (r == Role.ROAD && wid >= 2 && len >= 10 && (track == null || len > Math.max(track.box().sx(), track.box().sz()))) track = b;
				if (r == Role.ROAD_MAIN && wid >= 2 && len >= 16 && (main == null || len > Math.max(main.box().sx(), main.box().sz()))) main = b;
				if (r == Role.ROAD_CITY && wid >= 2 && len >= 10 && (city == null || len > Math.max(city.box().sx(), city.box().sz()))) city = b;
				if (r == Role.DECK && b.box().sy() >= 2 && piers == null) piers = b; // a pier: the deck is its top cell
				if (r == Role.DECK && b.box().sy() == 1 && deck == null) deck = b; // a village deck cell
			}
		}
		// town streets and tracks, one per surface block
		for (Box b : new Box[] {tier == Tier.TOWN ? town : null, tier.compareTo(Tier.TOWN) < 0 ? track : null}) {
			if (b == null) continue;
			int cx = b.box().x() + b.box().sx() / 2, cz = b.box().z() + b.box().sz() / 2, top = b.box().y() + b.box().sy();
			String blk = surfaceName(level, cx, top - 1, cz);
			String kind = b.role() == Role.ROAD_TOWN ? "street" : "track";
			String key = kind + "-" + blk;
			boolean wanted = true;
			if (wanted && found.add(key)) {
				boolean alongX = b.box().sx() >= b.box().sz();
				double sx = alongX ? b.box().x() + 0.5 : b.box().x() + b.box().sx() / 2.0, sz = alongX ? b.box().z() + b.box().sz() / 2.0 : b.box().z() + 0.5;
				double ex = alongX ? b.box().x() + b.box().sx() - 0.5 : sx, ez = alongX ? sz : b.box().z() + b.box().sz() - 0.5;
				level.getChunk((int) sx >> 4, (int) sz >> 4, ChunkStatus.FULL, true);
				level.getChunk((int) ex >> 4, (int) ez >> 4, ChunkStatus.FULL, true);
				fr.add(new Frame(key, sx, top, sz, ex, top + 1.6, ez, 40));
				notes.add(key + " along " + b.box());
			}
		}
		// a city junction: an arterial or main street meeting a street
		if (tier.atLeast(Tier.CITY) && main != null && city != null && found.add("junction")) {
			boolean mx = main.box().sx() >= main.box().sz();
			double jx = Math.max(main.box().x(), Math.min(main.box().maxX() - 1, city.box().x() + city.box().sx() / 2.0));
			double jz = Math.max(main.box().z(), Math.min(main.box().maxZ() - 1, city.box().z() + city.box().sz() / 2.0));
			// stand on the street, 12 blocks back from the main street's axis, looking at the crossing
			boolean cAlongX = city.box().sx() >= city.box().sz();
			double ccx = city.box().x() + city.box().sx() / 2.0, ccz = city.box().z() + city.box().sz() / 2.0;
			double bx = cAlongX ? city.box().x() + 1.5 : ccx, bz = cAlongX ? ccz : city.box().z() + 1.5;
			double tx = jx + 0.5, tz = jz + 0.5;
			int top = city.box().y() + city.box().sy();
			level.getChunk((int) bx >> 4, (int) bz >> 4, ChunkStatus.FULL, true);
			level.getChunk((int) tx >> 4, (int) tz >> 4, ChunkStatus.FULL, true);
			fr.add(new Frame("junction", bx, top + 1, bz, tx, top + 0.5, tz, 40));
			notes.add("junction main " + main.box() + " street " + city.box() + (mx ? " (main along x)" : " (main along z)"));
		}
		// bridges
		if (tier.atLeast(Tier.CITY) && piers != null && found.add("bridge-city")) bridge(level, piers, 1, "bridge-city", fr, notes);
		if (tier.compareTo(Tier.TOWN) <= 0 && deck != null && found.add("bridge-village")) bridge(level, deck, 0, "bridge-" + tier.json, fr, notes);
		if (quay != null && found.add("quay")) {
			var pts = quay.points();
			var a = pts.get(0); var z = pts.get(pts.size() - 1);
			double sx = a.x() + 0.5, sz = a.z() + 0.5, ex = z.x() + 0.5, ez = z.z() + 0.5;
			level.getChunk((int) sx >> 4, (int) sz >> 4, ChunkStatus.FULL, true);
			level.getChunk((int) ex >> 4, (int) ez >> 4, ChunkStatus.FULL, true);
			int hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) sx, (int) sz);
			fr.add(new Frame("quay-" + surfaceName(level, (int) sx, hy - 1, (int) sz), sx, hy, sz, ex, hy + 1.6, ez, 40));
			notes.add("quay " + quay.id() + " from " + a + " to " + z);
		}
	}

	/** A frame standing on a bridge deck, looking along the longest run of the deck's own block through that cell. */
	private static void bridge(ServerLevel level, Box b, int pierExtra, String label, List<Frame> fr, List<String> notes) {
		int x = b.box().x() + b.box().sx() / 2, z = b.box().z() + b.box().sz() / 2, y = b.box().y() + b.box().sy() - 1 + pierExtra; // the deck cell
		level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		var here = level.getBlockState(p.set(x, y, z)).getBlock();
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int best = -1, bl = 0;
		for (int d = 0; d < 4; d++) {
			int k = 0;
			while (k < 40) {
				int nx = x + dirs[d][0] * (k + 1), nz = z + dirs[d][1] * (k + 1);
				level.getChunk(nx >> 4, nz >> 4, ChunkStatus.FULL, true);
				if (level.getBlockState(p.set(nx, y, nz)).getBlock() != here) break;
				k++;
			}
			if (k > bl) { bl = k; best = d; }
		}
		if (best < 0 || bl < 4) { notes.add(label + ": no run of the deck block at " + x + "," + y + "," + z); return; }
		int back = Math.min(bl, 8);
		double sx = x + 0.5 - dirs[best][0] * 0, sz = z + 0.5;
		// walk to the far end of the opposite direction so the whole run lies ahead
		int bk = 0;
		int od = best ^ 1;
		while (bk < 40) {
			int nx = x + dirs[od][0] * (bk + 1), nz = z + dirs[od][1] * (bk + 1);
			level.getChunk(nx >> 4, nz >> 4, ChunkStatus.FULL, true);
			if (level.getBlockState(p.set(nx, y, nz)).getBlock() != here) break;
			bk++;
		}
		sx = x + 0.5 + dirs[od][0] * Math.max(0, bk - 1); sz = z + 0.5 + dirs[od][1] * Math.max(0, bk - 1);
		double ex = x + 0.5 + dirs[best][0] * bl, ez = z + 0.5 + dirs[best][1] * bl;
		fr.add(new Frame(label + "-" + name(here), sx, y + 1, sz, ex, y + 2.4, ez, 40));
		notes.add(label + " deck cell " + x + "," + y + "," + z + " block " + name(here) + ", run " + (bk + bl + 1) + " along " + (dirs[best][0] != 0 ? "x" : "z") + " (back " + back + ")");
	}

	/** Built strip high over the origin: three candidates for city asphalt, each running into the light grey street, between white sidewalks. */
	private static Prepared asphalt(ServerLevel level) {
		int y = 150, x0 = 0, z0 = 0;
		for (int cz = (z0 - 8) >> 4; cz <= (z0 + 56) >> 4; cz++) for (int cx = (x0 - 8) >> 4; cx <= (x0 + 40) >> 4; cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
		Block[] lanes = {Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, blk("gray_concrete")};
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int z = z0 - 6; z < z0 + 56; z++) for (int x = x0 - 6; x < x0 + 40; x++) {
			level.setBlock(p.set(x, y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
			for (int k = 0; k < 14; k++) level.setBlock(p.set(x, y + k, z), Blocks.AIR.defaultBlockState(), 3);
		}
		for (int i = 0; i < 3; i++) {
			int lx = x0 + i * 12;
			for (int z = z0; z < z0 + 50; z++) {
				Block surface = z < z0 + 24 ? lanes[i] : blk("light_gray_concrete");
				for (int dx = 0; dx < 8; dx++) {
					level.setBlock(p.set(lx + dx, y - 1, z), (dx == 0 || dx == 7 ? blk("white_concrete") : surface).defaultBlockState(), 3);
				}
				// kerb: the carriageway is half a block under the sidewalk (DS-31): sidewalks stand one block up
				level.setBlock(p.set(lx, y, z), blk("white_concrete").defaultBlockState(), 3);
				level.setBlock(p.set(lx + 7, y, z), blk("white_concrete").defaultBlockState(), 3);
			}
		}
		List<Frame> fr = new ArrayList<>();
		fr.add(new Frame("asphalt-overview", x0 + 17, y + 14, z0 + 62, x0 + 17, y, z0 + 20, 60));
		String[] names = {"cobbled_deepslate", "polished_deepslate", "gray_concrete"};
		for (int i = 0; i < 3; i++) {
			double cx = x0 + i * 12 + 3.5;
			fr.add(new Frame("asphalt-" + names[i], cx, y, z0 + 44, cx, y + 0.6, z0 + 10, 40));
		}
		fr.add(new Frame("asphalt-joint", x0 + 3.5, y + 6, z0 + 34, x0 + 3.5, y, z0 + 24, 40));
		return new Prepared("asphalt", null, fr, List.of("lanes along z: cobbled_deepslate, polished_deepslate, gray_concrete (z 0-23), then light_gray_concrete; white concrete sidewalks"), "asphalt");
	}
}
