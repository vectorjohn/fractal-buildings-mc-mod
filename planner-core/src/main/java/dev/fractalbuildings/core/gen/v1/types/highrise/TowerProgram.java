package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * The floor programme of a high-rise (plan docs/plans/highrises.md, section 2): a kind per storey, drawn
 * once from the massing's tiers and re-derived by later passes from the final massing ({@link #read}),
 * so no field on {@code Massing} carries it. Storeys {@code 0..n-1} are habitable, storey {@code n} is
 * the stair house.
 *
 * <p>Storey 0 is the lobby. Towers of 12+ storeys get 1 to 3 special floors (banquet, open floor,
 * suites) on candidate storeys (the first above the podium, the first of each later tier, the last
 * below the crown); never two adjacent, and never next to the lobby. Lobby and special floors are
 * {@link #SPECIAL_H} high.
 */
public final class TowerProgram {
	public enum Kind { LOBBY, TYPICAL, BANQUET, OPEN, SUITES, STAIRHOUSE }

	public static final int LOBBY_H = 6, SPECIAL_H = 6, TYPICAL_H = 4, STAIRHOUSE_H = 4;
	/** Smallest tower that gets special floors. */
	public static final int MIN_SPECIAL_STOREYS = 12;
	/** Facade band key of a mass's top storey (same value as the facade pass's). */
	public static final int TOP_KEY = 1_000_000;

	private final Kind[] kinds;
	private final int[] massFrom, massTo;

	private TowerProgram(Kind[] kinds, int[] massFrom, int[] massTo) {
		this.kinds = kinds;
		this.massFrom = massFrom;
		this.massTo = massTo;
	}

	/** Number of habitable storeys (the stair house is storey {@code n}). */
	public int habitable() { return kinds.length - 1; }

	public Kind kind(int s) { return kinds[s]; }

	/** A special floor (banquet, open floor or suites); the lobby and the stair house are not counted. */
	public boolean special(int s) {
		Kind k = kinds[s];
		return k == Kind.BANQUET || k == Kind.OPEN || k == Kind.SUITES;
	}

	/** Height of storey {@code s} for the programme. */
	public int height(int s) { return height(kinds[s]); }

	static int height(Kind k) {
		return switch (k) {
			case LOBBY -> LOBBY_H;
			case TYPICAL -> TYPICAL_H;
			case STAIRHOUSE -> STAIRHOUSE_H;
			default -> SPECIAL_H;
		};
	}

	/**
	 * Facade band key of storey {@code s}: 0 the lobby storey (the ground storey of the podium), else {@code 100 + mass index} for every storey of a mass (the top one too: a tier draws its bays once),
	 * typical and special storeys alike (same bays on every storey of a tier, DS-25).
	 */
	public int band(int s) {
		if (s == 0) return 0;
		for (int i = 0; i < massFrom.length; i++)
			if (s >= massFrom[i] && s < massTo[i]) return 100 + i;
		return TOP_KEY;
	}

	/**
	 * The programme of {@code n} habitable storeys (plus the stair house at index {@code n}): {@code podium}
	 * storeys in the main mass, {@code bandStarts} the first storey of each shaft tier, {@code crownStart} the
	 * crown's first storey or -1.
	 */
	public static Kind[] plan(int n, int podium, int[] bandStarts, int crownStart, Draws r) {
		Kind[] out = new Kind[n + 1];
		java.util.Arrays.fill(out, Kind.TYPICAL);
		out[0] = Kind.LOBBY;
		out[n] = Kind.STAIRHOUSE;
		if (n < MIN_SPECIAL_STOREYS) return out;
		int want = n < 20 ? 1 : n < 36 ? r.range(1, 2) : r.range(2, 3);
		// candidates in a fixed order: above the podium, each later tier start, under the crown; kind weights per role
		List<int[]> cand = new ArrayList<>(); // {storey, role}: 0 podium, 1 setback, 2 crown
		add(cand, podium, 0, n);
		for (int i = 0; i < bandStarts.length; i++) add(cand, bandStarts[i], i == 0 ? 0 : 1, n);
		add(cand, crownStart >= 0 ? crownStart - 1 : n - 1, 2, n);
		while (want > 0 && !cand.isEmpty()) {
			int[] pick = cand.remove(r.below(cand.size()));
			int s = pick[0];
			if (s < 2 || s >= n || out[s] != Kind.TYPICAL || special(out, s - 1) || special(out, s + 1)) continue;
			out[s] = switch (pick[1]) {
				case 2 -> r.below(3) < 2 ? Kind.SUITES : Kind.BANQUET;
				default -> r.below(2) == 0 ? Kind.OPEN : Kind.BANQUET;
			};
			want--;
		}
		return out;
	}

	private static void add(List<int[]> cand, int s, int role, int n) {
		if (s < 1 || s >= n) return;
		for (int[] c : cand) if (c[0] == s) return;
		cand.add(new int[] {s, role});
	}

	private static boolean special(Kind[] k, int s) {
		return k[s] == Kind.BANQUET || k[s] == Kind.OPEN || k[s] == Kind.SUITES;
	}

	/** The programme the massing {@code m} was built with; null for the fallback box (no core). */
	static TowerProgram of(Massing m, Draws r) {
		if (m.cores().isEmpty()) return null;
		int n = m.grid().count() - 1;
		int podium = m.main().to();
		List<Integer> shafts = new ArrayList<>();
		int crown = -1;
		int[] from = new int[m.masses().size()], to = new int[m.masses().size()];
		for (int i = 0; i < m.masses().size(); i++) {
			Mass a = m.masses().get(i);
			from[i] = a.from();
			to[i] = a.to();
			if (a.kind().equals("shaft")) shafts.add(a.from());
			if (a.kind().equals("crown")) crown = a.from();
		}
		int[] starts = shafts.stream().mapToInt(Integer::intValue).toArray();
		return new TowerProgram(plan(n, podium, starts, crown, r), from, to);
	}

	/** The programme of the built high-rise; null when its massing is the fallback box. */
	public static TowerProgram read(BuildContext c) {
		Massing m = c.massing();
		if (m.cores().isEmpty()) return null;
		return of(m, drawsFor(c, m.grid().count() - 1));
	}

	/** The draw stream for {@code n} habitable storeys; massing and later passes both use it. */
	static Draws drawsFor(BuildContext c, int n) { return c.draws("highrise/programme", n); }
}
