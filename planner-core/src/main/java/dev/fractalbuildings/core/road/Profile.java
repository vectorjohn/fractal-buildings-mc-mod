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

	/** The surface averaged over +- {@link #WINDOW} stations (ends clamped), rounded. */
	static int[] mean(int[] raw) {
		int n = raw.length, w = 2 * WINDOW + 1;
		int[] deck = new int[n];
		for (int k = 0; k < n; k++) {
			long sum = 0;
			for (int q = -WINDOW; q <= WINDOW; q++) sum += raw[Math.max(0, Math.min(n - 1, k + q))];
			deck[k] = (int) Math.floorDiv(sum + w / 2, w);
		}
		return deck;
	}

	/** A wet run longer than this is never built as an approach deck, crossing or not (a road running along a lake, John's road-0 case; ROADJOIN, architect 2026-10-27). */
	public static final int MAX_DECK_RUN = 100;

	/**
	 * {@link #mean} with the window shrunk near a pinned station: a station {@code d} stations from a pin averages over +- min(d, WINDOW),
	 * so next to a pinned street the deck follows the ground there instead of the far-away average (no embankment beside the gate).
	 */
	static int[] meanNear(int[] raw, java.util.Set<Integer> pins) {
		int n = raw.length;
		int[] deck = new int[n];
		for (int k = 0; k < n; k++) {
			int half = WINDOW;
			for (int p : pins) half = Math.min(half, Math.abs(p - k));
			long sum = 0;
			for (int q = -half; q <= half; q++) sum += raw[Math.max(0, Math.min(n - 1, k + q))];
			deck[k] = (int) Math.floorDiv(sum + half, 2L * half + 1);
		}
		return deck;
	}

	/**
	 * Pins further apart in height than {@link #MAX_STEP} per station allows cannot all hold. The earlier pin (the street at the exit)
	 * wins; the later one (the graph road's deck at the port) is moved to the grade limit from it, so the approach meets the graph road
	 * with exactly the infeasible remainder as a step, no more (ROADJOIN, verifier). Returns the total remainder in blocks; the port end
	 * is where the lower-ranked road takes it up.
	 */
	static int clampPins(int[] deck, boolean[] pin, int[] raw) {
		int prev = -1, excess = 0;
		for (int k = 0; k < deck.length; k++) {
			if (!pin[k]) continue;
			if (prev >= 0) {
				int allowed = MAX_STEP * (k - prev);
				int d = deck[k] - deck[prev];
				if (d > allowed) { excess += d - allowed; deck[k] = deck[prev] + allowed; }
				else if (-d > allowed) { excess += -d - allowed; deck[k] = deck[prev] - allowed; }
			}
			prev = k;
		}
		return excess;
	}

	/** How far the water reaches sideways from a station, per side, before a dry column (capped). */
	private static final int SIDE_CAP = 40, ALONG_NEAR = 8;

	/** Water this deep or more is water for the along test; less (puddles, marsh fringe) is not ({@code CoarseProbe.water}'s own threshold). */
	static final int ALONG_DEEP = 2;

	/** Clause (c) of the along test (architect, DS-19 interim): the longest stretch of water down the centre line is at most this times the water's reach across the road. */
	static final double ANGLE = 1.5;

	/** What {@link #alongMeasure} read for one wet run (tests and reviewers read it; {@link #alongBank} is its verdict). */
	public record Along(int span, int centreWater, int deep, int near, int longest, int reach) {
		public boolean clauseA() { return centreWater * 2 >= span; }
		public boolean clauseB() { return deep > 0 && near * 2 <= deep; }
		public boolean clauseC() { return reach > 0 && longest <= ANGLE * reach; }
	}

	/**
	 * Whether a long wet run lies along the water rather than across it (DECKDIAG F2, John 2026-11-01 "a run along water is never built";
	 * the architect's revised DS-19 test (3)). Water under {@link #ALONG_DEEP} deep does not count for the stations of (b) (a puddle or marsh fringe is no crossing); the centre line of (a) and (c) counts any water, as DECKDIAG measured it. The run is a crossing when all of:
	 * (a) water lies under at least half of its centre line; (b) at the stations whose own column is deep water, the water reaches past
	 * {@link #ALONG_NEAR} blocks on both sides at least half the time, and there is one such station; (c) the longest unbroken stretch of water
	 * under the centre line is at most {@link #ANGLE} times the water's reach across the road (left plus right, each to {@link #SIDE_CAP}) at the
	 * middle of that stretch: the road crosses at about 35 degrees or more instead of running down the water. {@code lo} and {@code hi} are the
	 * run's first and last wet arc positions ({@code Curve.STATION * k + s}). Exact reads only.
	 */
	static boolean alongBank(double[][] st, int a, int b, int lo, int hi, TerrainProbe p) {
		Along m = alongMeasure(st, a, b, lo, hi, p);
		return !(m.clauseA() && m.clauseB() && m.clauseC());
	}

	public static Along alongMeasure(double[][] st, int a, int b, int lo, int hi, TerrainProbe p) {
		int n = st.length, deep = 0, near = 0;
		for (int k = a; k <= b; k++) {
			if (p.waterDepth(round(st[k][0]), round(st[k][1])) < ALONG_DEEP) continue;
			double[] side = side(st, k);
			if (side == null) continue;
			int left = 0, right = 0;
			while (left < SIDE_CAP && wetAt(p, st[k][0] + side[0] * (left + 1), st[k][1] + side[1] * (left + 1))) left++;
			while (right < SIDE_CAP && wetAt(p, st[k][0] - side[0] * (right + 1), st[k][1] - side[1] * (right + 1))) right++;
			deep++;
			if (Math.min(left, right) <= ALONG_NEAR) near++;
		}
		// the centre line, one sample per block of arc from lo to hi
		int span = hi - lo + 1, centre = 0, run = 0, longest = 0, longestEnd = -1;
		for (int t = lo; t <= hi; t++) {
			double fk = Math.max(0, Math.min(n - 1, t / Curve.STATION)), f = fk - Math.floor(fk);
			int k0 = (int) Math.floor(fk), k1 = Math.min(n - 1, k0 + 1);
			double x = st[k0][0] + (st[k1][0] - st[k0][0]) * f, z = st[k0][1] + (st[k1][1] - st[k0][1]) * f;
			if (p.waterDepth(round(x), round(z)) >= 1) {
				centre++;
				if (++run > longest) { longest = run; longestEnd = t; }
			} else run = 0;
		}
		int reach = 0;
		if (longest > 0) {
			// the water's reach across the road at the middle of the longest stretch
			double fk = Math.max(0, Math.min(n - 1, (longestEnd - longest / 2) / Curve.STATION)), f = fk - Math.floor(fk);
			int k0 = (int) Math.floor(fk), k1 = Math.min(n - 1, k0 + 1);
			double x = st[k0][0] + (st[k1][0] - st[k0][0]) * f, z = st[k0][1] + (st[k1][1] - st[k0][1]) * f;
			double[] side = side(st, Math.round((float) fk));
			if (side != null) {
				int left = 0, right = 0;
				while (left < SIDE_CAP && wetAt(p, x + side[0] * (left + 1), z + side[1] * (left + 1))) left++;
				while (right < SIDE_CAP && wetAt(p, x - side[0] * (right + 1), z - side[1] * (right + 1))) right++;
				reach = left + right;
			}
		}
		return new Along(span, centre, deep, near, longest, reach);
	}

	/** The unit left normal of the road at station k, or null when it has no direction. */
	private static double[] side(double[][] st, int k) {
		int n = st.length;
		double[] u = st[Math.max(0, k - 1)], v = st[Math.min(n - 1, k + 1)];
		double tx = v[0] - u[0], tz = v[1] - u[1], tl = Math.sqrt(tx * tx + tz * tz);
		return tl == 0 ? null : new double[] {-tz / tl, tx / tl};
	}

	/** Limits the change between neighbouring stations to {@link #MAX_STEP}, leaving pinned stations as they are. */
	static void limit(int[] deck, boolean[] pin) {
		int n = deck.length;
		for (int pass = 0; pass < 3; pass++) {
			for (int k = 1; k < n; k++) if (!pin[k]) deck[k] = Math.max(deck[k - 1] - MAX_STEP, Math.min(deck[k - 1] + MAX_STEP, deck[k]));
			for (int k = n - 2; k >= 0; k--) if (!pin[k]) deck[k] = Math.max(deck[k + 1] - MAX_STEP, Math.min(deck[k + 1] + MAX_STEP, deck[k]));
		}
	}

	/**
	 * The deck of a road that is not checked for water (a settlement approach): the averaged surface at the stations, with the last
	 * station pinned to {@code endDeck} when given, so the road meets the one it joins. Exact reads at the stations only.
	 */
	public static int[] approachDeck(double[][] st, TerrainProbe exact, java.util.OptionalInt endDeck) {
		java.util.TreeMap<Integer, Integer> pins = new java.util.TreeMap<>();
		if (endDeck.isPresent() && st.length > 0) pins.put(st.length - 1, endDeck.getAsInt());
		return approachDeck(st, exact, pins);
	}

	/**
	 * As above with any number of pinned stations (station index to deck): the street levels at the exit end (ROADJOIN, the surfaces
	 * meet) and the road's deck at the port end. Pinned stations keep their level; the rest is averaged and limited to {@link #MAX_STEP}.
	 */
	public static int[] approachDeck(double[][] st, TerrainProbe exact, java.util.SortedMap<Integer, Integer> pins) {
		int n = st.length;
		int[] raw = new int[n];
		for (int k = 0; k < n; k++) raw[k] = exact.surfaceY(round(st[k][0]), round(st[k][1]));
		int[] deck = meanNear(raw, pins.keySet());
		boolean[] pin = new boolean[n];
		for (var e : pins.entrySet()) {
			if (e.getKey() < 0 || e.getKey() >= n) continue;
			deck[e.getKey()] = e.getValue();
			pin[e.getKey()] = true;
		}
		limit(deck, pin);
		return deck;
	}

	/** Neighbouring stations whose decks differ by more than {@link #MAX_STEP}: pins that {@link #limit} could not join. */
	public static int steepJoins(int[] deck) {
		int c = 0;
		for (int k = 0; k + 1 < deck.length; k++) if (Math.abs(deck[k] - deck[k + 1]) > MAX_STEP) c++;
		return c;
	}

	/** Spans longer than this get a block of air under the deck (architect, ROADJOIN: it reads as a bridge and a boat passes). */
	public static final int HIGH_SPAN = 6;

	/**
	 * An approach's profile: {@code deck}, the {@code span} flags for the raster (crossing columns are DECK), the {@code crossings}, and
	 * {@code wetFailures}, the crossings longer than {@link #MAX_CROSSING} (kept, never filled: WR3), and {@code steepJoins}.
	 */
	public record ApproachResult(int[] deck, boolean[] span, List<Crossing> crossings, int wetFailures, int steepJoins, int longRuns, int alongRuns, int portExcess) {}

	/**
	 * The profile of an approach with the water rule (ADR 0004 N5, WR1 to WR3): wet stations are grouped as in {@link #build} (an exit or
	 * port on a shore is a crossing end, not a failure), the deck is the pinned/limited mean as {@link #approachDeck}, and each crossing
	 * is flat at the higher bank's deck but at least one above the water ({@code deck >= surface + 1}; two when the span is over
	 * {@link #HIGH_SPAN}), so the deck never lies at water level. A pinned station inside a crossing keeps its level and sets the flat
	 * height. {@code airUnder} (town tier and above, architect 2026-10-27) gives one air block under the deck whatever the span. The ramp up to a raised crossing is {@link #limit}ed at 1 in 4 on the dry stations beside it.
	 */
	public static ApproachResult approach(double[][] st, TerrainProbe exact, java.util.SortedMap<Integer, Integer> pins, int width, boolean airUnder) {
		return approach(st, exact, pins, width, airUnder, MAX_DECK_RUN);
	}

	/**
	 * As above with the longest wet run that may be decked ({@code maxRun}; a longer run counts in {@code longRuns} and the approach is dropped
	 * or rerouted). LONGDECK (John 2026-11-02): towns and up deck up to {@link #MAX_DECK_RUN}; hamlets and villages only {@link #MAX_CROSSING}.
	 */
	public static ApproachResult approach(double[][] st, TerrainProbe exact, java.util.SortedMap<Integer, Integer> pins, int width, boolean airUnder, int maxRun) {
		int n = st.length;
		TerrainProbe p = exact;
		boolean[] wet = new boolean[n];
		int[] raw = new int[n], surf = new int[n], firstArc = new int[n], lastArc = new int[n];
		double hw = width / 2.0;
		int reach = (int) Curve.STATION;
		for (int k = 0; k < n; k++) {
			int x = round(st[k][0]), z = round(st[k][1]);
			// a station is wet when water lies under the road's own footprint (every block of its width) within half a station along the road (DECKDIAG F1:
			// the stations tile the road); a stream between two stations is still covered, by the stations either side of it. The pinned deck then covers
			// the water, and the ramp starts on dry stations beyond it
			double[] a = st[Math.max(0, k - 1)], b = st[Math.min(n - 1, k + 1)];
			double tx = b[0] - a[0], tz = b[1] - a[1], tl = Math.sqrt(tx * tx + tz * tz);
			if (tl == 0) { tx = 1; tz = 0; tl = 1; }
			double nx = -tz / tl, nz = tx / tl;
			int water = Integer.MIN_VALUE;
			firstArc[k] = Integer.MAX_VALUE;
			lastArc[k] = Integer.MIN_VALUE;
			for (int s = -reach / 2; s < reach / 2; s++) {
				if (k == 0 && s < 0 || k == n - 1 && s > 0) continue;
				for (int o = -(int) Math.floor(hw); o <= (int) Math.floor(hw); o++) {
					double cx = st[k][0] + tx / tl * s + nx * o, cz = st[k][1] + tz / tl * s + nz * o;
					int px = round(cx), pz = round(cz);
					int depth = p.waterDepth(px, pz);
					if (depth < 1) continue;
					water = Math.max(water, p.floorY(px, pz) + depth);
					firstArc[k] = Math.min(firstArc[k], reach * k + s);
					lastArc[k] = Math.max(lastArc[k], reach * k + s);
				}
			}
			wet[k] = water != Integer.MIN_VALUE;
			surf[k] = wet[k] ? water : p.floorY(x, z) + p.waterDepth(x, z);
			raw[k] = wet[k] ? surf[k] : p.surfaceY(x, z);
		}
		int[] deck = meanNear(raw, pins.keySet());
		boolean[] pin = new boolean[n];
		boolean[] span = new boolean[n];
		for (var e : pins.entrySet()) {
			if (e.getKey() < 0 || e.getKey() >= n) continue;
			deck[e.getKey()] = e.getValue();
			pin[e.getKey()] = true;
		}
		int portExcess = clampPins(deck, pin, raw);
		List<int[]> groups = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			if (!wet[k]) continue;
			int b = k;
			while (b + 1 < n && wet[b + 1]) b++;
			if (!groups.isEmpty() && (k - groups.get(groups.size() - 1)[1]) * Curve.STATION < MIN_CROSSING_GAP) groups.get(groups.size() - 1)[1] = b;
			else groups.add(new int[] {k, b});
			k = b;
		}
		List<Crossing> crossings = new ArrayList<>();
		int failures = 0, longRuns = 0, alongRuns = 0;
		for (int[] g : groups) {
			int a = g[0], b = g[1];
			int length = 0;
			int runLo = 0, runHi = 0;
			{
				int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
				for (int k = a; k <= b; k++) { lo = Math.min(lo, firstArc[k]); hi = Math.max(hi, lastArc[k]); }
				length = hi - lo + 1;
				runLo = lo;
				runHi = hi;
			}
			if (length > MAX_CROSSING) failures++;
			if (length > maxRun) longRuns++;
			else if (length > MAX_CROSSING && alongBank(st, a, b, runLo, runHi, p)) alongRuns++;
			int flat = Integer.MIN_VALUE;
			if (a > 0) flat = Math.max(flat, deck[a - 1]);
			if (b + 1 < n) flat = Math.max(flat, deck[b + 1]);
			int water = Integer.MIN_VALUE;
			for (int k = a; k <= b; k++) if (wet[k]) water = Math.max(water, surf[k]);
			flat = Math.max(flat, water + 1 + (length > HIGH_SPAN || airUnder ? 1 : 0));
			// a street pinned at or below the water at the exit is lifted to the floor (the join steps up by at most the floor's height and ramps at 1 in 4); the port end is the graph road's contract and stays
			int floor = water + 1 + (length > HIGH_SPAN || airUnder ? 1 : 0);
			for (int k = a; k <= b; k++) if (pin[k] && k != n - 1 && deck[k] < floor && floor - deck[k] <= MAX_STEP) deck[k] = floor; // a bigger gap is a stream on a slope, not a pond at the gate: the street stays
			int pinLo = Integer.MAX_VALUE, pinHi = Integer.MIN_VALUE;
			for (int k = a; k <= b; k++) if (pin[k]) { pinLo = Math.min(pinLo, deck[k]); pinHi = Math.max(pinHi, deck[k]); }
			if (pinHi != pinLo && pinHi != Integer.MIN_VALUE) {
				// pins at different levels inside one wet run (the street at the exit, the road at the port): no flat deck; each wet station sits above its own water and the pins' ramp joins them
				for (int k = a; k <= b; k++) if (!pin[k]) deck[k] = Math.max(deck[k], (wet[k] ? surf[k] : raw[k]) + 1 + (length > HIGH_SPAN || airUnder ? 1 : 0));
				failures++;
				for (int k = Math.max(0, a - 1); k <= Math.min(n - 1, b + 1); k++) span[k] = true;
				crossings.add(new Crossing(a, b, Math.max(0, a - 1), Math.min(n - 1, b + 1), length, deck[a]));
				continue;
			}
			for (int k = a; k <= b; k++) if (pin[k]) { flat = deck[k]; break; }
			for (int k = a; k <= b; k++) { deck[k] = flat; pin[k] = true; }
			for (int k = Math.max(0, a - 1); k <= Math.min(n - 1, b + 1); k++) span[k] = true;
			crossings.add(new Crossing(a, b, Math.max(0, a - 1), Math.min(n - 1, b + 1), length, flat));
		}
		limit(deck, pin);
		return new ApproachResult(deck, span, crossings, failures, steepJoins(deck), longRuns, alongRuns, portExcess);
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
		deck = mean(raw);
		boolean[] pin = new boolean[n];
		List<Crossing> crossings = new ArrayList<>();
		for (int[] m : measured) {
			int a = m[0], b = m[1];
			int flat = Math.max(deck[a - 1], deck[b + 1]);
			for (int k = a; k <= b; k++) { deck[k] = flat; pin[k] = true; }
			for (int k = a - 1; k <= b + 1; k++) span[k] = true;
			crossings.add(new Crossing(a, b, a - 1, b + 1, m[2], flat));
		}
		limit(deck, pin);
		return new Result(true, "", -1, wet, deck, span, crossings, reads[0]);
	}
}
