package ru.orion.client.mixin;

import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionOverworldClock;

@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin implements OrionOverworldClock {
    @Unique private boolean orion$overworld;
    @Override public void orion$setOverworld(boolean value) { orion$overworld = value; }

    @Inject(method = "totalTicks()J", at = @At("RETURN"), cancellable = true)
    private void orion$customTime(CallbackInfoReturnable<Long> callback) {
        var config = OrionClient.getConfigOrNull();
        if (orion$overworld && config != null && config.isCustomTimeEnabled()) {
            long day = Math.floorDiv(callback.getReturnValue(), 24000L);
            callback.setReturnValue(day * 24000L + config.getCustomTimeTicks());
        }
    }

    @Inject(method = "partialTick()F", at = @At("HEAD"), cancellable = true)
    private void orion$freezeFraction(CallbackInfoReturnable<Float> callback) {
        var config = OrionClient.getConfigOrNull();
        if (orion$overworld && config != null && config.isCustomTimeEnabled()) callback.setReturnValue(0F);
    }
}
