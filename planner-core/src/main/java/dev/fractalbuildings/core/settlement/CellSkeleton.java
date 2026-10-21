package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/**
 * One member cell's skeleton (plan step SK): a pure function of (seed, region view, cell, coarse probe, district
 * grid). {@code coarse} covers the cell's window; only samples the cell owns carry a mask, density and quarter
 * (the rest are wild), so the cells of a settlement merge by ownership. {@code hub} is null when the cell holds
 * no urban column. Ids of gates and landmarks are provisional; {@link Skeletons#assemble} numbers them.
 */
public record CellSkeleton(Cell cell, CellOutline outline, CoarseRaster coarse, Pt hub, List<Road> roads, List<Gate> gates,
		List<Landmark> landmarks, List<DistrictSummary> districts, List<Wall> walls) {
	public CellSkeleton {
		Objects.requireNonNull(cell);
		Objects.requireNonNull(outline);
		Objects.requireNonNull(coarse);
		roads = List.copyOf(roads);
		gates = List.copyOf(gates);
		landmarks = List.copyOf(landmarks);
		districts = List.copyOf(districts);
		walls = List.copyOf(walls);
	}
}
