package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DS-45 checks (SHOPLOW S2) on top of the {@link DoorRise} measure: the {@code door-rise} invariant (a shop or workshop door at most 2 above the street
 * in front of it, a house door at most 5) and the design warnings {@code door-rise} (beyond what the planner can meet) and {@code flight-landings}
 * (a straight run over {@link #MAX_RISERS} risers, DS-45 "Stair runs").
 *
 * <p>The invariant fails only <i>within the planner's contract</i>: shops and workshops on dry lots whose highest ground is at most
 * {@code Briefs.SHOP_FRONT_RISE} above the street, houses on dry lots at most {@code Briefs.HOUSE_FRONT_RISE} above it and with the ground in front
 * of the door at most {@code HOUSE_MAX_RISE - 1} above it. The dig-in cannot lower a door on steeper lots (the bank buildings of S8 take those): there
 * the same finding is a design warning.
 */
public final class DoorChecks {
	private DoorChecks() {}

	/** Most risers of a flight in a straight run without a level tread (DS-45 "Stair runs", every exterior flight of every lot). */
	public static final int MAX_RISERS = 3;
	/** Greatest rise of a shop door (and of a workshop's cart door). */
	public static final int SHOP_MAX_RISE = 2;
	/** Greatest rise of a house door by default. */
	public static final int HOUSE_MAX_RISE = 5;

	/** The measured entrance doors of a building of a scene: the street from the scene's road surfaces, the ground through the building's own site ({@link dev.fractalbuildings.core.terrain.SiteGround}). */
	public static List<DoorRise.Door> doors(SceneDoc scene, PlacedBuilding b, TerrainProbe raw) {
		Map<Long, Integer> surface = scene.genVersion() >= 2 && !scene.ground().isEmpty() ? StairGround.surfaceLevels(scene) : Map.of();
		return DoorRise.measure(scene, b, dev.fractalbuildings.core.terrain.SiteGround.probe(b, raw), surface);
	}

	/** Whether the building's entrance storey is its ground storey (a market hall on posts, or another stilted building, has its trade floor in the open ground). */
	public static boolean groundStorey(PlacedBuilding b) {
		if (b.shell().storeys().isEmpty()) return false;
		int lowest = Integer.MAX_VALUE;
		for (Room r : b.shell().rooms()) lowest = Math.min(lowest, r.storey());
		Storey s0 = b.shell().storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
		return s0.index() == 0;
	}

	/** How far the lot's highest ground may stand above the street for the rise limit to be possible (the planner's own limits, which the dig-in meets), or MAX_VALUE. */
	static int contract(String type) {
		return switch (type) {
			case "shop", "workshop" -> dev.fractalbuildings.core.settlement.Briefs.SHOP_FRONT_RISE;
			case "house" -> dev.fractalbuildings.core.settlement.Briefs.HOUSE_FRONT_RISE;
			default -> Integer.MAX_VALUE;
		};
	}

	/** The greatest rise a door of this type may have (DS-45), or MAX_VALUE for types the rule does not cover. */
	static int limit(String type) {
		return switch (type) {
			case "shop", "workshop" -> SHOP_MAX_RISE;
			case "house" -> HOUSE_MAX_RISE;
			default -> Integer.MAX_VALUE;
		};
	}

	/** The highest raw ground over the building's lots. */
	static int highest(SceneDoc scene, PlacedBuilding b, TerrainProbe raw) {
		int hi = Integer.MIN_VALUE;
		for (String id : b.lots())
			for (Lot l : scene.lots())
				if (l.id().equals(id))
					for (int z = l.z(); z < l.z() + l.d(); z++)
						for (int x = l.x(); x < l.x() + l.w(); x++) hi = Math.max(hi, raw.floorY(x, z));
		return hi;
	}

	/**
	 * Whether a column of the building's lots with water stands above {@code level}. The generator keeps the old slab when a site column above the dug slab is wet ({@code SiteLevel.wet}); the dug
	 * slab is never below the street, so a wet column above the street level is the narrowest test the scene alone can make for "the dig-in did not apply here".
	 */
	static boolean wetAbove(SceneDoc scene, PlacedBuilding b, TerrainProbe raw, int level) {
		for (String id : b.lots())
			for (Lot l : scene.lots())
				if (l.id().equals(id))
					for (int z = l.z(); z < l.z() + l.d(); z++)
						for (int x = l.x(); x < l.x() + l.w(); x++) if (raw.floorY(x, z) > level && raw.waterDepth(x, z) > 0) return true;
		return false;
	}

	private static Opening opening(PlacedBuilding b, String id) {
		for (Opening o : b.shell().openings()) if (o.id().equals(id)) return o;
		return null;
	}

	/**
	 * The front doors of a shop, workshop or house on its ground storey that stand above DS-45's limit (the rise is measured at the door's own street column, as DS-45 words it and S0 core's
	 * {@code DoorRise} reads it; the planner's limits use {@code frontY} at the middle of the lot front, so on a steep street a lot it accepted can land beyond its contract here: a warning): {door, whether the lot is within the planner's contract}. */
	static List<Object[]> tooHigh(SceneDoc scene, PlacedBuilding b, List<DoorRise.Door> doors, TerrainProbe raw) {
		List<Object[]> out = new ArrayList<>();
		String type = b.shell().spec().type();
		if (limit(type) == Integer.MAX_VALUE || !groundStorey(b)) return out;
		int hi = highest(scene, b, raw);
		for (DoorRise.Door d : doors) {
			if (!d.front() || !d.reached() || d.rise() <= limit(type)) continue;
			Opening o = opening(b, d.door());
			if (o == null) continue;
			int street = b.oy() + o.box().y() - d.rise();
			boolean in = hi - street <= contract(type) && !wetAbove(scene, b, raw, street);
			if (in && type.equals("house")) { // a house's door cannot stand lower than the ground right in front of it: that ground may be at most HOUSE_MAX_RISE - 1 above the street
				Placement pl = b.placement();
				Facing outW = pl.toWorld(o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2));
				Box3 dw = pl.toWorld(o.box());
				int rawOut = raw.floorY(dw.x() + outW.dx, dw.z() + outW.dz);
				in = rawOut - street <= HOUSE_MAX_RISE - 1;
			}
			out.add(new Object[] {d, in, o});
		}
		return out;
	}

	/** The cell of a door (its first block, in the world) for a violation. */
	static Box3 cell(PlacedBuilding b, Opening o) { return b.placement().toWorld(Box3.of(o.box().x(), o.box().y(), o.box().z(), 1, 1, 1)); }

	/**
	 * The design warnings of a scene: {@code door-rise} for a door over the limit on a lot beyond the planner's contract and {@code flight-landings} for every
	 * flight with a straight run over {@link #MAX_RISERS} (where no route with landings fitted the lot). Listed by {@code check} and {@code city-check}, never failing.
	 */
	public static List<Violation> warnings(SceneDoc scene, TerrainProbe raw) {
		List<Violation> out = new ArrayList<>();
		if (scene.genVersion() < 2) return out;
		Map<Long, Integer> surface = !scene.ground().isEmpty() ? StairGround.surfaceLevels(scene) : Map.of();
		for (PlacedBuilding b : scene.buildings()) {
			List<DoorRise.Door> doors = DoorRise.measure(scene, b, dev.fractalbuildings.core.terrain.SiteGround.probe(b, raw), surface);
			for (Object[] t : tooHigh(scene, b, doors, raw)) {
				if ((Boolean) t[1]) continue; // within the contract: the invariant reports it
				DoorRise.Door d = (DoorRise.Door) t[0];
				out.add(new Violation("door-rise", b.id(), d.door() + ": rise " + d.rise() + " from the street on a lot beyond the planner's limit (a " + b.shell().spec().type() + " door sits at most " + limit(b.shell().spec().type()) + " above it)", List.of(cell(b, (Opening) t[2]))));
			}
			for (DoorRise.Door d : doors) { // the architect's SHOPLOW S2 finding: a house 3 or more above the street with no built way (the hillside's own ledges) is counted
				if (b.shell().spec().type().equals("house") && d.front() && d.reached() && d.rise() >= 3 && d.run() <= 1 && !d.landing()) {
					Opening o = opening(b, d.door());
					if (o != null) out.add(new Violation("door-rise", b.id(), d.door() + ": rise " + d.rise() + " with no built approach (the raw hillside up to the door)", List.of(cell(b, o))));
				}
				if (!d.reached() || d.run() <= MAX_RISERS) continue;
				Opening o = opening(b, d.door());
				if (o != null) out.add(new Violation("flight-landings", b.id(), d.door() + ": a straight run of " + d.run() + " risers (" + b.shell().spec().type() + ")", List.of(cell(b, o))));
			}
		}
		return out;
	}
}
