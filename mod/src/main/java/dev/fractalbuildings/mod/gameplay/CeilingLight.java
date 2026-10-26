package dev.fractalbuildings.mod.gameplay;

/**
 * Geometry of a hung ceiling light (DS-24): a lantern on an iron chain under the ceiling. {@code clear} is the number of air cells from the
 * hook's cell up to the ceiling block, so the ceiling is {@code clear} above the hook and the highest air cell is {@code clear - 1}.
 * The chain is {@code max(0, clear - 3)} blocks long, the lantern hangs below it, so for {@code clear >= 3} the lantern is at hook y + 2
 * (head height of a 3-clear room, one above a standing player's head in a taller one); with 2 clear there is no room for a chain and the
 * lantern hangs directly under the ceiling at y + 1. Everything stays in the hook's column.
 */
final class CeilingLight {
	private CeilingLight() {}

	/** Chain blocks below the ceiling. */
	static int chain(int clear) { return Math.max(0, clear - 3); }

	/** Height of the lantern above the hook's cell. */
	static int lantern(int clear) { return clear - 1 - chain(clear); }

	/** True if a lantern can hang under a ceiling {@code clear} above the hook (at least 2 air cells). */
	static boolean fits(int clear) { return clear >= 2; }
}
