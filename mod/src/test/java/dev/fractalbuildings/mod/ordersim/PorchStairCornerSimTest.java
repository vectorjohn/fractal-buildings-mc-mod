package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.junit.jupiter.api.Test;

/**
 * ORDERSTAIR, the order test's {@code stone_brick_stairs->stone_brick_stairs [unknown]} (HEAVY14, {@code forward}, 1222,67,-9441) as a
 * scenario. A porch (or interior) flight that turns at a landing has a stair turned across in front of its last step; Minecraft's
 * {@code StairBlock.getStairsShape} gives that step an outer corner. The raster used to leave it straight, so the first vanilla
 * {@code updateShape} on it (a tree of a neighbour chunk decorated later ends with {@code StructureTemplate.updateShapeAtEdge}) turned it into
 * the corner, in some chunk orders only. Here the neighbour chunk runs that real call on the stair at its own decoration; every fast order must
 * keep the reference raster's state. Before the fix (Shapes: STAIR cells straight) the orders where the neighbour comes after the stair's chunk
 * fail.
 */
class PorchStairCornerSimTest {
	static final RegressionScenarios R = RegressionScenarios.get();

	record Spot(int x, int y, int z, int ncx, int ncz, Scenario scenario) {}

	static Spot spot;

	static long key(int x, int y, int z) { return PieceLedger.pack(x, y, z); }

	/** The first (tile order, then cell order) interior/porch stair with a stair turned across in front of or behind it, at the same height. */
	static synchronized Spot spot() {
		if (spot != null) return spot;
		var g = R.gate;
		TreeSet<Long> tiles = new TreeSet<>();
		for (int x = g.x() - g.radius(); x <= g.x() + g.radius(); x += 32)
			for (int z = g.z() - g.radius(); z <= g.z() + g.radius(); z += 32) {
				var t = R.planner.tileAt(x, z);
				tiles.add(((long) t.ix() << 32) | (t.iz() & 0xffffffffL));
			}
		Box3 all = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);
		for (long tk : tiles) {
			int ix = (int) (tk >> 32), iz = (int) tk;
			if (R.planner.tile(ix, iz).isEmpty()) continue;
			TreeMap<Long, Facing> stairs = new TreeMap<>(), porch = new TreeMap<>();
			for (BuildingPiece b : R.planner.buildings(ix, iz)) R.planner.raster(b.ref(), all, (x, y, z, role, id, shape) -> {
				if (shape.facing() == null || !(role == Role.STAIR || role.isRoofStair())) return;
				stairs.putIfAbsent(key(x, y, z), shape.facing());
				if (role == Role.STAIR) porch.putIfAbsent(key(x, y, z), shape.facing());
			});
			for (var e : porch.entrySet()) {
				int x = PieceLedger.x(e.getKey()), y = PieceLedger.y(e.getKey()), z = PieceLedger.z(e.getKey());
				Facing f = e.getValue();
				Facing front = stairs.get(key(x + f.dx, y, z + f.dz)), back = stairs.get(key(x - f.dx, y, z - f.dz));
				boolean turn = front != null && front.ordinal() % 2 != f.ordinal() % 2 || back != null && back.ordinal() % 2 != f.ordinal() % 2;
				if (!turn) continue;
				int cx = x >> 4, cz = z >> 4;
				// the neighbour chunk across the nearest x edge: its decoration updates the stair (as its tree would)
				int ncx = (x & 15) < 8 ? cx - 1 : cx + 1, ncz = cz;
				Scenario base = Scenario.city("porchstair", g, R.planner, cx - 1, cz - 1, 3, 0, false, List.of(), false);
				BlockPos at = new BlockPos(x, y, z);
				Scenario.Extra tree = (world, ccx, ccz) -> {
					if (ccx != ncx || ccz != ncz) return;
					BitSetDiscreteVoxelShape one = new BitSetDiscreteVoxelShape(1, 1, 1);
					one.fill(0, 0, 0);
					StructureTemplate.updateShapeAtEdge(new SimLevel(world, ccx, ccz, "feature:tree-edge@" + ccx + "," + ccz, 3L).proxy(), 3, one, at.getX(), at.getY(), at.getZ());
				};
				spot = new Spot(x, y, z, ncx, ncz, base.withExtras("porchstair", false, tree));
				System.out.println("[porchstair] stair " + f + " at " + x + "," + y + "," + z + " (front " + front + ", back " + back + "), updated from chunk " + ncx + "," + ncz);
				return spot;
			}
		}
		throw new AssertionError("no interior/porch stair at a landing turn in " + g.name());
	}

	@Test
	void aTreeUpdateOnAStairAtALandingTurnChangesNothingInAnyOrder() {
		Spot sp = spot();
		Scenario s = sp.scenario();
		int i = java.util.Arrays.binarySearch(s.plan().innerKeys, key(sp.x(), sp.y(), sp.z()));
		assertTrue(i >= 0, "the spot is an inner reference cell");
		BlockState want = Block.BLOCK_STATE_REGISTRY.byId(s.plan().ref.get(key(sp.x(), sp.y(), sp.z())));
		assertInstanceOf(StairBlock.class, want.getBlock(), "the spot is a stair");
		System.out.println("[porchstair] raster state " + want);
		OrderSim.Result res = OrderSim.runAll(s, SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed), true);
		SimReport.write(res);
		List<String> bad = new java.util.ArrayList<>();
		for (SimRun r : res.runs) {
			BlockState have = Block.BLOCK_STATE_REGISTRY.byId(r.have[i]);
			if (!have.equals(want)) bad.add(r.order + ": " + have);
		}
		assertEquals(List.of(), bad, "orders where the update turned the stair away from the raster's " + want);
		assertEquals(List.of(), res.failed());
	}
}
