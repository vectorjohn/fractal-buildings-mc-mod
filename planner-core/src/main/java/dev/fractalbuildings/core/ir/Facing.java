package dev.fractalbuildings.core.ir;

/** Horizontal direction, Minecraft convention: north is -z, east is +x. JSON: lower case name. */
public enum Facing {
	NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

	public final int dx, dz;

	Facing(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }

	/** Turned clockwise (seen from above) by {@code quarterTurns} quarter turns. */
	public Facing rotate(int quarterTurns) { return values()[Math.floorMod(ordinal() + quarterTurns, 4)]; }
}
