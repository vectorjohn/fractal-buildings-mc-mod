package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.FoliageWatch;
import dev.fractalbuildings.mod.worldgen.OwnWrites;
import dev.fractalbuildings.mod.worldgen.TreeClip;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A tree's room check counts the cells our pieces may claim as free (LEAFORDER, {@link TreeClip}); every write of a tree is recorded for
 * {@link OwnWrites} (ROADLEAFORDER). Targets, re-check with {@code javap -p} on each Minecraft bump: {@code private int
 * getMaxFreeTreeHeight(WorldGenLevel, int, BlockPos)} of the record {@code TreeFeature}, called once per tree by {@code doPlace} before
 * anything is written, and {@code public boolean place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)}.
 */
@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
	@Inject(method = "getMaxFreeTreeHeight", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$room(WorldGenLevel level, int maxTreeHeight, BlockPos treePos, CallbackInfoReturnable<Integer> cir) {
		Integer h = TreeClip.maxFreeHeight(level, (TreeFeature) (Object) this, maxTreeHeight, treePos);
		if (h != null) cir.setReturnValue(h);
	}

	@Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"))
	private void fractalbuildings$enter(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		OwnWrites.enterTree();
	}

	@Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z", at = @At("RETURN"))
	private void fractalbuildings$exit(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		OwnWrites.exitTree();
		if (FoliageWatch.ACTIVE && FoliageWatch.nearTree(origin)) FoliageWatch.tree(origin, "place " + cir.getReturnValueZ() + " below " + level.getBlockState(origin.below()) + " at " + level.getBlockState(origin));
	}
}
