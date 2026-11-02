package dev.fractalbuildings.gametest.perf;

import static dev.fractalbuildings.gametest.perf.PerfSupport.load;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.LaneStats;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * PORTALCOLD (6.30): how long does the overworld server thread stall when a player's first nether portal exits inside a cold settlement?
 * One dedicated server per case ({@code -Dfb.portal.case=town|metro|wild}, seed 987654321, fresh world, {@code scripts/portalcold.sh}). A
 * creative mock player is moved to the nether at the overworld exit / 8, a lit portal is built around it and {@code setAsInsidePortal} is
 * called every tick (what {@code NetherPortalBlock.entityInside} does); vanilla's own {@code handlePortal} then runs
 * {@code getPortalDestination} (find or create the exit portal) and the dimension change. Records the longest server tick, the ticks and wall
 * time from trigger to arrival, where the exit portal came out relative to the planned buildings, and a block dump around it.
 * Writes {@code portal-<case>.json} and {@code portal-<case>.txt}. Inert unless installed by the mod's {@code -Dfb.testHook}.
 */
public final class PortalColdHarness {
	private final String kase = System.getProperty("fb.portal.case", "town");
	/** Overworld exit target (x, z). town and metro are the perf world's gate city and metropolis centre; wild is an unplanned-looking spot (the tile is logged). */
	private final int ex, ez;
	private final Path out = Path.of(System.getProperty("fb.portal.out", "build/portalcold"));
	private final TreeMap<String, Object> rows = new TreeMap<>();
	private final StringBuilder dump = new StringBuilder();
	private ServerPlayer player;
	private ServerLevel nether;
	private BlockPos portalPos;
	private volatile int phase;
	private int wait, ticks;
	private volatile long tickStart;
	private long t0, trigTickNo, serverTick;
	private final List<Double> tickMs = new ArrayList<>();
	private double maxTickBefore;

	/** Case suffix -held: the trip goes through PortalHold first (6.30 option c), as the mixin does for a ticked entity. */
	private final boolean held = kase.endsWith("-held");
	/** Hold phase: first gate call (wall), ticks, the longest tick (incl. our gate call), the gate call times. */
	private long holdT0;
	private int holdTicks;
	private double holdMaxTick, holdMaxGate, firstGateMs;

	private PortalColdHarness() {
		switch (kase.replace("-held", "")) {
			case "town" -> { ex = 1280; ez = -9600; }
			case "metro" -> { ex = 2432; ez = -5696; }
			case "wild" -> { ex = 6000; ez = 6000; }
			case "wild2" -> { ex = -3000; ez = 3000; }
			default -> throw new IllegalArgumentException("fb.portal.case " + kase);
		}
	}

