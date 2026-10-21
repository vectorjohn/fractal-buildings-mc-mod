package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/**
 * A district tile as the settlement lists it. Tiles are chunk aligned ({@code x}, {@code z}, {@code w}, {@code d}
 * multiples of 16); {@code origin} is the chunk of the tile's corner. {@code detail} is the relative path of its
 * {@code fb-district/1} file, or null when not generated.
 */
public record DistrictSummary(String id, int ix, int iz, int x, int z, int w, int d, int originChunkX, int originChunkZ,
		int quarter, int density, List<StyleWeight> styles, int lots, String detail) {
	public DistrictSummary {
		Objects.requireNonNull(id);
		styles = List.copyOf(styles);
		if (!id.equals(Ids.district(ix, iz))) throw new IllegalArgumentException("district id " + id + " does not match " + ix + "," + iz);
		if (w <= 0 || d <= 0 || x % 16 != 0 || z % 16 != 0 || w % 16 != 0 || d % 16 != 0) {
			throw new IllegalArgumentException("district " + id + " is not chunk aligned: " + x + "," + z + " " + w + "x" + d);
		}
		if (originChunkX != x / 16 || originChunkZ != z / 16) throw new IllegalArgumentException("district " + id + " origin chunk does not match its corner");
		if (density < 0 || density > 100) throw new IllegalArgumentException("district " + id + " density " + density);
		if (lots < 0) throw new IllegalArgumentException("district " + id + " lots " + lots);
	}

	public Rect rect() { return new Rect(x, z, w, d); }
}
