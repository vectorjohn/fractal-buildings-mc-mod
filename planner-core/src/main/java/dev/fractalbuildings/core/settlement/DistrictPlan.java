package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.ir.Violation;
import java.util.List;
import java.util.Objects;

/**
 * One district tile's detail, JSON format {@code fb-district/1} (docs/IR.md "Settlement plans"): streets,
 * plazas and lots with briefs. {@code scene} is the relative path of its {@code fb-scene/1} file or null.
 * The reader rejects a lot outside the tile; the constructor only checks shapes, so tests can build a broken
 * plan for {@code SettlementChecks}.
 */
public record DistrictPlan(int genVersion, long seed, DistrictInfo district, List<Street> streets, List<Plaza> plazas, List<LotBrief> lots,
		String scene, List<Violation> violations) {
	public static final String FORMAT = "fb-district/1";

	public DistrictPlan {
		Objects.requireNonNull(district);
		streets = List.copyOf(streets);
		plazas = List.copyOf(plazas);
		lots = List.copyOf(lots);
		violations = List.copyOf(violations);
		SettlementPlan.unique("street", streets.stream().map(Street::id).toList());
		SettlementPlan.unique("plaza", plazas.stream().map(Plaza::id).toList());
		SettlementPlan.unique("lot", lots.stream().map(LotBrief::id).toList());
	}
}
