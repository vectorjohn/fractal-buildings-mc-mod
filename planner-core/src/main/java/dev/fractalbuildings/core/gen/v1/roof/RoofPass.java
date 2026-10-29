package dev.fractalbuildings.core.gen.v1.roof;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.roof.RoofField.Pitch;
import dev.fractalbuildings.core.gen.v1.style.RoofStyle;
import dev.fractalbuildings.core.grammar.Choice;
import dev.fractalbuildings.core.ir.Axis;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Roofs (step 4.3, docs/plans/phase4.md "R"). Masses with the same top that touch (and are not
 * separated by a party wall) form a <em>roof group</em>; the group's <em>region</em> is the union of
 * its footprints minus the columns a higher mass stands on and minus columns an earlier group with the
 * same top owns (the shared strip of two row units). One heightfield ({@link RoofField}) is chosen per
 * group (gable per mass with perpendicular wings carried into the main roof up to its ridge, hip,
 * pyramid, mansard, shed, flat) and voxelised closed:
 *
 * <ul>
 *   <li>one ROOF voxel per column at {@code base + h}; a column higher than a region neighbour by more
 *       than 1 gets ROOF risers beside it; a column next to open air gets WALL up to its roof (gable
 *       ends); neighbours that are walls of a higher mass need nothing (heights are capped by the
 *       neighbour's top slab, so its wall always reaches the roof);
 *   <li>overhang: ROOF at the top-slab level beyond the eave edges, on free envelope columns only;
 *   <li>flat roofs get a parapet ring; groups that are partly covered (a ring round a setback) or
 *       tall are always flat;
 *   <li>dormers on straight 1/1 slopes that face a street: a closed 3-wide box with a window;
 *   <li>pitch falls back to 1/2, then 1/3, then flat when the roof would pass {@code maxHeight}.
 * </ul>
 *
 * One {@link Roof} record per mass that has region columns, so {@code roof-covers} and
 * {@code roof-closed} check every volume that is roofed.
 */
public final class RoofPass {
	private RoofPass() {}

	static final Set<String> FORMS = Set.of("gable", "hip", "pyramid", "shed", "mansard", "flat");
	private static final int[][] PITCH_FALLBACK = {{-1, -1}, {1, 2}, {1, 3}};
	private static final Facing[] DIRS = Facing.values();
	/** A shed stops rising here (a plateau), so wide buildings do not get a wall-high ramp. */
	static final int SHED_MAX = 3;

	public static void run(BuildContext c) { run(c, c.style().roof()); }

	/** As {@link #run(BuildContext)} with the roof rules {@code rs} in place of the style's (types that force flat roofs). */
	public static void run(BuildContext c, RoofStyle rs) {
		Massing m = c.massing();
		StoreyGrid grid = m.grid();
		List<Mass> ms = m.masses();
		int n = ms.size();
		int[] root = new int[n];
		for (int i = 0; i < n; i++) root[i] = i;
		for (int i = 0; i < n; i++)
			for (int j = i + 1; j < n; j++) {
				Mass a = ms.get(i), b = ms.get(j);
				if (a.to() != b.to() || !touches(a, b) || m.party(a, b)) continue;
				int ra = find(root, i), rb = find(root, j);
				if (ra != rb) root[Math.max(ra, rb)] = Math.min(ra, rb);
			}
		List<List<Mass>> groups = new ArrayList<>();
		int[] gid = new int[n];
		for (int i = 0; i < n; i++) {
			if (find(root, i) == i) { gid[i] = groups.size(); groups.add(new ArrayList<>()); }
		}
		for (int i = 0; i < n; i++) {
			gid[i] = gid[find(root, i)];
			groups.get(gid[i]).add(ms.get(i));
		}
		int[][] claim = new int[c.width()][c.depth()];
		for (int[] row : claim) Arrays.fill(row, -1);
		for (int gi = 0; gi < groups.size(); gi++)
			for (Mass mass : groups.get(gi))
				for (int x = mass.x(); x < mass.maxX(); x++)
					for (int z = mass.z(); z < mass.maxZ(); z++)
						if (claim[x][z] < 0 && !coveredHigher(ms, mass.to(), x, z)) claim[x][z] = gi;
		for (int gi = 0; gi < groups.size(); gi++) new Group(c, rs, ms, groups.get(gi), gi, claim).build();
	}

	private static int find(int[] root, int i) {
		while (root[i] != i) i = root[i];
		return i;
	}

	private static boolean touches(Mass a, Mass b) {
		return a.x() < b.maxX() && b.x() < a.maxX() && a.z() < b.maxZ() && b.z() < a.maxZ();
	}

	private static boolean coveredHigher(List<Mass> ms, int to, int x, int z) {
		for (Mass o : ms) if (o.to() > to && o.contains(x, z)) return true;
		return false;
	}

	/**
	 * Footprint columns (indexed [x - mass.x][z - mass.z]) with nothing standing above the mass's top:
	 * a column is covered if another mass contains it and reaches higher, or reaches as high and
	 * comes earlier in the list.
	 */
	public static boolean[][] exposed(Massing m, Mass mass) {
		int top = mass.box(m.grid()).maxY();
		int idx = m.masses().indexOf(mass);
		boolean[][] e = new boolean[mass.sx()][mass.sz()];
		for (int i = 0; i < mass.sx(); i++)
			for (int k = 0; k < mass.sz(); k++) {
				int cx = mass.x() + i, cz = mass.z() + k;
				boolean covered = false;
				for (int j = 0; j < m.masses().size() && !covered; j++) {
					Mass o = m.masses().get(j);
					if (o == mass || !o.contains(cx, cz)) continue;
					int otop = o.box(m.grid()).maxY();
					if (otop > top || (otop == top && j < idx)) covered = true;
				}
				e[i][k] = !covered;
			}
		return e;
	}

	/** One roof group being built. */
	private static final class Group {
		final BuildContext c;
		final List<Mass> all, masses;
		final int gi, t, base, w, d, maxH;
		final StoreyGrid grid;
		final RoofStyle rs;
		final boolean[][] region, solid, footprint;
		final int[][] capH; // max height of a column next to a higher mass's wall
		int regionCols;
		boolean partial;
		String form;
		Pitch pitch;
		int[][] h;
		Facing shedDown; // shed: the downhill direction

		Group(BuildContext c, RoofStyle rs, List<Mass> all, List<Mass> masses, int gi, int[][] claim) {
			this.c = c;
			this.all = all;
			this.masses = masses;
			this.gi = gi;
			this.grid = c.massing().grid();
			this.rs = rs;
			this.t = masses.get(0).to();
			this.base = masses.get(0).topSlab(grid) + 1;
			this.w = c.width();
			this.d = c.depth();
			this.maxH = c.maxHeight();
			region = new boolean[w][d];
			solid = new boolean[w][d];
			footprint = new boolean[w][d];
			capH = new int[w][d];
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++) {
					region[x][z] = claim[x][z] == gi;
					if (region[x][z]) regionCols++;
					int cap = Integer.MAX_VALUE;
					for (Mass o : all) {
						if (!o.contains(x, z)) continue;
						footprint[x][z] = true;
						if (o.spans(t)) {
							solid[x][z] = true;
							cap = Math.min(cap, o.topSlab(grid) - base);
						}
					}
					capH[x][z] = cap;
				}
			for (Mass mass : masses)
				for (int x = mass.x(); x < mass.maxX(); x++)
					for (int z = mass.z(); z < mass.maxZ(); z++)
						if (mass.interior(x, z) && coveredHigher(all, t, x, z)) partial = true;
		}

		boolean in(int x, int z) { return x >= 0 && z >= 0 && x < w && z < d; }

		boolean reg(int x, int z) { return in(x, z) && region[x][z]; }

		boolean isSolid(int x, int z) { return in(x, z) && solid[x][z]; }

		boolean open(int x, int z) { return !reg(x, z) && !isSolid(x, z); }

		void build() {
			if (regionCols == 0) return;
			chooseForm();
			Pitch[] tries = pitches();
			h = null;
			for (Pitch p : tries) {
				pitch = p;
				int[][] f = field();
				int top = 0;
				for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) top = Math.max(top, f[x][z]);
				if (base + top + 1 <= maxH) { h = f; break; }
			}
			if (h == null) {
				form = "flat";
				pitch = new Pitch(0, 1);
				h = field();
			}
			voxelise();
			if (form.equals("flat")) parapet();
			else overhang();
			if ((form.equals("gable") || form.equals("hip")) && rs.dormers() > 0 && pitch.rise() == pitch.run()) dormers();
			for (Mass mass : masses) {
				boolean any = false;
				for (int x = mass.x(); x < mass.maxX() && !any; x++) for (int z = mass.z(); z < mass.maxZ(); z++) if (region[x][z]) { any = true; break; }
				if (!any) continue;
				boolean ridgeX = mass.sx() >= mass.sz();
				c.roof(new Roof(form, mass.id(), form.equals("gable") ? (ridgeX ? Axis.X : Axis.Z) : null, base, pitch.rise(), pitch.run()));
			}
		}

		void chooseForm() {
			boolean tall = t >= rs.tallStoreys();
			if (tall || partial) { form = "flat"; return; }
			List<Choice.Option> opts = new ArrayList<>();
			for (var e : rs.forms().entrySet()) if (e.getValue() > 0 && FORMS.contains(e.getKey())) opts.add(Choice.opt(e.getKey(), e.getValue()));
			if (opts.isEmpty()) { form = "gable"; return; }
			form = Choice.pick(c.hash("roof/form", gi), "form", opts);
			if (form.equals("pyramid")) {
				Mass a = masses.get(0);
				if (masses.size() > 1 || Math.abs(a.sx() - a.sz()) > 1) form = "hip";
			}
		}

		/** The style's pitch, then lower ones; flat roofs and mansards ignore the pitch. */
		Pitch[] pitches() {
			if (form.equals("flat")) return new Pitch[] {new Pitch(0, 1)};
			Pitch[] out = new Pitch[PITCH_FALLBACK.length];
			for (int i = 0; i < out.length; i++) out[i] = i == 0 ? new Pitch(rs.rise(), rs.run()) : new Pitch(PITCH_FALLBACK[i][0], PITCH_FALLBACK[i][1]);
			if (form.equals("mansard")) {
				// the steep mansard first, then the fallbacks as a hip
				return new Pitch[] {out[0], out[1], out[2]};
			}
			return out;
		}

		int[][] field() {
			int[][] f;
			switch (form) {
				case "flat" -> f = filled(0);
				case "gable" -> f = gable();
				case "shed" -> f = shed();
				default -> {
					boolean[][] op = new boolean[w][d];
					for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) op[x][z] = open(x, z);
					int[][] dist = RoofField.distance(region, op);
					boolean mansard = form.equals("mansard") && pitch.rise() == rs.rise() && pitch.run() == rs.run();
					f = RoofField.fromDistance(dist, region, mansard ? RoofField::mansard : pitch::h);
				}
			}
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++) {
					if (!region[x][z]) { f[x][z] = -1; continue; }
					int cap = Integer.MAX_VALUE;
					for (Facing nb : DIRS) if (isSolid(x + nb.dx, z + nb.dz)) cap = Math.min(cap, capH[x + nb.dx][z + nb.dz]);
					f[x][z] = Math.max(0, Math.min(f[x][z], cap));
				}
			return f;
		}

		int[][] filled(int v) {
			int[][] f = new int[w][d];
			for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) f[x][z] = region[x][z] ? v : -1;
			return f;
		}

		/** Per mass a gable along its longer side; a perpendicular neighbour's ridge runs on into the mass up to its centre line. */
		int[][] gable() {
			int[][] f = filled(-1);
			for (Mass m : masses) {
				boolean ridgeX = m.sx() >= m.sz();
				int across = ridgeX ? m.sz() : m.sx();
				int x0 = m.x(), x1 = m.maxX() - 1, z0 = m.z(), z1 = m.maxZ() - 1;
				for (Mass a : masses) {
					if (a == m || !touches(m, a) || (a.sx() >= a.sz()) == ridgeX) continue;
					if (ridgeX) {
						if (a.maxX() == m.x() + 1) x0 = Math.min(x0, a.x() + (a.sx() - 1) / 2);
						if (a.x() == m.maxX() - 1) x1 = Math.max(x1, a.x() + a.sx() / 2);
					} else {
						if (a.maxZ() == m.z() + 1) z0 = Math.min(z0, a.z() + (a.sz() - 1) / 2);
						if (a.z() == m.maxZ() - 1) z1 = Math.max(z1, a.z() + a.sz() / 2);
					}
				}
				for (int x = x0; x <= x1; x++)
					for (int z = z0; z <= z1; z++) {
						if (!reg(x, z)) continue;
						int i = ridgeX ? z - m.z() : x - m.x();
						f[x][z] = Math.max(f[x][z], pitch.h(Math.min(i, across - 1 - i)));
					}
			}
			return f;
		}

		int[][] shed() {
			if (shedDown == null) {
				int best = 0;
				for (Facing f : DIRS) {
					int cnt = 0;
					for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (region[x][z] && isSolid(x - f.dx, z - f.dz)) cnt++;
					if (cnt > best) { best = cnt; shedDown = f; }
				}
				if (shedDown == null) {
					List<Facing> streets = new ArrayList<>();
					for (Facing f : DIRS) if (c.spec().street(f)) streets.add(f);
					shedDown = streets.get(c.draws("roof/shed", gi).below(streets.size()));
				}
			}
			int umin = Integer.MAX_VALUE;
			for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (region[x][z]) umin = Math.min(umin, up(x, z));
			int[][] f = filled(0);
			for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (region[x][z]) f[x][z] = Math.min(SHED_MAX, pitch.h(up(x, z) - umin));
			return f;
		}

		int up(int x, int z) { return -(x * shedDown.dx + z * shedDown.dz); }

		// ---- voxelising ----

		void voxelise() {
			byte[][][] fill = new byte[w][d][];
			for (int z = 0; z < d; z++)
				for (int x = 0; x < w; x++) {
					if (!region[x][z]) continue;
					byte[] col = new byte[h[x][z]];
					for (Facing nb : DIRS) {
						int nx = x + nb.dx, nz = z + nb.dz;
						if (isSolid(nx, nz)) continue;
						if (reg(nx, nz)) {
							for (int y = h[nx][nz]; y < h[x][z]; y++) if (col[y] == 0) col[y] = 1; // starts level with the neighbour so steps touch face to face
						} else {
							for (int y = 0; y < h[x][z]; y++) col[y] = 2;
						}
					}
					fill[x][z] = col;
				}
			for (int z = 0; z < d; z++) {
				int x = 0;
				while (x < w) {
					if (!region[x][z]) { x++; continue; }
					int e = x + 1;
					Role top = topRole(x, z);
					while (e < w && region[e][z] && h[e][z] == h[x][z] && Arrays.equals(fill[e][z], fill[x][z]) && topRole(e, z) == top) e++;
					c.box(top, Layer.ROOF, Box.NO_STOREY, x, base + h[x][z], z, e - x, 1, 1);
					byte[] col = fill[x][z];
					int y = 0;
					while (y < col.length) {
						if (col[y] == 0) { y++; continue; }
						int ye = y + 1;
						while (ye < col.length && col[ye] == col[y]) ye++;
						c.box(col[y] == 2 ? Role.WALL : Role.ROOF, Layer.ROOF, Box.NO_STOREY, x, base + y, z, e - x, ye - y, 1);
						y = ye;
					}
					x = e;
				}
			}
		}

		/** Pitch laid in stairs (1 up per 1 across or steeper), slabs (1 per 2 or gentler) or, for flat roofs, full blocks (DS-36). */
		enum Lay { FULL, STAIRS, SLABS }

		Lay lay() {
			if (form.equals("flat") || pitch.rise() == 0) return Lay.FULL;
			boolean mansard = form.equals("mansard") && pitch.rise() == rs.rise() && pitch.run() == rs.run();
			return !mansard && pitch.run() >= 2 * pitch.rise() ? Lay.SLABS : Lay.STAIRS;
		}

		/** Height of the roof column beside a cell: a higher mass's wall counts as higher, open air as lower. */
		int nbH(int x, int z) {
			if (reg(x, z)) return h[x][z];
			return isSolid(x, z) ? Integer.MAX_VALUE / 2 : Integer.MIN_VALUE / 2;
		}

		/**
		 * The exposed top cell of a roof column (DS-36). Stairs face up the slope; at a summit a cell faces across the ridge
		 * (back to back with its twin on an even span), a corner of a hip alternates its facing from ring to ring so that
		 * Minecraft's own corner rule gives an outer corner on the diagonal and leaves the eave stairs straight; a lone ridge
		 * row or peak (odd span) and the middle of a plateau take a bottom slab. Slab pitches alternate bottom and top slabs.
		 */
		Role topRole(int x, int z) {
			Lay lay = lay();
			if (lay == Lay.FULL) return Role.ROOF;
			int hh = h[x][z];
			boolean[] up = new boolean[4], eq = new boolean[4];
			boolean lower = false;
			int nUp = 0, nEq = 0;
			for (Facing f : DIRS) {
				int n = nbH(x + f.dx, z + f.dz);
				if (n > hh) { up[f.ordinal()] = true; nUp++; }
				else if (n == hh) { eq[f.ordinal()] = true; nEq++; }
				else lower = true;
			}
			if (lay == Lay.SLABS) return lower ? Role.ROOF_SLAB : Role.ROOF_SLAB_TOP;
			if (nUp > 0) {
				Facing pick = null;
				for (Facing f : DIRS) if (up[f.ordinal()] && !up[f.rotate(2).ordinal()]) { pick = f; break; }
				if (pick == null) for (Facing f : DIRS) if (up[f.ordinal()]) { pick = f; break; }
				return Role.roofStair(pick);
			}
			if (form.equals("gable")) { // a gable's ridge cells face across the ridge, to the twin of an even span; a lone ridge row and its ends take a slab
				boolean ridgeX = true;
				for (Mass m : masses) if (m.contains(x, z)) { ridgeX = m.sx() >= m.sz(); break; }
				for (Facing f : DIRS) if ((f.dz != 0) == ridgeX && eq[f.ordinal()] && !eq[f.rotate(2).ordinal()]) return Role.roofStair(f);
				return Role.ROOF_SLAB;
			}
			if (nEq <= 1) return Role.ROOF_SLAB; // the end of a one-wide ridge
			if (nEq == 2) {
				for (Facing f : DIRS) {
					Facing g = f.rotate(1);
					if (eq[f.ordinal()] && eq[g.ordinal()]) { // a corner: ring parity picks the facing along x or along z
						boolean alongX = (hh / Math.max(1, pitch.rise())) % 2 == 0;
						return Role.roofStair((f.dx != 0) == alongX ? f : g);
					}
				}
			}
			for (Facing f : DIRS) if (eq[f.ordinal()] && !eq[f.rotate(2).ordinal()]) return Role.roofStair(f);
			return Role.ROOF_SLAB;
		}

		/** Eave overhang at the top-slab level, on free envelope columns beyond the eaves. */
		void overhang() {
			int ov = rs.overhang();
			if (ov == 0 || base < 1) return;
			boolean[][] oh = new boolean[w][d];
			boolean hipLike = !form.equals("gable") && !form.equals("shed");
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++) {
					if (!region[x][z] || h[x][z] != 0) continue;
					if (hipLike) continue;
					{
						for (Facing f : DIRS) {
							if (!eaveDir(x, z, f) || !open(x + f.dx, z + f.dz)) continue;
							for (int k = 1; k <= ov; k++) if (!free(oh, x + f.dx * k, z + f.dz * k)) break;
						}
				}
					}
			if (hipLike) {
				// grow ring by ring from the eave columns: every overhang cell touches the previous ring, so nothing floats
				boolean[][] prev = new boolean[w][d];
				for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) prev[x][z] = region[x][z] && h[x][z] == 0;
				for (int k = 1; k <= ov; k++) {
					boolean[][] ring = new boolean[w][d];
					for (int pass = 0; pass < 2; pass++)
						for (int x = 0; x < w; x++)
							for (int z = 0; z < d; z++) {
								if (!in(x, z) || footprint[x][z] || oh[x][z]) continue;
								int n = 0;
								for (Facing f : DIRS) {
									int nx = x + f.dx, nz = z + f.dz;
									if (in(nx, nz) && (prev[nx][nz] || (pass == 1 && ring[nx][nz]))) n++;
								}
								if (pass == 0 ? n >= 1 : n >= 2) { oh[x][z] = true; ring[x][z] = true; }
							}
					prev = ring;
				}
			}
			Lay lay = lay();
			for (int z = 0; z < d; z++)
				for (int x = 0; x < w; x++) {
					if (!oh[x][z]) continue;
					Role r = lay == Lay.SLABS ? Role.ROOF_SLAB_TOP : Role.ROOF_SLAB; // beyond the first ring a flat slab; slab pitches run a top slab under the eave's bottom slab
					if (lay == Lay.FULL) r = Role.ROOF;
					else {
						boolean beside = false;
						for (Facing f : DIRS) if (eave(x + f.dx, z + f.dz)) { // the ring beside the eave carries the slope on, half a block lower
							if (lay == Lay.STAIRS) r = Role.roofStair(f);
							beside = true;
							break;
						}
						Facing corner = beside || lay != Lay.STAIRS ? null : eaveCorner(oh, x, z);
						if (corner != null) r = Role.roofStair(corner);
					}
					c.box(r, Layer.ROOF, Box.NO_STOREY, x, base - 1, z, 1, 1, 1);
				}
		}

		/** An eave column: on the roof, at the lowest course. */
		boolean eave(int x, int z) { return reg(x, z) && h[x][z] == 0; }

		/**
		 * The facing of the overhang cell at a hip's outer eave corner (DS-36, architect r1): it touches the eave only on the diagonal, between
		 * two overhang stairs that face the eave. It faces along z, the ring parity one course below the eave corner (which faces along x,
		 * {@link #topRole}), so Minecraft's rule makes it an outer corner that carries the hip line down to the overhang. Null for any other cell.
		 */
		Facing eaveCorner(boolean[][] oh, int x, int z) {
			for (Facing f : DIRS) {
				Facing g = f.rotate(1);
				int fx = x + f.dx, fz = z + f.dz, gx = x + g.dx, gz = z + g.dz;
				if (!eave(fx + g.dx, fz + g.dz) || !in(fx, fz) || !oh[fx][fz] || !in(gx, gz) || !oh[gx][gz]) continue;
				return f.dz != 0 ? f : g;
			}
			return null;
		}

		boolean free(boolean[][] oh, int x, int z) {
			if (!in(x, z) || footprint[x][z]) return false;
			oh[x][z] = true;
			return true;
		}

		/** Gable and shed: overhang only past the eaves (ridge-parallel sides; the downhill side). */
		boolean eaveDir(int x, int z, Facing f) {
			if (form.equals("shed")) return f == shedDown;
			for (Mass m : masses) {
				if (!m.contains(x, z)) continue;
				boolean ridgeX = m.sx() >= m.sz();
				if (ridgeX == (f.dz != 0)) return true;
			}
			return false;
		}

		void parapet() {
			int ph = Math.min(rs.parapet(), maxH - base - 1);
			if (ph <= 0) return;
			Role role = rs.parapetRole().equals("fence") ? Role.FENCE : Role.WALL;
			for (int z = 0; z < d; z++) {
				int x = 0;
				while (x < w) {
					if (!edge(x, z)) { x++; continue; }
					int e = x + 1;
					while (e < w && edge(e, z)) e++;
					c.box(role, Layer.ROOF, Box.NO_STOREY, x, base + 1, z, e - x, ph, 1);
					x = e;
				}
			}
		}

		boolean edge(int x, int z) {
			if (!reg(x, z)) return false;
			boolean open = false;
			for (Facing f : DIRS) {
				if (isSolid(x + f.dx, z + f.dz)) return false; // a parapet would cover the windows of the taller wall
				if (open(x + f.dx, z + f.dz)) open = true;
			}
			return open;
		}

		// ---- dormers ----

		void dormers() {
			boolean[][] taken = new boolean[w][d];
			for (Facing down : DIRS) {
				if (!c.spec().street(down)) continue;
				if (Math.floorMod(c.hash("roof/dormer", gi, down.ordinal()), 100) >= rs.dormers()) continue;
				int ux = -down.dx, uz = -down.dz; // uphill
				int px = Math.abs(uz), pz = Math.abs(ux); // along the eave
				for (int r = 1; r >= 0; r--)
					for (int z = 0; z < d; z++)
						for (int x = 0; x < w; x++)
							if (dormerFits(x, z, r, down, ux, uz, px, pz, taken)) placeDormer(x, z, r, ux, uz, px, pz, taken);
			}
		}

		boolean dormerFits(int x, int z, int r, Facing down, int ux, int uz, int px, int pz, boolean[][] taken) {
			if (!reg(x, z) || h[x][z] != r) return false;
			for (int s = -2; s <= 2; s++) {
				int sx = x + px * s, sz = z + pz * s;
				if (!reg(sx, sz) || h[sx][sz] != r) return false;
				int fx = sx + down.dx, fz = sz + down.dz;
				if (r == 0 ? !open(fx, fz) : !(reg(fx, fz) && h[fx][fz] == r - 1)) return false;
				if (Math.abs(s) > 1) continue;
				for (int i = 0; i <= 3; i++) {
					int qx = sx + ux * i, qz = sz + uz * i;
					if (!reg(qx, qz) || h[qx][qz] != r + i) return false;
				}
			}
			for (int s = -3; s <= 3; s++) {
				int sx = x + px * s, sz = z + pz * s;
				if (in(sx, sz) && taken[sx][sz]) return false;
			}
			return true;
		}

		/**
		 * A dormer on a straight 1/1 slope, centred on column (x, z) of row 0 (the row at the eave side):
		 * a 3-wide, 2-high front wall with a window, two cheeks, and a cap that meets the slope three
		 * rows up. The one cell of air inside is sealed by the window, the cheeks, the cap and the slope.
		 */
		void placeDormer(int x, int z, int r, int ux, int uz, int px, int pz, boolean[][] taken) {
			int ys = base + r;
			for (int s = -1; s <= 1; s++) {
				cell(Role.WALL, x, z, s, 0, ys + 1, px, pz, ux, uz);
				cell(s == 0 ? Role.WINDOW : Role.WALL, x, z, s, 0, ys + 2, px, pz, ux, uz);
				if (s != 0) cell(Role.WALL, x, z, s, 1, ys + 2, px, pz, ux, uz);
				for (int i = 0; i <= 2; i++) cell(Role.ROOF_SLAB, x, z, s, i, ys + 3, px, pz, ux, uz); // a shed cap of slabs in the main roof's material, level with the slope where it meets it
			}
			for (int s = -3; s <= 3; s++) {
				int sx = x + px * s, sz = z + pz * s;
				if (in(sx, sz)) taken[sx][sz] = true;
			}
		}

		void cell(Role role, int x, int z, int s, int i, int y, int px, int pz, int ux, int uz) {
			c.box(role, Layer.ROOF, Box.NO_STOREY, x + px * s + ux * i, y, z + pz * s + uz * i, 1, 1, 1);
		}
	}
}
