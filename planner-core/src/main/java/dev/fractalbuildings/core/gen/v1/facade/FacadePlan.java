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

	/**
	 * Bays on one face: bay i covers positions [start + sum(bays[0..i)), ... + bays[i]) and has kind
	 * {@code kinds.get(i)} ({@link #WINDOW}, {@link #DOOR}, {@link #BLANK}, or a type's own kind such
	 * as "shopfront"). Pier positions are the bay boundaries.
	 */
	public record Layout(Face face, int start, int[] bays, List<String> kinds) {
		public Layout {
			bays = bays.clone();
			kinds = List.copyOf(kinds);
			if (bays.length != kinds.size()) throw new IllegalArgumentException("bays " + bays.length + " kinds " + kinds.size());
		}

		/** First position of bay i. */
		public int bayStart(int i) {
			int p = start;
			for (int k = 0; k < i; k++) p += bays[k];
			return p;
		}

		/** Centre position of bay i (left of centre for even widths). */
		public int centre(int i) { return bayStart(i) + (bays[i] - 1) / 2; }

		@Override
		public boolean equals(Object o) {
			return o instanceof Layout l && face.equals(l.face) && start == l.start && java.util.Arrays.equals(bays, l.bays) && kinds.equals(l.kinds);
		}

		@Override
		public int hashCode() { return Objects.hash(face, start, java.util.Arrays.hashCode(bays), kinds); }
	}

	/** The entrance door at position {@code pos} of {@code face} (1 wide, 2 high from the face's y0). */
	public record Entrance(Face face, int pos) {
		public Entrance {
			Objects.requireNonNull(face);
			if (pos < face.a() || pos >= face.b()) throw new IllegalArgumentException("entrance " + pos + " outside face " + face);
		}

		public int x() { return face.x(pos); }

		public int z() { return face.z(pos); }

		public int y() { return face.y0(); }
	}
}
