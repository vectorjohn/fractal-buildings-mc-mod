package dev.fractalbuildings.core.settlement;

/** Settlement size tier (docs/plans/phase5.md "Tier ladder"). JSON: the lower-case string shown. */
public enum Tier {
	HAMLET("hamlet"), VILLAGE("village"), TOWN("town"), CITY("city"), METROPOLIS("metropolis");

	public final String json;

	Tier(String json) { this.json = json; }

	public static Tier of(String s) {
		for (Tier v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown Tier '" + s + "'");
	}
}
