package dev.fractalbuildings.mod.mixin;

import dev.fractalbuildings.mod.teleport.TeleportHold;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link TeleportHold} take over long same-dimension teleports into ungenerated terrain (TPHOLD). */
@Mixin(ServerPlayer.class)
public class ServerPlayerTeleportMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
	private void fractalbuildings$hold(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (TeleportHold.intercept(self, transition)) cir.setReturnValue(self);
	}
}
