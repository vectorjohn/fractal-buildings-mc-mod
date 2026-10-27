package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.road.RoadNet.Col;
import java.util.ArrayList;
import java.util.List;

/**
 * What one chunk of road looks like, as heights (ADR 0004 TC, WR3, MF3): for each of the chunk's 256 columns, whether it is road,
 * deck, a terraced cut or an embankment beside the road, and the height of its new top block. Pure: a column's answer depends on the
 * road columns around it (from the planner's query), whether a district owns it, and its own ground, nothing else. The mod only
 * reads the ground and writes the blocks.
 *
 * <p>Heights here are block y of the <i>top block</i> (the road's {@code Col.y} is a surface height, one more).
 *
 * <p><b>Terraced cuts</b> (the ground is above the deck): past a 1-block shoulder level with the road, the column's top rises in
 * steps of {@link #STEP} with benches {@link #BENCH} wide, at most {@link #CUT_REACH} blocks from the road edge, and never above the
 * column's own ground. <b>Embankments</b> (the ground is below): 1:1 down from the deck, at most {@link #FILL_REACH} blocks out.
 * Beyond that reach the natural ground stays; any face left there is accepted. <b>Water</b>: a wet column under the road gets a deck
 * block and no fill; a wet column beside the road is left alone.
 */
public final class RoadSurface {
	private RoadSurface() {}

	public static final int STEP = 2;
	public static final int BENCH = 2;
	/** Blocks beyond the road edge a cut may reach (the shoulder is the first). */
	public static final int CUT_REACH = 12;
	public static final int FILL_REACH = 8;
	/** Air claimed above a new top block, or up to the old ground if that is higher. */
	public static final int HEADROOM = 4;

	public static final byte NONE = 0, ROAD = 1, DECK = 2, TERRACE = 3;

	/** The ground of a column, read from the world (or a test terrain). */
	public interface Ground {
		/** y of the top ground block: below plants, trees, water and ice. */
		int topY(int x, int z);

		/** Whether water or ice lies over that ground. */
		boolean wet(int x, int z);
	}

	/** One chunk: index {@code (z & 15) * 16 + (x & 15)}. {@code ground} is filled for every non-{@link #NONE} column. */
	public static final class Plan {
		public final byte[] kind = new byte[256];
		public final int[] surf = new int[256];
		public final int[] ground = new int[256];
		/** The surface block id of each ROAD or DECK column: its route's one material (DS-30); null where the route carries none. */
		public final String[] block = new String[256];
		public int roads, decks, terraces;

		public boolean empty() { return roads + decks + terraces == 0; }
	}

	/**
	 * The new top of a column {@code u} blocks beyond the road edge ({@code u = 0}: the shoulder, level with the road) whose road deck
	 * top block is at {@code deckTop} and whose ground top block is at {@code groundTop}. Equal to {@code groundTop} when nothing
	 * changes.
	 */
	public static int top(int deckTop, int u, int groundTop) {
		if (groundTop > deckTop) {
			if (u >= CUT_REACH) return groundTop;
			int terrace = deckTop + STEP * Math.floorDiv(u + BENCH - 1, BENCH);
			return Math.min(groundTop, terrace);
		}
		if (groundTop < deckTop) {
			if (u >= FILL_REACH) return groundTop;
			return Math.max(groundTop, deckTop - u);
		}
		return groundTop;
	}

	private static final int R = CUT_REACH;
	private static final int N = 16 + 2 * R;
	/** Offsets (dx, dz, blocks from the road edge + 1) to look at, nearest first; ties by (dz, dx) so the nearest road column is unique. */
	private static final int[][] OFFSETS = offsets();

	private static int[][] offsets() {
		List<int[]> l = new ArrayList<>();
		for (int dz = -R; dz <= R; dz++) {
			for (int dx = -R; dx <= R; dx++) {
				int d2 = dx * dx + dz * dz;
				if (d2 == 0) continue;
				int k = (int) StrictMath.floor(StrictMath.sqrt(d2) + 0.5);
				if (k > R) continue;
				l.add(new int[] {dx, dz, d2});
			}
		}
		l.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
		int[][] out = new int[l.size()][];
		for (int i = 0; i < out.length; i++) {
			int[] o = l.get(i);
			out[i] = new int[] {o[0], o[1], (int) StrictMath.floor(StrictMath.sqrt(o[2]) + 0.5)};
		}
		return out;
	}

	/**
	 * @param around the road columns of the 3 x 3 chunks around the chunk, index {@code (dz + 1) * 3 + (dx + 1)}; entry 4 is the chunk's own
	 * @param district columns a district draws (they are left alone), or null
	 */
	public static Plan plan(int chunkX, int chunkZ, List<List<Col>> around, boolean[] district, Ground ground) {
		Plan p = new Plan();
		boolean any = false;
		for (List<Col> l : around) if (!l.isEmpty()) { any = true; break; }
		if (!any) return p;
		int gx0 = chunkX * 16 - R, gz0 = chunkZ * 16 - R;
		int[] deck = new int[N * N];
		java.util.Arrays.fill(deck, Integer.MIN_VALUE);
		Col[] own = new Col[256];
		for (int i = 0; i < 9; i++) {
			for (Col c : around.get(i)) {
				int gx = c.x() - gx0, gz = c.z() - gz0;
				if (gx < 0 || gz < 0 || gx >= N || gz >= N) continue;
				deck[gz * N + gx] = c.y() - 1;
				if (i == 4) own[(c.z() & 15) * 16 + (c.x() & 15)] = c;
			}
		}
		int x0 = chunkX * 16, z0 = chunkZ * 16;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int i = dz * 16 + dx;
				if (district != null && district[i]) continue;
				int x = x0 + dx, z = z0 + dz;
				Col c = own[i];
				if (c != null) {
					boolean wet = ground.wet(x, z);
					p.kind[i] = wet || c.crossing() ? DECK : ROAD;
					p.block[i] = p.kind[i] == DECK ? c.deck() : c.road();
					p.surf[i] = c.y() - 1;
					p.ground[i] = ground.topY(x, z);
					if (p.kind[i] == DECK) p.decks++; else p.roads++;
					continue;
				}
				int gi = (dz + R) * N + (dx + R);
				for (int[] o : OFFSETS) {
					int d = deck[gi + o[1] * N + o[0]];
					if (d == Integer.MIN_VALUE) continue;
					if (ground.wet(x, z)) break;
					int g = ground.topY(x, z);
					int t = top(d, o[2] - 1, g);
					if (t != g) {
						p.kind[i] = TERRACE;
						p.surf[i] = t;
						p.ground[i] = g;
						p.terraces++;
					}
					break;
				}
			}
		}
		return p;
	}
}
