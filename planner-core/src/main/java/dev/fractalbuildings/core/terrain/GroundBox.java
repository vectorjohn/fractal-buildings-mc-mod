package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Box3;

/**
 * A world-space box that reshapes ground. Mapping to material roles belongs to the caller (P0's
 * scene roles): ROAD/PLAZA/DECK are surfaces, FILL is earth, CUT is air carving, PIER is a bridge
 * support.
 */
public record GroundBox(Box3 box, Kind kind) {
	public enum Kind { ROAD, PLAZA, FILL, CUT, DECK, PIER }
}
