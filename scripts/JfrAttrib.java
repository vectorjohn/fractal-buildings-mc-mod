// JFR attribution report (PROFILE, 2026-10-28): which CPU, allocation and GC in a recording is ours, which is vanilla code called from
// ours, and which is vanilla's own. A sample counts as "ours" when its top frame is our code, as "for us" when the top frame is not ours but
// some frame below it is (stack ancestry), and as "own" otherwise. Our code: a class under dev.fractalbuildings., or a method whose name
// contains "fractalbuildings" (a Mixin handler merged into a vanilla class); the perf harness (dev.fractalbuildings.gametest.) is not ours: its
// frames sit under the chunk requests of both worlds and count as "lib". The top frame's owner is split into mc (net.minecraft.,
// com.mojang.), jdk (java., jdk., sun., com.sun.) and lib (everything else: fastutil, Fabric, netty, ...).
//
//   scripts/dev java scripts/JfrAttrib.java [--event cpu|exec] [--top N] [--title T] [--chunks-out F] file.jfr...   (markdown on stdout;
//   --chunks-out writes "status<TAB>chunkX<TAB>chunkZ" for every minecraft.ChunkGeneration event, for scripts/profile-overlap.sh)
//
// --event cpu (default) reads jdk.CPUTimeSample (JDK 25, one sample per 10 ms of a thread's CPU time, every busy thread; needs the event
// enabled, as mod/src/gametest/.../perf/PerfProfile does); exec reads jdk.ExecutionSample (wall-clock period, at most a few threads per
// period, so it undercounts when many threads are busy). Several files are summed (e.g. all phases of one run). Stacks deeper than the
// JVM's -XX:FlightRecorderOptions:stackdepth (default 64) are truncated: a truncated stack with no frame of ours is counted as "unknown"
// (the ancestor that would decide it is missing). Used by scripts/profile.sh; read the numbers next to the exact per-thread CPU rows of
// world-<variant>.json (phase.*.cpu.*), which this does not replace.
import java.nio.file.Path;
import java.util.*;
import jdk.jfr.consumer.*;

public class JfrAttrib {
	static boolean ours(RecordedFrame f) {
		RecordedMethod m = f.getMethod();
		if (m == null) return false;
		String c = m.getType().getName();
		return (c.startsWith("dev.fractalbuildings.") && !c.startsWith("dev.fractalbuildings.gametest.")) || m.getName().contains("fractalbuildings");
	}

	static String owner(RecordedFrame f) {
		if (ours(f)) return "ours";
		String c = f.getMethod() == null ? "?" : f.getMethod().getType().getName();
		if (c.startsWith("net.minecraft.") || c.startsWith("com.mojang.")) return "mc";
		if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.") || c.startsWith("com.sun.")) return "jdk";
		return "lib";
	}

	static String name(RecordedFrame f) {
		RecordedMethod m = f.getMethod();
		if (m == null) return "?";
		String c = m.getType().getName();
		c = c.replace("net.minecraft.", "mc.").replace("dev.fractalbuildings.", "fb.").replaceAll("\\$\\$Lambda[^.]*", "\\$\\$Lambda");
		return c + "." + m.getName();
	}

	/** The vanilla pipeline step a sample belongs to: the first ChunkStatusTasks method from the top, else save/IO, light, tick or other. */
	static String step(List<RecordedFrame> fr, String thread) {
		boolean io = false, light = false, noise = false, biome = false;
		for (RecordedFrame f : fr) {
			if (f.getMethod() == null) continue;
			String c = f.getMethod().getType().getName();
			if (c.endsWith(".ChunkStatusTasks")) return "chunk " + f.getMethod().getName().replaceAll("^lambda\\$([a-zA-Z]+)\\$.*", "$1");
			if (c.contains("RegionFile") || c.contains("IOWorker") || c.contains("ChunkStorage") || c.contains("SerializableChunkData") || c.contains("NbtIo")) io = true;
			if (c.contains("LightEngine") || c.contains("LightEventListener")) light = true;
			if (c.endsWith(".NoiseBasedChunkGenerator") && !noise && !biome) { String m = f.getMethod().getName(); if (m.contains("Biome") || m.contains("biome")) biome = true; else noise = true; }
		}
		if (io) return "save/IO";
		if (light) return "light";
		if (noise) return "noise fill (NoiseBasedChunkGenerator, async)";
		if (biome) return "biome fill (NoiseBasedChunkGenerator, async)";
		if (thread.equals("Server thread")) return "server thread, other";
		return "other";
	}

