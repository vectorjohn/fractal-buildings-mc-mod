package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.interior.FloorPlan;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Door;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Leaf;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Result;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Spec;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Template;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plate templates (B2): synthetic plates, all templates, structural properties of every plan. */
class TowerPlateTest {
	static {
		TowerPlate.DEBUG = true;
	}

	static final Set<String> WINDOWED = Set.of("flat", "bedroom", "office", "lobby", "banquet", "open");
	static final Set<String> EXEMPT = Set.of("corridor", "entry", "stairwell", "storage", "plant");

	/** A synthetic plate: block at a legal position, window cells every 2 to 4 along the boundary. */
	static Spec plate(long seed, Template t) {
		for (int attempt = 0; ; attempt++) {
			Draws r = new Draws(Hash64.hash(seed, "plate", attempt));
			int db = 5 + r.below(10), hw = 2 + r.below(2), sd = 3 + r.below(2), len = 5 + r.below(5);
			int behind = r.below(3) == 0 ? 0 : 3 + r.below(4);
			int leftL = r.below(4) == 0 ? -1 : 3 + r.below(12), rightL = r.below(4) == 0 ? -1 : 3 + r.below(12);
			if (leftL < 0 && rightL < 0) continue;
			boolean shaftRight = r.below(2) == 0;
			int blockL = leftL < 0 ? -1 : leftL;
			int minX0 = blockL + 1;
			int inner = len + 4;
			int maxX1 = minX0 + inner;
			int w = rightL < 0 ? maxX1 : maxX1 + 1 + rightL;
			int hallZ0 = db + 1, hallZ1 = hallZ0 + hw, sz0 = hallZ1 + 1, sz1 = sz0 + sd;
			int d = behind > 0 ? sz1 + 1 + behind : sz1;
			if (w < 13 || w > 40 || d < 12 || d > 40) continue;
			int[] stair = shaftRight ? new int[] {minX0, sz0, minX0 + len, sz1} : new int[] {minX0 + 4, sz0, minX0 + 4 + len, sz1};
			int[] shaft = shaftRight ? new int[] {minX0 + len + 1, sz0, minX0 + len + 4, sz0 + 3} : new int[] {minX0, sz0, minX0 + 3, sz0 + 3};
			boolean[][] res = new boolean[w][d];
			for (int x = 1 + r.below(2); x < w; x += 2 + r.below(3)) { res[x][0] = true; res[x][d - 1] = true; }
			for (int z = 1 + r.below(2); z < d; z += 2 + r.below(3)) { res[0][z] = true; res[w - 1][z] = true; }
			// the massing keeps partitions off windows: the hall lines and every wall of the core block end on piers
			for (int z : new int[] {hallZ0 - 1, hallZ1}) { res[0][z] = false; res[w - 1][z] = false; }
			int mid = shaftRight ? minX0 + len : minX0 + 3;
			for (int c : new int[] {blockL, maxX1, mid}) if (c >= 0 && c < w) { res[c][d - 1] = false; res[c][0] = false; }
			if (sz1 < d) { res[0][sz1] = false; res[w - 1][sz1] = false; }
			int[] targets = {6, 10, 14};
			String use = switch (t) { case GRAND -> r.below(2) == 0 ? "lobby" : "open"; default -> ""; };
			return new Spec(w, d, stair, shaft, hallZ0, hallZ1, res, t, use, targets[r.below(3)], r.below(4) == 0 ? "office" : "flat", r.below(4) != 0,
					r.below(w));
		}
	}

	static boolean inShaft(Spec s, int x, int z) {
		return x >= s.shaft()[0] && x < s.shaft()[2] && z >= s.stairwell()[1] && z < s.stairwell()[3];
	}

