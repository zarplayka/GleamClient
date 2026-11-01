package ru.orion.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.orion.client.OrionClient;

@Mixin(ClientLevel.class)
public abstract class ClientExplosionParticlesMixin {
    @Inject(method = "trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideExplosionDebris(Vec3 center, float radius, int blockCount,
                                           WeightedList<ExplosionParticleInfo> particles, CallbackInfo callback) {
        var config = OrionClient.getConfigOrNull();
        if (config != null && config.isNoParticlesEnabled() && config.isNoParticlesExplosions()) {
            callback.cancel();
        }
    }
}
