package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ROADLEAFORDER (FoliageClientGameTest, seed 1 road at -616,108,-2301): a tree whose trunk an intercity road cuts leaves a crown over the
 * road; the foliage pass of the road's chunk removes it when nothing holds it. When the crown touches the crown of a tree in the next chunk,
 * whether that tree was already there decided whether the crown was "held" (6 leaf steps to the other tree's log) and stayed, or was
 * removed: the neighbour decorated first in one world and later in another. This test scripts that pair on the recorded river window
 * of world 12345 (a road column with the cut tree, a tree 3 to 5 blocks over a chunk line whose crown touches it) and compares the
 * foliage round both trees in every order.
 */
class RoadLeafOrderSimTest {
	static SimSites site;
	static WorldPlanner planner;
	static int wcx, wcz;
	static final int N = 3;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		site = RoadWorld.site();
		planner = RoadWorld.planner(site);
		// the N x N window of the river window with the most road chunks and no district column within one chunk
		int best = -1;
		for (int cz = RoadWorld.CZ0 + 2; cz + N + 2 <= RoadWorld.CZ1; cz++) {
			for (int cx = RoadWorld.CX0 + 2; cx + N + 2 <= RoadWorld.CX1; cx++) {
				int r = 0;
				boolean dist = false;
				for (int dz = -1; dz <= N && !dist; dz++) for (int dx = -1; dx <= N && !dist; dx++) {
					boolean[] d = planner.districtColumns(cx + dx, cz + dz);
					if (d != null) for (boolean b : d) dist |= b;
					if (dx >= 0 && dz >= 0 && dx < N && dz < N && !planner.roadColumns(cx + dx, cz + dz).isEmpty()) r++;
				}
				if (!dist && r > best) { best = r; wcx = cx; wcz = cz; }
			}
		}
	}

	/** The leaf cells a call puts down alone on bare terrain. */
	static Set<Long> leavesAlone(SimFeatures.Call c) {
		int cx = c.chunkX(), cz = c.chunkZ();
		SimWorld w = new SimWorld(site.seed(), cx - 1, cz - 1, 3, 3, site.probes().exact(), site.probes().biome());
		Set<Long> out = new TreeSet<>();
		if (!SimFeatures.place(w, null, c, new SimFeatures.Stats())) return out;
		for (SimWorld.Write wr : w.writes()) if (wr.now().is(BlockTags.LEAVES)) out.add(PieceLedger.pack(wr.x(), wr.y(), wr.z()));
		return out;
	}

	static boolean touch(Set<Long> a, Set<Long> b) {
		for (long k : a) {
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			for (int[] f : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) if (b.contains(PieceLedger.pack(x + f[0], y + f[1], z + f[2]))) return true;
		}
		return false;
	}

	/** A tree on a road column of the inner window and a tree over a chunk line whose crown touches its crown. Deterministic scan. */
	static List<SimFeatures.Call> pair() {
		var terrain = site.probes().exact();
		for (int cz = wcz; cz < wcz + N; cz++) for (int cx = wcx; cx < wcx + N; cx++) {
			List<RoadNet.Col> cols = new ArrayList<>(planner.roadColumns(cx, cz));
			cols.sort(java.util.Comparator.comparingInt(RoadNet.Col::z).thenComparingInt(RoadNet.Col::x));
			Set<Long> roadCols = new TreeSet<>();
			for (int ez = cz - 1; ez <= cz + 1; ez++) for (int ex = cx - 1; ex <= cx + 1; ex++) for (RoadNet.Col c : planner.roadColumns(ex, ez)) roadCols.add(PieceLedger.pack(c.x(), 0, c.z()));
			for (RoadNet.Col col : cols) {
				int x = col.x(), z = col.z();
				if (Math.floorDiv(x, 16) != cx || Math.floorDiv(z, 16) != cz) continue;
				int lx = x & 15, lz = z & 15;
				if (Math.min(Math.min(lx, 15 - lx), Math.min(lz, 15 - lz)) > 3) continue; // near a chunk line
				if (terrain.surfaceY(x, z) != terrain.floorY(x, z)) continue;
				for (String key : new String[] {"spruce", "oak", "birch"}) {
					SimFeatures.Call t = SimFeatures.scripted(key, x, terrain.floorY(x, z), z, site.seed(), terrain, site.probes().biome());
					if (t == null) continue;
					Set<Long> tl = leavesAlone(t);
					for (int d = 3; d <= 5; d++) for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int ux = x + dir[0] * d, uz = z + dir[1] * d;
						if (Math.floorDiv(ux, 16) == cx && Math.floorDiv(uz, 16) == cz) continue; // the other side of the line
						if (roadCols.contains(PieceLedger.pack(ux, 0, uz)) || terrain.surfaceY(ux, uz) != terrain.floorY(ux, uz)) continue;
						SimFeatures.Call u = SimFeatures.scripted(key, ux, terrain.floorY(ux, uz), uz, site.seed(), terrain, site.probes().biome());
						if (u == null || !touch(tl, leavesAlone(u))) continue;
						// the road cuts T, not U: after a forward run U's trunk stands and T's base log is gone
						Scenario s = new Scenario("roadleaf-probe", planner, site.seed(), terrain, site.probes().biome(), wcx, wcz, N, List.of(t, u), true);
						SimRun r = OrderSim.run(s, SimOrders.forward(s.decX0(), s.decZ0(), s.decN(), s.decN()), false);
						boolean uStands = r.world.get(ux, u.y(), uz).is(BlockTags.LOGS), tCut = !r.world.get(x, t.y(), z).is(BlockTags.LOGS) && !r.world.get(x, t.y() + 1, z).is(BlockTags.LOGS);
						if (!uStands || !tCut) continue;
						return List.of(t, u);
					}
				}
			}
		}
		return null;
	}

	static String name(BlockState s) { return s.getBlock().getDescriptionId().replace("block.minecraft.", ""); }

	/** Per order: the leaves, logs and vines round the two trees (claimed road cells included). */
	static Map<String, String> foliage(Scenario s, List<SimFeatures.Call> calls) {
		Map<String, String> out = new TreeMap<>();
		int x0 = Math.min(calls.get(0).x(), calls.get(1).x()) - 6, x1 = Math.max(calls.get(0).x(), calls.get(1).x()) + 6;
		int z0 = Math.min(calls.get(0).z(), calls.get(1).z()) - 6, z1 = Math.max(calls.get(0).z(), calls.get(1).z()) + 6;
		int y0 = Math.min(calls.get(0).y(), calls.get(1).y()) - 2, y1 = Math.max(calls.get(0).y(), calls.get(1).y()) + 16;
		for (SimOrders.Order o : SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed)) {
			SimRun r = OrderSim.run(s, o, false);
			assertEquals(List.of(), r.failed(true), o.name() + ": the claimed-cell checks pass");
			StringBuilder b = new StringBuilder();
			for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
				BlockState st = r.world.get(x, y, z);
				if (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(Blocks.VINE)) b.append(x).append(',').append(y).append(',').append(z).append('=').append(name(st)).append(' ');
			}
			out.put(o.name(), b.toString());
		}
		return out;
	}

	@org.junit.jupiter.api.AfterEach
	void reset() { Mutations.mode = null; }

	@Test
	void aCutCrownTouchingANeighboursCrownIsTheSameInEveryOrder() {
		List<SimFeatures.Call> calls = pair();
		assertNotNull(calls, "a tree on a road column whose crown touches a tree over a chunk line, in window " + wcx + "," + wcz);
		Scenario s = new Scenario("roadleaf", planner, site.seed(), site.probes().exact(), site.probes().biome(), wcx, wcz, N, calls, true);

		Mutations.mode = Mutations.FOLIAGE_OLD; // control: the pass before, support through any chunk's foliage at the time of the pass
		Map<String, String> old = foliage(s, calls);
		Mutations.mode = null;
		System.out.println("[roadleaf] trees " + calls + ", foliage-old: " + summary(old));
		assertTrue(old.values().stream().distinct().count() > 1, "with the old pass the foliage round the trees differs between orders: " + summary(old));

		Map<String, String> now = foliage(s, calls);
		System.out.println("[roadleaf] trees " + calls + ", own pass: " + summary(now));
		assertEquals(1, now.values().stream().distinct().count(), "the foliage round the cut tree and its neighbour is the same in every order: " + now);
		// r2 (verifier): not too much either. The neighbour tree stands whole: its base log, and every leaf it puts down alone on bare terrain
		// outside our claims is a leaf in every order (64 cells here; a pass that removed far more, e.g. the neighbour too, passed the old check)
		SimFeatures.Call u = calls.get(1);
		String any = now.values().iterator().next();
		assertTrue(any.contains(u.x() + "," + u.y() + "," + u.z() + "=" + u.key() + "_log"), "the neighbour's base log stands: " + any);
		Set<Long> uLeaves = leavesAlone(u);
		int x0 = u.x() - 8, z0 = u.z() - 8, y0 = u.y() - 2;
		dev.fractalbuildings.mod.worldgen.ClaimMask mask = dev.fractalbuildings.mod.worldgen.ClaimMask.of(planner, new dev.fractalbuildings.core.ir.Box3(x0, y0, z0, 17, 24, 17));
		int want = 0;
		List<String> missing = new ArrayList<>();
		for (long k : uLeaves) {
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			if (mask.role(x, y, z) != dev.fractalbuildings.mod.worldgen.ClaimMask.NONE) continue;
			want++;
			if (!any.contains(x + "," + y + "," + z + "=" + u.key() + "_leaves")) missing.add(x + "," + y + "," + z);
		}
		System.out.println("[roadleaf] neighbour leaves outside our claims " + want + ", missing " + missing);
		assertTrue(want >= 20, "the neighbour has a crown outside our claims: " + want);
		assertEquals(List.of(), missing, "every leaf of the neighbour outside our claims stays");
	}

	/**
	 * r2 (verifier): a feature write outside the generation region's write zone. Vanilla logs it and returns false; reading the cell throws
	 * ("Requested chunk unavailable"), which would kill the chunk. {@code OwnWrites.onSetBlock} (the mixin's hook) must not read it, and
	 * {@code FoliageClear.own} must not read back a record outside the zone (one made by a path that did not check) nor read a neighbour of
	 * an own cell outside the region.
	 */
	@Test
	void aWriteOutsideTheWriteZoneIsNeitherReadNorReadBack() {
		RoadNet.Col col = null;
		for (int cz = wcz; cz < wcz + N && col == null; cz++) for (int cx = wcx; cx < wcx + N && col == null; cx++)
			for (RoadNet.Col c : planner.roadColumns(cx, cz)) if (Math.floorDiv(c.x(), 16) == cx && Math.floorDiv(c.z(), 16) == cz && (c.x() & 15) > 0 && (c.x() & 15) < 15) { col = c; break; }
		assertNotNull(col);
		int cx = Math.floorDiv(col.x(), 16), cz = Math.floorDiv(col.z(), 16);
		SimWorld w = new SimWorld(site.seed(), cx - 1, cz - 1, 3, 3, site.probes().exact(), site.probes().biome());
		SimLevel lv = new SimLevel(w, cx, cz, "feature:test@" + cx + "," + cz, 1L);
		net.minecraft.core.BlockPos near = new net.minecraft.core.BlockPos(col.x(), col.y() + 1, col.z()), far = new net.minecraft.core.BlockPos(cx * 16 + 40, 70, cz * 16 + 8);
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false);
		dev.fractalbuildings.mod.worldgen.OwnWrites.begin(cx, cz);
		dev.fractalbuildings.mod.worldgen.OwnWrites.enterFeature();
		try {
			// the hook: outside the zone it returns false and never calls the reader (which throws, as the game's region does)
			assertFalse(dev.fractalbuildings.mod.worldgen.OwnWrites.onSetBlock(far, leaf, p -> false, p -> { throw new IllegalStateException("read outside the zone"); }));
			assertTrue(dev.fractalbuildings.mod.worldgen.OwnWrites.of(cx, cz).isEmpty(), "nothing recorded for the far write");
			lv.proxy().setBlock(near, leaf, 2 | 16); // a leaf of this chunk in the road's headroom (claimed air): recorded through SimLevel
			dev.fractalbuildings.mod.worldgen.OwnWrites.record(far, Blocks.AIR.defaultBlockState(), leaf); // a record no zone check stopped
			assertThrows(IllegalStateException.class, () -> lv.proxy().getBlockState(far), "the far cell cannot be read here (the control)");
		} finally {
			dev.fractalbuildings.mod.worldgen.OwnWrites.exitFeature();
		}
		try {
			assertDoesNotThrow(() -> dev.fractalbuildings.mod.worldgen.FoliageClear.own(lv.proxy(), planner, cx, cz));
			assertTrue(w.get(near.getX(), near.getY(), near.getZ()).isAir(), "the leaf in the road's headroom was put back to air");
		} finally {
			dev.fractalbuildings.mod.worldgen.OwnWrites.end();
		}
	}

	static String summary(Map<String, String> per) {
		StringBuilder b = new StringBuilder();
		per.forEach((k, v) -> b.append(k).append('=').append(v.isBlank() ? 0 : v.trim().split(" ").length).append(' '));
		return b.toString().trim();
	}
}
