package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.RegionField;
import java.util.Optional;

/**
 * What a cell skeleton may ask about the region: the Voronoi geometry of the cells and which settlement a cell
 * belongs to. {@link FixedSiteView} serves the CLI's {@code --site} and tests; the region field's join rule
 * (MC1's {@code FieldRegionView}) is adapted to this interface where cities are planned from the world.
 */
public interface RegionView {
	RegionField field();

	/** The settlement the cell is a member of (as claimant or joined cell), if any. */
	Optional<SettlementSite> siteOf(Cell cell);
}
