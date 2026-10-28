package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.mod.worldgen.FoliageClear;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ORDERSIM S1: the engine-free world, level proxy and boot. */
class SimWorldTest {
	static final int FLOOR = 64;

	@BeforeAll
	static void boot() { SimBoot.boot(); }

	/** 5 x 5 chunks, chunks -2..2, flat at FLOOR. */
	static SimWorld flat(String biome) {
		return new SimWorld(1L, -2, -2, 5, 5, (x, z) -> FLOOR, (x, z) -> biome);
	}

	@Test
	void tagsAreBound() {
		assertTrue(Blocks.OAK_LOG.defaultBlockState().is(BlockTags.LOGS), "logs");
		assertTrue(Blocks.OAK_LEAVES.defaultBlockState().is(BlockTags.LEAVES), "leaves");
		assertTrue(Blocks.ICE.defaultBlockState().is(BlockTags.ICE), "ice");
		assertTrue(Blocks.LAVA.defaultBlockState().getFluidState().is(net.minecraft.tags.FluidTags.LAVA), "lava fluid tag");
	}

	@Test
	void terrainMatchesProbe() {
		TerrainProbe t = new TerrainProbe() {
			public int floorY(int x, int z) { return x >= 0 ? 60 : 66; }
			public int waterDepth(int x, int z) { return x >= 0 ? 4 : 0; }
		};
		SimWorld w = new SimWorld(1L, -1, -1, 2, 2, t, (x, z) -> "plains");
		assertEquals(Blocks.GRASS_BLOCK, w.get(-5, 65, 3).getBlock());
		assertTrue(w.get(-5, 66, 3).isAir());
		assertEquals(Blocks.DIRT, w.get(5, 59, 3).getBlock(), "wet floor is dirt");
		assertEquals(Blocks.WATER, w.get(5, 63, 3).getBlock());
		assertTrue(w.get(5, 64, 3).isAir(), "water fills to surfaceY - 1 only");
		assertEquals(Blocks.STONE, w.get(5, 40, 3).getBlock());
		// Heightmaps: WORLD_SURFACE_WG is the top block, Level.getHeight adds one.
		SimLevel l = new SimLevel(w, 0, 0, "test", 1L);
		assertEquals(66, l.proxy().getHeight(Heightmap.Types.WORLD_SURFACE_WG, -5, 3));
	}

	@Test
	void darkOakPlacesWithDirtUnderTrunk() {
		SimWorld w = flat("dark_forest");
		SimLevel l = new SimLevel(w, 0, 0, "feature:dark_oak", 1L);
		BlockPos origin = new BlockPos(0, FLOOR, 0);
		boolean placed = false;
		for (int seed = 0; seed < 64 && !placed; seed++) {
			placed = SimBoot.feature(TreeFeatures.DARK_OAK).place(l.proxy(), null, RandomSource.create(seed), origin);
		}
		assertTrue(placed, "a dark oak placed within 64 seeds");
		Set<Long> baseCols = new HashSet<>(), dirtUnder = new HashSet<>();
		for (var wr : w.writes()) {
			if (wr.now().is(BlockTags.LOGS) && wr.y() == FLOOR) baseCols.add(((long) wr.x() << 32) | (wr.z() & 0xFFFFFFFFL));
			if (wr.now().is(Blocks.DIRT) && wr.y() == FLOOR - 1) dirtUnder.add(((long) wr.x() << 32) | (wr.z() & 0xFFFFFFFFL));
		}
		assertEquals(4, baseCols.size(), "2 x 2 trunk");
		assertTrue(dirtUnder.containsAll(baseCols), "dirt under every trunk column");
		assertTrue(w.writes().stream().anyMatch(wr -> wr.now().is(BlockTags.LEAVES)), "leaves");
		assertTrue(w.violations().isEmpty(), "inside the zone: " + w.violations());
	}

	@Test
	void lavaLakePlaces() {
		SimWorld w = flat("plains");
		SimLevel l = new SimLevel(w, 0, 0, "feature:lake_lava", 1L);
		boolean placed = false;
		for (int seed = 0; seed < 64 && !placed; seed++) {
			placed = SimBoot.feature(MiscOverworldFeatures.LAKE_LAVA).place(l.proxy(), null, RandomSource.create(seed), new BlockPos(2, FLOOR - 1, 2));
		}
		assertTrue(placed, "lake placed");
		assertTrue(w.writes().stream().anyMatch(wr -> wr.now().is(Blocks.LAVA)), "lava");
		assertTrue(w.writes().stream().anyMatch(wr -> wr.now().is(Blocks.CAVE_AIR)), "cave air");
	}

