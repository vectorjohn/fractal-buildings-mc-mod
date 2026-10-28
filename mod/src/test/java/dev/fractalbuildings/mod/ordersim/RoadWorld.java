package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/**
 * ORDERSIM S5: the region planner (settlements by the join rule, an intercity road graph) over the recorded river window of world 12345.
 * The window alone (2048 blocks) is too small for the region planner to decide any cell, so it is layered over the coarse world map
 * (finest dump first, as core's test-only {@code JoinWorld} does): terrain and biome come from {@code w12345-river-s8} inside its window
 * and from {@code world-12345-s64} outside, the region window is the world map's.
 */
public final class RoadWorld {
	private RoadWorld() {}

	public static final long SEED = 12345;

	record Layered(List<RecordedProbe> fineFirst) implements TerrainProbe, BiomeProbe {
		RecordedProbe at(int x, int z) {
			for (RecordedProbe r : fineFirst) {
				if (x >= r.x0() && z >= r.z0() && x <= r.x0() + (r.nx() - 1) * r.step() && z <= r.z0() + (r.nz() - 1) * r.step()) return r;
			}
			return fineFirst.get(fineFirst.size() - 1);
		}
		@Override public int floorY(int x, int z) { return at(x, z).floorY(x, z); }
		@Override public int waterDepth(int x, int z) { return at(x, z).waterDepth(x, z); }
		@Override public int surfaceY(int x, int z) { return at(x, z).surfaceY(x, z); }
		@Override public String biome(int x, int z) { return at(x, z).biome(x, z); }
	}

	/** The river window's bounds in chunks: x from -88 to 39, z from -260 to -133 (the dump covers x -1408..632, z -4160..-2120). */
	public static final int CX0 = -88, CX1 = 39, CZ0 = -260, CZ1 = -133;

