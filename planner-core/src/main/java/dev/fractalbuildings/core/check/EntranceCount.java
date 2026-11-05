package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Volume;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * DS-35 "More entrances" (and its workshop amendment), DS35DOORS: the {@code entrance-count} invariant and its report. A
 * building of 2+ storeys or {@link #BIG_FOOTPRINT}+ ground cells has at least two exterior ground-floor doors, and none
 * of them opens into a bedroom; a high-rise has doors on two faces and a main door 2+ wide; a workshop's living wing and
 * lean-to each have a door of their own (the cart door is the hall's). Towers, walls, stalls and market halls (entered by a
 * stair from the open floor) are exempt, as are one-room huts under the size line.
 */
public final class EntranceCount {
	private EntranceCount() {}

	/** Ground cells from which a building needs a second door whatever its height (matches the generator's {@code ExtraDoors}). */
	public static final int BIG_FOOTPRINT = 80;

	/** What was measured on one building. {@code applies}: the rule covers it. */
	public record Result(String type, boolean applies, int doors, int faces, int widest, List<String> bedroomDoors, List<String> bareVolumes, boolean big, boolean blocked, boolean wideRequired, int beds, int stores, int litStores) {
		/** The ways the building breaks the rule, empty if it keeps it. */
		public List<String> problems() {
			List<String> out = new ArrayList<>();
			if (!applies) return out;
			if (big && !blocked && doors < 2) out.add(doors + " exterior door(s) on a building of 2+ storeys or " + BIG_FOOTPRINT + "+ cells (needs 2)");
			if (type.equals("highrise") && big && !blocked && (faces < 2 || wideRequired && widest < 2)) out.add("high-rise has " + faces + " door face(s), widest door " + widest + " (needs 2 faces and, on all but the smallest lots, a main door 2+ wide)");
			if (!bareVolumes.isEmpty()) out.add("workshop " + String.join(", ", bareVolumes) + " has no door of its own");
			if (big && !bedroomDoors.isEmpty()) out.add("door(s) " + String.join(", ", bedroomDoors) + " open into a bedroom");
			return out;
		}
	}

	private static final Set<String> TYPES = Set.of("house", "shop", "hall", "temple", "highrise", "block", "workshop");
	/** How far below the door floor the ground outside may be for a short flight of steps to reach it. */
	public static final int MAX_DROP = 3; // a plain flight of at most 3 risers, the longest straight run DS-45 allows without a landing

	/**
	 * True if the ground leaves no wall cell of {@code volume} (null: of any ground mass) on a face other than the front (local north) with open
	 * ground outside at most the door floor and at most {@link #MAX_DROP} below it, and lot room straight out for that flight: a back or side door
	 * on every such face would be buried in the slope or stand high above it, so the generator walls it up and the count rules are not enforced
	 * for it. Every non-front face is tried, as the generator does (DS-35 on slopes, the user, 2026-10-28). {@code ground} is the effective ground.
	 */
	public static boolean blocked(PlacedBuilding b, TerrainProbe ground, List<Lot> lots, String volume) { return blocked(b, ground, ground, lots, volume); }

	/** As above with both grounds: a flight must reach the natural ground ({@code raw}: a dug-in well is no way out) and the ground as the site leaves it ({@code ground}: a cut makes the flight longer). */
	public static boolean blocked(PlacedBuilding b, TerrainProbe ground, TerrainProbe raw, List<Lot> lots, String volume) {
		int lowest = Integer.MAX_VALUE;
		for (Room r : b.shell().rooms()) lowest = Math.min(lowest, r.storey());
		if (lowest == Integer.MAX_VALUE || b.shell().storeys().isEmpty()) return false;
		Storey s0 = b.shell().storeys().get(Math.min(lowest, b.shell().storeys().size() - 1));
		int feet = b.oy() + s0.y() + 1;
		Set<Long> cells = new java.util.TreeSet<>(), own = new java.util.TreeSet<>(), inner = new java.util.TreeSet<>();
		for (Volume v : b.shell().volumes()) {
			if (v.box().y() != 0) continue;
			int area = (v.box().sx() - 2) * (v.box().sz() - 2);
			boolean upper = false; // the mass has a room above the ground storey
			for (Room r : b.shell().rooms()) upper |= r.volume().equals(v.id()) && r.storey() > lowest;
			boolean roomy = v.id().startsWith("lean") || area >= dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.LIVING_ROOM || area >= dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.MIN_ROOM && upper; // as the generator: a one-room mass too small for a door, a stove and a bed takes none (DS-48)
			for (int z = v.box().z(); z < v.box().maxZ(); z++)
				for (int x = v.box().x(); x < v.box().maxX(); x++) {
					cells.add(key(x, z));
					if (x > v.box().x() && x < v.box().maxX() - 1 && z > v.box().z() && z < v.box().maxZ() - 1) inner.add(key(x, z)); // inside the walls
					if (roomy && (volume == null || v.id().equals(volume))) own.add(key(x, z));
				}
		}
		Set<Long> stairs = new java.util.TreeSet<>(); // the cells of the ground-storey stairs and one cell round them (the generator keeps doors out of the stair core)
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.STAIR || o.box().y() >= s0.y() + s0.height() || o.box().maxY() <= s0.y()) continue;
			Box3 sb = o.box();
			for (int z = sb.z() - 1; z <= sb.maxZ(); z++) for (int x = sb.x() - 1; x <= sb.maxX(); x++) stairs.add(key(x, z)); // the stair core: the flight and the cells round it (its foot, its landing, the walk beside it)
		}
		int[] hall = {Integer.MIN_VALUE, Integer.MAX_VALUE}; // a high-rise's hall rows (its corridor upstairs): the generator puts the second door there, the one part of the lobby every plate keeps
		for (Room r : b.shell().rooms()) if (r.use().equals("corridor") && r.storey() == lowest + 1) for (Box3 p : r.parts()) { hall[0] = hall[0] == Integer.MIN_VALUE ? p.z() : Math.min(hall[0], p.z()); hall[1] = hall[1] == Integer.MAX_VALUE ? p.maxZ() : Math.max(hall[1], p.maxZ()); }
		List<int[]> doorsIn = new ArrayList<>(); // the inside cells of the ground doors there are: a second door must stand clear of them (as the generator's ExtraDoors)
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.DOOR || o.from().equals(Opening.OUTSIDE) == o.to().equals(Opening.OUTSIDE) || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			Facing out = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
			doorsIn.add(new int[] {o.box().x() - out.dx, o.box().z() - out.dz});
		}
		for (Facing f : dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.sides(b.shell().spec().type())) {
			Facing lat = f.rotate(1);
			for (long c : own) {
				int x = (int) (c >> 32), z = (int) c;
				int nx = x + f.dx, nz = z + f.dz;
				if (cells.contains(key(nx, nz)) || !inner.contains(key(x - f.dx, z - f.dz)) || !cells.contains(key(x + lat.dx, z + lat.dz)) || !cells.contains(key(x - lat.dx, z - lat.dz))) continue; // a wall cell with a room inside, not a corner
				if (cells.contains(key(nx + lat.dx, nz + lat.dz)) || cells.contains(key(nx - lat.dx, nz - lat.dz))) continue; // nor a re-entrant corner
				boolean near = false;
				for (int[] d : doorsIn) near |= Math.abs(d[0] - (x - f.dx)) + Math.abs(d[1] - (z - f.dz)) < 4;
				if (near || stairs.contains(key(x - f.dx, z - f.dz))) continue;
				String use = useAt(b, lowest, x - f.dx, z - f.dz);
				if (use == null || b.shell().spec().type().equals("highrise") && (!use.equals("lobby") || z - f.dz < hall[0] || z - f.dz >= hall[1])) continue; // a door opens into a room (the generator then makes it a day room, DS-35), a high-rise's into its lobby
				java.util.function.BiPredicate<Integer, Integer> free = (lx, lz) -> inLot(lots, b, lx, lz) && !cells.contains(key(lx, lz));
				int risers = Math.min(plainFlight((lx, lz) -> ground.floorY(b.placement().worldX(lx, lz), b.placement().worldZ(lx, lz)), free, feet, nx, nz, f), plainFlight((lx, lz) -> raw.floorY(b.placement().worldX(lx, lz), b.placement().worldZ(lx, lz)), free, feet, nx, nz, f));
				if (risers < 0) continue;
				int px = nx, pz = nz; // the ground just past the lot straight out more than 2 blocks above the sill: a bank (DoorRise rise below -2, DS-45)
				for (int k = 0; k < 64 && inLot(lots, b, px, pz); k++) { px += f.dx; pz += f.dz; }
				if (ground.floorY(b.placement().worldX(px, pz), b.placement().worldZ(px, pz)) > feet + 2) continue;
				return false;
			}
		}
		return true;
	}

	/**
	 * The descent (risers) of a plain straight flight (DS-45: at most {@link #MAX_DROP} without a landing) from a door whose outside cell is (x, z) on face
	 * {@code f}, standing level {@code feet}: the outside cell is a landing at the sill, each further cell a step one down until the ground is at most a block below the walker; every tread
	 * stands in {@code free} cells with a {@code free} cell on each side for its rail (DS-8). -1 if no such flight reaches the ground (a bank on the
	 * way, the lot line, the building, or more risers). {@code ground} gives the standing level of the ground at a local cell.
	 */
	public static int plainFlight(java.util.function.IntBinaryOperator ground, java.util.function.BiPredicate<Integer, Integer> free, int feet, int x, int z, Facing f) {
		Facing lat = f.rotate(1);
		int level = feet;
		for (int k = 0; k <= MAX_DROP + 1; k++) { // cell 0 is the landing at the sill (as the step search builds it), each further cell a step one down
			int cx = x + f.dx * k, cz = z + f.dz * k;
			if (!free.test(cx, cz)) return -1;
			int g = ground.applyAsInt(cx, cz);
			if (g > level) return -1; // the ground rises on the way: a bank
			if (g >= level - 1) return feet - g <= MAX_DROP ? Math.max(0, feet - g) : -1; // the walker steps onto the ground; the descent from the sill is the run (DoorRise)
			if (!free.test(cx + lat.dx, cz + lat.dz) || !free.test(cx - lat.dx, cz - lat.dz)) return -1; // the landing or a step here needs a rail each side
			if (k > 0) level--;
		}
		return -1;
	}

	public static boolean blocked(PlacedBuilding b, TerrainProbe ground, List<Lot> lots) { return blocked(b, ground, lots, null); }

	/** The use of the room of storey {@code storey} at local cell (x, z), null if the cell is in no room. */
	private static String useAt(PlacedBuilding b, int storey, int x, int z) {
		for (Room r : b.shell().rooms()) {
			if (r.storey() != storey) continue;
			for (Box3 p : r.parts()) if (x >= p.x() && x < p.maxX() && z >= p.z() && z < p.maxZ()) return r.use();
		}
		return null;
	}

	private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

	private static boolean inLot(List<Lot> lots, PlacedBuilding b, int lx, int lz) {
		int wx = b.placement().worldX(lx, lz), wz = b.placement().worldZ(lx, lz);
		for (Lot l : lots) if (l.containsColumn(wx, wz)) return true;
		return false;
	}

	/**
	 * As {@link #measure(PlacedBuilding, boolean)}, not enforcing a count rule only where {@link #blocked} says the ground leaves no non-front face a
	 * door could stand on (for the whole building, and for each workshop wing and lean-to on its own). Slopes are enforced like level lots
	 * (the user, REVIEW 2026-10-28: DS-35 holds on sloping lots).
	 */
	public static Result measure(PlacedBuilding b, TerrainProbe ground, TerrainProbe raw, List<Lot> lots) {
		return measure(b, blocked(b, ground, raw, lots, null), v -> blocked(b, ground, raw, lots, v)); // the natural ground and the site's: a dug-in well round the building is no way out, a cut makes the flight longer (the walker of DS-45)
	}

	public static Result measure(PlacedBuilding b) { return measure(b, false); }

	/** {@code blocked}: the ground allows no back or side door, so the count rules are not enforced. */
	public static Result measure(PlacedBuilding b, boolean blocked) { return measure(b, blocked, v -> blocked); }

	/** {@code volumeBlocked}: the ground leaves this workshop wing or lean-to (volume id) no face for a door of its own. */
	public static Result measure(PlacedBuilding b, boolean blocked, java.util.function.Predicate<String> volumeBlocked) {
		String type = b.shell().spec().type();
		List<Storey> storeys = b.shell().storeys();
		Map<String, Room> rooms = new TreeMap<>();
		int lowest = Integer.MAX_VALUE;
		for (Room r : b.shell().rooms()) {
			rooms.put(r.id(), r);
			lowest = Math.min(lowest, r.storey());
		}
		if (!TYPES.contains(type) || storeys.isEmpty() || lowest == Integer.MAX_VALUE) return new Result(type, false, 0, 0, 0, List.of(), List.of(), false, false, false, 0, 0, 0);
		Storey s0 = storeys.get(Math.min(lowest, storeys.size() - 1));
		boolean market = false;
		for (Opening o : b.shell().openings()) market |= o.kind() == OpeningKind.STAIR && o.from().equals(Opening.OUTSIDE);
		Set<Integer> cells = new java.util.TreeSet<>();
		for (Volume v : b.shell().volumes()) {
			if (v.box().y() != 0 || v.id().startsWith("post") || v.id().startsWith("leg")) continue; // posts and legs are no floor area
			for (int z = v.box().z(); z < v.box().maxZ(); z++)
				for (int x = v.box().x(); x < v.box().maxX(); x++) cells.add(z * 4096 + x);
		}
		boolean big = storeys.size() >= 2 || cells.size() >= BIG_FOOTPRINT;
		int doors = 0, widest = 0;
		Set<Facing> faces = new java.util.TreeSet<>();
		List<String> bedroom = new ArrayList<>();
		Set<String> doored = new java.util.TreeSet<>();
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			boolean fromOut = o.from().equals(Opening.OUTSIDE), toOut = o.to().equals(Opening.OUTSIDE);
			if (fromOut == toOut) continue;
			doors++;
			faces.add(toOut ? o.facing() : o.facing().rotate(2));
			Box3 bx = o.box();
			widest = Math.max(widest, Math.max(bx.sx(), bx.sz()));
			Room in = rooms.get(fromOut ? o.to() : o.from());
			if (in != null) {
				doored.add(in.volume());
				if (in.use().equals("bedroom")) bedroom.add(o.id() + " (" + in.id() + ")");
			}
		}
		List<String> bare = new ArrayList<>();
		if (type.equals("workshop"))
			for (Volume v : b.shell().volumes()) if ((v.id().startsWith("lean") || v.id().startsWith("wing") && (v.box().sx() - 2) * (v.box().sz() - 2) >= dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.LIVING_ROOM) && !doored.contains(v.id()) && !volumeBlocked.test(v.id())) bare.add(v.id()); // a lean-to of any size (a store takes a yard door, DS-35 amendment); a living wing only with room for a kitchen-hall (a lone bedroom keeps the hall person door)
		int beds = 0, stores = 0, lit = 0;
		for (dev.fractalbuildings.core.ir.Hook h : b.shell().hooks()) if (h.kind().equals("bed")) beds++;
		for (Room r : b.shell().rooms()) if (r.use().equals("store")) { stores++; for (dev.fractalbuildings.core.ir.Hook h : b.shell().hooks()) if (h.room().equals(r.id()) && h.kind().equals("ceiling_light")) { lit++; break; } }
		return new Result(type, !market, doors, faces.size(), widest, bedroom, bare, big, blocked, dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.wideMain(type, b.shell().spec().width(), b.shell().spec().depth()), beds, stores, lit);
	}

	/** Door counts by type for the report: type -> {buildings, buildings by door count 0,1,2,3+, rule-breaking}. */
	public static final class Report {
		private final Map<String, int[]> rows = new TreeMap<>();

		public synchronized void add(Result r) {
			if (!r.applies()) return;
			int[] row = rows.computeIfAbsent(r.type(), k -> new int[13]);
			row[0]++;
			row[1 + Math.min(3, r.doors())]++;
			if (!r.problems().isEmpty()) row[5]++;
			if (r.blocked()) row[6]++;
			if (r.beds() > 0) row[7]++;
			row[8] += r.stores();
			row[9] += r.litStores();
			if (r.big()) { row[10]++; if (r.doors() >= 2) row[11]++; if (r.blocked() && r.doors() < 2) row[12]++; }
		}

		public boolean isEmpty() { return rows.isEmpty(); }

		/** A markdown table: exterior ground-floor doors per building by type (DS-35). */
		public String table() {
			StringBuilder sb = new StringBuilder("| type | buildings | 0 doors | 1 | 2 | 3+ | breaking the rule | where the ground allows no back or side door (not enforced) | with a bed | lean-to stores | stores with a light | need 2 (2+ storeys or 80+ cells) | of those with 2+ | of those with 1, exempt (ground) |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
			for (Map.Entry<String, int[]> e : rows.entrySet()) {
				int[] r = e.getValue();
				sb.append("| ").append(e.getKey()).append(" | ").append(r[0]).append(" | ").append(r[1]).append(" | ").append(r[2]).append(" | ").append(r[3]).append(" | ").append(r[4]).append(" | ").append(r[5]).append(" | ").append(r[6]).append(" | ").append(r[7]).append(" | ").append(r[8]).append(" | ").append(r[9]).append(" | ").append(r[10]).append(" | ").append(r[11]).append(" | ").append(r[12]).append(" |\n");
			}
			return sb.toString();
		}
	}
}
