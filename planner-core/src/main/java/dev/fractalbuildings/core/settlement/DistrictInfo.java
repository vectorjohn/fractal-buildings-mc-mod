package dev.fractalbuildings.core.settlement;

import java.util.Objects;

/** The tile a district plan covers (same numbers as its {@link DistrictSummary}). */
public record DistrictInfo(String id, int ix, int iz, int x, int z, int w, int d, int quarter, int density) {
	public DistrictInfo {
		Objects.requireNonNull(id);
		if (!id.equals(Ids.district(ix, iz))) throw new IllegalArgumentException("district id " + id + " does not match " + ix + "," + iz);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("district " + id + " " + w + "x" + d);
		if (density < 0 || density > 100) throw new IllegalArgumentException("district " + id + " density " + density);
	}

	public Rect rect() { return new Rect(x, z, w, d); }
}
