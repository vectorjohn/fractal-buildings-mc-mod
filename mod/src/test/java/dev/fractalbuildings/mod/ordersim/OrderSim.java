package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.mod.gameplay.Gameplay;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import dev.fractalbuildings.mod.worldgen.BuildingAirClear;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.SpillClear;
import dev.fractalbuildings.mod.worldgen.StreetAirFix;
import dev.fractalbuildings.mod.worldgen.TilePiece;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ORDERSIM S3: runs our real passes over a window in many chunk orders, with the real vanilla features beside them, and compares the cells our
 * pieces claim. One chunk's turn (the decoration model of the plan, "Decoration model"):
 * <ol>
 * <li>for each {@link Decoration} step in order: our pieces whose box meets the chunk at the structures' step (top layer modification;
 *     surface structures under the {@code early-step} mutant), then that step's feature calls of the chunk, each through a level that reads and
 *     writes only the 3 x 3 chunks round it;
 * <li>at the top layer, after its features, the passes the road feature runs ({@code SpillClear} where the chunk has no ground piece,
 *     {@code roadLeaves}, {@code BuildingAirClear}, {@code StreetAirFix}). A copy of those lines of {@code RoadFeature.place} until the
 *     {@code placeIn} seam exists (the road draw itself is not run);
 * <li>promotion of every chunk whose 8 neighbours have all decorated (post-process marks replayed). In 26.3 almost no block asks for it.
 * </ol>
 */
public final class OrderSim {
	private OrderSim() {}

	/** Result of {@link #runAll}: the runs (the first is the baseline) and the totals. */
	public static final class Result {
		public final Scenario scenario;
		public final List<SimRun> runs = new ArrayList<>();
		/** {@code WorldPlanners.FAILURES} raised during the runs: a pass that threw (C1). */
		public int failures;
		public long planMs, totalMs;

		Result(Scenario s) { scenario = s; }

		/** One line per failed check, prefixed with the order. */
		public List<String> failed() {
			List<String> out = new ArrayList<>();
			if (failures > 0) out.add("C1 WorldPlanners.FAILURES raised by " + failures);
			for (SimRun r : runs) for (String f : r.failed(scenario.spaced)) out.add(r.order + ": " + f);
			return out;
		}

		public boolean passed() { return failed().isEmpty(); }
	}

