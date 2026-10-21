package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/**
 * One settlement: its tier, centre, radius, the cell that claimed it and every cell it spans.
 * {@code cells} holds at least the claimant (more once big settlements join cells, MC1).
 */
public record SettlementSite(String id, Tier tier, Pt centre, int radius, Cell claimant, List<Cell> cells) {
	public SettlementSite {
		Objects.requireNonNull(id);
		Objects.requireNonNull(tier);
		Objects.requireNonNull(centre);
		Objects.requireNonNull(claimant);
		cells = List.copyOf(cells);
		if (id.isEmpty()) throw new IllegalArgumentException("site id is empty");
		if (radius <= 0) throw new IllegalArgumentException("site radius " + radius);
		if (!cells.contains(claimant)) throw new IllegalArgumentException("site cells must include the claimant");
	}
}
