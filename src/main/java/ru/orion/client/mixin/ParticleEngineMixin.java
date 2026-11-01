package ru.orion.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;
import ru.orion.client.effect.OrionNoParticles;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void orion$filterParticle(ParticleOptions options, double x, double y, double z,
                                      double vx, double vy, double vz, CallbackInfoReturnable<Particle> callback) {
        if (OrionNoParticles.shouldHide(OrionClient.getConfigOrNull(), options)) callback.setReturnValue(null);
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$filterEmitter(Entity entity, ParticleOptions options, CallbackInfo callback) {
        if (OrionNoParticles.shouldHide(OrionClient.getConfigOrNull(), options)) callback.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$filterTimedEmitter(Entity entity, ParticleOptions options, int duration, CallbackInfo callback) {
        if (OrionNoParticles.shouldHide(OrionClient.getConfigOrNull(), options)) callback.cancel();
    }
}
