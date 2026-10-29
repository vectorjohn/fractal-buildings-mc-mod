package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.OwnWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the writes of a placed feature for {@link OwnWrites} (ROADLEAFORDER): structures write outside it. Target, re-check with
 * {@code javap -p} on each Minecraft bump: {@code public boolean place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)} of the
 * record {@code PlacedFeature} (nested features of a selector go through it too).
 */
@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"))
	private void fractalbuildings$enter(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		OwnWrites.enterFeature();
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void fractalbuildings$exit(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		OwnWrites.exitFeature();
	}
}
