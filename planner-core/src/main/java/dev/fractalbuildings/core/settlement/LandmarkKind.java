package dev.fractalbuildings.core.settlement;

/** A landmark placed by the skeleton, not by the ClassTable. JSON: the lower-case string shown. */
public enum LandmarkKind {
	SQUARE("square"), MARKET("market"), GREEN("green"), PARK("park"), TEMPLE("temple"), HALL("hall"), TOWER("tower");

	public final String json;

	LandmarkKind(String json) { this.json = json; }

	public static LandmarkKind of(String s) {
		for (LandmarkKind v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown LandmarkKind '" + s + "'");
	}
}
