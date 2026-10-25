package dev.fractalbuildings.gametest.order;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.gametest.worldgen.StartSupport;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * B2's server-side readbacks over a real {@link WorldPlanner} on flat plains (the flat game-test world cannot generate structures, so starts
 * are made by hand as in B1). Each check has a mutant switch ({@link Mutations}) that the same readback must catch:
 * <ul>
 * <li>block states in the world equal what the raster chose, fences, panes and walls that cross a chunk border included
 * (flag 2 alone lets the world re-shape them in write order);</li>
 * <li>room interiors are air even when the tile was solid before (a mutant skipping air writes leaves stone);</li>
 * </ul>
 */
// gametest-order: 7
public class OrderGameTest {
	/** Runs {@code body} with the planner installed and the mutation switch off again afterwards. */
	private static void with(WorldPlanner planner, Runnable body) {
		WorldPlanners.overrideForTests(planner);
		WorldPlanners.FAILURES.set(0);
		try {
			body.run();
			check(WorldPlanners.FAILURES.get() == 0, "the structures swallowed " + WorldPlanners.FAILURES.get() + " exceptions (see the log)");
		} finally {
			WorldPlanners.overrideForTests(null);
			Mutations.mode = null;
			if (last != null && lastLevel != null) { StartSupport.clear(lastLevel, last.building()); StartSupport.clear(lastLevel, last.ground()); }
		}
	}

	/** Flat plains with seed 6: a 62-building town at tile -2,2 (B1). */
	private static WorldPlanner flatPlanner() { return flatPlanner(6); }

	/** Towns of these seeds on flat plains (even seeds) or desert (odd) have fences, walls and panes that flag 2 alone re-shapes (found by probing seeds 1..14). */
	private static final int[] CONNECTING_SEEDS = {2, 5, 10, 11, 12};

