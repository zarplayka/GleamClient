package ru.orion.client.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.effect.OrionOverworldClock;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    @Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;", at = @At("RETURN"))
    private void orion$identifyClock(Holder<WorldClock> holder, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> callback) {
        ((OrionOverworldClock) callback.getReturnValue()).orion$setOverworld(holder.is(WorldClocks.OVERWORLD));
    }
}
