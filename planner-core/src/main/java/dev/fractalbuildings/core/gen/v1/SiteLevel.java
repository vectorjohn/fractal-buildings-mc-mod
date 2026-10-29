package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * SHOPLOW S2 (DS-45, docs/plans/shoplow.md sections 1-2): where the slab of a street-front building (shop, house, workshop) stands, and the
 * site boxes that make that level work: the cut and the approach fill.
 *
 * <p>Levels (world y; {@code oy} is the slab block, so the door's feet are at {@code oy + 1} and a street whose first free block is {@code G}
 * gives a door rise of {@code oy + 1 - G}, 1 on level ground):
 * <ul>
 *   <li>{@code G}: the street in front: the brief's {@code frontY} when it is known and within {@link #BANK_LIMIT} of the probe's ground in
 *       the column straight out from the main door, else that ground ({@code fr});
 *   <li>{@code M}: the highest ground over the site: the footprint, a one-cell ring round it, the columns under any volume and the approach
 *       (the cells straight out from the main door to the lot's edge), all inside the lot;
 *   <li>dig in: {@code oy = min(oy0, max(G, M - CUT_MAX - 1))}: never higher than the highest ground under the footprint ({@code oy0}, today's
 *       rule), as low as the street, as high as a cut of {@link #CUT_MAX} above the door's standing level (one more above the well floor) needs.
 * </ul>
 *
 * <p>Boxes (building-local, y relative to the slab): in every site column outside the footprint whose ground is above the well floor
 * (the first free block there is {@code oy}) an {@code AIR} box from one below the slab to the ground, and a {@code PAVING} cell at the
 * bottom (the areaway: a one-wide light well round the building at one level, so no window or door is below the ground outside it);
 * only in cells the shell does not use, because site boxes are rastered after the shell and an {@code AIR} box would erase it. Inside the
 * footprint the building replaces the earth, so the same cells get {@code AIR} only where the shell has nothing (a claim, no paving).
 * When the street is level with the forecourt ({@code G == oy}) the approach cells below it are filled (a level forecourt in front of the
 * door); a fill of 2 or more is a causeway in foundation stone with a rail on its open sides (architect A7).
 */
final class SiteLevel {
	/** Most blocks of earth above the door's standing level that the cut leaves (the cut the roads allow, {@code Grading.MAX_CUT_FILL}). */
	static final int CUT_MAX = 4;
	/** The brief's street may differ this much from the ground in front; beyond it the lot is left empty by the lot planner ({@code Briefs.BANK_LIMIT}). */
	static final int BANK_LIMIT = 4;
	static final Set<String> DIG_TYPES = Set.of("shop", "house", "workshop");

	/** Tests switch the dig-in and the stair-run search off to compare with the old slab rule and flights (per thread); the system property sets it for the whole JVM (before/after galleries). */
	static final ThreadLocal<Boolean> LEGACY = ThreadLocal.withInitial(() -> Boolean.getBoolean("fb.legacyslab")); // -Dfb.legacyslab=true: the whole old behaviour (before/after galleries)

	/** Whether this building takes the new level rule: a street-front type on its ground storey, on no wall walk. */
	static boolean applies(BuildingIr shell, Brief brief) {
		if (LEGACY.get() || !DIG_TYPES.contains(shell.spec().type())) return false;
		if (brief != null && brief.walk() != null) return false;
		if (shell.storeys().isEmpty() || InstancePassV1.entranceStorey(shell).y() != 0) return false; // stilted: its trade floor is the open ground
		return shell.volumes().stream().noneMatch(v -> v.id().startsWith("leg"));
	}

	private final BuildingIr shell;
	private final Lot lot;
	private final TerrainProbe t;
	private final Placement p0; // oy 0
	private final boolean[][] fp;
	private final int x0, z0, nx, nz; // the lot's bounding box in local columns
	private final boolean[] site; // site columns
	private final boolean[] approach; // columns straight out from the main door to the lot's edge
	private final boolean[] mainOut; // the outside cell of the main door
	private final boolean[] approachEnd; // the approach cells at the lot's edge that get a stair block
	private final int[] eave; // per site column outside the footprint: the lowest local y (1 or more) where the shell has a block (an eave over the ring), MAX_VALUE if none
	private int approachRows; // how many cells deep the approach runs in the lot
	private final boolean[] streetSide; // lot-edge columns whose outside is a street (brief mask)
	private final int[] raw;
	final int g; // the street at the main door
	final int m; // the highest ground over the site
	/** The street runs 2 or more above the natural ground in front of the lot (an embankment): its level at the door is not the brief's, so there is no forecourt or corridor to meet it (flights down into the lot, as before). */
	final boolean elevated;
	private boolean streetKnown; // the street's level comes from the brief (the street really is that level there), not from the raw ground
	private int outMax = Integer.MIN_VALUE; // the highest ground in the street columns straight out from the front doors (the corridor's exit)

	SiteLevel(BuildingIr shell, GenRequest req, Placement p0, boolean[][] fp) {
		this.shell = shell;
		this.lot = req.lot();
		this.t = req.terrain();
		this.p0 = p0;
		this.fp = fp;
		int[] cs = {p0.localX(lot.x(), lot.z()), p0.localX(lot.x() + lot.w() - 1, lot.z()), p0.localX(lot.x(), lot.z() + lot.d() - 1), p0.localX(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		int[] zs = {p0.localZ(lot.x(), lot.z()), p0.localZ(lot.x() + lot.w() - 1, lot.z()), p0.localZ(lot.x(), lot.z() + lot.d() - 1), p0.localZ(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		x0 = Math.min(Math.min(cs[0], cs[1]), Math.min(cs[2], cs[3]));
		z0 = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
		nx = Math.max(Math.max(cs[0], cs[1]), Math.max(cs[2], cs[3])) - x0 + 1;
		nz = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3])) - z0 + 1;
		site = new boolean[nx * nz];
		approach = new boolean[nx * nz];
		mainOut = new boolean[nx * nz];
		approachEnd = new boolean[nx * nz];
		streetSide = new boolean[nx * nz];
		raw = new int[nx * nz];
		eave = new int[nx * nz];
		java.util.Arrays.fill(eave, Integer.MAX_VALUE);
		for (int z = z0; z < z0 + nz; z++)
			for (int x = x0; x < x0 + nx; x++) raw[idx(x, z)] = t.floorY(p0.worldX(x, z), p0.worldZ(x, z));
		// the main door: an entrance door facing the lot's front, else the first
		List<Opening> doors = InstancePassV1.entranceDoors(shell);
		Opening main = doors.isEmpty() ? null : doors.get(0);
		for (Opening o : doors) if (p0.toWorld(InstancePassV1.outward(o)) == lot.front()) { main = o; break; }
		int fr;
		if (main != null) {
			Facing f = InstancePassV1.outward(main);
			Box3 mb = main.box();
			for (int x = mb.x(); x < mb.maxX(); x++) for (int z = mb.z(); z < mb.maxZ(); z++) if (inLot(x + f.dx, z + f.dz)) mainOut[idx(x + f.dx, z + f.dz)] = true;
			int k = 1;
			while (k <= 64 && inLot(mb.x() + k * f.dx, mb.z() + k * f.dz)) {
				java.util.Arrays.fill(approachEnd, false);
				for (int x = mb.x(); x < mb.maxX(); x++) for (int z = mb.z(); z < mb.maxZ(); z++) if (inLot(x + k * f.dx, z + k * f.dz)) { approach[idx(x + k * f.dx, z + k * f.dz)] = true; approachEnd[idx(x + k * f.dx, z + k * f.dz)] = true; }
				k++;
			}
			{ // the exit: the street the approach meets can stand a block either side of where the brief puts it. The approach's last cell stays plain (a walker steps down one block from it, or none),
				// and the cells beside it, on the lot's edge, get a stair block (a half step up from the well floor, and a half step down to a street a block higher): between them the street is reached at
				// a block lower, level, or a block higher
				boolean latX = f.dx == 0;
				int lo = latX ? mb.x() : mb.z(), hi = (latX ? mb.maxX() : mb.maxZ()) - 1;
				int row = latX ? mb.z() + (k - 1) * f.dz : mb.x() + (k - 1) * f.dx;
				java.util.Arrays.fill(approachEnd, false);
				approachRows = k - 1;
				for (int c : new int[] {lo - 1, hi + 1}) {
					int lx = latX ? c : row, lz = latX ? row : c;
					if (!inLot(lx, lz)) continue;
					approach[idx(lx, lz)] = true;
					approachEnd[idx(lx, lz)] = true;
				}
			}
			// the street column straight out from each door on the front (a workshop's cart door too), where DoorRise reads it: from the door's world min corner; the slab stands above the highest of them
			fr = Integer.MIN_VALUE;
			for (Opening o : doors) {
				Facing outW = p0.toWorld(InstancePassV1.outward(o));
				if (outW != lot.front()) continue;
				Box3 dw = p0.toWorld(o.box());
				int wx = dw.x(), wz = dw.z(), kk = 0;
				while (kk++ < 64 && lot.containsColumn(wx, wz)) { wx += outW.dx; wz += outW.dz; }
				int gy = t.floorY(wx, wz);
				fr = Math.max(fr, gy);
				outMax = Math.max(outMax, gy);
			}
			if (fr == Integer.MIN_VALUE) fr = frontMiddle();
		} else fr = frontMiddle();
		int fy = req.brief() == null ? Brief.NO_FRONT_Y : req.brief().frontY();
		// the brief's street level is read at the middle of the lot's front; the street slopes along a wide front like the ground does, so at the door's own column it is that level plus the ground's own difference there
		int mid = frontMiddle();
		streetKnown = fy != Brief.NO_FRONT_Y && Math.abs(fy - mid) <= BANK_LIMIT;
		elevated = streetKnown && fy - mid >= 2;
		// where the ground in front reads the street exactly (the settlement planner lays the street's own level over the row outside the lot's front, `Briefs.withStreetFront`; the middle column then equals the brief's), the street at the door's column is that ground;
		// otherwise it is guessed from the brief, the lower guess (a door below the street by a block is within DS-45, one above it by too much is not)
		g = !streetKnown ? fr : mid == fy ? fr : Math.min(fy, fy + (fr - mid));
		// street sides (the brief's mask, in the lot's frame: bit 0 front, 1 east of it, 2 back, 3 west of it)
		int mask = req.brief() == null ? 1 : req.brief().streets() | 1;
		for (int z = z0; z < z0 + nz; z++)
			for (int x = x0; x < x0 + nx; x++) {
				if (!inLot(x, z)) continue;
				int wx = p0.worldX(x, z), wz = p0.worldZ(x, z);
				for (Facing f : Facing.values()) {
					if (lot.containsColumn(wx + f.dx, wz + f.dz)) continue;
					if ((mask & (1 << Math.floorMod(f.ordinal() - lot.front().ordinal(), 4))) != 0) streetSide[idx(x, z)] = true;
				}
			}
		// the site
		for (int x = 0; x < fp.length; x++)
			for (int z = 0; z < fp[0].length; z++) {
				if (!fp[x][z]) continue;
				mark(x, z);
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) markLot(x + dx, z + dz);
			}
		for (Volume v : shell.volumes())
			for (int x = v.box().x(); x < v.box().maxX(); x++) for (int z = v.box().z(); z < v.box().maxZ(); z++) markLot(x, z);
		for (int i = 0; i < site.length; i++) if (approach[i]) site[i] = true;
		int hi = Integer.MIN_VALUE;
		for (int i = 0; i < site.length; i++) if (site[i]) hi = Math.max(hi, raw[i]);
		m = hi;
		VoxelGrid bare = VoxelGrid.of(new dev.fractalbuildings.core.ir.PlacedBuilding("b0", List.of(lot.id()), 0, 0, 0, 0, shell, List.of()));
		for (int z = z0; z < z0 + nz; z++)
			for (int x = x0; x < x0 + nx; x++) {
				if (!site[idx(x, z)] || isFootprint(x, z)) continue;
				for (int y = 1; y <= 12; y++) if (bare.get(x, y, z) != Role.AIR) { eave[idx(x, z)] = y; break; }
			}
	}

	/** The ground in the column outside the middle of the lot's front edge. */
	private int frontMiddle() {
		int k = ((lot.front().dx != 0 ? lot.d() : lot.w()) - 1) / 2;
		int[] c = switch (lot.front()) {
			case NORTH -> new int[] {lot.x() + k, lot.z() - 1};
			case SOUTH -> new int[] {lot.x() + k, lot.z() + lot.d()};
			case WEST -> new int[] {lot.x() - 1, lot.z() + k};
			case EAST -> new int[] {lot.x() + lot.w(), lot.z() + k};
		};
		return t.floorY(c[0], c[1]);
	}

	private int idx(int x, int z) { return (z - z0) * nx + (x - x0); }

	private boolean inBox(int x, int z) { return x >= x0 && x < x0 + nx && z >= z0 && z < z0 + nz; }

	private boolean inLot(int x, int z) { return inBox(x, z) && lot.containsColumn(p0.worldX(x, z), p0.worldZ(x, z)); }

	private void mark(int x, int z) { if (inBox(x, z)) site[idx(x, z)] = true; }

	private void markLot(int x, int z) { if (inLot(x, z)) site[idx(x, z)] = true; }

	private boolean isFootprint(int x, int z) { return x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z]; }

	/** Whether any site column above the slab stands in water (the cut keeps today's level there). */
	private boolean wet(int oy) {
		for (int z = z0; z < z0 + nz; z++)
			for (int x = x0; x < x0 + nx; x++)
				if (site[idx(x, z)] && raw[idx(x, z)] > oy && t.waterDepth(p0.worldX(x, z), p0.worldZ(x, z)) > 0) return true;
		return false;
	}

	/**
	 * The slab level for this site, never above {@code oy0} (the highest ground under the footprint): dig in as far as the street, but no
	 * deeper than a cut of {@link #CUT_MAX} above the door's standing level needs. A cut face on a street side is held to 1 above the standing
	 * level (the rail would stand on the street) as far as the door-rise limit allows.
	 */
	int level(int oy0) {
		int oy = Math.min(oy0, Math.max(g, m - CUT_MAX - 1));
		if (oy >= oy0) return oy0;
		int street = Integer.MIN_VALUE;
		for (int i = 0; i < site.length; i++) if (site[i] && streetSide[i]) street = Math.max(street, raw[i]);
		// (A4) a cut face with a street on top of it is held to 1 above the standing level where the type's door-rise limit has the room: houses (rise 5), not shops and workshops (rise 2, no slack for the street's own level being a block off)
		if (street != Integer.MIN_VALUE && shell.spec().type().equals("house")) oy = Math.min(oy0, Math.max(oy, Math.min(street - 2, g + 1)));
		// an eave must not close the well: where a shell block stands over a cut cell at or below the hill surface, the ground there is lowered below it by standing the slab higher (as far as the door-rise limit has room: houses up to rise 5, others not)
		int need = oy;
		for (int i = 0; i < site.length; i++) if (site[i] && eave[i] != Integer.MAX_VALUE && raw[i] - oy > eave[i]) need = Math.max(need, raw[i] - eave[i]);
		oy = Math.max(oy, Math.min(Math.min(oy0, shell.spec().type().equals("house") ? g + 4 : g + 1), need));
		return wet(oy) ? oy0 : oy;
	}

	/** The cut and the approach fill for slab level {@code oy}, and the causeway cells (local x, z) that need rails. */
	record Cut(List<Box> boxes, List<int[]> causeway, List<int[]> open) {}

	Cut cut(int oy, VoxelGrid shellGrid) {
		// the approach fill is wanted when the street is level with the forecourt (a street above it, with the lot below the street, keeps the old flights down into the lot)
		boolean fill = g == oy && !elevated;
		boolean causeway = false;
		// a door whose outside cell is above the well floor (earth against the sill) gets a corridor: the approach is cut to the well floor all the way out to the lot's edge, wherever the ground there is above it.
		// That leads somewhere only when the street is within a block of the floor (level, or one below): with the street further below there is no room for a flight in the margin, and the old rule applies
		// to the door (the slab is lifted to the ground in front of it, which then ends the flight); with the street above, the lot is below it (an embankment) and the same holds.
		boolean corridor = false;
		for (int i = 0; i < site.length; i++) if (mainOut[i] && raw[i] >= oy + 1 && !elevated) corridor = g <= oy && g >= oy - 1 && (streetKnown || outMax <= oy + 1); // a street the brief knows is graded to that level; otherwise the exit must meet the raw ground
		if (fill)
			for (int z = z0; z < z0 + nz; z++)
				for (int x = x0; x < x0 + nx; x++) if (approach[idx(x, z)] && !isFootprint(x, z) && raw[idx(x, z)] <= oy - 2 && free(shellGrid, x, 0, z)) causeway = true;
		boolean[] filled = new boolean[nx * nz];
		if (fill)
			for (int z = z0; z < z0 + nz; z++)
				for (int x = x0; x < x0 + nx; x++) {
					int i = idx(x, z);
					boolean near = approach[i];
					if (!near && causeway && inLot(x, z) && !isFootprint(x, z) && inLot(x + 1, z) && inLot(x - 1, z) && inLot(x, z + 1) && inLot(x, z - 1)) { // the causeway is one cell wider than the door on each side
						for (Facing f : Facing.values()) if (inBox(x + f.dx, z + f.dz) && approach[idx(x + f.dx, z + f.dz)]) near = true;
					}
					if (near && inLot(x, z) && !isFootprint(x, z) && raw[i] < oy && oy - raw[i] <= 4 && free(shellGrid, x, 0, z)) filled[i] = true;
				}
		List<Box> out = new ArrayList<>();
		List<int[]> cw = new ArrayList<>();
		// the cut: vertical runs of free cells per column, merged along x
		for (int z = z0; z < z0 + nz; z++) {
			int[][] runs = new int[nx][];
			for (int x = x0; x < x0 + nx; x++) {
				int i = idx(x, z);
				runs[x - x0] = new int[0];
				if (!site[i] || filled[i]) continue;
				boolean foot = isFootprint(x, z);
				int r = raw[i];
				int need = corridor && approach[i] && !foot ? oy + 1 : oy + 2; // cells are cut only above the standing level (the ground flush with the door sill stays), except along a corridor
				if (mainOut[i] && !corridor) continue; // no corridor: the ground in front of the door stays, the door guard lifts the slab to it
				if (foot ? r <= oy + 1 : r < need) continue;
				int lo = foot ? 0 : -1; // the well floor cell, one below the slab
				List<Integer> rs = new ArrayList<>();
				int start = Integer.MIN_VALUE;
				for (int y = lo; y <= r - oy; y++) {
					boolean fr = y < r - oy && free(shellGrid, x, y, z) && !(foot && inRoom(x, y, z));
					if (fr && start == Integer.MIN_VALUE) start = y;
					if (!fr && start != Integer.MIN_VALUE) { rs.add(start); rs.add(y); start = Integer.MIN_VALUE; }
				}
				runs[x - x0] = rs.stream().mapToInt(Integer::intValue).toArray();
			}
			for (int a = 0; a < nx; a++) {
				if (runs[a].length == 0) continue;
				int b = a + 1;
				while (b < nx && Arrays.equals(runs[b], runs[a])) b++;
				for (int k = 0; k < runs[a].length; k += 2) out.add(new Box(Box3.of(x0 + a, runs[a][k], z, b - a, runs[a][k + 1] - runs[a][k], 1), Role.AIR, Layer.FOUNDATION, Box.NO_STOREY));
				a = b - 1;
			}
		}
		// the well floor: paving one below the slab where the column was cut and the cell is free
		for (int z = z0; z < z0 + nz; z++) {
			int a = Integer.MIN_VALUE;
			for (int x = x0; x <= x0 + nx; x++) {
				boolean pave = false;
				if (x < x0 + nx) {
					int i = idx(x, z);
					pave = site[i] && !filled[i] && !isFootprint(x, z) && !(mainOut[i] && !corridor) && raw[i] >= (corridor && approach[i] ? oy + 1 : oy + 2) && free(shellGrid, x, -1, z);
				}
				if (pave && a == Integer.MIN_VALUE) a = x;
				if (!pave && a != Integer.MIN_VALUE) { out.add(new Box(Box3.of(a, -1, z, x - a, 1, 1), Role.PAVING, Layer.FOUNDATION, Box.NO_STOREY)); a = Integer.MIN_VALUE; }
			}
		}
		// the approach fill and the forecourt paving: GROUND from the ground up to the paving, or foundation stone in a causeway
		for (int z = z0; z < z0 + nz; z++)
			for (int x = x0; x < x0 + nx; x++) {
				int i = idx(x, z);
				if (!filled[i]) continue;
				int base = raw[i] - oy; // local y of the first filled cell
				if (base < -1) out.add(new Box(Box3.of(x, base, z, 1, -1 - base, 1), causeway ? Role.FOUNDATION : Role.GROUND, Layer.FOUNDATION, Box.NO_STOREY));
				out.add(new Box(Box3.of(x, -1, z, 1, 1, 1), Role.PAVING, Layer.FOUNDATION, Box.NO_STOREY));
				if (causeway) cw.add(new int[] {x, z});
			}
		// the ground drawn at the exit can stand a block above the street level the brief gives (a plaza beside the street, rounding): a stair block at the last approach cells is a half step each way
		// (the exit cells belong to a forecourt or corridor that exists: a door whose outside cell was too deep to fill keeps the old stoop and flight)
		boolean forecourt = corridor;
		for (int i = 0; i < site.length; i++) forecourt |= mainOut[i] && filled[i];
		if (forecourt && (streetKnown || outMax == oy + 1))
			for (int z = z0; z < z0 + nz; z++)
				for (int x = x0; x < x0 + nx; x++) {
					int i = idx(x, z);
					if (approachEnd[i] && !isFootprint(x, z) && (filled[i] || raw[i] >= oy + 1) && free(shellGrid, x, 0, z)) out.add(new Box(Box3.of(x, 0, z, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
				}
		// the step below the door: where the approach is two or more cells deep, the door's outside cell is a stair block (a half step down to the well floor, not a full block)
		if (forecourt) // PORCHSTEP (was: only an approach two or more cells deep): the door row too when it is the lot edge; a plain cell there is a full block under the sill
			for (int z = z0; z < z0 + nz; z++)
				for (int x = x0; x < x0 + nx; x++) {
					int i = idx(x, z);
					if (mainOut[i] && !isFootprint(x, z) && (filled[i] || corridor && raw[i] >= oy + 1) && free(shellGrid, x, 0, z)) out.add(new Box(Box3.of(x, 0, z, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
				}
		List<int[]> open = new ArrayList<>();
		return new Cut(out, cw, open);
	}

	/** Whether a local cell is room air (the raster clears it itself, so the cut need not claim it). */
	private boolean inRoom(int x, int y, int z) {
		for (var room : shell.rooms()) for (Box3 q : room.parts()) if (x >= q.x() && x < q.maxX() && y >= q.y() && y < q.maxY() && z >= q.z() && z < q.maxZ()) return true;
		return false;
	}

	private static boolean free(VoxelGrid g, int x, int y, int z) { return g.get(x, y, z) == Role.AIR; }
}
