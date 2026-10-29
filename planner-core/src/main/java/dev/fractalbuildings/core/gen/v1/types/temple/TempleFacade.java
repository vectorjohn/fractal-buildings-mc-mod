package dev.fractalbuildings.core.gen.v1.types.temple;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared facade layout, then the front made symmetric: the main entrance on the centre axis of
 * the front block's north face (odd width, so there is a centre cell) with mirrored window bays on
 * both sides of the door.
 */
final class TempleFacade {
	private TempleFacade() {}

	static void run(BuildContext c) {
		FacadePass.run(c);
		FacadePlan plan = c.facadePlan();
		String main = c.massing().main().id();
		FacadeStyle st = c.style().facade();
		List<FacadePlan.Layout> layouts = new ArrayList<>(plan.layouts());
		Face fr = null;
		for (Face f : c.faces()) if (f.mass().equals(main) && f.storey() == 0 && f.side() == Facing.NORTH) fr = f;
		final Face front = fr;
		if (front == null || front.length() % 2 == 0) return; // keep the default door
		int cp = front.a() + front.length() / 2;
		int half = cp - front.a() - 1;
		int[] side = half >= st.bayMin() ? FacadePass.bays(half, st.bayMin(), st.bayMax()) : null;
		if (side == null) side = half > 0 ? new int[] {half} : new int[0];
		boolean windows = half >= st.bayMin();
		int[] bays = new int[2 * side.length + 1];
		List<String> kinds = new ArrayList<>();
		for (int i = 0; i < side.length; i++) {
			bays[i] = side[i];
			bays[bays.length - 1 - i] = side[i];
		}
		for (int i = 0; i < side.length; i++) kinds.add(windows ? FacadePlan.WINDOW : FacadePlan.BLANK);
		kinds.add(FacadePlan.DOOR);
		for (int i = 0; i < side.length; i++) kinds.add(windows ? FacadePlan.WINDOW : FacadePlan.BLANK);
		bays[side.length] = 1;
		FacadePlan.Layout lay = new FacadePlan.Layout(front, front.a() + 1, bays, kinds);
		for (int i = 0; i < layouts.size(); i++) {
			if (layouts.get(i).face().equals(front)) layouts.set(i, lay);
		}
		if (layouts.stream().noneMatch(l -> l.face().equals(front))) layouts.add(lay);
		c.setFacadePlan(new FacadePlan(layouts, new FacadePlan.Entrance(front, cp), plan.others(), plan.extras()));
	}
}