	@Test
	void freezeTopLayerSnowsInSnowyBiome() {
		SimWorld snowy = flat("snowy_plains"), warm = flat("plains");
		for (SimWorld w : new SimWorld[] {snowy, warm}) {
			SimLevel l = new SimLevel(w, 0, 0, "feature:freeze_top_layer", 1L);
			SimBoot.feature(MiscOverworldFeatures.FREEZE_TOP_LAYER).place(l.proxy(), null, RandomSource.create(1), new BlockPos(0, FLOOR, 0));
		}
		assertTrue(snowy.get(3, FLOOR, 3).is(Blocks.SNOW), "snow layer on top: " + snowy.get(3, FLOOR, 3));
		assertTrue(warm.get(3, FLOOR, 3).isAir(), "no snow in plains");
	}

	@Test
	void zoneIsEnforced() {
		SimWorld w = flat("plains");
		SimLevel l = new SimLevel(w, 0, 0, "ours:0,0", 1L);
		BlockState stone = Blocks.STONE.defaultBlockState();
		assertTrue(l.proxy().setBlock(new BlockPos(16, FLOOR + 5, 0), stone, 2), "one chunk out is inside the zone");
		assertFalse(l.proxy().setBlock(new BlockPos(32, FLOOR + 5, 0), stone, 2), "two chunks out is dropped");
		assertTrue(w.get(32, FLOOR + 5, 0).isAir(), "and never reached the chunk");
		assertEquals(1, w.violations().size());
		var v = w.violations().get(0);
		assertTrue(v.write() && v.ours(), "an ours write violation: " + v);
		assertEquals("ours:0,0", w.writerOf(16, FLOOR + 5, 0));
		assertThrows(IllegalStateException.class, () -> l.proxy().getChunk(2, 0), "reading chunk 2 throws");
		assertThrows(IllegalStateException.class, () -> l.proxy().getBlockState(new BlockPos(40, FLOOR, 0)));
		// A vanilla feature's out-of-zone write is logged, but is not "ours".
		l.writer("feature:x@0,0,0");
		l.proxy().setBlock(new BlockPos(0, FLOOR + 5, 40), stone, 2);
		assertFalse(w.violations().get(w.violations().size() - 1).ours());
	}

	@Test
	void promotionReshapesMarkedCellsOnly() {
		// In 26.3 only a few blocks ask for post-processing (mushrooms, soul sand, magma). A floating mushroom that is marked
		// pops off at promotion (updateShape: cannot survive); one written with flag 16 is not marked and stays.
		for (boolean mark : new boolean[] {true, false}) {
			SimWorld w = flat("plains");
			SimLevel l = new SimLevel(w, 0, 0, "test", 1L);
			l.proxy().setBlock(new BlockPos(3, FLOOR + 8, 3), Blocks.BROWN_MUSHROOM.defaultBlockState(), mark ? 2 : 2 | 16);
			assertTrue(w.get(3, FLOOR + 8, 3).is(Blocks.BROWN_MUSHROOM), "placed");
			int n = w.promote(0, 0);
			assertEquals(mark ? 1 : 0, n);
			assertEquals(!mark, w.get(3, FLOOR + 8, 3).is(Blocks.BROWN_MUSHROOM), "mark=" + mark);
			assertEquals(0, w.promote(0, 0), "marks are consumed");
		}
	}

	@Test
	void foliageClearRemovesFloatingBlob() {
		SimWorld w = flat("plains");
		SimLevel l = new SimLevel(w, 0, 0, "test", 1L);
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
		int n = 0;
		for (int x = 4; x < 7; x++) for (int y = FLOOR + 10; y < FLOOR + 13; y++) for (int z = 4; z < 7; z++) { l.proxy().setBlock(new BlockPos(x, y, z), leaf, 2 | 16); n++; }
		boolean[] own = new boolean[256];
		java.util.Arrays.fill(own, true);
		int[] surf = new int[256];
		java.util.Arrays.fill(surf, FLOOR - 1);
		int removed = FoliageClear.run(l.proxy(), 0, 0, own, surf);
		assertEquals(n, removed);
		assertTrue(w.get(5, FLOOR + 11, 5).isAir());
	}

	@Test
	void unmodelledMethodThrowsWithItsName() {
		SimLevel l = new SimLevel(flat("plains"), 0, 0, "test", 1L);
		UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> l.proxy().getLevel());
		assertTrue(e.getMessage().contains("getLevel"), e.getMessage());
	}
}
