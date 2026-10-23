package dev.fractalbuildings.mod.api;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldHooks;
import dev.fractalbuildings.core.world.WorldHooks.WorldHook;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Read-only view of the settlements for other mods (a villager-AI mod, a map mod). No behaviour: it answers what the planner says is at a
 * position. Safe to call from the server thread; it may plan the tile (a few hundred ms the first time) but never generates chunks.
 */
public final class Settlements {
	private Settlements() {}

	/**
	 * What is at a block column.
	 *
	 * @param lotId the lot containing the column, or null when it is on a street, plaza or outside any lot
	 * @param buildingType type of the building on that lot, or null
	 * @param hooks the building's furnishing hooks in world coordinates (empty without a building)
	 */
	public record At(String settlementId, Tier tier, int tileX, int tileZ, String lotId, String buildingType, String style, List<WorldHook> hooks) {}

	public static Optional<At> at(ServerLevel level, BlockPos pos) {
		Optional<WorldPlanner> planner = WorldPlanners.of(level, level.getChunkSource().getGenerator());
		if (planner.isEmpty()) return Optional.empty();
		return at(planner.get(), pos.getX(), pos.getZ());
	}

	/** The same, on a planner (tests). */
	public static Optional<At> at(WorldPlanner w, int x, int z) {
		DistrictGrid.Tile t = w.grid().tileAt(x, z);
		Optional<TileInfo> info = w.tile(t.ix(), t.iz());
		if (info.isEmpty()) return Optional.empty();
		for (BuildingPiece b : w.buildings(t.ix(), t.iz())) {
			if (b.brief().rect().contains(x, z)) {
				return Optional.of(new At(info.get().settlementId(), info.get().tier(), t.ix(), t.iz(), b.brief().id(), b.brief().type(), b.brief().style(), WorldHooks.of(b)));
			}
		}
		return Optional.of(new At(info.get().settlementId(), info.get().tier(), t.ix(), t.iz(), null, null, null, List.of()));
	}
}
