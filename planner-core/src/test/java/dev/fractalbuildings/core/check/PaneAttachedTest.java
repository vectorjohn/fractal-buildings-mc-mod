package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SampleScenes;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** DS-51 {@code pane-attached} (GLASSSTUB): the unit cases, over the sample house with panes added or removed. */
class PaneAttachedTest {
	private static List<PaneAttached.Fail> fails(UnaryOperator<List<Box>> boxes) {
		SceneDoc s = InvariantsTest.mutateBoxes(boxes);
		return PaneAttached.check(VoxelGrid.of(s.buildings().get(0)));
	}

	private static Box pane(int x, int y, int z) { return new Box(Box3.of(x, y, z, 1, 1, 1), Role.WINDOW, Layer.SHELL, 0); }

	@Test
	void sampleHousePanesAreAttached() {
		assertEquals(List.of(), fails(b -> b));
	}

	@Test
	void panePlacedFreeInAirIsLone() {
		// above the roof edge, beside nothing: no block on any side
		List<PaneAttached.Fail> f = fails(b -> { b.add(pane(3, 10, 2)); return b; });
		assertTrue(f.stream().anyMatch(x -> x.y() == 10 && x.why().equals("lone")), f.toString());
	}

	@Test
	void paneOnTopOfAWallWithAirAboveIsCapped() {
		// a wall cell at (0,2,2) is already a window; stack a pane on the wall top (0,5,2)'s roof wall with air above
		List<PaneAttached.Fail> f = fails(b -> { b.add(pane(0, 8, 2)); b.add(new Box(Box3.of(1, 8, 2, 1, 1, 1), Role.WALL, Layer.SHELL, 0)); return b; });
		assertTrue(f.stream().anyMatch(x -> x.y() == 8 && x.why().equals("capped")), f.toString());
	}

	@Test
	void paneAtAnOuterCornerFails() {
		// walls east and south of the pane, air west and north, wall above: an L
		List<PaneAttached.Fail> f = fails(b -> {
			b.add(pane(10, 2, 10));
			b.add(new Box(Box3.of(11, 2, 10, 1, 1, 1), Role.WALL, Layer.SHELL, 0));
			b.add(new Box(Box3.of(10, 2, 11, 1, 1, 1), Role.WALL, Layer.SHELL, 0));
			b.add(new Box(Box3.of(10, 3, 10, 1, 1, 1), Role.WALL, Layer.SHELL, 0));
			return b;
		});
		assertTrue(f.stream().anyMatch(x -> x.x() == 10 && x.why().equals("corner")), f.toString());
	}

	@Test
	void paneRunWithWallAtBothEndsPasses() {
		List<PaneAttached.Fail> f = fails(b -> {
			b.add(new Box(Box3.of(10, 2, 10, 1, 2, 1), Role.WALL, Layer.SHELL, 0));
			b.add(new Box(Box3.of(14, 2, 10, 1, 2, 1), Role.WALL, Layer.SHELL, 0));
			b.add(new Box(Box3.of(11, 2, 10, 3, 1, 1), Role.WINDOW, Layer.SHELL, 0));
			b.add(new Box(Box3.of(11, 3, 10, 3, 1, 1), Role.WALL, Layer.SHELL, 0));
			return b;
		});
		assertEquals(List.of(), f.stream().filter(x -> x.x() >= 10 && x.x() <= 14).toList());
	}

	@Test
	void invariantReportsIt() {
		SceneDoc v1 = InvariantsTest.mutateBoxes(b -> { b.add(pane(3, 7, 0)); return b; });
		SceneDoc s = new SceneDoc(2, v1.seed(), v1.terrain(), v1.lots(), v1.buildings(), v1.violations());
		InvariantsTest.assertFires(s, "pane-attached");
	}
}