	/** Which part of our planning a stack belongs to (the same precedence as PerfReachTest.purpose: link rule, exit cut, then the rest): null when no frame of ours. */
	static String part(List<RecordedFrame> fr) {
		boolean any = false, linkRule = false, exitCut = false, quays = false, junction = false, road = false, region = false, regionForRoads = false, streets = false, tile = false, probe = false, place = false;
		for (RecordedFrame f : fr) {
			if (!ours(f) || f.getMethod() == null) continue;
			any = true;
			String c = f.getMethod().getType().getName(), m = f.getMethod().getName();
			if (c.endsWith(".ExitTrim") && m.equals("gateServed")) linkRule = true;
			else if (c.endsWith(".ExitTrim") && (m.equals("fullCut") || m.equals("cutWithin"))) exitCut = true;
			if (c.endsWith(".Quays") || c.endsWith(".QuayTrace") || m.toLowerCase(Locale.ROOT).contains("quay")) quays = true;
			else if (c.endsWith(".JunctionField")) junction = true;
			else if (c.endsWith(".RegionPlanner") || c.endsWith(".FieldRegionView") || c.endsWith(".RegionField")) region = true;
			else if (c.contains(".core.road.")) { if (region) regionForRoads = true; else road = true; }
			else if (c.contains("Street") || c.contains("Skeleton") || c.contains("Network")) streets = true;
			else if (c.contains(".core.settlement.") || c.contains(".core.gen.") || c.contains(".core.raster.")) tile = true;
			else if (c.contains(".mod.worldgen.") || c.contains(".mod.roads.") || c.contains(".mod.gameplay.") || c.contains(".mod.block.")) place = true;
			else if (c.contains(".mod.probe.")) probe = true;
		}
		if (!any) return null;
		if (linkRule) return "link rule (ExitTrim.gateServed)";
		if (exitCut) return "exit cut (ExitTrim.fullCut)";
		if (quays) return "quays";
		if (junction) return "junction field";
		if (region) return regionForRoads ? "region decisions for the road graph" : "region decisions (sites)";
		if (road) return "intercity roads (routes, stations)";
		if (streets) return "streets and skeleton";
		if (tile) return "tile and building plans";
		if (place) return "block placement and features (mod.worldgen/roads/gameplay)";
		return probe ? "probes (other)" : "other ours";
	}

	static String group(String thread) {
		return thread == null ? "?" : thread.replaceAll("[0-9]+", "N");
	}

	static final class Count {
		long n;
		final Map<String, Long> by = new HashMap<>();
		void add(String k, long w) { n += w; by.merge(k, w, Long::sum); }
	}

	static <K> List<Map.Entry<K, Long>> top(Map<K, Long> m, int k) {
		List<Map.Entry<K, Long>> l = new ArrayList<>(m.entrySet());
		l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		return l.subList(0, Math.min(k, l.size()));
	}

	static String pct(long a, long b) { return b == 0 ? "-" : String.format(Locale.ROOT, "%.1f %%", 100.0 * a / b); }

