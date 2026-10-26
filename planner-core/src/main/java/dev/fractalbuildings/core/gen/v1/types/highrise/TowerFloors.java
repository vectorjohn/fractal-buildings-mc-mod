package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlanner;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Result;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Spec;
import dev.fractalbuildings.core.gen.v1.types.highrise.TowerPlate.Template;
import dev.fractalbuildings.core.rand.Draws;

/**
 * The high-rise's floor planner (plan docs/plans/highrises.md, B3a): turns the {@link TowerProgram} kind of a storey,
 * the {@link TowerBlock} and the mass's window cells into a {@link TowerPlate} spec, and the plate's result into
 * the interior pass's plan. Lobby and open floors are one grand room, banquet floors a foyer and a banquet room,
 * typical floors and suites a corridor with units on both sides, the stair house the stairwell and a machine room.
 * A plate that does not fit its template (the window rhythm lets a partition meet a window, ...) is drawn again
 * with another seed ({@link #TRIES} times), then the generic planner takes the floor ({@link #fallbacks}).
 */
public final class TowerFloors implements FloorPlanner {
	/** John's open question: a windowless private entry strip on the hall side of deep units. */
	public static final boolean ENTRY_STRIP = true;
	/** Draws of one plate before the generic planner is used. */
	static final int TRIES = 6;
	/** Percent of towers whose typical floors are apartments (the rest are offices). */
	static final int FLAT_PERCENT = 60;

	/** Test counters: floors planned, and floors that fell back to the generic planner (per thread, reset by the test). */
	static final ThreadLocal<int[]> STATS = ThreadLocal.withInitial(() -> new int[2]);
	/** Test aid with {@link TowerPlate#DEBUG}: why each floor fell back. */
	static final ThreadLocal<java.util.List<String>> FAILS = ThreadLocal.withInitial(java.util.ArrayList::new);

	@Override
	public Planned plan(BuildContext c, int massIndex, Mass a, int storey, boolean[][] reserved, boolean[][] core) {
		Massing m = c.massing();
		TowerProgram prog = TowerProgram.read(c);
		TowerBlock b = TowerBlock.of(m, TowerBlock.SD);
		if (prog == null || b == null || storey > prog.habitable()) return null;
		TowerProgram.Kind kind = prog.kind(storey);
		int ox = a.x() + 1, oz = a.z() + 1, w = a.sx() - 2, d = a.sz() - 2;
		int[] sw = b.stairwell(), sh = b.shaft();
		int[] stair = {sw[0] - ox, sw[1] - oz, sw[0] - ox + sw[2], sw[1] - oz + sw[3]};
		int[] shaft = {sh[0] - ox, sh[1] - oz, sh[0] - ox + sh[2], sh[1] - oz + sh[3]};
		boolean flat = unitUse(c).equals("flat");
		int target = unitTarget(c, flat);
		boolean[][] win = dev.fractalbuildings.core.gen.v1.interior.InteriorPass.windowCells(c, a, storey);
		Template t;
		String grand = "";
		String unit = flat ? "flat" : "office";
		switch (kind) {
			case LOBBY -> { t = Template.GRAND; grand = "lobby"; }
			case OPEN -> { t = Template.GRAND; grand = "open"; }
			case BANQUET -> t = Template.BANQUET;
			case SUITES -> { t = Template.CORRIDOR; unit = "flat"; target = 14 + (int) (c.hash("highrise/suites") & 3); }
			case STAIRHOUSE -> t = Template.STAIRHOUSE;
			default -> t = Template.CORRIDOR;
		}
		int entranceX = Math.max(0, Math.min(w - 1, c.facadePlan().entrance().x() - ox));
		int[] st = STATS.get();
		String lastWhy = null;
		st[0]++;
		for (int attempt = 0; attempt < TRIES; attempt++) {
			Draws r = c.draws("highrise/plate", massIndex, t.ordinal(), attempt);
			Spec spec = new Spec(w, d, stair, shaft, b.hallZ0() - oz, b.hallZ1() - oz, win, t, grand, target, unit, ENTRY_STRIP && attempt < 3, entranceX);
			Result res = TowerPlate.plan(spec, r);
			if (res != null) return new Planned(res.plan(), res.uses(), res.studio());
			lastWhy = TowerPlate.DEBUG ? t + " " + TowerPlate.WHY.get() : null;
			if (t == Template.STAIRHOUSE) break;
		}
		st[1]++;
		if (TowerPlate.DEBUG) FAILS.get().add(lastWhy);
		return null;
	}

	@Override
	public boolean newBand(BuildContext c, int massIndex, Mass a, int storey) {
		TowerProgram prog = TowerProgram.read(c);
		return prog != null && storey > 0 && storey <= prog.habitable() && prog.kind(storey) != prog.kind(storey - 1);
	}

	/** The use of the units of this tower's typical floors: a draw per building. */
	static String unitUse(BuildContext c) { return c.draws("highrise/unit").below(100) < FLAT_PERCENT ? "flat" : "office"; }

	/** Unit width target (cells along the hall): 9-11 for flats, 8-12 for offices, one draw per building. */
	static int unitTarget(BuildContext c, boolean flat) {
		Draws r = c.draws("highrise/target");
		return flat ? r.range(9, 11) : r.range(8, 12);
	}
}
