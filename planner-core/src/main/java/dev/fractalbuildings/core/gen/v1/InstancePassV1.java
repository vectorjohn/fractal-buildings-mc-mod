package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell plus request to a placed building (generator v1). The envelope sits at the type's margins
 * inside the lot (rotation from the lot's front, as v0); the ground slab sits at the highest ground
 * under the ground footprint (the volumes that start at y 0); every footprint column whose ground is
 * lower gets a foundation down to it. Front steps (plan step S, 4.8): the slab is lifted to the
 * ground in front of any door that would be buried, and a landing plus a flight of one-block steps
 * leads from each ground-storey door down to the terrain ({@link #steps}).
 */
public final class InstancePassV1 {
	private InstancePassV1() {}

	/** The town-wall types, whose doors are walled up where the lot has no room for a flight down to the ground. */
	/** Id prefix of the window that replaced a secondary door the placement walled up (DS-35): the {@code entrance-count} check reads it. */
	public static final String SEALED = "sealed-";

	private static final java.util.Set<String> WALL_TYPES = java.util.Set.of("wall", "curtain", "bastion");

	public static PlacedBuilding place(BuildingIr shell, GenRequest req) { return place(shell, req, Types.get(shell.spec().type()).limits()); }

	/** With explicit margins (tests place shells of unregistered types). */
	public static PlacedBuilding place(BuildingIr shell, GenRequest req, TypeLimits lim) { return placeChecked(shell, req, lim).building(); }

	/** A placement, and whether every flight and landing in it can be railed inside the lot (DS-8) and every door has a way down; {@code dropped}: the secondary doors (DS-35) the placement walled up, {@code droppedSides} their outward faces (bit per {@code Facing.ordinal()}). */
	public record Placed(PlacedBuilding building, boolean guarded, int droppedSides, List<Opening> dropped) {
		public Placed(PlacedBuilding building, boolean guarded) { this(building, guarded, 0, List.of()); }
	}

	public static Placed placeChecked(BuildingIr shell, GenRequest req, TypeLimits lim) {
		Placed p = placeKeepingDoors(shell, req, lim);
		if (!diagonalGap(p.building()) || entranceDoors(shell).stream().noneMatch(o -> secondary(shell, o))) return p;
		Placed bare = placeWithoutSecondaryDoors(shell, req, lim); // the rails of a secondary door's flight leave a gap: the door gives way
		return diagonalGap(bare.building()) ? p : bare;
	}

	public static Placed placeKeepingDoors(BuildingIr shell, GenRequest req, TypeLimits lim) {
		int sides = 0;
		List<Opening> gone = new ArrayList<>();
		BuildingIr cur = shell;
		for (int round = 0; ; round++) { // DS-35: a back or side door the ground does not allow is walled up (its window back), the building keeps its other doors; sealing one door can leave another unwalkable
			Once o = placeOnce(cur, req, lim);
			if (o.dropped().isEmpty() || round >= 3) return new Placed(o.placed().building(), o.placed().guarded(), sides, List.copyOf(gone));
			for (Opening d : o.dropped()) sides |= 1 << outward(d).ordinal();
			gone.addAll(o.dropped());
			cur = sealed(cur, o.dropped());
		}
	}

	/** As above, with every secondary door walled up: the last resort when a flight or landing to one of them cannot be railed inside the lot (DS-8 comes before DS-35). */
	public static Placed placeWithoutSecondaryDoors(BuildingIr shell, GenRequest req, TypeLimits lim) {
		List<Opening> drop = new ArrayList<>();
		for (Opening o : entranceDoors(shell)) if (secondary(shell, o)) drop.add(o);
		if (drop.isEmpty()) return placeKeepingDoors(shell, req, lim);
		Placed p = placeKeepingDoors(sealed(shell, drop), req, lim);
		int sides = p.droppedSides();
		for (Opening d : drop) sides |= 1 << outward(d).ordinal();
		List<Opening> gone = new ArrayList<>(drop);
		gone.addAll(p.dropped());
		return new Placed(p.building(), p.guarded(), sides, List.copyOf(gone));
	}

	/** The secondary doors (DS-35) of a shell. */
	public static List<Opening> secondaryDoors(BuildingIr shell) { return entranceDoors(shell).stream().filter(o -> secondary(shell, o)).toList(); }

	/** The shell-local cell just outside a door. */
	public static int[] outsideCell(Opening o) { Facing f = outward(o); return new int[] {o.box().x() + f.dx, o.box().z() + f.dz}; }

	/** Types whose doors other than the front ones are secondary (DS-35): walled up where the ground allows no way down, never lifting the building for them. */
	private static final java.util.Set<String> SECONDARY_TYPES = java.util.Set.of("house", "shop", "hall", "temple", "highrise", "workshop");

	/** True if two rails of a placement meet diagonally at a tread with no rail, step or building cell in the corner between them (a gap wider than a player; the rail corner posts skip the cell in front of a door). */
	public static boolean hasSecondaryDoor(BuildingIr shell) { return entranceDoors(shell).stream().anyMatch(o -> secondary(shell, o)); }

	private static boolean diagonalGap(PlacedBuilding b) {
		java.util.Set<Long> rails = new java.util.TreeSet<>(), steps = new java.util.TreeSet<>();
		for (Box x : b.siteBoxes()) {
			long k = ((long) x.box().x() << 32) ^ (x.box().z() & 0xffffffffL);
			if (x.role() == Role.RAIL) rails.add(k);
			else if (x.role() == Role.STAIR) steps.add(k);
		}
		Box3 bd = b.shell().bounds();
		for (Box t : b.siteBoxes()) {
			if (t.role() != Role.STAIR) continue;
			for (int dx = -1; dx <= 1; dx += 2)
				for (int dz = -1; dz <= 1; dz += 2) {
					int tx = t.box().x(), tz = t.box().z();
					long ka = ((long) (tx + dx) << 32) ^ (tz & 0xffffffffL), kb = ((long) tx << 32) ^ ((tz + dz) & 0xffffffffL), kc = ((long) (tx + dx) << 32) ^ ((tz + dz) & 0xffffffffL);
					int cx = tx + dx, cz = tz + dz;
					if (rails.contains(ka) && rails.contains(kb) && !rails.contains(kc) && !steps.contains(kc) && !(cx >= 0 && cz >= 0 && cx < bd.maxX() && cz < bd.maxZ())) return true;
				}
		}
		return false;
	}

	/** A secondary door (DS-35): an entrance door of a type in {@link #SECONDARY_TYPES} that does not face the front (north in the shell's frame). */
	static boolean secondary(BuildingIr shell, Opening o) {
		if (!SECONDARY_TYPES.contains(shell.spec().type())) return false;
		if (outward(o) != Facing.NORTH) return true;
		if (!shell.spec().type().equals("workshop")) return false;
		String in = o.to().equals(Opening.OUTSIDE) ? o.from() : o.to(); // a workshop's wing and lean-to doors may face the front too; the cart door is the hall's
		for (dev.fractalbuildings.core.ir.Room r : shell.rooms()) if (r.id().equals(in)) return r.volume().startsWith("wing") || r.volume().startsWith("lean");
		return false;
	}

	private record Once(Placed placed, List<Opening> dropped) {}

	private static Once placeOnce(BuildingIr shell, GenRequest req, TypeLimits lim) {
		Lot lot = req.lot();
		int r = lot.front().ordinal();
		boolean odd = (r & 1) == 1;
		int lw = odd ? lot.d() : lot.w(), ld = odd ? lot.w() : lot.d();
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		int lead = shell.spec().lead(); // blocks of the shell in front of the usual envelope: they reach over the street (air rights)
		if (w + 2 * lim.side() > lw || d - lead + lim.front() + lim.back() > ld) {
			throw new IllegalArgumentException("envelope " + w + "x" + d + " does not fit lot " + lot.w() + "x" + lot.d() + " with the type's margins");
		}
		Placement lotFrame = new Placement(lot.x(), 0, lot.z(), r, lw, ld);
		int ax = lotFrame.worldX(lim.side(), lim.front() - lead), az = lotFrame.worldZ(lim.side(), lim.front() - lead);
		int bx = lotFrame.worldX(lim.side() + w - 1, lim.front() - lead + d - 1), bz = lotFrame.worldZ(lim.side() + w - 1, lim.front() - lead + d - 1);
		int ox = Math.min(ax, bx), oz = Math.min(az, bz);
		boolean[][] fp = footprint(shell);
		Placement p0 = new Placement(ox, 0, oz, r, w, d);
		TerrainProbe t = req.terrain();
		int oy0 = Integer.MIN_VALUE;
		for (int lx = 0; lx < w; lx++)
			for (int lz = 0; lz < d; lz++) if (fp[lx][lz]) oy0 = Math.max(oy0, t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)));
		dev.fractalbuildings.core.gen.Brief.Walk walk = req.brief() == null ? null : req.brief().walk();
		// SHOPLOW: a street-front building digs in to the street instead of standing at the highest ground under its footprint
		SiteLevel dig = SiteLevel.applies(shell, req.brief()) ? new SiteLevel(shell, req, p0, fp) : null;
		int oy = dig == null ? oy0 : dig.level(oy0);
		Site placedSite = null;
		List<Opening> drop = new ArrayList<>(); // secondary doors to wall up (DS-35)
		// a door whose outside ground is higher than the slab would be buried: lift the slab to it (the ground as the site leaves it, cut included)
		for (int round = 0; ; round++) {
			placedSite = site(shell, lot, p0, fp, t, dig, oy, ox, oz, r);
			TerrainProbe eff = dev.fractalbuildings.core.terrain.SiteGround.probe(placedSite.provisional(), t);
			drop.clear();
			int need = oy;
			for (Opening o : entranceDoors(shell)) {
				Facing f = outward(o);
				int wx = p0.worldX(o.box().x() + f.dx, o.box().z() + f.dz), wz = p0.worldZ(o.box().x() + f.dx, o.box().z() + f.dz);
				if (secondary(shell, o)) { // a back or side door never lifts the building: where the ground outside is above the floor or outside the lot, it is walled up
					if (!lot.containsColumn(wx, wz) || eff.floorY(wx, wz) - entranceStorey(shell).y() > oy + 1) drop.add(o);
				} else if (lot.containsColumn(wx, wz)) need = Math.max(need, eff.floorY(wx, wz) - entranceStorey(shell).y());
			}
			if (need <= oy || round >= 4) break;
			oy = need;
		}
		// DOORBANK: a landmark hall or temple whose street runs 3 or more above its entrance floor stands on a podium (the footprint's foundation filled up to the street) instead of facing the bank
		if (walk == null && PODIUM_TYPES.contains(shell.spec().type()) && req.brief() != null && req.brief().frontY() != dev.fractalbuildings.core.gen.Brief.NO_FRONT_Y && !SiteLevel.LEGACY.get()) {
			int want = req.brief().frontY() + 1 - entranceStorey(shell).y();
			if (want - oy >= 1 - dev.fractalbuildings.core.check.DoorRise.BANK_RISE) {
				oy = want;
				placedSite = site(shell, lot, p0, fp, t, dig, oy, ox, oz, r);
			}
		}
		if (walk != null && walk.base() > oy) { // a town-wall lot stands at its level in the ring's walk profile (the ground under it is filled up to it)
			oy = walk.base();
			placedSite = site(shell, lot, p0, fp, t, dig, oy, ox, oz, r);
		}
		List<Box> site = placedSite.boxes();
		PlacedBuilding provisional = placedSite.provisional();
		List<Box> all = new ArrayList<>(site);
		List<Opening> unreachable = new ArrayList<>();
		boolean guarded = steps(provisional, lot, t, all, stoopRise(shell, lim), placedSite.causeway(), placedSite.open(), unreachable) && unreachable.isEmpty();
		for (Opening o : unreachable) if (secondary(shell, o) && !drop.contains(o)) drop.add(o);
		BuildingIr built = shell;
		if (walk != null) {
			all.addAll(Types.get(shell.spec().type()).walkJoins(shell, walk));
			built = Types.get(shell.spec().type()).walkClear(built, walk);
		}
		if (!unreachable.isEmpty() && WALL_TYPES.contains(shell.spec().type())) built = sealed(shell, unreachable); // a wall tower or gatehouse standing high above steep ground: no way down in the lot, so no door

		PlacedBuilding result = new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, built, all);
		if (drop.isEmpty() && shell.openings().stream().anyMatch(o -> o.kind() == OpeningKind.DOOR && secondary(shell, o))) { // the model of steps-reach-ground may judge a flight the search found unwalkable: such a secondary door is walled up too
			java.util.Set<String> failing = new java.util.TreeSet<>(badSecondary(result, lot, t)); // no way down, a straight run over 3 risers (DS-45), or the ground outside well above the sill (a bank)
			for (Opening o : entranceDoors(shell)) if (failing.contains(o.id()) && secondary(shell, o)) drop.add(o);
		}
		return new Once(new Placed(result, guarded), List.copyOf(drop));
	}

	/** Ids of the secondary doors of a placement that fail DS-45 or the ground: no way down, a straight run over 3 risers, or the ground past the lot straight out more than 2 blocks above the sill (DoorRise rise below -2, the DS-45 limit; round 2 had -1). */
	/** Landmark types that stand on a podium where the street is well above them (DOORBANK). */
	private static final java.util.Set<String> PODIUM_TYPES = java.util.Set.of("hall", "temple");

	private static java.util.Set<String> badSecondary(PlacedBuilding b, Lot lot, TerrainProbe t) {
		java.util.Set<String> bad = new java.util.TreeSet<>();
		dev.fractalbuildings.core.ir.TerrainPatch patch = dev.fractalbuildings.core.ir.TerrainPatch.sample(t, lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		dev.fractalbuildings.core.ir.SceneDoc scene = new dev.fractalbuildings.core.ir.SceneDoc(2, 0, patch, List.of(lot), List.of(b), List.of());
		for (dev.fractalbuildings.core.check.DoorRise.Door d : dev.fractalbuildings.core.check.DoorRise.measure(scene, b, dev.fractalbuildings.core.terrain.SiteGround.probe(b, t), java.util.Map.of()))
			if (!d.reached() || d.run() > 3 || d.rise() < -2) bad.add(d.door());
		return bad;
	}

	/** The site boxes of a placement at slab level {@code oy} (foundation under the footprint, then the dig-in's cut and fill) and the provisional building they make. */
	private record Site(List<Box> boxes, List<int[]> causeway, List<int[]> open, PlacedBuilding provisional) {}

	private static Site site(BuildingIr shell, Lot lot, Placement p0, boolean[][] fp, TerrainProbe t, SiteLevel dig, int oy, int ox, int oz, int r) {
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		List<Box> site = new ArrayList<>();
		for (int lz = 0; lz < d; lz++) {
			int runStart = 0, runH = 0;
			for (int lx = 0; lx <= w; lx++) {
				int h = lx < w && fp[lx][lz] ? oy - t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)) : 0;
				if (lx == w || h != runH) {
					if (runH > 0) site.add(new Box(Box3.of(runStart, -runH, lz, lx - runStart, runH, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					runStart = lx;
					runH = h;
				}
			}
		}
		List<int[]> causeway = List.of(), open = List.of();
		if (dig != null) {
			PlacedBuilding bare = new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, List.of());
			SiteLevel.Cut c = dig.cut(oy, VoxelGrid.of(bare));
			site.addAll(c.boxes());
			causeway = c.causeway();
			open = c.open();
		}
		return new Site(site, causeway, open, new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, site));
	}

	/** The shell with these doors walled up: their boxes become wall and the openings go. */
	static BuildingIr sealed(BuildingIr shell, List<Opening> doors) {
		if (shell.spec().type().equals("curtain")) { // a gallery with no door at all: solid wall (its only way in was the guard door)
			List<Box> solid = new ArrayList<>(shell.boxes());
			for (dev.fractalbuildings.core.ir.Volume v : shell.volumes()) solid.add(new Box(v.box(), Role.WALL, Layer.SHELL, 0));
			return new BuildingIr(shell.spec(), shell.bounds(), shell.storeys(), shell.volumes(), List.of(), List.of(), shell.roofs(), solid, List.of());
		}
		List<Box> boxes = new ArrayList<>(shell.boxes());
		List<Opening> openings = new ArrayList<>(shell.openings());
		for (Opening o : doors) {
			int storey = Box.NO_STOREY;
			for (Box b : shell.boxes()) if (b.role() == Role.DOOR && b.box().equals(o.box())) storey = b.storey();
			if (WALL_TYPES.contains(shell.spec().type()) || o.box().sy() < 2) boxes.add(new Box(o.box(), Role.WALL, Layer.SHELL, storey));
			else { // the window the door replaced comes back: wall below, glass above
				Box3 b = o.box();
				boxes.add(new Box(Box3.of(b.x(), b.y(), b.z(), b.sx(), 1, b.sz()), Role.WALL, Layer.SHELL, storey));
				boxes.add(new Box(Box3.of(b.x(), b.y() + 1, b.z(), b.sx(), 1, b.sz()), Role.WINDOW, Layer.SHELL, storey));
				String room = o.to().equals(Opening.OUTSIDE) ? o.from() : o.to();
				openings.add(new Opening(SEALED + o.id(), OpeningKind.WINDOW, Box3.of(b.x(), b.y() + 1, b.z(), b.sx(), 1, b.sz()), outward(o), room, Opening.OUTSIDE));
			}
			openings.remove(o);
		}
		return new BuildingIr(shell.spec(), shell.bounds(), shell.storeys(), shell.volumes(), shell.rooms(), openings, shell.roofs(), boxes, shell.hooks());
	}

	/** The lowest storey with rooms (storey 0, except under stilts, where it is the open leg zone): its exterior doors need a way down. */
	public static Storey entranceStorey(BuildingIr shell) {
		int lowest = Integer.MAX_VALUE;
		for (dev.fractalbuildings.core.ir.Room r : shell.rooms()) lowest = Math.min(lowest, r.storey());
		return shell.storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
	}

	/** Ground-storey doors that lead outside: the ones that need a way down to the ground. */
	static List<Opening> entranceDoors(BuildingIr shell) {
		List<Opening> doors = new ArrayList<>();
		if (shell.storeys().isEmpty()) return doors;
		Storey s0 = entranceStorey(shell);
		for (Opening o : shell.openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
			doors.add(o);
		}
		return doors;
	}

	public static Facing outward(Opening o) { return o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2); }

	/**
	 * Front steps: for each entrance door, a landing one cell out at slab height, then a flight down
	 * one block per cell to the ground, straight out while the lot allows, else turning along the
	 * facade (breadth-first, so the shortest flight; ties prefer straight, then clockwise). Every
	 * step stands on foundation to the ground. A door whose outside ground is level with, or one
	 * below, the slab needs none. When no route exists the door is left as it is and the
	 * {@code entrance-reachable} invariant reports it.
	 */
	private static boolean steps(PlacedBuilding provisional, Lot lot, TerrainProbe t, List<Box> site, int stoopRise, List<int[]> causeway, List<int[]> open, List<Opening> unreachable) {
		BuildingIr shell = provisional.shell();
		List<Opening> doors = entranceDoors(shell);
		if (doors.isEmpty()) return true;
		boolean guarded = true; // false when a landing or flight had to go where no rail can stand inside the lot
		Placement p = provisional.placement();
		int base = provisional.oy(), oy = base + entranceStorey(shell).y(); // oy: the entrance floor level (above the slab under stilts)
		VoxelGrid grid = VoxelGrid.of(provisional);
		boolean[][] fp = footprint(shell);
		int[] cs = {p.localX(lot.x(), lot.z()), p.localX(lot.x() + lot.w() - 1, lot.z()), p.localX(lot.x(), lot.z() + lot.d() - 1), p.localX(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		int[] zs = {p.localZ(lot.x(), lot.z()), p.localZ(lot.x() + lot.w() - 1, lot.z()), p.localZ(lot.x(), lot.z() + lot.d() - 1), p.localZ(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		int x0 = Math.min(Math.min(cs[0], cs[1]), Math.min(cs[2], cs[3])), x1 = Math.max(Math.max(cs[0], cs[1]), Math.max(cs[2], cs[3]));
		int z0 = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3])), z1 = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
		int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
		boolean[] blocked = new boolean[nx * nz];
		TerrainProbe wrapped = dev.fractalbuildings.core.terrain.SiteGround.probe(provisional, t); // the effective ground: equals the raw probe until a building digs in (S2)
		int[] ground = new int[nx * nz];
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int i = (z - z0) * nx + (x - x0);
				ground[i] = wrapped.floorY(p.worldX(x, z), p.worldZ(x, z));
				blocked[i] = x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z];
			}
		int[] outG = new int[nx * nz]; // lowest ground just outside the lot beside each edge cell, MAX_VALUE for inner cells: a rail there would stand outside the lot
		java.util.Arrays.fill(outG, Integer.MAX_VALUE);
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++)
				for (Facing f : Facing.values()) {
					int ax = x + f.dx, az = z + f.dz;
					if (ax >= x0 && ax <= x1 && az >= z0 && az <= z1) continue;
					int i = (z - z0) * nx + (x - x0);
					outG[i] = Math.min(outG[i], t.floorY(p.worldX(ax, az), p.worldZ(ax, az)));
				}
		int[] flightTop = new int[nx * nz]; // highest solid of a placed flight's cell, MIN_VALUE where there is none
		java.util.Arrays.fill(flightTop, Integer.MIN_VALUE);
		for (Opening o : doors) { // keep every door's own outside cell free of other flights
			Facing f = outward(o);
			int x = o.box().x() + f.dx, z = o.box().z() + f.dz;
			if (x >= x0 && x <= x1 && z >= z0 && z <= z1) blocked[(z - z0) * nx + (x - x0)] = true;
		}
		boolean[] deckCells = new boolean[nx * nz]; // cells of a stoop built for an earlier door
		List<int[]> treads = new ArrayList<>(); // every placed step or landing cell: x, z, tread level, least drop to the ground beside it that gets a rail
		List<int[]> walk = new ArrayList<>(); // STEPBURY: cells a flight ends on the terrain: x, z, standing level
		for (int[] cw : causeway) { // the approach causeway (a level forecourt on fill): a landing no flight may cross, railed on its open sides
			int i = (cw[1] - z0) * nx + (cw[0] - x0);
			if (cw[0] < x0 || cw[0] > x1 || cw[1] < z0 || cw[1] > z1) continue;
			blocked[i] = true;
			flightTop[i] = oy;
			treads.add(new int[] {cw[0], cw[1], oy, RAIL_DROP_LANDING});
		}
		for (int[] oc : open) { // the exit cells of the approach: no rail stands on them
			if (oc[0] < x0 || oc[0] > x1 || oc[1] < z0 || oc[1] > z1) continue;
			blocked[(oc[1] - z0) * nx + (oc[0] - x0)] = true;
		}
		boolean posted = shell.volumes().stream().anyMatch(v -> v.id().startsWith("leg")); // a hall on posts: its landing is a deck on posts
		for (Opening o : doors) {
			Facing out = outward(o);
			int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz;
			if (sx < x0 || sx > x1 || sz < z0 || sz > z1) continue;
			int s0 = (sz - z0) * nx + (sx - x0);
			if (deckCells[s0]) continue; // the other leaf of a double door: already on the stoop
			int[] parent = new int[nx * nz], depth = new int[nx * nz];
			int found = -1;
			boolean merged = false;
			// a stoop (shopfronts): a door above the ground gets a landing at door level across the front, tried first;
			// the flight down then starts from the landing's end instead of beside the door
			List<int[]> candidates = new ArrayList<>();
			int nOut = 0; // rows between the door row and the lot's street-facing edge row
			while (true) {
				int ex = sx + (nOut + 1) * out.dx, ez = sz + (nOut + 1) * out.dz;
				if (ex < x0 || ex > x1 || ez < z0 || ez > z1) break;
				nOut++;
			}
			int nDeck = 0;
			// a door wider than one cell opens onto a landing as wide as the door: no drop beside the flight, in the doorway
			List<Integer> front = new ArrayList<>();
			boolean lowFront = false;
			Box3 ob0 = o.box();
			for (int x = ob0.x(); x < ob0.maxX(); x++)
				for (int z = ob0.z(); z < ob0.maxZ(); z++) {
					int fx = x + out.dx, fz = z + out.dz;
					if (fx < x0 || fx > x1 || fz < z0 || fz > z1) continue;
					int fi = (fz - z0) * nx + (fx - x0);
					if (!front.contains(fi)) front.add(fi);
					lowFront |= ground[fi] < oy;
				}
			boolean wide = front.size() > 1 && lowFront;
			// PORCHSTEP r2: a double door over ground a block under the sill steps under every leaf, or (where one leaf cannot hold a step) none: a plain drop
			int leaves = (ob0.maxX() - ob0.x()) * (ob0.maxZ() - ob0.z()), stepFrom = oy + 1;
			if (!wide && leaves > 1) {
				boolean any = false, fits = front.size() == leaves;
				for (int fi : front) {
					if (ground[fi] != oy) continue;
					any = true;
					int cx = x0 + fi % nx, cz = z0 + fi / nx;
					fits &= !(cx >= 0 && cz >= 0 && cx < fp.length && cz < fp[0].length && fp[cx][cz]) && (grid.get(cx, oy - base, cz) == Role.AIR || grid.get(cx, oy - base, cz) == Role.STAIR) && flightTop[fi] == Integer.MIN_VALUE && !deckCells[fi]; // (a step the site level already put there counts)
				}
				if (any && !fits) stepFrom = oy;
				if (any && fits) {
					for (int fi : front) {
						deckCells[fi] = true;
						blocked[fi] = true;
						if (ground[fi] != oy) continue;
						int cx = x0 + fi % nx, cz = z0 + fi / nx;
						flightTop[fi] = oy;
						if (grid.get(cx, oy - base, cz) != Role.STAIR) site.add(new Box(Box3.of(cx, oy - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
						treads.add(new int[] {cx, cz, oy, RAIL_DROP_LANDING});
					}
					continue;
				}
			}
			if (ground[s0] <= oy + 1 - stoopRise && !secondary(shell, o) || wide) { // a back or side door (DS-35) takes a plain flight: the stoop is the shopfront's
				for (int[] cand : stoopCandidates(out, sx, sz, x0, x1, z0, z1, nx, ground, oy, base, grid, fp)) {
					if (wide) { boolean covers = true; for (int fi : front) if (ground[fi] < oy + 1) { boolean in = false; for (int c : cand) in |= c == fi; covers &= in; } if (!covers) continue; }
					candidates.add(cand);
				}
				if (wide) { // just the width of the door
					int[] strip = front.stream().filter(fi -> ground[fi] < oy + 1).mapToInt(Integer::intValue).toArray();
					boolean ok = true;
					for (int fi : strip) ok &= deckOk(x0 + fi % nx, z0 + fi / nx, x0, x1, z0, z1, nx, ground, oy, base, grid, fp);
					if (ok) candidates.add(strip);
				}
			}
			// a landing never covers a cell an earlier door's flight or landing already uses (DOOR2: it overwrote the end of a neighbour's flight, leaving its steps short of the ground)
			candidates.removeIf(cand -> { for (int c : cand) if (flightTop[c] != Integer.MIN_VALUE) return true; return false; });
			// the same landings without the cells on the lot line that need a rail (only pass 0 takes these): the landing stays inside the lot
			for (int ci = 0, n = candidates.size(); ci < n; ci++) {
				int[] full = candidates.get(ci);
				int[] kept = java.util.Arrays.stream(full).filter(c -> oy - outG[c] < RAIL_DROP_LANDING).toArray();
				if (kept.length == 0 || kept.length == full.length || !connected(kept, s0, nx)) continue;
				candidates.add(kept);
			}
			if (wide) candidates.removeIf(cand -> { for (int fi : front) if (ground[fi] < oy + 1) { boolean in = false; for (int c : cand) in |= c == fi; if (!in) return true; } return false; }); // PORCHSTEP r2: a landing under every leaf (also the trimmed ones)
			nDeck = candidates.size();
			candidates.add(new int[] {s0});
			boolean[] deck = null;
			int foundPass = -1, foundCi = -1;
			for (int mode = 0; mode < 3 && found < 0; mode++) { // STAIRJOG: mode 0 tries only flights that run straight (no turn on the way down, DS-45 run and turn sizes) over every rail pass; mode 1 is the search as before
			for (int strictPass = 0; strictPass < 3 && found < 0; strictPass++) { // pass 0: only landings and flights whose open sides lie inside the lot (every one can be railed); pass 1: flights only (a landing on the lot line stays); pass 2: any route
			for (int stepLo = stepFrom; stepLo >= oy && found < 0; stepLo--) { // PORCHSTEP: first a door whose outside ground is a block under the sill gets a step (ground >= sill - 0); only where no route is found, the plain drop to the ground of old (stepLo = oy)
			for (int runLimit = SiteLevel.LEGACY.get() ? 1 : 0; runLimit < 2 && found < 0; runLimit++) { // 0: DS-45 stair runs (at most 3 risers without a level tread); 1: where no such route fits at this rail pass, today's shortest flight (a railed long run before an unrailed short one)
			boolean limited = runLimit == 0;
			if (mode < 2 && (!limited || strictPass > 1)) continue; // straight only where every rail stays inside the lot or on the lot line; an unrailed straight flight does not beat a railed jog
			boolean strict = strictPass <= 1, strictLanding = strictPass == 0;
			for (int ci = 0; ci < candidates.size(); ci++) {
				int[] srcs = candidates.get(ci);
				if ((strictPass == 0 || mode < 2) && nDeck > 0 && ci >= nDeck) continue; // a stoop door keeps its landing while one is possible (mode 0: always, the bare door cell is the old search's last resort)
				if (strictLanding && ci < nDeck) { boolean edge = false; for (int c : srcs) edge |= oy - outG[c] >= RAIL_DROP_LANDING; if (edge) continue; }
				boolean[] saved = blocked.clone();
				for (int c : srcs) blocked[c] = false;
				// pass 1: a flight of its own to the terrain. Pass 2 (only if pass 1 found none, e.g. earlier flights wall
				// the margin off on a steep slope): the flight may also end by stepping onto an earlier flight, one block
				// down or level, and share the rest of its way down.
				for (int pass = 0; pass < (mode < 2 ? 1 : 2) && found < 0; pass++) { // mode 0: a flight of its own only (merging onto an earlier flight is for the old search)
					java.util.Arrays.fill(parent, -2);
					if (limited) {
						boolean[] mg = {false};
						found = searchRuns(srcs, s0, ci, nDeck, strict, strictLanding, stepLo, pass, nx, nz, x0, x1, z0, z1, oy, base, ground, outG, blocked, flightTop, grid, out, sx, sz, nOut, parent, depth, mg, mode < 2, mode == 0);
						merged = mg[0];
						continue;
					}
					int[] queue = new int[nx * nz];
					int head = 0, tail = 0;
					for (int c : srcs) {
						parent[c] = -1;
						depth[c] = 0;
						queue[tail++] = c;
					}
					Facing[] dirs = {out, out.rotate(1), out.rotate(3), out.rotate(2)};
					while (head < tail && found < 0) {
						int c = queue[head++];
						int cx = x0 + c % nx, cz = z0 + c / nx, d = depth[c];
						int g = ground[c];
						int h = oy - d;
						boolean ends = d == 0 ? g >= stepLo && g <= oy + 1 : g >= h + 1 && g <= h + 2; // off the door floor (top oy + 1) or the last stair (top h + 3/2, one cell back) onto ground at most a block below and at most half a block above
						if (ci < nDeck && (cx - sx) * out.dx + (cz - sz) * out.dz != nOut) ends = false; // a flight from a landing must come down on the lot's street row, not behind the building
						int top = ends ? g : h; // highest solid under the feet
						// the cells the walker occupies (and the step block itself) must be free of the building
						boolean free = true;
						for (int y = top - base; y <= top + 3 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
						if (!free) continue;
						if (ends) { found = c; break; }
						if (strict && !(ci < nDeck && d == 0 && !strictLanding) && oy - d - outG[c] >= (ci < nDeck && d == 0 ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT)) continue; // a rail would have to stand outside the lot: this cell is not railable
						if (g > h + 2) continue;
						for (Facing f : dirs) {
							int nxx = cx + f.dx, nzz = cz + f.dz;
							if (nxx < x0 || nxx > x1 || nzz < z0 || nzz > z1) continue;
							int ni = (nzz - z0) * nx + (nxx - x0);
							if (parent[ni] != -2) continue;
							if (blocked[ni]) {
								if (pass == 1 && flightTop[ni] != Integer.MIN_VALUE && (flightTop[ni] == h - 1 || flightTop[ni] == h)) {
									found = c; // c is a step; its neighbour is an earlier flight at the right height
									merged = true;
									break;
								}
								continue;
							}
							parent[ni] = c;
							depth[ni] = d + 1;
							queue[tail++] = ni;
						}
					}
				}
				if (found >= 0) {
					foundPass = strictPass;
					foundCi = ci;
					if (ci < nDeck) {
						deck = new boolean[nx * nz];
						for (int c : srcs) deck[c] = true;
					}
					break;
				}
				System.arraycopy(saved, 0, blocked, 0, blocked.length);
				merged = false;
			}
			}
			}
			}
			}
			if (found < 0) {
				unreachable.add(o);
				continue;
			}
			if (foundPass >= 2) guarded = false;
			else if (foundPass == 1 && foundCi < nDeck) for (int c : candidates.get(foundCi)) guarded &= oy - outG[c] < RAIL_DROP_LANDING;
			if (deck != null) { // the landing: a level block at door level per cell; solid below, or (a hall on posts) open between posts so the floor under stays open to the street
				Facing lat = out.rotate(1);
				int outer = Integer.MIN_VALUE;
				for (int c = 0; c < deck.length; c++) if (deck[c]) outer = Math.max(outer, (x0 + c % nx - sx) * out.dx + (z0 + c / nx - sz) * out.dz);
				for (int c = 0; c < deck.length; c++) {
					if (!deck[c]) continue;
					deckCells[c] = true;
					blocked[c] = true;
					flightTop[c] = oy;
					int cx = x0 + c % nx, cz = z0 + c / nx, g = ground[c];
					boolean post = (cx - sx) * out.dx + (cz - sz) * out.dz == outer && Math.floorMod((cx - sx) * lat.dx + (cz - sz) * lat.dz, 3) == 0;
					if (!posted) site.add(new Box(Box3.of(cx, g - base, cz, 1, oy - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					else if (post) site.add(new Box(Box3.of(cx, g - base, cz, 1, oy - g, 1), Role.BEAM, Layer.FOUNDATION, Box.NO_STOREY));
					site.add(new Box(Box3.of(cx, oy - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
					treads.add(new int[] {cx, cz, oy, RAIL_DROP_LANDING});
				}
			}
			for (int c = found; c >= 0; c = parent[c]) {
				blocked[c] = true; // other doors' flights keep off this one
				if (deck != null && deck[c]) continue; // a landing cell, placed above
				flightTop[c] = c == found && !merged ? ground[c] : oy - depth[c];
				if (c == found && !merged) { walk.add(new int[] {x0 + c % nx, z0 + c / nx, ground[c]}); continue; } // ends on the terrain itself
				int cx = x0 + c % nx, cz = z0 + c / nx, h = oy - depth[c], g = ground[c];
				if (h >= g) { // a step on the ground itself (h == g) needs no foundation under it
					if (h > g) site.add(new Box(Box3.of(cx, g - base, cz, 1, h - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					site.add(new Box(Box3.of(cx, h - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
					treads.add(new int[] {cx, cz, h, parent[c] >= 0 && depth[parent[c]] == depth[c] ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT});
				}
			}
			if (wide) for (int fi : front) { // PORCHSTEP r2: a leaf of a double door whose ground is only a block under the sill has no step of the flight or landing beside it: it gets its own
				if (ground[fi] != oy || flightTop[fi] != Integer.MIN_VALUE) continue;
				int cx = x0 + fi % nx, cz = z0 + fi / nx;
				if (cx >= 0 && cz >= 0 && cx < fp.length && cz < fp[0].length && fp[cx][cz] || grid.get(cx, oy - base, cz) != Role.AIR) continue;
				blocked[fi] = true;
				flightTop[fi] = oy;
				site.add(new Box(Box3.of(cx, oy - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
				treads.add(new int[] {cx, cz, oy, RAIL_DROP_LANDING});
			}
		}
		boolean[] doorFront = new boolean[nx * nz]; // every cell in front of a door leaf (a wide door has several): a rail there would wall the door
		for (Opening o : doors) {
			Facing f = outward(o);
			Box3 ob = o.box();
			for (int x = ob.x(); x < ob.maxX(); x++)
				for (int z = ob.z(); z < ob.maxZ(); z++) {
					int fx = x + f.dx, fz = z + f.dz;
					if (fx >= x0 && fx <= x1 && fz >= z0 && fz <= z1) doorFront[(fz - z0) * nx + (fx - x0)] = true;
				}
		}
		List<Opening> extDoors = new ArrayList<>(); // every exterior door of any storey: no rail stands in front of one at its own height
		for (Opening o : shell.openings()) if (o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE) != o.from().equals(Opening.OUTSIDE)) extDoors.add(o);
		if (HEADROOM_ENABLED) headroom(treads, walk, doors, x0, x1, z0, z1, nx, ground, oy, base, grid, site);
		rails(treads, lot, p, x0, x1, z0, z1, nx, ground, blocked, doorFront, extDoors, grid, base, site);
		return guarded;
	}

	/** Blocks of claimed air over every step, landing and door approach: 4 like a street (DS-54 "door steps keep street headroom", DOORAIR 6.32; STEPBURY 6.13 had 3): the world's terrain, a beard or a tree must not close a flight). */
	static final int HEADROOM = 4;
	/** Test switch (STEPBURY client test control): false leaves the old site boxes, without the claimed headroom. Set before the planner of a world is built. */
	public static volatile boolean HEADROOM_ENABLED = !Boolean.getBoolean("fb.noheadroom");
	private static final int NONE = Integer.MIN_VALUE;

	/**
	 * STEPBURY (ROADMAP 6.13): the {@link #HEADROOM} cells over every tread, over the cell a flight ends on and over the cell in front of each
	 * door become {@code AIR} site boxes, so the piece claims them and the world-side terrain (a {@code beard_thin} raise toward the slab,
	 * sand, a tree grown later: {@code mod/worldgen/BuildingAirClear}) cannot bury or close a way in. Only cells the shell leaves free get a
	 * box, because site boxes raster after the shell and an {@code AIR} box would erase a wall or an eave; the boxes start above the
	 * standing level, so the effective ground ({@code SiteGround}) is unchanged. They are listed before the rails.
	 */
	private static void headroom(List<int[]> treads, List<int[]> walk, List<Opening> doors, int x0, int x1, int z0, int z1, int nx, int[] ground, int oy, int base, VoxelGrid grid, List<Box> site) {
		boolean[] done = new boolean[ground.length];
		List<int[]> cells = new ArrayList<>();
		for (int[] t : treads) cells.add(new int[] {t[0], t[1], t[2]});
		cells.addAll(walk);
		for (Box s : site) { // the dig-in's own steps (SiteLevel: the approach's exit steps) too
			Box3 q = s.box();
			if (s.role() == Role.STAIR && q.x() >= x0 && q.x() <= x1 && q.z() >= z0 && q.z() <= z1) for (int y = q.y(); y < q.maxY(); y++) cells.add(new int[] {q.x(), q.z(), base + y});
		}
		for (Opening o : doors) {
			Facing f = outward(o);
			Box3 ob = o.box();
			for (int x = ob.x(); x < ob.maxX(); x++)
				for (int z = ob.z(); z < ob.maxZ(); z++) {
					int fx = x + f.dx, fz = z + f.dz;
					if (fx >= x0 && fx <= x1 && fz >= z0 && fz <= z1) cells.add(new int[] {fx, fz, Math.max(ground[(fz - z0) * nx + (fx - x0)], oy)});
				}
		}
		for (int[] c : cells) {
			int i = (c[1] - z0) * nx + (c[0] - x0);
			if (done[i]) continue;
			done[i] = true;
			int run = NONE;
			for (int y = c[2] + 1; y <= c[2] + 1 + HEADROOM; y++) {
				boolean air = y <= c[2] + HEADROOM && grid.get(c[0], y - base, c[1]) == Role.AIR;
				if (air && run == NONE) run = y;
				if (!air && run != NONE) {
					site.add(new Box(Box3.of(c[0], run - base, c[1], 1, y - run, 1), Role.AIR, Layer.FOUNDATION, Box.NO_STOREY));
					run = NONE;
				}
			}
		}
	}

	/** Most descent in the walker's half blocks between level treads (DS-45 stair runs: {@code MAX_RISERS} 3 blocks, rounded up). */
	static final int MAX_RISERS = 3, MAX_CUR = 2 * MAX_RISERS;

	/**
	 * The run-aware flight search (DS-45 "Stair runs", the plan's S6 search): a breadth-first search over (cell, descent so far, half blocks
	 * of descent since the last level tread). A move is a descent (one block down: 2 half blocks), a level move onto a landing cell (resets the
	 * run: a landing, or the corner cell of a turn), or the end onto the ground. The walker model is {@code DoorRise.run}'s: the first cell out of the
	 * door is a half block below the door floor (1), each step down adds 2, the last step onto ground one half block lower adds 1; a run is
	 * (half blocks + 1) / 2 blocks and may not pass {@code MAX_RISERS}. Routes never use a cell twice. Returns the cell the route ends at (or, for
	 * a merge onto an earlier flight, the last step), or -1; {@code parent} and {@code depth} (per cell) hold the route.
	 */
	private static int searchRuns(int[] srcs, int s0, int ci, int nDeck, boolean strict, boolean strictLanding, int stepLo, int pass, int nx, int nz, int x0, int x1, int z0, int z1,
			int oy, int base, int[] ground, int[] outG, boolean[] blocked, int[] flightTop, VoxelGrid grid, Facing out, int sx, int sz, int nOut, int[] parent, int[] depth, boolean[] merged, boolean straight, boolean minRun) {
		int maxD = 2;
		for (int gi : ground) maxD = Math.max(maxD, oy - gi + 3);
		maxD = Math.min(maxD, 40);
		final int curN = MAX_CUR + 1, stride = (maxD + 1) * curN;
		int ns = nx * nz * stride;
		boolean[] seen = new boolean[ns];
		int[] from = new int[ns];
		int[] queue = new int[ns];
		int head = 0, tail = 0;
		boolean[] isSrc = new boolean[nx * nz]; // the landing (stoop) cells are the route's start: no route comes back through them
		for (int c : srcs) isSrc[c] = true;
		for (int c : srcs) {
			int st = c * stride + (c == s0 ? 1 : 0);
			seen[st] = true;
			from[st] = -1;
			queue[tail++] = st;
		}
		Facing[] dirs = {out, out.rotate(1), out.rotate(3), out.rotate(2)};
		int[] mark = new int[nx * nz], markState = new int[nx * nz]; // the cells of the route to the state being expanded (stamp), and their states: the route walks below become lookups
		int stamp = 0;
		while (head < tail) {
			int st = queue[head++];
			int c = st / stride, rest = st % stride, d = rest / curN, cur = rest % curN;
			int cx = x0 + c % nx, cz = z0 + c / nx;
			int g = ground[c], h = oy - d;
			boolean arrivedLevel = cur == 0;
			boolean row = (cx - sx) * out.dx + (cz - sz) * out.dz == nOut;
			if (d == 0 && !arrivedLevel) { // the source cell itself standing on ground: no flight
				boolean ends = g >= stepLo && g <= oy + 1 && !(ci < nDeck && !row);
				boolean free = true;
				int y0 = grid.get(cx, g - base, cz) == Role.STAIR ? g + 1 - base : g - base; // the step below the door (a stair block the dig-in put there) is what the walker stands on
				for (int y = y0; y <= g + 3 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
				if (ends && free) { chain(st, from, stride, curN, parent, depth, c, null); return c; }
			}
			boolean free = true;
			for (int y = h - base; y <= h + 3 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
			if (!free) continue;
			int thr = arrivedLevel || ci < nDeck && d == 0 ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT;
			if (strict && !(ci < nDeck && d == 0 && !strictLanding) && h - outG[c] >= thr) continue;
			if (g > h + 2) continue;
			stamp++;
			int rlen = 0;
			for (int s = st; s >= 0; s = from[s]) { int rc = s / stride; mark[rc] = stamp; markState[rc] = s; rlen++; }
			for (Facing f : dirs) {
				int nxx = cx + f.dx, nzz = cz + f.dz;
				if (nxx < x0 || nxx > x1 || nzz < z0 || nzz > z1) continue;
				int ni = (nzz - z0) * nx + (nxx - x0);
				if (blocked[ni]) {
					if (pass == 1 && flightTop[ni] != Integer.MIN_VALUE && (flightTop[ni] == h - 1 || flightTop[ni] == h) && mark[ni] != stamp) {
						chain(st, from, stride, curN, parent, depth, c, null);
						merged[0] = true;
						return c;
					}
					continue;
				}
				if (isSrc[ni] || mark[ni] == stamp) continue;
				// the end: ground at most a block below the last step and at most half a block above it, on the lot's street row when the flight starts from a landing
				int gn = ground[ni];
				boolean endsHere = gn >= h && gn <= h + 1 && !(ci < nDeck && (nxx - sx) * out.dx + (nzz - sz) * out.dz != nOut) && (gn > h || cur + 1 <= MAX_CUR);
				if (endsHere && !endTouchesOk(c, ni, gn, mark, markState, stamp, stride, curN, oy, nx, isSrc, nz)) endsHere = false;
				if (endsHere && minRun && cur < 4 && restBefore(st, from, stride, curN)) endsHere = false; // STAIRJOG r2: DS-45 runs of at least 2 risers: no single riser between the last rest and the ground
				if (endsHere && rlen + 1 > d + 1 + (d + 1) / 3 + 3) endsHere = false; // a route much longer than the descent needs (a landing every 3 risers and a corner or two) is a walk across the lot, not a flight
				if (endsHere) {
					boolean fr = true;
					for (int y = gn - base; y <= gn + 3 - base && fr; y++) fr = grid.get(nxx, y, nzz) == Role.AIR;
					if (fr) { chain(st, from, stride, curN, parent, depth, ni, new int[] {c, d + 1}); return ni; }
				}
				if (straight && from[st] >= 0 && (cx - (x0 + from[st] / stride % nx) != f.dx || cz - (z0 + from[st] / stride / nx) != f.dz)) continue; // STAIRJOG mode 0: no turn after the first move (only the last step onto the ground may leave the line)
				if (d + 1 <= maxD && cur + 2 <= MAX_CUR && touchesRouteOk(c, ni, oy - d - 1, false, mark, markState, stamp, stride, curN, oy, nx, isSrc, nz, ground)) { // a step down
					int ns1 = ni * stride + (d + 1) * curN + cur + 2;
					if (!seen[ns1]) { seen[ns1] = true; from[ns1] = st; queue[tail++] = ns1; }
				}
				if (minRun && d > 0 && cur > 0 && cur < 4) continue; // no rest after a single riser (the level move is the only move left past here)
				int nl = ni * stride + d * curN; // a level landing cell: ends the run
				if (!seen[nl] && touchesRouteOk(c, ni, h, true, mark, markState, stamp, stride, curN, oy, nx, isSrc, nz, ground)) { seen[nl] = true; from[nl] = st; queue[tail++] = nl; }
			}
		}
		return -1;
	}
	/** True if the route to state {@code st} already had a rest (a level cell below the door level). */
	private static boolean restBefore(int st, int[] from, int stride, int curN) {
		for (int s = st; s >= 0; s = from[s]) { int rest = s % stride; if (rest % curN == 0 && rest / curN > 0) return true; }
		return false;
	}


	/**
	 * A new tread at level {@code lvl} may touch (four ways) an earlier cell of the route other than the one it comes from only if no open edge is left
	 * between them: no rail can stand on a route cell, so two treads side by side must be within a block of each other's level, and a tread beside a cell
	 * where the route walks on the natural ground (its step is buried) may stand no more than the rail threshold above that ground. The landing cells (the
	 * stoop) count as treads at the door's level.
	 */
	private static boolean touchesRouteOk(int cur0, int cell, int lvl, boolean levelMove, int[] mark, int[] markState, int stamp, int stride, int curN, int oy, int nx, boolean[] isSrc, int nz, int[] ground) {
		int cx = cell % nx, cz = cell / nx;
		int thrN = levelMove ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT, topN = Math.max(lvl + 1, ground[cell]);
		for (int k = 0; k < 4; k++) { // the four ways; the landing cells are treads at the door's level
			int ax = cx + DX4[k], az = cz + DZ4[k];
			if (ax < 0 || az < 0 || ax >= nx || az >= nz) continue;
			int rc = az * nx + ax;
			if (isSrc[rc] && rc != cur0 && !edgeOk(oy, RAIL_DROP_LANDING, oy + 1, lvl, thrN, topN)) return false;
			if (mark[rc] != stamp || rc == cur0) continue; // an earlier cell of the route other than the one it comes from
			int s = markState[rc];
			int d = (s % stride) / curN, cur = s % curN, rl = oy - d;
			if (!edgeOk(rl, cur == 0 || d == 0 ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT, Math.max(rl + 1, ground[rc]), lvl, thrN, topN)) return false;
		}
		return true;
	}

	/** The ground cell a route ends on ({@code ground} first free level): it may not stand a rail threshold or more below an earlier tread beside it, which has no rail there (the end cell is part of the route). */
	private static boolean endTouchesOk(int cur0, int cell, int ground, int[] mark, int[] markState, int stamp, int stride, int curN, int oy, int nx, boolean[] isSrc, int nz) {
		int cx = cell % nx, cz = cell / nx;
		for (int k = 0; k < 4; k++) {
			int ax = cx + DX4[k], az = cz + DZ4[k];
			if (ax < 0 || az < 0 || ax >= nx || az >= nz) continue;
			int rc = az * nx + ax;
			if (isSrc[rc] && rc != cur0 && oy - ground >= RAIL_DROP_LANDING) return false;
			if (mark[rc] != stamp || rc == cur0) continue;
			int s = markState[rc];
			int d = (s % stride) / curN, cur = s % curN;
			if (oy - d - ground >= (cur == 0 || d == 0 ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT)) return false;
		}
		return true;
	}

	/** Two adjacent route cells (a tread at level {@code lm} with rail threshold {@code thrM} whose first free level is {@code topM}, and a tread {@code ln}): within a block of level, and neither stands a threshold or more above the other's top. */
	private static boolean edgeOk(int lm, int thrM, int topM, int ln, int thrN, int topN) {
		return Math.abs(lm - ln) <= 1 && lm - topN < thrM && ln - topM < thrN;
	}

	private static final int[] DX4 = {1, -1, 0, 0}, DZ4 = {0, 0, 1, -1};

	/** Writes the route ending at state {@code st} (and, for a ground end, the extra last cell {cell parent, depth}) into the per-cell arrays. */
	private static void chain(int st, int[] from, int stride, int curN, int[] parent, int[] depth, int endCell, int[] last) {
		if (last != null) { parent[endCell] = last[0]; depth[endCell] = last[1]; }
		for (int s = st; s >= 0; s = from[s]) {
			int c = s / stride, d = (s % stride) / curN;
			parent[c] = from[s] < 0 ? -1 : from[s] / stride;
			depth[c] = d;
		}
	}

	/** The door rise from which a landing across the front is tried first: shops 2, houses and workshops 4 (DS-45: a landing at the door above a plain flight of 3), other types never. */
	static int stoopRise(BuildingIr shell, TypeLimits lim) {
		if (lim.stoop()) return 2;
		String type = shell.spec().type();
		return type.equals("house") || type.equals("workshop") ? 4 : Integer.MAX_VALUE;
	}

	/** Least drop (blocks, tread level minus the ground beside it) that gets a rail: along a flight, and along a landing. */
	static final int RAIL_DROP_FLIGHT = 2, RAIL_DROP_LANDING = 1;

	/**
	 * Railings (6.9): for each free cell beside a step or landing where the ground is far enough below the tread, a kerb of
	 * foundation up to the tread's walking surface (tread y + 1) with a {@link Role#RAIL} block on it, so the rail stands one block above the surface it guards (fence collision 1.5: a player, who steps 0.6, cannot get onto it). Rails
	 * go only on cells that are not part of any flight, landing or door approach ({@code blocked}), are inside the lot and are
	 * free of the building, so they never cross the line of travel; a walker could only step sideways onto ground at most one
	 * block below the tread, and no rail stands there. Where several treads touch a cell the highest decides.
	 */
	private static void rails(List<int[]> treads, Lot lot, Placement p, int x0, int x1, int z0, int z1, int nx, int[] ground, boolean[] blocked, boolean[] doorFront, List<Opening> extDoors, VoxelGrid grid, int base, List<Box> site) {
		int[] level = new int[blocked.length];
		java.util.Arrays.fill(level, Integer.MIN_VALUE);
		for (int[] t : treads) {
			for (Facing f : Facing.values()) {
				int x = t[0] + f.dx, z = t[1] + f.dz;
				if (x < x0 || x > x1 || z < z0 || z > z1) continue;
				int i = (z - z0) * nx + (x - x0);
				if (blocked[i] || !lot.containsColumn(p.worldX(x, z), p.worldZ(x, z)) || t[2] - ground[i] < t[3]) continue;
				level[i] = Math.max(level[i], t[2]);
			}
		}
		// corner posts: a free cell diagonal to a tread with a rail on both its sides along x and z would leave a gap between two
		// rails that meet diagonally (wider than a player), so it gets a post too
		int[] corner = new int[blocked.length];
		java.util.Arrays.fill(corner, Integer.MIN_VALUE);
		for (int i = 0; i < level.length; i++) {
			if (level[i] != Integer.MIN_VALUE || blocked[i] || doorFront[i]) continue;
			int cx = x0 + i % nx, cz = z0 + i / nx;
			if (!lot.containsColumn(p.worldX(cx, cz), p.worldZ(cx, cz))) continue;
			int lx = Math.max(railLevel(level, x0, x1, z0, z1, nx, cx - 1, cz), railLevel(level, x0, x1, z0, z1, nx, cx + 1, cz));
			int lz = Math.max(railLevel(level, x0, x1, z0, z1, nx, cx, cz - 1), railLevel(level, x0, x1, z0, z1, nx, cx, cz + 1));
			if (lx != Integer.MIN_VALUE && lz != Integer.MIN_VALUE) corner[i] = Math.max(lx, lz);
		}
		for (int i = 0; i < level.length; i++) if (corner[i] != Integer.MIN_VALUE) level[i] = corner[i];
		// a rail's top must be 1.5 above every tread within one cell of it (a player steps 0.6 and may cut a corner), not only the
		// tread it guards: beside a flight the rail takes the level of the highest tread around it
		int[] raised = level.clone();
		for (int[] t : treads)
			for (int dz = -1; dz <= 1; dz++)
				for (int dx = -1; dx <= 1; dx++) {
					int x = t[0] + dx, z = t[1] + dz;
					if (x < x0 || x > x1 || z < z0 || z > z1) continue;
					int i = (z - z0) * nx + (x - x0);
					if (level[i] != Integer.MIN_VALUE) raised[i] = Math.max(raised[i], t[2]);
				}
		level = raised;
		for (int i = 0; i < level.length; i++) {
			if (level[i] == Integer.MIN_VALUE) continue;
			int cx = x0 + i % nx, cz = z0 + i / nx, h = level[i], g = ground[i];
			boolean free = true;
			for (int y = g - base; y <= h + 1 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
			if (!free) continue;
			for (Opening o : extDoors) { // a door at the rail's height is not walled in
				Facing f = outward(o);
				Box3 ob = o.box();
				if (h + 1 - base >= ob.y() && h + 1 - base < ob.maxY() && cx - f.dx >= ob.x() && cx - f.dx < ob.maxX() && cz - f.dz >= ob.z() && cz - f.dz < ob.maxZ()) free = false;
			}
			if (!free) continue;
			site.add(new Box(Box3.of(cx, g - base, cz, 1, h + 1 - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
			site.add(new Box(Box3.of(cx, h + 1 - base, cz, 1, 1, 1), Role.RAIL, Layer.FOUNDATION, Box.NO_STOREY));
		}
	}

	private static int railLevel(int[] level, int x0, int x1, int z0, int z1, int nx, int x, int z) {
		return x < x0 || x > x1 || z < z0 || z > z1 ? Integer.MIN_VALUE : level[(z - z0) * nx + (x - x0)];
	}


	/** Whether these cells form one 4-connected piece that contains {@code start}. */
	private static boolean connected(int[] cells, int start, int nx) {
		java.util.Set<Integer> left = new java.util.TreeSet<>();
		for (int c : cells) left.add(c);
		if (!left.remove(start)) return false;
		java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty()) {
			int c = q.poll();
			for (int n : new int[] {c - 1, c + 1, c - nx, c + nx}) if (left.remove(n)) q.add(n);
		}
		return left.isEmpty();
	}

	/** Most cells a stoop is trimmed at one end to make room for the flight. */
	private static final int STOOP_MAX_TRIM = 8;
	/** True if a flight or landing tread has an open edge inside the building's cell range's outside, at least a flight rail threshold above the ground beside it, with no rail and no step on it (the test {@code RailingTest.unguardedEdges}, in the core for the STAIRJOG setback). */
	static boolean unguarded(PlacedBuilding b, TerrainProbe raw) {
		java.util.Set<Long> columns = new java.util.TreeSet<>(), railCols = new java.util.TreeSet<>();
		for (Box x : b.siteBoxes()) {
			long k = ((long) x.box().x() << 32) ^ (x.box().z() & 0xffffffffL);
			if (x.role() == Role.RAIL) railCols.add(k);
			else if (x.role() == Role.STAIR) columns.add(k);
		}
		Box3 bd = b.shell().bounds();
		TerrainProbe eff = dev.fractalbuildings.core.terrain.SiteGround.probe(b, raw);
		java.util.Set<Long> doorFront = new java.util.TreeSet<>();
		for (Opening o : b.shell().openings())
			if (o.kind() == OpeningKind.DOOR) {
				Facing out = outward(o);
				for (int x = o.box().x(); x < o.box().maxX(); x++) for (int z = o.box().z(); z < o.box().maxZ(); z++) doorFront.add(((long) (x + out.dx) << 32) ^ ((z + out.dz) & 0xffffffffL));
			}
		for (Box t : b.siteBoxes()) {
			if (t.role() != Role.STAIR) continue;
			for (Facing f : Facing.values()) {
				int cx = t.box().x() + f.dx, cz = t.box().z() + f.dz;
				long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
				if (cx >= 0 && cz >= 0 && cx < bd.maxX() && cz < bd.maxZ() || doorFront.contains(k) || columns.contains(k)) continue;
				if (b.oy() + t.box().y() - eff.floorY(b.placement().worldX(cx, cz), b.placement().worldZ(cx, cz)) < RAIL_DROP_FLIGHT) continue;
				if (!railCols.contains(k)) return true;
			}
		}
		return false;
	}


	/**
	 * Candidate landings (stoops) in front of a door that sits above the ground: the door's row and, when the
	 * lot leaves two or more rows outward, the next row too (the last row in the lot is left for the flight),
	 * out to the lot's edges, minus {@code k} cells at one end so that the flight has room along the facade.
	 * Smallest {@code k} first (the widest landing), both ends. Cells must be building-free, in the lot and
	 * more than a step above the terrain; every candidate contains the door's outside cell.
	 */
	private static List<int[]> stoopCandidates(Facing out, int sx, int sz, int x0, int x1, int z0, int z1, int nx, int[] ground, int oy, int base, VoxelGrid grid, boolean[][] fp) {
		Facing lat = out.rotate(1);
		int rows = 1;
		for (int j = 1; ; j++) {
			int x = sx + j * out.dx, z = sz + j * out.dz;
			if (x < x0 || x > x1 || z < z0 || z > z1) break;
			rows = j; // rows 0..j-1 may be landing; row j is the last in the lot
		}
		rows = Math.min(rows, 2);
		int[][] ext = new int[rows][2]; // per row: cells reachable towards +lat and -lat from the door's column, -1 if the row has none there
		for (int j = 0; j < rows; j++) {
			for (int side = 0; side < 2; side++) {
				int sgn = side == 0 ? 1 : -1, e = -1;
				for (int i = 0; ; i++) {
					int x = sx + j * out.dx + sgn * i * lat.dx, z = sz + j * out.dz + sgn * i * lat.dz;
					if (!deckOk(x, z, x0, x1, z0, z1, nx, ground, oy, base, grid, fp)) break;
					e = i;
				}
				ext[j][side] = e;
			}
		}
		List<int[]> cand = new ArrayList<>();
		if (ext[0][0] < 0) return cand;
		for (int k = 0; k <= STOOP_MAX_TRIM; k++) {
			for (int side = 0; side < 2; side++) { // the end the flight is at
				boolean ok = true;
				List<Integer> cells = new ArrayList<>();
				for (int j = 0; j < rows && ok; j++) {
					int hi = ext[j][0], lo = -ext[j][1];
					if (ext[j][0] < 0 && ext[j][1] < 0) continue;
					if (ext[j][1] < 0) lo = 0;
					if (ext[j][0] < 0) hi = 0;
					if (side == 0) hi -= k; else lo += k;
					if (j == 0 && (lo > 0 || hi < 0)) ok = false;
					for (int i = lo; i <= hi && ok; i++) {
						int x = sx + j * out.dx + i * lat.dx, z = sz + j * out.dz + i * lat.dz;
						cells.add((z - z0) * nx + (x - x0));
					}
				}
				if (ok) cand.add(cells.stream().mapToInt(Integer::intValue).toArray());
			}
		}
		return cand;
	}

	private static boolean deckOk(int x, int z, int x0, int x1, int z0, int z1, int nx, int[] ground, int oy, int base, VoxelGrid grid, boolean[][] fp) {
		if (x < x0 || x > x1 || z < z0 || z > z1) return false;
		if (x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z]) return false;
		if (ground[(z - z0) * nx + (x - x0)] >= oy) return false;
		for (int y = oy - base; y <= oy + 3 - base; y++) if (grid.get(x, y, z) != Role.AIR) return false;
		return true;
	}

	/** Local columns [x][z] under a volume that starts at y 0. */
	public static boolean[][] footprint(BuildingIr shell) { return dev.fractalbuildings.core.terrain.SiteGround.footprint(shell); }
}
