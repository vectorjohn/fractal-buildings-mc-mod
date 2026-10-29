package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.StairSeam;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * DS36ROOFS r2, the order test's {@code granite_stairs->granite_stairs [shape]} (spiral and shuffle1, 1435,72,-9361) as a scenario. A vanilla
 * tree ends with {@code StructureTemplate.updateShapeAtEdge} (26.3 {@code TreeFeature.place}, line 188), which re-runs {@code updateShape} on
 * the blocks beside its leaves; a roof stair recomputes its corner from the world at that moment. Here the chunk of a roof stair whose corner
 * comes from a stair across the chunk border runs that real call on it right after its pieces (as a tree of a later neighbour would). In an
 * order where the stair's chunk comes before its neighbour's, the corner was lost for good before the stair seam ({@link StairSeam}); now every
 * fast order keeps the raster's shape. The mutant {@link StairSeam#OFF} shows the scenario catches it.
 */
class RoofStairSeamSimTest {
	static final RegressionScenarios R = RegressionScenarios.get();

	@AfterEach
	void reset() { Mutations.mode = null; }

	/** A roof stair of the plan whose corner shape comes from a stair across a chunk border (both in the scenario's inner window), and the scenario round it. */
	record Spot(int x, int y, int z, BlockState state, Scenario scenario) {}

	static Spot spot;

	static synchronized Spot spot() {
		if (spot != null) return spot;
		ScenarioFinder.Window win = R.finder.densestWindow(4);
		Scenario wide = Scenario.city("roofseam-search", R.gate, R.planner, win.cx(), win.cz(), win.n(), 0, false, List.of(), false);
		Scenario.Plan p = wide.plan();
		for (long k : p.innerKeys) {
			BlockState s = Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k));
			if (!(s.getBlock() instanceof StairBlock) || s.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT) continue;
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			Direction f = s.getValue(StairBlock.FACING);
			boolean outer = s.getValue(StairBlock.SHAPE) == StairsShape.OUTER_LEFT || s.getValue(StairBlock.SHAPE) == StairsShape.OUTER_RIGHT;
			Direction toSource = outer ? f : f.getOpposite(); // StairBlock.getStairsShape: outer from the stair at pos + facing, inner from pos - facing
			int sx = x + toSource.getStepX(), sz = z + toSource.getStepZ();
			if (sx >> 4 == x >> 4 && sz >> 4 == z >> 4) continue;
			int cx = x >> 4, cz = z >> 4;
			// the stair's chunk in the middle of a 3 x 3 inner window, so the neighbour's chunk is decorated too
			Scenario base = Scenario.city("roofseam", R.gate, R.planner, cx - 1, cz - 1, 3, 0, false, List.of(), false);
			BlockPos at = new BlockPos(x, y, z);
			Scenario.Extra tree = (world, ccx, ccz) -> {
				if (ccx != cx || ccz != cz) return;
				BitSetDiscreteVoxelShape one = new BitSetDiscreteVoxelShape(1, 1, 1);
				one.fill(0, 0, 0);
				StructureTemplate.updateShapeAtEdge(new SimLevel(world, ccx, ccz, "feature:tree-edge@" + ccx + "," + ccz, 3L).proxy(), 3, one, at.getX(), at.getY(), at.getZ());
			};
			spot = new Spot(x, y, z, s, base.withExtras("roofseam", false, tree));
			System.out.println("[roofseam] stair " + s + " at " + x + "," + y + "," + z + ", its corner from " + sx + "," + y + "," + sz);
			return spot;
		}
		throw new AssertionError("no roof stair whose corner comes from across a chunk border in window " + win);
	}

	static List<SimOrders.Order> fast(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed); }

	/** The world's state at the spot after the order (the runs keep the inner reference cells, not the world). */
	static BlockState have(Spot sp, SimRun r) {
		int i = java.util.Arrays.binarySearch(sp.scenario().plan().innerKeys, PieceLedger.pack(sp.x(), sp.y(), sp.z()));
		assertTrue(i >= 0, "the spot is an inner reference cell");
		return Block.BLOCK_STATE_REGISTRY.byId(r.have[i]);
	}

	@Test
	void aTreeUpdateBesideARoofStairAtAChunkBorderChangesNothingInAnyOrder() {
		Spot sp = spot();
		OrderSim.Result res = OrderSim.runAll(sp.scenario(), fast(sp.scenario()), true);
		SimReport.write(res);
		assertEquals(List.of(), res.failed());
		int seam = 0;
		for (SimRun r : res.runs) {
			assertEquals(sp.state(), have(sp, r), r.order + ": the roof stair keeps the raster's state");
			seam += r.branchWrites.getOrDefault("StairSeam.write", 0);
		}
		System.out.println("[roofseam] " + res.runs.size() + " orders pass; seam writes in the audit runs " + seam);
		assertTrue(seam > 0, "the seam writes in the audit runs");
	}

	/** The control: without the seam, some fast order loses the corner (C3 unknown solid). */
	@Test
	@Tag("heavy")
	void withoutTheSeamSomeOrderLosesTheCorner() {
		Spot sp = spot();
		Mutations.mode = StairSeam.OFF;
		OrderSim.Result res = OrderSim.runAll(sp.scenario(), fast(sp.scenario()), false);
		List<String> f = res.failed();
		System.out.println("[roofseam] without the seam: " + f.size() + " failure(s), first: " + (f.isEmpty() ? "-" : f.get(0)));
		long lost = res.runs.stream().filter(r -> !have(sp, r).equals(sp.state())).count();
		assertTrue(lost > 0, "some order reshapes the stair");
		assertFalse(f.isEmpty(), "the sim's checks see it");
	}
}
