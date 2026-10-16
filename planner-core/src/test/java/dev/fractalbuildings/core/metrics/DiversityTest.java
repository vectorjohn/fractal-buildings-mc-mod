package dev.fractalbuildings.core.metrics;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiversityTest {
	private static PlacedBuilding house() { return SampleScenes.house().buildings().get(0); }

	private static PlacedBuilding moved(PlacedBuilding b, int i, int rot) {
		return new PlacedBuilding("b" + i, b.lots(), b.ox() + i * 20, b.oy(), b.oz() - i, rot, b.shell(), b.siteBoxes());
	}

	/** A box-shaped building of a given size, one door, gable-less. */
	private static PlacedBuilding block(int w, int d, int h, int storeys) {
		BuildingSpec spec = new BuildingSpec(1, w * 1000L + d, "plain", "house", w, d, storeys, "flat");
		List<Box> boxes = List.of(new Box(Box3.of(0, 0, 0, w, h, d), Role.WALL, Layer.SHELL, 0));
		List<Storey> st = new ArrayList<>();
		List<Room> rooms = new ArrayList<>();
		for (int s = 0; s < storeys; s++) {
			st.add(new Storey(s, 0, 4));
			rooms.add(new Room("r" + s, s, "v0", List.of(Box3.of(0, 0, 0, 1, 1, 1)), "room"));
		}
		BuildingIr ir = new BuildingIr(spec, Box3.of(0, 0, 0, w, h, d), st, List.of(new Volume("v0", Box3.of(0, 0, 0, w, h, d), null)),
				rooms, List.of(), List.of(), boxes);
		return new PlacedBuilding("b", List.of("l"), 0, 0, 0, 0, ir, List.of());
	}

	@Test
	void identicalCopiesAreMinimallyDiverse() {
		List<PlacedBuilding> l = new ArrayList<>();
		for (int i = 0; i < 64; i++) l.add(moved(house(), i, i % 4));
		DiversityReport r = Diversity.of(l);
		assertEquals(0.0, r.meanJaccardDistance());
		assertEquals(1.0, r.nearDuplicateRate());
		assertEquals(63, r.duplicatesBySpecHash());
		assertEquals(63, r.duplicatesByVoxelHash());
		assertEquals(1, r.distinctSignatures());
		r.entropyBits().values().forEach(v -> assertEquals(0.0, v));
	}

	@Test
	void foundationsDoNotCountAsVariety() {
		PlacedBuilding a = house();
		PlacedBuilding b = new PlacedBuilding("x", a.lots(), 0, 0, 0, 2, a.shell(), List.of());
		assertEquals(0.0, Diversity.of(List.of(a, b)).meanJaccardDistance());
	}

	@Test
	void differentBuildingsScoreHigher() {
		List<PlacedBuilding> l = new ArrayList<>();
		for (int i = 0; i < 8; i++) l.add(block(3 + i, 4 + 2 * i, 4, 1 + i % 3));
		DiversityReport r = Diversity.of(l);
		assertTrue(r.meanJaccardDistance() > 0.4, "" + r.meanJaccardDistance());
		assertEquals(0.0, r.nearDuplicateRate());
		assertEquals(0, r.duplicatesByVoxelHash());
		assertEquals(8, r.distinctSignatures());
		assertEquals(3.0, r.entropyBits().get("width"), 1e-9);
		assertEquals(3.0, r.entropyBits().get("depth"), 1e-9);
		assertTrue(r.entropyBits().get("storeys") > 1.0);
	}

	@Test
	void smallChangeIsDistanceButBigChangeIsMore() {
		long[] a = Descriptor.of(block(10, 10, 4, 1)).voxels();
		long[] b = Descriptor.of(block(11, 10, 4, 1)).voxels();
		long[] c = Descriptor.of(block(3, 3, 4, 1)).voxels();
		assertEquals(1.0 / 11, Diversity.jaccardDistance(a, b), 1e-9);
		assertTrue(Diversity.jaccardDistance(a, c) > Diversity.jaccardDistance(a, b));
		assertEquals(0.0, Diversity.jaccardDistance(a, a));
	}

	@Test
	void orderIndependent() {
		List<PlacedBuilding> l = new ArrayList<>();
		for (int i = 0; i < 20; i++) l.add(block(3 + i % 7, 4 + i % 5, 4, 1 + i % 3));
		l.add(house());
		String base = Diversity.of(l).toJson();
		for (int s = 0; s < 5; s++) {
			Collections.shuffle(l, new java.util.Random(s)); // test-only shuffle, fixed seed
			assertEquals(base, Diversity.of(l).toJson());
		}
	}

	@Test
	void reportsRender() {
		DiversityReport r = Diversity.of(List.of(house(), block(5, 5, 4, 1)));
		assertTrue(r.toMarkdown().contains("mean pairwise Jaccard"));
		assertTrue(r.toJson().startsWith("{"));
		assertTrue(r.toJson().contains("\"entropyBits\""));
	}

	@Test
	void emptyAndSingle() {
		assertEquals(0, Diversity.of(List.of()).count());
		assertEquals(0.0, Diversity.of(List.of(house())).meanJaccardDistance());
	}
}
