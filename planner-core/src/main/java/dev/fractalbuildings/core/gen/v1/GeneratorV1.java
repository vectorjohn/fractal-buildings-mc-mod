package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.facade.ExtraDoors;
import dev.fractalbuildings.core.gen.Generator;
import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.SiteGround;
import java.util.ArrayList;
import java.util.List;

/** Generator v1 (Phase 4): SpecPolicyV1, then ShellGeneratorV1 (through a {@link ShellSource}), then InstancePassV1. */
public final class GeneratorV1 implements Generator {
	/**
	 * Part of every cache key. v0 is 1; v1 starts at 2. During Phase 4 it stays 2 while the passes
	 * change (no IR is cached outside a process yet; the golden digests catch unintended changes);
	 * from Phase 6 on it is bumped with every intended output change (plan "Golden digests").
	 */
	public static final int GEN_VERSION = 2;

	private final ShellSource shells;

	/** True when shells come straight from {@link ShellGeneratorV1}: only then does the door retry ({@link #withDoors}) regenerate them under an {@link ExtraDoors#SURVEY}, which a cached source would ignore or, worse, keep for a later spec lookup (shells must depend on the spec alone, ADR 0003 D6). */
	private final boolean direct;

	public GeneratorV1() { this.shells = ShellGeneratorV1::generate; this.direct = true; }

	public GeneratorV1(ShellSource shells) { this.shells = shells; this.direct = false; }

