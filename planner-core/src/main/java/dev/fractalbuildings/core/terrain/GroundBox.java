package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;

/**
 * A world-space box that reshapes ground. {@link Kind#role()} maps it to the scene's material role: ROAD and PLAZA are
 * surfaces, FILL is earth, CUT is air carving, DECK and PIER are bridge parts (role deck).
 */
public record GroundBox(Box3 box, Kind kind) {
	public enum Kind {
		ROAD(Role.ROAD), PLAZA(Role.PAVING), FILL(Role.GROUND), CUT(Role.AIR), DECK(Role.DECK), PIER(Role.DECK);

		private final Role role;

		Kind(Role role) { this.role = role; }

		/** The material role of boxes of this kind in a district scene. */
		public Role role() { return role; }
	}
}
