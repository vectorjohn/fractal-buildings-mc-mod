package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * A shell placed on one or more lots. The shell's local origin maps to world {@code (ox, oy, oz)}
 * under {@link #placement()}. {@code siteBoxes} are site-specific boxes in the shell's local frame
 * (foundations below y = 0, later party-wall masks), applied after the shell's boxes.
 */
public record PlacedBuilding(String id, List<String> lots, int ox, int oy, int oz, int rotation, BuildingIr shell, List<Box> siteBoxes) {
	public PlacedBuilding {
		Objects.requireNonNull(id);
		Objects.requireNonNull(shell);
		lots = List.copyOf(lots);
		siteBoxes = List.copyOf(siteBoxes);
		if (lots.isEmpty()) throw new IllegalArgumentException("building " + id + " has no lot");
		if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation " + rotation);
	}

	public Placement placement() {
		return new Placement(ox, oy, oz, rotation, shell.bounds().maxX(), shell.bounds().maxZ());
	}
}
