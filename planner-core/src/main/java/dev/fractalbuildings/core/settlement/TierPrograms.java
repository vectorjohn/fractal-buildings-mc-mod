package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.settlement.tiers.HamletProgram;
import dev.fractalbuildings.core.settlement.tiers.TownProgram;
import dev.fractalbuildings.core.settlement.tiers.VillageProgram;

/** The program a tier uses: the one place that maps {@link Tier} to its {@link TierProgram} (D1 to D3 add their tiers here). */
public final class TierPrograms {
	private TierPrograms() {}

	private static final TierProgram HAMLET = new HamletProgram(), VILLAGE = new VillageProgram(), TOWN = new TownProgram(), DEFAULT = new DefaultProgram();

	public static TierProgram of(Tier tier) {
		return switch (tier) {
			case HAMLET -> HAMLET;
			case VILLAGE -> VILLAGE;
			case TOWN -> TOWN;
			default -> DEFAULT;
		};
	}
}
