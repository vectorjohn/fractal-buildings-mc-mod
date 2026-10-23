package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.compat.CityVeto;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /locate} half of the veto (S8): {@code private boolean canCreateStructure(ChunkPos, Structure)} is what
 * {@code checkStart} asks, which every locate, explorer map and structure advancement goes through.
 */
@Mixin(StructureCheck.class)
public class StructureCheckMixin {
	@Shadow @Final private RegistryAccess registryAccess;
	@Shadow @Final private ChunkGenerator chunkGenerator;
	@Shadow @Final private RandomState randomState;
	@Shadow @Final private long seed;

	@Inject(method = "canCreateStructure", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$veto(ChunkPos pos, Structure structure, CallbackInfoReturnable<Boolean> cir) {
		var holder = registryAccess.lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
		if (CityVeto.vetoes(holder, pos, chunkGenerator, randomState, seed)) cir.setReturnValue(false);
	}
}
