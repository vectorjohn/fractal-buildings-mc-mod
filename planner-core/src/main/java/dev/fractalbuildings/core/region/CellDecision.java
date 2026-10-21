package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.settlement.Tier;

import java.util.Map;

/**
 * Everything a region cell decides, from (seed, cell, probe) alone. {@code radiusDrawn} is the tier's
 * draw, {@code radius} what the grow policy left of it (clamped to fit the cell).
 */
public record CellDecision(
	int cx, int cz, int siteX, int siteZ,
	boolean settled, String reason, Tier tier,
	int centreX, int centreZ, int radiusDrawn, int radius, int radiusFit, int edgeDist,
	double roll, double chance, double suitability, double siteSuitability, int candidate,
	double waterFrac, double peakFrac, Map<String, Double> groups) {

	/** True when two palette groups each cover at least a fifth of the footprint. */
	public boolean straddles() {
		int n = 0;
		for (double f : groups.values()) if (f >= 0.2) n++;
		return n >= 2;
	}

	public boolean fits() {
		return radius <= radiusFit;
	}

	/** The same decision with another radius (a join winner takes its drawn radius, past the cell's fit). */
	public CellDecision withRadius(int r) {
		return new CellDecision(cx, cz, siteX, siteZ, settled, reason, tier, centreX, centreZ, radiusDrawn, r, radiusFit, edgeDist,
			roll, chance, suitability, siteSuitability, candidate, waterFrac, peakFrac, groups);
	}
}
