package dev.fractalbuildings.mod.ordersim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * ORDERSIM S3: the chunk orders. An order is the sequence in which the decorated chunks (cx, cz) get their turn. Together forward and
 * reverse put every adjacent pair both ways round; shuffles interleave three or more chunks; the spiral starts at the centre; the
 * checker runs one parity first so every chunk has neighbours both before and after it.
 */
public final class SimOrders {
	private SimOrders() {}

	public record Order(String name, List<int[]> chunks) {
		public int size() { return chunks.size(); }
	}

	/** Extra shuffles for a soak: {@code -Dfb.ordersim.shuffles=N} makes shuffle-1..N (default 2). */
	public static int shuffles() { return Integer.getInteger("fb.ordersim.shuffles", 2); }

	/** The fast set over the w x h chunk window at (cx0, cz0): forward, reverse, shuffle-1..N, spiral, checker. */
	public static List<Order> fast(int cx0, int cz0, int w, int h, long seed) {
		List<Order> out = new ArrayList<>();
		out.add(forward(cx0, cz0, w, h));
		out.add(reverse(cx0, cz0, w, h));
		for (int k = 1; k <= shuffles(); k++) out.add(shuffle(cx0, cz0, w, h, seed, k));
		out.add(spiral(cx0, cz0, w, h));
		out.add(checker(cx0, cz0, w, h));
		return out;
	}

	public static Order forward(int cx0, int cz0, int w, int h) {
		List<int[]> l = new ArrayList<>();
		for (int cz = cz0; cz < cz0 + h; cz++) for (int cx = cx0; cx < cx0 + w; cx++) l.add(new int[] {cx, cz});
		return new Order("forward", l);
	}

	public static Order reverse(int cx0, int cz0, int w, int h) {
		List<int[]> l = new ArrayList<>(forward(cx0, cz0, w, h).chunks());
		java.util.Collections.reverse(l);
		return new Order("reverse", l);
	}

	public static Order shuffle(int cx0, int cz0, int w, int h, long seed, int k) {
		List<int[]> l = new ArrayList<>(forward(cx0, cz0, w, h).chunks());
		SplittableRandom r = new SplittableRandom(seed * 1_000_003L + k);
		for (int i = l.size() - 1; i > 0; i--) java.util.Collections.swap(l, i, r.nextInt(i + 1));
		return new Order("shuffle-" + k, l);
	}

	/** From the centre outwards, ring by ring (Chebyshev distance), each ring clockwise from its north-west... by angle. */
	public static Order spiral(int cx0, int cz0, int w, int h) {
		double mx = cx0 + (w - 1) / 2.0, mz = cz0 + (h - 1) / 2.0;
		List<int[]> l = new ArrayList<>(forward(cx0, cz0, w, h).chunks());
		l.sort(Comparator.<int[]>comparingDouble(c -> Math.max(Math.abs(c[0] - mx), Math.abs(c[1] - mz)))
			.thenComparingDouble(c -> Math.atan2(c[1] - mz, c[0] - mx)).thenComparingInt(c -> c[1]).thenComparingInt(c -> c[0]));
		return new Order("spiral", l);
	}

	public static Order checker(int cx0, int cz0, int w, int h) {
		List<int[]> l = new ArrayList<>();
		for (int parity = 0; parity < 2; parity++)
			for (int cz = cz0; cz < cz0 + h; cz++) for (int cx = cx0; cx < cx0 + w; cx++) if (Math.floorMod(cx + cz, 2) == parity) l.add(new int[] {cx, cz});
		return new Order("checker", l);
	}
}
