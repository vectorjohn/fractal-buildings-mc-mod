package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.compat.CityVeto;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * B7's compatibility matrix row, run once per mod set ({@code -PmodSet=...}, see mod/build.gradle): a normal singleplayer world on seed
 * 987654321 with whatever worldgen mods the run loaded. It finds the nearest settlement with the world's own planner, generates one of its
 * tiles through the ordinary pipeline (cost per chunk), checks the
 * veto against the real world (T3: villages inside a footprint plus margin have no start, {@code /locate} returns one outside, villages
 * outside still generate), takes a screenshot of the untouched generated tile, then places the start in two chunk orders and compares the
 * ledger digests (that clears the tile, so it comes last). Every result is a {@code [B7-MATRIX]} log line the matrix script collects.
 */
public class CompatClientGameTest implements FabricClientGameTest {
	static final TagKey<Structure> VILLAGE = TagKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village"));

	/** The names of the worldgen mods loaded in this run, e.g. {@code lithostitched+terralith}; {@code vanilla} when none. */
	static String label() {
		List<String> l = new ArrayList<>();
		for (String m : new String[] {"terrablender", "lithostitched", "terralith", "tectonic"}) if (FabricLoader.getInstance().isModLoaded(m)) l.add(m);
		return l.isEmpty() ? "vanilla" : String.join("+", l);
	}

	private static void line(String set, String msg) { FractalBuildingsMod.LOGGER.info("[B7-MATRIX] set={} {}", set, msg); }

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		String set = label();
		if (dev.fractalbuildings.gametest.flythrough.FlyThroughClientGameTest.skipOthers()) return; // B9: a fly-only run
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<int[]> view = new AtomicReference<>();
			AtomicReference<DistrictGrid.Tile> tileRef = new AtomicReference<>();
			AtomicReference<WorldPlanner> plannerRef = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				long t00 = System.nanoTime();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow(() -> new IllegalStateException("no planner in a normal world"));
				Optional<SettlementInfo> n = Optional.empty();
				for (int r = 1; r <= 4 && n.isEmpty(); r++) n = planner.nearest(0, 0, Tier.VILLAGE, r);
				for (int r = 4; r <= 8 && n.isEmpty(); r += 2) n = planner.nearest(0, 0, Tier.HAMLET, r);
				long searchMs = (System.nanoTime() - t00) / 1_000_000;
				SettlementInfo info = n.orElseThrow(() -> new IllegalStateException("no settlement within 8 cells of the origin"));
				int sx = info.site().centre().x(), sz = info.site().centre().z();
				line(set, "settlement=" + info.site().id() + " tier=" + info.tier().json + " at=" + sx + "," + sz + " radius=" + info.site().radius() + " searchMs=" + searchMs);

