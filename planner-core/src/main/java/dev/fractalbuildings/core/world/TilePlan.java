package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Landmark;
import java.util.List;

/**
 * The plan of one tile: streets, plazas and lots with their briefs (what {@code CityPlanner} gives the CLI for the district).
 *
 * @param plan the filled district plan; lot briefs are before the generator's fallbacks (see {@link BuildingPiece#brief})
 * @param landmarks landmarks that still reserve ground in the tile
 * @param notes what district planning dropped or moved
 * @param firstPlazaReached the first plaza is joined to a road that reaches the hub
 */
public record TilePlan(TileInfo info, dev.fractalbuildings.core.settlement.DistrictPlan plan, List<Landmark> landmarks, List<String> notes, boolean firstPlazaReached) {
	public TilePlan {
		landmarks = List.copyOf(landmarks);
		notes = List.copyOf(notes);
	}
}
