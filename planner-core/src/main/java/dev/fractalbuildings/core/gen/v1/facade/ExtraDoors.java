package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * More than one way in (DS-35 and its workshop amendment, DS35DOORS). {@link #add} picks the secondary doors of a
 * building after its main entrances are chosen, and turns the window bay each takes into a door bay in the layouts.
 * A secondary door is one wide and plain (no surround, DS-26), on a face away from the street where there is one
 * (back first, then the sides), on storey 0, with a free cell inside that is not a stair core.
 *
 * <p>Who gets what: a house of 2+ storeys or 80+ ground cells gets a back or side door (the interior pass makes the
 * room behind it the kitchen, {@link dev.fractalbuildings.core.gen.v1.interior.RoomProgram.Slot#back}); a shop with
 * upper floors or 80+ cells a house door at the back (into a hall that holds the stair); halls and temples a side
 * door; a high-rise a door on a second face (its main door is 2 wide, see {@code FacadePass.entrance}); a block one if
 * it has fewer than two; a workshop one door each for its lean-to (to the yard) and its living wing (from the kitchen),
 * besides the cart door. Towers, stalls, market halls and walls keep what they have.
 */
public final class ExtraDoors {
	private ExtraDoors() {}

	/** Ground cells from which a building needs a second door whatever its height. */
	public static final int BIG_FOOTPRINT = 80;

	/**
	 * Where on the ground a secondary door may stand, from a first placement of the building (DS-35 on sloping lots, the user, 2026-10-28): a score for the
	 * cell just outside a door on a face (shell-local x, z), higher is better, {@link Integer#MIN_VALUE} for no door there. {@code GeneratorV1} sets
	 * one on this thread when the placement walled a door up, and asks again; null (the first try) leaves the choice to the bay order alone.
	 */
	public interface Survey { int score(Facing side, int x, int z); }

	/** Test switch: true on this thread builds no secondary door at all (the output before DS35DOORS), to compare a building with and without its doors. */
	public static final ThreadLocal<Boolean> OFF = ThreadLocal.withInitial(() -> false);

	/** The survey of this thread's retry, or null. Always removed after the retry. */
	public static final ThreadLocal<Survey> SURVEY = new ThreadLocal<>();

	/** Kinds of a workshop's masses that get a person door of their own. */
	public static final String LEAN_TO = "lean-to", WING = "wing";

	/** Cells of the building's ground footprint (the union of its masses at storey 0, walls included). */
	public static int footprint(Massing m) {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = 0, z1 = 0;
		for (Mass a : m.masses()) {
			if (a.from() > 0) continue;
			x0 = Math.min(x0, a.x());
			z0 = Math.min(z0, a.z());
			x1 = Math.max(x1, a.maxX());
			z1 = Math.max(z1, a.maxZ());
		}
		if (x1 <= x0) return 0;
		int n = 0;
		for (int z = z0; z < z1; z++)
			for (int x = x0; x < x1; x++)
				for (Mass a : m.masses()) if (a.from() == 0 && a.contains(x, z)) { n++; break; }
		return n;
	}

	/** Smallest interior (cells) of a mass that takes a secondary door: a 3x3 wing cannot hold a door, a stove or a bed and keep a lane (DS-48). */
	public static final int MIN_ROOM = 12;

	/** Interior cells from which a one-storey living mass holds a door, a stove and a bed and keeps a lane (DS-48); the {@code entrance-count} check asks a workshop wing this big for a door of its own. */
	public static final int LIVING_ROOM = 20;

	/** True if a mass may take a secondary door: a lean-to store always (a 3x3 store keeps its light, chest and counter clear of the door, DS-35 workshop amendment), a living mass with room for the door, a stove and a bed. */
	public static boolean roomy(Mass a) {
		if (a.kind().equals(LEAN_TO)) return true;
		int area = (a.sx() - 2) * (a.sz() - 2);
		return area >= LIVING_ROOM || area >= MIN_ROOM && a.to() - a.from() >= 2; // a one-storey room must hold the door, a stove and a bed; a two-storey one sleeps upstairs
	}

	/** The faces a type's secondary door may take, best first (the {@code entrance-count} check follows the same order). */
	public static Facing[] sides(String type) {
		return switch (type) {
			case "hall", "temple" -> new Facing[] {Facing.EAST, Facing.WEST, Facing.SOUTH};
			case "highrise" -> new Facing[] {Facing.EAST, Facing.WEST, Facing.SOUTH};
			case "shop", "block" -> new Facing[] {Facing.SOUTH, Facing.EAST, Facing.WEST};
			default -> new Facing[] {Facing.SOUTH, Facing.EAST, Facing.WEST}; // never the front: a front-face door is not a secondary door to the instance pass
		};
	}

	/**
	 * True if a high-rise of this envelope has a 2-wide main entrance (DS-35): its landing is as wide as the door (DS-8 rails inside the lot), which the
	 * smallest lots on steep ground have no room for, so an envelope within 2 of the type minimum keeps a 1-wide door. {@code FacadePass.entrance} centres it on the drawn front wall: 2 leaves on an even front, 3 on an odd one.
	 */
	public static boolean wideMain(String type, int width, int depth) {
		if (!type.equals("highrise")) return false;
		var lim = dev.fractalbuildings.core.gen.v1.Types.get(type).limits();
		return width >= lim.minWidth() + 3 && depth >= lim.minDepth() + 2;
	}

	/** True if the building needs a second door by size: 2+ storeys or {@link #BIG_FOOTPRINT}+ ground cells. */
	public static boolean big(Massing m) { return m.storeys() >= 2 || footprint(m) >= BIG_FOOTPRINT; }

	/**
	 * The secondary doors for this building; {@code layouts} are edited in place (the chosen bays become door bays).
	 * {@code existing} are the main and other-mass entrances already chosen.
	 */
	public static List<FacadePlan.Entrance> add(BuildContext c, List<FacadePlan.Layout> layouts, List<FacadePlan.Entrance> existing) {
		if (OFF.get()) return List.of();
		Massing m = c.massing();
		String type = c.type().id();
		List<FacadePlan.Entrance> all = new ArrayList<>(existing);
		List<FacadePlan.Entrance> out = new ArrayList<>();
		switch (type) {
			case "house" -> { if (big(m)) take(c, m, layouts, all, out, null, sides("house"), false, 0); }
			case "shop" -> {
				if (!m.main().kind().equals("market") && big(m)) take(c, m, layouts, all, out, null, sides("shop"), false, 0);
			}
			case "hall", "temple" -> take(c, m, layouts, all, out, null, sides("hall"), true, 0);
			case "highrise" -> {
				Facing main = existing.get(0).face().side();
				var block = dev.fractalbuildings.core.gen.v1.types.highrise.TowerBlock.of(m, dev.fractalbuildings.core.gen.v1.types.highrise.TowerBlock.SD);
				takeIn(c, m, layouts, all, out, null, sides("highrise"), true, 0, (x, z) -> block == null || z >= block.hallZ0() && z < block.hallZ1(), main); // into the lobby (the hall rows), the one room the plates always cover there
			}
			case "block" -> { if (existing.size() < 2) take(c, m, layouts, all, out, null, sides("block"), false, 0); }
			case "workshop" -> {
				int i = 0;
				for (Mass a : m.masses()) {
					if (!a.kind().equals(LEAN_TO) && !a.kind().equals(WING)) continue;
					take(c, m, layouts, all, out, a.id(), sides("workshop"), false, ++i);
				}
				if (out.isEmpty() && big(m)) take(c, m, layouts, all, out, m.main().id(), sides("workshop"), false, 9); // no wing or lean-to with room for a door: the hall's own person door (DS-35: a cart door and a person door)
			}
			default -> {}
		}
		return out;
	}

	/** Picks one door on a face of {@code mass} (null: any mass) in the order of {@code sides}, best first; {@code centred} takes the bay nearest the face centre, else one is drawn. */
	private static void take(BuildContext c, Massing m, List<FacadePlan.Layout> layouts, List<FacadePlan.Entrance> all, List<FacadePlan.Entrance> out,
			String mass, Facing[] sides, boolean centred, int index, Facing... notSide) {
		takeIn(c, m, layouts, all, out, mass, sides, centred, index, (x, z) -> true, notSide);
	}

	/** As {@code take}, only at cells whose inside cell (local x, z) {@code inside} accepts. */
	private static void takeIn(BuildContext c, Massing m, List<FacadePlan.Layout> layouts, List<FacadePlan.Entrance> all, List<FacadePlan.Entrance> out,
			String mass, Facing[] sides, boolean centred, int index, java.util.function.BiPredicate<Integer, Integer> inside, Facing... notSide) {
		Survey sv = SURVEY.get();
		int bestScore = Integer.MIN_VALUE;
		List<int[]> best = new ArrayList<>();
		for (int li = 0; li < layouts.size(); li++) {
			FacadePlan.Layout l = layouts.get(li);
			Face f = l.face();
			if (f.storey() != 0 || (mass != null && !f.mass().equals(mass)) || !roomy(m.mass(f.mass()))) continue;
			int rank = -1;
			for (int k = 0; k < sides.length; k++) if (sides[k] == f.side()) rank = k;
			if (rank < 0) continue;
			boolean avoid = false;
			for (Facing n : notSide) avoid |= n == f.side();
			for (int b = 0; b < l.bays().length; b++) {
				if (!FacadePlan.WINDOW.equals(l.kinds().get(b))) continue;
				int p = -1, ground = Integer.MIN_VALUE; // the cell of the bay nearest its centre with a free cell inside (a small wing's stair core can take the middle); with a survey, the one whose ground suits a door best
				for (int k = 0; k < l.bays()[b] + 2 && (p < 0 || sv != null); k++) {
					int q = k < l.bays()[b] ? l.centre(b) + ((k % 2 == 0) ? k / 2 : -(k + 1) / 2) : k == l.bays()[b] ? f.a() : f.b() - 1; // then the wall cell where another mass joins (not a corner of the building)
					boolean inBay = q >= l.bayStart(b) && q < l.bayStart(b) + l.bays()[b];
					boolean junction = k >= l.bays()[b] && (q == f.a() ? !f.convexA() && b == 0 : !f.convexB() && b == l.bays().length - 1);
					if (!(inBay || junction) || m.inCore(0, f.insideX(q), f.insideZ(q)) || !inside.test(f.insideX(q), f.insideZ(q)) || crowded(all, f, q)) continue;
					int g = sv == null ? 0 : sv.score(f.side(), f.outsideX(q), f.outsideZ(q));
					if (g == Integer.MIN_VALUE || g <= ground) continue;
					p = q;
					ground = g;
				}
				if (p < 0) continue;
				int score = (sides.length - rank) * 100 - (avoid ? 400 : 0) - (c.spec().street(f.side()) ? 150 : 0);
				if (centred) score -= Math.abs(2 * p - (f.a() + f.b() - 1)) * 3;
				if (sv != null) score += ground * 1000; // on a slope the ground decides: a door that needs no long flight on any non-front face before the preferred face
				if (score > bestScore) { bestScore = score; best.clear(); }
				if (score == bestScore) best.add(new int[] {li, b, p});
			}
		}
		if (best.isEmpty()) { // no bay has a free cell inside (a big stair core): any free wall cell of an allowed face, nearest its centre
			FacadePlan.Entrance pick = null;
			long bestD = Long.MAX_VALUE;
			for (Face f : c.faces()) {
				if (f.storey() != 0 || (mass != null && !f.mass().equals(mass)) || !roomy(m.mass(f.mass()))) continue;
				int rank = -1;
				for (int k = 0; k < sides.length; k++) if (sides[k] == f.side()) rank = k;
				if (rank < 0) continue;
				for (int p = f.a() + 1; p < f.b() - 1; p++) {
					if (m.inCore(0, f.insideX(p), f.insideZ(p)) || !inside.test(f.insideX(p), f.insideZ(p)) || crowded(all, f, p)) continue;
					int g = sv == null ? 0 : sv.score(f.side(), f.outsideX(p), f.outsideZ(p));
					if (g == Integer.MIN_VALUE) continue;
					long d = -(long) g * 100000 + rank * 1000 + Math.abs(2 * p - (f.a() + f.b() - 1));
					if (d < bestD) { bestD = d; pick = new FacadePlan.Entrance(f, p); }
				}
			}
			if (pick != null) { out.add(pick); all.add(pick); }
			return;
		}
		int[] pick = best.size() == 1 ? best.get(0) : best.get(c.draws("facade/extra-door", index, out.size()).below(best.size()));
		FacadePlan.Layout l = layouts.get(pick[0]);
		List<String> kinds = new ArrayList<>(l.kinds());
		kinds.set(pick[1], FacadePlan.DOOR);
		layouts.set(pick[0], l.withKinds(kinds));
		FacadePlan.Entrance e = new FacadePlan.Entrance(l.face(), pick[2]);
		out.add(e);
		all.add(e);
	}

	/** True if an existing door is closer than 4 cells (Manhattan, inside cells) (along the same face or round a corner of the same mass). */
	private static boolean crowded(List<FacadePlan.Entrance> all, Face f, int p) {
		int x = f.insideX(p), z = f.insideZ(p);
		for (FacadePlan.Entrance e : all) {
			if (!e.face().mass().equals(f.mass())) continue;
			int ex = e.face().insideX(e.pos()), ez = e.face().insideZ(e.pos());
			if (Math.abs(ex - x) + Math.abs(ez - z) < 4) return true;
		}
		return false;
	}
}
