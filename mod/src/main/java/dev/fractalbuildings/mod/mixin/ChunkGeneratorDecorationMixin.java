package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.FoliageWatch;
import dev.fractalbuildings.mod.worldgen.OwnWrites;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chunk's decoration starts: {@link OwnWrites} forgets the last chunk's records (ROADLEAFORDER); {@link FoliageWatch} logs it near a
 * watched box. Target: {@code public void applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)}.
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorDecorationMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
	private void fractalbuildings$begin(WorldGenLevel level, ChunkAccess chunk, StructureManager sm, CallbackInfo ci) {
		OwnWrites.begin(chunk.getPos().x(), chunk.getPos().z());
		if (FoliageWatch.ACTIVE && FoliageWatch.near(chunk.getPos())) FoliageWatch.decorate(chunk.getPos());
	}

	@Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
	private void fractalbuildings$end(WorldGenLevel level, ChunkAccess chunk, StructureManager sm, CallbackInfo ci) {
		OwnWrites.end();
	}
}
