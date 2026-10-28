package dev.fractalbuildings.mod.ordersim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** ORDERSIM S3: what one scenario in one order produced, and the checks C1 to C6 on it. Plain fields; filled by {@link OrderSim} and {@link SimCompare}. */
public final class SimRun {
	public final String scenario, order;
	public long ms;

	// C1: no exception, no zone violation by our code
	public final List<String> errors = new ArrayList<>();
	public int oursZoneViolations, featureZoneViolations;
	public final SimFeatures.Stats features = new SimFeatures.Stats();
	public int promoted;

	// C2: the ledger against the reference raster (inner window)
	public int ledgerCells, refCells, missing, extra, wrong, duplicates;
	public String ledgerDigest = "";
	public boolean digestDiffers;

	// SC4 roads: the road feature's claimed cells (set apart from the reference raster: their reference is the ledger itself, which must not depend on the order)
	public int roadCells, roadSolid, roadBad, roadUnknown;
	public String roadDigest = "";
	public boolean roadDigestDiffers;
	/** The road feature's claimed cells inside the inner window with their state ids (key order). */
	public java.util.Map<Long, Integer> roadStates = java.util.Map.of();

	// C3: the world against the reference raster in the claimed cells
	public int solid, solidBad, solidUnknown, air, airBad, airUnknown;
	public final TreeMap<String, Integer> histogram = new TreeMap<>();
	public final List<String> badCells = new ArrayList<>();

	// C4: against the baseline order (-1: this is the baseline)
	public int crossDiff = -1;
	public final List<String> diffCells = new ArrayList<>();

	// C5: write contracts (-1: not checked in this run)
	public int contractViolations = -1;
	public final List<String> contractDetails = new ArrayList<>();
	/** Writes of our code by {@code Class.method} (audit runs only): which branches fired. */
	public final Map<String, Integer> branchWrites = new TreeMap<>();

	// C6: a second sweep of our top-layer step changes nothing (-1: not checked)
	public int idempotenceChanges = -1;

	/** State id at each of the plan's inner reference keys, after the order finished. */
	int[] have;
	SimWorld world;

	public SimRun(String scenario, String order) { this.scenario = scenario; this.order = order; }

	/** Checks that failed in this run, e.g. {@code "C3 unknown solid 4"}. {@code strictC4}: the scenario is {@code spaced}. */
	public List<String> failed(boolean strictC4) {
		List<String> f = new ArrayList<>();
		if (!errors.isEmpty()) f.add("C1 " + errors.size() + " error(s): " + errors.get(0));
		if (oursZoneViolations > 0) f.add("C1 " + oursZoneViolations + " zone violation(s) by our code");
		if (missing > 0 || extra > 0 || wrong > 0) f.add("C2 ledger against raster: missing " + missing + " extra " + extra + " wrong " + wrong);
		if (duplicates > 0) f.add("C2 " + duplicates + " duplicate ledger cell(s)");
		if (roadDigestDiffers) f.add("C2 road digest differs from the baseline order");
		if (roadUnknown > 0) f.add("C3 " + roadUnknown + " unknown road cell(s)");
		if (digestDiffers) f.add("C2 ledger digest differs from the baseline order");
		if (solidUnknown > 0) f.add("C3 " + solidUnknown + " unknown solid cell(s)");
		if (airUnknown > 0) f.add("C3 " + airUnknown + " unknown air cell(s)");
		if (strictC4 && crossDiff > 0) f.add("C4 " + crossDiff + " claimed cell(s) differ from the baseline order");
		if (contractViolations > 0) f.add("C5 " + contractViolations + " write contract violation(s): " + contractDetails.get(0));
		if (idempotenceChanges > 0) f.add("C6 a second sweep changed " + idempotenceChanges + " claimed cell(s)");
		return f;
	}
}