	/** All structural properties of one plan; returns an error text or null. */
	static String check(Spec s, Result res) {
		FloorPlan p = res.plan();
		String[] uses = res.uses();
		int n = p.leaves().size();
		boolean[][] cutCell = new boolean[p.w()][p.d()];
		for (var c : p.cuts()) for (int k = 0; k < c.len(); k++) cutCell[c.constX() ? c.c() : c.lo() + k][c.constX() ? c.lo() + k : c.c()] = true;
		for (int x = 0; x < p.w(); x++)
			for (int z = 0; z < p.d(); z++) {
				int i = p.leafAt()[x][z];
				if (i >= 0) {
					if (!p.leaves().get(i).contains(x, z)) return "leafAt disagrees at " + x + "," + z;
					if (cutCell[x][z]) return "cut over a room cell " + x + "," + z;
				} else {
					boolean shaft = s.template() != Template.STAIRHOUSE && inShaft(s, x, z);
					if (!cutCell[x][z] && !shaft) return "cell " + x + "," + z + " is no room, cut or shaft";
					if (cutCell[x][z] && s.reserved()[x][z]) return "cut on window cell " + x + "," + z;
				}
			}
		for (int i = 0; i < n; i++) {
			Leaf l = p.leaves().get(i);
			int min = Math.min(l.w(), l.d()), max = Math.max(l.w(), l.d());
			boolean strip = uses[i].equals("corridor") || uses[i].equals("entry");
			if (min < (strip ? 2 : 3)) return uses[i] + " leaf too thin: " + l;
			if (Set.of("flat", "bedroom", "office", "kitchen").contains(uses[i]) && max > 2 * min) return uses[i] + " aspect > 2: " + l;
			boolean window = false, boundary = false;
			for (int x = l.x(); x < l.x() + l.w(); x++)
				for (int z = l.z(); z < l.z() + l.d(); z++) {
					window |= s.reserved()[x][z];
					boundary |= x == 0 || z == 0 || x == p.w() - 1 || z == p.d() - 1;
				}
			if (WINDOWED.contains(uses[i]) && !window) return uses[i] + " without a window cell: " + l;
			if (!EXEMPT.contains(uses[i]) && !boundary) return uses[i] + " off the facade: " + l;
		}
		for (Door dd : p.doors()) {
			for (int k = 0; k < dd.span(); k++) {
				int x = dd.cellX(k), z = dd.cellZ(k);
				if (p.leafAt()[x][z] >= 0) return "door in a room cell " + x + "," + z;
				int ax = x - (dd.alongZ() ? 1 : 0), az = z - (dd.alongZ() ? 0 : 1), bx = x + (dd.alongZ() ? 1 : 0), bz = z + (dd.alongZ() ? 0 : 1);
				boolean fwd = dd.facing().dx > 0 || dd.facing().dz > 0;
				if (p.leafAt()[ax][az] != (fwd ? dd.from() : dd.to()) || p.leafAt()[bx][bz] != (fwd ? dd.to() : dd.from())) return "door sides wrong at " + x + "," + z;
			}
		}
		int kitchens = 0, offices = 0;
		for (int i = 0; i < n; i++) {
			Leaf l = p.leaves().get(i);
			double asp = (double) Math.max(l.w(), l.d()) / Math.min(l.w(), l.d());
			if (uses[i].equals("kitchen")) kitchens++;
			if (uses[i].equals("office") && s.template() == Template.GRAND && s.grandUse().equals("lobby")) offices++;
			if (uses[i].equals("banquet") && asp > 3.0) return "banquet room aspect " + asp;
			if (s.template() == Template.GRAND && (uses[i].equals("lobby") || uses[i].equals("open")) && asp > 2.5) return uses[i] + " aspect " + asp + " " + l;
			if (uses[i].equals("entry") && Math.min(l.w(), l.d()) > 6) return "entry wider than 6: " + l; // foyers in front of a flat (3 deep) or a private passage beside the core
			if (uses[i].equals("entry") && s.unitUse().equals("office") && s.template() == Template.CORRIDOR) return "office floor with an entry strip";
		}
		if (kitchens > 1) return kitchens + " kitchens";
		if (offices > 1) return offices + " lobby offices";
		if (s.template() == Template.STAIRHOUSE) return p.doors().size() == 1 && p.doors().get(0).span() == 1 ? null : "stairhouse: one door between the stairwell and the machine room expected, got " + p.doors().size();
		// the shaft's glass doorway (middle of its hall-side wall) looks into a room, never at a partition
		if (p.leafAt()[s.shaft()[0] + 1][s.hallZ1() - 1] < 0) return "the shaft doorway faces a partition at x " + (s.shaft()[0] + 1);
		// stairwell
		Leaf sl = p.leaves().get(res.stairwell());
		int[] sw = s.stairwell();
		if (!uses[res.stairwell()].equals("stairwell") || sl.x() != sw[0] || sl.z() != sw[1] || sl.x() + sl.w() != sw[2] || sl.z() + sl.d() != sw[3]) return "stairwell leaf != rect";
		List<Door> sd = new ArrayList<>();
		for (Door dd : p.doors()) if (dd.from() == res.stairwell() || dd.to() == res.stairwell()) sd.add(dd);
		if (sd.size() != 1) return "stairwell doors: " + sd.size();
		Door door = sd.get(0);
		if (door.x() != sw[0] + (sw[2] - sw[0]) / 2 || door.z() != s.hallZ1() || door.arch()) return "stairwell door misplaced: " + door;
		int other = door.from() == res.stairwell() ? door.to() : door.from();
		String ou = uses[other];
		if (s.template() == Template.GRAND ? !ou.equals(s.grandUse()) : !ou.equals("corridor")) return "stairwell opens onto " + ou;
		// reachability, bedrooms
		boolean[] seen = new boolean[n];
		int start = res.corridor() >= 0 ? res.corridor() : other;
		List<Integer> q = new ArrayList<>(List.of(start));
		seen[start] = true;
		for (int h = 0; h < q.size(); h++) {
			int cur = q.get(h);
			if (uses[cur].equals("bedroom")) continue; // a bedroom never leads on
			for (Door dd : p.doors()) {
				int o = dd.from() == cur ? dd.to() : dd.to() == cur ? dd.from() : -1;
				if (o >= 0 && !seen[o]) { seen[o] = true; q.add(o); }
			}
		}
		for (int i = 0; i < n; i++) if (!seen[i] && uses[i].equals("bedroom")) return "bedroom " + p.leaves().get(i) + " reached only through a bedroom or not at all";
		boolean[] any = new boolean[n]; // every room is reachable; a store room may hang off a bedroom or office
		List<Integer> q2 = new ArrayList<>(List.of(start));
		any[start] = true;
		for (int h = 0; h < q2.size(); h++)
			for (Door dd : p.doors()) {
				int cur = q2.get(h), o = dd.from() == cur ? dd.to() : dd.to() == cur ? dd.from() : -1;
				if (o >= 0 && !any[o]) { any[o] = true; q2.add(o); }
			}
		for (int i = 0; i < n; i++) if (!any[i]) return uses[i] + " " + p.leaves().get(i) + " unreachable";
		// entry strips open onto the corridor; units' flats/offices too (unit main rooms have a door from the corridor or an entry strip)
		if (res.corridor() >= 0) {
			for (int i = 0; i < n; i++) {
				if (!uses[i].equals("entry")) continue;
				boolean ok = false;
				for (Door dd : p.doors()) ok |= (dd.from() == i && dd.to() == res.corridor()) || (dd.to() == i && dd.from() == res.corridor());
				if (!ok) return "entry strip without a corridor door";
			}
			int lo = Integer.MAX_VALUE, hi = -1;
			for (Door dd : p.doors())
				if (dd.from() == res.corridor() || dd.to() == res.corridor()) {
					lo = Math.min(lo, dd.x());
					hi = Math.max(hi, dd.x() + dd.span() - 1);
				}
			int shaftX = s.shaft()[0] + 1; // B3a puts the shaft doorway in the hall-side wall
			lo = Math.min(lo, shaftX);
			hi = Math.max(hi, shaftX);
			if (lo > 4 || p.w() - 1 - hi > 4) return "dead end " + lo + " / " + (p.w() - 1 - hi);
		}
		if (s.template() == Template.BANQUET) {
			int banquet = uses.length;
			for (int i = 0; i < n; i++) if (uses[i].equals("banquet")) banquet = i;
			for (Door dd : p.doors()) {
				if ((dd.from() == res.stairwell() || dd.to() == res.stairwell()) && (dd.from() == banquet || dd.to() == banquet)) return "stairwell onto banquet";
				if (dd.from() == banquet || dd.to() == banquet) {
					if (!dd.arch() || dd.span() < 2 || dd.span() > 3) return "banquet entrance is not a 2-3 wide arch: " + dd;
				}
			}
		}
		return null;
	}

