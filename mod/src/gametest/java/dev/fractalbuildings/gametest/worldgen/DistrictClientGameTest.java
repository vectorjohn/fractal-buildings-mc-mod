package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * B1's look at a real world: a normal singleplayer world (seed 987654321, structures on), find the nearest settlement with the world's own
 * planner, generate one of its tiles through the ordinary chunk pipeline, and take an overview and two door screenshots (the door seen from
 * the side its {@code facing} points away from, and from the other side: A4 asked for an in-game look at hinges and facing). Also reads back
 * doors and checks that the tile's starts exist and nothing was swallowed.
 */
public class DistrictClientGameTest implements FabricClientGameTest {
	record Spot(int ix, int iz, int minX, int minZ, int maxX, int maxZ, String tier, int doorX, int doorY, int doorZ, Direction doorFacing) {}

	/** Lets the chunks around the player arrive and render; software rendering of a large view can take longer than the library's own wait, so a timeout only logs. */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		context.waitTicks(ticks);
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			FractalBuildingsMod.LOGGER.warn("[B1] chunks did not finish rendering in time; taking the screenshot anyway");
		}
		context.waitTicks(40);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("DistrictClientGameTest")) return;
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<Spot> spot = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow(() -> new IllegalStateException("no planner in a normal world"));
				long t0 = System.nanoTime();
				Optional<SettlementInfo> n = Optional.empty();
				for (int r = 1; r <= 3 && n.isEmpty(); r++) n = planner.nearest(0, 0, Tier.VILLAGE, r);
				FractalBuildingsMod.LOGGER.info("[B1] nearest village+ search {} ms: {}", (System.nanoTime() - t0) / 1_000_000, n.map(i -> i.site().id() + " " + i.site().tier() + " at " + i.site().centre().x() + "," + i.site().centre().z()).orElse("none"));
				SettlementInfo info = n.orElseThrow(() -> new IllegalStateException("no village-or-larger settlement within 3 cells of the origin"));
				DistrictGrid.Tile t = planner.grid().tileAt(info.site().centre().x(), info.site().centre().z());
				// the settlement centre's tile is planned unless the centre sits on a lane; take the nearest planned tile of its settlement
				DistrictGrid.Tile pick = null;
				for (int d = 0; d <= 3 && pick == null; d++) {
					for (int dz = -d; dz <= d && pick == null; dz++) {
						for (int dx = -d; dx <= d && pick == null; dx++) {
							if (planner.tile(t.ix() + dx, t.iz() + dz).isPresent() && !planner.buildings(t.ix() + dx, t.iz() + dz).isEmpty()) pick = planner.grid().tile(t.ix() + dx, t.iz() + dz);
						}
					}
				}
				if (pick == null) throw new IllegalStateException("no planned tile near " + t);
				long t1 = System.nanoTime();
				for (int cz = pick.minZ() >> 4; cz < pick.maxZ() >> 4; cz++) for (int cx = pick.minX() >> 4; cx < pick.maxX() >> 4; cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
				FractalBuildingsMod.LOGGER.info("[B1] generated tile {},{} ({} chunks) in {} ms", pick.ix(), pick.iz(), ((pick.maxX() - pick.minX()) >> 4) * ((pick.maxZ() - pick.minZ()) >> 4), (System.nanoTime() - t1) / 1_000_000);
				StructureStart start = level.getChunk(pick.originChunkX(), pick.originChunkZ()).getAllStarts().values().stream().filter(s -> s.isValid()).findFirst().orElse(null);
				if (start == null) throw new IllegalStateException("no valid structure start at the tile's centre chunk");
				if (WorldPlanners.FAILURES.get() != 0) throw new IllegalStateException("structures swallowed exceptions, see the log");
				// read every door back; remember the one nearest the tile centre that has air on both sides
				int tmx = (pick.minX() + pick.maxX()) / 2, tmz = (pick.minZ() + pick.maxZ()) / 2;
				int doors = 0, doorsOk = 0;
				long best = Long.MAX_VALUE;
				BlockPos bestPos = null;
				Direction bestFacing = null;
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
				for (int z = pick.minZ(); z < pick.maxZ(); z++) for (int x = pick.minX(); x < pick.maxX(); x++) {
					int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
					for (int y = top - 1; y >= top - 40; y--) {
						BlockState st = level.getBlockState(p.set(x, y, z));
						if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
						doors++;
						BlockState up = level.getBlockState(p.set(x, y + 1, z));
						if (up.is(st.getBlock()) && up.getValue(DoorBlock.FACING) == st.getValue(DoorBlock.FACING) && up.getValue(DoorBlock.HINGE) == st.getValue(DoorBlock.HINGE)) doorsOk++;
						Direction f = st.getValue(DoorBlock.FACING);
						boolean open = level.getBlockState(p.set(x - 2 * f.getStepX(), y, z - 2 * f.getStepZ())).isAir() && level.getBlockState(p.set(x + 2 * f.getStepX(), y, z + 2 * f.getStepZ())).isAir();
						long d = (long) (x - tmx) * (x - tmx) + (long) (z - tmz) * (z - tmz);
						if (open && d < best) { best = d; bestPos = new BlockPos(x, y, z); bestFacing = f; }
					}
				}
				String readback = "doors " + doors + " (matching halves " + doorsOk + ")";
				FractalBuildingsMod.LOGGER.info("[B1] tile {},{} tier {}: {} pieces in the start, {}; screenshot door {} facing {}", pick.ix(), pick.iz(), info.site().tier(), start.getPieces().size(), readback, bestPos, bestFacing);
				if (doors == 0 || doors != doorsOk) throw new IllegalStateException("door readback: " + readback);
				if (bestPos == null) throw new IllegalStateException("no door with free cells on both sides");
				spot.set(new Spot(pick.ix(), pick.iz(), pick.minX(), pick.minZ(), pick.maxX(), pick.maxZ(), info.site().tier().json, bestPos.getX(), bestPos.getY(), bestPos.getZ(), bestFacing));
			});
			Spot s = spot.get();
			int midX = (s.minX() + s.maxX()) / 2, midZ = (s.minZ() + s.maxZ()) / 2;
			AtomicReference<Integer> y = new AtomicReference<>(100);
			world.getServer().runOnServer(server -> y.set(server.overworld().getHeight(Heightmap.Types.WORLD_SURFACE, midX, midZ)));
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("time set noon");
			// from the south of the tile, looking north (yaw 180) and down
			world.getServer().runCommand("tp @p " + midX + " " + (y.get() + 45) + " " + (s.maxZ() + 20) + " 180 38");
			settle(context, world, 100);
			context.takeScreenshot("district-overview");
			// the door, 3 blocks away on each side, looking at it (yaw of a direction: south 0, west 90, north 180, east 270)
			Direction f = s.doorFacing();
			double cx = s.doorX() + 0.5, cz = s.doorZ() + 0.5;
			world.getServer().runCommand(String.format("tp @p %.1f %d %.1f %s 5", cx - 3.0 * f.getStepX(), s.doorY(), cz - 3.0 * f.getStepZ(), f.toYRot()));
			settle(context, world, 60);
			context.takeScreenshot("district-door-a");
			world.getServer().runCommand(String.format("tp @p %.1f %d %.1f %s 5", cx + 3.0 * f.getStepX(), s.doorY(), cz + 3.0 * f.getStepZ(), f.getOpposite().toYRot()));
			settle(context, world, 60);
			context.takeScreenshot("district-door-b");
			FractalBuildingsMod.LOGGER.info("[B1] client test done: {} tile {},{}", s.tier(), s.ix(), s.iz());
		}
	}
}
