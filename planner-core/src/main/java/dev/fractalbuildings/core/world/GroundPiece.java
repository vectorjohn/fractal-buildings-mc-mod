package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import java.util.List;

/**
 * The street and plaza grading of one chunk of a tile: road surface, cuts, fill, bridge decks and piers, as role boxes in world
 * coordinates. Every box lies inside the chunk and inside the tile (a road on a tile border is split along it: each tile builds its
 * own half). Boxes apply in list order; a later box overrides an earlier one.
 *
 * @param box the bounding box of {@code boxes}
 */
public record GroundPiece(PieceRef ref, int chunkX, int chunkZ, Box3 box, List<Box> boxes) {
	public GroundPiece { boxes = List.copyOf(boxes); }
}
