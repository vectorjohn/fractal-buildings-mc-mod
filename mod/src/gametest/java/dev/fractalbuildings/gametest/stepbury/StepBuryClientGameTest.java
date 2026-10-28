package dev.fractalbuildings.gametest.stepbury;

import dev.fractalbuildings.core.gen.v1.InstancePassV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.BuildingAirClear;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * STEPBURY (6.13) and TREEIN (6.14), in the world.
 *
 * <p><b>Steps</b> (John's desert world, seed 1709797029209580947, the high-rises near 1266,2411): every exterior step of the buildings
 * within 70 blocks of that point is read back: the two blocks over it must not be solid (a flight closed by the terrain a {@code beard_thin}
 * raised, sand), and the step block itself must be there. The world is built twice: with the claimed headroom off
 * ({@link InstancePassV1#HEADROOM_ENABLED}, the control: it must find buried steps, or the test proves nothing) and on.
 *
 * <p><b>Trees</b> (a forest settlement of seed 1): with {@link BuildingAirClear#ENABLED} off (control) and on, every cell that a building
 * claims as air (from the {@link PieceLedger}, inside a building piece's box, hook blocks set aside) is read: natural leaves, vines and
 * logs must not be there. Screenshots {@code stepbury-*} and {@code treein-*} show the same spot in both worlds.
 *
 * <p>Env: {@code FB_STEPBURY=0} skips, {@code FB_STEPBURY_PART=steps|trees} runs one part, {@code FB_STEPBURY_SEED} / {@code FB_STEPBURY_AT} (x,z) change the desert spot, {@code FB_TREEIN_SEED} the forest seed.
 */
public class StepBuryClientGameTest implements FabricClientGameTest {
	static final StringBuilder OUT = new StringBuilder();
	static final List<String> PROBLEMS = new ArrayList<>();

	static void say(String s) {
		System.out.println("STEPBURY " + s);
		OUT.append(s).append('\n');
	}

	static void problem(String s) {
		PROBLEMS.add(s);
		say("PROBLEM " + s);
	}

	record Spot(int x, int y, int z, String what) {}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("StepBuryClientGameTest")) return;
		if ("0".equals(System.getenv("FB_STEPBURY"))) return;
		try {
			String part = System.getenv().getOrDefault("FB_STEPBURY_PART", "all");
			if (!part.equals("trees")) steps(context);
			if (!part.equals("steps")) trees(context);
		} finally {
			InstancePassV1.HEADROOM_ENABLED = true;
			BuildingAirClear.ENABLED = true;
		}
		try {
			Files.writeString(Path.of("stepbury-results.txt"), OUT.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (!PROBLEMS.isEmpty()) throw new AssertionError("stepbury/treein test: " + PROBLEMS);
	}

	// ---- steps

	record StepCount(int steps, int buried, int missing, Spot worst, List<String> samples) {}

	void steps(ClientGameTestContext context) {
		String seed = System.getenv().getOrDefault("FB_STEPBURY_SEED", "1709797029209580947");
		String[] at = System.getenv().getOrDefault("FB_STEPBURY_AT", "1266,2411").split(",");
		int ax = Integer.parseInt(at[0]), az = Integer.parseInt(at[1]);
		StepCount[] res = new StepCount[2];
		for (int pass = 0; pass < 2; pass++) {
			boolean on = pass == 1;
			InstancePassV1.HEADROOM_ENABLED = on;
			try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
				AtomicReference<StepCount> out = new AtomicReference<>();
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
					WorldPlanners.FAILURES.set(0);
					out.set(readSteps(level, planner, ax, az, 70));
					if (WorldPlanners.FAILURES.get() != 0) problem("swallowed exceptions, see the log");
				});
				res[pass] = out.get();
				say("steps seed " + seed + " near " + ax + "," + az + (on ? " HEADROOM ON : " : " HEADROOM OFF (control): ") + res[pass].steps + " steps, " + res[pass].buried + " with solid blocks in the two cells over them, " + res[pass].missing + " missing; " + res[pass].samples);
				Spot sp = res[0].worst; // the control's worst step, shot in both worlds
				if (sp != null) shoot(context, world, sp, "stepbury-" + (on ? "after" : "before"), 7);
			} finally {
				InstancePassV1.HEADROOM_ENABLED = true;
			}
		}
		if (res[1].steps < 10) problem("steps: only " + res[1].steps + " steps within reach of " + ax + "," + az + ", pick another spot (FB_STEPBURY_AT)");
		if (res[1].buried != 0) problem("steps: " + res[1].buried + " of " + res[1].steps + " steps have solid blocks over them with the headroom claimed: " + res[1].samples);
		if (res[1].missing != 0) problem("steps: " + res[1].missing + " step blocks missing");
		if (res[0].buried == 0) problem("steps: the control (headroom off) found no buried step near " + ax + "," + az + ": the test cannot fail here, pick another spot");
	}

	static StepCount readSteps(ServerLevel level, WorldPlanner planner, int ax, int az, int r) {
		List<BuildingPiece> pieces = pieces(planner, ax, az, r);
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (BuildingPiece b : pieces) {
			Box3 pb = b.box();
			for (int cx = Math.floorDiv(pb.x() - 20, 16); cx <= Math.floorDiv(pb.maxX() + 20, 16); cx++) for (int cz = Math.floorDiv(pb.z() - 20, 16); cz <= Math.floorDiv(pb.maxZ() + 20, 16); cz++) chunks.add(new ChunkPos(cx, cz));
		}
		for (ChunkPos p : chunks) level.getChunkSource().getChunk(p.x(), p.z(), ChunkStatus.FULL, true);
		int steps = 0, buried = 0, missing = 0;
		Spot worst = null;
		int worstN = 0;
		List<String> samples = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (BuildingPiece b : pieces) {
			for (Box s : b.building().siteBoxes()) {
				if (s.role() != Role.STAIR) continue;
				Box3 w = b.building().placement().toWorld(s.box());
				for (int y = w.y(); y < w.maxY(); y++) for (int x = w.x(); x < w.maxX(); x++) for (int z = w.z(); z < w.maxZ(); z++) {
					steps++;
					if (level.getBlockState(pos.set(x, y, z)).isAir()) missing++;
					int n = 0;
					for (int h = 1; h <= 2; h++) if (!level.getBlockState(pos.set(x, y + h, z)).getCollisionShape(level, pos).isEmpty()) n++;
					if (n > 0) {
						buried++;
						if (n > worstN || worst == null) { worstN = n; worst = new Spot(x, y, z, b.ref().toString()); }
						if (samples.size() < 5) samples.add(x + "," + y + "," + z + " " + level.getBlockState(pos.set(x, y + 1, z)).getBlock().getDescriptionId());
					}
				}
			}
		}
		return new StepCount(steps, buried, missing, worst, samples);
	}

	static List<BuildingPiece> pieces(WorldPlanner planner, int ax, int az, int r) {
		List<BuildingPiece> out = new ArrayList<>();
		DistrictGrid.TileId ta = planner.grid().tileOf(ax - r, az - r), tb = planner.grid().tileOf(ax + r, az + r);
		for (int iz = ta.iz(); iz <= tb.iz(); iz++) for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
			if (planner.tile(ix, iz).isEmpty()) continue;
			for (BuildingPiece b : planner.buildings(ix, iz)) {
				Box3 pb = b.box();
				if (pb.maxX() > ax - r && pb.x() < ax + r && pb.maxZ() > az - r && pb.z() < az + r) out.add(b);
			}
		}
		return out;
	}

	// ---- trees

	/**
	 * One forest hamlet (seed 1) with the pass on: (1) the natural world: no natural leaf, vine or tree log in a cell a building claims as
	 * air (how often the control has any depends on the seed: a log line only); (2) a late tree, injected: after generation natural leaves,
	 * vines and a log cluster with its crown are written into claimed air cells of rooms (what a neighbour chunk's tree decorated after the
	 * building's pass does), a screenshot, everything planted is put back (planted leaves decay while the world ticks), the pass runs on the
	 * chunks, and every one of them must be gone, a log with no leaf near included (TREEINORDER: no piece puts a log in claimed air), while a
	 * hedge leaf (persistent) put into claimed air and the logs the buildings place themselves (beams, trim) stay.
	 */
	void trees(ClientGameTestContext context) {
		String seed = System.getenv().getOrDefault("FB_TREEIN_SEED", "1");
		BuildingAirClear.ENABLED = true;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
			List<String> samples = new ArrayList<>();
			int[] n = new int[4];
			Spot[] spot = new Spot[1];
			List<long[]> planted = new ArrayList<>(); // x, y, z, kind (0 natural leaf, 1 vine, 2 tree log, 3 persistent leaf, 4 a log with no leaf near)
			List<Long> beams = new ArrayList<>(); // cells the ledger gives a building as a log (its beams, trim): the pass must leave them
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				WorldPlanners.FAILURES.set(0);
				List<Long> air = readTrees(level, planner, n, samples, beams);
				say("trees seed " + seed + " natural world, pass on: " + n[3] + " claimed building air cells read, natural leaves " + n[0] + ", vines " + n[1] + ", logs " + n[2] + "; pass calls " + BuildingAirClear.CALLS.get() + " " + BuildingAirClear.NANOS.get() / 1_000_000 + " ms, removed leaves " + BuildingAirClear.LEAVES.get() + " vines " + BuildingAirClear.VINES.get() + " logs " + BuildingAirClear.LOGS.get() + "; " + samples);
				if (n[0] + n[1] + n[2] != 0) problem("trees: " + (n[0] + n[1] + n[2]) + " natural leaves, vines or logs in the air buildings claim, with the pass on: " + samples);
				plant(level, air, planted);
				if (!planted.isEmpty()) spot[0] = new Spot((int) planted.get(0)[0], (int) planted.get(0)[1], (int) planted.get(0)[2], "planted leaf");
			});
			if (planted.size() < 20) problem("trees: only " + planted.size() + " cells planted: no building air to test with");
			int[] before = new int[1];
			world.getServer().runOnServer(server -> before[0] = countStanding(server.overworld(), planted));
			say("planted " + planted.size() + " (natural leaves, vines, a tree, a hedge leaf, a log with no leaf near); standing before the pass: " + before[0]);
			if (spot[0] != null) shoot(context, world, spot[0], "treein-before", 3);
			int[] after = new int[4];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				// TREEINORDER: the world ticks while the screenshot is taken, and a planted leaf (distance 7, no neighbour update) is a decaying
				// leaf: a random tick takes it. A lone leaf gone that way passed without the pass; a tree log whose leaf went looked like a log
				// with no tree. Put everything back as planted in this task, so the pass sees exactly what was planted.
				after[3] = replant(level, planted);
				say("planted cells changed while the screenshot was taken (leaf decay), put back: " + after[3]);
				java.util.TreeSet<Long> chunks = new java.util.TreeSet<>();
				for (long[] p : planted) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) chunks.add(ChunkPos.pack(((int) p[0] + dx * 12) >> 4, ((int) p[2] + dz * 12) >> 4));
				for (long c : chunks) BuildingAirClear.run(level, planner, ChunkPos.getX(c), ChunkPos.getZ(c));
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (long[] p : planted) {
					BlockState s = level.getBlockState(pos.set((int) p[0], (int) p[1], (int) p[2]));
					boolean gone = !(s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(BlockTags.LOGS));
					if (p[3] != 3) { if (!gone) { after[0]++; say("  still standing: kind " + p[3] + " at " + p[0] + "," + p[1] + "," + p[2] + " " + s); } }
					else if (gone) after[1]++; // a hedge leaf must stay
				}
				for (long k : beams) if (!level.getBlockState(pos.set(PieceLedger.x(k), PieceLedger.y(k), PieceLedger.z(k))).is(BlockTags.LOGS)) after[2]++; // the buildings' own logs stay
			});
			say("after the pass: natural foliage or foreign logs still standing " + after[0] + ", persistent leaf wrongly removed " + after[1] + ", building logs (" + beams.size() + ") removed " + after[2]);
			if (after[2] != 0) problem("trees: the pass removed " + after[2] + " of the buildings' own logs");
			if (beams.isEmpty()) say("trees: no building log in the settlement, the building-log control did not run");
			if (before[0] < 20) problem("trees: the control (planted, pass not run) found only " + before[0] + " standing: the test cannot fail");
			if (after[0] != 0) problem("trees: " + after[0] + " planted natural leaves, vines or tree logs still stand after the pass");
			if (after[1] != 0) problem("trees: the pass removed " + after[1] + " hedge leaves it must leave alone");
			if (spot[0] != null) shoot(context, world, spot[0], "treein-after", 3);
		} finally {
			BuildingAirClear.ENABLED = true;
		}
	}

	static int countStanding(ServerLevel level, List<long[]> planted) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (long[] p : planted) {
			BlockState s = level.getBlockState(pos.set((int) p[0], (int) p[1], (int) p[2]));
			if (s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(BlockTags.LOGS)) n++;
		}
		return n;
	}

	/** Puts every planted cell back as {@link #plant} wrote it; returns how many had changed (a planted leaf decays on a random tick). */
	static int replant(ServerLevel level, List<long[]> planted) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (long[] p : planted) {
			BlockState want = plantedState((int) p[3]);
			pos.set((int) p[0], (int) p[1], (int) p[2]);
			BlockState have = level.getBlockState(pos);
			if (have == want) continue;
			if (!have.is(want.getBlock())) changed++; // gone (a leaf decayed), not just a leaf whose distance was updated
			level.setBlock(pos, want, Block.UPDATE_CLIENTS);
		}
		return changed;
	}

	static BlockState plantedState(int kind) {
		return switch (kind) {
			case 0 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
			case 1 -> Blocks.VINE.defaultBlockState();
			case 3 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			default -> Blocks.OAK_LOG.defaultBlockState();
		};
	}

	/**
	 * Writes a late tree into claimed air of rooms: 24 natural leaves (a crown), 3 vines, one oak trunk cell touching leaves; and the two
	 * last two: a persistent leaf (must stay) and a log with no leaf near (must go: a fallen log). Cells are taken from the claimed air list in key order, spread out.
	 */
	static void plant(ServerLevel level, List<Long> air, List<long[]> planted) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
		Set<Long> free = new java.util.HashSet<>(air);
		int stride = Math.max(1, air.size() / 80), made = 0;
		for (int i = 0; i + 1 < air.size() && made < 28; i += stride) {
			long k = air.get(i);
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			// an air cell with an air cell beside it, both in the world as air (the crown is two blocks)
			long k2 = PieceLedger.pack(x + 1, y, z);
			if (!free.contains(k2) || !level.getBlockState(pos.set(x, y, z)).isAir() || !level.getBlockState(pos.set(x + 1, y, z)).isAir()) continue;
			int kind = made < 20 ? 0 : made < 23 ? 1 : made < 25 ? 2 : made < 26 ? 3 : 4;
			if (kind == 0) {
				level.setBlock(pos.set(x, y, z), leaf, Block.UPDATE_CLIENTS);
				planted.add(new long[] {x, y, z, 0});
			} else if (kind == 1) {
				level.setBlock(pos.set(x, y, z), Blocks.VINE.defaultBlockState(), Block.UPDATE_CLIENTS);
				planted.add(new long[] {x, y, z, 1});
			} else if (kind == 2) { // a trunk cell with a leaf beside it
				level.setBlock(pos.set(x, y, z), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(pos.set(x + 1, y, z), leaf, Block.UPDATE_CLIENTS);
				planted.add(new long[] {x, y, z, 2});
				planted.add(new long[] {x + 1, y, z, 0});
			} else if (kind == 3) {
				level.setBlock(pos.set(x, y, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
				planted.add(new long[] {x, y, z, 3});
			} else {
				level.setBlock(pos.set(x, y, z), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
				planted.add(new long[] {x, y, z, 4});
			}
			free.remove(k);
			free.remove(k2);
			made++;
		}
	}

	/** Loads the most forested settlement's chunks and counts foliage in the cells buildings claim as air; returns those cells (not furniture), in key order. */
	static List<Long> readTrees(ServerLevel level, WorldPlanner planner, int[] n, List<String> samples, List<Long> beams) {
		Settlement best = null;
		double bestF = -1;
		for (Settlement s : planner.roads().settlementsNear(0, 0, 2600)) {
			if (s.r() < 30 || s.r() > 120) continue;
			double f = forestShare(level, s.x(), s.z(), s.r());
			if (f * s.r() > bestF) { bestF = f * s.r(); best = s; }
		}
		if (best == null) throw new IllegalStateException("no settlement near the origin");
		say("trees settlement " + best.tier() + " at " + best.x() + "," + best.z() + " r " + best.r() + " forest*r " + String.format("%.1f", bestF));
		List<BuildingPiece> pieces = pieces(planner, best.x(), best.z(), best.r() + 16);
		int airId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<Long> out = new ArrayList<>();
		try (PieceLedger.Recording rec = PieceLedger.start()) {
			Set<ChunkPos> chunks = new LinkedHashSet<>();
			for (BuildingPiece b : pieces) {
				Box3 pb = b.box();
				for (int cx = Math.floorDiv(pb.x(), 16) - 1; cx <= Math.floorDiv(pb.maxX(), 16) + 1; cx++) for (int cz = Math.floorDiv(pb.z(), 16) - 1; cz <= Math.floorDiv(pb.maxZ(), 16) + 1; cz++) chunks.add(new ChunkPos(cx, cz));
			}
			for (ChunkPos p : chunks) level.getChunkSource().getChunk(p.x(), p.z(), ChunkStatus.FULL, true);
			Map<Long, Integer> hooks = rec.hooks();
			for (var e : rec.states().entrySet()) { // key order
				if (e.getValue() != airId) {
					long kb = e.getKey(); // a log our pieces placed (beam, trim) and the world still has: the pass must leave it
					if (Block.BLOCK_STATE_REGISTRY.byId(e.getValue()).is(BlockTags.LOGS) && level.getBlockState(pos.set(PieceLedger.x(kb), PieceLedger.y(kb), PieceLedger.z(kb))).is(BlockTags.LOGS)) beams.add(kb);
					continue;
				}
				long k = e.getKey();
				int x = PieceLedger.x(k), z = PieceLedger.z(k), y = PieceLedger.y(k);
				boolean inBox = false;
				for (BuildingPiece b : pieces) { Box3 pb = b.box(); if (x >= pb.x() && x < pb.maxX() && z >= pb.z() && z < pb.maxZ() && y >= pb.y() && y < pb.maxY()) { inBox = true; break; } }
				if (!inBox || hooks.containsKey(k)) continue;
				out.add(k);
				n[3]++;
				BlockState s = level.getBlockState(pos.set(x, y, z));
				String kind = null;
				if (s.is(BlockTags.LEAVES) && !s.getValueOrElse(LeavesBlock.PERSISTENT, false)) { n[0]++; kind = "leaf"; }
				else if (s.is(Blocks.VINE)) { n[1]++; kind = "vine"; }
				else if (s.is(BlockTags.LOGS)) { n[2]++; kind = "log"; }
				if (kind != null && samples.size() < 6) samples.add(kind + " " + x + "," + y + "," + z);
			}
		}
		return out;
	}

	// ---- screenshots

	/** A shot of the spot from a clear cell near it (the same spot in the control and the fixed world). */
	static void shoot(ClientGameTestContext context, TestSingleplayerContext world, Spot sp, String name, int dist) {
		context.runOnClient(client -> client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
		world.getServer().runCommand("gamemode spectator @p");
		double[] cam = new double[3];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			double[] best = null;
			double bestClear = -1;
			for (int a = 0; a < 16; a++) {
				double ang = a * Math.PI / 8;
				double cx = sp.x() + 0.5 + Math.cos(ang) * dist, cz = sp.z() + 0.5 + Math.sin(ang) * dist, cy = sp.y() + 3;
				double clear = 0;
				for (int t = 0; t <= 20; t++) {
					double f = t / 20.0;
					BlockPos p = BlockPos.containing(cx + (sp.x() + 0.5 - cx) * f, cy + (sp.y() + 1.5 - cy) * f, cz + (sp.z() + 0.5 - cz) * f);
					if (!level.getBlockState(p).isAir()) break;
					clear = f;
				}
				if (!level.getBlockState(BlockPos.containing(cx, cy, cz)).isAir()) clear = -1;
				if (clear > bestClear) { bestClear = clear; best = new double[] {cx, cy, cz}; }
			}
			System.arraycopy(best, 0, cam, 0, 3);
		});
		double dx = sp.x() + 0.5 - cam[0], dz = sp.z() + 0.5 - cam[2], dy = (sp.y() + 1.5) - cam[1];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		world.getServer().runCommand(String.format("tp @p %.1f %.1f %.1f %.1f %.1f", cam[0], cam[1], cam[2], yaw, pitch));
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
		context.takeScreenshot(name);
		say("screenshot " + name + " of " + sp.what() + " at " + sp.x() + "," + sp.y() + "," + sp.z() + " from " + (int) cam[0] + "," + (int) cam[1] + "," + (int) cam[2]);
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
}
