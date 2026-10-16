package dev.fractalbuildings.core.ir;

/**
 * One entry of the building's storey grid (one grid per building, ADR 0001). {@code y} is the
 * local y of the storey's floor slab; {@code height} is slab to next slab, so people stand at
 * {@code y + 1}.
 */
public record Storey(int index, int y, int height) {
	public Storey {
		if (index < 0 || height < 3) throw new IllegalArgumentException("storey " + index + " height " + height);
	}
}
