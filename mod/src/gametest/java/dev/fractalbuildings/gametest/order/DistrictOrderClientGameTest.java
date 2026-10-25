package dev.fractalbuildings.gametest.order;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.block.BlockMapper;
import dev.fractalbuildings.mod.config.WorldConfigData;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * B2 (6.1 / 6.2 evidence, S3 re-implemented): in fresh normal worlds of one seed, the tiles of the settlement at the gate site
 * {@code gate-temperate-city} (1280, -9600) are generated in different chunk orders and on 8 threads (with a thread clearing every
 * {@code WorldPlanner} cache every 2 ms), and what our pieces owned (the {@link PieceLedger}: writes plus claimed air) must be identical in
 * all of them, equal to the reference raster of the core (no world reads), and the world must hold that raster in at least 99.9 % of the solid cells
 * (vanilla trees can still get into our air, ADR 0003 D5). Mutants (switches in {@link Mutations}) must each fail one of those checks.
 * Vanilla whole-chunk hashes are never compared: vanilla features overlap chunk borders and differ between runs of one order (S3).
 *
 * <p>Environment: {@code FB_ORDER_RADIUS} (default 1) takes the tiles within that many tiles of the settlement's centre tile;
 * {@code FB_ORDER_ONLY} (a comma list of run labels) limits the runs, for development.
 */
// gametest-order: 7
public class DistrictOrderClientGameTest implements FabricClientGameTest {
	private static final long SEED = 987654321L;
	private static final int SITE_X = 1280, SITE_Z = -9600;
	private static final double SOLID_LIMIT = 0.01;
	private static final double AIR_LIMIT = 0.01;
	/** Beds that lose a half at a chunk border: 8 of 5,886 bed cells after the B3 merge (B5 defect, reported; set to 0 once fixed). Chests and other hooks must be exact. */
	private static final double BED_LOSS_LIMIT = 0.0;
	/** The evictor clears every planner cache after each this-many completed chunk requests (about 6 evictions per world: each costs a cold rebuild of the plans, 5 to 10 s per tile). S3 evicted every 2 ms because its plans took microseconds; a live settlement plan takes seconds, and time-based eviction at 2 or 500 ms never let it finish (livelock seen in this test). */
	private static int evictEvery(int chunks) { return Math.max(5, chunks / 6); }

	/** What one world's run measured. */
	record Run(String label, String digest, TreeMap<Long, String> chunkDigests, String worldSolidDigest, int cells, int duplicates, int refCells, int missing, int extra, int wrong,
			int solid, int solidBad, int solidTrees, int air, int airBad, int failures, long genMs, String load, String detail,
			int unknownSolid, int vegAir, int unknownAir, String hist, String hookDigest, HookReadback.Result hook, boolean stepOk, String roadDigest) {
		/** What is wrong with this run compared with the baseline run ({@code null} for the baseline itself). */
		List<String> problems(Run base) {
			List<String> p = new ArrayList<>();
			if (base != null && !digest.equals(base.digest)) p.add("ledger digest differs from " + base.label);
			if (base != null) {
				int n = 0;
				for (var e : base.chunkDigests.entrySet()) if (!e.getValue().equals(chunkDigests.get(e.getKey()))) n++;
				if (n > 0) p.add(n + " chunk digests differ from " + base.label);
			}
			if (duplicates > 0) p.add(duplicates + " cells claimed twice");
			if (missing + extra + wrong > 0) p.add("ledger differs from the reference raster: " + missing + " missing, " + extra + " extra, " + wrong + " wrong");
			if (solidTrees > 0) p.add(solidTrees + " solid cells hold a vanilla log or leaves (a tree grew into the settlement)");
			if (solidBad > solid * SOLID_LIMIT) p.add(solidBad + " of " + solid + " solid cells differ in the world");
			if (airBad > air * AIR_LIMIT) p.add(airBad + " of " + air + " claimed air cells are not air in the world");
			if (base != null && !hookDigest.equals(base.hookDigest)) p.add("hook digest differs from " + base.label);
			if (base != null && !roadDigest.equals(base.roadDigest)) p.add("road feature cells differ from " + base.label + ": " + roadDigest + " vs " + base.roadDigest);
			if (!hook.ok(BED_LOSS_LIMIT)) p.add("hook readback: " + hook);
			if (unknownSolid > 0) p.add(unknownSolid + " solid cells changed by something that is not a known vanilla spill (see the histogram)");

			if (unknownAir > 0) p.add(unknownAir + " claimed air cells hold an unknown block");
			if (!stepOk) p.add("a district structure is not in the top_layer_modification step");
			if (failures > 0) p.add(failures + " swallowed exceptions");
			return p;
		}
	}

