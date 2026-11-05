import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;

/**
 * Offline reader for a saved Minecraft world (26.x region format, older layouts too): no server, no game, no lock.
 * Run it through {@code scripts/world-query.sh} (usage there and in {@link #USAGE}). Successor of {@code McaTop.java}
 * (top map) and {@code FloatingSnow.java} (scan floating-snow), which stay for the old commands in plans and logs.
 *
 * Output is deterministic (sorted, never in thread order) and tab-separated where it is a list, so scripts and the
 * world cache (W14, {@code scripts/worlds.sh}) can parse it. A chunk that fails to read (a live server writing it, a
 * truncated file) is counted and reported, never fatal.
 */
public class WorldQuery {
	static final String USAGE = """
		usage: world-query.sh [--dim D] <command> <world> [args]
		  <world>  a save folder (has level.dat), a dimension folder or a region folder
		  --dim D  overworld (default), the_nether, the_end
		commands (their arguments follow <world>):
		  info [--status]                   what the save covers: regions, chunks, block bbox, seed, version
		                                    (--status parses every chunk for its generation status)
		  top X0 Z0 X1 Z1 [--raw]           top-block map (letter + y per column; plants, snow layers, leaves,
		                                    torches skipped unless --raw)
		  block X Y Z                       the block state at a position
		  column X Z [--air]                runs of blocks from the top down (air runs only with --air)
		  find PATTERN [--box X0,Z0,X1,Z1] [--y LO..HI] [--limit N]
		                                    positions whose block matches (x y z state, tab-separated; N default 200)
		  count PATTERN [--box ..] [--y ..] [--props]
		                                    histogram of matching blocks (count, id; --props keeps the state)
		  scan CHECK [--box ..] [--samples N]
		                                    run a named check; exit 1 when it finds anything. Checks:
		                                    floating-snow  snow layers with air under them (ROADCLEAR)
		                                    floating-path  dirt_path with air under it (natural worlds have none)
		  PATTERN: block id glob, comma list (`oak_*,spruce_planks`, `*` = all); with `[` it matches the full
		           state (`*_stairs[*half=top*]`); a `minecraft:` prefix is ignored.
		exit: 0 ok, 1 scan found something, 2 usage, 3 world or chunk not found
		""";

	// ---------------------------------------------------------------- NBT

