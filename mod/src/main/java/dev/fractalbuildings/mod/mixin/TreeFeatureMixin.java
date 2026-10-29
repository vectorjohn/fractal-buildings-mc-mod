package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.TreeClip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A tree's room check counts the cells our pieces may claim as free (LEAFORDER, {@link TreeClip}). Target, re-check with {@code javap -p}
 * on each Minecraft bump: {@code private int getMaxFreeTreeHeight(WorldGenLevel, int, BlockPos)} of the record {@code TreeFeature}, called
 * once per tree by {@code doPlace} before anything is written.
 */
@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
	@Inject(method = "getMaxFreeTreeHeight", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$room(WorldGenLevel level, int maxTreeHeight, BlockPos treePos, CallbackInfoReturnable<Integer> cir) {
		Integer h = TreeClip.maxFreeHeight(level, (TreeFeature) (Object) this, maxTreeHeight, treePos);
		if (h != null) cir.setReturnValue(h);
	}
}