	private static WorldPlanner flatPlanner(int seed) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(seed, WorldConfig.defaults(), pr, null);
	}

	private record Town(StructureStart building, StructureStart ground, List<ChunkPos> chunks, DistrictGrid.Tile tile) {}

	private static Town last;
	private static ServerLevel lastLevel;

	private static Town town(ServerLevel level, WorldPlanner w) {
		SettlementInfo n = w.nearest(0, 0, Tier.HAMLET, 2).orElseThrow(() -> new IllegalStateException("flat plains with seed 6 should have a settlement near the origin"));
		DistrictGrid.Tile t = w.grid().tileAt(n.site().centre().x(), n.site().centre().z());
		TileInfo info = w.tile(t.ix(), t.iz()).orElseThrow();
		ChunkPos start = new ChunkPos(t.originChunkX(), t.originChunkZ());
		StructureStart b = StartSupport.start(level, info.tier().json, start);
		StructureStart g = StartSupport.start(level, "district_ground", start);
		check(b.isValid() && g.isValid(), "both starts are valid");
		List<ChunkPos> chunks = new ArrayList<>(StartSupport.chunks(b));
		for (ChunkPos c : StartSupport.chunks(g)) if (!chunks.contains(c)) chunks.add(c);
		chunks.sort((x, y) -> x.z() != y.z() ? Integer.compare(x.z(), y.z()) : Integer.compare(x.x(), y.x()));
		last = new Town(b, g, chunks, t);
		lastLevel = level;
		return last;
	}

	/** Places both starts, chunk by chunk in {@code order}; the merged ledger cells. */
	private static StartSupport.Placed placeBoth(ServerLevel level, Town t, List<ChunkPos> order) {
		TreeMap<Long, Integer> cells = new TreeMap<>();
		TreeMap<Long, Integer> hooks = new TreeMap<>();
		int outside = 0, dups = 0;
		for (ChunkPos c : order) {
			for (StructureStart s : List.of(t.building(), t.ground())) {
				StartSupport.Placed one = StartSupport.place(level, s, List.of(c), 1);
				outside += one.outsideChunk();
				dups += one.duplicates();
				hooks.putAll(one.hooks());
				for (var e : one.cells().entrySet()) if (cells.put(e.getKey(), e.getValue()) != null) dups++;
			}
		}
		return new StartSupport.Placed(cells, outside, dups, hooks);
	}

	private static List<ChunkPos> shuffled(List<ChunkPos> in, long seed) {
		List<ChunkPos> out = new ArrayList<>(in);
		Collections.shuffle(out, new Random(seed));
		return out;
	}

	private static boolean connecting(BlockState s) {
		return s.getBlock() instanceof CrossCollisionBlock || s.getBlock() instanceof WallBlock;
	}

	/** Blocks whose world state differs from the raster's, and how many of the connecting kind (fence, pane, wall) lie on a chunk border column. */
	private record Readback(int cells, int mismatches, int connecting, int connectingOnBorder, int connectingMismatches, int connectingBorderMismatches, String firstMismatch, HookReadback.Result hook) {}

	private static Readback readBack(ServerLevel level, StartSupport.Placed placed) {
		Map<Long, Integer> cells = placed.cells();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int n = 0, bad = 0, conn = 0, connBorder = 0, connBad = 0, connBorderBad = 0;
		String first = "";
		for (var e : cells.entrySet()) {
			BlockState want = Block.BLOCK_STATE_REGISTRY.byId(e.getValue());
			if (want == null || want.isAir() || placed.hooks().containsKey(e.getKey())) continue;
			long k = e.getKey();
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			BlockState have = level.getBlockState(p.set(x, y, z));
			n++;
			boolean c = connecting(want);
			boolean border = (x & 15) == 0 || (x & 15) == 15 || (z & 15) == 0 || (z & 15) == 15;
			if (c) { conn++; if (border) connBorder++; }
			if (have != want) {
				bad++;
				if (c) { connBad++; if (border) connBorderBad++; }
				if (first.isEmpty()) first = x + "," + y + "," + z + " want " + want + " have " + have;
			}
		}
		return new Readback(n, bad, conn, connBorder, connBad, connBorderBad, first, HookReadback.check(level, placed.hooks()));
	}

	/** Places the towns of {@link #CONNECTING_SEEDS} in {@code reversed} or ascending chunk order and adds up the readbacks. */
	private static Readback readTowns(ServerLevel level, boolean reversed, String mode) {
		int cells = 0, bad = 0, conn = 0, connBorder = 0, connBad = 0, connBorderBad = 0;
		String first = "";
		HookReadback.Result hook = HookReadback.Result.NONE;
		for (int seed : CONNECTING_SEEDS) {
			WorldPlanner w = flatPlanner(seed);
			final String[] one = new String[1];
			final Readback[] box = new Readback[1];
			with(w, () -> {
				Town t = town(level, w);
				Mutations.mode = mode;
				List<ChunkPos> order = new ArrayList<>(t.chunks());
				if (reversed) Collections.reverse(order);
				box[0] = readBack(level, placeBoth(level, t, order));
			});
			Readback r = box[0];
			cells += r.cells(); bad += r.mismatches(); conn += r.connecting(); connBorder += r.connectingOnBorder(); connBad += r.connectingMismatches(); connBorderBad += r.connectingBorderMismatches();
			if (first.isEmpty()) first = r.firstMismatch();
			hook = hook.plus(r.hook());
		}
		return new Readback(cells, bad, conn, connBorder, connBad, connBorderBad, first, hook);
	}

	@GameTest
	public void worldStatesEqualTheRasterAcrossChunkBorders(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (boolean reversed : new boolean[] {false, true}) {
			Readback r = readTowns(level, reversed, null);
			FractalBuildingsMod.LOGGER.info("[B2] flag readback (reversed={}): {}", reversed, r);
			check(r.connecting() > 100 && r.connectingOnBorder() > 0, "the towns have fences, panes or walls (" + r.connecting() + ") on chunk borders (" + r.connectingOnBorder() + ")");
			check(r.hook().ok() && r.hook().beds() > 0, "hook readback (beds must exist and have both halves): " + r.hook());
			check(r.mismatches() == 0, r.mismatches() + " of " + r.cells() + " blocks differ from the raster, first " + r.firstMismatch());
		}
		helper.succeed();
	}

	@GameTest
	public void mutantWithFlag2AloneIsCaughtByTheReadback(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Readback r = readTowns(level, true, Mutations.FLAG2);
		FractalBuildingsMod.LOGGER.info("[B2] flag2 mutant readback: {}", r);
		check(r.connectingMismatches() > 0 && r.connectingBorderMismatches() > 0, "flag 2 alone should re-shape fences, panes or walls, some on chunk borders, so they differ from the raster; got (" + r + ")");
		helper.succeed();
	}

	@GameTest
	public void hooksAreWhatTheLedgerSaysInAnyChunkOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int totalBeds = 0;
		for (int seed : new int[] {6, 2, 12}) {
			WorldPlanner w = flatPlanner(seed);
			final int[] beds = {0};
			with(w, () -> {
				Town t = town(level, w);
				for (int i = 0; i < 12; i++) {
					StartSupport.clear(level, t.building());
					StartSupport.clear(level, t.ground());
					HookReadback.Result r = HookReadback.check(level, placeBoth(level, t, i == 0 ? t.chunks() : shuffled(t.chunks(), i)).hooks());
					FractalBuildingsMod.LOGGER.info("[B2] hooks seed {} order {}: {}", seed, i, r);
					beds[0] += r.beds();
					check(r.ok(), "hook readback in order " + i + " of seed " + seed + ": " + r);
				}
			});
			totalBeds += beds[0];
		}
		check(totalBeds > 1000, "beds were read back: " + totalBeds);
		helper.succeed();
	}

	/** Fills the building start's box with stone, places, and counts the claimed-air cells that did not become air. */
	private static int[] solidInteriorTest(ServerLevel level, WorldPlanner w) {
		Town t = town(level, w);
		BoundingBox bb = t.building().getBoundingBox();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = bb.minY() - 6; y <= bb.maxY() + 2; y++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) for (int x = bb.minX(); x <= bb.maxX(); x++) {
			level.setBlock(p.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
		}
		StartSupport.Placed placed = placeBoth(level, t, t.chunks());
		int air = 0, notAir = 0;
		for (var e : placed.cells().entrySet()) {
			if (!Block.BLOCK_STATE_REGISTRY.byId(e.getValue()).isAir() || placed.hooks().containsKey(e.getKey())) continue;
			long k = e.getKey();
			air++;
			if (!level.getBlockState(p.set(PieceLedger.x(k), PieceLedger.y(k), PieceLedger.z(k))).isAir()) notAir++;
		}
		StartSupport.clear(level, t.building());
		StartSupport.clear(level, t.ground());
		return new int[] {air, notAir};
	}

	@GameTest
	public void roomInteriorsAreAirEvenWhenTheTileWasSolid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = flatPlanner();
		with(w, () -> {
			int[] r = solidInteriorTest(level, w);
			FractalBuildingsMod.LOGGER.info("[B2] solid-tile air clearing: {} claimed air cells, {} not air", r[0], r[1]);
			check(r[0] > 1000, "the town claims air (rooms): " + r[0]);
			check(r[1] == 0, r[1] + " of " + r[0] + " claimed air cells are still solid");
		});
		helper.succeed();
	}

	@GameTest
	public void mutantSkippingAirWritesIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = flatPlanner();
		with(w, () -> {
			Mutations.mode = Mutations.SKIP_AIR;
			int[] r = solidInteriorTest(level, w);
			FractalBuildingsMod.LOGGER.info("[B2] skip-air mutant: {} claimed air cells, {} not air", r[0], r[1]);
			check(r[1] > 0, "skipping air writes should leave solid cells inside claimed air; none did");
		});
		helper.succeed();
	}

	@GameTest
	public void mutantWithSharedCounterIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = flatPlanner();
		with(w, () -> {
			Town t = town(level, w);
			List<ChunkPos> asc = t.chunks();
			String clean = placeBoth(level, t, asc).digest();
			Mutations.mode = Mutations.COUNTER;
			String one = placeBoth(level, t, asc).digest();
			String two = placeBoth(level, t, shuffled(asc, 5)).digest();
			check(!one.equals(clean) && !one.equals(two), "a counter shared by all writes must change the digests between runs");
		});
		helper.succeed();
	}

	@GameTest
	public void mutantClaimingCellsTwiceIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = flatPlanner();
		with(w, () -> {
			Town t = town(level, w);
			check(placeBoth(level, t, t.chunks()).duplicates() == 0, "the clean run claims every cell once");
			Mutations.mode = Mutations.TWICE;
			check(placeBoth(level, t, t.chunks()).duplicates() > 0, "the mutant claims ground cells twice and the ledger counts them");
		});
		helper.succeed();
	}
}