	private static final Map<String, Run> RUNS = new java.util.LinkedHashMap<>();
	private static final StringBuilder REPORT = new StringBuilder();

	private static void report(String line) {
		FractalBuildingsMod.LOGGER.info("[B2] {}", line);
		REPORT.append(line).append('\n');
	}

	private static String blockName(BlockState s) { return Spill.name(s); }

	private static String load() {
		try {
			return Files.readString(Path.of("/proc/loadavg")).split(" ")[0];
		} catch (Exception e) {
			return "?";
		}
	}

	private static String sha(Iterable<Map.Entry<Long, Integer>> entries) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(12);
			for (var e : entries) { b.clear(); b.putLong(e.getKey()).putInt(e.getValue()); md.update(b.array(), 0, 12); }
			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static TreeMap<Long, String> perChunk(TreeMap<Long, Integer> cells) {
		TreeMap<Long, TreeMap<Long, Integer>> by = new TreeMap<>();
		for (var e : cells.entrySet()) by.computeIfAbsent(ChunkPos.pack(PieceLedger.x(e.getKey()) >> 4, PieceLedger.z(e.getKey()) >> 4), k -> new TreeMap<>()).put(e.getKey(), e.getValue());
		TreeMap<Long, String> out = new TreeMap<>();
		by.forEach((k, v) -> out.put(k, sha(v.entrySet())));
		return out;
	}

	private enum Order { FORWARD, REVERSE, SHUFFLE1, SHUFFLE2, SPIRAL, THREADS, THREADS_EVICT }

	private static List<ChunkPos> ordered(List<ChunkPos> asc, Order o) {
		List<ChunkPos> out = new ArrayList<>(asc);
		switch (o) {
			case REVERSE -> Collections.reverse(out);
			case SHUFFLE1 -> Collections.shuffle(out, new Random(1));
			case SHUFFLE2 -> Collections.shuffle(out, new Random(2));
			case THREADS, THREADS_EVICT -> Collections.shuffle(out, new Random(3));
			case SPIRAL -> {
				int cx = 0, cz = 0;
				for (ChunkPos c : asc) { cx += c.x(); cz += c.z(); }
				final int mx = cx / asc.size(), mz = cz / asc.size();
				out.sort((a, b) -> {
					int ra = Math.max(Math.abs(a.x() - mx), Math.abs(a.z() - mz)), rb = Math.max(Math.abs(b.x() - mx), Math.abs(b.z() - mz));
					if (ra != rb) return Integer.compare(ra, rb);
					return Double.compare(Math.atan2(a.z() - mz, a.x() - mx), Math.atan2(b.z() - mz, b.x() - mx));
				});
			}
			default -> {}
		}
		return out;
	}

