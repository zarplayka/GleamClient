package ru.orion.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.orion.client.OrionClient;

@Mixin(EntityRenderer.class)
public abstract class OrionRagdollRendererMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true)
    private void orion$hideReplacedPlayer(Entity entity, Frustum frustum, double x, double y, double z,
                                          float partialTick, CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof AbstractClientPlayer player) {
            var effects = OrionClient.getKillEffects();
            if (effects != null && effects.replacesPlayerModel(player)) result.setReturnValue(false);
        }
    }
}
