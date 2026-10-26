package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * High-rise design checks (docs/plans/highrises.md step B0; DS-32 floor programme, DS-22 tall buildings can be climbed),
 * over the IR of buildings of type {@code highrise}. They are <em>warnings</em> for now: {@code check} and
 * {@code city-check} list them apart from the invariants and they do not change an exit code (B4 makes them violations
 * once the generator passes them). Ids are in {@link #IDS}.
 *
 * <p>The room-use vocabulary is the one of the plan: {@code lobby}, {@code corridor}, {@code banquet}, {@code open}
 * (halls and grand rooms), {@code living} and {@code office} (units), {@code stairwell}. Rooms tagged with other uses
 * ({@code room}, {@code entry}, {@code stair} of the Phase 4 generator) satisfy none of the checks that need these uses.
 * Local-frame cells; {@link #check(SceneDoc)} maps them to world cells.
 */
public final class HighriseChecks {
	private HighriseChecks() {}

	public static final String TYPE = "highrise";
	public static final String LOBBY = "highrise-lobby";
	public static final String FLOOR_REPEAT = "floor-repeat";
	public static final String HALL_ACCESS = "hall-access";
	public static final String STAIRWELL = "stairwell-enclosed";
	public static final String SHAFT = "shaft-present";
	public static final String ROOM_WINDOW = "room-window";
	public static final String ROOM_LIGHT = "room-light";
	public static final String ROOF = "roof-reachable";
	public static final List<String> IDS = List.of(LOBBY, FLOOR_REPEAT, HALL_ACCESS, STAIRWELL, SHAFT, ROOM_WINDOW, ROOM_LIGHT, ROOF);

	static final int MAX_CELLS = 64;
	/** Rooms the stairwell door and the shaft doorway may open onto (never banquet, a unit or a back room). */
	static final Set<String> HALLS = Set.of("corridor", "lobby", "open");
	/** Rooms a unit door may open onto: its own entry, or a hall. */
	static final Set<String> UNIT_HALLS = Set.of("entry", "corridor", "lobby", "open");
	static final Set<String> UNITS = Set.of("flat", "office");
	/** Uses that need a window to the outside (DS-25). */
	static final Set<String> WINDOWED = Set.of("flat", "bedroom", "office", "lobby", "banquet", "open");
	/** Rooms standing on the roof: a stair house or the plant room over the shaft. */
	static final Set<String> ROOF_USES = Set.of("stairwell", "plant");
	/** A room at least this wide plus deep (bounding box) needs ceiling lights within LIGHT_REACH of every floor cell (DS-24, coarse). */
	static final int BIG_ROOM = 15, LIGHT_REACH = 6;
	/** Clear height of a grand room (DS-23: 5-7 clear). */
	static final int GRAND_CLEAR = 5;
	/** Plates at least this big (outer box) get 3-wide halls: interior 30+ both ways. */
	static final int WIDE_HALL_PLATE = 32;

	/** The warnings of every high-rise in the scene, in world coordinates, building order then check order. */
	public static List<Violation> check(SceneDoc scene) {
		List<Violation> out = new ArrayList<>();
		for (PlacedBuilding b : scene.buildings()) {
			if (!TYPE.equals(b.shell().spec().type())) continue;
			for (Violation v : check(b.shell())) {
				List<Box3> cells = new ArrayList<>();
				for (Box3 c : v.cells()) cells.add(b.placement().toWorld(c));
				out.add(new Violation(v.invariant(), b.id(), v.message(), cells));
			}
		}
		return out;
	}

	/** All six checks over one shell (whatever its type), local cells, building id null. */
	public static List<Violation> check(BuildingIr ir) {
		PlacedBuilding pb = new PlacedBuilding("ir", List.of("lot"), 0, 0, 0, 0, ir, List.of());
		Ctx c = new Ctx(ir, VoxelGrid.of(pb));
		List<Violation> out = new ArrayList<>();
		c.lobby(out);
		c.floorRepeat(out);
		c.hallAccess(out);
		c.stairwell(out);
		c.shaft(out);
		c.roomWindow(out);
		c.roomLight(out);
		c.roof(out);
		return out;
	}

	private static final class Ctx {
		final BuildingIr ir;
		final VoxelGrid g;
		final Map<String, Room> rooms = new TreeMap<>();
		final Map<Integer, Storey> storeys = new TreeMap<>();

		Ctx(BuildingIr ir, VoxelGrid g) {
			this.ir = ir;
			this.g = g;
			for (Room r : ir.rooms()) rooms.put(r.id(), r);
			for (Storey s : ir.storeys()) storeys.put(s.index(), s);
		}

		static Violation v(String id, String msg, List<Box3> cells) {
			List<Box3> c = cells.size() > MAX_CELLS ? cells.subList(0, MAX_CELLS) : cells;
			return new Violation(id, null, msg, c);
		}

		String use(String roomId) {
			Room r = rooms.get(roomId);
			return r == null ? Opening.OUTSIDE.equals(roomId) ? "outside" : "?" : r.use();
		}

		List<Box3> parts(Room r) { return r.parts(); }

		/** The room on the other side of {@code o} from {@code id}, or null. */
		static String other(Opening o, String id) { return o.from().equals(id) ? o.to() : o.to().equals(id) ? o.from() : null; }

		static boolean passage(Opening o) { return o.kind() == OpeningKind.DOOR || o.kind() == OpeningKind.ARCH; }

		boolean inRoom(Room r, int x, int y, int z) {
			for (Box3 p : r.parts()) if (p.contains(x, y, z)) return true;
			return false;
		}

		/** Storey indices whose y-range [y, y + height) overlaps the box. */
		List<Integer> storeysOf(Box3 b) {
			List<Integer> out = new ArrayList<>();
			for (Storey s : storeys.values()) if (b.y() < s.y() + s.height() && b.maxY() > s.y()) out.add(s.index());
			return out;
		}

		// highrise-lobby

		void lobby(List<Violation> out) {
			Storey s0 = storeys.get(0);
			if (s0 == null) return;
			List<Room> lobbies = new ArrayList<>();
			for (Room r : ir.rooms()) if (r.storey() == 0 && r.use().equals("lobby")) lobbies.add(r);
			if (lobbies.isEmpty()) {
				out.add(v(LOBBY, "storey 0 has no room of use lobby (uses: " + usesOf(0) + ")", List.of()));
				return;
			}
			List<Room> entered = new ArrayList<>();
			List<Box3> doors = new ArrayList<>();
			for (Opening o : ir.openings()) {
				if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
				if (o.from().equals(Opening.OUTSIDE) == o.to().equals(Opening.OUTSIDE)) continue;
				doors.add(o.box());
				Room in = rooms.get(o.from().equals(Opening.OUTSIDE) ? o.to() : o.from());
				if (in != null && in.use().equals("lobby") && !entered.contains(in)) entered.add(in);
			}
			if (doors.isEmpty()) { out.add(v(LOBBY, "storey 0 has no street door", List.of())); return; }
			if (entered.isEmpty()) {
				out.add(v(LOBBY, "no street door opens into a lobby", doors));
				return;
			}
			boolean tall = false, stairDoor = false;
			for (Room l : entered) {
				for (Box3 p : l.parts()) if (p.sy() >= GRAND_CLEAR) tall = true;
				for (Opening o : ir.openings()) {
					if (!passage(o)) continue;
					String x = other(o, l.id());
					if (x != null && use(x).equals("stairwell") && rooms.get(x).storey() == 0) stairDoor = true;
				}
			}
			if (!tall) out.add(v(LOBBY, "the lobby is under " + GRAND_CLEAR + " clear (DS-23)", entered.get(0).parts()));
			if (!stairDoor) out.add(v(LOBBY, "the lobby has no door onto the storey 0 stairwell", entered.get(0).parts()));
		}

		String usesOf(int storey) {
			TreeMap<String, Integer> n = new TreeMap<>();
			for (Room r : ir.rooms()) if (r.storey() == storey) n.merge(r.use(), 1, Integer::sum);
			return n.toString();
		}

		// floor-repeat

		void floorRepeat(List<Violation> out) {
			int pairs = 0, bad = 0;
			String first = null;
			List<Box3> cells = new ArrayList<>();
			for (Storey a : storeys.values()) {
				Storey b = storeys.get(a.index() + 1);
				if (b == null || a.height() != b.height()) continue;
				Map<String, Integer> ma = multiset(a.index()), mb = multiset(b.index());
				if (ma.isEmpty() || !ma.equals(mb)) continue;
				pairs++;
				int[] box = footprint(a.index());
				int diff = 0, fx = 0, fz = 0, fy = 0;
				for (int dy = 1; dy < a.height(); dy++)
					for (int z = Math.max(box[2] - 1, g.minZ); z <= Math.min(box[3] + 1, g.minZ + g.sz - 1); z++)
						for (int x = Math.max(box[0] - 1, g.minX); x <= Math.min(box[1] + 1, g.minX + g.sx - 1); x++)
							if (norm(g.get(x, a.y() + dy, z)) != norm(g.get(x, b.y() + dy, z))) {
								if (diff++ == 0) { fx = x; fy = a.y() + dy; fz = z; }
								if (cells.size() < MAX_CELLS) cells.add(Box3.of(x, a.y() + dy, z, 1, 1, 1));
							}
				if (diff > 0) {
					bad++;
					if (first == null) first = "storeys " + a.index() + " and " + b.index() + " differ in " + diff + " cell(s), first at " + fx + "," + fy + "," + fz;
				}
			}
			if (bad > 0) out.add(v(FLOOR_REPEAT, bad + " of " + pairs + " pairs of consecutive storeys with the same rooms are not the same plan after a y shift (" + first + ")", cells));
		}

		/** Walls, doors and windows are what a typical floor repeats; stairs, holes and the rest are not compared. */
		static Role norm(Role r) { return r == Role.WALL || r == Role.DOOR || r == Role.WINDOW ? r : Role.AIR; }

		Map<String, Integer> multiset(int storey) {
			Map<String, Integer> m = new TreeMap<>();
			for (Room r : ir.rooms()) if (r.storey() == storey) m.merge(r.volume() + "/" + r.use(), 1, Integer::sum);
			return m;
		}

		/** x0, x1, z0, z1 (inclusive) of the storey's rooms. */
		int[] footprint(int storey) {
			int[] f = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
			for (Room r : ir.rooms()) {
				if (r.storey() != storey) continue;
				for (Box3 p : r.parts()) { f[0] = Math.min(f[0], p.x()); f[1] = Math.max(f[1], p.maxX() - 1); f[2] = Math.min(f[2], p.z()); f[3] = Math.max(f[3], p.maxZ() - 1); }
			}
			return f;
		}

		// hall-access

		void hallAccess(List<Violation> out) {
			entryAndBedrooms(out);
			List<Box3> cells = new ArrayList<>();
			List<String> ids = new ArrayList<>();
			// a unit room is fine with a door onto a hall, or onto another room of its own unit's use that has one (an office tenancy's
			// second room, a wrap-around flat); the IR has no unit ids, so "own unit" is: same use, reached through doors of such rooms
			java.util.Set<String> ok = new java.util.TreeSet<>();
			for (Room r : ir.rooms()) {
				if (!UNITS.contains(r.use()) && !r.use().equals("kitchen")) continue;
				for (Opening o : ir.openings()) {
					if (!passage(o)) continue;
					String x = other(o, r.id());
					if (x != null && UNIT_HALLS.contains(use(x))) ok.add(r.id());
				}
			}
			for (boolean grew = true; grew; ) {
				grew = false;
				for (Room r : ir.rooms()) {
					if (!UNITS.contains(r.use()) || ok.contains(r.id())) continue;
					for (Opening o : ir.openings()) {
						if (!passage(o)) continue;
						String x = other(o, r.id());
						if (x != null && ok.contains(x) && (use(x).equals(r.use()) || use(x).equals("kitchen"))) { ok.add(r.id()); grew = true; break; }
					}
				}
			}
			for (Room r : ir.rooms()) if (UNITS.contains(r.use()) && !ok.contains(r.id())) { ids.add(r.id()); cells.addAll(r.parts()); }
			if (!ids.isEmpty()) out.add(v(HALL_ACCESS, ids.size() + " unit room(s) (living, office) have no door onto a corridor, lobby, banquet or open floor, e.g. " + ids.get(0), cells));
			cells = new ArrayList<>();
			ids = new ArrayList<>();
			for (Room r : ir.rooms()) {
				if (!r.use().equals("corridor")) continue;
				int need = 2;
				for (Volume vol : ir.volumes()) if (vol.id().equals(r.volume()) && vol.box().sx() >= WIDE_HALL_PLATE && vol.box().sz() >= WIDE_HALL_PLATE) need = 3;
				for (Box3 p : r.parts())
					if (Math.min(p.sx(), p.sz()) < need) { ids.add(r.id() + " (" + Math.min(p.sx(), p.sz()) + " wide, needs " + need + ")"); cells.add(p); }
			}
			if (!ids.isEmpty()) out.add(v(HALL_ACCESS, ids.size() + " corridor part(s) too narrow, e.g. " + ids.get(0), cells));
		}

		// stairwell-enclosed

		void stairwell(List<Violation> out) {
			TreeMap<Integer, List<Box3>> flights = new TreeMap<>();
			for (Opening o : ir.openings())
				if (o.kind() == OpeningKind.STAIR) for (int s : storeysOf(o.box())) flights.computeIfAbsent(s, k -> new ArrayList<>()).add(clip(o.box(), storeys.get(s)));
			if (flights.isEmpty()) return;
			Map<Integer, int[]> doorAt = new TreeMap<>();
			for (var e : flights.entrySet()) {
				int s = e.getKey();
				List<Room> wells = new ArrayList<>();
				for (Room r : ir.rooms()) if (r.storey() == s && r.use().equals("stairwell")) wells.add(r);
				if (wells.size() != 1) {
					out.add(v(STAIRWELL, "storey " + s + " has flights but " + wells.size() + " rooms of use stairwell (needs 1)", e.getValue()));
					continue;
				}
				Room w = wells.get(0);
				List<Box3> loose = new ArrayList<>();
				for (Box3 f : e.getValue()) if (!coversColumns(w, f)) loose.add(f);
				if (!loose.isEmpty()) out.add(v(STAIRWELL, "storey " + s + ": " + loose.size() + " flight(s) leave the stairwell " + w.id(), loose));
				int doors = 0;
				for (Opening o : ir.openings()) {
					if (!passage(o)) continue;
					String x = other(o, w.id());
					if (x == null) continue;
					doors++;
					if (!HALLS.contains(use(x)) && !x.equals(Opening.OUTSIDE)) out.add(v(STAIRWELL, "storey " + s + ": the stairwell door opens into " + x + " (use " + use(x) + "), not a hall, lobby or open floor", List.of(o.box())));
					if (!x.equals(Opening.OUTSIDE)) doorAt.putIfAbsent(s, new int[] {o.box().x(), o.box().z()});
				}
				if (doors != 1) out.add(v(STAIRWELL, "storey " + s + ": the stairwell has " + doors + " doors (needs exactly 1)", w.parts()));
				List<Box3> open = openWalls(w);
				if (!open.isEmpty()) out.add(v(STAIRWELL, "storey " + s + ": the stairwell wall has " + open.size() + " open cell(s) beside it", open));
			}
			int[] ref = null;
			for (var e : doorAt.entrySet()) {
				if (ref == null) { ref = e.getValue(); continue; }
				if (ref[0] != e.getValue()[0] || ref[1] != e.getValue()[1]) {
					out.add(v(STAIRWELL, "the stairwell door is not at the same x,z on every storey (" + ref[0] + "," + ref[1] + " vs " + e.getValue()[0] + "," + e.getValue()[1] + " on storey " + e.getKey() + ")", List.of()));
					break;
				}
			}
		}

		static Box3 clip(Box3 b, Storey s) {
			int y0 = Math.max(b.y(), s.y()), y1 = Math.min(b.maxY(), s.y() + s.height());
			return Box3.of(b.x(), y0, b.z(), b.sx(), y1 - y0, b.sz());
		}

		boolean coversColumns(Room r, Box3 f) {
			for (int z = f.z(); z < f.maxZ(); z++)
				for (int x = f.x(); x < f.maxX(); x++) {
					boolean in = false;
					for (Box3 p : r.parts()) if (x >= p.x() && x < p.maxX() && z >= p.z() && z < p.maxZ()) in = true;
					if (!in) return false;
				}
			return true;
		}

		/** Cells beside the room at its two lowest levels that are air: a wall missing. */
		List<Box3> openWalls(Room r) {
			List<Box3> out = new ArrayList<>();
			int[] hx = {1, -1, 0, 0}, hz = {0, 0, 1, -1};
			for (Box3 p : r.parts())
				for (int y = p.y(); y < Math.min(p.maxY(), p.y() + 2); y++)
					for (int z = p.z(); z < p.maxZ(); z++)
						for (int x = p.x(); x < p.maxX(); x++)
							for (int d = 0; d < 4; d++) {
								int nx = x + hx[d], nz = z + hz[d];
								if (inRoom(r, nx, y, nz) || g.get(nx, y, nz) != Role.AIR) continue;
								out.add(Box3.of(nx, y, nz, 1, 1, 1));
							}
			return out;
		}

		// shaft-present

		int topHabitable() {
			int top = -1;
			for (Room r : ir.rooms()) if (!ROOF_USES.contains(r.use())) top = Math.max(top, r.storey());
			return top;
		}

		void shaft(List<Violation> out) {
			Storey s0 = storeys.get(0);
			int top = topHabitable();
			if (s0 == null || top < 0) { out.add(v(SHAFT, "no habitable storeys to run a lift shaft through", List.of())); return; }
			Storey st = storeys.get(top);
			int yLo = s0.y() + 1, yHi = st.y() + st.height() - 1;
			String bestWhy = "no 3x3 column of air from storey 0 to storey " + top;
			int bestReach = -1;
			int bx = 0, bz = 0;
			for (int z = g.minZ; z + 3 <= g.minZ + g.sz; z++)
				for (int x = g.minX; x + 3 <= g.minX + g.sx; x++) {
					int reach = airReach(x, z, yLo, yHi);
					if (reach < 2) continue; // not a column at all
					String why = reach > yHi - yLo ? shaftWalls(x, z, top) : "the air column stops at y=" + (yLo + reach) + " (storey " + storeyAt(yLo + reach) + ")";
					if (why == null) why = shaftExtras(x, z, top);
				if (why == null) return;
					if (reach > bestReach) { bestReach = reach; bestWhy = "best candidate at " + x + "," + z + ": " + why; bx = x; bz = z; }
				}
			out.add(v(SHAFT, bestWhy, bestReach < 0 ? List.of() : List.of(Box3.of(bx, yLo, bz, 3, 1, 3))));
		}

		/** A light hook on the pit floor and a plant room over the shaft on a stair-house storey; null if both are there. */
		String shaftExtras(int x0, int z0, int top) {
			Storey s0 = storeys.get(0);
			boolean light = false;
			for (var h : ir.hooks())
				if ((h.kind().equals("light") || h.kind().equals("ceiling_light")) && h.x() >= x0 && h.x() < x0 + 3 && h.z() >= z0 && h.z() < z0 + 3 && h.y() <= s0.y() + 2) light = true;
			if (!light) return "no light hook on the shaft's pit floor";
			for (Room r : ir.rooms())
				if (r.use().equals("plant") && r.storey() > top)
					for (Box3 p : r.parts()) if (p.x() < x0 + 3 && p.maxX() > x0 && p.z() < z0 + 3 && p.maxZ() > z0) return null;
			return "no plant room above the shaft on a storey over " + top;
		}

		int storeyAt(int y) {
			int best = 0;
			for (Storey s : storeys.values()) if (s.y() <= y) best = s.index();
			return best;
		}

		/** Levels from yLo up whose 3x3 at (x, z) is all air. */
		int airReach(int x0, int z0, int yLo, int yHi) {
			int n = 0;
			for (int y = yLo; y <= yHi; y++) {
				for (int dz = 0; dz < 3; dz++) for (int dx = 0; dx < 3; dx++) if (!g.inGrid(x0 + dx, y, z0 + dz) || g.get(x0 + dx, y, z0 + dz) != Role.AIR) return n;
				n++;
			}
			return n;
		}

		/** Null if the 3x3 column at (x0, z0) is walled with a window-role doorway per storey onto a hall; else why not. */
		String shaftWalls(int x0, int z0, int top) {
			Storey s0 = storeys.get(0);
			for (int dz = 0; dz < 3; dz++) for (int dx = 0; dx < 3; dx++) if (!g.get(x0 + dx, s0.y(), z0 + dz).solid) return "no pit floor under the shaft";
			// ring side cells: (cx, cz, outward dx, dz)
			List<int[]> ring = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				ring.add(new int[] {x0 + i, z0 - 1, 0, -1});
				ring.add(new int[] {x0 + i, z0 + 3, 0, 1});
				ring.add(new int[] {x0 - 1, z0 + i, -1, 0});
				ring.add(new int[] {x0 + 3, z0 + i, 1, 0});
			}
			for (int k = 0; k <= top; k++) {
				Storey s = storeys.get(k);
				if (s == null) continue;
				int doorways = 0;
				boolean toHall = false;
				for (int[] c : ring)
					for (int y = s.y() + 1; y <= s.y() + 2; y++) {
						Role r = g.get(c[0], y, c[1]);
						if (!r.solid) return "storey " + k + ": shaft wall open at " + c[0] + "," + y + "," + c[1];
						if (r == Role.WINDOW && y == s.y() + 1) {
							doorways++;
							for (Room room : ir.rooms())
								if (room.storey() == k && HALLS.contains(room.use()) && inRoom(room, c[0] + c[2], y, c[1] + c[3])) toHall = true;
						}
					}
				if (doorways == 0) return "storey " + k + ": no window-role doorway in the shaft wall";
				if (!toHall) return "storey " + k + ": the shaft doorway does not face a corridor, lobby, banquet or open floor";
			}
			return null;
		}

		/** Entries open onto a corridor; a bedroom is not reached only through another bedroom. */
		void entryAndBedrooms(List<Violation> out) {
			List<Box3> cells = new ArrayList<>();
			List<String> ids = new ArrayList<>();
			for (Room r : ir.rooms()) {
				if (!r.use().equals("entry")) continue;
				boolean corridor = false;
				for (Opening o : ir.openings()) {
					String x = passage(o) ? other(o, r.id()) : null;
					if (x != null && use(x).equals("corridor")) corridor = true;
				}
				if (!corridor) { ids.add(r.id()); cells.addAll(r.parts()); }
			}
			if (!ids.isEmpty()) out.add(v(HALL_ACCESS, ids.size() + " entry room(s) have no door onto a corridor, e.g. " + ids.get(0), cells));
			cells = new ArrayList<>();
			ids = new ArrayList<>();
			for (Room r : ir.rooms()) {
				if (!r.use().equals("bedroom")) continue;
				boolean other = false;
				for (Opening o : ir.openings()) {
					String x = passage(o) ? other(o, r.id()) : null;
					if (x != null && !use(x).equals("bedroom")) other = true;
				}
				if (!other) { ids.add(r.id()); cells.addAll(r.parts()); }
			}
			if (!ids.isEmpty()) out.add(v(HALL_ACCESS, ids.size() + " bedroom(s) are reached only through another bedroom, e.g. " + ids.get(0), cells));
		}

		// room-window (DS-25)

		void roomWindow(List<Violation> out) {
			List<Box3> cells = new ArrayList<>();
			List<String> ids = new ArrayList<>();
			for (Room r : ir.rooms()) {
				if (!WINDOWED.contains(r.use())) continue;
				boolean win = false;
				for (Opening o : ir.openings()) if (o.kind() == OpeningKind.WINDOW && Opening.OUTSIDE.equals(other(o, r.id()))) win = true;
				if (!win) { ids.add(r.id() + " (" + r.use() + ")"); cells.addAll(r.parts()); }
			}
			if (!ids.isEmpty()) out.add(v(ROOM_WINDOW, ids.size() + " room(s) of a windowed use have no window to the outside, e.g. " + ids.get(0), cells));
		}

		// room-light (DS-24, coarse)

		void roomLight(List<Violation> out) {
			List<Box3> cells = new ArrayList<>();
			List<String> ids = new ArrayList<>();
			for (Room r : ir.rooms()) {
				List<int[]> lights = new ArrayList<>(), ceiling = new ArrayList<>();
				for (var h : ir.hooks()) {
					if (!h.room().equals(r.id())) continue;
					if (h.kind().equals("light") || h.kind().equals("ceiling_light")) lights.add(new int[] {h.x(), h.z()});
					if (h.kind().equals("ceiling_light")) ceiling.add(new int[] {h.x(), h.z()});
				}
				if (lights.isEmpty()) { ids.add(r.id() + " has no light"); cells.addAll(r.parts()); continue; }
				int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
				for (Box3 p : r.parts()) { x0 = Math.min(x0, p.x()); x1 = Math.max(x1, p.maxX()); z0 = Math.min(z0, p.z()); z1 = Math.max(z1, p.maxZ()); }
				if ((x1 - x0) + (z1 - z0) < BIG_ROOM) continue;
				boolean dark = false;
				for (Box3 p : r.parts())
					for (int z = p.z(); z < p.maxZ() && !dark; z++)
						for (int x = p.x(); x < p.maxX() && !dark; x++) {
							boolean lit = false;
							for (int[] l : ceiling) if (Math.max(Math.abs(l[0] - x), Math.abs(l[1] - z)) <= LIGHT_REACH) lit = true;
							if (!lit) dark = true;
						}
				if (dark) { ids.add(r.id() + " has floor more than " + LIGHT_REACH + " from a ceiling_light"); cells.addAll(r.parts()); }
			}
			if (!ids.isEmpty()) out.add(v(ROOM_LIGHT, ids.size() + " room(s) too dark, e.g. " + ids.get(0), cells));
		}

		// roof-reachable

		void roof(List<Violation> out) {
			if (ir.roofs().isEmpty()) { out.add(v(ROOF, "the building has no roof", List.of())); return; }
			Roof top = null;
			for (Roof r : ir.roofs()) {
				if (roofStructure(r.volume())) continue; // the stair house is the way up, not the roof to reach
				if (top == null || r.baseY() > top.baseY()) top = r;
			}
			if (top == null || !top.form().equals("flat")) return; // pitched roofs: not applicable (DS-22: a flat roof)
			boolean[] seen = Invariants.reachFromStreetDoors(g, streetDoors());
			List<Box3> houses = new ArrayList<>(); // standing inside the stair house is not standing on the roof
			for (Volume x : ir.volumes()) if (roofStructure(x.id())) houses.add(x.box());
			boolean reached = false;
			for (int y = top.baseY() + 1; y <= top.baseY() + 2 && !reached; y++)
				for (int z = g.minZ; z < g.minZ + g.sz; z++)
					for (int x = g.minX; x < g.minX + g.sx; x++)
						if (g.inGrid(x, y, z) && seen[Invariants.cellIndex(g, x, y, z)] && g.get(x, y - 1, z) == Role.ROOF && !inAny(houses, x, y, z)) reached = true;
			Volume vol = null;
			for (Volume x : ir.volumes()) if (x.id().equals(top.volume())) vol = x;
			if (!reached) out.add(v(ROOF, "no standing cell on the top roof (" + top.volume() + ", y=" + top.baseY() + ") is reachable on foot from the street doors", vol == null ? List.of() : List.of(vol.box())));
			rail(out, top, vol, houses);
		}

		/** The top roof's edge has a wall cell with a rail cell above it all round (DS-8 amendment), the stair house's footprint aside. */
		void rail(List<Violation> out, Roof top, Volume vol, List<Box3> houses) {
			if (vol == null) return;
			Box3 b = vol.box();
			List<Box3> bad = new ArrayList<>();
			int n = 0;
			for (int z = b.z(); z < b.maxZ(); z++)
				for (int x = b.x(); x < b.maxX(); x++) {
					if (x != b.x() && x != b.maxX() - 1 && z != b.z() && z != b.maxZ() - 1) continue;
					if (inAny(houses, x, top.baseY() + 1, z)) continue;
					int y = top.baseY() + 1;
					while (g.get(x, y, z) == Role.WALL) y++;
					if (y == top.baseY() + 1 || g.get(x, y, z) != Role.RAIL) {
						n++;
						if (bad.size() < MAX_CELLS) bad.add(Box3.of(x, top.baseY() + 1, z, 1, 1, 1));
					}
				}
			if (n > 0) out.add(v(ROOF, n + " edge column(s) of the top roof (" + top.volume() + ") lack a wall with a rail above it", bad));
		}

		/** Exterior doors of storey 0: the roof door of a stair house is not a way in from the street. */
		List<Opening> streetDoors() {
			List<Opening> out = new ArrayList<>();
			Storey s0 = storeys.get(0);
			for (Opening o : ir.openings())
				if (o.kind() == OpeningKind.DOOR && (o.from().equals(Opening.OUTSIDE) != o.to().equals(Opening.OUTSIDE)) && s0 != null && o.box().y() >= s0.y() && o.box().y() < s0.y() + s0.height()) out.add(o);
			return out;
		}

		static boolean inAny(List<Box3> boxes, int x, int y, int z) {
			for (Box3 b : boxes) if (b.contains(x, y, z)) return true;
			return false;
		}

		boolean roofStructure(String volume) {
			int n = 0;
			for (Room r : ir.rooms()) {
				if (!r.volume().equals(volume)) continue;
				if (!ROOF_USES.contains(r.use())) return false;
				n++;
			}
			return n > 0;
		}
	}
}
