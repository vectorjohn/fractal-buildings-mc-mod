package dev.fractalbuildings.core.world;

/**
 * The identity of one piece of a tile, small enough to save in a structure piece's NBT: the tile, what kind of piece, and which one
 * (the lot index in the tile plan for a building, the chunk's index along the tile's chunk rows for ground). A piece is rebuilt from
 * its ref through the planner, so the ref plus the world seed and config reproduce it.
 */
public record PieceRef(Kind kind, int ix, int iz, int index) {
	public enum Kind { BUILDING, GROUND }

	public static PieceRef building(int ix, int iz, int lotIndex) { return new PieceRef(Kind.BUILDING, ix, iz, lotIndex); }

	public static PieceRef ground(int ix, int iz, int chunkIndex) { return new PieceRef(Kind.GROUND, ix, iz, chunkIndex); }
}