				// a planned tile of it, nearest the centre
				DistrictGrid.Tile t = planner.grid().tileAt(sx, sz), pick = null;
				for (int d = 0; d <= 4 && pick == null; d++) {
					for (int dz = -d; dz <= d && pick == null; dz++) {
						for (int dx = -d; dx <= d && pick == null; dx++) {
							if (planner.tile(t.ix() + dx, t.iz() + dz).isPresent() && !planner.buildings(t.ix() + dx, t.iz() + dz).isEmpty()) pick = planner.grid().tile(t.ix() + dx, t.iz() + dz);
						}
					}
				}
				if (pick == null) throw new IllegalStateException("no planned tile near " + t);
				int chunks = ((pick.maxX() - pick.minX()) >> 4) * ((pick.maxZ() - pick.minZ()) >> 4);
				long t1 = System.nanoTime();
				for (int cz = pick.minZ() >> 4; cz < pick.maxZ() >> 4; cz++) for (int cx = pick.minX() >> 4; cx < pick.maxX() >> 4; cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
				long genMs = (System.nanoTime() - t1) / 1_000_000;
				StructureStart start = level.getChunk(pick.originChunkX(), pick.originChunkZ()).getAllStarts().values().stream().filter(StructureStart::isValid).findFirst().orElse(null);
				if (start == null) throw new IllegalStateException("no valid structure start at the tile's centre chunk");
				if (WorldPlanners.FAILURES.get() != 0) throw new IllegalStateException("structures swallowed exceptions, see the log");
				line(set, "tile=" + pick.ix() + "," + pick.iz() + " chunks=" + chunks + " genMs=" + genMs + " msPerChunk=" + genMs / Math.max(1, chunks) + " pieces=" + start.getPieces().size());

				vetoInWorld(set, level, planner, sx, sz);
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, (pick.minX() + pick.maxX()) / 2, (pick.minZ() + pick.maxZ()) / 2);
				tileRef.set(pick);
				plannerRef.set(planner);
				view.set(new int[] {(pick.minX() + pick.maxX()) / 2, top, pick.maxZ()});
			});
			int[] v = view.get();
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p " + v[0] + " " + (v[1] + 45) + " " + (v[2] + 20) + " 180 38");
			context.waitTicks(100);
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				FractalBuildingsMod.LOGGER.warn("[B7] chunks did not finish rendering in time; taking the screenshot anyway");
			}
			context.waitTicks(40);
			context.takeScreenshot("compat-" + set.replace('+', '_') + "-overview");
			// the digest check clears and re-places the tile, so it runs after the screenshot of the untouched generated tile
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = plannerRef.get();
				DistrictGrid.Tile pick = tileRef.get();
					// the same start placed in two chunk orders: equal digests (B2's check, on this mod set's terrain)
					String path = planner.tile(pick.ix(), pick.iz()).orElseThrow().site().tier().json;
					StructureStart b = StartSupport.start(level, path, new ChunkPos(pick.originChunkX(), pick.originChunkZ()));
					StartSupport.check(b.isValid() && !b.getPieces().isEmpty(), "a fresh start of the tile");
					List<ChunkPos> asc = new ArrayList<>(StartSupport.chunks(b));
					asc.sort((x, y) -> x.z() != y.z() ? Integer.compare(x.z(), y.z()) : Integer.compare(x.x(), y.x()));
					List<ChunkPos> desc = new ArrayList<>(asc);
					java.util.Collections.reverse(desc);
					StartSupport.clear(level, b);
					StartSupport.Placed one = StartSupport.place(level, b, asc, 1);
					StartSupport.clear(level, b);
					StartSupport.Placed two = StartSupport.place(level, b, desc, 2);
					boolean equal = one.digest().equals(two.digest());
					line(set, "digestEqual=" + equal + " cells=" + one.cells().size() + " outsideChunk=" + (one.outsideChunk() + two.outsideChunk()));
					if (!equal) throw new IllegalStateException("ledger digests differ between two chunk orders");
			});
			line(set, "done");
		}
	}

	/** T3 in a real world: villages near the settlement, split by the planner's own footprint question, then what the game does with each. */
	private static void vetoInWorld(String set, ServerLevel level, WorldPlanner planner, int sx, int sz) {
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var state = level.getChunkSource().getGeneratorState();
		List<ChunkPos> cands = new ArrayList<>();
		for (Holder<Structure> h : structures.getOrThrow(VILLAGE)) {
			for (StructurePlacement p : state.getPlacementsForStructure(h)) {
				if (!(p instanceof RandomSpreadStructurePlacement rsp)) continue;
				for (int rx = (sx >> 4) / rsp.spacing() - 4; rx <= (sx >> 4) / rsp.spacing() + 4; rx++) {
					for (int rz = (sz >> 4) / rsp.spacing() - 4; rz <= (sz >> 4) / rsp.spacing() + 4; rz++) {
						ChunkPos c = rsp.getPotentialStructureChunk(level.getSeed(), rx * rsp.spacing(), rz * rsp.spacing());
						if (!cands.contains(c)) cands.add(c);
					}
				}
			}
		}
		List<ChunkPos> inside = new ArrayList<>(), outside = new ArrayList<>();
		for (ChunkPos c : cands) (planner.footprintNear(c.getMiddleBlockX(), c.getMiddleBlockZ(), CityVeto.MARGIN) ? inside : outside).add(c);
		java.util.Comparator<ChunkPos> byDist = java.util.Comparator.comparingDouble(c -> Math.hypot(c.getMiddleBlockX() - sx, c.getMiddleBlockZ() - sz));
		inside.sort(byDist);
		outside.sort(byDist);
		int insideStarts = 0, outsideStarts = 0, insideChecked = Math.min(10, inside.size()), outsideChecked = Math.min(10, outside.size());
		for (int i = 0; i < insideChecked; i++) if (hasVillage(level, inside.get(i))) insideStarts++;
		for (int i = 0; i < outsideChecked; i++) if (hasVillage(level, outside.get(i))) outsideStarts++;
		var found = level.findNearestMapStructure(VILLAGE, new BlockPos(sx, 64, sz), 60, false);
		boolean locateOutside = found == null || !planner.footprintNear(found.getX(), found.getZ(), CityVeto.MARGIN);
		line(set, "veto candidates=" + cands.size() + " inside=" + inside.size() + " outside=" + outside.size() + " insideWithStart=" + insideStarts + "/" + insideChecked
			+ " outsideWithStart=" + outsideStarts + "/" + outsideChecked + " locate=" + (found == null ? "none" : found.getX() + "," + found.getZ()) + " locateOutsideFootprint=" + locateOutside);
		if (insideStarts != 0) throw new IllegalStateException("a vanilla village started inside the footprint plus margin");
		if (!locateOutside) throw new IllegalStateException("/locate returned a village inside the footprint plus margin: " + found);
	}

	private static boolean hasVillage(ServerLevel level, ChunkPos c) {
		var chunk = level.getChunk(c.x(), c.z(), ChunkStatus.STRUCTURE_STARTS, true);
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (var e : chunk.getAllStarts().entrySet()) if (e.getValue().isValid() && reg.wrapAsHolder(e.getKey()).is(VILLAGE)) return true;
		return false;
	}
}
