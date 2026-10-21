package dev.fractalbuildings.core.ir;

import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.Arrays;

/**
 * Ground heights for a rectangle of world columns, sampled from a probe and stored in the scene
 * so the viewer can draw the ground and invariants can check support. {@code floorY} is
 * row-major, z outer: index {@code (z - z0) * nx + (x - x0)}. Heights follow {@link TerrainProbe}.
 * {@code water} (optional, null when absent; district scenes) is the water depth above
 * {@code floorY} per column, same layout, 0 on land.
 */
public final class TerrainPatch implements TerrainProbe {
	private final int x0, z0, nx, nz;
	private final int[] floorY;
	private final int[] water;

	public TerrainPatch(int x0, int z0, int nx, int nz, int[] floorY) { this(x0, z0, nx, nz, floorY, null); }

	public TerrainPatch(int x0, int z0, int nx, int nz, int[] floorY, int[] water) {
		if (nx <= 0 || nz <= 0 || floorY.length != nx * nz) throw new IllegalArgumentException("terrain patch shape");
		if (water != null) {
			if (water.length != nx * nz) throw new IllegalArgumentException("terrain water shape");
			for (int w : water) if (w < 0) throw new IllegalArgumentException("negative water depth " + w);
		}
		this.x0 = x0;
		this.z0 = z0;
		this.nx = nx;
		this.nz = nz;
		this.floorY = floorY.clone();
		this.water = water == null ? null : water.clone();
	}

	public static TerrainPatch sample(TerrainProbe probe, int x0, int z0, int nx, int nz) {
		int[] h = new int[nx * nz];
		for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) h[j * nx + i] = probe.floorY(x0 + i, z0 + j);
		return new TerrainPatch(x0, z0, nx, nz, h);
	}

	/** Like {@link #sample} but also stores the probe's water depths. */
	public static TerrainPatch sampleWithWater(TerrainProbe probe, int x0, int z0, int nx, int nz) {
		int[] h = new int[nx * nz], w = new int[nx * nz];
		for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
			h[j * nx + i] = probe.floorY(x0 + i, z0 + j);
			w[j * nx + i] = probe.waterDepth(x0 + i, z0 + j);
		}
		return new TerrainPatch(x0, z0, nx, nz, h, w);
	}

	public int x0() { return x0; }
	public int z0() { return z0; }
	public int nx() { return nx; }
	public int nz() { return nz; }
	public int[] floorYs() { return floorY.clone(); }

	/** Water depths per column, or null when the patch carries none. */
	public int[] waters() { return water == null ? null : water.clone(); }

	public boolean covers(int x, int z) { return x >= x0 && x < x0 + nx && z >= z0 && z < z0 + nz; }

	@Override
	public int floorY(int x, int z) {
		if (!covers(x, z)) throw new IllegalArgumentException("outside terrain patch: " + x + "," + z);
		return floorY[(z - z0) * nx + (x - x0)];
	}

	@Override
	public int waterDepth(int x, int z) {
		if (!covers(x, z)) throw new IllegalArgumentException("outside terrain patch: " + x + "," + z);
		return water == null ? 0 : water[(z - z0) * nx + (x - x0)];
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TerrainPatch t && t.x0 == x0 && t.z0 == z0 && t.nx == nx && t.nz == nz && Arrays.equals(t.floorY, floorY)
				&& Arrays.equals(t.water, water);
	}

	@Override
	public int hashCode() { return 31 * (31 * (31 * x0 + z0) + Arrays.hashCode(floorY)) + Arrays.hashCode(water); }
}
