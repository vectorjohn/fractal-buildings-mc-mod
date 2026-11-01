package dev.fractalbuildings.gametest.perf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * PERFBASE: the measuring helpers that need nothing of our mod (only Minecraft and slf4j), so the vanilla half of the perf world run
 * (tools/vanilla-server, no fractalbuildings jar at all) compiles this file and {@link MockPlayer} straight from here. {@link PerfSupport}
 * adds the mod-only rows (lane counters, planner warm) on top.
 */
class PerfCore {
	static final String SEED = "987654321";
	static final int CITY_X = 1280, CITY_Z = -9600, METRO_X = 2432, METRO_Z = -5696;
	static final double ELYTRA_PER_TICK = 33.0 / 20.0;
	static final int FLIGHT_BLOCKS = Integer.parseInt(System.getenv().getOrDefault("FB_PERF_FLIGHT", "2500"));
	/** PROFILE: FB_PERF_TARGET=metro flies the server harness into the metropolis instead (ending 600 blocks short of its centre, inside its rim, so the cold block stays ungenerated); default: the gate city. */
	static final boolean FLY_METRO = "metro".equals(System.getenv("FB_PERF_TARGET"));
	static final int FLY_X = FLY_METRO ? METRO_X : CITY_X, FLY_Z = FLY_METRO ? METRO_Z - 600 : CITY_Z;

	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fb-perf");

	final Map<String, Object> out = new LinkedHashMap<>();
	/** PROFILE: called at each phase boundary of {@link #blockRows} (cold, warmprep, warm, tail); a no-op unless a profile is running. */
	java.util.function.Consumer<String> phase = x -> {};

	void put(String k, double v) {
		out.put(k, Math.round(v * 100.0) / 100.0);
		LOGGER.info("[PERF] {} = {}", k, String.format(Locale.ROOT, "%.2f", v));
	}

	void put(String k, String v) {
		out.put(k, v);
		LOGGER.info("[PERF] {} = {}", k, v);
	}

	static String load() {
		try { return Files.readString(Path.of("/proc/loadavg")).split(" ")[0]; } catch (IOException e) { return "?"; }
	}

