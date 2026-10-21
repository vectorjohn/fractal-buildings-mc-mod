package dev.fractalbuildings.core.metrics;

import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Room;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How many ordinary rooms a person walks through to reach a room (item 5.11). Each storey is
 * measured on its own: you arrive behind an outside door or at the top of a stair flight, and
 * every door or archway you take out of a room costs one unless that room is the one you arrived in or circulation space
 * (entry, corridor, stair, hall, landing, porch, nave: somewhere meant to be walked through). A
 * room with cost 2 or more is reachable only through at least two other rooms: the "chain of
 * rooms" the review flagged. {@link Result#deepStrict} is the literal count: every room, the one you arrive in included, counts.
 */
public final class Circulation {
	private Circulation() {}

	static final Set<String> SPACE = Set.of("entry", "corridor", "stair", "hall", "landing", "porch", "nave");

	/** Rooms in total, rooms behind two or more ordinary rooms, the largest number of rooms walked through. */
	public record Result(int rooms, int deep, int maxDepth, int deepStrict) {}

	public static Result measure(BuildingIr b) {
		Map<String, String> use = new LinkedHashMap<>();
		for (Room r : b.rooms()) use.put(r.id(), r.use());
		Map<String, String> all = new LinkedHashMap<>();
		for (Room r : b.rooms()) all.put(r.id(), "room");
		int[] strict = walk(b, all, false);
		int[] res = walk(b, use, true);
		return new Result(b.rooms().size(), res[0], res[1], strict[0]);
	}

	/** {rooms costing >= 2, largest cost}; the arrival room is free if {@code arrivalFree}, and rooms of a {@link #SPACE} use are free. */
	private static int[] walk(BuildingIr b, Map<String, String> use, boolean arrivalFree) {
		Map<String, Integer> cost = new LinkedHashMap<>();
		Deque<String> q = new ArrayDeque<>();
		Set<String> arrival = new java.util.TreeSet<>();
		for (Opening o : b.openings()) {
			String arrive = null;
			if (o.kind() == OpeningKind.DOOR && o.from().equals(Opening.OUTSIDE)) arrive = o.to();
			else if (o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE)) arrive = o.from();
			else if (o.kind() == OpeningKind.STAIR) arrive = o.to();
			if (arrive != null && !arrive.equals(Opening.OUTSIDE) && cost.putIfAbsent(arrive, 0) == null) {
				q.add(arrive);
				arrival.add(arrive);
			}
		}
		while (!q.isEmpty()) { // 0-1 BFS; re-relaxing is cheap at these sizes
			String a = q.pollFirst();
			int w = SPACE.contains(use.get(a)) || arrivalFree && arrival.contains(a) ? 0 : 1, ca = cost.get(a);
			for (Opening o : b.openings()) {
				if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
				String n = o.from().equals(a) ? o.to() : o.to().equals(a) ? o.from() : null;
				if (n == null || n.equals(Opening.OUTSIDE)) continue;
				Integer old = cost.get(n);
				if (old == null || old > ca + w) {
					cost.put(n, ca + w);
					if (w == 0) q.addFirst(n); else q.addLast(n);
				}
			}
		}
		int deep = 0, max = 0;
		for (Room r : b.rooms()) {
			Integer d = cost.get(r.id());
			if (d == null) continue;
			max = Math.max(max, d);
			if (d >= 2) deep++;
		}
		return new int[] {deep, max};
	}
}
