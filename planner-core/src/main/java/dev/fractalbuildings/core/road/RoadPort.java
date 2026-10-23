package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.region.CellKey;

/**
 * Where one road link meets a settlement (ADR 0004 N3, N5): the lattice point just outside its rim (or, for a touching pair,
 * the pair's shared meeting point), the width of the road, and the settlement at the other end. The skeleton aims a required
 * exit at {@code (x, z)}.
 */
public record RoadPort(CellKey other, int x, int z, int width, boolean meeting) {}
