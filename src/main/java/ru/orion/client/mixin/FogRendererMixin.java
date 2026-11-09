package ru.orion.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionWorldChanger;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void orion$applyWorldChangerFog(Camera camera, int renderDistance, DeltaTracker tickCounter, float darkenWorldAmount,
                                            ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled() || !config.isWorldChangerFogEnabled()) {
            return;
        }

        FogType fluid = camera.getFluidInCamera();
        if (fluid != FogType.NONE && fluid != FogType.ATMOSPHERIC) return;
        if (camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        var fog = cir.getReturnValue();
        var tint = OrionWorldChanger.fogVector(config.getWorldChangerFogColor());
        tint.w = fog.color.w;
        fog.color.lerp(tint, config.getWorldChangerFogStrength() / 100F);
        int configured = config.getWorldChangerFogDistance();
        if (configured > 0 && fog.renderDistanceEnd > 1F) {
            float end = Math.min(configured, fog.renderDistanceEnd);
            fog.environmentalStart = end * config.getWorldChangerFogStart() / 100F;
            fog.environmentalEnd = end;
            fog.skyEnd = Math.min(fog.skyEnd, end);
            fog.cloudEnd = Math.min(fog.cloudEnd, end);
        }
    }
}