	static Object tag(DataInputStream d, int t) throws IOException {
		switch (t) {
			case 1: return d.readByte();
			case 2: return d.readShort();
			case 3: return d.readInt();
			case 4: return d.readLong();
			case 5: return d.readFloat();
			case 6: return d.readDouble();
			case 7: { byte[] a = new byte[d.readInt()]; d.readFully(a); return a; }
			case 8: return d.readUTF();
			case 9: { int et = d.readByte(); int n = d.readInt(); List<Object> l = new ArrayList<>(n); for (int i = 0; i < n; i++) l.add(tag(d, et)); return l; }
			case 10: { Map<String, Object> m = new LinkedHashMap<>(); while (true) { int et = d.readByte(); if (et == 0) return m; String k = d.readUTF(); m.put(k, tag(d, et)); } }
			case 11: { int[] a = new int[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readInt(); return a; }
			case 12: { long[] a = new long[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readLong(); return a; }
			default: throw new IOException("bad NBT tag " + t);
		}
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> root(InputStream in) throws IOException {
		DataInputStream d = new DataInputStream(new BufferedInputStream(in));
		int t = d.readByte();
		if (t != 10) throw new IOException("root is not a compound");
		d.readUTF();
		return (Map<String, Object>) tag(d, t);
	}

	// ---------------------------------------------------------------- regions and chunks

	/** A region file, memory-mapped; read-only. */
	static final class Region {
		final int rx, rz;
		final ByteBuffer buf;

		Region(Path f, int rx, int rz) throws IOException {
			this.rx = rx; this.rz = rz;
			try (FileChannel ch = FileChannel.open(f, StandardOpenOption.READ)) {
				buf = ch.size() < 8192 ? ByteBuffer.allocate(0) : ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
			}
		}

		boolean has(int lx, int lz) {
			if (buf.capacity() < 4096) return false;
			return buf.getInt((lx + lz * 32) * 4) != 0;
		}

		long timestamp(int lx, int lz) { return buf.capacity() < 8192 ? 0 : buf.getInt(4096 + (lx + lz * 32) * 4) & 0xffffffffL; }

		Map<String, Object> chunk(int lx, int lz) throws IOException {
			if (!has(lx, lz)) return null;
			int loc = buf.getInt((lx + lz * 32) * 4);
			long off = (long) (loc >>> 8) * 4096;
			if (off == 0 || off + 5 > buf.capacity()) return null;
			int len = buf.getInt((int) off);
			int comp = buf.get((int) off + 4);
			if (len <= 1 || off + 4 + len > buf.capacity()) throw new IOException("chunk runs past the file (oversized .mcc or truncated)");
			byte[] b = new byte[len - 1];
			buf.get((int) off + 5, b);
			InputStream in = new ByteArrayInputStream(b);
			in = switch (comp & 0x7f) {
				case 1 -> new GZIPInputStream(in);
				case 2 -> new InflaterInputStream(in);
				case 3 -> in;
				default -> throw new IOException("compression " + comp);
			};
			return root(in);
		}
	}

	/** One chunk's blocks: full state strings by index (y - minY) * 256 + z * 16 + x, plus its status. */
	static final class Chunk {
		final int cx, cz, minY;
		final String[] states;
		final String status;

		Chunk(int cx, int cz, int minY, String[] states, String status) { this.cx = cx; this.cz = cz; this.minY = minY; this.states = states; this.status = status; }

		int height() { return states.length / 256; }
		String at(int lx, int y, int lz) { int i = y - minY; return i < 0 || i >= height() ? "air" : states[i * 256 + lz * 16 + lx]; }
	}

	static String strip(String id) { return id.startsWith("minecraft:") ? id.substring(10) : id; }
	static String base(String state) { int i = state.indexOf('['); return i < 0 ? state : state.substring(0, i); }
	static boolean isAir(String s) { return s.equals("air") || s.equals("cave_air") || s.equals("void_air"); }

	/** A palette entry as `id[k=v,...]` with sorted properties. 26.x writes {id, properties} and plain ids as {"": id}. */
	@SuppressWarnings("unchecked")
	static String stateName(Object pe) {
		if (pe instanceof String s) return strip(s);
		Map<String, Object> pm = (Map<String, Object>) pe;
		Object name = pm.get("Name") != null ? pm.get("Name") : pm.get("id") != null ? pm.get("id") : pm.get("");
		Object po = pm.get("Properties") != null ? pm.get("Properties") : pm.get("properties");
		String n = strip(String.valueOf(name));
		if (po instanceof Map<?, ?> pr && !pr.isEmpty()) {
			n += new TreeMap<>((Map<String, Object>) pr).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(",", "[", "]"));
		}
		return n;
	}

	@SuppressWarnings("unchecked")
	static Chunk decode(int cx, int cz, Map<String, Object> m) {
		Map<String, Object> lvl = m.containsKey("Level") ? (Map<String, Object>) m.get("Level") : m; // pre-1.18 wrapper
		List<Map<String, Object>> secs = (List<Map<String, Object>>) lvl.getOrDefault("sections", lvl.getOrDefault("Sections", List.of()));
		String status = strip(String.valueOf(lvl.getOrDefault("Status", "?")));
		if (secs.isEmpty()) return new Chunk(cx, cz, 0, new String[0], status);
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (Map<String, Object> s : secs) { int y = ((Number) s.get("Y")).intValue(); lo = Math.min(lo, y); hi = Math.max(hi, y); }
		String[] out = new String[(hi - lo + 1) * 4096];
		Arrays.fill(out, "air");
		for (Map<String, Object> s : secs) {
			int sy = ((Number) s.get("Y")).intValue();
			Map<String, Object> bs = (Map<String, Object>) s.get("block_states");
			if (bs == null) continue;
			List<Object> pal = (List<Object>) bs.get("palette");
			if (pal == null || pal.isEmpty()) continue;
			String[] names = new String[pal.size()];
			for (int i = 0; i < names.length; i++) names[i] = stateName(pal.get(i)).intern();
			long[] data = (long[]) bs.get("data");
			int baseIdx = (sy - lo) * 4096;
			if (data == null) { Arrays.fill(out, baseIdx, baseIdx + 4096, names[0]); continue; }
			int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(names.length - 1));
			int per = 64 / bits;
			for (int i = 0; i < 4096; i++) {
				int v = (int) ((data[i / per] >>> ((i % per) * bits)) & ((1L << bits) - 1));
				out[baseIdx + i] = v < names.length ? names[v] : "?";
			}
		}
		return new Chunk(cx, cz, lo * 16, out, status);
	}

	// ---------------------------------------------------------------- world

	static final class World {
		final Path save, regionDir;
		final Map<Long, Optional<Region>> regions = new HashMap<>();
		final Map<Long, Optional<Chunk>> chunks = new LinkedHashMap<>(64, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<Long, Optional<Chunk>> e) { return size() > 512; }
		};
		final AtomicInteger unreadable = new AtomicInteger();
		volatile String firstError;
		void fail(String what, Exception e) { unreadable.incrementAndGet(); if (firstError == null) firstError = what + ": " + e; }

		World(Path save, Path regionDir) { this.save = save; this.regionDir = regionDir; }

		static long key(int a, int b) { return ((long) b << 32) ^ (a & 0xffffffffL); }

		synchronized Region region(int rx, int rz) {
			return regions.computeIfAbsent(key(rx, rz), k -> {
				Path f = regionDir.resolve("r." + rx + "." + rz + ".mca");
				if (!Files.isRegularFile(f)) return Optional.empty();
				try { return Optional.of(new Region(f, rx, rz)); } catch (IOException e) { fail(f.getFileName().toString(), e); return Optional.empty(); }
			}).orElse(null);
		}

		/** The chunk, or null when it was never generated or can't be read. Cached (LRU) for point and box queries. */
		synchronized Chunk chunk(int cx, int cz) {
			return chunks.computeIfAbsent(key(cx, cz), k -> Optional.ofNullable(load(cx, cz))).orElse(null);
		}

		Chunk load(int cx, int cz) {
			Region r = region(Math.floorDiv(cx, 32), Math.floorDiv(cz, 32));
			if (r == null) return null;
			try {
				Map<String, Object> m = r.chunk(Math.floorMod(cx, 32), Math.floorMod(cz, 32));
				return m == null ? null : decode(cx, cz, m);
			} catch (Exception e) { fail("chunk " + cx + "," + cz, e); return null; }
		}

		/** Region coordinates present, sorted (z, then x). */
		List<int[]> regionList() throws IOException {
			Pattern p = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
			List<int[]> out = new ArrayList<>();
			try (Stream<Path> s = Files.list(regionDir)) {
				for (Path f : (Iterable<Path>) s::iterator) {
					Matcher m = p.matcher(f.getFileName().toString());
					if (m.matches()) out.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
				}
			}
			out.sort(Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
			return out;
		}

		/** Generated chunk coordinates inside the box (null = all), sorted (z, then x). */
		List<int[]> chunkList(int[] box) throws IOException {
			List<int[]> out = new ArrayList<>();
			for (int[] rc : regionList()) {
				Region r = region(rc[0], rc[1]);
				if (r == null) continue;
				for (int lz = 0; lz < 32; lz++) for (int lx = 0; lx < 32; lx++) {
					if (!r.has(lx, lz)) continue;
					int cx = rc[0] * 32 + lx, cz = rc[1] * 32 + lz;
					if (box != null && (cx * 16 + 15 < box[0] || cx * 16 > box[2] || cz * 16 + 15 < box[1] || cz * 16 > box[3])) continue;
					out.add(new int[] {cx, cz});
				}
			}
			out.sort(Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
			return out;
		}

		/** Runs f over every generated chunk in the box in parallel; results in chunk order. Chunks are not cached. */
		<T> List<T> each(int[] box, java.util.function.BiFunction<Chunk, int[], T> f) throws IOException {
			List<int[]> cs = chunkList(box);
			return cs.parallelStream().map(c -> { Chunk ch = load(c[0], c[1]); return ch == null ? null : f.apply(ch, box); })
				.filter(Objects::nonNull).collect(Collectors.toList());
		}
	}

	static World open(String arg, String dim) throws IOException {
		Path p = Path.of(arg).toAbsolutePath().normalize();
		if (!Files.isDirectory(p)) throw new FileNotFoundException("no such world folder: " + p);
		boolean saveRoot = Files.isRegularFile(p.resolve("level.dat"));
		List<Path> tries = new ArrayList<>();
		tries.add(p); // a region folder itself
		tries.add(p.resolve("dimensions/minecraft/" + dim + "/region")); // 26.x save
		if (saveRoot) tries.add(p.resolve(switch (dim) { case "the_nether" -> "DIM-1/region"; case "the_end" -> "DIM1/region"; default -> "region"; })); // older save
		else tries.add(p.resolve("region")); // a dimension folder
		for (Path t : tries) {
			if (!Files.isDirectory(t)) continue;
			boolean mca;
			try (Stream<Path> s = Files.list(t)) { mca = s.anyMatch(f -> f.getFileName().toString().endsWith(".mca")); }
			if (!mca) continue;
			Path save = t; // the save root: the nearest folder up holding level.dat, if any
			for (Path q = t; q != null; q = q.getParent()) if (Files.isRegularFile(q.resolve("level.dat"))) { save = q; break; }
			return new World(save, t);
		}
		throw new FileNotFoundException("no region files for dimension " + dim + " under " + p);
	}

	// ---------------------------------------------------------------- argument helpers

	static int[] parseBox(String s) {
		String[] a = s.split(",");
		if (a.length != 4) throw new IllegalArgumentException("--box wants X0,Z0,X1,Z1, got " + s);
		int x0 = Integer.parseInt(a[0].trim()), z0 = Integer.parseInt(a[1].trim()), x1 = Integer.parseInt(a[2].trim()), z1 = Integer.parseInt(a[3].trim());
		return new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};
	}

	static int[] parseYRange(String s) {
		String[] a = s.split("\\.\\.", -1);
		if (a.length != 2) throw new IllegalArgumentException("--y wants LO..HI, got " + s);
		return new int[] {a[0].isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(a[0]), a[1].isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(a[1])};
	}

	/** Glob list matcher on the base id, or on the full state when a glob has '['. */
	static java.util.function.Predicate<String> matcher(String pattern) {
		List<java.util.function.Predicate<String>> ps = new ArrayList<>();
		for (String g : pattern.split(",")) {
			g = strip(g.trim());
			if (g.isEmpty()) continue;
			boolean full = g.contains("[");
			StringBuilder re = new StringBuilder();
			for (char c : g.toCharArray()) re.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
			Pattern p = Pattern.compile(re.toString());
			ps.add(full ? s -> p.matcher(s).matches() : s -> p.matcher(base(s)).matches());
		}
		if (ps.isEmpty()) throw new IllegalArgumentException("empty PATTERN");
		Map<String, Boolean> memo = new java.util.concurrent.ConcurrentHashMap<>();
		return s -> memo.computeIfAbsent(s, k -> ps.stream().anyMatch(p -> p.test(k)));
	}

	static boolean inBox(int[] box, int x, int z) { return box == null || (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]); }

