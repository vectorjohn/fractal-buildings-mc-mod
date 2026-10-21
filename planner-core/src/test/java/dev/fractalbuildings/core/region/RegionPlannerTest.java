package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.rand.Hash64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RegionPlannerTest {
	static final String CROP = "../fixtures/terrain/s4-12345-crop32.json";

	static RegionPlanner planner(long seed) {
		return new RegionPlanner(new RegionField(seed, 1024), new RegionTestWorld(seed));
	}

	static List<CellDecision> cells(RegionPlanner p, int r) {
		List<CellDecision> out = new ArrayList<>();
		for (int cz = -r; cz <= r; cz++) for (int cx = -r; cx <= r; cx++) out.add(p.decide(cx, cz));
		return out;
	}

	@Test
	void settlementsAvoidWaterAndPeaksAndKeepOffTheCellEdge() {
		for (long seed : new long[] {1, 12345, 987654321L}) {
			RegionPlanner p = planner(seed);
			RegionTestWorld world = new RegionTestWorld(seed);
			int settled = 0;
			for (CellDecision c : cells(p, 12)) {
				if (!c.settled()) continue;
				settled++;
				assertFalse(world.water(c.centreX(), c.centreZ()), "centre on water " + c);
				assertFalse(BiomeClass.isPeak(world.biome(c.centreX(), c.centreZ())), "centre on a peak " + c);
				assertTrue(c.edgeDist() >= 0.15 * 1024 - RegionProbe.STRIDE, "centre near the cell edge " + c);
				assertTrue(c.radius() <= c.radiusFit() && c.radius() <= 512 && c.radius() <= c.radiusDrawn(), "radius " + c);
				assertEquals(0, c.centreX() % 64);
				assertEquals(0, c.centreZ() % 64);
				assertTrue(c.waterFrac() <= (Tiers.waterBias(c.tier()) > 0 ? Tiers.WATER_CAP_BIASED : p.maxWaterFrac) && c.peakFrac() <= p.maxPeakFrac);
			}
			assertTrue(settled > 100, "seed " + seed + " settled " + settled + " of 625");
		}
	}

	@Test
	void waterWorldRejectsEveryCellForWater() {
		RegionProbe sea = RegionProbe.of((x, z) -> 30, (x, z) -> "minecraft:ocean");
		RegionPlanner p = new RegionPlanner(new RegionField(3, 1024), sea);
		for (CellDecision c : cells(p, 3)) {
			assertFalse(c.settled());
			assertEquals("water", c.reason());
		}
	}

	@Test
	void decisionsDoNotDependOnQueryOrder() {
		RegionPlanner p = planner(12345);
		List<CellDecision> inOrder = cells(p, 6);
		List<CellDecision> shuffled = new ArrayList<>(inOrder);
		Collections.shuffle(shuffled, new Random(5));
		RegionPlanner fresh = planner(12345);
		for (CellDecision c : shuffled) assertEquals(c, fresh.decide(c.cx(), c.cz()));
	}

	@Test
	void oneThreadAndEightThreadsGiveTheSameDecisions() throws Exception {
		RegionPlanner p = planner(987654321L);
		List<CellDecision> single = cells(p, 8);
		ExecutorService ex = Executors.newFixedThreadPool(8);
		try {
			List<Future<CellDecision>> fs = new ArrayList<>();
			for (CellDecision c : single) fs.add(ex.submit(() -> p.decide(c.cx(), c.cz())));
			for (int i = 0; i < single.size(); i++) assertEquals(single.get(i), fs.get(i).get());
		} finally {
			ex.shutdown();
		}
	}

	@Test
	void seedsDifferingOnlyAboveBit32GiveDifferentDecisions() {
		RegionPlanner a = planner(12345L), b = new RegionPlanner(new RegionField(12345L + (1L << 32), 1024), new RegionTestWorld(12345L));
		int diff = 0;
		for (int cx = -8; cx < 8; cx++) if (!a.decide(cx, 0).equals(b.decide(cx, 0))) diff++;
		assertTrue(diff >= 12, "only " + diff + " of 16 cells differ");
	}

	@Test
	void settledRateTracksTheChance() {
		// Flat all-plains land: every cell scores 1.0, so the roll alone decides: about baseChance.
		RegionProbe land = RegionProbe.of((x, z) -> 64, (x, z) -> "minecraft:plains");
		RegionPlanner p = new RegionPlanner(new RegionField(8, 1024), land);
		int n = 0, settled = 0;
		for (CellDecision c : cells(p, 20)) {
			n++;
			if (c.settled()) settled++;
		}
		assertEquals(p.baseChance, settled / (double) n, 0.05);
	}

	@Test
	void recordedCropReplaysAndHoldsTheInvariants() throws IOException {
		RecordedProbe r = RecordedProbe.read(Path.of(CROP));
		RegionProbe probe = RegionProbe.of(r, r);
		RegionPlanner p = new RegionPlanner(new RegionField(12345L, 1024), probe);
		p.waterBias = false; // the bias widens the reach beyond what this small crop holds
		int x1 = r.x0() + (r.nx() - 1) * r.step(), z1 = r.z0() + (r.nz() - 1) * r.step();
		List<CellDecision> ds = p.decideWindow(r.x0(), r.z0(), x1, z1);
		assertFalse(ds.isEmpty(), "the crop should cover at least one cell");
		for (CellDecision c : ds) {
			assertEquals(c, p.decide(c.cx(), c.cz()));
			if (c.settled()) {
				assertFalse(probe.water(c.centreX(), c.centreZ()), "centre on water " + c);
				assertFalse(BiomeClass.isPeak(probe.biome(c.centreX(), c.centreZ())), "centre on a peak " + c);
			}
		}
	}

	/** Pure function of (seed, cell, probe): the world of one cell cannot be read outside its reach. */
	@Test
	void decisionReadsStayInsideProbeReach() {
		RegionPlanner[] holder = new RegionPlanner[1];
		RegionTestWorld world = new RegionTestWorld(5);
		int[] far = {0};
		for (int cx = -4; cx <= 4; cx++) {
			for (int cz = -4; cz <= 4; cz++) {
				final int fx = cx, fz = cz;
				RegionProbe probe = new RegionProbe() {
					@Override public int floorY(int x, int z) { check(x, z); return world.floorY(x, z); }
					@Override public int waterDepth(int x, int z) { return world.waterDepth(x, z); }
					@Override public String biome(int x, int z) { check(x, z); return world.biome(x, z); }
					void check(int x, int z) {
						if (x % 64 != 0 || z % 64 != 0) far[0]++;
						int reach = holder[0].probeReach(fx, fz);
						if (Math.abs(x - holder[0].field.siteX(fx, fz)) > reach || Math.abs(z - holder[0].field.siteZ(fx, fz)) > reach) far[0]++;
					}
				};
				holder[0] = new RegionPlanner(new RegionField(5, 1024), probe);
				holder[0].decide(fx, fz);
			}
		}
		assertEquals(0, far[0], "probe reads off the lattice or beyond probeReach");
	}

	/** Golden: the decisions of a fixed world, generated never hand-merged (copy build/golden/region/ over on an intended change). */
	@Test
	void goldenDecisions() throws Exception {
		List<String> stale = new ArrayList<>();
		for (long seed : new long[] {12345L, 987654321L}) {
			StringBuilder sb = new StringBuilder();
			for (CellDecision c : cells(planner(seed), 8)) {
				sb.append(c.cx()).append(' ').append(c.cz()).append(' ').append(c.settled() ? c.tier().json : "-").append(' ')
					.append(c.centreX()).append(' ').append(c.centreZ()).append(' ').append(c.radius()).append(' ').append(c.reason()).append('\n');
			}
			byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			String actual = HexFormat.of().formatHex(d) + "\n" + sb;
			String expected;
			try (var in = RegionPlannerTest.class.getResourceAsStream("/golden/region/" + seed + ".txt")) {
				expected = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			if (!expected.equals(actual)) {
				Path out = Path.of("build/golden/region/" + seed + ".txt");
				Files.createDirectories(out.getParent());
				Files.writeString(out, actual);
				stale.add(seed + " (new file " + out.toAbsolutePath() + ")");
			}
		}
		assertTrue(stale.isEmpty(), "region goldens differ: " + stale + ". If intended, copy build/golden/region/*.txt to src/test/resources/golden/region/.");
	}
}
