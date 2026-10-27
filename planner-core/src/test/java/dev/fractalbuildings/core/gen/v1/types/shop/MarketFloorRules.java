package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The open floor of a market hall (7.17 S4 round 2): posts in symmetric bays on pads with brackets, 2 to 4 stalls, a 3-wide walk and the stair
 * foot clear, goods and lights (DS-24). Empty list when a hall follows them.
 */
final class MarketFloorRules {
	private MarketFloorRules() {}

	static List<String> problems(BuildingIr b, int lift, Opening up) {
		List<String> p = new ArrayList<>();
		Box3 main = b.volumes().stream().filter(v -> v.id().equals("main0")).findFirst().orElseThrow().box();
		int mx = main.x(), maxX = main.maxX(), mz = main.z(), maxZ = main.maxZ();
		// posts: the front (square) face symmetric about the centre, each on a stone pad (paving and one block), a bracket beside each inner head
		List<Integer> xs = new ArrayList<>();
		for (var v : b.volumes()) if (v.id().startsWith("leg") && v.box().z() == mz) xs.add(v.box().x());
		Collections.sort(xs);
		for (int i = 0; i < xs.size(); i++) if (xs.get(i) + xs.get(xs.size() - 1 - i) != mx + maxX - 1) p.add("front posts " + xs + " are not symmetric in " + mx + ".." + (maxX - 1));
		if (maxX - mx == 11 && !xs.equals(List.of(mx, mx + 3, mx + 7, mx + 10))) p.add("width 11 front posts " + xs);
		for (var v : b.volumes()) {
			if (!v.id().startsWith("leg")) continue;
			boolean pad = b.boxes().stream().anyMatch(x -> x.role() == Role.FOUNDATION && x.box().sy() >= 2 && x.box().y() == 0 && x.box().x() == v.box().x() && x.box().z() == v.box().z());
			if (!pad) p.add("post " + v.id() + " has no pad");
		}
		for (int x : xs) {
			if (x == mx || x == maxX - 1) continue;
			boolean bracket = b.boxes().stream().anyMatch(q -> q.role() == Role.BEAM && q.box().y() == lift - 2 && q.box().z() == mz && Math.abs(q.box().x() - x) == 1);
			if (!bracket) p.add("post at " + x + " has no bracket");
		}
		// stalls: 2 to 4 (one lantern each on its counter), goods on them, nothing else on the floor but ceiling lights (no job blocks, no counter hook)
		long stalls = b.hooks().stream().filter(h -> h.kind().equals("light") && h.y() < lift).count();
		if (stalls < 2 || stalls > 4) p.add(stalls + " stalls");
		if (b.hooks().stream().filter(h -> h.kind().equals("goods")).count() < 2 * stalls) p.add("fewer than 2 goods hooks a stall");
		for (var h : b.hooks()) if (h.y() < lift && !List.of("light", "goods", "chest", "ceiling_light").contains(h.kind())) p.add("hook " + h.kind() + " on the open floor");
		if (MarketStalls.LEN > 3 || MarketStalls.DEPTH > 2) p.add("stall bigger than 3 x 2");
		// the walk: 3 wide on the axis from the square face to the stair's approach; the stair foot with its approach, clear up to head height
		int axis = mx + (maxX - mx) / 2;
		for (Box x : b.boxes()) {
			Box3 q = x.box();
			if (x.role() == Role.AIR || q.y() >= lift - 1 || q.maxY() <= 1) continue;
			boolean frontEdge = q.z() == mz; // the ring and the brackets on the square face stand in the edge row
			boolean inWalk = q.x() <= axis + 1 && q.maxX() > axis - 1 && q.z() < maxZ - 5 && q.maxZ() > mz + 1 && q.y() < lift - 2;
			if (inWalk && !frontEdge) p.add("a " + x.role() + " block in the walk at " + q);
			int footX = up.box().x() - 1;
			boolean inFoot = q.x() <= footX && q.maxX() > footX && q.z() <= up.box().z() && q.maxZ() > up.box().z() - 2 && q.y() < 3 && x.role() != Role.FOUNDATION;
			if (inFoot) p.add("a " + x.role() + " block at the stair foot " + q);
		}
		// light: a lantern from the hall floor about every 5 x 5 (at least one per 30 cells), every cell of the floor within 5 of one
		int iw = maxX - mx - 2, id = maxZ - mz - 2;
		List<int[]> lights = new ArrayList<>();
		for (var h : b.hooks()) if (h.kind().equals("ceiling_light") && h.y() < lift) lights.add(new int[] {h.x(), h.z()});
		if (lights.size() < (iw * id + 29) / 30) p.add(lights.size() + " lanterns for " + iw * id + " cells");
		boolean far = false;
		for (int x = mx + 1; x <= maxX - 2 && !far; x++)
			for (int z = mz + 1; z <= maxZ - 2 && !far; z++) {
				int best = 99;
				for (int[] l : lights) best = Math.min(best, Math.max(Math.abs(l[0] - x), Math.abs(l[1] - z)));
				if (best > 5) { p.add("cell " + x + "," + z + " is " + best + " from the nearest lantern"); far = true; }
			}
		return p;
	}
}
