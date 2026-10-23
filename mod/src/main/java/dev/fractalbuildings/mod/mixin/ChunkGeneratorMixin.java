package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.compat.CityVeto;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Start generation half of the veto (S8). Target, re-check with {@code javap -p} on each Minecraft bump:
 * {@code private boolean tryGenerateStructure(StructureSet.StructureSelectionEntry, StructureManager, RegistryAccess, RandomState,
 * StructureTemplateManager, long, ChunkAccess, ChunkPos, ResourceKey<Level>, Climate.Sampler)}. Returning false makes the caller try the
 * set's next weighted entry.
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
	@Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$veto(StructureSet.StructureSelectionEntry entry, StructureManager sm, RegistryAccess ra, RandomState rs,
			StructureTemplateManager tm, long seed, ChunkAccess chunk, ChunkPos pos, ResourceKey<Level> dim, Climate.Sampler sampler,
			CallbackInfoReturnable<Boolean> cir) {
		if (CityVeto.vetoes(entry.structure(), pos, (ChunkGenerator) (Object) this, rs, seed)) cir.setReturnValue(false);
	}
}
