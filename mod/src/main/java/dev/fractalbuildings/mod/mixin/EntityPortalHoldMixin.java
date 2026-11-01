package dev.fractalbuildings.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fractalbuildings.mod.teleport.PortalHold;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets {@link PortalHold} defer a nether-portal trip whose exit search would generate chunks on the server thread (6.30 PORTALCOLD). Wraps the
 * "is it time to go" test in {@code Entity.handlePortal}, before vanilla sets the portal cooldown and calls {@code getPortalDestination}: a
 * deferred trip leaves the portal state as it was, so vanilla asks again next tick while the entity stays inside. Also forgets the stale
 * supporting block after a portal trip that changed the dimension ({@link PortalHold#afterTrip}).
 */
@Mixin(Entity.class)
public class EntityPortalHoldMixin {
	@WrapOperation(method = "handlePortal", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/PortalProcessor;processPortalTeleportation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)Z"))
	private boolean fractalbuildings$portalHold(PortalProcessor proc, ServerLevel level, Entity entity, boolean allowed, Operation<Boolean> original) {
		boolean inside = proc.isInsidePortalThisTick(); // processPortalTeleportation clears it
		boolean go = original.call(proc, level, entity, allowed);
		return PortalHold.gate(level, entity, proc.isSamePortal((Portal) Blocks.NETHER_PORTAL), proc.getEntryPosition(), inside, allowed, go);
	}

	@WrapOperation(method = "handlePortal", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;teleportToPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/TeleportTransition;)V"))
	private void fractalbuildings$afterTrip(Entity self, ServerLevel level, TeleportTransition transition, Operation<Void> original) {
		original.call(self, level, transition);
		PortalHold.afterTrip(self, level);
	}
}
