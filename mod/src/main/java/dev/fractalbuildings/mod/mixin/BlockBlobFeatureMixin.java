package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.RockVeto;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No boulder where our pieces are (ROCKVETO, {@link RockVeto}). Target, re-check with {@code javap -p} on each Minecraft bump:
 * {@code public boolean place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} of the record {@code BlockBlobFeature}. Returning
 * false is what vanilla returns for a boulder that does not fit.
 */
@Mixin(BlockBlobFeature.class)
public class BlockBlobFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$veto(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (RockVeto.vetoes(level, gen, origin, ((BlockBlobFeature) (Object) this).canPlaceOn())) cir.setReturnValue(false);
	}
}
