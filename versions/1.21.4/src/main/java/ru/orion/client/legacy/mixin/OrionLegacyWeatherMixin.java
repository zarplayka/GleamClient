package ru.orion.client.legacy.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.legacy.OrionLegacyClient;

@Mixin(Level.class)
abstract class OrionLegacyWeatherMixin {
    @Inject(method = {"getRainLevel(F)F", "getThunderLevel(F)F"}, at = @At("HEAD"),
            cancellable = true, require = 2)
    private void orion$hideWeather(float partialTick, CallbackInfoReturnable<Float> callback) {
        if (!((Object) this instanceof ClientLevel)) {
            return;
        }
        var config = OrionLegacyClient.getConfigOrNull();
        if (config != null && config.isNoWeatherEnabled()) {
            callback.setReturnValue(0F);
        }
    }
}
