package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;

/**
 * A district tile that a settlement plans: which settlement and the tile's rectangle (its centre chunk is the structure start).
 *
 * @param site the settlement that owns the tile (the same value for every tile of one settlement)
 */
public record TileInfo(int ix, int iz, SettlementSite site, DistrictGrid.Tile tile) {
	public Tier tier() { return site.tier(); }

	public String settlementId() { return site.id(); }
}
