package dev.fractalbuildings.core.settlement;

/** How a district tile is cut into lots: rectangular street blocks on a hashed grid, or lots packed along organic roads. */
/** The third layout, SUBDIVIDE (IRR1), cuts old-town tiles into different-size blocks whose streets and lanes meet in T junctions. */
public enum StreetLayout { GRID, FRONTAGE, SUBDIVIDE }
