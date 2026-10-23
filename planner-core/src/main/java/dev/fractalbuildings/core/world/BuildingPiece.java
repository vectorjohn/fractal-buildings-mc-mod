package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.settlement.LotBrief;

/**
 * One generated building of a tile.
 *
 * @param ref {@code (BUILDING, ix, iz, lot index)}: the index is the lot's place in the tile plan's lots
 * @param brief the brief generated from (the house fallback's when the plan's type was refused)
 * @param lot the generated lot (carries air rights and the front street level)
 * @param box the world box the piece can touch: the lot rectangle and the shell's footprint (the air strip over the street included),
 *     from the lowest foundation to the top of the shell; {@code minY} is the slab level unless a foundation reaches lower
 * @param building the placed building; equal to what generating {@code brief} again gives (the planner may drop and rebuild it)
 */
public record BuildingPiece(PieceRef ref, LotBrief brief, Lot lot, Box3 box, PlacedBuilding building) {}
