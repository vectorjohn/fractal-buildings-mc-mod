package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.settlement.tiers.CityProgram;
import dev.fractalbuildings.core.settlement.tiers.HamletProgram;
import dev.fractalbuildings.core.settlement.tiers.MetropolisProgram;
import dev.fractalbuildings.core.settlement.tiers.TownProgram;
import dev.fractalbuildings.core.settlement.tiers.VillageProgram;

/** The program a tier uses: the one place that maps {@link Tier} to its {@link TierProgram} (D1 to D3 add their tiers here). */
public final class TierPrograms {
	private TierPrograms() {}

	private static final TierProgram HAMLET = new HamletProgram(), VILLAGE = new VillageProgram(), TOWN = new TownProgram(), CITY = new CityProgram(), METROPOLIS = new MetropolisProgram();

	private static final TierProgram CITY_AXIS = new CityProgram(false), METROPOLIS_AXIS = new MetropolisProgram(false);

	public static TierProgram of(Tier tier) { return of(tier, PlanOptions.DEFAULT); }

	/** The tier's program under the options; they only matter for a city or metropolis. */
	public static TierProgram of(Tier tier, PlanOptions o) {
		if (!o.diagonals()) {
			if (tier == Tier.CITY) return CITY_AXIS;
			if (tier == Tier.METROPOLIS) return METROPOLIS_AXIS;
		}
		return switch (tier) {
			case HAMLET -> HAMLET;
			case VILLAGE -> VILLAGE;
			case TOWN -> TOWN;
			case CITY -> CITY;
			case METROPOLIS -> METROPOLIS;
		};
	}
}
