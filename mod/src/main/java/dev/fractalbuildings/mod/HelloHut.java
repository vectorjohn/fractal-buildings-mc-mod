package dev.fractalbuildings.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

/**
 * Throwaway hello-world structure that proves the mod can place blocks: a 3x3 hut
 * (stone brick floor, plank walls and roof, a one-wide, two-high door on the north side).
 * The real placement path will come from the planner IR.
 */
public final class HelloHut {
	private HelloHut() {
	}

	/** Places the hut with its lowest north-west corner at {@code origin}. */
	public static void place(LevelAccessor level, BlockPos origin) {
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				level.setBlock(origin.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
				level.setBlock(origin.offset(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
				boolean ring = x != 1 || z != 1;
				boolean door = x == 1 && z == 0;
				for (int y = 1; y <= 2; y++) {
					var state = ring && !door ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					level.setBlock(origin.offset(x, y, z), state, 3);
				}
			}
		}
	}
}
