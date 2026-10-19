package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * A building shell in its local frame: +x along the front, +y up, +z from the front (z = 0) to the
 * back; local y = 0 is the ground-floor slab. Site-independent: a pure function of {@link #spec}.
 * Every box, room and opening lies inside {@link #bounds}.
 */
public record BuildingIr(
		BuildingSpec spec,
		Box3 bounds,
		List<Storey> storeys,
		List<Volume> volumes,
		List<Room> rooms,
		List<Opening> openings,
		List<Roof> roofs,
		List<Box> boxes,
		List<Hook> hooks) {
	/** A shell without furnishing hooks (generator v0). */
	public BuildingIr(BuildingSpec spec, Box3 bounds, List<Storey> storeys, List<Volume> volumes, List<Room> rooms,
			List<Opening> openings, List<Roof> roofs, List<Box> boxes) {
		this(spec, bounds, storeys, volumes, rooms, openings, roofs, boxes, List.of());
	}

	public BuildingIr {
		Objects.requireNonNull(spec);
		Objects.requireNonNull(bounds);
		storeys = List.copyOf(storeys);
		volumes = List.copyOf(volumes);
		rooms = List.copyOf(rooms);
		openings = List.copyOf(openings);
		roofs = List.copyOf(roofs);
		boxes = List.copyOf(boxes);
		hooks = List.copyOf(hooks);
	}
}
