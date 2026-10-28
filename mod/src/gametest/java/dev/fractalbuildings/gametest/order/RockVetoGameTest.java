package dev.fractalbuildings.gametest.order;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.gametest.worldgen.TestArea;
import dev.fractalbuildings.mod.worldgen.RockVeto;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROCKVETO: the one fast check that the {@code BlockBlobFeatureMixin} is applied (plain JUnit cannot apply mixins). The registry's real
 * {@code forest_rock} feature is placed over a stone block around an origin whose {@link RockVeto#box} meets a building of a planned town:
 * it must return false, count in {@link RockVeto#VETOED} and leave the stone alone. Controls: the same boulder with the veto switched off
 * ({@link Mutations#ROCKS}) is placed (so the stone does change), and a boulder high above the town is not counted. Without the mixin
 * (or with its target renamed) the first check fails.
 */
// gametest-order: 8
public class RockVetoGameTest {
	private static final int HALF = 10;

	private static WorldPlanner planner() {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(6, WorldConfig.defaults(), pr, null);
	}

	private static void fill(ServerLevel level, BlockPos o, boolean stone) {
		for (int y = -HALF / 2 - 1; y <= HALF / 2 + 1; y++)
			for (int z = -HALF - 1; z <= HALF + 1; z++)
				for (int x = -HALF - 1; x <= HALF + 1; x++)
					level.setBlock(o.offset(x, y, z), stone ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
	}

	/** Cells of the boulder's neighbourhood that are not stone. */
	private static int notStone(ServerLevel level, BlockPos o) {
		int n = 0;
		for (int y = -HALF / 2 - 1; y <= HALF / 2 + 1; y++)
			for (int z = -HALF - 1; z <= HALF + 1; z++)
				for (int x = -HALF - 1; x <= HALF + 1; x++)
					if (!level.getBlockState(o.offset(x, y, z)).is(Blocks.STONE)) n++;
		return n;
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void theRockMixinVetoesABoulderOverABuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = planner();
		SettlementInfo n = w.nearest(0, 0, Tier.HAMLET, 2).orElseThrow(() -> new IllegalStateException("flat plains with seed 6 should have a settlement near the origin"));
		DistrictGrid.Tile t = w.grid().tileAt(n.site().centre().x(), n.site().centre().z());
		TileInfo info = w.tile(t.ix(), t.iz()).orElseThrow();
		List<BoundingBox> area = TestArea.boxes(level, w, new ChunkPos(t.originChunkX(), t.originChunkZ()), info.tier().json, "district_ground");
		check(!area.isEmpty(), "the town has a start");
		TestArea.run(helper, area, () -> {
			Feature boulder = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(Identifier.withDefaultNamespace("forest_rock"));
			check(boulder != null, "the registry has the forest_rock feature");
			// a building well inside the loaded chunks: the one whose centre is closest to the middle of the start boxes
			BoundingBox all = area.get(0);
			for (BoundingBox b : area) all = BoundingBox.encapsulatingBoxes(List.of(all, b)).orElseThrow();
			int mx = (all.minX() + all.maxX()) / 2, mz = (all.minZ() + all.maxZ()) / 2;
			Box3 best = null;
			long bd = Long.MAX_VALUE;
			for (BuildingPiece b : w.buildings(t.ix(), t.iz())) {
				Box3 bb = b.box();
				long dx = bb.x() + bb.sx() / 2 - mx, dz = bb.z() + bb.sz() / 2 - mz;
				if (dx * dx + dz * dz < bd) { bd = dx * dx + dz * dz; best = bb; }
			}
			check(best != null, "the town has buildings");
			BlockPos origin = new BlockPos(best.x() + best.sx() / 2, best.y() + 3, best.z() + best.sz() / 2);
			Box3 rockBox = RockVeto.box(origin);
			check(w.claimsNear(rockBox), "the planner claims the boulder box at " + origin);
			WorldPlanners.overrideForTests(w);
			WorldPlanners.FAILURES.set(0);
			try {
				RandomSource rnd = RandomSource.create(1);
				// control: veto off, the boulder is placed and changes the stone
				fill(level, origin, true);
				Mutations.mode = Mutations.ROCKS;
				boolean placed = boulder.place(level, level.getChunkSource().getGenerator(), rnd, origin);
				Mutations.mode = null;
				int changed = notStone(level, origin);
				check(placed && changed > 3, "control: without the veto the boulder is placed (placed=" + placed + ", cells changed " + changed + ")");
				// the veto
				fill(level, origin, true);
				long before = RockVeto.VETOED.get();
				boolean vetoed = !boulder.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1), origin);
				check(vetoed, "a boulder over a building must not be placed (is BlockBlobFeatureMixin applied and its target current?)");
				check(RockVeto.VETOED.get() == before + 1, "RockVeto.VETOED went up by one, was " + before + " now " + RockVeto.VETOED.get());
				check(notStone(level, origin) == 0, "the vetoed boulder left the cells unchanged");
				fill(level, origin, false);
				// far from every piece (high above the town): not vetoed, not counted
				BlockPos far = origin;
				for (int dy = 40; dy < 200 && w.claimsNear(RockVeto.box(far)); dy += 8) far = origin.above(dy);
				check(!w.claimsNear(RockVeto.box(far)), "found a free origin " + far);
				fill(level, far, true);
				long b2 = RockVeto.VETOED.get();
				boolean freePlaced = boulder.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1), far);
				check(freePlaced, "a boulder away from our pieces is placed");
				check(RockVeto.VETOED.get() == b2, "a free boulder is not counted");
				fill(level, far, false);
				check(WorldPlanners.FAILURES.get() == 0, "no swallowed exceptions");
			} finally {
				Mutations.mode = null;
				WorldPlanners.overrideForTests(null);
			}
		});
	}
}