	/** Runs every order. The first is the baseline for C4 and the digest; audit (C5, C6) runs in the first two. Orders run on a small pool unless {@code parallel} is off (the mutant tests, since {@code Mutations.mode} is global). */
	public static Result runAll(Scenario s, List<Order> orders, boolean parallel) {
		long t0 = System.nanoTime();
		Result res = new Result(s);
		long p0 = System.nanoTime();
		s.plan();
		res.planMs = (System.nanoTime() - p0) / 1_000_000;
		boolean villagers = Gameplay.villagersEnabled();
		Gameplay.setVillagersForTests(false);
		int fail0 = WorldPlanners.FAILURES.get();
		try {
			SimRun base = run(s, orders.get(0), true);
			res.runs.add(base);
			List<SimRun> rest = new ArrayList<>();
			if (parallel && orders.size() > 2) {
				ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)));
				try {
					List<Future<SimRun>> fs = new ArrayList<>();
					for (int i = 1; i < orders.size(); i++) {
						Order o = orders.get(i);
						boolean audit = i < 2;
						fs.add(pool.submit(() -> { SimRun r = run(s, o, audit); SimCompare.crossOrder(s, base, r); r.world = audit ? r.world : null; return r; }));
					}
					for (Future<SimRun> f : fs) rest.add(f.get());
				} catch (InterruptedException | java.util.concurrent.ExecutionException e) {
					throw new IllegalStateException(e);
				} finally {
					pool.shutdownNow();
				}
			} else {
				for (int i = 1; i < orders.size(); i++) {
					SimRun r = run(s, orders.get(i), i < 2);
					SimCompare.crossOrder(s, base, r);
					rest.add(r);
				}
			}
			res.runs.addAll(rest);
		} finally {
			Gameplay.setVillagersForTests(villagers);
		}
		res.failures = WorldPlanners.FAILURES.get() - fail0;
		for (SimRun r : res.runs) r.world = null;
		res.totalMs = (System.nanoTime() - t0) / 1_000_000;
		return res;
	}

	/** One order. {@code audit} also logs every write with the code that made it, checks the write contracts (C5) and the second sweep (C6). */
	public static SimRun run(Scenario s, Order order, boolean audit) { return run(s, order, audit, null); }

	/** As above; {@code tamper} (a test hook) changes the finished world before the checks read it. */
	public static SimRun run(Scenario s, Order order, boolean audit, java.util.function.Consumer<SimWorld> tamper) {
		long t0 = System.nanoTime();
		Scenario.Plan plan = s.plan();
		SimRun run = new SimRun(s.id, order.name());
		SimWorld world = new SimWorld(s.seed, s.simX0(), s.simZ0(), s.simN(), s.simN(), s.terrain, s.biome);
		world.logWrites = audit;
		world.audit = audit;
		boolean[] done = new boolean[s.decN() * s.decN()];
		boolean[] promoted = new boolean[done.length];
		long orderHash = Hash64.of(order.name());
		if (structureStep() != Decoration.TOP_LAYER_MODIFICATION) run.errors.add("our structures are placed in step " + structureStep() + ", not TOP_LAYER_MODIFICATION (after the vegetation)"); // the order test's stepOk
		try (PieceLedger.Recording rec = PieceLedger.startLocal()) {
			for (int[] c : order.chunks()) {
				try {
					decorate(s, plan, world, c[0], c[1], orderHash, run);
				} catch (RuntimeException e) {
					run.errors.add("chunk " + c[0] + "," + c[1] + ": " + e);
				}
				done[idx(s, c[0], c[1])] = true;
				promoteReady(s, world, done, promoted, c[0], c[1], run);
			}
			if (tamper != null) tamper.accept(world);
			run.oursZoneViolations = (int) world.violations().stream().filter(SimWorld.ZoneViolation::ours).count();
			run.featureZoneViolations = world.violations().size() - run.oursZoneViolations;
			SimCompare.claimed(s, world, rec, run);
		}
		if (audit) {
			SimCompare.contracts(s, world, run);
			idempotence(s, plan, world, run, orderHash);
		}
		run.world = world;
		run.ms = (System.nanoTime() - t0) / 1_000_000;
		return run;
	}

	private static int idx(Scenario s, int cx, int cz) { return (cz - s.decZ0()) * s.decN() + (cx - s.decX0()); }

	private static boolean inDec(Scenario s, int cx, int cz) {
		return cx >= s.decX0() && cx < s.decX0() + s.decN() && cz >= s.decZ0() && cz < s.decZ0() + s.decN();
	}

	/** Promotes every chunk round (cx, cz) whose 9 chunks have all decorated: the only legal schedule (a chunk reaches FULL after its neighbours' features). */
	private static void promoteReady(Scenario s, SimWorld world, boolean[] done, boolean[] promoted, int cx, int cz, SimRun run) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int px = cx + dx, pz = cz + dz;
				if (!inDec(s, px, pz) || promoted[idx(s, px, pz)]) continue;
				boolean ready = true;
				for (int ez = -1; ez <= 1 && ready; ez++) for (int ex = -1; ex <= 1 && ready; ex++) ready = inDec(s, px + ex, pz + ez) && done[idx(s, px + ex, pz + ez)];
				if (!ready) continue;
				promoted[idx(s, px, pz)] = true;
				run.promoted += world.promote(px, pz);
			}
		}
	}

	/** The step our structures are placed in: asked of the real structure class with the settings of its JSON (top layer modification), so the early-step mutant moves it. */
	static Decoration structureStep() {
		return new dev.fractalbuildings.mod.worldgen.DistrictStructure(new net.minecraft.world.level.levelgen.structure.Structure.StructureSettings(net.minecraft.core.HolderSet.direct(), java.util.Map.of(), Decoration.TOP_LAYER_MODIFICATION,
			net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE), dev.fractalbuildings.core.settlement.Tier.HAMLET).step();
	}

	private static void decorate(Scenario s, Scenario.Plan plan, SimWorld world, int cx, int cz, long orderHash, SimRun run) {
		long key = ChunkPos.pack(cx, cz);
		List<SimFeatures.Call> calls = plan.calls.getOrDefault(key, List.of());
		Decoration structStep = structureStep();
		for (Decoration step : Decoration.values()) {
			if (step == structStep) pieces(s, plan, world, cx, cz, orderHash);
			for (SimFeatures.Call c : calls) if (c.step() == step) SimFeatures.place(world, s.planner, c, run.features);
			if (step == Decoration.TOP_LAYER_MODIFICATION) {
				for (Scenario.Extra e : s.extras) e.run(world, cx, cz);
				roadStep(s, world, cx, cz);
			}
		}
	}

	private static void pieces(Scenario s, Scenario.Plan plan, SimWorld world, int cx, int cz, long orderHash) {
		List<TilePiece> ps = plan.pieces.get(ChunkPos.pack(cx, cz));
		if (ps == null) return;
		SimLevel level = new SimLevel(world, cx, cz, "ours:piece:" + cx + "," + cz, s.seed);
		BoundingBox chunkBB = new BoundingBox(cx * 16, SimWorld.MIN_Y, cz * 16, cx * 16 + 15, SimWorld.MIN_Y + SimWorld.HEIGHT - 1, cz * 16 + 15);
		RandomSource random = RandomSource.create(Hash64.hash(orderHash, "ordersim-piece", cx, cz)); // only the RANDOM mutant reads it
		for (TilePiece p : ps) TilePiece.write(level.proxy(), s.planner, p, chunkBB, new ChunkPos(cx, cz), random);
	}

	/** What {@code RoadFeature.place} runs before the road draw. */
	private static void roadStep(Scenario s, SimWorld world, int cx, int cz) {
		SimLevel level = new SimLevel(world, cx, cz, "ours:road:" + cx + "," + cz, s.seed);
		var lv = level.proxy();
		try {
			if (s.planner.districtsNear(cx, cz) && !SpillClear.hasGround(s.planner, cx, cz)) SpillClear.run(lv, s.planner, cx, cz);
			if (s.planner.roadsEnabled()) SpillClear.roadLeaves(lv, s.planner, cx, cz);
			BuildingAirClear.run(lv, s.planner, cx, cz);
			StreetAirFix.run(lv, s.planner, cx, cz);
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
		}
	}

	/**
	 * C6: sweeps our top-layer step (pieces, then the road step) over every decorated chunk in order, twice. The first sweep may repair what
	 * later neighbours spilled; the second must change no claimed cell.
	 */
	private static void idempotence(Scenario s, Scenario.Plan plan, SimWorld world, SimRun run, long orderHash) {
		world.logWrites = false;
		world.audit = false;
		world.trackWriters = false; // keep the provenance of the order itself
		try (PieceLedger.Recording ignored = PieceLedger.startLocal()) {
			sweep(s, plan, world, orderHash);
			int[] first = snapshot(plan, world);
			sweep(s, plan, world, orderHash);
			int[] second = snapshot(plan, world);
			int changed = 0;
			for (int i = 0; i < first.length; i++) if (first[i] != second[i]) changed++;
			run.idempotenceChanges = changed;
		} catch (RuntimeException e) {
			run.errors.add("idempotence sweep: " + e);
		}
	}

	private static void sweep(Scenario s, Scenario.Plan plan, SimWorld world, long orderHash) {
		for (int cz = s.decZ0(); cz < s.decZ0() + s.decN(); cz++) {
			for (int cx = s.decX0(); cx < s.decX0() + s.decN(); cx++) {
				pieces(s, plan, world, cx, cz, orderHash);
				roadStep(s, world, cx, cz);
			}
		}
	}

	private static int[] snapshot(Scenario.Plan plan, SimWorld world) {
		int[] out = new int[plan.innerKeys.length];
		for (int i = 0; i < out.length; i++) {
			long k = plan.innerKeys[i];
			out[i] = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(world.get(PieceLedger.x(k), PieceLedger.y(k), PieceLedger.z(k)));
		}
		return out;
	}
}
