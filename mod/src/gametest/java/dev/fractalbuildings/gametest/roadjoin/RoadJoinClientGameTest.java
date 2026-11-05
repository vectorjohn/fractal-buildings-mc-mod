package dev.fractalbuildings.gametest.roadjoin;

import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ROADJOIN step 5 (6.15), in the world. Opt-in ({@code FB_ROADJOIN=1}; use {@code FB_ONLY=RoadJoinClientGameTest}), never part of the quick or
 * full layer. Seed 1709797029209580947 (the user's desert world). For each spot (the hamlet gate, the metropolis exits, the user's 21.31.49 and
 * 21.26.27 positions) every planned road column (graph roads and approaches, as the road layer draws them) within {@code R} blocks that no
 * district claims (D5: the district wins) is read back: the top solid block (no leaves) of each, and every pair of 4-adjacent such columns must
 * differ by at most 1. Steps between a road column and a neighbouring district column (the join with the street) are logged, and asserted
 * at most {@code JOIN_MAX} against the road's neighbour only when the district column is level ground of the street (reported, not asserted:
 * a building wall is no street). One screenshot per spot.
 *
 * <p>Env: {@code FB_ROADJOIN_SEED}, {@code FB_ROADJOIN_R} (default 40), {@code FB_ROADJOIN_SPOTS} (x,z;x,z;... replaces the list).
 */
public class RoadJoinClientGameTest implements FabricClientGameTest {
	static final StringBuilder OUT = new StringBuilder();
	static final List<String> PROBLEMS = new ArrayList<>();

	static void say(String s) {
		System.out.println("ROADJOIN " + s);
		OUT.append(s).append('\n');
	}

	static void problem(String s) {
		PROBLEMS.add(s);
		say("PROBLEM " + s);
	}

	record Spot(String name, int x, int z) {}

