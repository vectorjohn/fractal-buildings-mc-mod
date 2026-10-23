package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;

/**
 * The dry check and the deck of one road (ADR 0004 WR1 to WR3, HT), from exact reads at the stations of its curve, once.
 *
 * <p>A station is <i>wet</i> when the exact water depth there is at least 1. Wet stations are grouped into crossings; wet
 * stations less than {@link #MIN_CROSSING_GAP} blocks apart are one crossing. The wet extent of a crossing is measured block
 * by block between the dry stations on either side (at most {@code 2 x 7} more reads per crossing end). A crossing longer than
 * {@link #MAX_CROSSING} blocks, or wet ground at either end of the road, fails the road. The deck is the mean of the surface
 * over +- {@link #WINDOW} stations, limited to {@link #MAX_STEP} blocks of change per station; over a crossing it is flat at
 * the higher bank's deck height.
 *
 * <p>Pure: the result depends on the stations and the probe alone.
 */
public final class Profile {
	private Profile() {}

	/** Longest wet run a single span may bridge, blocks (John, 2026-10-23). */
	public static final int MAX_CROSSING = 20;
	/** Crossings closer than this count as one run. */
	public static final int MIN_CROSSING_GAP = 32;
	/** Smoothing half window, in stations. */
	public static final int WINDOW = 8;
	/** Largest deck change between two stations: 2 blocks in 8, a grade of 1 in 4. */
	public static final int MAX_STEP = 2;

	/**
	 * One crossing: stations {@code first} to {@code last} are wet (merged runs may hold dry stations), {@code from} and
	 * {@code to} are the dry bank stations on either side, {@code length} the measured wet extent in blocks, {@code deck} the flat deck height.
	 */
	public record Crossing(int first, int last, int from, int to, int length, int deck) {}

	/** {@code failure} is empty when the road is dry enough; {@code failStation} the station to steer round otherwise (-1: none). */
	public record Result(boolean ok, String failure, int failStation, boolean[] wet, int[] deck, boolean[] span, List<Crossing> crossings, long reads) {}

	private static boolean wetAt(TerrainProbe p, double x, double z) {
		return p.waterDepth(round(x), round(z)) >= 1;
	}

	static int round(double v) {
		return (int) StrictMath.floor(v + 0.5);
	}

	public static Result build(double[][] st, TerrainProbe exact) {
		int n = st.length;
		long[] reads = {0};
		TerrainProbe p = new TerrainProbe() {
			@Override public int floorY(int x, int z) { reads[0]++; return exact.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { reads[0]++; return exact.waterDepth(x, z); }
			@Override public int surfaceY(int x, int z) { reads[0] += 2; return exact.surfaceY(x, z); }
		};
		boolean[] wet = new boolean[n];
		int[] raw = new int[n];
		for (int k = 0; k < n; k++) {
			int x = round(st[k][0]), z = round(st[k][1]);
			int depth = p.waterDepth(x, z);
			wet[k] = depth >= 1;
			raw[k] = p.floorY(x, z) + depth;
		}
		boolean[] span = new boolean[n];
		int[] deck = new int[n];
		if (n < 2) return new Result(false, "too short", -1, wet, deck, span, List.of(), reads[0]);
		if (wet[0]) return new Result(false, "wet start", 0, wet, deck, span, List.of(), reads[0]);
		if (wet[n - 1]) return new Result(false, "wet end", n - 1, wet, deck, span, List.of(), reads[0]);

		// runs of wet stations, merged when closer than the minimum gap
		List<int[]> groups = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			if (!wet[k]) continue;
			int b = k;
			while (b + 1 < n && wet[b + 1]) b++;
			if (!groups.isEmpty() && (k - groups.get(groups.size() - 1)[1]) * Curve.STATION < MIN_CROSSING_GAP) groups.get(groups.size() - 1)[1] = b;
			else groups.add(new int[] {k, b});
			k = b;
		}
		List<int[]> measured = new ArrayList<>();
		for (int[] g : groups) {
			int a = g[0], b = g[1];
			int back = 0, fwd = 0;
			for (int t = 1; t < (int) Curve.STATION; t++) {
				double u = t / Curve.STATION;
				if (!wetAt(p, st[a][0] + (st[a - 1][0] - st[a][0]) * u, st[a][1] + (st[a - 1][1] - st[a][1]) * u)) break;
				back++;
			}
			for (int t = 1; t < (int) Curve.STATION; t++) {
				double u = t / Curve.STATION;
				if (!wetAt(p, st[b][0] + (st[b + 1][0] - st[b][0]) * u, st[b][1] + (st[b + 1][1] - st[b][1]) * u)) break;
				fwd++;
			}
			int length = (int) ((b - a) * Curve.STATION) + 1 + back + fwd;
			if (length > MAX_CROSSING) {
				return new Result(false, "wet run " + length, (a + b) / 2, wet, deck, span, List.of(), reads[0]);
			}
			measured.add(new int[] {a, b, length});
		}

		// deck: mean of the surface over +- WINDOW stations
		int w = 2 * WINDOW + 1;
		for (int k = 0; k < n; k++) {
			long sum = 0;
			for (int q = -WINDOW; q <= WINDOW; q++) sum += raw[Math.max(0, Math.min(n - 1, k + q))];
			deck[k] = (int) Math.floorDiv(sum + w / 2, w);
		}
		boolean[] pin = new boolean[n];
		List<Crossing> crossings = new ArrayList<>();
		for (int[] m : measured) {
			int a = m[0], b = m[1];
			int flat = Math.max(deck[a - 1], deck[b + 1]);
			for (int k = a; k <= b; k++) { deck[k] = flat; pin[k] = true; }
			for (int k = a - 1; k <= b + 1; k++) span[k] = true;
			crossings.add(new Crossing(a, b, a - 1, b + 1, m[2], flat));
		}
		for (int pass = 0; pass < 3; pass++) {
			for (int k = 1; k < n; k++) if (!pin[k]) deck[k] = Math.max(deck[k - 1] - MAX_STEP, Math.min(deck[k - 1] + MAX_STEP, deck[k]));
			for (int k = n - 2; k >= 0; k--) if (!pin[k]) deck[k] = Math.max(deck[k + 1] - MAX_STEP, Math.min(deck[k + 1] + MAX_STEP, deck[k]));
		}
		return new Result(true, "", -1, wet, deck, span, crossings, reads[0]);
	}
}