	// ---------------------------------------------------------------- commands

	static void info(World w, boolean status) throws IOException {
		System.out.println("world\t" + w.save);
		System.out.println("regions\t" + w.regionDir);
		Path lvl = w.save.resolve("level.dat");
		if (Files.isRegularFile(lvl)) {
			try (InputStream in = new GZIPInputStream(Files.newInputStream(lvl))) {
				Map<String, Object> m = root(in);
				@SuppressWarnings("unchecked") Map<String, Object> d = m.get("Data") instanceof Map<?, ?> dm ? (Map<String, Object>) dm : m;
				Object name = d.get("LevelName");
				if (name != null) System.out.println("level_name\t" + name);
				Object seed = findKey(d, "seed");
				if (seed == null) seed = findKey(d, "RandomSeed");
				Path wgs = w.save.resolve("data/minecraft/world_gen_settings.dat"); // 26.x keeps the seed here
				if (seed == null && Files.isRegularFile(wgs)) try (InputStream gin = new GZIPInputStream(Files.newInputStream(wgs))) { seed = findKey(root(gin), "seed"); }
				System.out.println("seed\t" + (seed == null ? "? (not in level.dat)" : seed));
				Object dv = d.get("DataVersion");
				if (dv != null) System.out.println("data_version\t" + dv);
				Object ver = d.get("Version") instanceof Map<?, ?> vm ? vm.get("Name") : null;
				if (ver != null) System.out.println("mc_version\t" + ver);
			} catch (Exception e) { System.out.println("level_dat\tunreadable (" + e.getMessage() + ")"); }
			System.out.println("level_dat_modified\t" + Files.getLastModifiedTime(lvl));
		}
		// the world cache's own stamp (W14 writes it next to level.dat: seed, area, commit, created)
		Path stamp = w.save.resolve("fractal-world.properties");
		if (Files.isRegularFile(stamp)) for (String line : Files.readAllLines(stamp)) {
			int i = line.indexOf('=');
			if (i > 0 && !line.startsWith("#")) System.out.println("stamp." + line.substring(0, i).trim() + "\t" + line.substring(i + 1).trim());
		}
		List<int[]> regs = w.regionList();
		long bytes = 0;
		for (int[] r : regs) bytes += Files.size(w.regionDir.resolve("r." + r[0] + "." + r[1] + ".mca"));
		List<int[]> cs = w.chunkList(null);
		System.out.println("region_files\t" + regs.size());
		System.out.println("region_bytes\t" + bytes);
		System.out.println("chunks\t" + cs.size());
		if (!cs.isEmpty()) {
			int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (int[] c : cs) { x0 = Math.min(x0, c[0]); z0 = Math.min(z0, c[1]); x1 = Math.max(x1, c[0]); z1 = Math.max(z1, c[1]); }
			System.out.println("chunk_bbox\t" + x0 + "," + z0 + "," + x1 + "," + z1);
			System.out.println("block_bbox\t" + x0 * 16 + "," + z0 * 16 + "," + (x1 * 16 + 15) + "," + (z1 * 16 + 15));
		}
		if (status) {
			TreeMap<String, Integer> hist = new TreeMap<>();
			for (String s : w.each(null, (c, b) -> c.status)) hist.merge(s, 1, Integer::sum);
			hist.forEach((k, v) -> System.out.println("status." + k + "\t" + v));
		}
	}

