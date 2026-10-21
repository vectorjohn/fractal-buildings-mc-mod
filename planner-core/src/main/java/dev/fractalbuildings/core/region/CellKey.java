package dev.fractalbuildings.core.region;

/** A region cell, ordered by (cz, cx) so every scan and list is deterministic. */
public record CellKey(int cx, int cz) implements Comparable<CellKey> {
	@Override
	public int compareTo(CellKey o) {
		int c = Integer.compare(cz, o.cz);
		return c != 0 ? c : Integer.compare(cx, o.cx);
	}
}
