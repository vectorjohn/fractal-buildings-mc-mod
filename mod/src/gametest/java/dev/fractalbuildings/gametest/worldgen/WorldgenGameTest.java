package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.FixtureWorldPlanner;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.worldgen.BuildingStructurePiece;
import dev.fractalbuildings.mod.worldgen.DistrictStructure;
import dev.fractalbuildings.mod.worldgen.GroundStructurePiece;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Server game tests of the district structures (plan B1). The test world is flat, seed 0 and generates no structures, so starts are made by
 * hand ({@code Structure.generate}, as S9 did) with a planner installed by {@code WorldPlanners.overrideForTests}: the hilly fixture town
 * and a real {@code WorldPlanner} on synthetic flat plains. Chunk-order evidence on a normal world is B2's.
 */
public class WorldgenGameTest {
	static final String[] TIERS = {"hamlet", "village", "town", "city", "metropolis"};

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
		}
	}

	/** Tile (0,0) of the fixture town: its structure path ({@code town}) and start chunk. */
	record Target(String structure, ChunkPos chunk, DistrictGrid.Tile tile, int buildings, int groundPieces) {}

	static Target fixtureTarget(WorldPlanner w) {
		TileInfo t = w.tile(0, 0).orElseThrow();
		return new Target(t.tier().json, new ChunkPos(t.tile().originChunkX(), t.tile().originChunkZ()), t.tile(), w.buildings(0, 0).size(), w.ground(0, 0).size());
	}

	/** A real planner on flat plains (seed 6 puts a 62-building town at tile -2,2, see the log of this step). */
	static WorldPlanner flatPlanner() {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(6, WorldConfig.defaults().withIntercityRoads(false), pr, null);
	}

	static Target flatTarget(WorldPlanner w) {
		Optional<SettlementInfo> n = w.nearest(0, 0, Tier.HAMLET, 2);
		check(n.isPresent(), "flat plains with seed 6 should have a settlement near the origin");
		var c = n.get().site().centre();
		DistrictGrid.Tile t = w.grid().tileAt(c.x(), c.z());
		TileInfo info = w.tile(t.ix(), t.iz()).orElseThrow(() -> new IllegalStateException("the settlement centre's tile is not planned"));
		return new Target(info.tier().json, new ChunkPos(t.originChunkX(), t.originChunkZ()), t, w.buildings(t.ix(), t.iz()).size(), w.ground(t.ix(), t.iz()).size());
	}

	private static List<ChunkPos> shuffled(List<ChunkPos> in, long seed) {
		List<ChunkPos> out = new ArrayList<>(in);
		Collections.shuffle(out, new Random(seed));
		return out;
	}

	private static List<ChunkPos> reversed(List<ChunkPos> in) {
		List<ChunkPos> out = new ArrayList<>(in);
		Collections.reverse(out);
		return out;
	}

	// ------------------------------------------------------------------------------------------------------------------ data

	@GameTest
	public void registriesHoldStructuresSetsAndTags(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String t : TIERS) {
			Structure s = StartSupport.structure(level, t).value();
			check(s instanceof DistrictStructure d && d.tier().json.equals(t), t + " is a DistrictStructure of its tier");
			check(s.terrainAdaptation().getSerializedName().equals("beard_thin"), t + " uses beard_thin");
			check(s.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION, t + " is in top_layer_modification");
			StructureSpawnOverride monsters = s.spawnOverrides().get(MobCategory.MONSTER);
			check(monsters != null && monsters.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE && monsters.spawns().isEmpty(), t + " allows no monster spawns inside its pieces");
		}
		Structure ground = StartSupport.structure(level, "district_ground").value();
		check(ground.terrainAdaptation().getSerializedName().equals("none"), "ground has no terrain adaptation");
		HolderSet<Structure> tag = structures.get(TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("fractalbuildings", "settlement"))).orElseThrow(() -> new IllegalStateException("tag #fractalbuildings:settlement missing"));
		check(tag.size() == 5, "settlement tag holds the five tiers, has " + tag.size());
		var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		for (String set : new String[] {"districts", "district_ground"}) {
			check(sets.get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath("fractalbuildings", set))).isPresent(), "structure set " + set);
		}
		helper.succeed();
	}

	@GameTest
	public void locateInATestWorldWithoutStructuresDoesNotThrow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("fractalbuildings", "settlement"));
		// structures are off in this world, so the answer is null; what matters is a clean "not found"
		var found = level.findNearestMapStructure(tag, new BlockPos(0, 64, 0), 8, false);
		check(found == null, "no structure can be found in a world that generates none");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------------------------ placement

	@GameTest
	public void fixtureTownPlacesIdenticallyInAnyChunkOrder(GameTestHelper helper) {
		with(FixtureWorldPlanner.sample(), () -> placesInAnyOrder(helper.getLevel(), fixtureTarget(WorldPlanners.of(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator()).orElseThrow())));
		helper.succeed();
	}

	@GameTest
	public void flatPlainsTownPlacesIdenticallyInAnyChunkOrder(GameTestHelper helper) {
		WorldPlanner w = flatPlanner();
		with(w, () -> placesInAnyOrder(helper.getLevel(), flatTarget(w)));
		helper.succeed();
	}

	/** Both starts of a tile, ascending, reversed and shuffled; then a save and a load. */
	private static void placesInAnyOrder(ServerLevel level, Target t) {
		StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
		StructureStart g = StartSupport.start(level, "district_ground", t.chunk());
		check(b.isValid() && g.isValid(), "both starts are valid for " + t);
		check(b.getPieces().size() > 0 && b.getPieces().stream().allMatch(p -> p instanceof BuildingStructurePiece), "building start holds building pieces");
		check(g.getPieces().size() == t.groundPieces() && g.getPieces().stream().allMatch(p -> p instanceof GroundStructurePiece), "ground start holds one piece per ground chunk");
		check(b.getPieces().size() <= t.buildings(), "no more building pieces than buildings");
		for (StructureStart s : List.of(b, g)) {
			for (StructurePiece p : s.getPieces()) {
				BoundingBox bb = p.getBoundingBox();
				check(t.tile().contains(bb.minX(), bb.minZ()) && t.tile().contains(bb.maxX(), bb.maxZ()), "piece box " + bb + " inside tile " + t.tile());
			}
		}

		// one pass of both starts into one ledger: no cell is claimed twice (ownership), and nothing leaves its chunk
		List<ChunkPos> chunks = new ArrayList<>(StartSupport.chunks(b));
		for (ChunkPos c : StartSupport.chunks(g)) if (!chunks.contains(c)) chunks.add(c);
		List<ChunkPos> asc = new ArrayList<>(chunks);
		asc.sort((x, y) -> x.z() != y.z() ? Integer.compare(x.z(), y.z()) : Integer.compare(x.x(), y.x()));
		StartSupport.Placed both = placeBoth(level, b, g, asc, 1);
		check(both.cells().size() > 2000, "cells claimed " + both.cells().size());
		check(both.outsideChunk() == 0, both.outsideChunk() + " cells written outside the chunk being generated");
		check(both.duplicates() == 0, both.duplicates() + " cells claimed twice by the building and ground starts");
		for (long k : both.cells().keySet()) {
			check(t.tile().contains(dev.fractalbuildings.mod.worldgen.PieceLedger.x(k), dev.fractalbuildings.mod.worldgen.PieceLedger.z(k)), "cell outside the tile: " + dev.fractalbuildings.mod.worldgen.PieceLedger.x(k) + "," + dev.fractalbuildings.mod.worldgen.PieceLedger.z(k));
		}
		long worldAsc = StartSupport.worldHash(level, b, g);
		checkDoorsAndStairs(level, b);

		for (var order : List.of(reversed(asc), shuffled(asc, 7), shuffled(asc, 99))) {
			StartSupport.clear(level, b);
			StartSupport.clear(level, g);
			StartSupport.Placed again = placeBoth(level, b, g, order, order.hashCode());
			check(again.digest().equals(both.digest()), "ledger digest differs in another chunk order");
			check(again.outsideChunk() == 0 && again.duplicates() == 0, "outside/dups in another order");
			check(StartSupport.worldHash(level, b, g) == worldAsc, "world blocks differ in another chunk order");
		}

		// save and load: a reloaded start places the same blocks
		StructurePieceSerializationContext ctx = StructurePieceSerializationContext.fromLevel(level);
		StructureStart b2 = StructureStart.loadStaticStart(ctx, b.createTag(ctx, t.chunk()), level.getSeed());
		StructureStart g2 = StructureStart.loadStaticStart(ctx, g.createTag(ctx, t.chunk()), level.getSeed());
		check(b2 != null && g2 != null && b2.getPieces().size() == b.getPieces().size() && g2.getPieces().size() == g.getPieces().size(), "start reloads with its pieces");
		StartSupport.clear(level, b);
		StartSupport.clear(level, g);
		check(placeBoth(level, b2, g2, asc, 3).digest().equals(both.digest()), "a start loaded from NBT places other blocks");
		StartSupport.clear(level, b);
		StartSupport.clear(level, g);
	}

	private static StartSupport.Placed placeBoth(ServerLevel level, StructureStart b, StructureStart g, List<ChunkPos> order, long salt) {
		// both starts in the same chunk step, as the decoration step does (their relative order must not matter)
		var cells = new java.util.TreeMap<Long, Integer>();
		int outside = 0, dups = 0;
		for (ChunkPos c : order) {
			for (StructureStart s : (c.x() + c.z()) % 2 == 0 ? List.of(b, g) : List.of(g, b)) {
				StartSupport.Placed one = StartSupport.place(level, s, List.of(c), salt);
				outside += one.outsideChunk();
				dups += one.duplicates();
				for (var e : one.cells().entrySet()) if (cells.put(e.getKey(), e.getValue()) != null) dups++;
			}
		}
		return new StartSupport.Placed(cells, outside, dups);
	}

	/** Every door is two halves of one door block facing alike; stairs have a facing. */
	private static void checkDoorsAndStairs(ServerLevel level, StructureStart s) {
		BoundingBox bb = s.getBoundingBox();
		int doors = 0, stairs = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY(), bb.minY() - 2); y <= bb.maxY(); y++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) for (int x = bb.minX(); x <= bb.maxX(); x++) {
			BlockState st = level.getBlockState(p.set(x, y, z));
			if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				BlockState up = level.getBlockState(p.set(x, y + 1, z));
				check(up.is(st.getBlock()) && up.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
						&& up.getValue(DoorBlock.FACING) == st.getValue(DoorBlock.FACING) && up.getValue(DoorBlock.HINGE) == st.getValue(DoorBlock.HINGE), "door at " + x + "," + y + "," + z + " " + st + " has no matching upper half, above is " + up);
				doors++;
			}
			if (st.getBlock() instanceof StairBlock) stairs++;
		}
		check(doors > 0, "a settlement start has at least one door");
		dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[B1] readback: {} doors, {} stair blocks in {}", doors, stairs, bb);
	}

	@GameTest
	public void wrongTierOrNonStartChunkGivesNoStart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = FixtureWorldPlanner.sample();
		with(w, () -> {
			Target t = fixtureTarget(w);
			for (String other : TIERS) {
				if (other.equals(t.structure())) continue;
				check(!StartSupport.start(level, other, t.chunk()).isValid(), other + " must not start on a " + t.structure() + " tile");
			}
			check(StartSupport.start(level, t.structure(), t.chunk()).isValid(), "the right tier starts");
			check(!StartSupport.start(level, t.structure(), new ChunkPos(t.chunk().x() + 1, t.chunk().z())).isValid(), "a chunk next to the start chunk is not a start");
			check(!StartSupport.start(level, "district_ground", new ChunkPos(t.chunk().x() + 1, t.chunk().z() + 1)).isValid(), "ground: a non-start chunk");
			check(!StartSupport.start(level, "city", new ChunkPos(900, 900)).isValid(), "a chunk of no tile");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------------------------ mutants

	@GameTest
	public void mutantReadingTheRandomBreaksOrderIndependence(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = FixtureWorldPlanner.sample();
		with(w, () -> {
			Target t = fixtureTarget(w);
			StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
			List<ChunkPos> asc = StartSupport.chunks(b);
			String clean = StartSupport.place(level, b, asc, 1).digest();
			Mutations.mode = Mutations.RANDOM;
			String one = StartSupport.place(level, b, asc, 1).digest();
			String two = StartSupport.place(level, b, reversed(asc), 2).digest();
			check(!one.equals(clean) && !one.equals(two), "the mutant (blocks depend on the random) should differ from the clean run and between orders");
		});
		helper.succeed();
	}

	@GameTest
	public void mutantWithoutChunkClipIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = FixtureWorldPlanner.sample();
		with(w, () -> {
			Target t = fixtureTarget(w);
			StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
			check(StartSupport.place(level, b, StartSupport.chunks(b), 1).outsideChunk() == 0, "the clean run stays in its chunks");
			Mutations.mode = Mutations.NO_CHUNK_CLIP;
			check(StartSupport.place(level, b, StartSupport.chunks(b), 1).outsideChunk() > 0, "the mutant writes outside the chunk and the check sees it");
		});
		helper.succeed();
	}
}
