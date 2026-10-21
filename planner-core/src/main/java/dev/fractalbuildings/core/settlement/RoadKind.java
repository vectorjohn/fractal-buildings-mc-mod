package dev.fractalbuildings.core.settlement;

/** Kind of road, widest first (a quay is the waterfront promenade, TR 5.3). JSON: the lower-case string shown. */
public enum RoadKind {
	ARTERIAL("arterial"), MAIN("main"), STREET("street"), QUAY("quay"), LANE("lane"), PATH("path");

	public final String json;

	RoadKind(String json) { this.json = json; }

	public static RoadKind of(String s) {
		for (RoadKind v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown RoadKind '" + s + "'");
	}
}
