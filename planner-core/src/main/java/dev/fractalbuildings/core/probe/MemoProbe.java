package dev.fractalbuildings.core.probe;

/**
 * A {@link TerrainProbe} that asks its source once per column and quantity (floor, water depth, surface), for one thread at a time:
 * a pass that reads the same columns several times (the junction field grades every whole street of a settlement after testing the
 * grid lines' edges and the streets' water) pays for each column once. Open addressing on the packed column, no iteration, so it
 * cannot leak an order into the output.
 */
public final class MemoProbe implements TerrainProbe {
	private final TerrainProbe src;
	private final Table floor = new Table(), water = new Table(), surface = new Table();

	public MemoProbe(TerrainProbe src) { this.src = src; }

	@Override public int floorY(int x, int z) {
		long k = key(x, z);
		int i = floor.slot(k);
		if (!floor.used[i]) floor.put(i, k, src.floorY(x, z));
		return floor.val[floor.slot(k)];
	}

	@Override public int waterDepth(int x, int z) {
		long k = key(x, z);
		int i = water.slot(k);
		if (!water.used[i]) water.put(i, k, src.waterDepth(x, z));
		return water.val[water.slot(k)];
	}

	@Override public int surfaceY(int x, int z) {
		long k = key(x, z);
		int i = surface.slot(k);
		if (!surface.used[i]) surface.put(i, k, src.surfaceY(x, z));
		return surface.val[surface.slot(k)];
	}

	private static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	/** Linear-probing table of long keys to int values, grown at half full. */
	private static final class Table {
		long[] keys = new long[1024];
		int[] val = new int[1024];
		boolean[] used = new boolean[1024];
		int size;

		int slot(long k) {
			int m = keys.length - 1;
			int i = (int) (mix(k) & m);
			while (used[i] && keys[i] != k) i = (i + 1) & m;
			return i;
		}

		void put(int i, long k, int v) {
			used[i] = true;
			keys[i] = k;
			val[i] = v;
			if (++size * 2 > keys.length) grow();
		}

		void grow() {
			long[] ok = keys;
			int[] ov = val;
			boolean[] ou = used;
			keys = new long[ok.length * 2];
			val = new int[ok.length * 2];
			used = new boolean[ok.length * 2];
			for (int j = 0; j < ok.length; j++) {
				if (!ou[j]) continue;
				int i = slot(ok[j]);
				used[i] = true;
				keys[i] = ok[j];
				val[i] = ov[j];
			}
		}

		static long mix(long k) {
			k ^= k >>> 33;
			k *= 0xff51afd7ed558ccdL;
			k ^= k >>> 33;
			return k;
		}
	}
}
