package dev.fractalbuildings.core.region;

import java.util.List;

/**
 * A settlement that won the join rule: the owner cell, its centre and drawn radius, and every cell it
 * claimed (sorted by (cz, cx); includes the owner). Claimed cells are members and decide nothing alone.
 */
public record SettlementSite(CellKey owner, int centreX, int centreZ, int radius, List<CellKey> cells) {
	public boolean joined() {
		return cells.size() > 1;
	}
}
