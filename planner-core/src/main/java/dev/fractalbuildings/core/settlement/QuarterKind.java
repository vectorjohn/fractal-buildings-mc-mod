package dev.fractalbuildings.core.settlement;

/** Character of a quarter, a terrain-aware field that does not move tile borders. JSON: the lower-case string shown. */
public enum QuarterKind {
	DOWNTOWN("downtown"), OLD_TOWN("old-town"), MARKET("market"), RESIDENTIAL("residential"), WORKSHOP("workshop"), WATERFRONT("waterfront"), PARK("park"), OUTSKIRTS("outskirts");

	public final String json;

	QuarterKind(String json) { this.json = json; }

	public static QuarterKind of(String s) {
		for (QuarterKind v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown QuarterKind '" + s + "'");
	}
}
