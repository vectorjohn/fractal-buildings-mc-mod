package dev.fractalbuildings.core.gen.v1.types.wall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Faces;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.RoofStyle;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * A curtain-wall segment of a town ring (D2): a plain wall between two towers, no gate. One gallery {@link WalkOps#BAND}
 * thick (3 inside) over the lot's whole width, a storey high ({@code GROUND}), with a guard door on the town side. Its flat
 * roof is the wall walk, with a crenellated parapet on both edges that stops at the lot ends so the walk runs on into the
 * next lot (a gatehouse or corner tower reaches it by a doorway on its first upper storey, see {@link WalkOps}).
 *
 * <p>The envelope fills the lot's width; the lot's front (outside) and back (town) rows are margins for the guard door's steps.
 */
public final class CurtainType implements BuildingType {
	public static final String ID = "curtain";

	private static final TypeLimits LIMITS = new TypeLimits(12, 5, 1, 1, 0, 1, 1, false,
			List.of(new int[] {24, 7}, new int[] {32, 9}, new int[] {48, 11}, new int[] {64, 11}), 120, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		StoreyGrid g = StoreyGrid.of(1, WallType.GROUND, c.style().massing().typical());
		Mass m = new Mass("curtain0", "curtain", 0, WalkOps.bandZ(sp.depth()), sp.width(), WalkOps.BAND, 0, 1, null, true);
		Massing out = new Massing(g, List.of(m), List.of());
		if (MassingCheck.check(out, sp.width(), sp.depth(), sp.maxHeight()).isEmpty()) return out;
		// last resort (tiny height cap): the style's own ground storey height
		StoreyGrid low = StoreyGrid.of(1, c.style().massing().ground(), c.style().massing().typical());
		return new Massing(low, List.of(m), List.of());
	}

	@Override
	public java.util.List<dev.fractalbuildings.core.ir.Box> walkJoins(dev.fractalbuildings.core.ir.BuildingIr shell, dev.fractalbuildings.core.gen.Brief.Walk walk) { return WalkOps.joins(shell, walk); }

	@Override
	public Program roofs() {
		return c -> {
			RoofStyle s = c.style().roof();
			dev.fractalbuildings.core.gen.v1.roof.RoofPass.run(c, new RoofStyle(s.forms(), s.rise(), s.run(), 0, 2, "wall", 1, 0));
			WallType.battlements(c);
			WalkOps.openEnds(c, c.massing().main());
		};
	}

	/** The guard door is on the town (south) side. */
	@Override
	public Program facade() {
		return c -> {
			FacadePass.run(c);
			FacadePlan p = c.facadePlan();
			Massing m = c.massing();
			Mass cur = m.main();
			List<Face> fs = Faces.of(m, cur.id(), 0, Facing.SOUTH);
			if (fs.size() != 1) throw new IllegalStateException("curtain south face: " + fs.size() + " runs");
			Face f = fs.get(0);
			Draws r = c.draws("curtain/door");
			int pos = f.a() + 10 + r.below(Math.max(1, f.length() - 20)); // clear of the ends, where a walk descending to a tower doorway is cut through the gallery
			FacadePlan.Entrance town = new FacadePlan.Entrance(f, pos);
			List<FacadePlan.Layout> layouts = new ArrayList<>();
			for (FacadePlan.Layout l : p.layouts()) {
				if (l.face().equals(f)) {
					List<String> kinds = new ArrayList<>(l.kinds());
					for (int b = 0; b < l.bays().length; b++) if (pos >= l.bayStart(b) && pos < l.bayStart(b) + l.bays()[b]) kinds.set(b, FacadePlan.DOOR);
					l = l.withKinds(kinds);
				}
				layouts.add(l);
			}
			c.setFacadePlan(new FacadePlan(layouts, town, List.of()));
		};
	}

	@Override
	public Program openings() { return c -> OpeningPass.run(c, e -> OpeningPass.door(c, e), true); }

	@Override
	public RoomProgram rooms() { return ROOMS; }

	/** A bare guard gallery: lit, with a chest now and then. */
	private static final RoomProgram ROOMS = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) {
			return switch (role) {
				case HALL -> "gallery";
				default -> RoomProgram.DEFAULT.use(role, area, d);
			};
		}

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			return use.equals("gallery") ? List.of("light", "chest") : RoomProgram.DEFAULT.hooks(use, area, d);
		}
	};
}