	static final int SEEDS = 400;

	@Test
	void everyTemplateOnSyntheticPlates() {
		for (Template t : new Template[] {Template.CORRIDOR, Template.GRAND, Template.BANQUET}) {
			int nulls = 0;
			java.util.TreeMap<String, Integer> why = new java.util.TreeMap<>();
			List<String> errs = new ArrayList<>();
			for (int seed = 0; seed < SEEDS; seed++) {
				Spec s = plate(seed, t);
				Result res = TowerPlate.plan(s, new Draws(Hash64.hash(seed, "draw")));
				if (res == null) {
					nulls++;
					why.merge(TowerPlate.WHY.get(), 1, Integer::sum);
					continue;
				}
				String e = check(s, res);
				if (e != null) errs.add(t + " seed " + seed + " (" + s.w() + "x" + s.d() + " " + s.unitUse() + " strip=" + s.entryStrip() + "): " + e + "\n" + TowerPlate.dump(res));
			}
			assertTrue(errs.isEmpty(), errs.size() + " bad plans; " + errs.stream().map(e -> e.lines().findFirst().get()).limit(12).toList() + "\nfirst:\n" + (errs.isEmpty() ? "" : errs.get(0)));
			assertTrue(nulls <= SEEDS / 10, t + " fell back to the guillotine planner " + nulls + " of " + SEEDS + " times: " + why);
			System.out.println("TowerPlate " + t + ": fallbacks " + nulls + " / " + SEEDS);
		}
	}

