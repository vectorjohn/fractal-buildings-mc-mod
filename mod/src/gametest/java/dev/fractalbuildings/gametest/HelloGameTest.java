package dev.fractalbuildings.gametest;

import dev.fractalbuildings.mod.HelloHut;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Server game test: the mod can place a structure into a world and read it back. */
// gametest-order: 1
public class HelloGameTest {
	@GameTest
	public void helloHutIsPlaced(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
		HelloHut.place(helper.getLevel(), origin);

		helper.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(2, 1, 2));
		helper.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(4, 1, 4));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, 2, 2));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 3, 4));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(3, 4, 3)); // roof centre
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 3)); // interior
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 2)); // door
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 2)); // door, upper
		helper.succeed();
	}
}
