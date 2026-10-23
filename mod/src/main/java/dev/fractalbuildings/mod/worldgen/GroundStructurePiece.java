package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.world.PieceRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The streets, plazas and grading of one chunk of a tile. */
public final class GroundStructurePiece extends TilePiece {
	public GroundStructurePiece(PieceRef ref, BoundingBox box) {
		super(ModWorldgen.GROUND_PIECE, ref, box.minY(), box);
	}

	public GroundStructurePiece(CompoundTag tag) {
		super(ModWorldgen.GROUND_PIECE, tag);
	}
}
