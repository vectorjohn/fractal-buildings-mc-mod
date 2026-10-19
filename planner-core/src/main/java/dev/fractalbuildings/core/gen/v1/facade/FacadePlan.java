package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.model.Face;
import java.util.List;
import java.util.Objects;

/**
 * The facade phase's frozen output, read by the interior pass (where partitions may meet the outer
 * walls, where the entrance is) and the opening pass (where to cut windows and doors).
 *
 * @param layouts one per {@link Face} that got bays, in face order
 * @param entrance the main entrance: a door on the main mass's front face, storey 0
 * @param others one entrance for each other mass in {@code Massing.entrances()}, in that order
 */
public record FacadePlan(List<Layout> layouts, Entrance entrance, List<Entrance> others) {
	public static final String WINDOW = "window";
	public static final String DOOR = "door";
	public static final String BLANK = "blank";

	public FacadePlan {
		layouts = List.copyOf(layouts);
		Objects.requireNonNull(entrance);
		others = List.copyOf(others);
	}

	/** The main entrance, then the others. */
	public List<Entrance> entrances() {
		List<Entrance> all = new java.util.ArrayList<>();
		all.add(entrance);
		all.addAll(others);
		return all;
	}

	/** Window shape of a layout: {@code width} (1 or 2; 2 only fits even bays of 4+), {@code height}, {@code sill} above the face's y0. */
	public record Win(int width, int height, int sill) {
		public Win {
			if (width < 1 || height < 1 || sill < 0) throw new IllegalArgumentException("window " + width + "x" + height + " sill " + sill);
		}
	}

	/**
	 * Bays on one face: bay i covers positions [start + sum(bays[0..i)), ... + bays[i]) and has kind
	 * {@code kinds.get(i)} ({@link #WINDOW}, {@link #DOOR}, {@link #BLANK}, or a type's own kind such
	 * as "shopfront"). Pier positions are the bay boundaries. {@code win} is the window shape for the
	 * face's window bays; null means the style's (1 wide, its height and sill).
	 */
	public record Layout(Face face, int start, int[] bays, List<String> kinds, Win win) {
		public Layout {
			bays = bays.clone();
			kinds = List.copyOf(kinds);
			if (bays.length != kinds.size()) throw new IllegalArgumentException("bays " + bays.length + " kinds " + kinds.size());
		}

		public Layout(Face face, int start, int[] bays, List<String> kinds) { this(face, start, bays, kinds, null); }

		/** The same layout with other kinds. */
		public Layout withKinds(List<String> k) { return new Layout(face, start, bays, k, win); }

		/** First position of bay i. */
		public int bayStart(int i) {
			int p = start;
			for (int k = 0; k < i; k++) p += bays[k];
			return p;
		}

		/** Centre position of bay i (left of centre for even widths). */
		public int centre(int i) { return bayStart(i) + (bays[i] - 1) / 2; }

		/** Width of bay i's window or double door: 2 in an even bay of 4+ when the window shape is 2 wide, else 1. */
		public int windowWidth(int i) { return win != null && win.width() >= 2 && bays[i] >= 4 && bays[i] % 2 == 0 ? 2 : 1; }

		/** First position of bay i's window, centred in the bay (left of centre for even widths). */
		public int windowStart(int i) { return bayStart(i) + (bays[i] - windowWidth(i)) / 2; }

		@Override
		public boolean equals(Object o) {
			return o instanceof Layout l && face.equals(l.face) && start == l.start && java.util.Arrays.equals(bays, l.bays)
					&& kinds.equals(l.kinds) && java.util.Objects.equals(win, l.win);
		}

		@Override
		public int hashCode() { return Objects.hash(face, start, java.util.Arrays.hashCode(bays), kinds, win); }
	}

	/**
	 * The entrance door at position {@code pos} of {@code face} (2 high from the face's y0). {@code width}
	 * 2 is a double door on {@code pos} and {@code pos + 1}; only the first cell is guaranteed free
	 * inside (the interior pass keeps that one), so the opening pass cuts the second cell only if it can.
	 */
	public record Entrance(Face face, int pos, int width) {
		public Entrance {
			Objects.requireNonNull(face);
			if (width < 1 || width > 2) throw new IllegalArgumentException("entrance width " + width);
			if (pos < face.a() || pos + width > face.b()) throw new IllegalArgumentException("entrance " + pos + " outside face " + face);
		}

		public Entrance(Face face, int pos) { this(face, pos, 1); }

		public int x() { return face.x(pos); }

		public int z() { return face.z(pos); }

		public int y() { return face.y0(); }
	}
}