	static String command(MinecraftServer server, String command, double x, double z) {
		StringBuilder o = new StringBuilder();
		CommandSource sink = new CommandSource() {
			@Override public void sendSystemMessage(Component m) { o.append(m.getString()).append('\n'); }
			@Override public boolean acceptsSuccess() { return true; }
			@Override public boolean acceptsFailure() { return true; }
			@Override public boolean shouldInformAdmins() { return false; }
		};
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(sink).withPosition(new net.minecraft.world.phys.Vec3(x, 64, z)), command);
		return o.toString().strip();
	}

	static long usedMb() {
		for (int i = 0; i < 3; i++) System.gc();
		Runtime r = Runtime.getRuntime();
		return (r.totalMemory() - r.freeMemory()) >> 20;
	}

	static long regionBytes(MinecraftServer server) {
		server.saveEverything(false, true, false);
		Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		try (var s = Files.walk(dir)) {
			return s.filter(p -> p.toString().endsWith(".mca")).mapToLong(p -> p.toFile().length()).sum();
		} catch (IOException e) {
			return -1;
		}
	}

	/** Generates a (2r+1)^2 block of chunks around a block position on the server thread; returns the per-chunk wall times (ms). */
	static double[] generate(ServerLevel level, int bx, int bz, int r, int shiftChunksX) {
		int ccx = (bx >> 4) + shiftChunksX, ccz = bz >> 4;
		List<Double> t = new ArrayList<>();
		for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
			long t0 = System.nanoTime();
			final int fi = t.size();
			Thread wd = sampler("chunk#" + fi);
			level.getChunk(ccx + dx, ccz + dz, ChunkStatus.FULL, true);
			wd.interrupt();
			t.add((System.nanoTime() - t0) / 1e6);
			if (t.get(fi) > 1000) System.out.println("[COLDCHUNK] chunk#" + fi + " (" + (ccx + dx) + "," + (ccz + dz) + ") took " + t.get(fi) + " ms");
		}
		return t.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/** FB_PERF_SAMPLE=1 (scripts/perf.sh forwards it as -Pfb.perfSample): the wall-clock sampler is on. Diagnostic only; test code, so the wall clock is fine. */
	static final boolean SAMPLE = System.getProperty("fractalbuildings.perfSample") != null || System.getenv("FB_PERF_SAMPLE") != null;

	/**
	 * CASCADE step 1: every 500 ms, every thread with a planner frame (or a busy pool thread), RUN or WAIT, by part, until interrupted. Prints
	 * {@code [JFPROF] <label> t=<s> <thread> RUN|WAIT <part> @ <top frame>}; {@code LINKRULE>} / {@code EXITCUT>} mark {@code ExitTrim.gateServed} /
	 * {@code fullCut} on the stack. Off unless {@link #SAMPLE}: then it returns an unstarted thread, so callers can interrupt it unconditionally.
	 */
	static Thread sampler(String label) {
		if (!SAMPLE) return new Thread(() -> { }, "jfprof-sampler-off");
		Thread wd = new Thread(() -> {
			try {
				long s0 = System.nanoTime();
				while (true) {
					Thread.sleep(500);
					StringBuilder sb = new StringBuilder();
					double ts = (System.nanoTime() - s0) / 1e9;
					for (var e : Thread.getAllStackTraces().entrySet()) {
						String n = e.getKey().getName();
						StackTraceElement[] st = e.getValue();
						if (st.length == 0) continue;
						String top0 = st[0].getMethodName();
						boolean wait = top0.startsWith("park") || top0.equals("wait") || top0.equals("wait0") || top0.startsWith("sleep");
						String pt = jfPart(st);
						if (pt == null) {
							if (n.startsWith("fractalbuildings") && !wait) pt = "pool, no planner frame";
							else continue;
						}
						StackTraceElement top = null;
						for (StackTraceElement f : st) if (f.getClassName().startsWith("dev.fractalbuildings.")) { top = f; break; }
						sb.append("[JFPROF] ").append(label).append(String.format(Locale.ROOT, " t=%.1f ", ts)).append(n).append(wait ? " WAIT " : " RUN ").append(pt)
							.append(" @ ").append(top == null ? "-" : top.getClassName().substring(top.getClassName().lastIndexOf('.') + 1) + "." + top.getMethodName() + ":" + top.getLineNumber()).append('\n');
					}
					if (sb.length() > 0) System.out.print(sb);
				}
			} catch (InterruptedException ignored) { }
		}, "jfprof-sampler");
		wd.setDaemon(true);
		wd.start();
		return wd;
	}

	/** The part of our planning a stack is in (JfrAttrib.part precedence), plus the outermost WorldPlanner entry. */
	static String jfPart(StackTraceElement[] st) {
		boolean any = false, quays = false, junction = false, road = false, region = false, regionForRoads = false, streets = false, tile = false;
		String entry = "";
		String cas = "";
		for (StackTraceElement f : st) {
			String c = f.getClassName(), m = f.getMethodName();
			if (!c.startsWith("dev.fractalbuildings.core.")) continue;
			any = true;
			if (c.endsWith(".WorldPlanner") && !m.startsWith("lambda")) entry = m;
			if (c.endsWith(".ExitTrim") && m.equals("gateServed")) cas = "LINKRULE> "; else if (c.endsWith(".ExitTrim") && (m.equals("fullCut") || m.equals("cutWithin")) && cas.isEmpty()) cas = "EXITCUT> ";
			if (c.endsWith(".Quays") || c.endsWith(".QuayTrace") || m.toLowerCase(Locale.ROOT).contains("quay")) quays = true;
			else if (c.endsWith(".JunctionField")) junction = true;
			else if (c.endsWith(".RegionPlanner") || c.endsWith(".FieldRegionView") || c.endsWith(".RegionField")) region = true;
			else if (c.contains(".core.road.")) { if (region) regionForRoads = true; else road = true; }
			else if (c.contains("Street") || c.contains("Skeleton") || c.contains("Network")) streets = true;
			else if (c.contains(".core.settlement.") || c.contains(".core.gen.") || c.contains(".core.raster.")) tile = true;
		}
		if (!any) return null;
		String p = quays ? "quays" : junction ? "junction field" : region ? (regionForRoads ? "region for road graph" : "region (sites)") : road ? "intercity roads" : streets ? "streets and skeleton" : tile ? "tile and buildings" : "other ours";
		return cas + p + " [WP." + entry + "]";
	}

	void stats(String k, double[] ms) {
		double[] s = ms.clone();
		Arrays.sort(s);
		put(k + ".mean.ms", Arrays.stream(s).average().orElse(0));
		put(k + ".median.ms", s[s.length / 2]);
		put(k + ".p95.ms", s[Math.min(s.length - 1, (int) Math.ceil(s.length * 0.95) - 1)]);
		put(k + ".max.ms", s[s.length - 1]);
	}

	/** The flight rows from the per-tick durations: stats, where the worst tick fell, how many ran over 50 ms. */
	void flightStats(String p, double[] tm) {
		if (tm.length > 0) stats(p + "flight.tick", tm);
		int worst = 0;
		for (int i = 0; i < tm.length; i++) if (tm[i] > tm[worst]) worst = i;
		put(p + "flight.tick.worstAt.blocksFlown", worst * ELYTRA_PER_TICK);
		double over = 0;
		for (double x : tm) if (x > 50) over++;
		put(p + "flight.ticksOver50ms", over);
	}

	/** COLDARRIVAL: the vanilla baseline of the {@code /locate} rows: the same village locate, fresh area, in the base world (no settlements, no veto work). */
	void locateRowsBase(MinecraftServer server) {
		long t0 = System.nanoTime();
		String loc = command(server, "locate structure #minecraft:village", -30000, -30000);
		put("base.locate.village.fresh.ms", (System.nanoTime() - t0) / 1e6);
		put("base.locate.village.fresh.text", loc);
		t0 = System.nanoTime();
		command(server, "locate structure #minecraft:village", -30000, -30000);
		put("base.locate.village.repeat.ms", (System.nanoTime() - t0) / 1e6);
	}

	/** After the flight, vanilla world: cold block in the metropolis, then the block next to it; region growth, heap, load. Same rows as {@link PerfSupport#blockRows}. */
	void blockRowsBase(MinecraftServer server, String p) {
		ServerLevel level = server.overworld();
		long bytes0 = regionBytes(server);
		phase.accept("cold");
		long t1 = System.nanoTime();
		double[] cold = generate(level, METRO_X, METRO_Z, 4, 0);
		put(p + "cold.block.wall.ms", (System.nanoTime() - t1) / 1e6);
		stats(p + "cold.chunk", cold);
		phase.accept("warm");
		long t2 = System.nanoTime();
		double[] warm = generate(level, METRO_X, METRO_Z, 4, 9);
		put(p + "warm.block.wall.ms", (System.nanoTime() - t2) / 1e6);
		stats(p + "warm.chunk", warm);
		phase.accept("tail");
		long bytes1 = regionBytes(server);
		put(p + "region.growth.bytes", bytes1 - bytes0);
		put(p + "heap.mb", usedMb());
		put(p + "load.end", load());
	}

	/** Writes the rows as a flat JSON object. */
	void write(Path f) {
		try {
			Files.createDirectories(f.getParent());
			StringBuilder sb = new StringBuilder("{\n");
			int i = 0;
			for (var e : out.entrySet()) {
				sb.append("  \"").append(e.getKey()).append("\": ");
				if (e.getValue() instanceof String s) sb.append('"').append(s.replace("\\", "/").replace("\"", "'")).append('"');
				else sb.append(e.getValue());
				sb.append(++i < out.size() ? ",\n" : "\n");
			}
			Files.writeString(f, sb.append("}\n").toString());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
