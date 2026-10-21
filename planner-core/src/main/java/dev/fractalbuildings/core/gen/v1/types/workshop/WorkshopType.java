package dev.fractalbuildings.core.gen.v1.types.workshop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.structure.Posts;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * A workshop (smithy, mill, workshop yard): a tall (5 to 6 high) open work hall at the front with a
 * 2-wide cart door in the middle of its front, a living wing (one or two storeys) behind or beside it
 * and sometimes a low lean-to. The hall is one room holding the workstations and a forge ({@code
 * hearth}); the wing's rooms are the default program. The unbuilt part of the lot is the yard.
 */
public final class WorkshopType implements BuildingType {
	public static final String ID = "workshop";

	private static final TypeLimits LIMITS = new TypeLimits(8, 6, 1, 2, 1, 2, 1, false,
			List.of(new int[] {12, 12}, new int[] {16, 14}, new int[] {20, 16}, new int[] {24, 20}), 400, 1);

	private static final RoomProgram ROOMS = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) { return role == Role.HALL ? "workshop" : RoomProgram.DEFAULT.use(role, area, d); }

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			if (!use.equals("workshop")) return RoomProgram.DEFAULT.hooks(use, area, d);
			List<String> h = new ArrayList<>(List.of("workstation", "workstation", "hearth", "chest"));
			if (area >= 70) h.add(2, "workstation");
			if (area >= 110) h.add("workstation");
			h.add("light");
			return h;
		}
	};

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) { return WorkshopMassing.build(c); }

	@Override
	public RoomProgram rooms() { return ROOMS; }

	@Override
	public Program facade() { return WorkshopType::facade; }

	/** The default layouts, with the hall's front rebuilt as [window bays] [4 or 5 wide door bay] [window bays] around a 2-wide door. */
	static void facade(BuildContext c) {
		FacadeStyle st = c.style().facade();
		Massing m = c.massing();
		List<FacadePlan.Layout> layouts = FacadePass.layout(c);
		FacadePlan.Entrance door = null;
		for (int i = 0; i < layouts.size(); i++) {
			FacadePlan.Layout l = layouts.get(i);
			Face f = l.face();
			if (!f.mass().equals(m.main().id()) || f.storey() != 0 || f.side() != Facing.NORTH) continue;
			int len = f.length() - 2;
			int dw = (len - 4) % 2 == 0 ? 4 : 5;
			int left = (len - dw) / 2, right = len - dw - left;
			List<Integer> bays = new ArrayList<>();
			List<String> kinds = new ArrayList<>();
			side(bays, kinds, left, st);
			int doorBay = bays.size();
			bays.add(dw);
			kinds.add(FacadePlan.DOOR);
			side(bays, kinds, right, st);
			int[] b = bays.stream().mapToInt(Integer::intValue).toArray();
			FacadePlan.Layout nl = new FacadePlan.Layout(f, l.start(), b, kinds, l.win());
			layouts.set(i, nl);
			door = new FacadePlan.Entrance(f, nl.bayStart(doorBay) + (dw - 2) / 2, 2);
			break;
		}
		if (door == null) throw new IllegalStateException("no front face for the cart door");
		List<FacadePlan.Entrance> others = new ArrayList<>();
		for (int e = 1; e < m.entrances().size(); e++) others.add(FacadePass.entrance(c, m, layouts, m.entrances().get(e), e));
		FacadePlan plan = new FacadePlan(layouts, door, others);
		c.setFacadePlan(plan);
		FacadePass.decorate(c, plan);
		porch(c, door);
	}

	/**
	 * The covered work yard: when the hall stands back from the front of the envelope (its {@code z} is the porch
	 * depth, 2 or 3), a lean-to canopy along the hall's front wall on a row of posts at the front edge (corner to
	 * corner, none in front of the cart door). The canopy is one ROOF block thick, rising towards the wall under the
	 * eaves as far as the hall is high; it touches the wall and the posts, so nothing floats, and it is not a
	 * volume, so no room lies under a roof that is open at the sides.
	 */
	private static void porch(BuildContext c, FacadePlan.Entrance door) {
		Mass hall = c.massing().main();
		int a = hall.z();
		if (a < 2) return;
		int hallH = c.massing().grid().y(1), low = Math.min(4, hallH - 1);
		int[] xs = Posts.spaced(hall.x(), hall.maxX() - 1, 4, door.pos() - 1, door.pos() + door.width());
		for (int i = 0; i < xs.length; i++) Posts.column(c, "post" + i, xs[i], 0, low);
		for (int z = 0; z < a; z++) {
			int y = Math.max(low, hallH - 1 - (a - 1 - z));
			c.box(Role.ROOF, Layer.ROOF, Box.NO_STOREY, hall.x(), y, z, hall.sx(), 1, 1);
		}
	}

	private static void side(List<Integer> bays, List<String> kinds, int len, FacadeStyle st) {
		if (len <= 0) return;
		int[] b = FacadePass.bays(len, st.bayMin(), st.bayMax());
		if (b == null) b = new int[] {len};
		for (int x : b) {
			bays.add(x);
			kinds.add(FacadePlan.WINDOW);
		}
	}
}
