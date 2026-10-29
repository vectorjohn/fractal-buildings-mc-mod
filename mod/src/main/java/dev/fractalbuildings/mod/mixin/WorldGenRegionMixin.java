package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.worldgen.FoliageWatch;
import dev.fractalbuildings.mod.worldgen.OwnWrites;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the writes of the decorating chunk's vanilla features ({@link OwnWrites}, ROADLEAFORDER) and, when {@code FB_FOLIAGE_WATCH} is set,
 * logs writes into the watched boxes ({@link FoliageWatch}). Target, re-check with {@code javap -p} on each Minecraft bump:
 * {@code public boolean setBlock(BlockPos, BlockState, int, int)} of {@code WorldGenRegion} (the 3-argument form delegates to it).
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
	@Shadow private Supplier<String> currentlyGenerating;

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
	private void fractalbuildings$record(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
		WorldGenRegion self = (WorldGenRegion) (Object) this;
		if (OwnWrites.recording()) OwnWrites.onSetBlock(pos, state, self::isWithinWriteZone, self::getBlockState); // never reads a cell outside the write zone (r2)
		if (FoliageWatch.ACTIVE && self.isWithinWriteZone(pos) && FoliageWatch.watched(pos.getX(), pos.getY(), pos.getZ()))
			FoliageWatch.write(self.getCenter(), currentlyGenerating == null ? "?" : currentlyGenerating.get(), pos, self.getBlockState(pos), state);
	}
}
