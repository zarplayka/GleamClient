package ru.orion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionZoom;

@Mixin(MouseHandler.class)
public abstract class OrionZoomMouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void orion$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != 0L && window == minecraft.getWindow().handle()
                && OrionZoom.scroll(minecraft, OrionClient.getConfigOrNull(), vertical)) {
            ci.cancel();
        }
    }

    @Redirect(method = "turnPlayer(D)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean orion$cinematic(Options options) {
        var config = OrionClient.getConfigOrNull();
        return options.smoothCamera || OrionZoom.secondary()
                || (config != null && OrionZoom.active() && config.getZoomCinematic() > 0F);
    }

    @ModifyArg(method = "turnPlayer(D)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/SmoothDouble;getNewDeltaValue(DD)D"), index = 1)
    private double orion$cinematicStrength(double smoothing) {
        var config = OrionClient.getConfigOrNull();
        float strength = config == null ? 0F : config.getZoomCinematic();
        return OrionZoom.active() && strength > 0F ? smoothing / Math.max(.01F, strength) : smoothing;
    }

    @Redirect(method = "turnPlayer(D)V", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object orion$relativeSensitivity(OptionInstance<?> option) {
        Object value = option.get();
        var config = OrionClient.getConfigOrNull();
        if (config == null || OrionZoom.current() == 1F) return value;
        double divisor = 1D + config.getZoomRelativeSensitivity() * (OrionZoom.current() - 1D);
        return (Double) value / divisor;
    }
}
