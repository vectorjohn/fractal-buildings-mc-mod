package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldHooks;
import dev.fractalbuildings.core.world.WorldHooks.WorldHook;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.gameplay.FbCommand;
import dev.fractalbuildings.mod.gameplay.Gameplay;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * B5 server tests (flat world, starts made by hand as in B1): beds, workstations and chests stand where the planner's hooks say, chests
 * carry a loot table and a seed derived from the position, villagers follow the tier and config rules and do not depend on the chunk order.
 * Seeds on synthetic flat plains (see the log): 1 is a hamlet with 16 beds in one tile, 5 a village with 47, 6 a town with 131.
 */
public class GameplayGameTest {
	private static WorldPlanner planner(long seed) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(seed, WorldConfig.defaults(), pr, null);
	}

	record Tile(WorldPlanner w, DistrictGrid.Tile t, TileInfo info, ChunkPos chunk) {}

	private static Tile tileOf(WorldPlanner w) {
		SettlementInfo n = w.nearest(0, 0, Tier.HAMLET, 2).orElseThrow();
		DistrictGrid.Tile t = w.grid().tileAt(n.site().centre().x(), n.site().centre().z());
		return new Tile(w, t, w.tile(t.ix(), t.iz()).orElseThrow(), new ChunkPos(t.originChunkX(), t.originChunkZ()));
	}

	/** What the start's box holds after placing. */
	record Found(TreeMap<Long, String> beds, int chests, int workstations, TreeMap<Long, String> loot, TreeSet<String> villagers, int villagerCount, long hash) {}

	private static AABB aabb(StructureStart s) {
		BoundingBox b = s.getBoundingBox();
		return new AABB(b.minX(), b.minY() - 2, b.minZ(), b.maxX() + 1, b.maxY() + 2, b.maxZ() + 1);
	}

	private static Found scan(ServerLevel level, StructureStart s) {
		BoundingBox b = s.getBoundingBox();
		TreeMap<Long, String> beds = new TreeMap<>(), loot = new TreeMap<>();
		int chests = 0, work = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = b.minY() - 2; y <= b.maxY() + 2; y++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
			p.set(x, y, z);
			BlockState st = level.getBlockState(p);
			if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.FOOT) beds.put(WorldHooks.key(x, y, z), st.getBlock().toString());
			else if (st.is(Blocks.CHEST)) {
				chests++;
				BlockEntity be = level.getBlockEntity(p);
				if (be instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) loot.put(WorldHooks.key(x, y, z), c.getLootTable().identifier() + "#" + c.getLootTableSeed());
			}
			if (!(st.getBlock() instanceof BedBlock) && PoiTypes.forState(st).isPresent()) work++;
		}
		TreeSet<String> vill = new TreeSet<>();
		List<Villager> vs = level.getEntitiesOfClass(Villager.class, aabb(s));
		for (Villager v : vs) vill.add(v.getUUID().toString());
		return new Found(beds, chests, work, loot, vill, vs.size(), StartSupport.worldHash(level, s));
	}

	private static void reset(ServerLevel level, StructureStart s) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, aabb(s))) v.discard();
		StartSupport.clear(level, s);
	}

	private static void run(Runnable body) {
		boolean villagers = Gameplay.villagersEnabled();
		try {
			body.run();
		} finally {
			WorldPlanners.overrideForTests(null);
			Gameplay.setVillagersForTests(villagers);
		}
	}

	private static List<ChunkPos> order(StructureStart s, int mode) {
		List<ChunkPos> cs = new ArrayList<>(StartSupport.chunks(s));
		if (mode == 1) Collections.reverse(cs);
		if (mode == 2) Collections.shuffle(cs, new Random(11));
		return cs;
	}

	/** Beds the planner's hooks promise and the shell lets us place: the foot cell and the head cell in one chunk. */
	private static List<WorldHook> bedHooks(WorldPlanner w, DistrictGrid.Tile t) {
		List<WorldHook> out = new ArrayList<>();
		for (BuildingPiece b : w.buildings(t.ix(), t.iz())) for (WorldHook h : WorldHooks.of(b)) if (h.kind().equals("bed")) out.add(h);
		return out;
	}

	@GameTest
	public void hamletGetsBedsChestsWorkstationsAndAtMostTenVillagers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		run(() -> {
			Tile tl = tileOf(planner(1));
			check(tl.info().tier() == Tier.HAMLET, "seed 1 should give a hamlet, got " + tl.info().tier());
			WorldPlanners.overrideForTests(tl.w());
			Gameplay.setVillagersForTests(true);
			StructureStart s = StartSupport.start(level, "hamlet", tl.chunk());
			check(s.isValid(), "hamlet start");
			StartSupport.place(level, s, order(s, 0), 1);
			Found f = scan(level, s);
			List<WorldHook> hooks = bedHooks(tl.w(), tl.t());
			check(hooks.size() > 10, "this hamlet should promise more than 10 beds, has " + hooks.size());
			check(f.beds().size() >= hooks.size() - 1 && f.beds().size() <= hooks.size(), "beds placed " + f.beds().size() + " of " + hooks.size() + " hooks");
			long crossing = hooks.stream().filter(h -> !h.bedInOneChunk()).count();
			check(crossing > 0 && hooks.stream().filter(h -> !h.bedInOneChunk()).anyMatch(h -> f.beds().containsKey(WorldHooks.key(h.x(), h.y(), h.z()))), "this hamlet needs a bed across a chunk edge that gets placed (crossing hooks: " + crossing + ")");
			for (WorldHook h : hooks) if (f.beds().containsKey(WorldHooks.key(h.x(), h.y(), h.z()))) check(level.getBlockState(new BlockPos(h.headX(), h.y(), h.headZ())).getBlock() instanceof BedBlock hb && level.getBlockState(new BlockPos(h.headX(), h.y(), h.headZ())).getValue(BedBlock.PART) == BedPart.HEAD, "bed at " + h.x() + "," + h.z() + " has no head");
			for (long k : f.beds().keySet()) check(hooks.stream().anyMatch(h -> WorldHooks.key(h.x(), h.y(), h.z()) == k), "a bed stands where no hook is");
			dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[B5] hamlet seed 1: {} bed hooks, {} beds placed, {} chests, {} job sites, {} villagers", hooks.size(), f.beds().size(), f.chests(), f.workstations(), f.villagerCount());
			check(f.chests() > 0 && f.loot().size() == f.chests(), "every chest has a loot table: " + f.loot().size() + " of " + f.chests());
			check(f.loot().values().stream().allMatch(v -> v.startsWith("fractalbuildings:chests/")), "loot tables " + f.loot().values());
			check(f.workstations() > 0, "job sites " + f.workstations());
			// PoiManager knows the beds
			// the PoiManager lists a home at the head of every bed (a bed's POI is its head block)
			for (WorldHook h : hooks) {
				if (!f.beds().containsKey(WorldHooks.key(h.x(), h.y(), h.z()))) continue;
				BlockPos head = new BlockPos(h.headX(), h.y(), h.headZ());
				check(level.getPoiManager().existsAtPosition(PoiTypes.HOME, head), "PoiManager has no home at the bed head " + head);
			}
			// villagers: one per selected bed, at most 10
			var selected = WorldHooks.villagerBeds(tl.w().buildings(tl.t().ix(), tl.t().iz()), level.getSeed(), 10);
			check(selected.size() == 10, "selected " + selected.size());
			long expected = selected.stream().filter(f.beds()::containsKey).count();
			check(f.villagerCount() == expected && expected > 0, "villagers " + f.villagerCount() + ", expected " + expected);
			check(f.villagers().size() == f.villagerCount(), "villager uuids are unique");
			for (Villager v : level.getEntitiesOfClass(Villager.class, aabb(s))) {
				BlockPos vp = v.blockPosition();
				check(selected.contains(WorldHooks.key(vp.getX(), vp.getY(), vp.getZ())) || selected.contains(WorldHooks.key(vp.getX(), vp.getY() - 1, vp.getZ())), "villager " + vp + " is not at a selected bed");
			}
			reset(level, s);
		});
		helper.succeed();
	}

	@GameTest
	public void villagersAreOffInTownsAndWhenConfigured(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		run(() -> {
			Tile town = tileOf(planner(6));
			check(town.info().tier() == Tier.TOWN, "seed 6 should give a town, got " + town.info().tier());
			WorldPlanners.overrideForTests(town.w());
			Gameplay.setVillagersForTests(true);
			StructureStart s = StartSupport.start(level, "town", town.chunk());
			StartSupport.place(level, s, order(s, 0), 1);
			Found f = scan(level, s);
			check(f.beds().size() > 10 && f.villagerCount() == 0, "town: beds " + f.beds().size() + ", villagers " + f.villagerCount());
			reset(level, s);

			Tile village = tileOf(planner(5));
			check(village.info().tier() == Tier.VILLAGE, "seed 5 should give a village, got " + village.info().tier());
			WorldPlanners.overrideForTests(village.w());
			Gameplay.setVillagersForTests(false);
			StructureStart v = StartSupport.start(level, "village", village.chunk());
			StartSupport.place(level, v, order(v, 0), 1);
			Found off = scan(level, v);
			check(off.beds().size() > 10 && off.villagerCount() == 0, "villagers off: beds " + off.beds().size() + ", villagers " + off.villagerCount());
			reset(level, v);
			Gameplay.setVillagersForTests(true);
			StartSupport.place(level, v, order(v, 0), 1);
			Found on = scan(level, v);
			check(on.villagerCount() == 10, "village with villagers on: " + on.villagerCount());
			reset(level, v);
		});
		helper.succeed();
	}

	@GameTest
	public void hooksAndLootAreTheSameInAnyChunkOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		run(() -> {
			Tile tl = tileOf(planner(5));
			WorldPlanners.overrideForTests(tl.w());
			Gameplay.setVillagersForTests(true);
			StructureStart s = StartSupport.start(level, "village", tl.chunk());
			StartSupport.Placed first = StartSupport.place(level, s, order(s, 0), 1);
			Found a = scan(level, s);
			check(a.chests() > 0 && a.villagerCount() > 0, "something to compare: chests " + a.chests() + " villagers " + a.villagerCount());
			// the loot seed is the hash of (world seed, position), not anything random
			for (BuildingPiece b : tl.w().buildings(tl.t().ix(), tl.t().iz())) for (WorldHook h : WorldHooks.of(b)) {
				if (!h.kind().equals("chest")) continue;
				String got = a.loot().get(WorldHooks.key(h.x(), h.y(), h.z()));
				if (got == null) continue;
				long want = RandomSource.create(h.seed(level.getSeed(), "loot")).nextLong();
				check(got.endsWith("#" + want), "chest at " + h.x() + "," + h.y() + "," + h.z() + " has " + got + ", the position gives seed " + want);
			}
			for (int mode = 1; mode <= 2; mode++) {
				reset(level, s);
				StartSupport.Placed again = StartSupport.place(level, s, order(s, mode), 100 + mode);
				Found b = scan(level, s);
				check(again.digest().equals(first.digest()), "ledger digest differs in order " + mode);
				check(b.hash() == a.hash(), "world blocks differ in order " + mode);
				check(b.beds().equals(a.beds()) && b.loot().equals(a.loot()), "beds or loot differ in order " + mode);
				check(b.villagers().equals(a.villagers()), "villager ids differ in order " + mode + ": " + b.villagers() + " vs " + a.villagers());
			}
			reset(level, s);
		});
		helper.succeed();
	}

	@GameTest
	public void fbInfoNamesTheBuildingAtALot(GameTestHelper helper) {
		Tile tl = tileOf(planner(5));
		BuildingPiece b = tl.w().buildings(tl.t().ix(), tl.t().iz()).get(0);
		int x = b.brief().x() + b.brief().w() / 2, z = b.brief().z() + b.brief().d() / 2;
		String text = FbCommand.describe(tl.w(), x, z);
		check(text.contains("lot " + b.brief().id()) && text.contains(b.brief().type()) && text.contains(tl.info().tier().json), "info text: " + text);
		check(FbCommand.describe(tl.w(), 1_000_000, 1_000_000).startsWith("No settlement"), "far away");
		var at = dev.fractalbuildings.mod.api.Settlements.at(tl.w(), x, z).orElseThrow();
		check(at.lotId().equals(b.brief().id()) && at.hooks().equals(WorldHooks.of(b)), "Settlements.at");
		helper.succeed();
	}

}
