package dev.fractalbuildings.gametest.terrain;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * B3: S5's terrain metrics on real settlements of the three review seeds, read back from the generated world. For each seed the nearest
 * village-or-larger settlement is scanned for its steepest tile and its wettest tile (by the live exact probe), both are generated through the
 * normal pipeline, and per building (void under the lot's footprint, wall exposure and burial on the ring around the lot) and per ground piece
 * (road, paving or deck cells with nothing under them, headroom over road cells, steps between neighbouring road columns, the tile border
 * included) the world is read. Two screenshots per seed: the road column with the deepest cut, and the road next to water.
 * Hard assertions: void under a ground floor is 0, no road or paving cell floats, a bridge deck is within 6 columns of a pier or over water, no step above one block across a tile border, headroom 0 (see
 */
public class TerrainClientGameTest implements FabricClientGameTest {
	static final String[] SEEDS = System.getProperty("fb.b3.seeds", "987654321,12345,1").split(",");

	record Site(int ix, int iz, int minX, int minZ, int maxX, int maxZ, int relief, double wet, int buildings, String kind) {}

	record Shot(String name, int x, int y, int z, int tx, int ty, int tz) {}

	private static void settle(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		context.waitTicks(ticks);
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			FractalBuildingsMod.LOGGER.warn("[B3] chunks did not finish rendering in time; taking the screenshot anyway");
		}
		context.waitTicks(40);
	}

	/** A block you can stand on: it has a collision shape. */
	private static boolean solid(ServerLevel level, BlockPos pos, BlockState st) { return !st.getCollisionShape(level, pos).isEmpty(); }

	/** A camera spot with a clear line to the target: tries a ring of offsets, then higher ones. */
	private static Shot camera(ServerLevel level, String name, int tx, int ty, int tz) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int[][] offs = {{0, 9, 15}, {0, 9, -15}, {15, 9, 0}, {-15, 9, 0}, {11, 10, 11}, {-11, 10, 11}, {11, 10, -11}, {-11, 10, -11}, {0, 20, 26}, {0, 20, -26}, {26, 20, 0}, {-26, 20, 0}};
		for (int[] o : offs) {
			int cx = tx + o[0], cy = ty + o[1], cz = tz + o[2];
			boolean clear = true;
			for (int i = 0; i <= 20 && clear; i++) {
				double f = i / 20.0;
				int x = (int) Math.round(cx + (tx - cx) * f), y = (int) Math.round(cy + (ty + 1 - cy) * f), z = (int) Math.round(cz + (tz - cz) * f);
				if (Math.abs(x - tx) + Math.abs(z - tz) <= 2) break;
				BlockState st = level.getBlockState(q.set(x, y, z));
				if (!st.getCollisionShape(level, q).isEmpty() && !st.is(BlockTags.LEAVES)) clear = false;
			}
			if (clear) return new Shot(name, cx, cy, cz, tx, ty, tz);
		}
		return new Shot(name, tx, ty + 30, tz + 30, tx, ty, tz);
	}

	/** The blocks of a column from y1 down to y0 as run-lengths, for the log. */
	private static String col(ServerLevel level, int x, int z, int y0, int y1) {
		StringBuilder sb = new StringBuilder("[" + x + "," + z + " y" + y1 + "..y" + y0 + ":");
		String last = null; int run = 0;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int y = y1; y >= y0; y--) {
			String id = level.getBlockState(q.set(x, y, z)).getBlock().getDescriptionId().replace("block.minecraft.", "");
			if (id.equals(last)) run++; else { if (last != null) sb.append(" ").append(last).append(run > 1 ? "*" + run : ""); last = id; run = 1; }
		}
		sb.append(" ").append(last).append(run > 1 ? "*" + run : "").append("]");
		return sb.toString();
	}

	private static long key(int x, int y, int z) { return ((long) x & 0x3ffffff) << 38 | ((long) z & 0x3ffffff) << 12 | ((long) (y + 64) & 0xfff); }

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> failures = new ArrayList<>();
		context.runOnClient(client -> client.options.renderDistance().set(6));
		for (String seed : SEEDS) {
			try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
				AtomicReference<List<Shot>> shots = new AtomicReference<>(List.of());
				world.getServer().runOnServer(server -> shots.set(measure(server.overworld(), seed, failures)));
				world.getServer().runCommand("gamemode spectator @p");
				world.getServer().runCommand("time set noon");
				for (Shot s : shots.get()) {
					double dx = s.tx() - s.x(), dy = s.ty() - s.y(), dz = s.tz() - s.z();
					double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
					world.getServer().runCommand(String.format("tp @p %d %d %d %.1f %.1f", s.x(), s.y(), s.z(), yaw, pitch));
					settle(context, world, 60);
					context.takeScreenshot("terrain-" + seed + "-" + s.name());
				}
			}
		}
		if (!failures.isEmpty()) throw new IllegalStateException("terrain metrics failed:\n" + String.join("\n", failures));
	}

	private static List<Shot> measure(ServerLevel level, String seed, List<String> failures) {
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
		WorldPlanner planner = WorldPlanners.of(level, gen).orElseThrow();
		TerrainProbe exact = new LiveProbes(gen, level.getChunkSource().randomState()).exact();
		Optional<SettlementInfo> n = Optional.empty();
		for (int r = 1; r <= 4 && n.isEmpty(); r++) n = planner.nearest(0, 0, Tier.VILLAGE, r);
		SettlementInfo info = n.orElseThrow(() -> new IllegalStateException("[B3] seed " + seed + ": no village+ near the origin"));
		DistrictGrid.Tile c = planner.grid().tileAt(info.site().centre().x(), info.site().centre().z());
		List<Site> cands = new ArrayList<>();
		for (int dz = -6; dz <= 6; dz++) for (int dx = -6; dx <= 6; dx++) {
			int ix = c.ix() + dx, iz = c.iz() + dz;
			if (planner.tile(ix, iz).isEmpty()) continue;
			int nb = planner.buildings(ix, iz).size();
			if (nb < 3) continue;
			DistrictGrid.Tile t = planner.grid().tile(ix, iz);
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0, total = 0;
			for (int z = t.minZ(); z < t.maxZ(); z += 6) for (int x = t.minX(); x < t.maxX(); x += 6) {
				int f = exact.floorY(x, z);
				lo = Math.min(lo, f); hi = Math.max(hi, f);
				if (exact.waterDepth(x, z) > 0) wet++;
				total++;
			}
			cands.add(new Site(ix, iz, t.minX(), t.minZ(), t.maxX(), t.maxZ(), hi - lo, wet / (double) total, nb, ""));
		}
		FractalBuildingsMod.LOGGER.info("[B3] seed {}: settlement {} {} at {},{}; {} candidate tiles", seed, info.site().id(), info.site().tier(), info.site().centre().x(), info.site().centre().z(), cands.size());
		if (cands.isEmpty()) throw new IllegalStateException("[B3] seed " + seed + ": no tile with 3 buildings near the settlement");
		Site slope = cands.stream().max((a, b) -> Integer.compare(a.relief(), b.relief())).get();
		Site shore = cands.stream().filter(s -> s.wet() > 0.02 && s.wet() < 0.7).max((a, b) -> Double.compare(a.wet(), b.wet())).orElse(null);
		if (shore == null) shore = cands.stream().max((a, b) -> Double.compare(a.wet(), b.wet())).get();
		List<Shot> shots = new ArrayList<>();
		Map<String, Site> picks = new TreeMap<>();
		picks.put("slope", slope);
		if (!(shore.ix() == slope.ix() && shore.iz() == slope.iz())) picks.put("shore", shore);
		else picks.put("shore", slope);
		for (var e : picks.entrySet()) {
			Site s = e.getValue();
			FractalBuildingsMod.LOGGER.info("[B3] seed {} {} tile {},{}: relief {} wet {} buildings {}", seed, e.getKey(), s.ix(), s.iz(), s.relief(), String.format("%.2f", s.wet()), s.buildings());
			shots.add(tile(level, planner, exact, seed, e.getKey(), s, failures));
		}
		// what is the white patch? count surface blocks around the first slope tile
		return shots;
	}

	private static Shot tile(ServerLevel level, WorldPlanner planner, TerrainProbe exact, String seed, String kind, Site s, List<String> failures) {
		long t0 = System.nanoTime();
		// generate the tile and a margin of one chunk
		for (int cz = (s.minZ() >> 4) - 1; cz <= (s.maxZ() >> 4); cz++) for (int cx = (s.minX() >> 4) - 1; cx <= (s.maxX() >> 4); cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
		FractalBuildingsMod.LOGGER.info("[B3] generated {} tile in {} ms", kind, (System.nanoTime() - t0) / 1_000_000);
		String tag = "seed " + seed + " " + kind + " tile " + s.ix() + "," + s.iz();
		Box3 all = new Box3(s.minX(), level.getMinY(), s.minZ(), s.maxX() - s.minX(), level.getMaxY() - level.getMinY() + 1, s.maxZ() - s.minZ());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Set<Long> buildingCells = new HashSet<>();
		// ---- buildings
		int voidCols = 0, wetCols = 0, cols = 0, ringN = 0;
		double expSum = 0, burSum = 0;
		int expMax = 0, burMax = 0;
		List<String> voidAt = new ArrayList<>();
		for (BuildingPiece b : planner.buildings(s.ix(), s.iz())) {
			Lot lot = b.lot();
			Map<Long, int[]> low = new HashMap<>(); // column -> {lowest solid y, lowest floor y}
			planner.raster(b.ref(), all, (x, y, z, role, id, shape) -> {
				buildingCells.add(key(x, y, z));
				if (role == Role.AIR || !lot.containsColumn(x, z)) return;
				int[] v = low.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE});
				v[0] = Math.min(v[0], y);
				if (role == Role.FLOOR) v[1] = Math.min(v[1], y);
			});
			int floorY = Integer.MAX_VALUE;
			for (int[] v : low.values()) floorY = Math.min(floorY, v[1]);
			for (var e : low.entrySet()) {
				int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
				int y = e.getValue()[1]; // the ground floor of this column: the block under it must be solid (foundation or ground), whatever caves lie deeper
				if (y > floorY + 1) continue; // no floor, an upper storey or a jetty over a yard: not part of the ground floor
				BlockState below = level.getBlockState(p.set(x, y - 1, z));
				cols++;
				if (!below.getFluidState().isEmpty()) wetCols++;
				else if (!solid(level, p, below)) { voidCols++; if (voidAt.size() < 6) voidAt.add(b.brief().id() + "@" + x + "," + y + "," + z + " probe " + exact.floorY(x, z) + " " + col(level, x, z, y - 8, y + 1)); }
			}
			if (floorY == Integer.MAX_VALUE) continue;
			for (int z = lot.z() - 1; z <= lot.z() + lot.d(); z++) for (int x = lot.x() - 1; x <= lot.x() + lot.w(); x++) {
				if (lot.containsColumn(x, z) || x < s.minX() || x >= s.maxX() || z < s.minZ() || z >= s.maxZ()) continue;
				if ((x != lot.x() - 1 && x != lot.x() + lot.w()) && (z != lot.z() - 1 && z != lot.z() + lot.d())) continue;
				int surf = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); // first free y
				int d = floorY - surf;
				ringN++;
				if (d > 0) { expSum += d; expMax = Math.max(expMax, d); } else { burSum += -d; burMax = Math.max(burMax, -d); }
			}
		}
		// ---- ground
		int roadCells = 0, floating = 0, headroom = 0, steps = 0, borderSteps = 0, cutMax = 0, nearWater = 0;
		int camX = 0, camY = 0, camZ = 0, bestCut = -1, bestWater = Integer.MAX_VALUE, wx = 0, wy = 0, wz = 0;
		Map<Long, Integer> top = new HashMap<>();
		Map<Long, String> src = new HashMap<>();
		Map<Long, Integer> deckCount = new HashMap<>();
		List<String> floatAt = new ArrayList<>(), headAt = new ArrayList<>(), stepAt = new ArrayList<>();
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
			if ((dx != 0 && dz != 0) || planner.tile(s.ix() + dx, s.iz() + dz).isEmpty()) continue;
			final int ddx = dx, ddz = dz;
			for (GroundPiece g : planner.ground(s.ix() + dx, s.iz() + dz)) {
				planner.raster(g.ref(), new Box3(g.box().x(), level.getMinY(), g.box().z(), g.box().sx(), level.getMaxY() - level.getMinY() + 1, g.box().sz()), (x, y, z, role, id, shape) -> {
					if (role != Role.AIR) buildingCells.add(key(x, y, z)); // what the plan owns: not terrain over the street
					if (role == Role.DECK) deckCount.merge(((long) x << 32) ^ (z & 0xffffffffL), 1, Integer::sum);
					if (role == Role.ROAD || role == Role.PAVING || role == Role.DECK) {
						long k = ((long) x << 32) ^ (z & 0xffffffffL);
						Integer old = top.get(k);
						if (old == null || y > old) { top.put(k, y); src.put(k, "t" + (s.ix() + ddx) + "," + (s.iz() + ddz) + " " + role); }
					}
				});
			}
		}
		Set<Long> pierCols = new HashSet<>();
		for (var e : deckCount.entrySet()) if (e.getValue() > 1) pierCols.add(e.getKey());
		for (GroundPiece g : planner.ground(s.ix(), s.iz())) {
			final int[] acc = new int[8]; // roadCells, floating, headroom, bestCut, x, y, z, wetBest
			List<int[]> cells = new ArrayList<>();
			planner.raster(g.ref(), new Box3(g.box().x(), level.getMinY(), g.box().z(), g.box().sx(), level.getMaxY() - level.getMinY() + 1, g.box().sz()), (x, y, z, role, id, shape) -> {
				if (role == Role.ROAD || role == Role.PAVING || role == Role.DECK) cells.add(new int[] {x, y, z, role.ordinal()});
			});
			for (int[] cell : cells) {
				int x = cell[0], y = cell[1], z = cell[2];
				Role role = Role.values()[cell[3]];
				roadCells++;
				BlockState below = level.getBlockState(p.set(x, y - 1, z));
				boolean fl;
				if (role == Role.DECK) {
					// a deck spans between piers (every 8th column of the bridge) over water or a gap: it floats only with no pier within 6 columns and nothing wet or solid within 8 below
					fl = true;
					for (int h = 1; h <= 8 && fl; h++) { BlockState st = level.getBlockState(p.set(x, y - h, z)); if (solid(level, p, st) || !st.getFluidState().isEmpty()) fl = false; }
					for (int q = -6; q <= 6 && fl; q++) for (int r = -6; r <= 6 && fl; r++) if (pierCols.contains(((long) (x + q) << 32) ^ ((z + r) & 0xffffffffL))) fl = false;
				} else fl = !solid(level, p, below);
				if (fl) { floating++; if (floatAt.size() < 6) floatAt.add(role + "@" + x + "," + y + "," + z + " probe " + exact.floorY(x, z) + " " + col(level, x, z, y - 8, y + 2)); }
				if (role == Role.ROAD || role == Role.PAVING) {
					for (int h = 1; h <= 3; h++) {
						BlockState st = level.getBlockState(p.set(x, y + h, z));
						if (solid(level, p, st) && !st.is(BlockTags.LEAVES) && !st.is(BlockTags.LOGS) && !buildingCells.contains(key(x, y + h, z))) {
							headroom++;
							if (headAt.size() < 6) headAt.add(role + "@" + x + "," + y + "," + z + " probe " + exact.floorY(x, z) + " " + col(level, x, z, y - 3, y + 8));
							break;
						}
					}
				}
				int cut = exact.floorY(x, z) - (y + 1);
				if (role == Role.ROAD && cut > bestCut) { bestCut = cut; camX = x; camY = y; camZ = z; }
				// distance to water in columns
				int wd = Integer.MAX_VALUE;
				for (int q = 0; q <= 3 && wd == Integer.MAX_VALUE; q++) for (int zz = -q; zz <= q; zz++) for (int xx = -q; xx <= q; xx++) if (exact.waterDepth(x + xx, z + zz) > 0) wd = Math.min(wd, q);
				if (wd != Integer.MAX_VALUE) nearWater++;
				int score = wd == Integer.MAX_VALUE ? 100 : wd;
				if (score < bestWater || (score == bestWater && role == Role.DECK)) { bestWater = score; wx = x; wy = y; wz = z; }
			}
		}
		cutMax = Math.max(bestCut, 0);
		for (var e : top.entrySet()) {
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
				Integer o = top.get(((long) (x + d[0]) << 32) ^ ((z + d[1]) & 0xffffffffL));
				if (o != null && Math.abs(o - e.getValue()) > 1) { steps++; if (!src.get(e.getKey()).substring(0, src.get(e.getKey()).indexOf(' ')).equals(src.get(((long) (x + d[0]) << 32) ^ ((z + d[1]) & 0xffffffffL)).substring(0, src.get(((long) (x + d[0]) << 32) ^ ((z + d[1]) & 0xffffffffL)).indexOf(' ')))) borderSteps++; if (stepAt.size() < 6) stepAt.add(x + "," + z + " " + e.getValue() + "(" + src.get(e.getKey()) + ")->" + o + "(" + src.get(((long) (x + d[0]) << 32) ^ ((z + d[1]) & 0xffffffffL)) + ")"); }
			}
		}
		FractalBuildingsMod.LOGGER.info("[B3] {}: buildings {}: footprint columns {} void {} wet {}; ring {} exposure mean {} max {} burial mean {} max {}", tag, planner.buildings(s.ix(), s.iz()).size(), cols, voidCols, wetCols, ringN,
				String.format("%.2f", expSum / Math.max(1, ringN)), expMax, String.format("%.2f", burSum / Math.max(1, ringN)), burMax);
		FractalBuildingsMod.LOGGER.info("[B3] {}: ground cells {} floating {} headroom {} steps>1 {} (across the tile border {}) deepest cut {} cells near water {}", tag, roadCells, floating, headroom, steps, borderSteps, cutMax, nearWater);
		if (!voidAt.isEmpty()) FractalBuildingsMod.LOGGER.info("[B3] {} void: {}", tag, voidAt);
		if (!floatAt.isEmpty()) FractalBuildingsMod.LOGGER.info("[B3] {} floating: {}", tag, floatAt);
		if (!headAt.isEmpty()) FractalBuildingsMod.LOGGER.info("[B3] {} headroom: {}", tag, headAt);
		if (!stepAt.isEmpty()) FractalBuildingsMod.LOGGER.info("[B3] {} steps: {}", tag, stepAt);
		surfaceHistogram(level, s, tag);
		if (voidCols > 0) failures.add(tag + ": " + voidCols + " footprint columns with air under the floor " + voidAt);
		if (floating > 0) failures.add(tag + ": " + floating + " road/paving/deck cells with nothing under them " + floatAt);
		if (headroom > 0) failures.add(tag + ": " + headroom + " road columns with solid terrain within 3 blocks above " + headAt);
		// steps inside a tile are the planner's street junctions (known issue, GroundWalkTest ratchets it); a step across the tile border is a tile disagreement
		if (borderSteps > 0) failures.add(tag + ": " + borderSteps + " neighbouring road columns across the tile border more than 1 block apart " + stepAt);
		if (kind.equals("shore")) return camera(level, kind, wx, wy, wz);
		return camera(level, kind, camX, camY, camZ);
	}

	/** Which blocks form the surface in and around the tile (answers "what is the white patch": snow, calcite, ...). */
	private static void surfaceHistogram(ServerLevel level, Site s, String tag) {
		Map<String, Integer> h = new TreeMap<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		String firstWhite = null;
		for (int z = s.minZ() - 64; z < s.maxZ() + 64; z += 2) for (int x = s.minX() - 64; x < s.maxX() + 64; x += 2) {
			if (!level.hasChunk(x >> 4, z >> 4)) continue;
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			BlockState st = level.getBlockState(p.set(x, y, z));
			String id = st.getBlock().getDescriptionId();
			h.merge(id, 1, Integer::sum);
			if (firstWhite == null && (id.contains("snow") || id.contains("calcite") || id.contains("white"))) firstWhite = id + " at " + x + "," + y + "," + z + " biome " + level.getBiome(p).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		}
		FractalBuildingsMod.LOGGER.info("[B3] {} surface blocks around the tile (every 2nd column): {}; first whitish: {}", tag, h, firstWhite);
	}
}
