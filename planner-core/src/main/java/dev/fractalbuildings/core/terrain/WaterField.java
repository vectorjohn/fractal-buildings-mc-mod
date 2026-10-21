package dev.fractalbuildings.core.terrain;

/**
 * Distance to water and water components over a window of a coarse lattice. Sample (i, j) is the
 * world column (x0 + i * stride, z0 + j * stride). Distances are 8-connected chamfer distances
 * (10 per straight step, 14 per diagonal), in blocks, rounded down; 0 on water. Components are
 * 4-connected, numbered 1.. in scan order (rows of z, then x); 0 is land. A component of more than
 * {@value #OCEAN_SAMPLES} samples is "ocean", smaller ones are river or lake.
 */
public final class WaterField {
	public static final int OCEAN_SAMPLES = 4096;
	private static final int INF = Integer.MAX_VALUE / 4;

	private final int x0, z0, nx, nz, stride;
	private final int[] dist10;     // chamfer distance in tenths of a stride
	private final int[] label;      // 0 = land
	private final int[] sizes;      // index = label

	private WaterField(int x0, int z0, int nx, int nz, int stride, int[] dist10, int[] label, int[] sizes) {
		this.x0 = x0; this.z0 = z0; this.nx = nx; this.nz = nz; this.stride = stride;
		this.dist10 = dist10; this.label = label; this.sizes = sizes;
	}

	public static WaterField of(WaterSamples src, int x0, int z0, int nx, int nz) {
		if (nx <= 0 || nz <= 0) throw new IllegalArgumentException("window " + nx + "x" + nz);
		int stride = src.stride();
		int n = nx * nz;
		int[] d = new int[n];
		boolean[] w = new boolean[n];
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				boolean wet = src.water(x0 + i * stride, z0 + j * stride);
				w[j * nx + i] = wet;
				d[j * nx + i] = wet ? 0 : INF;
			}
		}
		// two-pass chamfer
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int k = j * nx + i, v = d[k];
				if (i > 0) v = Math.min(v, d[k - 1] + 10);
				if (j > 0) {
					v = Math.min(v, d[k - nx] + 10);
					if (i > 0) v = Math.min(v, d[k - nx - 1] + 14);
					if (i < nx - 1) v = Math.min(v, d[k - nx + 1] + 14);
				}
				d[k] = v;
			}
		}
		for (int j = nz - 1; j >= 0; j--) {
			for (int i = nx - 1; i >= 0; i--) {
				int k = j * nx + i, v = d[k];
				if (i < nx - 1) v = Math.min(v, d[k + 1] + 10);
				if (j < nz - 1) {
					v = Math.min(v, d[k + nx] + 10);
					if (i < nx - 1) v = Math.min(v, d[k + nx + 1] + 14);
					if (i > 0) v = Math.min(v, d[k + nx - 1] + 14);
				}
				d[k] = v;
			}
		}
		// components, iterative flood fill in scan order
		int[] lab = new int[n];
		int[] sz = new int[n + 1];
		int next = 0;
		int[] stack = new int[n];
		for (int s = 0; s < n; s++) {
			if (!w[s] || lab[s] != 0) continue;
			next++;
			int sp = 0, count = 0;
			stack[sp++] = s;
			lab[s] = next;
			while (sp > 0) {
				int k = stack[--sp];
				count++;
				int i = k % nx, j = k / nx;
				if (i > 0 && w[k - 1] && lab[k - 1] == 0) { lab[k - 1] = next; stack[sp++] = k - 1; }
				if (i < nx - 1 && w[k + 1] && lab[k + 1] == 0) { lab[k + 1] = next; stack[sp++] = k + 1; }
				if (j > 0 && w[k - nx] && lab[k - nx] == 0) { lab[k - nx] = next; stack[sp++] = k - nx; }
				if (j < nz - 1 && w[k + nx] && lab[k + nx] == 0) { lab[k + nx] = next; stack[sp++] = k + nx; }
			}
			sz[next] = count;
		}
		return new WaterField(x0, z0, nx, nz, stride, d, lab, java.util.Arrays.copyOf(sz, next + 1));
	}

	public int nx() { return nx; }
	public int nz() { return nz; }
	public int stride() { return stride; }
	public int componentCount() { return sizes.length - 1; }

	private int idx(int i, int j) {
		if (i < 0 || j < 0 || i >= nx || j >= nz) throw new IndexOutOfBoundsException("sample " + i + "," + j);
		return j * nx + i;
	}

	public boolean isWater(int i, int j) { return label[idx(i, j)] != 0; }

	/** Distance in blocks to the nearest water sample (0 on water). With no water in the window: {@link Integer#MAX_VALUE}. */
	public int distance(int i, int j) {
		int v = dist10[idx(i, j)];
		return v >= INF ? Integer.MAX_VALUE : (int) ((long) v * stride / 10);
	}

	/** Component label of a sample, 0 on land. */
	public int component(int i, int j) { return label[idx(i, j)]; }

	public int componentSize(int label) { return sizes[label]; }

	public boolean isOcean(int label) { return label != 0 && sizes[label] > OCEAN_SAMPLES; }

	/** Nearest sample index for a world column (round to nearest, clamped to the window). */
	public int sampleI(int x) { return clamp(Math.floorDiv(x - x0 + stride / 2, stride), nx); }
	public int sampleJ(int z) { return clamp(Math.floorDiv(z - z0 + stride / 2, stride), nz); }

	/** Distance in blocks at a world column (nearest sample). */
	public int distanceAt(int x, int z) { return distance(sampleI(x), sampleJ(z)); }

	private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }
}
