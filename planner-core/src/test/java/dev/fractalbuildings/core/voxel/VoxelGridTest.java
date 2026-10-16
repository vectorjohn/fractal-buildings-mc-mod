package dev.fractalbuildings.core.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SampleScenes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoxelGridTest {
	@Test
	void sampleCounts() {
		VoxelGrid g = VoxelGrid.of(SampleScenes.house().buildings().get(0));
		assertEquals(196, g.count());
		assertEquals(70, g.count(Role.FLOOR));
		assertEquals(69, g.count(Role.WALL));
		assertEquals(35, g.count(Role.ROOF));
		assertEquals(14, g.count(Role.FOUNDATION));
		assertEquals(4, g.count(Role.DOOR));
		assertEquals(4, g.count(Role.WINDOW));
	}

	@Test
	void gridCoversSiteBoxesBelowBounds() {
		VoxelGrid g = VoxelGrid.of(SampleScenes.house().buildings().get(0));
		assertEquals(-1, g.minY);
		assertEquals(Role.FOUNDATION, g.get(0, -1, 0));
		assertEquals(Role.AIR, g.get(0, -5, 0));
	}

	@Test
	void iterationIsYZXOrder() {
		VoxelGrid g = VoxelGrid.of(SampleScenes.house().buildings().get(0));
		List<int[]> cells = new ArrayList<>();
		g.forEach((x, y, z, r) -> cells.add(new int[] {y, z, x}));
		for (int i = 1; i < cells.size(); i++) {
			int[] a = cells.get(i - 1), b = cells.get(i);
			boolean ordered = a[0] < b[0] || (a[0] == b[0] && (a[1] < b[1] || (a[1] == b[1] && a[2] < b[2])));
			if (!ordered) throw new AssertionError("not in (y,z,x) order at " + i);
		}
	}

	@Test
	void airBoxClearsLaterThanEarlier() {
		var b = SampleScenes.house().buildings().get(0);
		var boxes = new ArrayList<>(b.shell().boxes());
		boxes.add(new dev.fractalbuildings.core.ir.Box(dev.fractalbuildings.core.ir.Box3.of(0, 0, 0, 7, 1, 5), Role.AIR,
				dev.fractalbuildings.core.ir.Layer.SHELL, 0));
		var shell = new dev.fractalbuildings.core.ir.BuildingIr(b.shell().spec(), b.shell().bounds(), b.shell().storeys(),
				b.shell().volumes(), b.shell().rooms(), b.shell().openings(), b.shell().roofs(), boxes);
		var nb = new dev.fractalbuildings.core.ir.PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), shell, b.siteBoxes());
		assertEquals(0, VoxelGrid.of(nb).count(Role.FLOOR) - 35);
	}
}