	@SuppressWarnings("unchecked")
	static Object findKey(Object o, String key) {
		if (o instanceof Map<?, ?> m) {
			if (m.containsKey(key)) return m.get(key);
			for (Object v : m.values()) { Object r = findKey(v, key); if (r != null) return r; }
		}
		return null;
	}

	static final Set<String> TOP_SKIP_EXACT = Set.of("short_grass", "tall_grass", "fern", "large_fern", "snow", "vine", "dead_bush", "short_dry_grass", "tall_dry_grass");
	static final String[] TOP_SKIP_PART = {"leaves", "flower", "tulip", "poppy", "dandelion", "daisy", "bluet", "allium", "orchid", "bush", "cornflower", "lily_of", "petals", "leaf_litter", "sweet_berry", "torch", "lantern"};

	static boolean topSkip(String state) {
		String n = base(state);
		if (isAir(n) || TOP_SKIP_EXACT.contains(n)) return true;
		for (String p : TOP_SKIP_PART) if (n.contains(p)) return true;
		return false;
	}

	/** Short form for the top map legend: id plus type/half, as McaTop printed it. */
	static String legendName(String state) {
		String n = base(state);
		Matcher m = Pattern.compile("(?:^|[\\[,])(type|half)=([^,\\]]+)").matcher(state.substring(n.length()));
		StringBuilder sb = new StringBuilder(n);
		List<String[]> kv = new ArrayList<>();
		while (m.find()) kv.add(new String[] {m.group(1), m.group(2)});
		kv.sort(Comparator.comparing(a -> a[0].equals("type") ? 0 : 1));
		for (String[] a : kv) sb.append('[').append(a[1]).append(']');
		return sb.toString();
	}