	@Override
	public SceneDoc generate(GenRequest req) {
		Result r = search(req, false);
		if (!r.placed().guarded() && !NO_SETBACK.contains(req.type())) { // no rail inside the lot with every door: a secondary door (DS-35) gives way to the rails (DS-8)
			Result bare = search(req, true);
			if (bare.placed().guarded()) r = bare;
		}
		if (!NO_SETBACK.contains(req.type()) && InstancePassV1.hasSecondaryDoor(r.placed().building().shell()) && !(r.walks() != null ? r.walks() : dev.fractalbuildings.core.check.RoomWalk.check(r.placed().building(), dev.fractalbuildings.core.voxel.VoxelGrid.of(r.placed().building())).isEmpty())) { // a secondary door that leaves a furnished room without a clear lane (DS-48) gives way
			Result bareResult = search(req, true);
			if (dev.fractalbuildings.core.check.RoomWalk.check(bareResult.placed().building(), dev.fractalbuildings.core.voxel.VoxelGrid.of(bareResult.placed().building())).isEmpty()) r = bareResult;
		}
		PlacedBuilding b = r.placed().building();
		var lot = req.lot();
		BuildingSpec spec = r.spec();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GEN_VERSION, req.worldSeed(), terrain, List.of(spec.lead() > 0 ? lot.withAir(spec.lead() - r.lim().front(), req.brief().frontY()) : lot), List.of(b), List.of());
	}

	private record Result(InstancePassV1.Placed placed, BuildingSpec spec, TypeLimits lim, Boolean walks) {}

	/** The placement, setting the building back until every flight is railed (DS-8); {@code bare}: with every secondary door walled up. */
	private Result search(GenRequest req, boolean bare) {
		TypeLimits lim = Types.get(req.type()).limits();
		BuildingSpec spec = SpecPolicyV1.spec(req, lim);
		Doors d = withDoors(spec, req, lim, bare);
		InstancePassV1.Placed placed = d.placed();
		Boolean walks = d.walks();
		if (!placed.guarded() && !bare && !NO_SETBACK.contains(req.type())) { // a secondary door (DS-35) whose flight cannot be railed gives way before the building is set back
			InstancePassV1.Placed without = place(shells.shell(spec), req, lim, true);
			if (without.guarded()) { placed = without; walks = null; }
		}
		// DS-8: a flight or landing that needs a rail on the lot line cannot have one (a rail stays in the lot), so set the building back
		// one cell from the front first, then from every side, and keep the first placement whose flights are all railed
		if (!placed.guarded() && !NO_SETBACK.contains(req.type())) {
			for (int step = 0; step < SETBACKS.length && !placed.guarded(); step++) {
				int f = SETBACKS[step][0], s = SETBACKS[step][1];
				TypeLimits back = new TypeLimits(lim.minWidth(), lim.minDepth(), lim.minStoreys(), lim.maxStoreys(), lim.side() + s, lim.front() + f, lim.back() + s, lim.allStreets(), lim.testLots(), lim.propertySeeds(), lim.maxGrade(), lim.stoop());
				try {
					BuildingSpec s2 = SpecPolicyV1.spec(req, back);
					Doors d2 = withDoors(s2, req, back, bare);
					InstancePassV1.Placed p2 = d2.placed();
					if (p2.guarded()) { placed = p2; spec = s2; lim = back; walks = d2.walks(); }
				} catch (IllegalArgumentException e) { /* no room for a setback: keep the placement as it was */ }
			}
		}
		// STAIRJOG (DS-45 run and turn sizes): a flight that still jogs (two turns one riser apart) after the straight-run search found no straight way down: set the
		// building back further, one row more each time, and keep the first placement that is railed and has no jog
		if (!SiteLevel.LEGACY.get() && placed.guarded() && jogs(placed.building()) && !NO_SETBACK.contains(req.type())) {
			for (int step = 0; step < JOG_SETBACKS.length; step++) {
				int f = JOG_SETBACKS[step][0], s = JOG_SETBACKS[step][1];
				TypeLimits back = new TypeLimits(lim.minWidth(), lim.minDepth(), lim.minStoreys(), lim.maxStoreys(), lim.side() + s, lim.front() + f, lim.back() + s, lim.allStreets(), lim.testLots(), lim.propertySeeds(), lim.maxGrade(), lim.stoop());
				try {
					BuildingSpec s2 = SpecPolicyV1.spec(req, back);
					Doors d2 = withDoors(s2, req, back, bare);
					InstancePassV1.Placed p2 = d2.placed();
					if (p2.guarded() && !jogs(p2.building()) && !InstancePassV1.unguarded(p2.building(), req.terrain()) && sound(p2.building(), s2, back, req)) { placed = p2; spec = s2; lim = back; walks = d2.walks(); break; }
				} catch (IllegalArgumentException e) { /* no room for this setback */ }
			}
		}
		return new Result(placed, spec, lim, walks);
	}

	/** Shell generations a lost secondary door may cost before it is walled up for good. */
	private static final int DOOR_TRIES = 8;

	/**
	 * The placement of this spec. When the ground walls up a secondary door (DS-35), or its flight cannot be railed inside the lot (DS-8), the
	 * shell is generated again with a {@link ExtraDoors.Survey} of the first placement: every non-front face is tried, each cell scored by the
	 * flight it needs (a level cell first), the cells that failed banned, until every door stands or no cell is left (John, REVIEW 2026-10-28:
	 * second doors on sloping lots). The placement builds the flight with the DS-45 runs and landings, and walls a door up only when none fits.
	 */
	/** A placement and, when the door retry already walked it, whether every furnished room keeps a clear lane (null: not walked). */
	private record Doors(InstancePassV1.Placed placed, Boolean walks) {}

	private Doors withDoors(BuildingSpec spec, GenRequest req, TypeLimits lim, boolean bare) {
		BuildingIr shell = shells.shell(spec);
		InstancePassV1.Placed placed = place(shell, req, lim, bare);
		if (bare || !direct || NO_SETBACK.contains(req.type()) || InstancePassV1.secondaryDoors(shell).isEmpty()) return new Doors(placed, null);
		Judge judge = new Judge(() -> place(shell, req, lim, true));
		List<Opening> lost = judge.lost(placed);
		if (lost.isEmpty()) return new Doors(placed, judge.known(placed));
		int want = InstancePassV1.secondaryDoors(shell).size();
		java.util.Set<Long> banned = new java.util.TreeSet<>();
		for (Opening o : lost) ban(banned, o);
		InstancePassV1.Placed best = placed;
		int bestDoors = judge.standing(placed);
		try {
			for (int phase = 0; phase < 3 && bestDoors < want; phase++) { // the raw ground with a railed straight flight first, then any flight, then the ground as the cut left it (an areaway along the wall)
				ExtraDoors.Survey ground = survey(judge.without().building(), req, phase == 2, phase == 0);
				for (int tries = 0; tries < DOOR_TRIES; tries++) {
					ExtraDoors.SURVEY.set((side, x, z) -> banned.contains(key(x, z)) ? Integer.MIN_VALUE : ground.score(side, x, z));
					InstancePassV1.Placed again = place(ShellGeneratorV1.generate(spec), req, lim, false);
					int doors = judge.standing(again);
					if (doors > bestDoors) { best = again; bestDoors = doors; }
					if (bestDoors >= want) break;
					int before = banned.size();
					for (Opening o : judge.lost(again)) ban(banned, o);
					if (banned.size() == before) break; // no cell left that the survey allows
				}
			}
		} finally { ExtraDoors.SURVEY.remove(); }
		return new Doors(best, judge.known(best));
	}

	/**
	 * Whether a placement's secondary doors stand: not walled up by the placement, their flights railed inside the lot (DS-8) and every furnished room
	 * keeping a clear lane (DS-48), each judged only where the building without its secondary doors keeps it (else the door is not what breaks it).
	 */
	private static final class Judge {
		private final java.util.function.Supplier<InstancePassV1.Placed> withoutDoors;
		private InstancePassV1.Placed without;
		private Boolean bareWalks;

		Judge(java.util.function.Supplier<InstancePassV1.Placed> withoutDoors) { this.withoutDoors = withoutDoors; }

		InstancePassV1.Placed without() { return without != null ? without : (without = withoutDoors.get()); }

		private final List<Object[]> walked = new ArrayList<>(); // {placement, its RoomWalk verdict}: generate() reuses the verdict instead of walking the building again

		private boolean walks(InstancePassV1.Placed p) {
			Boolean k = known(p);
			if (k != null) return k;
			boolean w = dev.fractalbuildings.core.check.RoomWalk.check(p.building(), dev.fractalbuildings.core.voxel.VoxelGrid.of(p.building())).isEmpty();
			walked.add(new Object[] {p, w});
			return w;
		}

		/** The lane verdict (DS-48) already computed for this very placement, or null. */
		Boolean known(InstancePassV1.Placed p) {
			for (Object[] e : walked) if (e[0] == p) return (Boolean) e[1];
			return null;
		}

		/** True if the secondary doors that stand break the rails or a lane. */
		private boolean broken(InstancePassV1.Placed p) {
			if (InstancePassV1.secondaryDoors(p.building().shell()).isEmpty()) return false;
			if (!p.guarded() && without().guarded()) return true;
			if (walks(p)) return false;
			if (bareWalks == null) bareWalks = walks(without());
			return bareWalks;
		}

		/** The doors a retry must move: the walled-up ones, or all that stand when they break the rails or a lane. */
		List<Opening> lost(InstancePassV1.Placed p) {
			List<Opening> out = new ArrayList<>(p.dropped());
			if (broken(p)) out.addAll(InstancePassV1.secondaryDoors(p.building().shell()));
			return out;
		}

		/** Secondary doors that stand, -1 when they break the rails or a lane. */
		int standing(InstancePassV1.Placed p) { return broken(p) ? -1 : InstancePassV1.secondaryDoors(p.building().shell()).size(); }
	}

	private static void ban(java.util.Set<Long> banned, Opening o) {
		int[] c = InstancePassV1.outsideCell(o);
		banned.add(key(c[0], c[1]));
	}

	private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

	/**
	 * The ground round the building placed without its secondary doors as a door survey (its cut is the one a building keeps when every door fails, so the
	 * survey and {@code entrance-count} judge the same ground). A cell whose plain railed flight of at most 3 risers reaches the ground straight out
	 * ({@link dev.fractalbuildings.core.check.EntranceCount#plainFlight}, the test {@code entrance-count} exempts by) scores by its risers, fewest first;
	 * unless {@code railedOnly}, any other cell in the lot no more than a block above the floor scores below them (the flight search may still turn
	 * along the facade or take landings, DS-45). A cell whose ground past the lot is more than 2 above the sill is a bank (rise below -2).
	 */
	static ExtraDoors.Survey survey(PlacedBuilding b, GenRequest req, boolean cut, boolean railedOnly) {
		int floor = b.oy() + InstancePassV1.entranceStorey(b.shell()).y();
		TerrainProbe site = SiteGround.probe(b, req.terrain()), eff = cut ? site : req.terrain();
		Lot lot = req.lot();
		Placement p = b.placement();
		boolean[][] fp = SiteGround.footprint(b.shell());
		java.util.function.BiPredicate<Integer, Integer> free = (x, z) -> lot.containsColumn(p.worldX(x, z), p.worldZ(x, z)) && !(x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z]);
		return (side, x, z) -> {
			if (!lot.containsColumn(p.worldX(x, z), p.worldZ(x, z))) return Integer.MIN_VALUE;
			int g = eff.floorY(p.worldX(x, z), p.worldZ(x, z));
			if (g > floor + 1) return Integer.MIN_VALUE;
			int ox = x, oz = z; // the ground just past the lot straight out, as DoorRise reads a side door
			for (int k = 0; k < 64 && lot.containsColumn(p.worldX(ox, oz), p.worldZ(ox, oz)); k++) { ox += side.dx; oz += side.dz; }
			if (eff.floorY(p.worldX(ox, oz), p.worldZ(ox, oz)) > floor + 3) return Integer.MIN_VALUE;
			int risers = dev.fractalbuildings.core.check.EntranceCount.plainFlight((lx, lz) -> eff.floorY(p.worldX(lx, lz), p.worldZ(lx, lz)), free, floor + 1, x, z, side);
			if (railedOnly && risers >= 0) risers = Math.min(risers, dev.fractalbuildings.core.check.EntranceCount.plainFlight((lx, lz) -> site.floorY(p.worldX(lx, lz), p.worldZ(lx, lz)), free, floor + 1, x, z, side)); // and on the ground as the first cut left it (a cut makes the flight longer)
			if (risers >= 0) return -4 * risers;
			if (railedOnly) return Integer.MIN_VALUE;
			return -20 - 4 * Math.max(0, floor - g);
		};
	}

	private static InstancePassV1.Placed place(dev.fractalbuildings.core.ir.BuildingIr shell, GenRequest req, TypeLimits lim, boolean bare) {
		return bare ? InstancePassV1.placeWithoutSecondaryDoors(shell, req, lim) : InstancePassV1.placeChecked(shell, req, lim);
	}

	/** Extra margin tried in turn: {front, sides and back}. */
	private static final int[][] SETBACKS = {{1, 0}, {1, 1}, {2, 1}, {2, 2}};

	/** Further front setbacks (rows, sides) tried for a flight that jogs (STAIRJOG; a rise of 6 needs 7 or 8 rows). */
	private static final int[][] JOG_SETBACKS = {{3, 0}, {4, 1}, {6, 1}, {8, 2}};

	private static boolean jogs(PlacedBuilding b) { return dev.fractalbuildings.core.check.DoorChecks.flightShape(b).stream().anyMatch(v -> v.invariant().equals("flight-turns")); }

	/** The placement passes the building invariants as a scene of its own (a STAIRJOG setback is kept only if it does). */
	private static boolean sound(PlacedBuilding b, BuildingSpec spec, TypeLimits lim, GenRequest req) {
		var lot = req.lot();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return dev.fractalbuildings.core.check.Invariants.check(new SceneDoc(GEN_VERSION, req.worldSeed(), terrain, List.of(spec.lead() > 0 ? lot.withAir(spec.lead() - lim.front(), req.brief().frontY()) : lot), List.of(b), List.of()), req.terrain()).isEmpty();
	}

	/** Town-wall types stand on the ring's walk and keep their margins. */
	private static final java.util.Set<String> NO_SETBACK = java.util.Set.of("wall", "curtain", "bastion");
}
