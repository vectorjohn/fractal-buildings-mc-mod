package dev.fractalbuildings.core.settlement;

/** A gate: where a main road crosses a cell border, a wall, or leaves the settlement. JSON: the lower-case string shown. */
public enum GateKind {
	CELL("cell"), WALL("wall"), EXIT("exit");

	public final String json;

	GateKind(String json) { this.json = json; }

	public static GateKind of(String s) {
		for (GateKind v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown GateKind '" + s + "'");
	}
}
