package dev.fractalbuildings.core.gen.v1.model;

import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The massing contract (docs/plans/phase4.md "Massing contract"). Every massing program's output must
 * pass; {@code ShellGeneratorV1} calls {@link #require} so a broken rule fails loudly in property
 * tests instead of producing a broken shell. Rule ids (M1..M9) are in the messages.
 */
public final class MassingCheck {
	private MassingCheck() {}

	/** Largest jetty overhang per side, in blocks (John, REVIEW 3.12: "wider with an overhang on top"). */
	public static final int JETTY_MAX = 2;

	/** Throws {@link IllegalStateException} listing every broken rule. */
	public static void require(Massing m, int width, int depth, int maxHeight) {
		List<String> p = check(m, width, depth, maxHeight);
		if (!p.isEmpty()) throw new IllegalStateException("massing breaks its contract: " + String.join("; ", p));
	}

	public static List<String> check(Massing m, int width, int depth, int maxHeight) {
		List<String> out = new ArrayList<>();
		StoreyGrid g = m.grid();
		List<Mass> ms = m.masses();
		Mass main = m.main();
		// M1: main mass first, on the ground; ids unique
		if (!main.ground() || main.parent() != null) out.add("M1 main mass " + main.id() + " must stand on the ground");
		TreeSet<String> ids = new TreeSet<>();
		for (Mass a : ms) if (!ids.add(a.id())) out.add("M1 duplicate mass id " + a.id());
		// M1: entrances: the main mass first, then other ground masses, each once
		if (m.entrances().isEmpty() || !m.entrances().get(0).equals(main.id())) out.add("M1 the main mass must be the first entrance");
		TreeSet<String> ent = new TreeSet<>();
		for (String e : m.entrances()) {
			if (!ent.add(e)) out.add("M1 duplicate entrance " + e);
			if (!ids.contains(e)) out.add("M1 entrance " + e + " is not a mass");
			else if (!m.mass(e).ground()) out.add("M1 entrance " + e + " is not on the ground");
		}
		for (Massing.Party p : m.partyWalls()) {
			if (!ids.contains(p.a()) || !ids.contains(p.b())) out.add("M6 party wall " + p + " names an unknown mass");
			else if (Massing.strip(m.mass(p.a()), m.mass(p.b())) == null) out.add("M6 party wall " + p + " is not a shared strip");
		}
		if (!out.isEmpty()) return out;
		if (m.storeys() != g.count()) out.add("M3 the grid has " + g.count() + " storeys but the masses use " + m.storeys());
		for (int i = 0; i < ms.size(); i++) {
			Mass a = ms.get(i);
			// M2: inside the envelope
			if (a.x() < 0 || a.z() < 0 || a.maxX() > width || a.maxZ() > depth) out.add("M2 " + a.id() + " outside the envelope " + width + "x" + depth);
			// M3: storeys on the grid, top slab plus one roof layer under maxHeight
			if (a.to() > g.count()) {
				out.add("M3 " + a.id() + " reaches storey " + a.to() + " but the grid has " + g.count());
				continue;
			}
			if (a.topSlab(g) + 2 > maxHeight) out.add("M3 " + a.id() + " top slab y " + a.topSlab(g) + " leaves no roof room under maxHeight " + maxHeight);
			// M5: parents
			if (a.ground() != (a.parent() == null)) out.add("M5 " + a.id() + ": a parent iff it starts above storey 0");
			if (a.parent() != null) {
				int pi = index(ms, a.parent());
				if (pi < 0 || pi >= i) out.add("M5 " + a.id() + " parent " + a.parent() + " must be an earlier mass");
				else {
					Mass p = ms.get(pi);
					if (p.to() != a.from()) out.add("M5 " + a.id() + " must start at its parent's top (storey " + p.to() + ")");
					boolean setback = a.x() >= p.x() && a.z() >= p.z() && a.maxX() <= p.maxX() && a.maxZ() <= p.maxZ();
					boolean jetty = a.x() <= p.x() && a.z() <= p.z() && a.maxX() >= p.maxX() && a.maxZ() >= p.maxZ()
							&& p.x() - a.x() <= JETTY_MAX && p.z() - a.z() <= JETTY_MAX
							&& a.maxX() - p.maxX() <= JETTY_MAX && a.maxZ() - p.maxZ() <= JETTY_MAX;
					if (!setback && !jetty) out.add("M5 " + a.id() + " must lie inside its parent (setback) or contain it with at most " + JETTY_MAX + " overhang (jetty)");
				}
			}
		}
		// M6: pairs that are not ancestor and descendant meet only along a one-block wall strip
		for (int i = 0; i < ms.size(); i++)
			for (int j = i + 1; j < ms.size(); j++) {
				Mass a = ms.get(i), b = ms.get(j);
				if (ancestor(ms, a, b) || ancestor(ms, b, a)) continue;
				boolean share = a.from() < b.to() && b.from() < a.to();
				boolean meet = a.x() < b.maxX() && b.x() < a.maxX() && a.z() < b.maxZ() && b.z() < a.maxZ();
				int[] st = Massing.strip(a, b);
				if (meet && st == null) out.add("M6 " + a.id() + " and " + b.id() + " overlap by more than a wall strip");
				if (meet && st != null && share && Math.max(st[2], st[3]) < 3) out.add("M6 " + a.id() + " and " + b.id() + " share a strip shorter than 3");
				boolean touch = !meet && a.x() <= b.maxX() && b.x() <= a.maxX() && a.z() <= b.maxZ() && b.z() <= a.maxZ()
						&& (a.x() < b.maxX() && b.x() < a.maxX() || a.z() < b.maxZ() && b.z() < a.maxZ());
				if (touch && share) out.add("M6 " + a.id() + " and " + b.id() + " touch without sharing a wall (double wall)");
			}
		// M8: cores
		for (int c = 0; c < m.cores().size(); c++) {
			Core k = m.cores().get(c);
			if (k.to() > g.count()) {
				out.add("M8 core " + c + " reaches storey " + k.to() + " but the grid has " + g.count());
				continue;
			}
			int need = Core.length(g, k.from(), k.to());
			if (Math.max(k.sx(), k.sz()) < need) out.add("M8 core " + c + " long side " + Math.max(k.sx(), k.sz()) + " < " + need);
			for (int s = k.from(); s < k.to(); s++) {
				Mass host = m.interiorContaining(s, k.x(), k.z(), k.sx(), k.sz());
				if (host == null) out.add("M8 core " + c + " not inside a mass's walls on storey " + s);
				else if (host.open()) out.add("M8 core " + c + " in open mass " + host.id());
			}
			for (int d = c + 1; d < m.cores().size(); d++) {
				Core o = m.cores().get(d);
				boolean storeys = k.from() < o.to() && o.from() < k.to();
				boolean cols = k.x() < o.maxX() && o.x() < k.maxX() && k.z() < o.maxZ() && o.z() < k.maxZ();
				if (storeys && cols) out.add("M8 cores " + c + " and " + d + " overlap");
			}
		}
		if (!out.isEmpty()) return out;
		// M7: every floor (mass, storey) reachable from an entrance's ground floor through strips (not party walls) and cores
		connectivity(m, out);
		// M9: room for each entrance on storey 0: the main mass's on its front (north) face, the others on any face
		for (String id : m.entrances()) {
			boolean ok = false;
			for (Face f : Faces.of(m)) {
				if (!f.mass().equals(id) || f.storey() != 0 || (id.equals(main.id()) && f.side() != Facing.NORTH)) continue;
				for (int p = f.a() + 1; p < f.b() - 1; p++) if (!m.inCore(0, f.insideX(p), f.insideZ(p))) ok = true;
			}
			if (!ok) out.add("M9 no place for the entrance of " + id);
		}
		return out;
	}

	private static int index(List<Mass> ms, String id) {
		for (int i = 0; i < ms.size(); i++) if (ms.get(i).id().equals(id)) return i;
		return -1;
	}

	/** True if {@code a} is {@code b}'s parent, grandparent... */
	private static boolean ancestor(List<Mass> ms, Mass a, Mass b) {
		Mass cur = b;
		for (int guard = 0; cur.parent() != null && guard < ms.size(); guard++) {
			int i = index(ms, cur.parent());
			if (i < 0) return false;
			cur = ms.get(i);
			if (cur == a) return true;
		}
		return false;
	}

	private static void connectivity(Massing m, List<String> out) {
		List<Mass> ms = m.masses();
		int n = m.grid().count();
		boolean[][] reached = new boolean[ms.size()][n];
		for (String e : m.entrances()) reached[ms.indexOf(m.mass(e))][0] = true;
		boolean grew = true;
		while (grew) {
			grew = false;
			for (int i = 0; i < ms.size(); i++)
				for (int j = 0; j < ms.size(); j++) {
					if (i == j) continue;
					Mass a = ms.get(i), b = ms.get(j);
					int[] st = Massing.strip(a, b);
					if (st == null || Math.max(st[2], st[3]) < 3 || m.party(a, b)) continue;
					for (int s = 0; s < n; s++) {
						if (reached[i][s] && !reached[j][s] && a.floorAt(s) && b.floorAt(s)) {
							reached[j][s] = true;
							grew = true;
						}
					}
				}
			for (Core k : m.cores())
				for (int s = k.from(); s < k.to() - 1; s++) {
					int lo = ms.indexOf(m.interiorContaining(s, k.x(), k.z(), k.sx(), k.sz()));
					int hi = ms.indexOf(m.interiorContaining(s + 1, k.x(), k.z(), k.sx(), k.sz()));
					if (lo < 0 || hi < 0) continue;
					if (reached[lo][s] != reached[hi][s + 1]) {
						reached[lo][s] = reached[hi][s + 1] = true;
						grew = true;
					}
				}
		}
		for (int i = 0; i < ms.size(); i++)
			for (int s = 0; s < n; s++)
				if (ms.get(i).floorAt(s) && !reached[i][s]) out.add("M7 " + ms.get(i).id() + " storey " + s + " is not reachable from the entrance floor");
	}
}
