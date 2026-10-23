package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;

/** A settlement found by {@link WorldPlanner#nearest}: its site, and how far its centre is from the query point (blocks). */
public record SettlementInfo(SettlementSite site, double distance) {
	public Tier tier() { return site.tier(); }
}
