package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 6.15 ROADJOIN test harness (docs/plans/roadjoin.md): a {@link WorldPlanner} on layered recorded dumps (the finest dump covering a
 * column answers), and {@link #joins} measures, for each approach of a settlement, how its drawn columns meet the settlement's streets.
 *
 * <p>Fixtures: {@code pt1709-world-s64} (John's playtest world 1709797029209580947, the 64-block region map: region decisions are exact,
 * since the region layer reads lattice columns only) under {@code pt1709-join-s2} (2-block crop x 3136..3776, z 1344..1920: hamlet s3_1 and
 * the metropolis s1_2's two north-east exits). On the crop the planner reproduces the playtest world block for block at the hamlet join
 * (checked against the saved world's region files, 2026-10-27).
 */
final class JoinWorld {
	private JoinWorld() {}

	static final long PT_SEED = 1709797029209580947L;

	/** The finest of several dumps that covers a column (the last one answers everywhere else). */
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

	/** The probes of dumps given finest first; the region window is the last (coarsest) dump's, as the CLI computes it. */
	static WorldProbes probes(List<RecordedProbe> fineFirst) {
		Layered t = new Layered(fineFirst);
		RecordedProbe world = fineFirst.get(fineFirst.size() - 1);
		int s = RegionProbe.STRIDE;
		int x0 = Math.floorDiv(world.x0() + s - 1, s) * s, z0 = Math.floorDiv(world.z0() + s - 1, s) * s;
		int x1 = Math.floorDiv(world.x0() + (world.nx() - 1) * world.step(), s) * s, z1 = Math.floorDiv(world.z0() + (world.nz() - 1) * world.step(), s) * s;
		RegionMap.Window w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		return new WorldProbes(RegionProbe.of(t, t), CoarseProbe.of(t, t, 32), t, t, w, t);
	}

	static List<RecordedProbe> read(Path dir, String... namesFineFirst) {
		try {
			List<RecordedProbe> l = new ArrayList<>();
			for (String n : namesFineFirst) l.add(RecordedProbe.read(dir.resolve(n + ".json.gz")));
			return l;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The playtest world on the committed fixtures. */
	static WorldPlanner playtest() { return playtest(WorldPlanner.Caches.DEFAULT); }

	static WorldPlanner playtest(WorldPlanner.Caches caps) {
		return new WorldPlanner(PT_SEED, WorldConfig.defaults(), probes(read(GateWorld.FIXTURES, "pt1709-join-s2", "pt1709-world-s64")), null, caps);
	}

	static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	/** Street ('S'), district deck ('B') and plaza ('P') surface tops of the district tiles of the given chunks and their neighbours: {kind, top block y}. */
	static TreeMap<Long, int[]> districtTops(WorldPlanner w, java.util.Set<Long> chunks) {
		TreeMap<Long, int[]> out = new TreeMap<>();
		TreeSet<Long> tiles = new TreeSet<>();
		for (long ck : chunks) {
			int cx = (int) (ck & 0xffffffffL), cz = (int) (ck >> 32);
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
				var id = w.grid().tileOf((cx + dx) * 16 + 8, (cz + dz) * 16 + 8);
				if (!tiles.add(key(id.ix(), id.iz()))) continue;
				for (GroundPiece g : w.ground(id.ix(), id.iz())) {
					for (Box b : g.boxes()) {
						char c = b.role().isRoad() ? 'S' : b.role() == Role.DECK ? 'B' : b.role() == Role.PAVING ? 'P' : 0;
						if (c == 0 || (c == 'B' && b.box().sy() > 1)) continue; // piers are deck boxes taller than one
						var bx = b.box();
						for (int z = bx.z(); z < bx.maxZ(); z++) for (int x = bx.x(); x < bx.maxX(); x++) {
							int top = bx.maxY() - 1;
							int[] cur = out.get(key(x, z));
							if (cur == null || top > cur[1]) out.put(key(x, z), new int[] {c, top});
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * How one approach meets its settlement, as the world would draw it (road columns inside a district mask are the district's, D5).
	 *
	 * @param contacts 4-neighbour pairs (drawn approach column, district surface column)
	 * @param maxStep largest |approach top - district top| over the contacts (0 with no contact)
	 * @param gap Chebyshev distance from the nearest drawn approach column to the nearest district surface column (0 when touching; MAX_VALUE with none near)
	 * @param wetLow drawn approach columns over water whose top block is not above the water surface block (a deck at water level, WR)
	 * @param gateCut natural ground minus the street level at the exit (positive: a cut), 0 with no street there
	 * @param exitTilePlanned whether a settlement plans the district tile of the approach's first point (its exit)
	 */
	record Join(String id, Pt exit, int drawn, int masked, int contacts, int maxStep, String worst, int gap, int wetLow, boolean exitTilePlanned, int exitStep, int gateCut, int farStep, int farBad, int farWet) {
		boolean joined() { return contacts > 0 && maxStep <= 1; }
		@Override public String toString() {
			return String.format("%s exit %d,%d drawn %d masked %d contacts %d maxStep %d%s gap %s wetLow %d exitTile %s", id, exit.x(), exit.z(), drawn, masked, contacts, maxStep,
					worst.isEmpty() ? "" : " (" + worst + ")", gap == Integer.MAX_VALUE ? "none" : String.valueOf(gap), wetLow, exitTilePlanned ? "planned" : "UNPLANNED") + " exitStep " + exitStep + " gateCut " + gateCut + (Math.abs(gateCut) > 4 ? " (OVER 4)" : "") + " farStep " + farStep + " farBad " + farBad + " farWet " + farWet;
		}
	}

	/** Water within 8 blocks of the column (exact probe). */
	static boolean nearWater(TerrainProbe t, int x, int z) {
		for (int dz = -8; dz <= 8; dz += 4) for (int dx = -8; dx <= 8; dx += 4) if (t.waterDepth(x + dx, z + dz) > 0) return true;
		return false;
	}

	/** Graph-road ports of the settlement owning cell (cx, cz) that no approach joins (dead-end-port, 6.15 follow-up). */
	static List<dev.fractalbuildings.core.road.RoadPort> deadEndPorts(WorldPlanner w, int cx, int cz) {
		var site = w.siteOf(new Cell(cx, cz));
		return site.isEmpty() ? List.of() : w.deadEndPorts(site.get());
	}

	/** Every approach of the settlement owning cell (cx, cz), measured; empty when the cell has no settlement. */
	static List<Join> joins(WorldPlanner w, int cx, int cz) {
		List<Join> out = new ArrayList<>();
		RoadNet net = w.roads();
		var so = net.settlement(new CellKey(cx, cz));
		if (so.isEmpty()) return out;
		SettlementSite site = w.siteOf(new Cell(cx, cz)).orElse(null);
		if (site == null) return out;
		TerrainProbe t = w.probes().exact();
		for (WorldPlanner.Approach a : w.approachRoads(site, so.get())) {
			List<Col> cols = a.columns();
			TreeSet<Long> chunks = new TreeSet<>();
			for (Col k : cols) chunks.add(key(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16)));
			TreeMap<Long, int[]> d = districtTops(w, chunks);
			TreeMap<Long, Col> drawn = new TreeMap<>();
			int masked = 0, wetLow = 0;
			for (Col k : cols) {
				boolean[] m = w.districtColumns(Math.floorDiv(k.x(), 16), Math.floorDiv(k.z(), 16));
				if (m != null && m[(k.z() & 15) * 16 + (k.x() & 15)]) { masked++; continue; }
				drawn.put(key(k.x(), k.z()), k);
				if (t.waterDepth(k.x(), k.z()) > 0 && k.y() - 1 <= t.surfaceY(k.x(), k.z()) - 1 && !(a.port() != null && Math.max(Math.abs(k.x() - a.port().x()), Math.abs(k.z() - a.port().z())) <= 32)) wetLow++; // the 32 blocks at the port are the graph road's (RD4)
			}
			int contacts = 0, maxStep = 0, exitStep = 0, farStep = 0, farBad = 0, farWet = 0;
			Pt ex = a.road().points().get(0);
			Col nearest = null;
			for (Col k : drawn.values()) if (nearest == null || Math.max(Math.abs(k.x() - ex.x()), Math.abs(k.z() - ex.z())) < Math.max(Math.abs(nearest.x() - ex.x()), Math.abs(nearest.z() - ex.z()))) nearest = k;
			String worst = "";
			for (Col k : drawn.values()) {
				for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int[] s = d.get(key(k.x() + o[0], k.z() + o[1]));
					if (s == null) continue;
					contacts++;
					int step = Math.abs((k.y() - 1) - s[1]);
					if (nearest != null && Math.max(Math.abs(k.x() - nearest.x()), Math.abs(k.z() - nearest.z())) <= 2 * k.width()) exitStep = Math.max(exitStep, step);
					else if (step > 1 && nearWater(t, k.x(), k.z())) farWet++; // a deck lifted above water (WR3) beside a street end at the shore: the deck rule wins
					else { farStep = Math.max(farStep, step); if (step > 1) farBad++; } // a contact that is not the exit (a crossing or a street end beside the road)
					if (step > maxStep) { maxStep = step; worst = k.x() + "," + k.z() + " top " + (k.y() - 1) + " vs " + (char) s[0] + " " + s[1]; }
				}
			}
			int gap = Integer.MAX_VALUE;
			for (Col k : drawn.values()) for (long e : d.keySet()) {
				int x = (int) (e & 0xffffffffL), z = (int) (e >> 32);
				gap = Math.min(gap, Math.max(Math.abs(x - k.x()), Math.abs(z - k.z())) - 1);
			}
			Pt exit = a.road().points().get(0);
			var lvl = w.streetLevel(site, exit.x(), exit.z(), 6);
			int gateCut = lvl.isPresent() ? t.surfaceY(exit.x(), exit.z()) - lvl.getAsInt() : 0; // ground minus street at the gate: > 4 is the gate-cut metric (fix is 7.19)
			var tid = w.grid().tileOf(exit.x(), exit.z());
			out.add(new Join(a.road().id(), exit, drawn.size(), masked, contacts, maxStep, worst, gap, wetLow, w.tile(tid.ix(), tid.iz()).isPresent(), exitStep, gateCut, farStep, farBad, farWet));
		}
		return out;
	}
}
