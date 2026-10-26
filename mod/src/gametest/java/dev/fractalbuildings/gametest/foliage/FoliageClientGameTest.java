package dev.fractalbuildings.gametest.foliage;

import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.RoadNet.EdgeKey;
import dev.fractalbuildings.core.road.RoadNet.Route;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.FoliageClear;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/**
 * FOLIAGE (John's playtest 2026-10-23): no leaf, log or vine hangs in the air above a street or an intercity road in a forest. For each
 * seed a fresh world is built twice, once with {@link FoliageClear#ENABLED} off (the "before" control: orphans must exist, or the test
 * proves nothing) and once on. In each, the most forested settlement within reach of the origin and the most forested stretch of road
 * are generated, and the 14 blocks above every street column (ledger: the highest claimed cell is air) and road column are read: a leaf
 * is an orphan when no log is within 6 steps through leaves (vanilla's decay distance), a log when no chain of logs reaches a log that
 * stands on something, a vine when nothing holds it. The same camera spots are shot in both worlds.
 *
 * <p>Env: {@code FB_FOLIAGE_SEEDS} (comma list, default the pinned seeds), {@code FB_FOLIAGE=0} skips, {@code FB_FOLIAGE_SCAN=1} only lists
 * the forest share of the settlements and routes near the origin of each seed (to choose seeds).
 */
public class FoliageClientGameTest implements FabricClientGameTest {
	static final StringBuilder OUT = new StringBuilder();
	static final List<String> PROBLEMS = new ArrayList<>();
	/** Blocks above a street or road surface that are examined. */
	static final int ABOVE = 14;

	static void say(String s) {
		System.out.println("FOLIAGE " + s);
		OUT.append(s).append('\n');
	}

	static void problem(String s) {
		PROBLEMS.add(s);
		say("PROBLEM " + s);
	}

	static boolean forest(ServerLevel level, int x, int z) {
		String p = level.getBiome(new BlockPos(x, 70, z)).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return p.contains("forest") || p.contains("taiga") || p.contains("jungle") || p.contains("grove") || p.contains("birch") || p.contains("cherry");
	}

