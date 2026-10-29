package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.TreeClip;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * LEAFORDER: a vanilla tree's room check ({@code TreeFeature.getMaxFreeTreeHeight}, radius 2 for a spruce) reaches 2 blocks into the next
 * chunk. Before {@link TreeClip} a real spruce 2 blocks across a chunk line from one of our walls was placed whole when its chunk decorated
 * first and not at all when the wall's chunk did: a whole crown came and went with chunk order (FoliageClientGameTest: 334 or 374 leaves over
 * the seed 1 street). Every claimed-cell check of the sim passes in both cases, since the tree stands only in cells we do not claim; this
 * test compares the unclaimed cells round the tree. With the fix the tree stands in every order.
 */
class TreeClipOrderSimTest {
	static SimSites gate;
	static WorldPlanner planner;
	static ScenarioFinder.Window window;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		window = new ScenarioFinder(planner, gate.site(), gate.probes().exact()).densestWindow(2);
	}

	@AfterEach
	void reset() { Mutations.mode = null; }

	static boolean claimed(Scenario.Plan p, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) if (p.ref.containsKey(PieceLedger.pack(x, y, z))) return true;
		return false;
	}

	/**
	 * A spruce call 2 blocks across a chunk line from a claimed solid cell 3 to 6 above the spruce's floor, with no claimed cell in the
	 * tree's own chunk within 2 columns of it (so only the neighbour's piece can be in its way), and a seed with which it places on bare terrain.
	 */
	record Spot(SimFeatures.Call call, BlockPos wall) {}

	static SimFeatures.Call spruceBesideAWall(Scenario base) { Spot s = spot(base); return s == null ? null : s.call(); }

	static Spot spot(Scenario base) {
		Scenario.Plan p = base.plan();
		var terrain = gate.probes().exact();
		for (long k : p.innerKeys) {
			if (Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir()) continue;
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
				int ox = x + d[0], oz = z + d[1];
				if (Math.floorDiv(ox, 16) == Math.floorDiv(x, 16) && Math.floorDiv(oz, 16) == Math.floorDiv(z, 16)) continue;
				int oy = terrain.floorY(ox, oz);
				if (y < oy + 3 || y > oy + 6) continue;
				boolean free = true;
				for (int ax = -2; ax <= 2 && free; ax++) for (int az = -2; az <= 2 && free; az++) {
					int cx = ox + ax, cz = oz + az;
					if (Math.floorDiv(cx, 16) != Math.floorDiv(ox, 16) || Math.floorDiv(cz, 16) != Math.floorDiv(oz, 16)) continue;
					if (claimed(p, cx, oy - 1, oy + 14, cz)) free = false;
				}
				if (!free) continue;
				SimFeatures.Call c = SimFeatures.scripted("spruce", ox, oy, oz, gate.seed(), terrain, gate.probes().biome());
				if (c != null) return new Spot(c, new BlockPos(x, y, z));
			}
		}
		return null;
	}

	/** Per order: whether the spruce placed, and the unclaimed cells round it (7 x 17 x 7) that hold a log or leaves. */
	static Map<String, String> runOrders(Scenario s, SimFeatures.Call call, Map<String, Integer> placed) {
		Scenario.Plan p = s.plan();
		Map<String, String> out = new TreeMap<>();
		for (SimOrders.Order o : SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed)) {
			SimRun r = OrderSim.run(s, o, false);
			assertEquals(List.of(), r.failed(true), o.name() + ": the claimed-cell checks pass either way");
			StringBuilder cells = new StringBuilder();
			for (int dy = 0; dy <= 16; dy++) for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
				int x = call.x() + dx, y = call.y() + dy, z = call.z() + dz;
				if (p.ref.containsKey(PieceLedger.pack(x, y, z))) continue;
				var st = r.world.get(x, y, z);
				if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) cells.append(x).append(',').append(y).append(',').append(z).append('=').append(st.getBlock().getDescriptionId()).append(' ');
			}
			out.put(o.name(), cells.toString());
			placed.put(o.name(), r.features.placed());
		}
		return out;
	}

	@Test
	void aSpruceBesideAWallAcrossAChunkLineStandsInEveryOrder() {
		Scenario base = Scenario.city("treeclip", gate, planner, window.cx(), window.cz(), window.n(), 0, false, List.of(), true);
		SimFeatures.Call call = spruceBesideAWall(base);
		assertNotNull(call, "a spruce spot beside a wall across a chunk line in SC1");
		Scenario s = new Scenario("treeclip", planner, gate.seed(), gate.probes().exact(), gate.probes().biome(), window.cx(), window.cz(), window.n(), List.of(call), true);

		Mutations.mode = Mutations.TREES; // control: the room check sees our pieces
		Map<String, Integer> placedOff = new TreeMap<>();
		Map<String, String> off = runOrders(s, call, placedOff);
		Mutations.mode = null;
		System.out.println("[ordersim] treeclip at " + call.origin() + " without the fix, placed per order: " + placedOff);
		assertTrue(placedOff.containsValue(0) && placedOff.containsValue(1), "without the fix the spruce stands in some orders only: " + placedOff);
		assertTrue(off.values().stream().distinct().count() > 1, "without the fix the unclaimed cells round it differ between orders");

		Map<String, Integer> placedOn = new TreeMap<>();
		Map<String, String> on = runOrders(s, call, placedOn);
		System.out.println("[ordersim] treeclip at " + call.origin() + " with the fix, placed per order: " + placedOn);
		assertTrue(placedOn.values().stream().allMatch(v -> v == 1), "the spruce stands in every order: " + placedOn);
		assertEquals(1, on.values().stream().distinct().count(), "the unclaimed cells round it are the same in every order");
		assertTrue(on.values().iterator().next().contains("leaves"), "and they hold its crown");
	}

	/** {@link TreeClip#loop} is vanilla's {@code getMaxFreeTreeHeight}: same answer on bare and built terrain, for several trees and heights. */
	@Test
	void theLoopIsVanillas() throws Exception {
		Method vanilla = TreeFeature.class.getDeclaredMethod("getMaxFreeTreeHeight", WorldGenLevel.class, int.class, BlockPos.class);
		vanilla.setAccessible(true);
		var terrain = gate.probes().exact();
		SimWorld w = new SimWorld(gate.seed(), window.cx() - 1, window.cz() - 1, 4, 4, terrain, gate.probes().biome());
		// put a few solid blocks and vines about so the loop has something to stop at
		SimLevel lv = new SimLevel(w, window.cx(), window.cz(), 1, 2, "test", 1L);
		for (int i = 0; i < 400; i++) {
			int x = window.cx() * 16 + Math.floorMod(i * 7, 32), z = window.cz() * 16 + Math.floorMod(i * 13, 32);
			lv.proxy().setBlock(new BlockPos(x, terrain.floorY(x, z) + 1 + (i % 9), z), (i % 5 == 0 ? net.minecraft.world.level.block.Blocks.VINE : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState(), 2 | 16);
		}
		List<String> diff = new ArrayList<>();
		int n = 0;
		int clipped = 0;
		for (var key : List.of(TreeFeatures.SPRUCE, TreeFeatures.OAK, TreeFeatures.DARK_OAK, TreeFeatures.MEGA_SPRUCE, TreeFeatures.FANCY_OAK, TreeFeatures.BIRCH)) {
			TreeFeature tree = (TreeFeature) SimBoot.feature(key);
			for (int i = 0; i < 60; i++) {
				int x = window.cx() * 16 + 2 + Math.floorMod(i * 5, 28), z = window.cz() * 16 + 2 + Math.floorMod(i * 11, 28);
				BlockPos pos = new BlockPos(x, terrain.floorY(x, z), z);
				int h = 4 + i % 20;
				int v = (Integer) vanilla.invoke(tree, lv.proxy(), h, pos);
				int ours = TreeClip.loop(tree, h, pos, q -> tree.trunkPlacer().isFree(lv.proxy(), q) && (tree.ignoreVines() || !TreeFeature.isVine(lv.proxy(), q)));
				n++;
				if (v < h) clipped++;
				if (v != ours) diff.add(key.identifier() + " at " + pos + " h " + h + ": vanilla " + v + ", ours " + ours);
			}
		}
		assertEquals(List.of(), diff, "of " + n);
		assertTrue(clipped > 10, "the loop stops early in some cases: " + clipped + " of " + n);
	}

	/**
	 * The mixin's pure function ({@link TreeClip#maxFreeHeight}) gives the same height whether the neighbour's wall is already in the world or
	 * not; vanilla's loop over the same two worlds does not (the wall stops it).
	 */
	@Test
	void theRoomCheckDoesNotSeeTheWall() {
		Scenario base = Scenario.city("treeclip", gate, planner, window.cx(), window.cz(), window.n(), 0, false, List.of(), true);
		Spot spot = spot(base);
		assertNotNull(spot);
		TreeFeature spruce = (TreeFeature) SimBoot.feature(TreeFeatures.SPRUCE);
		var terrain = gate.probes().exact();
		BlockPos pos = spot.call().origin();
		int cx = Math.floorDiv(pos.getX(), 16), cz = Math.floorDiv(pos.getZ(), 16);
		SimWorld bare = new SimWorld(gate.seed(), cx - 1, cz - 1, 3, 3, terrain, gate.probes().biome());
		SimWorld built = new SimWorld(gate.seed(), cx - 1, cz - 1, 3, 3, terrain, gate.probes().biome());
		new SimLevel(built, cx, cz, "ours:wall", 1L).proxy().setBlock(spot.wall(), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
		int seen = 0;
		for (int h = 5; h <= 10; h++) {
			WorldGenLevel b = new SimLevel(bare, cx, cz, "t", 1L).proxy(), w = new SimLevel(built, cx, cz, "t", 1L).proxy();
			TreeClip.Free fb = q -> spruce.trunkPlacer().isFree(b, q) && !TreeFeature.isVine(b, q), fw = q -> spruce.trunkPlacer().isFree(w, q) && !TreeFeature.isVine(w, q);
			Integer ob = TreeClip.maxFreeHeight(planner, spruce, h, pos, fb), ow = TreeClip.maxFreeHeight(planner, spruce, h, pos, fw);
			assertNotNull(ob, "the room meets our claims");
			assertEquals(ob, ow, "height " + h + ": same answer with and without the wall");
			if (TreeClip.loop(spruce, h, pos, fb) != TreeClip.loop(spruce, h, pos, fw)) seen++;
		}
		assertTrue(seen > 0, "vanilla's loop does see the wall at " + spot.wall());
	}

	/** A dark oak whose 2 x 2 base reaches into a building of the next chunk at ground level, placing on bare terrain. */
	static SimFeatures.Call darkOakIntoABuilding(Scenario base) {
		Scenario.Plan p = base.plan();
		var terrain = gate.probes().exact();
		for (long k : p.innerKeys) {
			if (Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir()) continue;
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			for (int[] d : new int[][] {{-1, -1}, {-1, 0}, {0, -1}}) {
				int ox = x + d[0], oz = z + d[1];
				if (Math.floorDiv(ox, 16) == Math.floorDiv(x, 16) && Math.floorDiv(oz, 16) == Math.floorDiv(z, 16)) continue;
				int oy = terrain.floorY(ox, oz);
				if (y != oy - 1 && y != oy) continue; // the base row or the first trunk row is the wall's
				if (!planner.claimRegion(new dev.fractalbuildings.core.ir.Box3(x, y, z, 1, 1, 1)).containsBuilding(x, y, z)) continue;
				if (claimed(p, ox, oy - 1, oy + 12, oz)) continue; // the origin column itself is natural
				SimFeatures.Call c = SimFeatures.scripted("dark_oak", ox, oy, oz, gate.seed(), terrain, gate.probes().biome());
				if (c != null) return c;
			}
		}
		return null;
	}

	/**
	 * Without the 2 x 2 veto (sim switch) a dark oak that the natural-terrain room check lets stand writes its below-trunk dirt into the
	 * neighbour's building whenever the building came first (C4 in this spaced scenario, the dirt beside a log passing C3 as spill; C3 in SC1); with it the tree is not placed in any order and the checks pass.
	 */
	@Test
	void aDarkOakWhoseBaseReachesIntoABuildingIsNotPlaced() {
		Scenario base = Scenario.city("treeclip-2x2", gate, planner, window.cx(), window.cz(), window.n(), 0, false, List.of(), true);
		SimFeatures.Call call = darkOakIntoABuilding(base);
		assertNotNull(call, "a dark oak spot whose base reaches into a building across a chunk line in SC1");
		Scenario s = new Scenario("treeclip-2x2", planner, gate.seed(), gate.probes().exact(), gate.probes().biome(), window.cx(), window.cz(), window.n(), List.of(call), true);
		List<SimOrders.Order> orders = SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed);
		SimFeatures.trunkVeto = false;
		OrderSim.Result off;
		try { off = OrderSim.runAll(s, orders, false); } finally { SimFeatures.trunkVeto = true; }
		SimReport.write(off);
		System.out.println("[ordersim] treeclip-2x2 at " + call.origin() + " without the veto: " + off.failed());
		assertTrue(off.runs.stream().allMatch(r -> r.features.placed() == 1), "without the veto the dark oak stands in every order: " + off.runs.get(0).features);
		assertTrue(off.failed().stream().anyMatch(f -> f.contains("C3") || f.contains("C4")), "and its dirt changes claimed cells of the building by order: " + off.failed());
		OrderSim.Result on = OrderSim.runAll(s, orders, false);
		SimReport.write(on);
		assertEquals(List.of(), on.failed());
		assertTrue(on.runs.stream().allMatch(r -> r.features.placed() == 0 && r.features.vetoed() == 1), "vetoed in every order: " + on.runs.get(0).features);
	}
}
