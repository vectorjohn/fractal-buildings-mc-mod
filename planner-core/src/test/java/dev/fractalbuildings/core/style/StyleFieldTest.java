package dev.fractalbuildings.core.style;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.SyntheticBiomes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StyleFieldTest {
	private static final BiomeProbe SPLIT = SyntheticBiomes.halfPlaneX(0, "minecraft:desert", "minecraft:plains");

	private static double desertShare(StyleField f, int xLo, int xHi) {
		int n = 0, d = 0;
		for (int x = xLo; x <= xHi; x += 3)
			for (int z = -200; z <= 200; z += 5) {
				n++;
				if (f.pick(x, z).equals("desert")) d++;
			}
		return d / (double) n;
	}

	@Test
	void splitBorderBlends() {
		StyleField f = new StyleField(SPLIT, 7);
		assertTrue(desertShare(f, -300, -64) > 0.95);
		assertTrue(desertShare(f, 64, 300) < 0.05);
		double mid = desertShare(f, -15, 15);
		assertTrue(mid > 0.3 && mid < 0.7, "mid share " + mid);
	}

	@Test
	void picksArePureFunctions() {
		StyleField f = new StyleField(SPLIT, 7);
		List<int[]> pts = new ArrayList<>();
		for (int x = -60; x < 60; x += 4) for (int z = 0; z < 40; z += 8) pts.add(new int[] {x, z});
		List<String> a = pts.stream().map(p -> f.pick(p[0], p[1])).toList();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < pts.size(); i++) order.add(i);
		Collections.shuffle(order, new Random(3));
		StyleField g = new StyleField(SPLIT, 7);
		for (int i : order) assertEquals(a.get(i), g.pick(pts.get(i)[0], pts.get(i)[1]));
		StyleField other = new StyleField(SPLIT, 8);
		boolean differs = false;
		for (int i = 0; i < pts.size(); i++) differs |= !a.get(i).equals(other.pick(pts.get(i)[0], pts.get(i)[1]));
		assertTrue(differs, "seed must matter near the border");
	}

	@Test
	void sampleCountAndSortedWeights() {
		var w = new StyleField(SPLIT, 1).weights(0, 0);
		assertEquals(29, w.stream().mapToInt(StyleField.Weight::weight).sum());
		assertEquals(List.of("desert", "plains"), w.stream().map(StyleField.Weight::style).toList());
	}

	@Test
	void unclaimedBiomeIsPlainAndFallbackApplies() {
		assertEquals("plain", new StyleField(SyntheticBiomes.constant("minecraft:nether_wastes"), 1).pick(5, 5));
		assertEquals("plain", new StyleField(SyntheticBiomes.constant("mod:made_up"), 1).pick(5, 5));
		assertEquals("desert", StyleField.styleOf("minecraft:savanna"));
		assertEquals("jungle", StyleField.styleOf("minecraft:swamp"));
		assertEquals("snowy", StyleField.styleOf("minecraft:stony_peaks"));
		assertEquals("plains", StyleField.styleOf("minecraft:beach"));
		assertEquals("taiga", StyleField.styleOf("minecraft:dark_forest"));
		assertNull(StyleField.styleOf("minecraft:deep_ocean"));
		assertNull(StyleField.styleOf("minecraft:frozen_river"));
	}

	@Test
	void waterCastsNoVoteAndMedievalIsNeverPicked() {
		BiomeProbe coast = (x, z) -> x < 0 ? "minecraft:ocean" : "minecraft:desert";
		StyleField f = new StyleField(coast, 2);
		for (int x = -40; x < 100; x += 7) assertEquals("desert", f.pick(x, 0));
		assertEquals("plain", new StyleField(SyntheticBiomes.constant("minecraft:ocean"), 2).pick(0, 0));
		StyleField all = new StyleField((x, z) -> "minecraft:forest", 1);
		for (int i = 0; i < 200; i++) assertNotEquals("medieval", all.pick(i, i));
	}

	@Test
	void dominantAndVanillaBiomesAllResolve() throws Exception {
		StyleField f = new StyleField(SPLIT, 1);
		assertEquals("desert", f.dominant(-160, 0, 100, 100));
		assertEquals("plains", f.dominant(60, 0, 100, 100));
		assertEquals("desert", f.dominant(-32, 0, 64, 64)); // 2 vs 2 columns: tie -> lower id
		for (String b : Files.readAllLines(Path.of(getClass().getResource("/vanilla-26.3-biomes.txt").toURI()))) {
			if (b.isBlank()) continue;
			String s = StyleField.styleOf(b.strip());
			if (s != null) assertDoesNotThrow(() -> StyleLibrary.get(s));
		}
	}
}
