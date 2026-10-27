package dev.fractalbuildings.gametest.order;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What vanilla features may legitimately leave inside our cells: they run for a neighbouring chunk after our write and spill over the border
 * (ADR 0003 D5). Known kinds: carver cuts (cave air, lava, magma) into our ground, ores and disks (sand, gravel, clay) and moss patches over natural ground; the dirt base of a tree, leaves, leaf
 * litter, logs, huge mushrooms and short plants in our air; shape-only changes of a connecting block next to a vanilla block. Everything else
 * that changes one of our cells is {@code unknown} and fails the test; the histogram of every run is logged so a new kind is seen, not guessed.
 */
final class Spill {
	private Spill() {}

	/** Natural ground our raster may want (terrain-following fill, roads of gravel and sand). */
	private static final Set<String> GROUND = Set.of("dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "stone", "andesite", "diorite", "granite", "deepslate", "tuff",
			"gravel", "sand", "red_sand", "clay", "mud", "moss_block", "pale_moss_block", "sandstone", "red_sandstone", "calcite", "dripstone_block", "snow_block", "packed_ice", "ice", "terracotta", "water");
	/** DS-30 road surfaces that are not natural ground. Only a cell whose raster role is a road surface may be spill of these, and only where a carver of a later neighbouring chunk opened it (seen: cobbled_deepslate to lava; to stone only through {@link #lakeBarrier}). */
	private static final Set<String> ROAD_BLOCKS = Set.of("cobbled_deepslate", "stone_bricks", "mud_bricks", "light_gray_concrete", "oak_planks");
	/** What a carver leaves in a road cell it opens (not stone, ground or dirt: those are not carver output for a road). */
	private static final Set<String> ROAD_CARVED = Set.of("cave_air", "lava", "magma_block", "water");
	/** The base a tree feature puts under its trunk, whatever it replaces. */
	private static final Set<String> TREE_BASE = Set.of("dirt", "podzol", "rooted_dirt", "coarse_dirt", "grass_block");
	/** What a carver of a later neighbouring chunk leaves where it cuts into our ground (caves run per chunk, not in a global order): cave openings, lava, magma, stone. */
	private static final Set<String> CARVER = Set.of("cave_air", "lava", "magma_block", "stone", "deepslate", "water");
	/** What spill leaves behind in air. */
	private static final Set<String> AIR_SPILL = Set.of("moss_carpet", "pale_moss_carpet", "glow_lichen", "snow",
			"pale_hanging_moss", "cave_air", "red_mushroom_block", "brown_mushroom_block", "mushroom_stem", "leaf_litter", "short_grass", "tall_grass", "fern", "vine",
			// CITYQ r2: a fallen tree's decoration (a brown mushroom on its log, seen at 1441,64,-9476 in reverse and shuffle2 once the gate city's lattice drew a street there)
			"brown_mushroom", "red_mushroom");

	static String name(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(); }

	private static boolean ground(BlockState s) { String n = name(s); return GROUND.contains(n) || n.endsWith("_ore"); }

	/** "spill" (known vanilla), "shape" (same connecting block, other connections) or "unknown". {@code above} is the world block over the cell. */
	static String solidKind(BlockState want, BlockState have, BlockState above, BlockState above2, boolean roadCell) {
		if (want.getBlock() == have.getBlock() && (want.getBlock() instanceof CrossCollisionBlock || want.getBlock() instanceof WallBlock)) return "shape";
		boolean natural = ground(want) || name(want).equals("dirt_path");
		if (roadCell && ROAD_BLOCKS.contains(name(want)) && ROAD_CARVED.contains(name(have))) return "spill";
		if (natural && ground(have)) return "spill";
		if (natural && (TREE_BASE.contains(name(have)) || CARVER.contains(name(have)))) return "spill";
		// a tree's dirt base under a log may replace a block that is not ground (a road edge, a wall foot)
		if (TREE_BASE.contains(name(have)) && treeAbove(above, above2)) return "spill";
		return "unknown";
	}

	/**
	 * A lava lake's barrier: {@code LakeFeature} (26.3 bytecode, second pass of {@code place}) sets its barrier (stone for lava lakes) on every
	 * solid block of the lake's shell that its replace predicate allows, ours included, when a neighbour chunk decorated later puts its lake
	 * across the border (CITYQ r6: cobblestone->stone in 5 of 7 orders). Stone with lava within 3 blocks.
	 */
	static boolean lakeBarrier(BlockState have, net.minecraft.world.level.Level level, int x, int y, int z) {
		if (!name(have).equals("stone")) return false;
		net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++)
			if (level.getFluidState(p.set(x + dx, y + dy, z + dz)).is(net.minecraft.tags.FluidTags.LAVA)) return true;
		return false;
	}

	/** "spill" (canopy, litter, patches, a tree's dirt under a log) or "unknown". */
	static String airKind(BlockState have, BlockState above, BlockState above2) {
		String n = name(have);
		if (n.equals("dirt") && treeAbove(above, above2)) return "spill";
		if (AIR_SPILL.contains(n) || n.endsWith("_ore") || have.is(BlockTags.LEAVES) || have.is(BlockTags.LOGS) || have.is(BlockTags.FLOWERS) || have.is(BlockTags.SAPLINGS)) return "spill";
		return "unknown";
	}

	/**
	 * A tree's trunk stands over this cell: a log directly above, or one cell higher with grass or dirt between. Traced (p6-spill-dirt, reverse order,
	 * 1360,69,-9571 and -9570): a dark oak 2x2 of a neighbouring chunk's features, which run after our write, calls setDirtAt (flags 19) under its
	 * trunk cells at y 69 and puts the logs at y 71, over the cells our street cut had cleared; the grass at y 70 is natural terrain beside the cut.
	 */
	private static boolean treeAbove(BlockState above, BlockState above2) {
		return above.is(BlockTags.LOGS) || ((name(above).equals("grass_block") || name(above).equals("dirt")) && above2.is(BlockTags.LOGS));
	}
}