	public static void main(String[] args) throws Exception {
		String ev = "cpu", title = null;
		java.io.PrintWriter chunksOut = null;
		int topN = 15;
		List<Path> files = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--event" -> ev = args[++i];
				case "--top" -> topN = Integer.parseInt(args[++i]);
				case "--title" -> title = args[++i];
				case "--chunks-out" -> chunksOut = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(Path.of(args[++i])));
				default -> files.add(Path.of(args[i]));
			}
		}
		if (files.isEmpty()) { System.err.println("usage: java scripts/JfrAttrib.java [--event cpu|exec] [--top N] [--title T] file.jfr..."); System.exit(2); }
		String sampleEvent = ev.equals("exec") ? "jdk.ExecutionSample" : "jdk.CPUTimeSample";
		String[] cats = {"ours", "mc for us", "jdk for us", "lib for us", "mc own", "jdk own", "lib own", "unknown"};
		Map<String, long[]> byThread = new TreeMap<>();
		long[] total = new long[cats.length];
		long samples = 0, truncated = 0, failed = 0, lost = 0;
		Map<String, Long> self = new HashMap<>(), selfForUs = new HashMap<>();
		Map<String, Long> selfOwn = new HashMap<>();
		Map<String, Map<String, Long>> ownCallers = new HashMap<>();
		Map<String, Map<String, Long>> callers = new HashMap<>(), entries = new HashMap<>();
		Map<String, Long> entryForUs = new HashMap<>(), inclusiveOurs = new HashMap<>(), outerOurs = new HashMap<>();
		Map<String, long[]> steps = new TreeMap<>();
		Map<String, long[]> parts = new TreeMap<>(); // part -> {ours self, vanilla for us}
		Map<String, long[]> mcEvents = new TreeMap<>(); // key -> count, sum ns, max ns
		List<Long> tickNs = new ArrayList<>();
		Map<String, Long> pkgIncl = new TreeMap<>();
		long allocTotal = 0;
		Map<String, Long> allocCat = new LinkedHashMap<>(), allocType = new HashMap<>(), allocOurSite = new HashMap<>();
		long gcCount = 0, gcSumNs = 0, gcMaxNs = 0, pauseSumNs = 0, pauseMaxNs = 0;
		for (Path f : files) {
			try (RecordingFile rf = new RecordingFile(f)) {
				while (rf.hasMoreEvents()) {
					RecordedEvent e = rf.readEvent();
					String t = e.getEventType().getName();
					if (t.startsWith("minecraft.")) {
						String k = t;
						if (e.hasField("status")) k += " " + e.getString("status");
						if (e.hasField("structure")) k += " " + e.getString("structure");
						if (chunksOut != null && t.equals("minecraft.ChunkGeneration")) chunksOut.println(e.getString("status") + "\t" + e.getInt("chunkPosX") + "\t" + e.getInt("chunkPosZ"));
						if (t.equals("minecraft.ServerTickTime")) tickNs.add(e.getDuration("averageTickDuration").toNanos());
						long d = e.getDuration().toNanos();
						long[] a = mcEvents.computeIfAbsent(k, x -> new long[3]);
						a[0]++; a[1] += d; a[2] = Math.max(a[2], d);
						continue;
					}
					if (t.equals("jdk.CPUTimeSamplesLost")) { lost += e.hasField("lostSamples") ? e.getLong("lostSamples") : 1; continue; }
					if (t.equals("jdk.GarbageCollection")) {
						gcCount++;
						long d = e.getDuration().toNanos();
						gcSumNs += d; gcMaxNs = Math.max(gcMaxNs, d);
						long p = e.hasField("sumOfPauses") ? e.getDuration("sumOfPauses").toNanos() : 0;
						pauseSumNs += p; pauseMaxNs = Math.max(pauseMaxNs, e.hasField("longestPause") ? e.getDuration("longestPause").toNanos() : 0);
						continue;
					}
					boolean alloc = t.equals("jdk.ObjectAllocationSample");
					if (!alloc && !t.equals(sampleEvent)) continue;
					RecordedStackTrace st = e.getStackTrace();
					long w = 1;
					if (alloc) w = e.getLong("weight");
					if (st == null || st.getFrames().isEmpty()) { if (!alloc) failed++; continue; }
					List<RecordedFrame> fr = st.getFrames();
					int firstOurs = -1;
					for (int i = 0; i < fr.size(); i++) if (ours(fr.get(i))) { firstOurs = i; break; }
					String own = owner(fr.get(0));
					String cat;
					if (own.equals("ours")) cat = "ours";
					else if (firstOurs >= 0) cat = own + " for us";
					else if (st.isTruncated()) cat = "unknown";
					else cat = own + " own";
					if (alloc) {
						allocTotal += w;
						allocCat.merge(cat, w, Long::sum);
						String type = e.getClass("objectClass") == null ? "?" : e.getClass("objectClass").getName();
						allocType.merge(type, w, Long::sum);
						if (firstOurs >= 0) allocOurSite.merge(name(fr.get(firstOurs)), w, Long::sum);
						continue;
					}
					samples++;
					if (st.isTruncated()) truncated++;
					int ci = Arrays.asList(cats).indexOf(cat);
					RecordedThread th = e.hasField("sampledThread") ? e.getThread("sampledThread") : e.getThread();
					String g = group(th == null ? null : th.getJavaName());
					byThread.computeIfAbsent(g, k -> new long[cats.length])[ci]++;
					total[ci]++;
					String top = name(fr.get(0));
					self.merge(top, 1L, Long::sum);
					if (firstOurs > 0) selfForUs.merge(top, 1L, Long::sum);
					if (cat.endsWith(" own")) { selfOwn.merge(top, 1L, Long::sum); StringBuilder pth = new StringBuilder(); for (int q = 1; q < Math.min(fr.size(), 4); q++) pth.append(q > 1 ? " < " : "").append(name(fr.get(q)).replaceAll("^.*\\.([A-Za-z0-9$]+\\.[^.]+)$", "$1")); ownCallers.computeIfAbsent(top, x -> new HashMap<>()).merge(pth.toString(), 1L, Long::sum); }
					if (fr.size() > 1) callers.computeIfAbsent(top, k -> new HashMap<>()).merge(name(fr.get(1)), 1L, Long::sum);
					if (firstOurs > 0) {
						String en = name(fr.get(firstOurs));
						entries.computeIfAbsent(top, k -> new HashMap<>()).merge(en, 1L, Long::sum);
						entryForUs.merge(en, 1L, Long::sum);
					}
					{ String pt = part(fr); if (pt != null) parts.computeIfAbsent(pt, x -> new long[2])[ci == 0 ? 0 : 1]++; }
					steps.computeIfAbsent(step(fr, th == null ? "" : th.getJavaName()), k -> new long[cats.length])[ci]++;
					Set<String> pk = new HashSet<>();
					for (RecordedFrame x : fr) if (ours(x) && x.getMethod() != null) { String c = x.getMethod().getType().getName().replace("dev.fractalbuildings.", "fb."); int d = c.indexOf('.', c.indexOf('.', 3) + 1); String pn = d > 0 ? c.substring(0, d) : c; if (pk.add(pn)) pkgIncl.merge(pn, 1L, Long::sum); }
					Set<String> seen = new HashSet<>();
					String outer = null;
					for (RecordedFrame x : fr) if (ours(x)) { String nm = name(x); if (!nm.contains("Lambda") && !nm.contains(".lambda$") && seen.add(nm)) inclusiveOurs.merge(nm, 1L, Long::sum); outer = nm; }
					if (outer != null) outerOurs.merge(outer, 1L, Long::sum);
				}
			}
		}
		if (chunksOut != null) chunksOut.close();
		System.out.println("# " + (title != null ? title : "JFR attribution"));
		System.out.println();
		StringBuilder fl = new StringBuilder();
		for (Path f : files) fl.append(fl.length() > 0 ? ", " : "").append(f.getFileName());
		System.out.println("Files: " + fl + ". Event: " + sampleEvent + (ev.equals("cpu") ? " (one sample per 10 ms of thread CPU; samples x 0.01 = CPU s)" : " (wall-clock period)") + ".");
		System.out.printf(Locale.ROOT, "Samples: %d; truncated stacks: %d (%s); failed (no stack): %d; lost: %d.%n%n", samples, truncated, pct(truncated, samples), failed, lost);
		System.out.println("## By thread group (samples; % of all samples)");
		System.out.println();
		System.out.println("| thread group | total | ours | vanilla for us (mc+jdk+lib) | vanilla own (mc+jdk+lib) | unknown |");
		System.out.println("|---|---|---|---|---|---|");
		List<Map.Entry<String, long[]>> rows = new ArrayList<>(byThread.entrySet());
		rows.sort((a, b) -> Long.compare(Arrays.stream(b.getValue()).sum(), Arrays.stream(a.getValue()).sum()));
		rows.add(Map.entry("**all**", total));
		for (var r : rows) {
			long[] v = r.getValue();
			long s = Arrays.stream(v).sum();
			if (s * 1000 < samples && !r.getKey().equals("**all**")) continue;
			System.out.printf(Locale.ROOT, "| %s | %d (%s) | %d | %d (%d+%d+%d) | %d (%d+%d+%d) | %d |%n", r.getKey(), s, pct(s, samples), v[0], v[1] + v[2] + v[3], v[1], v[2], v[3], v[4] + v[5] + v[6], v[4], v[5], v[6], v[7]);
		}
		System.out.println();
		System.out.println("## Top " + topN + " methods by self samples");
		System.out.println();
		System.out.println("| # | method (top frame) | self | of which for us | top callers | nearest frame of ours |");
		System.out.println("|---|---|---|---|---|---|");
		int i = 0;
		for (var m : top(self, topN)) {
			StringBuilder c = new StringBuilder();
			for (var x : top(callers.getOrDefault(m.getKey(), Map.of()), 2)) c.append(c.length() > 0 ? "; " : "").append(x.getKey()).append(" ").append(pct(x.getValue(), m.getValue()));
			StringBuilder en = new StringBuilder();
			for (var x : top(entries.getOrDefault(m.getKey(), Map.of()), 2)) en.append(en.length() > 0 ? "; " : "").append(x.getKey()).append(" ").append(pct(x.getValue(), m.getValue()));
			System.out.printf(Locale.ROOT, "| %d | %s | %s | %s | %s | %s |%n", ++i, m.getKey(), pct(m.getValue(), samples), pct(selfForUs.getOrDefault(m.getKey(), 0L), m.getValue()), c, en.length() == 0 ? "-" : en);
		}
		System.out.println();
		long ownN = total[4] + total[5] + total[6];
		System.out.println("## Top " + topN + " methods, vanilla own samples only (no frame of ours on the stack)");
		System.out.println();
		System.out.println("| # | method | % of own | % of all | top call paths |");
		System.out.println("|---|---|---|---|---|");
		int io = 0;
		for (var m : top(selfOwn, topN)) { StringBuilder c = new StringBuilder(); for (var x : top(ownCallers.getOrDefault(m.getKey(), Map.of()), 2)) c.append(c.length() > 0 ? "; " : "").append(x.getKey()).append(" ").append(pct(x.getValue(), m.getValue())); System.out.printf(Locale.ROOT, "| %d | %s | %s | %s | %s |%n", ++io, m.getKey(), pct(m.getValue(), ownN), pct(m.getValue(), samples), c); }
		System.out.println();
		System.out.println("## Where our code calls into vanilla (nearest frame of ours, vanilla-for-us samples)");
		System.out.println();
		long forUs = total[1] + total[2] + total[3];
		System.out.println("| our frame | samples | % of all | % of for-us |");
		System.out.println("|---|---|---|---|");
		for (var m : top(entryForUs, topN)) System.out.printf(Locale.ROOT, "| %s | %d | %s | %s |%n", m.getKey(), m.getValue(), pct(m.getValue(), samples), pct(m.getValue(), forUs));
		System.out.println();
		System.out.println("## Our methods, inclusive (any frame; ours + vanilla for us; lambda frames left out)");
		System.out.println();
		System.out.println("| our method | inclusive | % of all |");
		System.out.println("|---|---|---|");
		for (var m : top(inclusiveOurs, topN)) System.out.printf(Locale.ROOT, "| %s | %d | %s |%n", m.getKey(), m.getValue(), pct(m.getValue(), samples));
		System.out.println();
		System.out.println("## By vanilla pipeline step (first ChunkStatusTasks frame from the top; else save/IO, light, server tick, other)");
		System.out.println();
		System.out.println("| step | total | ours | vanilla for us | vanilla own | unknown |");
		System.out.println("|---|---|---|---|---|---|");
		List<Map.Entry<String, long[]>> srows = new ArrayList<>(steps.entrySet());
		srows.sort((a, b) -> Long.compare(Arrays.stream(b.getValue()).sum(), Arrays.stream(a.getValue()).sum()));
		for (var r : srows) {
			long[] v = r.getValue();
			long s = Arrays.stream(v).sum();
			if (s * 500 < samples) continue;
			System.out.printf(Locale.ROOT, "| %s | %d (%s) | %d | %d | %d | %d |%n", r.getKey(), s, pct(s, samples), v[0], v[1] + v[2] + v[3], v[4] + v[5] + v[6], v[7]);
		}
		System.out.println();
		System.out.println("## By part of our planning (stack classification, as PerfReachTest)");
		System.out.println();
		System.out.println("| part | samples | % of all | ours self | vanilla for us |");
		System.out.println("|---|---|---|---|---|");
		List<Map.Entry<String, long[]>> prow = new ArrayList<>(parts.entrySet());
		prow.sort((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]));
		for (var r : prow) System.out.printf(Locale.ROOT, "| %s | %d | %s | %d | %d |%n", r.getKey(), r.getValue()[0] + r.getValue()[1], pct(r.getValue()[0] + r.getValue()[1], samples), r.getValue()[0], r.getValue()[1]);
		System.out.println();
		System.out.println("## Our packages, inclusive (a sample counts once per package on its stack)");
		System.out.println();
		System.out.println("| package | inclusive | % of all |");
		System.out.println("|---|---|---|");
		for (var m : top(pkgIncl, 20)) System.out.printf(Locale.ROOT, "| %s | %d | %s |%n", m.getKey(), m.getValue(), pct(m.getValue(), samples));
		System.out.println();
		System.out.println("## Outermost frame of ours (how vanilla entered our code)");
		System.out.println();
		System.out.println("| outermost our frame | samples | % of all |");
		System.out.println("|---|---|---|");
		for (var m : top(outerOurs, 10)) System.out.printf(Locale.ROOT, "| %s | %d | %s |%n", m.getKey(), m.getValue(), pct(m.getValue(), samples));
		System.out.println();
		System.out.println("## Allocation (jdk.ObjectAllocationSample, weighted estimate)");
		System.out.println();
		System.out.printf(Locale.ROOT, "Total %.0f MB.%n%n", allocTotal / 1048576.0);
		System.out.println("| category | MB | share |");
		System.out.println("|---|---|---|");
		for (var m : top(allocCat, 10)) System.out.printf(Locale.ROOT, "| %s | %.0f | %s |%n", m.getKey(), m.getValue() / 1048576.0, pct(m.getValue(), allocTotal));
		System.out.println();
		System.out.println("| top types | MB |  | top allocating frames of ours | MB |");
		System.out.println("|---|---|---|---|---|");
		var tt = top(allocType, 8); var ts = top(allocOurSite, 8);
		for (int k = 0; k < Math.max(tt.size(), ts.size()); k++)
			System.out.printf(Locale.ROOT, "| %s | %s | | %s | %s |%n", k < tt.size() ? tt.get(k).getKey() : "", k < tt.size() ? String.format(Locale.ROOT, "%.0f", tt.get(k).getValue() / 1048576.0) : "", k < ts.size() ? ts.get(k).getKey() : "", k < ts.size() ? String.format(Locale.ROOT, "%.0f", ts.get(k).getValue() / 1048576.0) : "");
		System.out.println();
		if (!mcEvents.isEmpty()) {
			System.out.println("## Minecraft's own JFR events (JvmProfiler)");
			System.out.println();
			System.out.println("| event | count | total ms | mean ms | max ms |");
			System.out.println("|---|---|---|---|---|");
			for (var m : mcEvents.entrySet()) {
				long[] a = m.getValue();
				if (m.getKey().startsWith("minecraft.Packet") || m.getKey().startsWith("minecraft.NetworkSummary") || m.getKey().startsWith("minecraft.ClientFps")) continue;
				System.out.printf(Locale.ROOT, "| %s | %d | %.0f | %.2f | %.1f |%n", m.getKey(), a[0], a[1] / 1e6, a[0] == 0 ? 0 : a[1] / 1e6 / a[0], a[2] / 1e6);
			}
			if (!tickNs.isEmpty()) {
				long[] s = tickNs.stream().mapToLong(Long::longValue).sorted().toArray();
				System.out.printf(Locale.ROOT, "%nServer tick, 1 s averages (minecraft.ServerTickTime): %d s; median %.1f ms, p95 %.1f ms, max %.1f ms.%n", s.length, s[s.length / 2] / 1e6, s[Math.min(s.length - 1, (int) (s.length * 0.95))] / 1e6, s[s.length - 1] / 1e6);
			}
			System.out.println();
		}
		System.out.printf(Locale.ROOT, "## GC%n%n%d collections, %.0f ms in collections, longest %.0f ms; pauses %.0f ms total, longest %.1f ms.%n", gcCount, gcSumNs / 1e6, gcMaxNs / 1e6, pauseSumNs / 1e6, pauseMaxNs / 1e6);
	}
}