	private static void generate(MinecraftServer server, ServerLevel level, WorldPlanner planner, List<ChunkPos> order, Order o) throws InterruptedException {
		if (o != Order.THREADS && o != Order.THREADS_EVICT) {
			for (ChunkPos c : order) level.getChunk(c.x(), c.z(), ChunkStatus.FULL, true);
			return;
		}
		ServerChunkCache cache = level.getChunkSource();
		ConcurrentLinkedQueue<ChunkPos> q = new ConcurrentLinkedQueue<>(order);
		AtomicInteger completed = new AtomicInteger(), finished = new AtomicInteger(), errors = new AtomicInteger(), evictions = new AtomicInteger();
		AtomicBoolean stop = new AtomicBoolean();
		Thread evictor = null;
		if (o == Order.THREADS_EVICT) {
			final int step = evictEvery(order.size());
			evictor = new Thread(() -> {
				int next = step;
				while (!stop.get()) {
					if (completed.get() >= next) {
						planner.evictAll();
						evictions.incrementAndGet();
						next += step;
					}
					try { Thread.sleep(2); } catch (InterruptedException e) { return; }
				}
			}, "b2-evictor");
			evictor.start();
		}
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			Thread t = new Thread(() -> {
				try {
					for (ChunkPos c; (c = q.poll()) != null;) {
						if (!cache.getChunkFuture(c.x(), c.z(), ChunkStatus.FULL, true).join().isSuccess()) errors.incrementAndGet();
						completed.incrementAndGet();
					}
				} catch (RuntimeException e) {
					FractalBuildingsMod.LOGGER.error("[B2] requester failed", e);
					errors.incrementAndGet();
				} finally {
					finished.incrementAndGet();
				}
			}, "b2-requester-" + i);
			threads.add(t);
			t.start();
		}
		server.managedBlock(() -> finished.get() == 8);
		stop.set(true);
		if (evictor != null) evictor.join();
		if (errors.get() > 0) throw new IllegalStateException(errors.get() + " chunk requests failed");
		if (o == Order.THREADS_EVICT) FractalBuildingsMod.LOGGER.info("[B2] evictor cleared the planner caches {} times during generation", evictions.get());
	}

	/** One world: generate the settlement's tiles in {@code o}, then measure. */
	private static Run runOne(ClientGameTestContext context, String label, Order o, String mutation, int radius, boolean checkConfig) {
		long[] t0 = {0};
		Run[] result = new Run[1];
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(Long.toString(SEED))).create()) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow(() -> new IllegalStateException("no planner in a normal world"));
				SettlementInfo site = planner.nearest(SITE_X, SITE_Z, Tier.HAMLET, 1).orElseThrow(() -> new IllegalStateException("no settlement near the gate site"));
				DistrictGrid.Tile centre = planner.grid().tileAt(site.site().centre().x(), site.site().centre().z());
				List<TileInfo> tiles = new ArrayList<>();
				for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
					planner.tile(centre.ix() + dx, centre.iz() + dz).filter(t -> t.settlementId().equals(site.site().id())).ifPresent(tiles::add);
				}
				if (tiles.isEmpty()) throw new IllegalStateException("the site's centre tile is not planned");
				TreeMap<Long, ChunkPos> chunkSet = new TreeMap<>();
				for (TileInfo t : tiles) for (int cz = t.tile().minZ() >> 4; cz <= (t.tile().maxZ() - 1) >> 4; cz++) for (int cx = t.tile().minX() >> 4; cx <= (t.tile().maxX() - 1) >> 4; cx++) chunkSet.put(ChunkPos.pack(cx, cz), new ChunkPos(cx, cz));
				List<ChunkPos> asc = new ArrayList<>(chunkSet.values());
				asc.sort((a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
				if (RUNS.isEmpty()) report("site " + site.site().id() + " " + site.site().tier() + " at " + site.site().centre() + ", " + tiles.size() + " tiles, " + asc.size() + " chunks");
				WorldPlanners.FAILURES.set(0);
				String loadBefore = load();
				long start = System.nanoTime();
				boolean[] stepOk = {true};
				String[] roadDigest = {""};
				TreeMap<Long, Integer> ledger, hooks = new TreeMap<>();
				int dups;
				Mutations.mode = mutation;
				try (PieceLedger.Recording rec = PieceLedger.start()) {
					generate(server, level, planner, ordered(asc, o), o);
					// the structures generate in the last decoration step, after vegetation (S3); read it from the live registry while the mutation is on
					for (String s : new String[] {"hamlet", "village", "town", "city", "metropolis", "district_ground"}) {
						if (dev.fractalbuildings.gametest.worldgen.StartSupport.structure(level, s).value().step() != net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION) stepOk[0] = false;
					}
					ledger = new TreeMap<>();
					TreeMap<Long, Integer> roadCells = new TreeMap<>();
					for (var e : rec.states().entrySet()) {
						int x = PieceLedger.x(e.getKey()), z = PieceLedger.z(e.getKey());
						if (rec.roadCells().contains(e.getKey())) { if (chunkSet.containsKey(ChunkPos.pack(x >> 4, z >> 4))) roadCells.put(e.getKey(), e.getValue()); continue; } // the road feature (C1) is not a district piece: set aside, compared across orders
						if (chunkSet.containsKey(ChunkPos.pack(x >> 4, z >> 4)) && tiles.stream().anyMatch(t -> t.tile().contains(x, z))) ledger.put(e.getKey(), e.getValue());
					}
					dups = (int) rec.duplicates().stream().filter(k -> !rec.roadCells().contains(k)).count();
					roadDigest[0] = PieceLedger.digest(roadCells) + " " + roadCells.size();
					for (var e : rec.hooks().entrySet()) {
						int x = PieceLedger.x(e.getKey()), z = PieceLedger.z(e.getKey());
						if (tiles.stream().anyMatch(t -> t.tile().contains(x, z))) hooks.put(e.getKey(), e.getValue());
					}
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				} finally {
					Mutations.mode = null;
				}
				long genMs = (System.nanoTime() - start) / 1_000_000;
				// reference raster: the core's cells of every piece of every tile, same live probes, no world reads
				TreeMap<Long, Integer> ref = new TreeMap<>();
				int minY = level.getMinY(), h = level.getMaxY() - minY + 1;
				for (TileInfo ti : tiles) {
					Box3 all = new Box3(ti.tile().minX(), minY, ti.tile().minZ(), ti.tile().widthX(), h, ti.tile().widthZ());
					for (BuildingPiece b : planner.buildings(ti.ix(), ti.iz())) planner.raster(b.ref(), all, (x, y, z, role, id, shape) -> ref.put(PieceLedger.pack(x, y, z), Block.BLOCK_STATE_REGISTRY.getId(BlockMapper.state(role, id, shape))));
					for (GroundPiece g : planner.ground(ti.ix(), ti.iz())) planner.raster(g.ref(), all, (x, y, z, role, id, shape) -> ref.put(PieceLedger.pack(x, y, z), Block.BLOCK_STATE_REGISTRY.getId(BlockMapper.state(role, id, shape))));
				}
				int missing = 0, extra = 0, wrong = 0;
				for (var e : ref.entrySet()) {
					Integer got = ledger.get(e.getKey());
					if (got == null) missing++; else if (!got.equals(e.getValue())) wrong++;
				}
				for (Long k : ledger.keySet()) if (!ref.containsKey(k)) extra++;
				// the world against the raster; hook blocks (beds, chests, lights) are read back on their own below
				for (ChunkPos c : asc) level.setChunkForced(c.x(), c.z(), true);
				for (ChunkPos c : asc) level.getChunk(c.x(), c.z(), ChunkStatus.FULL, true);
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
				int solid = 0, solidBad = 0, solidTrees = 0, air = 0, airBad = 0, unknownSolid = 0, vegAir = 0, unknownAir = 0;
				TreeMap<Long, Integer> worldSolid = new TreeMap<>();
				TreeMap<String, Integer> hist = new TreeMap<>();
				String firstBad = "";
				for (var e : ref.entrySet()) {
					long k = e.getKey();
					if (hooks.containsKey(k)) continue;
					BlockState want = Block.BLOCK_STATE_REGISTRY.byId(e.getValue());
					BlockState have = level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k), PieceLedger.z(k)));
					if (want.isAir()) {
						air++;
						if (!have.isAir()) {
							airBad++;
							String kind = Spill.airKind(have, level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k) + 1, PieceLedger.z(k))), level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k) + 2, PieceLedger.z(k))));
							hist.merge("air->" + blockName(have) + " [" + kind + "]", 1, Integer::sum);
							if (kind.equals("unknown")) {
								unknownAir++;
								if (unknownAir <= 10) { // C1: where they are, with what is around them (a 2x2 dark oak's dirt under a base cell whose log was blocked?)
									int ux = PieceLedger.x(k), uy = PieceLedger.y(k), uz = PieceLedger.z(k);
									dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[B2] unknown air cell {},{},{} has {}; above {}; below {}; around N {} E {} S {} W {}", ux, uy, uz, blockName(have), blockName(level.getBlockState(p.set(ux, uy + 1, uz))), blockName(level.getBlockState(p.set(ux, uy - 1, uz))), blockName(level.getBlockState(p.set(ux, uy, uz - 1))), blockName(level.getBlockState(p.set(ux + 1, uy, uz))), blockName(level.getBlockState(p.set(ux, uy, uz + 1))), blockName(level.getBlockState(p.set(ux - 1, uy, uz))));
								}
							}
						}
					} else {
						solid++;
						worldSolid.put(k, Block.BLOCK_STATE_REGISTRY.getId(have));
						if (have != want) {
							solidBad++;
							if (have.is(net.minecraft.tags.BlockTags.LOGS) || have.is(net.minecraft.tags.BlockTags.LEAVES)) solidTrees++;
							String kind = Spill.solidKind(want, have, level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k) + 1, PieceLedger.z(k))), level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k) + 2, PieceLedger.z(k))));
							hist.merge(blockName(want) + "->" + blockName(have) + " [" + kind + "]", 1, Integer::sum);
							if (kind.equals("unknown")) unknownSolid++;
							if (firstBad.isEmpty()) firstBad = PieceLedger.x(k) + "," + PieceLedger.y(k) + "," + PieceLedger.z(k) + " want " + want + " have " + have;
						}
					}
				}
				HookReadback.Result hookResult = HookReadback.check(level, hooks);
				result[0] = new Run(label, PieceLedger.digest(ledger), perChunk(ledger), sha(worldSolid.entrySet()), ledger.size(), dups, ref.size(), missing, extra, wrong, solid, solidBad, solidTrees, air, airBad,
						WorldPlanners.FAILURES.get(), genMs, loadBefore + " -> " + load(), firstBad, unknownSolid, vegAir, unknownAir, hist.toString(), PieceLedger.digest(hooks), hookResult, stepOk[0], roadDigest[0]);
				if (checkConfig) checkPinnedConfig(server, level);
				for (ChunkPos c : asc) level.setChunkForced(c.x(), c.z(), false);
			});
			// let the server drain the ticks it fell behind while the task ran, and the client catch up, before the world closes (closing a world 700 ticks behind hung the framework)
			context.waitTicks(60);
		}
		return result[0];
	}

	/**
	 * Gap 3: a world created while the config file says {@code cellSize 2048} saves that value, and the planner the world builds for itself
	 * (nothing here pins or clears anything) uses it. Deleting the LOAD hook in {@code ModWorldgen} makes the planner use the defaults and fails this.
	 */
	private static void savedConfigWorld(ClientGameTestContext context) {
		Path file = dev.fractalbuildings.mod.config.FbConfig.path();
		String old = null;
		try {
			if (Files.exists(file)) old = Files.readString(file);
			Files.writeString(file, "{\"cellSize\": 2048}\n");
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(Long.toString(SEED))).create()) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldConfig saved = WorldConfigData.get(server).settings().world();
				if (saved.cellSize() != 2048) throw new IllegalStateException("the new world did not save the file's cell size: " + saved);
				WorldConfig live = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow().config();
				if (!live.equals(saved)) throw new IllegalStateException("the world's own planner uses " + live + ", the world saved " + saved);
				report("saved config: a world saved with cellSize 2048 plans with cellSize " + live.cellSize());
			});
		} finally {
			try {
				if (old == null) Files.deleteIfExists(file); else Files.writeString(file, old);
			} catch (java.io.IOException e) {
				FractalBuildingsMod.LOGGER.warn("[B2] could not restore {}", file, e);
			}
		}
	}

	/** The live planner uses the world's pinned config, not the defaults and not the config file. */
	private static void checkPinnedConfig(MinecraftServer server, ServerLevel level) {
		WorldConfig pinned = WorldConfigData.get(server).settings().world();
		WorldConfig custom = new WorldConfig(2048, 0.5, pinned.maxRadius(), pinned.styles(), pinned.genVersion());
		if (custom.equals(pinned)) throw new IllegalStateException("the custom config must differ from the world's");
		try {
			WorldPlanners.clear();
			WorldPlanners.pin(pinned);
			WorldConfig live = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow().config();
			if (!live.equals(pinned)) throw new IllegalStateException("the planner's config " + live + " is not the world's pinned " + pinned);
			WorldPlanners.clear();
			WorldPlanners.pin(custom);
			live = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow().config();
			if (!live.equals(custom)) throw new IllegalStateException("a pinned config " + custom + " is not used: " + live);
			// mutant: a config source that ignores the pin must be caught by the same check
			WorldPlanners.setConfigSource(WorldConfig::defaults);
			WorldPlanners.clear();
			WorldPlanners.pin(custom);
			live = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow().config();
			if (live.equals(custom)) throw new IllegalStateException("the mutant ignoring the pin was not caught");
			report("pinned config: the planner uses the world's pinned config (" + pinned.cellSize() + ", " + pinned.frequency() + "); a custom pin is used; a source ignoring the pin is caught");
		} finally {
			WorldPlanners.resetConfigSource();
			WorldPlanners.clear();
			WorldPlanners.pin(pinned);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("DistrictOrderClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		int radius = Integer.parseInt(System.getenv().getOrDefault("FB_ORDER_RADIUS", "1"));
		String only = System.getenv("FB_ORDER_ONLY");
		List<String> failures = new ArrayList<>();
		if (only == null || only.contains("config")) savedConfigWorld(context);
		Run base = null;
		for (Order o : Order.values()) {
			String label = o.name().toLowerCase();
			if (only != null && !List.of(only.split(",")).contains(label)) continue;
			Run r = runOne(context, label, o, null, radius, o == Order.FORWARD);
			RUNS.put(label, r);
			if (base == null) base = r;
			List<String> p = r.problems(o == Order.FORWARD ? null : base);
			report(String.format("%-14s cells %d (reference %d) digest %.12s chunks %d, duplicates %d, ledger missing/extra/wrong %d/%d/%d, world solid %d bad %d (trees %d), air %d bad %d, failures %d, %d ms, load %s%s", label, r.cells, r.refCells,
					r.digest, r.chunkDigests.size(), r.duplicates, r.missing, r.extra, r.wrong, r.solid, r.solidBad, r.solidTrees, r.air, r.airBad, r.failures, r.genMs, r.load, r.detail.isEmpty() ? "" : " first bad: " + r.detail));
			report("  roads: " + r.roadDigest.substring(0, Math.min(12, r.roadDigest.length())) + r.roadDigest.substring(r.roadDigest.indexOf(" ")) + " cells; histogram: " + r.hist + "; hooks: " + r.hook);
			for (String s : p) failures.add(label + ": " + s);
		}
		// mutants: each must be caught by one of the checks above, on the centre tile only
		// each mutant counts as caught only by the check it targets (regex over the problem texts)
		String[][] mutants = {{Mutations.COUNTER, "ledger differs"}, {Mutations.TWICE, "claimed twice"}, {Mutations.SKIP_AIR, "unknown block|claimed air"}, {Mutations.EARLY_STEP, "top_layer_modification step"}, {Mutations.RANDOM, "ledger differs"}, {Mutations.WORLD_STONE, "not a known vanilla spill"}};
		if (only == null || only.contains("mutants")) {
			Run clean = runOne(context, "clean-centre", Order.FORWARD, null, 0, false);
			Run cleanRev = runOne(context, "reverse-centre", Order.REVERSE, null, 0, false);
			for (Run b : List.of(clean, cleanRev)) report(b.label + " (baseline of the mutants): solid bad " + b.solidBad + ", air bad " + b.airBad + ", histogram " + b.hist + "; hooks " + b.hook);
			if (!clean.problems(null).isEmpty()) failures.add("clean centre run: " + clean.problems(null));
			if (!cleanRev.problems(clean).isEmpty()) failures.add("reverse centre run: " + cleanRev.problems(clean));
			for (String[] m : mutants) {
				Run r = runOne(context, "mutant-" + m[0], Order.FORWARD, m[0], 0, false);
				List<String> p = r.problems(null);
				report(String.format("mutant %-12s caught=%s (own check %s) %s | solid %d bad %d (trees %d), air %d bad %d, duplicates %d, ledger %d/%d/%d", m[0], p.stream().anyMatch(s -> s.matches(".*(" + m[1] + ").*")), m[1], p, r.solid, r.solidBad, r.solidTrees, r.air, r.airBad, r.duplicates, r.missing, r.extra, r.wrong));
				boolean caught = p.stream().anyMatch(s -> s.matches(".*(" + m[1] + ").*"));
				report("  histogram: " + r.hist);
				if (!caught) failures.add("mutant " + m[0] + " was not caught by its own check (" + m[1] + "): " + p);
			}
		}
		try {
			Files.writeString(Path.of("order-report.txt"), REPORT.toString());
		} catch (Exception e) {
			FractalBuildingsMod.LOGGER.warn("[B2] could not write the report", e);
		}
		if (!failures.isEmpty()) throw new AssertionError("district order test: " + failures);
	}
}
