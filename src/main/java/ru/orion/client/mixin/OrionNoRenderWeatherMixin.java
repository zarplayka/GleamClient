package ru.orion.client.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.WeatherRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.WeatherRenderState;
*///?}
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.effect.OrionNoRender;

@Mixin(WeatherEffectRenderer.class)
public abstract class OrionNoRenderWeatherMixin {
    //? if >=26.3 {
    @Inject(method = "prepare", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    *///?}
    private void orion$filterPrecipitation(
            //? if <26.1 {
            /*net.minecraft.client.renderer.MultiBufferSource buffers,
            *///?}
            Vec3 camera, WeatherRenderState state, CallbackInfo callback) {
        if (OrionNoRender.hides(OrionNoRender.Option.RAIN)) state.rainColumns.clear();
        if (OrionNoRender.hides(OrionNoRender.Option.SNOW)) state.snowColumns.clear();
    }
}
