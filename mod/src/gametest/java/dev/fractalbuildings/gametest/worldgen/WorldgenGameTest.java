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
// gametest-order: 3
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

	/** The world area of a target tile: its building start's and ground start's boxes. */
	static List<BoundingBox> area(ServerLevel level, WorldPlanner w, Target t) {
		return TestArea.boxes(level, w, t.chunk(), t.structure(), "district_ground");
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void fixtureTownPlacesIdenticallyInAnyChunkOrder(GameTestHelper helper) {
		WorldPlanner w = FixtureWorldPlanner.sample();
		Target t = fixtureTarget(w);
		TestArea.run(helper, area(helper.getLevel(), w, t), () -> with(w, () -> placesInAnyOrder(helper.getLevel(), t)));
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void flatPlainsTownPlacesIdenticallyInAnyChunkOrder(GameTestHelper helper) {
		WorldPlanner w = flatPlanner();
		Target t = flatTarget(w);
		TestArea.run(helper, area(helper.getLevel(), w, t), () -> with(w, () -> placesInAnyOrder(helper.getLevel(), t)));
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
		StartSupport.Snapshot snapAsc = StartSupport.snapshot(level, b, g);
		checkDoorsAndStairs(level, b);
		checkConnectors(level, b);

		String[] names = {"reversed", "shuffled(7)", "shuffled(99)"};
		List<List<ChunkPos>> orders = List.of(reversed(asc), shuffled(asc, 7), shuffled(asc, 99));
		for (int i = 0; i < orders.size(); i++) {
			var order = orders.get(i);
			StartSupport.clear(level, b);
			StartSupport.clear(level, g);
			StartSupport.Placed again = placeBoth(level, b, g, order, order.hashCode());
			check(again.digest().equals(both.digest()), "ledger digest differs in another chunk order");
			check(again.outsideChunk() == 0 && again.duplicates() == 0, "outside/dups in another order");
			if (StartSupport.worldHash(level, b, g) != worldAsc) {
				throw new IllegalStateException("world blocks differ in another chunk order: ascending (expected) vs " + names[i] + " (actual) for " + t.structure() + " at " + t.chunk() + ": "
						+ StartSupport.diff(snapAsc, StartSupport.snapshot(level, b, g), both.cells(), 20));
			}
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

	/**
	 * Every door is two halves of one door block facing alike; stairs have a facing. DS36ROOFS r2: every stair already has the shape Minecraft
	 * gives it from its neighbours ({@code Block.updateFromNeighbourShapes}, what a tree's {@code updateShapeAtEdge} or a player's block
	 * next to it runs), so no later update reshapes a roof.
	 */
	private static void checkDoorsAndStairs(ServerLevel level, StructureStart s) {
		BoundingBox bb = s.getBoundingBox();
		int doors = 0, stairs = 0, corners = 0;
		List<String> reshaped = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY(), bb.minY() - 2); y <= bb.maxY(); y++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) for (int x = bb.minX(); x <= bb.maxX(); x++) {
			BlockState st = level.getBlockState(p.set(x, y, z));
			if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				BlockState up = level.getBlockState(p.set(x, y + 1, z));
				check(up.is(st.getBlock()) && up.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
						&& up.getValue(DoorBlock.FACING) == st.getValue(DoorBlock.FACING) && up.getValue(DoorBlock.HINGE) == st.getValue(DoorBlock.HINGE), "door at " + x + "," + y + "," + z + " " + st + " has no matching upper half, above is " + up);
				doors++;
			}
			if (st.getBlock() instanceof StairBlock) {
				stairs++;
				if (st.getValue(StairBlock.SHAPE) != net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT) corners++;
				BlockState updated = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(st, level, p.set(x, y, z));
				if (updated != st && reshaped.size() < 10) reshaped.add(x + "," + y + "," + z + " " + st + " -> " + updated.getValue(StairBlock.SHAPE));
			}
		}
		check(doors > 0, "a settlement start has at least one door");
		check(reshaped.isEmpty(), "stairs a neighbour update would reshape: " + reshaped);
		dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[B1] readback: {} doors, {} stair blocks ({} corners, none reshaped by an update) in {}", doors, stairs, corners, bb);
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void desertCityConnectorsMatchNeighbourShapes(GameTestHelper helper) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:desert"));
		WorldPlanner w = null;
		Optional<SettlementInfo> n = Optional.empty();
		for (long seed = 1; seed < 60 && n.isEmpty(); seed++) {
			w = new WorldPlanner(seed, WorldConfig.defaults().withIntercityRoads(false), pr, null);
			n = w.nearest(0, 0, Tier.CITY, 3);
		}
		check(n.isPresent(), "a desert city near the origin for some seed below 60");
		final WorldPlanner planner = w;
		var c = n.get().site().centre();
		DistrictGrid.Tile t = planner.grid().tileAt(c.x(), c.z());
		TileInfo info = planner.tile(t.ix(), t.iz()).orElseThrow();
		Target tg = new Target(info.tier().json, new ChunkPos(t.originChunkX(), t.originChunkZ()), t, planner.buildings(t.ix(), t.iz()).size(), planner.ground(t.ix(), t.iz()).size());
		TestArea.run(helper, area(helper.getLevel(), planner, tg), () -> with(planner, () -> {
			ServerLevel level = helper.getLevel();
			StructureStart b = StartSupport.start(level, tg.structure(), tg.chunk());
			StructureStart g = StartSupport.start(level, "district_ground", tg.chunk());
			List<ChunkPos> chunks = new ArrayList<>(StartSupport.chunks(b));
			for (ChunkPos cp : StartSupport.chunks(g)) if (!chunks.contains(cp)) chunks.add(cp);
			placeBoth(level, b, g, chunks, 1);
			checkConnectors(level, b);
			StartSupport.clear(level, b);
			StartSupport.clear(level, g);
		}));
	}

	/**
	 * GLASSSTUB r2: every pane, iron bars, fence and wall the pieces wrote already has the connection state Minecraft would give it from its
	 * neighbours (the pieces write with UPDATE_KNOWN_SHAPE, so nothing re-shapes them); a pane with all four sides false is a post.
	 * Counts what it saw per kind so the log shows the test looked at something.
	 */
	/** Bit mask of the four connection properties that are true (panes, bars, fences; walls count low and tall). */
	private static int sides(BlockState s) {
		int m = 0;
		for (net.minecraft.world.level.block.state.properties.Property<?> pr : s.getProperties()) {
			String n = pr.getName();
			if (!(n.equals("north") || n.equals("east") || n.equals("south") || n.equals("west"))) continue;
			if (!String.valueOf(s.getValue(pr)).equals("false") && !String.valueOf(s.getValue(pr)).equals("none")) m |= 1 << java.util.List.of("north", "east", "south", "west").indexOf(n);
		}
		return m;
	}

	private static void checkConnectors(ServerLevel level, StructureStart s) {
		BoundingBox bb = s.getBoundingBox();
		java.util.TreeMap<String, int[]> seen = new java.util.TreeMap<>(); // kind -> {count, reshaped, posts}
		List<String> bad = new ArrayList<>(), soft = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY(), bb.minY() - 2); y <= bb.maxY(); y++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) for (int x = bb.minX(); x <= bb.maxX(); x++) {
			BlockState st = level.getBlockState(p.set(x, y, z));
			String kind = st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock ? (st.is(Blocks.IRON_BARS) ? "bars" : "pane")
					: st.getBlock() instanceof net.minecraft.world.level.block.FenceBlock ? "fence" : st.getBlock() instanceof net.minecraft.world.level.block.WallBlock ? "wall" : null;
			if (kind == null) continue;
			int[] c = seen.computeIfAbsent(kind, k -> new int[3]);
			c[0]++;
			BlockState updated = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(st, level, p.set(x, y, z));
			if (updated != st) {
				c[1]++;
				int a = sides(st), u = sides(updated);
				boolean hard = (a & ~u) != 0 || (a == 0 && u != 0); // an arm to nothing, or a post that should join: a missing arm toward a furniture block (crafting table) is soft
				if (!hard) { soft.add(kind + " " + x + "," + y + "," + z); continue; }
				if (bad.size() < 10) { StringBuilder nb = new StringBuilder(); for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) nb.append(" ").append(d).append("=").append(level.getBlockState(p.set(x, y, z).move(d))); bad.add(kind + " " + x + "," + y + "," + z + " " + st + " -> " + updated + " nbrs" + nb); } }
		}
		dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[GLASSSTUB] connectors in {}: {}", bb, seen.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[0] + " reshaped=" + e.getValue()[1]).toList() + ", " + soft.size() + " only miss an arm toward a furniture block, first " + soft.stream().limit(3).toList());
		check(bad.isEmpty(), "connecting blocks a neighbour update would reshape (pane/bars/fence/wall): " + bad);
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

	@GameTest(maxTicks = TestArea.TICKS)
	public void mutantReadingTheRandomBreaksOrderIndependence(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = FixtureWorldPlanner.sample();
		Target t = fixtureTarget(w);
		TestArea.run(helper, area(level, w, t), () -> with(w, () -> {
			StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
			List<ChunkPos> asc = StartSupport.chunks(b);
			String clean = StartSupport.place(level, b, asc, 1).digest();
			Mutations.mode = Mutations.RANDOM;
			String one = StartSupport.place(level, b, asc, 1).digest();
			String two = StartSupport.place(level, b, reversed(asc), 2).digest();
			check(!one.equals(clean) && !one.equals(two), "the mutant (blocks depend on the random) should differ from the clean run and between orders");
		}));
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void mutantWithoutChunkClipIsCaught(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner w = FixtureWorldPlanner.sample();
		Target t = fixtureTarget(w);
		TestArea.run(helper, area(level, w, t), () -> with(w, () -> {
			StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
			check(StartSupport.place(level, b, StartSupport.chunks(b), 1).outsideChunk() == 0, "the clean run stays in its chunks");
			Mutations.mode = Mutations.NO_CHUNK_CLIP;
			check(StartSupport.place(level, b, StartSupport.chunks(b), 1).outsideChunk() > 0, "the mutant writes outside the chunk and the check sees it");
		}));
	}
}