	/** The probes (layered), and a "site" record that only carries them and the seed to {@link Scenario#city}. */
	public static SimSites site() {
		try {
			RecordedProbe fine = RecordedProbe.read(SimSites.FIXTURES.resolve("w12345-river-s8.json.gz"));
			RecordedProbe world = RecordedProbe.read(SimSites.FIXTURES.resolve("world-12345-s64.json.gz"));
			Layered t = new Layered(List.of(fine, world));
			int s = RegionProbe.STRIDE;
			int x0 = Math.floorDiv(world.x0() + s - 1, s) * s, z0 = Math.floorDiv(world.z0() + s - 1, s) * s;
			int x1 = Math.floorDiv(world.x0() + (world.nx() - 1) * world.step(), s) * s, z1 = Math.floorDiv(world.z0() + (world.nz() - 1) * world.step(), s) * s;
			RegionMap.Window w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
			WorldProbes pr = new WorldProbes(RegionProbe.of(t, t), CoarseProbe.of(t, t, 32), t, t, w, t);
			return new SimSites("w12345-river-s8+world-12345-s64", SEED, 0, 0, "region", 0, 0, fine, pr);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A planner with no workers (inline), as {@link SimSites#planner}. */
	public static WorldPlanner planner(SimSites site) {
		return new WorldPlanner(SEED, WorldConfig.defaults(), site.probes(), null);
	}

	/** An n x n window of chunks. */
	public record Window(int cx, int cz, int n, int roadChunks, int districtChunks, int bothChunks) {}

	/**
	 * The n x n chunk window of the river window with the most chunks where a road and a district both put columns: chunks that hold a
	 * road column (the chunk’s own {@code roadColumns}) and chunks that hold a district column ({@code districtColumns}) scored as
	 * min(road, district) first, then chunks with both, then road chunks. Ties go to the lowest (z, x). Pure plan reads, deterministic.
	 */
	public static Window densestWindow(WorldPlanner planner, int n) {
		int w = CX1 - CX0 + 1, h = CZ1 - CZ0 + 1;
		boolean[] road = new boolean[w * h], dist = new boolean[w * h];
		for (int cz = CZ0; cz <= CZ1; cz++) {
			for (int cx = CX0; cx <= CX1; cx++) {
				boolean[] d = planner.districtColumns(cx, cz);
				boolean anyD = false;
				if (d != null) for (boolean b : d) anyD |= b;
				dist[(cz - CZ0) * w + (cx - CX0)] = anyD;
				road[(cz - CZ0) * w + (cx - CX0)] = !planner.roadColumns(cx, cz).isEmpty();
			}
		}
		Window best = null;
		long bestScore = -1;
		for (int cz = CZ0; cz + n - 1 <= CZ1; cz++) {
			for (int cx = CX0; cx + n - 1 <= CX1; cx++) {
				int r = 0, d = 0, b = 0;
				for (int dz = 0; dz < n; dz++) for (int dx = 0; dx < n; dx++) {
					int i = (cz + dz - CZ0) * w + (cx + dx - CX0);
					if (road[i]) r++;
					if (dist[i]) d++;
					if (road[i] && dist[i]) b++;
				}
				long score = ((long) Math.min(r, d) << 40) | ((long) b << 20) | r;
				if (score > bestScore) { bestScore = score; best = new Window(cx, cz, n, r, d, b); }
			}
		}
		return best;
	}

	/**
	 * Scripted crowns over the road headroom: for up to {@code max} road columns on a chunk line of the inner window (dry, outside district
	 * columns, with a free dry column 2 to 3 blocks over the line, also outside the district and the road), a tree there whose leaves reach the
	 * road column. The road of one chunk and the tree of the other then meet in either order. A candidate counts only when the tree, placed
	 * alone on the bare terrain, puts leaves above the road column's ground. Sorted scan, so the picks follow the plan.
	 */
	public static List<SimFeatures.Call> crowns(WorldPlanner planner, SimSites site, Window win, int max) {
		var terrain = site.probes().exact();
		var biome = site.probes().biome();
		List<SimFeatures.Call> out = new java.util.ArrayList<>();
		java.util.Set<Long> usedChunks = new java.util.HashSet<>();
		java.util.List<int[]> dirs = new java.util.ArrayList<>(); // offsets by distance, then dz, dx
		for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) if (dx * dx + dz * dz >= 4 && dx * dx + dz * dz <= 9) dirs.add(new int[] {dx, dz});
		dirs.sort(java.util.Comparator.<int[]>comparingInt(d -> d[0] * d[0] + d[1] * d[1]).thenComparingInt(d -> d[1]).thenComparingInt(d -> d[0]));
		for (int cz = win.cz(); cz < win.cz() + win.n() && out.size() < max; cz++) {
			for (int cx = win.cx(); cx < win.cx() + win.n() && out.size() < max; cx++) {
				boolean[] district = planner.districtColumns(cx, cz);
				for (var col : planner.roadColumns(cx, cz)) {
					int x = col.x(), z = col.z();
					if (Math.floorDiv(x, 16) != cx || Math.floorDiv(z, 16) != cz || out.size() >= max || usedChunks.contains(ChunkPos.pack(cx, cz))) continue;
					if (district != null && district[(z & 15) * 16 + (x & 15)]) continue;
					if (terrain.surfaceY(x, z) != terrain.floorY(x, z)) continue;
					for (int[] d : dirs) {
						int qx = x + d[0], qz = z + d[1];
						if (Math.floorDiv(qx, 16) == cx && Math.floorDiv(qz, 16) == cz) continue; // the other side of the line
						if (terrain.surfaceY(qx, qz) != terrain.floorY(qx, qz)) continue;
						int qcx = Math.floorDiv(qx, 16), qcz = Math.floorDiv(qz, 16);
						if (planner.roadColumns(qcx, qcz).stream().anyMatch(c -> c.x() == qx && c.z() == qz)) continue;
						boolean[] qd = planner.districtColumns(qcx, qcz);
						if (qd != null && qd[(qz & 15) * 16 + (qx & 15)]) continue;
						SimFeatures.Call hit = null;
						for (String key : new String[] {"fancy_oak", "oak", "dark_oak"}) {
							SimFeatures.Call c = SimFeatures.scripted(key, qx, terrain.floorY(qx, qz), qz, site.seed(), terrain, biome);
							if (c != null && reaches(c, x, z, terrain, biome, site.seed())) { hit = c; break; }
						}
						if (hit != null) { out.add(hit); usedChunks.add(ChunkPos.pack(cx, cz)); break; }
					}
				}
			}
		}
		return out;
	}

	/** Does the call, alone on the bare terrain, put leaves above column (x, z)'s ground? */
	private static boolean reaches(SimFeatures.Call c, int x, int z, TerrainProbe terrain, BiomeProbe biome, long seed) {
		int cx = Math.floorDiv(c.x(), 16), cz = Math.floorDiv(c.z(), 16);
		SimWorld w = new SimWorld(seed, cx - 1, cz - 1, 3, 3, terrain, biome);
		if (!SimFeatures.place(w, null, c, new SimFeatures.Stats())) return false;
		for (int y = terrain.floorY(x, z); y < terrain.floorY(x, z) + 8; y++) if (w.get(x, y, z).is(net.minecraft.tags.BlockTags.LEAVES)) return true;
		return false;
	}
}