	static int top(World w, int x0, int z0, int x1, int z1, boolean raw) {
		TreeMap<String, Character> legend = new TreeMap<>();
		Map<String, Character> assigned = new HashMap<>();
		String codes = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		int missing = 0;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			StringBuilder sb = new StringBuilder(String.format("%6d ", z));
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				Chunk c = w.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
				if (c == null) { sb.append(" ???"); missing++; continue; }
				int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
				int y = c.minY + c.height() - 1;
				while (y >= c.minY && (raw ? isAir(c.at(lx, y, lz)) : topSkip(c.at(lx, y, lz)))) y--;
				String n = legendName(c.at(lx, y, lz));
				Character ch = assigned.get(n);
				if (ch == null) { ch = codes.charAt(assigned.size() % codes.length()); assigned.put(n, ch); legend.put(n, ch); }
				sb.append(ch).append(String.format("%03d", y));
			}
			System.out.println(sb);
		}
		for (var e : legend.entrySet()) System.out.println("  " + e.getValue() + " = " + e.getKey());
		if (missing > 0) System.out.println("  ??? = chunk not generated or unreadable (" + missing + " columns)");
		return 0;
	}

	static int block(World w, int x, int y, int z) {
		Chunk c = w.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
		if (c == null) { System.err.println("world-query: chunk " + Math.floorDiv(x, 16) + "," + Math.floorDiv(z, 16) + " not generated or unreadable"); return 3; }
		System.out.println(x + "\t" + y + "\t" + z + "\t" + c.at(Math.floorMod(x, 16), y, Math.floorMod(z, 16)));
		return 0;
	}

	static int column(World w, int x, int z, boolean air) {
		Chunk c = w.chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
		if (c == null) { System.err.println("world-query: chunk " + Math.floorDiv(x, 16) + "," + Math.floorDiv(z, 16) + " not generated or unreadable"); return 3; }
		int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
		int top = c.minY + c.height() - 1;
		int y = top;
		while (y >= c.minY) {
			String s = c.at(lx, y, lz);
			int y1 = y;
			while (y - 1 >= c.minY && c.at(lx, y - 1, lz).equals(s)) y--;
			if (air || !isAir(s)) System.out.println(y1 == y ? y + "\t\t" + s : y1 + "\t" + y + "\t" + s);
			y--;
		}
		return 0;
	}

	record Hit(int x, int y, int z, String state) {}

	static int find(World w, String pattern, int[] box, int[] yr, int limit) throws IOException {
		var m = matcher(pattern);
		List<List<Hit>> per = w.each(box, (c, b) -> {
			List<Hit> hs = new ArrayList<>();
			int ylo = Math.max(c.minY, yr[0]), yhi = Math.min(c.minY + c.height() - 1, yr[1]);
			for (int y = ylo; y <= yhi; y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
				int x = c.cx * 16 + lx, z = c.cz * 16 + lz;
				if (!inBox(b, x, z)) continue;
				String s = c.at(lx, y, lz);
				if (m.test(s)) hs.add(new Hit(x, y, z, s));
			}
			return hs;
		});
		int total = 0, shown = 0;
		for (List<Hit> hs : per) for (Hit h : hs) { total++; if (shown < limit) { System.out.println(h.x + "\t" + h.y + "\t" + h.z + "\t" + h.state); shown++; } }
		System.out.println("# matches " + total + (total > shown ? " (first " + shown + " shown; --limit N)" : ""));
		return 0;
	}

	static int count(World w, String pattern, int[] box, int[] yr, boolean props) throws IOException {
		var m = matcher(pattern);
		List<Map<String, Long>> per = w.each(box, (c, b) -> {
			Map<String, Long> h = new HashMap<>();
			int ylo = Math.max(c.minY, yr[0]), yhi = Math.min(c.minY + c.height() - 1, yr[1]);
			for (int y = ylo; y <= yhi; y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
				if (!inBox(b, c.cx * 16 + lx, c.cz * 16 + lz)) continue;
				String s = c.at(lx, y, lz);
				if (m.test(s)) h.merge(props ? s : base(s), 1L, Long::sum);
			}
			return h;
		});
		Map<String, Long> all = new TreeMap<>();
		for (Map<String, Long> h : per) h.forEach((k, v) -> all.merge(k, v, Long::sum));
		all.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.forEach(e -> System.out.println(e.getValue() + "\t" + e.getKey()));
		System.out.println("# total " + all.values().stream().mapToLong(Long::longValue).sum() + " in " + per.size() + " chunks");
		return 0;
	}

	static boolean roadBlock(String state) {
		String n = base(state);
		return n.contains("planks") || n.equals("gravel") || n.equals("dirt_path") || n.endsWith("_slab") && n.contains("plank");
	}

	/** Blocks of kind `what` with air directly under them; histogram by air depth and what is under the air. */
	static int scanFloating(World w, int[] box, int samples, java.util.function.Predicate<String> what, String label) throws IOException {
		record F(int x, int y, int z, int depth, String below, String state) {}
		List<List<F>> per = w.each(box, (c, b) -> {
			List<F> fs = new ArrayList<>();
			for (int y = c.minY + 1; y < c.minY + c.height(); y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
				String s = c.at(lx, y, lz);
				if (!what.test(s) || !isAir(c.at(lx, y - 1, lz))) continue;
				int x = c.cx * 16 + lx, z = c.cz * 16 + lz;
				if (!inBox(b, x, z)) continue;
				int d = 0;
				while (y - 1 - d >= c.minY && isAir(c.at(lx, y - 1 - d, lz))) d++;
				String below = y - 1 - d >= c.minY ? base(c.at(lx, y - 1 - d, lz)) : "void";
				fs.add(new F(x, y, z, d, below, s));
			}
			return fs;
		});
		TreeMap<String, Integer> hist = new TreeMap<>();
		int tot = 0, chunks = 0;
		List<F> road = new ArrayList<>(), other = new ArrayList<>();
		for (List<F> fs : per) {
			if (!fs.isEmpty()) chunks++;
			for (F f : fs) {
				tot++;
				boolean overRoad = roadBlock(f.below);
				hist.merge("air-run " + (f.depth > 8 ? ">8" : String.valueOf(f.depth)) + " over " + (overRoad ? "ROADBLOCK" : "other"), 1, Integer::sum);
				(overRoad ? road : other).add(f);
			}
		}
		System.out.println("# scanned chunks " + per.size() + "; " + label + " " + tot + " in " + chunks + " chunks");
		hist.forEach((k, v) -> System.out.println(k + "\t" + v));
		int shown = 0;
		for (List<F> l : List.of(road, other)) for (F f : l) {
			if (shown++ >= samples) break;
			System.out.println(f.x + "\t" + f.y + "\t" + f.z + "\t" + f.state + "\tair " + f.depth + " over " + f.below);
		}
		return tot > 0 ? 1 : 0;
	}

	static int scan(World w, String check, int[] box, int samples) throws IOException {
		return switch (check) {
			case "floating-snow" -> scanFloating(w, box, samples, s -> base(s).equals("snow"), "floating snow layers");
			case "floating-path" -> scanFloating(w, box, samples, s -> base(s).equals("dirt_path"), "floating dirt_path blocks");
			default -> throw new IllegalArgumentException("unknown check " + check + " (floating-snow, floating-path)");
		};
	}

	// ---------------------------------------------------------------- main

	public static void main(String[] argv) {
		List<String> a = new ArrayList<>(Arrays.asList(argv));
		String dim = "overworld";
		Map<String, String> opt = new HashMap<>();
		Set<String> flags = new HashSet<>();
		List<String> pos = new ArrayList<>();
		Set<String> valued = Set.of("--dim", "--box", "--y", "--limit", "--samples");
		Set<String> known = Set.of("--status", "--raw", "--air", "--props");
		for (int i = 0; i < a.size(); i++) {
			String s = a.get(i);
			if (s.equals("-h") || s.equals("--help")) { System.out.print(USAGE); System.exit(0); }
			if (valued.contains(s)) {
				if (i + 1 >= a.size()) { System.err.println("world-query: " + s + " needs a value"); System.exit(2); }
				opt.put(s, a.get(++i));
			} else if (known.contains(s)) flags.add(s);
			else if (s.startsWith("--")) { System.err.println("world-query: unknown option " + s + "\n" + USAGE); System.exit(2); }
			else pos.add(s);
		}
		if (opt.containsKey("--dim")) dim = opt.get("--dim");
		if (pos.size() < 2) { System.err.print(USAGE); System.exit(2); }
		String cmd = pos.get(0);
		int rc;
		try {
			World w = open(pos.get(1), dim);
			List<String> p = pos.subList(2, pos.size());
			int[] box = opt.containsKey("--box") ? parseBox(opt.get("--box")) : null;
			int[] yr = opt.containsKey("--y") ? parseYRange(opt.get("--y")) : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE};
			rc = switch (cmd) {
				case "info" -> { need(p, 0, cmd); info(w, flags.contains("--status")); yield 0; }
				case "top" -> { need(p, 4, cmd); yield top(w, i(p, 0), i(p, 1), i(p, 2), i(p, 3), flags.contains("--raw")); }
				case "block" -> { need(p, 3, cmd); yield block(w, i(p, 0), i(p, 1), i(p, 2)); }
				case "column" -> { need(p, 2, cmd); yield column(w, i(p, 0), i(p, 1), flags.contains("--air")); }
				case "find" -> { need(p, 1, cmd); yield find(w, p.get(0), box, yr, Integer.parseInt(opt.getOrDefault("--limit", "200"))); }
				case "count" -> { need(p, 1, cmd); yield count(w, p.get(0), box, yr, flags.contains("--props")); }
				case "scan" -> { need(p, 1, cmd); yield scan(w, p.get(0), box, Integer.parseInt(opt.getOrDefault("--samples", "40"))); }
				default -> throw new IllegalArgumentException("unknown command " + cmd);
			};
			if (w.unreadable.get() > 0) System.err.println("world-query: " + w.unreadable.get() + " chunk(s) or region file(s) could not be read (a server writing the save? a truncated file?); first: " + w.firstError);
		} catch (FileNotFoundException e) {
			System.err.println("world-query: " + e.getMessage());
			rc = 3;
		} catch (IllegalArgumentException e) {
			System.err.println("world-query: " + e.getMessage() + "\n" + USAGE);
			rc = 2;
		} catch (IOException e) {
			System.err.println("world-query: " + e);
			rc = 3;
		}
		System.out.flush();
		System.exit(rc);
	}

	static void need(List<String> p, int n, String cmd) { if (p.size() != n) throw new IllegalArgumentException(cmd + " wants " + n + " argument(s) after the world, got " + p.size()); }
	static int i(List<String> p, int k) { return Integer.parseInt(p.get(k)); }
}
