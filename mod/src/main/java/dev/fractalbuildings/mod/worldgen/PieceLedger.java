package dev.fractalbuildings.mod.worldgen;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Test hook: what the pieces claimed. Off by default (a null field, one volatile read per cell). While a {@link Recording} is installed,
 * every cell a piece's raster hands out is recorded with its block state id: solid blocks and also air (a claimed clear counts even when
 * the world already was air there, plan B1 / B2's digest "writes plus claimed air"). A cell recorded twice means two pieces (or one piece
 * twice) own it: {@link Recording#duplicates}. Digests depend only on the recorded cells, never on the order they arrived.
 */
public final class PieceLedger {
	private static volatile Recording active;

	private PieceLedger() {}

	/** Starts recording; stop with {@link Recording#close}. */
	public static Recording start() {
		Recording r = new Recording();
		active = r;
		return r;
	}

	static void record(int x, int y, int z, int stateId) {
		Recording r = active;
		if (r != null) r.add(x, y, z, stateId);
	}

	/** SHA-256 hex of a (key, state) map in key order: the digest of cells merged from several recordings. */
	public static String digest(java.util.SortedMap<Long, Integer> states) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			ByteBuffer buf = ByteBuffer.allocate(12);
			for (var e : states.entrySet()) {
				buf.clear();
				buf.putLong(e.getKey()).putInt(e.getValue());
				md.update(buf.array(), 0, 12);
			}
			return java.util.HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static long pack(int x, int y, int z) {
		return ((long) (x + (1 << 25)) << 38) | ((long) (z + (1 << 25)) << 12) | (y + 2048);
	}

	public static int x(long k) { return (int) (k >>> 38) - (1 << 25); }

	public static int z(long k) { return (int) ((k >>> 12) & 0x3FFFFFFL) - (1 << 25); }

	public static int y(long k) { return (int) (k & 0xFFF) - 2048; }

	public static final class Recording implements AutoCloseable {
		private final ConcurrentSkipListMap<Long, int[]> cells = new ConcurrentSkipListMap<>();

		private void add(int x, int y, int z, int stateId) {
			cells.merge(pack(x, y, z), new int[] {1, stateId}, (a, b) -> new int[] {a[0] + 1, b[1]});
		}

		/** Cells in (x, z, y) key order with their state ids. */
		public Map<Long, Integer> states() {
			TreeMap<Long, Integer> out = new TreeMap<>();
			cells.forEach((k, v) -> out.put(k, v[1]));
			return out;
		}

		public int size() { return cells.size(); }

		/** Keys recorded more than once (two owners of one cell). */
		public java.util.List<Long> duplicates() {
			java.util.ArrayList<Long> out = new java.util.ArrayList<>();
			cells.forEach((k, v) -> { if (v[0] > 1) out.add(k); });
			return out;
		}

		/** SHA-256 hex of the sorted (key, state) list of the whole recording. */
		public String digest() { return digest(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE); }

		/** SHA-256 hex of the cells inside the chunk. */
		public String chunkDigest(int chunkX, int chunkZ) {
			return digest(chunkX * 16, chunkX * 16 + 15, chunkZ * 16, chunkZ * 16 + 15);
		}

		private String digest(int x0, int x1, int z0, int z1) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				ByteBuffer buf = ByteBuffer.allocate(12);
				for (var e : cells.entrySet()) {
					long k = e.getKey();
					if (x(k) < x0 || x(k) > x1 || z(k) < z0 || z(k) > z1) continue;
					buf.clear();
					buf.putLong(k).putInt(e.getValue()[1]);
					md.update(buf.array(), 0, 12);
				}
				return java.util.HexFormat.of().formatHex(md.digest());
			} catch (java.security.NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override public void close() {
			if (active == this) active = null;
		}
	}
}
