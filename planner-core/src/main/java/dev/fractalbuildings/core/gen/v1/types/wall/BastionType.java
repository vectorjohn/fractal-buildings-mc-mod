package dev.fractalbuildings.core.gen.v1.types.wall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/**
 * A corner tower of a town ring (D2): a square tower filling its lot, 3 or 4 storeys with a stair core, a flat roof with a
 * crenellated parapet. The ring leaves a corner lot on its east (local +x) side and its back (local +z) side, clockwise
 * from the lot's front, so the two walks of the neighbouring lots meet the tower there: it has a doorway on its first upper
 * storey in each of those walls ({@link WalkOps}), and the stair core stands on the far side from both. Its entrance is on the
 * front (the outside); the field side is also where a guard goes out.
 */
public final class BastionType implements BuildingType {
	public static final String ID = "bastion";

	/** The lot is the tower's square: 12 across, 1 row of margin for the entrance steps in front, none on the sides or the back. */
	/** Margin rows in front, for the entrance steps. */
	static final int FRONT = 2;

	private static final TypeLimits LIMITS = new TypeLimits(12, 10, 3, 4, 0, FRONT, 0, false,
			List.of(new int[] {12, 12}), 120, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		for (int n = sp.storeys(); n >= 3; n--) {
			Massing m = attempt(c, n);
			if (m != null) return m;
		}
		// last resort (tiny height cap): one plain storey, no stair, no walk door
		StoreyGrid g = StoreyGrid.of(1, c.style().massing().ground(), c.style().massing().typical());
		return new Massing(g, List.of(Mass.ground("tower0", "tower", 0, 0, sp.width(), sp.depth(), 1)), List.of());
	}

	private static Massing attempt(BuildContext c, int n) {
		BuildingSpec sp = c.spec();
		StoreyGrid g = StoreyGrid.of(n, WallType.GROUND, c.style().massing().typical());
		if (g.y(n) + 2 > sp.maxHeight()) return null;
		Mass t = Mass.ground("tower0", "tower", 0, 0, sp.width(), sp.depth(), n);
		// the stair against the west wall, away from the doorways in the east and back walls
		int len = Core.length(g, 0, n);
		Core k = new Core(1, Math.max(2, sp.depth() - 1 - len), 2, len, 0, n);
		Massing m = new Massing(g, List.of(t), List.of(k));
		return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
	}

	@Override
	public Program roofs() { return new WallType().roofs(); }

	@Override
	public Program openings() {
		return c -> {
			Massing m = c.massing();
			Mass t = m.main();
			OpeningPass.run(c, e -> OpeningPass.door(c, e), true);
			if (t.to() < 2) return;
			WalkOps.endDoor(c, m, t, true, c.spec().depth(), FRONT, 0);
			WalkOps.backDoor(c, m, t, c.spec().width());
		};
	}

	@Override
	public RoomProgram rooms() {
		return new RoomProgram() {
			@Override
			public String use(Role role, int area, Draws d) { return RoomProgram.DEFAULT.use(role, area, d); }

			@Override
			public List<String> hooks(String use, int area, Draws d) {
				return switch (use) {
					case "entry" -> List.of("chest", "table");
					case "room", "hall" -> List.of("chest", "bed", "table").subList(0, Math.min(3, 1 + area / 8));
					default -> RoomProgram.DEFAULT.hooks(use, area, d);
				};
			}
		};
	}
}