	public static void install() {
		PortalColdHarness h = new PortalColdHarness();
		LaneStats.enable(true);
		ServerLifecycleEvents.SERVER_STARTED.register(h::started);
		ServerTickEvents.START_SERVER_TICK.register(sv -> h.tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(h::tickEnd);
		// the server thread's stack in any tick over 1.5 s once the spawn area is up, until the exit inspection (COLDTICK: repeated dumps, workers)
		LongTickWatch.install("[PORTAL]", () -> "phase " + h.phase, () -> h.player != null && h.phase < 3);
	}

	private void put(String k, Object v) {
		rows.put(k, v);
		FractalBuildingsMod.LOGGER.info("[PORTAL] {} = {}", k, v);
	}

	private void started(MinecraftServer server) {
		put("case", kase);
		put("host", host());
		put("renderer", "none (dedicated server)");
		put("load.start", load());
		put("exit.target", ex + "," + ez);
		nether = server.getLevel(Level.NETHER);
		if (nether == null) throw new IllegalStateException("no nether");
		WorldPlanner pl = WorldPlanners.of(server.overworld(), server.overworld().getChunkSource().getGenerator()).orElseThrow();
		var tile = pl.tileAt(ex, ez);
		put("exit.tile", tile.ix() + "," + tile.iz() + " " + tile.minX() + ".." + tile.maxX() + " x " + tile.minZ() + ".." + tile.maxZ());
		player = MockPlayer.join(server, server.overworld(), "portal-walker", 8, GameType.CREATIVE);
	}

	private static String host() {
		try { return Files.readString(Path.of("/etc/hostname")).strip(); } catch (IOException e) { return "?"; }
	}

	/** One tick of the held trip: asks PortalHold (inside the portal, allowed, vanilla says go) until it lets the trip go. */
	private boolean holdDone(double tickMs) {
		if (holdT0 == 0) { holdT0 = System.nanoTime(); LaneStats.reset(); put("load.trigger", load()); put("overworld.loadedChunks.before", player.level().getServer().overworld().getChunkSource().getLoadedChunksCount()); }
		long g0 = System.nanoTime();
		boolean go = dev.fractalbuildings.mod.teleport.PortalHold.gate(nether, player, true, portalPos, true, true, true);
		double gms = (System.nanoTime() - g0) / 1e6;
		if (holdTicks == 0) firstGateMs = gms;
		holdTicks++;
		holdMaxGate = Math.max(holdMaxGate, gms);
		if (holdTicks > 1) holdMaxTick = Math.max(holdMaxTick, tickMs + gms); // tickMs ends before this listener: add our own call
		else holdMaxTick = Math.max(holdMaxTick, gms);
		if (!go) {
			if (holdTicks % 200 == 0) FractalBuildingsMod.LOGGER.info("[PORTAL] held {} ticks, {} ms, overworld chunks {}", holdTicks, (System.nanoTime() - holdT0) / 1_000_000, player.level().getServer().overworld().getChunkSource().getLoadedChunksCount());
			return false;
		}
		put("hold.ms", String.format(Locale.ROOT, "%.0f", (System.nanoTime() - holdT0) / 1e6));
		put("hold.ticks", holdTicks);
		put("hold.maxTick.ms", String.format(Locale.ROOT, "%.0f", holdMaxTick));
		put("hold.firstGate.ms", String.format(Locale.ROOT, "%.0f", firstGateMs));
		put("hold.maxGate.ms", String.format(Locale.ROOT, "%.0f", holdMaxGate));
		put("hold.stats", dev.fractalbuildings.mod.teleport.PortalHold.lastStats);
		put("overworld.loadedChunks.atRelease", player.level().getServer().overworld().getChunkSource().getLoadedChunksCount());
		return true;
	}

	private void tickEnd(MinecraftServer server) {
		double ms = (System.nanoTime() - tickStart) / 1e6;
		serverTick++;
		if (player == null) return;
		ServerLevel overworld = server.overworld();
		switch (phase) {
			case 0 -> { // the spawn area settles, then the player goes to the nether (a plain, non-portal move: the nether side is not what is measured)
				if (++wait < 100) return;
				int nx = Math.floorDiv(ex, 8), nz = Math.floorDiv(ez, 8);
				portalPos = new BlockPos(nx, 71, nz); // an interior block of the portal (frame corner is at y 70)
				for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) nether.getChunk((nx >> 4) + dx, (nz >> 4) + dz, ChunkStatus.FULL, true);
				buildPortal(nx, 70, nz);
				player.teleportTo(nether, nx + 0.5, 71, nz + 0.5, Set.<Relative>of(), 0f, 0f, true);
				put("nether.portal", portalPos.toShortString());
				phase = 1;
				wait = 0;
			}
			case 1 -> { // let the nether view load and the load average settle
				if (player.level() != nether) { if (++wait > 400) throw new IllegalStateException("player never reached the nether"); return; }
				if (++wait < 140) return;
				if (held && !holdDone(ms)) return;
				if (!held) { LaneStats.reset(); put("load.trigger", load()); }
				// Vanilla's tick path (Entity.handlePortal) never fires for a mock player whose connection is not ticked, so the two calls it makes are made here,
				// timed apart: the exit search/creation (PortalForcer: getHeight, getBlockState around the exit: forces full chunk generation) and the dimension change itself.
				BlockState ps = nether.getBlockState(portalPos);
				if (!ps.is(Blocks.NETHER_PORTAL)) throw new IllegalStateException("portal block missing: " + ps);
				if (!held) put("overworld.loadedChunks.before", overworld.getChunkSource().getLoadedChunksCount());
				final Thread serverThread = Thread.currentThread();
				final java.util.concurrent.ConcurrentHashMap<String, Integer> hist = new java.util.concurrent.ConcurrentHashMap<>();
				final boolean[] stop = {false};
				Thread sampler = new Thread(() -> {
					try {
						while (!stop[0]) {
							StackTraceElement[] st = serverThread.getStackTrace();
							String key = "vanilla/other";
							for (StackTraceElement e : st) {
								String c = e.getClassName();
								if (c.startsWith("dev.fractalbuildings.") && !c.endsWith("PortalColdHarness")) { key = "ours:" + c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName(); break; }
							}
							if (key.startsWith("vanilla") && st.length > 0) key += " top=" + st[0].getClassName().replaceAll(".*[.$]", "") + "." + st[0].getMethodName();
							hist.merge(key, 1, Integer::sum);
							Thread.sleep(250);
						}
					} catch (InterruptedException ignored) { }
				}, "portalcold-sampler");
				sampler.setDaemon(true);
				sampler.start();
				t0 = System.nanoTime();
				var transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(nether, player, portalPos);
				long t1 = System.nanoTime();
				if (transition == null) throw new IllegalStateException("no portal destination");
				put("dest.pos", transition.position().toString());
				boolean ok = player.teleport(transition) != null || player.level() == overworld;
				long t2 = System.nanoTime();
				stop[0] = true; sampler.interrupt();
				put("sampler.top", hist.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(10).map(e -> e.getKey() + "=" + e.getValue()).toList().toString() + " (samples of 250 ms during the call)");
				put("overworld.loadedChunks", overworld.getChunkSource().getLoadedChunksCount());
				put("getPortalDestination.ms", String.format(Locale.ROOT, "%.0f", (t1 - t0) / 1e6));
				put("teleport.call.ms", String.format(Locale.ROOT, "%.0f", (t2 - t1) / 1e6));
				put("trigger.to.arrival.ms", String.format(Locale.ROOT, "%.0f", (t2 - t0) / 1e6));
				put("player.inOverworld", player.level() == overworld);
				put("teleport.held", dev.fractalbuildings.mod.teleport.TeleportHold.holding(player));
				put("load.arrive", load());
				put("lanes.at.arrival", LaneStats.report().replace('\n', ' '));
				phase = 2;
				wait = 0;
				tickMs.clear();
			}
			case 2 -> { // the following ticks: the rest of the stall shows as long ticks (chunk sending, the held-teleport replay, workers finishing)
				tickMs.add(ms);
				if (++wait < 100) return;
				double max = 0;
				for (double d : tickMs) max = Math.max(max, d);
				put("followTicks.max.ms", String.format(Locale.ROOT, "%.0f", max));
				put("followTicks.over100ms", tickMs.stream().filter(d -> d > 100).count());
				put("followTicks.top3", tickMs.stream().sorted((a, b) -> Double.compare(b, a)).limit(3).map(d -> String.format(Locale.ROOT, "%.0f", d)).toList().toString());
				put("player.inOverworld.after", player.level() == overworld);
				put("arrive.pos", String.format(Locale.ROOT, "%.1f,%.1f,%.1f", player.getX(), player.getY(), player.getZ()));
				phase = 3;
				wait = 0;
			}
			case 3 -> { // two quiet ticks, then inspect the exit
				if (++wait < 3) return;
				try { inspect(server, overworld); } finally { finish(server); }
				phase = 4;
			}
			default -> { }
		}
	}

