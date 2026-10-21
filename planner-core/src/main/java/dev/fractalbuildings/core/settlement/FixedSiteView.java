package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.RegionField;
import java.util.Objects;
import java.util.Optional;

/** A view with exactly one settlement: its cells belong to it, every other cell to nothing. */
public final class FixedSiteView implements RegionView {
	private final SettlementSite site;
	private final RegionField field;

	public FixedSiteView(SettlementSite site, RegionField field) {
		this.site = Objects.requireNonNull(site);
		this.field = Objects.requireNonNull(field);
	}

	@Override public RegionField field() { return field; }

	@Override public Optional<SettlementSite> siteOf(Cell cell) { return site.cells().contains(cell) ? Optional.of(site) : Optional.empty(); }

	public SettlementSite site() { return site; }
}
