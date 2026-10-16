package dev.fractalbuildings.core.probe;

import dev.fractalbuildings.core.json.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Terrain and biome probe over a recorded {@code fb-region-dump/1} lattice (see NOTES: crop of
 * seed 12345). Heights follow {@link TerrainProbe}'s heightmap convention; lattice floor =
 * surface - water. Between lattice points floor and surface are integer-bilinearly interpolated
 * (water = surface - floor); biome is the nearest lattice point. Columns that need a lattice
 * point outside the grid throw {@link IllegalArgumentException}. Immutable, so thread-safe.
 */
public final class RecordedProbe implements TerrainProbe, BiomeProbe {
	private final int x0, z0, step, nx, nz;
	private final int[] surface, water;
	private final String[] biomeNames;
	private final int[] biome;

	private RecordedProbe(int x0, int z0, int step, int nx, int nz, int[] surface, int[] water, String[] biomeNames, int[] biome) {
		this.x0 = x0; this.z0 = z0; this.step = step; this.nx = nx; this.nz = nz;
		this.surface = surface; this.water = water; this.biomeNames = biomeNames; this.biome = biome;
	}

	public static RecordedProbe read(Path file) throws IOException {
		return parse(Files.readString(file, StandardCharsets.UTF_8));
	}

	@SuppressWarnings("unchecked")
	public static RecordedProbe parse(String json) {
		Object root = JsonReader.parse(json);
		if (!(root instanceof Map<?, ?>)) throw new IllegalArgumentException("region dump: root is not an object");
		Map<String, Object> m = (Map<String, Object>) root;
		if (!"fb-region-dump/1".equals(m.get("format"))) throw new IllegalArgumentException("unsupported region dump format: " + m.get("format"));
		Map<String, Object> g = (Map<String, Object>) m.get("grid");
		int x0 = (int) num(g, "x0"), z0 = (int) num(g, "z0"), step = (int) num(g, "step");
		int nx = (int) num(g, "nx"), nz = (int) num(g, "nz");
		if (step <= 0 || nx <= 0 || nz <= 0) throw new IllegalArgumentException("bad grid");
		int[] surface = ints(m, "surface", nx * nz), water = ints(m, "water", nx * nz), bi = ints(m, "biome", nx * nz);
		List<Object> pal = (List<Object>) m.get("palette");
		String[] names = new String[pal.size()];
		for (int i = 0; i < names.length; i++) {
			String n = (String) pal.get(i);
			names[i] = n.indexOf(':') >= 0 ? n : "minecraft:" + n;
		}
		for (int b : bi) if (b < 0 || b >= names.length) throw new IllegalArgumentException("biome index out of palette: " + b);
		return new RecordedProbe(x0, z0, step, nx, nz, surface, water, names, bi);
	}

	private static long num(Map<String, Object> m, String k) {
		Object v = m.get(k);
		if (!(v instanceof Long l)) throw new IllegalArgumentException("region dump: missing integer '" + k + "'");
		return l;
	}

	@SuppressWarnings("unchecked")
	private static int[] ints(Map<String, Object> m, String k, int n) {
		Object v = m.get(k);
		if (!(v instanceof List<?> l) || l.size() != n) throw new IllegalArgumentException("region dump: '" + k + "' must be an array of " + n);
		int[] out = new int[n];
		for (int i = 0; i < n; i++) out[i] = (int) (long) (Long) l.get(i);
		return out;
	}

	public int x0() { return x0; }
	public int z0() { return z0; }
	public int step() { return step; }
	public int nx() { return nx; }
	public int nz() { return nz; }

	private int col(int x, int z, int i, int j) {
		if (i < 0 || j < 0 || i >= nx || j >= nz) {
			throw new IllegalArgumentException("column (" + x + ", " + z + ") is outside the recorded grid x "
				+ x0 + ".." + (x0 + (nx - 1) * step) + ", z " + z0 + ".." + (z0 + (nz - 1) * step));
		}
		return j * nx + i;
	}

	/** Integer bilinear interpolation of floor (surface - water) and surface; returns {floor, surface}. */
	private int[] interp(int x, int z) {
		int dx = Math.subtractExact(x, x0), dz = Math.subtractExact(z, z0);
		int i = Math.floorDiv(dx, step), j = Math.floorDiv(dz, step);
		int fx = Math.floorMod(dx, step), fz = Math.floorMod(dz, step);
		long fl = 0, su = 0;
		for (int b = 0; b < 4; b++) {
			int ox = b & 1, oz = b >> 1;
			long wgt = (long) (ox == 0 ? step - fx : fx) * (oz == 0 ? step - fz : fz);
			if (wgt == 0) continue;
			int idx = col(x, z, i + ox, j + oz);
			fl += wgt * (surface[idx] - water[idx]);
			su += wgt * surface[idx];
		}
		long area = (long) step * step;
		return new int[] {(int) Math.floorDiv(fl, area), (int) Math.floorDiv(su, area)};
	}

	@Override public int floorY(int x, int z) { return interp(x, z)[0]; }

	@Override public int surfaceY(int x, int z) { return interp(x, z)[1]; }

	@Override public int waterDepth(int x, int z) {
		int[] v = interp(x, z);
		return v[1] - v[0];
	}

	@Override public String biome(int x, int z) {
		int dx = Math.subtractExact(x, x0), dz = Math.subtractExact(z, z0);
		int i = Math.floorDiv(dx + step / 2, step), j = Math.floorDiv(dz + step / 2, step);
		return biomeNames[bi(col(x, z, i, j))];
	}

	private int bi(int idx) { return biome[idx]; }
}
