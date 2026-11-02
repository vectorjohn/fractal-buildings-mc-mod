package dev.fractalbuildings.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fractalbuildings.mod.worldgen.ReadyChunks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * COLDTICK (6.37): the cat and patrol spawners test {@code ServerLevel.hasChunksAt} around a random spot near a player, then read blocks
 * there on the server thread. A chunk with a FULL-level ticket passes that test while it is still generating, and the read then waits for
 * its whole generation (a 27 s tick on the perf worker, stack in docs/log/2026-11-02-p6-coldtick.md). Here the test also needs every chunk to
 * be FULL already ({@link ReadyChunks}); otherwise the attempt is skipped, as vanilla skips an unloaded spot. Changes no generated block:
 * cat and patrol spawns are gameplay, drawn from the level's own random. Targets (26.3 bytecode, re-check with {@code javap -c} on a bump):
 * {@code public void tick(ServerLevel, boolean)} of {@code CatSpawner} and {@code PatrolSpawner}, each with one
 * {@code ServerLevel.hasChunksAt(IIII)Z} call.
 */
@Mixin(targets = { "net.minecraft.world.entity.npc.CatSpawner", "net.minecraft.world.level.levelgen.PatrolSpawner" })
public class CustomSpawnerReadyMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;hasChunksAt(IIII)Z"))
	private boolean fractalbuildings$readyChunks(ServerLevel level, int x0, int z0, int x1, int z1, Operation<Boolean> original) {
		return original.call(level, x0, z0, x1, z1) && ReadyChunks.allFull(level, x0, z0, x1, z1);
	}
}
