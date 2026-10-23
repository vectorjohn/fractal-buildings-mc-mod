package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.world.PieceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One building of a tile (the plan's lot index in the reference). */
public final class BuildingStructurePiece extends TilePiece {
	public BuildingStructurePiece(PieceRef ref, int slabY, BoundingBox box) {
		super(ModWorldgen.BUILDING_PIECE, ref, slabY, box);
	}

	public BuildingStructurePiece(CompoundTag tag) {
		super(ModWorldgen.BUILDING_PIECE, tag);
	}
}