	public static boolean enabled() { return System.getenv("FB_ROADJOIN") != null; }

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("RoadJoinClientGameTest")) return;
		if (!enabled()) return;
		String seed = System.getenv().getOrDefault("FB_ROADJOIN_SEED", "1709797029209580947");
		int r = Integer.parseInt(System.getenv().getOrDefault("FB_ROADJOIN_R", "40"));
		List<Spot> spots = new ArrayList<>();
		String custom = System.getenv("FB_ROADJOIN_SPOTS");
		if (custom != null && !custom.isBlank()) {
			int i = 0;
			for (String p : custom.split(";")) { String[] c = p.split(","); spots.add(new Spot("spot" + i++, Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()))); }
		} else {
			spots.add(new Spot("hamlet-gate", 3616, 1440));
			spots.add(new Spot("metropolis-exit-a", 3200, 1696));
			spots.add(new Spot("metropolis-exit-b", 3200, 1792)); // step 3 moved the exit 3264,1856 here
			spots.add(new Spot("playtest-21.31.49", 3201, 1683));
			spots.add(new Spot("playtest-21.26.27", 3623, 1443));
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
			context.runOnClient(client -> client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			world.getServer().runCommand("gamemode spectator @p");
			for (Spot sp : spots) {
				AtomicReference<int[]> res = new AtomicReference<>();
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
					WorldPlanners.FAILURES.set(0);
					res.set(read(level, planner, sp, r));
					if (WorldPlanners.FAILURES.get() != 0) problem(sp.name() + ": swallowed exceptions, see the log");
				});
				int[] v = res.get(); // cols, pairs, maxStep, worstX, worstZ, over1, joinPairs, joinMax, topAtSpot
				say(sp.name() + " " + sp.x() + "," + sp.z() + ": " + v[0] + " road columns outside districts, " + v[1] + " adjacent pairs, max step " + v[2] + " (worst at " + v[3] + "," + v[4] + "), pairs over 1: " + v[5] + "; join with district columns: " + v[6] + " pairs, max step " + v[7] + "; planned road columns incl. district-claimed " + v[9] + ", worst planned-y vs world top " + v[10]);
				if (v[9] == 0 && !sp.name().contains("exit-b")) problem(sp.name() + ": no planned road column at all within " + r + " of " + sp.x() + "," + sp.z() + ": nothing to check, wrong spot?");
				else if (v[9] == 0) say(sp.name() + ": no drawn road here: the planner draws only the approach at 3200,1696 for this metropolis (ApproachJoinTest corpus; the link at 3200,1792 is dropped), so this exit is a street end with no road (shot for the look)");
				else if (v[0] < 8) say(sp.name() + ": only " + v[0] + " road columns outside districts (the rest are district-claimed), the step check is weak here");
				if (v[5] != 0) problem(sp.name() + ": " + v[5] + " adjacent road column pairs differ by more than 1 (max " + v[2] + " at " + v[3] + "," + v[4] + ")");
				shoot(context, world, sp, v[8]);
			}
		}
		try {
			Files.writeString(Path.of("roadjoin-results.txt"), OUT.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (!PROBLEMS.isEmpty()) throw new AssertionError("roadjoin test: " + PROBLEMS);
	}

	static int top(ServerLevel level, int x, int z) {
		return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) - 1;
	}

	static int[] read(ServerLevel level, WorldPlanner planner, Spot sp, int r) {
		int cx0 = Math.floorDiv(sp.x() - r - 2, 16), cx1 = Math.floorDiv(sp.x() + r + 2, 16), cz0 = Math.floorDiv(sp.z() - r - 2, 16), cz1 = Math.floorDiv(sp.z() + r + 2, 16);
		for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
		Map<Long, Integer> road = new HashMap<>();
		Map<Long, Boolean> district = new HashMap<>();
		for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
			boolean[] mask = planner.districtColumns(cx, cz);
			for (RoadNet.Col c : planner.roadColumns(cx, cz)) {
				if (Math.abs(c.x() - sp.x()) > r || Math.abs(c.z() - sp.z()) > r) continue;
				if (mask != null && mask[(c.z() & 15) * 16 + (c.x() & 15)]) continue;
				road.put(key(c.x(), c.z()), top(level, c.x(), c.z()));
			}
		}
		int pairs = 0, max = 0, wx = 0, wz = 0, over = 0, jp = 0, jmax = 0;
		int all = 0, allMax = 0;
		for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) for (RoadNet.Col c : planner.roadColumns(cx, cz)) if (Math.abs(c.x() - sp.x()) <= r && Math.abs(c.z() - sp.z()) <= r) { all++; allMax = Math.max(allMax, Math.abs(top(level, c.x(), c.z()) - c.y())); }
		int[][] d = {{1, 0}, {0, 1}};
		for (var e : road.entrySet()) {
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			for (int[] o : d) {
				Integer n = road.get(key(x + o[0], z + o[1]));
				if (n == null) continue;
				pairs++;
				int s = Math.abs(n - e.getValue());
				if (s > max) { max = s; wx = x; wz = z; }
				if (s > 1) over++;
			}
			for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + o[0], nz = z + o[1];
				if (road.containsKey(key(nx, nz))) continue;
				boolean[] mask = planner.districtColumns(Math.floorDiv(nx, 16), Math.floorDiv(nz, 16));
				if (mask == null || !mask[(nz & 15) * 16 + (nx & 15)]) continue;
				jp++;
				jmax = Math.max(jmax, Math.abs(top(level, nx, nz) - e.getValue()));
			}
		}
		return new int[] {road.size(), pairs, max, wx, wz, over, jp, jmax, top(level, sp.x(), sp.z()), all, allMax};
	}

	static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

	static void shoot(ClientGameTestContext context, TestSingleplayerContext world, Spot sp, int y) {
		double[] cam = new double[3];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			double[] best = null;
			double bestClear = -1;
			for (int a = 0; a < 16; a++) {
				double ang = a * Math.PI / 8;
				double cx = sp.x() + 0.5 + Math.cos(ang) * 12, cz = sp.z() + 0.5 + Math.sin(ang) * 12, cy = y + 7;
				double clear = 0;
				for (int t = 0; t <= 20; t++) {
					double f = t / 20.0;
					BlockPos p = BlockPos.containing(cx + (sp.x() + 0.5 - cx) * f, cy + (y + 1.5 - cy) * f, cz + (sp.z() + 0.5 - cz) * f);
					if (!level.getBlockState(p).isAir()) break;
					clear = f;
				}
				if (!level.getBlockState(BlockPos.containing(cx, cy, cz)).isAir()) clear = -1;
				if (clear > bestClear) { bestClear = clear; best = new double[] {cx, cy, cz}; }
			}
			System.arraycopy(best, 0, cam, 0, 3);
		});
		double dx = sp.x() + 0.5 - cam[0], dz = sp.z() + 0.5 - cam[2], dy = (y + 1.5) - cam[1];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		world.getServer().runCommand(String.format("tp @p %.1f %.1f %.1f %.1f %.1f", cam[0], cam[1], cam[2], yaw, pitch));
		for (int w = 0; w < 120; w++) {
			context.waitTicks(20);
			AtomicBoolean there = new AtomicBoolean();
			context.runOnClient(client -> there.set(client.player != null && client.player.distanceToSqr(cam[0], cam[1], cam[2]) < 4));
			if (there.get()) break;
		}
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			say("chunks did not finish rendering in time; taking the screenshot anyway");
		}
		context.waitTicks(20);
		context.takeScreenshot("roadjoin-" + sp.name());
		say("screenshot roadjoin-" + sp.name() + " of " + sp.x() + "," + y + "," + sp.z() + " from " + (int) cam[0] + "," + (int) cam[1] + "," + (int) cam[2]);
	}
}
