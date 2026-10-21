package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/** A gate at a column. {@code cells} are the two cells it joins (kind cell) or empty; {@code road} is a road id or null. */
public record Gate(String id, GateKind kind, int x, int z, List<Cell> cells, String road) {
	public Gate {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		cells = List.copyOf(cells);
		if (!cells.isEmpty() && cells.size() != 2) throw new IllegalArgumentException("gate " + id + " joins 0 or 2 cells, got " + cells.size());
	}
}
