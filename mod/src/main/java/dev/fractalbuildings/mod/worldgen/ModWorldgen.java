package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.mod.config.WorldConfigData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.Level;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Registers what districts need: the structure types (buildings by tier, ground), the piece types and the placement
 * ({@code fractalbuildings:districts}). The data (structures, sets, tags) is in {@code data/fractalbuildings/worldgen}.
 */
public final class ModWorldgen {
	public static StructureType<DistrictStructure> DISTRICT_TYPE;
	public static StructureType<GroundStructure> GROUND_TYPE;
	public static StructurePieceType BUILDING_PIECE;
	public static StructurePieceType GROUND_PIECE;

	private ModWorldgen() {}

	public static void register() {
		DISTRICT_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("district"), (StructureType<DistrictStructure>) () -> DistrictStructure.CODEC);
		GROUND_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("district_ground"), (StructureType<GroundStructure>) () -> GroundStructure.CODEC);
		BUILDING_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("building"), (StructurePieceType.ContextlessType) BuildingStructurePiece::new);
		GROUND_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("ground"), (StructurePieceType.ContextlessType) GroundStructurePiece::new);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("districts"), DistrictPlacement.CODEC);
		// the planner reads the world's pinned settings (B6): the saved data of the overworld, created from the config file on a new world
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) WorldPlanners.pin(WorldConfigData.get(server).settings().world());
		});
		// planners hold their level's RandomState; let them go with the server
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WorldPlanners.clear());
	}

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("fractalbuildings", path); }
}
