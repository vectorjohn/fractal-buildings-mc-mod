package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.ir.Role;

/**
 * Receives the cells of a piece: world position, abstract role, the chosen block id and its shape. Cells arrive in
 * (y, z, x) order. {@link Role#AIR} cells are explicit clears (a carved room, a road cut) and carry
 * {@code minecraft:air}.
 *
 * <p>The plan lists this interface under {@code core.world} (A2); it lives here because the raster is what calls it.
 */
@FunctionalInterface
public interface CellSink {
	void cell(int x, int y, int z, Role role, String blockId, Shape shape);
}
