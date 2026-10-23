package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldHooks;
import dev.fractalbuildings.core.world.WorldHooks.WorldHook;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * B5 in a real world (seed 987654321, structures on): {@code /locate structure #fractalbuildings:settlement}, {@code /fb locate} and
 * {@code /fb info} answer; a generated hamlet-or-village tile holds beds, chests with loot tables and at most 10 villagers; one screenshot
 * of an interior with a bed (and a chest if one is near).
 */
public class GameplayClientGameTest implements FabricClientGameTest {
	record Spot(int x, int y, int z, Direction look, int villagers, int beds, int chests) {}

	/** Runs a command as the server console and returns everything it said. */
	private static String run(MinecraftServer server, String command) { return run(server, command, null); }

	private static String run(MinecraftServer server, String command, net.minecraft.world.phys.Vec3 at) {
		StringBuilder out = new StringBuilder();
		CommandSource sink = new CommandSource() {
			@Override public void sendSystemMessage(Component m) { out.append(m.getString()).append('\n'); }
			@Override public boolean acceptsSuccess() { return true; }
			@Override public boolean acceptsFailure() { return true; }
			@Override public boolean shouldInformAdmins() { return false; }
		};
		var stack = server.createCommandSourceStack().withSource(sink);
		if (at != null) stack = stack.withPosition(at);
		server.getCommands().performPrefixedCommand(stack, command);
		return out.toString();
	}

	private static void check(boolean ok, String msg) { if (!ok) throw new IllegalStateException(msg); }

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed("987654321")).create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<Spot> spot = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				// /locate and /fb locate through the real command dispatcher
				String loc = run(server, "locate structure #fractalbuildings:settlement");
				FractalBuildingsMod.LOGGER.info("[B5] /locate structure #fractalbuildings:settlement -> {}", loc.strip());
				check(loc.contains("nearest") || loc.contains("located") || loc.toLowerCase().contains("the nearest"), "/locate gave: " + loc);
				String fbl = run(server, "fb locate");
				FractalBuildingsMod.LOGGER.info("[B5] /fb locate -> {}", fbl.strip());
				check(fbl.contains("The nearest"), "/fb locate gave: " + fbl);

				// the nearest hamlet or village (villagers live there) and the tile with the most beds
				Optional<SettlementInfo> n = Optional.empty();
				for (int r = 1; r <= 4 && n.isEmpty(); r++) {
					n = planner.nearest(0, 0, Tier.HAMLET, r);
					if (n.isPresent() && !WorldHooks.hasVillagers(n.get().tier())) n = Optional.empty();
				}
				SettlementInfo info = n.orElseThrow(() -> new IllegalStateException("no hamlet or village within 4 cells"));
				DistrictGrid.Tile c = planner.grid().tileAt(info.site().centre().x(), info.site().centre().z());
				DistrictGrid.Tile pick = null;
				int bestBeds = 0;
				for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
					if (planner.tile(c.ix() + dx, c.iz() + dz).filter(t -> t.settlementId().equals(info.site().id())).isEmpty()) continue;
					int beds = 0;
					for (BuildingPiece b : planner.buildings(c.ix() + dx, c.iz() + dz)) for (WorldHook h : WorldHooks.of(b)) if (h.kind().equals("bed")) beds++;
					if (beds > bestBeds) { bestBeds = beds; pick = planner.grid().tile(c.ix() + dx, c.iz() + dz); }
				}
				check(pick != null, "no tile with beds in " + info.site().id());
				for (int cz = pick.minZ() >> 4; cz < pick.maxZ() >> 4; cz++) for (int cx = pick.minX() >> 4; cx < pick.maxX() >> 4; cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
				StructureStart start = level.getChunk(pick.originChunkX(), pick.originChunkZ()).getAllStarts().values().stream().filter(StructureStart::isValid).findFirst().orElseThrow();
				check(WorldPlanners.FAILURES.get() == 0, "structures swallowed exceptions");
				AABB box = new AABB(pick.minX(), level.getMinY(), pick.minZ(), pick.maxX(), level.getMaxY(), pick.maxZ());
				List<Villager> vs = level.getEntitiesOfClass(Villager.class, box);
				int beds = 0, chests = 0, loot = 0;
				List<BlockPos> bedPos = new ArrayList<>();
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
				for (BuildingPiece b : planner.buildings(pick.ix(), pick.iz())) for (WorldHook h : WorldHooks.of(b)) {
					p.set(h.x(), h.y(), h.z());
					BlockState st = level.getBlockState(p);
					if (st.getBlock() instanceof BedBlock) { beds++; bedPos.add(p.immutable()); }
					if (st.is(Blocks.CHEST)) {
						chests++;
						if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity ch && ch.getLootTable() != null) loot++;
					}
				}
				FractalBuildingsMod.LOGGER.info("[B5] real world tile {},{} ({} {}): {} beds, {} chests ({} with loot), {} villagers", pick.ix(), pick.iz(), info.tier(), info.site().id(), beds, chests, loot, vs.size());
				check(beds > 0, "no bed in the generated tile");
				check(chests == loot, "chests without loot table");
				check(vs.size() <= 10, "villagers " + vs.size());
				check(!vs.isEmpty(), "no villager in a " + info.tier() + " tile with " + beds + " beds");
				// /fb info at the first lot of the tile
				BuildingPiece b0 = planner.buildings(pick.ix(), pick.iz()).get(0);
				String fi = run(server, "fb info", new net.minecraft.world.phys.Vec3(b0.brief().x() + b0.brief().w() / 2 + 0.5, 200, b0.brief().z() + b0.brief().d() / 2 + 0.5));
				FractalBuildingsMod.LOGGER.info("[B5] /fb info -> {}", fi.strip());
				check(fi.contains("Settlement") && fi.contains(info.site().id()), "/fb info gave: " + fi);
				// a viewpoint: a bed with two free cells on one side, looking at it
				for (BlockPos bp : bedPos) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						boolean free = true;
						for (int k = 1; k <= 3; k++) for (int dy = 0; dy <= 1; dy++) free &= level.getBlockState(bp.relative(d, k).above(dy)).isAir();
						if (free && spot.get() == null) spot.set(new Spot(bp.relative(d, 3).getX(), bp.getY(), bp.relative(d, 3).getZ(), d.getOpposite(), vs.size(), beds, chests));
					}
				}
				check(spot.get() != null, "no free viewpoint at any bed");
			});
			Spot s = spot.get();
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand(String.format("tp @p %.1f %.1f %.1f %s 20", s.x() + 0.5, s.y() + 1.0, s.z() + 0.5, s.look().toYRot()));
			context.waitTicks(60);
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				FractalBuildingsMod.LOGGER.warn("[B5] chunks did not finish rendering in time; taking the screenshot anyway");
			}
			context.waitTicks(40);
			context.takeScreenshot("gameplay-interior-bed");
			FractalBuildingsMod.LOGGER.info("[B5] client test done: {} villagers, {} beds, {} chests", s.villagers(), s.beds(), s.chests());
		}
	}
}
