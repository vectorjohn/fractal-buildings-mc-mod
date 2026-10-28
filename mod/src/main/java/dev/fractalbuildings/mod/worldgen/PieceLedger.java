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
	/** Sentinel in {@link #active}: recordings are per thread ({@link #startLocal}); the cell goes to {@link #LOCAL}'s recording of the calling thread, if any. */
	private static final Recording PER_THREAD = new Recording();
	private static final ThreadLocal<Recording> LOCAL = new ThreadLocal<>();
	private static final java.util.concurrent.atomic.AtomicInteger LOCALS = new java.util.concurrent.atomic.AtomicInteger();

	private PieceLedger() {}

	/** Starts recording; stop with {@link Recording#close}. */
	public static Recording start() {
		Recording r = new Recording();
		active = r;
		return r;
	}

	/**
	 * Starts a recording that only the calling thread's cells go to, so orders can run on parallel threads (ORDERSIM). Close it on the same
	 * thread. A thread without one records nothing; {@link #start()} still gives the one global recording (and replaces this mode while it is open).
	 */
	public static Recording startLocal() {
		Recording r = new Recording();
		r.owner = Thread.currentThread();
		if (LOCAL.get() != null) throw new IllegalStateException("this thread already has a local recording");
		LOCAL.set(r);
		LOCALS.incrementAndGet();
		active = PER_THREAD;
		return r;
	}

	/** The recording the calling thread's cells go to, or null. */
	private static Recording current() {
		Recording r = active;
		return r == PER_THREAD ? LOCAL.get() : r;
	}

	public static void record(int x, int y, int z, int stateId) {
		Recording r = current();
		if (r != null) r.add(x, y, z, stateId);
	}

	/** The road feature's cells (C1): recorded like any cell, and also listed in {@link Recording#roadCells} so a harness that compares district pieces to a reference raster can set them aside. */
	public static void recordRoad(int x, int y, int z, int stateId) {
		Recording r = current();
		if (r != null) {
			r.add(x, y, z, stateId);
			r.roads.add(pack(x, y, z));
		}
	}

	/** Records furniture placed over claimed air. Hook blocks are kept apart from the shell cells ({@link Recording#hooks}), so the shell can be compared with the core raster exactly. */
	public static void override(int x, int y, int z, int stateId) {
		Recording r = current();
		if (r != null) r.replace(x, y, z, stateId);
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
		/** The thread of a {@link #startLocal} recording, else null. */
		private Thread owner;
		private final ConcurrentSkipListMap<Long, int[]> cells = new ConcurrentSkipListMap<>();
		final java.util.concurrent.ConcurrentSkipListSet<Long> roads = new java.util.concurrent.ConcurrentSkipListSet<>();

		/** Keys of the cells the road feature recorded. */
		public java.util.Set<Long> roadCells() { return roads; }
		private final ConcurrentSkipListMap<Long, Integer> hooks = new ConcurrentSkipListMap<>();

		/** The hook blocks (furniture, chests, lights) with their state ids, in key order. */
		public Map<Long, Integer> hooks() { return new TreeMap<>(hooks); }

		private void add(int x, int y, int z, int stateId) {
			cells.merge(pack(x, y, z), new int[] {1, stateId}, (a, b) -> new int[] {a[0] + 1, b[1]});
		}

		/** A hook block (a bed on claimed room air): kept in its own map, the shell cell is not touched. */
		private void replace(int x, int y, int z, int stateId) {
			hooks.put(pack(x, y, z), stateId);
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
			if (owner != null) {
				if (LOCAL.get() == this) {
					LOCAL.remove();
					if (LOCALS.decrementAndGet() == 0 && active == PER_THREAD) active = null;
				}
				return;
			}
			if (active == this) active = null;
		}
	}
}
