package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A furnishing hook (ADR 0001 B4: furniture as hooks): a cell of room air standing on the floor where
 * the mod (6.4) may put a bed, workstation, chest and so on. The planner never places furniture
 * blocks. {@code kind} is a free tag ("bed", "table", "workstation", "chest", "hearth", "altar",
 * "counter", "light"...); {@code facing} is the side the furniture faces. Shell JSON key
 * {@code hooks}, genVersion 2 and later.
 */
public record Hook(String id, String kind, String room, int x, int y, int z, Facing facing) {
	public Hook {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		Objects.requireNonNull(room);
		Objects.requireNonNull(facing);
	}
}
