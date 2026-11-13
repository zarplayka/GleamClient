//? if <26.1 {
/*package ru.orion.client.mixin;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionWorldChanger;
@Mixin(FogRenderer.class)
public abstract class GleamLegacyFogMixin {
    @Inject(method="computeFogColor", at=@At("RETURN"))
    private void gleam$color(Camera camera, float delta, ClientLevel level, int distance, float darken,
                             CallbackInfoReturnable<Vector4f> callback) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled() || !config.isWorldChangerFogEnabled()) return;
        FogType fluid = camera.entity().isInWater() ? FogType.WATER : camera.getFluidInCamera();
        if (fluid != FogType.NONE && fluid != FogType.ATMOSPHERIC) return;
        if (camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        var color = callback.getReturnValue();
        var tint = OrionWorldChanger.fogVector(config.getWorldChangerFogColor());
        tint.w = color.w;
        color.lerp(tint, config.getWorldChangerFogStrength()/100F);
    }
    @Inject(method="setupFog", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void gleam$distance(CallbackInfoReturnable<Vector4f> callback, @com.llamalad7.mixinextras.sugar.Local FogData fog) {
        var config = OrionClient.getConfigOrNull();
        if (config == null || !config.isWorldChangerEnabled() || !config.isWorldChangerFogEnabled()) return;
        int configured = config.getWorldChangerFogDistance();
        if (configured > 0 && fog.renderDistanceEnd > 1F) {
            float end = Math.min(configured, fog.renderDistanceEnd);
            fog.environmentalStart = end * config.getWorldChangerFogStart() / 100F;
            fog.environmentalEnd = end;
            fog.skyEnd = Math.min(fog.skyEnd, end);
            fog.cloudEnd = Math.min(fog.cloudEnd, end);
        }
        return;
    }
}
*///?}
