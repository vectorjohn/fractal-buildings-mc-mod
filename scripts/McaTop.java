import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Top-block map of a saved world, read straight from its region files (no server): {@code scripts/dev java scripts/McaTop.java
 * REGION_DIR x0 z0 x1 z1}, REGION_DIR e.g. {@code <world>/dimensions/minecraft/overworld/region}. Prints one row per z: for each column
 * a letter (legend at the end) and the y of the topmost block that is not air, a plant, leaves, snow layer, torch or lantern. For
 * checking what a playtest world actually holds at a reported spot (ROADJOIN 2026-10-27). Handles the 26.x chunk NBT: palette
 * entries {@code {id, properties}}, and plain block ids wrapped as {@code {"": id}} in mixed lists.
 */
public class McaTop {
	static Map<String, Object> readChunk(Path dir, int cx, int cz) throws IOException {
		Path f = dir.resolve("r." + Math.floorDiv(cx, 32) + "." + Math.floorDiv(cz, 32) + ".mca");
		if (!Files.exists(f)) return null;
		try (RandomAccessFile r = new RandomAccessFile(f.toFile(), "r")) {
			int idx = (Math.floorMod(cx, 32) + Math.floorMod(cz, 32) * 32) * 4;
			r.seek(idx);
			int loc = r.readInt();
			int off = (loc >>> 8) * 4096;
			if (off == 0) return null;
			r.seek(off);
			int len = r.readInt();
			int comp = r.readByte();
			byte[] b = new byte[len - 1];
			r.readFully(b);
			InputStream in = comp == 2 ? new InflaterInputStream(new ByteArrayInputStream(b)) : comp == 1 ? new GZIPInputStream(new ByteArrayInputStream(b)) : new ByteArrayInputStream(b);
			DataInputStream d = new DataInputStream(new BufferedInputStream(in));
			int t = d.readByte();
			d.readUTF();
			@SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) tag(d, t);
			return m;
		}
	}

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
			case 9: { int et = d.readByte(); int n = d.readInt(); List<Object> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add(tag(d, et)); return l; }
			case 10: { Map<String, Object> m = new LinkedHashMap<>(); while (true) { int et = d.readByte(); if (et == 0) return m; String k = d.readUTF(); m.put(k, tag(d, et)); } }
			case 11: { int[] a = new int[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readInt(); return a; }
			case 12: { long[] a = new long[d.readInt()]; for (int i = 0; i < a.length; i++) a[i] = d.readLong(); return a; }
			default: throw new IOException("tag " + t);
		}
	}

	static final Map<Long, String[]> CACHE = new HashMap<>();

	/** Block names of a chunk, index (y - minY) * 256 + z * 16 + x; minY from sections. */
	static String[] blocks(Path dir, int cx, int cz, int[] minY) throws IOException {
		Map<String, Object> m = readChunk(dir, cx, cz);
		if (m == null) return null;
		@SuppressWarnings("unchecked") List<Map<String, Object>> secs = (List<Map<String, Object>>) m.get("sections");
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (Map<String, Object> s : secs) { int y = (Byte) s.get("Y"); lo = Math.min(lo, y); hi = Math.max(hi, y); }
		String[] out = new String[(hi - lo + 1) * 4096];
		Arrays.fill(out, "air");
		for (Map<String, Object> s : secs) {
			int sy = (Byte) s.get("Y");
			@SuppressWarnings("unchecked") Map<String, Object> bs = (Map<String, Object>) s.get("block_states");
			if (bs == null) continue;
			@SuppressWarnings("unchecked") List<Object> pal = (List<Object>) bs.get("palette");
			String[] names = new String[pal.size()];
			for (int i = 0; i < names.length; i++) {
				Object pe = pal.get(i);
				if (pe instanceof String ps) { names[i] = ps.replace("minecraft:", ""); continue; }
				@SuppressWarnings("unchecked") Map<String, Object> pm = (Map<String, Object>) pe;
				if (pm.get("Name") == null && pm.get("") instanceof String es) { names[i] = es.replace("minecraft:", ""); continue; }
				if (pm.get("Name") == null && pm.get("id") != null) { pm.put("Name", pm.get("id")); pm.put("Properties", pm.get("properties")); }
				String n = ((String) pm.get("Name")).replace("minecraft:", "");
				Object po = pm.get("Properties");
				@SuppressWarnings("unchecked") Map<String, Object> pr = po instanceof Map ? (Map<String, Object>) po : null;
				if (pr != null && pr.containsKey("type")) n += "[" + pr.get("type") + "]";
				if (pr != null && pr.containsKey("half")) n += "[" + pr.get("half") + "]";
				names[i] = n;
			}
			long[] data = (long[]) bs.get("data");
			int base = (sy - lo) * 4096;
			if (data == null) { for (int i = 0; i < 4096; i++) out[base + i] = names[0]; continue; }
			int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(names.length - 1));
			int per = 64 / bits;
			for (int i = 0; i < 4096; i++) {
				long v = (data[i / per] >>> ((i % per) * bits)) & ((1L << bits) - 1);
				out[base + i] = names[(int) v];
			}
		}
		minY[0] = lo * 16;
		return out;
	}

	static boolean skip(String n) {
		return n.equals("air") || n.equals("cave_air") || n.contains("leaves") || n.equals("short_grass") || n.equals("tall_grass") || n.equals("fern") || n.equals("large_fern")
			|| n.equals("snow") || n.contains("flower") || n.contains("tulip") || n.contains("poppy") || n.contains("dandelion") || n.contains("daisy") || n.contains("bluet") || n.contains("allium")
			|| n.contains("orchid") || n.contains("bush") || n.contains("cornflower") || n.contains("lily_of") || n.equals("vine") || n.contains("petals") || n.contains("leaf_litter") || n.contains("dead_bush") || n.contains("sweet_berry") || n.contains("torch") || n.contains("lantern");
	}

	public static void main(String[] a) throws Exception {
		Path dir = Path.of(a[0]);
		int x0 = Integer.parseInt(a[1]), z0 = Integer.parseInt(a[2]), x1 = Integer.parseInt(a[3]), z1 = Integer.parseInt(a[4]);
		boolean names = a.length > 5 && a[5].equals("names");
		Map<Long, String[]> chunks = new HashMap<>();
		Map<Long, Integer> mins = new HashMap<>();
		TreeMap<String, Character> legend = new TreeMap<>();
		String codes = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		StringBuilder head = new StringBuilder("       ");
		for (int z = z0; z <= z1; z++) {
			StringBuilder sb = new StringBuilder(String.format("%6d ", z));
			for (int x = x0; x <= x1; x++) {
				int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
				long k = ((long) cz << 32) ^ (cx & 0xffffffffL);
				if (!chunks.containsKey(k)) { int[] mn = {0}; chunks.put(k, blocks(dir, cx, cz, mn)); mins.put(k, mn[0]); }
				String[] bl = chunks.get(k);
				if (bl == null) { sb.append(" ???"); continue; }
				int top = bl.length / 256 - 1, mn = mins.get(k);
				int lx = Math.floorMod(x, 16), lz = Math.floorMod(z, 16);
				int y = top;
				while (y >= 0 && skip(bl[y * 256 + lz * 16 + lx])) y--;
				String n = bl[y * 256 + lz * 16 + lx];
				Character c = legend.get(n);
				if (c == null) { c = codes.charAt(legend.size() % codes.length()); legend.put(n, c); }
				sb.append(c).append(String.format("%03d", y + mn));
			}
			System.out.println(sb);
		}
		for (var e : legend.entrySet()) System.out.println("  " + e.getValue() + " = " + e.getKey());
	}
}
