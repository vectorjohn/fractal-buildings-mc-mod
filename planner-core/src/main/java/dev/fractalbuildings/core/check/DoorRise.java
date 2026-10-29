package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * DS-45 door rise (SHOPLOW S0, docs/plans/shoplow.md), report only: how far each entrance door stands above the street in
 * front of it. Entrance doors are {@link StairGround#doors}. <b>Rise</b> = the door's standing level minus the street level
 * at the first column outside the building's lots straight out from the door: the scene's road, paving or deck surface
 * there ({@link StairGround#surfaceLevels}), else the terrain's {@code floorY}. <b>Run</b> = the most blocks of descent
 * between level treads on the walkable way from the door down to the nearest ground (StairGround's walker; turns do not
 * end a run). <b>Landing</b> = a level move at door height before the way goes down. <b>Kind</b> = the spec type, except
 * {@code market} for a shop whose lowest room storey is above storey 0 (the market hall on posts) and
 * {@code stilted-<type>} for any other type like that. Definitions match {@code scripts/DoorRise.java}, whose baseline is
 * docs/review/phase7/shoplow/baseline.md.
 */
public final class DoorRise {
	/** DOORBANK: a door this far below the street (or lower; 2 blocks is out of a jump's reach) needs a built way up to it. */
	public static final int BANK_RISE = -2;
	private static int wxOf(VoxelGrid g, int x, int z) { return g.worldX(x, z); }
	private static int wzOf(VoxelGrid g, int x, int z) { return g.worldZ(x, z); }
	private DoorRise() {}

	/** One entrance door. {@code front}: it faces its lot's front. {@code source}: {@code ground} (road, paving, deck) or {@code terrain}. */
	public record Door(String door, String building, String lot, String kind, String style, boolean front, int rise, String source, int run, boolean landing, boolean reached, boolean toStreet) {
		/** The record as before DOORBANK, for hand-built doors: the way to the street is taken as found whenever the way to the ground is. */
		public Door(String door, String building, String lot, String kind, String style, boolean front, int rise, String source, int run, boolean landing, boolean reached) { this(door, building, lot, kind, style, front, rise, source, run, landing, reached, reached); }

		/** DS-45: shops and market halls -2..2; others -2..3, or 4-5 with a landing; and no run over 3 risers when the way was measured. */
		public boolean withinLimits() {
			boolean shop = kind.equals("shop") || kind.equals("market");
			boolean riseOk = shop ? rise >= -2 && rise <= 2 : rise >= -2 && (rise <= 3 || (rise <= 5 && landing));
			return riseOk && (!reached || run <= 3);
		}
	}

	/** Every entrance door of the scene's buildings. */
	public static List<Door> measure(SceneDoc scene, TerrainProbe terrain) {
		Map<Long, Integer> surface = StairGround.surfaceLevels(scene);
		List<Door> out = new ArrayList<>();
		for (PlacedBuilding b : scene.buildings()) out.addAll(measure(scene, b, dev.fractalbuildings.core.terrain.SiteGround.probe(b, terrain), surface)); // the ground as the building leaves it: its cut and fill are ground (SHOPLOW S2)
		return out;
	}

	public static List<Door> measure(SceneDoc scene, PlacedBuilding b, TerrainProbe terrain, Map<Long, Integer> surface) {
		List<Door> out = new ArrayList<>();
		BuildingIr s = b.shell();
		List<Opening> doors = StairGround.doors(b);
		if (doors.isEmpty()) return out;
		int lowest = Integer.MAX_VALUE;
		for (Room r : s.rooms()) lowest = Math.min(lowest, r.storey());
		if (lowest == Integer.MAX_VALUE) lowest = 0;
		String type = s.spec().type();
		String kind = lowest > 0 ? (type.equals("shop") ? "market" : "stilted-" + type) : type;
		List<Lot> lots = new ArrayList<>();
		for (String id : b.lots()) for (Lot l : scene.lots()) if (l.id().equals(id)) lots.add(l);
		VoxelGrid g = VoxelGrid.of(b);
		Placement pl = b.placement();
		for (Opening o : doors) {
			Facing outL = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
			Facing outW = pl.toWorld(outL);
			Box3 dw = pl.toWorld(o.box());
			int wx = dw.x(), wz = dw.z(), level = pl.worldY(o.box().y());
			boolean front = !lots.isEmpty() && lots.get(0).front() == outW;
			int k = 0;
			while (k < 64 && inLots(lots, wx, wz)) { wx += outW.dx; wz += outW.dz; k++; }
			Integer sv = surface.get(StairGround.key(wx, wz));
			int street = sv != null ? sv : terrain.floorY(wx, wz);
			int[] run = run(g, o, outL, terrain, surface, lots, false, 0);
			int[] up = level - street <= BANK_RISE ? run(g, o, outL, terrain, surface, lots, true, 2 * street) : run;
			out.add(new Door(o.id(), b.id(), lots.isEmpty() ? "" : lots.get(0).id(), kind, s.spec().style(), front, level - street, sv != null ? "ground" : "terrain", run[0], run[1] == 1, run[2] == 1, up[2] == 1));
		}
		return out;
	}

	private static boolean inLots(List<Lot> lots, int x, int z) {
		for (Lot l : lots) if (l.containsColumn(x, z)) return true;
		return false;
	}

	private static int top2(long n) { return (int) ((n >>> 40) - 4096); }

	/** {longest run of risers, landing at the door 0/1, reached ground 0/1}: StairGround's walker, with parents kept. */
	private static int[] run(VoxelGrid g, Opening o, Facing out, TerrainProbe terrain, Map<Long, Integer> surface, List<Lot> lots, boolean toStreet, int streetTop2) {
		int x0 = g.minX, x1 = g.minX + g.sx - 1, z0 = g.minZ, z1 = g.minZ + g.sz - 1;
		for (Lot l : lots)
			for (int cx : new int[] {l.x(), l.x() + l.w() - 1})
				for (int cz : new int[] {l.z(), l.z() + l.d() - 1}) {
					int lx = g.placement().localX(cx, cz), lz = g.placement().localZ(cx, cz);
					x0 = Math.min(x0, lx); x1 = Math.max(x1, lx); z0 = Math.min(z0, lz); z1 = Math.max(z1, lz);
				}
		x0 -= 2; x1 += 2; z0 -= 2; z1 += 2;
		int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
		int[] level = new int[nx * nz];
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int wx = g.worldX(x, z), wz = g.worldZ(x, z);
				int lv = terrain.floorY(wx, wz);
				Integer s = surface.get(StairGround.key(wx, wz));
				if (s != null) lv = s;
				level[(z - z0) * nx + (x - x0)] = lv;
				lo = Math.min(lo, lv);
				hi = Math.max(hi, lv);
			}
		int ylo = Math.min(lo, g.worldY(g.minY)) - 2, yhi = Math.max(hi, g.worldY(g.minY + g.sy)) + 3;
		int floor2 = 2 * g.worldY(o.box().y());
		Map<Long, long[]> parent = new TreeMap<>(); // node -> {parent node, facing ordinal}
		List<int[]> queue = new ArrayList<>();
		queue.add(new int[] {o.box().x(), o.box().z(), floor2});
		long start = StairGround.node(o.box().x(), o.box().z(), floor2);
		parent.put(start, new long[] {Long.MIN_VALUE, -1});
		long goal = Long.MIN_VALUE;
		for (int head = 0; head < queue.size() && goal == Long.MIN_VALUE; head++) {
			int[] c = queue.get(head);
			long cn = StairGround.node(c[0], c[1], c[2]);
			for (Facing f : head == 0 ? new Facing[] {out} : Facing.values()) {
				int px = c[0] + f.dx, pz = c[1] + f.dz;
				if (px < x0 || px > x1 || pz < z0 || pz > z1) continue;
				int lv = level[(pz - z0) * nx + (px - x0)];
				for (int top2 = 2 * (ylo + 1); top2 <= 2 * yhi; top2++) {
					if (top2 - c[2] > 1 || c[2] - top2 > 2) continue;
					int kind = StairGround.surfaceKind(g, px, pz, top2, lv);
					if (kind == StairGround.NONE) continue;
					long k = StairGround.node(px, pz, top2);
					if (parent.containsKey(k)) continue;
					parent.put(k, new long[] {cn, f.ordinal()});
					if (toStreet ? top2 >= streetTop2 - 1 && !inLots(lots, wxOf(g, px, pz), wzOf(g, px, pz)) : kind == StairGround.GROUND) { goal = k; break; }
					queue.add(new int[] {px, pz, top2});
				}
				if (goal != Long.MIN_VALUE) break;
			}
		}
		if (goal == Long.MIN_VALUE) return new int[] {0, 0, 0};
		// walk back: the path as (top2, facing) from the door outward
		List<long[]> path = new ArrayList<>();
		for (long n = goal; n != start; n = parent.get(n)[0]) path.add(new long[] {top2(n), parent.get(n)[1]});
		Collections.reverse(path);
		// Walker steps are half blocks (a stair top is half a block), so a straight flight of rise R is R blocks of descent.
		// A run is the descent between level moves (a tread or landing), in whole blocks rounded up; turns do not end it.
		// A landing at the door: a level move before the walker is more than half a block below the door's floor.
		int prev = floor2, best = 0, cur = 0;
		boolean landing = false;
		for (long[] st : path) {
			int t = (int) st[0];
			if (t < prev) cur += prev - t;
			else {
				if (t == prev && t >= floor2 - 1) landing = true;
				cur = 0;
			}
			best = Math.max(best, (cur + 1) / 2);
			prev = t;
		}
		return new int[] {best, landing ? 1 : 0, 1};
	}


	// ---- report

	static final String[] BUCKETS = {"<= -3", "-2..0", "1", "2", "3", "4", "5", "6-8", "9+"};
	private static final List<String> KINDS = List.of("shop", "market", "house", "workshop");

	static int bucket(int r) { return r <= -3 ? 0 : r <= 0 ? 1 : r == 1 ? 2 : r == 2 ? 3 : r == 3 ? 4 : r == 4 ? 5 : r == 5 ? 6 : r <= 8 ? 7 : 8; }

	private static final class Row {
		int doors, hi, landing, run4, within, max = Integer.MIN_VALUE;
		final int[] h = new int[BUCKETS.length];
	}

	/** Accumulates doors by kind, front doors and side/back doors apart, and renders the DS-45 tables. */
	public static final class Report {
		private final Map<String, Row> front = new TreeMap<>(), side = new TreeMap<>();

		public synchronized void add(List<Door> doors) { for (Door d : doors) add(d); }

		public synchronized void add(Door d) {
			Row r = (d.front() ? front : side).computeIfAbsent(d.kind(), k -> new Row());
			r.doors++;
			r.h[bucket(d.rise())]++;
			if (d.rise() >= 4) r.hi++;
			if (d.landing()) r.landing++;
			if (d.run() >= 4) r.run4++;
			if (d.withinLimits()) r.within++;
			r.max = Math.max(r.max, d.rise());
		}

		public synchronized int doors() {
			int n = 0;
			for (Row r : front.values()) n += r.doors;
			for (Row r : side.values()) n += r.doors;
			return n;
		}

		public synchronized String toMarkdown() {
			return "Front doors: rise from the street surface in front.\n\n" + table(front) + "\nSide and back entrance doors: rise from the ground straight out.\n\n" + table(side);
		}

		private static String table(Map<String, Row> rows) {
			StringBuilder sb = new StringBuilder("| kind | doors |");
			for (String b : BUCKETS) sb.append(' ').append(b).append(" |");
			sb.append(" 4+ | landing | run 4+ | max | within DS-45 |\n|---|---|");
			for (int i = 0; i < BUCKETS.length + 5; i++) sb.append("---|");
			sb.append('\n');
			List<String> kinds = new ArrayList<>(rows.keySet());
			kinds.sort(Comparator.comparingInt((String k) -> KINDS.indexOf(k) < 0 ? 99 : KINDS.indexOf(k)).thenComparing(k -> k));
			for (String k : kinds) {
				Row r = rows.get(k);
				sb.append("| ").append(k).append(" | ").append(r.doors).append(" |");
				for (int c : r.h) sb.append(' ').append(c).append(" |");
				sb.append(String.format(Locale.ROOT, " %d (%d %%) | %d %% | %d | %d | %d (%d %%) |%n", r.hi, Math.round(100.0 * r.hi / r.doors), Math.round(100.0 * r.landing / r.doors), r.run4, r.max, r.within, Math.round(100.0 * r.within / r.doors)));
			}
			return sb.toString();
		}
	}
}