	/** An obsidian floor and frame (4 wide, 5 tall) with a 2x3 portal in the x axis, clear air around it, as a player's portal would be. */
	private void buildPortal(int x, int y, int z) {
		for (int dx = -3; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 6; dy++)
			nether.setBlock(new BlockPos(x + dx, y + dy, z + dz), dy == -1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++) if (dx == -1 || dx == 2 || dy == 0 || dy == 4) nether.setBlock(new BlockPos(x + dx, y + dy, z), obs, 3);
		for (int dx = 0; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++) nether.setBlock(new BlockPos(x + dx, y + dy, z), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 3);
		FractalBuildingsMod.LOGGER.info("[PORTAL] built, interior state {}", nether.getBlockState(new BlockPos(x, y + 1, z)));
	}

	private static final Set<String> BUILDING_WORDS = Set.of("planks", "brick", "cobblestone", "glass", "door", "fence", "slab", "stairs", "log", "wool", "terracotta", "concrete", "path", "lantern", "torch", "bed", "table", "chest", "barrel", "bookshelf", "carpet", "thatch", "hay", "wall");

	private void inspect(MinecraftServer server, ServerLevel level) {
		// find the exit portal the transition made (near where the player arrived)
		BlockPos ap = player.blockPosition();
		BlockPos lo = null, hi = null;
		for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -8; dy <= 8; dy++) {
			BlockPos p = ap.offset(dx, dy, dz);
			if (level.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
				lo = lo == null ? p : new BlockPos(Math.min(lo.getX(), p.getX()), Math.min(lo.getY(), p.getY()), Math.min(lo.getZ(), p.getZ()));
				hi = hi == null ? p : new BlockPos(Math.max(hi.getX(), p.getX()), Math.max(hi.getY(), p.getY()), Math.max(hi.getZ(), p.getZ()));
			}
		}
		put("exit.portalBlocks", lo == null ? "none found" : lo.toShortString() + " .. " + hi.toShortString());
		BlockPos c = lo == null ? ap : lo;
		int fx = c.getX(), fy = c.getY(), fz = c.getZ();
		put("exit.offsetFromTarget", (fx - ex) + "," + (fz - ez));
		// planner: does the exit structure (portal frame plus its 4x? platform, ~ x-2..x+3, z-3..z+3) overlap a planned building box?
		WorldPlanner pl = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
		int hit = 0, near = 0;
		StringBuilder names = new StringBuilder();
		var tile = pl.tileAt(fx, fz);
		put("exit.tile.now", tile.ix() + "," + tile.iz());
		Box3 foot = new Box3(fx - 2, fy - 1, fz - 3, 6, 7, 7);
		try {
			for (var b : pl.buildings(tile.ix(), tile.iz())) {
				Box3 bx = b.box();
				if (overlap(bx, foot)) { hit++; names.append(b.brief().toString(), 0, Math.min(60, b.brief().toString().length())).append("; "); }
				else if (overlap(bx, new Box3(foot.x() - 3, foot.y(), foot.z() - 3, foot.sx() + 6, foot.sy(), foot.sz() + 6))) near++;
			}
		} catch (RuntimeException e) { put("exit.planner.error", e.toString()); }
		put("exit.buildingBoxesOverlapFootprint", hit + " " + names);
		put("exit.buildingBoxesWithin3", near);
		// block census of a 17x17x14 box around the exit: building-looking blocks, ground, what the portal's own platform replaced is unknown
		TreeMap<String, Integer> census = new TreeMap<>();
		int buildingish = 0;
		for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int dy = -3; dy <= 10; dy++) {
			BlockState s = level.getBlockState(new BlockPos(fx + dx, fy + dy, fz + dz));
			if (s.isAir()) continue;
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
			census.merge(id, 1, Integer::sum);
			for (String w : BUILDING_WORDS) if (id.contains(w)) { buildingish++; break; }
		}
		put("exit.buildingishBlocksIn17x17x14", buildingish);
		put("exit.censusTop", census.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12).map(e -> e.getKey() + "=" + e.getValue()).toList().toString());
		// ASCII dump: layers fy-1 .. fy+5 over x-6..x+8, z-6..z+6 (O obsidian, N portal, . air, # other solid, b building-looking, ~ fluid)
		for (int dy = -1; dy <= 5; dy++) {
			dump.append("layer y=").append(fy + dy).append('\n');
			for (int dz = -6; dz <= 6; dz++) {
				for (int dx = -6; dx <= 8; dx++) {
					BlockState s = level.getBlockState(new BlockPos(fx + dx, fy + dy, fz + dz));
					String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
					char ch;
					if (s.isAir()) ch = '.';
					else if (s.is(Blocks.OBSIDIAN)) ch = 'O';
					else if (s.is(Blocks.NETHER_PORTAL)) ch = 'N';
					else if (!s.getFluidState().isEmpty()) ch = '~';
					else {
						ch = '#';
						for (String w : BUILDING_WORDS) if (id.contains(w)) { ch = 'b'; break; }
					}
					dump.append(ch);
				}
				dump.append('\n');
			}
		}
	}

	private static boolean overlap(Box3 a, Box3 b) {
		return a.x() < b.x() + b.sx() && b.x() < a.x() + a.sx() && a.y() < b.y() + b.sy() && b.y() < a.y() + a.sy() && a.z() < b.z() + b.sz() && b.z() < a.z() + a.sz();
	}

	private void finish(MinecraftServer server) {
		try {
			Files.createDirectories(out);
			StringBuilder j = new StringBuilder("{\n");
			for (var e : rows.entrySet()) j.append("  \"").append(e.getKey()).append("\": \"").append(String.valueOf(e.getValue()).replace("\"", "'")).append("\",\n");
			j.setLength(j.length() - 2);
			j.append("\n}\n");
			Files.writeString(out.resolve("portal-" + kase + ".json"), j.toString());
			Files.writeString(out.resolve("portal-" + kase + ".txt"), dump.toString());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			server.halt(false);
		}
	}
}