	@Test
	void sameSpecAndSeedGiveTheSamePlan() {
		for (int seed = 0; seed < 60; seed++)
			for (Template t : new Template[] {Template.CORRIDOR, Template.GRAND, Template.BANQUET}) {
				Spec s = plate(seed, t);
				Result a = TowerPlate.plan(s, new Draws(seed)), b = TowerPlate.plan(s, new Draws(seed));
				assertEquals(a == null, b == null);
				if (a != null) {
					assertEquals(TowerPlate.dump(a), TowerPlate.dump(b));
					assertEquals(a.plan().doors(), b.plan().doors());
					assertEquals(a.plan().cuts(), b.plan().cuts());
				}
			}
	}


	/** An open floor's back-of-house rooms off the grand room are offices (cut in depth when deep, so none is demoted for its shape). */
	@Test
	void openFloorBackOfHouseKeepsItsOffices() {
		int offices = 0, demoted = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			Spec s = plate(seed, Template.GRAND);
			if (!s.grandUse().equals("open")) continue;
			Result res = TowerPlate.plan(s, new Draws(Hash64.hash(seed, "draw")));
			if (res == null) continue;
			int grand = -1;
			for (Door dd : res.plan().doors()) if (dd.from() == res.stairwell()) grand = dd.to(); else if (dd.to() == res.stairwell()) grand = dd.from();
			for (Door dd : res.plan().doors()) {
				int o = dd.from() == grand ? dd.to() : dd.to() == grand ? dd.from() : -1;
				if (o < 0 || o == res.stairwell() || res.uses()[o].equals("open")) continue;
				if (res.uses()[o].equals("office")) offices++; else demoted++;
			}
		}
		assertTrue(offices > 100, "offices " + offices);
		assertTrue(demoted <= offices / 8, "demoted " + demoted + " of " + (offices + demoted));
	}
	@Test
	void stairhouseIsStairwellAndPlant() {
		for (int len = 5; len <= 9; len++)
			for (int sd = 3; sd <= 4; sd++)
				for (boolean east : new boolean[] {true, false}) {
					int w = len + 4;
					int[] stair = east ? new int[] {0, 0, len, sd} : new int[] {4, 0, w, sd};
					int[] shaft = east ? new int[] {len + 1, 0, w, 3} : new int[] {0, 0, 3, 3};
					Spec s = new Spec(w, sd, stair, shaft, 0, 0, new boolean[w][sd], Template.STAIRHOUSE, "", 0, "flat", true, 0);
					Result res = TowerPlate.plan(s, new Draws(1));
					assertNotNull(res, "east=" + east);
					assertEquals(null, check(s, res));
					assertEquals(List.of("stairwell", "plant"), List.of(res.uses()), "east=" + east);
				}
	}

	/** Demotions of offices, bedrooms and back rooms to storage stay rare on corridor floors. */
	@Test
	void corridorDemotionsAreRare() {
		int rooms = 0, demoted = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			Spec s = plate(seed, Template.CORRIDOR);
			Result res = TowerPlate.plan(s, new Draws(Hash64.hash(seed, "draw")));
			if (res == null) continue;
			demoted += res.demoted();
			for (String u : res.uses()) if (u.equals("office") || u.equals("bedroom")) rooms++;
		}
		assertTrue(demoted * 20 <= rooms + demoted, "demoted " + demoted + " of " + (rooms + demoted));
	}

	/** Programme doubles and entry strips: one kitchen per banquet floor, one office by a lobby, strips of at most 3, none for offices (checked per plan); rates printed. */
	@Test
	void programmeRates() {
		int banq = 0, dupK = 0, lob = 0, dupO = 0, strips = 0, bigStrips = 0, grandRooms = 0, longGrand = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			Result b = TowerPlate.plan(plate(seed, Template.BANQUET), new Draws(seed));
			if (b != null) {
				banq++;
				if (java.util.Arrays.stream(b.uses()).filter("kitchen"::equals).count() > 1) dupK++;
			}
			Spec ls = plate(seed, Template.GRAND);
			Result l = TowerPlate.plan(ls, new Draws(seed));
			if (l != null) {
				if (ls.grandUse().equals("lobby")) {
					lob++;
					if (java.util.Arrays.stream(l.uses()).filter("office"::equals).count() > 1) dupO++;
				}
				for (int i = 0; i < l.uses().length; i++) {
					if (!l.uses()[i].equals(ls.grandUse())) continue;
					Leaf lf = l.plan().leaves().get(i);
					grandRooms++;
					if ((double) Math.max(lf.w(), lf.d()) / Math.min(lf.w(), lf.d()) > 2.5) longGrand++;
				}
			}
			Result c = TowerPlate.plan(plate(seed, Template.CORRIDOR), new Draws(seed));
			if (c != null) for (int i = 0; i < c.uses().length; i++) if (c.uses()[i].equals("entry")) {
				Leaf lf = c.plan().leaves().get(i);
				strips++;
				if (lf.w() * lf.d() > 0 && Math.min(lf.w(), lf.d()) > 6) bigStrips++;
			}
		}
		System.out.println("TowerPlate rates: banquets with two kitchens " + dupK + "/" + banq + ", lobbies with two offices " + dupO + "/" + lob + ", strips deeper than 3 " + bigStrips + "/" + strips + ", grand rooms over 2.5:1 " + longGrand + "/" + grandRooms);
		assertEquals(0, dupK);
		assertEquals(0, dupO);
		assertEquals(0, bigStrips);
		assertEquals(0, longGrand);
	}
	@Test
	void unitDepthTable() {
		for (int d = 3; d <= 10; d++) assertEquals(0, TowerPlate.stripDepth(d), "no strip up to 10 deep (fewer, wider units)");
		for (int d = 11; d <= 16; d++) assertEquals(3, TowerPlate.stripDepth(d));
	}

	/** Dev printer: three plate sizes per template into the build dir (and the log), read by humans. */
	@Test
	void dumpExamples() throws Exception {
		StringBuilder sb = new StringBuilder();
		long[] seeds = {3, 11, 29};
		for (Template t : new Template[] {Template.CORRIDOR, Template.GRAND, Template.BANQUET}) {
			int shown = 0;
			for (long seed = 0; seed < 500 && shown < 3; seed++) {
				Spec s = plate(seed, t);
				if (s.w() < 20 + shown * 8 - 6 && shown > 0) continue;
				Result res = TowerPlate.plan(s, new Draws(seed));
				if (res == null) continue;
				if (shown == 0 && s.w() > 26) continue;
				if (shown == 1 && (s.w() < 26 || s.w() > 34)) continue;
				if (shown == 2 && s.w() < 34) continue;
				sb.append("== ").append(t).append(' ').append(s.w()).append('x').append(s.d()).append(" seed ").append(seed).append(" hall ").append(s.hallZ0()).append('-').append(s.hallZ1())
						.append(" stair ").append(java.util.Arrays.toString(s.stairwell())).append(" use=").append(s.unitUse()).append(" strip=").append(s.entryStrip()).append(" target=").append(s.unitTarget()).append('\n');
				sb.append(TowerPlate.dump(res)).append('\n');
				shown++;
			}
		}
		java.nio.file.Path out = java.nio.file.Path.of("build", "tower-plate-dumps.txt");
		java.nio.file.Files.createDirectories(out.getParent());
		java.nio.file.Files.writeString(out, sb.toString());
	}
}