	static double forestShare(ServerLevel level, double cx, double cz, double r) {
		int n = 0, f = 0;
		for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) {
			if (i * i + j * j > 9) continue;
			n++;
			if (forest(level, (int) (cx + i * r / 3), (int) (cz + j * r / 3))) f++;
		}
		return (double) f / n;
	}

	/** What the test measures in one world. */
	record Spot(String kind, int x, int y, int z, int orphans) {}

	static final class Count {
		int columns, leaves, orphanLeaves, orphanLogs, orphanVines, headroom;
		final List<int[]> cols = new ArrayList<>();
		final List<String> headSamples = new ArrayList<>();
		final List<String> samples = new ArrayList<>();
		final List<int[]> sampleXyz = new ArrayList<>();
		Spot worst;
		String line() { return "foliage in the clear volume (surface+1..4) at the end " + headroom + " (grew in after the chunk pass; SpillClear removes natural leaves there, CITYQ r7); " + columns + " columns, " + leaves + " leaves above them, orphans: " + orphanLeaves + " leaves, " + orphanLogs + " logs, " + orphanVines + " vines"; }
		int orphans() { return orphanLeaves + orphanLogs + orphanVines; }
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("FoliageClientGameTest")) return;
		if ("0".equals(System.getenv("FB_FOLIAGE"))) return;
		String[] seeds = System.getenv().getOrDefault("FB_FOLIAGE_SEEDS", "1,31337").split(",");
		boolean scan = "1".equals(System.getenv("FB_FOLIAGE_SCAN"));
		for (String seed : seeds) {
			if (scan) {
				scan(context, seed);
				continue;
			}
			Count[] before = new Count[2], after = new Count[2];
			Spot[] spots = new Spot[2];
			for (int pass = 0; pass < 2; pass++) {
				boolean on = pass == 1;
				FoliageClear.ENABLED = on;
				dev.fractalbuildings.mod.worldgen.SpillClear.LEAF_CLEAR = on; // CITYQ r7: the control also leaves the late crowns, so the headroom check has a signal
				dev.fractalbuildings.mod.worldgen.SpillClear.LEAVES.set(0);
				FoliageClear.REMOVED.set(0);
				FoliageClear.RESIDUAL.set(0);
				FoliageClear.RESIDUAL_STREET.set(0);
				FoliageClear.RESIDUAL_SAMPLES.set(0);
				FoliageClear.CUT_KEPT.set(0);
				FoliageClear.MEASURE = true;
				try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
					Count[] c = new Count[2];
					AtomicReference<int[][]> cams = new AtomicReference<>();
					world.getServer().runOnServer(server -> {
						ServerLevel level = server.overworld();
						WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
						WorldPlanners.FAILURES.set(0);
						c[0] = streets(level, planner, spots, on);
						c[1] = roads(level, planner, spots, on);
						if (WorldPlanners.FAILURES.get() != 0) problem("swallowed exceptions, see the log");
					});
					say("seed " + seed + (on ? " AFTER " : " BEFORE ") + "street " + c[0].line() + " | road " + c[1].line() + " | removed by the pass " + FoliageClear.REMOVED.get() + " | leaves SpillClear removed " + dev.fractalbuildings.mod.worldgen.SpillClear.LEAVES.get() + " | foliage left in the cleared volume by the passes themselves (surface+1..4, own columns): " + FoliageClear.RESIDUAL.get() + " (street pieces " + FoliageClear.RESIDUAL_STREET.get() + ")" + " | trees still standing that the clear cut a cell of (crown notched from below): " + FoliageClear.CUT_KEPT.get());
					if (on) for (Count cc : c) for (String sm : cc.samples) say("  orphan " + sm);
					if (on) for (Count cc : c) for (String sm : cc.headSamples) say("  headroom " + sm);
					if (on) { after = c; if (FoliageClear.RESIDUAL.get() != 0) problem("seed " + seed + ": the passes left " + FoliageClear.RESIDUAL.get() + " foliage blocks in the cleared volume (surface+1..4) of their own columns"); } else { before = c; }
					shoot(context, world, spots, on ? "after" : "before", seed);
				} finally {
					FoliageClear.ENABLED = true;
					dev.fractalbuildings.mod.worldgen.SpillClear.LEAF_CLEAR = true;
					FoliageClear.MEASURE = false;
				}
			}
			String[] names = {"street", "road"};
			for (int k = 0; k < 2; k++) {
				if (before[k].orphans() < 5) say("seed " + seed + " " + names[k] + ": the control found only " + before[k].orphans() + " orphans, no signal here");
				// foliage inside the clear volume at the end (headroom) = what grew after a chunk pass: a neighbour chunk tree spilling in. SpillClear (street air) and SpillClear.roadLeaves (road headroom) remove natural leaves there (CITYQ r7), so none may stay.
				if (after[k].headroom != 0) problem("seed " + seed + " " + names[k] + ": " + after[k].headroom + " natural leaves in the clear volume (surface+1..4) at the end, samples " + after[k].headSamples);
				if (after[k].orphans() != 0) problem("seed " + seed + " " + names[k] + ": " + after[k].orphans() + " orphans above " + after[k].columns + " columns remain (" + after[k].line() + "), before " + before[k].orphans());
				if (before[k].columns != after[k].columns && Math.abs(before[k].columns - after[k].columns) > 4) say("columns differ between the worlds: " + before[k].columns + " vs " + after[k].columns);
			}
			say("seed " + seed + " headroom control (leaf clear off): street " + before[0].headroom + ", road " + before[1].headroom + (before[0].headroom + before[1].headroom == 0 ? " (no late crowns here: the headroom check has no signal on this seed)" : ""));
			if (before[0].orphans() + before[1].orphans() < 5) problem("seed " + seed + ": the control (pass off) found fewer than 5 orphans in all, the test cannot fail: pick another seed");
		}
		try {
			Files.writeString(Path.of("foliage-results.txt"), OUT.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (!PROBLEMS.isEmpty()) throw new AssertionError("foliage test: " + PROBLEMS);
	}

	static void scan(ClientGameTestContext context, String seed) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				RoadNet net = planner.roads();
				for (Settlement s : net.settlementsNear(0, 0, 2600)) {
					say(String.format("scan seed %s settlement %s at %d,%d r %d forest %.2f", seed, s.tier(), s.x(), s.z(), s.r(), forestShare(level, s.x(), s.z(), s.r())));
				}
				for (Settlement s : net.settlementsNear(0, 0, 2600)) {
					for (EdgeKey e : net.edgesOf(s)) {
						Route r = net.route(e);
						if (!r.ok || r.touching) continue;
						int f = 0;
						for (double[] st : r.stations) if (forest(level, (int) st[0], (int) st[1])) f++;
						say("scan seed " + seed + " route " + e + " length " + (int) r.length + " forest stations " + f + " of " + r.stations.length);
					}
				}
			});
		}
	}

	// ---- settlement streets

	static Count streets(ServerLevel level, WorldPlanner planner, Spot[] spots, boolean on) {
		RoadNet net = planner.roads();
		Settlement best = null;
		double bestF = -1;
		for (Settlement s : net.settlementsNear(0, 0, 2600)) {
			if (s.r() < 30 || s.r() > 120) continue;
			double f = forestShare(level, s.x(), s.z(), s.r());
			if (f > bestF) { bestF = f; best = s; }
		}
		if (best == null) throw new IllegalStateException("no settlement near the origin");
		int rr = Math.min(best.r() + 16, 96);
		say("street settlement " + best.tier() + " at " + best.x() + "," + best.z() + " r " + best.r() + " forest " + String.format("%.2f", bestF));
		List<ChunkPos> chunks = new ArrayList<>();
		for (int cx = Math.floorDiv(best.x() - rr, 16); cx <= Math.floorDiv(best.x() + rr, 16); cx++) {
			for (int cz = Math.floorDiv(best.z() - rr, 16); cz <= Math.floorDiv(best.z() + rr, 16); cz++) chunks.add(new ChunkPos(cx, cz));
		}
		Count c = new Count();
		int airId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState());
		try (PieceLedger.Recording rec = PieceLedger.start()) {
			for (ChunkPos p : chunks) level.getChunkSource().getChunk(p.x(), p.z(), ChunkStatus.FULL, true);
			// street columns: the highest claimed cell is air, with a solid claimed cell right under the claimed air
			Map<Long, int[]> col = new TreeMap<>(); // (x,z) packed with y 0 -> {topSolid, maxClaimed}
			for (var e : rec.states().entrySet()) {
				long k = e.getKey();
				long key = PieceLedger.pack(PieceLedger.x(k), 0, PieceLedger.z(k));
				int y = PieceLedger.y(k);
				int[] v = col.computeIfAbsent(key, kk -> new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE});
				if (e.getValue() != airId) v[0] = Math.max(v[0], y);
				v[1] = Math.max(v[1], y);
			}
			Set<Long> seen = new HashSet<>();
			for (var e : col.entrySet()) {
				int[] v = e.getValue();
				if (v[0] == Integer.MIN_VALUE || v[1] <= v[0]) continue;
				int x = PieceLedger.x(e.getKey()), z = PieceLedger.z(e.getKey());
				if (Math.hypot(x - best.x(), z - best.z()) > best.r() + 8) continue;
				if (seen.add(e.getKey())) measure(level, c, x, v[0], z, "street", on);
			}
		}
		c.headroom = headroom(level, c, false);
		spots[0] = c.worst;
		return c;
	}

	// ---- intercity roads

	static Count roads(ServerLevel level, WorldPlanner planner, Spot[] spots, boolean on) {
		RoadNet net = planner.roads();
		Route best = null;
		int bestF = -1, bestFrom = 0;
		for (Settlement s : net.settlementsNear(0, 0, 2600)) {
			for (EdgeKey e : net.edgesOf(s)) {
				Route r = net.route(e);
				if (!r.ok || r.touching) continue;
				int win = 60; // stations
				for (int from = 0; from + win <= r.stations.length; from += 10) {
					int f = 0;
					boolean far = true;
					for (int k = from; k < from + win; k++) {
						double[] st = r.stations[k];
						if (forest(level, (int) st[0], (int) st[1])) f++;
						double da = Math.hypot(st[0] - r.a.x(), st[1] - r.a.z()) - r.a.r(), db = Math.hypot(st[0] - r.b.x(), st[1] - r.b.z()) - r.b.r();
						if (Math.min(da, db) < 330) far = false;
					}
					if (far && f > bestF) { bestF = f; best = r; bestFrom = from; }
				}
			}
		}
		if (best == null) throw new IllegalStateException("no route far from the settlements");
		say("road " + best.key + " stations " + bestFrom + ".." + (bestFrom + 60) + " forest stations " + bestF + " of 60");
		Set<ChunkPos> chunks = new java.util.LinkedHashSet<>();
		for (int k = bestFrom; k < bestFrom + 60; k++) {
			int x = (int) Math.floor(best.stations[k][0]), z = (int) Math.floor(best.stations[k][1]);
			for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) chunks.add(new ChunkPos((x >> 4) + ox, (z >> 4) + oz));
		}
		for (ChunkPos p : chunks) level.getChunkSource().getChunk(p.x(), p.z(), ChunkStatus.FULL, true);
		Count c = new Count();
		Set<Long> seen = new HashSet<>();
		for (int k = bestFrom; k < bestFrom + 60; k++) {
			int sx = (int) Math.floor(best.stations[k][0]), sz = (int) Math.floor(best.stations[k][1]);
			for (Col col : planner.roadColumns(sx >> 4, sz >> 4)) {
				if (Math.abs(col.x() - sx) > 3 || Math.abs(col.z() - sz) > 3) continue;
				// columns of the chunks around the stretch have all their neighbours generated: those inside the corridor
				if (!chunks.contains(new ChunkPos((col.x() - 8) >> 4, (col.z() - 8) >> 4)) || !chunks.contains(new ChunkPos((col.x() + 8) >> 4, (col.z() + 8) >> 4))) continue;
				if (seen.add(PieceLedger.pack(col.x(), 0, col.z()))) measure(level, c, col.x(), col.y() - 1, col.z(), "road", on);
			}
		}
		c.headroom = headroom(level, c, false);
		spots[1] = c.worst;
		return c;
	}

	// ---- reading the world

	/** Foliage (any leaf, log or vine, held or not) in the clear volume, surface +1..+4, over the measured columns. */
	static int headroom(ServerLevel level, Count c, boolean late) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int[] col : c.cols) for (int h = 1; h <= 4; h++) {
			BlockState s = level.getBlockState(pos.set(col[0], col[1] + h, col[2]));
			if (s.is(BlockTags.LEAVES) && !s.getValueOrElse(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)) {
				n++;
				if (c.headSamples.size() < 12) c.headSamples.add((late ? "late " : "") + col[0] + "," + (col[1] + h) + "," + col[2] + " " + s.getBlock().getDescriptionId());
			}
		}
		return n;
	}

	static final int[][] FACE = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** Reads the {@link #ABOVE} blocks over the surface block (x, surf, z) and counts what hangs. */
	static void measure(ServerLevel level, Count c, int x, int surf, int z, String kind, boolean on) {
		c.columns++;
		c.cols.add(new int[] {x, surf, z});
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int here = 0;
		for (int h = 1; h <= ABOVE; h++) {
			int y = surf + h;
			BlockState s = level.getBlockState(pos.set(x, y, z));
			if (s.is(BlockTags.LEAVES)) {
				c.leaves++;
				if (!leafHeld(level, x, y, z)) { c.orphanLeaves++; here++; if (c.samples.size() < 40) c.sampleXyz.add(new int[] {x, y, z}); c.samples.add(kind + " leaf " + x + "," + y + "," + z + " chunk-local " + (x & 15) + "," + (z & 15) + " h " + h + " " + s.getBlock().getDescriptionId()); }
			} else if (s.is(BlockTags.LOGS)) {
				if (!logHeld(level, x, y, z)) { c.orphanLogs++; here++; }
			} else if (s.is(Blocks.VINE)) {
				if (!vineHeld(level, x, y, z)) { c.orphanVines++; here++; }
			}
		}
		if (here > 0 && (c.worst == null || here > c.worst.orphans())) c.worst = new Spot(kind, x, surf, z, here);
	}

	static boolean leafHeld(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {x, y, z, 0});
		seen.add(PieceLedger.pack(x, y, z));
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int[] f : FACE) {
				int nx = c[0] + f[0], ny = c[1] + f[1], nz = c[2] + f[2];
				BlockState s = level.getBlockState(pos.set(nx, ny, nz));
				if (s.is(BlockTags.LOGS)) return true;
				if (s.is(BlockTags.LEAVES) && c[3] < 6 && seen.add(PieceLedger.pack(nx, ny, nz))) q.add(new int[] {nx, ny, nz, c[3] + 1});
			}
		}
		return false;
	}

	/** A log is held when a chain of logs (26-neighbour) reaches one standing on a block that is not foliage or air. */
	static boolean logHeld(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {x, y, z});
		seen.add(PieceLedger.pack(x, y, z));
		while (!q.isEmpty() && seen.size() < 4000) {
			int[] c = q.poll();
			BlockState below = level.getBlockState(pos.set(c[0], c[1] - 1, c[2]));
			if (!below.isAir() && !below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES) && !below.is(Blocks.VINE) && !below.is(Blocks.SNOW)) return true;
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				int nx = c[0] + dx, ny = c[1] + dy, nz = c[2] + dz;
				if (level.getBlockState(pos.set(nx, ny, nz)).is(BlockTags.LOGS) && seen.add(PieceLedger.pack(nx, ny, nz))) q.add(new int[] {nx, ny, nz});
			}
		}
		return seen.size() >= 4000; // a huge connected tree: held
	}

	static boolean vineHeld(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int[] f : FACE) {
			if (f[1] == -1) continue;
			BlockState s = level.getBlockState(pos.set(x + f[0], y + f[1], z + f[2]));
			if (s.isAir() || s.canBeReplaced()) continue;
			if (s.is(Blocks.VINE)) { if (f[1] == 1) return true; continue; }
			return true;
		}
		return false;
	}

	// ---- screenshots

	static void shoot(ClientGameTestContext context, TestSingleplayerContext world, Spot[] spots, String phase, String seed) {
		context.runOnClient(client -> client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
		world.getServer().runCommand("gamemode spectator @p");
		String[] names = {"street", "road"};
		for (int k = 0; k < 2; k++) {
			Spot sp = ShotSpots.get(seed, k, spots[k]);
			if (sp == null) continue;
			double[] cam = new double[3];
			double[] cached = ShotSpots.CAMS.get(seed + "/" + k);
			if (cached != null) System.arraycopy(cached, 0, cam, 0, 3);
			else world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				double[] best = null;
				double bestClear = -1;
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 8;
					double cx = sp.x() + 0.5 + Math.cos(ang) * 11, cz = sp.z() + 0.5 + Math.sin(ang) * 11, cy = sp.y() + 6;
					// how far the line from the camera to the target is clear of blocks
					double clear = 0;
					for (int t = 0; t <= 20; t++) {
						double f = t / 20.0;
						BlockPos p = BlockPos.containing(cx + (sp.x() + 0.5 - cx) * f, cy + (sp.y() + 4 - cy) * f, cz + (sp.z() + 0.5 - cz) * f);
						if (!level.getBlockState(p).isAir()) break;
						clear = f;
					}
					BlockPos at = BlockPos.containing(cx, cy, cz);
					if (!level.getBlockState(at).isAir()) clear = -1;
					if (clear > bestClear) { bestClear = clear; best = new double[] {cx, cy, cz}; }
				}
				System.arraycopy(best, 0, cam, 0, 3);
			});
			ShotSpots.CAMS.putIfAbsent(seed + "/" + k, cam.clone());
			double dx = sp.x() + 0.5 - cam[0], dz = sp.z() + 0.5 - cam[2], dy = (sp.y() + 7) - cam[1];
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			world.getServer().runCommand(String.format("tp @p %.1f %.1f %.1f %.1f %.1f", cam[0], cam[1], cam[2], yaw, pitch));
			// a far teleport is held until the terrain there is FULL (TPHOLD): wait until the client player stands at the camera, or the shot shows "Generating terrain..."
			final double[] want = cam;
			for (int w = 0; w < 120; w++) {
				context.waitTicks(20);
				java.util.concurrent.atomic.AtomicBoolean there = new java.util.concurrent.atomic.AtomicBoolean();
				context.runOnClient(client -> there.set(client.player != null && client.player.distanceToSqr(want[0], want[1], want[2]) < 4));
				if (there.get()) break;
			}
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				say("chunks did not finish rendering in time; taking the screenshot anyway");
			}
			context.waitTicks(20);
			context.takeScreenshot("foliage-s" + seed + "-" + names[k] + "-" + phase);
			say("screenshot foliage-s" + seed + "-" + names[k] + "-" + phase + " at " + sp.x() + "," + sp.y() + "," + sp.z() + " from " + (int) cam[0] + "," + (int) cam[1] + "," + (int) cam[2]);
		}
	}

	/** The spot to shoot: the worst one of the "before" world, remembered for the "after" world of the same seed (same terrain). */
	static final class ShotSpots {
		static final Map<String, Spot> SPOTS = new TreeMap<>();
		static final Map<String, double[]> CAMS = new TreeMap<>();

		static Spot get(String seed, int kind, Spot measured) {
			String key = seed + "/" + kind;
			if (measured != null && !SPOTS.containsKey(key)) SPOTS.put(key, measured);
			return SPOTS.get(key);
		}
	}
}
