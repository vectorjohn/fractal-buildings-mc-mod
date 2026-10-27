package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.LakeVeto;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No lake where our pieces are (ORDERFAIL, {@link LakeVeto}). Target, re-check with {@code javap -p} on each Minecraft bump:
 * {@code public boolean place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} of the record {@code LakeFeature}. Returning
 * false is what vanilla returns for a lake that does not fit.
 */
@Mixin(LakeFeature.class)
public class LakeFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$veto(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (LakeVeto.vetoes(level, gen, origin)) cir.setReturnValue(false);
	}
}
