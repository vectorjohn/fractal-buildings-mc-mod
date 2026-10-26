package dev.fractalbuildings.core.ir;

import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Text floor plans of a building, for reviewers that read text (design-review agents, CLI {@code floorplan}).
 * A pure function of the IR: per storey a header, a top-down character grid of the standing layer (1 char per
 * block, or 2 with {@code wide}), the legend, a room table, the openings, the furniture hooks, and finally a
 * circulation summary and a roof height map. Output is plain ASCII, stable in order (no hash iteration), and
 * safe inside a markdown code fence. The grid is in the shell's local frame: +x to the right, the front
 * (z = 0) at the top.
 *
 * <p>Glyphs (grid): {@code #} exterior wall, {@code +} interior wall, {@code ~} window, {@code /} interior
 * door, {@code %} door to outside, {@code :} arch (doorless opening), {@code ^ > v <} stair rising from this
 * floor in that direction, {@code @} top step of a stair arriving from below, {@code O} opening in the floor
 * (stairwell, void), {@code .} room floor, {@code ,} floor outside any room (corridor, terrace), {@code |}
 * fence or railing, {@code !} post or beam, {@code =} stair block not in a stair opening, {@code `} outside
 * the building, uppercase letters and digits label rooms, lowercase letters are furniture kinds (see legend).
 */
public final class FloorPlanText {
	private FloorPlanText() {}

	/** Room label alphabet (no {@code O}, which means a floor opening). */
	static final String LABELS = "ABCDEFGHIJKLMNPQRSTUVWXYZ0123456789";
	/** Furniture letters (no {@code v}, which is a stair arrow). */
	static final String FURNITURE_LETTERS = "abcdefghijklmnopqrstuwxyz";

	/** All buildings of a scene (one if {@code buildingId} is given, which must exist). */
	public static String render(SceneDoc doc, String buildingId, boolean wide) {
		StringBuilder sb = new StringBuilder();
		boolean any = false;
		for (PlacedBuilding b : doc.buildings()) {
			if (buildingId != null && !b.id().equals(buildingId)) continue;
			Lot lot = null;
			for (Lot l : doc.lots()) if (!b.lots().isEmpty() && l.id().equals(b.lots().get(0))) lot = l;
			if (any) sb.append("\n\n");
			sb.append(render(b, lot, wide));
			any = true;
		}
		if (!any) throw new IllegalArgumentException("no building" + (buildingId == null ? "" : " '" + buildingId + "'") + " in the scene");
		return sb.toString();
	}

	/** One-line summaries of the scene's buildings, for choosing one. */
	public static String index(SceneDoc doc) {
		StringBuilder sb = new StringBuilder();
		for (PlacedBuilding b : doc.buildings()) {
			BuildingSpec s = b.shell().spec();
			sb.append(b.id()).append("  ").append(s.type()).append(' ').append(s.style()).append("  ")
					.append(b.shell().bounds().sx()).append('x').append(b.shell().bounds().sz()).append("  storeys ")
					.append(b.shell().storeys().size()).append("  rooms ").append(b.shell().rooms().size()).append('\n');
		}
		return sb.toString();
	}

	/** A bare shell (no placement), as building "shell". */
	public static String render(BuildingIr ir, boolean wide) {
		return render(new PlacedBuilding("shell", List.of("lot"), 0, 0, 0, 0, ir, List.of()), null, wide);
	}

	public static String render(PlacedBuilding pb, Lot lot, boolean wide) {
		BuildingIr ir = pb.shell();
		BuildingSpec spec = ir.spec();
		Box3 bd = ir.bounds();
		// Shell boxes only: site boxes (foundations, street steps) are not part of the plan.
		VoxelGrid g = VoxelGrid.of(new PlacedBuilding(pb.id(), pb.lots(), pb.ox(), pb.oy(), pb.oz(), pb.rotation(), ir, List.of()));
		StringBuilder out = new StringBuilder();
		out.append("# Building ").append(pb.id()).append(": ").append(spec.type()).append(", style ").append(spec.style())
				.append(", seed ").append(spec.specSeed()).append(", gen v").append(spec.genVersion()).append('\n');
		out.append("footprint ").append(bd.sx()).append(" x ").append(bd.sz()).append(" blocks (x by z), height ")
				.append(bd.sy()).append(", ").append(ir.storeys().size()).append(" storeys, roof ").append(spec.roof()).append('\n');
		out.append("placed at world (").append(pb.ox()).append(", ").append(pb.oy()).append(", ").append(pb.oz())
				.append("), rotation ").append(pb.rotation()).append(" quarter turns clockwise, front faces ")
				.append(Facing.NORTH.rotate(pb.rotation()).json()).append(", lots ").append(String.join(",", pb.lots())).append('\n');
		if (lot != null) {
			out.append("lot ").append(lot.id()).append(": ").append(lot.w()).append(" x ").append(lot.d()).append(" at (")
					.append(lot.x()).append(", ").append(lot.z()).append("), street side ").append(lot.front().json()).append('\n');
		}
		out.append("grid is the building's local frame: x to the right, z down, the front (z = 0, the street side) at the top; ")
				.append("a backtick is outside the building.\n");

		Map<String, Room> roomById = new LinkedHashMap<>();
		for (Room r : ir.rooms()) roomById.put(r.id(), r);
		Map<String, Character> kindLetter = furnitureLetters(ir.hooks());
		List<Storey> storeys = new ArrayList<>(ir.storeys());
		storeys.sort((a, b) -> Integer.compare(a.y(), b.y()));

		if (ir.rooms().isEmpty()) out.append("note: this shell has no rooms (an exterior-only scene; plan it with interiors to see layouts)\n");
		for (Storey s : storeys) {
			out.append('\n');
			floor(out, pb, g, s, storeys, roomById, kindLetter, wide);
		}
		out.append('\n');
		circulation(out, ir, roomById);
		out.append('\n');
		roof(out, ir, g, wide);
		return out.toString();
	}

	// ---- one storey ----

	private static void floor(StringBuilder out, PlacedBuilding pb, VoxelGrid g, Storey s, List<Storey> storeys,
			Map<String, Room> roomById, Map<String, Character> kindLetter, boolean wide) {
		BuildingIr ir = pb.shell();
		Box3 bd = ir.bounds();
		int w = bd.maxX(), d = bd.maxZ();
		int slab = s.y(), feet = s.y() + 1;
		String kind = s.y() < 0 ? "basement " : "";
		out.append("## ").append(kind).append("Storey ").append(s.index()).append(": floor slab y=").append(slab)
				.append(" (standing y=").append(feet).append("), floor-to-floor height ").append(s.height())
				.append(", clear air about ").append(s.height() - 1).append(" blocks, ceiling at y=").append(slab + s.height()).append('\n');

		List<Room> rooms = new ArrayList<>();
		for (Room r : ir.rooms()) if (r.storey() == s.index()) rooms.add(r);
		char[][] grid = new char[d][w];
		String[][] roomAt = new String[d][w];
		Map<String, Integer> area = new LinkedHashMap<>();
		for (Room r : rooms) area.put(r.id(), 0);

		// Opening glyphs first, so they can override walls.
		char[][] over = new char[d][w];
		for (Opening o : ir.openings()) {
			Box3 b = o.box();
			if (o.kind() == OpeningKind.STAIR) {
				Room from = roomById.get(o.from());
				if (from == null || from.storey() != s.index()) continue;
				char c = arrow(o.facing());
				for (int z = b.z(); z < b.maxZ(); z++) for (int x = b.x(); x < b.maxX(); x++) set(over, x, z, c);
			} else {
				if (b.y() < slab || b.y() >= slab + s.height()) continue;
				char c = switch (o.kind()) {
					case DOOR -> o.from().equals(Opening.OUTSIDE) || o.to().equals(Opening.OUTSIDE) ? '%' : '/';
					case ARCH -> ':';
					default -> '~';
				};
				for (int z = b.z(); z < b.maxZ(); z++) for (int x = b.x(); x < b.maxX(); x++) {
					// A window above a door's column (transom) must not hide the door.
					if (c == '~' && get(over, x, z) != 0) continue;
					set(over, x, z, c);
				}
			}
		}

		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				Room room = null;
				for (Room r : rooms) {
					for (Box3 p : r.parts()) {
						if (p.contains(x, feet, z) || (x >= p.x() && x < p.maxX() && z >= p.z() && z < p.maxZ() && p.y() <= feet && p.maxY() > feet)) {
							room = r;
							break;
						}
					}
					if (room != null) break;
				}
				roomAt[z][x] = room == null ? null : room.id();
				if (room != null) area.merge(room.id(), 1, Integer::sum);
			}
		}

		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				Role feetRole = g.get(x, feet, z);
				Role slabRole = g.get(x, slab, z);
				boolean inRoom = roomAt[z][x] != null;
				char c;
				if (over[z][x] != 0) {
					c = over[z][x];
				} else if (feetRole != Role.AIR && feetRole.solid) {
					c = switch (feetRole) {
						case WINDOW -> '~';
						case FENCE, RAIL -> '|';
						case BEAM -> '!';
						case STAIR -> '=';
						default -> exteriorWall(g, roomAt, x, feet, z, w, d) ? '#' : '+';
					};
				} else if (feetRole == Role.DOOR) {
					c = '/';
				} else if (slabRole == Role.STAIR) {
					c = '@';
				} else if (slabRole != Role.AIR && slabRole.solid) {
					c = inRoom ? '.' : ',';
				} else if (inRoom || insideVolume(ir, x, z, s)) {
					c = 'O';
				} else {
					c = '`';
				}
				grid[z][x] = c;
			}
		}
		// A column with no solid feet block that is air above a missing slab but not in any room and not in a volume stays '`'.

		// Furniture: lights first, so any other piece in the same column wins.
		List<Hook> hooks = new ArrayList<>();
		for (Hook h : ir.hooks()) if (h.y() >= slab && h.y() < slab + s.height()) hooks.add(h);
		for (int pass = 0; pass < 2; pass++) {
			for (Hook h : hooks) {
				if (kindIsLight(h) != (pass == 0)) continue;
				char cur = grid[h.z()][h.x()];
				boolean overLight = pass == 1 && Character.isLowerCase(cur);
				if (cur == '.' || cur == ',' || overLight) grid[h.z()][h.x()] = kindLetter.get(h.kind());
			}
		}

		// Room labels.
		Map<String, Character> label = new LinkedHashMap<>();
		int li = 0;
		for (Room r : rooms) {
			char lab = li < LABELS.length() ? LABELS.charAt(li) : '?';
			li++;
			label.put(r.id(), lab);
			int minx = Integer.MAX_VALUE, maxx = -1, minz = Integer.MAX_VALUE, maxz = -1;
			for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) if (r.id().equals(roomAt[z][x])) {
				minx = Math.min(minx, x); maxx = Math.max(maxx, x); minz = Math.min(minz, z); maxz = Math.max(maxz, z);
			}
			if (maxx < 0) continue;
			double cx = (minx + maxx) / 2.0, cz = (minz + maxz) / 2.0;
			int bx = -1, bz = -1;
			double best = Double.MAX_VALUE;
			for (int z = minz; z <= maxz; z++) for (int x = minx; x <= maxx; x++) {
				if (!r.id().equals(roomAt[z][x]) || grid[z][x] != '.') continue;
				double dist = (x - cx) * (x - cx) + (z - cz) * (z - cz);
				if (dist < best) { best = dist; bx = x; bz = z; }
			}
			if (bx >= 0) grid[bz][bx] = lab;
		}

		grid(out, grid, w, d, wide);
		legend(out, grid, kindLetter, ir.hooks(), s);

		out.append("\nRooms on storey ").append(s.index()).append(":\n");
		out.append(String.format("%-4s %-5s %-12s %-6s %-8s %s%n", "ch", "id", "use", "area", "volume", "connects to (opening, dir)"));
		for (Room r : rooms) {
			StringBuilder con = new StringBuilder();
			for (Opening o : ir.openings()) {
				if (o.kind() == OpeningKind.WINDOW) continue;
				String other = o.from().equals(r.id()) ? o.to() : o.to().equals(r.id()) ? o.from() : null;
				if (other == null) continue;
				if (con.length() > 0) con.append("; ");
				Room orm = roomById.get(other);
				con.append(orm == null ? other : other + (orm.storey() == s.index() ? "" : "@storey" + orm.storey()))
						.append(" (").append(o.kind().json()).append(' ').append(o.id()).append(", ")
						.append(o.from().equals(r.id()) ? "leave heading " : "enter heading ").append(o.facing().json()).append(')');
			}
			out.append(String.format("%-4s %-5s %-12s %-6d %-8s %s%n", label.get(r.id()), r.id(), r.use(), area.get(r.id()), r.volume(),
					con.length() == 0 ? "(none)" : con));
		}
		if (rooms.isEmpty()) out.append("(no rooms)\n");

		// Openings of this floor.
		List<String> ol = new ArrayList<>();
		for (Opening o : ir.openings()) {
			Box3 b = o.box();
			Room fr = roomById.get(o.from());
			boolean here;
			if (o.kind() == OpeningKind.STAIR) {
				Room to = roomById.get(o.to());
				here = fr != null && fr.storey() == s.index() || to != null && to.storey() == s.index();
			} else {
				here = b.y() >= slab && b.y() < slab + s.height();
			}
			if (!here) continue;
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%-4s %-6s at x=%d..%d z=%d..%d", o.id(), o.kind().json(), b.x(), b.maxX() - 1, b.z(), b.maxZ() - 1));
			if (o.kind() == OpeningKind.STAIR) {
				Room to = roomById.get(o.to());
				boolean departs = fr != null && fr.storey() == s.index();
				sb.append(" ascends ").append(o.facing().json()).append(" over ").append(b.sy()).append(" blocks: ")
						.append(departs ? "goes UP from " + o.from() + " to " + o.to() + (to == null ? "" : " (storey " + to.storey() + ")")
								: "arrives from below (" + o.from() + ") at " + o.to() + ", top step in the floor opening");
			} else {
				int sill = b.y() - feet, head = b.maxY() - feet;
				sb.append(" y=").append(b.y()).append("..").append(b.maxY() - 1).append(" (").append(sill).append("..").append(head)
						.append(" above floor surface), faces ").append(o.facing().json()).append(", ")
						.append(o.from()).append(" -> ").append(o.to());
				if (o.kind() == OpeningKind.WINDOW) sb.append(", sill +").append(sill).append(" head +").append(head);
			}
			ol.add(sb.toString());
		}
		out.append("\nOpenings (heights relative to the floor surface (top of the slab); \"faces\" is the direction of travel from -> to):\n");
		if (ol.isEmpty()) out.append("(none)\n");
		for (String l : ol) out.append(l).append('\n');

		// Furniture of this floor.
		out.append("\nFurniture hooks:\n");
		if (hooks.isEmpty()) out.append("(none)\n");
		for (Hook h : hooks) {
			out.append(String.format("%-5s %c %-10s room %-4s at x=%d z=%d y=%d facing %s%n", h.id(), kindLetter.get(h.kind()), h.kind(), h.room(),
					h.x(), h.z(), h.y(), h.facing().json()));
		}
	}

	private static boolean kindIsLight(Hook h) { return h.kind().equals("light") || h.kind().equals("ceiling_light"); }

	private static boolean insideVolume(BuildingIr ir, int x, int z, Storey s) {
		for (Volume v : ir.volumes()) {
			Box3 b = v.box();
			if (x >= b.x() && x < b.maxX() && z >= b.z() && z < b.maxZ() && b.y() <= s.y() + 1 && b.maxY() > s.y() + 1) return true;
		}
		return false;
	}

	/** A wall cell with any 4-neighbour outside the grid or that is neither wall-like nor in a room: the building's outside. */
	private static boolean exteriorWall(VoxelGrid g, String[][] roomAt, int x, int feet, int z, int w, int d) {
		int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] n : nb) {
			int nx = x + n[0], nz = z + n[1];
			if (nx < 0 || nz < 0 || nx >= w || nz >= d) return true;
			Role r = g.get(nx, feet, nz);
			boolean solid = r != Role.AIR && r.solid;
			if (!solid && roomAt[nz][nx] == null && r != Role.DOOR) return true;
		}
		return false;
	}

	private static char arrow(Facing f) {
		return switch (f) {
			case NORTH -> '^';
			case EAST -> '>';
			case SOUTH -> 'v';
			default -> '<';
		};
	}

	private static void set(char[][] a, int x, int z, char c) {
		if (z >= 0 && z < a.length && x >= 0 && x < a[0].length) a[z][x] = c;
	}

	private static char get(char[][] a, int x, int z) {
		return z >= 0 && z < a.length && x >= 0 && x < a[0].length ? a[z][x] : 0;
	}

	/** Letters per furniture kind: the first free letter of the kind's name, kinds in alphabetical order. */
	static Map<String, Character> furnitureLetters(List<Hook> hooks) {
		TreeSet<String> kinds = new TreeSet<>();
		for (Hook h : hooks) kinds.add(h.kind());
		Map<String, Character> m = new TreeMap<>();
		TreeSet<Character> used = new TreeSet<>();
		for (String k : kinds) {
			char pick = 0;
			for (char c : k.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
				if (FURNITURE_LETTERS.indexOf(c) >= 0 && !used.contains(c)) { pick = c; break; }
			}
			if (pick == 0) {
				for (char c : FURNITURE_LETTERS.toCharArray()) if (!used.contains(c)) { pick = c; break; }
			}
			if (pick == 0) pick = '?';
			used.add(pick);
			m.put(k, pick);
		}
		return m;
	}

	// ---- grid drawing ----

	private static void grid(StringBuilder out, char[][] grid, int w, int d, boolean wide) {
		int unit = wide ? 2 : 1;
		StringBuilder tens = new StringBuilder("     "), ones = new StringBuilder("     ");
		for (int x = 0; x < w; x++) {
			tens.append(x % 10 == 0 && w > 10 ? String.valueOf(x / 10 % 10) : " ");
			ones.append(x % 10);
			if (wide) { tens.append(' '); ones.append(' '); }
		}
		if (w > 10) out.append(trimEnd(tens)).append('\n');
		out.append(trimEnd(ones)).append("   (x)\n");
		for (int z = 0; z < d; z++) {
			StringBuilder row = new StringBuilder(String.format("%3d |", z));
			for (int x = 0; x < w; x++) {
				char c = grid[z][x];
				row.append(c);
				if (wide) row.append(c == '#' || c == '+' || c == '~' ? c : ' ');
			}
			out.append(row).append('\n');
		}
		if (unit == 2) out.append("(wide: 2 chars per block)\n");
	}

	private static String trimEnd(StringBuilder sb) {
		int n = sb.length();
		while (n > 0 && sb.charAt(n - 1) == ' ') n--;
		return sb.substring(0, n);
	}

	private static void legend(StringBuilder out, char[][] grid, Map<String, Character> kindLetter, List<Hook> hooks, Storey s) {
		out.append("Legend: # exterior wall, + interior wall, ~ window, / interior door, % door to outside, : arch (doorless), ")
				.append("^ > v < stair rising from here (direction of ascent), @ top step of a stair arriving from below, ")
				.append("O opening in the floor (stairwell), . room floor, , floor outside a room, | fence/rail, ! post/beam, ")
				.append("= other stair block, ` outside; A-Z/0-9 room labels (table below)");
		if (!kindLetter.isEmpty()) {
			out.append("; furniture: ");
			boolean first = true;
			for (var e : kindLetter.entrySet()) {
				if (!first) out.append(", ");
				first = false;
				out.append(e.getValue()).append(' ').append(e.getKey());
			}
		}
		out.append('\n');
	}

	// ---- whole-building sections ----

	private static void circulation(StringBuilder out, BuildingIr ir, Map<String, Room> roomById) {
		out.append("## Circulation\n");
		Map<String, List<String>> adj = new LinkedHashMap<>();
		adj.put(Opening.OUTSIDE, new ArrayList<>());
		for (Room r : ir.rooms()) adj.put(r.id(), new ArrayList<>());
		List<String> entrances = new ArrayList<>();
		for (Opening o : ir.openings()) {
			if (o.kind() == OpeningKind.WINDOW) continue;
			if (!adj.containsKey(o.from()) || !adj.containsKey(o.to())) continue;
			adj.get(o.from()).add(o.to());
			adj.get(o.to()).add(o.from());
			if ((o.from().equals(Opening.OUTSIDE) || o.to().equals(Opening.OUTSIDE)) && o.kind() != OpeningKind.STAIR) {
				String in = o.from().equals(Opening.OUTSIDE) ? o.to() : o.from();
				entrances.add(o.id() + " (" + o.kind().json() + ") into " + in + " (" + roomById.get(in).use() + ") at x="
						+ o.box().x() + " z=" + o.box().z() + ", facing " + o.facing().json());
			}
		}
		out.append("Entrances from outside:").append(entrances.isEmpty() ? " NONE" : "").append('\n');
		for (String e : entrances) out.append("  ").append(e).append('\n');
		TreeSet<String> seen = new TreeSet<>();
		ArrayDeque<String> q = new ArrayDeque<>();
		seen.add(Opening.OUTSIDE);
		q.add(Opening.OUTSIDE);
		while (!q.isEmpty()) {
			String c = q.poll();
			for (String n : adj.get(c)) if (seen.add(n)) q.add(n);
		}
		List<String> unreachable = new ArrayList<>();
		for (Room r : ir.rooms()) if (!seen.contains(r.id())) unreachable.add(r.id() + " (" + r.use() + ", storey " + r.storey() + ")");
		out.append("Rooms not reachable from outside through doors, arches and stairs: ")
				.append(unreachable.isEmpty() ? "none" : String.join(", ", unreachable)).append('\n');
		int windows = 0;
		for (Opening o : ir.openings()) if (o.kind() == OpeningKind.WINDOW) windows++;
		out.append("Windows: ").append(windows).append(", rooms: ").append(ir.rooms().size()).append(", furniture hooks: ").append(ir.hooks().size()).append('\n');
	}

	private static void roof(StringBuilder out, BuildingIr ir, VoxelGrid g, boolean wide) {
		out.append("## Roof\n");
		if (ir.roofs().isEmpty()) out.append("(no roof records)\n");
		for (Roof r : ir.roofs()) {
			out.append("roof ").append(r.form()).append(" over ").append(r.volume()).append(", base y=").append(r.baseY()).append(", pitch ")
					.append(r.rise()).append('/').append(r.run()).append(", ridge ").append(r.ridgeAxis() == null ? "none" : r.ridgeAxis().name().toLowerCase(java.util.Locale.ROOT)).append('\n');
		}
		Box3 bd = ir.bounds();
		int w = bd.maxX(), d = bd.maxZ();
		char[][] grid = new char[d][w];
		for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
			int top = -1;
			for (int y = g.minY + g.sy - 1; y >= g.minY && top < 0; y--) {
				Role r = g.get(x, y, z);
				if (r != Role.AIR) top = y;
			}
			grid[z][x] = top < 0 ? '`' : top > 35 ? '+' : Character.forDigit(top, 36);
		}
		out.append("Top block height map (local y of the highest block in each column, base 36: 0-9 then a=10 ... z=35, + above; ` empty):\n");
		grid(out, grid, w, d, wide);
	}
}
