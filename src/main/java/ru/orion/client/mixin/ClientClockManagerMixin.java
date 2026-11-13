//? if >=26.1 {
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
    //? if >=26.3 {
    @Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;", at = @At("RETURN"))
    private void orion$identifyClock(Holder<WorldClock> holder, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> callback) {
        ((OrionOverworldClock) callback.getReturnValue()).orion$setOverworld(holder.is(WorldClocks.OVERWORLD));
    }
    //?} else {
        /*@Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J", at = @At("RETURN"), cancellable = true)
    private void gleam$customTime(Holder<WorldClock> holder, CallbackInfoReturnable<Long> callback) {
        var config = ru.orion.client.OrionClient.getConfigOrNull();
        if (holder.is(WorldClocks.OVERWORLD) && config != null && config.isCustomTimeEnabled()) {
            long day = Math.floorDiv(callback.getReturnValue(), 24000L);
            callback.setReturnValue(day * 24000L + config.getCustomTimeTicks());
        }
    }
    *///?}
}
//?}
